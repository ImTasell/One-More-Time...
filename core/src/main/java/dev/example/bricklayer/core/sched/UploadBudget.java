/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.sched;

/**
 * Frame upload budget for the render thread.
 *
 * <p>The governor drains finished meshes until the millisecond budget is spent or the upload cap is
 * reached. If the previous frame was already slow the budget halves, so a backlog cannot lock the
 * frame rate at a crawl; it recovers gradually once frames are healthy again.
 */
public final class UploadBudget {
  /** Hard cap on uploads drained in a single frame. */
  public static final int MAX_UPLOADS_PER_FRAME = 6;

  /** A frame slower than this is treated as overloaded. */
  public static final double SLOW_FRAME_MS = 25.0;

  private final double initialMs;
  private double budgetMs;

  public UploadBudget(double initialMs) {
    this.initialMs = Math.max(0.25, initialMs);
    this.budgetMs = this.initialMs;
  }

  public double budgetMs() {
    return budgetMs;
  }

  /** Feeds the previous frame's duration in and adapts the budget. */
  public void recordFrame(double frameMs) {
    if (frameMs > SLOW_FRAME_MS) {
      budgetMs = Math.max(0.25, budgetMs * 0.5);
    } else {
      budgetMs = Math.min(initialMs, budgetMs * 1.05 + 0.01);
    }
  }

  /** True when another upload may run in this frame. */
  public boolean mayUpload(long startNanos, int uploadsSoFar) {
    if (uploadsSoFar >= MAX_UPLOADS_PER_FRAME) {
      return false;
    }
    return (System.nanoTime() - startNanos) / 1.0E6 < budgetMs;
  }

  public void reset() {
    budgetMs = initialMs;
  }
}
