package dev.shblock.codecraft.core.cmd.cmds

import dev.shblock.codecraft.core.cmd.Cmd
import dev.shblock.codecraft.core.cmd.CmdContext
import dev.shblock.codecraft.core.utils.CCByteBuf
import net.minecraft.network.chat.Component

class SendSystemChatCmd(context: CmdContext, buf: CCByteBuf) : Cmd(context, buf) {
    //TODO: component based message
    private val message = buf.readStr()

    override fun run() {
        mc.playerList.broadcastSystemMessage(
            Component.literal(message),
            false
        )
        success()
    }
}