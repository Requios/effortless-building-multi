package nl.requios.effortlessbuilding;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.screen.RadialMenu;
import nl.requios.effortlessbuilding.screen.TestScreen;
import org.lwjgl.glfw.GLFW;

public class EffortlessBuildingClient implements ClientModInitializer {

    public static KeyMapping openTestScreen;
    private static boolean prevUseDown = false;

    @Override
    public void onInitializeClient() {
        openTestScreen = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.effortlessbuilding.open_test_screen",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_ADD,
            "key.categories.effortlessbuilding"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openTestScreen.consumeClick()) {
                Minecraft.getInstance().setScreen(new TestScreen());
            }

            if (client.screen == null) {
                long window = client.getWindow().getWindow();
                boolean altHeld = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT) ||
                                  InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
                if (altHeld) {
                    Minecraft.getInstance().setScreen(RadialMenu.instance);
                }

                // Fire build-mode click on rising edge of right-click (once per press, not every held tick).
                if (client.player != null && client.level != null) {
                    boolean useDown = client.options.keyUse.isDown();
                    if (useDown && !prevUseDown && BuildModes.CLIENT.getBuildMode() != BuildModeEnum.DISABLED) {
                        BuildModes.handleRightClick(Minecraft.getInstance());
                    }
                    prevUseDown = useDown;
                }
            } else {
                prevUseDown = false;
            }
        });
    }
}
