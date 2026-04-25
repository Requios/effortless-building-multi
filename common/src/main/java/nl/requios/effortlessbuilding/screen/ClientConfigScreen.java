package nl.requios.effortlessbuilding.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import nl.requios.effortlessbuilding.config.ClientConfig;

public class ClientConfigScreen extends Screen {

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 142;

    // --- Slider state (rendered manually; value stored as fraction 0-1 within allowed range) ---
    private float sizeValue;        // 0.10 – 1.0
    private float transparencyValue; // 0.0 – 1.0
    private boolean protectTileEntities;

    private boolean draggingSize;
    private boolean draggingTransparency;

    // Slider geometry
    private static final int SLIDER_W = 100;
    private static final int SLIDER_H = 14;

    public ClientConfigScreen() {
        super(Component.translatable("effortlessbuilding.screen.client_config"));
    }

    @Override
    protected void init() {
        super.init();
        ClientConfig cfg = ClientConfig.INSTANCE;

        sizeValue = cfg.getPreviewBlockSize();
        transparencyValue = cfg.getPreviewBlockTransparency();
        protectTileEntities = cfg.shouldProtectTileEntities();

        int left = (width - PANEL_W) / 2;
        int top = (height - PANEL_H) / 2;
        int rowX = left + PANEL_W - 114;
        int rowY = top + 25;

        // Row 1 & 2: sliders are rendered manually (no widget needed, handled in mouse events)

        // Row 3: protect tile entities toggle
        rowY += 28 * 2;
        addRenderableWidget(Button.builder(
                        Component.literal(onOff(protectTileEntities)),
                        btn -> {
                            protectTileEntities = !protectTileEntities;
                            btn.setMessage(Component.literal(onOff(protectTileEntities)));
                        })
                .bounds(rowX, rowY, SLIDER_W, SLIDER_H).build());

        // Save / Cancel
        rowY += 34;
        addRenderableWidget(Button.builder(Component.translatable("effortlessbuilding.button.save"), btn -> save())
                .bounds(left + PANEL_W / 2 - 60, rowY, 55, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), btn -> onClose())
                .bounds(left + PANEL_W / 2 + 5, rowY, 55, 20).build());
    }

    private void save() {
        ClientConfig cfg = ClientConfig.INSTANCE;
        cfg.setPreviewBlockSize(sizeValue);
        cfg.setPreviewBlockTransparency(transparencyValue);
        cfg.setProtectTileEntities(protectTileEntities);
        cfg.save();
        onClose();
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = (width - PANEL_W) / 2;
        int top = (height - PANEL_H) / 2;

        // Full-width darker panel
        renderMenuBackground(graphics, 0, top, this.width, PANEL_H);

        // Title
        graphics.drawCenteredString(font, title, width / 2, top + 6, 0xFFFFFF);

        int labelX = left + 10;
        int sliderX = left + PANEL_W - 114;
        int rowY = top + 25;

        // Row 1: preview block size slider
        graphics.drawString(font, Component.translatable("effortlessbuilding.config.preview_block_size"), labelX, rowY + 3, 0xFFFFFF);
        renderSlider(graphics, sliderX, rowY, sizeFraction(), Math.round(sizeValue * 100) + "%");

        // Row 2: preview block transparency slider
        rowY += 28;
        graphics.drawString(font, Component.translatable("effortlessbuilding.config.preview_block_transparency"), labelX, rowY + 3, 0xFFFFFF);
        renderSlider(graphics, sliderX, rowY, transparencyValue, Math.round(transparencyValue * 100) + "%");

        // Row 3: protect tile entities (button already rendered by super)
        rowY += 28;
        graphics.drawString(font, Component.translatable("effortlessbuilding.config.protect_tile_entities"), labelX, rowY + 3, 0xFFFFFF);
    }

    private void renderSlider(GuiGraphics graphics, int x, int y, float fraction, String label) {
        // Track
        graphics.fill(x, y, x + SLIDER_W, y + SLIDER_H, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + SLIDER_W - 1, y + SLIDER_H - 1, 0xFF333333);
        // Filled portion
        int fillW = (int) ((SLIDER_W - 2) * fraction);
        graphics.fill(x + 1, y + 1, x + 1 + fillW, y + SLIDER_H - 1, 0xFF4488CC);
        // Handle
        int hx = x + 1 + fillW - 2;
        graphics.fill(Math.max(hx, x + 1), y + 1, Math.min(hx + 4, x + SLIDER_W - 1), y + SLIDER_H - 1, 0xFFFFFFFF);
        // Label
        graphics.drawCenteredString(font, label, x + SLIDER_W / 2, y + 3, 0xFFFFFF);
    }

    /** Maps sizeValue (0.10–1.0) to a 0–1 fraction for the slider track. */
    private float sizeFraction() {
        return (sizeValue - ClientConfig.MIN_PREVIEW_BLOCK_SIZE)
                / (ClientConfig.MAX_PREVIEW_BLOCK_SIZE - ClientConfig.MIN_PREVIEW_BLOCK_SIZE);
    }

    /** Maps a 0–1 slider fraction back to the size value (0.10–1.0). */
    private float sizeFromFraction(float frac) {
        return ClientConfig.MIN_PREVIEW_BLOCK_SIZE
                + frac * (ClientConfig.MAX_PREVIEW_BLOCK_SIZE - ClientConfig.MIN_PREVIEW_BLOCK_SIZE);
    }

    // =========================================================================
    // Input (slider dragging)
    // =========================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int sliderX = (width - PANEL_W) / 2 + PANEL_W - 114;
            int top = (height - PANEL_H) / 2;

            int sizeY = top + 25;
            if (isInSlider(mouseX, mouseY, sliderX, sizeY)) {
                draggingSize = true;
                updateSizeSlider(mouseX, sliderX);
                return true;
            }
            int transY = sizeY + 28;
            if (isInSlider(mouseX, mouseY, sliderX, transY)) {
                draggingTransparency = true;
                updateTransparencySlider(mouseX, sliderX);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        int sliderX = (width - PANEL_W) / 2 + PANEL_W - 114;
        if (draggingSize) {
            updateSizeSlider(mouseX, sliderX);
            return true;
        }
        if (draggingTransparency) {
            updateTransparencySlider(mouseX, sliderX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSize = false;
        draggingTransparency = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean isInSlider(double mx, double my, int sx, int sy) {
        return mx >= sx && mx <= sx + SLIDER_W && my >= sy && my <= sy + SLIDER_H;
    }

    /** Snaps a 0–1 fraction to the nearest 5% step. */
    private static float snapToStep(float frac) {
        return Math.round(frac * 20f) / 20f; // 1/20 = 0.05 = 5%
    }

    private void updateSizeSlider(double mouseX, int sliderX) {
        float frac = Mth.clamp((float) (mouseX - sliderX - 1) / (SLIDER_W - 2), 0f, 1f);
        sizeValue = snapToStep(sizeFromFraction(frac));
    }

    private void updateTransparencySlider(double mouseX, int sliderX) {
        float frac = Mth.clamp((float) (mouseX - sliderX - 1) / (SLIDER_W - 2), 0f, 1f);
        transparencyValue = snapToStep(frac);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}


