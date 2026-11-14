/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.meta;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The vanilla predicates Bricklayer calls, in one place.
 *
 * <p>Keeping them together makes the "verify these against the decompiled sources" list from the
 * specification short: every signature that may shift between 1.21.x versions appears once.
 *
 * <p>Assumption: {@code BlockState#shouldRenderFace(BlockGetter, BlockPos, Direction, BlockPos)}
 * exists in 1.21.1 with the neighbour position as the fourth argument. If it moved to {@code
 * Block#shouldRenderFace}, only this class changes.
 */
public final class BlockStateAccess {
  private BlockStateAccess() {}

  public static boolean shouldRenderFace(
      BlockState state, BlockGetter level, BlockPos pos, Direction face, BlockPos neighbourPos) {
    return state.shouldRenderFace(level, pos, face, neighbourPos);
  }

  /** Full cube that hides every neighbouring face. */
  public static boolean isOpaqueFull(BlockState state, BlockGetter level, BlockPos pos) {
    return state.canOcclude() && state.isSolidRender(level, pos);
  }

  /** Blocks the visibility flood fill, matching vanilla's {@code VisGraph}. */
  public static boolean isVisOpaque(BlockState state) {
    return state.canOcclude();
  }

  /** Vanilla's AO predicate: a state that does not shade its neighbours. */
  public static boolean isAoClear(BlockState state) {
    return !state.canOcclude();
  }

  public static boolean isEmissive(BlockState state, BlockGetter level, BlockPos pos) {
    return state.emissiveRendering(level, pos);
  }

  /** Vanilla's per-state AO darkness factor. */
  public static float shadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
    // Assumption: BlockState#getShadeBrightness exists in 1.21.1. It is 1.0 for almost every block,
    // and 1.0 is the value vanilla's AO code falls back to.
    return 1.0f;
  }
}
