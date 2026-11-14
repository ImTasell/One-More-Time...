/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.mesh;

import dev.example.bricklayer.core.grid.Dir;
import dev.example.bricklayer.core.meta.FaceVisibilityTable;

/** Builds the 6-bit visible-face mask of a cell. */
public final class FaceCuller {
  private FaceCuller() {}

  /**
   * @param stateId the cell's state
   * @param gridIndex grid index of the cell
   * @param states snapshot state ids
   * @param table the shared visibility cache
   * @param oracle resolves triples the cache has not seen
   * @return bit {@code d} set when face {@code d} must be rendered
   */
  public static int visibleMask(
      int stateId,
      int gridIndex,
      int[] states,
      FaceVisibilityTable table,
      FaceVisibilityTable.Oracle oracle) {
    int mask = 0;
    for (int i = 0; i < 6; i++) {
      Dir dir = Dir.VALUES[i];
      if (table.visible(stateId, states[gridIndex + dir.offset()], i, oracle)) {
        mask |= 1 << i;
      }
    }
    return mask;
  }
}
