/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.quad;

/**
 * Port of vanilla's {@code LegacyRandomSource} (the 48-bit LCG), reduced to one mutable long.
 *
 * <p>Only the pieces needed to reproduce a position-seeded variant draw are implemented. An
 * instance is cheap enough to keep as a field on a worker's scratch object, so selecting a weighted
 * model variant allocates nothing.
 */
public final class LegacyRandom {
  public static final long MULTIPLIER = 25214903917L;
  public static final long ADDEND = 11L;
  public static final long MASK = (1L << 48) - 1;

  private long seed;

  public LegacyRandom() {
    this(0L);
  }

  public LegacyRandom(long seed) {
    setSeed(seed);
  }

  public void setSeed(long seed) {
    this.seed = (seed ^ MULTIPLIER) & MASK;
  }

  /** Vanilla {@code next(int)}: the top {@code bits} of the next LCG state. */
  public int next(int bits) {
    seed = (seed * MULTIPLIER + ADDEND) & MASK;
    return (int) (seed >>> (48 - bits));
  }

  public int nextInt() {
    return next(32);
  }

  /** Vanilla {@code nextLong()}, including its sign-extension quirk. */
  public long nextLong() {
    return ((long) next(32) << 32) + (long) next(32);
  }

  public double nextDouble() {
    return (((long) next(26) << 27) + (long) next(27)) * 0x1.0p-53;
  }

  /** Vanilla {@code nextInt(int)}. */
  public int nextInt(int bound) {
    if (bound <= 0) {
      throw new IllegalArgumentException("bound must be positive");
    }
    if ((bound & -bound) == bound) {
      return (int) (((long) bound * (long) next(31)) >> 31);
    }
    int j = next(31);
    int k = j % bound;
    return j - k + (bound - 1) < 0 ? (k < 0 ? k + bound : k) : k;
  }

  /** The single draw used to pick a weighted model variant. */
  public static long variantDraw(long worldSeed) {
    LegacyRandom random = new LegacyRandom(worldSeed);
    return random.nextLong();
  }
}
