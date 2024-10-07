package dev.shblock.codecraft.core.cmd.cmds

import dev.shblock.codecraft.core.cmd.Cmd
import dev.shblock.codecraft.core.cmd.CmdContext
import dev.shblock.codecraft.utils.buf.BufReader
import dev.shblock.codecraft.utils.buf.readWorldKey


abstract class AbstractWorldCmd(context: CmdContext, buf: BufReader<*>) : Cmd(context, buf) {
    @Suppress("MemberVisibilityCanBePrivate")
    protected val worldKey = buf.readWorldKey(mc)
    val world by lazy {
        mc.getLevel(worldKey) ?: throw fail("Invalid dimension: $worldKey")
    }
}