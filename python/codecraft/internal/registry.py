from __future__ import annotations

from abc import ABC
from typing import TYPE_CHECKING, final, override

from codecraft.internal.resource import ResLoc
from codecraft.log.log import LOGGER

if TYPE_CHECKING:
    from typing import Self, ClassVar, Any

    from codecraft.internal import ResLocLike

REG_LOGGER = LOGGER.getChild("Registry")


# noinspection PyProtectedMember,PyUnresolvedReferences
class Registered[T: Registered]:
    reg_name: ResLoc  # can be overridden by instance attribute
    registry: ClassVar[Registry]

    def __init_subclass__(cls, reg_name: ResLocLike = None, registry_name: ResLocLike = None,
                          registry_type: type[Registry[T, Any]] = None, **kwargs):
        super().__init_subclass__(**kwargs)

        reg_name = ResLoc.from_opt_like(reg_name)
        registry_name = ResLoc.from_opt_like(registry_name)

        if reg_name is not None:
            cls.reg_name = reg_name
            REG_LOGGER.debug(f"Auto-registering to <%s>: <%s> (%s)", cls.registry.name, reg_name, cls.__name__)
            cls.registry._put(reg_name, cls)

        # is direct subclass
        if cls in Registered.__subclasses__():
            if registry_name is None:
                raise ValueError("Registry name not provided")

            if hasattr(cls, "registry"):
                raise TypeError(
                    "This class already has a \"registry\" attribute, "
                    "most likely because multiple class in the inheritance tree subclassed \"Registered\"")

            if registry_type is None:
                registry_type = TypeRegistry

            cls.registry = registry_type(cls, registry_name)


class Registry[T: Registered, R](ABC):
    def __init__(self, base_type: type[T], name: ResLocLike):
        self._base_type = base_type
        self._name = ResLoc.from_like(name)
        self.__map: dict[ResLoc, type[T]] = {}

    @property
    def name(self) -> ResLoc:
        return self._name

    def _put(self, key: ResLocLike, obj: type[T]) -> Self:
        key = ResLoc.from_like(key)
        if key in self.__map:
            raise KeyError(f"The registry key {repr(key)} is taken")
        self.__map[key] = obj
        return self

    def __len__(self):
        return len(self.__map)

    def get[D](self, key: ResLocLike, default: D = None) -> R | D:
        key = ResLoc.from_like(key)
        result = self.__map.get(key)
        return result if result is not None else default

    @final
    def __getitem__(self, key: ResLocLike) -> R:
        result = self.get(key)
        if result is None:
            raise KeyError(f"No entry of key {repr(key)}")
        return result

    def __iter__(self):
        return iter(self.__map)

    def __repr__(self):
        return f"Registry({self._base_type.__name__}, {repr(self._name)})"

    def __contains__(self, item):
        return item in self.__map


class TypeRegistry[T: Registered](Registry[T, type[T]]):
    pass


class InstantiatingRegistry[T: Registered](Registry[T, T]):
    """A registry that returns instantiated objects of entries."""

    @override
    def get[D](self, key: ResLocLike, default: D = None) -> T | D:
        tp = super().get(key)
        return tp() if tp is not None else default


class DefaultedInstantiatingRegistry[T: Registered](InstantiatingRegistry[T]):
    """A registry that returns instantiated objects of entries;
    if no type has been registered for `reg_name`, return obj using `obj = T(); obj.reg_name = reg_name` (`T` is the type parameter and also the _base_type of this registry)."""

    @override
    def get[D](self, key: ResLocLike, _default: D = None) -> T | D:
        obj = super().get(key)
        if obj is not None:
            return obj

        obj = self._base_type()
        obj.reg_name = key
        return obj
