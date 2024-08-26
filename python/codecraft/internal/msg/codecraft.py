from __future__ import annotations

from typing import override
from typing import TYPE_CHECKING

from codecraft.internal.resource import ResLoc
from .msg import Msg

if TYPE_CHECKING:
    from codecraft.client import CCClient
    from codecraft.internal import CCByteBuf


class CmdResultMsg(Msg, reg_name=ResLoc.codecraft("cmd_result")):
    @override
    def __init__(self, data: CCByteBuf, client: CCClient):
        super().__init__(data, client)
        self.cmd_uid = data.read_varint()
        self.success = data.read_bool()
        if self.success:
            # TODO: custom read by command
            self.results = tuple(data.read_dynamic() for _ in range(data.read_varint()))
            self.error = None
        else:
            self.results = None
            self.error = data.read_str()
