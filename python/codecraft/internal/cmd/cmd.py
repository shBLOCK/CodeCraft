from __future__ import annotations

from abc import ABC, abstractmethod
from typing import TYPE_CHECKING

from codecraft.internal.resource import ResLoc
from codecraft.internal.registry import Registered

if TYPE_CHECKING:
    from typing import Optional

    from codecraft.client import CCClient
    from codecraft.internal import CCByteBuf


class Cmd(ABC, Registered, registry_name=ResLoc.codecraft("cmd")):
    def __init__(self):
        # The unique id of this command, determined once before sending to the client (_write).
        self._uid: Optional[int] = None

    # noinspection PyProtectedMember
    @abstractmethod
    def _write(self, buf: CCByteBuf, client: CCClient):
        if self._uid is not None:
            raise ValueError("_write() can only be called once for every Cmd object")
        self._uid = client._next_cmd_uid()
        buf.write_varint(self._uid)
