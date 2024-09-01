from typing import overload

from codecraft.block import Block
from codecraft.internal import Registered, DefaultedInstantiatingRegistry, LazyDefaultInstance
from codecraft.internal.registry import ResLocLike, FlexibleParamOfRegistered
from codecraft.internal.typings import Vec3iLike
from codecraft.coro import MaybeAwaitable


class World(Registered["World", DefaultedInstantiatingRegistry["World"]], LazyDefaultInstance,
            registry_name="dimension", registry_type=DefaultedInstantiatingRegistry):
    """A Minecraft dimension (e.g. Overworld, Nether, The End)."""

    def __init__(self, name: ResLocLike = "overworld"):
        ...

    @classmethod
    @overload
    def set_block(
        cls,
        pos: Vec3iLike,
        block: FlexibleParamOfRegistered[Block],
        *,
        update: bool = True,
        keep: bool = False,
        destroy: bool = False,
        drop_item: bool = False,
        on_tick: bool = True,
        set_state: bool = True,
        set_nbt: bool = True
    ) -> MaybeAwaitable[None]:
        ...

    @overload
    def set_block(
        self,
        pos: Vec3iLike,
        block: FlexibleParamOfRegistered[Block],
        *,
        update: bool = True,
        keep: bool = False,
        destroy: bool = False,
        drop_item: bool = False,
        on_tick: bool = True,
        set_state: bool = True,
        set_nbt: bool = True
    ) -> MaybeAwaitable[None]:
        ...
