package nl.requios.effortlessbuilding.buildpipeline;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import nl.requios.effortlessbuilding.config.ServerConfig;
import nl.requios.effortlessbuilding.utilities.*;

/**
 * Pipeline stage that marks entries as rejected based on server configuration rules.
 *
 * <p>Runs after modifiers have expanded the block set. Instead of removing entries,
 * it marks them with a {@link BlockStatus} so the renderer can show why a position
 * is invalid, while the server simply skips non-valid entries during execution.
 *
 * <p>Checks performed (in order):
 * <ol>
 *   <li>Breaking disabled — all entries marked if breaking is disallowed (breaking only)</li>
 *   <li>Max blocks limit — entries beyond the cap are marked {@link BlockStatus#MAX_BLOCKS_EXCEEDED}</li>
 *   <li>Only-placed-blocks — positions not tracked by {@link PlacedBlockTracker}</li>
 *   <li>Max hardness — blocks exceeding {@code survivalMaxHardness}</li>
 *   <li>Require tools — blocks that need a tool the player doesn't have</li>
 * </ol>
 *
 * <p>For placement with replace mode, the same breaking checks apply to any existing
 * non-replaceable block that would be displaced.
 */
public class ConstraintSystem implements IBuildSystem {

    public static final ConstraintSystem INSTANCE = new ConstraintSystem();

    @Override
    public void processBlocks(BlockSet blocks, Player player, BuildPipeline.BuildState action) {
        if (player.getAbilities().instabuild) return; // Creative skips all constraints

        Level level = player.level();
        boolean isBreaking = action == BuildPipeline.BuildState.BREAKING;

        // Check if breaking is globally disabled
        if (isBreaking && !ServerConfig.INSTANCE.survivalAllowBreaking) {
            for (BlockEntry entry : blocks.values()) {
                entry.markRejected(BlockStatus.BREAKING_DISABLED);
            }
            return;
        }

        // Max blocks limit — mark entries beyond the cap
        int maxBlocks = ServerConfig.INSTANCE.getMaxBlocksPlaced(player);
        int count = 0;
        for (BlockEntry entry : blocks.values()) {
            count++;
            if (count > maxBlocks) {
                entry.markRejected(BlockStatus.MAX_BLOCKS_EXCEEDED);
            }
        }

        // Per-position survival checks for breaking OR replacing existing blocks during placement
        for (var mapEntry : blocks.entrySet()) {
            BlockEntry entry = mapEntry.getValue();
            if (!entry.isValid()) continue; // already rejected

            BlockPos pos = mapEntry.getKey();
            BlockState state = level.getBlockState(pos);

            if (isBreaking) {
                // Skip air blocks for breaking
                if (state.isAir()) continue;
            } else {
                // For placement: only apply breaking constraints to non-replaceable existing blocks
                if (state.canBeReplaced()) continue;
            }

            // Only placed blocks check
            if (ServerConfig.INSTANCE.survivalOnlyPlacedBlocks
                    && !PlacedBlockTracker.isTrackedAnySide(player, level, pos)) {
                entry.markRejected(BlockStatus.NOT_PLACED_BY_PLAYER);
                continue;
            }

            // Max hardness check
            if (ServerConfig.INSTANCE.survivalMaxHardness >= 0) {
                float hardness = state.getDestroySpeed(level, pos);
                if (hardness > ServerConfig.INSTANCE.survivalMaxHardness) {
                    entry.markRejected(BlockStatus.TOO_HARD);
                    continue;
                }
            }

            // Require tools check
            if (ServerConfig.INSTANCE.survivalRequireTools && state.requiresCorrectToolForDrops()) {
                if (!InventoryHelper.hasCorrectToolForBlock(player, state)) {
                    entry.markRejected(BlockStatus.MISSING_TOOL);
                }
            }
        }
    }
}

