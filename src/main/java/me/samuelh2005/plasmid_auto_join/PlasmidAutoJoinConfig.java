package me.samuelh2005.plasmid_auto_join;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * config/plasmid_auto_join.json
 * <p>
 * The only thing an admin needs to touch: {@code gameConfig} is exactly the id you'd type into
 * {@code /game open <id>} - the path to your data pack's {@code data/<namespace>/plasmid/games/<path>.json}.
 */
public final class PlasmidAutoJoinConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "plasmid_auto_join.json";

    /** e.g. "myminigame:skywars_small" - same id used with /game open. */
    private final String gameConfig = "example:example_game";

    /**
     * What to do with a connecting player if the configured game space isn't open/ready yet.
     * KICK (recommended) disconnects with notReadyMessage; VANILLA_FALLBACK lets vanilla decide
     * where they spawn (testing only - defeats the point of this mod in production).
     */
    private final FailureMode ifGameNotReady = FailureMode.KICK;

    private final String notReadyMessage = "This game is still starting up, please reconnect in a moment.";

    private final String rejectedFallbackMessage = "Could not join the game.";

    /**
     * If true, halts the server once the game space closes (match end). Standard for a true
     * one-match-per-process ephemeral server - whatever spun this process up is expected to
     * replace it with a fresh one for the next match.
     */
    private final boolean shutdownWhenGameCloses = true;

    public enum FailureMode {
        KICK,
        VANILLA_FALLBACK
    }

    public static PlasmidAutoJoinConfig load(Logger logger) {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);

        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                PlasmidAutoJoinConfig config = GSON.fromJson(reader, PlasmidAutoJoinConfig.class);
                if (config != null) {
                    return config;
                }
                logger.warn("Config file was empty/invalid, using defaults");
            } catch (IOException e) {
                logger.error("Failed to read config, using defaults", e);
            }
        }

        PlasmidAutoJoinConfig defaults = new PlasmidAutoJoinConfig();
        defaults.save(logger);
        return defaults;
    }

    public void save(Logger logger) {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            logger.error("Failed to write default config", e);
        }
    }

    public String getGameConfig() {
        // Check if an environment variable is set for the game config, which can override the config file.
        // This is useful for ephemeral servers managed by a script or container, where you might want to specify the game config without a file.
        String envGameConfig = System.getenv("PLASMID_AUTO_JOIN_GAME_CONFIG");
        if (envGameConfig != null && !envGameConfig.isEmpty()) {
            return envGameConfig;
        }

        return gameConfig;
    }

    public FailureMode getIfGameNotReady() {
        return ifGameNotReady;
    }

    public String getNotReadyMessage() {
        return notReadyMessage;
    }

    public String getRejectedFallbackMessage() {
        return rejectedFallbackMessage;
    }

    public boolean isShutdownWhenGameCloses() {
        return shutdownWhenGameCloses;
    }
}
