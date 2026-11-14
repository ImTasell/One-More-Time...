/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.mesh;

import dev.example.bricklayer.core.grid.Dir;
import dev.example.bricklayer.core.grid.SectionGrid;
import dev.example.bricklayer.core.meta.CellFlags;
import dev.example.bricklayer.core.meta.MetaTable;
import java.util.Arrays;

/**
 * Replacement for vanilla's {@code VisGraph}: which pairs of section faces can see each other
 * through non-opaque cells.
 *
 * <p>A {@code long[64]} bitset marks the opaque cells. Every unvisited non-opaque interior cell
 * starts an iterative flood fill; the component records a 6-bit mask of the section faces it
 * touches, and when the component is finished every ordered pair of those faces is marked mutually
 * visible.
 *
 * <p>Result layout matches vanilla's {@code VisibilitySet}: bit {@code from * 6 + to}, with
 * directions in vanilla ordinal order.
 */
public final class VisFlood {
  /** All 36 ordered face pairs visible, which is what vanilla reports for an empty section. */
  public static final long ALL_VISIBLE = 0xFFFFFFFFFL;

  private VisFlood() {}

  /**
   * @param states snapshot state ids
   * @param meta meta table the snapshot was built against
   * @param scratch per-thread working memory
   * @return the 36-bit face-pair mask
   */
  public static long compute(int[] states, MetaTable meta, Scratch scratch) {
    long[] opaque = scratch.visBits;
    byte[] visited = scratch.visitFlags;
    int[] stack = scratch.floodStack;
    Arrays.fill(opaque, 0L);
    Arrays.fill(visited, (byte) 0);

    for (int cell = 0; cell < SectionGrid.CELLS; cell++) {
      int state = states[SectionGrid.INTERIOR_ORIGIN + gridDelta(cell)];
      if ((meta.flags(state) & CellFlags.VIS_OPAQUE) != 0) {
        opaque[cell >>> 6] |= 1L << cell;
      }
    }

    long pairs = 0L;
    for (int start = 0; start < SectionGrid.CELLS; start++) {
      if ((opaque[start >>> 6] & (1L << start)) != 0 || visited[start] != 0) {
        continue;
      }
      int top = 0;
      stack[top++] = start;
      visited[start] = 1;
      int faceMask = 0;
      while (top > 0) {
        int cell = stack[--top];
        int x = cell & 15;
        int z = (cell >> 4) & 15;
        int y = (cell >> 8) & 15;
        if (x == 0) {
          faceMask |= 1 << 4; // west
        }
        if (x == 15) {
          faceMask |= 1 << 5; // east
        }
        if (y == 0) {
          faceMask |= 1 << 0; // down
        }
        if (y == 15) {
          faceMask |= 1 << 1; // up
        }
        if (z == 0) {
          faceMask |= 1 << 2; // north
        }
        if (z == 15) {
          faceMask |= 1 << 3; // south
        }
        for (Dir dir : Dir.VALUES) {
          int nx = x + dir.dx();
          int ny = y + dir.dy();
          int nz = z + dir.dz();
          if (nx < 0 || nx > 15 || ny < 0 || ny > 15 || nz < 0 || nz > 15) {
            continue;
          }
          int neighbour = SectionGrid.cellIndex(nx, ny, nz);
          if (visited[neighbour] != 0) {
            continue;
          }
          if ((opaque[neighbour >>> 6] & (1L << neighbour)) != 0) {
            continue;
          }
          visited[neighbour] = 1;
          stack[top++] = neighbour;
        }
      }
      if (faceMask == 0) {
        continue;
      }
      for (int from = 0; from < 6; from++) {
        if ((faceMask & (1 << from)) == 0) {
          continue;
        }
        for (int to = 0; to < 6; to++) {
          if ((faceMask & (1 << to)) != 0) {
            pairs |= 1L << (from * 6 + to);
          }
        }
      }
    }
    return pairs;
  }

  /** Grid displacement of a dense interior cell index. */
  public static int gridDelta(int cell) {
    int x = cell & 15;
    int z = (cell >> 4) & 15;
    int y = (cell >> 8) & 15;
    return y * 324 + z * 18 + x;
  }
}
