package dev.shblock.codecraft.core.cmd.cmds

import dev.shblock.codecraft.core.cmd.CmdContext
import dev.shblock.codecraft.core.cmd.CmdResult
import dev.shblock.codecraft.core.registry.CCAutoReg
import dev.shblock.codecraft.utils.CCByteBuf
import dev.shblock.codecraft.utils.eventloop.get
import dev.shblock.codecraft.utils.has
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.invoke
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.Clearable
import net.minecraft.world.level.block.state.BlockState

//TODO: Block.updateFromNeighbourShapes
@Suppress("MemberVisibilityCanBePrivate")
@CCAutoReg("set_block")
class SetBlockCmd(context: CmdContext, buf: CCByteBuf) : AbstractWorldCmd(context, buf) {
    val pos = buf.readBlockPos()
    private val flags = buf.readByte()
    val blockState: BlockState =
        if (flags has SET_STATE)
            buf.readBlockState()
        else buf.readUsingRegistryOrThrow(BuiltInRegistries.BLOCK).value()
            .defaultBlockState()
    val nbt = if (flags has SET_NBT) buf.readNBTCompound() else null

    override suspend fun executeImpl(): CmdResult {
        val block = suspend {
            val changed = setBlock(pos, blockState, flags, nbt)
            success { writeBool(changed) }
        }
        return if (flags has ON_TICK) Dispatchers[mc]{ block() } else block()
    }

    //TODO: put in abstract based class (AbstractSetBlockCmd) for SetBlocksCmd and FillCmd
    private fun setBlock(pos: BlockPos, blockState: BlockState, flags: Byte, nbt: CompoundTag?): Boolean {
        if (world.isOutsideBuildHeight(pos)) return false

        if (flags has KEEP && world.getBlockState(pos).isAir)
            return false

        if (flags has DESTROY) {
            world.destroyBlock(pos, flags has DROP_ITEM)
        } else {
            Clearable.tryClear(world.getBlockEntity(pos))
        }

        var updateFlags = 2 // always send updates to client
        if (flags has BLOCK_UPDATE)
            updateFlags = updateFlags or 1
        else
            updateFlags = updateFlags or (16 or 32) // prevent neighbor reactions


        val chunk = world.getChunkAt(pos)
        val oldBlockState = chunk.setBlockState(pos, blockState, false)

        if (nbt != null)
            world.getBlockEntity(pos)
                ?.loadWithComponents(nbt, world.registryAccess()) //TODO: custom impl for error detecting

        if (oldBlockState != null)
            world.markAndNotifyBlock(pos, chunk, oldBlockState, blockState, updateFlags, 512)

        return oldBlockState != null || nbt != null
    }

    private companion object {
        const val SET_STATE = 1.toByte()
        const val SET_NBT = 2.toByte()
        const val ON_TICK = 4.toByte()
        const val BLOCK_UPDATE = 8.toByte()
        const val KEEP = 16.toByte()
        const val DESTROY = 32.toByte()
        const val DROP_ITEM = 64.toByte()
    }
}