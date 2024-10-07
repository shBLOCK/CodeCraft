@file:Suppress("unused", "NOTHING_TO_INLINE", "ClassName")
@file:OptIn(ExperimentalUnsignedTypes::class)

package dev.shblock.codecraft.utils.buf

import dev.shblock.codecraft.utils.GenericSelf
import dev.shblock.codecraft.utils.self
import it.unimi.dsi.fastutil.bytes.Byte2ReferenceArrayMap
import kotlinx.io.IOException
import kotlinx.io.bytestring.ByteString
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import org.joml.*
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties


typealias BufException = IOException


interface BufReader<SELF : BufReader<SELF>> : GenericSelf<SELF> {
    //region Base Read Methods
    fun readByte(): Byte
    fun readShort(): Short
    fun readInt(): Int
    fun readLong(): Long
    fun readVarInt(): Int
    fun readVarLong(): Long

    fun readUByte(): UByte
    fun readUShort(): UShort
    fun readUInt(): UInt
    fun readULong(): ULong
    fun readUVarInt(): UInt
    fun readUVarLong(): ULong

    fun readFloat(): Float
    fun readDouble(): Double

    fun readBool(): Boolean


    fun readByteArray(): ByteArray
    fun readShortArray(): ShortArray
    fun readIntArray(): IntArray
    fun readLongArray(): LongArray
    fun readVarIntArray(): IntArray
    fun readVarLongArray(): LongArray

    fun readUByteArray(): UByteArray
    fun readUShortArray(): UShortArray
    fun readUIntArray(): UIntArray
    fun readULongArray(): ULongArray
    fun readUVarIntArray(): UIntArray
    fun readUVarLongArray(): ULongArray

    fun readFloatArray(): FloatArray
    fun readDoubleArray(): DoubleArray

    fun readBoolArray(): BooleanArray


    fun readVec2i(): Vector2i
    fun readVec2f(): Vector2f
    fun readVec2d(): Vector2d
    fun readVec3i(): Vector3i
    fun readVec3f(): Vector3f
    fun readVec3d(): Vector3d
    fun readTransform2Df(): Matrix3x2f
    fun readTransform2Dd(): Matrix3x2d
    fun readTransform3Df(): Matrix4x3f
    fun readTransform3Dd(): Matrix4x3d


    fun readBlob(): ByteString
    fun readStr(): String

    fun readResLoc(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(readAscii(), readAscii())

    fun readUUID(): UUID = UUID(readLong(), readLong())

    fun readNBT(): Tag

    fun readBlockState(): BlockState
    fun readFluidState(): FluidState
    //endregion

    /**
     * Read an ascii-only string. This means that the number of bytes of the string is the same as the number of chars.
     *
     * This method is not responsable for checking if the string is actually ascii-only.
     */
    fun readAscii() = readStr()

    /**
     * Generate a checksum of the current position to the end of the buffer, doesn't affect the read position.
     *
     * Current uses CRC32C, but this could change in the future.
     */
    fun checksum(): ULong

    val exhausted: Boolean
}


interface BufWriter<SELF : BufWriter<SELF>> : GenericSelf<SELF> {
    //region Base Write Methods
    fun writeByte(value: Byte): SELF
    fun writeShort(value: Short): SELF
    fun writeInt(value: Int): SELF
    fun writeLong(value: Long): SELF
    fun writeVarInt(value: Int): SELF
    fun writeVarLong(value: Long): SELF

    fun writeUByte(value: UByte): SELF
    fun writeUShort(value: UShort): SELF
    fun writeUInt(value: UInt): SELF
    fun writeULong(value: ULong): SELF
    fun writeUVarInt(value: UInt): SELF
    fun writeUVarLong(value: ULong): SELF

    fun writeFloat(value: Float): SELF
    fun writeDouble(value: Double): SELF

    fun writeBool(value: Boolean): SELF


    fun writeByteArray(value: ByteArray): SELF
    fun writeShortArray(value: ShortArray): SELF
    fun writeIntArray(value: IntArray): SELF
    fun writeLongArray(value: LongArray): SELF
    fun writeVarIntArray(value: IntArray): SELF
    fun writeVarLongArray(value: LongArray): SELF

    fun writeUByteArray(value: UByteArray): SELF
    fun writeUShortArray(value: UShortArray): SELF
    fun writeUIntArray(value: UIntArray): SELF
    fun writeULongArray(value: ULongArray): SELF
    fun writeUVarIntArray(value: UIntArray): SELF
    fun writeUVarLongArray(value: ULongArray): SELF

    fun writeFloatArray(value: FloatArray): SELF
    fun writeDoubleArray(value: DoubleArray): SELF

    fun writeBoolArray(value: BooleanArray): SELF


    fun writeVec2i(x: Int, y: Int): SELF
    fun writeVec2f(x: Float, y: Float): SELF
    fun writeVec2d(x: Double, y: Double): SELF
    fun writeVec3i(x: Int, y: Int, z: Int): SELF
    fun writeVec3f(x: Float, y: Float, z: Float): SELF
    fun writeVec3d(x: Double, y: Double, z: Double): SELF

    fun writeVec2i(value: Vector2i): SELF
    fun writeVec2f(value: Vector2f): SELF
    fun writeVec2d(value: Vector2d): SELF
    fun writeVec3i(value: Vector3i): SELF
    fun writeVec3f(value: Vector3f): SELF
    fun writeVec3d(value: Vector3d): SELF
    fun writeTransform2Df(value: Matrix3x2f): SELF
    fun writeTransform2Dd(value: Matrix3x2d): SELF
    fun writeTransform3Df(value: Matrix4x3f): SELF
    fun writeTransform3Dd(value: Matrix4x3d): SELF


    fun writeBlob(value: ByteString, start: Int = 0, end: Int = value.size): SELF
    fun writeStr(value: String, start: Int = 0, end: Int = value.length): SELF

    fun writeResLoc(value: ResourceLocation): SELF =
        _writingType(Buf.Primitive.RESOURCE_LOCATION) { writeAscii(value.path); writeAscii(value.namespace) }

    fun writeUUID(value: UUID): SELF =
        _writingType(Buf.Primitive.UUID) { writeLong(value.mostSignificantBits); writeLong(value.leastSignificantBits) }

    fun writeNBT(value: Tag): SELF

    fun writeBlockState(value: BlockState): SELF
    fun writeFluidState(value: FluidState): SELF
    //endregion

    /**
     * Write an ascii-only string. This means that the number of bytes of the string is the same as the number of chars.
     *
     * This method is not responsable for checking if the string is actually ascii-only, but it may validate the bytes written.
     */
    fun writeAscii(value: String, start: Int = 0, end: Int = value.length): SELF = writeStr(value, start, end)
}

/**
 * May be used to implement writing dynamic types in the future,
 * currently simply a pass-through.
 *
 * Should only be used within the
 */
@Suppress("FunctionName")
inline fun <SELF : BufWriter<SELF>> BufWriter<SELF>._writingType(
    @Suppress("UNUSED_PARAMETER")
    type: Buf.Primitive<*>,
    block: BufWriter<SELF>.() -> Unit
): SELF {
    block()
    return self
}


@PublishedApi
internal val BY_ID = Byte2ReferenceArrayMap<Buf.Primitive<*>>()

interface Buf<SELF : Buf<SELF>> : BufReader<SELF>, BufWriter<SELF>, GenericSelf<SELF> {
    /**
     * Write all values from [buf] to this buffer.
     *
     * Only allow operation between the same type of buffers by default, for optimization reasons
     * (e.g. ByteBuf can simply add the bytes from the other buffer).
     */
    fun append(buf: SELF): SELF

    /**
     * Clear the content and reset the read/write position.
     */
    fun clear(): SELF

    //region Primitive
    @Suppress("unused", "MemberVisibilityCanBePrivate")
    @OptIn(ExperimentalUnsignedTypes::class)
    sealed class Primitive<T : Any> private constructor(
        val id: Byte,
        val clazz: KClass<T>,
        internal val readMethod: BufReader<*>.() -> T,
        internal val writeMethod: BufWriter<*>.(value: T) -> BufWriter<*>
    ) {
        val name by lazy {
            Companion::class.declaredMemberProperties.find { it.get(Companion) == this }!!.name
        }

        override fun toString() = "${name}($id)"

        init {
            assert(id in BY_ID) { "Duplicate CCBufPrimitive id: $id" }
            BY_ID[id] = this
        }

        companion object {
            inline operator fun get(id: Byte): Primitive<*>? = BY_ID.get(id)
            inline operator fun get(id: Int): Primitive<*>? = if (id > 127) null else BY_ID.get(id.toByte())
        }

        // id=0 is reserved
        //@formatter:off
        object BYTE : Primitive<Byte>(1, Byte::class, BufReader<*>::readByte, BufWriter<*>::writeByte)
        object SHORT : Primitive<Short>(2, Short::class, BufReader<*>::readShort, BufWriter<*>::writeShort)
        object INT : Primitive<Int>(3, Int::class, BufReader<*>::readInt, BufWriter<*>::writeInt)
        object LONG : Primitive<Long>(4, Long::class, BufReader<*>::readLong, BufWriter<*>::writeLong)
        object VARINT : Primitive<Int>(5, Int::class, BufReader<*>::readVarInt, BufWriter<*>::writeVarInt)
        object VARLONG : Primitive<Long>(6, Long::class, BufReader<*>::readVarLong, BufWriter<*>::writeVarLong)

        object UBYTE : Primitive<UByte>(7, UByte::class, BufReader<*>::readUByte, BufWriter<*>::writeUByte)
        object USHORT : Primitive<UShort>(8, UShort::class, BufReader<*>::readUShort, BufWriter<*>::writeUShort)
        object UINT : Primitive<UInt>(9, UInt::class, BufReader<*>::readUInt, BufWriter<*>::writeUInt)
        object ULONG : Primitive<ULong>(10, ULong::class, BufReader<*>::readULong, BufWriter<*>::writeULong)
        object UVARINT : Primitive<UInt>(11, UInt::class, BufReader<*>::readUVarInt, BufWriter<*>::writeUVarInt)
        object UVARLONG : Primitive<ULong>(12, ULong::class, BufReader<*>::readUVarLong, BufWriter<*>::writeUVarLong)

        object FLOAT : Primitive<Float>(13, Float::class, BufReader<*>::readFloat, BufWriter<*>::writeFloat)
        object DOUBLE : Primitive<Double>(14, Double::class, BufReader<*>::readDouble, BufWriter<*>::writeDouble)

        object BOOLEAN : Primitive<Boolean>(15, Boolean::class, BufReader<*>::readBool, BufWriter<*>::writeBool)


        object BYTE_ARRAY : Primitive<ByteArray>(16, ByteArray::class, BufReader<*>::readByteArray, BufWriter<*>::writeByteArray)
        object SHORT_ARRAY : Primitive<ShortArray>(17, ShortArray::class, BufReader<*>::readShortArray, BufWriter<*>::writeShortArray)
        object INT_ARRAY : Primitive<IntArray>(18, IntArray::class, BufReader<*>::readIntArray, BufWriter<*>::writeIntArray)
        object LONG_ARRAY : Primitive<LongArray>(19, LongArray::class, BufReader<*>::readLongArray, BufWriter<*>::writeLongArray)
        object VARINT_ARRAY : Primitive<IntArray>(20, IntArray::class, BufReader<*>::readVarIntArray, BufWriter<*>::writeVarIntArray)
        object VARLONG_ARRAY : Primitive<LongArray>(21, LongArray::class, BufReader<*>::readVarLongArray, BufWriter<*>::writeVarLongArray)

        object UBYTE_ARRAY : Primitive<UByteArray>(22, UByteArray::class, BufReader<*>::readUByteArray, BufWriter<*>::writeUByteArray)
        object USHORT_ARRAY : Primitive<UShortArray>(23, UShortArray::class, BufReader<*>::readUShortArray, BufWriter<*>::writeUShortArray)
        object UINT_ARRAY : Primitive<UIntArray>(24, UIntArray::class, BufReader<*>::readUIntArray, BufWriter<*>::writeUIntArray)
        object ULONG_ARRAY : Primitive<ULongArray>(25, ULongArray::class, BufReader<*>::readULongArray, BufWriter<*>::writeULongArray)
        object UVARINT_ARRAY : Primitive<UIntArray>(26, UIntArray::class, BufReader<*>::readUVarIntArray, BufWriter<*>::writeUVarIntArray)
        object UVARLONG_ARRAY : Primitive<ULongArray>(27, ULongArray::class, BufReader<*>::readUVarLongArray, BufWriter<*>::writeUVarLongArray)

        object FLOAT_ARRAY : Primitive<FloatArray>(28, FloatArray::class, BufReader<*>::readFloatArray, BufWriter<*>::writeFloatArray)
        object DOUBLE_ARRAY : Primitive<DoubleArray>(29, DoubleArray::class, BufReader<*>::readDoubleArray, BufWriter<*>::writeDoubleArray)

        object BOOLEAN_ARRAY : Primitive<BooleanArray>(30, BooleanArray::class, BufReader<*>::readBoolArray, BufWriter<*>::writeBoolArray)


        object VEC2I : Primitive<Vector2i>(31, Vector2i::class, BufReader<*>::readVec2i, BufWriter<*>::writeVec2i)
        object VEC2F : Primitive<Vector2f>(32, Vector2f::class, BufReader<*>::readVec2f, BufWriter<*>::writeVec2f)
        object VEC2D : Primitive<Vector2d>(33, Vector2d::class, BufReader<*>::readVec2d, BufWriter<*>::writeVec2d)
        object VEC3I : Primitive<Vector3i>(34, Vector3i::class, BufReader<*>::readVec3i, BufWriter<*>::writeVec3i)
        object VEC3F : Primitive<Vector3f>(35, Vector3f::class, BufReader<*>::readVec3f, BufWriter<*>::writeVec3f)
        object VEC3D : Primitive<Vector3d>(36, Vector3d::class, BufReader<*>::readVec3d, BufWriter<*>::writeVec3d)
        object TRANSFORM2DF : Primitive<Matrix3x2f>(37, Matrix3x2f::class, BufReader<*>::readTransform2Df, BufWriter<*>::writeTransform2Df)
        object TRANSFORM2DD : Primitive<Matrix3x2d>(38, Matrix3x2d::class, BufReader<*>::readTransform2Dd, BufWriter<*>::writeTransform2Dd)
        object TRANSFORM3DF : Primitive<Matrix4x3f>(39, Matrix4x3f::class, BufReader<*>::readTransform3Df, BufWriter<*>::writeTransform3Df)
        object TRANSFORM3DD : Primitive<Matrix4x3d>(40, Matrix4x3d::class, BufReader<*>::readTransform3Dd, BufWriter<*>::writeTransform3Dd)


        object BLOB : Primitive<ByteString>(41, ByteString::class, BufReader<*>::readBlob, BufWriter<*>::writeBlob)
        object STRING : Primitive<String>(42, String::class, BufReader<*>::readStr, BufWriter<*>::writeStr)

        object RESOURCE_LOCATION : Primitive<ResourceLocation>(43, ResourceLocation::class, BufReader<*>::readResLoc, BufWriter<*>::writeResLoc)

        object UUID : Primitive<java.util.UUID>(44, java.util.UUID::class, BufReader<*>::readUUID, BufWriter<*>::writeUUID)

        object NBT : Primitive<Tag>(45, Tag::class, BufReader<*>::readNBT, BufWriter<*>::writeNBT)

        object BLOCK_STATE : Primitive<BlockState>(46, BlockState::class, BufReader<*>::readBlockState, BufWriter<*>::writeBlockState)
        object FLUID_STATE : Primitive<FluidState>(47, FluidState::class, BufReader<*>::readFluidState, BufWriter<*>::writeFluidState)
        //@formatter:on
    }
    //endregion
}