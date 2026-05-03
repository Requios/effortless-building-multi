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
 *   <li>World border / build height — all players including creative</li>
 *   <li>Max blocks limit — entries beyond the cap are marked {@link BlockStatus#MAX_BLOCKS_EXCEEDED} (all players)</li>
 *   <li>Breaking disabled — all entries marked if breaking is disallowed (survival, breaking only)</li>
 *   <li>Protected tile entities — tile entities the player wants protected</li>
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

    /**
     * Thread-local placement context set by the server before running the pipeline.
     * On the client, this is null and the system reads from ClientConfig instead.
     */
    private static final ThreadLocal<PlacementContext> PLACEMENT_CTX = new ThreadLocal<>();

    public record PlacementContext(boolean protectTileEntities) {}

    public static void setPlacementContext(PlacementContext ctx) {
        PLACEMENT_CTX.set(ctx);
    }

    public static void clearPlacementContext() {
        PLACEMENT_CTX.remove();
    }

    @Override
    public void processBlocks(BlockSet blocks, Player player, BuildPipeline.BuildState action) {
        Level level = player.level();
        boolean isBreaking = action == BuildPipeline.BuildState.BREAKING;

        // World border + build height — applies to ALL players (including creative)
        var worldBorder = level.getWorldBorder();
        for (var mapEntry : blocks.entrySet()) {
            BlockEntry entry = mapEntry.getValue();
            if (!entry.isValid()) continue;
            BlockPos pos = mapEntry.getKey();
            if (level.isOutsideBuildHeight(pos) || !worldBorder.isWithinBounds(pos)) {
                entry.markRejected(BlockStatus.WORLD_BORDER);
            }
        }

        // Max blocks limit — applies to ALL players (including creative)
        int maxBlocks = ServerConfig.INSTANCE.getMaxBlocksPlaced(player);
        int validCount = 0;
        for (BlockEntry entry : blocks.values()) {
            if (!entry.isValid()) continue; // don't count already-rejected entries
            validCount++;
            if (validCount > maxBlocks) {
                entry.markRejected(BlockStatus.MAX_BLOCKS_EXCEEDED);
            }
        }

        // Protected tile entities — applies to ALL players (including creative)
        boolean protectTiles = getProtectTileEntities();
        if (protectTiles) {
            for (var mapEntry : blocks.entrySet()) {
                BlockEntry entry = mapEntry.getValue();
                if (!entry.isValid()) continue;
                if (level.getBlockEntity(mapEntry.getKey()) != null) {
                    entry.markRejected(BlockStatus.PROTECTED_TILE_ENTITY);
                }
            }
        }

        if (player.getAbilities().instabuild) return; // Creative skips survival constraints

        // Check if breaking is globally disabled
        if (isBreaking && !ServerConfig.INSTANCE.survivalAllowBreaking) {
            for (BlockEntry entry : blocks.values()) {
                entry.markRejected(BlockStatus.BREAKING_DISABLED);
            }
            return;
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

    /**
     * Gets the protectTileEntities setting. Server reads from ThreadLocal context,
     * client reads from ClientConfig.
     */
    private boolean getProtectTileEntities() {
        PlacementContext ctx = PLACEMENT_CTX.get();
        if (ctx != null) {
            return ctx.protectTileEntities();
        }
        // Client-side: read from ClientConfig
        try {
            return nl.requios.effortlessbuilding.config.ClientConfig.INSTANCE.shouldProtectTileEntities();
        } catch (Exception e) {
            return false; // Fallback if ClientConfig not available (dedicated server)
        }
    }
}

