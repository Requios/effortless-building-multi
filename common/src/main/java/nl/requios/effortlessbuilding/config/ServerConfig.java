package nl.requios.effortlessbuilding.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.player.Player;

/**
 * Server-authoritative configuration.
 * The {@link #INSTANCE} is populated on both client (via sync packet) and server (via storage).
 */
public class ServerConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Shared instance — on the server it's loaded from disk, on the client it's synced via packet. */
    public static final ServerConfig INSTANCE = new ServerConfig();

    // --- Survival settings ---
    public int survivalReach = 32;
    public int survivalMaxBlocksPlaced = 2000;
    public int survivalMaxBlocksPerAxis = 64;
    public int survivalMaxMirrorSize = 256;
    public int survivalMaxArrayCount = 64;
    public int survivalMaxArrayOffset = 64;
    public boolean survivalAllowBreaking = true;
    public boolean survivalOnlyPlacedBlocks = true;
    public float survivalMaxHardness = -1f; // -1 = no limit
    public boolean survivalRequireTools = false;
    public boolean survivalUseDurability = false;

    // --- Creative settings ---
    public int creativeReach = 200;
    public int creativeMaxBlocksPlaced = 10000;
    public int creativeMaxBlocksPerAxis = 1000;
    public int creativeMaxMirrorSize = 256;
    public int creativeMaxArrayCount = 256;
    public int creativeMaxArrayOffset = 256;

    // --- Player-aware convenience accessors ---

    public int getReach(Player player) {
        return player.isCreative() ? creativeReach : survivalReach;
    }

    public int getMaxBlocksPlaced(Player player) {
        return player.isCreative() ? creativeMaxBlocksPlaced : survivalMaxBlocksPlaced;
    }

    public int getMaxBlocksPerAxis(Player player) {
        return player.isCreative() ? creativeMaxBlocksPerAxis : survivalMaxBlocksPerAxis;
    }

    public int getMaxMirrorSize(Player player) {
        return player.isCreative() ? creativeMaxMirrorSize : survivalMaxMirrorSize;
    }

    public int getMaxArrayCount(Player player) {
        return player.isCreative() ? creativeMaxArrayCount : survivalMaxArrayCount;
    }

    public int getMaxArrayOffset(Player player) {
        return player.isCreative() ? creativeMaxArrayOffset : survivalMaxArrayOffset;
    }

    // --- Mutators (with clamping) ---

    public void clampAll() {
        survivalReach = Math.clamp(survivalReach, 1, 1000);
        survivalMaxBlocksPlaced = Math.clamp(survivalMaxBlocksPlaced, 1, 100000);
        survivalMaxBlocksPerAxis = Math.clamp(survivalMaxBlocksPerAxis, 1, 1000);
        survivalMaxMirrorSize = Math.clamp(survivalMaxMirrorSize, 1, 1000);
        survivalMaxArrayCount = Math.clamp(survivalMaxArrayCount, 1, 1000);
        survivalMaxArrayOffset = Math.clamp(survivalMaxArrayOffset, 1, 1000);

        creativeReach = Math.clamp(creativeReach, 1, 1000);
        creativeMaxBlocksPlaced = Math.clamp(creativeMaxBlocksPlaced, 1, 100000);
        creativeMaxBlocksPerAxis = Math.clamp(creativeMaxBlocksPerAxis, 1, 1000);
        creativeMaxMirrorSize = Math.clamp(creativeMaxMirrorSize, 1, 1000);
        creativeMaxArrayCount = Math.clamp(creativeMaxArrayCount, 1, 1000);
        creativeMaxArrayOffset = Math.clamp(creativeMaxArrayOffset, 1, 1000);
    }

    public void copyFrom(ServerConfig other) {
        this.survivalReach = other.survivalReach;
        this.survivalMaxBlocksPlaced = other.survivalMaxBlocksPlaced;
        this.survivalMaxBlocksPerAxis = other.survivalMaxBlocksPerAxis;
        this.survivalMaxMirrorSize = other.survivalMaxMirrorSize;
        this.survivalMaxArrayCount = other.survivalMaxArrayCount;
        this.survivalMaxArrayOffset = other.survivalMaxArrayOffset;
        this.survivalAllowBreaking = other.survivalAllowBreaking;
        this.survivalOnlyPlacedBlocks = other.survivalOnlyPlacedBlocks;
        this.survivalMaxHardness = other.survivalMaxHardness;
        this.survivalRequireTools = other.survivalRequireTools;
        this.survivalUseDurability = other.survivalUseDurability;

        this.creativeReach = other.creativeReach;
        this.creativeMaxBlocksPlaced = other.creativeMaxBlocksPlaced;
        this.creativeMaxBlocksPerAxis = other.creativeMaxBlocksPerAxis;
        this.creativeMaxMirrorSize = other.creativeMaxMirrorSize;
        this.creativeMaxArrayCount = other.creativeMaxArrayCount;
        this.creativeMaxArrayOffset = other.creativeMaxArrayOffset;
    }

    public void reset() {
        copyFrom(new ServerConfig());
    }

    // --- Serialization ---

    public String toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("survivalReach", survivalReach);
        obj.addProperty("survivalMaxBlocksPlaced", survivalMaxBlocksPlaced);
        obj.addProperty("survivalMaxBlocksPerAxis", survivalMaxBlocksPerAxis);
        obj.addProperty("survivalMaxMirrorSize", survivalMaxMirrorSize);
        obj.addProperty("survivalMaxArrayCount", survivalMaxArrayCount);
        obj.addProperty("survivalMaxArrayOffset", survivalMaxArrayOffset);
        obj.addProperty("survivalAllowBreaking", survivalAllowBreaking);
        obj.addProperty("survivalOnlyPlacedBlocks", survivalOnlyPlacedBlocks);
        obj.addProperty("survivalMaxHardness", survivalMaxHardness);
        obj.addProperty("survivalRequireTools", survivalRequireTools);
        obj.addProperty("survivalUseDurability", survivalUseDurability);

        obj.addProperty("creativeReach", creativeReach);
        obj.addProperty("creativeMaxBlocksPlaced", creativeMaxBlocksPlaced);
        obj.addProperty("creativeMaxBlocksPerAxis", creativeMaxBlocksPerAxis);
        obj.addProperty("creativeMaxMirrorSize", creativeMaxMirrorSize);
        obj.addProperty("creativeMaxArrayCount", creativeMaxArrayCount);
        obj.addProperty("creativeMaxArrayOffset", creativeMaxArrayOffset);
        return GSON.toJson(obj);
    }

    public static ServerConfig fromJson(String json) {
        ServerConfig config = new ServerConfig();
        try {
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj.has("survivalReach")) config.survivalReach = obj.get("survivalReach").getAsInt();
            if (obj.has("survivalMaxBlocksPlaced")) config.survivalMaxBlocksPlaced = obj.get("survivalMaxBlocksPlaced").getAsInt();
            if (obj.has("survivalMaxBlocksPerAxis")) config.survivalMaxBlocksPerAxis = obj.get("survivalMaxBlocksPerAxis").getAsInt();
            if (obj.has("survivalMaxMirrorSize")) config.survivalMaxMirrorSize = obj.get("survivalMaxMirrorSize").getAsInt();
            if (obj.has("survivalMaxArrayCount")) config.survivalMaxArrayCount = obj.get("survivalMaxArrayCount").getAsInt();
            if (obj.has("survivalMaxArrayOffset")) config.survivalMaxArrayOffset = obj.get("survivalMaxArrayOffset").getAsInt();
            if (obj.has("survivalAllowBreaking")) config.survivalAllowBreaking = obj.get("survivalAllowBreaking").getAsBoolean();
            if (obj.has("survivalOnlyPlacedBlocks")) config.survivalOnlyPlacedBlocks = obj.get("survivalOnlyPlacedBlocks").getAsBoolean();
            if (obj.has("survivalMaxHardness")) config.survivalMaxHardness = obj.get("survivalMaxHardness").getAsFloat();
            if (obj.has("survivalRequireTools")) config.survivalRequireTools = obj.get("survivalRequireTools").getAsBoolean();
            if (obj.has("survivalUseDurability")) config.survivalUseDurability = obj.get("survivalUseDurability").getAsBoolean();

            if (obj.has("creativeReach")) config.creativeReach = obj.get("creativeReach").getAsInt();
            if (obj.has("creativeMaxBlocksPlaced")) config.creativeMaxBlocksPlaced = obj.get("creativeMaxBlocksPlaced").getAsInt();
            if (obj.has("creativeMaxBlocksPerAxis")) config.creativeMaxBlocksPerAxis = obj.get("creativeMaxBlocksPerAxis").getAsInt();
            if (obj.has("creativeMaxMirrorSize")) config.creativeMaxMirrorSize = obj.get("creativeMaxMirrorSize").getAsInt();
            if (obj.has("creativeMaxArrayCount")) config.creativeMaxArrayCount = obj.get("creativeMaxArrayCount").getAsInt();
            if (obj.has("creativeMaxArrayOffset")) config.creativeMaxArrayOffset = obj.get("creativeMaxArrayOffset").getAsInt();
        } catch (Exception ignored) {}
        config.clampAll();
        return config;
    }
}
