package dev.shblock.codecraft.core.cmd.cmds

import dev.shblock.codecraft.core.cmd.Cmd
import dev.shblock.codecraft.core.cmd.CmdContext
import dev.shblock.codecraft.core.cmd.CmdResult
import dev.shblock.codecraft.core.registry.CCAutoReg
import dev.shblock.codecraft.utils.buf.BufReader
import net.minecraft.network.chat.Component

@Suppress("MemberVisibilityCanBePrivate")
@CCAutoReg("send_system_chat")
class SendSystemChatCmd(context: CmdContext, buf: BufReader<*>) : Cmd(context, buf) {
    //TODO: component based message
    val message: Component = Component.literal(buf.readStr())

    override suspend fun executeImpl(): CmdResult {
        mc.playerList.broadcastSystemMessage(
            message,
            false
        )
        return success()
    }
}