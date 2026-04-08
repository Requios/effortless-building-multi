package nl.requios.effortlessbuilding.utilities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import java.util.*;

/**
 * Tracks how many blocks the player wants to place vs how many they have.
 * Both client (for preview) and server (for placement) can use instances of this.
 */
public class ItemUsageTracker {

    /** How many of each item we want to place in total. */
    public Map<Item, Integer> total = new LinkedHashMap<>();

    /** How many of each item the player has in inventory. */
    public Map<Item, Integer> inInventory = new HashMap<>();

    /** How many of each item we can actually place (min of total, available). */
    public Map<Item, Integer> placed = new HashMap<>();

    /** How many of each item are missing from inventory. */
    public Map<Item, Integer> missing = new HashMap<>();

    /** Set of positions that cannot be placed due to insufficient items. */
    public Set<BlockPos> missingPositions = new HashSet<>();

    public void initialize() {
        total.clear();
        inInventory.clear();
        placed.clear();
        missing.clear();
        missingPositions.clear();
    }

    /**
     * Computes item usage for a block set given the player's current inventory.
     *
     * @param player    the player
     * @param positions the positions to be placed
     * @param heldItem  the item the player is holding (determines what gets placed)
     * @param isCreative true if the player is in creative mode
     */
    public void compute(Player player, Collection<BlockPos> positions, Item heldItem, boolean isCreative) {
        initialize();

        if (heldItem == null || positions.isEmpty()) return;

        int count = positions.size();
        total.put(heldItem, count);

        if (isCreative) {
            placed.put(heldItem, count);
            return;
        }

        int have = InventoryHelper.findTotalItemsInInventory(player, heldItem);
        inInventory.put(heldItem, have);

        int canPlace = Math.min(count, have);
        placed.put(heldItem, canPlace);

        if (count > have) {
            missing.put(heldItem, count - have);

            // Mark the last (count - have) positions as missing
            int missingCount = count - have;
            List<BlockPos> posList = new ArrayList<>(positions);
            for (int i = posList.size() - missingCount; i < posList.size(); i++) {
                missingPositions.add(posList.get(i));
            }
        }
    }

    public int getMissingCount(Item item) {
        return missing.getOrDefault(item, 0);
    }

    public int getTotalMissing() {
        int sum = 0;
        for (int v : missing.values()) sum += v;
        return sum;
    }

    public boolean hasMissing() {
        return !missing.isEmpty();
    }
}

