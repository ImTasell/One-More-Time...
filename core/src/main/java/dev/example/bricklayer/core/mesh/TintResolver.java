/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.mesh;

/**
 * Resolves a block tint colour for a cell. Implemented by the mod glue over {@code BlockColors}.
 */
@FunctionalInterface
public interface TintResolver {
  /**
   * @param stateId global block-state id
   * @param tintIndex the quad's tint index
   * @param localX section-local x in 0..15
   * @param localZ section-local z in 0..15
   * @return packed {@code 0xRRGGBB}, or -1 for no tint
   */
  int tint(int stateId, int tintIndex, int localX, int localZ);
}
