package nl.requios.effortlessbuilding.utilities;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import nl.requios.effortlessbuilding.compat.ae2.AE2Integration;

/**
 * Server-safe inventory helpers for counting, consuming, and giving items.
 *
 * <p>Methods suffixed with {@code WithNetwork} also consult the player's
 * AE2 ME network (via a wireless terminal) as a supplemental item source.
 */
public class InventoryHelper {

    /**
     * Counts how many of the given item the player has across their entire inventory,
     * including main hand, off hand, and all inventory slots.
     */
    public static int findTotalItemsInInventory(Player player, Item item) {
        int total = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * Consumes {@code count} items from the player's inventory.
     * Drains the main-hand stack first, then searches the rest of the inventory.
     *
     * @return the number of items actually consumed (may be less than count if inventory runs out)
     */
    public static int consumeItems(Player player, Item item, int count) {
        if (count <= 0) return 0;
        int remaining = count;
        Inventory inv = player.getInventory();

        // Drain main hand first
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.is(item)) {
            int take = Math.min(remaining, mainHand.getCount());
            mainHand.shrink(take);
            remaining -= take;
            if (remaining <= 0) return count;
        }

        // Then search the rest of inventory
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
                if (remaining <= 0) return count;
            }
        }

        return count - remaining;
    }

    /**
     * Gives {@code count} items to the player. Adds to inventory first;
     * any overflow is dropped at the player's feet.
     */
    public static void giveOrDropItems(Player player, Item item, int count) {
        if (count <= 0) return;
        int remaining = count;
        while (remaining > 0) {
            int batchSize = Math.min(remaining, item.getDefaultMaxStackSize());
            ItemStack stack = new ItemStack(item, batchSize);
            if (!player.getInventory().add(stack)) {
                // Inventory full — drop remainder at feet
                ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack);
                drop.setNoPickUpDelay();
                player.level().addFreshEntity(drop);
            }
            remaining -= batchSize;
        }
    }

    /**
     * Checks if the player has a tool in their inventory that can correctly harvest the given block state.
     * A block that doesn't require a correct tool returns true immediately.
     */
    public static boolean hasCorrectToolForBlock(Player player, BlockState state) {
        if (!state.requiresCorrectToolForDrops()) return true;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.isCorrectToolForDrops(state)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the best tool in the player's inventory for the given block state.
     * Returns the tool stack (with enchantments etc.) so it can be used for drop calculations.
     * Falls back to the main hand item if no specific tool is found.
     */
    public static ItemStack findCorrectTool(Player player, BlockState state) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.isCorrectToolForDrops(state)) {
                return stack;
            }
        }
        return player.getMainHandItem();
    }

    /**
     * Finds the first tool in the player's inventory that can harvest the given block state
     * and damages it by 1. If no specific tool is needed, damages the main hand item (if damageable).
     */
    public static void damageCorrectTool(Player player, BlockState state) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
        ServerLevel serverLevel = serverPlayer.serverLevel();

        Inventory inv = player.getInventory();
        // First try to find a tool that matches the block
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.isDamageableItem() && stack.isCorrectToolForDrops(state)) {
                stack.hurtAndBreak(1, serverLevel, serverPlayer, item -> {});
                return;
            }
        }
        // If no specific tool found but block doesn't require one, damage main hand if it's a tool
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty() && mainHand.isDamageableItem()) {
            mainHand.hurtAndBreak(1, serverLevel, serverPlayer, item -> {});
        }
    }

    // -------------------------------------------------------------------------
    // AE2 network-aware inventory helpers
    // -------------------------------------------------------------------------

    /**
     * Counts total items available, including both the player's physical inventory
     * and any items on the AE2 ME network reachable via a wireless terminal.
     *
     * @return inventory count + AE2 network count
     */
    public static int findTotalItemsWithNetwork(Player player, Item item) {
        int inventoryCount = findTotalItemsInInventory(player, item);
        int networkCount = AE2Integration.countOnNetwork(player, item);
        return inventoryCount + networkCount;
    }

    /**
     * If the player has an AE2 wireless terminal linked to an ME network, extracts
     * up to {@code needed} items from that network. The items are removed from
     * digital storage — no physical ItemStack is created (they are consumed as
     * building material).
     *
     * @return number of items actually extracted from the network
     */
    public static int supplementFromNetwork(Player player, Item item, int needed) {
        if (needed <= 0) return 0;
        return AE2Integration.extractFromNetwork(player, item, needed);
    }

    /**
     * Attempts to refill the player's main-hand stack from the AE2 network.
     * Only works if the held item is a block/item that exists on the network.
     *
     * @return number of items restocked
     */
    public static int restockFromNetwork(Player player) {
        return AE2Integration.restockMainHand(player);
    }
}
