package nl.requios.effortlessbuilding;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.platform.InputConstants;
import nl.requios.effortlessbuilding.buildchain.BuildChain;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.modifier.ModifierSystem;
import nl.requios.effortlessbuilding.render.BlockPreviewRenderer;
import nl.requios.effortlessbuilding.screen.ModifiersScreen;
import nl.requios.effortlessbuilding.screen.RadialMenu;
import org.lwjgl.glfw.GLFW;

public class EffortlessBuildingClient implements ClientModInitializer {

    public static KeyMapping openModifiersScreen;
    private static boolean prevRightDown = false;
    private static boolean prevLeftDown = false;

    @Override
    public void onInitializeClient() {
        openModifiersScreen = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.effortlessbuilding.open_modifiers_screen",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_ADD,
            "key.categories.effortlessbuilding"
        ));

        BuildChain.CLIENT.addSystem(ModifierSystem.CLIENT);
        // SERVER shares the same JVM in singleplayer, so it will see the same modifier list.
        BuildChain.SERVER.addSystem(ModifierSystem.CLIENT);

        HudRenderCallback.EVENT.register((graphics, tickCounter) ->
                BlockPreviewRenderer.renderSubtitle(graphics));

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (context.consumers() == null || context.matrixStack() == null) return;
            var camPos = context.camera().getPosition();
            BlockPreviewRenderer.render(
                    context.matrixStack(),
                    (MultiBufferSource.BufferSource) context.consumers(),
                    camPos.x, camPos.y, camPos.z);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openModifiersScreen.consumeClick()) {
                Minecraft.getInstance().setScreen(new ModifiersScreen());
            }

            if (client.screen == null) {
                long window = client.getWindow().getWindow();
                boolean altHeld = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT) ||
                                  InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
                if (altHeld) {
                    Minecraft.getInstance().setScreen(RadialMenu.instance);
                }

                if (client.player != null && client.level != null && BuildModes.CLIENT.getBuildMode() != BuildModeEnum.DISABLED) {
                    boolean rightDown = client.options.keyUse.isDown();
                    boolean leftDown = client.options.keyAttack.isDown();
                    boolean rightJustPressed = rightDown && !prevRightDown;
                    boolean leftJustPressed = leftDown && !prevLeftDown;

                    if (rightJustPressed) {
                        if (BuildChain.getBuildState() == BuildChain.BuildState.BREAKING) {
                            BuildChain.cancelCurrentSequence();
                        } else if (BuildChain.isBuildTriggerItem(client.player.getMainHandItem())
                                || BuildChain.getBuildState() == BuildChain.BuildState.PLACING) {
                            BuildChain.handleRightClick(Minecraft.getInstance());
                        }
                        // else: non-placeable item, no sequence → vanilla handles it
                    }
                    if (leftJustPressed) {
                        if (BuildChain.getBuildState() == BuildChain.BuildState.PLACING) {
                            BuildChain.cancelCurrentSequence();
                        } else {
                            BuildChain.handleLeftClick(Minecraft.getInstance());
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
