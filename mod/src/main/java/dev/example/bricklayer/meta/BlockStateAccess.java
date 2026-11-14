/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.meta;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The vanilla predicates Bricklayer calls, in one place.
 *
 * <p>Keeping them together makes the "verify against the decompiled sources" list short: every
 * signature that can shift between 1.21.x versions appears once, and it is checked against 1.21.1's
 * sources.
 */
public final class BlockStateAccess {
  private BlockStateAccess() {}

  /** Vanilla's six-face culling decision, including same-block and modded face-hiding overrides. */
  public static boolean shouldRenderFace(
      BlockState state, BlockGetter level, BlockPos pos, Direction face, BlockPos neighbourPos) {
    return Block.shouldRenderFace(state, level, pos, face, neighbourPos);
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
    return state.getShadeBrightness(level, pos);
  }
}
