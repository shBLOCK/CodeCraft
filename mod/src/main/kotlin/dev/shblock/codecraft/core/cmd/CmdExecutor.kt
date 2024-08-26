package dev.shblock.codecraft.core.cmd

import dev.shblock.codecraft.CodeCraft
import dev.shblock.codecraft.core.connect.CCServer
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.tick.ServerTickEvent

@Suppress("MemberVisibilityCanBePrivate")
@EventBusSubscriber(modid = CodeCraft.MODID, bus = EventBusSubscriber.Bus.GAME)
object CmdExecutor {
    fun getActiveCmdContexts(): Collection<CmdContext> {
        val contexts = mutableListOf<CmdContext>()
        CCServer.clients.forEach { contexts.add(it.context) }
        return contexts.toList()
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    private fun onServerTick(event: ServerTickEvent.Post) {
        getActiveCmdContexts().forEach { it.onPostServerTick(event.server) }
    }
}

fun MinecraftServer.dimensions(): Registry<Level> = registryAccess().registryOrThrow(Registries.DIMENSION)
