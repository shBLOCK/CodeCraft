from __future__ import annotations

from abc import ABC, abstractmethod
from typing import TYPE_CHECKING

from codecraft.internal.resource import ResLoc
from codecraft.internal.registry import TypeRegistry, Registered

if TYPE_CHECKING:
    from codecraft.client import CCClient
    from codecraft.internal import CCByteBuf


class Msg(ABC, Registered["Msg", TypeRegistry], registry_name=ResLoc.codecraft("msg")):
    @abstractmethod
    def __init__(self, buf: CCByteBuf, client: CCClient): ...
