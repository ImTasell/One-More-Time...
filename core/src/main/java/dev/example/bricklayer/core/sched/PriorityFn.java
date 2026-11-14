/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.sched;

/**
 * Task priority: nearest sections first, out-of-frustum sections penalised, sections behind the
 * camera penalised again, and an age term so a distant section cannot starve forever.
 */
public final class PriorityFn {
  private PriorityFn() {}

  /**
   * @param distSq squared distance from the camera to the section centre
   * @param inFrustum whether the section passed the frustum test
   * @param behindCamera whether the section is behind the camera plane
   * @param ageTicks how long the task has waited
   * @return a score where lower means sooner
   */
  public static double score(
      double distSq, boolean inFrustum, boolean behindCamera, long ageTicks) {
    double score = distSq;
    if (!inFrustum) {
      score *= 4.0;
    }
    if (behindCamera) {
      score *= 1.5;
    }
    // Age bonus: after roughly 40 ticks a task competes as if it were twice as close.
    return score / (1.0 + Math.min(ageTicks, 400L) / 40.0);
  }
}
