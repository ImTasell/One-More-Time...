/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.upload;

import dev.example.bricklayer.config.BricklayerConfig;
import dev.example.bricklayer.core.sched.UploadBudget;
import java.util.function.Consumer;

/**
 * Drains finished meshes on the render thread, within a millisecond budget.
 *
 * <p>Six uploads or the configured budget per frame, whichever comes first. When the previous frame
 * was already slow the budget halves so a backlog cannot lock the frame rate, and recovers once
 * frames are healthy again.
 */
public final class UploadGovernor {
  private final ResultQueue results = new ResultQueue();
  private UploadBudget budget = new UploadBudget(BricklayerConfig.get().uploadBudgetMs());
  private long lastFrameStartNanos;
  private int uploadsThisFrame;
  private long uploadsTotal;
  private double lastFrameMs;

  public void submit(CompiledMesh mesh) {
    results.add(mesh);
  }

  public int queued() {
    return results.size();
  }

  public long uploadsTotal() {
    return uploadsTotal;
  }

  /** Drops a finished mesh that lost its race with a newer edit. */
  public void discard(CompiledMesh mesh) {
    results.recordDrop(mesh);
  }

  /** Drains up to the budget, handing each accepted mesh to {@code uploader}. */
  public void drain(Consumer<CompiledMesh> uploader) {
    lastFrameStartNanos = System.nanoTime();
    uploadsThisFrame = 0;
    CompiledMesh mesh;
    while (budget.mayUpload(lastFrameStartNanos, uploadsThisFrame)
        && (mesh = results.poll()) != null) {
      uploader.accept(mesh);
      uploadsThisFrame++;
      uploadsTotal++;
    }
  }

  /** Called at the end of a frame; adapts the budget from the frame time. */
  public void endFrame() {
    long frameNanos = lastFrameStartNanos == 0 ? 0 : System.nanoTime() - lastFrameStartNanos;
    lastFrameMs = frameNanos / 1.0E6;
    if (lastFrameMs > 0.0) {
      budget.recordFrame(lastFrameMs);
    }
  }

  public double lastFrameMs() {
    return lastFrameMs;
  }

  public int queuedDropped() {
    return (int) results.droppedCount();
  }

  public void reset() {
    results.clear();
    budget = new UploadBudget(BricklayerConfig.get().uploadBudgetMs());
    uploadsThisFrame = 0;
    lastFrameStartNanos = 0;
  }
}
