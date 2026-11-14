/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.example.bricklayer.core.grid.SectionSnapshot;
import dev.example.bricklayer.core.mesh.MeshResult;
import dev.example.bricklayer.core.mesh.Scratch;
import dev.example.bricklayer.core.mesh.SectionMesher;
import dev.example.bricklayer.core.meta.FaceVisibilityTable;
import dev.example.bricklayer.core.meta.MetaTable;
import dev.example.bricklayer.core.quad.QuadSink;
import dev.example.bricklayer.core.quad.QuadStore;
import org.junit.jupiter.api.Test;

class SectionMesherTest {
  private static final FaceVisibilityTable.Oracle VANILLA_LIKE_ORACLE =
      (state, neighbour, face) -> state != neighbour;

  private static MeshResult mesh(
      SectionSnapshot snapshot,
      MetaTable meta,
      QuadStore store,
      QuadSink[] sinks,
      Scratch scratch) {
    FaceVisibilityTable table = new FaceVisibilityTable(meta);
    SectionMesher.Options options = new SectionMesher.Options();
    options.sectionX = 0;
    options.sectionY = 0;
    options.sectionZ = 0;
    return SectionMesher.mesh(
        snapshot,
        meta,
        store,
        table,
        scratch,
        sinks,
        options,
        (s, t, x, z) -> -1,
        VANILLA_LIKE_ORACLE);
  }

  @Test
  void singleBlockEmitsSixQuads() {
    MetaTable meta = TestSections.meta();
    QuadStore store = TestSections.cubeStore();
    SectionSnapshot snapshot = TestSections.snapshot(TestSections.AIR);
    TestSections.setBlock(snapshot, 3, 4, 5, TestSections.STONE);

    TestSections.CapturingSink solid = new TestSections.CapturingSink();
    TestSections.CapturingSink cutout = new TestSections.CapturingSink();
    MeshResult result =
        mesh(
            snapshot,
            meta,
            store,
            new QuadSink[] {solid, new TestSections.CapturingSink(), cutout},
            new Scratch());

    assertEquals(6, result.quadCount);
    assertEquals(24, solid.vertices);
    assertEquals(0, result.deferredCells.length);
    assertEquals(0, cutout.vertices);
  }

  @Test
  void touchingBlocksCullTheSharedFace() {
    MetaTable meta = TestSections.meta();
    QuadStore store = TestSections.cubeStore();
    SectionSnapshot snapshot = TestSections.snapshot(TestSections.AIR);
    TestSections.setBlock(snapshot, 3, 4, 5, TestSections.STONE);
    TestSections.setBlock(snapshot, 4, 4, 5, TestSections.STONE);

    TestSections.CapturingSink solid = new TestSections.CapturingSink();
    MeshResult result =
        mesh(snapshot, meta, store, new QuadSink[] {solid, solid, solid}, new Scratch());

    assertEquals(10, result.quadCount);
  }

  @Test
  void fullyEnclosedBlockEmitsNothing() {
    MetaTable meta = TestSections.meta();
    QuadStore store = TestSections.cubeStore();
    SectionSnapshot snapshot = TestSections.snapshot(TestSections.STONE);

    TestSections.CapturingSink solid = new TestSections.CapturingSink();
    MeshResult result =
        mesh(snapshot, meta, store, new QuadSink[] {solid, solid, solid}, new Scratch());

    assertEquals(0, result.quadCount);
    assertEquals(0, solid.vertices);
  }

  @Test
  void nonOpaqueBlocksStillRenderAgainstEachOther() {
    // The oracle says two different non-opaque states show their shared face, like glass next to
    // stone in vanilla; the fast paths must not decide that on their own.
    MetaTable meta = TestSections.meta();
    QuadStore store = TestSections.cubeStore();
    SectionSnapshot snapshot = TestSections.snapshot(TestSections.AIR);
    TestSections.setBlock(snapshot, 0, 0, 0, TestSections.GLASS);

    TestSections.CapturingSink cutout = new TestSections.CapturingSink();
    MeshResult result =
        mesh(
            snapshot,
            meta,
            store,
            new QuadSink[] {
              new TestSections.CapturingSink(), new TestSections.CapturingSink(), cutout
            },
            new Scratch());

    assertEquals(6, result.quadCount);
    assertEquals(24, cutout.vertices);
  }

  @Test
  void airOnlySectionShortCircuits() {
    MetaTable meta = TestSections.meta();
    SectionSnapshot snapshot = TestSections.snapshot(TestSections.AIR);
    snapshot.empty = true;

    TestSections.CapturingSink solid = new TestSections.CapturingSink();
    MeshResult result =
        mesh(
            snapshot,
            meta,
            TestSections.cubeStore(),
            new QuadSink[] {solid, solid, solid},
            new Scratch());

    assertEquals(0, result.quadCount);
    assertEquals(0xFFFFFFFFFL, result.visibilityPairs);
  }

  @Test
  void emissiveLightingIsFullBright() {
    MetaTable meta =
        new MetaTable(new int[] {0x7FF, 0x7FF}, new float[] {1f, 1f}, new int[] {-1, 0});
    // 0x7FF = AIR|OPAQUE|VIS|STATIC|AO|TINT|EMISSIVE|... - not meaningful; use explicit flags.
    int air =
        dev.example.bricklayer.core.meta.CellFlags.AIR
            | dev.example.bricklayer.core.meta.CellFlags.AO_CLEAR;
    int lamp =
        dev.example.bricklayer.core.meta.CellFlags.STATIC_MODEL
            | dev.example.bricklayer.core.meta.CellFlags.EMISSIVE
            | dev.example.bricklayer.core.meta.CellFlags.LAYER_SOLID;
    MetaTable lampMeta =
        new MetaTable(new int[] {air, lamp}, new float[] {1f, 1f}, new int[] {-1, 0});

    SectionSnapshot snapshot = TestSections.snapshot(TestSections.AIR);
    TestSections.setBlock(snapshot, 1, 1, 1, 1);

    TestSections.CapturingSink solid = new TestSections.CapturingSink();
    MeshResult result =
        mesh(
            snapshot,
            lampMeta,
            TestSections.cubeStore(),
            new QuadSink[] {solid, solid, solid},
            new Scratch());

    assertTrue(result.quadCount > 0);
    assertEquals(0xF000F0, solid.lastLight);
  }
}
