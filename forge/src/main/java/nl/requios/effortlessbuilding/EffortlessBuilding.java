package nl.requios.effortlessbuilding;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.bus.api.IEventBus;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;
import nl.requios.effortlessbuilding.block.ModBlocks;

@Mod(Constants.MOD_ID)
public class EffortlessBuilding {

    public EffortlessBuilding(IEventBus modEventBus) {
        Constants.LOG.info("Hello Forge world!");

        modEventBus.addListener((RegisterEvent event) -> {
            event.register(Registries.BLOCK, helper ->
                helper.register(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "test_block"), ModBlocks.TEST_BLOCK));
            event.register(Registries.ITEM, helper ->
                helper.register(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "test_block"), new BlockItem(ModBlocks.TEST_BLOCK, new Item.Properties())));
        });

        modEventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
                event.accept(ModBlocks.TEST_BLOCK);
            }
        });

        CommonClass.init();
    }
}