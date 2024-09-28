package dev.shblock.codecraft.utils

import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level

fun MinecraftServer.dimensions(): Registry<Level> = registryAccess().registryOrThrow(Registries.DIMENSION)
