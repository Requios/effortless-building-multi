package nl.requios.effortlessbuilding.modifier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Rotation;
import nl.requios.effortlessbuilding.buildchain.BuildChain;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Rotational (radial) symmetry around a vertical (Y) axis.
 *
 * <p>Origins are doubles to support half-block offsets (e.g. 0.5 places the
 * axis on a block edge).
 *
 * <p>Blocks (both original and rotated copies) that fall outside {@code radius}
 * from the origin (XZ distance) are removed.
 */
public class RadialMirrorModifier implements IModifier {

    private boolean enabled = true;
    public double originX = 0, originY = 64, originZ = 0;
    public int slices = 4;
    public boolean mirrorSlices = false;
    public int radius = 20;

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

    private void addRotated(BlockSet blocks, List<BlockPos> snapshot, double angle, boolean doMirrorZ) {
        double rSq = (double) radius * radius;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        Rotation sliceRotation = angleToRotation(angle);
        for (BlockPos pos : snapshot) {
            double dx = pos.getX() + 0.5 - originX;
            double dz = pos.getZ() + 0.5 - originZ;
            if (doMirrorZ) dz = -dz;
            double rx = originX + dx * cos - dz * sin - 0.5;
            double rz = originZ + dx * sin + dz * cos - 0.5;
            BlockPos rotated = BlockPos.containing(rx, pos.getY(), rz);
            if (rotated.equals(pos)) continue;

            // Skip rotated copy if it falls outside the radius (XZ distance).
            double rdx = rotated.getX() + 0.5 - originX;
            double rdz = rotated.getZ() + 0.5 - originZ;
            if (rdx * rdx + rdz * rdz > rSq) continue;

            BlockEntry entry = new BlockEntry(rotated);
            BlockEntry original = blocks.get(pos);
            if (original != null) {
                entry.copyRotationSettingsFrom(original);
                entry.rotation = composeRotations(entry.rotation, sliceRotation);
                if (doMirrorZ) entry.mirrorZ = !entry.mirrorZ;
            }
            blocks.add(entry);
        }
    }

    private static Rotation angleToRotation(double angle) {
        double normalized = ((angle % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
        int quarter = (int) Math.round(normalized / (Math.PI / 2)) % 4;
        return Rotation.values()[quarter];
    }

    private static Rotation composeRotations(Rotation first, Rotation second) {
        return Rotation.values()[(first.ordinal() + second.ordinal()) % 4];
    }
}
