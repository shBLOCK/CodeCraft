package dev.shblock.codecraft.core.utils

import net.neoforged.fml.ModList
import net.neoforged.neoforgespi.language.IModInfo
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

fun getOwningModOfClass(clazz: KClass<Any>): IModInfo? {
    val className = clazz.jvmName
    return ModList.get().mods.find { mod ->
        mod.owningFile.file.scanResult.classes
            .any { it.clazz.className == className }
    }
}
