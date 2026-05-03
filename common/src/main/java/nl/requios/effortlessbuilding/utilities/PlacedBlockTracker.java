package nl.requios.effortlessbuilding.utilities;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * Tracks block positions placed by each player during the current session.
 * Survival players may break (via mod build-mode breaking) any position that
 * is in this set, receiving the drops into inventory instead of the world.
 *
 * <p>Server-side only. Cleared when a player disconnects.
 * Capped at {@link #MAX_POSITIONS_PER_PLAYER} with FIFO eviction.
 */
public class PlacedBlockTracker {

    private static final int MAX_POSITIONS_PER_PLAYER = 10_000;

    /**
     * Fixed UUID used for client-side tracking (the client only ever tracks its own placements).
     * Uses a specific non-zero UUID to avoid collisions with real player UUIDs (including
     * dev environments that may use all-zeros).
     */
    public static final UUID CLIENT_ID = new UUID(0xEFF0B1E55L, 0xC11EA7L);

    // UUID -> (dimension -> insertion-ordered set of positions)
    private static final Map<UUID, Map<ResourceKey<Level>, LinkedHashSet<BlockPos>>> data = new HashMap<>();

    /**
     * Client-side convenience: track positions for the local player.
     */
    public static void clientTrackAll(ResourceKey<Level> dimension, Collection<BlockPos> positions) {
        trackAll(CLIENT_ID, dimension, positions);
    }

    /**
     * Client-side convenience: check if a position is tracked for the local player.
     */
    public static boolean clientIsTracked(ResourceKey<Level> dimension, BlockPos pos) {
        return isTracked(CLIENT_ID, dimension, pos);
    }

    /**
     * Clear client-side tracking data (e.g. on disconnect).
     */
    public static void clearClient() {
        clearPlayer(CLIENT_ID);
    }

    /**
     * Mark positions as placed by the given player in the given dimension.
     */
    public static void trackAll(UUID playerId, ResourceKey<Level> dimension, Collection<BlockPos> positions) {
        if (positions.isEmpty()) return;
        LinkedHashSet<BlockPos> set = getOrCreate(playerId, dimension);
        for (BlockPos pos : positions) {
            set.add(pos.immutable());
        }
        evict(playerId, dimension);
    }

    /**
     * Check if a position is tracked for the given player and dimension.
     */
    public static boolean isTracked(UUID playerId, ResourceKey<Level> dimension, BlockPos pos) {
        Map<ResourceKey<Level>, LinkedHashSet<BlockPos>> dimMap = data.get(playerId);
        if (dimMap == null) return false;
        LinkedHashSet<BlockPos> set = dimMap.get(dimension);
        return set != null && set.contains(pos);
    }

    /**
     * Side-agnostic check: on the server uses the player's UUID, on the client uses CLIENT_ID.
     * This allows the ConstraintSystem to run identically on both sides.
     */
    public static boolean isTrackedAnySide(net.minecraft.world.entity.player.Player player, Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return clientIsTracked(level.dimension(), pos);
        } else {
            return isTracked(player.getUUID(), level.dimension(), pos);
        }
    }

    /**
     * Clear all tracked data for a player (call on disconnect).
     */
    public static void clearPlayer(UUID playerId) {
        data.remove(playerId);
    }

    // -------------------------------------------------------------------------

    private static LinkedHashSet<BlockPos> getOrCreate(UUID playerId, ResourceKey<Level> dimension) {
        return data.computeIfAbsent(playerId, k -> new HashMap<>())
                   .computeIfAbsent(dimension, k -> new LinkedHashSet<>());
    }

    /**
     * If total positions across all dimensions exceed the cap, remove oldest entries.
     */
    private static void evict(UUID playerId, ResourceKey<Level> dimension) {
        Map<ResourceKey<Level>, LinkedHashSet<BlockPos>> dimMap = data.get(playerId);
        if (dimMap == null) return;

        // Count total
        int total = 0;
        for (LinkedHashSet<BlockPos> s : dimMap.values()) total += s.size();

        if (total <= MAX_POSITIONS_PER_PLAYER) return;

        // Remove oldest from the dimension we just added to (simplest FIFO)
        LinkedHashSet<BlockPos> set = dimMap.get(dimension);
        if (set == null) return;
        Iterator<BlockPos> it = set.iterator();
        while (total > MAX_POSITIONS_PER_PLAYER && it.hasNext()) {
            it.next();
            it.remove();
            total--;
        }
    }
}
