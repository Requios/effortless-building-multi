package nl.requios.effortlessbuilding.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import org.lwjgl.glfw.GLFW;

public class AltScreen extends Screen {

    private static final int PANEL_WIDTH = 200;
    private static final int BUTTON_WIDTH = 160;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int PANEL_PADDING_TOP = 28;
    private static final int PANEL_PADDING_BOTTOM = 10;
    private static final int PANEL_HEIGHT = PANEL_PADDING_TOP
        + BuildModeEnum.values().length * (BUTTON_HEIGHT + BUTTON_GAP) - BUTTON_GAP
        + PANEL_PADDING_BOTTOM;

    private static final long FADE_DURATION_MS = 150;

    private long openTimeMs;
    private final Button[] modeButtons = new Button[BuildModeEnum.values().length];

    public AltScreen() {
        super(Component.translatable("screen.effortlessbuilding.alt_screen"));
    }

    @Override
    protected void init() {
        openTimeMs = System.currentTimeMillis();

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int buttonX = panelX + (PANEL_WIDTH - BUTTON_WIDTH) / 2;

        BuildModeEnum[] modes = BuildModeEnum.values();
        for (int i = 0; i < modes.length; i++) {
            BuildModeEnum mode = modes[i];
            int buttonY = panelY + PANEL_PADDING_TOP + i * (BUTTON_HEIGHT + BUTTON_GAP);
            modeButtons[i] = addRenderableWidget(
                Button.builder(Component.translatable(mode.getNameKey()), btn -> {
                    BuildModes.CLIENT.setBuildMode(mode);
                }).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build()
            );
        }
    }

    @Override
    public void tick() {
        long windowHandle = Minecraft.getInstance().getWindow().getWindow();
        boolean altHeld = InputConstants.isKeyDown(windowHandle, GLFW.GLFW_KEY_LEFT_ALT) ||
                          InputConstants.isKeyDown(windowHandle, GLFW.GLFW_KEY_RIGHT_ALT);
        if (!altHeld) {
            this.onClose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float progress = Math.min(1f, (System.currentTimeMillis() - openTimeMs) / (float) FADE_DURATION_MS);
        int alpha = (int) (0x60 * progress);
        graphics.fill(0, 0, this.width, this.height, alpha << 24);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xC0101010);
        graphics.renderOutline(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFFAAAAAA);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, panelY + 10, 0xFFFFFF);

        // Highlight the active mode's button
        BuildModeEnum activeMode = BuildModes.CLIENT.getBuildMode();
        BuildModeEnum[] modes = BuildModeEnum.values();
        for (int i = 0; i < modes.length; i++) {
            if (modes[i] == activeMode) {
                Button btn = modeButtons[i];
                graphics.fill(btn.getX() - 2, btn.getY() - 2,
                              btn.getX() + btn.getWidth() + 2, btn.getY() + btn.getHeight() + 2,
                              0x6000AAFF);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
