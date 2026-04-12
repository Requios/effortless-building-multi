package nl.requios.effortlessbuilding.modifier;

import net.minecraft.client.Minecraft;
import nl.requios.effortlessbuilding.Constants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-side modifier persistence to
 * {@code <gameDir>/config/effortlessbuilding_modifiers.json}.
 *
 * <p><b>Deprecated:</b> Modifier persistence has moved to server-side
 * per-player-per-world storage via {@link ModifierServerStorage}.
 * This class is kept for potential migration of existing client-side configs.
 *
 * <p>Only call from client-side code.
 */
public class ModifierPersistence {

    private static Path filePath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("effortlessbuilding_modifiers.json");
    }

    // -------------------------------------------------------------------------
    // Save (deprecated — use UpdateModifiersC2SPacket instead)
    // -------------------------------------------------------------------------

    public static void save() {
        String json = ModifierSerializer.serialize(ModifierSystem.CLIENT.getModifiers());
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, json);
        } catch (IOException e) {
            Constants.LOG.error("[EffortlessBuilding] Failed to save modifiers: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Load (deprecated — modifiers are now synced from server on login)
    // -------------------------------------------------------------------------

    public static void load() {
        Path path = filePath();
        if (!Files.exists(path)) return;
        try {
            String json = Files.readString(path);
            var modifiers = ModifierSerializer.deserialize(json);
            ModifierSystem.CLIENT.clearModifiers();
            for (IModifier modifier : modifiers) {
                ModifierSystem.CLIENT.addModifier(modifier);
            }
        } catch (Exception e) {
            Constants.LOG.error("[EffortlessBuilding] Failed to load modifiers: {}", e.getMessage());
        }
    }
}
