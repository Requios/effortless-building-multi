package nl.requios.effortlessbuilding.utilities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import nl.requios.effortlessbuilding.compat.ae2.AE2Integration;
import nl.requios.effortlessbuilding.network.PacketHandler;
import nl.requios.effortlessbuilding.network.QueryAE2CountC2SPacket;

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

    /** How many of each item are available on the AE2 ME network. */
    public Map<Item, Integer> fromNetwork = new HashMap<>();

    /**
     * True if the player has an AE2 wireless terminal with a linked WAP position.
     * Does NOT verify that the terminal is in range or the grid is reachable
     * (getLinkedGrid requires ServerLevel which isn't available on the client).
     */
    public boolean ae2Linked = false;

    /** Set of positions that cannot be placed due to insufficient items. */
    public Set<BlockPos> missingPositions = new HashSet<>();

    public void initialize() {
        total.clear();
        inInventory.clear();
        placed.clear();
        missing.clear();
        fromNetwork.clear();
        ae2Linked = false;
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

        Map<Item, List<BlockPos>> requirements = new LinkedHashMap<>();
        requirements.put(heldItem, new ArrayList<>(positions));
        computeRequirements(player, requirements, isCreative);
    }

    /** Computes per-item availability for block sets populated by the randomizer pipeline stage. */
    public void compute(Player player, BlockSet blocks, boolean isCreative) {
        initialize();
        Map<Item, List<BlockPos>> requirements = new LinkedHashMap<>();
        for (var mapEntry : blocks.validEntries()) {
            Item item = mapEntry.getValue().item;
            if (item != null) {
                requirements.computeIfAbsent(item, ignored -> new ArrayList<>()).add(mapEntry.getKey());
            }
        }
        computeRequirements(player, requirements, isCreative);
    }

    private void computeRequirements(Player player, Map<Item, List<BlockPos>> requirements, boolean isCreative) {
        for (var requirement : requirements.entrySet()) {
            computeItem(player, requirement.getKey(), requirement.getValue(), isCreative);
        }
    }

    private void computeItem(Player player, Item heldItem, List<BlockPos> positions, boolean isCreative) {
        if (heldItem == null || positions.isEmpty()) return;

        int count = positions.size();
        total.put(heldItem, count);

        if (isCreative) {
            placed.put(heldItem, count);
            return;
        }

        // Count items in vanilla inventory
        int have = InventoryHelper.findTotalItemsInInventory(player, heldItem);
        inInventory.put(heldItem, have);

        // Check AE2 network for additional items
        int networkCount = AE2Integration.countOnNetwork(player, heldItem);
        boolean hasLinkedTerminal = AE2Integration.hasLinkedTerminal(player);
        ae2Linked = hasLinkedTerminal;
        boolean isClient = player.level().isClientSide();

        if (networkCount > 0) {
            // Server-side: real count from getLinkedGrid()
            fromNetwork.put(heldItem, networkCount);
        } else if (hasLinkedTerminal && isClient) {
            // Client-side: getLinkedGrid() doesn't work (needs ServerLevel).
            // Check if we have a server-synced cached count.
            int cached = AE2Integration.getCachedCount(heldItem);
            if (cached >= 0) {
                fromNetwork.put(heldItem, cached);
            } else {
                // No cached count yet — send a query packet to the server.
                // Use optimistic assumption for this frame; next frame will have real data.
                PacketHandler.sendToServer(new QueryAE2CountC2SPacket(heldItem));
                fromNetwork.put(heldItem, Math.max(0, count - have));
            }
        }

        // Total available = inventory + AE2
        int totalAvailable = have + fromNetwork.getOrDefault(heldItem, 0);

        int canPlace = Math.min(count, totalAvailable);
        placed.put(heldItem, canPlace);

        if (count > totalAvailable) {
            missing.put(heldItem, count - totalAvailable);

            // Sort positions by distance to player (closest first) so nearby blocks are placed first
            List<BlockPos> posList = new ArrayList<>(positions);
            BlockPos playerPos = player.blockPosition();
            posList.sort(Comparator.comparingDouble(pos -> pos.distSqr(playerPos)));

            // Mark the farthest (count - totalAvailable) positions as missing
            int missingCount = count - totalAvailable;
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
