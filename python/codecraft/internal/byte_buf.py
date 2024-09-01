from __future__ import annotations

from contextlib import contextmanager
import enum
from uuid import UUID
from collections.abc import Buffer

import amulet_nbt
from amulet_nbt import ReadOffset
import mutf8
from itertools import repeat
from struct import Struct
from typing import TYPE_CHECKING
from spatium import Vec3, Vec3i
import crc32c

from codecraft.internal.resource import ResLoc

if TYPE_CHECKING:
    from amulet_nbt import AnyNBT, NamedTag
    from collections.abc import Sequence, Callable, Sized
    from typing import Self, Optional, Any, Protocol

    from codecraft.client import CCClient
    from codecraft.block import Block
    from codecraft.internal import IdMap


    class SizedBuffer(Buffer, Sized, Protocol): pass

__all__ = ("CCByteBuf",)

ST_BYTE = Struct(">b")
ST_INT = Struct(">i")
ST_LONG = Struct(">q")
ST_FLOAT = Struct(">d")

ST_UBYTE = Struct(">B")
ST_UINT = Struct(">I")
ST_ULONG = Struct(">Q")


# noinspection PyProtectedMember
class CCByteBuf:
    __slots__ = "_buffer", "_pos", "_size", "_is_write_dyn", "_client"

    class Type(enum.Enum):
        def __init__(self, type_id: int):
            self.type_id = type_id
            self._reader: Callable  # assigned after CCByteBuf class is created

        BYTE = 0
        INT = 1
        LONG = 2
        FLOAT = 3
        VARINT = 4

        BYTE_ARRAY = 5
        INT_ARRAY = 6
        LONG_ARRAY = 7
        FLOAT_ARRAY = 8
        VARINT_ARRAY = 9

        BOOL = 10

        STR = 11

        VEC3I = 12
        VEC3 = 13

        RES_LOC = 14

        UUID = 15

        NBT = 16

        BLOCK_STATE = 17

        @classmethod
        def get(cls, type_id: int):
            # noinspection PyUnresolvedReferences
            return cls._type_by_id.get(type_id)

    Type._type_by_id = {}
    for t in Type:
        Type._type_by_id[t.type_id] = t
    del t

    def __init__(self, value: int | SizedBuffer = 0, client: CCClient = None):
        self._buffer: bytearray | memoryview
        if isinstance(value, int):
            self._buffer = bytearray(value)
        elif isinstance(value, memoryview):
            self._buffer = value
        elif isinstance(value, Buffer):
            self._buffer = bytearray(value)
        else:
            raise TypeError("Invalid buffer")

        self._pos = 0
        self._size = len(self._buffer)
        self._is_write_dyn = False

        self._client = client

    def __bytes__(self):
        return bytes(self._buffer)

    @property
    def written_view(self) -> memoryview:
        return memoryview(self._buffer)[:self._pos]

    @property
    def to_read_view(self) -> memoryview:
        return memoryview(self._buffer)[self._pos: self._size]

    @property
    def full_view(self) -> memoryview:
        return memoryview(self._buffer)[:self._size]

    @property
    def pos(self) -> int:
        return self._pos

    @property
    def remaining(self) -> int:
        return len(self) - self._pos

    def seek(self, num: int) -> Self:
        self._pos += num
        return self

    def __len__(self):
        return self._size

    def __getitem__(self, item: slice):
        """Create a view of a slice in this buffer."""
        if not isinstance(item, slice):
            raise TypeError("CCByteBuf only supports slicing")
        result = CCByteBuf()
        result._buffer = memoryview(self._buffer)[item]
        return result

    def clear(self):
        if isinstance(self._buffer, bytearray):
            self._pos = 0
            self._size = 0
            self._buffer.clear()
        else:
            raise ValueError("This buffer is not resizable")

    def checksum(self) -> int:
        return crc32c.crc32c(self.full_view)

    def allocate(self, length: int):
        if length < 0:
            raise ValueError("Can't allocate a negative amount of bytes")
        if not isinstance(self._buffer, bytearray):
            raise ValueError("This buffer is not resizable")
        self._buffer.extend(repeat(0, length))  # TODO: smart allocate strategy? (less micro-allocations)
        self._size += length

    def _allocate_to_fit(self, size: int):
        remaining = self.remaining
        if remaining < size:
            self.allocate(size - remaining)

    def _read_struct(self, st: Struct):
        if self.remaining < st.size:
            raise ValueError("Buffer underflow")
        result = st.unpack_from(self._buffer, self._pos)
        self._pos += st.size
        return result[0]

    def _write_struct(self, st: Struct, *data) -> Self:
        self._allocate_to_fit(st.size)
        st.pack_into(self._buffer, self._pos, *data)
        self._pos += st.size
        return self

    def _read_struct_array(self, st: Struct):
        return tuple(self._read_struct(st) for _ in range(self.read_varint()))

    def _write_struct_array(self, st: Struct, arr: Sequence) -> Self:
        self.write_varint(len(arr))
        for item in arr:
            self._write_struct(st, item)
        return self

    def write_buffer(self, data: SizedBuffer):
        self._allocate_to_fit(len(data))
        self._buffer[self._pos: self._pos + len(data)] = data
        self._pos += len(data)

    def read_dynamic(self) -> tuple[Optional[Any], Type]:
        type_id = self.read_byte()
        tpe = CCByteBuf.Type.get(type_id)
        if tpe is None:
            raise ValueError(f"Invalid type id: {type_id}")
        return tpe._reader(self), tpe

    @contextmanager
    def write_dynamic(self):
        self._is_write_dyn = True
        try:
            yield
        finally:
            self._is_write_dyn = False

    def __writing_type(self, tp: Type):
        if self._is_write_dyn:
            self._write_struct(ST_BYTE, tp.type_id)

    def read_byte(self, tc: bool = False) -> int:
        return self._read_struct(ST_UBYTE if tc else ST_BYTE)
    def write_byte(self, value: int, tc: bool = False) -> Self:
        self.__writing_type(CCByteBuf.Type.BYTE)
        return self._write_struct(ST_UBYTE if tc else ST_BYTE, value)
    def read_byte_array(self, tc: bool = False) -> tuple[int, ...]:
        return self._read_struct_array(ST_UBYTE if tc else ST_BYTE)
    def write_byte_array(self, value: Sequence[int], tc: bool = False) -> Self:
        self.__writing_type(CCByteBuf.Type.BYTE_ARRAY)
        return self._write_struct_array(ST_UBYTE if tc else ST_BYTE, value)
    def read_bytes(self) -> bytes:
        length = self.read_varint()
        view = self.to_read_view[:length]
        self._pos += length
        return bytes(view)
    def write_bytes(self, data: SizedBuffer) -> Self:
        self.__writing_type(CCByteBuf.Type.BYTE_ARRAY)
        self.write_varint(len(data))
        self._allocate_to_fit(len(data))
        self._buffer[self._pos: self._pos + len(data)] = data
        self._pos += len(data)
        return self
    def read_slice(self) -> CCByteBuf:
        length = self.read_varint()
        view = self.to_read_view[:length]
        self._pos += length
        return CCByteBuf(view)

    def read_int(self, tc: bool = False) -> int:
        return self._read_struct(ST_UINT if tc else ST_INT)
    def write_int(self, value: int, tc: bool = False) -> Self:
        self.__writing_type(CCByteBuf.Type.INT)
        return self._write_struct(ST_UINT if tc else ST_INT, value)
    def read_int_array(self, tc: bool = False) -> tuple[int, ...]:
        return self._read_struct_array(ST_UINT if tc else ST_INT)
    def write_int_array(self, value: Sequence[int], tc: bool = False) -> Self:
        self.__writing_type(CCByteBuf.Type.INT_ARRAY)
        return self._write_struct_array(ST_UINT if tc else ST_INT, value)

    def read_long(self, tc: bool = False) -> int:
        return self._read_struct(ST_ULONG if tc else ST_LONG)
    def write_long(self, value: int, tc: bool = False) -> Self:
        self.__writing_type(CCByteBuf.Type.LONG)
        return self._write_struct(ST_ULONG if tc else ST_LONG, value)
    def read_long_array(self, tc: bool = False) -> tuple[int, ...]:
        return self._read_struct_array(ST_ULONG if tc else ST_LONG)
    def write_long_array(self, value: Sequence[int], tc: bool = False) -> Self:
        self.__writing_type(CCByteBuf.Type.LONG_ARRAY)
        return self._write_struct_array(ST_ULONG if tc else ST_LONG, value)

    def read_float(self) -> float:
        return self._read_struct(ST_FLOAT)
    def write_float(self, value: float) -> Self:
        self.__writing_type(CCByteBuf.Type.FLOAT)
        return self._write_struct(ST_FLOAT, value)
    def read_float_array(self) -> tuple[float, ...]:
        return self._read_struct_array(ST_FLOAT)
    def write_float_array(self, value: Sequence[float]) -> Self:
        self.__writing_type(CCByteBuf.Type.FLOAT_ARRAY)
        return self._write_struct_array(ST_FLOAT, value)

    def read_varint(self, tc: bool = False) -> int:
        value = 0
        for i in range(5):
            byte = self._read_struct(ST_UBYTE)
            value |= (byte & 0b0111_1111) << (i * 7)
            if not byte & 0b1000_0000:
                break
        else:
            raise ValueError("Varint too long")
        return value if tc else _from_tc(value, 32)

    def write_varint(self, value: int, tc: bool = False) -> Self:
        if value > 2 ** 31 - 1 or value < -(2 ** 31):
            raise ValueError("Number too big for varint")
        if not tc:
            value = _to_tc(value, 32)
        byts = -(value.bit_length() // -7)  # ceil div
        byts = max(byts, 1)
        self.__writing_type(CCByteBuf.Type.VARINT)
        self._allocate_to_fit(byts)
        for i in range(byts):
            continue_flag = (1 << 7) if i != byts - 1 else 0
            self._write_struct(ST_UBYTE, value & 0b0111_1111 | continue_flag)
            value >>= 7
        return self

    def read_varint_array(self) -> tuple[int, ...]:
        return tuple(self.read_varint() for _ in range(self.read_varint()))

    def write_varint_array(self, value: Sequence[int]) -> Self:
        self.__writing_type(CCByteBuf.Type.VARINT_ARRAY)
        self.write_varint(len(value))
        for item in value:
            self.write_varint(item)
        return self

    def read_bool(self) -> bool:
        self._pos += 1
        return self._buffer[self._pos - 1] != 0

    def write_bool(self, value: bool) -> Self:
        self.__writing_type(CCByteBuf.Type.BOOL)
        self._allocate_to_fit(1)
        self._buffer[self._pos] = 1 if value else 0
        self._pos += 1
        return self

    def read_vec3i(self) -> Vec3i:
        return Vec3i(self.read_int(), self.read_int(), self.read_int())
    def write_vec3i(self, value: Vec3i) -> Self:
        self.__writing_type(CCByteBuf.Type.VEC3I)
        self.write_int(value.x)
        self.write_int(value.y)
        self.write_int(value.z)
        return self

    def read_vec3(self) -> Vec3:
        return Vec3(self.read_float(), self.read_float(), self.read_float())
    def write_vec3(self, value: Vec3) -> Self:
        self.__writing_type(CCByteBuf.Type.VEC3)
        self.write_float(value.x)
        self.write_float(value.y)
        self.write_float(value.z)
        return self

    def read_str(self) -> str:
        bts = self.read_varint()
        result = mutf8.decode_modified_utf8(memoryview(self._buffer)[self._pos:self._pos + bts])
        self._pos += bts
        return result
    def write_str(self, value: str) -> Self:
        self.__writing_type(CCByteBuf.Type.STR)
        data = mutf8.encode_modified_utf8(value)
        self.write_varint(len(data))
        self.write_buffer(data)
        return self

    def read_resloc(self) -> ResLoc:
        v = self.read_str(), self.read_str()
        return ResLoc(*v)
    def write_resloc(self, value: ResLoc) -> Self:
        self.__writing_type(CCByteBuf.Type.RES_LOC)
        self.write_str(value.namespace)
        self.write_str(value.path)
        return self

    def read_uuid(self) -> UUID:
        self._pos += 16
        return UUID(bytes=memoryview(self._buffer)[self._pos - 16:self._pos].tobytes())
    def write_uuid(self, value: UUID) -> Self:
        self.__writing_type(CCByteBuf.Type.UUID)
        self.write_buffer(value.bytes)
        return self

    def read_nbt(self) -> Optional[NamedTag]:
        if self._buffer[self._pos] == 0:
            return None
        ctx = ReadOffset()
        result = amulet_nbt.read_nbt(
            memoryview(self._buffer)[self._pos:],
            compressed=False,
            read_offset=ctx
        )
        self._pos += ctx.offset
        return result

    def write_nbt(self, value: Optional[AnyNBT | NamedTag]) -> Self:
        self.__writing_type(CCByteBuf.Type.NBT)
        if value is None:
            self.write_byte(0)
            return self
        data = value.to_nbt(compressed=False)
        self.write_buffer(data)
        return self

    def read_using_id_map[T](self, id_map: IdMap[T]) -> T:
        return id_map[self.read_varint()]

    def write_using_id_map[T](self, id_map: IdMap[T], obj: T) -> Self:
        self.write_varint(id_map[obj])
        return self

    def read_blockstate(self) -> Block:
        block: Block = self.read_using_id_map(self._client.reg_id_maps.block)
        for _ in range(self.read_varint()):
            name = self.read_str()
            prop = block._properties[name]
            block[name] = prop.deserialize(self.read_str())
        return block

    def write_blockstate(self, block: Block, all=False) -> Self:
        self.__writing_type(CCByteBuf.Type.BLOCK_STATE)
        self.write_using_id_map(self._client.reg_id_maps.block, block)

        props = block._all_properties() if all else block._assigned_properties()
        num = block._num_properties() if all else len(block._assigned_properties())

        self.write_varint(num)
        for prop_name in props:
            self.write_str(prop_name)
            self.write_str(block._get_property(prop_name).serialize(block[prop_name]))
        return self


# noinspection DuplicatedCode
CCByteBuf.Type.BYTE._reader = CCByteBuf.read_byte
CCByteBuf.Type.INT._reader = CCByteBuf.read_int
CCByteBuf.Type.LONG._reader = CCByteBuf.read_long
CCByteBuf.Type.FLOAT._reader = CCByteBuf.read_float
CCByteBuf.Type.VARINT._reader = CCByteBuf.read_varint
CCByteBuf.Type.BYTE_ARRAY._reader = CCByteBuf.read_byte_array
CCByteBuf.Type.INT_ARRAY._reader = CCByteBuf.read_int_array
CCByteBuf.Type.LONG_ARRAY._reader = CCByteBuf.read_long_array
CCByteBuf.Type.FLOAT_ARRAY._reader = CCByteBuf.read_float_array
# noinspection DuplicatedCode
CCByteBuf.Type.VARINT_ARRAY._reader = CCByteBuf.read_varint_array
CCByteBuf.Type.BOOL._reader = CCByteBuf.read_bool
CCByteBuf.Type.STR._reader = CCByteBuf.read_str
CCByteBuf.Type.VEC3I._reader = CCByteBuf.read_vec3i
CCByteBuf.Type.VEC3._reader = CCByteBuf.read_vec3
CCByteBuf.Type.RES_LOC._reader = CCByteBuf.read_resloc
CCByteBuf.Type.UUID._reader = CCByteBuf.read_uuid
CCByteBuf.Type.NBT._reader = CCByteBuf.read_nbt
CCByteBuf.Type.BLOCK_STATE._reader = CCByteBuf.read_blockstate
for t in CCByteBuf.Type:
    # noinspection PyProtectedMember,PyUnresolvedReferences
    assert t._reader is not None
del t


def _to_tc(num: int, bits: int) -> int:
    """To two's complement."""
    sign_mask = 1 << (bits - 1)
    if num >= 0:
        return num & ~sign_mask
    return (num + (1 << (bits - 1))) | sign_mask


def _from_tc(num: int, bits: int) -> int:
    """From two's complement."""
    if num & (1 << (bits - 1)):
        return num - (1 << bits)
    return num
