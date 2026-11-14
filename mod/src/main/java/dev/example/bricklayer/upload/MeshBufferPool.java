/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.upload;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import java.util.ArrayDeque;

/**
 * Hands out per-layer {@link ByteBufferBuilder}s in size classes from 64 KiB to 4 MiB.
 *
 * <p>Each layer tracks the largest buffer it has needed so a section that outgrows its buffer gets
 * a right-sized replacement instead of growing on every call. Buffers that have been idle for 30
 * seconds are released, which keeps a long exploration session from holding tens of megabytes of
 * scratch memory.
 */
public final class MeshBufferPool {
  private static final int MIN_SIZE = 64 * 1024;
  private static final int MAX_SIZE = 4 * 1024 * 1024;
  private static final long IDLE_TRIM_NANOS = 30_000_000_000L;

  private final LayerPool[] layers = {
    new LayerPool("solid"), new LayerPool("cutout_mipped"), new LayerPool("cutout")
  };

  /**
   * Acquires a buffer for a layer; callers must return it with {@link #release(int,
   * ByteBufferBuilder)}.
   */
  public ByteBufferBuilder acquire(int layer) {
    return layers[clamp(layer)].acquire();
  }

  public void release(int layer, ByteBufferBuilder buffer) {
    if (buffer != null) {
      layers[clamp(layer)].release(buffer);
    }
  }

  /** Called periodically; drops idle buffers. */
  public void trimIdle() {
    for (LayerPool layer : layers) {
      layer.trimIdle();
    }
  }

  /** Rounds a required size up to the next size class, never above the maximum. */
  public static int sizeClass(int requiredBytes) {
    int size = MIN_SIZE;
    while (size < requiredBytes && size < MAX_SIZE) {
      size <<= 1;
    }
    return size;
  }

  public String describe() {
    StringBuilder builder = new StringBuilder();
    for (LayerPool layer : layers) {
      builder
          .append(layer.name)
          .append(": pooled=")
          .append(layer.pooled())
          .append(" highWater=")
          .append(layer.highWater)
          .append(' ');
    }
    return builder.toString();
  }

  public void clear() {
    for (LayerPool layer : layers) {
      layer.clear();
    }
  }

  private static int clamp(int layer) {
    return layer < 0 ? 0 : Math.min(layer, 2);
  }

  private static final class LayerPool {
    private final String name;
    private final ArrayDeque<ByteBufferBuilder> free = new ArrayDeque<>();
    private int highWater;
    private long lastUseNanos = System.nanoTime();

    LayerPool(String name) {
      this.name = name;
    }

    synchronized ByteBufferBuilder acquire() {
      lastUseNanos = System.nanoTime();
      ByteBufferBuilder buffer = free.poll();
      return buffer != null ? buffer : new ByteBufferBuilder(Math.max(MIN_SIZE, highWater));
    }

    synchronized void release(ByteBufferBuilder buffer) {
      lastUseNanos = System.nanoTime();
      if (buffer == null) {
        return;
      }
      free.add(buffer);
    }

    /** Records how large a buffer the mesher needed, so the next one is right-sized. */
    synchronized void recordSize(int bytes) {
      if (bytes > highWater) {
        highWater = Math.min(MAX_SIZE, sizeClass(bytes));
      }
    }

    synchronized int pooled() {
      return free.size();
    }

    synchronized void trimIdle() {
      if (!free.isEmpty() && System.nanoTime() - lastUseNanos > IDLE_TRIM_NANOS) {
        free.clear();
        // Keep the high-water mark: re-growing on the next burst is cheaper than a rebuild.
      }
    }

    synchronized void clear() {
      free.clear();
      highWater = 0;
    }
  }
}
