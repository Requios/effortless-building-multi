package nl.requios.effortlessbuilding;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
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
import nl.requios.effortlessbuilding.utilities.PlacedBlockTracker;
import nl.requios.effortlessbuilding.utilities.UndoManager;

public class EffortlessBuilding implements ModInitializer {

    @Override
    public void onInitialize() {
        Constants.LOG.info("Hello Fabric world!");

        // Register items
        ModItems.REACH_UPGRADE = Registry.register(BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "reach_upgrade"),
                new ReachUpgradeItem());
        ModItems.AXIS_UPGRADE = Registry.register(BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "axis_upgrade"),
                new AxisUpgradeItem());
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> {
                    entries.accept(ModItems.REACH_UPGRADE);
                    entries.accept(ModItems.AXIS_UPGRADE);
                });

        // Add upgrade items to dungeon / structure loot tables
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (source.isBuiltin() && (
                    key.equals(BuiltInLootTables.SIMPLE_DUNGEON)
                    || key.equals(BuiltInLootTables.ABANDONED_MINESHAFT)
                    || key.equals(BuiltInLootTables.DESERT_PYRAMID)
                    || key.equals(BuiltInLootTables.JUNGLE_TEMPLE)
                    || key.equals(BuiltInLootTables.STRONGHOLD_CORRIDOR)
                    || key.equals(BuiltInLootTables.WOODLAND_MANSION)
                    || key.equals(BuiltInLootTables.END_CITY_TREASURE))) {
                tableBuilder.pool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(ModItems.REACH_UPGRADE).setWeight(1))
                        .add(LootItem.lootTableItem(ModItems.AXIS_UPGRADE).setWeight(1))
                        .when(LootItemRandomChanceCondition.randomChance(0.25f))
                        .build());
            }
        });

        // Register C2S packets
        PayloadTypeRegistry.playC2S().register(PlaceBuildModePacket.TYPE, PlaceBuildModePacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(BreakBuildModePacket.TYPE, BreakBuildModePacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UndoPacket.TYPE, UndoPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RedoPacket.TYPE, RedoPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateModifiersC2SPacket.TYPE, UpdateModifiersC2SPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateServerConfigC2SPacket.TYPE, UpdateServerConfigC2SPacket.STREAM_CODEC);

        // Register S2C packets
        PayloadTypeRegistry.playS2C().register(SyncModifiersS2CPacket.TYPE, SyncModifiersS2CPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SyncServerConfigS2CPacket.TYPE, SyncServerConfigS2CPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SyncBuildSkillsS2CPacket.TYPE, SyncBuildSkillsS2CPacket.STREAM_CODEC);

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

        // Load + send modifiers, config, and skills on player join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            ModifierServerStorage.loadPlayer(server, player.getUUID());
            BuildSkills.loadPlayer(server, player.getUUID());
            PacketHandler.sendToClient(player, new SyncModifiersS2CPacket(
                    ModifierServerStorage.serializePlayer(player.getUUID())));
            PacketHandler.sendToClient(player, new SyncServerConfigS2CPacket(
                    ServerConfig.INSTANCE.toJson()));
            PacketHandler.sendToClient(player, new SyncBuildSkillsS2CPacket(
                    BuildSkills.getEffectiveReach(player),
                    BuildSkills.getEffectiveAxis(player)));
        });

        // Save + clean up on player disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            ModifierServerStorage.savePlayer(server, player.getUUID());
            ModifierServerStorage.removePlayer(player.getUUID());
            BuildSkills.savePlayer(server, player.getUUID());
            BuildSkills.removePlayer(player.getUUID());
            UndoManager.clearPlayer(player.getUUID());
            PlacedBlockTracker.clearPlayer(player.getUUID());
        });

        // Clear all cached data when the server stops
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ModifierServerStorage.clearAll();
            BuildSkills.clearAll();
            ServerConfigStorage.clear();
        });

        // Load server config on server start
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerConfigStorage.load(server);
        });

        CommonClass.init();
    }
}
