/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.meta;

import dev.example.bricklayer.core.meta.FaceVisibilityTable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Fills {@link FaceVisibilityTable} misses with vanilla's own answer.
 *
 * <p>Rather than reimplementing "does glass cull against glass" or NeoForge's face-hiding
 * extension, this evaluates {@code Block#shouldRenderFace} through a two-cell {@link BlockGetter}
 * stub: the state in question at the origin and the neighbour one step along the face. Leaves,
 * glass, same-block culling and modded overrides therefore behave exactly as in vanilla.
 */
public final class FaceVisibilityOracle implements FaceVisibilityTable.Oracle {
  private final TwoCellLevel level = new TwoCellLevel();

  @Override
  public boolean visible(int stateId, int neighbourState, int face) {
    BlockState state = VanillaStates.byId(stateId);
    BlockState neighbour = VanillaStates.byId(neighbourState);
    if (state == null || neighbour == null) {
      return true;
    }
    level.set(state, neighbour);
    Direction direction = Direction.values()[face];
    return BlockStateAccess.shouldRenderFace(
        state, level, BlockPos.ZERO, direction, BlockPos.ZERO.relative(direction));
  }

  /**
   * A BlockGetter that only knows two cells. Every other query is answered conservatively, which is
   * all {@code shouldRenderFace} needs.
   */
  private static final class TwoCellLevel implements BlockGetter {
    private BlockState origin = Blocks.AIR.defaultBlockState();
    private BlockState neighbour = Blocks.AIR.defaultBlockState();

    void set(BlockState origin, BlockState neighbour) {
      this.origin = origin;
      this.neighbour = neighbour;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
      return pos.equals(BlockPos.ZERO) ? origin : neighbour;
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
      return getBlockState(pos).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
      return null;
    }

    @Override
    public int getHeight() {
      return 384;
    }

    @Override
    public int getMinBuildHeight() {
      return -64;
    }
  }
}
