package nl.requios.effortlessbuilding.buildchain;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import nl.requios.effortlessbuilding.Constants;
import nl.requios.effortlessbuilding.config.ServerConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side per-player build skill persistence.
 *
 * <p>Tracks upgradeable skills like extra reach and extra max blocks per axis.
 * Each player's data is cached in memory and persisted to
 * {@code <worldDir>/effortlessbuilding/skills/<uuid>.txt} as a simple
 * {@code reach,axis} CSV line.
 */
public class BuildSkills {

    public static final int UPGRADE_AMOUNT = 3;

    // ---- Client-side synced effective values ----
    private static int clientEffectiveReach = ServerConfig.INSTANCE.getBuildModeReach();
    private static int clientEffectiveAxis  = ServerConfig.INSTANCE.getMaxBlocksPerAxis();

    public static int getClientEffectiveReach() { return clientEffectiveReach; }
    public static int getClientEffectiveAxis()  { return clientEffectiveAxis; }

    public static void setClientEffectiveReach(int reach) { clientEffectiveReach = reach; }
    public static void setClientEffectiveAxis(int axis)   { clientEffectiveAxis = axis; }

    // ---- In-memory cache ----
    private static final Map<UUID, int[]> playerSkills = new HashMap<>();
    // indices
    private static final int IDX_REACH = 0;
    private static final int IDX_AXIS  = 1;
    private static final int SKILL_COUNT = 2;

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    private static int[] getOrCreate(UUID playerId) {
        return playerSkills.computeIfAbsent(playerId, k -> new int[SKILL_COUNT]);
    }

    /** Extra reach bonus (0 by default). */
    public static int getExtraReach(Player player) {
        return getOrCreate(player.getUUID())[IDX_REACH];
    }

    /** Extra max-blocks-per-axis bonus (0 by default). */
    public static int getExtraAxis(Player player) {
        return getOrCreate(player.getUUID())[IDX_AXIS];
    }

    public static void setExtraReach(Player player, int value) {
        getOrCreate(player.getUUID())[IDX_REACH] = Math.max(0, value);
    }

    public static void setExtraAxis(Player player, int value) {
        getOrCreate(player.getUUID())[IDX_AXIS] = Math.max(0, value);
    }

    public static void addReach(Player player, int amount) {
        int current = getExtraReach(player);
        int max = ServerConfig.INSTANCE.getBuildModeReach();
        setExtraReach(player, Math.min(current + amount, max));
    }

    public static void addAxis(Player player, int amount) {
        int current = getExtraAxis(player);
        int max = ServerConfig.INSTANCE.getMaxBlocksPerAxis();
        setExtraAxis(player, Math.min(current + amount, max));
    }

    // ---- Effective values (creative = max, survival = base + bonus capped) ----

    private static final int BASE_REACH = 5;
    private static final int BASE_AXIS  = 6;

    public static int getEffectiveReach(Player player) {
        int max = ServerConfig.INSTANCE.getBuildModeReach();
        if (player.isCreative()) return max;
        return Math.min(BASE_REACH + getExtraReach(player), max);
    }

    public static int getEffectiveAxis(Player player) {
        int max = ServerConfig.INSTANCE.getMaxBlocksPerAxis();
        if (player.isCreative()) return max;
        return Math.min(BASE_AXIS + getExtraAxis(player), max);
    }

    // -------------------------------------------------------------------------
    // File I/O
    // -------------------------------------------------------------------------

    private static Path playerFile(MinecraftServer server, UUID playerId) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("effortlessbuilding")
                .resolve("skills")
                .resolve(playerId + ".txt");
    }

    /** Loads a player's skills from disk into the cache. */
    public static void loadPlayer(MinecraftServer server, UUID playerId) {
        Path file = playerFile(server, playerId);
        int[] skills = new int[SKILL_COUNT];
        if (Files.exists(file)) {
            try {
                String content = Files.readString(file).trim();
                String[] parts = content.split(",");
                if (parts.length >= 1) skills[IDX_REACH] = Math.max(0, Integer.parseInt(parts[0].trim()));
                if (parts.length >= 2) skills[IDX_AXIS]  = Math.max(0, Integer.parseInt(parts[1].trim()));
            } catch (Exception e) {
                Constants.LOG.error("[EffortlessBuilding] Failed to load skills for {}: {}", playerId, e.getMessage());
            }
        }
        playerSkills.put(playerId, skills);
    }

    /** Saves a player's cached skills to disk. */
    public static void savePlayer(MinecraftServer server, UUID playerId) {
        int[] skills = playerSkills.getOrDefault(playerId, new int[SKILL_COUNT]);
        Path file = playerFile(server, playerId);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, skills[IDX_REACH] + "," + skills[IDX_AXIS]);
        } catch (IOException e) {
            Constants.LOG.error("[EffortlessBuilding] Failed to save skills for {}: {}", playerId, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    public static void removePlayer(UUID playerId) {
        playerSkills.remove(playerId);
    }

    public static void clearAll() {
        playerSkills.clear();
    }
}
