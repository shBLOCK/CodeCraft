package dev.shblock.codecraft

import com.mojang.logging.LogUtils
import dev.shblock.codecraft.core.registry.CCRegistries
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import org.slf4j.Logger

@Mod(CodeCraft.MODID)
class CodeCraft {
    companion object {
        const val MODID = "codecraft"

        fun path(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MODID, path)

        val LOGGER: Logger = LogUtils.getLogger()
    }

    @Suppress("ConvertSecondaryConstructorToPrimary", "unused")
    constructor(modEventBus: IEventBus, modContainer: ModContainer) {
        Config.init(modContainer)

        CCRegistries
    }
}