package nl.requios.effortlessbuilding.buildchain;

import net.minecraft.world.entity.player.Player;
import nl.requios.effortlessbuilding.utilities.BlockSet;

/**
 * One stage in the {@link BuildChain} that can observe or transform the set of
 * block positions produced by earlier stages.
 *
 * <p>Implementations may add, remove, or reposition entries — for example:
 * <ul>
 *   <li>a <em>constraint system</em> that removes positions outside an allowed region</li>
 *   <li>a <em>modifier system</em> that mirrors or rotates the shape</li>
 * </ul>
 *
 * <p>{@link #processBlocks} is called both during per-frame preview and during actual
 * placement/breaking, so implementations must not rely on call-count or timing.
 */
public interface IBuildSystem {
    /**
     * Transform {@code blocks} in-place.
     *
     * @param blocks the block set populated so far (may already contain entries from prior stages)
     * @param player the acting player
     * @param action whether this is a {@link BuildChain.BuildState#PLACING} or
     *               {@link BuildChain.BuildState#BREAKING} operation
     */
    void processBlocks(BlockSet blocks, Player player, BuildChain.BuildState action);
}
