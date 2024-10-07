package dev.shblock.codecraft.core.registry

import dev.shblock.codecraft.CodeCraft
import dev.shblock.codecraft.core.cmd.Cmd
import dev.shblock.codecraft.core.cmd.CmdContext
import dev.shblock.codecraft.core.msg.Msg
import dev.shblock.codecraft.utils.buf.BufReader
import net.minecraft.resources.ResourceLocation
import kotlin.reflect.KClass

@Suppress("unused")
object CCRegistries {
    val CMD = ClassRegistry.create<Cmd, _>(CodeCraft.path("cmd")) { id, clazz ->
        ConstructingClassRegistryEntry(
            id, clazz,
            listOf(CmdContext::class, BufReader::class),
            constructorRequired = false
        )
    }

    val MSG = ClassRegistry.create<Msg, _>(CodeCraft.path("msg"), ::ClassRegistryEntry)

    fun register(name: ResourceLocation, clazz: KClass<Any>) = ClassRegistry.register(name, clazz)
    fun register(name: String, clazz: KClass<Any>) = ClassRegistry.register(name, clazz)
}

