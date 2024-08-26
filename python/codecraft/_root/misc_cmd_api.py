from codecraft.client.client import CCClient
from codecraft.internal.cmd.codecraft import SendSystemChatCmd

_cc = CCClient.current


def send_chat(message: str) -> None:
    _cc().send_cmd(SendSystemChatCmd(message))
