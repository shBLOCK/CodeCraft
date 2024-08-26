package dev.shblock.codecraft.core.msg

import dev.shblock.codecraft.core.cmd.CmdContext
import dev.shblock.codecraft.core.utils.CCByteBuf

/**
 * A general server-to-client message, can be written to multiple contexts multiple times.
 */
abstract class Msg {
    abstract fun write(context: CmdContext, buf: CCByteBuf)
}

/**
 * A single-use message that is bound to a context and can only be sent once.
 */
abstract class ContextMsg(protected val context: CmdContext) : Msg() {
    private var written = false

    override fun write(context: CmdContext, buf: CCByteBuf) {
        if (written) throw IllegalStateException("ContextMsg can only be written once")
        if (context != this.context) throw IllegalStateException("Wrong context for ContextMsg $this: $context")
        written = true
    }
}
