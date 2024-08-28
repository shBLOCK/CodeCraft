from codecraft.coro import auto_async
from codecraft.client.client import CCClient
from codecraft.internal.cmd.codecraft import SendSystemChatCmd

_cc = CCClient.current


@auto_async
async def send_chat(message: str) -> None:
    await _cc().run_cmd(SendSystemChatCmd(message))
