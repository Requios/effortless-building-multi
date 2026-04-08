package nl.requios.effortlessbuilding.modifier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import nl.requios.effortlessbuilding.buildchain.BuildChain;
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
 * Blocks (both original and mirrored) that fall outside {@code radius} from the
 * origin are removed.
 */
public class MirrorModifier implements IModifier {

    private boolean enabled = true;
    public double originX = 0, originY = 64, originZ = 0;
    public boolean mirrorX = true, mirrorY = false, mirrorZ = false;
    public int radius = 20;

    @Override
    public Component getDisplayName() {
        return Component.literal("Mirror");
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void processBlocks(BlockSet blocks, Player player, BuildChain.BuildState action) {
        if (mirrorX) applyAxisMirror(blocks, 0);
        if (mirrorY) applyAxisMirror(blocks, 1);
        if (mirrorZ) applyAxisMirror(blocks, 2);
    }

    private void applyAxisMirror(BlockSet blocks, int axis) {
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

            // Skip mirrored copy if it falls outside the radius (Manhattan / Chebyshev).
            // radius=10 → a 20×20×20 working cube centred on the origin.
            double dx = Math.abs(mirrored.getX() + 0.5 - originX);
            double dy = Math.abs(mirrored.getY() + 0.5 - originY);
            double dz = Math.abs(mirrored.getZ() + 0.5 - originZ);
            if (dx > radius || dy > radius || dz > radius) continue;

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
