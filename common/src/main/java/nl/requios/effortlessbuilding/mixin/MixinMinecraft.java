package nl.requios.effortlessbuilding.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipelineClient;
import nl.requios.effortlessbuilding.item.RandomizerToolItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    // Cancel vanilla item use (right-click) when the build pipeline should intercept.
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void onStartUseItem(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player == null || mc.level == null) return;
        if (!BuildPipelineClient.shouldInterceptPlacing()) return;
        if (mc.player.isShiftKeyDown()
                && mc.player.getMainHandItem().getItem() instanceof RandomizerToolItem) return;
        boolean sequenceActive = BuildPipelineClient.getBuildState() != null;
        if (BuildPipeline.isBuildTriggerItem(mc.player.getMainHandItem()) || sequenceActive) ci.cancel();
    }

    // Cancel vanilla block breaking (left-click on block) when the build pipeline should intercept.
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player == null || mc.level == null) return;
        if (!BuildPipelineClient.shouldInterceptBreaking()) return;
        boolean sequenceActive = BuildPipelineClient.getBuildState() != null;
        if (!sequenceActive && !BuildPipeline.isBuildTriggerItem(mc.player.getMainHandItem())) return;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    // Cancel vanilla hold-to-mine (continueAttack) when the build pipeline should intercept.
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void onContinueAttack(boolean leftClick, CallbackInfo ci) {
        if (!leftClick) return;
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player == null || mc.level == null) return;
        if (!BuildPipelineClient.shouldInterceptBreaking()) return;
        boolean sequenceActive = BuildPipelineClient.getBuildState() != null;
        if (!sequenceActive && !BuildPipeline.isBuildTriggerItem(mc.player.getMainHandItem())) return;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            ci.cancel();
        }
    }
}
