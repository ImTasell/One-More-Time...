/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.grid;

import java.util.ArrayDeque;

/**
 * Fixed-size free list of snapshots bounded by the number of in-flight tasks.
 *
 * <p>A pool that is empty hands out a fresh snapshot; that can only happen when the caller broke
 * the in-flight cap, and the extra snapshot is dropped rather than pooled on release so the free
 * list never grows past its bound.
 */
public final class SnapshotPool {
  private final ArrayDeque<SectionSnapshot> free;
  private final int max;
  private int created;
  private int handedOut;
  private int peak;

  public SnapshotPool(int max) {
    this.max = Math.max(1, max);
    this.free = new ArrayDeque<>(this.max);
    for (int i = 0; i < this.max; i++) {
      free.add(new SectionSnapshot());
      created++;
    }
  }

  public int max() {
    return max;
  }

  public synchronized SectionSnapshot acquire() {
    SectionSnapshot snapshot = free.poll();
    if (snapshot == null) {
      snapshot = new SectionSnapshot();
      created++;
    }
    snapshot.reset();
    handedOut++;
    if (handedOut > peak) {
      peak = handedOut;
    }
    return snapshot;
  }

  public synchronized void release(SectionSnapshot snapshot) {
    if (snapshot == null) {
      return;
    }
    handedOut--;
    snapshot.reset();
    if (free.size() < max) {
      free.add(snapshot);
    }
  }

  public synchronized int freeCount() {
    return free.size();
  }

  public synchronized int createdCount() {
    return created;
  }

  public synchronized int peakHandedOut() {
    return peak;
  }

  public synchronized void clear() {
    free.clear();
    for (int i = 0; i < max; i++) {
      free.add(new SectionSnapshot());
    }
    handedOut = 0;
  }
}
