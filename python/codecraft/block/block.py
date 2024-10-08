from __future__ import annotations

import itertools
from typing import TYPE_CHECKING, final

from .properties import BlockStateProperty
from codecraft.internal.default_instance import LazyDefaultInstance
from codecraft.internal.resource import ResLoc
from codecraft.internal.registry import Registered, DefaultedInstantiatingRegistry

if TYPE_CHECKING:
    from typing import Any, Iterable, Optional
    from collections.abc import Collection

    from codecraft.internal import ResLocLike


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


# TODO: block entity NBT
class Block(Registered["Block", DefaultedInstantiatingRegistry["Block"]],
            LazyDefaultInstance,
            metaclass=BlockMeta,
            registry_name="block",
            registry_type=DefaultedInstantiatingRegistry):
    __slots__ = "reg_name", "_states", "_extra_properties"

    _properties: dict[str, BlockStateProperty[Any]]

    def __init__(self, reg_name: Optional[ResLocLike] = None, **extra_properties: BlockStateProperty[Any]):
        if reg_name is not None:
            if hasattr(self, "reg_name"):
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

    def _num_properties(self) -> int:
        return len(self._properties) + len(self._extra_properties)

    def _all_properties(self) -> Iterable[str]:
        return itertools.chain(self._properties, self._extra_properties)

    def _assigned_properties(self) -> Collection[str]:
        return self._states.keys()  # TODO: also include all of `self._extra_properties`

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
        prop_texts = []
        for prop in self._assigned_properties():
            prop_texts.append(f", {prop}={self[prop]}")
        return f"Block(\"{self.reg_name}\"{"".join(prop_texts)})"
