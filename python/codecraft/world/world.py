from __future__ import annotations

from typing import final, TYPE_CHECKING

from codecraft.internal.resource import ResLoc

if TYPE_CHECKING:
    from codecraft.internal import ResLocLike


@final
class World:
    """A Minecraft dimension (e.g. Overworld, Nether, The End)."""

    def __init__(self, name: ResLocLike = "overworld"):
        self._name = ResLoc.from_like(name)

    def set_block(self):
        ...

    def __repr__(self):
        return f"World({self._name})"
