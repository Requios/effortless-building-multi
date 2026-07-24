package nl.requios.effortlessbuilding.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
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

    private RandomizerToolData() {}

    public static List<Item> getItems(ItemStack tool) {
        List<Item> result = new ArrayList<>(SLOT_COUNT);
        ListTag tag = tool.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getList(BLOCKS_TAG, Tag.TAG_STRING);
        for (int i = 0; i < SLOT_COUNT; i++) {
            Item item = Items.AIR;
            if (i < tag.size()) {
                ResourceLocation id = ResourceLocation.tryParse(tag.getString(i));
                if (id != null) item = BuiltInRegistries.ITEM.get(id);
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

    public static void setItems(ItemStack tool, List<Item> items) {
        CustomData.update(DataComponents.CUSTOM_DATA, tool, root -> {
            ListTag list = new ListTag();
            for (int i = 0; i < SLOT_COUNT; i++) {
                Item item = i < items.size() ? items.get(i) : Items.AIR;
                String id = item == null || item == Items.AIR
                        ? ""
                        : BuiltInRegistries.ITEM.getKey(item).toString();
                list.add(StringTag.valueOf(id));
            }
            root.put(BLOCKS_TAG, list);
        });
    }
}
