package com.loohp.imageframe.fabric.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicInteger;

public class MultipartHdMapInfo {

    private final byte[] EMPTY_ARRAY = new byte[0];

    private final Int2ObjectMap<byte[]> buffer;
    private final AtomicInteger lastIndex;

    public MultipartHdMapInfo() {
        this.buffer = new Int2ObjectArrayMap<>();
        this.lastIndex = new AtomicInteger(0);
    }

    public void put(int index, byte[] data) {
        this.buffer.put(index, data);
    }

    public void setLastIndex(int index) {
        this.lastIndex.set(index);
    }

    public boolean isCompleted() {
        int max = lastIndex.get();
        if (max <= 0) {
            return false;
        }
        for (int i = 0; i <= max; i++) {
            if (!buffer.containsKey(i)) {
                return false;
            }
        }
        return true;
    }

    public byte[] complete() {
        int max = lastIndex.get();
        int length = buffer.values().stream().mapToInt(b -> b.length).sum();
        ByteArrayOutputStream out = new ByteArrayOutputStream(length);
        for (int i = 0; i <= max; i++) {
            out.writeBytes(buffer.getOrDefault(i, EMPTY_ARRAY));
        }
        return out.toByteArray();
    }
}
