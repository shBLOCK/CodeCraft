from __future__ import annotations

from abc import ABC
from enum import IntFlag
from typing import TYPE_CHECKING

from spatium import Vec3i

from codecraft.internal.resource import ResLoc
from .cmd import Cmd

if TYPE_CHECKING:
    from codecraft.client.client import CCClient
    from codecraft.block.block import Block
    from codecraft.world import World
    from codecraft.internal import CCByteBuf


class SendSystemChatCmd(Cmd, reg_name=ResLoc.codecraft("send_system_chat")):
    def __init__(self, message: str):
        super().__init__()
        self._message = message

    def _write(self, buf: CCByteBuf, client: CCClient):
        super()._write(buf, client)
        buf.write_str(self._message)


class AbstractWorldCmd(Cmd, ABC):
    def __init__(self, world: World):
        super().__init__()
        self._world = world

    def _write(self, buf: CCByteBuf, client: CCClient):
        super()._write(buf, client)
        buf.write_using_id_map(client.reg_id_maps.world, self._world)


class SetBlockFlags(IntFlag):
    SET_STATE = 1
    SET_NBT = 2
    ON_TICK = 4
    BLOCK_UPDATE = 8
    KEEP = 16
    DESTROY = 32
    DROP_ITEM = 64


class SetBlockCmd(AbstractWorldCmd, reg_name=ResLoc.codecraft("set_block")):
    def __init__(self, world: World, pos: Vec3i, block: Block, flags: int):
        super().__init__(world)
        self._block = block
        self._pos = pos
        self._flags = flags

    def _write(self, buf: CCByteBuf, client: CCClient):
        super()._write(buf, client)
        buf.write_vec3i(self._pos)
        buf.write_byte(self._flags, tc=True)

        if self._flags & SetBlockFlags.SET_STATE:
            buf.write_blockstate(self._block)
        else:
            buf.write_using_id_map(client.reg_id_maps.block, self._block)

        # TODO: block nbt
        # if self._flags & SetBlockFlags.SET_NBT:
        #     buf.write_nbt(self._block.nbt)

    def _parse_result(self, buf: CCByteBuf, client: CCClient) -> bool:
        return buf.read_bool()
