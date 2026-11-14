/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.grid;

/**
 * Geometry of one section plus its one-block border.
 *
 * <pre>
 * idx(x, y, z) = ((y + 1) * 18 + (z + 1)) * 18 + (x + 1)   with x, y, z in -1..16
 * </pre>
 *
 * <p>The interior cells (0..15 on every axis) are addressed by a dense 0..4095 "cell" index in y,
 * z, x order, which is also the order the mesher scans in. The interior starts at grid index 343
 * and advances by 1 for x, 18 for z and 324 for y, so the scan needs no per-cell index math beyond
 * the grid compare.
 */
public final class SectionGrid {
  /** Edge length of the bordered grid. */
  public static final int SIZE = 18;

  /** Edge length of the section itself. */
  public static final int INTERIOR = 16;

  /** Total cells in the bordered grid: 5832. */
  public static final int VOLUME = SIZE * SIZE * SIZE;

  /** Cells in the section proper: 4096. */
  public static final int CELLS = INTERIOR * INTERIOR * INTERIOR;

  /** Grid index of cell (0, 0, 0). */
  public static final int INTERIOR_ORIGIN = idx(0, 0, 0);

  private static final int Y_STRIDE = SIZE * SIZE;
  private static final int Z_STRIDE = SIZE;

  private SectionGrid() {}

  /** Grid index of a bordered-grid coordinate in -1..16. */
  public static int idx(int x, int y, int z) {
    return ((y + 1) * SIZE + (z + 1)) * SIZE + (x + 1);
  }

  /** Grid-index delta for an arbitrary offset. */
  public static int step(int dx, int dy, int dz) {
    return dy * Y_STRIDE + dz * Z_STRIDE + dx;
  }

  /** Dense interior cell index (0..4095) in y, z, x order. */
  public static int cellIndex(int x, int y, int z) {
    return (y * INTERIOR + z) * INTERIOR + x;
  }

  public static int cellX(int cell) {
    return cell & 15;
  }

  public static int cellY(int cell) {
    return (cell >> 8) & 15;
  }

  public static int cellZ(int cell) {
    return (cell >> 4) & 15;
  }

  /** Grid index of a dense interior cell index. */
  public static int gridOf(int cell) {
    return INTERIOR_ORIGIN + cellY(cell) * Y_STRIDE + cellZ(cell) * Z_STRIDE + cellX(cell);
  }

  /** Grid index of section-local coordinates in 0..15. */
  public static int gridOfLocal(int x, int y, int z) {
    return INTERIOR_ORIGIN + y * Y_STRIDE + z * Z_STRIDE + x;
  }

  /** Dense interior cell index of a grid index; only valid for interior cells. */
  public static int cellOfGrid(int gridIndex) {
    int rel = gridIndex - INTERIOR_ORIGIN;
    return ((rel / Y_STRIDE) << 8) | (((rel % Y_STRIDE) / Z_STRIDE) << 4) | (rel % Z_STRIDE);
  }
}
