package nl.requios.effortlessbuilding.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;

import java.util.*;

/**
 * Replacement for {@code MultiBufferSource} which was removed in Minecraft 26.2.
 * <p>
 * Wraps a {@link SubmitNodeCollector} and {@link PoseStack} to buffer baked quads
 * and submit them via {@link SubmitNodeCollector#submitCustomGeometry}.
 * <p>
 * For simple geometry (bounding box faces, edges, mirror planes), use
 * {@link SubmitNodeCollector#submitCustomGeometry} directly instead of this class.
 */
public class VertexConsumerProvider {

    private final SubmitNodeCollector nodeCollector;
    private final PoseStack poseStack;
    private final Map<QuadKey, List<QuadData>> quadBuffer = new HashMap<>();

    private record QuadKey(RenderType renderType, boolean isMissing) {}

    public record QuadData(PoseStack.Pose pose, BakedQuad quad, QuadInstance instance) {}

    public VertexConsumerProvider(SubmitNodeCollector nodeCollector, PoseStack poseStack) {
        this.nodeCollector = nodeCollector;
        this.poseStack = poseStack;
    }

    /**
     * Returns a {@link VertexConsumer} that buffers {@code putBakedQuad} calls.
     * All other vertex methods are no-ops since this is only used for block model rendering.
     */
    public VertexConsumer getBuffer(RenderType renderType) {
        return getBuffer(renderType, false);
    }

    /**
     * Returns a {@link VertexConsumer} that buffers {@code putBakedQuad} calls,
     * tagged with the given missing status for alpha/tint application on submit.
     */
    public VertexConsumer getBuffer(RenderType renderType, boolean isMissing) {
        QuadKey key = new QuadKey(renderType, isMissing);
        return new VertexConsumer() {
            @Override public VertexConsumer addVertex(PoseStack.Pose pose, float x, float y, float z) { return this; }
            @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
            @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
            @Override public VertexConsumer setColor(int color) { return this; }
            @Override public VertexConsumer setUv(float u, float v) { return this; }
            @Override public VertexConsumer setUv1(int u, int v) { return this; }
            @Override public VertexConsumer setUv2(int u, int v) { return this; }
            @Override public VertexConsumer setOverlay(int overlay) { return this; }
            @Override public VertexConsumer setLight(int light) { return this; }
            @Override public VertexConsumer setNormal(PoseStack.Pose pose, float nx, float ny, float nz) { return this; }
            @Override public VertexConsumer setNormal(float nx, float ny, float nz) { return this; }
            @Override public VertexConsumer setLineWidth(float width) { return this; }

            @Override
            public void putBakedQuad(PoseStack.Pose pose, BakedQuad quad, QuadInstance instance) {
                quadBuffer.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new QuadData(pose, quad, instance));
            }
        };
    }

    /**
     * Submits all buffered quads via {@link SubmitNodeCollector#submitCustomGeometry}.
     * @param alpha              alpha value for normal (non-missing) quads
     * @param missingR           red value for missing quads
     * @param missingG           green value for missing quads
     * @param missingB           blue value for missing quads
     * @param missingA           alpha value for missing quads
     */
    public void submitAll(int alpha, int missingR, int missingG, int missingB, int missingA) {
        for (var entry : quadBuffer.entrySet()) {
            QuadKey key = entry.getKey();
            List<QuadData> quads = entry.getValue();
            if (quads.isEmpty()) continue;

            nodeCollector.submitCustomGeometry(poseStack, key.renderType(), (pose, consumer) -> {
                VertexConsumer wrapped = key.isMissing()
                        ? new BlockPreviewRenderer.TintedVertexConsumer(consumer, missingR, missingG, missingB, missingA)
                        : new BlockPreviewRenderer.AlphaVertexConsumer(consumer, alpha);
                for (var qd : quads) {
                    wrapped.putBakedQuad(qd.pose(), qd.quad(), qd.instance());
                }
            });
        }
    }
}