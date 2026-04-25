package nl.requios.effortlessbuilding.buildchain;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nl.requios.effortlessbuilding.Constants;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.buildmode.BuildSettings;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.config.ClientConfig;
import nl.requios.effortlessbuilding.network.BreakBuildModePacket;
import nl.requios.effortlessbuilding.network.PacketHandler;
import nl.requios.effortlessbuilding.network.PlaceBuildModePacket;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import nl.requios.effortlessbuilding.utilities.ItemUsageTracker;
import nl.requios.effortlessbuilding.utilities.PlacedBlockTracker;
import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.material.Fluids;
import nl.requios.effortlessbuilding.mixin.BucketItemAccessor;

/**
 * Client-only counterpart to {@link BuildChain}.
 *
 * <p>This class holds the client-side {@link BuildChain} singleton, multi-click
 * sequence state, and every method that touches {@link Minecraft} or other
 * client-only types.  Keeping it separate prevents Fabric's server-side
 * classloader from pulling in {@code net.minecraft.client.*} when
 * {@link BuildChain} is loaded on the dedicated server.
 */
public class BuildChainClient {

    /** Client-side singleton — registered systems run during preview and before packet dispatch. */
    public static final BuildChain CLIENT = new BuildChain();

    /** Client-side item usage tracker — updated each frame during preview. */
    public static final ItemUsageTracker ITEM_USAGE = new ItemUsageTracker();

    // -------------------------------------------------------------------------
    // Sequence state
    // -------------------------------------------------------------------------

    @Nullable private static BuildChain.BuildState buildState = null;
    @Nullable private static BlockHitResult firstClickHit = null;

    public static @Nullable BuildChain.BuildState getBuildState() { return buildState; }
    public static @Nullable BlockHitResult getFirstClickHit() { return firstClickHit; }

    // -------------------------------------------------------------------------
    // Entry points — called by platform-specific client tick handlers
    // -------------------------------------------------------------------------

    public static void handleRightClick(Minecraft mc) {
        handleClick(mc, BuildChain.BuildState.PLACING);
    }

    public static void handleLeftClick(Minecraft mc) {
        handleClick(mc, BuildChain.BuildState.BREAKING);
    }

    private static void handleClick(Minecraft mc, BuildChain.BuildState action) {
        BuildModeEnum mode = BuildModes.CLIENT.getBuildMode();
        Player player = mc.player;
        if (player == null || mc.level == null) return;


        BlockPos clickedPos;
        if (mode.instance.isFirstClick()) {
            Vec3 start = player.getEyePosition();
            Vec3 end = start.add(player.getLookAngle().scale(BuildModes.getBuildModeReach()));
            ClipContext ctx = new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
            BlockHitResult hit = mc.level.clip(ctx);
            if (hit.getType() != HitResult.Type.BLOCK) return;
            clickedPos = resolveFirstClickPos(hit, action, mc.level);
            buildState = action;
            firstClickHit = hit;
        } else {
            clickedPos = player.blockPosition();
        }

        BlockSet blocks = new BlockSet();
        boolean shouldPlace = mode.instance.onClick(blocks, clickedPos, player);

        if (shouldPlace) {
            mode.instance.findCoordinates(blocks, player);
            CLIENT.processBlocks(blocks, player, action);

            if (blocks.firstPos != null && blocks.lastPos != null) {
                SoundType soundType;
                if (action == BuildChain.BuildState.PLACING) {
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

                if (action == BuildChain.BuildState.PLACING) {
                    // Check for unreplaceable blocks and warn
                    if (!player.getAbilities().instabuild
                            && BuildSettings.CLIENT.getReplaceMode() != BuildSettings.ReplaceMode.ONLY_AIR) {
                        boolean hasUnreplaceable = false;
                        var dimension = mc.level.dimension();
                        for (BlockPos pos : blocks.keySet()) {
                            if (!mc.level.getBlockState(pos).canBeReplaced()
                                    && !PlacedBlockTracker.clientIsTracked(dimension, pos)) {
                                hasUnreplaceable = true;
                                break;
                            }
                        }
                        if (hasUnreplaceable) {
                            player.displayClientMessage(
                                    Component.translatable("effortlessbuilding.message.only_replace_placed"), true);
                        }
                    }
                    Direction hitFace = firstClickHit != null ? firstClickHit.getDirection() : Direction.UP;
                    Vec3 hitLocation = firstClickHit != null ? firstClickHit.getLocation() : Vec3.atCenterOf(blocks.firstPos);
                    PacketHandler.sendToServer(new PlaceBuildModePacket(
                            mode, blocks.firstPos, secondPos, thirdPos,
                            hitFace, hitLocation,
                            ModeOptions.getFill(), ModeOptions.getCubeFill(),
                            ModeOptions.getRaisedEdge(), ModeOptions.getCircleStart(),
                            BuildSettings.CLIENT.getReplaceMode(),
                            ClientConfig.INSTANCE.shouldProtectTileEntities()));
                    // Client-side placement tracking
                    PlacedBlockTracker.clientTrackAll(mc.level.dimension(), blocks.keySet());
                } else {
                    // Check for unbreakable blocks and warn
                    if (!player.getAbilities().instabuild) {
                        boolean hasUnbreakable = false;
                        for (BlockPos pos : blocks.keySet()) {
                            if (!PlacedBlockTracker.clientIsTracked(mc.level.dimension(), pos)) {
                                hasUnbreakable = true;
                                break;
                            }
                        }
                        if (hasUnbreakable) {
                            player.displayClientMessage(
                                    Component.translatable("effortlessbuilding.message.only_break_placed"), true);
                        }
                    }
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
     */
    public static BlockSet getPreviewBlocks(Minecraft mc) {
        Player player = mc.player;
        if (player == null || mc.level == null) return null;

        BuildModeEnum mode = BuildModes.CLIENT.getBuildMode();

        BlockSet result;
        if (mode != BuildModeEnum.DISABLED && !mode.instance.isFirstClick()) {
            BlockSet previewBlocks = new BlockSet();
            mode.instance.findCoordinates(previewBlocks, player);
            BuildChain.BuildState action = buildState != null ? buildState : BuildChain.BuildState.PLACING;
            CLIENT.processBlocks(previewBlocks, player, action);
            if (previewBlocks.isEmpty()) return null;
            result = previewBlocks;
        } else {
            Vec3 start = player.getEyePosition();
            Vec3 end = start.add(player.getLookAngle().scale(BuildModes.getBuildModeReach()));
            ClipContext ctx = new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
            BlockHitResult hit = mc.level.clip(ctx);
            if (hit.getType() != HitResult.Type.BLOCK) return null;
            BlockPos targetPos = resolveFirstClickPos(hit, BuildChain.BuildState.PLACING, mc.level);
            BlockSet blockSet = new BlockSet();
            blockSet.add(new BlockEntry(targetPos));
            CLIENT.processBlocks(blockSet, player, BuildChain.BuildState.PLACING);
            result = blockSet;
        }

        // Update item usage tracker for the preview
        updateItemUsage(player, result);
        return result;
    }

    /**
     * Updates the client-side {@link ItemUsageTracker} based on the current preview block set.
     */
    private static void updateItemUsage(Player player, BlockSet blockSet) {
        var held = player.getMainHandItem();
        net.minecraft.world.item.Item heldItem = null;
        if (held.getItem() instanceof BlockItem) {
            heldItem = held.getItem();
        } else if (held.getItem() instanceof BucketItem bucketItem) {
            var fluid = ((BucketItemAccessor) bucketItem).effortlessbuilding$getFluid();
            if (!fluid.isSame(Fluids.EMPTY)) {
                heldItem = held.getItem();
            }
        }

        if (heldItem != null) {
            ITEM_USAGE.compute(player, blockSet.keySet(), heldItem, player.getAbilities().instabuild);
        } else {
            ITEM_USAGE.initialize();
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
    // Internal helpers
    // -------------------------------------------------------------------------

    private static BlockPos resolveFirstClickPos(BlockHitResult hit, BuildChain.BuildState action, Level level) {
        BlockPos hitPos = hit.getBlockPos();
        if (action == BuildChain.BuildState.BREAKING) return hitPos;
        // When replacing blocks, click on the block itself instead of adjacent
        if (BuildSettings.CLIENT.shouldOffsetStartPosition()) return hitPos;
        if (level.getBlockState(hitPos).canBeReplaced()) return hitPos;
        return hitPos.relative(hit.getDirection());
    }
}

