package nl.requios.effortlessbuilding.utilities;

import net.minecraft.core.BlockPos;
import nl.requios.effortlessbuilding.Constants;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class BlockSet extends LinkedHashMap<BlockPos, BlockEntry> implements Iterable<BlockEntry> {
    public static boolean logging = true;

    public BlockPos firstPos;
    public BlockPos lastPos;
    public boolean skipFirst;

    public BlockSet() {
        super();
    }

    public BlockSet(BlockSet blockSet) {
        super(blockSet);
        this.firstPos = blockSet.firstPos;
        this.lastPos = blockSet.lastPos;
        this.skipFirst = blockSet.skipFirst;
    }

    public BlockSet(List<BlockEntry> blockEntries, BlockPos firstPos, BlockPos lastPos, boolean skipFirst) {
        super();
        for (BlockEntry blockEntry : blockEntries) {
            add(blockEntry);
        }
        this.firstPos = firstPos;
        this.lastPos = lastPos;
        this.skipFirst = skipFirst;
    }

    public void setStartPos(BlockEntry startPos) {
        clear();
        add(startPos);
        firstPos = startPos.blockPos;
        lastPos = startPos.blockPos;
    }

    public void add(BlockEntry blockEntry) {
        if (!containsKey(blockEntry.blockPos)) {
            put(blockEntry.blockPos, blockEntry);
        } else {
            if (logging) Constants.LOG.debug("BlockSet already contains block at {}", blockEntry.blockPos);
        }
    }

    /** Removes entries beyond the given limit, keeping insertion order (first N entries). */
    public void truncate(int maxSize) {
        if (size() <= maxSize) return;
        var iter = keySet().iterator();
        int count = 0;
        while (iter.hasNext()) {
            iter.next();
            count++;
            if (count > maxSize) iter.remove();
        }
    }

    /** Re-orders entries by distance to {@link #firstPos} (closest first). No-op if firstPos is null. */
    public void sortByDistance() {
        if (firstPos == null) return;
        List<Map.Entry<BlockPos, BlockEntry>> entries = new ArrayList<>(entrySet());
        entries.sort(Comparator.comparingDouble(e -> e.getKey().distSqr(firstPos)));
        clear();
        for (var entry : entries) {
            put(entry.getKey(), entry.getValue());
        }
    }

    /** Returns only entries with {@link BlockStatus#VALID} status. */
    public List<Map.Entry<BlockPos, BlockEntry>> validEntries() {
        return entrySet().stream()
                .filter(e -> e.getValue().isValid())
                .collect(Collectors.toList());
    }

    /** Returns only entries that have been marked with a rejection reason. */
    public List<Map.Entry<BlockPos, BlockEntry>> rejectedEntries() {
        return entrySet().stream()
                .filter(e -> !e.getValue().isValid())
                .collect(Collectors.toList());
    }

    /** Returns positions of valid entries only. */
    public List<BlockPos> validPositions() {
        List<BlockPos> result = new ArrayList<>();
        for (var entry : entrySet()) {
            if (entry.getValue().isValid()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /** Returns the count of valid entries. */
    public int validCount() {
        int count = 0;
        for (BlockEntry entry : values()) {
            if (entry.isValid()) count++;
        }
        return count;
    }

    /** Resets all entry statuses back to VALID. Called before re-running the pipeline for preview. */
    public void resetAllStatuses() {
        for (BlockEntry entry : values()) {
            entry.resetStatus();
        }
    }

    @NotNull
    @Override
    public Iterator<BlockEntry> iterator() {
        return this.values().iterator();
    }
}
