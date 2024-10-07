@file:Suppress("NOTHING_TO_INLINE", "UNUSED_PARAMETER", "unused")
@file:OptIn(ExperimentalUnsignedTypes::class)

package dev.shblock.codecraft.utils.buf

/**
 * Wrapper types to implement convenient buffer APIs only. Not intended for general use.
 */
sealed interface TypeMarker

//@formatter:off
@JvmInline
value class VarInt @PublishedApi internal constructor(@PublishedApi internal val value: Int) : TypeMarker { companion object }
@JvmInline
value class VarLong @PublishedApi internal constructor(@PublishedApi internal val value: Long) : TypeMarker { companion object }
@JvmInline
value class UVarInt @PublishedApi internal constructor(@PublishedApi internal val value: UInt) : TypeMarker { companion object }
@JvmInline
value class UVarLong @PublishedApi internal constructor(@PublishedApi internal val value: ULong) : TypeMarker { companion object }

@JvmInline
value class VarLenNumArray<T> @PublishedApi internal constructor(@PublishedApi internal val value: T) : TypeMarker { companion object }
//@formatter:on

inline infix fun Int.by(type: VarInt.Companion) = VarInt(this)
inline infix fun Long.by(type: VarLong.Companion) = VarLong(this)
inline infix fun UInt.by(type: UVarInt.Companion) = UVarInt(this)
inline infix fun ULong.by(type: UVarLong.Companion) = UVarLong(this)
inline infix fun IntArray.by(type: VarInt.Companion) = VarLenNumArray(this)
inline infix fun LongArray.by(type: VarLong.Companion) = VarLenNumArray(this)
inline infix fun UIntArray.by(type: UVarInt.Companion) = VarLenNumArray(this)
inline infix fun ULongArray.by(type: UVarLong.Companion) = VarLenNumArray(this)