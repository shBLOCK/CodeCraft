package dev.shblock.codecraft.core.cmd.cmds

import dev.shblock.codecraft.core.cmd.Cmd
import dev.shblock.codecraft.core.cmd.CmdContext
import dev.shblock.codecraft.core.cmd.utils.getWorldOrThrow
import dev.shblock.codecraft.utils.CCByteBuf
import dev.shblock.codecraft.utils.readWorldKey

abstract class AbstractWorldCmd(context: CmdContext, buf: CCByteBuf) : Cmd(context, buf) {
    @Suppress("MemberVisibilityCanBePrivate")
    protected val worldKey = buf.readWorldKey(mc)
    protected val world by lazy {
        getWorldOrThrow(worldKey)
    }
}