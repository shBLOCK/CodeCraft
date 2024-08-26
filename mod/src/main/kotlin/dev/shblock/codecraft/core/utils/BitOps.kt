@file:Suppress("NOTHING_TO_INLINE")

package dev.shblock.codecraft.core.utils

inline infix fun Int.has(flags: Int) = this and flags != 0
