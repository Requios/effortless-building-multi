package nl.requios.effortlessbuilding.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import nl.requios.effortlessbuilding.Constants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-only configuration saved locally (not synced to the server).
 * Access from anywhere via {@link #INSTANCE}.
 */
public class ClientConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Global singleton — always available on the client. */
    public static final ClientConfig INSTANCE = new ClientConfig();

    // --- Defaults ---
    public static final float DEFAULT_PREVIEW_BLOCK_SIZE = 0.5f;
    public static final float DEFAULT_PREVIEW_BLOCK_TRANSPARENCY = 0.8f;
    public static final boolean DEFAULT_PROTECT_TILE_ENTITIES = true;

    // --- Bounds ---
    public static final float MIN_PREVIEW_BLOCK_SIZE = 0.10f;
    public static final float MAX_PREVIEW_BLOCK_SIZE = 1.0f;
    public static final float MIN_PREVIEW_BLOCK_TRANSPARENCY = 0.0f;
    public static final float MAX_PREVIEW_BLOCK_TRANSPARENCY = 1.0f;

    // --- Settings ---
    private float previewBlockSize = DEFAULT_PREVIEW_BLOCK_SIZE;
    private float previewBlockTransparency = DEFAULT_PREVIEW_BLOCK_TRANSPARENCY;
    private boolean protectTileEntities = DEFAULT_PROTECT_TILE_ENTITIES;

    // --- Getters ---

    /** Preview block size as a fraction (0.10 – 1.0). */
    public float getPreviewBlockSize() {
        return previewBlockSize;
    }

    /** Preview block transparency as a fraction (0.0 – 1.0). 0 = opaque, 1 = fully transparent. */
    public float getPreviewBlockTransparency() {
        return previewBlockTransparency;
    }

    public boolean shouldProtectTileEntities() {
        return protectTileEntities;
    }

    // --- Setters (clamped) ---

    public void setPreviewBlockSize(float value) {
        this.previewBlockSize = Math.clamp(value, MIN_PREVIEW_BLOCK_SIZE, MAX_PREVIEW_BLOCK_SIZE);
    }

    public void setPreviewBlockTransparency(float value) {
        this.previewBlockTransparency = Math.clamp(value, MIN_PREVIEW_BLOCK_TRANSPARENCY, MAX_PREVIEW_BLOCK_TRANSPARENCY);
    }

    public void setProtectTileEntities(boolean value) {
        this.protectTileEntities = value;
    }

    // --- Serialization ---

    public String toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("previewBlockSize", previewBlockSize);
        obj.addProperty("previewBlockTransparency", previewBlockTransparency);
        obj.addProperty("protectTileEntities", protectTileEntities);
        return GSON.toJson(obj);
    }

    public void loadFromJson(String json) {
        try {
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj.has("previewBlockSize")) setPreviewBlockSize(obj.get("previewBlockSize").getAsFloat());
            if (obj.has("previewBlockTransparency")) setPreviewBlockTransparency(obj.get("previewBlockTransparency").getAsFloat());
            if (obj.has("protectTileEntities")) setProtectTileEntities(obj.get("protectTileEntities").getAsBoolean());
        } catch (Exception ignored) {
            // Keep current / default values on parse failure
        }
    }

    // --- File persistence ---

    private static Path configPath() {
        // Stored next to other Minecraft config files: <gameDir>/config/effortlessbuilding-client.json
        return Path.of("config", Constants.MOD_ID + "-client.json");
    }

    /** Load from disk. Call once during client init. */
    public void load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                loadFromJson(json);
            } catch (IOException e) {
                Constants.LOG.warn("Failed to load client config: {}", e.getMessage());
            }
        }
    }

    /** Save to disk. Call after the user changes settings. */
    public void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, toJson());
        } catch (IOException e) {
                Constants.LOG.warn("Failed to save client config: {}", e.getMessage());
        }
    }

}
