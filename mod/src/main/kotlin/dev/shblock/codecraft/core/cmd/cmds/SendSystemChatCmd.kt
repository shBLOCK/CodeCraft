package dev.shblock.codecraft.core.cmd.cmds

import dev.shblock.codecraft.core.CCAutoReg
import dev.shblock.codecraft.core.cmd.Cmd
import dev.shblock.codecraft.core.cmd.CmdContext
import dev.shblock.codecraft.utils.CCByteBuf
import net.minecraft.network.chat.Component

@Suppress("MemberVisibilityCanBePrivate")
@CCAutoReg("send_system_chat")
class SendSystemChatCmd(context: CmdContext, buf: CCByteBuf) : Cmd(context, buf) {
    //TODO: component based message
    val message: Component = Component.literal(buf.readStr())

    override fun run() {
        mc.playerList.broadcastSystemMessage(
            message,
            false
        )
        success()
    }
}