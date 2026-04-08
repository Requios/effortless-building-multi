package nl.requios.effortlessbuilding.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import nl.requios.effortlessbuilding.mixin.BucketItemAccessor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import nl.requios.effortlessbuilding.Constants;
import nl.requios.effortlessbuilding.buildchain.BuildChain;
import nl.requios.effortlessbuilding.platform.Services;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;

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
     * then applies world mutations.
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

        int placed = 0;
        if (held.getItem() instanceof BlockItem blockItem) {
            double yFrac = packet.hitLocation().y - Math.floor(packet.hitLocation().y);
            for (BlockPos pos : blockSet.keySet()) {
                if (level.getBlockState(pos).canBeReplaced()) {
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
        } else if (held.getItem() instanceof BucketItem bucketItem) {
            var fluid = ((BucketItemAccessor) bucketItem).effortlessbuilding$getFluid();
            if (!fluid.isSame(Fluids.EMPTY)) {
                BlockState fluidState = fluid.defaultFluidState().createLegacyBlock();
                for (BlockPos pos : blockSet.keySet()) {
                    if (level.getBlockState(pos).canBeReplaced()) {
                        level.setBlock(pos, fluidState, 3);
                        placed++;
                    }
                }
            }
        } else {
            return;
        }

        Constants.LOG.debug("[EffortlessBuilding] Placed {} blocks for {} (mode {})", placed, player.getName().getString(), packet.buildMode());
    }

    /**
     * Called on the server when a {@link BreakBuildModePacket} is received.
     * Uses the unified {@link BuildChain#computeServerBlocks} pipeline,
     * then applies world mutations.
     */
    public static void handleBreakBuildMode(BreakBuildModePacket packet, ServerPlayer player) {
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
