from __future__ import annotations

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from collections.abc import Callable, Awaitable

from .runner import RUNNER


def async_main(main: Awaitable[None] | Callable[[], Awaitable[None]]):
    RUNNER.run(main)
    RUNNER.close()
