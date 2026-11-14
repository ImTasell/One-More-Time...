/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.mesh;

import dev.example.bricklayer.core.grid.SectionGrid;
import dev.example.bricklayer.core.grid.SectionSnapshot;
import dev.example.bricklayer.core.light.AoTables;
import dev.example.bricklayer.core.light.LightMath;
import dev.example.bricklayer.core.meta.CellFlags;
import dev.example.bricklayer.core.meta.FaceVisibilityTable;
import dev.example.bricklayer.core.meta.MetaTable;
import dev.example.bricklayer.core.quad.QuadSink;
import dev.example.bricklayer.core.quad.QuadStore;
import dev.example.bricklayer.core.quad.VariantSelector;
import java.util.Arrays;

/**
 * The whole fast path as one pure function: snapshot plus tables in, vertex streams plus visibility
 * out. No Minecraft types, no allocation, no synchronisation.
 *
 * <p>Pipeline order, matching the specification: interior scan, cull, quad fetch, AO and light,
 * emission, deferred collection, visibility flood fill, result.
 */
public final class SectionMesher {
  /** Tunables read once per task from the client config. */
  public static final class Options {
    public boolean smoothLight = true;

    /** PARITY runs the full AO port; false is FAST mode, which uses cubic weights everywhere. */
    public boolean parity = true;

    /** Diffuse lighting off disables the per-face directional shade, as in vanilla. */
    public boolean diffuseLight = true;

    /** Section origin in section coordinates, used for the variant seed. */
    public int sectionX;

    public int sectionY;
    public int sectionZ;
  }

  private SectionMesher() {}

  /**
   * Meshes one section.
   *
   * @param snapshot the captured section and border
   * @param meta per-state facts
   * @param quads the static quad store
   * @param visibility the shared face visibility cache
   * @param scratch this thread's working memory
   * @param sinks one sink per render layer
   * @param options per-task options
   * @param tint biome tint lookup
   * @param oracle fills misses in the visibility cache
   */
  public static MeshResult mesh(
      SectionSnapshot snapshot,
      MetaTable meta,
      QuadStore quads,
      FaceVisibilityTable visibility,
      Scratch scratch,
      QuadSink[] sinks,
      Options options,
      TintResolver tint,
      FaceVisibilityTable.Oracle oracle) {

    long startNanos = System.nanoTime();
    scratch.reset();
    for (QuadSink sink : sinks) {
      sink.reset();
    }

    int[] bePositions = Arrays.copyOf(snapshot.bePositions, snapshot.beCount);

    if (snapshot.empty) {
      return MeshResult.of(
          snapshot.sectionKey,
          snapshot.version,
          snapshot.resourceGeneration,
          new int[3],
          VisFlood.ALL_VISIBLE,
          bePositions,
          new int[0],
          0,
          System.nanoTime() - startNanos);
    }

    int[] states = snapshot.state;
    byte[] light = snapshot.light;
    int[] deferred = scratch.deferred;
    int deferredCount = 0;
    int quadCount = 0;
    int baseWorldX = options.sectionX << 4;
    int baseWorldY = options.sectionY << 4;
    int baseWorldZ = options.sectionZ << 4;

    for (int y = 0; y < 16; y++) {
      int rowBase = SectionGrid.INTERIOR_ORIGIN + y * 324;
      for (int z = 0; z < 16; z++) {
        int columnBase = rowBase + z * 18;
        for (int x = 0; x < 16; x++) {
          int grid = columnBase + x;
          int state = states[grid];
          int flags = meta.flags(state);
          if ((flags & CellFlags.AIR) != 0) {
            continue;
          }
          int model = meta.model(state);
          if ((flags & (CellFlags.FORCE_VANILLA | CellFlags.FLUID)) != 0
              || (flags & CellFlags.STATIC_MODEL) == 0
              || model < 0) {
            deferred[deferredCount++] = SectionGrid.cellIndex(x, y, z);
            continue;
          }

          int mask = FaceCuller.visibleMask(state, grid, states, visibility, oracle);
          if (mask == 0) {
            continue;
          }

          long seed = VariantSelector.positionSeed(baseWorldX + x, baseWorldY + y, baseWorldZ + z);
          int variant = quads.variantModel(model, seed);
          boolean emissive = (flags & CellFlags.EMISSIVE) != 0;

          for (int dir = 0; dir < 6; dir++) {
            if ((mask & (1 << dir)) == 0) {
              continue;
            }
            quadCount +=
                emitRange(
                    quads.rangeStart(variant, dir),
                    quads.rangeEnd(variant, dir),
                    grid,
                    state,
                    flags,
                    states,
                    light,
                    meta,
                    quads,
                    scratch,
                    sinks,
                    options,
                    tint,
                    emissive,
                    x,
                    z);
          }
          quadCount +=
              emitRange(
                  quads.rangeStart(variant, QuadStore.NO_CULL),
                  quads.rangeEnd(variant, QuadStore.NO_CULL),
                  grid,
                  state,
                  flags,
                  states,
                  light,
                  meta,
                  quads,
                  scratch,
                  sinks,
                  options,
                  tint,
                  emissive,
                  x,
                  z);
        }
      }
    }

    int[] layerCounts = new int[3];
    for (int i = 0; i < sinks.length && i < 3; i++) {
      layerCounts[i] = sinks[i].vertexCount();
    }

    long visibilityPairs = VisFlood.compute(states, meta, scratch);

    return MeshResult.of(
        snapshot.sectionKey,
        snapshot.version,
        snapshot.resourceGeneration,
        layerCounts,
        visibilityPairs,
        bePositions,
        Arrays.copyOf(deferred, deferredCount),
        quadCount,
        System.nanoTime() - startNanos);
  }

  private static int emitRange(
      int from,
      int to,
      int grid,
      int stateId,
      int stateFlags,
      int[] states,
      byte[] light,
      MetaTable meta,
      QuadStore quads,
      Scratch scratch,
      QuadSink[] sinks,
      Options options,
      TintResolver tint,
      boolean emissive,
      int localX,
      int localZ) {
    if (from >= to) {
      return 0;
    }
    int[] data = quads.data();
    byte[] quadFace = quads.face();
    byte[] quadTint = quads.tintIndex();
    byte[] quadFlags = quads.flags();
    byte[] quadLayer = quads.layer();
    int emitted = 0;

    for (int q = from; q < to; q++) {
      int offset = q * QuadStore.INTS_PER_QUAD;
      int face = quadFace[q];
      scratch.aoCalculator.calculate(
          states,
          light,
          meta,
          grid,
          face,
          data,
          offset,
          options.smoothLight,
          options.parity,
          scratch.ao,
          scratch.light);
      if (emissive) {
        Arrays.fill(scratch.light, LightMath.FULL_BRIGHT);
      }

      boolean shaded = (quadFlags[q] & QuadStore.QUAD_SHADE) != 0;
      float directional = shaded && options.diffuseLight ? AoTables.FACE_SHADE[face] : 1.0f;
      float[] shade = scratch.shade;
      for (int v = 0; v < 4; v++) {
        shade[v] = scratch.ao[v] * directional;
      }

      float red = 1.0f;
      float green = 1.0f;
      float blue = 1.0f;
      int tintIndex = quadTint[q];
      if (tintIndex >= 0 && (stateFlags & CellFlags.HAS_TINT) != 0) {
        boolean positional = (stateFlags & CellFlags.TINT_POSITIONAL) != 0;
        int rgb = scratch.tint(tint, stateId, tintIndex, localX, localZ, positional);
        if (rgb >= 0) {
          red = ((rgb >> 16) & 0xFF) / 255.0f;
          green = ((rgb >> 8) & 0xFF) / 255.0f;
          blue = (rgb & 0xFF) / 255.0f;
        }
      }

      sinks[quadLayer[q]].quad(data, offset, shade, scratch.light, red, green, blue, 1.0f);
      emitted++;
    }
    return emitted;
  }
}
