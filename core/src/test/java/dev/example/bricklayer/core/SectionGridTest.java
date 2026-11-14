/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.example.bricklayer.core.grid.Dir;
import dev.example.bricklayer.core.grid.SectionGrid;
import org.junit.jupiter.api.Test;

class SectionGridTest {
  @Test
  void indexFormulaMatchesTheSpecification() {
    assertEquals(0, SectionGrid.idx(-1, -1, -1));
    assertEquals(5831, SectionGrid.idx(16, 16, 16));
    assertEquals(((0 + 1) * 18 + (0 + 1)) * 18 + (0 + 1), SectionGrid.idx(0, 0, 0));
  }

  @Test
  void interiorOriginIsTheFirstInteriorCell() {
    assertEquals(SectionGrid.idx(0, 0, 0), SectionGrid.INTERIOR_ORIGIN);
    assertEquals(343, SectionGrid.INTERIOR_ORIGIN);
  }

  @Test
  void directionOffsetsMatchTheGridStrides() {
    assertEquals(-324, Dir.DOWN.offset());
    assertEquals(324, Dir.UP.offset());
    assertEquals(-18, Dir.NORTH.offset());
    assertEquals(18, Dir.SOUTH.offset());
    assertEquals(-1, Dir.WEST.offset());
    assertEquals(1, Dir.EAST.offset());
  }

  @Test
  void directionsAreInVanillaOrdinalOrder() {
    assertEquals(0, Dir.DOWN.id());
    assertEquals(1, Dir.UP.id());
    assertEquals(2, Dir.NORTH.id());
    assertEquals(3, Dir.SOUTH.id());
    assertEquals(4, Dir.WEST.id());
    assertEquals(5, Dir.EAST.id());
    assertEquals(Dir.DOWN, Dir.UP.opposite());
    assertEquals(Dir.NORTH, Dir.SOUTH.opposite());
    assertEquals(Dir.WEST, Dir.EAST.opposite());
  }

  @Test
  void cellAndGridIndicesRoundTrip() {
    for (int y = 0; y < 16; y++) {
      for (int z = 0; z < 16; z++) {
        for (int x = 0; x < 16; x++) {
          int cell = SectionGrid.cellIndex(x, y, z);
          int grid = SectionGrid.gridOf(cell);
          assertEquals(SectionGrid.idx(x, y, z), grid);
          assertEquals(cell, SectionGrid.cellOfGrid(grid));
          assertEquals(x, SectionGrid.cellX(cell));
          assertEquals(y, SectionGrid.cellY(cell));
          assertEquals(z, SectionGrid.cellZ(cell));
        }
      }
    }
  }

  @Test
  void neighbourLookupsStayInsideTheBorder() {
    for (int x = 0; x < 16; x++) {
      for (int y = 0; y < 16; y++) {
        for (int z = 0; z < 16; z++) {
          int grid = SectionGrid.idx(x, y, z);
          for (Dir dir : Dir.VALUES) {
            int neighbour = grid + dir.offset();
            assertTrue(neighbour >= 0 && neighbour < SectionGrid.VOLUME);
            // AO reads the face neighbour plus its two tangential side steps and the diagonal,
            // never a second step along the normal, so one border block is enough.
            int u = dev.example.bricklayer.core.light.AoTables.U_UNIT[dir.id()];
            int v = dev.example.bricklayer.core.light.AoTables.V_UNIT[dir.id()];
            for (int su = -1; su <= 1; su++) {
              for (int sv = -1; sv <= 1; sv++) {
                int sample = neighbour + su * u + sv * v;
                assertTrue(
                    sample >= 0 && sample < SectionGrid.VOLUME,
                    "AO sample out of grid at " + x + "," + y + "," + z + " for " + dir);
              }
            }
          }
        }
      }
    }
  }
}
