package nl.requios.effortlessbuilding.item;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Palette data passed from the randomizer item to its client-side tooltip renderer. */
public record RandomizerTooltipData(List<ItemStack> stacks) implements TooltipComponent {
    public RandomizerTooltipData {
        stacks = stacks.stream().map(ItemStack::copy).toList();
    }
}
