package dev.shblock.codecraft.core.connect

import com.google.common.primitives.Longs
import dev.shblock.codecraft.core.CCRegistry
import dev.shblock.codecraft.core.cmd.CmdContext
import dev.shblock.codecraft.core.cmd.dimensions
import dev.shblock.codecraft.core.msg.Msg
import dev.shblock.codecraft.core.utils.CCByteBuf
import dev.shblock.codecraft.core.utils.CCDecodingException
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.getOrElse
import kotlinx.coroutines.launch
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.full.primaryConstructor

class CCClient(private val session: WebSocketServerSession, mc: MinecraftServer) {
    var logger: Logger = LoggerFactory.getLogger(
        "CCClient(${session.call.request.local.remoteAddress}:${session.call.request.local.remotePort})"
    )
        private set

    init {
        logger.info("Accepted")
    }

    val context = CmdContext(this, mc)
    private var established = false

    companion object {
        private fun CCByteBuf.writeRegistryIdMapSyncPacket(registry: Registry<*>): CCByteBuf {
            writeResLoc(registry.key().registry())
            writeVarInt(registry.size())
            for (name in registry.keySet()) {
                writeVarInt(registry.getId(name))
                writeResLoc(name)
            }
            return this
        }

        private val registrySyncPacket by lazy {
            CCByteBuf()
                .writeRegistryIdMapSyncPacket(CCRegistry.CMD_REGISTRY)
                .writeRegistryIdMapSyncPacket(CCRegistry.MSG_REGISTRY)
                .writeRegistryIdMapSyncPacket(BuiltInRegistries.BLOCK)
                .writeRegistryIdMapSyncPacket(BuiltInRegistries.ITEM)
                .writeRegistryIdMapSyncPacket(BuiltInRegistries.ENTITY_TYPE)
                .writeRegistryIdMapSyncPacket(CCServer.mc.dimensions()) //TODO: dynamic dimensions
        }

        private val registrySyncPacketChecksum by lazy(registrySyncPacket::checksum)
    }

    private suspend fun establishSyncRegistryIdMap() {
        sendRaw { writeByteArray(Longs.toByteArray(registrySyncPacketChecksum)) }
        val cached = receiveRaw().readBool()
        logger.debug("Establish: client registry map cached: $cached")
        if (!cached) {
            sendRaw(registrySyncPacket)
        }
        logger.debug("Establish: registry sync complete")
    }

    suspend fun establish() {
        if (established) throw IllegalStateException("Already established")

        establishSyncRegistryIdMap()

        if (!receiveRaw().readBool())
            throw NetworkException("Client failed to establish")

        established = true
        logger.info("Established")
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun ensureEstablished() {
        if (!established) throw IllegalStateException("Not established")
    }

    suspend fun close(code: CloseReason.Codes, message: String) {
        val reason = CloseReason(code, message)
        if (established) {
            logger.info("Closing: ${code.name}, \"${message}\"")
            session.close(reason)
        } else {
            logger.debug("close() called on already closed client with reason: {}", reason)
        }
    }

    suspend fun close() {
        close(
            CloseReason.Codes.NORMAL,
            "No message"
        )
    }

    fun queueClose(code: CloseReason.Codes, message: String) {
        session.launch { close(code, message) }
    }

    fun queueClose() {
        session.launch { close() }
    }

    private suspend fun sendRaw(buf: CCByteBuf) {
        try {
            session.send(buf)
        } catch (e: ClosedSendChannelException) {
            throw NetworkException("Send failed (closed): ${e.message}", e)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw NetworkException("Send failed: $e", e)
        }
    }

    private suspend fun sendRaw(writer: CCByteBuf.() -> Unit) {
        val buf = CCByteBuf()
        buf.writer()
        sendRaw(buf)
    }

    private suspend fun receiveRaw(): CCByteBuf {
        val result = session.incoming.receiveCatching()
        val frame = result.getOrElse {
            if (it is ClosedReceiveChannelException) {
                throw NetworkException("Receive failed (closed): ${it.message}", it)
            } else if (it != null) {
                throw NetworkException("Receive failed: $it", it)
            } else {
                throw NetworkException("Receive failed")
            }
        }
        return CCByteBuf(frame.data)
    }

    fun sendMsg(msg: Msg) { // TODO batching
        ensureEstablished()
        session.launch { sendRaw { msg.write(context, this@sendRaw) } }
    }

    internal suspend fun loop() {
        ensureEstablished()
        try {
            while (true) {
                val buf = receiveRaw()

                while (buf.readableBytes > 0) {
                    val cmdId = buf.readVarInt()

                    val cmdClass = CCRegistry.CMD_REGISTRY.byId(cmdId)
                    if (cmdClass == null) {
                        close(
                            CloseReason.Codes.VIOLATED_POLICY,
                            "Invalid cmd id: $cmdId"
                        )
                        return
                    }

                    val cmd = try {
                        cmdClass.primaryConstructor!!.call(context, buf)
                    } catch (e: CCDecodingException) {
                        close(
                            CloseReason.Codes.VIOLATED_POLICY,
                            "Error while parsing command: $e"
                        )
                        return
                    } catch (e: Exception) {
                        close(
                            CloseReason.Codes.INTERNAL_ERROR,
                            "Unexpected error while parsing command: $e"
                        )
                        return
                    }

                    context.runCmd(cmd)
                }
            }
        } catch (e: NetworkException) {
            close(
                CloseReason.Codes.PROTOCOL_ERROR,
                "Network error: ${e.message}"
            )
            return
        }
    }

    @Suppress("unused")
    class NetworkException : RuntimeException {
        constructor() : super()
        constructor(message: String) : super(message)
        constructor(message: String, cause: Throwable) : super(message, cause)
        constructor(cause: Throwable) : super(cause)
    }
}

private suspend fun WebSocketSession.send(data: CCByteBuf) {
    send(Frame.Binary(true, data.nioBuffer())) //TODO: optimize? (avoid copying)
}