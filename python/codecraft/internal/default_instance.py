from abc import ABC
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from codecraft.internal.typing import InstOrType


def or_default_instance[T: LazyDefaultInstance](obj_or_type: InstOrType[T]) -> T:
    if isinstance(obj_or_type, type):
        obj_or_type: type[LazyDefaultInstance]
        return obj_or_type.get_default_instance()
    return obj_or_type


class LazyDefaultInstance(ABC):
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
