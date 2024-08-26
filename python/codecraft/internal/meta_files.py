from __future__ import annotations

import json
from typing import TYPE_CHECKING

from pathlib import Path

if TYPE_CHECKING:
    from os import PathLike
    from typing import Any

__all__ = ("read_meta_file", "write_meta_file")

_META_PATH = Path("./.codecraft")


def _gitignore():
    file = _META_PATH / ".gitignore"
    if not file.exists():
        with file.open("w") as f:
            f.writelines((
                "# created by CodeCraft automatically",
                "*"
            ))


def _meta_file(file: PathLike | str) -> Path:
    try:
        actual_file = _META_PATH / Path(file).with_suffix(".json")

        if not _META_PATH.is_dir():
            _META_PATH.mkdir()

        _gitignore()

        if not actual_file.parent.is_dir():
            actual_file.parent.mkdir()
    except IOError as e:
        raise IOError(f"Failed to access meta file {file}: {repr(e)}")

    return actual_file


def read_meta_file(file: PathLike | str) -> Any:
    actual_file = _meta_file(file)

    try:
        with actual_file.open("rt", encoding="utf8") as f:
            return json.load(f)
    except IOError | ValueError as e:
        raise IOError(f"Failed to read meta file {file}: {repr(e)}")


def write_meta_file(file: PathLike | str, data: Any) -> None:
    actual_file = _meta_file(file)

    try:
        with actual_file.open("wt", encoding="utf8") as f:
            json.dump(data, f, ensure_ascii=False)
    except IOError | ValueError as e:
        raise IOError(f"Failed to write meta file {file}: {repr(e)}")
