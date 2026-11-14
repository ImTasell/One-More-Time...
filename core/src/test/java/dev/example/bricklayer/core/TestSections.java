/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core;

import dev.example.bricklayer.core.grid.SectionGrid;
import dev.example.bricklayer.core.grid.SectionSnapshot;
import dev.example.bricklayer.core.meta.CellFlags;
import dev.example.bricklayer.core.meta.MetaTable;
import dev.example.bricklayer.core.quad.QuadSink;
import dev.example.bricklayer.core.quad.QuadStore;

/** Synthetic block states, a cube model and a capturing sink; no Minecraft required. */
final class TestSections {
  static final int AIR = 0;
  static final int STONE = 1;
  static final int GLASS = 2;

  private TestSections() {}

  static MetaTable meta() {
    int air = CellFlags.AIR | CellFlags.AO_CLEAR;
    int stone =
        CellFlags.OPAQUE_FULL
            | CellFlags.VIS_OPAQUE
            | CellFlags.STATIC_MODEL
            | CellFlags.USES_AO
            | CellFlags.LAYER_SOLID;
    int glass = CellFlags.STATIC_MODEL | CellFlags.USES_AO | CellFlags.LAYER_CUTOUT;
    return new MetaTable(
        new int[] {air, stone, glass}, new float[] {1.0f, 1.0f, 1.0f}, new int[] {-1, 0, 1});
  }

  static MetaTable emptyMeta() {
    int air = CellFlags.AIR | CellFlags.AO_CLEAR;
    return new MetaTable(new int[] {air}, new float[] {1.0f}, new int[] {-1});
  }

  static SectionSnapshot snapshot(int fill) {
    SectionSnapshot snapshot = new SectionSnapshot();
    snapshot.fill(fill);
    snapshot.sectionKey = 1234L;
    snapshot.version = 7;
    snapshot.resourceGeneration = 3;
    return snapshot;
  }

  static void setBlock(SectionSnapshot snapshot, int x, int y, int z, int state) {
    snapshot.state[SectionGrid.idx(x, y, z)] = state;
  }

  /** A unit cube of six cull-faced quads, one per direction, scaled to 0..16. */
  static void addCube(QuadStore.Builder builder, int model, int layer) {
    int[][][] faces = {
      {{0, 0, 0}, {0, 0, 1}, {1, 0, 1}, {1, 0, 1}}, // down
      {{0, 1, 1}, {0, 1, 0}, {1, 1, 0}, {1, 1, 1}}, // up
      {{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}}, // north
      {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}}, // south
      {{0, 0, 1}, {0, 0, 0}, {0, 1, 0}, {0, 1, 1}}, // west
      {{1, 0, 0}, {1, 0, 1}, {1, 1, 1}, {1, 1, 0}} // east
    };
    for (int face = 0; face < 6; face++) {
      int[] data = new int[QuadStore.INTS_PER_QUAD];
      for (int vertex = 0; vertex < 4; vertex++) {
        int base = vertex * QuadStore.INTS_PER_VERTEX;
        int[] position = faces[face][vertex];
        data[base] = Float.floatToRawIntBits(position[0] * 16.0f);
        data[base + 1] = Float.floatToRawIntBits(position[1] * 16.0f);
        data[base + 2] = Float.floatToRawIntBits(position[2] * 16.0f);
        data[base + 3] = 0xFFFFFFFF;
        data[base + 4] = Float.floatToRawIntBits(0.0f);
        data[base + 5] = Float.floatToRawIntBits(0.0f);
        data[base + 6] = 0;
        data[base + 7] = 0;
      }
      builder.addQuad(model, face, face, layer, -1, true, data, 0);
    }
  }

  static QuadStore cubeStore() {
    QuadStore.Builder builder = new QuadStore.Builder();
    int stone = builder.beginModel();
    addCube(builder, stone, 0);
    int glass = builder.beginModel();
    addCube(builder, glass, 2);
    return builder.build();
  }

  /** Counts what the mesher emits; the real sinks write GPU vertices instead. */
  static final class CapturingSink implements QuadSink {
    int quads;
    int vertices;
    float lastShade = 1.0f;
    int lastLight;

    @Override
    public void quad(
        int[] data, int offset, float[] shade, int[] light, float r, float g, float b, float a) {
      quads++;
      vertices += 4;
      lastShade = shade[0];
      lastLight = light[0];
    }

    @Override
    public int vertexCount() {
      return vertices;
    }

    @Override
    public void reset() {
      quads = 0;
      vertices = 0;
    }
  }
}
