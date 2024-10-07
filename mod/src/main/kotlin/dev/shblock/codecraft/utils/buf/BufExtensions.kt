@file:Suppress("NOTHING_TO_INLINE", "unused")
@file:OptIn(ExperimentalUnsignedTypes::class)

package dev.shblock.codecraft.utils.buf

import dev.shblock.codecraft.core.registry.ClassRegistry
import dev.shblock.codecraft.core.registry.ClassRegistryEntry
import dev.shblock.codecraft.utils.dimensions
import dev.shblock.codecraft.utils.self
import kotlinx.io.bytestring.ByteString
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.Vec3i
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import org.joml.*
import java.util.*
import kotlin.enums.enumEntries


//region Using Type Object
fun <T : Any> BufReader<*>.readPrimitive(type: Buf.Primitive<T>): T =
    (type.readMethod)()

fun <T : Any, SELF : BufWriter<SELF>> BufWriter<SELF>.writePrimitive(type: Buf.Primitive<T>, value: T) =
    self.also { (type.writeMethod)(value) }
//endregion

//region Write Operator
//@formatter:off
inline fun BufWriter<*>.write(value: Byte) { writeByte(value) }
inline fun BufWriter<*>.write(value: Short) { writeShort(value) }
inline fun BufWriter<*>.write(value: Int) { writeInt(value) }
inline fun BufWriter<*>.write(value: Long) { writeLong(value) }
inline fun BufWriter<*>.write(value: VarInt) { writeVarInt(value.value) }
inline fun BufWriter<*>.write(value: VarLong) { writeVarLong(value.value) }

inline fun BufWriter<*>.write(value: UByte) { writeUByte(value) }
inline fun BufWriter<*>.write(value: UShort) { writeUShort(value) }
inline fun BufWriter<*>.write(value: UInt) { writeUInt(value) }
inline fun BufWriter<*>.write(value: ULong) { writeULong(value) }
inline fun BufWriter<*>.write(value: UVarInt) { writeUVarInt(value.value) }
inline fun BufWriter<*>.write(value: UVarLong) { writeUVarLong(value.value) }

inline fun BufWriter<*>.write(value: Float) { writeFloat(value) }
inline fun BufWriter<*>.write(value: Double) { writeDouble(value) }

inline fun BufWriter<*>.write(value: Boolean) { writeBool(value) }


inline fun BufWriter<*>.write(value: ByteArray) { writeByteArray(value) }
inline fun BufWriter<*>.write(value: ShortArray) { writeShortArray(value) }
inline fun BufWriter<*>.write(value: IntArray) { writeIntArray(value) }
inline fun BufWriter<*>.write(value: LongArray) { writeLongArray(value) }
@JvmName("writeByVarInt")
inline fun BufWriter<*>.write(value: VarLenNumArray<IntArray>) { writeVarIntArray(value.value) }
@JvmName("writeByVarLong")
inline fun BufWriter<*>.write(value: VarLenNumArray<LongArray>) { writeVarLongArray(value.value) }

inline fun BufWriter<*>.write(value: UByteArray) { writeUByteArray(value) }
inline fun BufWriter<*>.write(value: UShortArray) { writeUShortArray(value) }
inline fun BufWriter<*>.write(value: UIntArray) { writeUIntArray(value) }
inline fun BufWriter<*>.write(value: ULongArray) { writeULongArray(value) }
@JvmName("writeByUVarInt")
inline fun BufWriter<*>.write(value: VarLenNumArray<UIntArray>) { writeUVarIntArray(value.value) }
@JvmName("writeByUVarLong")
inline fun BufWriter<*>.write(value: VarLenNumArray<ULongArray>) { writeUVarLongArray(value.value) }

inline fun BufWriter<*>.write(value: FloatArray) { writeFloatArray(value) }
inline fun BufWriter<*>.write(value: DoubleArray) { writeDoubleArray(value) }

inline fun BufWriter<*>.write(value: BooleanArray) { writeBoolArray(value) }


inline fun BufWriter<*>.write(value: Vector2i) { writeVec2i(value) }
inline fun BufWriter<*>.write(value: Vector2f) { writeVec2f(value) }
inline fun BufWriter<*>.write(value: Vector2d) { writeVec2d(value) }
inline fun BufWriter<*>.write(value: Vector3i) { writeVec3i(value) }
inline fun BufWriter<*>.write(value: Vector3f) { writeVec3f(value) }
inline fun BufWriter<*>.write(value: Vector3d) { writeVec3d(value) }
inline fun BufWriter<*>.write(value: Matrix3x2f) { writeTransform2Df(value) }
inline fun BufWriter<*>.write(value: Matrix3x2d) { writeTransform2Dd(value) }
inline fun BufWriter<*>.write(value: Matrix4x3f) { writeTransform3Df(value) }
inline fun BufWriter<*>.write(value: Matrix4x3d) { writeTransform3Dd(value) }


inline fun BufWriter<*>.write(value: ByteString) { writeBlob(value) }
inline fun BufWriter<*>.write(value: String) { writeStr(value) }

inline fun BufWriter<*>.write(value: ResourceLocation) { writeResLoc(value) }

inline fun BufWriter<*>.write(value: UUID) { writeUUID(value) }

inline fun BufWriter<*>.write(value: Tag) { writeNBT(value) }

inline fun BufWriter<*>.write(value: BlockState) { writeBlockState(value) }
inline fun BufWriter<*>.write(value: FluidState) { writeFluidState(value) }
//@formatter:on
//endregion

//region Registry
inline fun <T> BufReader<*>.readByRegistry(registry: Registry<T>): Holder.Reference<T>? =
    registry.getHolder(readVarInt()).orElse(null)

inline fun <T> BufReader<*>.readByRegistryOrThrow(registry: Registry<T>): Holder.Reference<T> {
    val id = readVarInt()
    return registry.getHolder(id).orElseThrow {
        BufException(
            "Invalid id $id for registry ${
                registry.key().location()
            }"
        )
    }
}

fun <T : Any, E : ClassRegistryEntry<T>> BufReader<*>.readByClassRegistry(registry: ClassRegistry<T, E>): E? =
    registry.getEntry(readVarInt())

fun <T : Any, E : ClassRegistryEntry<T>> BufReader<*>.readByClassRegistryOrThrow(registry: ClassRegistry<T, E>): E {
    val id = readVarInt()
    return registry.getEntry(id) ?: throw BufException(
        "Invalid id $id for class registry ${
            registry.key().location()
        }"
    )
}

inline fun <T : Any, SELF : BufWriter<SELF>> BufWriter<SELF>.writeByRegistry(obj: T, registry: Registry<in T>) =
    self.also {
        val id = try {
            registry.getIdOrThrow(obj)
        } catch (e: Exception) {
            throw BufException("Registry $registry doesn't contain object $obj", e)
        }
        writeVarInt(id)
    }

inline fun <T : Any, SELF : BufWriter<SELF>> BufWriter<SELF>.writeByRegistry(
    key: ResourceLocation,
    registry: Registry<T>
) =
    self.also {
        val obj: T = registry.get(key) ?: throw BufException("Registry $registry doesn't contain key $key")
        writeByRegistry(obj, registry)
    }
//endregion

//region Enum
inline fun <reified T : Enum<T>> BufReader<*>.readEnum() =
    enumEntries<T>()[readUByte().toInt()]

inline fun <SELF : BufWriter<SELF>> BufWriter<SELF>.writeEnum(entry: Enum<*>) =
    self.also { writeUByte(entry.ordinal.toUByte()) }
//endregion

//region Misc
inline operator fun <SELF : Buf<SELF>> Buf<SELF>.plusAssign(value: SELF) = Unit.also { append(value) }

inline fun BufReader<*>.readWorldKey(mc: MinecraftServer) = readByRegistryOrThrow(mc.dimensions()).key!!

inline fun <SELF : BufWriter<SELF>> BufWriter<SELF>.writeVec2f(value: Vec2) = value.run { writeVec2f(x, y) }
inline fun <SELF : BufWriter<SELF>> BufWriter<SELF>.writeVec3i(value: Vec3i) = value.run { writeVec3i(x, y, z) }
inline fun <SELF : BufWriter<SELF>> BufWriter<SELF>.writeVec3d(value: Vec3) = value.run { writeVec3d(x, y, z) }

inline fun BufReader<*>.readBlockPos() = readVec3i().run { BlockPos(x, y, z) }

inline fun BufReader<*>.readNBTCompound(): CompoundTag =
    readNBT().also { if (it !is CompoundTag) throw BufException("Expected a compound tag, got $it") } as CompoundTag
//endregion