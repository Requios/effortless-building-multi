package nl.requios.effortlessbuilding.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DataSlot;
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
    private final int[] ratios = new int[RandomizerToolData.SLOT_COUNT];

    public RandomizerMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, playerInventory.player.getMainHandItem());
    }

    public RandomizerMenu(int containerId, Inventory playerInventory, ItemStack tool) {
        super(ModMenus.RANDOMIZER, containerId);
        this.tool = tool;

        List<ItemStack> configured = RandomizerToolData.getStacks(tool);
        List<Integer> configuredRatios = RandomizerToolData.getRatios(tool);
        for (int i = 0; i < RandomizerToolData.SLOT_COUNT; i++) {
            ghostSlots.setItem(i, configured.get(i));
            ratios[i] = configuredRatios.get(i);
            addSlot(new GhostSlot(ghostSlots, i, 8 + i * 18, 36));
            final int slot = i;
            addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    return ratios[slot];
                }

                @Override
                public void set(int value) {
                    ratios[slot] = Math.max(0, value);
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        8 + column * 18, 68 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 126));
        }
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (slotId >= 0 && slotId < GHOST_SLOT_END) {
            ItemStack source = clickType == ContainerInput.SWAP && button >= 0 && button < 9
                    ? player.getInventory().getItem(button)
                    : getCarried();
            if (isSafePaletteBlock(source)) {
                ghostSlots.setItem(slotId, source.copyWithCount(1));
                if (ratios[slotId] == 0) ratios[slotId] = 1;
            } else if (clickType == ContainerInput.PICKUP && source.isEmpty()) {
                ghostSlots.setItem(slotId, ItemStack.EMPTY);
                if (ratios[slotId] == 1) ratios[slotId] = 0;
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
                && isSafePaletteBlock(stack)) {
            for (int i = 0; i < GHOST_SLOT_END; i++) {
                if (ghostSlots.getItem(i).isEmpty()) {
                    ghostSlots.setItem(i, stack.copyWithCount(1));
                    ratios[i] = 1;
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

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= GHOST_SLOT_END * 2) return false;

        int slot = id % GHOST_SLOT_END;
        int delta = id < GHOST_SLOT_END ? 1 : -1;
        if (delta < 0 && ratios[slot] == 0) return false;
        if (delta > 0 && ratios[slot] == Integer.MAX_VALUE) return false;

        ratios[slot] += delta;
        savePalette(player);
        return true;
    }

    public int getRatio(int slot) {
        return slot >= 0 && slot < GHOST_SLOT_END ? ratios[slot] : 0;
    }

    /** Updates the client display immediately while the server menu action is in flight. */
    public boolean adjustRatioClient(int slot, int delta) {
        if (slot < 0 || slot >= GHOST_SLOT_END || delta == 0) return false;
        if (delta < 0 && ratios[slot] == 0) return false;
        if (delta > 0 && ratios[slot] == Integer.MAX_VALUE) return false;
        ratios[slot] += delta > 0 ? 1 : -1;
        return true;
    }

    private void savePalette(Player player) {
        List<Item> items = new ArrayList<>(RandomizerToolData.SLOT_COUNT);
        for (int i = 0; i < RandomizerToolData.SLOT_COUNT; i++) {
            ItemStack stack = ghostSlots.getItem(i);
            items.add(stack.isEmpty() ? Items.AIR : stack.getItem());
        }
        List<Integer> configuredRatios = new ArrayList<>(RandomizerToolData.SLOT_COUNT);
        for (int ratio : ratios) configuredRatios.add(ratio);
        RandomizerToolData.setConfiguration(tool, items, configuredRatios);
        player.getInventory().setChanged();
        broadcastChanges();
    }

    /**
     * The randomizer persists item identifiers, rather than complete item stacks.
     * Reject component-bearing stacks so their contents or mod data cannot silently
     * disappear when they are used as a palette entry.
     */
    private static boolean isSafePaletteBlock(ItemStack stack) {
        return stack.getItem() instanceof BlockItem
                && BuildPipeline.isBuildTriggerItem(stack)
                && stack.getComponentsPatch().isEmpty();
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
