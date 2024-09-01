from collections.abc import Awaitable

from codecraft.coro import auto_async
from codecraft.client.client import _cc
from codecraft.internal.cmd.codecraft import SendSystemChatCmd


@auto_async
def send_chat(message: str) -> Awaitable[None]:
    return _cc().run_cmd(SendSystemChatCmd(message))
