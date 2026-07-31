package nl.requios.effortlessbuilding.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import nl.requios.effortlessbuilding.item.RandomizerTooltipData;

/** Renders the randomizer palette as a compact inventory grid in an item tooltip. */
public final class RandomizerTooltipComponent implements ClientTooltipComponent {
    private static final int COLUMNS = 9;
    private static final int SLOT_SIZE = 18;
    private static final int PADDING = 2;

    private final RandomizerTooltipData data;

    public RandomizerTooltipComponent(RandomizerTooltipData data) {
        this.data = data;
    }

    @Override
    public int getHeight(Font font) {
        return PADDING * 2 + SLOT_SIZE;
    }

    @Override
    public int getWidth(Font font) {
        return PADDING * 2 + SLOT_SIZE * COLUMNS;
    }

    @Override
    public void extractText(GuiGraphicsExtractor graphics, Font font, int x, int y) {
        // This tooltip has no text portion; all information is rendered as item icons.
    }

    @Override
    public void extractImage(Font font, int x, int y, int tooltipWidth, int tooltipHeight, GuiGraphicsExtractor graphics) {
        int width = getWidth(font);
        int height = getHeight(font);
//        graphics.fill(x, y, x + width, y + height, 0xFF202020);

        for (int i = 0; i < data.stacks().size(); i++) {
            int itemX = x + PADDING + i % COLUMNS * SLOT_SIZE;
            int itemY = y + PADDING + i / COLUMNS * SLOT_SIZE;
//            graphics.fill(itemX, itemY, itemX + 16, itemY + 16, 0xFF8B8B8B);

            ItemStack stack = data.stacks().get(i);
            if (!stack.isEmpty()) {
                graphics.item(stack, itemX, itemY);
                graphics.itemDecorations(font, stack, itemX, itemY);
            }
        }
    }
}
