package com.loohp.imageframe.fabric;

import com.loohp.imageframe.fabric.commands.FabricCommandRegistrar;
import com.loohp.imageframe.fabric.listeners.FabricEventsRegistrar;
import com.loohp.imageframe.fabric.network.ImageFrameNetworkHandler;
import com.loohp.imageframe.fabric.permissions.FabricPermissionManager;
import net.fabricmc.api.ModInitializer;
import net.minecraft.server.level.ServerPlayer;
import org.simpleyaml.configuration.file.YamlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;

public class ImageFrameMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("imageframe");
    public static ImageFrameMod instance;

    private File configFolder;
    private YamlConfiguration config;

    @Override
    public void onInitialize() {
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

        // Register HD image protocol payloads (must be done before handlers)
        ImageFrameNetworkHandler.registerPayloads();

        // Register server-side network handlers for HD client protocol
        ImageFrameNetworkHandler.registerServerHandlers();

        // Initialize language manager
        com.loohp.imageframe.fabric.language.FabricLanguageManager.getInstance();

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

    public void reloadConfiguration() {
        try {
            File configFile = new File(configFolder, "config.yml");
            if (!configFile.exists()) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                    if (in != null) {
                        Files.copy(in, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        configFile.createNewFile();
                    }
                }
            }
            config = YamlConfiguration.loadConfiguration(configFile);
            LOGGER.info("[ImageFrame] Configuration reloaded successfully.");
        } catch (Exception e) {
            LOGGER.error("[ImageFrame] Error reloading configuration: ", e);
        }
    }

    public boolean isRequireEmptyMaps() {
        return config != null ? config.getBoolean("Settings.RequireEmptyMaps", true) : true;
    }

    public int getMaxSize() {
        return config != null ? config.getInt("Settings.MaxSize", 100) : 100;
    }

    public boolean isRestrictImageUrl() {
        return config != null ? config.getBoolean("Settings.RestrictImageUrl.Enabled", false) : false;
    }

    public List<String> getImageUrlWhitelist() {
        return config != null ? config.getStringList("Settings.RestrictImageUrl.Whitelist") : Collections.emptyList();
    }

    public long getMaxImageFileSize() {
        return config != null ? config.getLong("Settings.MaxImageFileSize", 52428800L) : 52428800L;
    }

    public int getPlayerCreationLimit(ServerPlayer player) {
        if (config == null) return -1;
        if (FabricPermissionManager.hasPermission(player, "imageframe.createlimit.unlimited", 2)) {
            return -1;
        }
        if (config.isConfigurationSection("Settings.PlayerCreationLimit")) {
            org.simpleyaml.configuration.ConfigurationSection section = config.getConfigurationSection("Settings.PlayerCreationLimit");
            for (String group : section.getKeys(false)) {
                if (!group.equalsIgnoreCase("default") && FabricPermissionManager.hasPermission(player, "imageframe.createlimit." + group.toLowerCase(), 2)) {
                    return section.getInt(group, -1);
                }
            }
            return section.getInt("default", 10);
        }
        return 10;
    }
}

