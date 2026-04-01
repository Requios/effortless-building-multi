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
 * Rotational (radial) symmetry around a vertical (Y) axis.
 *
 * <p>The original shape is rotated {@code slices - 1} times by {@code 360 / slices} degrees
 * around the point ({@code originX}, y, {@code originZ}).
 *
 * <p>If {@code mirrorSlices} is true, a Z-mirrored copy of each rotated slice is also added,
 * producing reflective symmetry in addition to rotational symmetry.
 */
public class RadialMirrorModifier implements IModifier {

    private boolean enabled = true;
    public int originX = 0, originZ = 0;
    public int slices = 4;
    public boolean mirrorSlices = false;

    @Override
    public Component getDisplayName() {
        return Component.literal("Radial Mirror");
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
        if (slices <= 1) return;
        List<BlockPos> snapshot = new ArrayList<>(blocks.keySet());

        for (int i = 1; i < slices; i++) {
            double angle = (2 * Math.PI * i) / slices;
            addRotated(blocks, snapshot, angle, false);
        }

        if (mirrorSlices) {
            for (int i = 0; i < slices; i++) {
                double angle = (2 * Math.PI * i) / slices;
                addRotated(blocks, snapshot, angle, true);
            }
        }
    }

    private void addRotated(BlockSet blocks, List<BlockPos> snapshot, double angle, boolean mirror) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        for (BlockPos pos : snapshot) {
            double dx = pos.getX() - originX;
            double dz = pos.getZ() - originZ;
            if (mirror) dz = -dz;
            int nx = (int) Math.round(originX + dx * cos - dz * sin);
            int nz = (int) Math.round(originZ + dx * sin + dz * cos);
            BlockPos rotated = new BlockPos(nx, pos.getY(), nz);
            if (rotated.equals(pos)) continue;
            BlockEntry entry = new BlockEntry(rotated);
            BlockEntry original = blocks.get(pos);
            if (original != null) entry.copyRotationSettingsFrom(original);
            blocks.add(entry);
        }
    }
}
