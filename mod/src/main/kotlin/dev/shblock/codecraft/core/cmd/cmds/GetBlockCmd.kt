package dev.shblock.codecraft.core.cmd.cmds

import dev.shblock.codecraft.core.cmd.CmdContext
import dev.shblock.codecraft.core.cmd.CmdResult
import dev.shblock.codecraft.core.registry.CCAutoReg
import dev.shblock.codecraft.utils.buf.BufReader
import dev.shblock.codecraft.utils.buf.readBlockPos


@Suppress("MemberVisibilityCanBePrivate")
@CCAutoReg("get_block")
class GetBlockCmd(context: CmdContext, buf: BufReader<*>) : AbstractWorldCmd(context, buf) {
    val pos = buf.readBlockPos()
    val nbt = buf.readBool()

    override suspend fun executeImpl(): CmdResult {
        val blockState = world.getBlockState(pos)
        val blockEntity = if (nbt) world.getBlockEntity(pos) else null
        val blockNBT = blockEntity?.saveWithoutMetadata(world.registryAccess())//TODO: custom impl for error detecting

        return success {
            writeBlockState(blockState)
            if (nbt) {
                writeBool(blockEntity != null)
                blockNBT?.also(::writeNBT)
            }
        }
    }
}