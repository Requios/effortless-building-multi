package nl.requios.effortlessbuilding.utilities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Tracks what blocks will be broken in the preview, which are rejected,
 * and what tools are required but missing.
 * Used by the HUD renderer to display break stacks near the crosshair.
 */
public class BreakDisplayTracker {

    /** Items we will obtain from valid (breakable) blocks, keyed by item type. */
    public Map<Item, Integer> breakable = new LinkedHashMap<>();

    /** Items from rejected (unbreakable) blocks, keyed by item type. */
    public Map<Item, Integer> rejected = new LinkedHashMap<>();

    /** Tools required for MISSING_TOOL entries that the player lacks. Keyed by tool item. */
    public Set<Item> missingTools = new LinkedHashSet<>();

    public void initialize() {
        breakable.clear();
        rejected.clear();
        missingTools.clear();
    }

    /**
     * Computes break display info from a block set that has already been through the constraint pipeline.
     */
    public void compute(Player player, BlockSet blockSet) {
        initialize();

        Level level = player.level();
        if (level == null) return;

        for (var mapEntry : blockSet.entrySet()) {
            BlockPos pos = mapEntry.getKey();
            BlockEntry entry = mapEntry.getValue();
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;

            Item blockItem = state.getBlock().asItem();
            // Some blocks don't have a corresponding item (e.g. fire) — skip display
            if (blockItem == net.minecraft.world.item.Items.AIR) continue;

            if (entry.isValid()) {
                breakable.merge(blockItem, 1, Integer::sum);
            } else {
                rejected.merge(blockItem, 1, Integer::sum);

                // If rejected due to missing tool, find what tool type would work
                if (entry.getStatus() == BlockStatus.MISSING_TOOL) {
                    collectMissingToolHint(state);
                }
            }
        }
    }

    /**
     * Tries to determine what kind of tool is needed for a block and adds a representative item
     * to {@link #missingTools}. Uses tag-based heuristics since Minecraft doesn't expose a
     * "required tool" item directly.
     */
    private void collectMissingToolHint(BlockState state) {
        // Use vanilla tags to determine the tool type
        if (state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)) {
            missingTools.add(net.minecraft.world.item.Items.IRON_PICKAXE);
        } else if (state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE)) {
            missingTools.add(net.minecraft.world.item.Items.IRON_AXE);
        } else if (state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL)) {
            missingTools.add(net.minecraft.world.item.Items.IRON_SHOVEL);
        } else if (state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_HOE)) {
            missingTools.add(net.minecraft.world.item.Items.IRON_HOE);
        }
    }

    public boolean hasRejected() {
        return !rejected.isEmpty();
    }

    public boolean hasMissingTools() {
        return !missingTools.isEmpty();
    }

    public int getTotalBreakable() {
        int sum = 0;
        for (int v : breakable.values()) sum += v;
        return sum;
    }

    public int getTotalRejected() {
        int sum = 0;
        for (int v : rejected.values()) sum += v;
        return sum;
    }
}

