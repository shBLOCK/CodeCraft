from __future__ import annotations

import asyncio
from typing import final, TYPE_CHECKING
from contextlib import asynccontextmanager

import websockets
from websockets import ConnectionClosed
from websockets.frames import CloseCode

from .id_maps import RegistryIdMaps
from .msg_queue import MsgQueue
from codecraft.logging.logging import LOGGER
from codecraft.internal.byte_buf import CCByteBuf
from codecraft.asyncio.runner import RUNNER
from codecraft.asyncio.misc import set_task_name

if TYPE_CHECKING:
    from asyncio import CancelledError, Task
    from collections.abc import Awaitable
    from typing import Optional, Self
    from logging import Logger

    from websockets import WebSocketClientProtocol

    from codecraft.internal.cmd import Cmd
    from codecraft.internal.msg import CmdResultMsg


# noinspection PyProtectedMember
@final
class CCClient:
    def __init__(self, uri: str, name: str = None, *, establish: bool = True):
        if CCClient.__current is None:  # first instance becomes the default current client
            CCClient.__current = self

        self._uri = uri
        self._name = name if name is not None else uri
        self._logger = LOGGER.getChild(f"Client({self.name})")
        self._conn: Optional[WebSocketClientProtocol] = None

        self._established = False

        self._id_maps: RegistryIdMaps
        self._msg_queue = MsgQueue(self)

        self.__cmd_uid = -1

        self._batching = False
        self._batch_result_waiters: list[Task[CmdResultMsg]] = []
        self._send_buffer = CCByteBuf(client=self)

        if establish:
            self.establish()

    def _next_cmd_uid(self) -> int:
        self.__cmd_uid += 1
        return self.__cmd_uid

    async def _establish_sync_registry_id_map(self):
        hash = (await self.recv_raw()).read_bytes()
        self.logger.debug(f"Registry id maps hash: {hash.hex().upper()}")
        self._id_maps = RegistryIdMaps.load_cache(hash.hex().upper(), self)
        if self._id_maps is not None:  # cached
            self.logger.debug("Loaded registry id map from cache")
            await self.send_raw(CCByteBuf().write_bool(True))
        else:  # not cached
            await self.send_raw(CCByteBuf().write_bool(False))
            sync_packets = await self.recv_raw()
            self._id_maps = RegistryIdMaps(self)
            while sync_packets.remaining:
                self._id_maps.read_sync_packet(sync_packets)
            self.logger.debug("Received registry id map")
            self._id_maps.save_cache(hash.hex().upper())

    @property
    def reg_id_maps(self) -> RegistryIdMaps:
        return self._id_maps

    def establish(self):
        async def establish():
            set_task_name("Establishing")

            import atexit

            if self.established:
                raise ValueError("Already established")

            self.logger.debug("Establishing...")
            try:
                self._conn = await websockets.connect(
                    uri=self._uri,
                    open_timeout=10,
                    ping_interval=5,
                    ping_timeout=5,
                    close_timeout=5
                ).__aenter__()
            except Exception as e:
                self.logger.error(f"Failed to connect to CodeCraft server at <{self._uri}>: %s", e)
                return

            self.logger.debug("Connected")

            atexit.register(self.close, "Script exited", CloseCode.GOING_AWAY)

            await self._establish_sync_registry_id_map()

            await self.send_raw(CCByteBuf().write_bool(True))

            self._established = True

            self._msg_queue._start()

            self.logger.debug("Established")

        RUNNER.run(establish())

    @property
    def established(self) -> bool:
        return self._established

    def ensure_established(self):
        if not self._established:
            raise CCClient.NetworkError("Not established")

    def close(self, reason: str = "No message", code: int = CloseCode.NORMAL_CLOSURE):
        if self._established:
            self._established = False
            asyncio.run(self._conn.close(reason=reason, code=code))

    def __del__(self):
        self.close("CCClient object destructing", CloseCode.GOING_AWAY)

    async def send_raw(self, buf: CCByteBuf) -> Self:
        try:
            # noinspection PyTypeChecker
            await self._conn.send(buf.raw_view)
        except ConnectionClosed as e:
            self.close(str(e), CloseCode.ABNORMAL_CLOSURE)
            raise CCClient.NetworkError(f"Connection closed: {str(e)}")
        return self

    async def recv_raw(self) -> CCByteBuf:
        try:
            frame = await self._conn.recv()
        except ConnectionClosed as e:
            self.close(str(e), CloseCode.ABNORMAL_CLOSURE)
            raise CCClient.NetworkError(f"Connection closed: {str(e)}.")

        if isinstance(frame, str):
            self.close("Received invalid frame", CloseCode.POLICY_VIOLATION)
            raise CCClient.NetworkError(f"Invalid frame format")

        return CCByteBuf(frame, client=self)

    async def send_cmd(self, cmd: Cmd) -> Awaitable[Awaitable[CmdResultMsg]]:
        self.ensure_established()
        cmd._write(self._send_buffer, self)
        batching = self._batching
        if not batching:
            await self._end_batch_cmd()
        waiter = asyncio.create_task(self._msg_queue._wait_for_result(cmd))
        if batching:
            self._batch_result_waiters.append(waiter)
        return waiter

    async def _end_batch_cmd(self):
        # noinspection PyTypeChecker
        await self._conn.send(self._send_buffer.raw_view)
        self._send_buffer.clear()
        self._batch_result_waiters.clear()

    @asynccontextmanager
    async def batch_cmd(self):
        self.ensure_established()
        self._batching = True
        try:
            yield
            await self._end_batch_cmd()
        except CancelledError as e:
            for waiter in self._batch_result_waiters:
                waiter.cancel()
            raise e
        finally:
            self._batching = False

    __current: Optional[CCClient] = None

    @classmethod
    def current(cls):
        if cls.__current is None:
            raise ValueError("No active client!")
        return cls.__current

    def __enter__(self):
        self._last_current = CCClient.__current
        CCClient.__current = self

    def __exit__(self, exc_type, exc_val, exc_tb):
        CCClient.__current = self._last_current

    def __repr__(self):
        return f"CCClient(\"{self.name}\")"

    @property
    def name(self) -> str:
        return self._name

    @property
    def logger(self) -> Logger:
        return self._logger

    class NetworkError(IOError):
        """Represents networking errors or other internal communication errors."""
        pass
