package nl.requios.effortlessbuilding.buildmode;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client-side build settings for replace mode and tile-entity protection.
 * Replace mode is available to all players; survival players may only replace
 * blocks they placed this session (enforced server-side via PlacedBlockTracker).
 */
public class BuildSettings {

    public static final BuildSettings CLIENT = new BuildSettings();

    public enum ReplaceMode {
        ONLY_AIR,
        BLOCKS_AND_AIR,
        ONLY_BLOCKS,
        FILTERED_BY_OFFHAND
    }

    private ReplaceMode replaceMode = ReplaceMode.ONLY_AIR;
    private boolean protectTileEntities = true;

    // -------------------------------------------------------------------------
    // Replace mode
    // -------------------------------------------------------------------------

    /**
     * Cycle to the next replace mode.
     */
    public void cycleReplaceMode() {
        ReplaceMode[] values = ReplaceMode.values();
        replaceMode = values[(replaceMode.ordinal() + 1) % values.length];
    }

    public void setReplaceMode(ReplaceMode mode) {
        this.replaceMode = mode;
    }

    /**
     * Returns the effective replace mode. Falls back to ONLY_AIR if there is no local player.
     */
    public ReplaceMode getReplaceMode() {
        if (!canReplace()) return ReplaceMode.ONLY_AIR;
        return replaceMode;
    }

    public ReplaceMode getReplaceModeRaw() {
        return replaceMode;
    }

    public boolean isQuickReplacing() {
        return getReplaceMode() != ReplaceMode.ONLY_AIR;
    }

    public ModeOptions.ActionEnum getReplaceModeActionEnum() {
        return switch (getReplaceMode()) {
            case ONLY_AIR -> ModeOptions.ActionEnum.REPLACE_ONLY_AIR;
            case BLOCKS_AND_AIR -> ModeOptions.ActionEnum.REPLACE_BLOCKS_AND_AIR;
            case ONLY_BLOCKS -> ModeOptions.ActionEnum.REPLACE_ONLY_BLOCKS;
            case FILTERED_BY_OFFHAND -> ModeOptions.ActionEnum.REPLACE_FILTERED_BY_OFFHAND;
        };
    }

    // -------------------------------------------------------------------------
    // Protect tile entities
    // -------------------------------------------------------------------------

    public void toggleProtectTileEntities() {
        protectTileEntities = !protectTileEntities;
    }

    public boolean shouldProtectTileEntities() {
        return protectTileEntities;
    }

    // -------------------------------------------------------------------------
    // Query helpers
    // -------------------------------------------------------------------------

    public boolean shouldReplaceAir() {
        ReplaceMode mode = getReplaceMode();
        return mode == ReplaceMode.ONLY_AIR || mode == ReplaceMode.BLOCKS_AND_AIR;
    }

    public boolean shouldReplaceBlocks() {
        ReplaceMode mode = getReplaceMode();
        return mode == ReplaceMode.ONLY_BLOCKS || mode == ReplaceMode.BLOCKS_AND_AIR;
    }

    public boolean shouldReplaceFiltered() {
        return getReplaceMode() == ReplaceMode.FILTERED_BY_OFFHAND;
    }

    /**
     * When replacing, the first click should target the existing block instead
     * of placing adjacent to it.
     */
    public boolean shouldOffsetStartPosition() {
        return getReplaceMode() != ReplaceMode.ONLY_AIR;
    }

    // -------------------------------------------------------------------------
    // Server-side block filtering
    // -------------------------------------------------------------------------

    /**
     * Determines whether a block position can be placed at, given the current
     * replace settings. Call this on the server with the settings sent in the packet.
     *
     * @param level         the server level
     * @param pos           the block position to test
     * @param replaceMode   the replace mode sent from the client
     * @param protectTiles  whether tile entities should be protected
     * @param offHandStack  the player's off-hand item (for filtered mode)
     */
    public static boolean canPlaceAt(Level level, BlockPos pos, ReplaceMode replaceMode,
                                     boolean protectTiles, ItemStack offHandStack) {
        BlockState existing = level.getBlockState(pos);

        // Always protect tile entities if the setting is on
        if (protectTiles && level.getBlockEntity(pos) != null) {
            return existing.canBeReplaced();
        }

        return switch (replaceMode) {
            case ONLY_AIR -> existing.canBeReplaced();
            case BLOCKS_AND_AIR -> true;
            case ONLY_BLOCKS -> !existing.isAir();
            case FILTERED_BY_OFFHAND -> {
                if (existing.canBeReplaced()) {
                    yield true; // air-like blocks are always OK
                }
                // Replace only if the existing block matches the off-hand item
                if (offHandStack.isEmpty()) {
                    yield false;
                }
                var existingBlockItem = existing.getBlock().asItem();
                yield offHandStack.getItem() == existingBlockItem;
            }
        };
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private boolean canReplace() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null;
    }
}




