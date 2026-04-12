package nl.requios.effortlessbuilding.modifier;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import nl.requios.effortlessbuilding.Constants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Server-side per-player modifier storage.
 *
 * <p>Each player's modifier list is cached in memory and persisted to
 * {@code <worldDir>/effortlessbuilding/modifiers/<uuid>.json}.
 *
 * <p>Only call from server-side code.
 */
public class ModifierServerStorage {

    /** In-memory cache of each player's modifier system. */
    private static final Map<UUID, ModifierSystem> playerModifiers = new HashMap<>();

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the modifier system for the given player, creating an empty one
     * if none is cached yet.
     */
    public static ModifierSystem getModifiers(UUID playerId) {
        return playerModifiers.computeIfAbsent(playerId, k -> new ModifierSystem());
    }

    /**
     * Replaces the cached modifier list for the given player.
     */
    public static void setModifiers(UUID playerId, List<IModifier> modifiers) {
        ModifierSystem system = playerModifiers.computeIfAbsent(playerId, k -> new ModifierSystem());
        system.clearModifiers();
        for (IModifier m : modifiers) {
            system.addModifier(m);
        }
    }

    // -------------------------------------------------------------------------
    // File I/O
    // -------------------------------------------------------------------------

    private static Path playerFile(MinecraftServer server, UUID playerId) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("effortlessbuilding")
                .resolve("modifiers")
                .resolve(playerId + ".json");
    }

    /**
     * Loads a player's modifiers from disk into the cache.
     * If no file exists, the cache will hold an empty modifier system.
     */
    public static void loadPlayer(MinecraftServer server, UUID playerId) {
        Path file = playerFile(server, playerId);
        if (!Files.exists(file)) {
            // Ensure an empty system exists in cache
            playerModifiers.computeIfAbsent(playerId, k -> new ModifierSystem());
            return;
        }
        try {
            String json = Files.readString(file);
            List<IModifier> modifiers = ModifierSerializer.deserialize(json);
            setModifiers(playerId, modifiers);
        } catch (Exception e) {
            Constants.LOG.error("[EffortlessBuilding] Failed to load modifiers for {}: {}", playerId, e.getMessage());
            playerModifiers.computeIfAbsent(playerId, k -> new ModifierSystem());
        }
    }

    /**
     * Saves a player's cached modifiers to disk.
     */
    public static void savePlayer(MinecraftServer server, UUID playerId) {
        ModifierSystem system = playerModifiers.get(playerId);
        if (system == null) return;

        String json = ModifierSerializer.serialize(system.getModifiers());
        Path file = playerFile(server, playerId);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json);
        } catch (IOException e) {
            Constants.LOG.error("[EffortlessBuilding] Failed to save modifiers for {}: {}", playerId, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    /**
     * Removes a player's cached data (call on disconnect after saving).
     */
    public static void removePlayer(UUID playerId) {
        playerModifiers.remove(playerId);
    }

    /**
     * Clears all cached data (call on server stop to avoid leaks across
     * world loads in singleplayer).
     */
    public static void clearAll() {
        playerModifiers.clear();
    }

    // -------------------------------------------------------------------------
    // Serialization helpers for packets
    // -------------------------------------------------------------------------

    /**
     * Serializes a player's current modifier list to a JSON string (for packets).
     */
    public static String serializePlayer(UUID playerId) {
        ModifierSystem system = playerModifiers.get(playerId);
        if (system == null) return "[]";
        return ModifierSerializer.serialize(system.getModifiers());
    }
}

