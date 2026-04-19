package nl.requios.effortlessbuilding.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * Server-authoritative configuration.
 * The {@link #INSTANCE} is populated on both client (via sync packet) and server (via storage).
 */
public class ServerConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Shared instance — on the server it's loaded from disk, on the client it's synced via packet. */
    public static final ServerConfig INSTANCE = new ServerConfig();

    // --- Settings ---
    public static final int DEFAULT_BUILD_MODE_REACH = 32;
    public static final int DEFAULT_MAX_BLOCKS_PER_AXIS = 20;

    public static final int MIN_BUILD_MODE_REACH = 1;
    public static final int MAX_BUILD_MODE_REACH = 256;
    public static final int MIN_MAX_BLOCKS_PER_AXIS = 1;
    public static final int MAX_MAX_BLOCKS_PER_AXIS = 128;

    private int buildModeReach = DEFAULT_BUILD_MODE_REACH;
    private int maxBlocksPerAxis = DEFAULT_MAX_BLOCKS_PER_AXIS;

    // --- Getters / Setters ---

    public int getBuildModeReach() {
        return buildModeReach;
    }

    public void setBuildModeReach(int value) {
        this.buildModeReach = clamp(value, MIN_BUILD_MODE_REACH, MAX_BUILD_MODE_REACH);
    }

    public int getMaxBlocksPerAxis() {
        return maxBlocksPerAxis;
    }

    public void setMaxBlocksPerAxis(int value) {
        this.maxBlocksPerAxis = clamp(value, MIN_MAX_BLOCKS_PER_AXIS, MAX_MAX_BLOCKS_PER_AXIS);
    }

    public void copyFrom(ServerConfig other) {
        this.buildModeReach = other.buildModeReach;
        this.maxBlocksPerAxis = other.maxBlocksPerAxis;
    }

    public void reset() {
        this.buildModeReach = DEFAULT_BUILD_MODE_REACH;
        this.maxBlocksPerAxis = DEFAULT_MAX_BLOCKS_PER_AXIS;
    }

    // --- Serialization ---

    public String toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("buildModeReach", buildModeReach);
        obj.addProperty("maxBlocksPerAxis", maxBlocksPerAxis);
        return GSON.toJson(obj);
    }

    public static ServerConfig fromJson(String json) {
        ServerConfig config = new ServerConfig();
        try {
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj.has("buildModeReach")) config.setBuildModeReach(obj.get("buildModeReach").getAsInt());
            if (obj.has("maxBlocksPerAxis")) config.setMaxBlocksPerAxis(obj.get("maxBlocksPerAxis").getAsInt());
        } catch (Exception ignored) {
            // Return defaults on parse failure
        }
        return config;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

