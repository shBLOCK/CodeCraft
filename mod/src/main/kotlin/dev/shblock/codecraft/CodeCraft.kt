package dev.shblock.codecraft

import com.mojang.logging.LogUtils
import dev.shblock.codecraft.core.cmd.CCCmds
import dev.shblock.codecraft.core.msg.CCMsgs
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTab
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.registries.DeferredRegister
import org.slf4j.Logger

@Mod(CodeCraft.MODID)
class CodeCraft {
    companion object {
        const val MODID = "codecraft"

        fun path(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MODID, path)

        val LOGGER: Logger = LogUtils.getLogger()

        val BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(MODID)
        val ITEMS: DeferredRegister.Items = DeferredRegister.createItems(MODID)
        val CREATIVE_MODE_TABS: DeferredRegister<CreativeModeTab> =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID)

//        // Creates a new Block with the id "examplemod:example_block", combining the namespace and path
//        val EXAMPLE_BLOCK: DeferredBlock<Block> =
//            BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE))
//
//        // Creates a new BlockItem with the id "examplemod:example_block", combining the namespace and path
//        val EXAMPLE_BLOCK_ITEM: DeferredItem<BlockItem> = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK)
//
//        // Creates a new food item with the id "examplemod:example_id", nutrition 1 and saturation 2
//        val EXAMPLE_ITEM: DeferredItem<Item> = ITEMS.registerSimpleItem(
//            "example_item", Item.Properties().food(
//                FoodProperties.Builder()
//                    .alwaysEdible().nutrition(1).saturationModifier(2f).build()
//            )
//        )
//
//        // Creates a creative tab with the id "examplemod:example_tab" for the example item, that is placed after the combat tab
//        val EXAMPLE_TAB: DeferredHolder<CreativeModeTab, CreativeModeTab> = CREATIVE_MODE_TABS.register("example_tab",
//            Supplier {
//                CreativeModeTab.builder()
//                    .title(Component.translatable("itemGroup.examplemod")) //The language key for the title of your CreativeModeTab
//                    .withTabsBefore(CreativeModeTabs.COMBAT)
//                    .icon { EXAMPLE_ITEM.get().defaultInstance }
//                    .displayItems { parameters: ItemDisplayParameters?, output: CreativeModeTab.Output ->
//                        output.accept(EXAMPLE_ITEM.get()) // Add the example item to the tab. For your own tabs, this method is preferred over the event
//                    }.build()
//            })
//
//        // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
//        @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
//        object ClientModEvents {
//            @SubscribeEvent
//            fun onClientSetup(event: FMLClientSetupEvent?) {
//                // Some client setup code
//                LOGGER.info("HELLO FROM CLIENT SETUP")
//                LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().user.name)
//            }
//        }
    }

    @Suppress("ConvertSecondaryConstructorToPrimary", "unused")
    constructor(modEventBus: IEventBus, modContainer: ModContainer) {
        modEventBus.addListener(::commonSetup)

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC)

        CCCmds.CMDS.register(modEventBus)
        CCMsgs.MSGS.register(modEventBus)

        BLOCKS.register(modEventBus)
        ITEMS.register(modEventBus)
        CREATIVE_MODE_TABS.register(modEventBus)

//        // Register ourselves for server and other game events we are interested in.
//        // Note that this is necessary if and only if we want *this* class (ExampleMod) to respond directly to events.
//        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
//        NeoForge.EVENT_BUS.register(this)

//        // Register the item to a creative tab
//        modEventBus.addListener(::addCreative)


    }

    private fun commonSetup(event: FMLCommonSetupEvent) {
//        // Some common setup code
//        LOGGER.info("HELLO FROM COMMON SETUP")
//
//        if (Config.logDirtBlock) LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT))
//
//        LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber)
//
//        Config.items.forEach(Consumer { item: Item ->
//            LOGGER.info(
//                "ITEM >> {}",
//                item.toString()
//            )
//        })
    }

//    // Add the example block item to the building blocks tab
//    private fun addCreative(event: BuildCreativeModeTabContentsEvent) {
//        if (event.tabKey === CreativeModeTabs.BUILDING_BLOCKS) event.accept(EXAMPLE_BLOCK_ITEM)
//    }

}