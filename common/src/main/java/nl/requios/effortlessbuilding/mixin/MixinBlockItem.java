package nl.requios.effortlessbuilding.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class MixinBlockItem {

    @Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;", at = @At("RETURN"))
    private void onPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().consumesAction()) return;

        Level level = context.getLevel();
        if (level.isClientSide()) return;

        BlockPos placedPos = context.getClickedPos();
        BlockPos torchPos = placedPos.above();

        if (!level.getBlockState(torchPos).isAir()) return;

        BlockState torchState = Blocks.TORCH.defaultBlockState();
        if (torchState.canSurvive(level, torchPos)) {
            level.setBlock(torchPos, torchState, 3);
        }
    }
}
