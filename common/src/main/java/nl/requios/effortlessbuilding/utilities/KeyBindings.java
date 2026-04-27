package nl.requios.effortlessbuilding.utilities;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Central holder for all mod keybindings.
 * Instances are created here; loader-specific code registers them.
 */
public class KeyBindings {

    public static final String CATEGORY = "key.categories.effortlessbuilding";

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
}

