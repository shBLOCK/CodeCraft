@file:Suppress("NOTHING_TO_INLINE")

package dev.shblock.codecraft.core.utils

import kotlin.experimental.and

inline infix fun Byte.has(flags: Byte) = this and flags != 0.toByte()
inline infix fun Int.has(flags: Int) = this and flags != 0
inline infix fun Long.has(flags: Long) = this and flags != 0L
