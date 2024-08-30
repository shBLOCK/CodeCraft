from __future__ import annotations

from collections.abc import Callable
from typing import TYPE_CHECKING, Protocol, Concatenate

if TYPE_CHECKING:
    from typing import Optional

    from codecraft.internal.typings import InstOrType


def or_default_instance[T: LazyDefaultInstance](obj_or_type: InstOrType[T]) -> T:
    if isinstance(obj_or_type, type):
        obj_or_type: type[LazyDefaultInstance]
        return obj_or_type.get_default_instance()
    return obj_or_type


class LazyDefaultInstance:  # can't use ABC here: causes metaclass conflicts
    """A utility base class that adds the get_default_instance() class method
    which returns a (lazy-instantiated) instance of the class.

    One use case of this is the `Block` classes.
    In many use cases the default instance is used.
    To avoid unnecessary instantiations one can instead pass
    """

    @classmethod
    def get_default_instance(cls):
        if hasattr(cls, "_def_inst"):
            return cls._def_inst

        inst = cls._def_inst = cls()
        return inst


class LazyDefaultInstanceProto(Protocol):
    @classmethod
    def get_default_instance(cls):
        pass


class DefaultInstanceMethod[T: LazyDefaultInstance, ** P, R]:
    """Decorate a method with this to make it callable from both the class and an instance.
    When called from the class, uses the default instance of the class as the instance.

    To make PyCharm work better with the decorated method, do this:

    >>> from typing import overload
    >>>
    >>> class Foo(LazyDefaultInstance):
    >>>     @classmethod
    >>>     @overload
    >>>     def bar(cls, a: str) -> int: pass
    >>>
    >>>     @overload
    >>>     def bar(self, a: str) -> int: pass
    >>>
    >>>     @DefaultInstanceMethod
    >>>     def bar(self, a: str) -> int:
    >>>         ...
    """

    __slots__ = "_f"

    def __init__(self, f: Callable[Concatenate[T, P], R]):
        self._f = f

    def __get__(self, instance: Optional[T], owner: type[T]) -> Callable[P, R]:
        if instance is None:
            instance = owner.get_default_instance()
        # noinspection PyUnresolvedReferences
        return self._f.__get__(instance, owner)

    def __set__(self, instance, value):
        raise AttributeError("You shouldn't assign to a method")

    def __getattr__(self, item):
        # noinspection PyUnresolvedReferences
        return self._f.__getattr__(item)
