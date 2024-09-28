package dev.shblock.codecraft.utils.eventloop

import dev.shblock.codecraft.CodeCraft
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.minecraft.server.MinecraftServer
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
class MinecraftServerEventLoop(private val mcServer: MinecraftServer) :
    TickedEventLoop(mcServer.runningThread) {
    override val currentTick: Int
        get() = mcServer.tickCount

    @EventBusSubscriber(modid = CodeCraft.MODID)
    companion object {
        private val instances = ConcurrentHashMap<MinecraftServer, MinecraftServerEventLoop>()

        operator fun get(server: MinecraftServer): MinecraftServerEventLoop =
            instances.getOrPut(server) { MinecraftServerEventLoop(server) }

        @SubscribeEvent
        private fun onServerTick(event: ServerTickEvent.Post) {
            instances[event.server]?.processAllImmediate()
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        private fun onServerStopping(event: ServerStoppingEvent) {
            instances.remove(event.server)?.apply {
                // All jobs running in the eventloop must be cancelled explicitly on server stop,
                // otherwise after the eventloop closes the jobs would simply "stall"

                // process cancellations, TODO handle cancelled tick events properly?
                processAllImmediate(tickTasks = false)
            }
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
inline operator fun Dispatchers.get(mcServer: MinecraftServer): CoroutineDispatcher =
    MinecraftServerEventLoop[mcServer]