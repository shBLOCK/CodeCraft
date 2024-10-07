package dev.shblock.codecraft.core.msg

import dev.shblock.codecraft.core.cmd.CmdContext
import dev.shblock.codecraft.core.cmd.CmdResult
import dev.shblock.codecraft.core.registry.CCAutoReg
import dev.shblock.codecraft.utils.buf.BufWriter
import dev.shblock.codecraft.utils.buf.writeEnum

@CCAutoReg("cmd_result")
internal class CmdResultMsg internal constructor(
    context: CmdContext,
    private val result: CmdResult
) : ContextMsg(context) {

    override fun write(context: CmdContext, buf: BufWriter<*>) {
        super.write(context, buf)
        buf.writeUVarInt(result.uid)
        buf.writeEnum(result.type)
        buf.(result.resultWriter)()
    }
}