package nl.requios.effortlessbuilding.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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

public class PacketHandler {

    public static void sendToServer(PlaceBuildModePacket packet) {
        Services.NETWORK.sendToServer(packet);
    }

    public static void sendToServer(BreakBuildModePacket packet) {
        Services.NETWORK.sendToServer(packet);
    }

    /**
     * Called on the server when a {@link PlaceBuildModePacket} is received.
     * Uses the unified {@link BuildChain#computeServerBlocks} pipeline,
     * then applies world mutations.  In survival, items are consumed from
     * inventory and positions beyond the player's supply are skipped.
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

        // Replace mode is creative-only; force ONLY_AIR for survival
        BuildSettings.ReplaceMode replaceMode = creative
                ? packet.replaceMode()
                : BuildSettings.ReplaceMode.ONLY_AIR;
        boolean protectTiles = packet.protectTileEntities();

        int placed = 0;
        if (held.getItem() instanceof BlockItem blockItem) {
            Item heldItem = held.getItem();

            // In survival, figure out how many blocks we can afford
            int available = creative ? Integer.MAX_VALUE
                    : InventoryHelper.findTotalItemsInInventory(player, heldItem);

            double yFrac = packet.hitLocation().y - Math.floor(packet.hitLocation().y);
            for (BlockPos pos : blockSet.keySet()) {
                if (!creative && placed >= available) break;

                if (BuildSettings.canPlaceAt(level, pos, replaceMode, protectTiles, offHand)) {
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
                    placed++;
                }
            }

            // Consume items from inventory in survival
            if (!creative && placed > 0) {
                InventoryHelper.consumeItems(player, heldItem, placed);
            }
        } else if (held.getItem() instanceof BucketItem bucketItem) {
            var fluid = ((BucketItemAccessor) bucketItem).effortlessbuilding$getFluid();
            if (!fluid.isSame(Fluids.EMPTY)) {
                BlockState fluidState = fluid.defaultFluidState().createLegacyBlock();
                // In survival, a bucket is single-use
                int maxPlace = creative ? Integer.MAX_VALUE : 1;
                for (BlockPos pos : blockSet.keySet()) {
                    if (placed >= maxPlace) break;
                    if (BuildSettings.canPlaceAt(level, pos, replaceMode, protectTiles, offHand)) {
                        level.setBlock(pos, fluidState, 3);
                        placed++;
                    }
                }
                // Consume the bucket in survival (replace with empty bucket)
                if (!creative && placed > 0) {
                    player.setItemInHand(InteractionHand.MAIN_HAND,
                            new ItemStack(net.minecraft.world.item.Items.BUCKET));
                }
            }
        } else {
            return;
        }

        Constants.LOG.debug("[EffortlessBuilding] Placed {} blocks for {} (mode {})", placed, player.getName().getString(), packet.buildMode());
    }

    /**
     * Called on the server when a {@link BreakBuildModePacket} is received.
     * Only allowed in creative mode — survival players cannot mass-break.
     */
    public static void handleBreakBuildMode(BreakBuildModePacket packet, ServerPlayer player) {
        // Mod-assisted breaking is creative-only
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

        int broken = 0;
        for (BlockPos pos : blockSet.keySet()) {
            if (!level.getBlockState(pos).isAir()) {
                level.destroyBlock(pos, true, player);
                broken++;
            }
        }

        Constants.LOG.debug("[EffortlessBuilding] Broke {} blocks for {} (mode {})", broken, player.getName().getString(), packet.buildMode());
    }

    /** Exposes the protected {@link BlockPlaceContext} constructor for server-side use. */
    private static final class OpenBlockPlaceContext extends BlockPlaceContext {
        OpenBlockPlaceContext(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player,
                              InteractionHand hand, ItemStack stack, BlockHitResult hit) {
            super(level, player, hand, stack, hit);
        }
    }
}
