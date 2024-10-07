from itertools import count
from typing import Any

import sys

from array import array

_IS_LE = sys.byteorder == "little"


def _array_adapt_byteorder(arr: array[Any]):
    if not _IS_LE:
        arr.byteswap()
