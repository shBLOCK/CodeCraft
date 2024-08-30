package dev.shblock.codecraft

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.event.config.ModConfigEvent
import net.neoforged.neoforge.common.ModConfigSpec
import kotlin.reflect.KProperty


@Suppress("HasPlatformType")
@EventBusSubscriber(modid = CodeCraft.MODID, bus = EventBusSubscriber.Bus.MOD)
internal object Config {
    private val BUILDER: ModConfigSpec.Builder = ModConfigSpec.Builder()

    object Server {
        val shutdownGracePeriod by BUILDER
            .comment("The maximum amount of time (ms) to wait until a server stops gracefully")
            .worldRestart()
            .defineInRange("server.shutdownGracePeriod", 1000, 0, Int.MAX_VALUE, Int::class.java)
    }

    init {
        // The object names that seems to do nothing triggers the lazy loading of sub-config objects.
        // Kinda stupid, but actually useful for categorization.
        BUILDER.path("server") { Server }
    }


    private val SPEC: ModConfigSpec = BUILDER.build()

    internal fun init(modContainer: ModContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, SPEC)
//        modContainer.registerExtensionPoint // TODO: cloth config configuration screen
    }

    @SubscribeEvent
    fun onLoad(event: ModConfigEvent) {
        // Nothing to do for now
    }
}

private fun ModConfigSpec.Builder.path(path: String, block: () -> Any) {
    push(path)
    block()
    pop()
}

private operator fun <T : Any> ModConfigSpec.ConfigValue<T>.getValue(
    thisRef: Any?,
    property: KProperty<*>
): T = get()
