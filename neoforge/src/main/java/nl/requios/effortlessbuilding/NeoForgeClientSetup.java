package nl.requios.effortlessbuilding;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.screen.RadialMenu;
import nl.requios.effortlessbuilding.screen.TestScreen;
import org.lwjgl.glfw.GLFW;

public class NeoForgeClientSetup {

    static KeyMapping openTestScreen;

    // Mod-bus events (RegisterKeyMappingsEvent).
    @EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            openTestScreen = new KeyMapping(
                    "key.effortlessbuilding.open_test_screen",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_KP_ADD,
                    "key.categories.effortlessbuilding"
            );
            event.register(openTestScreen);
        }
    }

    // Game-bus events (ClientTickEvent).
    @EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
    public static class GameEvents {
        private static boolean prevUseDown = false;

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            if (openTestScreen != null && openTestScreen.consumeClick()) {
                Minecraft.getInstance().setScreen(new TestScreen());
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) {
                long window = mc.getWindow().getWindow();
                boolean altHeld = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT) ||
                                  InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
                if (altHeld) {
                    mc.setScreen(RadialMenu.instance);
                }

                // Fire build-mode click on rising edge of right-click (once per press, not every held tick).
                if (mc.player != null && mc.level != null) {
                    boolean useDown = mc.options.keyUse.isDown();
                    if (useDown && !prevUseDown && BuildModes.CLIENT.getBuildMode() != BuildModeEnum.DISABLED) {
                        BuildModes.handleRightClick(mc);
                    }
                    prevUseDown = useDown;
                }
            } else {
                prevUseDown = false;
            }
        }
    }
}
