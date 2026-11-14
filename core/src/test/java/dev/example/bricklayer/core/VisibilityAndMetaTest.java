/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.example.bricklayer.core.grid.SectionSnapshot;
import dev.example.bricklayer.core.mesh.Scratch;
import dev.example.bricklayer.core.mesh.VisFlood;
import dev.example.bricklayer.core.meta.FaceVisibilityTable;
import dev.example.bricklayer.core.meta.MetaTable;
import dev.example.bricklayer.core.quad.LegacyRandom;
import dev.example.bricklayer.core.quad.QuadStore;
import dev.example.bricklayer.core.quad.VariantSelector;
import dev.example.bricklayer.core.sched.DirtyVersions;
import dev.example.bricklayer.core.sched.PriorityFn;
import dev.example.bricklayer.core.sched.TaskQueue;
import dev.example.bricklayer.core.sched.UploadBudget;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class VisibilityAndMetaTest {
  private static boolean pair(long mask, int from, int to) {
    return (mask & (1L << (from * 6 + to))) != 0;
  }

  @Test
  void faceVisibilityFastPaths() {
    MetaTable meta = TestSections.meta();
    FaceVisibilityTable table = new FaceVisibilityTable(meta);
    AtomicInteger oracleCalls = new AtomicInteger();
    FaceVisibilityTable.Oracle oracle =
        (state, neighbour, face) -> {
          oracleCalls.incrementAndGet();
          return true;
        };

    assertTrue(table.visible(TestSections.STONE, TestSections.AIR, 0, oracle));
    assertEquals(0, oracleCalls.get());

    assertFalse(table.visible(TestSections.GLASS, TestSections.STONE, 0, oracle));
    assertEquals(0, oracleCalls.get());

    assertTrue(table.visible(TestSections.GLASS, TestSections.GLASS, 0, oracle));
    assertEquals(1, oracleCalls.get());

    // Same triple again: memoised, no second oracle call.
    assertTrue(table.visible(TestSections.GLASS, TestSections.GLASS, 0, oracle));
    assertEquals(1, oracleCalls.get());

    table.visible(TestSections.GLASS, TestSections.GLASS, 1, oracle);
    assertEquals(2, oracleCalls.get());
  }

  @Test
  void emptySectionSeesEveryFacePair() {
    MetaTable meta = TestSections.meta();
    SectionSnapshot snapshot = TestSections.snapshot(TestSections.AIR);
    assertEquals(VisFlood.ALL_VISIBLE, VisFlood.compute(snapshot.state, meta, new Scratch()));
  }

  @Test
  void solidSectionSeesNothing() {
    MetaTable meta = TestSections.meta();
    SectionSnapshot snapshot = TestSections.snapshot(TestSections.STONE);
    assertEquals(0L, VisFlood.compute(snapshot.state, meta, new Scratch()));
  }

  @Test
  void aSolidWallBlocksTheFacesBehindIt() {
    MetaTable meta = TestSections.meta();
    SectionSnapshot snapshot = TestSections.snapshot(TestSections.AIR);
    for (int y = 0; y < 16; y++) {
      for (int z = 0; z < 16; z++) {
        for (int x = 0; x < 8; x++) {
          TestSections.setBlock(snapshot, x, y, z, TestSections.STONE);
        }
      }
    }
    long mask = VisFlood.compute(snapshot.state, meta, new Scratch());
    // West (4) is sealed off by the wall; the other five faces still see each other.
    assertFalse(pair(mask, 4, 5));
    assertFalse(pair(mask, 5, 4));
    assertTrue(pair(mask, 0, 1));
    assertTrue(pair(mask, 2, 3));
    assertTrue(pair(mask, 5, 0));
  }

  @Test
  void quadStoreSortsQuadsIntoSixSevenSlots() {
    QuadStore.Builder builder = new QuadStore.Builder();
    int model = builder.beginModel();
    int[] data = new int[QuadStore.INTS_PER_QUAD];
    for (int face = 0; face < 6; face++) {
      builder.addQuad(model, face, face, 0, -1, true, data, 0);
    }
    builder.addQuad(model, 1, -1, 0, -1, true, data, 0);
    QuadStore store = builder.build();

    assertEquals(1, store.modelCount());
    assertEquals(7, store.quadCount());
    int total = 0;
    for (int slot = 0; slot < 7; slot++) {
      int start = store.rangeStart(model, slot);
      int end = store.rangeEnd(model, slot);
      assertEquals(1, end - start);
      if (slot < 6) {
        assertEquals(slot, store.face()[start]);
        assertEquals(slot, store.cullFace()[start]);
      } else {
        assertEquals(-1, store.cullFace()[start]);
      }
      total += end - start;
    }
    assertEquals(7, total);
  }

  @Test
  void weightedVariantsAreDeterministicAndSpreadOut() {
    QuadStore.Builder builder = new QuadStore.Builder();
    int base = builder.beginModel();
    for (int i = 0; i < 3; i++) {
      builder.beginModel();
      TestSections.addCube(builder, base + i, 0);
    }
    builder.variants(base, base, 3, 3, new int[] {1, 1, 1});
    QuadStore store = builder.build();

    boolean[] seen = new boolean[3];
    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        long seed = VariantSelector.positionSeed(x, 5, z);
        int first = store.variantModel(base, seed);
        int second = store.variantModel(base, seed);
        assertEquals(first, second);
        assertTrue(first >= base && first < base + 3);
        seen[first - base] = true;
      }
    }
    assertTrue(seen[0] && seen[1] && seen[2]);
  }

  @Test
  void legacyRandomStaysInBounds() {
    LegacyRandom random = new LegacyRandom(42L);
    for (int i = 0; i < 1000; i++) {
      int value = random.nextInt(7);
      assertTrue(value >= 0 && value < 7);
    }
  }

  @Test
  void priorityPrefersNearFrustumSections() {
    double near = PriorityFn.score(100.0, true, false, 0);
    double far = PriorityFn.score(10_000.0, true, false, 0);
    double outside = PriorityFn.score(10_000.0, false, false, 0);
    double behind = PriorityFn.score(10_000.0, true, true, 0);
    double aged = PriorityFn.score(10_000.0, true, false, 400);
    assertTrue(near < far);
    assertTrue(far < outside);
    assertTrue(far < behind);
    assertTrue(aged < far);
  }

  @Test
  void taskQueueEvictsTheFarthestTask() {
    TaskQueue queue = new TaskQueue(2);
    SectionSnapshot first = TestSections.snapshot(0);
    SectionSnapshot second = TestSections.snapshot(0);
    SectionSnapshot third = TestSections.snapshot(0);
    assertTrue(queue.offer(new TaskQueue.Task(1L, 1, first, 10.0)));
    assertTrue(queue.offer(new TaskQueue.Task(2L, 1, second, 20.0)));
    // A nearer task evicts the farthest one.
    assertTrue(queue.offer(new TaskQueue.Task(3L, 1, third, 5.0)));
    assertEquals(2, queue.size());
    assertEquals(1L, queue.evictedCount());
    assertEquals(3L, queue.poll().sectionKey);
    assertEquals(1L, queue.poll().sectionKey);
  }

  @Test
  void dirtyVersionsDetectStaleResults() {
    DirtyVersions versions = new DirtyVersions();
    int version = versions.bump(99L);
    assertTrue(versions.matches(99L, version));
    versions.bump(99L);
    assertFalse(versions.matches(99L, version));
    assertEquals(0, versions.current(1234L));
  }

  @Test
  void uploadBudgetHalvesAndRecovers() {
    UploadBudget budget = new UploadBudget(2.0);
    budget.recordFrame(40.0);
    assertEquals(1.0, budget.budgetMs(), 1.0E-6);
    budget.recordFrame(40.0);
    assertEquals(0.5, budget.budgetMs(), 1.0E-6);
    for (int i = 0; i < 100; i++) {
      budget.recordFrame(10.0);
    }
    assertTrue(budget.budgetMs() <= 2.0);
    assertFalse(budget.mayUpload(System.nanoTime(), UploadBudget.MAX_UPLOADS_PER_FRAME));
  }
}
