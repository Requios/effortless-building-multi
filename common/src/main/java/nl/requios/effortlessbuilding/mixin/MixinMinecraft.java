package nl.requios.effortlessbuilding.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    // Cancel vanilla item use (right-click) when a build mode is active and the player is
    // holding a block item or is mid-sequence. Non-block items (ender pearls, food, etc.)
    // are left alone when no sequence is in progress.
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void onStartUseItem(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player == null || mc.level == null) return;
        if (BuildModes.CLIENT.getBuildMode() == BuildModeEnum.DISABLED) return;
        boolean sequenceActive = BuildModes.getPendingAction() != null;
        if (BuildModes.isBuildTriggerItem(mc.player.getMainHandItem()) || sequenceActive) ci.cancel();
    }

    // Cancel vanilla block breaking (left-click on block) when a build mode is active.
    // Only cancels for blocks; entity attacks are left alone.
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player == null || mc.level == null) return;
        if (BuildModes.CLIENT.getBuildMode() == BuildModeEnum.DISABLED) return;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}
