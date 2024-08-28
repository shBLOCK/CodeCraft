from __future__ import annotations

from typing import final, TYPE_CHECKING

from codecraft.internal.resource import ResLoc
from codecraft.internal.id_map import RegistryIdMap
from codecraft.internal.meta_files import read_meta_file, write_meta_file
from codecraft.log.log import LOGGER
from codecraft.internal.cmd.cmd import Cmd
from codecraft.internal.msg.msg import Msg

if TYPE_CHECKING:
    from typing import Optional, Self

    from codecraft.internal import CCByteBuf
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

        self.cmd = add(RegistryIdMap(Cmd.registry))
        self.msg = add(RegistryIdMap(Msg.registry))
        self.block = add(RegistryIdMap("block"))
        self.item = add(RegistryIdMap("item"))
        self.entity = add(RegistryIdMap("entity_type"))
        self.world = add(RegistryIdMap("dimension"))

    def read_sync_packet(self, buf: CCByteBuf):
        name = buf.read_resloc()
        id_map = self._maps.get(name)
        if id_map is None:
            self._client.logger.warning(f"Received registry id map sync packet with unknown registry {name}")
        for _ in range(buf.read_varint()):
            id = buf.read_varint()
            reg_name = buf.read_resloc()
            if id_map is not None:
                id_map.put(id, reg_name)

        self._client.logger.debug(f"Reading registry id map: {name}")

    def save_cache(self, name: str):
        write_meta_file(
            f"{CACHE_DIR}/{name}",
            {str(k): m.to_json_dict() for k, m in self._maps.items()}
        )

    @classmethod
    def load_cache(cls, name: str, client: CCClient) -> Optional[Self]:
        try:
            data = read_meta_file(f"{CACHE_DIR}/{name}")
            obj = cls(client)
            for k, m in obj._maps.items():
                m.from_json_dict(data[str(k)], freeze=True)
            return obj
        except Exception as e:
            LOGGER.debug(f"No registry id map cache <{name}> or failed to load: {e}")
            return None
