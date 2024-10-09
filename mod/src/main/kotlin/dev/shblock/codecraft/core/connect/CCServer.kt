package dev.shblock.codecraft.core.connect

import dev.shblock.codecraft.CodeCraft
import dev.shblock.codecraft.Config
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.launch
import net.minecraft.server.MinecraftServer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent

@Suppress("UNUSED_PARAMETER")
@EventBusSubscriber(modid = CodeCraft.MODID, bus = EventBusSubscriber.Bus.GAME)
object CCServer {
    private val _clients: MutableCollection<CCClient> = mutableSetOf()

    @Suppress("unused")
    val clients: Collection<CCClient>
        get() = _clients

    private var server: ApplicationEngine? = null

    var running = false
        private set

    internal lateinit var mc: MinecraftServer
        private set

    private fun start() {
        if (running) throw IllegalStateException("Already running")

        CodeCraft.LOGGER.info("CodeCraft server starting...")
        //TODO: ktor logging?
        server = embeddedServer(
            Netty,
            port = 6767,
            watchPaths = emptyList(),
            configure = {}
        ) {
            install(WebSockets) {
//                pingPeriod = Duration.ofSeconds(5)
//                timeout = Duration.ofSeconds(0)
            }
            routing {
                webSocket {
                    val client = CCClient(this@webSocket, mc)

                    try {
                        client.establish()
                    } catch (e: Exception) {
                        client.logger.warn("Failed to establish client", e)
                        client.close(
                            CloseReason.Codes.VIOLATED_POLICY,
                            "Failed to establish: $e"
                        )
                        return@webSocket
                    }

                    _clients.add(client)

                    try {
                        client.loop()
                    } catch (e: Exception) {
                        client.logger.error("Unexpected error in client loop", e)
                        client.close(
                            CloseReason.Codes.INTERNAL_ERROR,
                            "Unexpected error: $e"
                        )
                        return@webSocket
                    } finally {
                        _clients.remove(client)
                    }
                }
            }
        }
        server!!.start(wait = false)
        running = true
        CodeCraft.LOGGER.info("CodeCraft server started")
    }

    private fun stop() {

        if (!running) throw IllegalStateException("Not running")

        CodeCraft.LOGGER.info("CodeCraft server stopping...")
        running = false
        _clients.forEach {
            it.apply { scope.launch { close(CloseReason.Codes.GOING_AWAY, "Server shutting down") } }
        }
        server!!.stop(
            gracePeriodMillis = Config.Server.shutdownGracePeriod.toLong(),
            timeoutMillis = Config.Server.shutdownGracePeriod.toLong()
        )
        CodeCraft.LOGGER.info("CodeCraft server stopped")
    }

    @SubscribeEvent
    private fun onMinecraftServerStarted(event: ServerStartedEvent) {
        this.mc = event.server
        if (!running) start()
    }

    @SubscribeEvent
    private fun onMinecraftServerStopping(event: ServerStoppingEvent) {
        if (running) stop()
    }
}