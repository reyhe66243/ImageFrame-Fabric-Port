package com.loohp.imageframe.fabric.utils;

import net.minecraft.world.level.material.MapColor;

import java.awt.image.BufferedImage;
import java.util.Arrays;

public class FabricMapColorPalette {

    private static final int[] R = new int[256];
    private static final int[] G = new int[256];
    private static final int[] B = new int[256];

    static {
        // Inicializar dinámicamente desde net.minecraft.world.level.material.MapColor
        int loadedColors = 0;
        for (int b = 0; b < 64; b++) {
            MapColor base = null;
            try {
                base = MapColor.byId(b);
            } catch (Throwable t) {
                // Ignorar
            }
            if (base == null || base.id != b) {
                continue;
            }

            int baseColor = base.col;
            int baseR = (baseColor >> 16) & 0xFF;
            int baseG = (baseColor >> 8) & 0xFF;
            int baseB = baseColor & 0xFF;

            for (int s = 0; s < 4; s++) {
                int index = b * 4 + s;
                if (index >= 256) break;

                int mul = 220;
                if (s == 0) mul = 180;
                if (s == 1) mul = 220;
                if (s == 2) mul = 255;
                if (s == 3) mul = 135;

                R[index] = (baseR * mul) / 255;
                G[index] = (baseG * mul) / 255;
                B[index] = (baseB * mul) / 255;
            }
            loadedColors++;
        }
        
        // Si por alguna razón la inicialización de NMS falla (por ejemplo, en un entorno de pruebas sin MC cargado),
        // llenamos con colores por defecto para evitar punteros nulos.
        if (loadedColors == 0) {
            // Relleno seguro
            for (int i = 0; i < 256; i++) {
                R[i] = 0;
                G[i] = 0;
                B[i] = 0;
            }
        }
    }

    public static byte getClosestColorIndex(int r, int g, int b, int a) {
        if (a < 128) {
            return 0; // Transparente
        }

        int bestIndex = 4; // Comenzamos en 4 porque 0-3 son transparente o negro
        int bestDist = Integer.MAX_VALUE;

        for (int i = 4; i < 256; i++) {
            int dr = r - R[i];
            int dg = g - G[i];
            int db = b - B[i];
            int dist = dr * dr + dg * dg + db * db;
            if (dist < bestDist) {
                bestDist = dist;
                bestIndex = i;
            }
        }

        return (byte) bestIndex;
    }

    private static int clampByte(float value) {
        int rounded = Math.round(value);
        if (rounded < 0) return 0;
        if (rounded > 255) return 255;
        return rounded;
    }

    public static byte[][] convertImageToTiles(BufferedImage scaledImage, int tilesWide, int tilesHigh, String ditheringType) {
        if (tilesWide <= 0 || tilesHigh <= 0) {
            return new byte[0][];
        }
        int tileCount = tilesWide * tilesHigh;
        byte[][] tiles = new byte[tileCount][16384];
        if (scaledImage == null) {
            return tiles;
        }

        int w = tilesWide * 128;
        int h = tilesHigh * 128;
        int copyW = Math.min(w, scaledImage.getWidth());
        int copyH = Math.min(h, scaledImage.getHeight());
        int[] pixels = new int[w * h];
        scaledImage.getRGB(0, 0, copyW, copyH, pixels, 0, w);

        boolean noDithering = ditheringType != null
                && (ditheringType.equalsIgnoreCase("nearest-color") || ditheringType.equalsIgnoreCase("none"));

        if (noDithering) {
            for (int y = 0; y < h; y++) {
                int tileRowOffset = (y >> 7) * tilesWide;
                int localRowOffset = (y & 127) << 7;
                int rowOffset = y * w;
                for (int x = 0; x < w; x++) {
                    int argb = pixels[rowOffset + x];
                    int a = (argb >>> 24) & 0xFF;
                    int r = (argb >>> 16) & 0xFF;
                    int g = (argb >>> 8) & 0xFF;
                    int b = argb & 0xFF;
                    tiles[tileRowOffset + (x >> 7)][localRowOffset | (x & 127)] = getClosestColorIndex(r, g, b, a);
                }
            }
            return tiles;
        }

        float[] currR = new float[w];
        float[] currG = new float[w];
        float[] currB = new float[w];
        float[] nextR = new float[w];
        float[] nextG = new float[w];
        float[] nextB = new float[w];

        for (int y = 0; y < h; y++) {
            Arrays.fill(nextR, 0f);
            Arrays.fill(nextG, 0f);
            Arrays.fill(nextB, 0f);

            boolean hasNextRow = (y + 1) < h;
            int tileRowOffset = (y >> 7) * tilesWide;
            int localRowOffset = (y & 127) << 7;
            int rowOffset = y * w;

            for (int x = 0; x < w; x++) {
                int argb = pixels[rowOffset + x];
                int a = (argb >>> 24) & 0xFF;
                int tileIndex = tileRowOffset + (x >> 7);
                int localIndex = localRowOffset | (x & 127);

                if (a < 128) {
                    tiles[tileIndex][localIndex] = 0;
                    continue;
                }

                int oldR = clampByte(((argb >>> 16) & 0xFF) + currR[x]);
                int oldG = clampByte(((argb >>> 8) & 0xFF) + currG[x]);
                int oldB = clampByte((argb & 0xFF) + currB[x]);

                byte mapColor = getClosestColorIndex(oldR, oldG, oldB, 255);
                tiles[tileIndex][localIndex] = mapColor;

                int idx = mapColor & 0xFF;
                float er = oldR - R[idx];
                float eg = oldG - G[idx];
                float eb = oldB - B[idx];

                if (x + 1 < w) {
                    currR[x + 1] += er * (7f / 16f);
                    currG[x + 1] += eg * (7f / 16f);
                    currB[x + 1] += eb * (7f / 16f);
                }
                if (hasNextRow) {
                    if (x - 1 >= 0) {
                        nextR[x - 1] += er * (3f / 16f);
                        nextG[x - 1] += eg * (3f / 16f);
                        nextB[x - 1] += eb * (3f / 16f);
                    }
                    nextR[x] += er * (5f / 16f);
                    nextG[x] += eg * (5f / 16f);
                    nextB[x] += eb * (5f / 16f);
                    if (x + 1 < w) {
                        nextR[x + 1] += er * (1f / 16f);
                        nextG[x + 1] += eg * (1f / 16f);
                        nextB[x + 1] += eb * (1f / 16f);
                    }
                }
            }

            float[] tmp;
            tmp = currR; currR = nextR; nextR = tmp;
            tmp = currG; currG = nextG; nextG = tmp;
            tmp = currB; currB = nextB; nextB = tmp;
        }

        return tiles;
    }
}
