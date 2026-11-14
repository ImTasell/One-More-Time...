/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.meta;

/**
 * Per-block-state facts, laid out as a struct of arrays and indexed by vanilla state id.
 *
 * <p>Published through a single volatile reference after a resource reload and never mutated
 * afterwards, so any number of worker threads can read it without synchronisation.
 */
public final class MetaTable {
  /** An empty table, useful before the first model bake completes. */
  public static final MetaTable EMPTY = new MetaTable(new int[0], new float[0], new int[0]);

  private final int[] flags;
  private final float[] shadeBrightness;
  private final int[] modelId;

  public MetaTable(int[] flags, float[] shadeBrightness, int[] modelId) {
    int size = flags.length;
    if (shadeBrightness.length != size || modelId.length != size) {
      throw new IllegalArgumentException("meta arrays must share a length");
    }
    this.flags = flags;
    this.shadeBrightness = shadeBrightness;
    this.modelId = modelId;
  }

  public int size() {
    return flags.length;
  }

  public int flags(int stateId) {
    return stateId >= 0 && stateId < flags.length ? flags[stateId] : CellFlags.FORCE_VANILLA;
  }

  public boolean has(int stateId, int bit) {
    return (flags(stateId) & bit) != 0;
  }

  /** Vanilla's per-state AO darkness factor. */
  public float shadeBrightness(int stateId) {
    return stateId >= 0 && stateId < shadeBrightness.length ? shadeBrightness[stateId] : 1.0f;
  }

  /** Quad-store model index for the state, or -1. */
  public int model(int stateId) {
    return stateId >= 0 && stateId < modelId.length ? modelId[stateId] : -1;
  }

  /** Convenience for the glue layer: a bit test on a raw flag word. */
  public static boolean hasFlag(int flags, int bit) {
    return (flags & bit) != 0;
  }
}
