package nl.requios.effortlessbuilding.utilities;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Server-safe inventory helpers for counting, consuming, and giving items.
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
}
