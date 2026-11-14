/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.sched;

import dev.example.bricklayer.Bricklayer;
import dev.example.bricklayer.compat.CompatGate;
import dev.example.bricklayer.config.BricklayerConfig;
import dev.example.bricklayer.core.grid.SectionSnapshot;
import dev.example.bricklayer.core.grid.SnapshotPool;
import dev.example.bricklayer.core.sched.DirtyVersions;
import dev.example.bricklayer.core.sched.PriorityFn;
import dev.example.bricklayer.core.sched.TaskQueue;
import dev.example.bricklayer.debug.CompileEvent;
import dev.example.bricklayer.pipeline.MeshPipeline;
import dev.example.bricklayer.snapshot.DecodeCache;
import dev.example.bricklayer.snapshot.Snapshotter;
import dev.example.bricklayer.upload.CompiledMesh;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;

/**
 * The scheduler that replaces vanilla's section dispatch queue.
 *
 * <p>Snapshots are taken on the main thread inside the dirty hook, tasks go into a bounded priority
 * queue, and a small pool of daemon workers drains it. Finished meshes land in the upload
 * governor's queue and are uploaded on the render thread.
 *
 * <p>Player-initiated edits keep vanilla's synchronous semantics: {@link #submitImmediate} runs the
 * same pipeline inline so a broken block never waits for a worker.
 */
public final class BricklayerScheduler {
  private static final int QUEUE_CAPACITY = 4096;
  private static final AtomicInteger THREAD_INDEX = new AtomicInteger();

  private final TaskQueue queue = new TaskQueue(QUEUE_CAPACITY);
  private final DirtyVersions versions = new DirtyVersions();
  private final SnapshotPool pool;
  private final Snapshotter snapshotter;
  private final ExecutorService executor;
  private final AtomicBoolean pumping = new AtomicBoolean();
  private final AtomicInteger inFlight = new AtomicInteger();
  private final MeshPipeline pipeline = new MeshPipeline();

  private volatile long totalTasks;
  private volatile long totalNanos;
  private volatile long deferredCells;

  public BricklayerScheduler(SnapshotPool pool, DecodeCache decodeCache) {
    this.pool = pool;
    this.snapshotter = new Snapshotter(decodeCache);
    BricklayerConfig.Values config = BricklayerConfig.get();
    ThreadFactory factory =
        runnable -> {
          Thread thread =
              new Thread(runnable, "Bricklayer-Mesher-" + THREAD_INDEX.incrementAndGet());
          thread.setDaemon(true);
          thread.setPriority(Thread.NORM_PRIORITY - 1);
          return thread;
        };
    this.executor = Executors.newFixedThreadPool(config.workerThreads(), factory);
  }

  public DirtyVersions versions() {
    return versions;
  }

  public int queueDepth() {
    return queue.size();
  }

  public int inFlight() {
    return inFlight.get();
  }

  public int workerCount() {
    return BricklayerConfig.get().workerThreads();
  }

  public double meanCompileMs() {
    long tasks = totalTasks;
    return tasks == 0 ? 0.0 : totalNanos / 1.0E6 / tasks;
  }

  public double deferredRatio() {
    long tasks = totalTasks;
    return tasks == 0 ? 0.0 : deferredCells / (double) (tasks * 4096L);
  }

  /**
   * Captures a section and queues it.
   *
   * @return true when a task was queued; false when the queue refused it
   */
  public boolean submit(ClientLevel level, SectionPos pos, double score, @Nullable Object owner) {
    if (!CompatGate.active() || inFlight.get() >= BricklayerConfig.get().maxInFlight()) {
      return false;
    }
    long key = pos.asLong();
    int version = versions.bump(key);
    SectionSnapshot snapshot = pool.acquire();
    snapshotter.capture(level, pos, key, version, snapshot);
    TaskQueue.Task task = new TaskQueue.Task(key, version, snapshot, score, owner);
    if (!queue.offer(task)) {
      pool.release(snapshot);
      return false;
    }
    pump();
    return true;
  }

  /**
   * Compiles a section inline on the calling (main) thread, preserving vanilla's synchronous
   * behaviour for player edits.
   */
  @Nullable
  public CompiledMesh submitImmediate(
      ClientLevel level, SectionPos pos, SectionBufferBuilderPack buffers, @Nullable Object owner) {
    if (!CompatGate.active()) {
      return null;
    }
    long key = pos.asLong();
    int version = versions.bump(key);
    SectionSnapshot snapshot = pool.acquire();
    try {
      snapshotter.capture(level, pos, key, version, snapshot);
      return pipeline.mesh(level, pos, snapshot, buffers, Bricklayer.resourceGeneration(), owner);
    } finally {
      pool.release(snapshot);
    }
  }

  /**
   * Captures a snapshot for a section vanilla is about to rebuild itself, stamping the dirty
   * version. Called from the rebuild path, i.e. on the main thread.
   */
  public SectionSnapshot capture(ClientLevel level, SectionPos pos) {
    long key = pos.asLong();
    SectionSnapshot snapshot = pool.acquire();
    snapshotter.capture(level, pos, key, versions.bump(key), snapshot);
    return snapshot;
  }

  private void pump() {
    if (queue.size() == 0 || !pumping.compareAndSet(false, true)) {
      return;
    }
    executor.execute(this::drainLoop);
  }

  private void drainLoop() {
    try {
      TaskQueue.Task task;
      while ((task = queue.poll()) != null) {
        inFlight.incrementAndGet();
        try {
          process(task);
        } finally {
          inFlight.decrementAndGet();
        }
      }
    } finally {
      pumping.set(false);
      if (queue.size() > 0) {
        pump();
      }
    }
  }

  private void process(TaskQueue.Task task) {
    SectionSnapshot snapshot = task.snapshot;
    ClientLevel level = Minecraft.getInstance().level;
    SectionPos pos = SectionPos.of(task.sectionKey);
    if (level == null || !versions.matches(task.sectionKey, task.version)) {
      pool.release(snapshot);
      return;
    }

    long start = System.nanoTime();
    CompiledMesh mesh = null;
    try {
      // Vanilla hands each task its own buffer pack from a pool; a fresh pack per task matches that
      // lifetime and keeps the worker free of shared mutable state.
      SectionBufferBuilderPack buffers = new SectionBufferBuilderPack();
      mesh =
          pipeline.mesh(
              level, pos, snapshot, buffers, Bricklayer.resourceGeneration(), task.attachment);
      buffers.close();
      if (mesh != null) {
        Bricklayer.get().uploadGovernor().submit(mesh);
        totalTasks++;
        totalNanos += System.nanoTime() - start;
        deferredCells += mesh.deferredCellCount;
        CompileEvent.record(
            task.sectionKey, mesh.compileNanos, mesh.quadCount, mesh.deferredCellCount);
      }
    } catch (RuntimeException | LinkageError failure) {
      // Never let a bad section take the game down: mark it vanilla-only and move on.
      CompatGate.recordFailure("compile " + task.sectionKey, failure);
      if (mesh != null) {
        mesh.close();
      }
    } finally {
      pool.release(snapshot);
    }
  }

  /** Priority score for a section, from the current camera. */
  public static double score(
      SectionPos pos,
      double cameraX,
      double cameraY,
      double cameraZ,
      boolean inFrustum,
      boolean behind,
      long ageTicks) {
    double dx = pos.minBlockX() + 8 - cameraX;
    double dy = pos.minBlockY() + 8 - cameraY;
    double dz = pos.minBlockZ() + 8 - cameraZ;
    return PriorityFn.score(dx * dx + dy * dy + dz * dz, inFrustum, behind, ageTicks);
  }

  /** Priority score for a render section, using vanilla's own distance. */
  public static double score(SectionRenderDispatcher.RenderSection section) {
    var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    var origin = section.getOrigin();
    return score(SectionPos.of(origin), camera.x, camera.y, camera.z, true, false, 0L);
  }

  /** Drops every queued task and returns its snapshot to the pool. */
  public void reset() {
    TaskQueue.Task task;
    while ((task = queue.poll()) != null) {
      pool.release(task.snapshot);
    }
    versions.clear();
    Bricklayer.get().uploadGovernor().reset();
  }

  public void shutdown() {
    reset();
    executor.shutdownNow();
  }
}
