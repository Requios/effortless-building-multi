package nl.requios.effortlessbuilding.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.material.Fluids;
import nl.requios.effortlessbuilding.mixin.BucketItemAccessor;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BlockPreviewRenderer {

    // Vanilla survival block reach; used to suppress the extended-reach outline
    // when the player is already within normal interaction range.
    private static final double VANILLA_REACH_SQ = 4.5 * 4.5;

    public static void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                               double camX, double camY, double camZ) {
        if (BuildModes.CLIENT.getBuildMode() == BuildModeEnum.DISABLED) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        boolean emptyHand = !BuildModes.isBuildTriggerItem(mc.player.getMainHandItem());
        boolean sequenceActive = BuildModes.getPendingAction() != null;

        // Empty hand with no active sequence: skip block preview, show extended-reach
        // outline only if the target block is beyond normal vanilla reach.
        if (emptyHand && !sequenceActive) {
            renderExtendedReachOutline(poseStack, bufferSource, mc, camX, camY, camZ);
            return;
        }

        List<BlockPos> positions = BuildModes.getPreviewPositions(mc);
        if (positions.isEmpty()) return;

        BuildModes.ClickAction pendingAction = BuildModes.getPendingAction();
        boolean isBreaking = pendingAction == BuildModes.ClickAction.BREAKING;

        // Pass 1: block/fluid preview (placing only).
        if (!isBreaking) {
            var held = mc.player.getMainHandItem();
            BlockState previewState = null;
            if (held.getItem() instanceof BlockItem blockItem) {
                previewState = getPlacementState(blockItem, mc);
            } else if (held.getItem() instanceof BucketItem bucketItem) {
                var fluid = ((BucketItemAccessor) bucketItem).effortlessbuilding$getFluid();
                if (!fluid.isSame(Fluids.EMPTY)) {
                    previewState = fluid.defaultFluidState().createLegacyBlock();
                }
            }
            if (previewState != null) {
                final BlockState state = previewState;
                try {
                    var wrappedSource = new AlphaMultiBufferSource(bufferSource, 160);
                    for (BlockPos pos : positions) {
                        poseStack.pushPose();
                        poseStack.translate(pos.getX() - camX, pos.getY() - camY, pos.getZ() - camZ);
                        // Scale to 80% around the block center.
                        poseStack.translate(0.5, 0.5, 0.5);
                        poseStack.scale(0.8f, 0.8f, 0.8f);
                        poseStack.translate(-0.5, -0.5, -0.5);
                        mc.getBlockRenderer().renderSingleBlock(state, poseStack, wrappedSource,
                                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                        poseStack.popPose();
                    }
                    bufferSource.endBatch(RenderType.translucent());
                } catch (Exception ignored) {
                    // Render failed; outline-only fallback handled by Pass 2.
                }
            }
        }

        // Pass 2: smart wireframe — boundary edges only (interior shared edges cancel out).
        float or = 1f, og = isBreaking ? 0f : 1f, ob = isBreaking ? 0f : 1f;
        var lines = bufferSource.getBuffer(RenderType.lines());
        var pose = poseStack.last();
        for (EdgeKey edge : computeBorderEdges(positions)) {
            double x0 = edge.x() - camX, y0 = edge.y() - camY, z0 = edge.z() - camZ;
            double x1 = x0 + (edge.axis() == 0 ? 1 : 0);
            double y1 = y0 + (edge.axis() == 1 ? 1 : 0);
            double z1 = z0 + (edge.axis() == 2 ? 1 : 0);
            float nx = edge.axis() == 0 ? 1f : 0f, ny = edge.axis() == 1 ? 1f : 0f, nz = edge.axis() == 2 ? 1f : 0f;
            lines.addVertex(pose, (float) x0, (float) y0, (float) z0).setColor(or, og, ob, 1f).setNormal(pose, nx, ny, nz);
            lines.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(or, og, ob, 1f).setNormal(pose, nx, ny, nz);
        }
        RenderSystem.lineWidth(3.0f);
        bufferSource.endBatch(RenderType.lines());
        RenderSystem.lineWidth(1.0f);
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
        BlockHitResult hit = BuildModes.getFirstClickHit();

        if (hit == null) {
            // Pre-click: raytrace to show what would be placed at the current target.
            Vec3 start = player.getEyePosition();
            Vec3 end = start.add(player.getLookAngle().scale(BuildModes.BUILD_MODE_REACH));
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

    private static void renderExtendedReachOutline(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                                    Minecraft mc, double camX, double camY, double camZ) {
        Player player = mc.player;
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(BuildModes.BUILD_MODE_REACH));
        ClipContext ctx = new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
        BlockHitResult hit = mc.level.clip(ctx);
        if (hit.getType() != HitResult.Type.BLOCK) return;

        // Only draw if the block is beyond normal vanilla reach.
        if (hit.getLocation().distanceToSqr(start) <= VANILLA_REACH_SQ) return;

        BlockPos pos = hit.getBlockPos();
        double x = pos.getX() - camX;
        double y = pos.getY() - camY;
        double z = pos.getZ() - camZ;
        AABB outline = new AABB(x - 0.002, y - 0.002, z - 0.002,
                x + 1.002, y + 1.002, z + 1.002);

        // Match vanilla: thin dark semi-transparent outline.
        var lines = bufferSource.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, lines, outline, 0.0f, 0.0f, 0.0f, 0.4f);
        bufferSource.endBatch(RenderType.lines());
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

    /**
     * Routes all render type requests to {@link RenderType#translucent()} and
     * overrides the alpha channel on every vertex so the block preview appears
     * semi-transparent. All other vertex data (position, UV, lightmap, normal)
     * is forwarded unchanged.
     */
    private static class AlphaMultiBufferSource implements MultiBufferSource {
        private final MultiBufferSource.BufferSource delegate;
        private final int alpha;

        AlphaMultiBufferSource(MultiBufferSource.BufferSource delegate, int alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return new AlphaVertexConsumer(delegate.getBuffer(RenderType.translucent()), alpha);
        }
    }

    private static class AlphaVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int alpha;

        AlphaVertexConsumer(VertexConsumer delegate, int alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            delegate.setColor(r, g, b, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float nx, float ny, float nz) {
            delegate.setNormal(nx, ny, nz);
            return this;
        }
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
