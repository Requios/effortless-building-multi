package nl.requios.effortlessbuilding;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import nl.requios.effortlessbuilding.block.ModBlocks;
import nl.requios.effortlessbuilding.item.ModItems;
import nl.requios.effortlessbuilding.network.BreakBuildModePacket;
import nl.requios.effortlessbuilding.network.PacketHandler;
import nl.requios.effortlessbuilding.network.PlaceBuildModePacket;
import nl.requios.effortlessbuilding.network.UndoPacket;
import nl.requios.effortlessbuilding.network.RedoPacket;
import nl.requios.effortlessbuilding.utilities.UndoManager;

@Mod(Constants.MOD_ID)
public class EffortlessBuilding {

    public EffortlessBuilding(IEventBus eventBus) {
        Constants.LOG.info("Hello NeoForge world!");

        eventBus.addListener((RegisterEvent event) -> {
            event.register(Registries.BLOCK, helper ->
                helper.register(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "test_block"), ModBlocks.TEST_BLOCK));
            event.register(Registries.ITEM, helper -> {
                helper.register(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "test_block"), new BlockItem(ModBlocks.TEST_BLOCK, new Item.Properties()));
                helper.register(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "test_item"), ModItems.TEST_ITEM);
            });
        });

        eventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
                event.accept(ModBlocks.TEST_BLOCK);
            } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                event.accept(ModItems.TEST_ITEM);
            }
        });

        eventBus.addListener((RegisterPayloadHandlersEvent event) -> {
            var registrar = event.registrar(Constants.MOD_ID);
            registrar.playToServer(
                    PlaceBuildModePacket.TYPE,
                    PlaceBuildModePacket.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() ->
                            PacketHandler.handlePlaceBuildMode(payload, (ServerPlayer) context.player())));
            registrar.playToServer(
                    BreakBuildModePacket.TYPE,
                    BreakBuildModePacket.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() ->
                            PacketHandler.handleBreakBuildMode(payload, (ServerPlayer) context.player())));
            registrar.playToServer(
                    UndoPacket.TYPE,
                    UndoPacket.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() ->
                            PacketHandler.handleUndo((ServerPlayer) context.player())));
            registrar.playToServer(
                    RedoPacket.TYPE,
                    RedoPacket.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() ->
                            PacketHandler.handleRedo((ServerPlayer) context.player())));
        });

        // Clean up undo stacks on player disconnect
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            UndoManager.clearPlayer(event.getEntity().getUUID());
        });

        CommonClass.init();
    }
}
