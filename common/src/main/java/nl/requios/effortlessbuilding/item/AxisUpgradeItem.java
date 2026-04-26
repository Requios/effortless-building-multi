package nl.requios.effortlessbuilding.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import nl.requios.effortlessbuilding.buildchain.BuildSkills;
import nl.requios.effortlessbuilding.config.ServerConfig;
import nl.requios.effortlessbuilding.network.PacketHandler;
import nl.requios.effortlessbuilding.network.SyncBuildSkillsS2CPacket;

import java.util.List;

/**
 * Consumable item that permanently increases the player's max blocks per axis by
 * {@link BuildSkills#UPGRADE_AMOUNT}, up to the server-configured maximum.
 */
public class AxisUpgradeItem extends Item {

    public AxisUpgradeItem() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;
        int effective = BuildSkills.getEffectiveAxis(serverPlayer);

        if (effective >= ServerConfig.INSTANCE.getMaxBlocksPerAxis()) {
            serverPlayer.displayClientMessage(
                    Component.translatable("effortlessbuilding.message.axis_already_max",
                            ServerConfig.INSTANCE.getMaxBlocksPerAxis()), true);
            return InteractionResultHolder.fail(stack);
        }

        BuildSkills.addAxis(serverPlayer, BuildSkills.UPGRADE_AMOUNT);
        BuildSkills.savePlayer(serverPlayer.server, serverPlayer.getUUID());

        int newEffective = BuildSkills.getEffectiveAxis(serverPlayer);
        PacketHandler.sendToClient(serverPlayer, new SyncBuildSkillsS2CPacket(
                BuildSkills.getEffectiveReach(serverPlayer), newEffective));

        if (!player.isCreative()) {
            stack.shrink(1);
        }

        serverPlayer.displayClientMessage(
                Component.translatable("effortlessbuilding.message.axis_upgraded", newEffective), true);

        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.effortlessbuilding.axis_upgrade.desc").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("item.effortlessbuilding.axis_upgrade.desc2",
                BuildSkills.UPGRADE_AMOUNT).withStyle(ChatFormatting.BLUE));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}

