package nl.requios.effortlessbuilding;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import nl.requios.effortlessbuilding.block.ModBlocks;
import nl.requios.effortlessbuilding.item.ModItems;
import nl.requios.effortlessbuilding.modifier.ModifierServerStorage;
import nl.requios.effortlessbuilding.network.BreakBuildModePacket;
import nl.requios.effortlessbuilding.network.PacketHandler;
import nl.requios.effortlessbuilding.network.PlaceBuildModePacket;
import nl.requios.effortlessbuilding.network.UndoPacket;
import nl.requios.effortlessbuilding.network.RedoPacket;
import nl.requios.effortlessbuilding.network.UpdateModifiersC2SPacket;
import nl.requios.effortlessbuilding.network.SyncModifiersS2CPacket;
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
        PayloadTypeRegistry.playC2S().register(UpdateModifiersC2SPacket.TYPE, UpdateModifiersC2SPacket.STREAM_CODEC);

        // Register S2C packets
        PayloadTypeRegistry.playS2C().register(SyncModifiersS2CPacket.TYPE, SyncModifiersS2CPacket.STREAM_CODEC);

        // Register server-side handlers
        ServerPlayNetworking.registerGlobalReceiver(PlaceBuildModePacket.TYPE, (payload, context) ->
                context.server().execute(() -> PacketHandler.handlePlaceBuildMode(payload, context.player())));
        ServerPlayNetworking.registerGlobalReceiver(BreakBuildModePacket.TYPE, (payload, context) ->
                context.server().execute(() -> PacketHandler.handleBreakBuildMode(payload, context.player())));
        ServerPlayNetworking.registerGlobalReceiver(UndoPacket.TYPE, (payload, context) ->
                context.server().execute(() -> PacketHandler.handleUndo(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(RedoPacket.TYPE, (payload, context) ->
                context.server().execute(() -> PacketHandler.handleRedo(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(UpdateModifiersC2SPacket.TYPE, (payload, context) ->
                context.server().execute(() -> PacketHandler.handleUpdateModifiers(payload, context.player())));

        // Load + send modifiers on player join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            ModifierServerStorage.loadPlayer(server, player.getUUID());
            PacketHandler.sendToClient(player, new SyncModifiersS2CPacket(
                    ModifierServerStorage.serializePlayer(player.getUUID())));
        });

        // Save + clean up on player disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            ModifierServerStorage.savePlayer(server, player.getUUID());
            ModifierServerStorage.removePlayer(player.getUUID());
            UndoManager.clearPlayer(player.getUUID());
        });

        // Clear all cached data when the server stops
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ModifierServerStorage.clearAll();
        });

        CommonClass.init();
    }
}
