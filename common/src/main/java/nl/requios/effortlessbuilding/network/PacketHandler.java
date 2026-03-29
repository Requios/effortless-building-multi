package nl.requios.effortlessbuilding.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import nl.requios.effortlessbuilding.Constants;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.platform.Services;

import java.util.List;

public class PacketHandler {

    public static void sendToServer(PlaceBuildModePacket packet) {
        Services.NETWORK.sendToServer(packet);
    }

    public static void sendToServer(BreakBuildModePacket packet) {
        Services.NETWORK.sendToServer(packet);
    }

    /**
     * Called on the server when a {@link PlaceBuildModePacket} is received.
     * Recalculates block positions using the mode's own algorithm and places them.
     */
    public static void handlePlaceBuildMode(PlaceBuildModePacket packet, ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        // Apply the options the client used so getAllBlocks/getFinalBlocks produce the same result.
        ModeOptions.applyForCalculation(packet.fill(), packet.cubeFill(), packet.raisedEdge(), packet.circleStart());

        List<BlockPos> positions = packet.buildMode().instance.getServerBlocks(
                player, packet.firstPos(), packet.secondPos(), packet.thirdPos());

        if (positions.isEmpty()) {
            Constants.LOG.warn("[EffortlessBuilding] Received PlaceBuildModePacket but mode {} returned no blocks", packet.buildMode());
            return;
        }

        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(held.getItem() instanceof BlockItem blockItem)) return;

        // Preserve the Y fraction within the first clicked block so slabs and similar
        // blocks get the correct half across all positions in the shape.
        double yFrac = packet.hitLocation().y - Math.floor(packet.hitLocation().y);

        int placed = 0;
        for (BlockPos pos : positions) {
            if (level.getBlockState(pos).canBeReplaced()) {
                // Build a hit result anchored to this specific block position so that
                // getStateForPlacement can correctly compute per-position properties
                // (e.g. slab half uses hitLocation.y - clickedPos.getY()).
                Vec3 localHit = new Vec3(packet.hitLocation().x, pos.getY() + yFrac, packet.hitLocation().z);
                BlockHitResult serverHit = new BlockHitResult(localHit, packet.hitFace(), pos, false);
                BlockPlaceContext ctx = new OpenBlockPlaceContext(level, player, InteractionHand.MAIN_HAND, held, serverHit);
                BlockState state = blockItem.getBlock().getStateForPlacement(ctx);
                if (state == null) state = blockItem.getBlock().defaultBlockState();
                level.setBlock(pos, state, 3);
                placed++;
            }
        }

        Constants.LOG.debug("[EffortlessBuilding] Placed {} blocks for {} (mode {})", placed, player.getName().getString(), packet.buildMode());
    }

    /**
     * Called on the server when a {@link BreakBuildModePacket} is received.
     * Recalculates block positions using the mode's own algorithm and breaks them.
     */
    public static void handleBreakBuildMode(BreakBuildModePacket packet, ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        ModeOptions.applyForCalculation(packet.fill(), packet.cubeFill(), packet.raisedEdge(), packet.circleStart());

        List<BlockPos> positions = packet.buildMode().instance.getServerBlocks(
                player, packet.firstPos(), packet.secondPos(), packet.thirdPos());

        if (positions.isEmpty()) {
            Constants.LOG.warn("[EffortlessBuilding] Received BreakBuildModePacket but mode {} returned no blocks", packet.buildMode());
            return;
        }

        int broken = 0;
        for (BlockPos pos : positions) {
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
