from __future__ import annotations

import asyncio
import threading
from asyncio import AbstractEventLoop
from typing import TYPE_CHECKING

import websockets.asyncio
from websockets.asyncio.client import ClientConnection
from websockets.frames import CloseCode

from codecraft.internal.typing import dummy
from codecraft.logging import LOGGER

if TYPE_CHECKING:
    from typing import Any
    from collections.abc import Buffer, Coroutine

    from codecraft.client import CCClient


# TODO: uvloop?

class Connection:
    """A stand-alone thread for networking.

    This makes supporting synchronous (blocking) use of codecraft easier.
    It also allows for a more flexible use of asyncio for the application code,
    for example, the application can easily use a custom event loop implementation
    or start/stop event loops at any time while not influencing networking.
    (This is a problem because the `websockets` library doesn't support cross event loop usage.)
    """

    def __init__(self, client: CCClient, **kwargs):
        self._client = client
        self._kwargs = kwargs
        self._conn: ClientConnection = dummy()
        self._loop: AbstractEventLoop = dummy()

        ready_event = threading.Event()

        def thread_main():
            self._loop = asyncio.new_event_loop()
            asyncio.set_event_loop(self._loop)
            ready_event.set()
            self._loop.run_forever()

        self._thread = threading.Thread(
            target=thread_main,
            name=f"Networker(client.name)",
            daemon=True
        )

        self._thread.start()
        ready_event.wait()
        LOGGER.debug(f"Networking thread {self._thread.name} started")

    async def connect(self) -> None:
        async def _connect():
            self._conn = await websockets.asyncio.client.connect(**self._kwargs)
        await self._run(_connect())

    async def send(self, message: Buffer | str) -> None:
        await self._run(self._conn.send(message))

    async def recv(self) -> bytes | str:
        return await self._run(self._conn.recv())

    async def close(self, code=CloseCode.NORMAL_CLOSURE, reason="") -> None:
        await self._run(self._conn.close(code, reason))

    async def _run[T](self, coro: Coroutine[Any, Any, T]) -> T:
        # TODO: optimize cross-thread coroutine?
        return await asyncio.wrap_future(asyncio.run_coroutine_threadsafe(coro, self._loop))
