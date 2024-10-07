package dev.shblock.codecraft.utils

/**
 * An interface to work around Kotlin not supporting the `Self` type.
 *
 * Intended for builder-type classes that allows chainable method calls.
 */
interface GenericSelf<SELF : GenericSelf<SELF>>

@Suppress("UNCHECKED_CAST")
inline val <SELF : GenericSelf<SELF>> GenericSelf<SELF>.self get() = this as SELF