package dev.shblock.codecraft.core.registry

import dev.shblock.codecraft.CodeCraft
import dev.shblock.codecraft.core.utils.getOwningModOfClass
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import it.unimi.dsi.fastutil.objects.ObjectList
import net.minecraft.core.Registry
import net.minecraft.core.WritableRegistry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.registries.NewRegistryEvent
import net.neoforged.neoforge.registries.RegisterEvent
import net.neoforged.neoforge.registries.RegistryBuilder
import net.neoforged.neoforge.registries.callback.AddCallback
import net.neoforged.neoforgespi.language.IModFileInfo
import java.lang.annotation.ElementType
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KClass
import kotlin.reflect.full.findParameterByName
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.valueParameters


@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Suppress("unused")
annotation class CCAutoReg(
    val name: String,
    val namespace: String = "",
    val whenModsLoaded: Array<String> = []
)

open class ClassRegistryEntry<out T : Any>(
    open val id: ResourceLocation,
    open val clazz: KClass<out T>
)

class ConstructingClassRegistryEntry<out T : Any>(
    id: ResourceLocation,
    clazz: KClass<out T>,
    constructorParams: List<KClass<out Any>>,
    constructorRequired: Boolean
) : ClassRegistryEntry<T>(id, clazz) {

    val constructor =
        clazz.constructors.find {
            if (it.valueParameters.size != constructorParams.size)
                return@find false
            for ((param, clz) in it.valueParameters zip constructorParams) {
                if (!clz.isSubclassOf(param.type.classifier as KClass<*>))
                    return@find false
            }
            true
        }

    init {
        if (constructorRequired) throw IllegalArgumentException("No valid constructor found in $clazz")
    }

    val constructable get() = constructor != null

    @Suppress("NOTHING_TO_INLINE")
    inline fun construct(vararg params: Any): T {
        try {
            return constructor!!.call(*params)
        } catch (e: InvocationTargetException) {
            throw e.cause!!
        }
    }
}

class ClassRegistry<T : Any, out E : ClassRegistryEntry<T>> internal constructor(
    val registry: WritableRegistry<KClass<out T>>,
    val entryFactory: (ResourceLocation, KClass<out T>) -> E,
    val baseClass: KClass<out T>
) : WritableRegistry<KClass<out T>> by registry {
    internal val toRegister = mutableMapOf<ResourceLocation, KClass<out T>>()

    private fun deferEntry(name: ResourceLocation, clazz: KClass<out Any>) {
        if (name in toRegister) throw IllegalArgumentException("The register name \"$name\" of $clazz clashed with other entries")
        @Suppress("UNCHECKED_CAST")
        toRegister[name] = clazz as KClass<out T>
    }

    private val entryById: ObjectList<E> = ObjectArrayList(256)

    init {
        addCallback(object : AddCallback<KClass<out T>> {
            override fun onAdd(
                registry: Registry<KClass<out T>>, id: Int, key: ResourceKey<KClass<out T>>, clazz: KClass<out T>
            ) {
                check(id == entryById.size)
                entryById += entryFactory(key.location(), clazz)
            }
        })
    }

    fun getEntry(id: Int) = entryById.getOrNull(id)

    @EventBusSubscriber(modid = CodeCraft.MODID, bus = EventBusSubscriber.Bus.MOD)
    companion object {
        private val instances = mutableListOf<ClassRegistry<out Any, *>>()

        internal inline fun <reified T : Any, E : ClassRegistryEntry<T>> create(
            id: ResourceLocation,
            noinline entryFactory: (ResourceLocation, KClass<out T>) -> E
        ): ClassRegistry<T, E> {
            return ClassRegistry(
                RegistryBuilder<KClass<out T>>(ResourceKey.createRegistryKey(id)).create()
                    as WritableRegistry<KClass<out T>>,
                entryFactory,
                T::class
            ).also { instances.add(it) }
        }

        internal fun register(name: ResourceLocation, clazz: KClass<Any>) {
            val reg = instances
                .find { clazz.isSubclassOf(it.baseClass) }
                ?: throw IllegalArgumentException("No appropriate registry found for $clazz")

            reg.deferEntry(name, clazz)
        }

        internal fun register(name: String, clazz: KClass<Any>) {
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
            instances.forEach { event.register(it) }

            // At this point all mods should've been discovered
            registerAutoRegClasses()
        }

        @SubscribeEvent
        private fun onRegister(event: RegisterEvent) {
            for (reg in instances) {
                @Suppress("UNCHECKED_CAST")
                event.register((reg as Registry<KClass<out Any>>).key()) { registerHelper ->
                    reg.toRegister.forEach { registerHelper.register(it.key, it.value) }
                }
            }
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

        private fun registerAutoRegClasses() {
            ModList.get().modFiles.forEach { modFile ->
                modFile.file.scanResult
                    .getAnnotatedBy(CCAutoReg::class.java, ElementType.TYPE)
                    .forEach currentClass@{ annoData ->
                        val clazz = Class.forName(annoData.clazz.className).kotlin

                        val annoConstructor = CCAutoReg::class.primaryConstructor!!
                        val annotation = annoConstructor.callBy(
                            annoData.annotationData
                                .mapKeys { annoConstructor.findParameterByName(it.key)!! }
                                .mapValues {
                                    @Suppress("UNCHECKED_CAST")
                                    when (val value = it.value) {
                                        is ArrayList<*> -> (value as ArrayList<String>).toTypedArray()
                                        else -> value
                                    }
                                }
                        )

                        if (!checkAutoRegConditions(modFile, clazz, annotation)) return@currentClass

                        val id = ResourceLocation.fromNamespaceAndPath(
                            annotation.namespace.ifEmpty { modFile.mods[0].namespace },
                            annotation.name
                        )

                        instances.forEach { reg ->
                            if (clazz.isSubclassOf(reg.baseClass)) {
                                CodeCraft.LOGGER.debug(
                                    "Auto registering {} (class: {}) to {}",
                                    id,
                                    clazz.simpleName,
                                    reg.registry.key().location()
                                )
                                reg.deferEntry(id, clazz)
                                return@currentClass
                            }
                        }
                        throw IllegalArgumentException("No appropriate registry found for @CCAutoReg $clazz")
                    }
            }
        }
    }
}