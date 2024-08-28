from __future__ import annotations

from typing import override
from typing import TYPE_CHECKING

from codecraft.internal.resource import ResLoc
from .msg import Msg
from codecraft.internal.error import NetworkError

if TYPE_CHECKING:
    from typing import Optional, Any

    from codecraft.client import CCClient
    from codecraft.internal import CCByteBuf


# noinspection PyProtectedMember
class CmdResultMsg(Msg, reg_name=ResLoc.codecraft("cmd_result")):
    @override
    def __init__(self, buf: CCByteBuf, client: CCClient):
        super().__init__(buf, client)
        self.cmd_uid = buf.read_varint()
        cmd = client._msg_queue._get_running_cmd(self.cmd_uid)
        if cmd is None:
            raise NetworkError(f"No command with uid {self.cmd_uid}")

        self.success = buf.read_bool()
        self.result: Optional[Any]
        self.error: Optional[str]
        if self.success:
            self.result = cmd._parse_result(buf, client)
            self.error = None
        else:
            self.result = None
            self.error = buf.read_str()
