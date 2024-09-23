package dev.shblock.codecraft.core.mc

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

object CodeCraftCommand {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("codecraft")
                .then(
                    Commands.literal("dev")
                )
        )
    }
}