from __future__ import annotations

import string
from typing import final, TYPE_CHECKING

from codecraft.internal.constants import MODID

if TYPE_CHECKING:
    from typing import Optional, Self

__all__ = ("ResLoc", "ResLocLike")

_VALID_NAMESPACE_CHARS = {ord(c) for c in "_-/." + string.ascii_lowercase + string.digits}
_VALID_PATH_CHARS = {ord(c) for c in "_-." + string.ascii_lowercase + string.digits}

type ResLocLike = ResLoc | str


@final
class ResLoc:
    __slots__ = "namespace", "path"

    def __init__(self, a: str, b: Optional[str] = None):
        if b is not None:
            self.namespace = a
            self.path = b
        else:
            split = a.split(":")
            if len(split) == 1:
                self.namespace = "minecraft"
                self.path = split[0]
            elif len(split) == 2:
                self.namespace, self.path = split
            else:
                raise ValueError(f"Invalid resource location: \"{a}\"")

        # TODO: maybe regex is be faster?
        valid_namespace_chars = _VALID_NAMESPACE_CHARS
        valid_path_chars = _VALID_PATH_CHARS
        if any(ord(c) not in valid_namespace_chars for c in self.namespace):
            raise ValueError(f"Invalid namespace: \"{self.namespace}\"")
        if any(ord(c) not in valid_path_chars for c in self.path):
            raise ValueError(f"Invalid path: \"{self.path}\"")

    @classmethod
    def from_like(cls, value: ResLocLike) -> Self:
        if isinstance(value, ResLoc):
            return value
        if isinstance(value, str):
            return ResLoc(value)
        else:
            raise TypeError(f"Invalid resource location: {value}")

    @classmethod
    def from_opt_like(cls, value: Optional[ResLocLike]) -> Optional[Self]:
        if value is None:
            return None
        return cls.from_like(value)

    def __eq__(self, other):
        if not isinstance(other, ResLoc):
            return False
        return other.namespace == self.namespace and other.path == self.path

    def __str__(self):
        return f"{self.namespace}:{self.path}"

    def __repr__(self):
        return f"<{self.namespace}:{self.path}>"

    def __hash__(self):
        return hash(self.namespace) ^ hash(self.path)

    @classmethod
    def codecraft(cls, path: str) -> Self:
        return cls(MODID, path)
