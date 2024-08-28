from __future__ import annotations

import asyncio
from asyncio import CancelledError
from typing import TYPE_CHECKING

from websockets.frames import CloseCode

from codecraft.asyncio.misc import set_task_name
from codecraft.asyncio.runner import LOOP
from codecraft.internal.error import NetworkError
from codecraft.internal.msg.codecraft import CmdResultMsg

if TYPE_CHECKING:
    from typing import Optional
    from asyncio import Future
    from collections.abc import MutableMapping

    from codecraft.client import CCClient
    from codecraft.internal.cmd import Cmd
    from codecraft.internal.msg import Msg


# noinspection PyProtectedMember
class MsgQueue:
    def __init__(self, client: CCClient):
        self._client = client

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
                client.close(f"Message queue error: {e}", CloseCode.POLICY_VIOLATION)
                raise
            except Exception as e:
                client._logger.error("Message queue encountered unexpected error, closing", exc_info=e)
                client.close("Message queue error", CloseCode.INTERNAL_ERROR)
                raise NetworkError("Message queue unexpected error") from e

    def _start(self):
        self._receiver = LOOP.create_task(self._receiver_main())

    def _stop(self):
        self._receiver.cancel()

    def __put(self, msg: Msg):
        if isinstance(msg, CmdResultMsg):
            if waiter := self._result_waiters.get(msg.cmd_uid):
                waiter[1].set_result(msg)
        else:
            ...
            # TODO: msg handling

    def _get_running_cmd(self, id: int) -> Optional[Cmd]:
        tup = self._result_waiters.get(id)
        return tup[0] if tup is not None else None

    async def _wait_for_result(self, cmd: Cmd) -> CmdResultMsg:
        """Wait for a command result to arrive and return it.

        If the result is already received, return it immediately.
        One command result can only be received once.
        """
        fut = asyncio.get_running_loop().create_future()
        self._result_waiters[cmd._uid] = (cmd, fut)
        try:
            return await fut
        finally:
            del self._result_waiters[cmd._uid]
