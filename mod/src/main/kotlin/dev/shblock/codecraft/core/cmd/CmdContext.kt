package dev.shblock.codecraft.core.cmd

import dev.shblock.codecraft.CodeCraft
import dev.shblock.codecraft.core.connect.CCClient
import dev.shblock.codecraft.core.msg.Msg
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class CmdContext(val client: CCClient?, val mc: MinecraftServer) {
    internal val onTickQueue: Queue<Cmd.Task> = ConcurrentLinkedQueue()

    private inline val logger: Logger
        get() = client?.logger ?: CodeCraft.LOGGER

    fun runCmd(cmd: Cmd) = runCmdCode(cmd, cmd::run)

    private fun runCmdTask(task: Cmd.Task) = runCmdCode(task.cmd, task.task)

    private inline fun runCmdCode(cmd: Cmd, block: () -> Unit) {
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

    fun sendMsg(msg: Msg) {
        client?.sendMsg(msg)
    }
}

private fun <T> Queue<T>.consumeAll(consumer: (T) -> Unit) {
    while (isNotEmpty()) consumer(poll())
}
