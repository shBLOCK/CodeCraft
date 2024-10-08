package dev.shblock.codecraft.core.connect

import dev.shblock.codecraft.core.msg.Msg
import dev.shblock.codecraft.core.registry.CCRegistries
import dev.shblock.codecraft.utils.CompletedJob
import dev.shblock.codecraft.utils.UserSourcedException
import dev.shblock.codecraft.utils.buf.BufReader
import dev.shblock.codecraft.utils.buf.BufWriter
import dev.shblock.codecraft.utils.buf.ByteBuf
import dev.shblock.codecraft.utils.buf.writeByRegistry
import dev.shblock.codecraft.utils.dimensions
import dev.shblock.codecraft.utils.self
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
) {
    var logger: Logger = LoggerFactory.getLogger(
        "CCClient(${session.call.request.local.remoteAddress}:${session.call.request.local.remotePort})"
    )
        private set

    init {
        logger.info("Accepted")
    }

    internal inline val scope: CoroutineScope get() = session

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
        private fun <SELF : BufWriter<SELF>> BufWriter<SELF>.writeRegistryIdMapSyncPacket(registry: Registry<*>): SELF {
            writeResLoc(registry.key().location())
            writeUVarInt(registry.size().toUInt())
            for (name in registry.keySet()) {
                writeVarInt(registry.getId(name))
                writeResLoc(name)
            }
            return self
        }

        private val registrySyncPacket by lazy {
            ByteBuf().also { buf ->
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
        sendRaw { writeULong(registrySyncPacketChecksum) }
        val cached = receiveRaw().readBool()
        logger.debug("Establish: client registry map cached: $cached")
        if (!cached) {
            sendRaw(registrySyncPacket)
            logger.debug("Establish: registry sync packet sent")
        }
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
        val deferred = scope.async {
            withContext(NonCancellable) {
                closeMutex.withLock {
                    val reason =
                        @OptIn(DelicateCoroutinesApi::class)
                        if (session.incoming.isClosedForReceive)
                            session.closeReason.await() ?: CloseReason(
                                CloseReason.Codes.VIOLATED_POLICY,
                                "Unknown reason"
                            )
                        else
                            CloseReason(code, message)

                    if (lifecycle == Lifecycle.CLOSED) return@withLock reason

                    if (lifecycle == Lifecycle.ACTIVE) cmdContext.close()
                    lifecycle = Lifecycle.CLOSED
                    logger.info("Closing connection: ${reason.knownReason?.name}, \"${reason.message}\"")
                    @OptIn(DelicateCoroutinesApi::class)
                    if (!session.incoming.isClosedForReceive) runCatching { session.close(reason) }
                    session.cancel("Closing: ${reason.knownReason?.name}, \"${reason.message}\"")

                    return@withLock reason
                }
            }
        }

        return deferred.await()
    }

    private suspend fun sendRaw(buf: ByteBuf<*>) {
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

    private suspend fun sendRaw(writer: BufWriter<*>.() -> Unit) {
        val buf = ByteBuf()
        buf.writer()
        sendRaw(buf)
    }

    private suspend fun receiveRaw(): BufReader<*> {
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

        return ByteBuf(frame.data)
    }

    suspend fun sendMsg(msg: Msg) { // TODO batching
        ensureActive()
        sendRaw {
            writeByRegistry(msg::class, CCRegistries.MSG)
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

                while (!buf.exhausted) {
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

private suspend fun WebSocketSession.send(data: ByteBuf<*>) {
    send(Frame.Binary(true, data.toByteArray())) //TODO: optimize? (avoid copying)
}