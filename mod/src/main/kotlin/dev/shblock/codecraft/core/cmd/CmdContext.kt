package dev.shblock.codecraft.core.cmd

import dev.shblock.codecraft.core.msg.Msg
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

abstract class CmdContext(val mc: MinecraftServer) {
    internal val onTickQueue: Queue<Cmd.Task> = ConcurrentLinkedQueue()

    @OptIn(ExperimentalStdlibApi::class)
    open val logger: Logger = LoggerFactory.getLogger("CmdContext(${super.hashCode().toHexString()})")

    open val valid = true

    fun runCmd(cmd: Cmd) = runCmdCode(cmd, cmd::run)

    private fun runCmdTask(task: Cmd.Task) = runCmdCode(task.cmd, task.task)

    private inline fun runCmdCode(cmd: Cmd, block: () -> Unit) {
        if (!valid) return

        try {
            block()
        } catch (_: Cmd.CmdException) {
        } catch (e: Exception) {
            cmd.errorNoThrow("Internal error", e)
            logger.error("Uncaught error in command $cmd", e)
        }
    }

    internal fun onPostServerTick(server: MinecraftServer) {
        if (server != mc) return
        onTickQueue.consumeAll(::runCmdTask)
    }

    abstract fun handleMsg(msg: Msg)
}

private fun <T> Queue<T>.consumeAll(consumer: (T) -> Unit) {
    while (isNotEmpty()) consumer(poll())
}
