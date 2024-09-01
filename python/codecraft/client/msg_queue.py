from __future__ import annotations

import asyncio
import threading
from asyncio import CancelledError
from typing import TYPE_CHECKING, Any

from websockets.frames import CloseCode

from codecraft.coro import set_task_name
from codecraft.internal.error import NetworkError
from codecraft.internal.msg.codecraft import CmdResultMsg
from codecraft.internal.typings import dummy_for_ide

if TYPE_CHECKING:
    from typing import Optional
    from asyncio import Future, AbstractEventLoop
    from collections.abc import Awaitable

    from codecraft.client import CCClient
    from codecraft.internal.cmd import Cmd
    from codecraft.internal.msg import Msg


# noinspection PyProtectedMember
class MsgQueue:
    def __init__(self, client: CCClient):
        self._client = client
        self._loop: AbstractEventLoop = dummy_for_ide()

        # A result waiter is guaranteed to be added here before the command is sent to the server.
        # (Unless the command result is intended to be discarded)
        self._running_cmds: dict[int, Cmd] = {}
        self._result_waiters: dict[int, Future[CmdResultMsg]] = {}
        self._waiters_lock = threading.Lock()

        self._receiver: asyncio.Task[None]

    async def _receiver_main(self):
        client = self._client
        set_task_name("MsgReceiver")
        client._logger.debug("Message receiver started")
        while True:
            try:
                data = await client.recv_raw()
                while data.remaining:
                    msg_type = data.read_using_id_map(client.reg_id_maps.msg)
                    msg = msg_type(data, client)

                    self.__put(msg)
            except CancelledError:
                raise
            except NetworkError as e:
                client._logger.error("Error in message queue: %s", e)
                # this also calls self._stop()
                _ = asyncio.create_task(client.close(f"Message queue error: {e}", CloseCode.POLICY_VIOLATION))
                raise
            except Exception as e:
                client._logger.error("Message queue encountered unexpected error, closing", exc_info=e)
                # this also calls self._stop()
                _ = asyncio.create_task(client.close(f"Message queue error: {e}", CloseCode.INTERNAL_ERROR))
                raise NetworkError("Message queue unexpected error") from e

    async def _start(self):
        self._loop = asyncio.get_running_loop()
        self._receiver = self._loop.create_task(self._receiver_main())

    def _stop(self):
        """Stop the message listener coroutine and cancel all awaiting things, thread safe."""

        with self._waiters_lock:
            for fut in self._result_waiters.values():
                fut.get_loop().call_soon_threadsafe(fut.cancel, "Message queue closed")

        async def cancel():
            self._receiver.cancel()
            try:
                await self._receiver
            except CancelledError:
                pass
            with self._waiters_lock:
                self._result_waiters.clear()

        asyncio.run_coroutine_threadsafe(cancel(), self._loop)

    def __put(self, msg: Msg):
        if isinstance(msg, CmdResultMsg):
            with self._waiters_lock:
                if fut := self._result_waiters.get(msg.cmd_uid):
                    fut.get_loop().call_soon_threadsafe(_safe_set_result, fut, msg)
        else:
            ...
            # TODO: msg handling

    def _pop_running_cmd(self, id: int) -> Optional[Cmd]:
        return self._running_cmds.pop(id, None)

    def _wait_for_result(self, cmd: Cmd) -> Awaitable[CmdResultMsg]:
        """Wait for a command result to arrive and return it.

        If the result has already received, return it immediately.
        One command result can only be received once.

        Separating this method with the actual async waiter function
        ensures that the waiter gets added to self._result_waiters
        immediately upon calling this, avoiding some race conditions
        when the server responds too quickly.
        """
        fut = asyncio.get_running_loop().create_future()
        self._running_cmds[cmd._uid] = cmd
        with self._waiters_lock:
            self._result_waiters[cmd._uid] = fut
        return self.__wait_for_result(cmd, fut)

    async def __wait_for_result(self, cmd: Cmd, fut: Future[CmdResultMsg]) -> CmdResultMsg:
        try:
            return await fut
        finally:
            with self._waiters_lock:
                if cmd._uid in self._result_waiters:
                    del self._result_waiters[cmd._uid]


def _safe_set_result(fut: Future, result: Any):
    if not fut.cancelled():
        fut.set_result(result)
