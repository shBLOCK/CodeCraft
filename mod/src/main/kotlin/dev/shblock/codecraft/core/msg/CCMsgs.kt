package dev.shblock.codecraft.core.msg

import dev.shblock.codecraft.CodeCraft
import dev.shblock.codecraft.core.CCRegistry
import dev.shblock.codecraft.core.MsgType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister

@Suppress("unused")
object CCMsgs {
    internal val MSGS = DeferredRegister.create(CCRegistry.MSG_REGISTRY_KEY, CodeCraft.MODID)

    val CMD_RESULT = reg("cmd_result", CmdResultMsg::class)

    private fun reg(name: String, msg: MsgType): DeferredHolder<MsgType, MsgType> =
        MSGS.register(name) { -> msg }
}