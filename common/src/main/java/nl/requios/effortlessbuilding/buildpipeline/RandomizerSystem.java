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

        List<ItemStack> slots = RandomizerToolData.getStacks(tool);
        List<Integer> ratios = RandomizerToolData.getRatios(tool);
        long totalRatio = 0;
        for (int i = 0; i < RandomizerToolData.SLOT_COUNT; i++) {
            ItemStack stack = slots.get(i);
            if (stack.isEmpty() || stack.getItem() instanceof BlockItem && BuildPipeline.isBuildTriggerItem(stack)) {
                totalRatio += ratios.get(i);
            }
        }
        if (totalRatio == 0) {
            blocks.clear();
            return;
        }

        var iterator = blocks.values().iterator();
        while (iterator.hasNext()) {
            BlockEntry entry = iterator.next();
            ItemStack choice = getChoice(slots, ratios, randomIndex(entry.blockPos.asLong(), totalRatio));
            if (choice.isEmpty()) {
                iterator.remove();
                continue;
            }
            BlockItem blockItem = (BlockItem) choice.getItem();
            entry.item = blockItem;
            entry.blockState = blockItem.getBlock().defaultBlockState();
        }
    }

    private static ItemStack getChoice(List<ItemStack> slots, List<Integer> ratios, long selection) {
        long upperBound = 0;
        for (int i = 0; i < RandomizerToolData.SLOT_COUNT; i++) {
            ItemStack stack = slots.get(i);
            if (!stack.isEmpty() && (!(stack.getItem() instanceof BlockItem)
                    || !BuildPipeline.isBuildTriggerItem(stack))) continue;
            upperBound += ratios.get(i);
            if (selection < upperBound) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static long randomIndex(long position, long size) {
        long value = position + 0x9E3779B97F4A7C15L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return Math.floorMod(value, size);
    }
}
