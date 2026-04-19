package nl.requios.effortlessbuilding.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import nl.requios.effortlessbuilding.buildchain.BuildChain;
import nl.requios.effortlessbuilding.buildchain.BuildChainClient;
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
        boolean sequenceActive = BuildChainClient.getBuildState() != null;
        if (BuildChain.isBuildTriggerItem(mc.player.getMainHandItem()) || sequenceActive) ci.cancel();
    }

    // Cancel vanilla block breaking (left-click on block) when a build mode is active
    // and the player is holding a build trigger item or mid-sequence.
    // Tools and other non-build items are left alone so vanilla mining works.
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player == null || mc.level == null) return;
        if (BuildModes.CLIENT.getBuildMode() == BuildModeEnum.DISABLED) return;
        boolean sequenceActive = BuildChainClient.getBuildState() != null;
        if (!sequenceActive && !BuildChain.isBuildTriggerItem(mc.player.getMainHandItem())) return;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    // Cancel vanilla hold-to-mine (continueAttack) when a build mode is active
    // and the player is holding a build trigger item or mid-sequence.
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void onContinueAttack(boolean leftClick, CallbackInfo ci) {
        if (!leftClick) return;
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player == null || mc.level == null) return;
        if (BuildModes.CLIENT.getBuildMode() == BuildModeEnum.DISABLED) return;
        boolean sequenceActive = BuildChainClient.getBuildState() != null;
        if (!sequenceActive && !BuildChain.isBuildTriggerItem(mc.player.getMainHandItem())) return;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            ci.cancel();
        }
    }
}
