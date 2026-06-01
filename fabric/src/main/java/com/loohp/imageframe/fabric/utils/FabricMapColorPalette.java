package com.loohp.imageframe.fabric.utils;

import net.minecraft.world.level.material.MapColor;

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
        double bestDist = Double.MAX_VALUE;

        for (int i = 4; i < 256; i++) {
            int pr = R[i];
            int pg = G[i];
            int pb = B[i];

            double dist = Math.pow(r - pr, 2) + Math.pow(g - pg, 2) + Math.pow(b - pb, 2);
            if (dist < bestDist) {
                bestDist = dist;
                bestIndex = i;
            }
        }

        return (byte) bestIndex;
    }
}
