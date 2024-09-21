package dev.shblock.codecraft.core.cmd.cmds

import dev.shblock.codecraft.core.CCAutoReg
import dev.shblock.codecraft.core.cmd.CmdContext
import dev.shblock.codecraft.core.utils.CCByteBuf

@CCAutoReg("get_block")
class GetBlockCmd(context: CmdContext, buf: CCByteBuf) : AbstractWorldCmd(context, buf) {
    private val pos = buf.readBlockPos()
    private val nbt = buf.readBool()

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