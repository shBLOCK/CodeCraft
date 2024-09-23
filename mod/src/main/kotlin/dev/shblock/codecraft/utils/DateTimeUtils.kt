@file:Suppress("unused", "NOTHING_TO_INLINE")

package dev.shblock.codecraft.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import kotlin.time.Duration
import kotlin.time.DurationUnit


typealias DateTime = @Serializable(CCDateTimeSerializer::class) OffsetDateTime
typealias UTCDateTime = @Serializable(CCUTCDateTimeSerializer::class) OffsetDateTime

//region Extensions
inline fun utcnow(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

inline val OffsetDateTime.local: OffsetDateTime
    get() = with(OffsetDateTime.now()) // TODO: better way of getting system ZoneOffset?

inline val OffsetDateTime.utc: OffsetDateTime
    get() = with(ZoneOffset.UTC)

inline val OffsetDateTime.micro
    get() = nano / 1000

inline fun OffsetDateTime.withMicro(microOfSecond: Int): OffsetDateTime = withNano(microOfSecond * 1000)

inline operator fun OffsetDateTime.plus(duration: Duration): OffsetDateTime =
    plusNanos(duration.toLong(DurationUnit.NANOSECONDS))

inline operator fun OffsetDateTime.minus(duration: Duration): OffsetDateTime =
    minusNanos(duration.toLong(DurationUnit.NANOSECONDS))
//endregion

//region Serialization
private val CC_UTC_DATETIME_FORMAT = DateTimeFormatterBuilder()
    .appendValue(ChronoField.HOUR_OF_DAY, 2)
    .appendLiteral(':')
    .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
    .optionalStart()
    .appendLiteral(':')
    .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
    .optionalStart()
    .appendFraction(ChronoField.NANO_OF_SECOND, 0, 6, true) // microsecond percision
    .toFormatter()

private val CC_DATETIME_FORMAT = DateTimeFormatterBuilder()
    .parseCaseInsensitive()
    .append(CC_UTC_DATETIME_FORMAT)
    .appendOffsetId()
    .toFormatter()

private sealed class OffsetDateTimeStringSerializer(
    name: String,
    val formatter: DateTimeFormatter
) : KSerializer<OffsetDateTime> {
    override val descriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: OffsetDateTime) {
        encoder.encodeString(value.format(formatter))
    }

    override fun deserialize(decoder: Decoder): OffsetDateTime {
        return OffsetDateTime.parse(decoder.decodeString(), formatter)
    }
}

private object CCDateTimeSerializer : OffsetDateTimeStringSerializer("DateTime", CC_DATETIME_FORMAT)

private object CCUTCDateTimeSerializer : OffsetDateTimeStringSerializer("UTCDateTime", CC_UTC_DATETIME_FORMAT) {
    override fun serialize(encoder: Encoder, value: OffsetDateTime) {
        super.serialize(encoder, value.utc)
    }
}
//endregion
