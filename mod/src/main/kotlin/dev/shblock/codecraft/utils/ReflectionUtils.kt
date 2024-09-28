package dev.shblock.codecraft.utils

import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible

fun <R> KClass<*>.findMemberFunction(name: String): KFunction<R> {
    val func = memberFunctions
        .filter { it.name == name }
        .also { if (it.size > 1) throw IllegalArgumentException("Found multiple member functions named $name in $this") }
        .firstOrNull() ?: throw IllegalArgumentException("No function named $name in $this")
    @Suppress("UNCHECKED_CAST")
    return func as KFunction<R>
}

fun KCallable<*>.makeAccessible() {
    this.isAccessible = true
}
