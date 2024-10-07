@file:Suppress("NOTHING_TO_INLINE", "unused")

package dev.shblock.codecraft.utils


inline infix fun Int.has(flags: Int) = this and flags != 0
inline infix fun UInt.has(flags: UInt) = this and flags != 0u
inline infix fun Long.has(flags: Long) = this and flags != 0L
inline infix fun ULong.has(flags: ULong) = this and flags != 0uL
