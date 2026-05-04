package nl.requios.effortlessbuilding.buildpipeline;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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
 * Unified build pipeline: an ordered list of {@link IBuildSystem} stages.
 *
 * <p>Both client (preview) and server (execution) use the same pipeline structure.
 * The server pipeline has these stages registered (in order):
 * <ol>
 *   <li>{@link BuildModeSystem} — generates initial positions from the build mode</li>
 *   <li>{@link ModifierSystemServer} — multiplies positions (mirror/array/radial, per-player)</li>
 *   <li>{@link ConstraintSystem} — marks invalid positions with rejection reasons</li>
 * </ol>
 *
 * <p>The client pipeline has:
 * <ol>
 *   <li>{@link nl.requios.effortlessbuilding.modifier.ModifierSystem#CLIENT} — multiplies positions</li>
 *   <li>{@link ConstraintSystem} — marks invalid positions</li>
 * </ol>
 * (The client doesn't use BuildModeSystem because it handles multi-click state directly.)
 */
public class BuildPipeline {

    /**
     * Server-side singleton with all stages pre-registered.
     * Pipeline order: BuildModeSystem → ModifierSystemServer → ConstraintSystem
     */
    public static final BuildPipeline SERVER = createServerPipeline();

    public enum BuildState { PLACING, BREAKING }

    private final List<IBuildSystem> systems = new ArrayList<>();

    private static BuildPipeline createServerPipeline() {
        BuildPipeline pipeline = new BuildPipeline();
        pipeline.addSystem(BuildModeSystem.INSTANCE);
        pipeline.addSystem(ModifierSystemServer.INSTANCE);
        pipeline.addSystem(ConstraintSystem.INSTANCE);
        return pipeline;
    }

    /**
     * Returns {@code true} if right-clicking with this item should trigger the
     * build-mode sequence: block items and non-empty bucket items (water, lava, etc.).
     * Multiblock items (doors, beds, tall plants) are excluded — they need vanilla's
     * setPlacedBy logic which the mod's batch placement cannot replicate correctly.
     */
    public static boolean isBuildTriggerItem(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            BlockState defaultState = blockItem.getBlock().defaultBlockState();
            for (var property : defaultState.getProperties()) {
                if (property.getValueClass() == DoubleBlockHalf.class
                        || property.getValueClass() == BedPart.class) {
                    return false;
                }
            }
            return true;
        }
        if (stack.getItem() instanceof BucketItem) {
            return !((BucketItemAccessor) stack.getItem()).effortlessbuilding$getFluid().isSame(Fluids.EMPTY);
        }
        // Tools that modify blocks on right-click (axe strips logs, shovel makes paths, hoe tills)
        if (stack.has(DataComponents.TOOL)) {
            return true;
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
    // Pipeline execution
    // -------------------------------------------------------------------------

    /**
     * Runs the full server pipeline: sets context for {@link BuildModeSystem}, then
     * executes all stages. Returns the resulting block set, or null if empty.
     *
     * <p>This is the ONLY method PacketHandler needs to call.
     */
    public @Nullable BlockSet runServerPipeline(BuildModeEnum mode,
                                                BlockPos firstPos, BlockPos secondPos,
                                                @Nullable BlockPos thirdPos,
                                                Player player, BuildState action,
                                                ModeOptions.ActionEnum fill, ModeOptions.ActionEnum cubeFill,
                                                ModeOptions.ActionEnum raisedEdge, ModeOptions.ActionEnum circleStart,
                                                boolean protectTileEntities) {
        BuildModeSystem.setContext(new BuildModeSystem.Context(
                mode, firstPos, secondPos, thirdPos, fill, cubeFill, raisedEdge, circleStart));
        ConstraintSystem.setPlacementContext(new ConstraintSystem.PlacementContext(protectTileEntities));
        try {
            BlockSet blockSet = new BlockSet();
            processBlocks(blockSet, player, action);
            if (blockSet.isEmpty()) return null;
            return blockSet;
        } finally {
            BuildModeSystem.clearContext();
            ConstraintSystem.clearPlacementContext();
        }
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
