from typing import Any


def dummy() -> Any:
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
    >>>         print(self.bar.split("")) # Unresolved attribute reference 'bar' for class 'Foo'
    """
