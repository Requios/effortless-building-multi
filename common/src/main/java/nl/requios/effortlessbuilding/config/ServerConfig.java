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

    // --- General settings ---
    public static final int DEFAULT_BUILD_MODE_REACH = 32;
    public static final int DEFAULT_MAX_BLOCKS_PER_AXIS = 20;

    public static final int MIN_BUILD_MODE_REACH = 1;
    public static final int MAX_BUILD_MODE_REACH = 256;
    public static final int MIN_MAX_BLOCKS_PER_AXIS = 1;
    public static final int MAX_MAX_BLOCKS_PER_AXIS = 128;

    private int buildModeReach = DEFAULT_BUILD_MODE_REACH;
    private int maxBlocksPerAxis = DEFAULT_MAX_BLOCKS_PER_AXIS;

    // --- Breaking sub-configs ---
    private final BreakingConfig breakingPlacedBlocks = new BreakingConfig(true, -1f, false, false, 0f);
    private final BreakingConfig breakingAnyBlocks    = new BreakingConfig(false, -1f, false, false, 0f);
    private final BreakingConfig breakingWithUndo     = new BreakingConfig(true, -1f, false, false, 0f);

    // --- General getters / setters ---

    public int getBuildModeReach() {
        return buildModeReach;
    }

    public void setBuildModeReach(int value) {
        this.buildModeReach = Math.clamp(value, MIN_BUILD_MODE_REACH, MAX_BUILD_MODE_REACH);
    }

    public int getMaxBlocksPerAxis() {
        return maxBlocksPerAxis;
    }

    public void setMaxBlocksPerAxis(int value) {
        this.maxBlocksPerAxis = Math.clamp(value, MIN_MAX_BLOCKS_PER_AXIS, MAX_MAX_BLOCKS_PER_AXIS);
    }

    // --- Breaking config accessors ---

    public BreakingConfig getBreakingPlacedBlocks() { return breakingPlacedBlocks; }
    public BreakingConfig getBreakingAnyBlocks()    { return breakingAnyBlocks; }
    public BreakingConfig getBreakingWithUndo()     { return breakingWithUndo; }

    public void copyFrom(ServerConfig other) {
        this.buildModeReach = other.buildModeReach;
        this.maxBlocksPerAxis = other.maxBlocksPerAxis;
        this.breakingPlacedBlocks.copyFrom(other.breakingPlacedBlocks);
        this.breakingAnyBlocks.copyFrom(other.breakingAnyBlocks);
        this.breakingWithUndo.copyFrom(other.breakingWithUndo);
    }

    public void reset() {
        this.buildModeReach = DEFAULT_BUILD_MODE_REACH;
        this.maxBlocksPerAxis = DEFAULT_MAX_BLOCKS_PER_AXIS;
        this.breakingPlacedBlocks.reset(true);
        this.breakingAnyBlocks.reset(false);
        this.breakingWithUndo.reset(true);
    }

    // --- Serialization ---

    public String toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("buildModeReach", buildModeReach);
        obj.addProperty("maxBlocksPerAxis", maxBlocksPerAxis);
        obj.add("breakingPlacedBlocks", breakingPlacedBlocks.toJson());
        obj.add("breakingAnyBlocks", breakingAnyBlocks.toJson());
        obj.add("breakingWithUndo", breakingWithUndo.toJson());
        return GSON.toJson(obj);
    }

    public static ServerConfig fromJson(String json) {
        ServerConfig config = new ServerConfig();
        try {
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj.has("buildModeReach")) config.setBuildModeReach(obj.get("buildModeReach").getAsInt());
            if (obj.has("maxBlocksPerAxis")) config.setMaxBlocksPerAxis(obj.get("maxBlocksPerAxis").getAsInt());
            if (obj.has("breakingPlacedBlocks")) config.breakingPlacedBlocks.fromJson(obj.getAsJsonObject("breakingPlacedBlocks"));
            if (obj.has("breakingAnyBlocks")) config.breakingAnyBlocks.fromJson(obj.getAsJsonObject("breakingAnyBlocks"));
            if (obj.has("breakingWithUndo")) config.breakingWithUndo.fromJson(obj.getAsJsonObject("breakingWithUndo"));
        } catch (Exception ignored) {
            // Return defaults on parse failure
        }
        return config;
    }

    // =========================================================================
    // Breaking sub-config
    // =========================================================================

    /**
     * Settings for a specific breaking category (placed blocks, any blocks, or undo).
     * Accessed via {@link ServerConfig#getBreakingPlacedBlocks()},
     * {@link ServerConfig#getBreakingAnyBlocks()}, or {@link ServerConfig#getBreakingWithUndo()}.
     */
    public static class BreakingConfig {

        public static final float DEFAULT_MAX_HARDNESS = -1f; // -1 = unlimited
        public static final float DEFAULT_SATURATION   = 0f;

        private boolean allowBreaking;
        private float maxHardness;          // -1 = no limit
        private boolean requireToolsToBreak;
        private boolean requireToolsForDrops;
        private float saturationPerBlock;

        public BreakingConfig(boolean allowBreaking, float maxHardness,
                              boolean requireToolsToBreak, boolean requireToolsForDrops,
                              float saturationPerBlock) {
            this.allowBreaking = allowBreaking;
            this.maxHardness = maxHardness;
            this.requireToolsToBreak = requireToolsToBreak;
            this.requireToolsForDrops = requireToolsForDrops;
            this.saturationPerBlock = saturationPerBlock;
        }

        // --- Getters ---
        public boolean isAllowBreaking()        { return allowBreaking; }
        public float getMaxHardness()            { return maxHardness; }
        public boolean isRequireToolsToBreak()   { return requireToolsToBreak; }
        public boolean isRequireToolsForDrops()  { return requireToolsForDrops; }
        public float getSaturationPerBlock()     { return saturationPerBlock; }

        // --- Setters ---
        public void setAllowBreaking(boolean v)        { this.allowBreaking = v; }
        public void setMaxHardness(float v)             { this.maxHardness = v; }
        public void setRequireToolsToBreak(boolean v)   { this.requireToolsToBreak = v; }
        public void setRequireToolsForDrops(boolean v)  { this.requireToolsForDrops = v; }
        public void setSaturationPerBlock(float v)      { this.saturationPerBlock = Math.max(0f, v); }

        public void copyFrom(BreakingConfig other) {
            this.allowBreaking = other.allowBreaking;
            this.maxHardness = other.maxHardness;
            this.requireToolsToBreak = other.requireToolsToBreak;
            this.requireToolsForDrops = other.requireToolsForDrops;
            this.saturationPerBlock = other.saturationPerBlock;
        }

        public void reset(boolean defaultAllowBreaking) {
            this.allowBreaking = defaultAllowBreaking;
            this.maxHardness = DEFAULT_MAX_HARDNESS;
            this.requireToolsToBreak = false;
            this.requireToolsForDrops = false;
            this.saturationPerBlock = DEFAULT_SATURATION;
        }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("allowBreaking", allowBreaking);
            obj.addProperty("maxHardness", maxHardness);
            obj.addProperty("requireToolsToBreak", requireToolsToBreak);
            obj.addProperty("requireToolsForDrops", requireToolsForDrops);
            obj.addProperty("saturationPerBlock", saturationPerBlock);
            return obj;
        }

        public void fromJson(JsonObject obj) {
            if (obj.has("allowBreaking")) allowBreaking = obj.get("allowBreaking").getAsBoolean();
            if (obj.has("maxHardness")) maxHardness = obj.get("maxHardness").getAsFloat();
            if (obj.has("requireToolsToBreak")) requireToolsToBreak = obj.get("requireToolsToBreak").getAsBoolean();
            if (obj.has("requireToolsForDrops")) requireToolsForDrops = obj.get("requireToolsForDrops").getAsBoolean();
            if (obj.has("saturationPerBlock")) saturationPerBlock = obj.get("saturationPerBlock").getAsFloat();
        }
    }
}
