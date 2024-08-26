from __future__ import annotations

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from typing import Iterable, Any


# TODO: not used, remove?
def add_to_slots(dic: dict[str, Any], items: Iterable[str]):
    slots = []
    org = dic.get("__slots__")
    if isinstance(org, str):
        slots.append(org)
    elif isinstance(org, Iterable):
        slots.extend(org)

    slots.extend(items)

    dic["__slots__"] = slots
