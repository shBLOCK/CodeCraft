package dev.shblock.codecraft.core.mc

import dev.shblock.codecraft.CodeCraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterCommandsEvent

@EventBusSubscriber(modid = CodeCraft.MODID, bus = EventBusSubscriber.Bus.GAME)
object CCCommands {
    @SubscribeEvent
    fun onCommandRegister(event: RegisterCommandsEvent) {
        CodeCraftCommand.register(event.dispatcher)
    }
}