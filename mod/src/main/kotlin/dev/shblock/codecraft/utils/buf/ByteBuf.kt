@file:OptIn(ExperimentalUnsignedTypes::class)
@file:Suppress("OVERRIDE_BY_INLINE", "NOTHING_TO_INLINE", "unused")

package dev.shblock.codecraft.utils.buf

import dev.shblock.codecraft.utils.buf.Buf.Primitive
import dev.shblock.codecraft.utils.onEach
import dev.shblock.codecraft.utils.self
import dev.shblock.codecraft.utils.toIntChecked
import kotlinx.io.*
import kotlinx.io.bytestring.ByteString
import kotlinx.io.unsafe.UnsafeBufferOperations
import kotlinx.io.unsafe.withData
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.StateHolder
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.level.material.FluidState
import org.joml.*
import java.util.*
import java.util.zip.CRC32C
import kotlin.math.min


class ByteBuf<SELF : ByteBuf<SELF>>(@PublishedApi internal val buffer: Buffer) : Buf<SELF> {
    constructor() : this(Buffer())

    constructor(array: ByteArray, start: Int = 0, end: Int = array.size) : this(Buffer()) {
        @OptIn(UnsafeIoApi::class)
        UnsafeBufferOperations.moveToTail(buffer, array, start, end)
    }

    //region Simple Types
    //@formatter:off
    override inline fun readByte() = buffer.readByte()
    override inline fun readShort() = buffer.readShort()
    override inline fun readInt() = buffer.readInt()
    override inline fun readLong() = buffer.readLong()
    override inline fun readVarInt() = buffer.readVarInt()
    override inline fun readVarLong() = buffer.readVarLong()
    override inline fun readUByte() = buffer.readUByte()
    override inline fun readUShort() = buffer.readUShort()
    override inline fun readUInt() = buffer.readUInt()
    override inline fun readULong() = buffer.readULong()
    override inline fun readUVarInt() = buffer.readUVarInt()
    override inline fun readUVarLong() = buffer.readUVarLong()
    override inline fun readFloat() = buffer.readFloat()
    override inline fun readDouble() = buffer.readDouble()
    override inline fun readBool() = buffer.readBooleanStrict()

    override inline fun writeByte(value: Byte) = _writingType(Primitive.BYTE) { buffer.writeByte(value) }
    override inline fun writeShort(value: Short) = _writingType(Primitive.SHORT) { buffer.writeShort(value) }
    override inline fun writeInt(value: Int) = _writingType(Primitive.INT) { buffer.writeInt(value) }
    override inline fun writeLong(value: Long) = _writingType(Primitive.LONG) { buffer.writeLong(value) }
    override inline fun writeVarInt(value: Int) = _writingType(Primitive.INT) { buffer.writeVarInt(value) }
    override inline fun writeVarLong(value: Long) = _writingType(Primitive.LONG) { buffer.writeVarLong(value) }
    override inline fun writeUByte(value: UByte) = _writingType(Primitive.UBYTE) { buffer.writeUByte(value) }
    override inline fun writeUShort(value: UShort) = _writingType(Primitive.USHORT) { buffer.writeUShort(value) }
    override inline fun writeUInt(value: UInt) = _writingType(Primitive.UINT) { buffer.writeUInt(value) }
    override inline fun writeULong(value: ULong) = _writingType(Primitive.ULONG) { buffer.writeULong(value) }
    override inline fun writeUVarInt(value: UInt) = _writingType(Primitive.UINT) { buffer.writeUVarInt(value) }
    override inline fun writeUVarLong(value: ULong) = _writingType(Primitive.ULONG) { buffer.writeUVarLong(value) }
    override inline fun writeFloat(value: Float) = _writingType(Primitive.FLOAT) { buffer.writeFloat(value) }
    override inline fun writeDouble(value: Double) = _writingType(Primitive.DOUBLE) { buffer.writeDouble(value) }
    override inline fun writeBool(value: Boolean) = _writingType(Primitive.BOOLEAN) { buffer.writeBoolean(value) }

    override fun readByteArray() = ByteArray(buffer.readUVarInt().toIntChecked()) { buffer.readByte() }
    override fun readShortArray() = ShortArray(buffer.readUVarInt().toIntChecked()) { buffer.readShort() }
    override fun readIntArray() = IntArray(buffer.readUVarInt().toIntChecked()) { buffer.readInt() }
    override fun readLongArray() = LongArray(buffer.readUVarInt().toIntChecked()) { buffer.readLong() }
    override fun readVarIntArray() = IntArray(buffer.readUVarInt().toIntChecked()) { buffer.readInt() }
    override fun readVarLongArray() = LongArray(buffer.readUVarInt().toIntChecked()) { buffer.readLong() }
    override fun readUByteArray() = UByteArray(buffer.readUVarInt().toIntChecked()) { buffer.readUByte() }
    override fun readUShortArray() = UShortArray(buffer.readUVarInt().toIntChecked()) { buffer.readUShort() }
    override fun readUIntArray() = UIntArray(buffer.readUVarInt().toIntChecked()) { buffer.readUInt() }
    override fun readULongArray() = ULongArray(buffer.readUVarInt().toIntChecked()) { buffer.readULong() }
    override fun readUVarIntArray() = UIntArray(buffer.readUVarInt().toIntChecked()) { buffer.readUInt() }
    override fun readUVarLongArray() = ULongArray(buffer.readUVarInt().toIntChecked()) { buffer.readULong() }
    override fun readFloatArray() = FloatArray(buffer.readUVarInt().toIntChecked()) { buffer.readFloat() }
    override fun readDoubleArray() = DoubleArray(buffer.readUVarInt().toIntChecked()) { buffer.readDouble() }
    override fun readBoolArray() = BooleanArray(buffer.readUVarInt().toIntChecked()) { buffer.readBooleanStrict() }

    override fun writeByteArray(value: ByteArray) = _writingType(Primitive.BYTE_ARRAY) { buffer.writeUVarInt(value.size.toUInt()); value.forEach(buffer::writeByte) }
    override fun writeShortArray(value: ShortArray) = _writingType(Primitive.SHORT_ARRAY) { buffer.writeUVarInt(value.size.toUInt()); value.forEach(buffer::writeShort) }
    override fun writeIntArray(value: IntArray) = _writingType(Primitive.INT_ARRAY) { buffer.writeUVarInt(value.size.toUInt()); value.forEach(buffer::writeInt) }
    override fun writeLongArray(value: LongArray) = _writingType(Primitive.LONG_ARRAY) { buffer.writeUVarInt(value.size.toUInt()); value.forEach(buffer::writeLong) }
    override fun writeVarIntArray(value: IntArray) = _writingType(Primitive.INT_ARRAY) { buffer.writeUVarInt(value.size.toUInt()); value.forEach(buffer::writeInt) }
    override fun writeVarLongArray(value: LongArray) = _writingType(Primitive.LONG_ARRAY) { buffer.writeUVarInt(value.size.toUInt()); value.forEach(buffer::writeLong) }
    override fun writeUByteArray(value: UByteArray) = _writingType(Primitive.UBYTE_ARRAY) { buffer.writeUVarInt(value.size.toUInt()); value.forEach(buffer::writeUByte) }
    override fun writeUShortArray(value: UShortArray) = _writingType(Primitive.USHORT_ARRAY) { buffer.writeUVarInt(value.size.toUInt()); value.forEach(buffer::writeUShort) }
    override fun writeUIntArray(value: UIntArray) = _writingType(Primitive.UINT_ARRAY) { buffer.writeUVarInt(value.size.toUInt()); value.forEach(buffer::writeUInt) }
    override fun writeULongArray(value: ULongArray) = _writingType(Primitive.ULONG_ARRAY) { buffer.writeUVarInt(value.size.toUInt()); value.forEach(buffer::writeULong) }
    override fun writeUVarIntArray(value: UIntArray) = _writingType(Primitive.UINT_ARRAY) { buffer.writeUVarInt(value.size.toUInt()); value.forEach(buffer::writeUInt) }
    override fun writeUVarLongArray(value: ULongArray) = _writingType(Primitive.ULONG_ARRAY) { buffer.writeUVarInt(value.size.toUInt()); value.forEach(buffer::writeULong) }
    override fun writeFloatArray(value: FloatArray) = _writingType(Primitive.FLOAT_ARRAY) { buffer.writeUVarInt(value.size.toUInt()); value.forEach(buffer::writeFloat) }
    override fun writeDoubleArray(value: DoubleArray) = _writingType(Primitive.DOUBLE_ARRAY) { buffer.writeUVarInt(value.size.toUInt()); value.forEach(buffer::writeDouble) }
    override fun writeBoolArray(value: BooleanArray) = _writingType(Primitive.BOOLEAN_ARRAY) { buffer.writeUVarInt(value.size.toUInt()); value.forEach(buffer::writeBoolean) }

    override fun readVec2i() = Vector2i(buffer.readInt(), buffer.readInt())
    override fun readVec2f() = Vector2f(buffer.readFloat(), buffer.readFloat())
    override fun readVec2d() = Vector2d(buffer.readDouble(), buffer.readDouble())
    override fun readVec3i() = Vector3i(buffer.readInt(), buffer.readInt(), buffer.readInt())
    override fun readVec3f() = Vector3f(buffer.readFloat(), buffer.readFloat(), buffer.readFloat())
    override fun readVec3d() = Vector3d(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
    override fun readTransform2Df() = Matrix3x2f(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat())
    override fun readTransform2Dd() = Matrix3x2d(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
    override fun readTransform3Df() = Matrix4x3f(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat())
    override fun readTransform3Dd() = Matrix4x3d(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble())

    override fun writeVec2i(x: Int, y: Int) = self.also { buffer.writeInt(x); buffer.writeInt(y) }
    override fun writeVec2f(x: Float, y: Float) = self.also { buffer.writeFloat(x); buffer.writeFloat(y) }
    override fun writeVec2d(x: Double, y: Double) = self.also { buffer.writeDouble(x); buffer.writeDouble(y) }
    override fun writeVec3i(x: Int, y: Int, z: Int) = self.also { buffer.writeInt(x); buffer.writeInt(y); buffer.writeInt(z) }
    override fun writeVec3f(x: Float, y: Float, z: Float) = self.also { buffer.writeFloat(x); buffer.writeFloat(y); buffer.writeFloat(z) }
    override fun writeVec3d(x: Double, y: Double, z: Double) = self.also { buffer.writeDouble(x); buffer.writeDouble(y); buffer.writeDouble(z) }
    override fun writeVec2i(value: Vector2i) = self.also { with(value) { onEach(x, y, buffer::writeInt) } }
    override fun writeVec2f(value: Vector2f) = self.also { with(value) { onEach(x, y, buffer::writeFloat) } }
    override fun writeVec2d(value: Vector2d) = self.also { with(value) { onEach(x, y, buffer::writeDouble) } }
    override fun writeVec3i(value: Vector3i) = self.also { with(value) { onEach(x, y, z, buffer::writeInt) } }
    override fun writeVec3f(value: Vector3f) = self.also { with(value) { onEach(x, y, z, buffer::writeFloat) } }
    override fun writeVec3d(value: Vector3d) = self.also { with(value) { onEach(x, y, z, buffer::writeDouble) } }
    override fun writeTransform2Df(value: Matrix3x2f) = self.also { with(value) { onEach(m00, m01, m10, m11, m20, m21, buffer::writeFloat) } }
    override fun writeTransform2Dd(value: Matrix3x2d) = self.also { with(value) { onEach(m00, m01, m10, m11, m20, m21, buffer::writeDouble) } }
    override fun writeTransform3Df(value: Matrix4x3f) = self.also { with(value) { onEach(m00(), m01(), m02(), m10(), m11(), m12(), m20(), m21(), m22(), m30(), m31(), m32(), buffer::writeFloat) } }
    override fun writeTransform3Dd(value: Matrix4x3d) = self.also { with(value) { onEach(m00(), m01(), m02(), m10(), m11(), m12(), m20(), m21(), m22(), m30(), m31(), m32(), buffer::writeDouble) } }
    //@formatter:on
    //endregion

    override fun readBlob() = buffer.readByteString(buffer.readUVarInt().toIntChecked())

    override fun writeBlob(value: ByteString, start: Int, end: Int) =
        _writingType(Primitive.BLOB) { buffer.writeUVarInt((end - start).toUInt()); buffer.write(value, start, end) }

    override fun readStr() = buffer.readString(byteCount = buffer.readUVarInt().toLong())

    override fun writeStr(value: String, start: Int, end: Int) =
        _writingType(Primitive.STRING) {
            val data = Buffer().apply { writeString(value, start, end) }
            if (data.size.toULong() and 0xFFFF_FFFF_8000_0000uL != 0uL)
                throw BufException("String too big: ${data.size} bytes encoded")
            buffer.writeUVarInt(data.size.toUInt())
            buffer.write(data, data.size)
        }

    override fun writeAscii(value: String, start: Int, end: Int) =
        _writingType(Primitive.STRING) {
            buffer.writeUVarInt(value.length.toUInt())
            val oldSize = buffer.size
            buffer.writeString(value, start, end)
            check(buffer.size - oldSize == (end - start).toLong()) { "not ascii-only" }
        }

    override fun readResLoc(): ResourceLocation =
        ResourceLocation.parse(buffer.readString(buffer.readUVarInt().toLong()))

    override fun writeResLoc(value: ResourceLocation) = _writingType(Primitive.RESOURCE_LOCATION) {
        value.toString().also {
            buffer.writeUVarInt(it.length.toUInt())
            buffer.writeString(it)
        }
    }

    override fun readUUID() = UUID(buffer.readLong(), buffer.readLong())

    override fun writeUUID(value: UUID) = _writingType(Primitive.UUID) {
        buffer.writeLong(value.mostSignificantBits)
        buffer.writeLong(value.leastSignificantBits)
    }

    private val bufferDataInput by lazy { buffer.asCustomDataInput() }
    private val bufferDataOutput by lazy { buffer.asCustomDataOutput() }

    override fun readNBT(): Tag = NbtIo.readAnyTag(bufferDataInput, NbtAccounter.unlimitedHeap())

    override fun writeNBT(value: Tag) = self.also { NbtIo.writeAnyTag(value, bufferDataOutput) }

    private fun <SH : StateHolder<*, SH>> readStateHolderValues(
        states: SH,
        stateDef: StateDefinition<*, SH>
    ): SH {
        var s = states
        repeat(readUByte().toInt()) {
            val propName = readStr()
            val valueStr = readStr()

            @Suppress("UNCHECKED_CAST")
            val prop = (stateDef.getProperty(readStr())
                ?: throw BufException("No property \"$propName\"")) as Property<Comparable<Any>>

            val value = prop.getValue(valueStr)
                .orElseThrow { BufException("Failed to read property \"$prop\" from string \"$valueStr\"") }
                as Comparable<Any>
            s = s.setValue(prop, value)
        }
        return s
    }

    private fun writeStateHolderValues(states: StateHolder<*, *>) {
        @Suppress("UNCHECKED_CAST")
        val values = states.values as Map<Property<in Comparable<Any>>, Comparable<Any>>
        writeUByte(values.size.toUByte())
        for ((prop, value) in values) {
            writeStr(prop.name)
            writeStr(prop.getName(value))
        }
    }

    override fun readBlockState(): BlockState {
        val block = readByRegistryOrThrow(BuiltInRegistries.BLOCK).value()
        return readStateHolderValues(block.defaultBlockState(), block.stateDefinition)
    }

    override fun writeBlockState(value: BlockState) = self.also {
        writeByRegistry(value.block, BuiltInRegistries.BLOCK)
        writeStateHolderValues(value)
    }

    override fun readFluidState(): FluidState {
        val fluid = readByRegistryOrThrow(BuiltInRegistries.FLUID).value()
        return readStateHolderValues(fluid.defaultFluidState(), fluid.stateDefinition)
    }

    override fun writeFluidState(value: FluidState) = self.also {
        buffer.snapshot()
        writeByRegistry(value.type, BuiltInRegistries.FLUID)
        writeStateHolderValues(value)
    }

    override fun checksum(): ULong {
        val cs = CRC32C()
        @OptIn(UnsafeIoApi::class)
        UnsafeBufferOperations.forEachSegment(buffer) { ctx, segment ->
            ctx.withData(segment) { array, start, end ->
                cs.update(array, start, end - start)
            }
        }
        return cs.value.toULong()
    }

    override fun append(buf: SELF) = self.also {
        buffer.write(buf.buffer, buf.buffer.size)
    }

    override fun clear() = self.also { buffer.clear() }

    override inline val exhausted get() = buffer.exhausted()

    inline val size get() = buffer.size

    /** Copy [bytes] bytes from this source and returns them as a byte array. */
    fun toByteArray(bytes: Int = -1): ByteArray {
        val aBytes = if (bytes != -1) bytes else buffer.size.toInt()
        require(0 <= aBytes && aBytes <= buffer.size) { "Invalid number of bytes: $bytes" }
        return ByteArray(aBytes).also { array ->
            var pos = 0
            @OptIn(UnsafeIoApi::class)
            UnsafeBufferOperations.iterate(buffer) { ctx, head ->
                var segment = head
                while (pos < aBytes) {
                    ctx.withData(segment!!) { data, start, dEnd ->
                        val end = min(dEnd, start + (aBytes - pos))
                        data.copyInto(array, pos, start, end)
                        pos += end - start
                    }
                    segment = ctx.next(segment)
                }
            }
        }
    }

    override fun toString(): String {
        return "ByteBuf(${buffer.toString().run { substring(6..lastIndex - 1) }})"
    }
}