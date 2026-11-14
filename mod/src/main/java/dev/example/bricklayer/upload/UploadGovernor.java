/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.upload;

import dev.example.bricklayer.config.BricklayerConfig;
import dev.example.bricklayer.core.sched.UploadBudget;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;

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

  public void submit(CompiledSection section) {
    results.add(section);
  }

  public int queued() {
    return results.size();
  }

  public long uploadsTotal() {
    return uploadsTotal;
  }

  public void beginFrame() {
    lastFrameStartNanos = System.nanoTime();
    uploadsThisFrame = 0;
  }

  /** Drops a finished section that lost its race with a newer edit. */
  public void discard(CompiledSection section) {
    results.recordDrop(section);
  }

  /** Drains up to the budget, handing each accepted section to {@code uploader}. */
  public void drain(Consumer<CompiledSection> uploader) {
    beginFrame();
    CompiledSection section;
    while (budget.mayUpload(lastFrameStartNanos, uploadsThisFrame)
        && (section = results.poll()) != null) {
      uploader.accept(section);
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

  public void reset() {
    results.clear();
    budget = new UploadBudget(BricklayerConfig.get().uploadBudgetMs());
    uploadsThisFrame = 0;
    lastFrameStartNanos = 0;
  }

  @Nullable
  public ResultQueue results() {
    return results;
  }

  /** True when the render thread currently owns the frame; used by the debug overlay. */
  public boolean onRenderThread() {
    return Minecraft.getInstance().isSameThread();
  }
}
