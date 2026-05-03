package nl.requios.effortlessbuilding.utilities;

import nl.requios.effortlessbuilding.buildpipeline.ConstraintSystem;

/**
 * Status of a {@link BlockEntry} after the constraint pipeline runs.
 * Entries start as {@link #VALID} and may be marked with a rejection reason
 * by the {@link ConstraintSystem}.
 *
 * <p>The renderer uses the status to show appropriate visual feedback (icon + color).
 * The server skips non-VALID entries during world mutation.
 */
public enum BlockStatus {
    /** Block can be placed/broken normally. */
    VALID,
    /** Survival: block was not placed by this player this session. */
    NOT_PLACED_BY_PLAYER,
    /** Survival: block hardness exceeds survivalMaxHardness. */
    TOO_HARD,
    /** Survival: correct tool required but player doesn't have one. */
    MISSING_TOOL,
    /** Block is outside the configured reach distance. */
    OUTSIDE_REACH,
    /** Max blocks limit has been exceeded (this entry is above the cap). */
    MAX_BLOCKS_EXCEEDED,
    /** Block is outside the world border or build height. */
    WORLD_BORDER,
    /** Tile entity is protected by config. */
    PROTECTED_TILE_ENTITY,
    /** Survival: player doesn't have enough items. */
    INSUFFICIENT_ITEMS,
    /** Survival: breaking is disabled in server config. */
    BREAKING_DISABLED;

    public boolean isValid() {
        return this == VALID;
    }
}

