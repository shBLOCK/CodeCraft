from typing import Any, final, ClassVar

from operator import call
import tomllib

from pydantic import ValidationError
from pydantic_settings import InitSettingsSource, BaseSettings, PydanticBaseSettingsSource

from codecraft.internal.meta_folder import meta_path

__all__ = "CCConfigBase", "load_cc_config"

from codecraft.log import LOGGER


@call
def _CONFIG() -> dict[str, Any]:
    try:
        file = meta_path("config.toml", vcs=True)
        if not file.is_file():
            return {}
        with file.open("r", encoding="utf8") as f:
            return tomllib.loads(f.read())
    except (OSError, tomllib.TOMLDecodeError) as e:
        raise RuntimeError(f"Failed to load codecraft config file: {e}") from e


class _CCTomlSettingsSource(InitSettingsSource):
    def __init__(self, settings_cls: type[BaseSettings], namespace: str):
        super().__init__(settings_cls, _CONFIG.get(namespace, {}))


class CCConfigBase(BaseSettings):
    config_namespace: ClassVar[str]

    @classmethod
    @final
    def settings_customise_sources(
        cls,
        settings_cls: type[BaseSettings],
        init_settings: PydanticBaseSettingsSource,
        env_settings: PydanticBaseSettingsSource,
        dotenv_settings: PydanticBaseSettingsSource,
        file_secret_settings: PydanticBaseSettingsSource,
    ) -> tuple[PydanticBaseSettingsSource, ...]:
        return (_CCTomlSettingsSource(settings_cls, cls.config_namespace),)

    @final
    def __repr_args__(self):
        return (("namespace", type(self).config_namespace),)


def load_cc_config[T: CCConfigBase](cls: type[T]) -> T:
    try:
        LOGGER.debug(f"Loading config {cls.__name__} with namespace \"{cls.config_namespace}\"")
        return cls()
    except ValidationError as e:
        raise RuntimeError(f"Failed to load config {cls.__name__}: {str(e)}")
