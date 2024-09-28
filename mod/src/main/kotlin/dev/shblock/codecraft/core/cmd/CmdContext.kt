package dev.shblock.codecraft.core.cmd

import dev.shblock.codecraft.core.msg.CmdResultMsg
import dev.shblock.codecraft.core.msg.Msg
import dev.shblock.codecraft.utils.CCByteBuf
import kotlinx.coroutines.*
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class CmdContext {
    abstract val mc: MinecraftServer

    protected abstract val scope: CoroutineScope

    @OptIn(ExperimentalStdlibApi::class)
    open val logger: Logger = LoggerFactory.getLogger("CmdContext(${super.hashCode().toHexString()})")

    open val active = true

    internal fun executeCmdFromBufAndPostResult(buf: CCByteBuf): Job {
        return scope.launch(Dispatchers.Unconfined) {
            try {
                Cmd.executeFromBuf(this@CmdContext, buf, ::handleCmdResult)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleCmdException(e)
            }
        }
    }

    protected open suspend fun handleCmdException(exception: Exception): Unit = throw exception

    private suspend fun handleCmdResult(cmdResult: CmdResult) =
        handleMsg(CmdResultMsg(this, cmdResult))

    abstract suspend fun handleMsg(msg: Msg)

    suspend fun close() {
        scope.cancel("Command context closing")
        scope.coroutineContext[Job]!!.join()
    }
}
