package nl.requios.effortlessbuilding;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import nl.requios.effortlessbuilding.buildchain.BuildSkills;
import nl.requios.effortlessbuilding.item.ModItems;
import nl.requios.effortlessbuilding.item.AxisUpgradeItem;
import nl.requios.effortlessbuilding.item.ReachUpgradeItem;
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
import nl.requios.effortlessbuilding.network.SyncBuildSkillsS2CPacket;
import nl.requios.effortlessbuilding.config.ServerConfig;
import nl.requios.effortlessbuilding.config.ServerConfigStorage;
import nl.requios.effortlessbuilding.screen.ClientConfigScreen;
import nl.requios.effortlessbuilding.utilities.PlacedBlockTracker;
import nl.requios.effortlessbuilding.utilities.UndoManager;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.function.Supplier;

@Mod(Constants.MOD_ID)
public class EffortlessBuilding {

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Constants.MOD_ID);
    private static final Supplier<Item> REACH_UPGRADE = ITEMS.register("reach_upgrade", ReachUpgradeItem::new);
    private static final Supplier<Item> AXIS_UPGRADE = ITEMS.register("axis_upgrade", AxisUpgradeItem::new);

    public EffortlessBuilding(IEventBus eventBus, ModContainer modContainer) {
        
        // Register items
        ITEMS.register(eventBus);
        eventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                event.accept(REACH_UPGRADE.get());
                event.accept(AXIS_UPGRADE.get());
            }
        });

        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, parent) -> new ClientConfigScreen());

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
            registrar.playToClient(
                    SyncBuildSkillsS2CPacket.TYPE,
                    SyncBuildSkillsS2CPacket.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() ->
                            PacketHandler.handleSyncBuildSkills(payload)));
        });

        // Load + send modifiers, config, and skills on player join
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                ModifierServerStorage.loadPlayer(serverPlayer.server, serverPlayer.getUUID());
                BuildSkills.loadPlayer(serverPlayer.server, serverPlayer.getUUID());
                PacketHandler.sendToClient(serverPlayer, new SyncModifiersS2CPacket(
                        ModifierServerStorage.serializePlayer(serverPlayer.getUUID())));
                PacketHandler.sendToClient(serverPlayer, new SyncServerConfigS2CPacket(
                        ServerConfig.INSTANCE.getBuildModeReach(),
                        ServerConfig.INSTANCE.getMaxBlocksPerAxis()));
                PacketHandler.sendToClient(serverPlayer, new SyncBuildSkillsS2CPacket(
                        BuildSkills.getEffectiveReach(serverPlayer),
                        BuildSkills.getEffectiveAxis(serverPlayer)));
            }
        });

        // Save + clean up on player disconnect
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                ModifierServerStorage.savePlayer(serverPlayer.server, serverPlayer.getUUID());
                ModifierServerStorage.removePlayer(serverPlayer.getUUID());
                BuildSkills.savePlayer(serverPlayer.server, serverPlayer.getUUID());
                BuildSkills.removePlayer(serverPlayer.getUUID());
            }
            UndoManager.clearPlayer(event.getEntity().getUUID());
            PlacedBlockTracker.clearPlayer(event.getEntity().getUUID());
        });

        // Clear all cached data when the server stops (singleplayer world changes)
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            ModifierServerStorage.clearAll();
            BuildSkills.clearAll();
            ServerConfigStorage.clear();
        });

        // Load server config on server start
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            ServerConfigStorage.load(event.getServer());
        });

        // Add upgrade items to dungeon / structure loot tables
        NeoForge.EVENT_BUS.addListener((LootTableLoadEvent event) -> {
            var key = event.getName();
            if (key.equals(BuiltInLootTables.SIMPLE_DUNGEON.location())
                    || key.equals(BuiltInLootTables.ABANDONED_MINESHAFT.location())
                    || key.equals(BuiltInLootTables.DESERT_PYRAMID.location())
                    || key.equals(BuiltInLootTables.JUNGLE_TEMPLE.location())
                    || key.equals(BuiltInLootTables.STRONGHOLD_CORRIDOR.location())
                    || key.equals(BuiltInLootTables.WOODLAND_MANSION.location())
                    || key.equals(BuiltInLootTables.END_CITY_TREASURE.location())) {
                event.getTable().addPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(ModItems.REACH_UPGRADE).setWeight(1))
                        .add(LootItem.lootTableItem(ModItems.AXIS_UPGRADE).setWeight(1))
                        .when(LootItemRandomChanceCondition.randomChance(0.25f))
                        .build());
            }
        });

        eventBus.addListener((net.neoforged.neoforge.registries.RegisterEvent event) -> {
            event.register(Registries.ITEM, helper -> {
                ModItems.REACH_UPGRADE = REACH_UPGRADE.get();
                ModItems.AXIS_UPGRADE = AXIS_UPGRADE.get();
            });
        });

        CommonClass.init();
    }
}
