package nl.requios.effortlessbuilding.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.material.Fluids;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.config.ClientConfig;
import nl.requios.effortlessbuilding.config.ServerConfig;
import nl.requios.effortlessbuilding.mixin.BucketItemAccessor;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.Identifier;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipelineClient;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BlockPreviewRenderer {

    private static final int FULL_BRIGHT = 0x00F000F0;


    private static final Identifier CHECKERBOARD_TEXTURE =
            Identifier.fromNamespaceAndPath("effortlessbuilding", "textures/special/checkerboard.png");

    private static final Identifier OUTLINE_TEXTURE =
            Identifier.fromNamespaceAndPath("effortlessbuilding", "textures/special/blank.png");

    // Vanilla survival block reach; used to suppress the extended-reach outline
    // when the player is already within normal interaction range.
    private static final double VANILLA_REACH_SQ = 4.5 * 4.5;

    public static void render(PoseStack poseStack, SubmitNodeCollector submitter,
                               double camX, double camY, double camZ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        boolean emptyHand = !BuildPipeline.isBuildTriggerItem(mc.player.getMainHandItem());
        boolean sequenceActive = BuildPipelineClient.getBuildState() != null;
        boolean modeActive = BuildModes.CLIENT.getBuildMode() != BuildModeEnum.DISABLED;

        BlockSet blockSet = BuildPipelineClient.getPreviewBlocks(mc);
        if (blockSet == null || blockSet.isEmpty()) {
            RenderHandler.resetPreviewSize();
            return;
        }

        // Don't show preview for a single block (the cursor target itself).
        // Only render when modifiers or the build mode produced additional copies.
        if (blockSet.size() <= 1 && !sequenceActive) {
            RenderHandler.resetPreviewSize();
            return;
        }

        List<BlockPos> positions = new ArrayList<>(blockSet.keySet());

        BuildPipeline.BuildState pendingAction = BuildPipelineClient.getBuildState();

        // Delegate sound + action-bar to RenderHandler.
        RenderHandler.updateFeedback(positions, sequenceActive, pendingAction);

        boolean isBreaking = pendingAction == BuildPipeline.BuildState.BREAKING;

        // Separate valid and rejected positions using BlockEntry status from the constraint pipeline
        List<BlockPos> breakablePositions = new ArrayList<>();
        List<BlockPos> unbreakablePositions = new ArrayList<>();
        for (BlockPos pos : positions) {
            BlockEntry entry = blockSet.get(pos);
            if (entry != null && !entry.isValid()) {
                unbreakablePositions.add(pos);
            } else {
                breakablePositions.add(pos);
            }
        }

        // Narrow the working list so block previews and wireframes only cover actionable positions
        positions = breakablePositions;

        // Pass 1: translucent block/fluid preview (placing only).
        if (!isBreaking) {
            renderGhostBlocks(poseStack, submitter, mc, blockSet, positions, camX, camY, camZ);
        }

        // Pass 2: bounding box faces with checkerboard texture.
        renderBoundingBoxFaces(poseStack, submitter, breakablePositions, camX, camY, camZ, isBreaking, false);
        if (!unbreakablePositions.isEmpty()) {
            renderBoundingBoxFaces(poseStack, submitter, unbreakablePositions, camX, camY, camZ, isBreaking, true);
        }

        // Pass 3: wireframe as camera-facing quads (GL lineWidth is unreliable on most drivers).
        float outlineWidth = 0.02f; // half-width in world units
        int oR = 255, oG = isBreaking ? 0 : 255, oB = isBreaking ? 0 : 255;

        // Separate valid positions from missing positions for different edge colors
        Set<BlockPos> missingSet = BuildPipelineClient.ITEM_USAGE.missingPositions;
        List<BlockPos> validPositions = new ArrayList<>();
        List<BlockPos> missingPositionsList = new ArrayList<>();
        for (BlockPos pos : breakablePositions) {
            if (missingSet.contains(pos)) {
                missingPositionsList.add(pos);
            } else {
                validPositions.add(pos);
            }
        }

        if (!validPositions.isEmpty()) {
            renderEdgeQuads(poseStack, submitter, computeBorderEdges(validPositions),
                    camX, camY, camZ, outlineWidth, oR, oG, oB, 255);
        }
        if (!missingPositionsList.isEmpty()) {
            renderEdgeQuads(poseStack, submitter, computeBorderEdges(missingPositionsList),
                    camX, camY, camZ, outlineWidth, 255, 0, 0, 255);
        }
        if (!unbreakablePositions.isEmpty()) {
            renderEdgeQuads(poseStack, submitter, computeBorderEdges(unbreakablePositions),
                    camX, camY, camZ, outlineWidth, 100, 100, 100, 255);
        }
    }

    private static void renderGhostBlocks(PoseStack poseStack, SubmitNodeCollector submitter, Minecraft mc,
                                          BlockSet blockSet, List<BlockPos> positions,
                                          double camX, double camY, double camZ) {
        if (positions.isEmpty()) return;

        var held = mc.player.getMainHandItem();
        BlockState baseState = null;
        if (held.getItem() instanceof BlockItem blockItem) {
            baseState = getPlacementState(blockItem, mc);
        } else if (held.getItem() instanceof BucketItem bucketItem) {
            var fluid = ((BucketItemAccessor) bucketItem).effortlessbuilding$getFluid();
            if (!fluid.isSame(Fluids.EMPTY)) {
                baseState = fluid.defaultFluidState().createLegacyBlock();
            }
        }
        if (baseState == null) return;

        int maxPreviews = ClientConfig.INSTANCE.getMaxBlockPreviews();
        if (maxPreviews <= 0) return;

        float blockScale = ClientConfig.INSTANCE.getPreviewBlockSize();
        int blockAlpha = (int) (ClientConfig.INSTANCE.getPreviewBlockTransparency() * 255);
        Set<BlockPos> missingPositions = BuildPipelineClient.ITEM_USAGE.missingPositions;

        BlockColors blockColors = mc.getBlockColors();
        BlockStateModelSet modelSet = mc.getModelManager().getBlockStateModelSet();
        ModelBlockRenderer blockRenderer = new ModelBlockRenderer(false, false, blockColors);
        BlockState baseStateForRender = baseState;

        submitter.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (pose, consumer) -> {
            int rendered = 0;
            for (BlockPos pos : positions) {
                if (rendered >= maxPreviews) break;

                BlockState state = baseStateForRender;
                BlockEntry entry = blockSet.get(pos);
                if (entry != null) {
                    state = entry.applyTransforms(state);
                }

                VertexConsumer colorConsumer = missingPositions.contains(pos)
                        ? new TintedVertexConsumer(consumer, 255, 80, 80, 200)
                        : new AlphaVertexConsumer(consumer, blockAlpha);

                float originX = (float) (pos.getX() - camX);
                float originY = (float) (pos.getY() - camY);
                float originZ = (float) (pos.getZ() - camZ);
                VertexConsumer transformed = new ScaledBlockVertexConsumer(
                        colorConsumer, originX, originY, originZ, blockScale);

                try {
                    BlockStateModel model = modelSet.get(state);
                    long seed = state.getSeed(pos);
                    BlockQuadOutput output = (qx, qy, qz, quad, instance) ->
                            transformed.putBlockBakedQuad(originX + qx, originY + qy, originZ + qz, quad, instance);
                    blockRenderer.tesselateBlock(output, 0f, 0f, 0f, mc.level, pos, state, model, seed);
                } catch (Exception ignored) {
                    // Outline and bounding-box passes still cover blocks whose model preview fails.
                }
                rendered++;
            }
        });
    }

    private static void renderBoundingBoxFaces(PoseStack poseStack, SubmitNodeCollector submitter,
                                                List<BlockPos> positions, double camX, double camY, double camZ,
                                                boolean isBreaking, boolean isUnbreakable) {
        if (positions.isEmpty()) return;
        Set<BlockPos> posSet = new HashSet<>(positions);

        int r, g, b;
        if (isUnbreakable) {
            r = 255; g = 80; b = 80;
        } else {
            r = isBreaking ? 255 : 255;
            g = isBreaking ? 0 : 255;
            b = isBreaking ? 0 : 255;
        }
        int a = isUnbreakable ? 100 : 150;
        final float eps = 0.002f;

        submitter.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(CHECKERBOARD_TEXTURE), (pose, consumer) -> {
            for (BlockPos pos : positions) {
                float x0 = (float)(pos.getX() - camX) - eps, x1 = x0 + 1 + eps * 2;
                float y0 = (float)(pos.getY() - camY) - eps, y1 = y0 + 1 + eps * 2;
                float z0 = (float)(pos.getZ() - camZ) - eps, z1 = z0 + 1 + eps * 2;

                if (!posSet.contains(pos.below()))
                    addFace(consumer, pose, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1, 0,-1,0, r,g,b,a);
                if (!posSet.contains(pos.above()))
                    addFace(consumer, pose, x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0, 0,1,0, r,g,b,a);
                if (!posSet.contains(pos.north()))
                    addFace(consumer, pose, x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0, 0,0,-1, r,g,b,a);
                if (!posSet.contains(pos.south()))
                    addFace(consumer, pose, x1,y0,z1, x1,y1,z1, x0,y1,z1, x0,y0,z1, 0,0,1, r,g,b,a);
                if (!posSet.contains(pos.west()))
                    addFace(consumer, pose, x0,y0,z1, x0,y1,z1, x0,y1,z0, x0,y0,z0, -1,0,0, r,g,b,a);
                if (!posSet.contains(pos.east()))
                    addFace(consumer, pose, x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1, 1,0,0, r,g,b,a);
            }
        });
    }

    private static void addFace(VertexConsumer consumer, PoseStack.Pose pose,
                                 float x0, float y0, float z0,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float x3, float y3, float z3,
                                 float nx, float ny, float nz, int r, int g, int b, int a) {
        consumer.addVertex(pose, x0, y0, z0).setColor(r,g,b,a).setUv(0,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, x1, y1, z1).setColor(r,g,b,a).setUv(0,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, x2, y2, z2).setColor(r,g,b,a).setUv(1,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, x3, y3, z3).setColor(r,g,b,a).setUv(1,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, nx, ny, nz);
    }

    /**
     * Renders each border edge as a camera-facing quad (billboard) with real
     * world-space thickness, since {@code RenderSystem.lineWidth()} is clamped
     * to 1 on most OpenGL core-profile drivers.
     */
    private static void renderEdgeQuads(PoseStack poseStack, SubmitNodeCollector submitter,
                                         Set<EdgeKey> edges, double camX, double camY, double camZ,
                                         float halfWidth, int r, int g, int b, int a) {
        if (edges.isEmpty()) return;

        submitter.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(OUTLINE_TEXTURE), (pose, consumer) -> {
            for (EdgeKey edge : edges) {
                float x0 = (float)(edge.x() - camX);
                float y0 = (float)(edge.y() - camY);
                float z0 = (float)(edge.z() - camZ);
                float x1 = x0 + (edge.axis() == 0 ? 1 : 0);
                float y1 = y0 + (edge.axis() == 1 ? 1 : 0);
                float z1 = z0 + (edge.axis() == 2 ? 1 : 0);

                // Edge direction (unit length, axis-aligned).
                float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;

                // Extend both endpoints by halfWidth so adjacent edges overlap at
                // corners, producing clean mitered joints instead of gaps.
                x0 -= dx * halfWidth; y0 -= dy * halfWidth; z0 -= dz * halfWidth;
                x1 += dx * halfWidth; y1 += dy * halfWidth; z1 += dz * halfWidth;

                // Midpoint is also the view direction (camera sits at origin in camera-relative space).
                float mx = (x0 + x1) * 0.5f;
                float my = (y0 + y1) * 0.5f;
                float mz = (z0 + z1) * 0.5f;


                // Perpendicular = cross(edgeDir, viewDir), normalized & scaled to halfWidth.
                float cx = dy * mz - dz * my;
                float cy = dz * mx - dx * mz;
                float cz = dx * my - dy * mx;
                float len = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
                if (len < 1e-6f) continue; // edge seen head-on — invisible
                float s = halfWidth / len;
                cx *= s; cy *= s; cz *= s;

                addFace(consumer, pose,
                        x0 - cx, y0 - cy, z0 - cz,
                        x0 + cx, y0 + cy, z0 + cz,
                        x1 + cx, y1 + cy, z1 + cz,
                        x1 - cx, y1 - cy, z1 - cz,
                        0, 1, 0,
                        r, g, b, a);
            }
        });
    }

    /**
     * Returns the block state that would actually be placed given the player's current look.
     * Raytraces to get a real {@link BlockHitResult} and feeds it into {@link BlockPlaceContext}
     * so that {@code getStateForPlacement} sees the correct clicked face and player facing.
     * Falls back to {@code defaultBlockState()} on miss or null result.
     */
    private static BlockState getPlacementState(BlockItem blockItem, Minecraft mc) {
        Player player = mc.player;

        // Mid-sequence: use the first click's hit result so that face-dependent properties
        // (log axis, upside-down stairs/slabs) match the actual placement.
        // The player object is always current, so getHorizontalDirection() (stair facing) stays live.
        BlockHitResult hit = BuildPipelineClient.getFirstClickHit();

        if (hit == null) {
            // Pre-click: raytrace to show what would be placed at the current target.
            Vec3 start = player.getEyePosition();
            Vec3 end = start.add(player.getLookAngle().scale(ServerConfig.INSTANCE.getReach(player)));
            ClipContext clipCtx = new ClipContext(start, end, ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE, player);
            hit = mc.level.clip(clipCtx);
            if (hit.getType() != HitResult.Type.BLOCK) {
                return blockItem.getBlock().defaultBlockState();
            }
        }

        BlockPlaceContext placeCtx = new OpenBlockPlaceContext(
                mc.level, player, InteractionHand.MAIN_HAND, player.getMainHandItem(), hit);
        BlockState state = blockItem.getBlock().getStateForPlacement(placeCtx);
        return state != null ? state : blockItem.getBlock().defaultBlockState();
    }

    private record EdgeKey(int axis, int x, int y, int z) {}

    /**
     * Returns only the boundary edges of the block set using a parity/toggle algorithm.
     * Each block contributes 12 edges; when two adjacent blocks share an edge it is
     * toggled twice (added then removed), so only edges with a single contribution survive.
     */
    private static Set<EdgeKey> computeBorderEdges(List<BlockPos> positions) {
        Set<EdgeKey> edges = new HashSet<>();
        for (BlockPos pos : positions) {
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();
            // 4 X-aligned edges
            toggleEdge(edges, 0, x, y,     z    );
            toggleEdge(edges, 0, x, y + 1, z    );
            toggleEdge(edges, 0, x, y,     z + 1);
            toggleEdge(edges, 0, x, y + 1, z + 1);
            // 4 Y-aligned edges
            toggleEdge(edges, 1, x,     y, z    );
            toggleEdge(edges, 1, x + 1, y, z    );
            toggleEdge(edges, 1, x,     y, z + 1);
            toggleEdge(edges, 1, x + 1, y, z + 1);
            // 4 Z-aligned edges
            toggleEdge(edges, 2, x,     y,     z);
            toggleEdge(edges, 2, x + 1, y,     z);
            toggleEdge(edges, 2, x,     y + 1, z);
            toggleEdge(edges, 2, x + 1, y + 1, z);
        }
        return edges;
    }

    private static void toggleEdge(Set<EdgeKey> edges, int axis, int x, int y, int z) {
        var key = new EdgeKey(axis, x, y, z);
        if (!edges.remove(key)) edges.add(key);
    }

    private static class ScaledBlockVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float centerX;
        private final float centerY;
        private final float centerZ;
        private final float scale;

        ScaledBlockVertexConsumer(VertexConsumer delegate, float originX, float originY, float originZ, float scale) {
            this.delegate = delegate;
            this.centerX = originX + 0.5f;
            this.centerY = originY + 0.5f;
            this.centerZ = originZ + 0.5f;
            this.scale = scale;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(
                    centerX + (x - centerX) * scale,
                    centerY + (y - centerY) * scale,
                    centerZ + (z - centerZ) * scale);
            return this;
        }

        @Override public VertexConsumer setColor(int r, int g, int b, int a) { delegate.setColor(r, g, b, a); return this; }
        @Override public VertexConsumer setColor(int color) { delegate.setColor(color); return this; }
        @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float nx, float ny, float nz) { delegate.setNormal(nx, ny, nz); return this; }
        @Override public VertexConsumer setLineWidth(float width) { delegate.setLineWidth(width); return this; }
    }

    private static class AlphaVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int alpha;

        AlphaVertexConsumer(VertexConsumer delegate, int alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) { delegate.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { delegate.setColor(r, g, b, alpha); return this; }
        @Override public VertexConsumer setColor(int color) { delegate.setColor((color & 0x00FFFFFF) | (alpha << 24)); return this; }
        @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float nx, float ny, float nz) { delegate.setNormal(nx, ny, nz); return this; }
        @Override public VertexConsumer setLineWidth(float width) { delegate.setLineWidth(width); return this; }
    }

    private static class TintedVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int r;
        private final int g;
        private final int b;
        private final int a;

        TintedVertexConsumer(VertexConsumer delegate, int r, int g, int b, int a) {
            this.delegate = delegate;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) { delegate.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int cr, int cg, int cb, int ca) { delegate.setColor(r, g, b, a); return this; }
        @Override public VertexConsumer setColor(int color) { delegate.setColor((a << 24) | (r << 16) | (g << 8) | b); return this; }
        @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float nx, float ny, float nz) { delegate.setNormal(nx, ny, nz); return this; }
        @Override public VertexConsumer setLineWidth(float width) { delegate.setLineWidth(width); return this; }
    }

    /** Exposes the protected {@link BlockPlaceContext} constructor for client-side preview use. */
    private static final class OpenBlockPlaceContext extends BlockPlaceContext {
        OpenBlockPlaceContext(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player,
                              InteractionHand hand, net.minecraft.world.item.ItemStack stack,
                              BlockHitResult hit) {
            super(level, player, hand, stack, hit);
        }
    }

}
