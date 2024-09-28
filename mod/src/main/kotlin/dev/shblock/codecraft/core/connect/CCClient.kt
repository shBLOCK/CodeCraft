package dev.shblock.codecraft.core.connect

import com.google.common.primitives.Longs
import dev.shblock.codecraft.core.msg.Msg
import dev.shblock.codecraft.core.registry.CCRegistries
import dev.shblock.codecraft.utils.CCByteBuf
import dev.shblock.codecraft.utils.UserSourcedException
import dev.shblock.codecraft.utils.dimensions
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class CCClient(
    private val session: DefaultWebSocketServerSession,
    val mc: MinecraftServer
) : CoroutineScope by session {
    var logger: Logger = LoggerFactory.getLogger(
        "CCClient(${session.call.request.local.remoteAddress}:${session.call.request.local.remotePort})"
    )
        private set

    init {
        logger.info("Accepted")
    }

    private lateinit var _cmdContext: CCClientCmdContext
    val cmdContext: CCClientCmdContext
        get() {
            if (lifecycle == Lifecycle.CONNECTED)
                throw IllegalStateException("The client has not been established")
            return _cmdContext
        }

    enum class Lifecycle {
        CONNECTED, ACTIVE, CLOSED
    }

    var lifecycle = Lifecycle.CONNECTED
        private set

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
            CCByteBuf().also { buf ->
                arrayOf(
                    CCRegistries.CMD,
                    CCRegistries.MSG,
                    BuiltInRegistries.BLOCK,
                    BuiltInRegistries.ITEM,
                    BuiltInRegistries.ENTITY_TYPE,
                    CCServer.mc.dimensions() //TODO: handle dynamic dimensions?
                ).forEach { buf.writeRegistryIdMapSyncPacket(it) }
            }
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
        if (lifecycle != Lifecycle.CONNECTED)
            throw IllegalStateException("Client's lifecycle is invalid for establishing: $lifecycle")

        establishSyncRegistryIdMap()

        if (!receiveRaw().readBool()) // client signals initialization complete
            throw UserSourcedException("Client signaled establishing failure")

        _cmdContext = CCClientCmdContext(this)
        lifecycle = Lifecycle.ACTIVE
        logger.info("Established")
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun ensureActive() {
        if (lifecycle != Lifecycle.ACTIVE) throw IllegalStateException("not active")
    }

    private val closeMutex = Mutex()

    /**
     * Close the client with the specified reason, defaulting to VIOLATED_POLICY and no message.
     *
     * If the client connection is already closed, session.close() won't be called again.
     *
     * @return the close reason;
     * if the client connection has already closed,
     * the original close reason would be used instead of that from the parameters
     */
    suspend fun close(code: CloseReason.Codes = CloseReason.Codes.VIOLATED_POLICY, message: String = ""): CloseReason {
        return withContext(NonCancellable) {
            closeMutex.withLock {
                val reason =
                    @OptIn(DelicateCoroutinesApi::class)
                    if (session.incoming.isClosedForReceive)
                        session.closeReason.await() ?: CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unknown reason")
                    else
                        CloseReason(code, message)

                if (lifecycle == Lifecycle.CLOSED) return@withLock reason

                cmdContext.close()
                lifecycle = Lifecycle.CLOSED
                logger.info("Closing connection: ${reason.knownReason?.name}, \"${reason.message}\"")
                @OptIn(DelicateCoroutinesApi::class)
                if (!session.incoming.isClosedForReceive) runCatching { session.close(reason) }
                session.cancel("Closing: ${reason.knownReason?.name}, \"${reason.message}\"")

                return@withLock reason
            }
        }
    }

    private suspend fun sendRaw(buf: CCByteBuf) {
        try {
            session.send(buf)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // in case of ClosedSendChannelException, the original close reason would be used (see doc of close())
            close(CloseReason.Codes.INTERNAL_ERROR, e.toString())
            throw e
        } finally {
            @OptIn(DelicateCoroutinesApi::class)
            if (session.incoming.isClosedForReceive) close()
            // no need to throw anything, all exceptions has been rethrown
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
        } catch (e: CancellationException) {
            // For the incoming channel, a closed connection would only throw ClosedReceiveChannelException,
            // so no need to check for closure here
            throw e
        } catch (e: Exception) {
            // in case of ClosedReceiveChannelException, the original close reason would be used (see doc of close())
            close(CloseReason.Codes.INTERNAL_ERROR, e.toString())
            throw e
        }

        return CCByteBuf(frame.data)
    }

    suspend fun sendMsg(msg: Msg) { // TODO batching
        ensureActive()
        sendRaw {
            writeUsingRegistry(msg::class, CCRegistries.MSG)
            msg.write(cmdContext, this@sendRaw)
        }
    }

    internal suspend fun loop() {
        ensureActive()
        try {
            while (lifecycle == Lifecycle.ACTIVE) {
                val buf = try {
                    receiveRaw()
                } catch (e: ClosedReceiveChannelException) {
                    close()
                    return
                }

                while (buf.readableBytes > 0) {
                    cmdContext.executeCmdFromBufAndPostResult(buf)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: UserSourcedException) {
            logger.info("Disconnecting (violated policy): $e", e)
            close(
                CloseReason.Codes.VIOLATED_POLICY,
                e.toString()
            )
        } catch (e: Exception) {
            logger.error("Internal error, disconnecting", e)
            close(
                CloseReason.Codes.INTERNAL_ERROR,
                e.toString()
            )
        }
    }
}

private suspend fun WebSocketSession.send(data: CCByteBuf) {
    val buf = data.nioBuffer()
    send(Frame.Binary(true, buf)) //TODO: optimize? (avoid copying)
}