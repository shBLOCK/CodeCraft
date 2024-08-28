from __future__ import annotations

import asyncio
from asyncio import CancelledError
from typing import TYPE_CHECKING

from websockets.frames import CloseCode

from codecraft.asyncio import set_task_name
from codecraft.internal.error import NetworkError
from codecraft.internal.msg.codecraft import CmdResultMsg
from codecraft.internal.typing import dummy_for_ide

if TYPE_CHECKING:
    from typing import Optional
    from asyncio import Future, AbstractEventLoop
    from collections.abc import MutableMapping

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
        self._result_waiters: MutableMapping[int, tuple[Cmd, Future[CmdResultMsg]]] = {}

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
                client._logger.error("Network error while receiving message, closing: %s", e)
                # this also calls self._stop()
                # noinspection PyAsyncCall
                asyncio.create_task(client.close(f"Message queue error: {e}", CloseCode.POLICY_VIOLATION))
                raise
            except Exception as e:
                client._logger.error("Message queue encountered unexpected error, closing", exc_info=e)
                # this also calls self._stop()
                # noinspection PyAsyncCall
                asyncio.create_task(client.close("Message queue error", CloseCode.INTERNAL_ERROR))
                raise NetworkError("Message queue unexpected error") from e

    async def _start(self):
        self._loop = asyncio.get_running_loop()
        self._receiver = self._loop.create_task(self._receiver_main())

    def _stop(self):
        """Stop the message listener coroutine and cancel all awaiting things, thread safe."""

        self._loop.call_soon_threadsafe(self._receiver.cancel)
        for cmd, fut in self._result_waiters:
            fut.get_loop().call_soon_threadsafe(fut.cancel, "Message queue closed")
        self._result_waiters.clear()

    def __put(self, msg: Msg):
        if isinstance(msg, CmdResultMsg):
            if waiter := self._result_waiters.get(msg.cmd_uid):
                fut = waiter[1]
                fut.get_loop().call_soon_threadsafe(fut.set_result, msg)
        else:
            ...
            # TODO: msg handling

    def _get_running_cmd(self, id: int) -> Optional[Cmd]:
        tup = self._result_waiters.get(id)
        return tup[0] if tup is not None else None

    def _add_waiter(self, cmd: Cmd):
        """Register to receive (wait for) the command's results.

        A separate function for creating the waiter is necessary
        since asyncio might not immediately execute a task upon registration,
        which means the waiter might not be created immediately,
        and result in race condition if the result arrives immediately.
        """
        fut = asyncio.get_running_loop().create_future()
        self._result_waiters[cmd._uid] = (cmd, fut)

    # noinspection PyProtectedMember
    async def _wait_for_result(self, cmd: Cmd) -> CmdResultMsg:
        """Wait for a command result to arrive and return it.

        If the result has already received, return it immediately.
        One command result can only be received once.
        """
        _, fut = self._result_waiters[cmd._uid]
        try:
            return await fut
        finally:
            del self._result_waiters[cmd._uid]
