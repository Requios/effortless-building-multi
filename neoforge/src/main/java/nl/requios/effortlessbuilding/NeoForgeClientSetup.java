package nl.requios.effortlessbuilding;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import nl.requios.effortlessbuilding.config.ClientConfig;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipelineClient;
import nl.requios.effortlessbuilding.network.PacketHandler;
import nl.requios.effortlessbuilding.network.UndoPacket;
import nl.requios.effortlessbuilding.network.RedoPacket;
import nl.requios.effortlessbuilding.render.RenderHandler;
import nl.requios.effortlessbuilding.utilities.KeyBindings;
import nl.requios.effortlessbuilding.screen.ModifiersScreen;
import nl.requios.effortlessbuilding.screen.RadialMenu;
import org.lwjgl.glfw.GLFW;

public class NeoForgeClientSetup {

    // Mod-bus events (RegisterKeyMappingsEvent).
    @EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            ClientConfig.INSTANCE.load();

            event.register(KeyBindings.openModifiersScreen);
            event.register(KeyBindings.undo);
            event.register(KeyBindings.redo);
        }
    }

    // Game-bus events (ClientTickEvent).
    @EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
    public static class GameEvents {
        private static boolean prevRightDown = false;
        private static boolean prevLeftDown = false;

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            if (KeyBindings.openModifiersScreen.consumeClick()) {
                Minecraft.getInstance().setScreen(new ModifiersScreen());
            }
            // Undo/redo keybindings — require Ctrl held
            Minecraft mc = Minecraft.getInstance();
            while (KeyBindings.undo.consumeClick()) {
                if (InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                        || InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL)) {
                    PacketHandler.sendToServer(new UndoPacket());
                }
            }
            while (KeyBindings.redo.consumeClick()) {
                if (InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                        || InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL)) {
                    PacketHandler.sendToServer(new RedoPacket());
                }
            }

            if (mc.screen == null) {
                long window = mc.getWindow().getWindow();
                boolean altHeld = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT) ||
                                  InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
                if (altHeld) {
                    mc.setScreen(RadialMenu.instance);
                }

                if (mc.player != null && mc.level != null && BuildPipelineClient.shouldIntercept()) {
                    boolean rightDown = mc.options.keyUse.isDown();
                    boolean leftDown = mc.options.keyAttack.isDown();
                    boolean rightJustPressed = rightDown && !prevRightDown;
                    boolean leftJustPressed = leftDown && !prevLeftDown;

                    if (rightJustPressed) {
                        if (BuildPipelineClient.getBuildState() == BuildPipeline.BuildState.BREAKING) {
                            BuildPipelineClient.cancelCurrentSequence();
                        } else if (BuildPipeline.isBuildTriggerItem(mc.player.getMainHandItem())
                                || BuildPipelineClient.getBuildState() == BuildPipeline.BuildState.PLACING) {
                            BuildPipelineClient.handleRightClick(mc);
                        }
                    }
                    if (leftJustPressed) {
                        if (BuildPipelineClient.getBuildState() == BuildPipeline.BuildState.PLACING) {
                            BuildPipelineClient.cancelCurrentSequence();
                        } else if (mc.player.getMainHandItem().isEmpty()
                                || BuildPipeline.isBuildTriggerItem(mc.player.getMainHandItem())
                                || BuildPipelineClient.getBuildState() != null) {
                            BuildPipelineClient.handleLeftClick(mc);
                        }
                    }
                    prevRightDown = rightDown;
                    prevLeftDown = leftDown;
                }
            } else {
                prevRightDown = false;
                prevLeftDown = false;
            }
        }

        @SubscribeEvent
        public static void onRenderGui(RenderGuiEvent.Post event) {
            RenderHandler.onRenderGui(event.getGuiGraphics());
        }

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
            var camPos = event.getCamera().getPosition();
            var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
            RenderHandler.onRenderLevel(event.getPoseStack(), bufferSource,
                    camPos.x, camPos.y, camPos.z);
        }

        @SubscribeEvent
        public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
            if (!event.getEntity().level().isClientSide()) return;
            if (!BuildPipelineClient.shouldIntercept()) return;
            var player = event.getEntity();
            if (player.getMainHandItem().isEmpty()
                    || BuildPipeline.isBuildTriggerItem(player.getMainHandItem())
                    || BuildPipelineClient.getBuildState() != null) {
                event.setCanceled(true);
            }
        }
    }
}
