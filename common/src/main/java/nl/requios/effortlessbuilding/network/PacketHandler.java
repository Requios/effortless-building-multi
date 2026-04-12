package nl.requios.effortlessbuilding.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import nl.requios.effortlessbuilding.mixin.BucketItemAccessor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import nl.requios.effortlessbuilding.Constants;
import nl.requios.effortlessbuilding.buildchain.BuildChain;
import nl.requios.effortlessbuilding.buildmode.BuildSettings;
import nl.requios.effortlessbuilding.platform.Services;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import nl.requios.effortlessbuilding.utilities.InventoryHelper;
import nl.requios.effortlessbuilding.utilities.UndoManager;

import java.util.LinkedHashMap;
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

    /**
     * Called on the server when a {@link PlaceBuildModePacket} is received.
     */
    public static void handlePlaceBuildMode(PlaceBuildModePacket packet, ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        BlockSet blockSet = BuildChain.SERVER.computeServerBlocks(
                packet.buildMode(), packet.firstPos(), packet.secondPos(), packet.thirdPos(),
                player, BuildChain.BuildState.PLACING,
                packet.fill(), packet.cubeFill(), packet.raisedEdge(), packet.circleStart());

        if (blockSet == null) {
            Constants.LOG.warn("[EffortlessBuilding] Received PlaceBuildModePacket but mode {} returned no blocks", packet.buildMode());
            return;
        }

        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack offHand = player.getItemInHand(InteractionHand.OFF_HAND);
        boolean creative = player.isCreative();

        BuildSettings.ReplaceMode replaceMode = creative
                ? packet.replaceMode()
                : BuildSettings.ReplaceMode.ONLY_AIR;
        boolean protectTiles = packet.protectTileEntities();

        Map<BlockPos, UndoManager.BlockChange> undoChanges = new LinkedHashMap<>();

        int placed = 0;
        if (held.getItem() instanceof BlockItem blockItem) {
            Item heldItem = held.getItem();

            int available = creative ? Integer.MAX_VALUE
                    : InventoryHelper.findTotalItemsInInventory(player, heldItem);

            double yFrac = packet.hitLocation().y - Math.floor(packet.hitLocation().y);
            for (BlockPos pos : blockSet.keySet()) {
                if (!creative && placed >= available) break;

                if (BuildSettings.canPlaceAt(level, pos, replaceMode, protectTiles, offHand)) {
                    BlockState oldState = level.getBlockState(pos);
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
                    undoChanges.put(pos.immutable(), new UndoManager.BlockChange(oldState, state));
                    placed++;
                }
            }

            if (!creative && placed > 0) {
                InventoryHelper.consumeItems(player, heldItem, placed);
            }
        } else if (held.getItem() instanceof BucketItem bucketItem) {
            var fluid = ((BucketItemAccessor) bucketItem).effortlessbuilding$getFluid();
            if (!fluid.isSame(Fluids.EMPTY)) {
                BlockState fluidState = fluid.defaultFluidState().createLegacyBlock();
                int maxPlace = creative ? Integer.MAX_VALUE : 1;
                for (BlockPos pos : blockSet.keySet()) {
                    if (placed >= maxPlace) break;
                    if (BuildSettings.canPlaceAt(level, pos, replaceMode, protectTiles, offHand)) {
                        BlockState oldState = level.getBlockState(pos);
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
        } else {
            return;
        }

        if (!undoChanges.isEmpty()) {
            UndoManager.recordOperation(player, level.dimension(), undoChanges);
        }

        Constants.LOG.debug("[EffortlessBuilding] Placed {} blocks for {} (mode {})", placed, player.getName().getString(), packet.buildMode());
    }

    /**
     * Called on the server when a {@link BreakBuildModePacket} is received.
     */
    public static void handleBreakBuildMode(BreakBuildModePacket packet, ServerPlayer player) {
        if (!player.isCreative()) {
            Constants.LOG.warn("[EffortlessBuilding] Survival player {} tried to use build-mode breaking, ignoring", player.getName().getString());
            return;
        }

        ServerLevel level = player.serverLevel();

        BlockSet blockSet = BuildChain.SERVER.computeServerBlocks(
                packet.buildMode(), packet.firstPos(), packet.secondPos(), packet.thirdPos(),
                player, BuildChain.BuildState.BREAKING,
                packet.fill(), packet.cubeFill(), packet.raisedEdge(), packet.circleStart());

        if (blockSet == null) {
            Constants.LOG.warn("[EffortlessBuilding] Received BreakBuildModePacket but mode {} returned no blocks", packet.buildMode());
            return;
        }

        Map<BlockPos, UndoManager.BlockChange> undoChanges = new LinkedHashMap<>();
        BlockState airState = Blocks.AIR.defaultBlockState();

        int broken = 0;
        for (BlockPos pos : blockSet.keySet()) {
            BlockState oldState = level.getBlockState(pos);
            if (!oldState.isAir()) {
				level.destroyBlock(pos, false, player);
                undoChanges.put(pos.immutable(), new UndoManager.BlockChange(oldState, airState));
                broken++;
            }
        }

        if (!undoChanges.isEmpty()) {
            UndoManager.recordOperation(player, level.dimension(), undoChanges);
        }

        Constants.LOG.debug("[EffortlessBuilding] Broke {} blocks for {} (mode {})", broken, player.getName().getString(), packet.buildMode());
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

    /** Exposes the protected {@link BlockPlaceContext} constructor for server-side use. */
    private static final class OpenBlockPlaceContext extends BlockPlaceContext {
        OpenBlockPlaceContext(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player,
                              InteractionHand hand, ItemStack stack, BlockHitResult hit) {
            super(level, player, hand, stack, hit);
        }
    }
}
