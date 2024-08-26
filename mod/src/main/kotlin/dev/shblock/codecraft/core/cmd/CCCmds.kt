package dev.shblock.codecraft.core.cmd

import dev.shblock.codecraft.CodeCraft
import dev.shblock.codecraft.core.CCRegistry
import dev.shblock.codecraft.core.CmdType
import dev.shblock.codecraft.core.cmd.cmds.GetBlockCmd
import dev.shblock.codecraft.core.cmd.cmds.SendSystemChatCmd
import dev.shblock.codecraft.core.cmd.cmds.SetBlockCmd
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister

@Suppress("unused")
object CCCmds {
    internal val CMDS = DeferredRegister.create(CCRegistry.CMD_REGISTRY_KEY, CodeCraft.MODID)

    val SEND_SYSTEM_CHAT = reg("send_system_chat", SendSystemChatCmd::class)
    val SET_BLOCK = reg("set_block", SetBlockCmd::class)
    val GET_BLOCK = reg("get_block", GetBlockCmd::class)

    private fun reg(name: String, cmd: CmdType): DeferredHolder<CmdType, CmdType> =
        CMDS.register(name) { -> cmd }
}