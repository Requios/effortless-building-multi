package nl.requios.effortlessbuilding.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import nl.requios.effortlessbuilding.config.ServerConfig;
import nl.requios.effortlessbuilding.network.PacketHandler;
import nl.requios.effortlessbuilding.network.UpdateServerConfigC2SPacket;

public class ServerConfigScreen extends Screen {

    private static final int PANEL_W = 260;
    private static final int PANEL_H = 140;

    private EditBox reachField;
    private EditBox axisField;

    public ServerConfigScreen() {
        super(Component.translatable("effortlessbuilding.screen.server_config"));
    }

    @Override
    protected void init() {
        super.init();

        int left = (width - PANEL_W) / 2;
        int top = (height - PANEL_H) / 2;

        int fieldX = left + 160;
        int fieldW = 60;
        int rowY = top + 25;

        reachField = new EditBox(font, fieldX, rowY, fieldW, 18, Component.literal(""));
        reachField.setValue(String.valueOf(ServerConfig.INSTANCE.getBuildModeReach()));
        reachField.setFilter(s -> s.isEmpty() || s.matches("\\d{0,4}"));
        addRenderableWidget(reachField);

        rowY += 28;
        axisField = new EditBox(font, fieldX, rowY, fieldW, 18, Component.literal(""));
        axisField.setValue(String.valueOf(ServerConfig.INSTANCE.getMaxBlocksPerAxis()));
        axisField.setFilter(s -> s.isEmpty() || s.matches("\\d{0,4}"));
        addRenderableWidget(axisField);

        rowY += 36;
        addRenderableWidget(Button.builder(Component.translatable("effortlessbuilding.button.save"), btn -> save())
                .bounds(left + PANEL_W / 2 - 60, rowY, 55, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), btn -> onClose())
                .bounds(left + PANEL_W / 2 + 5, rowY, 55, 20).build());
    }

    private void save() {
        int reach = parseOrDefault(reachField.getValue(), ServerConfig.DEFAULT_BUILD_MODE_REACH);
        int axis = parseOrDefault(axisField.getValue(), ServerConfig.DEFAULT_MAX_BLOCKS_PER_AXIS);
        PacketHandler.sendToServer(new UpdateServerConfigC2SPacket(reach, axis));
        onClose();
    }

    private int parseOrDefault(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 150 << 24);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = (width - PANEL_W) / 2;
        int top = (height - PANEL_H) / 2;

        // Title
        graphics.drawCenteredString(font, title, width / 2, top + 6, 0xFFFFFF);

        // Labels
        int labelX = left + 10;
        int rowY = top + 30;
        graphics.drawString(font, Component.translatable("effortlessbuilding.config.build_mode_reach"), labelX, rowY, 0xFFFFFF);
        rowY += 28;
        graphics.drawString(font, Component.translatable("effortlessbuilding.config.max_blocks_per_axis"), labelX, rowY, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}


