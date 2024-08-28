from __future__ import annotations

import asyncio
import threading
from asyncio import AbstractEventLoop
from typing import TYPE_CHECKING

import websockets.asyncio
from websockets.asyncio.client import ClientConnection
from websockets.frames import CloseCode
from codecraft.internal.typing import dummy_for_ide

if TYPE_CHECKING:
    from collections.abc import Buffer, Awaitable

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
        self._conn: ClientConnection = dummy_for_ide()
        self.loop: AbstractEventLoop = dummy_for_ide()
        self._closed = False

        ready_event = threading.Event()

        def thread_main():
            self.loop = asyncio.new_event_loop()
            asyncio.set_event_loop(self.loop)
            ready_event.set()
            client.logger.debug(f"Networking thread started")
            self.loop.run_forever()
            self.loop.close()

        self._thread = threading.Thread(
            target=thread_main,
            name=f"Networker({client.name})",
            daemon=True
        )

        self._thread.start()
        ready_event.wait()

    def connect(self) -> Awaitable[None]:
        assert not self._closed, "closed"
        async def _connect():
            self._conn = await websockets.asyncio.client.connect(**self._kwargs)
        return self._run(_connect())

    def send(self, message: Buffer | str) -> Awaitable[None]:
        assert not self._closed, "closed"
        return self._run(self._conn.send(message))

    def recv(self) -> Awaitable[bytes | str]:
        assert not self._closed, "closed"
        return self._run(self._conn.recv())

    async def close(self, code=CloseCode.NORMAL_CLOSURE, reason=""):
        """Close the connection and stop the thread, thread safe."""

        assert not self._closed, "closed"
        self._closed = True

        async def stop():
            await self._conn.close(code, reason)
            current = asyncio.current_task()
            for task in asyncio.all_tasks(self.loop):
                if task is not current:
                    task.cancel()

            # wait for tasks to complete (or terminate)
            try:
                async with asyncio.timeout(5):
                    while True:
                        await asyncio.sleep(0)
                        for task in asyncio.all_tasks(self.loop):
                            if task is not current:
                                if not task.done():
                                    break
                        else:
                            break
            except TimeoutError:
                self._client.logger.warning("Some tasks didn't finish in 5 seconds after being cancelled.")

        await self._run(stop())
        self.loop.call_soon_threadsafe(self.loop.stop)
        self._thread.join()  # wait for event loop to terminate

    def _run[T](self, coro: Awaitable[T]) -> Awaitable[T]:
        # TODO: optimize cross-thread coroutine?
        return asyncio.wrap_future(asyncio.run_coroutine_threadsafe(coro, self.loop))
