package nl.requios.effortlessbuilding.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import nl.requios.effortlessbuilding.config.ServerConfig;

/**
 * Sub-screen for one breaking category (placed blocks / any blocks / undo).
 * Edits a {@link ServerConfig.BreakingConfig} instance directly — the parent
 * {@link ServerConfigScreen} owns the scratch {@link ServerConfig} and sends the
 * packet on Save.
 */
public class BreakingConfigScreen extends Screen {

    private static final int PANEL_W = 280;
    private static final int ROW_H = 28;

    private final Screen parent;
    private final ServerConfig.BreakingConfig config;
    private final String allowBreakingKey;

    private boolean allowBreaking;
    private boolean requireToolsToBreak;
    private boolean requireToolsForDrops;

    private EditBox maxHardnessField;
    private EditBox saturationField;

    public BreakingConfigScreen(Screen parent, ServerConfig.BreakingConfig config, String categoryKey) {
        super(Component.translatable(categoryKey));
        this.parent = parent;
        this.config = config;
        this.allowBreakingKey = categoryKey + ".allow";
    }

    @Override
    protected void init() {
        super.init();

        allowBreaking = config.isAllowBreaking();
        requireToolsToBreak = config.isRequireToolsToBreak();
        requireToolsForDrops = config.isRequireToolsForDrops();

        int left = (width - PANEL_W) / 2;
        int widgetX = left + PANEL_W - 110;
        int widgetW = 100;
        int y = (height - (ROW_H * 5 + 40)) / 2;

        // Row 1: Allow Breaking (toggle)
        addRenderableWidget(Button.builder(
                        Component.literal(onOff(allowBreaking)),
                        btn -> { allowBreaking = !allowBreaking; btn.setMessage(Component.literal(onOff(allowBreaking))); })
                .bounds(widgetX, y, widgetW, 20).build());
        y += ROW_H;

        // Row 2: Max Hardness (-1 = unlimited)
        maxHardnessField = new EditBox(font, widgetX, y, 60, 18, Component.literal(""));
        maxHardnessField.setValue(formatFloat(config.getMaxHardness()));
        maxHardnessField.setFilter(s -> s.isEmpty() || s.matches("-?\\d{0,5}\\.?\\d{0,2}"));
        addRenderableWidget(maxHardnessField);
        y += ROW_H;

        // Row 3: Require Tools to Break (toggle)
        addRenderableWidget(Button.builder(
                        Component.literal(onOff(requireToolsToBreak)),
                        btn -> { requireToolsToBreak = !requireToolsToBreak; btn.setMessage(Component.literal(onOff(requireToolsToBreak))); })
                .bounds(widgetX, y, widgetW, 20).build());
        y += ROW_H;

        // Row 4: Require Tools for Drops (toggle)
        addRenderableWidget(Button.builder(
                        Component.literal(onOff(requireToolsForDrops)),
                        btn -> { requireToolsForDrops = !requireToolsForDrops; btn.setMessage(Component.literal(onOff(requireToolsForDrops))); })
                .bounds(widgetX, y, widgetW, 20).build());
        y += ROW_H;

        // Row 5: Saturation per Block
        saturationField = new EditBox(font, widgetX, y, 60, 18, Component.literal(""));
        saturationField.setValue(formatFloat(config.getSaturationPerBlock()));
        saturationField.setFilter(s -> s.isEmpty() || s.matches("\\d{0,4}\\.?\\d{0,2}"));
        addRenderableWidget(saturationField);
        y += ROW_H;

        // Done button — writes back to the config and returns to parent
        y += 12;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> done())
                .bounds(left + PANEL_W / 2 - 50, y, 100, 20).build());
    }

    private void done() {
        config.setAllowBreaking(allowBreaking);
        config.setMaxHardness(parseFloatOrDefault(maxHardnessField.getValue(), ServerConfig.BreakingConfig.DEFAULT_MAX_HARDNESS));
        config.setRequireToolsToBreak(requireToolsToBreak);
        config.setRequireToolsForDrops(requireToolsForDrops);
        config.setSaturationPerBlock(parseFloatOrDefault(saturationField.getValue(), ServerConfig.BreakingConfig.DEFAULT_SATURATION));
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        // Discard changes and go back to parent
        minecraft.setScreen(parent);
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 150 << 24);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = (width - PANEL_W) / 2;
        int labelX = left + 10;
        int y = (height - (ROW_H * 5 + 40)) / 2;

        // Row labels — first row uses contextual key (e.g. "Allow Breaking Placed Blocks")
        graphics.drawString(font, Component.translatable(allowBreakingKey), labelX, y + 5, 0xFFFFFF);
        y += ROW_H;
        graphics.drawString(font, Component.translatable("effortlessbuilding.config.max_hardness"), labelX, y + 5, 0xFFFFFF);
        y += ROW_H;
        graphics.drawString(font, Component.translatable("effortlessbuilding.config.require_tools_to_break"), labelX, y + 5, 0xFFFFFF);
        y += ROW_H;
        graphics.drawString(font, Component.translatable("effortlessbuilding.config.require_tools_for_drops"), labelX, y + 5, 0xFFFFFF);
        y += ROW_H;
        graphics.drawString(font, Component.translatable("effortlessbuilding.config.saturation_per_block"), labelX, y + 5, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private static String formatFloat(float v) {
        if (v == (int) v) return String.valueOf((int) v);
        return String.valueOf(v);
    }

    private static float parseFloatOrDefault(String s, float def) {
        try { return Float.parseFloat(s); } catch (NumberFormatException e) { return def; }
    }
}

