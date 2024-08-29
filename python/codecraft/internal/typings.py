from typing import Any

type InstOrType[T] = T | type[T]


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
