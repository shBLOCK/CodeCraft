package dev.shblock.codecraft.core.connect

import dev.shblock.codecraft.core.cmd.CmdContext
import dev.shblock.codecraft.core.msg.Msg
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger

class CCClientCmdContext(mc: MinecraftServer, val client: CCClient) : CmdContext(mc) {
    @Suppress("OVERRIDE_BY_INLINE")
    override inline val logger: Logger
        get() = client.logger

    override fun handleMsg(msg: Msg) = client.sendMsg(msg)
}