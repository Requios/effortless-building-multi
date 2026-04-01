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
 * <p>The mirror plane for axis X passes through x = {@code originX} (i.e. the origin block
 * lies on the plane and maps to itself; adjacent blocks are reflected symmetrically).
 * Each enabled axis doubles the block count; all three enabled gives 8× symmetry.
 */
public class MirrorModifier implements IModifier {

    private boolean enabled = true;
    public int originX = 0, originY = 64, originZ = 0;
    public boolean mirrorX = true, mirrorY = false, mirrorZ = false;

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
        // Each axis is applied to the already-grown set so combinations are covered naturally.
        if (mirrorX) applyAxisMirror(blocks, 0);
        if (mirrorY) applyAxisMirror(blocks, 1);
        if (mirrorZ) applyAxisMirror(blocks, 2);
    }

    private void applyAxisMirror(BlockSet blocks, int axis) {
        // Snapshot before iterating so newly-added entries are not reflected again.
        List<BlockPos> snapshot = new ArrayList<>(blocks.keySet());
        for (BlockPos pos : snapshot) {
            int mx = pos.getX(), my = pos.getY(), mz = pos.getZ();
            switch (axis) {
                case 0 -> mx = 2 * originX - pos.getX();
                case 1 -> my = 2 * originY - pos.getY();
                case 2 -> mz = 2 * originZ - pos.getZ();
            }
            BlockPos mirrored = new BlockPos(mx, my, mz);
            if (mirrored.equals(pos)) continue; // on the mirror plane, skip duplicate

            BlockEntry entry = new BlockEntry(mirrored);
            BlockEntry original = blocks.get(pos);
            if (original != null) {
                entry.copyRotationSettingsFrom(original);
                // Flip the relevant mirror flag so the placed block faces the right way.
                if (axis == 0) entry.mirrorX = !entry.mirrorX;
                else if (axis == 1) entry.mirrorY = !entry.mirrorY;
                else entry.mirrorZ = !entry.mirrorZ;
            }
            blocks.add(entry);
        }
    }
}
