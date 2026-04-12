package nl.requios.effortlessbuilding;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.platform.InputConstants;
import nl.requios.effortlessbuilding.buildchain.BuildChain;
import nl.requios.effortlessbuilding.buildchain.BuildChainClient;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.modifier.ModifierSystem;
import nl.requios.effortlessbuilding.network.PacketHandler;
import nl.requios.effortlessbuilding.network.SyncModifiersS2CPacket;
import nl.requios.effortlessbuilding.network.UndoPacket;
import nl.requios.effortlessbuilding.network.RedoPacket;
import nl.requios.effortlessbuilding.render.RenderHandler;
import nl.requios.effortlessbuilding.screen.KeyBindings;
import nl.requios.effortlessbuilding.screen.ModifiersScreen;
import nl.requios.effortlessbuilding.screen.RadialMenu;
import org.lwjgl.glfw.GLFW;

public class EffortlessBuildingClient implements ClientModInitializer {

    private static boolean prevRightDown = false;
    private static boolean prevLeftDown = false;

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(KeyBindings.openModifiersScreen);
        KeyBindingHelper.registerKeyBinding(KeyBindings.undo);
        KeyBindingHelper.registerKeyBinding(KeyBindings.redo);

        BuildChainClient.CLIENT.addSystem(ModifierSystem.CLIENT);

        // Register client-side handler for S2C modifier sync packet
        ClientPlayNetworking.registerGlobalReceiver(SyncModifiersS2CPacket.TYPE, (payload, context) ->
                context.client().execute(() -> PacketHandler.handleSyncModifiers(payload)));

        HudRenderCallback.EVENT.register((graphics, tickCounter) ->
                RenderHandler.onRenderGui(graphics));

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (context.consumers() == null || context.matrixStack() == null) return;
            var camPos = context.camera().getPosition();
            RenderHandler.onRenderLevel(
                    context.matrixStack(),
                    (MultiBufferSource.BufferSource) context.consumers(),
                    camPos.x, camPos.y, camPos.z);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (KeyBindings.openModifiersScreen.consumeClick()) {
                Minecraft.getInstance().setScreen(new ModifiersScreen());
            }
            // Undo/redo keybindings — require Ctrl held
            while (KeyBindings.undo.consumeClick()) {
                if (InputConstants.isKeyDown(client.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                        || InputConstants.isKeyDown(client.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL)) {
                    PacketHandler.sendToServer(new UndoPacket());
                }
            }
            while (KeyBindings.redo.consumeClick()) {
                if (InputConstants.isKeyDown(client.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                        || InputConstants.isKeyDown(client.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL)) {
                    PacketHandler.sendToServer(new RedoPacket());
                }
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
                        if (BuildChainClient.getBuildState() == BuildChain.BuildState.BREAKING) {
                            BuildChainClient.cancelCurrentSequence();
                        } else if (BuildChain.isBuildTriggerItem(client.player.getMainHandItem())
                                || BuildChainClient.getBuildState() == BuildChain.BuildState.PLACING) {
                            BuildChainClient.handleRightClick(Minecraft.getInstance());
                        }
                    }
                    if (leftJustPressed) {
                        if (BuildChainClient.getBuildState() == BuildChain.BuildState.PLACING) {
                            BuildChainClient.cancelCurrentSequence();
                        } else if (client.player.getAbilities().instabuild) {
                            BuildChainClient.handleLeftClick(Minecraft.getInstance());
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
