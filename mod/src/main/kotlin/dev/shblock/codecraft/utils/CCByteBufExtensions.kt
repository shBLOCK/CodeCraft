package dev.shblock.codecraft.utils

import net.minecraft.server.MinecraftServer

fun CCByteBuf.readWorldKey(mc: MinecraftServer) = readUsingRegistryOrThrow(mc.dimensions()).key!!
