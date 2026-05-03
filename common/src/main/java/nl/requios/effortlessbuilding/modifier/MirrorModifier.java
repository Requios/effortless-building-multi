package nl.requios.effortlessbuilding.modifier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import nl.requios.effortlessbuilding.buildchain.BuildChain;
import nl.requios.effortlessbuilding.config.ServerConfig;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors the block set across one or more axis-aligned planes.
 *
 * <p>Origins are stored as doubles to support half-block offsets (e.g. 0.5
 * places the plane on a block edge instead of through the block centre).
 *
 * <p>Each enabled axis doubles the block count; all three enabled gives 8× symmetry.
 * Blocks (both original and mirrored) that fall outside {@code size/2} from the
 * origin are removed.
 */
public class MirrorModifier extends AbstractModifier {

    public double originX, originY = 64, originZ;
    public boolean mirrorX = true, mirrorY = false, mirrorZ = false;
    /** Diameter of the working area (blocks). Half of this is the effective radius. */
    public int size = 40;

    @Override
    public Component getDisplayName() {
        return Component.literal("Mirror");
    }


    @Override
    public void processBlocks(BlockSet blocks, Player player, BuildChain.BuildState action) {
        int effectiveSize = Math.min(size, ServerConfig.INSTANCE.getMaxMirrorSize(player));
        if (mirrorX) applyAxisMirror(blocks, 0, effectiveSize);
        if (mirrorY) applyAxisMirror(blocks, 1, effectiveSize);
        if (mirrorZ) applyAxisMirror(blocks, 2, effectiveSize);
    }

    private void applyAxisMirror(BlockSet blocks, int axis, int effectiveSize) {
        double halfSize = effectiveSize / 2.0;
        List<BlockPos> snapshot = new ArrayList<>(blocks.keySet());
        for (BlockPos pos : snapshot) {
            double mx = pos.getX(), my = pos.getY(), mz = pos.getZ();
            switch (axis) {
                case 0 -> mx = 2 * originX - pos.getX() - 1;
                case 1 -> my = 2 * originY - pos.getY() - 1;
                case 2 -> mz = 2 * originZ - pos.getZ() - 1;
            }
            BlockPos mirrored = BlockPos.containing(mx, my, mz);
            if (mirrored.equals(pos)) continue;

            // Skip mirrored copy if it falls outside the effective area (Chebyshev distance).
            // size=40 → halfSize=20 → a 40×40×40 working cube centred on the origin.
            double dx = Math.abs(mirrored.getX() + 0.5 - originX);
            double dy = Math.abs(mirrored.getY() + 0.5 - originY);
            double dz = Math.abs(mirrored.getZ() + 0.5 - originZ);
            if (dx > halfSize || dy > halfSize || dz > halfSize) continue;

            BlockEntry entry = new BlockEntry(mirrored);
            BlockEntry original = blocks.get(pos);
            if (original != null) {
                entry.copyRotationSettingsFrom(original);
                if (axis == 0) entry.mirrorX = !entry.mirrorX;
                else if (axis == 1) entry.mirrorY = !entry.mirrorY;
                else entry.mirrorZ = !entry.mirrorZ;
            }
            blocks.add(entry);
        }
    }
}
