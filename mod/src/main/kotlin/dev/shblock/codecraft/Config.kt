package dev.shblock.codecraft

import net.neoforged.neoforge.common.ModConfigSpec

//@EventBusSubscriber(modid = CodeCraft.MODID, bus = EventBusSubscriber.Bus.MOD)
internal object Config {
    private val BUILDER: ModConfigSpec.Builder = ModConfigSpec.Builder()

//    private val LOG_DIRT_BLOCK: ModConfigSpec.BooleanValue =
//        BUILDER.comment("Whether to log the dirt block on common setup").define("logDirtBlock", true)
//
//    private val MAGIC_NUMBER: ModConfigSpec.IntValue =
//        BUILDER.comment("A magic number").defineInRange("magicNumber", 42, 0, Int.MAX_VALUE)
//
//    val MAGIC_NUMBER_INTRODUCTION: ModConfigSpec.ConfigValue<String> =
//        BUILDER.comment("What you want the introduction message to be for the magic number")
//            .define("magicNumberIntroduction", "The magic number is... ")
//
//    private val ITEM_STRINGS: ModConfigSpec.ConfigValue<List<String>> =
//        BUILDER.comment("A list of items to log on common setup.").defineListAllowEmpty(
//            "items",
//            listOf("minecraft:iron_ingot"),
//            Config::validateItemName
//        )

    object Server {
        init {
            BUILDER.push("server")
        }

        private val SHUTDOWN_GRACE_PERIOD = BUILDER
            .comment("The maximum amount of time (ms) to wait until a server stops gracefully")
            .defineInRange("server.shutdownGracePeriod", 3000, 0, Int.MAX_VALUE, Int::class.java)
        val shutdownGracePeriod: Int
            get() = SHUTDOWN_GRACE_PERIOD.get()

        init {
            BUILDER.pop()
        }
    }


    internal val SPEC: ModConfigSpec = BUILDER.build()

//    var logDirtBlock: Boolean = false
//    var magicNumber: Int = 0
//    lateinit var magicNumberIntroduction: String
//    lateinit var items: Set<Item>
//
//    private fun validateItemName(obj: Any): Boolean {
//        return obj is String && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(obj))
//    }

//    @SubscribeEvent
//    fun onLoad(event: ModConfigEvent) {
//        logDirtBlock = LOG_DIRT_BLOCK.get()
//        magicNumber = MAGIC_NUMBER.get()
//        magicNumberIntroduction = MAGIC_NUMBER_INTRODUCTION.get()
//
//        items = ITEM_STRINGS.get().stream().map { itemName: String? ->
//            BuiltInRegistries.ITEM[ResourceLocation.parse(
//                itemName
//            )]
//        }.collect(Collectors.toSet())
//    }
}