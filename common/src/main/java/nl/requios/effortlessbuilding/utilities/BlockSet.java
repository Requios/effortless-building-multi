package nl.requios.effortlessbuilding.utilities;

import net.minecraft.core.BlockPos;
import nl.requios.effortlessbuilding.Constants;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

public class BlockSet extends HashMap<BlockPos, BlockEntry> implements Iterable<BlockEntry> {
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

    public HashSet<BlockPos> getCoordinates() {
        return new HashSet<>(keySet());
    }

    public BlockEntry getFirstBlockEntry() {
        return get(firstPos);
    }

    public BlockEntry getLastBlockEntry() {
        return get(lastPos);
    }

    @NotNull
    @Override
    public Iterator<BlockEntry> iterator() {
        return this.values().iterator();
    }
}
