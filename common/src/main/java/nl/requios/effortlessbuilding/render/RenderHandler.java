package nl.requios.effortlessbuilding.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import nl.requios.effortlessbuilding.buildchain.BuildChain;
import nl.requios.effortlessbuilding.buildchain.BuildChainClient;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;

import java.util.List;

/**
 * Top-level render orchestrator.
 * Routes into {@link BlockPreviewRenderer} for block visuals and
 * {@link ModifierRenderer} for modifier overlays, while owning the
 * non-visual feedback (sound effects, action-bar messages, subtitle).
 */
public class RenderHandler {

    private static int lastPreviewSize = 0;

    private static final Component PLACING_TEXT = Component.literal("Left-click to ")
            .withStyle(ChatFormatting.WHITE)
            .append(Component.literal("cancel").withStyle(ChatFormatting.DARK_AQUA))
            .append(Component.literal(", Right-click to ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal("place").withStyle(ChatFormatting.DARK_AQUA));

    private static final Component BREAKING_TEXT = Component.literal("Left-click to ")
            .withStyle(ChatFormatting.WHITE)
            .append(Component.literal("break").withStyle(ChatFormatting.RED))
            .append(Component.literal(", Right-click to ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal("cancel").withStyle(ChatFormatting.RED));

    // =========================================================================
    // World rendering entry point (called from loader hooks)
    // =========================================================================

    public static void onRenderLevel(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                      double camX, double camY, double camZ) {
        // Always render modifier overlays (mirrors, radial boundaries).
        ModifierRenderer.render(poseStack, bufferSource, camX, camY, camZ);

        // Block preview + feedback
        BlockPreviewRenderer.render(poseStack, bufferSource, camX, camY, camZ);
    }

    // =========================================================================
    // HUD rendering entry point
    // =========================================================================

    public static void onRenderGui(GuiGraphics graphics) {
        renderSubtitle(graphics);
    }

    // =========================================================================
    // Sound + action-bar feedback (called from BlockPreviewRenderer)
    // =========================================================================

    /**
     * Called by {@link BlockPreviewRenderer#render} each frame with the
     * current preview positions so that sound + action-bar stay in sync.
     */
    static void updateFeedback(List<BlockPos> positions, boolean sequenceActive,
                                BuildChain.BuildState pendingAction) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        boolean isBreaking = pendingAction == BuildChain.BuildState.BREAKING;

        // Sound tick on preview size change.
        if (sequenceActive && positions.size() != lastPreviewSize) {
            SoundType soundType = isBreaking
                    ? mc.level.getBlockState(positions.getFirst()).getSoundType()
                    : (mc.player.getMainHandItem().getItem() instanceof BlockItem blockItem
                            ? blockItem.getBlock().defaultBlockState().getSoundType()
                            : SoundType.STONE);
            var sound = isBreaking ? soundType.getBreakSound() : soundType.getPlaceSound();
            mc.level.playLocalSound(positions.getFirst(), sound, SoundSource.BLOCKS,
                    soundType.getVolume() * 0.25f, soundType.getPitch(), false);
        }
        lastPreviewSize = positions.size();

        // Action bar: block count + bounding-box dimensions.
        if (sequenceActive) {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : positions) {
                if (pos.getX() < minX) minX = pos.getX();
                if (pos.getX() > maxX) maxX = pos.getX();
                if (pos.getY() < minY) minY = pos.getY();
                if (pos.getY() > maxY) maxY = pos.getY();
                if (pos.getZ() < minZ) minZ = pos.getZ();
                if (pos.getZ() > maxZ) maxZ = pos.getZ();
            }
            int dx = maxX - minX + 1, dy = maxY - minY + 1, dz = maxZ - minZ + 1;
            int[] dims = java.util.Arrays.stream(new int[]{dx, dy, dz}).filter(d -> d > 1).toArray();
            String msg;
            if (dims.length <= 1) {
                msg = String.valueOf(positions.size());
            } else {
                StringBuilder sb = new StringBuilder().append(positions.size()).append(" (");
                for (int i = 0; i < dims.length; i++) {
                    if (i > 0) sb.append('\u00d7');
                    sb.append(dims[i]);
                }
                sb.append(')');
                msg = sb.toString();
            }
            mc.player.displayClientMessage(Component.literal(msg), true);
        }
    }

    static void resetPreviewSize() {
        lastPreviewSize = 0;
    }

    // =========================================================================
    // Subtitle
    // =========================================================================

    private static void renderSubtitle(GuiGraphics graphics) {
        BuildChain.BuildState pendingAction = BuildChainClient.getBuildState();
        if (pendingAction == null) return;

        Minecraft mc = Minecraft.getInstance();
        Component text = pendingAction == BuildChain.BuildState.PLACING ? PLACING_TEXT : BREAKING_TEXT;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        var font = mc.font;

        graphics.pose().pushPose();
        graphics.pose().translate(screenWidth / 2.0, screenHeight - 54, 0.0);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int w = font.width(text);
        graphics.drawString(font, text, -w / 2, -4, 0xffffffff, true);
        RenderSystem.disableBlend();
        graphics.pose().popPose();
    }
}

