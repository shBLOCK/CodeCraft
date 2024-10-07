@file:Suppress("NOTHING_TO_INLINE", "unused")

package dev.shblock.codecraft.utils.buf

class DelegatingBufReader<SELF : BufReader<SELF>>
@PublishedApi internal constructor(private val buf: BufReader<SELF>) :
    BufReader<SELF> by buf

class DelegatingBufWriter<SELF : BufWriter<SELF>>
@PublishedApi internal constructor(private val buf: BufWriter<SELF>) :
    BufWriter<SELF> by buf

/**
 * Make a [BufReader] that wraps [wrapped].
 */
inline fun <SELF : BufReader<SELF>> BufReader(wrapped: BufReader<SELF>): BufReader<SELF> = DelegatingBufReader(wrapped)

/**
 * Make a [BufWriter] that wraps [wrapped].
 */
inline fun <SELF : BufWriter<SELF>> BufWriter(wrapped: BufWriter<SELF>): BufWriter<SELF> = DelegatingBufWriter(wrapped)