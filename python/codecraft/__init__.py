from . import logging

# TODO: lazy module importing (see: https://github.com/pyglet/pyglet/blob/master/pyglet/__init__.py)

logging.configure_logger("DEBUG")  # TODO: make logging configurable

# noinspection PyProtectedMember
logging._cc_init_begin()

from . import asyncio
from . import block
from . import client
from . import entity
from . import enum
from . import internal
from . import item
from . import world

from ._root import *

# noinspection PyProtectedMember
logging._cc_init_end()
