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

        loadConfiguration();

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

    public synchronized void reloadConfiguration() {
        loadConfiguration();
    }

    public synchronized void loadConfiguration() {
        try {
            File configFile = new File(configFolder, "config.yml");
            boolean needsCopy = !configFile.exists() || configFile.length() == 0;

            if (configFile.exists() && configFile.length() > 0) {
                try {
                    YamlConfiguration existing = YamlConfiguration.loadConfiguration(configFile);
                    if (!existing.contains("Settings")) {
                        LOGGER.warn("[ImageFrame] Existing config/ImageFrame/config.yml is missing ImageFrame 'Settings' section (likely an empty file or Floodgate's config). Backing up to config.yml.bak and creating authentic ImageFrame configuration...");
                        File backupFile = new File(configFolder, "config.yml.bak");
                        if (backupFile.exists()) {
                            backupFile.delete();
                        }
                        configFile.renameTo(backupFile);
                        needsCopy = true;
                    }
                } catch (Exception ex) {
                    LOGGER.warn("[ImageFrame] Existing config.yml could not be parsed as YAML. Backing up to config.yml.bak...");
                    File backupFile = new File(configFolder, "config.yml.bak");
                    if (backupFile.exists()) {
                        backupFile.delete();
                    }
                    configFile.renameTo(backupFile);
                    needsCopy = true;
                }
            }

            if (needsCopy) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                    if (in != null) {
                        Files.copy(in, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        LOGGER.info("[ImageFrame] Created authentic ImageFrame config.yml from default resources.");
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

    public int getMaxProcessingTime() {
        return config != null ? config.getInt("Settings.MaxProcessingTime", 60) : 60;
    }

    public boolean isCombinedByDefault() {
        return config != null ? config.getBoolean("Settings.CombinedByDefault", false) : false;
    }

    public int getInvisibleFrameMaxConversions() {
        return config != null ? config.getInt("InvisibleFrame.MaxConversionsPerSplash", 8) : 8;
    }

    public boolean isGlowEmptyFrames() {
        return config != null ? config.getBoolean("InvisibleFrame.GlowEmptyFrames", true) : true;
    }

    public String getDateFormat() {
        return config != null ? config.getString("Settings.DateFormat", "dd/MM/yyyy HH:mm:ss zzz") : "dd/MM/yyyy HH:mm:ss zzz";
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
            return section.getInt("default", -1);
        }
        return -1;
    }
}

