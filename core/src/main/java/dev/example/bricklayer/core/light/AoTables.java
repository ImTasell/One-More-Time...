/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.light;

import dev.example.bricklayer.core.grid.Dir;
import dev.example.bricklayer.core.grid.SectionGrid;

/**
 * Precomputed tables for the AO pass: for each face, its in-plane axes and the grid deltas one step
 * along them.
 *
 * <p>Vanilla derives AO corner samples from the quad's own vertex positions, so this port does too:
 * a vertex's corner sign is read from its position (0 or 16 on each in-plane axis) instead of being
 * assumed from a fixed vertex order. That keeps the port correct for quads whose vertex order does
 * not follow the cube convention, which happens with modded models.
 */
public final class AoTables {
  /** Grid delta of one step along the face normal. */
  public static final int[] NORMAL = new int[6];

  /** Grid delta of one step along the face's first in-plane axis. */
  public static final int[] U_UNIT = new int[6];

  /** Grid delta of one step along the face's second in-plane axis. */
  public static final int[] V_UNIT = new int[6];

  /** Component (0 = x, 1 = y, 2 = z) of the first in-plane axis. */
  public static final int[] U_AXIS = new int[6];

  /** Component (0 = x, 1 = y, 2 = z) of the second in-plane axis. */
  public static final int[] V_AXIS = new int[6];

  /** Vanilla's per-face directional shade: down 0.5, up 1.0, z faces 0.8, x faces 0.6. */
  public static final float[] FACE_SHADE = {0.5f, 1.0f, 0.8f, 0.8f, 0.6f, 0.6f};

  /** AO darkness contributed by each occluding neighbour, vanilla's 20% step. */
  public static final float AO_STEP = 0.2f;

  /** Darkest AO value vanilla will produce for a corner. */
  public static final float AO_MIN = 0.4f;

  static {
    for (Dir dir : Dir.VALUES) {
      int i = dir.id();
      NORMAL[i] = dir.offset();
      switch (dir) {
        case DOWN, UP -> {
          U_AXIS[i] = 0; // x
          V_AXIS[i] = 2; // z
          U_UNIT[i] = SectionGrid.step(1, 0, 0);
          V_UNIT[i] = SectionGrid.step(0, 0, 1);
        }
        case NORTH, SOUTH -> {
          U_AXIS[i] = 0; // x
          V_AXIS[i] = 1; // y
          U_UNIT[i] = SectionGrid.step(1, 0, 0);
          V_UNIT[i] = SectionGrid.step(0, 1, 0);
        }
        case WEST, EAST -> {
          U_AXIS[i] = 2; // z
          V_AXIS[i] = 1; // y
          U_UNIT[i] = SectionGrid.step(0, 0, 1);
          V_UNIT[i] = SectionGrid.step(0, 1, 0);
        }
      }
    }
  }

  private AoTables() {}
}
