package nl.requios.effortlessbuilding.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import nl.requios.effortlessbuilding.modifier.IModifier;
import nl.requios.effortlessbuilding.modifier.MirrorModifier;
import nl.requios.effortlessbuilding.modifier.ModifierSystem;
import nl.requios.effortlessbuilding.modifier.RadialMirrorModifier;

/**
 * Renders in-world visualizations for active modifiers:
 * translucent mirror planes and radial boundary circles.
 */
public class ModifierRenderer {

    private static final Identifier BLANK_TEXTURE =
            Identifier.fromNamespaceAndPath("effortlessbuilding", "textures/special/blank.png");

    /** Number of line segments used to approximate a radial circle. */
    private static final int CIRCLE_SEGMENTS = 64;

    public static void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                               double camX, double camY, double camZ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        for (IModifier modifier : ModifierSystem.CLIENT.getModifiers()) {
            if (!modifier.isEnabled()) continue;

            if (modifier instanceof MirrorModifier mirror) {
                renderMirrorPlanes(poseStack, bufferSource, mirror, camX, camY, camZ);
            } else if (modifier instanceof RadialMirrorModifier radial) {
                renderRadialBoundary(poseStack, bufferSource, radial, camX, camY, camZ);
            }
        }

        // Flush all modifier visuals.
        bufferSource.endBatch(RenderTypes.entityTranslucent(BLANK_TEXTURE));
    }

    // =========================================================================
    // Mirror planes
    // =========================================================================

    private static void renderMirrorPlanes(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                            MirrorModifier mirror, double camX, double camY, double camZ) {
        var consumer = bufferSource.getBuffer(RenderTypes.entityTranslucent(BLANK_TEXTURE));
        var pose = poseStack.last();
        int radius = mirror.size / 2;

        float ox = (float)(mirror.originX - camX);
        float oy = (float)(mirror.originY - camY);
        float oz = (float)(mirror.originZ - camZ);

        // Small offset along the plane normal to avoid z-fighting with block faces.
        float e = 0.005f;

        // X plane (red) — YZ rectangle at originX
        if (mirror.mirrorX) {
            addFace(consumer, pose,
                    ox + e, oy - radius, oz - radius,
                    ox + e, oy - radius, oz + radius,
                    ox + e, oy + radius, oz + radius,
                    ox + e, oy + radius, oz - radius,
                    1, 0, 0,
                    255, 80, 80, 50);
        }

        // Y plane (green) — XZ rectangle at originY
        if (mirror.mirrorY) {
            addFace(consumer, pose,
                    ox - radius, oy + e, oz - radius,
                    ox + radius, oy + e, oz - radius,
                    ox + radius, oy + e, oz + radius,
                    ox - radius, oy + e, oz + radius,
                    0, 1, 0,
                    80, 255, 80, 50);
        }

        // Z plane (blue) — XY rectangle at originZ
        if (mirror.mirrorZ) {
            addFace(consumer, pose,
                    ox - radius, oy - radius, oz + e,
                    ox + radius, oy - radius, oz + e,
                    ox + radius, oy + radius, oz + e,
                    ox - radius, oy + radius, oz + e,
                    0, 0, 1,
                    80, 80, 255, 50);
        }
    }

    // =========================================================================
    // Radial boundary
    // =========================================================================

    private static void renderRadialBoundary(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                              RadialMirrorModifier radial,
                                              double camX, double camY, double camZ) {
        var consumer = bufferSource.getBuffer(RenderTypes.entityTranslucent(BLANK_TEXTURE));
        var pose = poseStack.last();

        float ox = (float)(radial.originX - camX);
        float oy = (float)(radial.originY - camY);
        float oz = (float)(radial.originZ - camZ);
        float r = radial.size / 2.0f;
        float halfWidth = 0.04f;

        // Radial slice lines from center out to radius.
        for (int i = 0; i < radial.slices; i++) {
            double angle = (2 * Math.PI * i) / radial.slices;
            float dx = (float) Math.cos(angle) * r;
            float dz = (float) Math.sin(angle) * r;

            // Billboard quad for each slice line.
            // The line goes from (ox, oy, oz) to (ox+dx, oy, oz+dz) — horizontal.
            // Perpendicular in Y gives thickness.
            addFace(consumer, pose,
                    ox,      oy - halfWidth, oz,
                    ox,      oy + halfWidth, oz,
                    ox + dx, oy + halfWidth, oz + dz,
                    ox + dx, oy - halfWidth, oz + dz,
                    0, 1, 0,
                    200, 100, 255, 80);
        }

        // Circle outline at radius distance (polygon approximation).
        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            double a0 = (2 * Math.PI * i) / CIRCLE_SEGMENTS;
            double a1 = (2 * Math.PI * (i + 1)) / CIRCLE_SEGMENTS;
            float x0 = ox + (float) Math.cos(a0) * r;
            float z0 = oz + (float) Math.sin(a0) * r;
            float x1 = ox + (float) Math.cos(a1) * r;
            float z1 = oz + (float) Math.sin(a1) * r;

            // Thin horizontal quad from (x0, oy, z0) to (x1, oy, z1), thickness in Y.
            addFace(consumer, pose,
                    x0, oy - halfWidth, z0,
                    x0, oy + halfWidth, z0,
                    x1, oy + halfWidth, z1,
                    x1, oy - halfWidth, z1,
                    0, 1, 0,
                    200, 100, 255, 80);
        }
    }

    // =========================================================================
    // Vertex helpers
    // =========================================================================

    private static void addFace(VertexConsumer consumer, PoseStack.Pose pose,
                                 float x0, float y0, float z0,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float x3, float y3, float z3,
                                 float nx, float ny, float nz,
                                 int r, int g, int b, int a) {
        consumer.addVertex(pose, x0, y0, z0).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, x3, y3, z3).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, nx, ny, nz);
    }
}
