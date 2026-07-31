package nl.requios.effortlessbuilding.config;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import nl.requios.effortlessbuilding.Constants;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Persists which players have received the build-mode usage hint for the current world. */
public final class BuildModeHintStorage {
    private static final Gson GSON = new Gson();
    private static final Set<UUID> shownPlayers = new HashSet<>();

    private BuildModeHintStorage() {}

    public static void load(MinecraftServer server) {
        shownPlayers.clear();
        Path file = hintFile(server);
        if (!Files.exists(file)) return;
        try {
            JsonArray entries = GSON.fromJson(Files.readString(file), JsonArray.class);
            if (entries == null) return;
            for (JsonElement entry : entries) shownPlayers.add(UUID.fromString(entry.getAsString()));
        } catch (Exception e) {
            Constants.LOG.error("[EffortlessBuilding] Failed to load build-mode hints: {}", e.getMessage());
            shownPlayers.clear();
        }
    }

    public static void showIfNeeded(ServerPlayer player) {
        if (!ServerConfig.INSTANCE.showBuildModeHint || !shownPlayers.add(player.getUUID())) return;
        save(player.level().getServer());
        player.sendSystemMessage(Component.translatable(
                "effortlessbuilding.message.build_mode_hint",
                Component.keybind("key.effortlessbuilding.open_radial_menu").withStyle(ChatFormatting.BLUE)));
    }

    public static void clear() {
        shownPlayers.clear();
    }

    private static Path hintFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("effortlessbuilding").resolve("build_mode_hints.json");
    }

    private static void save(MinecraftServer server) {
        Path file = hintFile(server);
        try {
            Files.createDirectories(file.getParent());
            JsonArray entries = new JsonArray();
            for (UUID playerId : shownPlayers) entries.add(playerId.toString());
            Files.writeString(file, GSON.toJson(entries));
        } catch (Exception e) {
            Constants.LOG.error("[EffortlessBuilding] Failed to save build-mode hints: {}", e.getMessage());
        }
    }
}
