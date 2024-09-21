package dev.shblock.codecraft.core

import dev.shblock.codecraft.CodeCraft
import dev.shblock.codecraft.core.cmd.Cmd
import dev.shblock.codecraft.core.msg.Msg
import dev.shblock.codecraft.core.utils.getOwningModOfClass
import dev.shblock.codecraft.edu.task.Task
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.registries.NewRegistryEvent
import net.neoforged.neoforge.registries.RegisterEvent
import net.neoforged.neoforge.registries.RegistryBuilder
import net.neoforged.neoforgespi.language.IModFileInfo
import java.lang.annotation.ElementType
import kotlin.reflect.KClass
import kotlin.reflect.full.findParameterByName
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Suppress("unused")
annotation class CCAutoReg(
    val name: String,
    val namespace: String = "",
    val whenModsLoaded: Array<String> = []
)

@Suppress("MemberVisibilityCanBePrivate", "unused")
@EventBusSubscriber(modid = CodeCraft.MODID, bus = EventBusSubscriber.Bus.MOD)
object CCRegistries {
    val CMD = classRegistry<Cmd>("cmd")
    val MSG = classRegistry<Msg>("msg")
    val TASK = classRegistry<Task>("task")

    fun register(name: ResourceLocation, clazz: KClass<Any>) {
        ModList.get().mods.forEach { it.owningFile.file.scanResult.classes }

        val reg = classRegistries.values
            .find { clazz.isSubclassOf(it.baseClass) }
            ?: throw IllegalArgumentException("No appropriate registry found for $clazz")

        reg.toRegister[name] = clazz
    }

    fun register(name: String, clazz: KClass<Any>) {
        val namespace = getOwningModOfClass(clazz)?.namespace
            ?: throw IllegalArgumentException("Can't determine owning mod of $clazz")

        register(
            ResourceLocation.fromNamespaceAndPath(
                namespace,
                name
            ),
            clazz
        )
    }


    @SubscribeEvent
    private fun onNewRegistry(event: NewRegistryEvent) {
        classRegistries.values.forEach { event.register(it.registry) }

        // At this point all mods should've been discovered
        scanAutoRegClasses()
    }

    @SubscribeEvent
    private fun onRegister(event: RegisterEvent) {
        for ((key, reg) in classRegistries) {
            event.register(key as ResourceKey<out Registry<KClass<out Any>>>) { registerHelper ->
                reg.toRegister.forEach { registerHelper.register(it.key, it.value) }
            }
        }
    }
}

private class ClassRegistry<T : Any>(
    val registry: Registry<in KClass<out T>>,
    val baseClass: KClass<out T>
) {
    val toRegister = mutableMapOf<ResourceLocation, KClass<out Any>>()
}

private val classRegistries = mutableMapOf<ResourceKey<Registry<KClass<out Any>>>, ClassRegistry<out Any>>()

private inline fun <reified T : Any> classRegistry(name: String): Registry<KClass<out T>> {
    val reg = RegistryBuilder<KClass<out T>>(ResourceKey.createRegistryKey(CodeCraft.path(name))).create()
    @Suppress("UNCHECKED_CAST")
    classRegistries[reg.key() as ResourceKey<Registry<KClass<out Any>>>] = ClassRegistry(reg, T::class)
    return reg
}

@Suppress("UNUSED_PARAMETER")
private fun checkAutoRegConditions(
    modFile: IModFileInfo,
    clazz: KClass<out Any>,
    annotation: CCAutoReg
): Boolean {
    annotation.whenModsLoaded
        .forEach { if (!ModList.get().isLoaded(it)) return false }

    //TODO: config option to disable specific commands

    return true
}

private fun scanAutoRegClasses() {
    ModList.get().modFiles.forEach { modFile ->
        modFile.file.scanResult
            .getAnnotatedBy(CCAutoReg::class.java, ElementType.TYPE)
            .forEach currentClass@{ annoData ->
                val clazz = Class.forName(annoData.clazz.className).kotlin

                val annoConstructor = CCAutoReg::class.primaryConstructor!!
                val annotation = annoConstructor.callBy(
                    annoData.annotationData.mapKeys {
                        annoConstructor.findParameterByName(it.key)!!
                    }
                )

                if (!checkAutoRegConditions(modFile, clazz, annotation)) return@currentClass

                val id = ResourceLocation.fromNamespaceAndPath(
                    annotation.namespace.ifEmpty { modFile.mods[0].namespace },
                    annotation.name
                )

                classRegistries.values.forEach { reg ->
                    if (clazz.isSubclassOf(reg.baseClass)) {
                        CodeCraft.LOGGER.debug(
                            "Auto registering {} (class: {}) to {}",
                            id,
                            clazz.simpleName,
                            reg.registry.key().location()
                        )
                        reg.toRegister[id] = clazz
                        return@currentClass
                    }
                }
                throw IllegalArgumentException("No appropriate registry found for @CCAutoReg $clazz")
            }
    }
}
