package nl.requios.effortlessbuilding.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.Level;
import nl.requios.effortlessbuilding.menu.RandomizerMenu;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Marker item whose configured block palette is applied by RandomizerSystem. */
public class RandomizerToolItem extends Item {
    public RandomizerToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!player.isShiftKeyDown() || usedHand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, menuPlayer) -> new RandomizerMenu(containerId, inventory, stack),
                    Component.translatable("effortlessbuilding.screen.randomizer")));
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay,
                                Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.accept(Component.translatable("item.effortlessbuilding.randomizer_tool.when_in_hand")
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.accept(Component.translatable("item.effortlessbuilding.randomizer_tool.place_hint",
                        Component.translatable("item.effortlessbuilding.randomizer_tool.right_click").withStyle(ChatFormatting.BLUE))
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.accept(Component.translatable("item.effortlessbuilding.randomizer_tool.configure_hint",
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
