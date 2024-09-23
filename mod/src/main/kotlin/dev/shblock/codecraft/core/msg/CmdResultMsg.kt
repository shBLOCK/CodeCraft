package dev.shblock.codecraft.core.msg

import dev.shblock.codecraft.core.CCAutoReg
import dev.shblock.codecraft.core.cmd.CmdContext
import dev.shblock.codecraft.utils.CCByteBuf

@CCAutoReg("cmd_result")
class CmdResultMsg internal constructor(
    context: CmdContext,
    private val uid: Int,
    private val success: Boolean,
    private val contentWriter: (CCByteBuf) -> Unit
) : ContextMsg(context) {
    // Shouldn't hold a reference to the command object so that it can be freed.

    override fun write(context: CmdContext, buf: CCByteBuf) {
        super.write(context, buf)
        buf.writeVarInt(uid)
        buf.writeBool(success)
        contentWriter(buf)
    }
}