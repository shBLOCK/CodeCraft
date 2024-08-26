from __future__ import annotations

from enum import Enum
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from typing import Self


class SerializableNamedEnum(Enum):
    __by_name: dict[str, Self] = {}

    def __new__(cls, serialization_name: str, *args):
        obj = object.__new__(cls)
        obj._value_ = serialization_name
        # noinspection PyUnresolvedReferences
        cls.__by_name[serialization_name] = obj
        return obj

    @classmethod
    def from_serialization_name(cls, name: str) -> Self:
        try:
            # noinspection PyUnresolvedReferences
            return cls.__by_name[name]
        except KeyError:
            raise KeyError(f"Invalid serialization name {name}")

    def __repr__(self):
        return f"<{self.name}>"
