package dev.shblock.codecraft.core

import dev.shblock.codecraft.CodeCraft
import dev.shblock.codecraft.core.cmd.Cmd
import dev.shblock.codecraft.core.msg.Msg
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.registries.NewRegistryEvent
import net.neoforged.neoforge.registries.RegistryBuilder
import kotlin.reflect.KClass

typealias CmdType = KClass<out Cmd>
typealias MsgType = KClass<out Msg>

@Suppress("MemberVisibilityCanBePrivate")
@EventBusSubscriber(modid = CodeCraft.MODID, bus = EventBusSubscriber.Bus.MOD)
object CCRegistry {
    val CMD_REGISTRY_KEY: ResourceKey<Registry<CmdType>> = ResourceKey.createRegistryKey(CodeCraft.path("cmd"))

    val CMD_REGISTRY: Registry<CmdType> = RegistryBuilder(CMD_REGISTRY_KEY).create()

    val MSG_REGISTRY_KEY: ResourceKey<Registry<MsgType>> = ResourceKey.createRegistryKey(CodeCraft.path("msg"))

    val MSG_REGISTRY: Registry<MsgType> = RegistryBuilder(MSG_REGISTRY_KEY).create()

    @SubscribeEvent
    private fun onNewRegistry(event: NewRegistryEvent) {
        event.register(CMD_REGISTRY)
        event.register(MSG_REGISTRY)
    }
}