package nl.requios.effortlessbuilding.utilities;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import nl.requios.effortlessbuilding.Constants;

import java.util.*;

/**
 * Server-side per-player undo/redo stacks.
 * Each entry records the block positions with their old and new states.
 */
public class UndoManager {

    private static final int MAX_STACK_SIZE = 50;

    private static final Map<UUID, Deque<UndoEntry>> undoStacks = new HashMap<>();
    private static final Map<UUID, Deque<UndoEntry>> redoStacks = new HashMap<>();

    /**
     * A single undo-able operation: a set of block changes in a specific dimension.
     */
    public record BlockChange(BlockState oldState, BlockState newState) {}

    public record UndoEntry(ResourceKey<Level> dimension, Map<BlockPos, BlockChange> changes) {}

    // -------------------------------------------------------------------------
    // Recording
    // -------------------------------------------------------------------------

    /**
     * Record a new operation. Clears the redo stack.
     */
    public static void recordOperation(ServerPlayer player, ResourceKey<Level> dimension, Map<BlockPos, BlockChange> changes) {
        if (changes.isEmpty()) return;

        UUID id = player.getUUID();
        Deque<UndoEntry> undoStack = undoStacks.computeIfAbsent(id, k -> new ArrayDeque<>());
        undoStack.push(new UndoEntry(dimension, changes));
        if (undoStack.size() > MAX_STACK_SIZE) {
            // Remove the oldest entry (bottom of the stack)
            ((ArrayDeque<UndoEntry>) undoStack).removeLast();
        }

        // New operation invalidates redo history
        redoStacks.computeIfAbsent(id, k -> new ArrayDeque<>()).clear();
    }

    // -------------------------------------------------------------------------
    // Undo / Redo
    // -------------------------------------------------------------------------

    /**
     * Undo the most recent operation. Returns the number of blocks restored, or -1 if nothing to undo.
     */
    public static int undo(ServerPlayer player) {
        UUID id = player.getUUID();
        Deque<UndoEntry> undoStack = undoStacks.get(id);
        if (undoStack == null || undoStack.isEmpty()) return -1;

        UndoEntry entry = undoStack.pop();
        ServerLevel level = player.server.getLevel(entry.dimension());
        if (level == null) {
            Constants.LOG.warn("[EffortlessBuilding] Cannot undo: dimension {} no longer loaded", entry.dimension());
            return -1;
        }

        int restored = 0;
        for (var e : entry.changes().entrySet()) {
            BlockPos pos = e.getKey();
            BlockState oldState = e.getValue().oldState();
            level.setBlock(pos, oldState, 3);
            restored++;
        }

        // Push to redo stack
        Deque<UndoEntry> redoStack = redoStacks.computeIfAbsent(id, k -> new ArrayDeque<>());
        redoStack.push(entry);
        if (redoStack.size() > MAX_STACK_SIZE) {
            ((ArrayDeque<UndoEntry>) redoStack).removeLast();
        }

        return restored;
    }

    /**
     * Redo the most recently undone operation. Returns the number of blocks re-applied, or -1 if nothing to redo.
     */
    public static int redo(ServerPlayer player) {
        UUID id = player.getUUID();
        Deque<UndoEntry> redoStack = redoStacks.get(id);
        if (redoStack == null || redoStack.isEmpty()) return -1;

        UndoEntry entry = redoStack.pop();
        ServerLevel level = player.server.getLevel(entry.dimension());
        if (level == null) {
            Constants.LOG.warn("[EffortlessBuilding] Cannot redo: dimension {} no longer loaded", entry.dimension());
            return -1;
        }

        int reapplied = 0;
        for (var e : entry.changes().entrySet()) {
            BlockPos pos = e.getKey();
            BlockState newState = e.getValue().newState();
            level.setBlock(pos, newState, 3);
            reapplied++;
        }

        // Push back to undo stack
        Deque<UndoEntry> undoStack = undoStacks.computeIfAbsent(id, k -> new ArrayDeque<>());
        undoStack.push(entry);
        if (undoStack.size() > MAX_STACK_SIZE) {
            ((ArrayDeque<UndoEntry>) undoStack).removeLast();
        }

        return reapplied;
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    /**
     * Clear undo/redo stacks for a player (call on disconnect).
     */
    public static void clearPlayer(UUID playerId) {
        undoStacks.remove(playerId);
        redoStacks.remove(playerId);
    }
}

