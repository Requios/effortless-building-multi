package nl.requios.effortlessbuilding.screen;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

import java.util.ArrayList;
import java.util.List;

/**
 * A custom {@link GuiElementRenderState} that batches arbitrary position+color quads
 * for the radial menu. Each quad is defined by four 2D vertices with an ARGB color.
 * <p>
 * This is the 1.21.11 replacement for the old immediate-mode approach of
 * {@code BufferUploader.drawWithShader(meshData)} with the position-color shader.
 */
public class RadialMenuGuiElement implements GuiElementRenderState {

    private final Matrix3x2f pose;
    private final List<Quad> quads = new ArrayList<>();
    private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

    public RadialMenuGuiElement(Matrix3x2fc pose) {
        this.pose = new Matrix3x2f(pose);
    }

    /**
     * Adds a quad with four vertices and a single color (packed ARGB).
     */
    public void addQuad(float x0, float y0, float x1, float y1,
                        float x2, float y2, float x3, float y3,
                        int color) {
        quads.add(new Quad(x0, y0, x1, y1, x2, y2, x3, y3, color));
        // Update bounds
        updateBounds(x0, y0);
        updateBounds(x1, y1);
        updateBounds(x2, y2);
        updateBounds(x3, y3);
    }

    /**
     * Adds a quad using float RGBA components (0-1 range).
     */
    public void addQuad(float x0, float y0, float x1, float y1,
                        float x2, float y2, float x3, float y3,
                        float r, float g, float b, float a) {
        int color = ((int)(a * 255) << 24) | ((int)(r * 255) << 16)
                    | ((int)(g * 255) << 8) | (int)(b * 255);
        addQuad(x0, y0, x1, y1, x2, y2, x3, y3, color);
    }

    private void updateBounds(float x, float y) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int ixCeil = (int) Math.ceil(x);
        int iyCeil = (int) Math.ceil(y);
        if (ix < minX) minX = ix;
        if (iy < minY) minY = iy;
        if (ixCeil > maxX) maxX = ixCeil;
        if (iyCeil > maxY) maxY = iyCeil;
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        for (Quad q : quads) {
            consumer.addVertexWith2DPose(pose, q.x0, q.y0).setUv(0, 0).setColor(q.color);
            consumer.addVertexWith2DPose(pose, q.x1, q.y1).setUv(0, 1).setColor(q.color);
            consumer.addVertexWith2DPose(pose, q.x2, q.y2).setUv(1, 1).setColor(q.color);
            consumer.addVertexWith2DPose(pose, q.x3, q.y3).setUv(1, 0).setColor(q.color);
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public ScreenRectangle scissorArea() {
        return null;
    }

    @Override
    public ScreenRectangle bounds() {
        if (quads.isEmpty()) return ScreenRectangle.empty();
        return new ScreenRectangle(minX, minY, maxX - minX, maxY - minY);
    }

    private record Quad(float x0, float y0, float x1, float y1,
                        float x2, float y2, float x3, float y3,
                        int color) {}
}

