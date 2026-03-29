package nl.requios.effortlessbuilding.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;

import java.util.List;

public class BlockPreviewRenderer {

    public static void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                               double camX, double camY, double camZ) {
        if (BuildModes.CLIENT.getBuildMode() == BuildModeEnum.DISABLED) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        List<BlockPos> positions = BuildModes.getPreviewPositions(mc);
        if (positions.isEmpty()) return;

        BuildModes.ClickAction pendingAction = BuildModes.getPendingAction();
        boolean isBreaking = pendingAction == BuildModes.ClickAction.BREAKING;

        // Pass 1: filled translucent blocks at 80% scale (placing only).
        // Must be a separate pass so that switching to RenderType.lines() doesn't
        // invalidate the filled-box VertexConsumer mid-loop.
        if (!isBreaking) {
            var quads = bufferSource.getBuffer(RenderType.debugFilledBox());
            for (BlockPos pos : positions) {
                double x = pos.getX() - camX;
                double y = pos.getY() - camY;
                double z = pos.getZ() - camZ;
                double m = 0.1; // margin = (1 - 0.8) / 2
                LevelRenderer.addChainedFilledBoxVertices(poseStack, quads,
                        x + m, y + m, z + m,
                        x + 1 - m, y + 1 - m, z + 1 - m,
                        0.9f, 0.9f, 0.9f, 0.5f);
            }
            bufferSource.endBatch(RenderType.debugFilledBox());
        }

        // Pass 2: wireframe outline.
        float or = 1f, og = isBreaking ? 0f : 1f, ob = isBreaking ? 0f : 1f;
        var lines = bufferSource.getBuffer(RenderType.lines());
        for (BlockPos pos : positions) {
            double x = pos.getX() - camX;
            double y = pos.getY() - camY;
            double z = pos.getZ() - camZ;
            AABB outline = new AABB(x - 0.002, y - 0.002, z - 0.002,
                    x + 1.002, y + 1.002, z + 1.002);
            LevelRenderer.renderLineBox(poseStack, lines, outline, or, og, ob, 1.0f);
        }
        RenderSystem.lineWidth(3.0f);
        bufferSource.endBatch(RenderType.lines());
        RenderSystem.lineWidth(1.0f);
    }
}
