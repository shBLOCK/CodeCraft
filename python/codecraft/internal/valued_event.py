from __future__ import annotations

import asyncio.mixins
import collections

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from typing import Optional


# TODO: not used, remove?
# noinspection PyUnresolvedReferences,PyProtectedMember
class ValuedEvent[T](asyncio.mixins._LoopBoundMixin):
    """A valued version of asyncio.Event."""

    def __init__(self):
        self._waiters = collections.deque()
        self._value: Optional[T] = None

    def __repr__(self):
        res = super().__repr__()
        extra = 'set' if self._value else 'unset'
        if self._waiters:
            extra = f'{extra}, waiters:{len(self._waiters)}'
        return f'<{res[1:-1]} ({repr(self._value)}) [{extra}]>'

    def is_set(self):
        return self._value is not None

    def set(self, value: T):
        if self._value is None:
            self._value = value

            for fut in self._waiters:
                if not fut.done():
                    fut.set_result(True)

    def clear(self):
        self._value = None

    async def wait(self) -> T:
        if self._value is not None:
            return self._value

        fut = self._get_loop().create_future()
        self._waiters.append(fut)
        try:
            await fut
            return self._value
        finally:
            self._waiters.remove(fut)
