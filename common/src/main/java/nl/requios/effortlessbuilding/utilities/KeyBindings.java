package nl.requios.effortlessbuilding.utilities;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import nl.requios.effortlessbuilding.mixin.KeyMappingAccessor;
import org.lwjgl.glfw.GLFW;

/**
 * Central holder for all mod keybindings.
 * Instances are created here; loader-specific code registers them.
 */
public class KeyBindings {

    public static final String CATEGORY = "key.categories.effortlessbuilding";
    
    public static KeyMapping openRadialMenu = new KeyMapping(
            "key.effortlessbuilding.open_radial_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            CATEGORY
    );
    
    public static KeyMapping openModifiersScreen = new KeyMapping(
            "key.effortlessbuilding.open_modifiers_screen",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_ADD,
            CATEGORY
    );

    public static KeyMapping undo = new KeyMapping(
            "key.effortlessbuilding.undo.desc",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            CATEGORY
    );

    public static KeyMapping redo = new KeyMapping(
            "key.effortlessbuilding.redo.desc",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            CATEGORY
    );

    /**
     * Checks if the physical key bound to a KeyMapping is currently held down.
     * Unlike KeyMapping.isDown(), this works even when a Screen is open.
     */
    public static boolean isKeyDown(KeyMapping keyMapping) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(window, ((KeyMappingAccessor) keyMapping).effortlessbuilding$getKey().getValue());
    }
}
