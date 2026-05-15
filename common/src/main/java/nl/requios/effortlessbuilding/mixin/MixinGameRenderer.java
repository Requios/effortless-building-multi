package nl.requios.effortlessbuilding.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipelineClient;
import nl.requios.effortlessbuilding.config.ServerConfig;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Extends the crosshair raytrace ({@code hitResult}) to the effective build reach
 * when a build mode is active, so that pick-block (middle-click) and the block highlight
 * work at extended range.
 *
 * In 26.1.2 the {@code pick(float)} method moved from {@code GameRenderer} to {@code Minecraft}.
 */
@Mixin(Minecraft.class)
public class MixinGameRenderer {

    @Inject(method = "pick(F)V", at = @At("TAIL"))
    private void onPick(float partialTicks, CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.player == null || minecraft.level == null) return;
        if (BuildModes.CLIENT.getBuildMode() == BuildModeEnum.DISABLED) return;

        // Extend reach for build trigger items, empty hand (breaking/pick block), or mid-sequence.
        if (!minecraft.player.getMainHandItem().isEmpty()
                && !BuildPipeline.isBuildTriggerItem(minecraft.player.getMainHandItem())
                && BuildPipelineClient.getBuildState() == null) return;

        // Only extend if the vanilla pick didn't find a block.
        if (minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.BLOCK) return;

        net.minecraft.world.phys.Vec3 start = minecraft.player.getEyePosition(partialTicks);
        net.minecraft.world.phys.Vec3 end = start.add(minecraft.player.getViewVector(partialTicks).scale(ServerConfig.INSTANCE.getReach(minecraft.player)));
        net.minecraft.world.level.ClipContext ctx = new net.minecraft.world.level.ClipContext(
                start, end, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, minecraft.player);
        net.minecraft.world.phys.BlockHitResult hit = minecraft.level.clip(ctx);
        if (hit.getType() == HitResult.Type.BLOCK) {
            minecraft.hitResult = hit;
            minecraft.crosshairPickEntity = null;
        }
    }
}
