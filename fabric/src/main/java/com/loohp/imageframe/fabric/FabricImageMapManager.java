package com.loohp.imageframe.fabric;

import com.loohp.imageframe.fabric.nms.FabricMapHelper;
import com.loohp.imageframe.fabric.utils.FabricMapColorPalette;
import com.loohp.imageframe.utils.GifReader;
import com.loohp.platformscheduler.Scheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class FabricImageMapManager {

    public static class FabricImageMap {
        public int index;
        public String name;
        public String url;
        public int width;
        public int height;
        public List<Integer> mapIds;
        public UUID owner;
        public String dithering;
        public boolean isCombined;
        public long creationDate;

        // Animation metadata if it is a GIF
        public boolean isAnimated;
        public List<byte[][]> framesColors; // Frame list, each frame has [tileIndex][colorBytes]
        public List<Integer> framesDelays; // Delays in ms
        public int currentFrameIndex = 0;
        public long lastFrameUpdate = 0;
        public boolean isPaused = false;
    }

    public static class PlayerSelection {
        public ItemFrame corner1;
        public ItemFrame corner2;
        public boolean active = false;
    }

    private static FabricImageMapManager instance;
    private final Map<String, FabricImageMap> maps = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerSelection> selections = new ConcurrentHashMap<>();
    private final Set<UUID> invisibleFrames = ConcurrentHashMap.newKeySet();

    public static FabricImageMapManager getInstance() {
        if (instance == null) {
            instance = new FabricImageMapManager();
        }
        return instance;
    }

    private FabricImageMapManager() {
        loadInvisibleFrames();
        loadMaps();

        // Start periodic animation updater (GIFs)
        Scheduler.runTaskAsynchronously(null, this::runAnimationLoop);
    }

    private void loadInvisibleFrames() {
        try {
            File file = new File(ImageFrameMod.instance.getConfigFolder(), "invisible_frames.json");
            if (file.exists()) {
                try (java.io.BufferedReader br = java.nio.file.Files.newBufferedReader(file.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                    com.google.gson.JsonArray arr = new com.google.gson.Gson().fromJson(br, com.google.gson.JsonArray.class);
                    if (arr != null) {
                        for (com.google.gson.JsonElement elem : arr) {
                            invisibleFrames.add(UUID.fromString(elem.getAsString()));
                        }
                    }
                }
            }
        } catch (Exception e) {
            ImageFrameMod.LOGGER.error("[ImageFrame] Error loading invisible_frames.json: ", e);
        }
    }

    public synchronized void saveInvisibleFrames() {
        try {
            File file = new File(ImageFrameMod.instance.getConfigFolder(), "invisible_frames.json");
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.OutputStreamWriter(java.nio.file.Files.newOutputStream(file.toPath()), java.nio.charset.StandardCharsets.UTF_8))) {
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (UUID uuid : invisibleFrames) {
                    arr.add(uuid.toString());
                }
                pw.println(new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(arr));
                pw.flush();
            }
        } catch (Exception e) {
            ImageFrameMod.LOGGER.error("[ImageFrame] Error saving invisible_frames.json: ", e);
        }
    }

    public Set<UUID> getInvisibleFrames() {
        return invisibleFrames;
    }

    public boolean isInvisibleFrame(UUID uuid) {
        return invisibleFrames.contains(uuid);
    }

    public void addInvisibleFrame(UUID uuid) {
        invisibleFrames.add(uuid);
        saveInvisibleFrames();
    }

    public void removeInvisibleFrame(UUID uuid) {
        invisibleFrames.remove(uuid);
        saveInvisibleFrames();
    }

    public void updateInvisibleItemFrame(ItemFrame itemFrame) {
        if (itemFrame == null || !itemFrame.isAlive()) return;
        ItemStack item = itemFrame.getItem();
        if (item.isEmpty()) {
            itemFrame.setInvisible(false);
            itemFrame.setGlowingTag(true);
        } else {
            itemFrame.setInvisible(true);
            itemFrame.setGlowingTag(false);
        }
    }

    public static boolean isInvisibleItemFrame(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        net.minecraft.world.item.component.CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        net.minecraft.nbt.CompoundTag tag = customData.copyTag();
        if (tag.contains("invisible")) {
            return tag.getBoolean("invisible").orElse(false) || tag.getByte("invisible").orElse((byte) 0) > 0;
        }
        if (tag.contains("PublicBukkitValues")) {
            net.minecraft.nbt.CompoundTag pdc = tag.getCompound("PublicBukkitValues").orElse(null);
            if (pdc != null && pdc.contains("imageframe:invisible")) {
                return pdc.getByte("imageframe:invisible").orElse((byte) 0) > 0;
            }
        }
        return false;
    }

    public void setSelectionActive(UUID uuid, boolean active) {
        PlayerSelection selection = selections.computeIfAbsent(uuid, k -> new PlayerSelection());
        selection.active = active;
        if (!active) {
            selection.corner1 = null;
            selection.corner2 = null;
        }
    }

    public boolean isSelectionActive(UUID uuid) {
        PlayerSelection selection = selections.get(uuid);
        return selection != null && selection.active;
    }

    public PlayerSelection getSelection(UUID uuid) {
        return selections.get(uuid);
    }

    public Map<String, FabricImageMap> getMaps() {
        return maps;
    }

    public FabricImageMap getMap(String name) {
        return maps.get(name.toLowerCase());
    }

    /**
     * Find a map by its storage index.
     */
    public FabricImageMap getMapByIndex(int index) {
        for (FabricImageMap map : maps.values()) {
            if (map.index == index) {
                return map;
            }
        }
        return null;
    }

    /**
     * Find the map that contains a specific Minecraft map ID.
     */
    public FabricImageMap getMapByMapId(int mapId) {
        for (FabricImageMap map : maps.values()) {
            if (map.mapIds.contains(mapId)) {
                return map;
            }
        }
        return null;
    }

    /**
     * Get the tile index of a specific map ID within its parent ImageMap.
     */
    public int getTileIndex(FabricImageMap map, int mapId) {
        return map.mapIds.indexOf(mapId);
    }

    private MapItemSavedData getOrCreateMapData(ServerLevel level, int id) {
        MapId mapId = new MapId(id);
        MapItemSavedData data = level.getMapData(mapId);
        if (data == null) {
            data = MapItemSavedData.createFresh(0, 0, (byte) 3, false, false, level.dimension());
            level.setMapData(mapId, data);
        }
        return data;
    }

    public synchronized void saveMapData(FabricImageMap map) {
        try {
            File dataFolder = new File(ImageFrameMod.instance.getConfigFolder(), "data");
            File folder = new File(dataFolder, String.valueOf(map.index));
            folder.mkdirs();

            File dataFile = new File(folder, "data.json");
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.OutputStreamWriter(java.nio.file.Files.newOutputStream(dataFile.toPath()), java.nio.charset.StandardCharsets.UTF_8))) {
                com.google.gson.JsonObject json = new com.google.gson.JsonObject();
                json.addProperty("type", map.isAnimated ? "imageframe:url_animated" : "imageframe:url_static");
                json.addProperty("index", map.index);
                json.addProperty("name", map.name);
                json.addProperty("url", map.url);
                json.addProperty("width", map.width);
                json.addProperty("height", map.height);
                json.addProperty("ditheringType", map.dithering);
                json.addProperty("creationTime", map.creationDate);
                json.addProperty("creator", map.owner.toString());
                json.addProperty("isCombined", map.isCombined);

                com.google.gson.JsonArray mapDataArray = new com.google.gson.JsonArray();
                if (map.isAnimated) {
                    int u = 0;
                    int numFrames = map.framesColors != null ? map.framesColors.size() : 0;
                    for (int i = 0; i < map.mapIds.size(); i++) {
                        int id = map.mapIds.get(i);
                        com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
                        obj.addProperty("mapid", id);
                        com.google.gson.JsonArray framesArray = new com.google.gson.JsonArray();
                        for (int f = 0; f < numFrames; f++) {
                            framesArray.add(u + ".png");
                            u++;
                        }
                        obj.add("images", framesArray);
                        obj.add("markers", new com.google.gson.JsonArray());
                        mapDataArray.add(obj);
                    }
                } else {
                    for (int i = 0; i < map.mapIds.size(); i++) {
                        int id = map.mapIds.get(i);
                        com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
                        obj.addProperty("mapid", id);
                        obj.addProperty("image", i + ".png");
                        obj.add("markers", new com.google.gson.JsonArray());
                        mapDataArray.add(obj);
                    }
                }
                json.add("mapdata", mapDataArray);

                pw.println(new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json));
                pw.flush();
            }
        } catch (Exception e) {
            ImageFrameMod.LOGGER.error("[ImageFrame] Error saving data.json for " + map.name + ": ", e);
        }
    }

    public synchronized void deleteMapFolder(FabricImageMap map) {
        try {
            File dataFolder = new File(ImageFrameMod.instance.getConfigFolder(), "data");
            File folder = new File(dataFolder, String.valueOf(map.index));
            if (folder.exists() && folder.isDirectory()) {
                File[] files = folder.listFiles();
                if (files != null) {
                    for (File file : files) {
                        file.delete();
                    }
                }
                folder.delete();
            }
        } catch (Exception e) {
            ImageFrameMod.LOGGER.error("[ImageFrame] Error deleting map directory for " + map.name + ": ", e);
        }
    }

    private boolean tryLoadFromLocalFiles(FabricImageMap map) {
        File dataFolder = new File(ImageFrameMod.instance.getConfigFolder(), "data");
        File folder = new File(dataFolder, String.valueOf(map.index));
        if (!folder.exists() || !folder.isDirectory()) {
            return false;
        }

        File dataFile = new File(folder, "data.json");
        if (!dataFile.exists()) {
            return false;
        }

        try (java.io.BufferedReader reader = java.nio.file.Files.newBufferedReader(dataFile.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
            com.google.gson.JsonObject json = new com.google.gson.Gson().fromJson(reader, com.google.gson.JsonObject.class);
            if (!json.has("mapdata")) {
                return false;
            }

            com.google.gson.JsonArray mapDataArray = json.get("mapdata").getAsJsonArray();
            if (mapDataArray.isEmpty()) {
                return false;
            }

            String type = json.has("type") ? json.get("type").getAsString() : "";
            boolean animated = type.contains("animated");

            if (animated) {
                com.google.gson.JsonObject firstTile = mapDataArray.get(0).getAsJsonObject();
                if (!firstTile.has("images")) {
                    return false;
                }
                com.google.gson.JsonArray firstTileImages = firstTile.get("images").getAsJsonArray();
                int numFrames = firstTileImages.size();
                if (numFrames == 0) {
                    return false;
                }

                List<List<File>> tileFiles = new ArrayList<>();
                for (int i = 0; i < mapDataArray.size(); i++) {
                    com.google.gson.JsonObject tileObj = mapDataArray.get(i).getAsJsonObject();
                    if (!tileObj.has("images")) {
                        return false;
                    }
                    com.google.gson.JsonArray imagesArr = tileObj.get("images").getAsJsonArray();
                    if (imagesArr.size() != numFrames) {
                        return false;
                    }
                    List<File> files = new ArrayList<>();
                    for (int f = 0; f < numFrames; f++) {
                        File imgFile = new File(folder, imagesArr.get(f).getAsString());
                        if (!imgFile.exists()) {
                            return false;
                        }
                        files.add(imgFile);
                    }
                    tileFiles.add(files);
                }

                List<byte[][]> framesColors = new ArrayList<>();
                for (int f = 0; f < numFrames; f++) {
                    byte[][] frameTilesColors = new byte[mapDataArray.size()][16384];
                    for (int i = 0; i < mapDataArray.size(); i++) {
                        File imgFile = tileFiles.get(i).get(f);
                        BufferedImage img = ImageIO.read(imgFile);
                        if (img == null) {
                            return false;
                        }
                        if (img.getWidth() != 128 || img.getHeight() != 128) {
                            BufferedImage scaled = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
                            Graphics2D g = scaled.createGraphics();
                            g.drawImage(img.getScaledInstance(128, 128, Image.SCALE_SMOOTH), 0, 0, null);
                            g.dispose();
                            img = scaled;
                        }
                        byte[] tileColors = frameTilesColors[i];
                        for (int px = 0; px < 128; px++) {
                            for (int py = 0; py < 128; py++) {
                                int argb = img.getRGB(px, py);
                                int a = (argb >> 24) & 0xFF;
                                int r = (argb >> 16) & 0xFF;
                                int gVal = (argb >> 8) & 0xFF;
                                int b = argb & 0xFF;
                                tileColors[py * 128 + px] = FabricMapColorPalette.getClosestColorIndex(r, gVal, b, a);
                            }
                        }
                    }
                    framesColors.add(frameTilesColors);
                }

                map.isAnimated = true;
                map.framesColors = new CopyOnWriteArrayList<>(framesColors);
                List<Integer> delays = new ArrayList<>();
                for (int f = 0; f < numFrames; f++) {
                    delays.add(50);
                }
                map.framesDelays = delays;
                return true;

            } else {
                List<File> files = new ArrayList<>();
                for (int i = 0; i < mapDataArray.size(); i++) {
                    com.google.gson.JsonObject tileObj = mapDataArray.get(i).getAsJsonObject();
                    String imgName = tileObj.has("image") ? tileObj.get("image").getAsString() : (i + ".png");
                    File imgFile = new File(folder, imgName);
                    if (!imgFile.exists()) {
                        imgFile = new File(folder, i + ".png");
                        if (!imgFile.exists()) {
                            return false;
                        }
                    }
                    files.add(imgFile);
                }

                byte[][] frameTilesColors = new byte[mapDataArray.size()][16384];
                for (int i = 0; i < mapDataArray.size(); i++) {
                    File imgFile = files.get(i);
                    BufferedImage img = ImageIO.read(imgFile);
                    if (img == null) {
                        return false;
                    }
                    if (img.getWidth() != 128 || img.getHeight() != 128) {
                        BufferedImage scaled = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g = scaled.createGraphics();
                        g.drawImage(img.getScaledInstance(128, 128, Image.SCALE_SMOOTH), 0, 0, null);
                        g.dispose();
                        img = scaled;
                    }
                    byte[] tileColors = frameTilesColors[i];
                    for (int px = 0; px < 128; px++) {
                        for (int py = 0; py < 128; py++) {
                            int argb = img.getRGB(px, py);
                            int a = (argb >> 24) & 0xFF;
                            int r = (argb >> 16) & 0xFF;
                            int gVal = (argb >> 8) & 0xFF;
                            int b = argb & 0xFF;
                            tileColors[py * 128 + px] = FabricMapColorPalette.getClosestColorIndex(r, gVal, b, a);
                        }
                    }
                }

                map.isAnimated = false;
                map.framesColors = new CopyOnWriteArrayList<>();
                map.framesColors.add(frameTilesColors);
                List<Integer> delays = new ArrayList<>();
                delays.add(0);
                map.framesDelays = delays;
                return true;
            }
        } catch (Exception e) {
            ImageFrameMod.LOGGER.error("[ImageFrame] Error trying to load map " + map.name + " from local files: ", e);
        }
        return false;
    }

    private void saveLocalPNGFiles(FabricImageMap map, List<BufferedImage> images) {
        try {
            File dataFolder = new File(ImageFrameMod.instance.getConfigFolder(), "data");
            File folder = new File(dataFolder, String.valueOf(map.index));
            folder.mkdirs();

            int targetWidth = map.width * 128;
            int targetHeight = map.height * 128;

            List<BufferedImage> scaledFrames = new ArrayList<>();
            for (BufferedImage rawImg : images) {
                BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = scaled.createGraphics();
                g.drawImage(rawImg.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH), 0, 0, null);
                g.dispose();
                scaledFrames.add(scaled);
            }

            if (map.isAnimated && scaledFrames.size() > 1) {
                int u = 0;
                for (int i = 0; i < map.mapIds.size(); i++) {
                    int x = i % map.width;
                    int y = i / map.width;
                    for (int f = 0; f < scaledFrames.size(); f++) {
                        BufferedImage scaled = scaledFrames.get(f);
                        BufferedImage tileImg = scaled.getSubimage(x * 128, y * 128, 128, 128);
                        File outFile = new File(folder, u + ".png");
                        ImageIO.write(tileImg, "png", outFile);
                        u++;
                    }
                }
            } else {
                BufferedImage scaled = scaledFrames.get(0);
                for (int i = 0; i < map.mapIds.size(); i++) {
                    int x = i % map.width;
                    int y = i / map.width;
                    BufferedImage tileImg = scaled.getSubimage(x * 128, y * 128, 128, 128);
                    File outFile = new File(folder, i + ".png");
                    ImageIO.write(tileImg, "png", outFile);
                }
            }
        } catch (Exception e) {
            ImageFrameMod.LOGGER.error("[ImageFrame] Error saving local PNG images for " + map.name + ": ", e);
        }
    }

    public synchronized void loadMaps() {
        File dataFolder = new File(ImageFrameMod.instance.getConfigFolder(), "data");
        dataFolder.mkdirs();

        maps.clear();
        File[] subfolders = dataFolder.listFiles();
        if (subfolders != null) {
            for (File folder : subfolders) {
                if (folder.isDirectory()) {
                    File dataFile = new File(folder, "data.json");
                    if (dataFile.exists()) {
                        try (java.io.BufferedReader reader = java.nio.file.Files.newBufferedReader(dataFile.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                            com.google.gson.JsonObject json = new com.google.gson.Gson().fromJson(reader, com.google.gson.JsonObject.class);

                            int index = json.has("index") ? json.get("index").getAsInt() : Integer.parseInt(folder.getName());
                            String name = json.has("name") ? json.get("name").getAsString() : folder.getName();
                            String url = json.has("url") ? json.get("url").getAsString() : "";
                            int width = json.has("width") ? json.get("width").getAsInt() : 1;
                            int height = json.has("height") ? json.get("height").getAsInt() : 1;
                            UUID creator = json.has("creator") ? UUID.fromString(json.get("creator").getAsString()) : UUID.randomUUID();
                            String dithering = json.has("ditheringType") ? json.get("ditheringType").getAsString() : "floyd-steinberg";
                            long creationTime = json.has("creationTime") ? json.get("creationTime").getAsLong() : System.currentTimeMillis();

                            List<Integer> mapIds = new ArrayList<>();
                            if (json.has("mapdata")) {
                                com.google.gson.JsonArray mapDataArray = json.get("mapdata").getAsJsonArray();
                                for (com.google.gson.JsonElement elem : mapDataArray) {
                                    com.google.gson.JsonObject obj = elem.getAsJsonObject();
                                    if (obj.has("mapid")) {
                                        mapIds.add(obj.get("mapid").getAsInt());
                                    }
                                }
                            }

                            FabricImageMap map = new FabricImageMap();
                            map.index = index;
                            map.name = name;
                            map.url = url;
                            map.width = width;
                            map.height = height;
                            map.mapIds = mapIds;
                            map.owner = creator;
                            map.dithering = dithering;
                            map.isCombined = json.has("isCombined") ? json.get("isCombined").getAsBoolean() : false;
                            map.creationDate = creationTime;

                            maps.put(name.toLowerCase(), map);
                        } catch (Exception e) {
                            ImageFrameMod.LOGGER.error("[ImageFrame] Error loading map in " + folder.getAbsolutePath() + ": ", e);
                        }
                    }
                }
            }
            ImageFrameMod.LOGGER.info("[ImageFrame] Successfully loaded " + maps.size() + " maps from the 'data' storage directory.");
        }

        // Load and process images asynchronously to update states and animations
        for (FabricImageMap map : maps.values()) {
            Scheduler.runTaskAsynchronously(null, () -> loadAndProcessImageAsync(map, false, null, null));
        }
    }

    public void handleItemFrameInteraction(ServerPlayer player, ItemFrame itemFrame) {
        UUID uuid = player.getUUID();
        PlayerSelection selection = selections.computeIfAbsent(uuid, k -> new PlayerSelection());
        if (!selection.active) return;

        if (selection.corner1 == null) {
            selection.corner1 = itemFrame;
            player.sendSystemMessage(Component.literal("§a[ImageFrame] First frame selected!"));
        } else if (selection.corner2 == null) {
            selection.corner2 = itemFrame;
            player.sendSystemMessage(Component.literal("§a[ImageFrame] Second frame selected!"));
            selection.active = false;

            // Calculate selected frames
            List<ItemFrame> frames = getSelectedFrames(player, selection.corner1, selection.corner2);
            if (frames.isEmpty()) {
                player.sendSystemMessage(Component.literal("§c[ImageFrame] Invalid or empty selection."));
                selection.corner1 = null;
                selection.corner2 = null;
            } else {
                player.sendSystemMessage(Component.literal("§a[ImageFrame] Selection completed successfully. Width x Height: " + getSelectionWidth(frames) + "x" + getSelectionHeight(frames)));
            }
        }
    }

    public List<ItemFrame> getSelectedFrames(ServerPlayer player, ItemFrame corner1, ItemFrame corner2) {
        if (corner1 == null || corner2 == null) return Collections.emptyList();
        if (!corner1.level().equals(corner2.level())) return Collections.emptyList();

        ServerLevel level = (ServerLevel) corner1.level();
        AABB box = corner1.getBoundingBox().minmax(corner2.getBoundingBox());

        List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, box);
        Direction dir = corner1.getDirection();

        // Filter by the same item frame direction
        frames.removeIf(f -> !f.getDirection().equals(dir));

        // Sort frames from top to bottom, left to right (perspective-based)
        frames.sort((f1, f2) -> {
            BlockPos p1 = f1.getPos();
            BlockPos p2 = f2.getPos();

            // Highest Y first (top to bottom)
            if (p1.getY() != p2.getY()) {
                return Integer.compare(p2.getY(), p1.getY());
            }

            // Based on horizontal orientation
            switch (dir) {
                case NORTH:
                    return Integer.compare(p2.getX(), p1.getX());
                case SOUTH:
                    return Integer.compare(p1.getX(), p2.getX());
                case WEST:
                    return Integer.compare(p1.getZ(), p2.getZ());
                case EAST:
                    return Integer.compare(p2.getZ(), p1.getZ());
                default:
                    return 0;
            }
        });

        return frames;
    }

    private int getSelectionWidth(List<ItemFrame> frames) {
        if (frames.isEmpty()) return 0;
        int highestY = frames.get(0).getPos().getY();
        int width = 0;
        for (ItemFrame f : frames) {
            if (f.getPos().getY() == highestY) {
                width++;
            } else {
                break;
            }
        }
        return width;
    }

    private int getSelectionHeight(List<ItemFrame> frames) {
        if (frames.isEmpty()) return 0;
        int width = getSelectionWidth(frames);
        return frames.size() / width;
    }

    public void createMap(String name, String url, int width, int height, UUID owner, String dithering, boolean combined, boolean useSelection, ServerPlayer player) {
        ServerLevel overworld = ((ServerLevel) player.level()).getServer().getLevel(Level.OVERWORLD);

        List<ItemFrame> selectedFrames = null;
        if (useSelection) {
            PlayerSelection sel = selections.get(player.getUUID());
            if (sel == null || sel.corner1 == null || sel.corner2 == null) {
                player.sendSystemMessage(Component.literal("§c[ImageFrame] You must first make a selection with /imageframe select"));
                return;
            }
            selectedFrames = getSelectedFrames(player, sel.corner1, sel.corner2);
            if (selectedFrames.isEmpty()) {
                player.sendSystemMessage(Component.literal("§c[ImageFrame] Your item frame selection is empty."));
                return;
            }
            width = getSelectionWidth(selectedFrames);
            height = getSelectionHeight(selectedFrames);
        }

        if (maps.containsKey(name.toLowerCase())) {
            player.sendSystemMessage(Component.literal("§c[ImageFrame] An image map with that name already exists."));
            return;
        }

        player.sendSystemMessage(Component.literal("§e[ImageFrame] Reserving Map IDs and downloading image..."));

        List<Integer> mapIds = new ArrayList<>();
        for (int i = 0; i < width * height; i++) {
            MapId mapId = overworld.getFreeMapId();
            mapIds.add(mapId.id());

            // Register MapItemSavedData in overworld
            MapItemSavedData savedData = MapItemSavedData.createFresh(
                player.getX(), player.getZ(), (byte) 3, false, false, overworld.dimension()
            );
            overworld.setMapData(mapId, savedData);
        }

        // Find next free index for storage subdirectory
        int nextIndex = 0;
        File dataFolder = new File(ImageFrameMod.instance.getConfigFolder(), "data");
        while (new File(dataFolder, String.valueOf(nextIndex)).exists()) {
            nextIndex++;
        }

        FabricImageMap map = new FabricImageMap();
        map.index = nextIndex;
        map.name = name;
        map.url = url;
        map.width = width;
        map.height = height;
        map.mapIds = mapIds;
        map.owner = owner;
        map.dithering = dithering;
        map.isCombined = combined;
        map.creationDate = System.currentTimeMillis();

        maps.put(name.toLowerCase(), map);
        saveMapData(map);

        // Process image asynchronously
        final List<ItemFrame> finalFrames = selectedFrames;
        Scheduler.runTaskAsynchronously(null, () -> loadAndProcessImageAsync(map, true, player, finalFrames));
    }

    private void loadAndProcessImageAsync(FabricImageMap map, boolean isNewCreation, ServerPlayer notifyPlayer, List<ItemFrame> fillFrames) {
        try {
            // Try loading from local pre-existing files first to avoid re-downloading
            if (!isNewCreation && tryLoadFromLocalFiles(map)) {
                ImageFrameMod.LOGGER.info("[ImageFrame] Map \"" + map.name + "\" loaded locally (no network download needed).");

                // Apply first frame to Minecraft maps
                ServerLevel overworld = null;
                if (playerListSource() != null) {
                    overworld = playerListSource().getServer().getLevel(Level.OVERWORLD);
                }
                if (overworld != null && map.framesColors != null && !map.framesColors.isEmpty()) {
                    byte[][] initialColors = map.framesColors.get(0);
                    for (int i = 0; i < map.mapIds.size(); i++) {
                        int id = map.mapIds.get(i);
                        MapItemSavedData savedData = getOrCreateMapData(overworld, id);
                        FabricMapHelper.setColors(savedData, initialColors[i]);
                    }
                }
                return;
            }

            if (map.url == null || map.url.trim().isEmpty()) {
                throw new Exception("Map has no URL specified and no local image files were found.");
            }

            URLConnection conn = new URL(map.url).openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            // Attempt to detect if it's a GIF
            boolean isGif = map.url.toLowerCase().contains(".gif") || map.url.toLowerCase().contains("gif");
            List<BufferedImage> images = new ArrayList<>();
            List<Integer> delays = new ArrayList<>();

            try (InputStream in = conn.getInputStream()) {
                if (isGif) {
                    List<GifReader.ImageFrame> frames = GifReader.readGif(in, com.loohp.imageframe.ImageFrame.maxImageFileSize).get();
                    for (GifReader.ImageFrame f : frames) {
                        images.add(f.getImage());
                        delays.add(f.getDelay());
                    }
                } else {
                    BufferedImage img = ImageIO.read(in);
                    if (img != null) {
                        images.add(img);
                        delays.add(0);
                    }
                }
            }

            if (images.isEmpty()) {
                throw new Exception("Could not load any frames from the image.");
            }

            map.isAnimated = isGif && images.size() > 1;
            map.framesColors = new CopyOnWriteArrayList<>();
            map.framesDelays = delays;

            // Process each frame and crop into tiles
            int targetWidth = map.width * 128;
            int targetHeight = map.height * 128;

            for (BufferedImage rawImg : images) {
                // Scale image to final size
                BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = scaled.createGraphics();
                g.drawImage(rawImg.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH), 0, 0, null);
                g.dispose();

                byte[][] frameTilesColors = new byte[map.mapIds.size()][16384];

                for (int y = 0; y < map.height; y++) {
                    for (int x = 0; x < map.width; x++) {
                        int tileIndex = y * map.width + x;
                        byte[] tileColors = frameTilesColors[tileIndex];

                        for (int px = 0; px < 128; px++) {
                            for (int py = 0; py < 128; py++) {
                                int rx = x * 128 + px;
                                int ry = y * 128 + py;
                                int argb = scaled.getRGB(rx, ry);
                                int a = (argb >> 24) & 0xFF;
                                int r = (argb >> 16) & 0xFF;
                                int gVal = (argb >> 8) & 0xFF;
                                int b = argb & 0xFF;

                                tileColors[py * 128 + px] = FabricMapColorPalette.getClosestColorIndex(r, gVal, b, a);
                            }
                        }
                    }
                }

                map.framesColors.add(frameTilesColors);
            }

            // Apply first frame to Minecraft maps
            ServerLevel overworld = notifyPlayer != null ? (ServerLevel) notifyPlayer.level() : null;
            if (overworld == null && !map.mapIds.isEmpty()) {
                // Find default overworld level
                if (playerListSource() != null) {
                    overworld = playerListSource().getServer().getLevel(Level.OVERWORLD);
                }
            }

            if (overworld != null) {
                byte[][] initialColors = map.framesColors.get(0);
                for (int i = 0; i < map.mapIds.size(); i++) {
                    int id = map.mapIds.get(i);
                    MapItemSavedData savedData = getOrCreateMapData(overworld, id);
                    FabricMapHelper.setColors(savedData, initialColors[i]);
                }
            }

            if (isNewCreation) {
                // Save local PNG images for absolute offline parity with Spigot
                saveLocalPNGFiles(map, images);
                
                // Save the final updated data.json with isAnimated and markers properties
                saveMapData(map);

                if (notifyPlayer != null) {
                    notifyPlayer.sendSystemMessage(Component.literal("§a[ImageFrame] Image map \"" + map.name + "\" created and rendered successfully!"));
                    // Give maps
                    giveMapsToPlayer(notifyPlayer, map, fillFrames);
                }
            }

            // Broadcast update to HD clients so they re-fetch textures
            try {
                com.loohp.imageframe.fabric.network.ImageFrameNetworkHandler.broadcastImageUpdate(map);
            } catch (Exception ex) {
                ImageFrameMod.LOGGER.debug("[ImageFrame] Could not broadcast HD update (server may not be fully started): " + ex.getMessage());
            }

        } catch (Exception e) {
            ImageFrameMod.LOGGER.error("[ImageFrame] Error processing image for " + map.name + ": ", e);
            if (isNewCreation) {
                if (notifyPlayer != null) {
                    notifyPlayer.sendSystemMessage(Component.literal("§c[ImageFrame] Error downloading/processing image: " + e.getMessage()));
                }
                // Remove map created in error
                maps.remove(map.name.toLowerCase());
                deleteMapFolder(map);
            }
        }
    }

    private void giveMapsToPlayer(ServerPlayer player, FabricImageMap map, List<ItemFrame> fillFrames) {
        // Link item frames if selection was used
        if (fillFrames != null && !fillFrames.isEmpty()) {
            for (int i = 0; i < Math.min(fillFrames.size(), map.mapIds.size()); i++) {
                ItemFrame frame = fillFrames.get(i);
                int mapId = map.mapIds.get(i);

                ItemStack mapItem = new ItemStack(Items.FILLED_MAP);
                mapItem.set(net.minecraft.core.component.DataComponents.MAP_ID, new MapId(mapId));

                frame.setItem(mapItem);
            }
            player.sendSystemMessage(Component.literal("§a[ImageFrame] Item frames auto-filled!"));
            return;
        }

        // If combined
        if (map.isCombined) {
            ItemStack combinedItem = new ItemStack(Items.FILLED_MAP);
            combinedItem.set(net.minecraft.core.component.DataComponents.MAP_ID, new MapId(map.mapIds.get(0)));
            combinedItem.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§6Combined: " + map.name));

            // Add lore indicating the size
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("§7" + com.loohp.imageframe.fabric.language.FabricLanguageManager.getInstance().get("imageframe.settings.combined_map_item.lore.1", map.width, map.height)));
            lore.add(Component.literal("§8ImageID: " + map.index));
            lore.add(Component.literal("§8Size: " + map.width + "x" + map.height));
            combinedItem.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));

            // Custom metadata in NBT
            com.loohp.imageframe.objectholders.CombinedMapItemInfo info = new com.loohp.imageframe.objectholders.CombinedMapItemInfo(map.index);
            FabricMapHelper.withCombinedMapItemInfo(combinedItem, info);

            player.getInventory().add(combinedItem);
        } else {
            // Give all separate maps
            for (int id : map.mapIds) {
                ItemStack mapItem = new ItemStack(Items.FILLED_MAP);
                mapItem.set(net.minecraft.core.component.DataComponents.MAP_ID, new MapId(id));
                player.getInventory().add(mapItem);
            }
        }
        player.sendSystemMessage(Component.literal("§a[ImageFrame] Image maps have been added to your inventory."));
    }

    private final Map<UUID, Set<Integer>> playerVisibleMapsCache = new ConcurrentHashMap<>();

    public void updateVisibleMapsCache(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Set<Integer> set = new HashSet<>();
            
            // 1. Check hands
            ItemStack main = player.getMainHandItem();
            if (!main.isEmpty() && main.is(Items.FILLED_MAP)) {
                MapId mId = main.get(net.minecraft.core.component.DataComponents.MAP_ID);
                if (mId != null) {
                    set.add(mId.id());
                }
            }
            ItemStack off = player.getOffhandItem();
            if (!off.isEmpty() && off.is(Items.FILLED_MAP)) {
                MapId mId = off.get(net.minecraft.core.component.DataComponents.MAP_ID);
                if (mId != null) {
                    set.add(mId.id());
                }
            }

            // 2. Check nearby ItemFrames within 32 blocks
            try {
                net.minecraft.world.phys.AABB box = player.getBoundingBox().inflate(32.0);
                List<ItemFrame> frames = player.level().getEntitiesOfClass(ItemFrame.class, box);
                for (ItemFrame frame : frames) {
                    ItemStack item = frame.getItem();
                    if (!item.isEmpty() && item.is(Items.FILLED_MAP)) {
                        MapId mId = item.get(net.minecraft.core.component.DataComponents.MAP_ID);
                        if (mId != null) {
                            set.add(mId.id());
                        }
                    }
                }
            } catch (Exception e) {
                // Prevent any minor race condition
            }
            playerVisibleMapsCache.put(player.getUUID(), set);
        }

        // Clean up offline players
        if (playerVisibleMapsCache.size() > server.getPlayerCount()) {
            Set<UUID> onlineUuids = new HashSet<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                onlineUuids.add(player.getUUID());
            }
            playerVisibleMapsCache.keySet().removeIf(uuid -> !onlineUuids.contains(uuid));
        }
    }

    private void runAnimationLoop() {
        while (true) {
            try {
                long now = System.currentTimeMillis();
                for (FabricImageMap map : maps.values()) {
                    if (!map.isAnimated || map.isPaused || map.framesColors == null || map.framesColors.size() <= 1) {
                        continue;
                    }

                    int delay = map.framesDelays.get(map.currentFrameIndex);
                    if (delay <= 0) delay = 100; // Default to 100ms

                    if (now - map.lastFrameUpdate >= delay) {
                        map.currentFrameIndex = (map.currentFrameIndex + 1) % map.framesColors.size();
                        map.lastFrameUpdate = now;

                        // Update MapItemSavedData in memory and send packets
                        updateAnimationMapDataAndSend(map);
                    }
                }
                Thread.sleep(10); // Sleep 10ms
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                ImageFrameMod.LOGGER.error("[ImageFrame] Error in animation loop: ", e);
            }
        }
    }

    private void updateAnimationMapDataAndSend(FabricImageMap map) {
        if (playerListSource() == null) return;

        // Find overworld
        ServerLevel overworld = playerListSource().getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        byte[][] frameColors = map.framesColors.get(map.currentFrameIndex);

        for (int i = 0; i < map.mapIds.size(); i++) {
            int id = map.mapIds.get(i);
            MapItemSavedData savedData = getOrCreateMapData(overworld, id);
            FabricMapHelper.setColors(savedData, frameColors[i]);

            // Send the packet only to players who are watching this map
            ClientboundMapItemDataPacket packet = FabricMapHelper.createMapPacket(id, frameColors[i], null);
            for (ServerPlayer player : playerListSource().getPlayers()) {
                Set<Integer> visibleIds = playerVisibleMapsCache.get(player.getUUID());
                if (visibleIds != null && visibleIds.contains(id)) {
                    FabricMapHelper.sendPacket(player, packet);
                }
            }
        }

        // Broadcast frame update signal to HD clients for instant zero-lag HD animation
        com.loohp.imageframe.fabric.network.ImageFrameNetworkHandler.broadcastFrameUpdate(map, map.currentFrameIndex);
    }

    private net.minecraft.server.players.PlayerList playerListSource() {
        return playerListSource;
    }

    private static net.minecraft.server.players.PlayerList playerListSource;
    public static void setPlayerListSource(net.minecraft.server.players.PlayerList source) {
        playerListSource = source;
    }

    /**
     * Static accessor for the player list, used by ImageFrameNetworkHandler.
     */
    public static net.minecraft.server.players.PlayerList getPlayerListSourceStatic() {
        return playerListSource;
    }
}
