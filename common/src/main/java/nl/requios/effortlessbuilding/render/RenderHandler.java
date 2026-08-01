package nl.requios.effortlessbuilding.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipelineClient;
import nl.requios.effortlessbuilding.utilities.ItemUsageTracker;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

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

    private static final Component INTERACTING_TEXT = Component.literal("Left-click to ")
            .withStyle(ChatFormatting.WHITE)
            .append(Component.literal("cancel").withStyle(ChatFormatting.DARK_AQUA))
            .append(Component.literal(", Right-click to ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal("interact").withStyle(ChatFormatting.DARK_AQUA));

    private static final Component BREAKING_TEXT = Component.literal("Left-click to ")
            .withStyle(ChatFormatting.WHITE)
            .append(Component.literal("break").withStyle(ChatFormatting.RED))
            .append(Component.literal(", Right-click to ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal("cancel").withStyle(ChatFormatting.RED));

    // =========================================================================
    // World rendering entry point (called from loader hooks)
    // =========================================================================

    public static void onRenderLevel(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                                      double camX, double camY, double camZ) {
        // Always render modifier overlays (mirrors, radial boundaries).
        ModifierRenderer.render(poseStack, submitNodeCollector, camX, camY, camZ);

        // Block preview + feedback
        BlockPreviewRenderer.render(poseStack, submitNodeCollector, camX, camY, camZ);
    }

    // =========================================================================
    // HUD rendering entry point
    // =========================================================================

    public static void onRenderGui(GuiGraphicsExtractor graphics) {
        renderSubtitle(graphics);
        drawStacks(graphics);
    }

    // =========================================================================
    // Sound + action-bar feedback (called from BlockPreviewRenderer)
    // =========================================================================

    /**
     * Called by {@link BlockPreviewRenderer#render} each frame with the
     * current preview positions so that sound + action-bar stay in sync.
     */
    static void updateFeedback(List<BlockPos> positions, boolean sequenceActive,
                                BuildPipeline.BuildState pendingAction) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        boolean isBreaking = pendingAction == BuildPipeline.BuildState.BREAKING;

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
                    if (i > 0) sb.append('×');
                    sb.append(dims[i]);
                }
                sb.append(')');
                msg = sb.toString();
            }

            mc.player.sendOverlayMessage(Component.literal(msg));
        }
    }

    static void resetPreviewSize() {
        lastPreviewSize = 0;
    }

    // =========================================================================
    // Item stacks overlay near cursor
    // =========================================================================

    /**
     * Draws item stacks at the crosshair showing what will be used/obtained and what is missing/rejected.
     * For placing: shows items consumed and missing items in red.
     * For breaking: shows blocks that will be broken, rejected blocks in red, and missing tool icons.
     */
    private static void drawStacks(GuiGraphicsExtractor guiGraphics) {
        var state = BuildPipelineClient.getBuildState();
        if (state == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int x = screenWidth / 2 + 10;
        int y = screenHeight / 2 - 8;

        if (state == BuildPipeline.BuildState.PLACING) {
            drawPlacingStacks(guiGraphics, mc, x, y);
        } else {
            drawBreakingStacks(guiGraphics, mc, x, y);
        }
    }

    private static void drawPlacingStacks(GuiGraphicsExtractor guiGraphics, Minecraft mc, int x, int y) {
        ItemUsageTracker tracker = BuildPipelineClient.ITEM_USAGE;
        var stacks = tracker.total;

        // Show if we are in survival or we are using multiple types of items
        if (mc.player.getAbilities().instabuild && stacks.size() <= 1) {
            return;
        }

        int i = 0;
        for (Map.Entry<Item, Integer> entry : stacks.entrySet()) {
            int total = entry.getValue();
            int have = tracker.inInventory.getOrDefault(entry.getKey(), 0);
            int networkCount = tracker.fromNetwork.getOrDefault(entry.getKey(), 0);
            int missing = tracker.getMissingCount(entry.getKey());

            int available = Math.min(total - missing, total);
            boolean usingAE2 = networkCount > 0 && have < total && missing == 0;
            if (available > 0) {
                if (usingAE2) {
                    // Single icon: combined count, green, with "AE2" suffix
                    drawItemStack(guiGraphics, new ItemStack(entry.getKey(), available),
                            x + i * 20, y, false, 0x55FF55, "AE2");
                } else {
                    // Single icon: plain inventory count, white
                    drawItemStack(guiGraphics, new ItemStack(entry.getKey(), available),
                            x + i * 20, y, false);
                }
                i++;
            }

            // Truly missing items (red) — only shown when inventory + AE2 isn't enough
            if (missing > 0) {
                drawItemStack(guiGraphics, new ItemStack(entry.getKey(), missing),
                        x + i * 20, y, true);
                i++;
            }
        }
    }

    // Overload with network indicator
    private static void drawItemStack(GuiGraphicsExtractor guiGraphics, ItemStack stack, int x, int y,
                                       boolean missing, int textColor, @Nullable String suffix) {
        guiGraphics.item(stack, x, y);

        Font font = Minecraft.getInstance().font;
        String count = String.valueOf(stack.getCount());
        String text = suffix != null ? count + suffix : count;
        int color = missing ? 0xFF5555 : textColor;
        int textX = x + 19 - 2 - font.width(text);
        int textY = y + 6 + 3;

        guiGraphics.nextStratum();
        guiGraphics.text(font, text, textX, textY, color, true);
    }

    private static void drawBreakingStacks(GuiGraphicsExtractor guiGraphics, Minecraft mc, int x, int y) {
        var tracker = BuildPipelineClient.BREAK_DISPLAY;

        // Nothing to show if no blocks
        if (tracker.breakable.isEmpty() && tracker.rejected.isEmpty()) return;

        // In creative, only show if there are rejected entries (shouldn't happen, but safety)
        if (mc.player.getAbilities().instabuild && tracker.rejected.isEmpty()) return;

        int i = 0;

        // Draw breakable blocks (white count)
        for (Map.Entry<Item, Integer> entry : tracker.breakable.entrySet()) {
            drawItemStack(guiGraphics, new ItemStack(entry.getKey(), entry.getValue()), x + i * 20, y, false);
            i++;
        }

        // Draw rejected/unbreakable blocks (red count)
        for (Map.Entry<Item, Integer> entry : tracker.rejected.entrySet()) {
            drawItemStack(guiGraphics, new ItemStack(entry.getKey(), entry.getValue()), x + i * 20, y, true);
            i++;
        }

        // Draw missing tool icons
        if (tracker.hasMissingTools()) {
            for (Item tool : tracker.missingTools) {
                drawItemStack(guiGraphics, new ItemStack(tool), x + i * 20, y, true);
                i++;
            }
        }
    }

    private static void drawItemStack(GuiGraphicsExtractor guiGraphics, ItemStack stack, int x, int y, boolean missing) {
        guiGraphics.item(stack, x, y);

        // Draw count text, red if missing
        Font font = Minecraft.getInstance().font;
        String text = String.valueOf(stack.getCount());
        int color = missing ? 0xFF5555 : 0xFFFFFF;
        int textX = x + 19 - 2 - font.width(text);
        int textY = y + 6 + 3;

        // Push above the item icon — use nextStratum for z-ordering in 1.21.11
        guiGraphics.nextStratum();
        guiGraphics.text(font, text, textX, textY, color, true);
    }

    // =========================================================================
    // Subtitle
    // =========================================================================

    private static void renderSubtitle(GuiGraphicsExtractor graphics) {
        BuildPipeline.BuildState pendingAction = BuildPipelineClient.getBuildState();
        if (pendingAction == null) return;

        Minecraft mc = Minecraft.getInstance();
        boolean isToolInteraction = pendingAction == BuildPipeline.BuildState.PLACING
                && mc.player != null
                && BuildPipeline.isToolInteractionItem(mc.player.getMainHandItem());
        Component text = pendingAction == BuildPipeline.BuildState.BREAKING
                ? BREAKING_TEXT
                : (isToolInteraction ? INTERACTING_TEXT : PLACING_TEXT);

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        var font = mc.font;

        int w = font.width(text);
        int drawX = screenWidth / 2 - w / 2;
        int drawY = screenHeight - 54 - 4;
        graphics.text(font, text, drawX, drawY, 0xffffffff, true);
    }
}
