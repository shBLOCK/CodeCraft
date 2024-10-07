@file:Suppress("NOTHING_TO_INLINE", "FunctionName", "unused", "ObjectPropertyName")

package dev.shblock.codecraft.utils.buf

import kotlinx.io.IOException
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlin.experimental.and
import kotlin.experimental.or


@PublishedApi
internal inline val Byte._varintContinuation get() = this and 0b1000_0000.toByte() != 0.toByte()

@PublishedApi
internal inline val Byte._varintData get() = this and 0b0111_1111

//region Read VarInteger
@PublishedApi
internal inline fun <T : Number> _readVarInteger(
    buffer: Source,
    name: String,
    maxBytes: Int,
    toT: Number.() -> T,
    or: T.(T) -> T,
    shl: T.(Int) -> T
): T {
    var number = 0.toT()
    var n = 0

    do {
        val byte = buffer.readByte()
        number = number.or(byte._varintData.toT().shl(7 * n))
        if (++n == maxBytes) {
            if (byte._varintContinuation) throw IOException("$name too big")
            break
        }
    } while (byte._varintContinuation)

    return number
}

@PublishedApi
internal inline fun _readVarInt(buffer: Source, name: String, maxBytes: Int) =
    _readVarInteger(buffer, name, maxBytes, toT = Number::toInt, or = Int::or, shl = Int::shl)

@PublishedApi
internal inline fun _readVarLong(buffer: Source, name: String, maxBytes: Int) =
    _readVarInteger(buffer, name, maxBytes, toT = Number::toLong, or = Long::or, shl = Long::shl)

@PublishedApi
internal inline fun Source.readVarInt() = _readVarInt(this, "VarInt", 5).rotateRight(1)

@PublishedApi
internal inline fun Source.readVarLong() = _readVarLong(this, "VarInt", 10).rotateRight(1)

@PublishedApi
internal inline fun Source.readUVarInt() = _readVarInt(this, "UVarInt", 5).toUInt()

@PublishedApi
internal inline fun Source.readUVarLong() = _readVarInt(this, "UVarLong", 10).toULong()
//endregion

//region Write VarInteger
@PublishedApi
internal inline fun <T : Number> _writeVarInteger(
    buffer: Sink,
    value: T,
    toT: Number.() -> T,
    and: T.(T) -> T,
    ushr: T.(Int) -> T
) {
    var current = value
    while (true) {
        val byte = current.and(0b0111_1111.toT()).toByte()
        current = current.ushr(7)
        if (current != 0.toT()) {
            buffer.writeByte(byte or 0b1000_0000.toByte())
        } else {
            buffer.writeByte(byte)
            return
        }
    }
}

@PublishedApi
internal inline fun _writeVarInt(buffer: Sink, value: Int) =
    _writeVarInteger(buffer, value, toT = Number::toInt, and = Int::and, ushr = Int::ushr)

@PublishedApi
internal inline fun _writeVarLong(buffer: Sink, value: Long) =
    _writeVarInteger(buffer, value, toT = Number::toLong, and = Long::and, ushr = Long::ushr)

@PublishedApi
internal inline fun Sink.writeVarInt(value: Int) = _writeVarInt(this, value.rotateLeft(1))

@PublishedApi
internal inline fun Sink.writeVarLong(value: Long) = _writeVarLong(this, value.rotateLeft(1))

@PublishedApi
internal inline fun Sink.writeUVarInt(value: UInt) = _writeVarInt(this, value.toInt())

@PublishedApi
internal inline fun Sink.writeUVarLong(value: ULong) = _writeVarLong(this, value.toLong())
//endregion

@PublishedApi
internal inline fun Source.readBooleanStrict() =
    readByte()
        .also { if (it and 0b1111_1110.toByte() != 0.toByte()) throw IOException("Invalid bool: $it") }
        .let { it == 1.toByte() }

@PublishedApi
internal inline fun Sink.writeBoolean(value: Boolean) = writeByte(if (value) 1 else 0)