package me.samuelh2005.plasmid_auto_join;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.nucleoid.plasmid.api.game.GameCloseReason;
import xyz.nucleoid.plasmid.api.game.GameLifecycle;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.config.GameConfig;
import xyz.nucleoid.plasmid.api.game.player.GamePlayerJoiner;
import xyz.nucleoid.plasmid.api.game.player.JoinIntent;
import xyz.nucleoid.plasmid.api.registry.PlasmidRegistryKeys;
import xyz.nucleoid.plasmid.impl.game.manager.GameSpaceManagerImpl;

public class PlasmidAutoJoin implements ModInitializer {
    public static final String MOD_ID = "plasmid_auto_join";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static volatile GameSpace ACTIVE_GAME_SPACE;
    private static volatile ServerLevel TARGET_LEVEL;

    private PlasmidAutoJoinConfig config;

    @Override
    public void onInitialize() {
        this.config = PlasmidAutoJoinConfig.load(LOGGER);

        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarting);
        ServerPlayConnectionEvents.JOIN.register(this::onPlayerJoin);
    }

    /**
     * Runs on the server thread after the server has started, but before any players have joined.
     * Opens the game specified in the config, and sets ACTIVE_GAME_SPACE and TARGET_LEVEL for use by onPlayerJoin.
     * 
     * We need to use SERVER_STARTED instead of SERVER_STARTING because the GameSpaceManagerImpl is not guaranteed to be initialized yet during SERVER_STARTING.
     */
    private void onServerStarting(MinecraftServer server) {
        Identifier id = Identifier.parse(this.config.getGameConfig());
        ResourceKey<GameConfig<?>> key = ResourceKey.create(PlasmidRegistryKeys.GAME_CONFIG, id);

        var registry = server.registryAccess().lookupOrThrow(PlasmidRegistryKeys.GAME_CONFIG);
        Holder<GameConfig<?>> configHolder = registry.get(key).orElse(null);

        if (configHolder == null) {
            LOGGER.error(
                    "No GameConfig found for id '{}'. Check data/<namespace>/plasmid/games/<path>.json " +
                    "matches 'gameConfig' in config/plasmid_auto_join.json. The server will start, but every " +
                    "connecting player will be handled according to ifGameNotReady ({}).",
                    id, this.config.getIfGameNotReady()
            );
            return;
        }

        LOGGER.info("Opening configured game '{}'...", id);

        GameSpaceManagerImpl.get().open(configHolder).handleAsync((gameSpace, throwable) -> {
            if (throwable != null) {
                LOGGER.error("Failed to open configured game '{}'", id, throwable);
                return null;
            }

            ACTIVE_GAME_SPACE = gameSpace;
            TARGET_LEVEL = gameSpace.getLevels().iterator().next();

            LOGGER.info("Game '{}' is open and ready in level '{}'.",
                    id, TARGET_LEVEL.dimension().identifier());

            if (this.config.isShutdownWhenGameCloses()) {
                gameSpace.getLifecycle().addListeners(new GameLifecycle.Listeners() {
                    @Override
                    public void onClosed(GameSpace closedSpace, List<ServerPlayer> players, GameCloseReason reason) {
                        LOGGER.info("Game closed ({}), shutting down server.", reason);
                        server.execute(() -> server.halt(false));
                    }
                });
            }
            return null;
        }, server);
    }

    /**
     * By this point PlayerListMixin has already placed the player's entity directly into
     * TARGET_LEVEL during placeNewPlayer, so this is bookkeeping (inventory/gamemode/statistics,
     * firing GamePlayerEvents.ADD) rather than a real cross-dimension teleport - no second
     * loading screen.
     */
    private void onPlayerJoin(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server) {
        ServerPlayer player = handler.getPlayer();
        GameSpace gameSpace = ACTIVE_GAME_SPACE;
 
        if (gameSpace == null || gameSpace.isClosed()) {
            if (this.config.getIfGameNotReady() == PlasmidAutoJoinConfig.FailureMode.KICK) {
                player.connection.disconnect(Component.literal(this.config.getNotReadyMessage()));
            }
            return;
        }

        var result = GamePlayerJoiner.tryJoin(player, gameSpace, JoinIntent.PLAY);
        if (result.isError()) {
            player.connection.disconnect(
                    Component.literal(this.config.getRejectedFallbackMessage() + " ").append(result.errorCopy())
            );
        }
    }

    /**
     * Used by {@link me.samuelh2005.plasmid_auto_join.mixin.PlayerListMixin} to redirect a
     * freshly-connecting player's level before any login packets are sent.
     */
    public static ServerLevel getTargetLevel() {
        return TARGET_LEVEL;
    }


    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
