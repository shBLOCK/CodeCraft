package dev.shblock.codecraft.core.msg

import dev.shblock.codecraft.core.cmd.CmdContext
import dev.shblock.codecraft.core.cmd.CmdResult
import dev.shblock.codecraft.core.registry.CCAutoReg
import dev.shblock.codecraft.utils.CCByteBuf

@CCAutoReg("cmd_result")
internal class CmdResultMsg internal constructor(
    context: CmdContext,
    private val result: CmdResult
) : ContextMsg(context) {

    override fun write(context: CmdContext, buf: CCByteBuf) {
        super.write(context, buf)
        buf.writeVarInt(result.uid)
        buf.writeEnum(result.type)
        buf.(result.resultWriter)()
    }
}