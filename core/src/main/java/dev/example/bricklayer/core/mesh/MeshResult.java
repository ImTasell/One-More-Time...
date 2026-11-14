/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.mesh;

/** Everything one meshed section produces, plus the counters the overlay and JFR event report. */
public final class MeshResult {
  public final long sectionKey;
  public final int version;
  public final int resourceGeneration;

  /** Vertices written per layer: 0 solid, 1 cutout-mipped, 2 cutout. */
  public final int[] layerVertexCounts;

  /** Visibility face-pair mask in vanilla {@code VisibilitySet} layout. */
  public final long visibilityPairs;

  /** Section-local block-entity cell indices. */
  public final int[] bePositions;

  /** Cells the fast path deferred to vanilla. */
  public final int[] deferredCells;

  public final int quadCount;
  public final long nanos;

  /** True when this result carries no geometry and the section must fall back to vanilla. */
  public final boolean vanillaOnly;

  private MeshResult(
      long sectionKey,
      int version,
      int resourceGeneration,
      int[] layerVertexCounts,
      long visibilityPairs,
      int[] bePositions,
      int[] deferredCells,
      int quadCount,
      long nanos,
      boolean vanillaOnly) {
    this.sectionKey = sectionKey;
    this.version = version;
    this.resourceGeneration = resourceGeneration;
    this.layerVertexCounts = layerVertexCounts;
    this.visibilityPairs = visibilityPairs;
    this.bePositions = bePositions;
    this.deferredCells = deferredCells;
    this.quadCount = quadCount;
    this.nanos = nanos;
    this.vanillaOnly = vanillaOnly;
  }

  public static MeshResult of(
      long sectionKey,
      int version,
      int resourceGeneration,
      int[] layerVertexCounts,
      long visibilityPairs,
      int[] bePositions,
      int[] deferredCells,
      int quadCount,
      long nanos) {
    return new MeshResult(
        sectionKey,
        version,
        resourceGeneration,
        layerVertexCounts,
        visibilityPairs,
        bePositions,
        deferredCells,
        quadCount,
        nanos,
        false);
  }

  public static MeshResult vanillaOnly(long sectionKey, int version, int resourceGeneration) {
    return new MeshResult(
        sectionKey, version, resourceGeneration, new int[3], 0L, EMPTY, EMPTY, 0, 0L, true);
  }

  private static final int[] EMPTY = new int[0];

  /** Total vertices across all layers. */
  public int totalVertices() {
    return layerVertexCounts[0] + layerVertexCounts[1] + layerVertexCounts[2];
  }

  /** Fraction of cells that had to be deferred, for the overlay. */
  public float deferredRatio() {
    return deferredCells.length / 4096.0f;
  }
}
