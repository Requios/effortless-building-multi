package nl.requios.effortlessbuilding.buildpipeline;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * First stage in the build pipeline: generates initial block positions from the active build mode.
 *
 * <p>On the <b>server</b>, this uses the resolved positions sent in the packet
 * (firstPos, secondPos, thirdPos) and calls {@code mode.instance.getServerBlocks(...)}.
 *
 * <p>On the <b>client</b>, this is NOT used in the pipeline — the client calls
 * {@code mode.instance.findCoordinates()} directly before running the pipeline,
 * since the client needs the multi-click state machine. The client pipeline
 * starts with the block set already populated by the build mode.
 *
 * <p>This system is only registered in the server pipeline. It reads its configuration
 * from thread-local context set before the pipeline runs.
 */
public class BuildModeSystem implements IBuildSystem {

    public static final BuildModeSystem INSTANCE = new BuildModeSystem();

    // Thread-local context set before pipeline execution on the server
    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();

    public record Context(
            BuildModeEnum mode,
            BlockPos firstPos,
            BlockPos secondPos,
            @Nullable BlockPos thirdPos,
            ModeOptions.ActionEnum fill,
            ModeOptions.ActionEnum cubeFill,
            ModeOptions.ActionEnum raisedEdge,
            ModeOptions.ActionEnum circleStart
    ) {}

    /**
     * Sets the context for the next pipeline run. Must be called before
     * {@link BuildPipeline#processBlocks} on the server.
     */
    public static void setContext(Context context) {
        CONTEXT.set(context);
    }

    /** Clears the context after pipeline execution. */
    public static void clearContext() {
        CONTEXT.remove();
    }

    @Override
    public void processBlocks(BlockSet blocks, Player player, BuildPipeline.BuildState action) {
        Context ctx = CONTEXT.get();
        if (ctx == null) return; // Client-side: blocks are pre-populated, nothing to do

        ModeOptions.applyForCalculation(ctx.fill(), ctx.cubeFill(), ctx.raisedEdge(), ctx.circleStart());

        List<BlockPos> rawPositions = ctx.mode().instance.getServerBlocks(player, ctx.firstPos(), ctx.secondPos(), ctx.thirdPos());
        if (rawPositions.isEmpty()) return;

        // Populate the block set with the mode's positions
        for (BlockPos pos : rawPositions) {
            blocks.add(new BlockEntry(pos));
        }
        if (!rawPositions.isEmpty()) {
            blocks.firstPos = rawPositions.getFirst();
            blocks.lastPos = rawPositions.getLast();
        }
    }
}

