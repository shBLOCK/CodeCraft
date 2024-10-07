package dev.shblock.codecraft.utils.buf

import kotlinx.io.*
import java.io.DataInput
import java.io.DataOutput
import java.io.UTFDataFormatException


class SinkCustomDataOutput internal constructor(private val sink: Sink) : DataOutput {
    override fun write(b: Int) = sink.writeByte(b.toByte())

    override fun write(b: ByteArray) = sink.write(b)

    override fun write(b: ByteArray, off: Int, len: Int) = sink.write(b, off, off + len)

    override fun writeBoolean(v: Boolean) = sink.writeByte(if (v) 1 else 0)

    override fun writeByte(v: Int) = sink.writeByte(v.toByte())

    override fun writeShort(v: Int) = sink.writeShort(v.toShort())

    override fun writeChar(v: Int) = sink.writeShort(v.toShort())

    override fun writeInt(v: Int) = sink.writeInt(v)

    override fun writeLong(v: Long) = sink.writeLong(v)

    override fun writeFloat(v: Float) = sink.writeFloat(v)

    override fun writeDouble(v: Double) = sink.writeDouble(v)

    override fun writeBytes(s: String) = throw NotImplementedError()

    override fun writeChars(s: String) = throw NotImplementedError()

    /**
     * This is a custom implementation that doesn't comply with the original spec.
     */
    override fun writeUTF(s: String) {
        val data = Buffer().apply { writeString(s) }
        if (data.size > UShort.MAX_VALUE.toInt()) throw UTFDataFormatException("String too long: ${data.size} bytes")
        sink.writeUShort(data.size.toUShort())
        sink.write(data, data.size)
    }
}

fun Sink.asCustomDataOutput(): DataOutput = SinkCustomDataOutput(this)

class SourceCustomDataInput internal constructor(private val source: Source) : DataInput {
    override fun readFully(b: ByteArray) = source.readTo(b)

    override fun readFully(b: ByteArray, off: Int, len: Int) = source.readTo(b, off, off + len)

    override fun skipBytes(n: Int): Int = throw NotImplementedError()

    override fun readBoolean(): Boolean = source.readBooleanStrict()

    override fun readByte(): Byte = source.readByte()

    override fun readUnsignedByte(): Int = source.readUByte().toInt()

    override fun readShort(): Short = source.readShort()

    override fun readUnsignedShort(): Int = source.readUShort().toInt()

    override fun readChar(): Char = Char(source.readUShort())

    override fun readInt(): Int = source.readInt()

    override fun readLong(): Long = source.readLong()

    override fun readFloat(): Float = source.readFloat()

    override fun readDouble(): Double = source.readDouble()

    override fun readLine(): String = throw NotImplementedError()

    /**
     * This is a custom implementation that doesn't comply with the original spec.
     */
    override fun readUTF(): String = source.readString(source.readUShort().toLong())
}

fun Source.asCustomDataInput(): DataInput = SourceCustomDataInput(this)