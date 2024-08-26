package dev.shblock.codecraft.core.utils

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.EncoderException
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.Vec3i
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.Tag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.StateHolder
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3i
import java.nio.ByteBuffer
import java.util.*
import java.util.zip.CRC32C


typealias CCEncodingException = EncoderException
typealias CCDecodingException = DecoderException

@Suppress("unused", "MemberVisibilityCanBePrivate")
class CCByteBuf(private val data: FriendlyByteBuf) {
    constructor(arr: ByteArray) : this(Unpooled.wrappedBuffer(arr))

    constructor(buf: ByteBuf) : this(FriendlyByteBuf(buf))

    constructor(initialSize: Int = 0) : this(Unpooled.buffer(initialSize))

    class Type<T> internal constructor(val id: Byte, internal val reader: CCByteBuf.() -> T)

    companion object {
        object Types {
            private val _type_by_id_map = mutableMapOf<Byte, Type<*>>()

            private fun <T> make(id: Byte, reader: CCByteBuf.() -> T) =
                Type(id, reader).also { _type_by_id_map[id] = it }

            val BYTE = make(0, CCByteBuf::readByte)
            val INT = make(1, CCByteBuf::readInt)
            val LONG = make(2, CCByteBuf::readLong)
            val FLOAT = make(3, CCByteBuf::readFloat)
            val VARINT = make(4, CCByteBuf::readVarInt)

            val BYTE_ARRAY = make(5, CCByteBuf::readByteArray)
            val INT_ARRAY = make(6, CCByteBuf::readIntArray)
            val LONG_ARRAY = make(7, CCByteBuf::readLongArray)
            val FLOAT_ARRAY = make(8, CCByteBuf::readFloatArray)
            val VARINT_ARRAY = make(9, CCByteBuf::readVarIntArray)

            val BOOL = make(10, CCByteBuf::readBool)

            val STR = make(11, CCByteBuf::readStr)

            val VEC3I = make(12, CCByteBuf::readVec3i)
            val VEC3 = make(13, CCByteBuf::readVec3)

            val RES_LOC = make(14, CCByteBuf::readResLoc)

            val UUID = make(15, CCByteBuf::readUUID)

            val NBT = make(16, CCByteBuf::readNBT)

            val BLOCK_STATE = make(17, CCByteBuf::readBlockState)
//            val BLOCK_HIT_RESULT = make(17, CCByteBuf::readBlockHitResult)

            operator fun get(id: Byte) = _type_by_id_map[id]
        }
    }

    fun readDynamic(): Pair<Any?, Type<*>> {
        val id = readByte()
        val type = Types[id] ?: throw CCDecodingException("Invalid type id: $id")
        return Pair(this.run(type.reader), type)
    }

    private var isWriteDynamic = false
    fun writeDynamic(inner: CCByteBuf.() -> Unit) {
        isWriteDynamic = true
        this.inner()
        isWriteDynamic = false
    }

    private inline fun <T> reading(reader: CCByteBuf.() -> T): T {
        try {
            return this.reader()
        } catch (e: CCDecodingException) {
            throw e
        } catch (e: IndexOutOfBoundsException) {
            throw CCDecodingException("Buffer overflow")
        } catch (e: Exception) {
            throw CCDecodingException(e)
        }
    }

    private var inWritingType = false // avoid writing multiple type bytes when in writeDynamic
    private inline fun <T> writingType(type: Type<T>, writer: CCByteBuf.() -> Unit): CCByteBuf {
        try {
            if (!inWritingType && isWriteDynamic) data.writeByte(type.id)
            inWritingType = true
            this.writer()
        } catch (e: CCEncodingException) {
            throw e
        } catch (e: IndexOutOfBoundsException) {
            throw CCEncodingException("Buffer underflow", e)
        } catch (e: Exception) {
            throw CCEncodingException(e)
        } finally {
            inWritingType = false
        }
        return this
    }

    fun readByte() = reading { data.readByte() }
    fun writeByte(value: Byte) = writingType(Types.BYTE) { data.writeByte(value) }
    fun readByteArray(): ByteArray = reading { data.readByteArray() }
    fun writeByteArray(value: ByteArray) = writingType(Types.BYTE_ARRAY) { data.writeByteArray(value) }

    fun readInt() = reading { data.readInt() }
    fun writeInt(value: Int) = writingType(Types.INT) { data.writeInt(value) }
    fun readIntArray() = reading { IntArray(readVarInt()) { readInt() } }
    fun writeIntArray(value: IntArray) =
        writingType(Types.INT_ARRAY) {
            writeVarInt(value.size)
            value.forEach(::writeInt)
        }

    fun readLong() = reading { data.readLong() }
    fun writeLong(value: Long) = writingType(Types.LONG) { data.writeLong(value) }
    fun readLongArray(): LongArray = reading { data.readLongArray() }
    fun writeLongArray(value: LongArray) = writingType(Types.LONG_ARRAY) { data.writeLongArray(value) }

    fun readFloat() = reading { data.readDouble() }
    fun writeFloat(value: Double) = writingType(Types.FLOAT) { data.writeDouble(value) }
    fun readFloatArray() = reading { DoubleArray(readVarInt()) { readFloat() } }
    fun writeFloatArray(value: DoubleArray) =
        writingType(Types.FLOAT_ARRAY) {
            writeVarInt(value.size)
            value.forEach(::writeFloat)
        }

    fun readVarInt() = reading { data.readVarInt() }
    fun writeVarInt(value: Int) = writingType(Types.VARINT) { data.writeVarInt(value) }
    fun readVarIntArray(): IntArray = reading { data.readVarIntArray() }
    fun writeVarIntArray(value: IntArray) = writingType(Types.VARINT_ARRAY) { data.writeVarIntArray(value) }

    fun readBool() = reading { data.readBoolean() }
    fun writeBool(value: Boolean) = writingType(Types.BOOL) { data.writeBoolean(value) }

    fun readVec3i() = reading { Vector3i(data.readInt(), data.readInt(), data.readInt()) }
    fun readBlockPos() = reading { BlockPos(data.readInt(), data.readInt(), data.readInt()) }
    fun writeVec3i(value: Vector3i) =
        writingType(Types.VEC3I) {
            data.writeInt(value.x)
            data.writeInt(value.y)
            data.writeInt(value.z)
        }

    fun writeVec3i(value: Vec3i) =
        writingType(Types.VEC3I) {
            data.writeInt(value.x)
            data.writeInt(value.y)
            data.writeInt(value.z)
        }

    fun readVec3() = reading { Vector3d(data.readDouble(), data.readDouble(), data.readDouble()) }
    fun writeVec3(value: Vector3d) =
        writingType(Types.VEC3) {
            data.writeDouble(value.x)
            data.writeDouble(value.y)
            data.writeDouble(value.z)
        }

    fun writeVec3(value: Vec3) =
        writingType(Types.VEC3) {
            data.writeDouble(value.x)
            data.writeDouble(value.y)
            data.writeDouble(value.z)
        }

    fun readStr(): String = reading { data.readUtf(Int.MAX_VALUE) }
    fun writeStr(value: String) = writingType(Types.STR) { data.writeUtf(value) }

    fun readResLoc(): ResourceLocation = reading { data.readResourceLocation() }
    fun writeResLoc(value: ResourceLocation) = writingType(Types.RES_LOC) { data.writeResourceLocation(value) }

    fun <T> readResKey(key: ResourceKey<out Registry<T>>): ResourceKey<T> = reading { data.readResourceKey(key) }
    fun writeResKey(value: ResourceKey<*>) = writingType(Types.RES_LOC) { data.writeResourceKey(value) }

    fun readUUID(): UUID = reading { data.readUUID() }
    fun writeUUID(value: UUID) = writingType(Types.UUID) { data.writeUUID(value) }

    fun readNBT(quota: Long = Long.MAX_VALUE): Tag? = reading { data.readNbt(NbtAccounter.create(quota)) }
    fun readNBTCompound(quota: Long = Long.MAX_VALUE): CompoundTag {
        val tag = data.readNbt(NbtAccounter.create(quota))
        return if (tag is CompoundTag) tag else throw CCDecodingException("Not a compound tag: $tag")
    }

    fun writeNBT(value: Tag?) = writingType(Types.NBT) { data.writeNbt(value) }

    //TODO
//    fun readBlockHitResult(): BlockHitResult = reading { data.readBlockHitResult() }
//    fun writeBlockHitResult(value: BlockHitResult) =
//        writingType(Types.BLOCK_HIT_RESULT) { data.writeBlockHitResult(value) }

    fun <T> readUsingRegistry(registry: Registry<T>): Holder.Reference<T>? {
        return registry.getHolder(readVarInt()).orElse(null)
    }

    fun <T> readUsingRegistryOrThrow(registry: Registry<T>): Holder.Reference<T> {
        val id = readVarInt()
        return readUsingRegistry(registry) ?: throw CCDecodingException("Invalid id $id for registry $registry")
    }

    fun <T> writeUsingRegistry(obj: T, registry: Registry<in T>) = this.apply {
        val id = try {
            registry.getIdOrThrow(obj)
        } catch (e: Exception) {
            throw CCEncodingException("Registry $registry doesn't contain object $obj", e)
        }
        writeVarInt(id)
    }

    fun <T> writeUsingRegistry(key: ResourceLocation, registry: Registry<T>) = this.apply {
        val obj: T = registry.get(key) ?: throw CCEncodingException("Registry $registry doesn't contain key $key")
        writeUsingRegistry(obj, registry)
    }

    private fun <SH : StateHolder<*, SH>> readStateHolderValues(
        states: SH,
        stateDef: StateDefinition<*, SH>
    ): SH {
        @Suppress("NAME_SHADOWING") var states = states
        repeat(readVarInt()) {
            val propName = readStr()
            val valueStr = readStr()

            @Suppress("UNCHECKED_CAST")
            val prop = (stateDef.getProperty(readStr())
                ?: throw CCDecodingException("No property \"$propName\"")) as Property<Comparable<Any>>

            val value = prop.getValue(valueStr)
                .orElseThrow { CCDecodingException("Failed to read property \"$prop\" from string \"$valueStr\"") }
                as Comparable<Any>
            states = states.setValue(prop, value)
        }
        return states
    }

    private fun writeStateHolderValues(states: StateHolder<*, *>) {
        @Suppress("UNCHECKED_CAST")
        val values = states.values as Map<Property<in Comparable<Any>>, Comparable<Any>>
        writeVarInt(values.size)
        for ((prop, value) in values) {
            writeStr(prop.name)
            writeStr(prop.getName(value))
        }
    }

    fun readBlockState(): BlockState = reading {
        val block = readUsingRegistryOrThrow(BuiltInRegistries.BLOCK).value()
        return readStateHolderValues(block.defaultBlockState(), block.stateDefinition)
    }

    fun writeBlockState(blockState: BlockState) = writingType(Types.BLOCK_STATE) {
        writeUsingRegistry(blockState.block, BuiltInRegistries.BLOCK)
        writeStateHolderValues(blockState)
    }

    fun seek(bytes: Int) = this.apply { data.skipBytes(bytes) }
    fun writePadding(bytes: Int) = this.apply { data.writeZero(bytes) }

    fun copy() = CCByteBuf(data.copy())
    fun copy(index: Int = data.readerIndex(), length: Int) = CCByteBuf(data.copy(index, length))
    fun slice() = CCByteBuf(data.slice())
    fun slice(index: Int = data.readerIndex(), length: Int) = CCByteBuf(data.slice(index, length))
    fun retainedSlice() = CCByteBuf(data.retainedSlice())
    fun retainedSlice(index: Int = data.readerIndex(), length: Int) = CCByteBuf(data.retainedSlice(index, length))
    fun readSlice(length: Int): ByteBuf = data.readSlice(length)
    fun readRetainedSlice(length: Int): ByteBuf = data.readRetainedSlice(length)
    fun duplicate() = CCByteBuf(data.duplicate())
    fun retainedDuplicate() = CCByteBuf(data.duplicate())
    fun hasArray() = data.hasArray()
    fun rawArray(): ByteArray = data.array()
    fun rawArrayOffset() = data.arrayOffset()
    fun nioBuffer(): ByteBuffer = data.nioBuffer()
    fun checksum(): Long {
        val cs = CRC32C()
        data.markReaderIndex()
        data.readerIndex(0)
        if (hasArray()) {
            cs.update(rawArray(), rawArrayOffset(), data.writerIndex())
        } else {
            data.forEachByte { cs.update(it.toInt()); true }
        }
        data.resetReaderIndex()
        return cs.value
    }

    val readableBytes: Int
        get() = data.readableBytes()
    val writableBytes: Int
        get() = data.writableBytes()

    override fun hashCode() = data.hashCode()
    operator fun compareTo(other: CCByteBuf) = data.compareTo(other.data)
    override operator fun equals(other: Any?) = data == other
    override fun toString() = data.toString()

    val refCnt: Int
        get() = data.refCnt()

    fun retain(cnt: Int = 1) = this.apply { data.retain(cnt) }
    fun release(cnt: Int = 1) = data.release(cnt)
}