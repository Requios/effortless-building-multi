package nl.requios.effortlessbuilding.buildchain;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nl.requios.effortlessbuilding.Constants;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.mixin.BucketItemAccessor;
import nl.requios.effortlessbuilding.network.BreakBuildModePacket;
import nl.requios.effortlessbuilding.network.PacketHandler;
import nl.requios.effortlessbuilding.network.PlaceBuildModePacket;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Central coordinator for build-mode placement and breaking.
 *
 * <p>The chain consists of ordered {@link IBuildSystem} stages that transform the
 * block set after {@link BuildModes} computes the initial positions:
 * <ol>
 *   <li>{@link BuildModes} — tracks click state, computes initial positions via
 *       {@link nl.requios.effortlessbuilding.buildmode.IBuildMode}</li>
 *   <li>(Future) constraint system — removes positions outside an allowed region</li>
 *   <li>(Future) modifier system — mirrors or otherwise transforms positions</li>
 * </ol>
 *
 * <p>Platform-specific client-tick handlers must call {@link #handleRightClick} and
 * {@link #handleLeftClick} instead of anything on {@link BuildModes} directly.
 */
public class BuildChain {

    /** Client-side singleton — registered systems run during preview and before packet dispatch. */
    public static final BuildChain CLIENT = new BuildChain();

    /** Server-side singleton — registered systems run on the server when a packet is received. */
    public static final BuildChain SERVER = new BuildChain();

    public enum BuildState { PLACING, BREAKING }

    private final List<IBuildSystem> systems = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Sequence state
    // -------------------------------------------------------------------------

    // Tracks whether a multi-click sequence is in progress and which button started it.
    @Nullable private static BuildChain.BuildState buildState = null;

    // The BlockHitResult from the first click — carried forward so the server packet
    // gets the correct clicked face and hit location for block rotation.
    @Nullable private static BlockHitResult firstClickHit = null;

    public static @Nullable BuildChain.BuildState getBuildState() { return buildState; }
    public static @Nullable BlockHitResult getFirstClickHit() { return firstClickHit; }

    /**
     * Returns {@code true} if right-clicking with this item should trigger the
     * build-mode sequence: block items and non-empty bucket items (water, lava, etc.).
     */
    public static boolean isBuildTriggerItem(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem) return true;
        if (stack.getItem() instanceof BucketItem) {
            return !((BucketItemAccessor) stack.getItem()).effortlessbuilding$getFluid().isSame(Fluids.EMPTY);
        }
        return false;
    }

    // Use this instead of player.getLookAngle() in any build-modes code.
    // Keeps components away from exactly 0 or ±1 to avoid division-by-zero in findXBound etc.
    public static Vec3 getPlayerLookVec(Player player) {
        Vec3 lookVec = player.getLookAngle();
        double x = lookVec.x;
        double y = lookVec.y;
        double z = lookVec.z;

        if (Math.abs(x) < 0.0001) x = 0.0001;
        if (Math.abs(x - 1.0) < 0.0001) x = 0.9999;
        if (Math.abs(x + 1.0) < 0.0001) x = -0.9999;

        if (Math.abs(y) < 0.0001) y = 0.0001;
        if (Math.abs(y - 1.0) < 0.0001) y = 0.9999;
        if (Math.abs(y + 1.0) < 0.0001) y = -0.9999;

        if (Math.abs(z) < 0.0001) z = 0.0001;
        if (Math.abs(z - 1.0) < 0.0001) z = 0.9999;
        if (Math.abs(z + 1.0) < 0.0001) z = -0.9999;

        return new Vec3(x, y, z);
    }

    // -------------------------------------------------------------------------
    // System registration
    // -------------------------------------------------------------------------

    /** Appends a system to the end of the processing pipeline. */
    public void addSystem(IBuildSystem system) {
        systems.add(system);
    }

    // -------------------------------------------------------------------------
    // Entry points — called by platform-specific client tick handlers
    // -------------------------------------------------------------------------

    public static void handleRightClick(Minecraft mc) {
        handleClick(mc, BuildState.PLACING);
    }

    public static void handleLeftClick(Minecraft mc) {
        handleClick(mc, BuildState.BREAKING);
    }

    private static void handleClick(Minecraft mc, BuildState action) {
        BuildModeEnum mode = BuildModes.CLIENT.getBuildMode();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        BlockPos clickedPos;
        if (mode.instance.isFirstClick()) {
            // First click must hit a real block (extended reach).
            Vec3 start = player.getEyePosition();
            Vec3 end = start.add(player.getLookAngle().scale(BuildModes.BUILD_MODE_REACH));
            ClipContext ctx = new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
            BlockHitResult hit = mc.level.clip(ctx);
            if (hit.getType() != HitResult.Type.BLOCK) return;
            clickedPos = resolveFirstClickPos(hit, action, mc.level);
            buildState = action;
            firstClickHit = hit;
        } else {
            // Subsequent clicks may be in the air; the mode's findCoordinates() computes
            // real positions from the player's look direction.
            clickedPos = player.blockPosition();
        }

        BlockSet blocks = new BlockSet();
        boolean shouldPlace = mode.instance.onClick(blocks, clickedPos, player);

        if (shouldPlace) {
            mode.instance.findCoordinates(blocks, player);
            CLIENT.processBlocks(blocks, player, action);

            if (blocks.firstPos != null && blocks.lastPos != null) {
                SoundType soundType;
                if (action == BuildState.PLACING) {
                    var held = player.getMainHandItem();
                    soundType = held.getItem() instanceof BlockItem blockItem
                            ? blockItem.getBlock().defaultBlockState().getSoundType()
                            : SoundType.STONE;
                    mc.level.playLocalSound(blocks.firstPos, soundType.getPlaceSound(), SoundSource.BLOCKS,
                            soundType.getVolume(), soundType.getPitch(), false);
                } else {
                    soundType = mc.level.getBlockState(blocks.firstPos).getSoundType();
                    mc.level.playLocalSound(blocks.firstPos, soundType.getBreakSound(), SoundSource.BLOCKS,
                            soundType.getVolume(), soundType.getPitch(), false);
                }

                BlockPos intermediate = mode.instance.getIntermediatePos();
                BlockPos secondPos = intermediate != null ? intermediate : blocks.lastPos;
                BlockPos thirdPos  = intermediate != null ? blocks.lastPos : null;

                if (action == BuildState.PLACING) {
                    Direction hitFace = firstClickHit != null ? firstClickHit.getDirection() : Direction.UP;
                    Vec3 hitLocation = firstClickHit != null ? firstClickHit.getLocation() : Vec3.atCenterOf(blocks.firstPos);
                    PacketHandler.sendToServer(new PlaceBuildModePacket(
                            mode, blocks.firstPos, secondPos, thirdPos,
                            hitFace, hitLocation,
                            ModeOptions.getFill(), ModeOptions.getCubeFill(),
                            ModeOptions.getRaisedEdge(), ModeOptions.getCircleStart()));
                } else {
                    PacketHandler.sendToServer(new BreakBuildModePacket(
                            mode, blocks.firstPos, secondPos, thirdPos,
                            ModeOptions.getFill(), ModeOptions.getCubeFill(),
                            ModeOptions.getRaisedEdge(), ModeOptions.getCircleStart()));
                }
            } else {
                Constants.LOG.warn("[EffortlessBuilding] Build mode {} produced no block positions", mode);
            }

            mode.instance.initialize();
            buildState = null;
            firstClickHit = null;
        }
    }

    // -------------------------------------------------------------------------
    // Preview
    // -------------------------------------------------------------------------

    /**
     * Returns the block set that should be highlighted in the preview this frame.
     * Each {@link BlockEntry} carries mirror/rotation flags so the renderer can
     * show per-block transforms matching what the server will actually place.
     */
    public static BlockSet getPreviewBlocks(Minecraft mc) {
        BuildModeEnum mode = BuildModes.CLIENT.getBuildMode();
        if (mode == BuildModeEnum.DISABLED) return null;
        Player player = mc.player;
        if (player == null || mc.level == null) return null;

        if (mode.instance.isFirstClick()) {
            // No sequence in progress — show target block via extended raytrace.
            Vec3 start = player.getEyePosition();
            Vec3 end = start.add(player.getLookAngle().scale(BuildModes.BUILD_MODE_REACH));
            ClipContext ctx = new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
            BlockHitResult hit = mc.level.clip(ctx);
            if (hit.getType() != HitResult.Type.BLOCK) return null;
            BlockSet single = new BlockSet();
            single.add(new BlockEntry(resolveFirstClickPos(hit, BuildState.PLACING, mc.level)));
            return single;
        } else {
            // Mid-sequence — compute live shape using stored clicks + current look.
            BlockSet previewBlocks = new BlockSet();
            mode.instance.findCoordinates(previewBlocks, player);
            BuildState action = buildState != null ? buildState : BuildState.PLACING;
            CLIENT.processBlocks(previewBlocks, player, action);
            if (previewBlocks.isEmpty()) return null;
            return previewBlocks;
        }
    }

    // -------------------------------------------------------------------------
    // Sequence cancellation
    // -------------------------------------------------------------------------

    public static void cancelCurrentSequence() {
        if (buildState != null) {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_OUT, 1f));
        }
        BuildModes.CLIENT.getBuildMode().instance.initialize();
        buildState = null;
        firstClickHit = null;
    }

    // -------------------------------------------------------------------------
    // Shared server-side pipeline
    // -------------------------------------------------------------------------

    /**
     * Computes and processes the full block set for a server-side build action.
     * This is the single authoritative pipeline that both place and break handlers use.
     *
     * @return the processed {@link BlockSet}, or {@code null} if the mode produced no blocks.
     */
    public @Nullable BlockSet computeServerBlocks(BuildModeEnum mode,
                                                   BlockPos firstPos, BlockPos secondPos,
                                                   @Nullable BlockPos thirdPos,
                                                   Player player, BuildState action,
                                                   ModeOptions.ActionEnum fill, ModeOptions.ActionEnum cubeFill,
                                                   ModeOptions.ActionEnum raisedEdge, ModeOptions.ActionEnum circleStart) {
        ModeOptions.applyForCalculation(fill, cubeFill, raisedEdge, circleStart);

        List<BlockPos> rawPositions = mode.instance.getServerBlocks(player, firstPos, secondPos, thirdPos);
        if (rawPositions.isEmpty()) return null;

        BlockSet blockSet = toBlockSet(rawPositions);
        processBlocks(blockSet, player, action);
        return blockSet;
    }

    /** Wraps a flat list of positions into a {@link BlockSet} for chain processing. */
    public static BlockSet toBlockSet(List<BlockPos> positions) {
        BlockSet blockSet = new BlockSet();
        for (BlockPos pos : positions) {
            blockSet.add(new BlockEntry(pos));
        }
        if (!positions.isEmpty()) {
            blockSet.firstPos = positions.getFirst();
            blockSet.lastPos = positions.getLast();
        }
        return blockSet;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Run all registered {@link IBuildSystem} stages over {@code blocks} in order. */
    public void processBlocks(BlockSet blocks, Player player, BuildState action) {
        for (IBuildSystem system : systems) {
            system.processBlocks(blocks, player, action);
        }
    }

    /**
     * Resolves the block position to use for the first click of a sequence.
     * Breaking always targets the hit block; placing targets the adjacent block,
     * unless the hit block is replaceable (tall grass, flowers, etc.).
     */
    private static BlockPos resolveFirstClickPos(BlockHitResult hit, BuildState action, Level level) {
        BlockPos hitPos = hit.getBlockPos();
        if (action == BuildState.BREAKING) return hitPos;
        if (level.getBlockState(hitPos).canBeReplaced()) return hitPos;
        return hitPos.relative(hit.getDirection());
    }

}
