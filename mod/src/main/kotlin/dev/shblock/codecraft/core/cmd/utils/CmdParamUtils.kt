package dev.shblock.codecraft.core.cmd.utils

import dev.shblock.codecraft.core.cmd.Cmd
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level

fun Cmd.getWorldOrThrow(key: ResourceKey<Level>) = mc.getLevel(key) ?: error("Invalid dimension: $key")
