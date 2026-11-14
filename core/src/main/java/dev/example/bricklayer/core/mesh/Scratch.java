/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.mesh;

import dev.example.bricklayer.core.grid.SectionGrid;
import dev.example.bricklayer.core.light.AoCalculator;
import dev.example.bricklayer.core.quad.LegacyRandom;
import java.util.Arrays;

/**
 * One block of reusable working memory per meshing thread.
 *
 * <p>Everything the pipeline needs that is bigger than a handful of values lives here, so a mesh
 * task allocates nothing: the deferred-cell list, the flood-fill stack, the visibility and visited
 * bitsets, the per-quad AO and light buffers, and the tint memo.
 *
 * <p>The tint memo is an open-addressed table keyed by {@code (state, tintIndex, localX, localZ)},
 * which is safe because a biome tint depends on the column and not on the height. States whose tint
 * is height dependent bypass the memo entirely through {@code CellFlags.TINT_POSITIONAL}.
 */
public final class Scratch {
  private static final int TINT_SLOTS = 1024;
  private static final int TINT_MASK = TINT_SLOTS - 1;

  /** Deferred cell indices, in scan order. */
  public final int[] deferred = new int[SectionGrid.CELLS];

  /** Flood-fill stack; one entry per interior cell is always enough. */
  public final int[] floodStack = new int[SectionGrid.CELLS];

  /** Bitset over interior cells, used by the visibility pass. */
  public final long[] visBits = new long[SectionGrid.CELLS / 64];

  /** Per-cell visit markers for the visibility pass. */
  public final byte[] visitFlags = new byte[SectionGrid.CELLS];

  /** Per-vertex AO factor for the quad being emitted. */
  public final float[] ao = new float[4];

  /** Per-vertex packed light for the quad being emitted. */
  public final int[] light = new int[4];

  /** Per-vertex combined brightness handed to the sink. */
  public final float[] shade = new float[4];

  /** AO and light pass, preallocated so it stays allocation free. */
  public final AoCalculator aoCalculator = new AoCalculator();

  /** Variant draw scratch. */
  public final LegacyRandom random = new LegacyRandom();

  private final long[] tintKeys = new long[TINT_SLOTS];
  private final int[] tintValues = new int[TINT_SLOTS];

  private static final ThreadLocal<Scratch> LOCAL = ThreadLocal.withInitial(Scratch::new);

  /** The current thread's scratch block, created lazily. */
  public static Scratch get() {
    return LOCAL.get();
  }

  /** Resets the per-task state. Cheap: two array fills and a counter free table. */
  public void reset() {
    Arrays.fill(tintKeys, -1L);
  }

  /**
   * Returns a memoised tint, resolving it through {@code resolver} on a miss.
   *
   * @param positional true when the tint depends on height and must not be memoised
   * @return packed {@code 0xRRGGBB}, or -1
   */
  public int tint(
      TintResolver resolver,
      int stateId,
      int tintIndex,
      int localX,
      int localZ,
      boolean positional) {
    if (positional) {
      return resolver.tint(stateId, tintIndex, localX, localZ);
    }
    long key =
        ((long) stateId << 24)
            | ((long) (tintIndex & 0xFF) << 16)
            | ((long) (localX & 15) << 4)
            | (localZ & 15);
    int slot = (int) ((key * 0x9E3779B97F4A7C15L) >>> 40) & TINT_MASK;
    for (int probe = 0; probe < TINT_SLOTS; probe++) {
      long existing = tintKeys[slot];
      if (existing == key) {
        return tintValues[slot];
      }
      if (existing == -1L) {
        int value = resolver.tint(stateId, tintIndex, localX, localZ);
        tintKeys[slot] = key;
        tintValues[slot] = value;
        return value;
      }
      slot = (slot + 1) & TINT_MASK;
    }
    return resolver.tint(stateId, tintIndex, localX, localZ);
  }

  /** Clears the memo, e.g. after a resource reload. */
  public void clearTintMemo() {
    Arrays.fill(tintKeys, -1L);
  }
}
