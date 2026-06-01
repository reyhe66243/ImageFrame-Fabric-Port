package com.loohp.imageframe.fabric;

import com.loohp.imageframe.fabric.commands.FabricCommandRegistrar;
import com.loohp.imageframe.fabric.listeners.FabricEventsRegistrar;
import net.fabricmc.api.DedicatedServerModInitializer;
import org.simpleyaml.configuration.file.YamlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class ImageFrameMod implements DedicatedServerModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("imageframe");
    public static ImageFrameMod instance;

    private File configFolder;
    private YamlConfiguration config;

    @Override
    public void onInitializeServer() {
        instance = this;
        LOGGER.info("[ImageFrame] Initializing ImageFrame on Fabric for Minecraft 26.x...");

        configFolder = new File("config/ImageFrame");
        configFolder.mkdirs();

        try {
            File configFile = new File(configFolder, "config.yml");
            if (!configFile.exists()) {
                // Copy default config from resources
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                    if (in != null) {
                        Files.copy(in, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        configFile.createNewFile();
                    }
                }
            }
            config = YamlConfiguration.loadConfiguration(configFile);
            LOGGER.info("[ImageFrame] Configuration config.yml loaded successfully.");
        } catch (Exception e) {
            LOGGER.error("[ImageFrame] Error loading configuration: ", e);
        }

        // Register native Fabric commands
        FabricCommandRegistrar.register();

        // Register native Fabric events
        FabricEventsRegistrar.register();

        // Register server started callback to bind PlayerList
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            FabricImageMapManager.setPlayerListSource(server.getPlayerList());
            // Initialize the map manager
            FabricImageMapManager.getInstance();
        });

        LOGGER.info("[ImageFrame] ImageFrame Mod initialized successfully.");
    }

    public File getConfigFolder() {
        return configFolder;
    }

    public YamlConfiguration getConfig() {
        return config;
    }
}
