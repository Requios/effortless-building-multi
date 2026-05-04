package nl.requios.effortlessbuilding.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import nl.requios.effortlessbuilding.config.ServerConfig;
import nl.requios.effortlessbuilding.modifier.ArrayModifier;
import nl.requios.effortlessbuilding.modifier.IModifier;
import nl.requios.effortlessbuilding.modifier.MirrorModifier;
import nl.requios.effortlessbuilding.modifier.RadialMirrorModifier;

import static nl.requios.effortlessbuilding.screen.ScreenWidgets.*;

/**
 * Builds and renders the right-side settings panel for a selected modifier.
 */
public class ModifierSettingsPanel {

    private final ScreenWidgets widgets;
    private final Runnable rebuildWidgets;

    public ModifierSettingsPanel(ScreenWidgets widgets, Runnable rebuildWidgets) {
        this.widgets = widgets;
        this.rebuildWidgets = rebuildWidgets;
    }

    /** Creates widgets for the selected modifier's settings. */
    public void buildWidgets(IModifier modifier, int sx, int sy) {
        var player = Minecraft.getInstance().player;
        int maxMirrorSize = player != null ? ServerConfig.INSTANCE.getMaxMirrorSize(player) : 256;
        int maxArrayCount = player != null ? ServerConfig.INSTANCE.getMaxArrayCount(player) : 64;
        int maxArrayOffset = player != null ? ServerConfig.INSTANCE.getMaxArrayOffset(player) : 64;

        if (modifier instanceof MirrorModifier mirror) {
            buildMirrorWidgets(mirror, sx, sy, maxMirrorSize);
        } else if (modifier instanceof ArrayModifier array) {
            buildArrayWidgets(array, sx, sy, maxArrayCount, maxArrayOffset);
        } else if (modifier instanceof RadialMirrorModifier radial) {
            buildRadialWidgets(radial, sx, sy, maxMirrorSize);
        }
    }

    /** Renders labels for the selected modifier's settings. */
    public void renderLabels(GuiGraphics graphics, IModifier modifier, int sx, int sy) {
        if (modifier instanceof MirrorModifier) {
            int vecY = sy + ROW_GAP + VEC3_EXTRA_Y;
            graphics.drawString(Minecraft.getInstance().font, "Axis", sx, sy + 3, 0xFFCCCCCC);
            graphics.drawString(Minecraft.getInstance().font, "Position", sx, vecY + 4, 0xFFCCCCCC);
            graphics.drawString(Minecraft.getInstance().font, "Size", sx, vecY + ROW_GAP + 4, 0xFFCCCCCC);
            widgets.renderVec3Labels(graphics, sx, vecY);
        } else if (modifier instanceof ArrayModifier) {
            int vecY = sy + ROW_GAP + VEC3_EXTRA_Y;
            graphics.drawString(Minecraft.getInstance().font, "Count", sx, sy + 4, 0xFFCCCCCC);
            graphics.drawString(Minecraft.getInstance().font, "Offset", sx, vecY + 4, 0xFFCCCCCC);
            widgets.renderVec3Labels(graphics, sx, vecY);
        } else if (modifier instanceof RadialMirrorModifier) {
            int vecY = sy + ROW_GAP * 2 + VEC3_EXTRA_Y;
            graphics.drawString(Minecraft.getInstance().font, "Slices", sx, sy + 4, 0xFFCCCCCC);
            graphics.drawString(Minecraft.getInstance().font, "Position", sx, vecY + 4, 0xFFCCCCCC);
            graphics.drawString(Minecraft.getInstance().font, "Size", sx, vecY + ROW_GAP + 4, 0xFFCCCCCC);
            widgets.renderVec3Labels(graphics, sx, vecY);
        }
    }

    // ---- private per-modifier builders ----

    private void buildMirrorWidgets(MirrorModifier mirror, int sx, int sy, int maxSize) {
        int cbX = sx + LABEL_W;
        widgets.addCheckbox(cbX, sy + 3, "X", mirror.mirrorX,
                () -> { mirror.mirrorX = !mirror.mirrorX; rebuildWidgets.run(); });
        widgets.addCheckbox(cbX + 28, sy + 3, "Y", mirror.mirrorY,
                () -> { mirror.mirrorY = !mirror.mirrorY; rebuildWidgets.run(); });
        widgets.addCheckbox(cbX + 56, sy + 3, "Z", mirror.mirrorZ,
                () -> { mirror.mirrorZ = !mirror.mirrorZ; rebuildWidgets.run(); });

        int vecY = sy + ROW_GAP + VEC3_EXTRA_Y;
        widgets.addVec3DoubleField(sx, vecY,
                mirror.originX, mirror.originY, mirror.originZ,
                v -> mirror.originX = v, v -> mirror.originY = v, v -> mirror.originZ = v);
        widgets.addSetToPlayerButton(sx, vecY, () -> {
            var pos = playerBlockPos();
            mirror.originX = pos.getX(); mirror.originY = pos.getY(); mirror.originZ = pos.getZ();
            rebuildWidgets.run();
        });

        final int max = maxSize;
        widgets.addIntField(sx, vecY + ROW_GAP, String.valueOf(mirror.size),
                v -> mirror.size = Math.clamp(v, 1, max));
    }

    private void buildArrayWidgets(ArrayModifier array, int sx, int sy, int maxCount, int maxOffset) {
        final int countMax = maxCount;
        widgets.addIntField(sx, sy, String.valueOf(array.count),
                v -> array.count = Math.clamp(v, 0, countMax));

        int vecY = sy + ROW_GAP + VEC3_EXTRA_Y;
        final int offMax = maxOffset;
        widgets.addVec3IntField(sx, vecY,
                array.offsetX, array.offsetY, array.offsetZ,
                v -> array.offsetX = Math.clamp(v, -offMax, offMax),
                v -> array.offsetY = Math.clamp(v, -offMax, offMax),
                v -> array.offsetZ = Math.clamp(v, -offMax, offMax));
    }

    private void buildRadialWidgets(RadialMirrorModifier radial, int sx, int sy, int maxSize) {
        widgets.addIntField(sx, sy, String.valueOf(radial.slices),
                v -> radial.slices = Math.clamp(v, 2, 32));
        widgets.addCheckbox(sx, sy + ROW_GAP + 3, "Mirror slices", radial.mirrorSlices,
                () -> { radial.mirrorSlices = !radial.mirrorSlices; rebuildWidgets.run(); });

        int vecY = sy + ROW_GAP * 2 + VEC3_EXTRA_Y;
        widgets.addVec3DoubleField(sx, vecY,
                radial.originX, radial.originY, radial.originZ,
                v -> radial.originX = v, v -> radial.originY = v, v -> radial.originZ = v);
        widgets.addSetToPlayerButton(sx, vecY, () -> {
            var pos = playerBlockPos();
            radial.originX = pos.getX(); radial.originY = pos.getY(); radial.originZ = pos.getZ();
            rebuildWidgets.run();
        });

        final int max = maxSize;
        widgets.addIntField(sx, vecY + ROW_GAP, String.valueOf(radial.size),
                v -> radial.size = Math.clamp(v, 1, max));
    }

    private static BlockPos playerBlockPos() {
        var player = Minecraft.getInstance().player;
        return player != null ? player.blockPosition() : BlockPos.ZERO;
    }
}
