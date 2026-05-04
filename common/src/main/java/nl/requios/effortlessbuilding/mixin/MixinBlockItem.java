package nl.requios.effortlessbuilding.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipelineClient;
import nl.requios.effortlessbuilding.utilities.PlacedBlockTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(BlockItem.class)
public class MixinBlockItem {

    // Safety net: cancel vanilla block placement whenever the build pipeline should intercept.
    @Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;", at = @At("HEAD"), cancellable = true)
    private void onPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (!context.getLevel().isClientSide()) return;
        if (!BuildPipelineClient.shouldInterceptPlacing()) return;
        cir.setReturnValue(InteractionResult.SUCCESS);
        cir.cancel();
    }

    // Track vanilla single-block placements so survival players can break them with build-mode breaking.
    @Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;", at = @At("TAIL"))
    private void effortlessbuilding$trackPlacement(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult result = cir.getReturnValue();
        if (!result.consumesAction()) return;

        Player player = context.getPlayer();
        if (player == null) return;

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (level.isClientSide()) {
            PlacedBlockTracker.clientTrackAll(level.dimension(), List.of(pos));
        } else if (player instanceof ServerPlayer) {
            PlacedBlockTracker.trackAll(player.getUUID(), level.dimension(), List.of(pos));
        }
    }
}
