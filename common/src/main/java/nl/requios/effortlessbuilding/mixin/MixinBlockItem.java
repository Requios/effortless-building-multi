package nl.requios.effortlessbuilding.mixin;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class MixinBlockItem {

    // Safety net: cancel vanilla block placement whenever a build mode is active.
    // The actual placement is handled by MixinMinecraft.onStartUseItem → BuildModes.handleRightClick.
    @Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;", at = @At("HEAD"), cancellable = true)
    private void onPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (!context.getLevel().isClientSide()) return;
        if (BuildModes.CLIENT.getBuildMode() == BuildModeEnum.DISABLED) return;
        cir.setReturnValue(InteractionResult.sidedSuccess(true));
        cir.cancel();
    }
}
