package com.loohp.platformscheduler;

import java.util.concurrent.ForkJoinPool;

/**
 * Mock de la librería PlatformScheduler para permitir la ejecución de tareas
 * asíncronas de manera nativa e independiente de Spigot.
 */
public class Scheduler {

    public static void runTaskAsynchronously(Object plugin, Runnable runnable) {
        ForkJoinPool.commonPool().execute(runnable);
    }
}
