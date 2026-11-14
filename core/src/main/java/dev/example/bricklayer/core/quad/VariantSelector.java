/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.quad;

/**
 * Chooses one sub-model of a weighted model for a world position.
 *
 * <p>Vanilla's stone, dirt and grass block models are weighted multi-variant models: each variant
 * is drawn once per position from a position-seeded random. Treating those as dynamic would defer
 * most of the terrain, so every variant is cached as its own sub-model and the draw is reproduced
 * here.
 *
 * <p>The seed is vanilla's {@code Mth#getSeed} on the cell's world position, run through the legacy
 * LCG and then reduced modulo the total weight. Multipart models are evaluated once per state id by
 * the glue layer instead, because their conditions depend only on the state.
 */
public final class VariantSelector {
  private VariantSelector() {}

  /** Vanilla {@code Mth#getSeed}. */
  public static long positionSeed(int x, int y, int z) {
    long seed = (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;
    seed = seed * seed * 42317861L + seed * 11L;
    return seed >> 16;
  }

  /**
   * @param worldSeed vanilla position seed
   * @param cumulative cumulative weights of the variants, starting at {@code cumBase}
   * @param cumBase offset of this model's weights inside {@code cumulative}
   * @param count number of variants
   * @param totalWeight sum of all weights
   * @return variant index in 0..count-1
   */
  public static int select(
      long worldSeed, int[] cumulative, int cumBase, int count, int totalWeight) {
    if (count <= 1 || totalWeight <= 0) {
      return 0;
    }
    long draw = LegacyRandom.variantDraw(worldSeed);
    int target = Math.floorMod(draw, totalWeight);
    for (int i = 0; i < count; i++) {
      if (target < cumulative[cumBase + i]) {
        return i;
      }
    }
    return count - 1;
  }

  /** Convenience overload for a world position. */
  public static int select(
      int x, int y, int z, int[] cumulative, int cumBase, int count, int totalWeight) {
    return select(positionSeed(x, y, z), cumulative, cumBase, count, totalWeight);
  }
}
