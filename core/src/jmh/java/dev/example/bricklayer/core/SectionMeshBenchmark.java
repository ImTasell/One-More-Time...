/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core;

import dev.example.bricklayer.core.grid.SectionGrid;
import dev.example.bricklayer.core.grid.SectionSnapshot;
import dev.example.bricklayer.core.mesh.Scratch;
import dev.example.bricklayer.core.mesh.SectionMesher;
import dev.example.bricklayer.core.meta.CellFlags;
import dev.example.bricklayer.core.meta.FaceVisibilityTable;
import dev.example.bricklayer.core.meta.MetaTable;
import dev.example.bricklayer.core.quad.QuadSink;
import dev.example.bricklayer.core.quad.QuadStore;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Synthetic sections: flat terrain, a checkerboard, a hollow cube and noise caves. Numbers on
 * shared CI runners are only meaningful relative to each other between runs.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class SectionMeshBenchmark {
  private static final int AIR = 0;
  private static final int STONE = 1;

  private MetaTable meta;
  private QuadStore store;
  private FaceVisibilityTable visibility;
  private Scratch scratch;
  private QuadSink[] sinks;
  private SectionMesher.Options options;
  private SectionSnapshot flat;
  private SectionSnapshot checkerboard;
  private SectionSnapshot hollow;
  private SectionSnapshot caves;

  @Setup
  public void setup() {
    int air = CellFlags.AIR | CellFlags.AO_CLEAR;
    int stone =
        CellFlags.OPAQUE_FULL
            | CellFlags.VIS_OPAQUE
            | CellFlags.STATIC_MODEL
            | CellFlags.USES_AO
            | CellFlags.LAYER_SOLID;
    meta = new MetaTable(new int[] {air, stone}, new float[] {1.0f, 1.0f}, new int[] {-1, 0});

    QuadStore.Builder builder = new QuadStore.Builder();
    int model = builder.beginModel();
    addCube(builder, model);
    store = builder.build();

    visibility = new FaceVisibilityTable(meta);
    scratch = new Scratch();
    sinks = new QuadSink[] {new CountingSink(), new CountingSink(), new CountingSink()};
    options = new SectionMesher.Options();

    flat = new SectionSnapshot();
    flat.fill(AIR);
    for (int y = 0; y < 8; y++) {
      for (int z = 0; z < 16; z++) {
        for (int x = 0; x < 16; x++) {
          flat.state[SectionGrid.idx(x, y, z)] = STONE;
        }
      }
    }

    checkerboard = new SectionSnapshot();
    for (int y = 0; y < 16; y++) {
      for (int z = 0; z < 16; z++) {
        for (int x = 0; x < 16; x++) {
          checkerboard.state[SectionGrid.idx(x, y, z)] = ((x + y + z) & 1) == 0 ? STONE : AIR;
        }
      }
    }

    hollow = new SectionSnapshot();
    for (int y = 0; y < 16; y++) {
      for (int z = 0; z < 16; z++) {
        for (int x = 0; x < 16; x++) {
          boolean shell = x == 0 || x == 15 || y == 0 || y == 15 || z == 0 || z == 15;
          hollow.state[SectionGrid.idx(x, y, z)] = shell ? STONE : AIR;
        }
      }
    }

    Random random = new Random(1234L);
    caves = new SectionSnapshot();
    for (int y = 0; y < 16; y++) {
      for (int z = 0; z < 16; z++) {
        for (int x = 0; x < 16; x++) {
          caves.state[SectionGrid.idx(x, y, z)] = random.nextInt(100) < 85 ? STONE : AIR;
        }
      }
    }
  }

  private static void addCube(QuadStore.Builder builder, int model) {
    for (int face = 0; face < 6; face++) {
      int[] data = new int[QuadStore.INTS_PER_QUAD];
      for (int vertex = 0; vertex < 4; vertex++) {
        int base = vertex * QuadStore.INTS_PER_VERTEX;
        data[base] = Float.floatToRawIntBits((vertex & 1) * 16.0f);
        data[base + 1] = Float.floatToRawIntBits((vertex & 2) * 8.0f);
        data[base + 2] = Float.floatToRawIntBits((vertex >> 1) * 16.0f);
        data[base + 3] = 0xFFFFFFFF;
      }
      builder.addQuad(model, face, face, 0, -1, true, data, 0);
    }
  }

  private void run(SectionSnapshot snapshot) {
    SectionMesher.mesh(
        snapshot,
        meta,
        store,
        visibility,
        scratch,
        sinks,
        options,
        (state, tint, x, z) -> -1,
        (state, neighbour, face) -> state != neighbour);
  }

  @Benchmark
  public void flat() {
    run(flat);
  }

  @Benchmark
  public void checkerboard() {
    run(checkerboard);
  }

  @Benchmark
  public void hollowCube() {
    run(hollow);
  }

  @Benchmark
  public void noiseCaves() {
    run(caves);
  }

  /** Minimal sink so the benchmark measures the pipeline and not the GPU path. */
  static final class CountingSink implements QuadSink {
    private int vertices;

    @Override
    public void quad(
        int[] data, int offset, float[] shade, int[] light, float r, float g, float b, float a) {
      vertices += 4;
    }

    @Override
    public int vertexCount() {
      return vertices;
    }

    @Override
    public void reset() {
      vertices = 0;
    }
  }
}
