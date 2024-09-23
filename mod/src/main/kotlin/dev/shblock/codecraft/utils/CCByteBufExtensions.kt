package dev.shblock.codecraft.utils

import dev.shblock.codecraft.core.cmd.dimensions
import net.minecraft.server.MinecraftServer

fun CCByteBuf.readWorldKey(mc: MinecraftServer) = readUsingRegistryOrThrow(mc.dimensions()).key!!
