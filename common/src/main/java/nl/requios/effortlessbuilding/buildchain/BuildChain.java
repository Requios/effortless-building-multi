package nl.requios.effortlessbuilding.buildchain;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.mixin.BucketItemAccessor;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Central coordinator for build-mode placement and breaking.
 *
 * <p>The chain consists of ordered {@link IBuildSystem} stages that transform the
 * block set after the build mode computes the initial positions.
 */
public class BuildChain {

    /** Server-side singleton — registered systems run on the server when a packet is received. */
    public static final BuildChain SERVER = new BuildChain();

    public enum BuildState { PLACING, BREAKING }

    private final List<IBuildSystem> systems = new ArrayList<>();

    /**
     * Returns {@code true} if right-clicking with this item should trigger the
     * build-mode sequence: block items and non-empty bucket items (water, lava, etc.).
     */
    public static boolean isBuildTriggerItem(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem) return true;
        if (stack.getItem() instanceof BucketItem) {
            return !((BucketItemAccessor) stack.getItem()).effortlessbuilding$getFluid().isSame(Fluids.EMPTY);
        }
        return false;
    }

    // Use this instead of player.getLookAngle() in any build-modes code.
    // Keeps components away from exactly 0 or ±1 to avoid division-by-zero in findXBound etc.
    public static Vec3 getPlayerLookVec(Player player) {
        Vec3 lookVec = player.getLookAngle();
        double x = lookVec.x;
        double y = lookVec.y;
        double z = lookVec.z;

        if (Math.abs(x) < 0.0001) x = 0.0001;
        if (Math.abs(x - 1.0) < 0.0001) x = 0.9999;
        if (Math.abs(x + 1.0) < 0.0001) x = -0.9999;

        if (Math.abs(y) < 0.0001) y = 0.0001;
        if (Math.abs(y - 1.0) < 0.0001) y = 0.9999;
        if (Math.abs(y + 1.0) < 0.0001) y = -0.9999;

        if (Math.abs(z) < 0.0001) z = 0.0001;
        if (Math.abs(z - 1.0) < 0.0001) z = 0.9999;
        if (Math.abs(z + 1.0) < 0.0001) z = -0.9999;

        return new Vec3(x, y, z);
    }

    // -------------------------------------------------------------------------
    // System registration
    // -------------------------------------------------------------------------

    /** Appends a system to the end of the processing pipeline. */
    public void addSystem(IBuildSystem system) {
        systems.add(system);
    }

    // -------------------------------------------------------------------------
    // Shared server-side pipeline
    // -------------------------------------------------------------------------

    /**
     * Computes and processes the full block set for a server-side build action.
     * This is the single authoritative pipeline that both place and break handlers use.
     *
     * @return the processed {@link BlockSet}, or {@code null} if the mode produced no blocks.
     */
    public @Nullable BlockSet computeServerBlocks(BuildModeEnum mode,
                                                   BlockPos firstPos, BlockPos secondPos,
                                                   @Nullable BlockPos thirdPos,
                                                   Player player, BuildState action,
                                                   ModeOptions.ActionEnum fill, ModeOptions.ActionEnum cubeFill,
                                                   ModeOptions.ActionEnum raisedEdge, ModeOptions.ActionEnum circleStart) {
        ModeOptions.applyForCalculation(fill, cubeFill, raisedEdge, circleStart);

        List<BlockPos> rawPositions = mode.instance.getServerBlocks(player, firstPos, secondPos, thirdPos);
        if (rawPositions.isEmpty()) return null;

        BlockSet blockSet = toBlockSet(rawPositions);
        processBlocks(blockSet, player, action);
        return blockSet;
    }

    /** Wraps a flat list of positions into a {@link BlockSet} for chain processing. */
    public static BlockSet toBlockSet(List<BlockPos> positions) {
        BlockSet blockSet = new BlockSet();
        for (BlockPos pos : positions) {
            blockSet.add(new BlockEntry(pos));
        }
        if (!positions.isEmpty()) {
            blockSet.firstPos = positions.getFirst();
            blockSet.lastPos = positions.getLast();
        }
        return blockSet;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Run all registered {@link IBuildSystem} stages over {@code blocks} in order. */
    public void processBlocks(BlockSet blocks, Player player, BuildState action) {
        for (IBuildSystem system : systems) {
            system.processBlocks(blocks, player, action);
        }
    }

}
