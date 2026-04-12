package nl.requios.effortlessbuilding;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import nl.requios.effortlessbuilding.block.ModBlocks;
import nl.requios.effortlessbuilding.item.ModItems;
import nl.requios.effortlessbuilding.network.BreakBuildModePacket;
import nl.requios.effortlessbuilding.network.PacketHandler;
import nl.requios.effortlessbuilding.network.PlaceBuildModePacket;
import nl.requios.effortlessbuilding.network.UndoPacket;
import nl.requios.effortlessbuilding.network.RedoPacket;
import nl.requios.effortlessbuilding.utilities.UndoManager;

public class EffortlessBuilding implements ModInitializer {

    @Override
    public void onInitialize() {
        Constants.LOG.info("Hello Fabric world!");

        Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "test_block"), ModBlocks.TEST_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "test_block"), new BlockItem(ModBlocks.TEST_BLOCK, new Item.Properties()));
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "test_item"), ModItems.TEST_ITEM);

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.BUILDING_BLOCKS).register(entries -> entries.accept(ModBlocks.TEST_BLOCK));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.accept(ModItems.TEST_ITEM));

        // Register C2S packets
        PayloadTypeRegistry.playC2S().register(PlaceBuildModePacket.TYPE, PlaceBuildModePacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(BreakBuildModePacket.TYPE, BreakBuildModePacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UndoPacket.TYPE, UndoPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RedoPacket.TYPE, RedoPacket.STREAM_CODEC);

        // Register server-side handlers
        ServerPlayNetworking.registerGlobalReceiver(PlaceBuildModePacket.TYPE, (payload, context) ->
                context.server().execute(() -> PacketHandler.handlePlaceBuildMode(payload, context.player())));
        ServerPlayNetworking.registerGlobalReceiver(BreakBuildModePacket.TYPE, (payload, context) ->
                context.server().execute(() -> PacketHandler.handleBreakBuildMode(payload, context.player())));
        ServerPlayNetworking.registerGlobalReceiver(UndoPacket.TYPE, (payload, context) ->
                context.server().execute(() -> PacketHandler.handleUndo(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(RedoPacket.TYPE, (payload, context) ->
                context.server().execute(() -> PacketHandler.handleRedo(context.player())));

        // Clean up undo stacks on player disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                UndoManager.clearPlayer(handler.getPlayer().getUUID()));

        CommonClass.init();
    }
}
