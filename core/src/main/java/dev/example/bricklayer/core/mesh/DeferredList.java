/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.mesh;

import java.util.Arrays;

/** Cell indices the fast path could not handle, in scan order, for the vanilla renderers. */
public final class DeferredList {
  private int[] items;
  private int size;

  public DeferredList(int capacity) {
    this.items = new int[Math.max(1, capacity)];
  }

  public void clear() {
    size = 0;
  }

  public void add(int cell) {
    if (size == items.length) {
      items = Arrays.copyOf(items, items.length * 2);
    }
    items[size++] = cell;
  }

  public int size() {
    return size;
  }

  public int get(int index) {
    return items[index];
  }

  public int[] backing() {
    return items;
  }

  /** A right-sized copy, safe to keep in a {@link MeshResult} once the task returns. */
  public int[] toArray() {
    return Arrays.copyOf(items, size);
  }
}
