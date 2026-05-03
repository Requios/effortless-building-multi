package nl.requios.effortlessbuilding.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import nl.requios.effortlessbuilding.config.ServerConfig;
import nl.requios.effortlessbuilding.network.PacketHandler;
import nl.requios.effortlessbuilding.network.UpdateServerConfigC2SPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Server configuration screen with survival and creative sections.
 */
public class ServerConfigScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int ROW_H = 24;
    private static final int SECTION_GAP = 12;
    private static final int BOTTOM_BAR_H = 30;

    /** Tooltip keys for survival rows (in order). */
    private static final String[] SURVIVAL_TOOLTIP_KEYS = {
        "effortlessbuilding.config.reach.tooltip",
        "effortlessbuilding.config.max_blocks_placed.tooltip",
        "effortlessbuilding.config.max_blocks_per_axis.tooltip",
        "effortlessbuilding.config.max_mirror_size.tooltip",
        "effortlessbuilding.config.max_array_count.tooltip",
        "effortlessbuilding.config.max_array_offset.tooltip",
        "effortlessbuilding.config.allow_breaking.tooltip",
        "effortlessbuilding.config.only_placed_blocks.tooltip",
        "effortlessbuilding.config.max_hardness.tooltip",
        "effortlessbuilding.config.require_tools.tooltip",
        "effortlessbuilding.config.use_durability.tooltip",
    };

    /** Tooltip keys for creative rows (in order). */
    private static final String[] CREATIVE_TOOLTIP_KEYS = {
        "effortlessbuilding.config.reach.tooltip",
        "effortlessbuilding.config.max_blocks_placed.tooltip",
        "effortlessbuilding.config.max_blocks_per_axis.tooltip",
        "effortlessbuilding.config.max_mirror_size.tooltip",
        "effortlessbuilding.config.max_array_count.tooltip",
        "effortlessbuilding.config.max_array_offset.tooltip",
    };

    private final ServerConfig scratch;

    // Survival
    private EditBox survReachField;
    private EditBox survMaxPlacedField;
    private EditBox survAxisField;
    private EditBox survMirrorSizeField;
    private EditBox survArrayCountField;
    private EditBox survArrayOffsetField;
    private boolean survAllowBreaking;
    private boolean survOnlyPlacedBlocks;
    private EditBox survMaxHardnessField;
    private boolean survRequireTools;
    private boolean survUseDurability;

    // Creative
    private EditBox creReachField;
    private EditBox creMaxPlacedField;
    private EditBox creAxisField;
    private EditBox creMirrorSizeField;
    private EditBox creArrayCountField;
    private EditBox creArrayOffsetField;

    // Scrollable widgets in order for repositioning
    private final List<Object> widgetOrder = new ArrayList<>();

    // Fixed buttons (not scrolled)
    private Button saveBtn;
    private Button cancelBtn;

    // Scroll state
    private int contentHeight;
    private int scrollOffset;
    private int maxScroll;

    public ServerConfigScreen() {
        super(Component.translatable("effortlessbuilding.screen.server_config"));
        scratch = ServerConfig.fromJson(ServerConfig.INSTANCE.toJson());
    }

    @Override
    protected void init() {
        super.init();
        scrollOffset = 0;
        widgetOrder.clear();

        survAllowBreaking = scratch.survivalAllowBreaking;
        survOnlyPlacedBlocks = scratch.survivalOnlyPlacedBlocks;
        survRequireTools = scratch.survivalRequireTools;
        survUseDurability = scratch.survivalUseDurability;

        int left = (width - PANEL_W) / 2;
        int fieldX = left + 220;
        int fieldW = 60;

        int y = 0;
        y += 24; // title

        // -- Survival section header --
        y += ROW_H;

        survReachField = addIntField(fieldX, fieldW, scratch.survivalReach); y += ROW_H;
        survMaxPlacedField = addIntField(fieldX, fieldW, scratch.survivalMaxBlocksPlaced); y += ROW_H;
        survAxisField = addIntField(fieldX, fieldW, scratch.survivalMaxBlocksPerAxis); y += ROW_H;
        survMirrorSizeField = addIntField(fieldX, fieldW, scratch.survivalMaxMirrorSize); y += ROW_H;
        survArrayCountField = addIntField(fieldX, fieldW, scratch.survivalMaxArrayCount); y += ROW_H;
        survArrayOffsetField = addIntField(fieldX, fieldW, scratch.survivalMaxArrayOffset); y += ROW_H;

        // Booleans — same fieldX and fieldW as int fields
        var btnAllowBreaking = addRenderableWidget(Button.builder(
                Component.literal(onOff(survAllowBreaking)),
                btn -> { survAllowBreaking = !survAllowBreaking; btn.setMessage(Component.literal(onOff(survAllowBreaking))); })
                .bounds(fieldX, 0, fieldW, 18).build());
        widgetOrder.add(btnAllowBreaking);
        y += ROW_H;

        var btnOnlyPlaced = addRenderableWidget(Button.builder(
                Component.literal(onOff(survOnlyPlacedBlocks)),
                btn -> { survOnlyPlacedBlocks = !survOnlyPlacedBlocks; btn.setMessage(Component.literal(onOff(survOnlyPlacedBlocks))); })
                .bounds(fieldX, 0, fieldW, 18).build());
        widgetOrder.add(btnOnlyPlaced);
        y += ROW_H;

        survMaxHardnessField = new EditBox(font, fieldX, 0, fieldW, 18, Component.literal(""));
        survMaxHardnessField.setValue(formatFloat(scratch.survivalMaxHardness));
        survMaxHardnessField.setFilter(s -> s.isEmpty() || s.matches("-?\\d{0,5}\\.?\\d{0,2}"));
        addRenderableWidget(survMaxHardnessField);
        widgetOrder.add(survMaxHardnessField);
        y += ROW_H;

        var btnRequireTools = addRenderableWidget(Button.builder(
                Component.literal(onOff(survRequireTools)),
                btn -> { survRequireTools = !survRequireTools; btn.setMessage(Component.literal(onOff(survRequireTools))); })
                .bounds(fieldX, 0, fieldW, 18).build());
        widgetOrder.add(btnRequireTools);
        y += ROW_H;

        var btnUseDurability = addRenderableWidget(Button.builder(
                Component.literal(onOff(survUseDurability)),
                btn -> { survUseDurability = !survUseDurability; btn.setMessage(Component.literal(onOff(survUseDurability))); })
                .bounds(fieldX, 0, fieldW, 18).build());
        widgetOrder.add(btnUseDurability);
        y += ROW_H;

        y += SECTION_GAP;

        // -- Creative section header --
        y += ROW_H;

        creReachField = addIntField(fieldX, fieldW, scratch.creativeReach); y += ROW_H;
        creMaxPlacedField = addIntField(fieldX, fieldW, scratch.creativeMaxBlocksPlaced); y += ROW_H;
        creAxisField = addIntField(fieldX, fieldW, scratch.creativeMaxBlocksPerAxis); y += ROW_H;
        creMirrorSizeField = addIntField(fieldX, fieldW, scratch.creativeMaxMirrorSize); y += ROW_H;
        creArrayCountField = addIntField(fieldX, fieldW, scratch.creativeMaxArrayCount); y += ROW_H;
        creArrayOffsetField = addIntField(fieldX, fieldW, scratch.creativeMaxArrayOffset); y += ROW_H;

        y += SECTION_GAP;
        contentHeight = y;

        // Save / Cancel — fixed at bottom, not part of scrollable content
        int bottomY = height - BOTTOM_BAR_H + 5;
        saveBtn = addRenderableWidget(Button.builder(Component.translatable("effortlessbuilding.button.save"), btn -> save())
                .bounds(width / 2 - 60, bottomY, 55, 20).build());
        cancelBtn = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), btn -> onClose())
                .bounds(width / 2 + 5, bottomY, 55, 20).build());

        int scrollableArea = height - 20 - BOTTOM_BAR_H; // top margin 20, bottom bar
        maxScroll = Math.max(0, contentHeight - scrollableArea);
        repositionWidgets();
    }

    private EditBox addIntField(int x, int w, int value) {
        EditBox field = new EditBox(font, x, 0, w, 18, Component.literal(""));
        field.setValue(String.valueOf(value));
        field.setFilter(s -> s.isEmpty() || s.matches("\\d{0,6}"));
        addRenderableWidget(field);
        widgetOrder.add(field);
        return field;
    }

    private void repositionWidgets() {
        int left = (width - PANEL_W) / 2;
        int fieldX = left + 220;
        int top = 20 - scrollOffset;
        int y = top + 24;
        int i = 0;

        // Survival header
        y += ROW_H;

        // 6 int fields + 3 toggles + 1 float field + 1 toggle = 11 widgets
        for (int n = 0; n < 11; n++) { setPos(i++, fieldX, y); y += ROW_H; }

        y += SECTION_GAP;

        // Creative header
        y += ROW_H;

        // 6 int fields
        for (int n = 0; n < 6; n++) { setPos(i++, fieldX, y); y += ROW_H; }
    }

    private void setPos(int index, int x, int y) {
        if (index >= widgetOrder.size()) return;
        Object w = widgetOrder.get(index);
        if (w instanceof EditBox eb) eb.setPosition(x, y);
        else if (w instanceof Button btn) btn.setPosition(x, y);
    }

    private void save() {
        scratch.survivalReach = parseOrDefault(survReachField.getValue(), 20);
        scratch.survivalMaxBlocksPlaced = parseOrDefault(survMaxPlacedField.getValue(), 200);
        scratch.survivalMaxBlocksPerAxis = parseOrDefault(survAxisField.getValue(), 12);
        scratch.survivalMaxMirrorSize = parseOrDefault(survMirrorSizeField.getValue(), 32);
        scratch.survivalMaxArrayCount = parseOrDefault(survArrayCountField.getValue(), 10);
        scratch.survivalMaxArrayOffset = parseOrDefault(survArrayOffsetField.getValue(), 32);
        scratch.survivalAllowBreaking = survAllowBreaking;
        scratch.survivalOnlyPlacedBlocks = survOnlyPlacedBlocks;
        scratch.survivalMaxHardness = parseFloatOrDefault(survMaxHardnessField.getValue(), -1f);
        scratch.survivalRequireTools = survRequireTools;
        scratch.survivalUseDurability = survUseDurability;

        scratch.creativeReach = parseOrDefault(creReachField.getValue(), 64);
        scratch.creativeMaxBlocksPlaced = parseOrDefault(creMaxPlacedField.getValue(), 10000);
        scratch.creativeMaxBlocksPerAxis = parseOrDefault(creAxisField.getValue(), 64);
        scratch.creativeMaxMirrorSize = parseOrDefault(creMirrorSizeField.getValue(), 128);
        scratch.creativeMaxArrayCount = parseOrDefault(creArrayCountField.getValue(), 64);
        scratch.creativeMaxArrayOffset = parseOrDefault(creArrayOffsetField.getValue(), 128);

        scratch.clampAll();
        PacketHandler.sendToServer(new UpdateServerConfigC2SPacket(scratch.toJson()));
        onClose();
    }

    private int parseOrDefault(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    private float parseFloatOrDefault(String s, float def) {
        try { return Float.parseFloat(s); } catch (NumberFormatException e) { return def; }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 150 << 24);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render widgets (fields, toggles) via super
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = (width - PANEL_W) / 2;
        int top = 20 - scrollOffset;
        int labelX = left + 10;

        // Use scissor to clip scrollable content above the bottom bar
        graphics.enableScissor(0, 0, width, height - BOTTOM_BAR_H);

        graphics.drawCenteredString(font, title, width / 2, top + 6, 0xFFFFFF);

        int y = top + 24;

        // --- Survival ---
        graphics.drawString(font, Component.translatable("effortlessbuilding.config.section_survival"), labelX, y + 5, 0x55FF55);
        y += ROW_H;
        drawLabel(graphics, labelX + 8, y, "effortlessbuilding.config.reach"); y += ROW_H;
        drawLabel(graphics, labelX + 8, y, "effortlessbuilding.config.max_blocks_placed"); y += ROW_H;
        drawLabel(graphics, labelX + 8, y, "effortlessbuilding.config.max_blocks_per_axis"); y += ROW_H;
        drawLabel(graphics, labelX + 8, y, "effortlessbuilding.config.max_mirror_size"); y += ROW_H;
        drawLabel(graphics, labelX + 8, y, "effortlessbuilding.config.max_array_count"); y += ROW_H;
        drawLabel(graphics, labelX + 8, y, "effortlessbuilding.config.max_array_offset"); y += ROW_H;
        drawLabel(graphics, labelX + 8, y, "effortlessbuilding.config.allow_breaking"); y += ROW_H;
        drawLabel(graphics, labelX + 8, y, "effortlessbuilding.config.only_placed_blocks"); y += ROW_H;
        drawLabel(graphics, labelX + 8, y, "effortlessbuilding.config.max_hardness"); y += ROW_H;
        drawLabel(graphics, labelX + 8, y, "effortlessbuilding.config.require_tools"); y += ROW_H;
        drawLabel(graphics, labelX + 8, y, "effortlessbuilding.config.use_durability"); y += ROW_H;

        y += SECTION_GAP;

        // --- Creative ---
        graphics.drawString(font, Component.translatable("effortlessbuilding.config.section_creative"), labelX, y + 5, 0xFFFF55);
        y += ROW_H;
        drawLabel(graphics, labelX + 8, y, "effortlessbuilding.config.reach"); y += ROW_H;
        drawLabel(graphics, labelX + 8, y, "effortlessbuilding.config.max_blocks_placed"); y += ROW_H;
        drawLabel(graphics, labelX + 8, y, "effortlessbuilding.config.max_blocks_per_axis"); y += ROW_H;
        drawLabel(graphics, labelX + 8, y, "effortlessbuilding.config.max_mirror_size"); y += ROW_H;
        drawLabel(graphics, labelX + 8, y, "effortlessbuilding.config.max_array_count"); y += ROW_H;
        drawLabel(graphics, labelX + 8, y, "effortlessbuilding.config.max_array_offset"); y += ROW_H;

        graphics.disableScissor();

        // Bottom bar — drawn AFTER scissor is disabled so it's never clipped
        graphics.fill(0, height - BOTTOM_BAR_H, width, height, 0xFF000000);
        saveBtn.render(graphics, mouseX, mouseY, partialTick);
        cancelBtn.render(graphics, mouseX, mouseY, partialTick);

        // Tooltips — render last so they appear on top of everything
        renderRowTooltips(graphics, mouseX, mouseY);
    }

    private void renderRowTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        // Don't show tooltips if hovering over the bottom bar
        if (mouseY >= height - BOTTOM_BAR_H) return;

        int left = (width - PANEL_W) / 2;
        int labelX = left + 10 + 8;
        int labelMaxX = left + 215; // label area ends before the field column
        int top = 20 - scrollOffset;
        int y = top + 24;

        // Only show tooltip if mouse is over the label text area
        if (mouseX < labelX || mouseX >= labelMaxX) return;

        // Survival header row (no tooltip)
        y += ROW_H;

        // Survival data rows
        for (String key : SURVIVAL_TOOLTIP_KEYS) {
            if (mouseY >= y && mouseY < y + ROW_H) {
                renderMultiLineTooltip(graphics, key, mouseX, mouseY);
                return;
            }
            y += ROW_H;
        }

        y += SECTION_GAP;

        // Creative header row (no tooltip)
        y += ROW_H;

        // Creative data rows
        for (String key : CREATIVE_TOOLTIP_KEYS) {
            if (mouseY >= y && mouseY < y + ROW_H) {
                renderMultiLineTooltip(graphics, key, mouseX, mouseY);
                return;
            }
            y += ROW_H;
        }
    }

    /** Renders a tooltip, splitting the translated text on newlines for multi-line support. */
    private void renderMultiLineTooltip(GuiGraphics graphics, String key, int mouseX, int mouseY) {
        String text = Component.translatable(key).getString();
        String[] lines = text.split("\n");
        List<Component> components = new ArrayList<>();
        for (String line : lines) {
            components.add(Component.literal(line));
        }
        graphics.renderTooltip(font, components, java.util.Optional.empty(), mouseX, mouseY);
    }

    private boolean isInRow(int mouseX, int mouseY, int left, int rowY) {
        return mouseX >= left && mouseX < left + PANEL_W && mouseY >= rowY && mouseY < rowY + ROW_H;
    }

    private void drawLabel(GuiGraphics graphics, int x, int y, String key) {
        graphics.drawString(font, Component.translatable(key), x, y + 5, 0xFFFFFF);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset = Math.clamp((int) (scrollOffset - verticalAmount * 10), 0, maxScroll);
        repositionWidgets();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String onOff(boolean value) { return value ? "ON" : "OFF"; }

    private static String formatFloat(float v) {
        return v == (int) v ? String.valueOf((int) v) : String.valueOf(v);
    }
}
