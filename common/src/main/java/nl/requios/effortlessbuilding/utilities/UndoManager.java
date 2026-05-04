package nl.requios.effortlessbuilding.utilities;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import nl.requios.effortlessbuilding.Constants;

import java.util.*;

/**
 * Server-side per-player undo/redo stacks.
 * Each entry records the block positions with their old and new states.
 * In survival, undo/redo also handles inventory: undoing a placement gives
 * items back; undoing a break consumes items (or skips if unavailable).
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
     * In survival, undoing a placement (block→air) gives items back; undoing a break (air→block)
     * consumes items from inventory (positions without enough items are skipped).
     */
    public static int undo(ServerPlayer player) {
        UUID id = player.getUUID();
        Deque<UndoEntry> undoStack = undoStacks.get(id);
        if (undoStack == null || undoStack.isEmpty()) return -1;

        UndoEntry entry = undoStack.pop();
        ServerLevel level = player.level().getServer().getLevel(entry.dimension());
        if (level == null) {
            Constants.LOG.warn("[EffortlessBuilding] Cannot undo: dimension {} no longer loaded", entry.dimension());
            return -1;
        }

        boolean creative = player.isCreative();
        int restored = 0;

        for (var e : entry.changes().entrySet()) {
            BlockPos pos = e.getKey();
            BlockChange change = e.getValue();
            BlockState oldState = change.oldState();
            BlockState newState = change.newState();
            BlockState currentState = level.getBlockState(pos);

            if (!creative) {
                // Undoing a placement: newState is the placed block, oldState was air/replaceable
                // → remove the block and give items back (only if the block is still there)
                if (!newState.isAir() && oldState.canBeReplaced()) {
                    if (!currentState.equals(newState)) continue; // someone changed it, skip
                    level.setBlock(pos, oldState, 3);
                    giveBlockItem(player, newState);
                    restored++;
                    continue;
                }

                // Undoing a break: oldState was a block, newState is air
                // → need to consume the item to restore the block
                // Only restore if the position is still air (nobody built there since)
                if (!oldState.isAir() && newState.isAir()) {
                    if (!currentState.isAir()) continue; // someone placed something here, skip
                    Item requiredItem = oldState.getBlock().asItem();
                    if (requiredItem != net.minecraft.world.item.Items.AIR
                            && InventoryHelper.findTotalItemsInInventory(player, requiredItem) > 0) {
                        InventoryHelper.consumeItems(player, requiredItem, 1);
                        level.setBlock(pos, oldState, 3);
                        PlacedBlockTracker.trackAll(player.getUUID(), entry.dimension(), List.of(pos));
                        restored++;
                    }
                    continue;
                }
            }

            // Creative or replace-mode changes: just restore
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
     * In survival, redoing a placement consumes items; redoing a break gives items back.
     */
    public static int redo(ServerPlayer player) {
        UUID id = player.getUUID();
        Deque<UndoEntry> redoStack = redoStacks.get(id);
        if (redoStack == null || redoStack.isEmpty()) return -1;

        UndoEntry entry = redoStack.pop();
        ServerLevel level = player.level().getServer().getLevel(entry.dimension());
        if (level == null) {
            Constants.LOG.warn("[EffortlessBuilding] Cannot redo: dimension {} no longer loaded", entry.dimension());
            return -1;
        }

        boolean creative = player.isCreative();
        int reapplied = 0;

        for (var e : entry.changes().entrySet()) {
            BlockPos pos = e.getKey();
            BlockChange change = e.getValue();
            BlockState oldState = change.oldState();
            BlockState newState = change.newState();
            BlockState currentState = level.getBlockState(pos);

            if (!creative) {
                // Redoing a placement: need to consume the item to place the block
                if (!newState.isAir() && oldState.canBeReplaced()) {
                    if (!currentState.equals(oldState)) continue;
                    Item requiredItem = newState.getBlock().asItem();
                    if (requiredItem != net.minecraft.world.item.Items.AIR
                            && InventoryHelper.findTotalItemsInInventory(player, requiredItem) > 0) {
                        InventoryHelper.consumeItems(player, requiredItem, 1);
                        level.setBlock(pos, newState, 3);
                        PlacedBlockTracker.trackAll(player.getUUID(), entry.dimension(), List.of(pos));
                        reapplied++;
                    }
                    continue;
                }

                // Redoing a break: remove the block and give items back
                if (!oldState.isAir() && newState.isAir()) {
                    if (!currentState.equals(oldState)) continue;
                    level.setBlock(pos, newState, 3);
                    giveBlockItem(player, oldState);
                    reapplied++;
                    continue;
                }
            }

            // Creative or replace-mode changes: just reapply
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

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Give the player one block item corresponding to the given block state.
     * Goes to inventory; overflow drops at feet.
     */
    private static void giveBlockItem(ServerPlayer player, BlockState state) {
        Item item = state.getBlock().asItem();
        if (item != net.minecraft.world.item.Items.AIR) {
            InventoryHelper.giveOrDropItems(player, item, 1);
        }
    }
}
