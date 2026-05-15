package nl.requios.effortlessbuilding.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import nl.requios.effortlessbuilding.config.ClientConfig;

import java.util.ArrayList;
import java.util.List;

public class ClientConfigScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int ROW_H = 24;
    private static final int FIELD_W = 100;
    private static final int FIELD_H = 20;

    /** Label keys in row order, used for both rendering and tooltip lookup. */
    private static final String[] LABEL_KEYS = {
        "effortlessbuilding.config.preview_block_size",
        "effortlessbuilding.config.preview_block_transparency",
        "effortlessbuilding.config.max_block_previews",
        "effortlessbuilding.config.protect_tile_entities",
    };

    private float sizeValue;
    private float transparencyValue;
    private int maxBlockPreviews;
    private boolean protectTileEntities;

    private EditBox maxPreviewsField;

    public ClientConfigScreen() {
        super(Component.translatable("effortlessbuilding.screen.client_config"));
    }

    @Override
    protected void init() {
        super.init();
        ClientConfig cfg = ClientConfig.INSTANCE;

        sizeValue = cfg.getPreviewBlockSize();
        transparencyValue = cfg.getPreviewBlockTransparency();
        maxBlockPreviews = cfg.getMaxBlockPreviews();
        protectTileEntities = cfg.shouldProtectTileEntities();

        int left = (width - PANEL_W) / 2;
        int totalH = LABEL_KEYS.length * ROW_H + 40;
        int top = (height - totalH) / 2;
        int fieldX = left + PANEL_W - FIELD_W - 10;
        int rowY = top;

        // Row 1: Preview block size (slider)
        addRenderableWidget(new AbstractSliderButton(fieldX, rowY, FIELD_W, FIELD_H,
                Component.literal(Math.round(sizeValue * 100) + "%"), sizeFraction(sizeValue)) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(Math.round(sizeValue * 100) + "%"));
            }
            @Override
            protected void applyValue() {
                sizeValue = sizeFromFraction((float) this.value);
            }
        });

        // Row 2: Preview block transparency (slider)
        rowY += ROW_H;
        addRenderableWidget(new AbstractSliderButton(fieldX, rowY, FIELD_W, FIELD_H,
                Component.literal(Math.round(transparencyValue * 100) + "%"), transparencyValue) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(Math.round(transparencyValue * 100) + "%"));
            }
            @Override
            protected void applyValue() {
                transparencyValue = Math.round((float) this.value * 20f) / 20f;
            }
        });

        // Row 3: Max block previews (int field with -/+ buttons)
        rowY += ROW_H;
        int btnW = 14;
        int editW = FIELD_W - btnW * 2;
        addRenderableWidget(Button.builder(Component.literal("−"),
                        btn -> stepMaxPreviews(-50))
                .bounds(fieldX, rowY, btnW, FIELD_H).build());
        maxPreviewsField = new EditBox(font, fieldX + btnW, rowY, editW, FIELD_H, Component.empty());
        maxPreviewsField.setValue(String.valueOf(maxBlockPreviews));
        maxPreviewsField.setResponder(s -> {
            try { maxBlockPreviews = Integer.parseInt(s); }
            catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(maxPreviewsField);
        addRenderableWidget(Button.builder(Component.literal("+"),
                        btn -> stepMaxPreviews(50))
                .bounds(fieldX + btnW + editW, rowY, btnW, FIELD_H).build());

        // Row 4: Protect tile entities (toggle)
        rowY += ROW_H;
        addRenderableWidget(Button.builder(
                        Component.literal(onOff(protectTileEntities)),
                        btn -> {
                            protectTileEntities = !protectTileEntities;
                            btn.setMessage(Component.literal(onOff(protectTileEntities)));
                        })
                .bounds(fieldX, rowY, FIELD_W, FIELD_H).build());

        // Save / Cancel
        rowY += 40;
        addRenderableWidget(Button.builder(Component.translatable("effortlessbuilding.button.save"), btn -> save())
                .bounds(left + PANEL_W / 2 - 60, rowY, 55, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), btn -> onClose())
                .bounds(left + PANEL_W / 2 + 5, rowY, 55, 20).build());
    }

    private void stepMaxPreviews(int delta) {
        maxBlockPreviews = Math.clamp(maxBlockPreviews + delta,
                ClientConfig.MIN_MAX_BLOCK_PREVIEWS, ClientConfig.MAX_MAX_BLOCK_PREVIEWS);
        maxPreviewsField.setValue(String.valueOf(maxBlockPreviews));
    }

    private void save() {
        ClientConfig cfg = ClientConfig.INSTANCE;
        cfg.setPreviewBlockSize(sizeValue);
        cfg.setPreviewBlockTransparency(transparencyValue);
        cfg.setMaxBlockPreviews(maxBlockPreviews);
        cfg.setProtectTileEntities(protectTileEntities);
        cfg.save();
        onClose();
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 150 << 24);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int left = (width - PANEL_W) / 2;
        int totalH = LABEL_KEYS.length * ROW_H + 40;
        int top = (height - totalH) / 2;
        int labelX = left + 10;

        // Title
        graphics.centeredText(font, title, width / 2, top - 16, 0xFFFFFFFF);

        // Labels
        int rowY = top;
        for (String key : LABEL_KEYS) {
            graphics.text(font, Component.translatable(key), labelX, rowY + 6, 0xFFFFFFFF);
            rowY += ROW_H;
        }

        // Tooltips — render last so they appear on top
        renderRowTooltips(graphics, mouseX, mouseY);
    }

    private void renderRowTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = (width - PANEL_W) / 2;
        int totalH = LABEL_KEYS.length * ROW_H + 40;
        int top = (height - totalH) / 2;
        int labelX = left + 10;
        int labelMaxX = left + PANEL_W - FIELD_W - 15;

        if (mouseX < labelX || mouseX >= labelMaxX) return;

        int rowY = top;
        for (String key : LABEL_KEYS) {
            if (mouseY >= rowY && mouseY < rowY + ROW_H) {
                String tooltipKey = key + ".tooltip";
                String text = Component.translatable(tooltipKey).getString();
                // Don't show tooltip if translation is missing (returns the key itself)
                if (!text.equals(tooltipKey)) {
                    String[] lines = text.split("\n");
                    List<Component> components = new ArrayList<>();
                    for (String line : lines) {
                        components.add(Component.literal(line));
                    }
                    graphics.setComponentTooltipForNextFrame(font, components, mouseX, mouseY);
                }
                return;
            }
            rowY += ROW_H;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Allow scrolling over the max previews field
        if (maxPreviewsField != null
                && mouseX >= maxPreviewsField.getX() && mouseX <= maxPreviewsField.getX() + maxPreviewsField.getWidth()
                && mouseY >= maxPreviewsField.getY() && mouseY <= maxPreviewsField.getY() + maxPreviewsField.getHeight()) {
            stepMaxPreviews(verticalAmount > 0 ? 50 : -50);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // =========================================================================
    // Value mapping
    // =========================================================================

    private static float sizeFraction(float size) {
        return (size - ClientConfig.MIN_PREVIEW_BLOCK_SIZE)
                / (ClientConfig.MAX_PREVIEW_BLOCK_SIZE - ClientConfig.MIN_PREVIEW_BLOCK_SIZE);
    }

    private static float sizeFromFraction(float frac) {
        float raw = ClientConfig.MIN_PREVIEW_BLOCK_SIZE
                + frac * (ClientConfig.MAX_PREVIEW_BLOCK_SIZE - ClientConfig.MIN_PREVIEW_BLOCK_SIZE);
        return Math.round(raw * 20f) / 20f; // snap to 5%
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

