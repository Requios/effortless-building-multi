package nl.requios.effortlessbuilding.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import nl.requios.effortlessbuilding.Constants;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.platform.Services;

import java.util.List;

public class PacketHandler {

    /**
     * Send a build-mode placement request from the client to the server.
     * Delegates to the platform-specific network implementation via {@link Services#NETWORK}.
     */
    public static void sendToServer(PlaceBuildModePacket packet) {
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

        var defaultState = blockItem.getBlock().defaultBlockState();
        int placed = 0;
        for (BlockPos pos : positions) {
            if (level.getBlockState(pos).canBeReplaced()) {
                level.setBlock(pos, defaultState, 3);
                placed++;
            }
        }

        Constants.LOG.debug("[EffortlessBuilding] Placed {} blocks for {} (mode {})", placed, player.getName().getString(), packet.buildMode());
    }
}
