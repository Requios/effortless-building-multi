package nl.requios.effortlessbuilding.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import nl.requios.effortlessbuilding.config.ServerConfig;
import nl.requios.effortlessbuilding.network.PacketHandler;
import nl.requios.effortlessbuilding.network.UpdateServerConfigC2SPacket;

/**
 * Main server configuration screen.
 * Contains general settings at the top and navigation buttons to breaking sub-screens.
 * The screen scrolls if content exceeds the visible area.
 */
public class ServerConfigScreen extends Screen {

    private static final int PANEL_W = 280;
    private static final int ROW_H = 28;
    private static final int SECTION_GAP = 12;

    /** Scratch copy of the config being edited — only applied on Save. */
    private final ServerConfig scratch;

    private EditBox reachField;
    private EditBox axisField;

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

        int left = (width - PANEL_W) / 2;
        int fieldX = left + 180;
        int fieldW = 60;

        int y = 0;
        y += 24; // title

        // Row: Build Mode Reach
        reachField = new EditBox(font, fieldX, 0, fieldW, 18, Component.literal(""));
        reachField.setValue(String.valueOf(scratch.getBuildModeReach()));
        reachField.setFilter(s -> s.isEmpty() || s.matches("\\d{0,4}"));
        addRenderableWidget(reachField);
        y += ROW_H;

        // Row: Max Blocks Per Axis
        axisField = new EditBox(font, fieldX, 0, fieldW, 18, Component.literal(""));
        axisField.setValue(String.valueOf(scratch.getMaxBlocksPerAxis()));
        axisField.setFilter(s -> s.isEmpty() || s.matches("\\d{0,4}"));
        addRenderableWidget(axisField);
        y += ROW_H;

        y += SECTION_GAP;

        // Breaking sub-screen rows: label on the left, "Configure..." button on the right
        int configBtnW = 80;
        int configBtnX = left + PANEL_W - configBtnW - 10;

        addRenderableWidget(Button.builder(
                        Component.translatable("effortlessbuilding.button.configure"),
                        btn -> openBreakingSub(scratch.getBreakingPlacedBlocks(),
                                "effortlessbuilding.config.breaking_placed_blocks"))
                .bounds(configBtnX, 0, configBtnW, 20).build());
        y += ROW_H;

        addRenderableWidget(Button.builder(
                        Component.translatable("effortlessbuilding.button.configure"),
                        btn -> openBreakingSub(scratch.getBreakingAnyBlocks(),
                                "effortlessbuilding.config.breaking_any_blocks"))
                .bounds(configBtnX, 0, configBtnW, 20).build());
        y += ROW_H;

        addRenderableWidget(Button.builder(
                        Component.translatable("effortlessbuilding.button.configure"),
                        btn -> openBreakingSub(scratch.getBreakingWithUndo(),
                                "effortlessbuilding.config.breaking_with_undo"))
                .bounds(configBtnX, 0, configBtnW, 20).build());
        y += ROW_H;

        y += SECTION_GAP;

        // Save / Cancel
        addRenderableWidget(Button.builder(Component.translatable("effortlessbuilding.button.save"), btn -> save())
                .bounds(left + PANEL_W / 2 - 60, 0, 55, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), btn -> onClose())
                .bounds(left + PANEL_W / 2 + 5, 0, 55, 20).build());
        y += 28;

        contentHeight = y;
        maxScroll = Math.max(0, contentHeight - (height - 40));

        repositionWidgets();
    }

    private void openBreakingSub(ServerConfig.BreakingConfig breakingConfig, String titleKey) {
        minecraft.setScreen(new BreakingConfigScreen(this, breakingConfig, titleKey));
    }

    /** Reposition all widgets based on current scrollOffset. */
    private void repositionWidgets() {
        int left = (width - PANEL_W) / 2;
        int fieldX = left + 180;
        int top = 20 - scrollOffset;
        int y = top + 24;

        reachField.setPosition(fieldX, y);
        y += ROW_H;

        axisField.setPosition(fieldX, y);
        y += ROW_H;

        y += SECTION_GAP;

        // Widget order: reachField(0), axisField(1), btn_placed(2), btn_any(3), btn_undo(4), save(5), cancel(6)
        var widgets = children();
        if (widgets.size() >= 7) {
            int configBtnW = 80;
            int configBtnX = left + PANEL_W - configBtnW - 10;
            ((Button) widgets.get(2)).setPosition(configBtnX, y);
            y += ROW_H;
            ((Button) widgets.get(3)).setPosition(configBtnX, y);
            y += ROW_H;
            ((Button) widgets.get(4)).setPosition(configBtnX, y);
            y += ROW_H;

            y += SECTION_GAP;
            ((Button) widgets.get(5)).setPosition(left + PANEL_W / 2 - 60, y);
            ((Button) widgets.get(6)).setPosition(left + PANEL_W / 2 + 5, y);
        }
    }

    private void save() {
        scratch.setBuildModeReach(parseOrDefault(reachField.getValue(), ServerConfig.DEFAULT_BUILD_MODE_REACH));
        scratch.setMaxBlocksPerAxis(parseOrDefault(axisField.getValue(), ServerConfig.DEFAULT_MAX_BLOCKS_PER_AXIS));
        PacketHandler.sendToServer(new UpdateServerConfigC2SPacket(scratch.toJson()));
        onClose();
    }

    private int parseOrDefault(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 150 << 24);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = (width - PANEL_W) / 2;
        int top = 20 - scrollOffset;

        // Title
        graphics.drawCenteredString(font, title, width / 2, top + 6, 0xFFFFFF);

        int labelX = left + 10;
        int y = top + 24;

        // General labels
        graphics.drawString(font, Component.translatable("effortlessbuilding.config.build_mode_reach"), labelX, y + 5, 0xFFFFFF);
        y += ROW_H;
        graphics.drawString(font, Component.translatable("effortlessbuilding.config.max_blocks_per_axis"), labelX, y + 5, 0xFFFFFF);
        y += ROW_H;

        y += SECTION_GAP;

        // Breaking section labels
        graphics.drawString(font, Component.translatable("effortlessbuilding.config.breaking_placed_blocks"), labelX, y + 5, 0xFFFFFF);
        y += ROW_H;
        graphics.drawString(font, Component.translatable("effortlessbuilding.config.breaking_any_blocks"), labelX, y + 5, 0xFFFFFF);
        y += ROW_H;
        graphics.drawString(font, Component.translatable("effortlessbuilding.config.breaking_with_undo"), labelX, y + 5, 0xFFFFFF);
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
}
