import logging
import sys
from typing import override

import colorama
from numpy.ma.extras import stack

LOGGER = logging.getLogger("CodeCraft")


class ColoramaFormatter(logging.Formatter):
    DEFAULT = {
        logging.DEBUG: colorama.Fore.CYAN,
        logging.INFO: colorama.Fore.RESET,
        logging.WARNING: colorama.Fore.YELLOW,
        logging.ERROR: colorama.Fore.RED,
        logging.CRITICAL: colorama.Fore.MAGENTA
    }

    # noinspection PyDefaultArgument
    def __init__(self, *args, color_map: dict[int, str] = DEFAULT, **kwargs):
        super().__init__(*args, **kwargs)
        self.color_map = color_map

    @override
    def format(self, record: logging.LogRecord) -> str:
        text = super().format(record)
        return f"{self.color_map.get(record.levelno) or ""}{text}{colorama.Style.RESET_ALL}"


def configure_logger(level: str | int = logging.WARNING):
    LOGGER.setLevel(level)

    console_formatter = ColoramaFormatter(
        "[{asctime}.{msecs:03.0f}] [{taskName}/{levelname}] [{name}]: {message}",
        datefmt="%H:%M:%S",
        style="{"
    )

    stdout_handler = logging.StreamHandler(sys.stdout)
    stdout_handler.addFilter(lambda r: r.levelno <= logging.INFO)
    stdout_handler.setFormatter(console_formatter)
    LOGGER.addHandler(stdout_handler)

    stderr_handler = logging.StreamHandler(sys.stderr)
    stderr_handler.setLevel(logging.WARNING)
    stderr_handler.setFormatter(console_formatter)
    LOGGER.addHandler(stderr_handler)

    # TODO: file handler


import time

_init_begin_time: float


def _cc_init_begin():
    global _init_begin_time
    _init_begin_time = time.perf_counter()
    LOGGER.debug("CodeCraft initializing...")


def _cc_init_end():
    ms = (time.perf_counter() - _init_begin_time) * 1e3
    LOGGER.info(f"CodeCraft initialization completed in {ms:.0f}ms")
