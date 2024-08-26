from __future__ import annotations

import itertools
from abc import ABC, abstractmethod
from typing import TYPE_CHECKING, final, overload

from codecraft.internal.resource import ResLoc
from codecraft.internal.registry import Registered, DefaultedFactoryRegistry

if TYPE_CHECKING:
    from collections.abc import Collection
    from typing import Any, Iterable, Optional

    from codecraft.enum.bases import SerializableNamedEnum
    from codecraft.internal import ResLocLike

__all__ = (
    "Block",
    "BlockStateProperty",
    "BooleanProperty",
    "IntegerProperty",
    "EnumProperty"
)


# noinspection PyProtectedMember
class BlockStateProperty[T](ABC):
    def __init__(self, default: Optional[T] = None):
        self._default = default
        self._class: type[Block]
        self._name: str

    @final
    def __set_name__(self, owner, name):
        if not issubclass(owner, Block):
            raise TypeError(f"{self.__class__.__name__} can only be used in {Block.__name__} classes")
        self._class = owner
        self._name = name

    def get(self, instance: Block, use_default: bool = True) -> Optional[T]:
        value = instance._states.get(self._name)
        if value is None:
            if use_default and self._default is not None:
                value = self._default
            else:
                raise AttributeError(f"Property {self} has no default value and has not been set for {instance}")
        return value

    def __get__(self, instance: Block, _owner):
        return self.get(instance)

    def set(self, instance: Block, value: T):
        instance._states[self._name] = value

    def __set__(self, instance: Block, value: T):
        self.set(instance, value)

    @final
    @property
    def name(self) -> str:
        return self._name

    @property
    @abstractmethod
    def possible_values(self) -> Collection[T]:
        pass

    @abstractmethod
    def serialize(self, value: T) -> str:
        pass

    @abstractmethod
    def deserialize(self, data: str) -> T:
        pass

    def __repr__(self):
        return f"{self.__class__.__name__}({self._class}.\"{self.name}\", [{", ".join(map(repr, self.possible_values))}])"


# noinspection PyProtectedMember
class BlockMeta(type):
    def __new__(cls, name: str, bases: tuple[type, ...], dic: dict[str, Any], **kwargs):
        properties = {}
        if "Block" in globals():
            for base in bases:
                if issubclass(base, Block):
                    # add properties from inherited (abstract) block classes
                    properties.update(base._properties)

        properties.update({k: v for k, v in dic.items() if isinstance(v, BlockStateProperty)})

        dic["_properties"] = properties
        # add_to_slots(dic, map(BlockStateProperty.to_internal_name, properties.keys()))
        return type.__new__(cls, name, bases, dic, **kwargs)


class Block(Registered, metaclass=BlockMeta, registry_name="block", registry_type=DefaultedFactoryRegistry):
    __slots__ = "reg_name", "_states", "_extra_properties"

    _properties: dict[str, BlockStateProperty[Any]]

    def __init__(self, reg_name: Optional[ResLocLike] = None, **extra_properties: BlockStateProperty[Any]):
        if reg_name is not None:
            if hasattr(type(self), "reg_name"):
                raise AttributeError(f"Register name is already defined for {self}")
            self.reg_name = ResLoc.from_like(reg_name)

        self._states: dict[str, Any] = {}
        self._extra_properties: Optional[dict[str, BlockStateProperty[Any]]] = extra_properties

    def _get_property(self, name: str) -> BlockStateProperty[Any]:
        prop = self._properties.get(name)
        if prop is None:
            prop = self._extra_properties.get(name)
            if prop is None:
                raise KeyError(name)
        return prop

    def _all_properties(self) -> Iterable[str]:
        return itertools.chain(self._properties, self._extra_properties)

    @final
    def __getitem__[T](self, name: str) -> T:
        return self._get_property(name).get(self)

    @final
    def __setitem__[T](self, name: str, value: T | BlockStateProperty[T]):
        if isinstance(value, BlockStateProperty):
            self._extra_properties[name] = value
        else:
            self._get_property(name).set(self, value)

    def __repr__(self):
        return f"Block(\"{self.reg_name}\")"  # TODO properties: eg. Block("minecraft:abc", p1=true, p2=1, p3=north)


class BooleanProperty(BlockStateProperty[bool]):
    @property
    def possible_values(self) -> Collection[bool]:
        return False, True

    def serialize(self, value: bool) -> str:
        return "true" if value else "false"

    def deserialize(self, data: str) -> bool:
        match data:
            case "false":
                return False
            case "true":
                return True
            case _:
                raise ValueError(f"Invalid string \"{data}\" for {self}")


class IntegerProperty(BlockStateProperty[int]):
    @overload
    def __init__(self, possible_values: Iterable[int]):
        pass
    @overload
    def __init__(self, value_min: int, value_max: int):
        pass

    def __init__(self, a, b=None):
        super().__init__()

        self._possible: set[int]
        if isinstance(a, int) and isinstance(b, int):
            if a < 0 or a > b:
                raise ValueError(f"Invalid range: [{a}, {b}]")
            self._possible = set(range(a, b + 1))
        elif isinstance(a, Iterable) and b is None:
            self._possible = set(a)
        else:
            raise TypeError("Invalid arguments")

    @property
    def possible_values(self) -> Collection[int]:
        return self._possible

    def serialize(self, value: int) -> str:
        return str(value)

    def deserialize(self, data: str) -> int:
        if not data.isdigit():
            raise ValueError(f"Invalid string \"{data}\" for {self}")

        try:
            value = int(data)
        except ValueError:
            raise ValueError(f"Invalid string \"{data}\" for {self}")

        if value not in self.possible_values:
            raise ValueError(f"{value} is not a possible value for {self}")

        return value


class EnumProperty[T: SerializableNamedEnum](BlockStateProperty[T]):
    @overload
    def __init__(self, enum: type[T]):
        pass
    @overload
    def __init__(self, *args: T):
        pass

    def __init__(self, *args):
        super().__init__()

        if not args:
            raise ValueError("No arguments")

        self._enum: type[T]
        self._possible: set[T]
        if len(args) == 1 and issubclass(args[0], SerializableNamedEnum):
            self._enum = args[0]
            self._possible = set(iter(self._enum))
        elif all(isinstance(a, SerializableNamedEnum) for a in args):
            self._enum = type(args[0])
            if not all(isinstance(a, self._enum) for a in args):
                raise ValueError("All values must be entries of the same enum")
            self._possible = set(args)
        else:
            raise TypeError("Invalid arguments.")

    @property
    def possible_values(self) -> Collection[T]:
        return self._possible

    def serialize(self, value: T) -> str:
        # noinspection PyTypeChecker
        return value.value

    def deserialize(self, data: str) -> T:
        value = self._enum.from_serialization_name(data)
        if value not in self.possible_values:
            raise ValueError(f"{value} is not a possible value for {self}.")
        return value

    def __repr__(self):
        return f"{self._enum.__name__}{super().__repr__()}"
