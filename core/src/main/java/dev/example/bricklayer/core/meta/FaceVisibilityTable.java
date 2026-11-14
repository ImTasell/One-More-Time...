/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.meta;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * "Is face {@code d} of state A visible next to state B?"
 *
 * <p>The two cases that dominate a real world are answered from the meta table alone: air next to
 * anything shows the face, and an opaque full cube next to anything hides it. Everything else
 * (glass, leaves, same-block culling, NeoForge's face-hiding extension) goes through a shared,
 * lock-free, append-only open-addressing table keyed by {@code (A << 32) | (B << 3) | d}. Entries
 * are never evicted, so a warm table always hits and the oracle is only consulted once per distinct
 * triple.
 *
 * <p>Slot {@code 0} of a key is a legal key, which is why presence is tracked in the value array (0
 * empty, 1 hidden, 2 visible) rather than in the key array.
 */
public final class FaceVisibilityTable {
  /** Resolves visibility for a triple the table has not seen yet. Implemented by the mod glue. */
  @FunctionalInterface
  public interface Oracle {
    /**
     * @param stateId the state owning the face
     * @param neighbourState the state in front of that face
     * @param face face ordinal, 0..5
     * @return true when the face must be rendered
     */
    boolean visible(int stateId, int neighbourState, int face);
  }

  /** Air is state id 0 in vanilla's block-state registry. */
  public static final int AIR_STATE = 0;

  private static final int SLOTS = 1 << 16;
  private static final int MASK = SLOTS - 1;
  private static final VarHandle LONGS = MethodHandles.arrayElementVarHandle(long[].class);
  private static final VarHandle INTS = MethodHandles.arrayElementVarHandle(int[].class);

  private final MetaTable meta;
  private final long[] keys = new long[SLOTS];
  private final int[] values = new int[SLOTS];

  public FaceVisibilityTable(MetaTable meta) {
    this.meta = meta;
  }

  /**
   * Returns whether the face is visible, filling the cache from {@code oracle} on a miss.
   *
   * <p>Safe to call from several threads at once: racing fills compute the same answer and the key
   * CAS decides which thread stores it.
   */
  public boolean visible(int stateId, int neighbourState, int face, Oracle oracle) {
    if (neighbourState == AIR_STATE) {
      return true;
    }
    if ((meta.flags(neighbourState) & CellFlags.OPAQUE_FULL) != 0) {
      return false;
    }
    long key = ((long) stateId << 32) | ((long) neighbourState << 3) | face;
    int slot = mix(key) & MASK;
    for (int probe = 0; probe < SLOTS; probe++) {
      int value = (int) INTS.getVolatile(values, slot);
      if (value != 0) {
        if ((long) LONGS.getVolatile(keys, slot) == key) {
          return value == 2;
        }
      } else if (LONGS.compareAndSet(keys, slot, 0L, key)) {
        boolean visible = oracle.visible(stateId, neighbourState, face);
        INTS.setVolatile(values, slot, visible ? 2 : 1);
        return visible;
      } else if ((long) LONGS.getVolatile(keys, slot) == key) {
        // Another thread installed the same key between our read and our CAS.
        boolean visible = oracle.visible(stateId, neighbourState, face);
        INTS.setVolatile(values, slot, visible ? 2 : 1);
        return visible;
      }
      slot = (slot + 1) & MASK;
    }
    // The cache is full: answer directly rather than growing it.
    return oracle.visible(stateId, neighbourState, face);
  }

  /** Number of filled slots, for the debug overlay. */
  public int size() {
    int filled = 0;
    for (int i = 0; i < SLOTS; i++) {
      if ((int) INTS.getVolatile(values, i) != 0) {
        filled++;
      }
    }
    return filled;
  }

  private static int mix(long key) {
    long h = key * 0x9E3779B97F4A7C15L;
    return (int) (h ^ (h >>> 32));
  }
}
