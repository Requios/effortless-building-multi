package nl.requios.effortlessbuilding;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.bus.api.IEventBus;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.RegisterEvent;
import nl.requios.effortlessbuilding.block.ModBlocks;
import nl.requios.effortlessbuilding.buildchain.BuildChain;
import nl.requios.effortlessbuilding.buildchain.BuildChainClient;
import nl.requios.effortlessbuilding.item.ModItems;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.network.ForgeChannel;
import nl.requios.effortlessbuilding.screen.RadialMenu;
import nl.requios.effortlessbuilding.screen.TestScreen;
import org.lwjgl.glfw.GLFW;

@Mod(Constants.MOD_ID)
public class EffortlessBuilding {

    private static KeyMapping openTestScreen;
    private static boolean prevRightDown = false;
    private static boolean prevLeftDown = false;

    public EffortlessBuilding(IEventBus modEventBus) {
        Constants.LOG.info("Hello Forge world!");

        modEventBus.addListener((RegisterEvent event) -> {
            event.register(Registries.BLOCK, helper ->
                helper.register(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "test_block"), ModBlocks.TEST_BLOCK));
            event.register(Registries.ITEM, helper -> {
                helper.register(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "test_block"), new BlockItem(ModBlocks.TEST_BLOCK, new Item.Properties()));
                helper.register(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "test_item"), ModItems.TEST_ITEM);
            });
        });

        modEventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
                event.accept(ModBlocks.TEST_BLOCK);
            } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                event.accept(ModItems.TEST_ITEM);
            }
        });

        ForgeChannel.init();

        if (FMLEnvironment.dist.isClient()) {
            modEventBus.addListener((RegisterKeyMappingsEvent event) -> {
                openTestScreen = new KeyMapping(
                    "key.effortlessbuilding.open_test_screen",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_KP_ADD,
                    "key.categories.effortlessbuilding"
                );
                event.register(openTestScreen);
            });

            MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
                if (event.phase == TickEvent.Phase.END && openTestScreen != null && openTestScreen.consumeClick()) {
                    Minecraft.getInstance().setScreen(new TestScreen());
                }

                if (event.phase == TickEvent.Phase.END) {
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
            });
        }

        CommonClass.init();
    }
}
