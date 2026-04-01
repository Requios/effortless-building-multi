package nl.requios.effortlessbuilding.modifier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import nl.requios.effortlessbuilding.Constants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Saves and loads the {@link ModifierSystem#CLIENT} modifier list to/from
 * {@code <gameDir>/config/effortlessbuilding_modifiers.json}.
 *
 * <p>Only call from client-side code.
 */
public class ModifierPersistence {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path filePath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("effortlessbuilding_modifiers.json");
    }

    // -------------------------------------------------------------------------
    // Save
    // -------------------------------------------------------------------------

    public static void save() {
        JsonArray array = new JsonArray();
        for (IModifier modifier : ModifierSystem.CLIENT.getModifiers()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("enabled", modifier.isEnabled());
            serialize(modifier, obj);
            array.add(obj);
        }
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(array));
        } catch (IOException e) {
            Constants.LOG.error("[EffortlessBuilding] Failed to save modifiers: {}", e.getMessage());
        }
    }

    private static void serialize(IModifier modifier, JsonObject obj) {
        if (modifier instanceof MirrorModifier mirror) {
            obj.addProperty("type", "mirror");
            obj.addProperty("mirrorX", mirror.mirrorX);
            obj.addProperty("mirrorY", mirror.mirrorY);
            obj.addProperty("mirrorZ", mirror.mirrorZ);
            obj.addProperty("originX", mirror.originX);
            obj.addProperty("originY", mirror.originY);
            obj.addProperty("originZ", mirror.originZ);
        } else if (modifier instanceof ArrayModifier array) {
            obj.addProperty("type", "array");
            obj.addProperty("count", array.count);
            obj.addProperty("offsetX", array.offsetX);
            obj.addProperty("offsetY", array.offsetY);
            obj.addProperty("offsetZ", array.offsetZ);
        } else if (modifier instanceof RadialMirrorModifier radial) {
            obj.addProperty("type", "radial_mirror");
            obj.addProperty("slices", radial.slices);
            obj.addProperty("mirrorSlices", radial.mirrorSlices);
            obj.addProperty("originX", radial.originX);
            obj.addProperty("originZ", radial.originZ);
        }
    }

    // -------------------------------------------------------------------------
    // Load
    // -------------------------------------------------------------------------

    public static void load() {
        Path path = filePath();
        if (!Files.exists(path)) return;
        try {
            String json = Files.readString(path);
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            ModifierSystem.CLIENT.clearModifiers();
            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                IModifier modifier = deserialize(obj);
                if (modifier == null) continue;
                modifier.setEnabled(getBoolean(obj, "enabled", true));
                ModifierSystem.CLIENT.addModifier(modifier);
            }
        } catch (Exception e) {
            Constants.LOG.error("[EffortlessBuilding] Failed to load modifiers: {}", e.getMessage());
        }
    }

    private static IModifier deserialize(JsonObject obj) {
        if (!obj.has("type")) return null;
        return switch (obj.get("type").getAsString()) {
            case "mirror" -> {
                MirrorModifier mirror = new MirrorModifier();
                mirror.mirrorX  = getBoolean(obj, "mirrorX",  mirror.mirrorX);
                mirror.mirrorY  = getBoolean(obj, "mirrorY",  mirror.mirrorY);
                mirror.mirrorZ  = getBoolean(obj, "mirrorZ",  mirror.mirrorZ);
                mirror.originX  = getInt(obj, "originX", mirror.originX);
                mirror.originY  = getInt(obj, "originY", mirror.originY);
                mirror.originZ  = getInt(obj, "originZ", mirror.originZ);
                yield mirror;
            }
            case "array" -> {
                ArrayModifier array = new ArrayModifier();
                array.count   = getInt(obj, "count",   array.count);
                array.offsetX = getInt(obj, "offsetX", array.offsetX);
                array.offsetY = getInt(obj, "offsetY", array.offsetY);
                array.offsetZ = getInt(obj, "offsetZ", array.offsetZ);
                yield array;
            }
            case "radial_mirror" -> {
                RadialMirrorModifier radial = new RadialMirrorModifier();
                radial.slices       = getInt(obj, "slices",       radial.slices);
                radial.mirrorSlices = getBoolean(obj, "mirrorSlices", radial.mirrorSlices);
                radial.originX      = getInt(obj, "originX", radial.originX);
                radial.originZ      = getInt(obj, "originZ", radial.originZ);
                yield radial;
            }
            default -> null;
        };
    }

    // ---- helpers ----

    private static int getInt(JsonObject obj, String key, int fallback) {
        return obj.has(key) ? obj.get(key).getAsInt() : fallback;
    }

    private static boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        return obj.has(key) ? obj.get(key).getAsBoolean() : fallback;
    }
}
