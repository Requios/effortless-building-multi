package nl.requios.effortlessbuilding;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import nl.requios.effortlessbuilding.menu.ModMenus;
import net.minecraft.server.level.ServerPlayer;
import nl.requios.effortlessbuilding.modifier.ModifierServerStorage;
import nl.requios.effortlessbuilding.network.BreakBuildModePacket;
import nl.requios.effortlessbuilding.network.PacketHandler;
import nl.requios.effortlessbuilding.network.PlaceBuildModePacket;
import nl.requios.effortlessbuilding.network.UndoPacket;
import nl.requios.effortlessbuilding.network.RedoPacket;
import nl.requios.effortlessbuilding.network.UpdateModifiersC2SPacket;
import nl.requios.effortlessbuilding.network.SyncModifiersS2CPacket;
import nl.requios.effortlessbuilding.network.UpdateServerConfigC2SPacket;
import nl.requios.effortlessbuilding.network.SyncServerConfigS2CPacket;
import nl.requios.effortlessbuilding.network.QueryAE2CountC2SPacket;
import nl.requios.effortlessbuilding.network.SyncAE2CountS2CPacket;
import nl.requios.effortlessbuilding.network.BuildModeHintC2SPacket;
import nl.requios.effortlessbuilding.config.ServerConfig;
import nl.requios.effortlessbuilding.config.ServerConfigStorage;
import nl.requios.effortlessbuilding.config.WelcomeMessageStorage;
import nl.requios.effortlessbuilding.config.BuildModeHintStorage;
import nl.requios.effortlessbuilding.utilities.PlacedBlockTracker;
import nl.requios.effortlessbuilding.utilities.UndoManager;
import nl.requios.effortlessbuilding.item.RandomizerToolItem;

public class EffortlessBuilding implements ModInitializer {

    @Override
    public void onInitialize() {
        Constants.LOG.info("Hello Fabric world!");

        Identifier randomizerToolId = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "randomizer_tool");
        Item randomizerTool = Registry.register(BuiltInRegistries.ITEM, randomizerToolId,
                new RandomizerToolItem(new Item.Properties()
                        .setId(ResourceKey.create(Registries.ITEM, randomizerToolId))
                        .stacksTo(1)));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> entries.accept(randomizerTool));
        Registry.register(BuiltInRegistries.MENU,
                Identifier.fromNamespaceAndPath(Constants.MOD_ID, "randomizer"), ModMenus.RANDOMIZER);

        // Register C2S packets
        PayloadTypeRegistry.serverboundPlay().register(PlaceBuildModePacket.TYPE, PlaceBuildModePacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BreakBuildModePacket.TYPE, BreakBuildModePacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UndoPacket.TYPE, UndoPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RedoPacket.TYPE, RedoPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpdateModifiersC2SPacket.TYPE, UpdateModifiersC2SPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpdateServerConfigC2SPacket.TYPE, UpdateServerConfigC2SPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(QueryAE2CountC2SPacket.TYPE, QueryAE2CountC2SPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BuildModeHintC2SPacket.TYPE, BuildModeHintC2SPacket.STREAM_CODEC);

        // Register S2C packets
        PayloadTypeRegistry.clientboundPlay().register(SyncModifiersS2CPacket.TYPE, SyncModifiersS2CPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncServerConfigS2CPacket.TYPE, SyncServerConfigS2CPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncAE2CountS2CPacket.TYPE, SyncAE2CountS2CPacket.STREAM_CODEC);

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
        ServerPlayNetworking.registerGlobalReceiver(UpdateServerConfigC2SPacket.TYPE, (payload, context) ->
                context.server().execute(() -> PacketHandler.handleUpdateServerConfig(payload, context.player())));
        ServerPlayNetworking.registerGlobalReceiver(QueryAE2CountC2SPacket.TYPE, (payload, context) ->
                context.server().execute(() -> PacketHandler.handleQueryAE2Count(payload, context.player())));
        ServerPlayNetworking.registerGlobalReceiver(BuildModeHintC2SPacket.TYPE, (payload, context) ->
                context.server().execute(() -> PacketHandler.handleBuildModeHint(context.player())));

        // Load + send modifiers and config on player join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            ModifierServerStorage.loadPlayer(server, player.getUUID());
            PacketHandler.sendToClient(player, new SyncModifiersS2CPacket(
                    ModifierServerStorage.serializePlayer(player.getUUID())));
            PacketHandler.sendToClient(player, new SyncServerConfigS2CPacket(
                    ServerConfig.INSTANCE.toJson()));
            WelcomeMessageStorage.showIfNeeded(player);
        });

        // Save + clean up on player disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            ModifierServerStorage.savePlayer(server, player.getUUID());
            ModifierServerStorage.removePlayer(player.getUUID());
            UndoManager.clearPlayer(player.getUUID());
            PlacedBlockTracker.clearPlayer(player.getUUID());
        });

        // Clear all cached data when the server stops
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ModifierServerStorage.clearAll();
            ServerConfigStorage.clear();
            WelcomeMessageStorage.clear();
            BuildModeHintStorage.clear();
        });

        // Load server config on server start
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerConfigStorage.load(server);
            WelcomeMessageStorage.load(server);
            BuildModeHintStorage.load(server);
        });

        CommonClass.init();
    }
}
