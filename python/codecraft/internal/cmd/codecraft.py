from __future__ import annotations

from abc import ABC
from typing import TYPE_CHECKING

from codecraft.client.client import CCClient
from codecraft.internal.resource import ResLoc
from .cmd import Cmd

if TYPE_CHECKING:
    from codecraft.internal import ResLocLike, CCByteBuf


class SendSystemChatCmd(Cmd, reg_name=ResLoc.codecraft("send_system_chat")):
    def __init__(self, message: str):
        super().__init__()
        self._message = message

    def _write(self, buf: CCByteBuf, client: CCClient):
        super()._write(buf, client)
        buf.write_str(self._message)


class AbstractWorldCmd(Cmd, ABC):
    def __init__(self, world: ResLocLike):
        super().__init__()
        self._world = ResLoc.from_like(world)

    def _write(self, buf: CCByteBuf, client: CCClient):
        super()._write(buf, client)
        buf.write_using_id_map(client.reg_id_maps.world, self._world)

# class SetBlockCmd(Cmd, reg_name=ResLoc.codecraft("set_block")):
#     pass
