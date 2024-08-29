from __future__ import annotations

from typing import TYPE_CHECKING

from spatium import Vec3i

# noinspection PyProtectedMember
from codecraft.client.client import _cc
from codecraft.block.block import Block
from codecraft.internal.cmd.codecraft import SetBlockCmd, SetBlockFlags
from codecraft.internal.resource import ResLoc
from codecraft.internal.registry import Registered, DefaultedInstantiatingRegistry, FlexibleParamOfRegistered
from codecraft.internal.default_instance import LazyDefaultInstance
from codecraft.internal.registry import flexible_param_get_instance
from codecraft.coro import auto_async

if TYPE_CHECKING:
    from codecraft.internal.typings import Vec3iLike
    from codecraft.internal import ResLocLike


class World(Registered["World", DefaultedInstantiatingRegistry["World"]], LazyDefaultInstance,
            registry_name="dimension", registry_type=DefaultedInstantiatingRegistry):
    """A Minecraft dimension (e.g. Overworld, Nether, The End)."""

    def __init__(self, name: ResLocLike = "overworld"):
        self._name = ResLoc.from_like(name)

    @auto_async
    async def set_block(
        self,
        pos: Vec3iLike,
        block: FlexibleParamOfRegistered[Block],
        *,
        update: bool = True,
        prevent_neighbor_reactions: bool = False,
        silent: bool = False,
        keep: bool = False,
        destroy: bool = False,
        drop_item: bool = False,
        set_state: bool = True,
        set_nbt: bool = True  # TODO
    ):
        pos = Vec3i(pos)
        block = flexible_param_get_instance(block, Block.registry)

        flags = 0

        if silent:
            flags |= SetBlockFlags.PREVENT_NEIGHBOR_REACTIONS
        else:
            if update:
                flags |= SetBlockFlags.BLOCK_UPDATE
            if prevent_neighbor_reactions:
                flags |= SetBlockFlags.PREVENT_NEIGHBOR_REACTIONS

        if keep:
            flags |= SetBlockFlags.KEEP

        if destroy:
            flags |= SetBlockFlags.DESTROY
            if drop_item:
                flags |= SetBlockFlags.DROP_ITEM

        if set_state:
            flags |= SetBlockFlags.SET_STATE

        # if set_nbt and block.nbt:
        #     flags |= SetBlockFlags.SET_NBT

        return await _cc().run_cmd(SetBlockCmd(self, pos, block, flags))

    def __repr__(self):
        return f"World({self._name})"
