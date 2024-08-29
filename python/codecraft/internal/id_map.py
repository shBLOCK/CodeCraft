from __future__ import annotations

from operator import itemgetter
from typing import overload, override, TYPE_CHECKING

from codecraft.internal.resource import ResLoc
from codecraft.internal.registry import Registry, Registered

if TYPE_CHECKING:
    from collections.abc import Mapping, Hashable, MutableMapping, Sequence
    from typing import Self, Optional

    from codecraft.internal import ResLocLike
    from codecraft.internal.typings import InstOrType


class IdMap[T: Hashable]:
    def __init__(self, initial: Mapping[int, T] = None, *, freeze: bool = False):
        self.__frozen = False
        self.__contiguous = False

        self._from_id: MutableMapping[int, T] | Mapping[int, T] | Sequence[T] = {}
        self._to_id: MutableMapping[T, int] | Mapping[T, int] = {}

        if initial is not None:
            for item in initial.items():
                self.put(*item)

        if freeze:
            self.freeze()

    def __len__(self):
        return len(self._from_id)

    @overload
    def __getitem__(self, key: int) -> T:
        ...
    @overload
    def __getitem__(self, key: T) -> int:
        ...

    def __getitem__(self, key):
        if isinstance(key, int):
            return self._from_id[key]
        else:
            return self._to_id[key]

    def put(self, id: int, obj: T):
        if self.__frozen:
            raise ValueError("Already frozen")
        if id in self._from_id:
            raise KeyError(f"Id {id} is taken")
        self._from_id[id] = obj
        self._to_id[obj] = id

    def clear(self):
        if self.__frozen:
            raise ValueError("Frozen")
        self._from_id.clear()
        self._to_id.clear()

    @property
    def frozen(self) -> bool:
        return self.__frozen

    def freeze(self) -> Self:
        if self.__frozen:
            return self
        self.__frozen = True

        if len(self._from_id) == 0:
            return self

        items = sorted((item for item in self._from_id.items()), key=itemgetter(0))
        self.__contiguous = items[0][0] == 0 and items[-1][0] == len(items) - 1

        if self.__contiguous:
            self._from_id = tuple(map(itemgetter(1), items))

        return self

    def __iter__(self):
        return iter(self._from_id)


class RegistryIdMap[T, R](IdMap[ResLoc]):
    def __init__(self, registry: ResLocLike | Registry[T, R], *args, **kwargs):
        super().__init__(*args, **kwargs)

        if isinstance(registry, Registry):
            self._name = registry.name
            self._registry = registry
        else:
            self._name = ResLoc.from_like(registry)
            self._registry = None

    @property
    def name(self) -> ResLoc:
        return self._name

    @property
    def registry(self) -> Optional[Registry[T, R]]:
        return self._registry

    def to_json_dict(self) -> dict[str, int]:
        return {str(k): v for k, v in self._to_id.items()}

    @overload
    def __getitem__(self, key: int) -> R | ResLoc:
        ...
    @overload
    def __getitem__(self, key: InstOrType[T] | ResLoc) -> int:
        ...

    @override
    def __getitem__(self, key) -> R | ResLoc | int:
        if isinstance(key, int):
            name = super().__getitem__(key)
            return self._registry[name] if self._registry is not None else name
        elif isinstance(key, ResLoc):
            return super().__getitem__(key)
        elif issubclass(key, Registered) or isinstance(key, Registered):
            return super().__getitem__(key.reg_name)
        else:
            raise TypeError(key)

    def from_json_dict(self, json_dict: dict[str, int], *, freeze: bool = True):
        self.clear()
        for k, v in json_dict.items():
            self.put(v, ResLoc(k))
        if freeze:
            self.freeze()

    # def apply_registry[T](self, registry: Registry[T]) -> IdMap[type[T]]:
    #     return IdMap({i: registry[self[i]] for i in self if self[i] in registry}, frozen=self.frozen)
