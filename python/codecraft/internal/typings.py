from typing import Any

from spatium import Vec3, Vec3i

type InstOrType[T] = T | type[T]

# TODO add more compatible type after updating updating spatium
# Not including numbers as they are unintuitive in most cases
type Vec3Like = Vec3 | Vec3i
type Vec3iLike = Vec3i | Vec3


def dummy_for_ide() -> Any:
    # noinspection PyUnresolvedReferences
    """A dummy function to workaround PyCharm not \"believing\" the type hints.

    >>> import threading
    >>>
    >>> class Foo:
    >>>     def __init__(self):
    >>>         self.bar: str
    >>>         def thread_main():
    >>>             self.bar = "Hello world!"
    >>>         threading.Thread(target=thread_main).start()
    >>>
    >>>     def foobar(self):
    >>>         print(self.bar.split("")) #  PyCharm: Unresolved attribute reference 'bar' for class 'Foo'
    """
