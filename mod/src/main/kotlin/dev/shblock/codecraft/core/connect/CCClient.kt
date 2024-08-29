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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.full.primaryConstructor

class CCClient(private val session: DefaultWebSocketServerSession, mc: MinecraftServer) {
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
            writeResLoc(registry.key().location())
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
        logger.debug("Establish: registry sync packet sent")
    }

    suspend fun establish() {
        if (established) throw IllegalStateException("Already established")

        establishSyncRegistryIdMap()

        if (!receiveRaw().readBool()) // client signals initialization complete
            throw ClientException.ViolatedPolicy("Client signaled establishing failure")

        established = true
        logger.info("Established")
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun ensureEstablished() {
        if (!established) throw IllegalStateException("Not established")
    }

    suspend fun close(code: CloseReason.Codes, message: String): CloseReason {
        val reason = CloseReason(code, message)
        if (established) {
            established = false
            if (session.isActive) {
                logger.info("Closing connection: ${code.name}, \"${message}\"")
                session.close(reason)
            }
        }
        return reason
    }

    suspend fun close(): CloseReason {
        return close(
            CloseReason.Codes.NORMAL,
            "No message"
        )
    }

    fun queueClose(code: CloseReason.Codes, message: String) {
        session.launch { close(code, message) }
    }

    private suspend fun onDisconnected(): Nothing {
        val reason = session.closeReason.await()
        if (reason != null) {
            when (reason.code) {
                CloseReason.Codes.NORMAL.code, CloseReason.Codes.GOING_AWAY.code
                    -> throw ClientException.Disconnected(true, reason)

                else -> {
                    throw ClientException.Disconnected(false, reason)
                }
            }
        } else {
            throw ClientException.Disconnected(false, null)
        }
    }

    private suspend fun sendRaw(buf: CCByteBuf) {
        try {
            session.send(buf)
        } catch (e: ClosedSendChannelException) {
            onDisconnected()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ClientException.Internal("Send failed: $e", e)
        }
    }

    private suspend fun sendRaw(writer: CCByteBuf.() -> Unit) {
        val buf = CCByteBuf()
        buf.writer()
        sendRaw(buf)
    }

    private suspend fun receiveRaw(): CCByteBuf {
        val frame = try {
            session.incoming.receive()
        } catch (e: ClosedReceiveChannelException) {
            onDisconnected()
        }

        try {
            return CCByteBuf(frame.data)
        } catch (e: Exception) {
            throw ClientException.Internal("Receive failed: $e", e)
        }
    }

    fun sendMsg(msg: Msg) { // TODO batching
        ensureEstablished()
        session.launch {
            sendRaw {
                writeUsingRegistry(msg::class, CCRegistry.MSG_REGISTRY)
                msg.write(context, this@sendRaw)
            }
        }
    }

    internal suspend fun loop() {
        ensureEstablished()
        try {
            while (true) {
                val buf = receiveRaw()

                while (buf.readableBytes > 0) {
                    val cmd = try {
                        val cmdClass = buf.readUsingRegistryOrThrow(CCRegistry.CMD_REGISTRY).value()
                        cmdClass.primaryConstructor!!.call(context, buf)
                    } catch (original: Exception) {
                        val e = if (original is InvocationTargetException) original.cause else original
                        if (e is CCDecodingException) {
                            throw ClientException.ViolatedPolicy("Failed to parse command", cause = e)
                        } else {
                            throw ClientException.Internal(
                                "Unexpected error while parsing command",
                                e ?: original
                            )
                        }
                    }

                    context.runCmd(cmd)
                }
            }
        } catch (e: ClientException.Disconnected) {
            if (e.normal) {
                logger.info("Client disconnected: ${e.message}")
            } else {
                logger.info("Client disconnected abnormally: ${e.message}")
            }
            close() // for local cleanup
        } catch (e: ClientException.ViolatedPolicy) {
            logger.info("Disconnecting (violated policy): $e", e)
            close(
                CloseReason.Codes.VIOLATED_POLICY,
                e.toString() // TODO: better toString: include cause exception
            )
        } catch (original: Exception) {
            logger.error("Internal error, disconnecting", original)
            val e = if (original !is ClientException.Internal) {
                ClientException.Internal("Unexpected internal error", original)
            } else original
            close(
                CloseReason.Codes.INTERNAL_ERROR,
                e.toString() // TODO: better toString: include cause exception
            )
        }
    }

    @Suppress("unused")
    open class ClientException private constructor(message: String? = null, cause: Throwable? = null) :
        RuntimeException(message, cause) {
        /**
         * Represents an unexpected internal error.
         */
        class Internal(message: String? = null, cause: Throwable? = null) : ClientException(message, cause)

        /**
         * Represents an error caused by violation of the protocol by the client.
         */
        class ViolatedPolicy(message: String? = null, cause: Throwable? = null) : ClientException(message, cause)

        /**
         * Represents disconnections caused by the client disconnecting voluntarily or a network failure.
         */
        @Suppress("MemberVisibilityCanBePrivate")
        class Disconnected internal constructor(
            val normal: Boolean,
            val closeReason: CloseReason?,
            private val msg: String? = null
        ) : ClientException() {

            override val message: String?
                get() {
                    if (msg != null) return msg
                    if (closeReason != null)
                        return "${closeReason.code} (${closeReason.knownReason?.name ?: "UNKNOWN"}) ${closeReason.message}"
                    return null
                }

            override fun toString(): String {
                return "Disconnected(${if (normal) "Normal" else "Abnormal"}: ${message ?: closeReason})"
            }
        }
    }
}

private suspend fun WebSocketSession.send(data: CCByteBuf) {
    val buf = data.nioBuffer()
    send(Frame.Binary(true, buf)) //TODO: optimize? (avoid copying)
}