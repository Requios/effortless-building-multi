package nl.requios.effortlessbuilding.utilities;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Helpers for transforming {@link BlockState} beyond what vanilla's
 * {@link BlockState#mirror} and {@link BlockState#rotate} cover.
 */
public final class BlockUtilities {

    private BlockUtilities() {}

    /**
     * Mirrors a block state vertically (Y axis).
     * <p>Vanilla's {@code BlockState.mirror()} only handles horizontal mirrors.
     * This method flips:
     * <ul>
     *   <li>{@code HALF} (top ↔ bottom) — stairs, trapdoors</li>
     *   <li>{@code FACING} when vertical (UP ↔ DOWN) — pistons, observers, dispensers</li>
     * </ul>
     */
    public static BlockState applyVerticalMirror(BlockState state) {
        // Flip top/bottom half (stairs, trapdoors, etc.)
        if (state.hasProperty(BlockStateProperties.HALF)) {
            Half half = state.getValue(BlockStateProperties.HALF);
            state = state.setValue(BlockStateProperties.HALF,
                    half == Half.TOP ? Half.BOTTOM : Half.TOP);
        }

        // Flip vertical facing (pistons, observers, dispensers, end rods, etc.)
        if (state.hasProperty(BlockStateProperties.FACING)) {
            Direction dir = state.getValue(BlockStateProperties.FACING);
            if (dir == Direction.UP || dir == Direction.DOWN) {
                state = state.setValue(BlockStateProperties.FACING, dir.getOpposite());
            }
        }

        return state;
    }
}

