package dev.shblock.codecraft.core.cmd.cmds

import dev.shblock.codecraft.core.CCAutoReg
import dev.shblock.codecraft.core.cmd.CmdContext
import dev.shblock.codecraft.utils.CCByteBuf

@Suppress("MemberVisibilityCanBePrivate")
@CCAutoReg("get_block")
class GetBlockCmd(context: CmdContext, buf: CCByteBuf) : AbstractWorldCmd(context, buf) {
    val pos = buf.readBlockPos()
    val nbt = buf.readBool()

    override fun run() {
        val blockState = world.getBlockState(pos)
        val blockEntity = if (nbt) world.getBlockEntity(pos) else null
        val blockNBT = blockEntity?.saveWithoutMetadata(world.registryAccess())//TODO: custom impl for error detecting

        success {
            writeBlockState(blockState)
            if (nbt) {
                writeBool(blockEntity != null)
                blockNBT?.also(::writeNBT)
            }
        }
    }
}