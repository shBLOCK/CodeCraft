from __future__ import annotations

import json

from typing import final, TYPE_CHECKING

from codecraft.internal.resource import ResLoc
from codecraft.internal.id_map import RegistryIdMap
from codecraft.internal.meta_folder import meta_path, should_write_meta_folder
from codecraft.log.log import LOGGER

if TYPE_CHECKING:
    from typing import Optional, Self
    from codecraft.internal.byte_buf.byte_buf import ByteBuf

    from codecraft.client import CCClient

CACHE_DIR = "registryIdMapCache"


@final
class RegistryIdMaps:
    def __init__(self, client: CCClient):
        self._client = client

        self._maps: dict[ResLoc, RegistryIdMap] = {}
        def add[T, R](m: RegistryIdMap[T, R]) -> RegistryIdMap[T, R]:
            self._maps[m.name] = m
            return m

        from codecraft.internal.cmd.cmd import Cmd
        from codecraft.internal.msg.msg import Msg
        from codecraft.block.block import Block
        from codecraft.world.world import World

        self.cmd = add(RegistryIdMap(Cmd.registry))
        self.msg = add(RegistryIdMap(Msg.registry))
        self.block = add(RegistryIdMap(Block.registry))
        self.item = add(RegistryIdMap("item"))
        self.entity = add(RegistryIdMap("entity_type"))
        self.world = add(RegistryIdMap(World.registry))

    def read_sync_packet(self, buf: ByteBuf):
        name = buf.read_resloc()
        self._client.logger.debug(f"Reading registry id map: {name}")
        id_map = self._maps.get(name)
        if id_map is None:
            self._client.logger.warning(f"Received registry id map sync packet with unknown registry {name}")
        for _ in range(buf.read_uvarint()):
            id = buf.read_varint()
            reg_name = buf.read_resloc()
            if id_map is not None:
                id_map.put(id, reg_name)

    def save_cache(self, name: str):
        try:
            if not should_write_meta_folder():
                return
            file = meta_path(f"{CACHE_DIR}/{name}.json")
            file.parent.mkdir(exist_ok=True)
            with file.open("wt", encoding="utf8") as f:
                json.dump(
                    {str(k): m.to_json_dict() for k, m in self._maps.items()},
                    f,
                    ensure_ascii=False,
                    indent=4
                )
        except (IOError, ValueError) as e:
            LOGGER.warn(f"Failed to save registry id map cache: {e}")

    @classmethod
    def load_cache(cls, name: str, client: CCClient) -> Optional[Self]:
        try:
            file = meta_path(f"{CACHE_DIR}/{name}.json")
            if not file.is_file():
                return None
            with file.open("rt", encoding="utf8") as f:
                data = json.load(f)
            obj = cls(client)
            for k, m in obj._maps.items():
                m.from_json_dict(data[str(k)], freeze=True)
            return obj
        except (IOError, ValueError) as e:
            LOGGER.debug(f"Failed to load registry id map <{name}>: {e}")
            return None
