package nl.requios.effortlessbuilding.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import nl.requios.effortlessbuilding.mixin.BucketItemAccessor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import nl.requios.effortlessbuilding.Constants;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;

import nl.requios.effortlessbuilding.buildmode.BuildSettings;
import nl.requios.effortlessbuilding.config.ServerConfig;
import nl.requios.effortlessbuilding.config.ServerConfigStorage;
import nl.requios.effortlessbuilding.config.BuildModeHintStorage;
import nl.requios.effortlessbuilding.modifier.IModifier;
import nl.requios.effortlessbuilding.modifier.ModifierSerializer;
import nl.requios.effortlessbuilding.modifier.ModifierServerStorage;
import nl.requios.effortlessbuilding.modifier.ModifierSystem;
import nl.requios.effortlessbuilding.platform.Services;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import nl.requios.effortlessbuilding.utilities.InventoryHelper;
import nl.requios.effortlessbuilding.compat.ae2.AE2Integration;
import nl.requios.effortlessbuilding.utilities.PlacedBlockTracker;
import nl.requios.effortlessbuilding.utilities.UndoManager;
import nl.requios.effortlessbuilding.item.RandomizerToolItem;

import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PacketHandler {

    public static void sendToServer(PlaceBuildModePacket packet) {
        Services.NETWORK.sendToServer(packet);
    }

    public static void sendToServer(BreakBuildModePacket packet) {
        Services.NETWORK.sendToServer(packet);
    }

    public static void sendToServer(UndoPacket packet) {
        Services.NETWORK.sendToServer(packet);
    }

    public static void sendToServer(RedoPacket packet) {
        Services.NETWORK.sendToServer(packet);
    }

    public static void sendToServer(UpdateModifiersC2SPacket packet) {
        Services.NETWORK.sendToServer(packet);
    }

    public static void sendToClient(ServerPlayer player, SyncModifiersS2CPacket packet) {
        Services.NETWORK.sendToClient(player, packet);
    }

    public static void sendToServer(UpdateServerConfigC2SPacket packet) {
        Services.NETWORK.sendToServer(packet);
    }

    public static void sendToClient(ServerPlayer player, SyncServerConfigS2CPacket packet) {
        Services.NETWORK.sendToClient(player, packet);
    }

    public static void sendToServer(QueryAE2CountC2SPacket packet) {
        Services.NETWORK.sendToServer(packet);
    }

    public static void sendToServer(BuildModeHintC2SPacket packet) {
        Services.NETWORK.sendToServer(packet);
    }

    public static void sendToClient(ServerPlayer player, SyncAE2CountS2CPacket packet) {
        Services.NETWORK.sendToClient(player, packet);
    }

    /**
     * Called on the server when a {@link QueryAE2CountC2SPacket} is received.
     * Queries the AE2 network and sends the count back to the client.
     */
    public static void handleQueryAE2Count(QueryAE2CountC2SPacket packet, ServerPlayer player) {
        int count = AE2Integration.countOnNetwork(player, packet.item());
        sendToClient(player, new SyncAE2CountS2CPacket(packet.item(), count));
    }

    /**
     * Called on the client when a {@link SyncAE2CountS2CPacket} is received.
     * Stores the count for the HUD preview.
     */
    public static void handleSyncAE2Count(SyncAE2CountS2CPacket packet) {
        AE2Integration.setCachedCount(packet.item(), packet.count());
    }

    /** Called after the client selects a non-disabled build mode. */
    public static void handleBuildModeHint(ServerPlayer player) {
        BuildModeHintStorage.showIfNeeded(player);
    }

    /**
     * Called on the server when a {@link PlaceBuildModePacket} is received.
     */
    public static void handlePlaceBuildMode(PlaceBuildModePacket packet, ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        // Run the full server pipeline: BuildMode → Modifiers → Constraints
        BlockSet blockSet = BuildPipeline.SERVER.runServerPipeline(
                packet.buildMode(), packet.firstPos(), packet.secondPos(), packet.thirdPos(),
                player, BuildPipeline.BuildState.PLACING,
                packet.fill(), packet.cubeFill(), packet.raisedEdge(), packet.circleStart(), 
                packet.protectTileEntities());

        if (blockSet == null) {
            Constants.LOG.warn("[EffortlessBuilding] Received PlaceBuildModePacket but mode {} returned no blocks", packet.buildMode());
            return;
        }

        // Sort by distance to player so closest blocks are placed first when inventory is limited
        blockSet.sortByDistance();

        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack offHand = player.getItemInHand(InteractionHand.OFF_HAND);
        boolean creative = player.isCreative();

        BuildSettings.ReplaceMode replaceMode = packet.replaceMode();

        Map<BlockPos, UndoManager.BlockChange> undoChanges = new LinkedHashMap<>();

        int placed = 0;
        if (held.getItem() instanceof RandomizerToolItem) {
            Map<Item, Integer> required = new LinkedHashMap<>();
            for (var mapEntry : blockSet.validEntries()) {
                if (!BuildSettings.canPlaceAt(level, mapEntry.getKey(), replaceMode, offHand)) continue;
                Item item = mapEntry.getValue().item;
                if (item instanceof BlockItem) required.merge(item, 1, Integer::sum);
            }

            Map<Item, Integer> available = new HashMap<>();
            for (var requirement : required.entrySet()) {
                if (creative) {
                    available.put(requirement.getKey(), Integer.MAX_VALUE);
                } else {
                    int inventoryCount = InventoryHelper.findTotalItemsInInventory(player, requirement.getKey());
                    int networkNeeded = Math.max(0, requirement.getValue() - inventoryCount);
                    int fromNetwork = InventoryHelper.supplementFromNetwork(
                            player, requirement.getKey(), networkNeeded);
                    available.put(requirement.getKey(), inventoryCount + fromNetwork);
                }
            }

            Map<Item, Integer> used = new HashMap<>();
            double yFrac = packet.hitLocation().y - Math.floor(packet.hitLocation().y);
            for (var mapEntry : blockSet.validEntries()) {
                BlockPos pos = mapEntry.getKey();
                BlockEntry entry = mapEntry.getValue();
                if (!(entry.item instanceof BlockItem blockItem)) continue;
                if (!creative && used.getOrDefault(entry.item, 0) >= available.getOrDefault(entry.item, 0)) continue;
                if (!BuildSettings.canPlaceAt(level, pos, replaceMode, offHand)) continue;

                BlockState oldState = level.getBlockState(pos);
                if (!creative && !oldState.canBeReplaced()) {
                    ItemStack toolForDrops = ServerConfig.INSTANCE.survivalRequireTools
                            ? InventoryHelper.findCorrectTool(player, oldState)
                            : player.getMainHandItem();
                    var drops = Block.getDrops(oldState, level, pos, level.getBlockEntity(pos), player, toolForDrops);
                    for (ItemStack drop : drops) {
                        InventoryHelper.giveOrDropItems(player, drop.getItem(), drop.getCount());
                    }
                    if (ServerConfig.INSTANCE.survivalUseDurability) {
                        InventoryHelper.damageCorrectTool(player, oldState);
                    }
                }

                ItemStack placementStack = new ItemStack(entry.item);
                Vec3 localHit = new Vec3(packet.hitLocation().x, pos.getY() + yFrac, packet.hitLocation().z);
                BlockHitResult serverHit = new BlockHitResult(localHit, packet.hitFace(), pos, false);
                BlockPlaceContext ctx = new OpenBlockPlaceContext(
                        level, player, InteractionHand.MAIN_HAND, placementStack, serverHit);
                BlockState state = blockItem.getBlock().getStateForPlacement(ctx);
                if (state == null) state = blockItem.getBlock().defaultBlockState();
                state = entry.applyTransforms(state);
                level.setBlock(pos, state, 3);
                undoChanges.put(pos.immutable(), new UndoManager.BlockChange(oldState, state));
                used.merge(entry.item, 1, Integer::sum);
                placed++;
            }

            if (!creative) {
                for (var usage : used.entrySet()) {
                    InventoryHelper.consumeItems(player, usage.getKey(), usage.getValue());
                }
            }
        } else if (held.getItem() instanceof BlockItem blockItem) {
            Item heldItem = held.getItem();
            // Components belong to this exact stack. Do not let a filled or otherwise
            // customised block borrow plain copies from the inventory/AE2 network,
            // because that would duplicate its data onto those copies.
            boolean hasStackData = !held.getComponentsPatch().isEmpty();

            // Determine how many blocks we can afford BEFORE placing any
            int available;
            if (creative) {
                available = Integer.MAX_VALUE;
            } else if (hasStackData) {
                available = held.getCount();
            } else {
                int inventoryCount = InventoryHelper.findTotalItemsInInventory(player, heldItem);
                int validCount = blockSet.validEntries().size();

                // Pre-extract from AE2 what exceeds inventory (digital — no ItemStack created)
                int neededFromNetwork = Math.max(0, validCount - inventoryCount);
                int ae2Extracted = 0;
                if (neededFromNetwork > 0) {
                    ae2Extracted = InventoryHelper.supplementFromNetwork(player, heldItem, neededFromNetwork);
                }
                available = inventoryCount + ae2Extracted;
            }

            double yFrac = packet.hitLocation().y - Math.floor(packet.hitLocation().y);
            for (var mapEntry : blockSet.validEntries()) {
                BlockPos pos = mapEntry.getKey();
                if (!creative && placed >= available) break;

                if (BuildSettings.canPlaceAt(level, pos, replaceMode, offHand)) {
                    BlockState oldState = level.getBlockState(pos);

                    // Survival: give drops and damage tools for displaced non-replaceable blocks
                    if (!creative && !oldState.canBeReplaced()) {
                        ItemStack toolForDrops = ServerConfig.INSTANCE.survivalRequireTools
                                ? InventoryHelper.findCorrectTool(player, oldState)
                                : player.getMainHandItem();
                        var drops = Block.getDrops(oldState, level, pos, level.getBlockEntity(pos),
                                player, toolForDrops);
                        for (ItemStack drop : drops) {
                            InventoryHelper.giveOrDropItems(player, drop.getItem(), drop.getCount());
                        }
                        if (ServerConfig.INSTANCE.survivalUseDurability) {
                            InventoryHelper.damageCorrectTool(player, oldState);
                        }
                    }

                    Vec3 localHit = new Vec3(packet.hitLocation().x, pos.getY() + yFrac, packet.hitLocation().z);
                    BlockHitResult serverHit = new BlockHitResult(localHit, packet.hitFace(), pos, false);
                    BlockPlaceContext ctx = new OpenBlockPlaceContext(level, player, InteractionHand.MAIN_HAND, held, serverHit);
                    BlockState state = blockItem.getBlock().getStateForPlacement(ctx);
                    if (state == null) state = blockItem.getBlock().defaultBlockState();
                    BlockEntry entry = blockSet.get(pos);
                    if (entry != null) {
                        state = entry.applyTransforms(state);
                    }
                    level.setBlock(pos, state, 3);
                    transferBlockItemData(level, player, pos, held);
                    undoChanges.put(pos.immutable(), new UndoManager.BlockChange(oldState, state));
                    placed++;
                }
            }

            if (!creative && placed > 0) {
                if (hasStackData) {
                    held.shrink(placed);
                } else {
                    // Consume from player inventory (AE2 was already debited before placement)
                    InventoryHelper.consumeItems(player, heldItem, placed);
                    // Restock held stack from AE2 network (e.g. top-up from 4 → 64)
                    InventoryHelper.restockFromNetwork(player);
                }
            }
        } else if (held.getItem() instanceof BucketItem bucketItem) {
            var fluid = ((BucketItemAccessor) bucketItem).effortlessbuilding$getFluid();
            if (!fluid.isSame(Fluids.EMPTY)) {
                BlockState fluidState = fluid.defaultFluidState().createLegacyBlock();
                int maxPlace = creative ? Integer.MAX_VALUE : 1;
                for (var mapEntry : blockSet.validEntries()) {
                    BlockPos pos = mapEntry.getKey();
                    if (placed >= maxPlace) break;
                    if (BuildSettings.canPlaceAt(level, pos, replaceMode, offHand)) {
                        BlockState oldState = level.getBlockState(pos);

                        // Survival: give drops and damage tools for displaced non-replaceable blocks
                        if (!creative && !oldState.canBeReplaced()) {
                            ItemStack toolForDrops = ServerConfig.INSTANCE.survivalRequireTools
                                    ? InventoryHelper.findCorrectTool(player, oldState)
                                    : player.getMainHandItem();
                            var drops = Block.getDrops(oldState, level, pos, level.getBlockEntity(pos),
                                    player, toolForDrops);
                            for (ItemStack drop : drops) {
                                InventoryHelper.giveOrDropItems(player, drop.getItem(), drop.getCount());
                            }
                            if (ServerConfig.INSTANCE.survivalUseDurability) {
                                InventoryHelper.damageCorrectTool(player, oldState);
                            }
                        }

                        level.setBlock(pos, fluidState, 3);
                        undoChanges.put(pos.immutable(), new UndoManager.BlockChange(oldState, fluidState));
                        placed++;
                    }
                }
                if (!creative && placed > 0) {
                    player.setItemInHand(InteractionHand.MAIN_HAND,
                            new ItemStack(net.minecraft.world.item.Items.BUCKET));
                }
            }
        } else if (held.has(DataComponents.TOOL)) {
            // Tool interactions: axe strips logs, shovel makes paths, hoe tills dirt, etc.
            // Calls useOn for each position — works for vanilla and modded tools.
            net.minecraft.world.level.Level worldLevel = level;
            for (var mapEntry : blockSet.validEntries()) {
                BlockPos pos = mapEntry.getKey();
                BlockState oldState = level.getBlockState(pos);

                Vec3 localHit = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                BlockHitResult serverHit = new BlockHitResult(localHit, packet.hitFace(), pos, false);
                UseOnContext useCtx = new OpenUseOnContext(worldLevel, player, InteractionHand.MAIN_HAND, held, serverHit);
                var result = held.getItem().useOn(useCtx);
                if (result.consumesAction()) {
                    BlockState newState = level.getBlockState(pos);
                    if (!oldState.equals(newState)) {
                        undoChanges.put(pos.immutable(), new UndoManager.BlockChange(oldState, newState));
                        placed++;
                    }
                }
                // Stop if tool breaks
                if (held.isEmpty()) break;
            }
        } else {
            return;
        }

        if (!undoChanges.isEmpty()) {
            UndoManager.recordOperation(player, level.dimension(), undoChanges);
            PlacedBlockTracker.trackAll(player.getUUID(), level.dimension(), undoChanges.keySet());
        }
    }

    /**
     * Mirrors the block-entity part of {@link BlockItem#place(BlockPlaceContext)}.
     *
     * <p>The build pipeline intentionally sets the block directly so a modifier can
     * control the exact target position and transformed state. Direct placement skips
     * vanilla's item-to-block-entity transfer, however, which would otherwise erase
     * contents such as a filled shulker box or data stored by another mod.</p>
     */
    private static void transferBlockItemData(ServerLevel level, ServerPlayer player,
                                              BlockPos pos, ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return;

        BlockState placedState = level.getBlockState(pos);
        if (!placedState.is(blockItem.getBlock())) return;

        BlockItem.updateCustomBlockEntityTag(level, player, pos, stack);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            blockEntity.applyComponentsFromItemStack(stack);
            blockEntity.setChanged();
        }
        placedState.getBlock().setPlacedBy(level, pos, placedState, player, stack);
    }

    /**
     * Called on the server when a {@link BreakBuildModePacket} is received.
     */
    public static void handleBreakBuildMode(BreakBuildModePacket packet, ServerPlayer player) {
        boolean creative = player.isCreative();

        // Enforce survivalAllowBreaking (early exit before running pipeline)
        if (!creative && !ServerConfig.INSTANCE.survivalAllowBreaking) {
            player.displayClientMessage(
                    Component.translatable("effortlessbuilding.message.breaking_disabled"), true);
            return;
        }

        ServerLevel level = player.serverLevel();

        // Run the full server pipeline: BuildMode → Modifiers → Constraints
        BlockSet blockSet = BuildPipeline.SERVER.runServerPipeline(
                packet.buildMode(), packet.firstPos(), packet.secondPos(), packet.thirdPos(),
                player, BuildPipeline.BuildState.BREAKING,
                packet.fill(), packet.cubeFill(), packet.raisedEdge(), packet.circleStart(),
                packet.protectTileEntities());

        if (blockSet == null) {
            Constants.LOG.warn("[EffortlessBuilding] Received BreakBuildModePacket but mode {} returned no blocks", packet.buildMode());
            return;
        }

        Map<BlockPos, UndoManager.BlockChange> undoChanges = new LinkedHashMap<>();
        BlockState airState = Blocks.AIR.defaultBlockState();

        int broken = 0;
        for (var mapEntry : blockSet.validEntries()) {
            BlockPos pos = mapEntry.getKey();
            BlockState oldState = level.getBlockState(pos);
            if (oldState.isAir()) continue;

            if (!creative) {

                // Use the correct tool from inventory for drop calculation (enchantments matter)
                ItemStack toolForDrops = ServerConfig.INSTANCE.survivalRequireTools
                        ? InventoryHelper.findCorrectTool(player, oldState)
                        : player.getMainHandItem();
                var drops = Block.getDrops(oldState, level, pos, level.getBlockEntity(pos),
                        player, toolForDrops);
                for (ItemStack drop : drops) {
                    InventoryHelper.giveOrDropItems(player, drop.getItem(), drop.getCount());
                }
                // Use tool durability if enabled
                if (ServerConfig.INSTANCE.survivalUseDurability) {
                    InventoryHelper.damageCorrectTool(player, oldState);
                }
                level.setBlock(pos, airState, 3);
            } else {
                level.destroyBlock(pos, false, player);
            }
            undoChanges.put(pos.immutable(), new UndoManager.BlockChange(oldState, airState));
            broken++;
        }

        if (!undoChanges.isEmpty()) {
            UndoManager.recordOperation(player, level.dimension(), undoChanges);
        }
    }

    /**
     * Called on the server when an {@link UndoPacket} is received.
     */
    public static void handleUndo(ServerPlayer player) {
        int count = UndoManager.undo(player);
        if (count >= 0) {
            player.displayClientMessage(
                    Component.translatable("effortlessbuilding.message.undo", count), true);
        } else {
            player.displayClientMessage(
                    Component.translatable("effortlessbuilding.message.nothing_to_undo"), true);
        }
    }

    /**
     * Called on the server when a {@link RedoPacket} is received.
     */
    public static void handleRedo(ServerPlayer player) {
        int count = UndoManager.redo(player);
        if (count >= 0) {
            player.displayClientMessage(
                    Component.translatable("effortlessbuilding.message.redo", count), true);
        } else {
            player.displayClientMessage(
                    Component.translatable("effortlessbuilding.message.nothing_to_redo"), true);
        }
    }

    /**
     * Called on the server when an {@link UpdateModifiersC2SPacket} is received.
     */
    public static void handleUpdateModifiers(UpdateModifiersC2SPacket packet, ServerPlayer player) {
        List<IModifier> modifiers = ModifierSerializer.deserialize(packet.json());
        ModifierServerStorage.setModifiers(player.getUUID(), modifiers);
        ModifierServerStorage.savePlayer(player.server, player.getUUID());
        // Echo back to client as confirmation
        sendToClient(player, new SyncModifiersS2CPacket(
                ModifierServerStorage.serializePlayer(player.getUUID())));
    }

    /**
     * Called on the client when a {@link SyncModifiersS2CPacket} is received.
     * Replaces the client-side modifier list with the server's authoritative copy.
     */
    public static void handleSyncModifiers(SyncModifiersS2CPacket packet) {
        List<IModifier> modifiers = ModifierSerializer.deserialize(packet.json());
        ModifierSystem.CLIENT.clearModifiers();
        for (IModifier m : modifiers) {
            ModifierSystem.CLIENT.addModifier(m);
        }
    }

    /**
     * Called on the server when an {@link UpdateServerConfigC2SPacket} is received.
     * Only operators (permission level 2+) may update the config.
     */
    public static void handleUpdateServerConfig(UpdateServerConfigC2SPacket packet, ServerPlayer player) {
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(
                    Component.translatable("effortlessbuilding.message.not_operator"), false);
            return;
        }
        ServerConfig incoming = ServerConfig.fromJson(packet.json());
        ServerConfig.INSTANCE.copyFrom(incoming);
        ServerConfigStorage.save(player.server);

        // Broadcast updated config to all connected players
        String json = ServerConfig.INSTANCE.toJson();
        for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
            sendToClient(p, new SyncServerConfigS2CPacket(json));
        }
    }

    /**
     * Called on the client when a {@link SyncServerConfigS2CPacket} is received.
     */
    public static void handleSyncServerConfig(SyncServerConfigS2CPacket packet) {
        ServerConfig incoming = ServerConfig.fromJson(packet.json());
        ServerConfig.INSTANCE.copyFrom(incoming);
    }


    /** Exposes the protected {@link BlockPlaceContext} constructor for server-side use. */
    private static final class OpenBlockPlaceContext extends BlockPlaceContext {
        OpenBlockPlaceContext(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player,
                              InteractionHand hand, ItemStack stack, BlockHitResult hit) {
            super(level, player, hand, stack, hit);
        }
    }

    /** Exposes the protected {@link UseOnContext} constructor for server-side use. */
    private static final class OpenUseOnContext extends UseOnContext {
        OpenUseOnContext(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player,
                         InteractionHand hand, ItemStack stack, BlockHitResult hit) {
            super(level, player, hand, stack, hit);
        }
    }
}
