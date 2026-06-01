package com.loohp.imageframe;

/**
 * Contenedor de configuración estática para resolver las llamadas de ImageFrame
 * desde el código compartido de procesamiento de imágenes.
 */
public class ImageFrame {
    public static Object plugin = new Object();
    public static long maxImageFileSize = 104857600L; // 100 MB por defecto
}
