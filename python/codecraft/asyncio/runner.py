from __future__ import annotations

import asyncio
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from collections.abc import Awaitable, Callable

RUNNER = asyncio.Runner()
LOOP = RUNNER.get_loop()

LOOP.set_task_factory(asyncio.eager_task_factory)


def auto_async[** P, R](coro_func: Callable[P, Awaitable[R]]) -> Callable[P, R | Awaitable[R]]:
    """
    A decorator to allow both asyncio and normal (synchronous) use of a coroutine function.
    When called from an `asyncio.Task`, return the coroutine created by `coro_func`.
    When called from a normal (synchronous) context, run `RUNNER.run(coro_func())` and return the result.
    """
    if not asyncio.iscoroutinefunction(coro_func):
        raise TypeError("@auto_async can only be used on coroutine functions")

    def inner(*args: P.args, **kwargs: P.kwargs) -> R | Awaitable[R]:
        if asyncio.current_task(LOOP) is not None:
            return coro_func(*args, **kwargs)
        else:
            # noinspection PyTypeChecker
            return RUNNER.run(coro_func(*args, **kwargs))

    return inner
