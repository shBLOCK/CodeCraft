package dev.shblock.codecraft.core.cmd.cmds

import dev.shblock.codecraft.core.cmd.CmdContext
import dev.shblock.codecraft.core.utils.CCByteBuf
import dev.shblock.codecraft.core.utils.has
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.Clearable
import net.minecraft.world.level.block.state.BlockState

//TODO: Block.updateFromNeighbourShapes

class SetBlockCmd(context: CmdContext, buf: CCByteBuf) : AbstractWorldCmd(context, buf) {
    private val pos = buf.readBlockPos()
    private val flags = buf.readByte().toInt()
    private val blockState =
        if (flags has SET_STATE)
            buf.readBlockState()
        else buf.readUsingRegistryOrThrow(BuiltInRegistries.BLOCK).value()
            .defaultBlockState()
    private val nbt = if (flags has SET_NBT) buf.readNBTCompound() else null

    override fun run() {
        val changed = setBlock(pos, blockState, flags, nbt)
        success { writeBool(changed) }
    }

    //TODO: put in abstract based class (AbstractSetBlockCmd) for SetBlocksCmd and FillCmd
    private fun setBlock(pos: BlockPos, blockState: BlockState, flags: Int, nbt: CompoundTag?): Boolean {
        if (flags has KEEP && world.getBlockState(pos).isAir)
            return false

        if (flags has DESTROY) {
            world.destroyBlock(pos, flags has DROP_ITEM)
        } else {
            Clearable.tryClear(world.getBlockEntity(pos))
        }

        var updateFlags = 0
        if (flags has BLOCK_UPDATE)
            updateFlags = updateFlags or 1
        if (flags has PREVENT_NEIGHBOR_REACTIONS)
            updateFlags = updateFlags or (16 and 32)


        val chunk = world.getChunkAt(pos)
        val oldBlockState = chunk.setBlockState(pos, blockState, false)

        if (nbt != null)
            world.getBlockEntity(pos)
                ?.loadWithComponents(nbt, world.registryAccess()) //TODO: custom impl for error detecting

        if (oldBlockState != null)
            world.markAndNotifyBlock(pos, chunk, oldBlockState, blockState, updateFlags, 512)

        return oldBlockState != null || nbt != null
    }

    companion object {
        const val SET_STATE = 1
        const val SET_NBT = 2
        const val BLOCK_UPDATE = 4
        const val PREVENT_NEIGHBOR_REACTIONS = 8
        const val KEEP = 16
        const val DESTROY = 32
        const val DROP_ITEM = 64
    }
}