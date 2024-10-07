from __future__ import annotations

from abc import ABC
from typing import TYPE_CHECKING

from codecraft.internal.resource import ResLoc
from codecraft.internal.registry import Registered, TypeRegistry

if TYPE_CHECKING:
    from typing import Optional, Any

    from codecraft.client import CCClient
    from codecraft.internal import CCByteBuf


class Cmd(ABC, Registered["Cmd", TypeRegistry], registry_name=ResLoc.codecraft("cmd")):
    def __init__(self):
        # The unique id of this command, determined once before sending to the client (_write).
        self._uid: Optional[int] = None

    # noinspection PyProtectedMember
    def _write(self, buf: CCByteBuf, client: CCClient):
        if self._uid is not None:
            raise ValueError("_write() can only be called once for every Cmd object")
        self._uid = client._next_cmd_uid()
        buf.write_uvarint(self._uid)

    # noinspection PyMethodMayBeStatic
    def _parse_result(self, buf: CCByteBuf, client: CCClient) -> Any:
        """Parse the result of the command.

        Note that it's possible that this might be called from the networking thread.
        """
        return None
