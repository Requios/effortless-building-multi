package nl.requios.effortlessbuilding.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import nl.requios.effortlessbuilding.item.RandomizerToolData;
import nl.requios.effortlessbuilding.network.PacketHandler;
import nl.requios.effortlessbuilding.network.UpdateRandomizerC2SPacket;

import java.util.ArrayList;
import java.util.List;

/** Client-only fake-slot editor. It never mutates the player's inventory. */
public class RandomizerScreen extends Screen {
    private static final int SLOT = 18;
    private static final int PANEL_W = 194;
    private static final int PANEL_H = 150;

    private final ItemStack tool;
    private final List<Item> palette;
    private Item selected = Items.AIR;
    private int left;
    private int top;

    public RandomizerScreen(ItemStack tool) {
        super(Component.translatable("effortlessbuilding.screen.randomizer"));
        this.tool = tool;
        this.palette = new ArrayList<>(RandomizerToolData.getItems(tool));
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left, top, left + PANEL_W, top + PANEL_H, 0xE0101010);
        graphics.fill(left + 1, top + 1, left + PANEL_W - 1, top + PANEL_H - 1, 0xE0282828);
        graphics.drawString(font, title, left + 10, top + 8, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("effortlessbuilding.screen.randomizer.hint"),
                left + 10, top + 22, 0xA0A0A0, false);

        int fakeY = top + 38;
        int slotsX = left + 16;
        for (int i = 0; i < RandomizerToolData.SLOT_COUNT; i++) {
            drawSlot(graphics, slotsX + i * SLOT, fakeY, itemStack(palette.get(i)), mouseX, mouseY, true);
        }

        int inventoryY = top + 70;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int inventoryIndex = 9 + row * 9 + col;
                drawSlot(graphics, slotsX + col * SLOT, inventoryY + row * SLOT,
                        minecraft.player.getInventory().getItem(inventoryIndex), mouseX, mouseY, false);
            }
        }
        int hotbarY = inventoryY + 58;
        for (int col = 0; col < 9; col++) {
            drawSlot(graphics, slotsX + col * SLOT, hotbarY,
                    minecraft.player.getInventory().getItem(col), mouseX, mouseY, false);
        }

        if (selected != Items.AIR) {
            graphics.renderItem(new ItemStack(selected), mouseX - 8, mouseY - 8);
        }

        ItemStack hovered = hoveredStack(mouseX, mouseY);
        if (!hovered.isEmpty()) graphics.renderTooltip(font, hovered, mouseX, mouseY);
    }

    private void drawSlot(GuiGraphics graphics, int x, int y, ItemStack stack,
                          int mouseX, int mouseY, boolean fake) {
        int border = inside(mouseX, mouseY, x, y) ? 0xFFFFFFFF : 0xFF8B8B8B;
        graphics.fill(x, y, x + SLOT, y + SLOT, border);
        graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, fake ? 0xFF343C44 : 0xFF202020);
        if (!stack.isEmpty()) graphics.renderItem(stack, x + 1, y + 1);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int slotsX = left + 16;
        int fakeY = top + 38;
        for (int i = 0; i < RandomizerToolData.SLOT_COUNT; i++) {
            if (inside(mouseX, mouseY, slotsX + i * SLOT, fakeY)) {
                if (button == 1) {
                    palette.set(i, Items.AIR);
                } else if (selected != Items.AIR) {
                    palette.set(i, selected);
                } else if (palette.get(i) != Items.AIR) {
                    selected = palette.get(i);
                }
                save();
                return true;
            }
        }

        int inventoryY = top + 70;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                if (inside(mouseX, mouseY, slotsX + col * SLOT, inventoryY + row * SLOT)) {
                    selectInventoryItem(9 + row * 9 + col);
                    return true;
                }
            }
        }
        int hotbarY = inventoryY + 58;
        for (int col = 0; col < 9; col++) {
            if (inside(mouseX, mouseY, slotsX + col * SLOT, hotbarY)) {
                selectInventoryItem(col);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void selectInventoryItem(int index) {
        ItemStack stack = minecraft.player.getInventory().getItem(index);
        if (stack.getItem() instanceof BlockItem && BuildPipeline.isBuildTriggerItem(stack)) {
            selected = stack.getItem();
            if (hasShiftDown()) {
                for (int i = 0; i < palette.size(); i++) {
                    if (palette.get(i) == Items.AIR) {
                        palette.set(i, selected);
                        save();
                        break;
                    }
                }
            }
        }
    }

    private void save() {
        RandomizerToolData.setItems(tool, palette);
        PacketHandler.sendToServer(new UpdateRandomizerC2SPacket(palette));
    }

    private ItemStack hoveredStack(double mouseX, double mouseY) {
        int slotsX = left + 16;
        int fakeY = top + 38;
        for (int i = 0; i < palette.size(); i++) {
            if (inside(mouseX, mouseY, slotsX + i * SLOT, fakeY)) return itemStack(palette.get(i));
        }
        int inventoryY = top + 70;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                if (inside(mouseX, mouseY, slotsX + col * SLOT, inventoryY + row * SLOT)) {
                    return minecraft.player.getInventory().getItem(9 + row * 9 + col);
                }
            }
        }
        int hotbarY = inventoryY + 58;
        for (int col = 0; col < 9; col++) {
            if (inside(mouseX, mouseY, slotsX + col * SLOT, hotbarY)) {
                return minecraft.player.getInventory().getItem(col);
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack itemStack(Item item) {
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
