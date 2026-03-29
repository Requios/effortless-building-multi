package nl.requios.effortlessbuilding.mixin;

import net.minecraft.client.Minecraft;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    // Cancel vanilla item use entirely when a build mode is active.
    // The actual build-mode click is fired from the platform tick handler (rising-edge detection).
    // Block placement cancellation is also backed up by MixinBlockItem.
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void onStartUseItem(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player == null || mc.level == null) return;
        if (BuildModes.CLIENT.getBuildMode() == BuildModeEnum.DISABLED) return;
        ci.cancel();
    }
}
