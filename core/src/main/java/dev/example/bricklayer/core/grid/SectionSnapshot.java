/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.grid;

/**
 * One immutable-while-in-flight copy of a section and its border.
 *
 * <p>The state array holds vanilla global block-state ids; the light array holds {@code (sky << 4)
 * | block} per cell and is filled by the worker, never by the main thread. A snapshot is roughly 29
 * KB and is recycled through {@link SnapshotPool}, so steady-state meshing allocates nothing.
 *
 * <p>Threading: a snapshot is owned by the main thread until it is submitted, then owned by exactly
 * one worker, then owned by the render thread until it is returned to the pool.
 */
public final class SectionSnapshot {
  /** Global block-state ids for every grid cell. */
  public final int[] state = new int[SectionGrid.VOLUME];

  /** Packed {@code (sky << 4) | block} light for every grid cell. */
  public final byte[] light = new byte[SectionGrid.VOLUME];

  /** Vanilla section key ({@code SectionPos#asLong}). */
  public long sectionKey;

  /** Monotonic per-section dirty version this snapshot was taken for. */
  public int version;

  /** Resource generation the snapshot belongs to; results from older generations are dropped. */
  public int resourceGeneration;

  /** Section-local block-entity cell indices. */
  public int[] bePositions = new int[8];

  /** Number of valid entries in {@link #bePositions}. */
  public int beCount;

  /** True when the section proper is entirely air, so the mesher can bail out immediately. */
  public boolean empty;

  /** Clears the snapshot for reuse. Arrays are left allocated. */
  public void reset() {
    sectionKey = 0L;
    version = 0;
    resourceGeneration = 0;
    beCount = 0;
    empty = false;
  }

  /** Appends a section-local block-entity cell index. */
  public void addBlockEntity(int cell) {
    if (beCount == bePositions.length) {
      int[] grown = new int[bePositions.length * 2];
      System.arraycopy(bePositions, 0, grown, 0, beCount);
      bePositions = grown;
    }
    bePositions[beCount++] = cell;
  }

  /** Copies the metadata of another snapshot; the arrays are assumed already filled. */
  public void copyMetaFrom(SectionSnapshot other) {
    sectionKey = other.sectionKey;
    version = other.version;
    resourceGeneration = other.resourceGeneration;
    empty = other.empty;
    beCount = other.beCount;
    if (bePositions.length < other.beCount) {
      bePositions = new int[other.beCount];
    }
    System.arraycopy(other.bePositions, 0, bePositions, 0, other.beCount);
  }

  /** Fills the whole grid, border included, with one state id. Used by tests and empty sections. */
  public void fill(int stateId) {
    java.util.Arrays.fill(state, stateId);
    java.util.Arrays.fill(light, (byte) 0);
  }
}
