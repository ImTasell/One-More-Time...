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
import dev.example.bricklayer.upload.CompiledSection;
import dev.example.bricklayer.upload.UploadGovernor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.core.SectionPos;

/**
 * The scheduler that replaces vanilla's section dispatch queue.
 *
 * <p>Snapshots are taken on the main thread inside the dirty hook, tasks go into a bounded priority
 * queue, and a small pool of daemon workers drains it. Finished sections land in the upload
 * governor's queue and are uploaded on the render thread.
 *
 * <p>Player-initiated edits keep vanilla's synchronous semantics: {@link #submitImmediate} runs the
 * same pipeline inline so a broken block never waits for a worker.
 */
public final class BricklayerScheduler {
  private static final int QUEUE_CAPACITY = 4096;

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
              new Thread(runnable, "Bricklayer-Mesher-" + threadIndex.incrementAndGet());
          thread.setDaemon(true);
          thread.setPriority(Thread.NORM_PRIORITY - 1);
          return thread;
        };
    this.executor = Executors.newFixedThreadPool(config.workerThreads(), factory);
  }

  private static final AtomicInteger threadIndex = new AtomicInteger();

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
  public boolean submit(ClientLevel level, SectionPos pos, double score) {
    if (!CompatGate.active() || inFlight.get() >= BricklayerConfig.get().maxInFlight()) {
      return false;
    }
    long key = pos.asLong();
    int version = versions.bump(key);
    SectionSnapshot snapshot = pool.acquire();
    snapshotter.capture(level, pos, key, version, snapshot);
    TaskQueue.Task task = new TaskQueue.Task(key, version, snapshot, score);
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
  public CompiledSection submitImmediate(
      ClientLevel level, SectionPos pos, SectionBufferBuilderPack buffers) {
    if (!CompatGate.active()) {
      return null;
    }
    long key = pos.asLong();
    int version = versions.bump(key);
    SectionSnapshot snapshot = pool.acquire();
    try {
      snapshotter.capture(level, pos, key, version, snapshot);
      return pipeline.mesh(level, pos, snapshot, buffers, Bricklayer.resourceGeneration());
    } finally {
      pool.release(snapshot);
    }
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
    ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
    SectionPos pos = SectionPos.of(task.sectionKey);
    if (level == null || !versions.matches(task.sectionKey, task.version)) {
      pool.release(snapshot);
      return;
    }

    long start = System.nanoTime();
    SectionBufferBuilderPack buffers = new SectionBufferBuilderPack();
    CompiledSection compiled = null;
    try {
      compiled = pipeline.mesh(level, pos, snapshot, buffers, Bricklayer.resourceGeneration());
      if (compiled != null) {
        Bricklayer.get().uploadGovernor().submit(compiled);
        totalTasks++;
        totalNanos += System.nanoTime() - start;
        deferredCells += compiled.deferredCellCount;
        CompileEvent.record(
            task.sectionKey, compiled.compileNanos, compiled.quadCount, compiled.deferredCellCount);
      }
    } catch (RuntimeException | LinkageError failure) {
      // Never let a bad section take the game down: mark it vanilla-only and reset the builders.
      CompatGate.recordFailure("compile " + task.sectionKey, failure);
      if (compiled != null) {
        compiled.close();
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

  /**
   * Captures a snapshot for a section vanilla is about to rebuild itself, stamping the dirty
   * version. Called from the rebuild task's constructor, i.e. on the main thread.
   */
  public SectionSnapshot capture(ClientLevel level, SectionPos pos) {
    long key = pos.asLong();
    SectionSnapshot snapshot = pool.acquire();
    snapshotter.capture(level, pos, key, versions.bump(key), snapshot);
    return snapshot;
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

  /** Test hook: drains everything synchronously on the calling thread. */
  void drainNow() {
    TaskQueue.Task task;
    while ((task = queue.poll()) != null) {
      process(task);
    }
  }

  @Nullable
  public UploadGovernor governor() {
    return Bricklayer.get().uploadGovernor();
  }
}
