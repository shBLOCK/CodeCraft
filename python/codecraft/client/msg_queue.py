from __future__ import annotations

import asyncio
from typing import TYPE_CHECKING

from websockets.frames import CloseCode

from codecraft.asyncio.misc import set_task_name
from codecraft.asyncio.runner import LOOP

if TYPE_CHECKING:
    from asyncio import Future
    from collections.abc import MutableMapping

    from codecraft.client import CCClient
    from codecraft.internal.cmd import Cmd
    from codecraft.internal.msg import Msg, CmdResultMsg


# noinspection PyProtectedMember
class MsgQueue:
    def __init__(self, client: CCClient):
        from threading import Thread

        # A result waiter is guaranteed to be added here before the command is sent to the server.
        # (Unless the command result is intended to be discarded)
        self._result_waiters: MutableMapping[int, Future[CmdResultMsg]] = {}

        def receiver_thread():
            async def main():
                set_task_name("MsgReceiver")

                while True:
                    try:
                        data = await client.recv_raw()
                        while data.remaining:
                            msg = client.reg_id_maps.msg[data.read_varint()](data, client)
                            asyncio.run_coroutine_threadsafe(self.__put(msg), LOOP)
                    except CCClient.NetworkError as e:
                        client.logger.warning("Network error while receiving message, closing: %s", e)
                        return
                    except Exception as e:
                        client.logger.warning("Message queue thread encountered unexpected error, closing", exc_info=e)
                        client.close("Message queue error", CloseCode.INTERNAL_ERROR)
                        return
            asyncio.run(main())

        self._receiver = Thread(
            name="CodeCraft Message Receiver Thread",
            target=receiver_thread,
            daemon=True
        )

    def _start(self):
        self._receiver.start()

    async def __put(self, msg: Msg):
        if isinstance(msg, CmdResultMsg):
            if waiter := self._result_waiters.get(msg.cmd_uid):
                waiter.set_result(msg)
        else:
            ...
            # TODO: msg handling

    async def _wait_for_result(self, cmd: Cmd) -> CmdResultMsg:
        """Wait for a command result to arrive and return it.

        If the result is already received, return it immediately.
        One command result can only be received once.
        """
        fut = asyncio.get_running_loop().create_future()
        self._result_waiters[cmd._uid] = fut
        try:
            return await fut
        finally:
            del self._result_waiters[cmd._uid]
