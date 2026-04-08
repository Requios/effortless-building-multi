package nl.requios.effortlessbuilding;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import nl.requios.effortlessbuilding.buildchain.BuildChain;
import nl.requios.effortlessbuilding.buildchain.BuildChainClient;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.modifier.ModifierPersistence;
import nl.requios.effortlessbuilding.modifier.ModifierSystem;
import nl.requios.effortlessbuilding.render.RenderHandler;
import nl.requios.effortlessbuilding.screen.ModifiersScreen;
import nl.requios.effortlessbuilding.screen.RadialMenu;
import org.lwjgl.glfw.GLFW;

public class NeoForgeClientSetup {

    static KeyMapping openModifiersScreen;

    // Mod-bus events (RegisterKeyMappingsEvent).
    @EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            openModifiersScreen = new KeyMapping(
                    "key.effortlessbuilding.open_modifiers_screen",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_KP_ADD,
                    "key.categories.effortlessbuilding"
            );
            event.register(openModifiersScreen);
            BuildChainClient.CLIENT.addSystem(ModifierSystem.CLIENT);
            // SERVER shares the same JVM in singleplayer, so it will see the same modifier list.
            BuildChain.SERVER.addSystem(ModifierSystem.CLIENT);
            ModifierPersistence.load();
        }
    }

    // Game-bus events (ClientTickEvent).
    @EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
    public static class GameEvents {
        private static boolean prevRightDown = false;
        private static boolean prevLeftDown = false;

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            if (openModifiersScreen != null && openModifiersScreen.consumeClick()) {
                Minecraft.getInstance().setScreen(new ModifiersScreen());
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) {
                long window = mc.getWindow().getWindow();
                boolean altHeld = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT) ||
                                  InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
                if (altHeld) {
                    mc.setScreen(RadialMenu.instance);
                }

                if (mc.player != null && mc.level != null && BuildModes.CLIENT.getBuildMode() != BuildModeEnum.DISABLED) {
                    boolean rightDown = mc.options.keyUse.isDown();
                    boolean leftDown = mc.options.keyAttack.isDown();
                    boolean rightJustPressed = rightDown && !prevRightDown;
                    boolean leftJustPressed = leftDown && !prevLeftDown;

                    if (rightJustPressed) {
                        if (BuildChainClient.getBuildState() == BuildChain.BuildState.BREAKING) {
                            BuildChainClient.cancelCurrentSequence();
                        } else if (BuildChain.isBuildTriggerItem(mc.player.getMainHandItem())
                                || BuildChainClient.getBuildState() == BuildChain.BuildState.PLACING) {
                            BuildChainClient.handleRightClick(mc);
                        }
                        // else: non-placeable item, no sequence → vanilla handles it
                    }
                    if (leftJustPressed) {
                        if (BuildChainClient.getBuildState() == BuildChain.BuildState.PLACING) {
                            BuildChainClient.cancelCurrentSequence();
                        } else {
                            BuildChainClient.handleLeftClick(mc);
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
    }
}
