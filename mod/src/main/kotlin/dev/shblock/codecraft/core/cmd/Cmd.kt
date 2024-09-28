package dev.shblock.codecraft.core.cmd

import dev.shblock.codecraft.core.registry.CCRegistries
import dev.shblock.codecraft.core.registry.ClassRegistryEntry
import dev.shblock.codecraft.utils.CCByteBuf
import dev.shblock.codecraft.utils.CCDecodingException
import dev.shblock.codecraft.utils.UserSourcedException
import kotlinx.coroutines.CancellationException
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import org.jetbrains.annotations.ApiStatus
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KClass
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSupertypeOf
import kotlin.reflect.full.valueParameters

typealias CmdResultHandler<T> = suspend (CmdResult) -> T

/**
 * A client-to-server command.
 *
 * Command objects are single-use,
 * which means one command object can not be used to receive multiple commands from contexts
 * nor can it be executed multiple times.
 */
abstract class Cmd(val context: CmdContext, val uid: Int) {
    constructor(context: CmdContext, buf: CCByteBuf) : this(context, buf.readVarInt())

    init {
        if (uid < 0) throw CCDecodingException("Command uid has to be >=0, got $uid")
    }

    protected abstract suspend fun executeImpl(): CmdResult

    internal suspend fun <T : Any> execute(resultHandler: CmdResultHandler<T>): T {
        return doExecute(context, resultHandler) { this }
    }

    companion object {
        internal suspend fun <T : Any> executeFromBuf(
            context: CmdContext,
            buf: CCByteBuf,
            resultHandler: CmdResultHandler<T>
        ): T {
            return doExecute(context, resultHandler) {
                try {
                    parse(context, buf)
                } catch (e: CCDecodingException) {
                    throw UserSourcedException("Failed to parse command", cause = e)
                }
            }
        }

        @Suppress("NOTHING_TO_INLINE")
        inline fun parse(context: CmdContext, buf: CCByteBuf) =
            buf.readUsingClassRegistryOrThrow(CCRegistries.CMD)
                .parse(context, buf)

        /**
         * Execute the command provided by [cmdProvider].
         *
         * Use [kotlinx.coroutines.Dispatchers.Unconfined] to ensure that the [cmdProvider] gets executed before it suspends.
         */
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
    private inline fun result(type: CmdResult.Type, noinline resultWriter: CCByteBuf.() -> Unit) =
        CmdResult(uid, type, resultWriter)

    /**
     * Create a success result, you should return the result immediately.
     *
     * The result writer should ideally not contain too much logic of the command and just write the results.
     */
    protected fun success(resultWriter: CCByteBuf.() -> Unit = { }) = result(CmdResult.Type.SUCCESS, resultWriter)

    protected fun fail(msg: String) = result(CmdResult.Type.FAIL) { writeStr(msg) }

    protected fun fail(cause: Throwable) = fail("$cause")
}

class CmdResult internal constructor(
    // We don't include the command object as it might not be properly initialized and can result in unexpected errors
    val uid: Int,
    val type: Type,
    val resultWriter: CCByteBuf.() -> Unit
) : Throwable() {
    enum class Type {
        SUCCESS,
        FAIL
    }

    val successful get() = type == Type.SUCCESS
}

@Suppress("NOTHING_TO_INLINE")
@ApiStatus.Internal
class CmdRegistryEntry(id: ResourceLocation, clazz: KClass<out Cmd>) : ClassRegistryEntry<Cmd>(id, clazz) {
    val parserConstructor =
        clazz.constructors.find {
            it.valueParameters.size == 2
                && it.valueParameters[0].type.isSupertypeOf(CmdContext::class.createType())
                && it.valueParameters[1].type.isSupertypeOf(CCByteBuf::class.createType())
        }

    inline val supportsParsing get() = parserConstructor != null

    inline fun parse(context: CmdContext, buf: CCByteBuf): Cmd {
        return try {
            parserConstructor?.call(context, buf)
                ?: throw IllegalStateException("${clazz.simpleName} doesn't support buffer parsing")
        } catch (e: InvocationTargetException) {
            // unwrap InvocationTargetException
            throw e.cause!!
        }
    }
}