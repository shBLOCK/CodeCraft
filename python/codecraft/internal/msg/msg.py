from __future__ import annotations

from abc import ABC, abstractmethod
from typing import TYPE_CHECKING

from codecraft.internal.resource import ResLoc
from codecraft.internal.registry import Registered

if TYPE_CHECKING:
    from codecraft.client import CCClient
    from codecraft.internal import CCByteBuf


class Msg(ABC, Registered, registry_name=ResLoc.codecraft("msg")):
    @abstractmethod
    def __init__(self, data: CCByteBuf, client: CCClient): ...
