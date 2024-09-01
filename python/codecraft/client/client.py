from __future__ import annotations

import asyncio
from collections.abc import Awaitable

import time
from typing import final, TYPE_CHECKING

from websockets import ConnectionClosed
from websockets.frames import CloseCode, CLOSE_CODE_EXPLANATIONS

from .id_maps import RegistryIdMaps
from .msg_queue import MsgQueue
from .connection import Connection
from .cmd_runner import SimpleCmdRunner, CmdRunner, BatchingCmdRunner
from codecraft.log.log import LOGGER
from codecraft.internal.byte_buf import CCByteBuf
from codecraft.coro import auto_async
from codecraft.coro import set_task_name
from codecraft.internal.error import NetworkError, CmdError

if TYPE_CHECKING:
    from typing import Optional, Self, Any
    from logging import Logger

    from codecraft.internal.cmd import Cmd
    from codecraft.internal.msg import CmdResultMsg

import threading


# noinspection PyProtectedMember
@final
class CCClient:
    def __init__(self, uri: str, name: str = None, *, establish: bool = True):
        if CCClient.__current is None:  # first instance becomes the default current client
            CCClient.__current = self

        self._uri = uri
        self._name = name if name is not None else uri
        self._logger = LOGGER.getChild(f"Client({self._name})")
        self._conn: Connection = Connection(
            self,
            uri=uri,
            open_timeout=10,
            ping_interval=5,
            close_timeout=5,
            ping_timeout=None
        )

        self._connected = False
        self._established = False

        self._id_maps: RegistryIdMaps
        self._msg_queue = MsgQueue(self)

        self.__cmd_uid = -1

        # also set by BatchingCmdRunner
        self._cmd_runner: CmdRunner = SimpleCmdRunner(self)

        if establish:
            self.establish()

        self._lifecycle_lock = threading.Lock()

    def _next_cmd_uid(self) -> int:
        self.__cmd_uid += 1
        return self.__cmd_uid

    @property
    def msg_queue(self) -> MsgQueue:
        return self._msg_queue

    async def _establish_sync_registry_id_map(self):
        hash = (await self.recv_raw()).read_bytes()
        self._logger.debug(f"Server registry id maps hash: {hash.hex().upper()}")
        # self._id_maps = RegistryIdMaps.load_cache(hash.hex().upper(), self) # TODO: configurable
        self._id_maps = None
        if self._id_maps is not None:  # cached
            self._logger.debug("Loaded registry id map from cache")
            await self.send_raw(CCByteBuf().write_bool(True))
        else:  # not cached
            await self.send_raw(CCByteBuf().write_bool(False))
            sync_packets = await self.recv_raw()
            self._id_maps = RegistryIdMaps(self)
            while sync_packets.remaining:
                self._id_maps.read_sync_packet(sync_packets)
            self._logger.debug("Received registry id maps")
            self._id_maps.save_cache(hash.hex().upper())

    @property
    def reg_id_maps(self) -> RegistryIdMaps:
        return self._id_maps

    @auto_async
    async def establish(self):
        set_task_name("Establishing")

        import atexit

        if self.established:
            raise ValueError("Already established")

        self._logger.info("Establishing...")
        t = time.perf_counter()
        try:
            await self._conn.connect()
        except Exception as e:
            self._logger.info(f"Failed to connect to CodeCraft server at <{self._uri}>: %s", e)
            raise NetworkError(f"Failed to connect to CodeCraft server at <{self._uri}>: {e}") from e

        self._connected = True
        self._logger.debug("Connected")

        atexit.register(self.close, "Script exited", CloseCode.GOING_AWAY)

        await self._establish_sync_registry_id_map()

        await self.send_raw(CCByteBuf().write_bool(True))
        self._established = True

        asyncio.run_coroutine_threadsafe(self._msg_queue._start(), self._conn.loop).result()

        self._logger.info(f"Established in {(time.perf_counter() - t) * 1e3:.0f}ms")

    @property
    def established(self) -> bool:
        return self._established

    def ensure_established(self):
        if not self._established:
            raise NetworkError("Not established")

    @auto_async
    async def close(self, reason: str = "", code: int = CloseCode.NORMAL_CLOSURE):
        """Close the client if it is active, thread safe."""

        with self._lifecycle_lock:
            connected, established = self._connected, self._established
            self._connected = self._established = False

        if connected:
            if established:
                self._msg_queue._stop()

            if code != CloseCode.ABNORMAL_CLOSURE:  # signals that the connection has already been closed
                self._logger.info("Client closing: %d (%s) %s", code, CLOSE_CODE_EXPLANATIONS[code], reason)
            else:
                self._logger.info("Client closing")
            # we run this even if the network connection has already been closed to terminate the networking thread, etc.
            await self._conn.close(reason=reason, code=code)
            self._logger.info("Client closed")

    def __del__(self):
        # calling event loop when python is shutting down causes problems
        # (Error on reading from the event loop self pipe)
        if threading.current_thread().is_alive():
            self.close("CCClient object destructing", CloseCode.GOING_AWAY)

    async def send_raw(self, buf: CCByteBuf) -> Self:
        try:
            await self._conn.send(buf.written_view)
        except ConnectionClosed as e:
            self.close(code=CloseCode.ABNORMAL_CLOSURE)
            raise NetworkError(f"Connection closed: {str(e)}") from e
        return self

    async def recv_raw(self) -> CCByteBuf:
        try:
            frame = await self._conn.recv()
        except ConnectionClosed as e:
            _ = asyncio.create_task(self.close(code=CloseCode.ABNORMAL_CLOSURE))
            raise NetworkError(f"Connection closed: {str(e)}") from e

        if isinstance(frame, str):
            _ = asyncio.create_task(self.close("Received invalid string frame", CloseCode.POLICY_VIOLATION))
            raise NetworkError(f"Invalid frame format: string")

        return CCByteBuf(frame, client=self)

    def run_cmd(self, cmd: Cmd):
        self.ensure_established()
        return self._run_cmd_coro(self._cmd_runner._run_cmd(cmd))

    # noinspection PyMethodMayBeStatic
    async def _run_cmd_coro(self, waiter: Awaitable[CmdResultMsg]) -> Optional[Any]:
        msg = await waiter

        if msg.success:
            return msg.result
        else:
            raise CmdError(msg.error)

    def batch_cmd(self):
        self.ensure_established()
        return BatchingCmdRunner(self)

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


_cc = CCClient.current  # internal helper alias
