from __future__ import annotations

import asyncio
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from typing import Optional, Any
    from collections.abc import Awaitable, Callable, Coroutine
    from asyncio import AbstractEventLoop

_RUNNER = asyncio.Runner()

type MaybeAwaitable[T] = T | Awaitable[T]


def auto_async[** P, R](coro_func: Callable[P, Coroutine[Any, Any, R]]) -> Callable[P, MaybeAwaitable[R]]:
    """A decorator to allow both asyncio and normal (synchronous) use of a coroutine function.

    When called from a running event loop, return the coroutine created by `coro_func`.
    When called from a normal (synchronous) context, run `_RUNNER.run(coro_func())` and return the result.
    """
    if not asyncio.iscoroutinefunction(coro_func):
        raise TypeError("@auto_async can only be used on coroutine functions")

    def inner(*args: P.args, **kwargs: P.kwargs) -> MaybeAwaitable[R]:
        # noinspection PyUnresolvedReferences,PyProtectedMember
        loop: Optional[AbstractEventLoop] = asyncio._get_running_loop()
        if loop is not None:
            return coro_func(*args, **kwargs)
        else:
            return _RUNNER.run(coro_func(*args, **kwargs))

    return inner


def set_task_name(name: str):
    asyncio.current_task().set_name(name)
