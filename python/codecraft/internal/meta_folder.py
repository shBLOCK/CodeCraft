from __future__ import annotations

from operator import call

from itertools import islice, chain

from typing import TYPE_CHECKING

from pathlib import Path, PurePosixPath, PurePath

from codecraft.log import LOGGER

if TYPE_CHECKING:
    from os import PathLike
    from collections.abc import Iterable

__all__ = ("meta_path", "should_write_meta_folder")

_META_DIR: Path | None = None


@call
def _():  # init
    def _meta_dirs() -> Iterable[Path]:
        cwd = Path.cwd()
        name = ".codecraft"
        yield cwd / name
        for outer in islice(cwd.parents, 3):
            outer / name

    global _META_DIR
    for dir in _meta_dirs():
        # use the first in _meta_dirs() as default
        if _META_DIR is None:
            _META_DIR = dir

        if dir.exists():
            _META_DIR = dir
            break
    else:
        try:
            _META_DIR.mkdir()
        except OSError as e:
            raise RuntimeError(f"Failed to create codecraft meta folder: {e}") from e


_VCS_INCLUDE = set()


def _gitignore():
    if not should_write_meta_folder():
        return
    try:
        file = _META_DIR / ".gitignore"
        if not file.exists():
            LOGGER.debug("Creating .gitignore in meta folder")
            file.parent.mkdir(exist_ok=True)
            file.touch()
        with file.open("r+") as f:
            for line in f.readlines():
                if line.startswith("!"):
                    _VCS_INCLUDE.add(line[1:].strip())
            f.truncate(0)
            f.seek(0)
            f.writelines((
                "# Created by CodeCraft automatically, do not edit unless you know what you are doing.\n",
                "*\n",
                *map(lambda b: f"!{b}\n", _VCS_INCLUDE)
            ))
    except OSError as e:
        LOGGER.warn(f"Failed to create .gitignore in meta folder", e)


def add_vcs_include(pattern: str):
    _VCS_INCLUDE.add(pattern)


class MetaPath(Path):
    def __init__(self, *args: PurePath | str, vcs: bool = False):
        args = list(args)
        for i, arg in enumerate(args):
            if isinstance(arg, MetaPath | PurePosixPath):
                # noinspection PyUnresolvedReferences,PyProtectedMember
                args[i] = arg._meta_path
            elif isinstance(arg, PurePath):  # is pure path that's not a MetaPath
                raise TypeError("MetaPath can't operate with path objects other than MetaPath and ")

        if args:
            if str(args[0]).startswith("/") or str(args[0]).startswith("./"):
                raise ValueError("Invalid meta path, meta path must not start with '/' or './'")

        try:
            if not _META_DIR.is_dir():
                LOGGER.debug(f"Creating meta folder: {_META_DIR}")
                _META_DIR.mkdir()
        except OSError as e:
            raise RuntimeError(f"Failed to create meta folder: {e}") from e

        self._meta_path = PurePosixPath(*args)
        if self._meta_path.anchor:
            raise ValueError("Invalid meta folder path, meta path must not be anchored")
        super().__init__(_META_DIR, self._meta_path)

        if vcs:
            add_vcs_include(f"/{self._meta_path}")

        _gitignore()

    def __str__(self):
        return str(self._meta_path)

    def __repr__(self):
        return f"MetaPath('{self._meta_path}')"


def should_write_meta_folder():
    """
    Indicates whether the environment is suitable for using (especially writing) meta folder files.

    This is False when, for example, the current runtime is a REPL.
    """
    # noinspection PyCompatibility
    import __main__
    return hasattr(__main__, "__file__")


def meta_path(*args: PathLike | str, vcs: bool = False) -> Path:
    path = PurePosixPath(*args)
    try:
        actual_file = _META_DIR / path
        if not actual_file.is_relative_to(_META_DIR):
            raise ValueError(f"Invalid meta path: {path}")

        try:
            if not _META_DIR.is_dir():
                LOGGER.debug(f"Creating meta folder: {_META_DIR}")
                _META_DIR.mkdir()
        except OSError as e:
            raise RuntimeError(f"Failed to create meta folder: {e}") from e

        if vcs:
            add_vcs_include(str(path))

        _gitignore()

    except IOError as e:
        raise IOError(f"Failed to access meta path {path}: {repr(e)}")

    return actual_file
