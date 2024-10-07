@file:Suppress("NOTHING_TO_INLINE", "unused")

package dev.shblock.codecraft.utils

import kotlin.experimental.and


inline fun Byte.toUByteChecked() =
    if (this and 0x80.toByte() == 0.toByte()) toByte() else throw ArithmeticException("Overflow: $this")

inline fun Short.toUShortChecked() =
    if (this and 0x8000.toShort() == 0.toShort()) toShort() else throw ArithmeticException("Overflow: $this")

inline fun Int.toUIntChecked() =
    if (this and 0x8000_0000.toInt() == 0) toInt() else throw ArithmeticException("Overflow: $this")

inline fun Long.toULongChecked() =
    if (this and (1 shl 63) == 0L) toUInt() else throw ArithmeticException("Overflow: $this")

inline fun UByte.toByteChecked() =
    if (this and 0x80u == 0u.toUByte()) toByte() else throw ArithmeticException("Overflow: $this")

inline fun UShort.toShortChecked() =
    if (this and 0x8000u == 0u.toUShort()) toShort() else throw ArithmeticException("Overflow: $this")

inline fun UInt.toIntChecked() =
    if (this and 0x8000_0000u == 0u) toInt() else throw ArithmeticException("Overflow: $this")

inline fun ULong.toLongChecked() =
    if (this and 0x8000_0000_0000_0000uL == 0uL) toLong() else throw ArithmeticException("Overflow: $this")