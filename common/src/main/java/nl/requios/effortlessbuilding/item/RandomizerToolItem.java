package nl.requios.effortlessbuilding.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.Level;
import nl.requios.effortlessbuilding.menu.RandomizerMenu;

import java.util.List;
import java.util.Optional;

/** Marker item whose configured block palette is applied by RandomizerSystem. */
public class RandomizerToolItem extends Item {
    public RandomizerToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!player.isShiftKeyDown() || usedHand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide()) {
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, menuPlayer) -> new RandomizerMenu(containerId, inventory, stack),
                    Component.translatable("effortlessbuilding.screen.randomizer")));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.effortlessbuilding.randomizer_tool.when_in_hand")
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("item.effortlessbuilding.randomizer_tool.place_hint",
                        Component.translatable("item.effortlessbuilding.randomizer_tool.right_click").withStyle(ChatFormatting.BLUE))
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("item.effortlessbuilding.randomizer_tool.configure_hint",
                        Component.translatable("item.effortlessbuilding.randomizer_tool.shift_right_click").withStyle(ChatFormatting.BLUE))
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        List<ItemStack> palette = RandomizerToolData.getStacks(stack);
        return palette.stream().anyMatch(item -> !item.isEmpty())
                ? Optional.of(new RandomizerTooltipData(palette))
                : Optional.empty();
    }
}
