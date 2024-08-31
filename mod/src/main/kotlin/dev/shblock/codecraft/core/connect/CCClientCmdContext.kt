package dev.shblock.codecraft.core.connect

import dev.shblock.codecraft.core.cmd.CmdContext
import dev.shblock.codecraft.core.msg.Msg
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger

class CCClientCmdContext(mc: MinecraftServer, val client: CCClient) : CmdContext(mc) {
    @Suppress("OVERRIDE_BY_INLINE")
    override inline val logger: Logger
        get() = client.logger

    @Suppress("OVERRIDE_BY_INLINE")
    override inline val valid: Boolean
        get() = client.lifecycle == CCClient.Lifecycle.ACTIVE

    override fun handleMsg(msg: Msg) = try {
        client.sendMsg(msg)
    } catch (_: CCClient.ClientException) {
        // We ignore ClientExceptions to avoid throwing unexpected exceptions to Cmd.errorNoThrow().
        // TODO: Definitely not the best solution to handle ClientExceptions in handleMsg, refactor needed
    }
}