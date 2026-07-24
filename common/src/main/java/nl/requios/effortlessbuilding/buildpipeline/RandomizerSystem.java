package nl.requios.effortlessbuilding.buildpipeline;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import nl.requios.effortlessbuilding.item.RandomizerToolData;
import nl.requios.effortlessbuilding.item.RandomizerToolItem;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;

import java.util.List;

/** Assigns a configured random block item to each final, modifier-expanded block entry. */
public final class RandomizerSystem implements IBuildSystem {
    public static final RandomizerSystem INSTANCE = new RandomizerSystem();

    private RandomizerSystem() {}

    @Override
    public void processBlocks(BlockSet blocks, Player player, BuildPipeline.BuildState action) {
        if (action != BuildPipeline.BuildState.PLACING) return;
        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof RandomizerToolItem)) return;

        List<ItemStack> choices = RandomizerToolData.getStacks(tool).stream()
                .filter(stack -> stack.getItem() instanceof BlockItem)
                .filter(BuildPipeline::isBuildTriggerItem)
                .toList();
        if (choices.isEmpty()) return;

        for (BlockEntry entry : blocks.values()) {
            ItemStack choice = choices.get(randomIndex(entry.blockPos.asLong(), choices.size()));
            BlockItem blockItem = (BlockItem) choice.getItem();
            entry.item = blockItem;
            entry.blockState = blockItem.getBlock().defaultBlockState();
        }
    }

    private static int randomIndex(long position, int size) {
        long value = position + 0x9E3779B97F4A7C15L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return Math.floorMod(value, size);
    }
}
