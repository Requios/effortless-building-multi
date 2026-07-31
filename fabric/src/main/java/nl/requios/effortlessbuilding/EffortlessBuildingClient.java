package nl.requios.effortlessbuilding;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import nl.requios.effortlessbuilding.config.ClientConfig;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipelineClient;
import nl.requios.effortlessbuilding.network.PacketHandler;
import nl.requios.effortlessbuilding.network.SyncModifiersS2CPacket;
import nl.requios.effortlessbuilding.network.SyncServerConfigS2CPacket;
import nl.requios.effortlessbuilding.network.SyncAE2CountS2CPacket;
import nl.requios.effortlessbuilding.network.UndoPacket;
import nl.requios.effortlessbuilding.network.RedoPacket;
import nl.requios.effortlessbuilding.render.RenderHandler;
import nl.requios.effortlessbuilding.utilities.KeyBindings;
import nl.requios.effortlessbuilding.screen.ModifiersScreen;
import nl.requios.effortlessbuilding.screen.RadialMenu;
import nl.requios.effortlessbuilding.screen.RandomizerScreen;
import nl.requios.effortlessbuilding.screen.RandomizerTooltipComponent;
import nl.requios.effortlessbuilding.item.RandomizerToolItem;
import nl.requios.effortlessbuilding.item.RandomizerTooltipData;
import nl.requios.effortlessbuilding.menu.ModMenus;
import org.lwjgl.glfw.GLFW;

public class EffortlessBuildingClient implements ClientModInitializer {

    private static final Identifier HUD_ELEMENT_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "hud");

    private static boolean prevRightDown = false;
    private static boolean prevLeftDown = false;

    @Override
    public void onInitializeClient() {
        ClientConfig.INSTANCE.load();
        MenuScreens.register(ModMenus.RANDOMIZER, RandomizerScreen::new);
        ClientTooltipComponentCallback.EVENT.register(data -> data instanceof RandomizerTooltipData randomizerData
                ? new RandomizerTooltipComponent(randomizerData) : null);

        KeyMappingHelper.registerKeyMapping(KeyBindings.openRadialMenu);
        KeyMappingHelper.registerKeyMapping(KeyBindings.openModifiersScreen);
        KeyMappingHelper.registerKeyMapping(KeyBindings.undo);
        KeyMappingHelper.registerKeyMapping(KeyBindings.redo);

        // Register client-side handler for S2C modifier sync packet
        ClientPlayNetworking.registerGlobalReceiver(SyncModifiersS2CPacket.TYPE, (payload, context) ->
                context.client().execute(() -> PacketHandler.handleSyncModifiers(payload)));

        // Register client-side handler for S2C server config sync packet
        ClientPlayNetworking.registerGlobalReceiver(SyncServerConfigS2CPacket.TYPE, (payload, context) ->
                context.client().execute(() -> PacketHandler.handleSyncServerConfig(payload)));
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, HUD_ELEMENT_ID,
                (graphics, deltaTracker) -> RenderHandler.onRenderGui(graphics));

        // Register client-side handler for AE2 count sync
        ClientPlayNetworking.registerGlobalReceiver(SyncAE2CountS2CPacket.TYPE, (payload, context) ->
                context.client().execute(() -> PacketHandler.handleSyncAE2Count(payload)));

        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(context -> {
            if (context.bufferSource() == null || context.poseStack() == null) return;
            var camPos = context.levelState().cameraRenderState.pos;
            RenderHandler.onRenderLevel(
                    context.poseStack(),
                    context.bufferSource(),
                    camPos.x, camPos.y, camPos.z);
        });

        // Cancel vanilla block breaking when the build pipeline should intercept
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!world.isClientSide()) return InteractionResult.PASS;
            if (!BuildPipelineClient.shouldInterceptBreaking()) return InteractionResult.PASS;
            if (player.getMainHandItem().isEmpty()
                    || BuildPipeline.isBuildTriggerItem(player.getMainHandItem())
                    || BuildPipelineClient.getBuildState() != null) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (KeyBindings.openModifiersScreen.consumeClick()) {
                Minecraft.getInstance().setScreen(new ModifiersScreen());
            }
            // Undo/redo keybindings — require Ctrl held
            while (KeyBindings.undo.consumeClick()) {
                if (InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                        || InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL)) {
                    PacketHandler.sendToServer(new UndoPacket());
                }
            }
            while (KeyBindings.redo.consumeClick()) {
                if (InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                        || InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL)) {
                    PacketHandler.sendToServer(new RedoPacket());
                }
            }

            if (client.screen == null) {
                if (KeyBindings.isKeyDown(KeyBindings.openRadialMenu)) {
                    Minecraft.getInstance().setScreen(RadialMenu.instance);
                }

                if (client.player != null && client.level != null && BuildPipelineClient.shouldInterceptPlacing()) {
                    boolean rightDown = client.options.keyUse.isDown();
                    boolean leftDown = client.options.keyAttack.isDown();
                    boolean rightJustPressed = rightDown && !prevRightDown;
                    boolean leftJustPressed = leftDown && !prevLeftDown;

                    if (rightJustPressed) {
                        if (client.player.isShiftKeyDown()
                                && client.player.getMainHandItem().getItem() instanceof RandomizerToolItem) {
                            // Vanilla item use opens the server-backed randomizer menu.
                        } else if (BuildPipelineClient.getBuildState() == BuildPipeline.BuildState.BREAKING) {
                            BuildPipelineClient.cancelCurrentSequence();
                        } else if (BuildPipeline.isBuildTriggerItem(client.player.getMainHandItem())
                                || BuildPipelineClient.getBuildState() == BuildPipeline.BuildState.PLACING) {
                            BuildPipelineClient.handleRightClick(Minecraft.getInstance());
                        }
                    }
                    if (leftJustPressed && BuildPipelineClient.shouldInterceptBreaking()) {
                        if (BuildPipelineClient.getBuildState() == BuildPipeline.BuildState.PLACING) {
                            BuildPipelineClient.cancelCurrentSequence();
                        } else if (client.player.getMainHandItem().isEmpty()
                                || BuildPipeline.isBuildTriggerItem(client.player.getMainHandItem())
                                || BuildPipelineClient.getBuildState() != null) {
                            BuildPipelineClient.handleLeftClick(Minecraft.getInstance());
                        }
                    }
                    prevRightDown = rightDown;
                    prevLeftDown = leftDown;
                }
            } else {
                prevRightDown = false;
                prevLeftDown = false;
            }
        });
    }
}
