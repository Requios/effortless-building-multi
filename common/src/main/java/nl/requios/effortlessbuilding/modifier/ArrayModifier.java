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
 * Repeats the block set {@code count} additional times along a fixed offset vector.
 *
 * <p>Example: count=2, offset=(5, 0, 0) produces the original shape plus two copies
 * shifted by (5, 0, 0) and (10, 0, 0) respectively.
 */
public class ArrayModifier extends AbstractModifier {

    public int count = 1;
    public int offsetX = 1, offsetY = 0, offsetZ = 0;

    @Override
    public Component getDisplayName() {
        return Component.literal("Array");
    }


    @Override
    public void processBlocks(BlockSet blocks, Player player, BuildChain.BuildState action) {
        if (count <= 0) return;
        int maxCount = ServerConfig.INSTANCE.getMaxArrayCount(player);
        int maxOffset = ServerConfig.INSTANCE.getMaxArrayOffset(player);
        int effectiveCount = Math.min(count, maxCount);
        int effOffsetX = Math.clamp(offsetX, -maxOffset, maxOffset);
        int effOffsetY = Math.clamp(offsetY, -maxOffset, maxOffset);
        int effOffsetZ = Math.clamp(offsetZ, -maxOffset, maxOffset);
        List<BlockPos> snapshot = new ArrayList<>(blocks.keySet());
        for (int i = 1; i <= effectiveCount; i++) {
            int dx = effOffsetX * i, dy = effOffsetY * i, dz = effOffsetZ * i;
            for (BlockPos pos : snapshot) {
                BlockPos copy = pos.offset(dx, dy, dz);
                BlockEntry entry = new BlockEntry(copy);
                BlockEntry original = blocks.get(pos);
                if (original != null) entry.copyRotationSettingsFrom(original);
                blocks.add(entry);
            }
        }
    }
}
