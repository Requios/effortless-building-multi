package nl.requios.effortlessbuilding.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import nl.requios.effortlessbuilding.item.RandomizerToolData;

import java.util.ArrayList;
import java.util.List;

/** Server-backed inventory menu with nine non-consuming block palette slots. */
public class RandomizerMenu extends AbstractContainerMenu {
    private static final int GHOST_SLOT_END = 9;
    private static final int PLAYER_MAIN_END = 36;
    private static final int PLAYER_SLOT_END = 45;

    private final Container ghostSlots = new SimpleContainer(RandomizerToolData.SLOT_COUNT);
    private final ItemStack tool;

    public RandomizerMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, playerInventory.player.getMainHandItem());
    }

    public RandomizerMenu(int containerId, Inventory playerInventory, ItemStack tool) {
        super(ModMenus.RANDOMIZER, containerId);
        this.tool = tool;

        List<ItemStack> configured = RandomizerToolData.getStacks(tool);
        for (int i = 0; i < RandomizerToolData.SLOT_COUNT; i++) {
            ghostSlots.setItem(i, configured.get(i));
            addSlot(new GhostSlot(ghostSlots, i, 8 + i * 18, 20));
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        8 + column * 18, 51 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 109));
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < GHOST_SLOT_END) {
            ItemStack source = clickType == ClickType.SWAP && button >= 0 && button < 9
                    ? player.getInventory().getItem(button)
                    : getCarried();
            if (source.getItem() instanceof BlockItem && BuildPipeline.isBuildTriggerItem(source)) {
                ghostSlots.setItem(slotId, source.copyWithCount(1));
            } else if (clickType == ClickType.PICKUP && source.isEmpty()) {
                ghostSlots.setItem(slotId, ItemStack.EMPTY);
            }
            savePalette(player);
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        if (index >= GHOST_SLOT_END
                && stack.getItem() instanceof BlockItem
                && BuildPipeline.isBuildTriggerItem(stack)) {
            for (int i = 0; i < GHOST_SLOT_END; i++) {
                if (ghostSlots.getItem(i).isEmpty()) {
                    ghostSlots.setItem(i, stack.copyWithCount(1));
                    savePalette(player);
                    return ItemStack.EMPTY;
                }
            }
        }

        ItemStack original = stack.copy();
        if (index >= GHOST_SLOT_END && index < PLAYER_MAIN_END) {
            if (!moveItemStackTo(stack, PLAYER_MAIN_END, PLAYER_SLOT_END, false)) return ItemStack.EMPTY;
        } else if (index >= PLAYER_MAIN_END && index < PLAYER_SLOT_END) {
            if (!moveItemStackTo(stack, GHOST_SLOT_END, PLAYER_MAIN_END, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    private void savePalette(Player player) {
        List<Item> items = new ArrayList<>(RandomizerToolData.SLOT_COUNT);
        for (int i = 0; i < RandomizerToolData.SLOT_COUNT; i++) {
            ItemStack stack = ghostSlots.getItem(i);
            items.add(stack.isEmpty() ? Items.AIR : stack.getItem());
        }
        RandomizerToolData.setItems(tool, items);
        player.getInventory().setChanged();
        broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    private static final class GhostSlot extends Slot {
        private GhostSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
