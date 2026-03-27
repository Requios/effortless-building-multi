package nl.requios.effortlessbuilding;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import nl.requios.effortlessbuilding.block.ModBlocks;

@Mod(Constants.MOD_ID)
public class EffortlessBuilding {

    public EffortlessBuilding(IEventBus eventBus) {
        Constants.LOG.info("Hello NeoForge world!");

        eventBus.addListener((RegisterEvent event) -> {
            event.register(Registries.BLOCK, helper ->
                helper.register(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "test_block"), ModBlocks.TEST_BLOCK));
            event.register(Registries.ITEM, helper ->
                helper.register(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "test_block"), new BlockItem(ModBlocks.TEST_BLOCK, new Item.Properties())));
        });

        eventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
                event.accept(ModBlocks.TEST_BLOCK);
            }
        });

        CommonClass.init();
    }
}