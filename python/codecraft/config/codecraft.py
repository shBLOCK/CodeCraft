from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict

from .config import load_cc_config, CCConfigBase
from ..internal import meta_path

__all__ = "CCConfig", "CCEnviron"


@load_cc_config
class CCConfig(CCConfigBase):
    config_namespace = "codecraft"

    log_level: Literal["NOTSET", "DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"] = "INFO"


_ENV_FILE = meta_path(".env")


class CCEnviron(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="cc_", env_file=_ENV_FILE, env_file_encoding="utf8")
