from __future__ import annotations

import asyncio
from asyncio import Task
from typing import TYPE_CHECKING, override
from abc import ABC, abstractmethod

from codecraft.internal import CCByteBuf
from codecraft.internal.msg import CmdResultMsg

if TYPE_CHECKING:
    from codecraft.internal.cmd import Cmd
    from codecraft.client import CCClient


class CmdRunner(ABC):
    def __init__(self, client: CCClient):
        self._client = client

    @abstractmethod
    async def _run_cmd(self, cmd: Cmd) -> CmdResultMsg:
        ...


# noinspection PyProtectedMember
class SimpleCmdRunner(CmdRunner):
    @override
    async def _run_cmd(self, cmd: Cmd):
        buf = CCByteBuf(client=self._client)
        buf.write_using_id_map(self._client.reg_id_maps.cmd, type(cmd))
        cmd._write(buf, self._client)

        waiter = self._client._msg_queue._wait_for_result(cmd)

        await self._client.send_raw(buf)

        return await waiter


# noinspection PyProtectedMember
class BatchingCmdRunner(CmdRunner):
    def __init__(self, client: CCClient):
        super().__init__(client)
        self._old_cmd_runner: CmdRunner
        self._buffer = CCByteBuf(client=self._client)
        self._waiters: list[Task] = []

    async def __aenter__(self):
        self._old_cmd_runner = self._client._cmd_runner
        self._client._cmd_runner = self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        self._client._cmd_runner = self._old_cmd_runner

        if exc_val is not None:
            for waiter in self._waiters:
                waiter.cancel("Command wasn't sent")
            self._buffer = CCByteBuf(client=self._client)
            self._waiters.clear()
            return False

        await self._client.send_raw(self._buffer)
        self._buffer = CCByteBuf(client=self._client)
        self._waiters.clear()

    @override
    def _run_cmd(self, cmd: Cmd):
        # not async def to make sure the sending logic runs immediately

        self._buffer.write_using_id_map(self._client.reg_id_maps.cmd, type(cmd))
        cmd._write(self._buffer, self._client)

        waiter = asyncio.create_task(
            self._client._msg_queue._wait_for_result(cmd))
        self._waiters.append(waiter)

        return waiter

    def __enter__(self):
        raise ValueError("CmdBatch can't be used with regular with statement, use async with instead")
