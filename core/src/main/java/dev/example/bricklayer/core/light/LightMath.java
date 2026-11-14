/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.light;

/** Conversions between the snapshot's packed light byte and vanilla's vertex light format. */
public final class LightMath {
  /** Vanilla's full-bright vertex light value: block 15, sky 15. */
  public static final int FULL_BRIGHT = 0xF000F0;

  private LightMath() {}

  /** Vanilla {@code LightTexture#pack}: block in 4..7, sky in 20..23. */
  public static int pack(int block, int sky) {
    return (block & 15) << 4 | (sky & 15) << 20;
  }

  public static int block(int packed) {
    return (packed >> 4) & 15;
  }

  public static int sky(int packed) {
    return (packed >> 20) & 15;
  }

  /** Converts a snapshot light byte, {@code (sky << 4) | block}, to a vertex light value. */
  public static int vertexLight(byte cellLight) {
    int value = cellLight & 0xFF;
    return ((value & 15) << 4) | (((value >> 4) & 15) << 20);
  }

  /** Packs two 0..15 channels from floats, rounding to nearest. */
  public static int packRounded(float block, float sky) {
    int b = Math.round(block);
    int s = Math.round(sky);
    if (b < 0) {
      b = 0;
    } else if (b > 15) {
      b = 15;
    }
    if (s < 0) {
      s = 0;
    } else if (s > 15) {
      s = 15;
    }
    return pack(b, s);
  }
}
