package nl.requios.effortlessbuilding.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import nl.requios.effortlessbuilding.Constants;
import nl.requios.effortlessbuilding.item.RandomizerToolData;
import nl.requios.effortlessbuilding.menu.RandomizerMenu;

/** Dispenser-style randomizer palette backed by a normal player inventory menu. */
public class RandomizerScreen extends AbstractContainerScreen<RandomizerMenu> {
    private static final int RATIO_Y = 19;
    private static final int RATIO_WIDTH = 16;
    private static final int RATIO_HEIGHT = 14;
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(
            Constants.MOD_ID, "textures/gui/container/randomizertool.png");

    public RandomizerScreen(RandomizerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 150);
        inventoryLabelY = 56;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        extractRatios(graphics, mouseX, mouseY);
        int hoveredRatio = getHoveredRatio(mouseX, mouseY);
        if (hoveredRatio >= 0) {
            graphics.setTooltipForNextFrame(font,
                    Component.translatable("effortlessbuilding.screen.randomizer.ratio.tooltip"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int slot = getHoveredRatio(mouseX, mouseY);
        if (slot < 0 || verticalAmount == 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        int delta = verticalAmount > 0 ? 1 : -1;
        if (menu.adjustRatioClient(slot, delta)) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                        slot + (delta > 0 ? 0 : RandomizerToolData.SLOT_COUNT));
            }
        }
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    private void extractRatios(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int hoveredRatio = getHoveredRatio(mouseX, mouseY);
        for (int i = 0; i < RandomizerToolData.SLOT_COUNT; i++) {
            int x = leftPos + 8 + i * 18;
            int y = topPos + RATIO_Y;
            if (i == hoveredRatio) {
                graphics.fill(x, y, x + RATIO_WIDTH, y + RATIO_HEIGHT, 0x998b8b8b);
            }
            String text = Integer.toString(menu.getRatio(i));
            graphics.text(font, text, x + RATIO_WIDTH / 2 - font.width(text) / 2 - 3, y + 4, 0xFF404040, false);
        }
    }

    private int getHoveredRatio(double mouseX, double mouseY) {
        if (mouseY < topPos + RATIO_Y || mouseY >= topPos + RATIO_Y + RATIO_HEIGHT) return -1;
        int slot = (int) ((mouseX - leftPos - 8) / 18);
        if (slot < 0 || slot >= RandomizerToolData.SLOT_COUNT) return -1;
        int x = leftPos + 8 + slot * 18;
        return mouseX >= x && mouseX < x + RATIO_WIDTH ? slot : -1;
    }
}
