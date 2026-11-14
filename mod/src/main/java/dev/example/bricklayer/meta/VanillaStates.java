/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.meta;

import javax.annotation.Nullable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Conversions between vanilla global block-state ids and {@link BlockState}.
 *
 * <p>The snapshot stores ids and the mesher never resolves them; only the glue layer does, when it
 * asks vanilla a question the fast path could not answer.
 */
public final class VanillaStates {
  private VanillaStates() {}

  /** Global id of a state, as stored in a snapshot. */
  public static int id(BlockState state) {
    return Block.BLOCK_STATE_REGISTRY.getId(state);
  }

  @Nullable
  public static BlockState byId(int id) {
    if (id < 0 || id >= Block.BLOCK_STATE_REGISTRY.size()) {
      return null;
    }
    return Block.BLOCK_STATE_REGISTRY.byId(id);
  }

  /** Number of registered states, i.e. the size of every meta array. */
  public static int count() {
    return Block.BLOCK_STATE_REGISTRY.size();
  }
}
