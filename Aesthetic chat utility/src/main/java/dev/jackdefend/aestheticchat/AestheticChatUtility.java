package dev.jackdefend.aestheticchat;

import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AestheticChatUtility implements ModInitializer {
    public static final String MOD_ID = "aesthetic-chat-utility";
    public static final Logger LOGGER = LoggerFactory.getLogger("Aesthetic Chat Utility");

    private static int tickCounter = 0;
    private static Path configPath;
    private static AcuConfig config;
    private static PlayerStore store;

    public static AcuConfig config() { return config; }
    public static PlayerStore store() { return store; }

    public static void reloadConfig() {
        config = AcuConfig.load(configPath);
    }

    @Override
    public void onInitialize() {
        Path dir = FabricLoader.getInstance().getConfigDir();
        configPath = dir.resolve("aesthetic-chat-utility.json5");
        reloadConfig();
        store = PlayerStore.load(dir.resolve("aesthetic-chat-utility-players.json"));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                AcuCommands.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(ColorSync::ensureTeams);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter >= 40) { // about every 2 seconds
                tickCounter = 0;
                ColorSync.enforceLocator(server);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ColorSync.apply(handler.getPlayer()));
    }
}
