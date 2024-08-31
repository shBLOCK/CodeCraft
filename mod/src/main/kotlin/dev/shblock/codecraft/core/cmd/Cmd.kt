package dev.shblock.codecraft.core.cmd

import dev.shblock.codecraft.core.msg.CmdResultMsg
import dev.shblock.codecraft.core.utils.CCByteBuf
import dev.shblock.codecraft.core.utils.CCDecodingException
import net.minecraft.server.MinecraftServer
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * A client-to-server command.
 *
 * Command objects are single-use,
 * which means one command object can not be used to receive multiple commands from contexts
 * nor can it be executed multiple times.
 */
abstract class Cmd(val context: CmdContext, buf: CCByteBuf) {
    @Suppress("MemberVisibilityCanBePrivate")
    val uid = buf.readVarInt()

    init {
        if (uid < 0) throw CCDecodingException("Command uid has to be >=0, got $uid")
    }

    @Internal
    abstract fun run()

    protected fun onTick(block: () -> Unit) {
        context.onTickQueue.add(Task(this, block))
    }

    inline val mc: MinecraftServer
        get() = context.mc

    /**
     * Return a success result to the client.
     *
     * PLEASE DO NOT PUT THE COMMAND EXECUTION CODE IN `resultWriter`!!!
     * IT'S ONLY FOR WRITING THE RESULTS!
     */
    protected fun success(resultWriter: CCByteBuf.() -> Unit = { }): Nothing {
        successNoThrow(resultWriter)
        throw CmdException.Success()
    }

    protected fun successNoThrow(resultWriter: CCByteBuf.() -> Unit = { }) {
        context.sendMsg(CmdResultMsg(context, uid, true, resultWriter))
    }

    internal fun errorNoThrow(msg: String) {
        context.sendMsg(
            CmdResultMsg(context, uid, false) {
                it.writeStr(msg)
            }
        )
    }

    internal fun errorNoThrow(msg: String, exception: Exception) {
        context.sendMsg(
            CmdResultMsg(context, uid, false) {
                it.writeStr("$msg ($exception)")
            }
        )
    }

    internal fun errorNoThrow(exception: Exception) {
        context.sendMsg(
            CmdResultMsg(context, uid, false) {
                it.writeStr("$exception")
            }
        )
    }

    internal fun error(msg: String): Nothing {
        errorNoThrow(msg)
        throw CmdException.Error(msg)
    }

    internal fun error(msg: String, exception: Exception): Nothing {
        errorNoThrow(msg, exception)
        throw CmdException.Error(msg, exception)
    }

    internal fun error(exception: Exception): Nothing {
        errorNoThrow(exception)
        throw CmdException.Error(cause = exception)
    }

    internal class Task(val cmd: Cmd, val task: () -> Unit)

    internal sealed class CmdException(message: String? = null, cause: Throwable? = null) :
        RuntimeException(message, cause) {
        class Error(message: String? = null, cause: Throwable? = null) : CmdException(message, cause)
        class Success : CmdException()
    }
}
