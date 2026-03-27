package nl.requios.effortlessbuilding.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class AltScreen extends Screen {

    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 150;
    private static final long FADE_DURATION_MS = 150;

    private long openTimeMs;

    public AltScreen() {
        super(Component.translatable("screen.effortlessbuilding.alt_screen"));
    }

    @Override
    protected void init() {
        openTimeMs = System.currentTimeMillis();
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

        int x = (this.width - PANEL_WIDTH) / 2;
        int y = (this.height - PANEL_HEIGHT) / 2;

        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xC0101010);
        graphics.renderOutline(x, y, PANEL_WIDTH, PANEL_HEIGHT, 0xFFAAAAAA);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, y + 10, 0xFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
