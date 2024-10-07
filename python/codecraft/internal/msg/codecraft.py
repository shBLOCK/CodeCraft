from __future__ import annotations

from typing import override
from typing import TYPE_CHECKING

from codecraft.internal.resource import ResLoc
from .msg import Msg
from codecraft.internal.error import NetworkError

if TYPE_CHECKING:
    from typing import Optional, Any

    from codecraft.client import CCClient
    from ..byte_buf.byte_buf import ByteBuf


# noinspection PyProtectedMember
class CmdResultMsg(Msg, reg_name=ResLoc.codecraft("cmd_result")):
    @override
    def __init__(self, buf: ByteBuf, client: CCClient):
        super().__init__(buf, client)
        self.cmd_uid = buf.read_uvarint()
        cmd = client._msg_queue._pop_running_cmd(self.cmd_uid)
        if cmd is None:
            raise NetworkError(f"No command with uid {self.cmd_uid}")

        self.type = buf.read_byte()
        self.result: Optional[Any]

        if self.successful:
            self.result = cmd._parse_result(buf, client)
        else:
            self.result = (buf.read_str(),)

    @property
    def successful(self) -> bool:
        return self.type == 0
