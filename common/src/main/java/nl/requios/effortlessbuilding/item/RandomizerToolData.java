package nl.requios.effortlessbuilding.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;

/** Reads and writes the nine non-consuming placeholder slots stored on a randomizer tool. */
public final class RandomizerToolData {
    public static final int SLOT_COUNT = 9;
    private static final String BLOCKS_TAG = "RandomizerBlocks";
    private static final String RATIOS_TAG = "RandomizerRatios";

    private RandomizerToolData() {}

    public static List<Item> getItems(ItemStack tool) {
        List<Item> result = new ArrayList<>(SLOT_COUNT);
        ListTag tag = tool.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getListOrEmpty(BLOCKS_TAG);
        for (int i = 0; i < SLOT_COUNT; i++) {
            Item item = Items.AIR;
            if (i < tag.size()) {
                ResourceLocation id = ResourceLocation.tryParse(tag.getStringOr(i, ""));
                if (id != null) item = BuiltInRegistries.ITEM.get(id)
                        .map(reference -> reference.value())
                        .orElse(Items.AIR);
            }
            result.add(item);
        }
        return result;
    }

    public static List<ItemStack> getStacks(ItemStack tool) {
        return getItems(tool).stream()
                .map(item -> item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item))
                .toList();
    }

    /** Returns the selection weight of every palette slot. */
    public static List<Integer> getRatios(ItemStack tool) {
        var root = tool.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        Tag storedRatios = root.get(RATIOS_TAG);
        ListTag tag = storedRatios instanceof ListTag list ? list : new ListTag();
        int[] ratioArray = storedRatios instanceof IntArrayTag ? root.getIntArray(RATIOS_TAG).orElse(null) : null;
        List<Integer> result = new ArrayList<>(SLOT_COUNT);
        List<Item> items = getItems(tool);
        boolean hasStoredRatios = ratioArray != null || storedRatios instanceof ListTag;
        for (int i = 0; i < SLOT_COUNT; i++) {
            // Existing configured tools predate ratios, so retain their previous equal odds.
            int ratio = ratioArray != null && i < ratioArray.length ? ratioArray[i]
                    : hasStoredRatios && i < tag.size() ? tag.getIntOr(i, 0)
                    : (items.get(i) == Items.AIR ? 0 : 1);
            result.add(Math.max(0, ratio));
        }
        return result;
    }

    public static void setItems(ItemStack tool, List<Item> items) {
        setConfiguration(tool, items, getRatios(tool));
    }

    /** Writes the complete palette configuration in one item-data update. */
    public static void setConfiguration(ItemStack tool, List<Item> items, List<Integer> ratios) {
        CustomData.update(DataComponents.CUSTOM_DATA, tool, root -> {
            ListTag blocks = new ListTag();
            for (int i = 0; i < SLOT_COUNT; i++) {
                Item item = i < items.size() ? items.get(i) : Items.AIR;
                String id = item == null || item == Items.AIR
                        ? ""
                        : BuiltInRegistries.ITEM.getKey(item).toString();
                blocks.add(StringTag.valueOf(id));
            }
            root.put(BLOCKS_TAG, blocks);

            int[] storedRatios = new int[SLOT_COUNT];
            for (int i = 0; i < SLOT_COUNT; i++) {
                int ratio = i < ratios.size() ? ratios.get(i) : 0;
                storedRatios[i] = Math.max(0, ratio);
            }
            root.putIntArray(RATIOS_TAG, storedRatios);
        });
    }
}
