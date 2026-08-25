package com.loohp.imageframe.fabric.language;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.loohp.imageframe.fabric.ImageFrameMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FabricLanguageManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("imageframe-lang");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String LANGUAGE_META_URL = "https://api.loohpjames.com/spigot/plugins/imageframe/language";

    private static FabricLanguageManager INSTANCE;
    private final File languageFolder;
    private final Map<String, Map<String, String>> translations = new ConcurrentHashMap<>();
    private String serverLanguage = "en_US";

    public FabricLanguageManager() {
        INSTANCE = this;
        this.languageFolder = new File(ImageFrameMod.instance.getConfigFolder(), "language");
        this.languageFolder.mkdirs();
        loadServerConfig();
        saveDefaultEnUs();
        reloadLanguages();
    }

    public static FabricLanguageManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FabricLanguageManager();
        }
        return INSTANCE;
    }

    public void loadServerConfig() {
        try {
            File configFile = new File(ImageFrameMod.instance.getConfigFolder(), "config.json");
            if (configFile.exists()) {
                try (Reader reader = Files.newBufferedReader(configFile.toPath(), StandardCharsets.UTF_8)) {
                    JsonObject obj = GSON.fromJson(reader, JsonObject.class);
                    if (obj != null && obj.has("language")) {
                        this.serverLanguage = obj.get("language").getAsString();
                    }
                }
            } else {
                saveServerConfig();
            }
        } catch (Exception e) {
            LOGGER.error("[ImageFrame] Error loading language config: ", e);
        }
    }

    public void saveServerConfig() {
        try {
            File configFile = new File(ImageFrameMod.instance.getConfigFolder(), "config.json");
            JsonObject obj = new JsonObject();
            obj.addProperty("language", this.serverLanguage);
            try (Writer writer = Files.newBufferedWriter(configFile.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(obj, writer);
            }
        } catch (Exception e) {
            LOGGER.error("[ImageFrame] Error saving language config: ", e);
        }
    }

    public void setServerLanguage(String lang) {
        this.serverLanguage = lang;
        saveServerConfig();
    }

    public String getServerLanguage() {
        return serverLanguage;
    }

    public void reloadLanguages() {
        loadTranslationsFromDisk();
        // Asynchronously check and download remote translations from Crowdin / API
        Thread.ofVirtual().start(this::downloadRemoteTranslations);
    }

    public void loadTranslationsFromDisk() {
        File[] files = languageFolder.listFiles();
        if (files == null) return;

        for (File file : files) {
            String name = file.getName();
            if (name.endsWith(".json")) {
                String lang = name.substring(0, name.indexOf(".")).toLowerCase();
                Map<String, String> map = new ConcurrentHashMap<>();
                try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                    JsonObject obj = GSON.fromJson(reader, JsonObject.class);
                    if (obj != null) {
                        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                            map.put(entry.getKey(), entry.getValue().getAsString());
                        }
                    }
                    translations.put(lang, map);
                } catch (Exception e) {
                    LOGGER.error("[ImageFrame] Error reading language file " + file.getName() + ": ", e);
                }
            }
        }
    }

    private void downloadRemoteTranslations() {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(LANGUAGE_META_URL).toURL().openConnection();
            conn.setRequestProperty("User-Agent", "ImageFrame-Fabric");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                try (Reader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    JsonObject meta = GSON.fromJson(reader, JsonObject.class);
                    if (meta != null && meta.has("languages")) {
                        for (JsonElement elem : meta.get("languages").getAsJsonArray()) {
                            JsonObject langObj = elem.getAsJsonObject();
                            String lang = langObj.get("language").getAsString();
                            String url = langObj.get("url").getAsString();
                            File targetFile = new File(languageFolder, lang + ".json");
                            if (!targetFile.exists()) {
                                downloadFile(url, targetFile);
                            }
                        }
                        loadTranslationsFromDisk();
                    }
                }
            }
        } catch (Exception ignored) {
            // Offline or rate-limited; fallback to bundled translations
        }
    }

    private void downloadFile(String urlStr, File target) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
            conn.setRequestProperty("User-Agent", "ImageFrame-Fabric");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() == 200) {
                try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(target)) {
                    in.transferTo(out);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public String get(String key) {
        return get(key, serverLanguage);
    }

    public String get(String key, Object... args) {
        String msg = get(key);
        if (args == null || args.length == 0) {
            return msg;
        }
        try {
            return String.format(msg, args);
        } catch (Exception e) {
            return msg;
        }
    }

    public String get(String key, String language) {
        String lang = language.toLowerCase();
        Map<String, String> map = translations.get(lang);
        if (map != null && map.containsKey(key)) {
            return map.get(key);
        }
        // Fallback to en_us
        Map<String, String> defaultMap = translations.get("en_us");
        if (defaultMap != null && defaultMap.containsKey(key)) {
            return defaultMap.get(key);
        }
        return key;
    }

    private void saveDefaultEnUs() {
        File enUsFile = new File(languageFolder, "en_us.json");
        if (enUsFile.exists()) return;

        JsonObject en = new JsonObject();
        en.addProperty("imageframe.messages.reloaded", "ImageFrame has been reloaded!");
        en.addProperty("imageframe.messages.resync", "Performing resync! See console for details.");
        en.addProperty("imageframe.messages.storage_migration", "Performing storage migration! See console for details.");
        en.addProperty("imageframe.messages.image_map_processing", "ImageMap is being processed, please wait!");
        en.addProperty("imageframe.messages.image_map_processing_action_bar", "ImageMap %1$s is being processed%2$s");
        en.addProperty("imageframe.messages.image_map_queued_action_bar", "ImageMap %1$s is currently queued (%2$s Remaining)");
        en.addProperty("imageframe.messages.image_map_created", "ImageMap has been created!");
        en.addProperty("imageframe.messages.image_map_refreshed", "ImageMap has been refreshed!");
        en.addProperty("imageframe.messages.image_map_deleted", "ImageMap had been deleted!");
        en.addProperty("imageframe.messages.image_map_renamed", "ImageMap had been renamed!");
        en.addProperty("imageframe.messages.image_map_toggle_paused", "Toggled ImageMap playback pause!");
        en.addProperty("imageframe.messages.image_map_playback_jump_to", "Jumped to position at %s seconds!");
        en.addProperty("imageframe.messages.invalid_overlay_map", "Overlay only works on Vanilla Minecraft maps and without duplicates in selection!");
        en.addProperty("imageframe.messages.unable_to_load_map", "ImageMap cannot be loaded, there is a problem while reading the image.");
        en.addProperty("imageframe.messages.unknown_error", "An unknown error had occurred.");
        en.addProperty("imageframe.messages.image_over_max_file_size", "ImageMap cannot be loaded as it is over the max file size allowed. (%s bytes)");
        en.addProperty("imageframe.messages.not_an_image_map", "That is not an ImageMap.");
        en.addProperty("imageframe.messages.no_permission", "You do not have permission to do that!");
        en.addProperty("imageframe.messages.no_console", "This command can only be ran by players!");
        en.addProperty("imageframe.messages.player_not_found", "This player cannot be found!");
        en.addProperty("imageframe.messages.invalid_usage", "Invalid Usage!");
        en.addProperty("imageframe.messages.not_enough_maps", "You do not have %s maps!");
        en.addProperty("imageframe.messages.oversize", "That is too big! Max size for a map is %s.");
        en.addProperty("imageframe.messages.url_restricted", "That URL is restricted and cannot be used to create image maps.");
        en.addProperty("imageframe.messages.player_creation_limit_reached", "You can only create %s maps at once! Delete some to create new ones.");
        en.addProperty("imageframe.messages.duplicate_map_name", "You've already created an image map with that name!");
        en.addProperty("imageframe.messages.map_lookup", "List of image maps:");
        en.addProperty("imageframe.messages.item_frame_occupied", "Failed to place or remove some maps on selected ItemFrame.");
        en.addProperty("imageframe.messages.not_enough_space", "Unable to place Combined ImageMap as there is not enough room.");
        en.addProperty("imageframe.messages.invalid_image_map", "This image map had likely already been deleted.");
        en.addProperty("imageframe.messages.selection.begin", "Right click an Item Frame to select corner 1 and 2.");
        en.addProperty("imageframe.messages.selection.clear", "Leaving selection mode.");
        en.addProperty("imageframe.messages.selection.corner1", "Selected Item Frame corner 1.");
        en.addProperty("imageframe.messages.selection.corner2", "Selected Item Frame corner 2.");
        en.addProperty("imageframe.messages.selection.invalid", "Invalid selection!");
        en.addProperty("imageframe.messages.selection.oversize", "Oversize selection! Max size for a map is %s.");
        en.addProperty("imageframe.messages.selection.success", "Selected %1$s x %2$s Item Frames!");
        en.addProperty("imageframe.messages.selection.no_selection", "You don't have a valid selection yet.");
        en.addProperty("imageframe.messages.selection.incorrect_size", "Your selection's size does not match, %1$s x %2$s required.");
        en.addProperty("imageframe.settings.combined_map_item.name", "ImageMap - %s (%s x %s)");
        en.addProperty("imageframe.settings.combined_map_item.lore.1", "Right Click on Item Frames of size %s x %s to place ImageMap.");

        try (Writer writer = Files.newBufferedWriter(enUsFile.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(en, writer);
        } catch (Exception e) {
            LOGGER.error("[ImageFrame] Error writing default en_us.json: ", e);
        }
    }
}
