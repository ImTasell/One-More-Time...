/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.meta;

/** Per-state flag bits held in {@link MetaTable#flags}. */
public final class CellFlags {
  /** The state is air and can never emit geometry. */
  public static final int AIR = 1;

  /** A full cube that hides every neighbouring face. */
  public static final int OPAQUE_FULL = 1 << 1;

  /** Blocks the visibility flood fill (vanilla's {@code VisGraph} rules). */
  public static final int VIS_OPAQUE = 1 << 2;

  /** The model is cacheable in the quad store and may take the fast path. */
  public static final int STATIC_MODEL = 1 << 3;

  /** The state participates in smooth lighting / ambient occlusion. */
  public static final int USES_AO = 1 << 4;

  /** At least one quad carries a tint index. */
  public static final int HAS_TINT = 1 << 5;

  /** The state renders at full brightness. */
  public static final int EMISSIVE = 1 << 6;

  /** The state has a non-empty fluid state; always deferred to vanilla. */
  public static final int FLUID = 1 << 7;

  /** The state has a block-entity renderer. */
  public static final int BE_RENDERER = 1 << 8;

  /** The state must be meshed by vanilla's {@code BlockRenderDispatcher}. */
  public static final int FORCE_VANILLA = 1 << 9;

  /**
   * The exact predicate vanilla's AO code uses to decide a neighbour does not shade: a state with
   * this bit set is skipped when gathering side and corner samples.
   */
  public static final int AO_CLEAR = 1 << 10;

  /** Renders into the solid layer. */
  public static final int LAYER_SOLID = 1 << 11;

  /** Renders into the cutout-mipped layer. */
  public static final int LAYER_CUTOUT_MIPPED = 1 << 12;

  /** Renders into the cutout layer. */
  public static final int LAYER_CUTOUT = 1 << 13;

  /** The tint colour depends on the world position's Y, so it cannot be memoised per section. */
  public static final int TINT_POSITIONAL = 1 << 14;

  /** All three layer bits. */
  public static final int LAYERS = LAYER_SOLID | LAYER_CUTOUT_MIPPED | LAYER_CUTOUT;

  private CellFlags() {}

  /** Layer index (0 solid, 1 cutout-mipped, 2 cutout) of a flag word. */
  public static int layerOf(int flags) {
    if ((flags & LAYER_SOLID) != 0) {
      return 0;
    }
    if ((flags & LAYER_CUTOUT_MIPPED) != 0) {
      return 1;
    }
    return 2;
  }
}
