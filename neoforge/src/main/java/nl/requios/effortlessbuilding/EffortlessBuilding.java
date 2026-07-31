package nl.requios.effortlessbuilding;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import nl.requios.effortlessbuilding.menu.ModMenus;
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

@Mod(Constants.MOD_ID)
public class EffortlessBuilding {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Constants.MOD_ID);
    private static final DeferredItem<Item> RANDOMIZER_TOOL = ITEMS.register(
            "randomizer_tool", () -> new RandomizerToolItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM,
                            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "randomizer_tool")))
                    .stacksTo(1)));
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Constants.MOD_ID);

    static {
        MENUS.register("randomizer", () -> ModMenus.RANDOMIZER);
    }

    public EffortlessBuilding(IEventBus eventBus, ModContainer modContainer) {

        ITEMS.register(eventBus);
        MENUS.register(eventBus);
        eventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(RANDOMIZER_TOOL);
        });

        if (FMLEnvironment.getDist().isClient()) {
            NeoForgeConfigScreenRegistrar.register(modContainer);
        }

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
            registrar.playToServer(
                    UpdateModifiersC2SPacket.TYPE,
                    UpdateModifiersC2SPacket.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() ->
                            PacketHandler.handleUpdateModifiers(payload, (ServerPlayer) context.player())));
            registrar.playToClient(
                    SyncModifiersS2CPacket.TYPE,
                    SyncModifiersS2CPacket.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() ->
                            PacketHandler.handleSyncModifiers(payload)));
            registrar.playToServer(
                    UpdateServerConfigC2SPacket.TYPE,
                    UpdateServerConfigC2SPacket.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() ->
                            PacketHandler.handleUpdateServerConfig(payload, (ServerPlayer) context.player())));
            registrar.playToClient(
                    SyncServerConfigS2CPacket.TYPE,
                    SyncServerConfigS2CPacket.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() ->
                            PacketHandler.handleSyncServerConfig(payload)));
            // AE2 count query — client requests item count from ME network
            registrar.playToServer(
                    QueryAE2CountC2SPacket.TYPE,
                    QueryAE2CountC2SPacket.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() ->
                            PacketHandler.handleQueryAE2Count(payload, (ServerPlayer) context.player())));
            registrar.playToServer(
                    BuildModeHintC2SPacket.TYPE,
                    BuildModeHintC2SPacket.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() ->
                            PacketHandler.handleBuildModeHint((ServerPlayer) context.player())));
            registrar.playToClient(
                    SyncAE2CountS2CPacket.TYPE,
                    SyncAE2CountS2CPacket.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() ->
                            PacketHandler.handleSyncAE2Count(payload)));
        });

        // Load + send modifiers and config on player join
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                ModifierServerStorage.loadPlayer(serverPlayer.level().getServer(), serverPlayer.getUUID());
                PacketHandler.sendToClient(serverPlayer, new SyncModifiersS2CPacket(
                        ModifierServerStorage.serializePlayer(serverPlayer.getUUID())));
                PacketHandler.sendToClient(serverPlayer, new SyncServerConfigS2CPacket(
                        ServerConfig.INSTANCE.toJson()));
                WelcomeMessageStorage.showIfNeeded(serverPlayer);
            }
        });

        // Save + clean up on player disconnect
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                ModifierServerStorage.savePlayer(serverPlayer.level().getServer(), serverPlayer.getUUID());
                ModifierServerStorage.removePlayer(serverPlayer.getUUID());
            }
            UndoManager.clearPlayer(event.getEntity().getUUID());
            PlacedBlockTracker.clearPlayer(event.getEntity().getUUID());
        });

        // Clear all cached data when the server stops (singleplayer world changes)
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            ModifierServerStorage.clearAll();
            ServerConfigStorage.clear();
            WelcomeMessageStorage.clear();
            BuildModeHintStorage.clear();
        });

        // Load server config on server start
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            ServerConfigStorage.load(event.getServer());
            WelcomeMessageStorage.load(event.getServer());
            BuildModeHintStorage.load(event.getServer());
        });

        CommonClass.init();
    }
}
