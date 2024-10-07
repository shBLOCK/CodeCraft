package dev.shblock.codecraft.core.cmd

import dev.shblock.codecraft.core.registry.CCRegistries
import dev.shblock.codecraft.utils.UserSourcedException
import dev.shblock.codecraft.utils.buf.BufException
import dev.shblock.codecraft.utils.buf.BufReader
import dev.shblock.codecraft.utils.buf.BufWriter
import dev.shblock.codecraft.utils.buf.readByClassRegistryOrThrow
import kotlinx.coroutines.CancellationException
import net.minecraft.server.MinecraftServer

typealias CmdResultHandler<T> = suspend (CmdResult) -> T

/**
 * A client-to-server command.
 *
 * Command objects are single-use,
 * which means one command object can not be used to receive multiple commands from contexts
 * nor can it be executed multiple times.
 */
@Suppress("MemberVisibilityCanBePrivate")
abstract class Cmd(val context: CmdContext, val uid: UInt) {
    constructor(context: CmdContext, buf: BufReader<*>) : this(context, buf.readUVarInt())

    init {
        if (uid < 0u) throw BufException("Command uid has to be >=0, got $uid")
    }

    protected abstract suspend fun executeImpl(): CmdResult

    internal suspend fun <T : Any> execute(resultHandler: CmdResultHandler<T>): T {
        return doExecute(context, resultHandler) { this }
    }

    companion object {
        internal suspend fun <T : Any> executeFromBuf(
            context: CmdContext,
            buf: BufReader<*>,
            resultHandler: CmdResultHandler<T>
        ): T {
            return doExecute(context, resultHandler) {
                try {
                    parse(context, buf)
                } catch (e: BufException) {
                    throw UserSourcedException("Failed to parse command", cause = e)
                }
            }
        }

        @Suppress("NOTHING_TO_INLINE")
        inline fun parse(context: CmdContext, buf: BufReader<*>) =
            buf.readByClassRegistryOrThrow(CCRegistries.CMD)
                .also { if (!it.constructable) throw BufException("${it.id} doesn't support parsing") }
                .construct(context, buf)

        /**
         * Execute the command provided by [cmdProvider].
         *
         * Use [kotlinx.coroutines.Dispatchers.Unconfined] to ensure that the [cmdProvider] gets executed before it suspends.
         */
        @Suppress("UNUSED_PARAMETER")
        private suspend inline fun <T : Any> doExecute(
            context: CmdContext,
            resultHandler: CmdResultHandler<T>,
            cmdProvider: () -> Cmd
        ): T {
            try {
                val cmd = cmdProvider()
                return resultHandler(cmd.executeImpl())
            } catch (result: CmdResult) {
                // This is to make handling errors in the result writer easier
                // (the unsuccessful results should only contain some basic error messages)
                check(!result.successful) { "Don't use throw for a successful result!" }
                return resultHandler(result)
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    inline val mc: MinecraftServer
        get() = context.mc

    @Suppress("NOTHING_TO_INLINE")
    private inline fun result(type: CmdResult.Type, noinline resultWriter: BufWriter<*>.() -> Unit) =
        CmdResult(uid, type, resultWriter)

    /**
     * Create a success result, you should return the result immediately.
     *
     * The result writer should ideally not contain too much logic of the command and just write the results.
     */
    protected fun success(resultWriter: BufWriter<*>.() -> Unit = { }) = result(CmdResult.Type.SUCCESS, resultWriter)

    protected fun fail(msg: String) = result(CmdResult.Type.FAIL) { writeStr(msg) }

    protected fun fail(cause: Throwable) = fail("$cause")
}

class CmdResult internal constructor(
    // We don't include the command object as it might not be properly initialized and can result in unexpected errors
    val uid: UInt,
    val type: Type,
    val resultWriter: BufWriter<*>.() -> Unit
) : Throwable() {
    enum class Type {
        SUCCESS,
        FAIL
    }

    val successful get() = type == Type.SUCCESS
}