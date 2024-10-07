from __future__ import annotations

from contextlib import contextmanager
import enum
from uuid import UUID
from array import array
from collections.abc import Buffer, Iterable

import amulet_nbt
from amulet_nbt import ReadOffset, CompoundTag
from itertools import repeat, count
from struct import Struct
from typing import TYPE_CHECKING
from spatium import Vec2i, Vec2, Vec3i, Vec3, Transform2D, Transform3D
import crc32c

from codecraft.internal.byte_buf.byte_utils import _array_adapt_byteorder
from codecraft.internal.resource import ResLoc

if TYPE_CHECKING:
    from amulet_nbt import AnyNBT
    from collections.abc import Sequence, Callable
    from typing import Self, Any

    from codecraft.client import CCClient
    from codecraft.block import Block
    from codecraft.internal import IdMap

__all__ = ("ByteBuf",)

TC_BYTE = "b"
TC_SHORT = "h"
TC_INT = "i"
TC_LONG = "q"
TC_UBYTE = "B"
TC_USHORT = "H"
TC_UINT = "I"
TC_ULONG = "Q"
TC_FLOAT = "f"
TC_DOUBLE = "d"

# noinspection DuplicatedCode
ST_BYTE = Struct(f"!{TC_BYTE}")
ST_SHORT = Struct(f"!{TC_SHORT}")
ST_INT = Struct(f"!{TC_INT}")
ST_LONG = Struct(f"!{TC_LONG}")
ST_UBYTE = Struct(f"!{TC_UBYTE}")
ST_USHORT = Struct(f"!{TC_USHORT}")
ST_UINT = Struct(f"!{TC_UINT}")
ST_ULONG = Struct(f"!{TC_ULONG}")
ST_FLOAT = Struct(f"!{TC_FLOAT}")
ST_DOUBLE = Struct(f"!{TC_DOUBLE}")

# noinspection DuplicatedCode
ST_VEC2I = Struct(f"!2{TC_INT}")
ST_VEC2F = Struct(f"!2{TC_FLOAT}")
ST_VEC2D = Struct(f"!2{TC_DOUBLE}")
ST_VEC3I = Struct(f"!3{TC_INT}")
ST_VEC3F = Struct(f"!3{TC_FLOAT}")
ST_VEC3D = Struct(f"!3{TC_DOUBLE}")
ST_TRANSFORM2DF = Struct(f"!6{TC_FLOAT}")
ST_TRANSFORM2DD = Struct(f"!6{TC_DOUBLE}")
ST_TRANSFORM3DF = Struct(f"!12{TC_FLOAT}")
ST_TRANSFORM3DD = Struct(f"!12{TC_DOUBLE}")

ST_UUID = Struct(f"!QQ")


# noinspection PyProtectedMember
class ByteBuf:
    __slots__ = "_buffer", "_pos", "_size", "_client"

    def __init__(self, value: int | memoryview | Buffer = 0, client: CCClient = None):
        self._buffer: bytearray | memoryview
        if isinstance(value, int):
            self._buffer = bytearray(value)
            self._size = 0
        elif isinstance(value, memoryview):
            self._buffer = value
            self._size = len(self._buffer)
        elif isinstance(value, Buffer):
            self._buffer = bytearray(value)
            self._size = len(self._buffer)
        else:
            raise TypeError("Invalid buffer")

        self._pos = 0

        self._client = client

    def __bytes__(self):
        return bytes(self._buffer)

    @property
    def written_view(self) -> memoryview:
        return memoryview(self._buffer)[:self._size]

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
        result = ByteBuf()
        result._buffer = memoryview(self._buffer)[item]
        return result

    def reset(self):
        self._pos = 0

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

    # region Internal
    def _allocate_to_fit(self, size: int):
        remaining = len(self._buffer) - self._size
        if remaining < size:
            self.allocate(size - remaining)

    def _read_struct(self, st: Struct):
        if self.remaining < st.size:
            raise ValueError("Buffer underflow")
        result = st.unpack_from(self._buffer, self._pos)
        self._pos += st.size
        return result

    def _write_struct(self, st: Struct, *data) -> Self:
        self._allocate_to_fit(st.size)
        st.pack_into(self._buffer, self._size, *data)
        self._size += st.size
        return self

    def _write_struct_raw(self, st: Struct, data) -> Self:
        self._allocate_to_fit(st.size)
        st.pack_into(self._buffer, self._size, *data)
        self._size += st.size
        return self

    def _read_struct_array_tuple(self, st: Struct):
        n = self.read_uvarint()
        size = st.size
        result = tuple(st.unpack_from(self._buffer, self._pos + size * i)[0] for i in range(n))
        self._pos += size * n
        return result

    def _write_struct_sequence(self, st: Struct, seq: Sequence) -> Self:
        self.write_uvarint(len(seq))
        size = st.size * len(seq)
        self._size += size
        self._allocate_to_fit(size)
        for item in seq:
            st.pack_into(self._buffer, self._size, item)
        return self

    def _read_memoryview(self, size: int) -> memoryview:
        view = memoryview(self._buffer)[self._pos: self._pos + size]
        self._pos += size
        return view

    def _read_raw_array(self, tc: str) -> array:
        arr = array(tc)
        arr.frombytes(self._read_memoryview(arr.itemsize * self.read_uvarint()))
        _array_adapt_byteorder(arr)
        return arr

    def _write_raw_array(self, tc: str, arr: array) -> Self:
        if arr.typecode != tc:
            raise TypeError(f"Expected array of type '{tc}', got '{arr.typecode}'")
        _array_adapt_byteorder(arr)
        self._write_buffer(arr, arr.itemsize * len(arr))
        return self

    def _read_many[T](self, reader: Callable[[], T]) -> Iterable[T]:
        for _ in range(self.read_uvarint()):
            yield reader()

    def _write_many[T](self, values: Sequence[T], writer: Callable[[T], Any]) -> Self:
        self.write_uvarint(len(values))
        for item in values:
            writer(item)
        return self

    def _read_varinteger(self, bits: int, max_bytes: int, signed: bool) -> int:
        value = 0
        for i in count():
            if i == max_bytes:
                raise ValueError(f"Var len number too long (>{max_bytes} bytes)")
            byte = self._buffer[self._pos + i]
            value |= (byte & 0b0111_1111) << (i * 7)
            if not byte & 0b1000_0000:
                self._pos += (i + 1)
                break

        if signed:
            sign_bit = value & 0b1
            value >>= 1
            if sign_bit:
                value -= (1 << (bits - 1))

        return value

    def _write_varinteger(
        self,
        value: int,
        bits: int, mask: int, max_bytes: int, signed: bool,
        min: int, max: int
    ) -> Self:
        self._allocate_to_fit(max_bytes)

        if value < min or value > max:
            raise ValueError(f"Var len number out of range: {value}")

        if signed:
            value &= mask
            value = (value << 1 & mask) | (value >> (bits - 1))

        for i in count():
            byte = value & 0b0111_1111
            value >>= 7
            if value:
                byte |= 0b1000_0000
            self._buffer[self._size + i] = byte
            if not value:
                self._size += (i + 1)
                break

        return self

    def _write_buffer(self, data: Buffer, size: int = None):
        # noinspection PyTypeChecker
        size = len(data) if size is None else size
        self._buffer[self._size: self._size + size] = data
        self._size += size

    @contextmanager
    def __writing_type(self, tp: BufPrimitive):
        """May be used for dynamic types in the future, currently unused."""
        yield
    # endregion

    # region Simple
    def read_byte(self) -> int:
        return self._read_struct(ST_BYTE)[0]
    def read_short(self) -> int:
        return self._read_struct(ST_SHORT)[0]
    def read_int(self) -> int:
        return self._read_struct(ST_INT)[0]
    def read_long(self) -> int:
        return self._read_struct(ST_LONG)[0]
    def read_float(self) -> float:
        return self._read_struct(ST_FLOAT)[0]
    def read_double(self) -> float:
        return self._read_struct(ST_DOUBLE)[0]
    def read_ubyte(self) -> int:
        return self._read_struct(ST_UBYTE)[0]
    def read_ushort(self) -> int:
        return self._read_struct(ST_USHORT)[0]
    def read_uint(self) -> int:
        return self._read_struct(ST_UINT)[0]
    def read_ulong(self) -> int:
        return self._read_struct(ST_ULONG)[0]

    def write_byte(self, value: int) -> Self:
        with self.__writing_type(BufPrimitive.BYTE):
            return self._write_struct(ST_BYTE, value)
    def write_short(self, value: int) -> Self:
        with self.__writing_type(BufPrimitive.SHORT):
            return self._write_struct(ST_SHORT, value)
    def write_int(self, value: int) -> Self:
        with self.__writing_type(BufPrimitive.INT):
            return self._write_struct(ST_INT, value)
    def write_long(self, value: int) -> Self:
        with self.__writing_type(BufPrimitive.LONG):
            return self._write_struct(ST_LONG, value)
    def write_float(self, value: float) -> Self:
        with self.__writing_type(BufPrimitive.FLOAT):
            return self._write_struct(ST_FLOAT, value)
    def write_double(self, value: float) -> Self:
        with self.__writing_type(BufPrimitive.DOUBLE):
            return self._write_struct(ST_DOUBLE, value)
    def write_ubyte(self, value: int) -> Self:
        with self.__writing_type(BufPrimitive.UBYTE):
            return self._write_struct(ST_UBYTE, value)
    def write_ushort(self, value: int) -> Self:
        with self.__writing_type(BufPrimitive.USHORT):
            return self._write_struct(ST_USHORT, value)
    def write_uint(self, value: int) -> Self:
        with self.__writing_type(BufPrimitive.UINT):
            return self._write_struct(ST_UINT, value)
    def write_ulong(self, value: int) -> Self:
        with self.__writing_type(BufPrimitive.ULONG):
            return self._write_struct(ST_ULONG, value)

    def read_varint(self) -> int:
        return self._read_varinteger(32, 5, True)
    def read_uvarint(self) -> int:
        return self._read_varinteger(32, 5, False)
    def read_varlong(self) -> int:
        return self._read_varinteger(64, 10, True)
    def read_uvarlong(self) -> int:
        return self._read_varinteger(64, 10, False)

    def write_varint(self, value: int) -> Self:
        with self.__writing_type(BufPrimitive.VARINT):
            return self._write_varinteger(value, 32, 0xFFFF_FFFF, 5, True, -2 ** 31, 2 ** 31 - 1)
    def write_uvarint(self, value: int) -> Self:
        with self.__writing_type(BufPrimitive.UVARINT):
            return self._write_varinteger(value, 32, 0xFFFF_FFFF, 5, False, 0, 2 ** 32 - 1)
    def write_varlong(self, value: int) -> Self:
        with self.__writing_type(BufPrimitive.VARLONG):
            return self._write_varinteger(value, 64, 0xFFFF_FFFF_FFFF_FFFF, 10, True, -2 ** 63, 2 ** 63 - 1)
    def write_uvarlong(self, value: int) -> Self:
        with self.__writing_type(BufPrimitive.UVARLONG):
            return self._write_varinteger(value, 64, 0xFFFF_FFFF_FFFF_FFFF, 10, False, 0, 2 ** 64 - 1)

    def read_varint_tuple(self) -> tuple[int, ...]:
        return tuple(self._read_many(self.read_varint))
    def read_uvarint_tuple(self) -> tuple[int, ...]:
        return tuple(self._read_many(self.read_uvarint))
    def read_varlong_tuple(self) -> tuple[int, ...]:
        return tuple(self._read_many(self.read_varlong))
    def read_uvarlong_tuple(self) -> tuple[int, ...]:
        return tuple(self._read_many(self.read_uvarlong))

    def write_varint_sequence(self, values: Sequence[int]) -> Self:
        with self.__writing_type(BufPrimitive.VARINT_ARRAY):
            return self._write_many(values, self.write_varint)
    def write_uvarint_sequence(self, values: Sequence[int]) -> Self:
        with self.__writing_type(BufPrimitive.UVARINT_ARRAY):
            return self._write_many(values, self.write_uvarint)
    def write_varlong_sequence(self, values: Sequence[int]) -> Self:
        with self.__writing_type(BufPrimitive.VARLONG_ARRAY):
            return self._write_many(values, self.write_varlong)
    def write_uvarlong_sequence(self, values: Sequence[int]) -> Self:
        with self.__writing_type(BufPrimitive.UVARLONG_ARRAY):
            return self._write_many(values, self.write_uvarlong)

    def read_byte_tuple(self) -> tuple[int, ...]:
        return self._read_struct_array_tuple(ST_BYTE)
    def read_short_tuple(self) -> tuple[int, ...]:
        return self._read_struct_array_tuple(ST_SHORT)
    def read_int_tuple(self) -> tuple[int, ...]:
        return self._read_struct_array_tuple(ST_INT)
    def read_long_tuple(self) -> tuple[int, ...]:
        return self._read_struct_array_tuple(ST_LONG)
    def read_float_tuple(self) -> tuple[float, ...]:
        return self._read_struct_array_tuple(ST_FLOAT)
    def read_double_tuple(self) -> tuple[float, ...]:
        return self._read_struct_array_tuple(ST_DOUBLE)
    def read_ubyte_tuple(self) -> tuple[int, ...]:
        return self._read_struct_array_tuple(ST_UBYTE)
    def read_ushort_tuple(self) -> tuple[int, ...]:
        return self._read_struct_array_tuple(ST_USHORT)
    def read_uint_tuple(self) -> tuple[int, ...]:
        return self._read_struct_array_tuple(ST_UINT)
    def read_ulong_tuple(self) -> tuple[int, ...]:
        return self._read_struct_array_tuple(ST_ULONG)

    def write_byte_sequence(self, sequence: Sequence[int]) -> Self:
        with self.__writing_type(BufPrimitive.BYTE_ARRAY):
            return self._write_struct_sequence(ST_BYTE, sequence)
    def write_short_sequence(self, sequence: Sequence[int]) -> Self:
        with self.__writing_type(BufPrimitive.SHORT_ARRAY):
            return self._write_struct_sequence(ST_SHORT, sequence)
    def write_int_sequence(self, sequence: Sequence[int]) -> Self:
        with self.__writing_type(BufPrimitive.INT_ARRAY):
            return self._write_struct_sequence(ST_INT, sequence)
    def write_long_sequence(self, sequence: Sequence[int]) -> Self:
        with self.__writing_type(BufPrimitive.LONG_ARRAY):
            return self._write_struct_sequence(ST_LONG, sequence)
    def write_float_sequence(self, sequence: Sequence[float]) -> Self:
        with self.__writing_type(BufPrimitive.FLOAT_ARRAY):
            return self._write_struct_sequence(ST_FLOAT, sequence)
    def write_double_sequence(self, sequence: Sequence[float]) -> Self:
        with self.__writing_type(BufPrimitive.DOUBLE_ARRAY):
            return self._write_struct_sequence(ST_DOUBLE, sequence)
    def write_ubyte_sequence(self, sequence: Sequence[int]) -> Self:
        with self.__writing_type(BufPrimitive.UBYTE_ARRAY):
            return self._write_struct_sequence(ST_UBYTE, sequence)
    def write_ushort_sequence(self, sequence: Sequence[int]) -> Self:
        with self.__writing_type(BufPrimitive.USHORT_ARRAY):
            return self._write_struct_sequence(ST_USHORT, sequence)
    def write_uint_sequence(self, sequence: Sequence[int]) -> Self:
        with self.__writing_type(BufPrimitive.UINT_ARRAY):
            return self._write_struct_sequence(ST_UINT, sequence)
    def write_ulong_sequence(self, sequence: Sequence[int]) -> Self:
        with self.__writing_type(BufPrimitive.ULONG_ARRAY):
            return self._write_struct_sequence(ST_ULONG, sequence)

    def read_byte_array(self) -> array[int]:
        return self._read_raw_array(TC_BYTE)
    def read_short_array(self) -> array[int]:
        return self._read_raw_array(TC_SHORT)
    def read_int_array(self) -> array[int]:
        return self._read_raw_array(TC_INT)
    def read_long_array(self) -> array[int]:
        return self._read_raw_array(TC_LONG)
    def read_float_array(self) -> array[float]:
        return self._read_raw_array(TC_FLOAT)
    def read_double_array(self) -> array[float]:
        return self._read_raw_array(TC_DOUBLE)
    def read_ubyte_array(self) -> array[int]:
        return self._read_raw_array(TC_UBYTE)
    def read_ushort_array(self) -> array[int]:
        return self._read_raw_array(TC_USHORT)
    def read_uint_array(self) -> array[int]:
        return self._read_raw_array(TC_UINT)
    def read_ulong_array(self) -> array[int]:
        return self._read_raw_array(TC_ULONG)

    def write_byte_array(self, arr: array[int]) -> Self:
        with self.__writing_type(BufPrimitive.BYTE_ARRAY):
            return self._write_raw_array(TC_BYTE, arr)
    def write_short_array(self, arr: array[int]) -> Self:
        with self.__writing_type(BufPrimitive.SHORT_ARRAY):
            return self._write_raw_array(TC_SHORT, arr)
    def write_int_array(self, arr: array[int]) -> Self:
        with self.__writing_type(BufPrimitive.INT_ARRAY):
            return self._write_raw_array(TC_INT, arr)
    def write_long_array(self, arr: array[int]) -> Self:
        with self.__writing_type(BufPrimitive.LONG_ARRAY):
            return self._write_raw_array(TC_LONG, arr)
    def write_float_array(self, arr: array[float]) -> Self:
        with self.__writing_type(BufPrimitive.FLOAT_ARRAY):
            return self._write_raw_array(TC_FLOAT, arr)
    def write_double_array(self, arr: array[float]) -> Self:
        with self.__writing_type(BufPrimitive.DOUBLE_ARRAY):
            return self._write_raw_array(TC_DOUBLE, arr)
    def write_ubyte_array(self, arr: array[int]) -> Self:
        with self.__writing_type(BufPrimitive.UBYTE_ARRAY):
            return self._write_raw_array(TC_UBYTE, arr)
    def write_ushort_array(self, arr: array[int]) -> Self:
        with self.__writing_type(BufPrimitive.USHORT_ARRAY):
            return self._write_raw_array(TC_USHORT, arr)
    def write_uint_array(self, arr: array[int]) -> Self:
        with self.__writing_type(BufPrimitive.UINT_ARRAY):
            return self._write_raw_array(TC_UINT, arr)
    def write_ulong_array(self, arr: array[int]) -> Self:
        with self.__writing_type(BufPrimitive.ULONG_ARRAY):
            return self._write_raw_array(TC_ULONG, arr)

    def read_vec2i(self) -> Vec2i:
        return Vec2i(*self._read_struct(ST_VEC2I))
    def read_vec2f(self) -> Vec2:
        return Vec2(*self._read_struct(ST_VEC2F))
    def read_vec2d(self) -> Vec2:
        return Vec2(*self._read_struct(ST_VEC2D))
    def read_vec3i(self) -> Vec3i:
        return Vec3i(*self._read_struct(ST_VEC3I))
    def read_vec3f(self) -> Vec3:
        return Vec3(*self._read_struct(ST_VEC3F))
    def read_vec3d(self) -> Vec3:
        return Vec3(*self._read_struct(ST_VEC3D))
    def read_transform2df(self) -> Transform2D:
        return Transform2D(*self._read_struct(ST_TRANSFORM2DF))
    def read_transform2dd(self) -> Transform2D:
        return Transform2D(*self._read_struct(ST_TRANSFORM2DD))
    def read_transform3df(self) -> Transform3D:
        return Transform3D(*self._read_struct(ST_TRANSFORM3DF))
    def read_transform3dd(self) -> Transform3D:
        return Transform3D(*self._read_struct(ST_TRANSFORM3DD))

    def write_vec2i(self, value: Vec2i) -> Self:
        with self.__writing_type(BufPrimitive.VEC2I):
            return self._write_struct_raw(ST_VEC2I, value)
    def write_vec2f(self, value: Vec2) -> Self:
        with self.__writing_type(BufPrimitive.VEC2F):
            return self._write_struct_raw(ST_VEC2F, value)
    def write_vec2d(self, value: Vec2) -> Self:
        with self.__writing_type(BufPrimitive.VEC2D):
            return self._write_struct_raw(ST_VEC2D, value)
    def write_vec3i(self, value: Vec3i) -> Self:
        with self.__writing_type(BufPrimitive.VEC3I):
            return self._write_struct_raw(ST_VEC3I, value)
    def write_vec3f(self, value: Vec3) -> Self:
        with self.__writing_type(BufPrimitive.VEC3F):
            return self._write_struct_raw(ST_VEC3F, value)
    def write_vec3d(self, value: Vec3) -> Self:
        with self.__writing_type(BufPrimitive.VEC3D):
            return self._write_struct_raw(ST_VEC3D, value)
    def write_transform2df(self, value: Transform2D) -> Self:
        with self.__writing_type(BufPrimitive.TRANSFORM2DF):
            return self._write_struct_raw(ST_TRANSFORM2DF, value)
    def write_transform2dd(self, value: Transform2D) -> Self:
        with self.__writing_type(BufPrimitive.TRANSFORM2DD):
            return self._write_struct_raw(ST_TRANSFORM2DD, value)
    def write_transform3df(self, value: Transform3D) -> Self:
        with self.__writing_type(BufPrimitive.TRANSFORM3DF):
            return self._write_struct_raw(ST_TRANSFORM3DF, value)
    def write_transform3dd(self, value: Transform3D) -> Self:
        with self.__writing_type(BufPrimitive.TRANSFORM3DD):
            return self._write_struct_raw(ST_TRANSFORM3DD, value)
    # endregion

    def read_bool(self) -> bool:
        byte = self._buffer[self._pos]
        self._pos += 1
        if byte & 0b1111_1110:
            raise ValueError(f"Invalid boolean byte: {byte}")
        return bool(byte)

    def write_bool(self, value: bool) -> Self:
        with self.__writing_type(BufPrimitive.BOOLEAN):
            self._allocate_to_fit(1)
            self._buffer[self._size] = 1 if value else 0
            self._size += 1
        return self

    def read_bool_tuple(self) -> tuple[bool, ...]:
        return tuple(self._read_many(self.read_bool))
    def write_bool_sequence(self, values: Sequence[bool]) -> Self:
        with self.__writing_type(BufPrimitive.BOOLEAN_ARRAY):
            self._allocate_to_fit(len(values))
            return self._write_many(values, self.write_bool)

    def read_blob(self) -> bytes:
        return self._read_memoryview(self.read_uvarint()).tobytes()

    def write_blob(self, blob: bytes | bytearray | memoryview) -> Self:
        with self.__writing_type(BufPrimitive.BLOB):
            if isinstance(blob, bytes | bytearray):
                self._write_buffer(blob)
            elif isinstance(blob, memoryview):
                self._write_buffer(blob, blob.nbytes)
            else:
                raise TypeError(blob)
        return self

    def read_str(self) -> str:
        return str(self._read_memoryview(self.read_uvarint()), encoding="utf8")

    def write_str(self, value: str) -> Self:
        with self.__writing_type(BufPrimitive.STRING):
            data = value.encode("utf8")
            self.write_uvarint(len(data))
            self._write_buffer(data)
        return self

    def read_ascii(self) -> str:
        return str(self._read_memoryview(self.read_uvarint()), encoding="ascii")

    def write_ascii(self, value: str) -> Self:
        with self.__writing_type(BufPrimitive.STRING):
            data = value.encode("ascii")
            self.write_uvarint(len(data))
            self._write_buffer(data)
        return self

    def read_resloc(self) -> ResLoc:
        return ResLoc(self.read_ascii())

    def write_resloc(self, value: ResLoc) -> Self:
        with self.__writing_type(BufPrimitive.RESOURCE_LOCATION):
            self.write_ascii(str(value))
        return self

    def read_uuid(self) -> UUID:
        msb, lsb = self._read_struct(ST_UUID)
        return UUID(int=msb << 64 | lsb)

    def write_uuid(self, value: UUID) -> Self:
        with self.__writing_type(BufPrimitive.UUID):
            self._write_struct(ST_UUID, value.int >> 64, value.int & 0xFFFFFFFF_FFFFFFFF)
        return self

    def read_nbt(self) -> AnyNBT:
        # TODO: big hack to workaround Amulet-NBT not supporting unnamed tag,
        #   remove when https://github.com/Amulet-Team/Amulet-NBT/issues/88 resolves
        data = bytes((self._buffer[self._pos], 0, 0))
        self._pos += 1
        data += self.to_read_view.tobytes()
        # noinspection PyArgumentList
        ctx = ReadOffset()
        result = amulet_nbt.read_nbt(
            data,
            compressed=False,
            little_endian=False,
            string_encoding=amulet_nbt.utf8_encoding,
            read_offset=ctx
        )
        self._pos += ctx.offset
        return result.tag

    def read_nbt_compound(self) -> CompoundTag:
        tag = self.read_nbt()
        if not isinstance(tag, CompoundTag):
            raise ValueError(f"Expected a compound tag, got {tag}")
        return tag

    def write_nbt(self, value: AnyNBT) -> Self:
        with self.__writing_type(BufPrimitive.NBT):
            data = value.to_nbt(
                compressed=False,
                little_endian=False,
                string_encoding=amulet_nbt.utf8_encoding
            )
            # TODO: workaround Amulet-NBT not supporting unnamed tag,
            #   remove when https://github.com/Amulet-Team/Amulet-NBT/issues/88 resolves
            self._allocate_to_fit(len(data) - 2)
            self._buffer[self._size] = data[0]
            self._size += 1
            self._write_buffer(memoryview(data)[3:])
        return self

    def read_blockstate(self) -> Block:
        block: Block = self.read_using_id_map(self._client.reg_id_maps.block)
        for _ in range(self.read_varint()):
            name = self.read_str()
            prop = block._properties[name]
            block[name] = prop.deserialize(self.read_str())
        return block

    def write_blockstate(self, block: Block, all=False) -> Self:
        with self.__writing_type(BufPrimitive.BLOCK_STATE):
            self.write_using_id_map(self._client.reg_id_maps.block, block)

            props = block._all_properties() if all else block._assigned_properties()
            num = block._num_properties() if all else len(block._assigned_properties())

            self.write_varint(num)
            for prop_name in props:
                self.write_str(prop_name)
                self.write_str(block._get_property(prop_name).serialize(block[prop_name]))
        return self

    def read_fluidstate(self) -> ...:
        raise NotImplemented

    def write_fluidstate(self, value) -> Self:
        with self.__writing_type(BufPrimitive.FLUID_STATE):
            raise NotImplemented
        return self

    def read_using_id_map[T](self, id_map: IdMap[T]) -> T:
        return id_map[self.read_varint()]

    def write_using_id_map[T](self, id_map: IdMap[T], obj: T) -> Self:
        self.write_varint(id_map[obj])
        return self


class BufPrimitive(enum.Enum):
    BYTE = 1, ByteBuf.read_byte, ByteBuf.write_byte
    SHORT = 2, ByteBuf.read_short, ByteBuf.write_short
    INT = 3, ByteBuf.read_int, ByteBuf.write_int
    LONG = 4, ByteBuf.read_long, ByteBuf.write_long
    VARINT = 5, ByteBuf.read_varint, ByteBuf.write_varint
    VARLONG = 6, ByteBuf.read_varlong, ByteBuf.write_varlong

    UBYTE = 7, ByteBuf.read_ubyte, ByteBuf.write_ubyte
    USHORT = 8, ByteBuf.read_ushort, ByteBuf.write_ushort
    UINT = 9, ByteBuf.read_uint, ByteBuf.write_uint
    ULONG = 10, ByteBuf.read_ulong, ByteBuf.write_ulong
    UVARINT = 11, ByteBuf.read_uvarint, ByteBuf.write_uvarint
    UVARLONG = 12, ByteBuf.read_uvarlong, ByteBuf.write_uvarlong

    FLOAT = 13, ByteBuf.read_float, ByteBuf.write_float
    DOUBLE = 14, ByteBuf.read_double, ByteBuf.write_double

    BOOLEAN = 15, ByteBuf.read_bool, ByteBuf.write_bool

    BYTE_ARRAY = 16, ByteBuf.read_byte_array, ByteBuf.write_byte_array
    SHORT_ARRAY = 17, ByteBuf.read_short_array, ByteBuf.write_short_array
    INT_ARRAY = 18, ByteBuf.read_int_array, ByteBuf.write_int_array
    LONG_ARRAY = 19, ByteBuf.read_long_array, ByteBuf.write_long_array
    VARINT_ARRAY = 20, ByteBuf.read_varint_tuple, ByteBuf.write_varint_sequence
    VARLONG_ARRAY = 21, ByteBuf.read_varlong_tuple, ByteBuf.write_varlong_sequence

    UBYTE_ARRAY = 22, ByteBuf.read_ubyte_array, ByteBuf.write_ubyte_array
    USHORT_ARRAY = 23, ByteBuf.read_ushort_array, ByteBuf.write_ushort_array
    UINT_ARRAY = 24, ByteBuf.read_uint_array, ByteBuf.write_uint_array
    ULONG_ARRAY = 25, ByteBuf.read_ulong_array, ByteBuf.write_ulong_array
    UVARINT_ARRAY = 26, ByteBuf.read_uvarint_tuple, ByteBuf.write_uvarint_sequence
    UVARLONG_ARRAY = 27, ByteBuf.read_uvarlong_tuple, ByteBuf.write_uvarlong_sequence

    FLOAT_ARRAY = 28, ByteBuf.read_float_array, ByteBuf.write_float_array
    DOUBLE_ARRAY = 29, ByteBuf.read_double_array, ByteBuf.write_double_array

    BOOLEAN_ARRAY = 30, ByteBuf.read_bool_tuple, ByteBuf.write_bool_sequence

    VEC2I = 31, ByteBuf.read_vec2i, ByteBuf.write_vec2i
    VEC2F = 32, ByteBuf.read_vec2f, ByteBuf.write_vec2f
    VEC2D = 33, ByteBuf.read_vec2d, ByteBuf.write_vec2d
    VEC3I = 34, ByteBuf.read_vec3i, ByteBuf.write_vec3i
    VEC3F = 35, ByteBuf.read_vec3f, ByteBuf.write_vec3f
    VEC3D = 36, ByteBuf.read_vec3d, ByteBuf.write_vec3d
    TRANSFORM2DF = 37, ByteBuf.read_transform2df, ByteBuf.write_transform2df
    TRANSFORM2DD = 38, ByteBuf.read_transform2dd, ByteBuf.write_transform2dd
    TRANSFORM3DF = 39, ByteBuf.read_transform3df, ByteBuf.write_transform3df
    TRANSFORM3DD = 40, ByteBuf.read_transform3dd, ByteBuf.write_transform3dd

    BLOB = 41, ByteBuf.read_blob, ByteBuf.write_blob
    STRING = 42, ByteBuf.read_str, ByteBuf.write_str

    RESOURCE_LOCATION = 43, ByteBuf.read_resloc, ByteBuf.write_resloc

    UUID = 44, ByteBuf.read_uuid, ByteBuf.write_uuid

    NBT = 45, ByteBuf.read_nbt, ByteBuf.write_nbt

    BLOCK_STATE = 46, ByteBuf.read_blockstate, ByteBuf.write_blockstate
    FLUID_STATE = 47, ByteBuf.read_fluidstate, ByteBuf.write_fluidstate
