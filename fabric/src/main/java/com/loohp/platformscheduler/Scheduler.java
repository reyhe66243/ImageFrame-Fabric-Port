package com.loohp.platformscheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock de la librería PlatformScheduler para permitir la ejecución de tareas
 * asíncronas de manera nativa e independiente de Spigot.
 *
 * Se usa un pool dedicado (hilos daemon) en lugar de ForkJoinPool.commonPool()
 * porque el bucle de animación de GIFs ocupa un hilo permanentemente y, en
 * máquinas con pocos núcleos, podía dejar sin ejecutar las descargas de imágenes
 * (síntoma: "Reserving Map IDs and downloading image..." sin respuesta).
 */
public class Scheduler {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    private static final ExecutorService ASYNC_EXECUTOR = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "ImageFrame-Async-" + THREAD_COUNTER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });

    public static void runTaskAsynchronously(Object plugin, Runnable runnable) {
        ASYNC_EXECUTOR.execute(runnable);
    }
}
