package nl.requios.effortlessbuilding.buildpipeline;

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
import nl.requios.effortlessbuilding.config.ServerConfig;
import nl.requios.effortlessbuilding.modifier.ModifierSystem;
import nl.requios.effortlessbuilding.network.BreakBuildModePacket;
import nl.requios.effortlessbuilding.network.PacketHandler;
import nl.requios.effortlessbuilding.network.PlaceBuildModePacket;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import nl.requios.effortlessbuilding.utilities.BlockStatus;
import nl.requios.effortlessbuilding.utilities.BreakDisplayTracker;
import nl.requios.effortlessbuilding.utilities.ItemUsageTracker;
import nl.requios.effortlessbuilding.utilities.PlacedBlockTracker;
import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.material.Fluids;
import nl.requios.effortlessbuilding.mixin.BucketItemAccessor;

/**
 * Client-side controller for the unified build pipeline.
 *
 * <p>Manages the multi-click sequence state, preview computation, and packet
 * dispatch for all build modes. When mode is DISABLED but modifiers are active,
 * the pipeline still runs (producing mirrored/arrayed copies of the single block).
 *
 * <p>Every method here touches {@link Minecraft} or other client-only types, keeping
 * them out of the server-side {@link BuildPipeline} to prevent Fabric's dedicated-server
 * classloader from pulling in {@code net.minecraft.client.*}.
 */
public class BuildPipelineClient {

    /**
     * Client-side pipeline with all stages pre-registered.
     * Pipeline order: ModifierSystem.CLIENT → ConstraintSystem
     */
    public static final BuildPipeline CLIENT = createClientPipeline();

    private static BuildPipeline createClientPipeline() {
        BuildPipeline pipeline = new BuildPipeline();
        pipeline.addSystem(ModifierSystem.CLIENT);
        pipeline.addSystem(ConstraintSystem.INSTANCE);
        return pipeline;
    }

    /** Client-side item usage tracker — updated each frame during preview rendering. */
    public static final ItemUsageTracker ITEM_USAGE = new ItemUsageTracker();

    /** Client-side break display tracker — updated each frame during breaking preview. */
    public static final BreakDisplayTracker BREAK_DISPLAY = new BreakDisplayTracker();

    // -------------------------------------------------------------------------
    // Multi-click sequence state
    // -------------------------------------------------------------------------

    @Nullable private static BuildPipeline.BuildState buildState = null;
    @Nullable private static BlockHitResult firstClickHit = null;

    public static @Nullable BuildPipeline.BuildState getBuildState() { return buildState; }
    public static @Nullable BlockHitResult getFirstClickHit() { return firstClickHit; }

    // -------------------------------------------------------------------------
    // Interception decision
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the mod should intercept vanilla click handling.
     */
    public static boolean shouldInterceptPlacing() {
        if (BuildModes.CLIENT.getBuildMode() == BuildModeEnum.DISABLED) return false;
        return true;
    }
    
    /**
     * Returns {@code true} if the mod should intercept vanilla break handling.
     */
    public static boolean shouldInterceptBreaking() {
        if (BuildModes.CLIENT.getBuildMode() == BuildModeEnum.DISABLED) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.player.getAbilities().instabuild
                && !ServerConfig.INSTANCE.survivalAllowBreaking) {
            return false;
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Click handling — called by platform-specific client tick handlers
    // -------------------------------------------------------------------------

    public static void handleRightClick(Minecraft mc) {
        handleClick(mc, BuildPipeline.BuildState.PLACING);
    }

    public static void handleLeftClick(Minecraft mc) {
        handleClick(mc, BuildPipeline.BuildState.BREAKING);
    }

    private static void handleClick(Minecraft mc, BuildPipeline.BuildState action) {
        BuildModeEnum mode = BuildModes.CLIENT.getBuildMode();
        Player player = mc.player;
        if (player == null || mc.level == null) return;
        
        BlockPos clickedPos;
        if (mode.instance.isFirstClick()) {
            Vec3 start = player.getEyePosition();
            Vec3 end = start.add(player.getLookAngle().scale(ServerConfig.INSTANCE.getReach(player)));
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
                if (action == BuildPipeline.BuildState.PLACING) {
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

                if (action == BuildPipeline.BuildState.PLACING) {
                    // Show warnings for rejected entries during placement
                    if (!blocks.rejectedEntries().isEmpty()) {
                        BlockStatus firstRejection = blocks.rejectedEntries().getFirst().getValue().getStatus();
                        if (firstRejection == BlockStatus.WORLD_BORDER) {
                            player.sendOverlayMessage(
                                    Component.translatable("effortlessbuilding.message.world_border"));
                        } else if (!player.getAbilities().instabuild) {
                            if (firstRejection == BlockStatus.NOT_PLACED_BY_PLAYER) {
                                player.sendOverlayMessage(
                                        Component.translatable("effortlessbuilding.message.only_replace_placed"));
                            } else if (firstRejection == BlockStatus.TOO_HARD) {
                                player.sendOverlayMessage(
                                        Component.translatable("effortlessbuilding.message.too_hard"));
                            } else if (firstRejection == BlockStatus.PROTECTED_TILE_ENTITY) {
                                player.sendOverlayMessage(
                                        Component.translatable("effortlessbuilding.message.protected_tile_entity"));
                            }
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
                    // Show warnings for specific rejection reasons
                    if (!blocks.rejectedEntries().isEmpty()) {
                        BlockStatus firstRejection = blocks.rejectedEntries().getFirst().getValue().getStatus();
                        if (firstRejection == BlockStatus.WORLD_BORDER) {
                            player.sendOverlayMessage(
                                    Component.translatable("effortlessbuilding.message.world_border"));
                        } else if (!player.getAbilities().instabuild) {
                            if (firstRejection == BlockStatus.NOT_PLACED_BY_PLAYER) {
                                player.sendOverlayMessage(
                                        Component.translatable("effortlessbuilding.message.only_break_placed"));
                            } else if (firstRejection == BlockStatus.TOO_HARD) {
                                player.sendOverlayMessage(
                                        Component.translatable("effortlessbuilding.message.too_hard"));
                            } else if (firstRejection == BlockStatus.PROTECTED_TILE_ENTITY) {
                                player.sendOverlayMessage(
                                        Component.translatable("effortlessbuilding.message.protected_tile_entity"));
                            }
                        }
                    }
                    PacketHandler.sendToServer(new BreakBuildModePacket(
                            mode, blocks.firstPos, secondPos, thirdPos,
                            ModeOptions.getFill(), ModeOptions.getCubeFill(),
                            ModeOptions.getRaisedEdge(), ModeOptions.getCircleStart(),
                            ClientConfig.INSTANCE.shouldProtectTileEntities()));
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
    // Preview computation
    // -------------------------------------------------------------------------

    /**
     * Computes the block set that should be highlighted in the preview this frame.
     */
    public static BlockSet getPreviewBlocks(Minecraft mc) {
        Player player = mc.player;
        if (player == null || mc.level == null) return null;

        BuildModeEnum mode = BuildModes.CLIENT.getBuildMode();

        BlockSet result;
        if (!mode.instance.isFirstClick()) {
            // Multi-click sequence in progress: show the shape being built
            BlockSet previewBlocks = new BlockSet();
            mode.instance.findCoordinates(previewBlocks, player);
            BuildPipeline.BuildState action = buildState != null ? buildState : BuildPipeline.BuildState.PLACING;
            CLIENT.processBlocks(previewBlocks, player, action);
            if (previewBlocks.isEmpty()) return null;
            previewBlocks.sortByDistance();
            previewBlocks.truncate(ServerConfig.INSTANCE.getMaxBlocksPlaced(player));
            result = previewBlocks;
        } else {
            // First-click preview: show what would happen at the look target
            Vec3 start = player.getEyePosition();
            Vec3 end = start.add(player.getLookAngle().scale(ServerConfig.INSTANCE.getReach(player)));
            ClipContext ctx = new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
            BlockHitResult hit = mc.level.clip(ctx);
            if (hit.getType() != HitResult.Type.BLOCK) return null;
            BlockPos targetPos = resolveFirstClickPos(hit, BuildPipeline.BuildState.PLACING, mc.level);
            BlockSet blockSet = new BlockSet();
            blockSet.add(new BlockEntry(targetPos));
            blockSet.firstPos = targetPos;
            blockSet.lastPos = targetPos;
            CLIENT.processBlocks(blockSet, player, BuildPipeline.BuildState.PLACING);
            result = blockSet;
        }

        // Update item usage tracker for the preview
        updateDisplayTrackers(player, result);
        return result;
    }

    /**
     * Updates the client-side trackers based on the current preview block set.
     */
    private static void updateDisplayTrackers(Player player, BlockSet blockSet) {
        BuildPipeline.BuildState action = buildState != null ? buildState : BuildPipeline.BuildState.PLACING;

        if (action == BuildPipeline.BuildState.BREAKING) {
            BREAK_DISPLAY.compute(player, blockSet);
            ITEM_USAGE.initialize();
        } else {
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
                ITEM_USAGE.compute(player, blockSet.validPositions(), heldItem, player.getAbilities().instabuild);
            } else {
                ITEM_USAGE.initialize();
            }
            BREAK_DISPLAY.initialize();
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

    private static BlockPos resolveFirstClickPos(BlockHitResult hit, BuildPipeline.BuildState action, Level level) {
        BlockPos hitPos = hit.getBlockPos();
        if (action == BuildPipeline.BuildState.BREAKING) return hitPos;
        // Tools interact with the clicked block itself, not adjacent
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getMainHandItem().has(net.minecraft.core.component.DataComponents.TOOL)) {
            return hitPos;
        }
        // When replacing blocks, click on the block itself instead of adjacent
        if (BuildSettings.CLIENT.shouldOffsetStartPosition()) return hitPos;
        if (level.getBlockState(hitPos).canBeReplaced()) return hitPos;
        return hitPos.relative(hit.getDirection());
    }
}
