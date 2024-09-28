package dev.shblock.codecraft.core.registry

import dev.shblock.codecraft.CodeCraft
import dev.shblock.codecraft.core.cmd.Cmd
import dev.shblock.codecraft.core.cmd.CmdRegistryEntry
import dev.shblock.codecraft.core.msg.Msg
import net.minecraft.resources.ResourceLocation
import kotlin.reflect.KClass

@Suppress("unused")
object CCRegistries {
    val CMD = ClassRegistry.create<Cmd, CmdRegistryEntry>(CodeCraft.path("cmd"), ::CmdRegistryEntry)
    val MSG = ClassRegistry.create<Msg, ClassRegistryEntry<Msg>>(CodeCraft.path("msg"), ::ClassRegistryEntry)

    fun register(name: ResourceLocation, clazz: KClass<Any>) = ClassRegistry.register(name, clazz)
    fun register(name: String, clazz: KClass<Any>) = ClassRegistry.register(name, clazz)
}

