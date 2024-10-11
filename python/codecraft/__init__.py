from . import log

# TODO: lazy module importing (see: https://github.com/pyglet/pyglet/blob/master/pyglet/__init__.py)

from . import config

log.configure_logger(config.CCConfig.log_level)

# noinspection PyProtectedMember
log._cc_init_begin()

from . import coro
from . import block
from . import client
from . import entity
from . import enums
from . import internal
from . import item
from . import world

from ._root import *

# noinspection PyProtectedMember
log._cc_init_end()
