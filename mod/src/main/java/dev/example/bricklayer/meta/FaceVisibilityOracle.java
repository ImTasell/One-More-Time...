/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.meta;

import dev.example.bricklayer.core.meta.FaceVisibilityTable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Fills {@link FaceVisibilityTable} misses with vanilla's own answer.
 *
 * <p>Rather than reimplementing "does glass cull against glass" or NeoForge's face-hiding
 * extension, this evaluates {@code Block#shouldRenderFace} through a two-cell {@link BlockGetter}
 * stub: the state in question at the origin and the neighbour one step along the face. The client's
 * own block-state registry supplies the states, so leaves, glass, same-block culling and modded
 * overrides behave exactly as in vanilla.
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
   * Small BlockGetter that can only answer about two cells. Every other query is answered
   * conservatively, which is enough for {@code shouldRenderFace}.
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
      if (pos.equals(BlockPos.ZERO)) {
        return origin;
      }
      return neighbour;
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

    @Override
    public float getShade(Direction direction, boolean shade) {
      return Minecraft.getInstance().level == null
          ? 1.0f
          : Minecraft.getInstance().level.getShade(direction, shade);
    }

    @Override
    public int getLightEmission(BlockPos pos) {
      return 0;
    }

    @Override
    public VoxelShape getBlockSupportShape(BlockPos pos) {
      return getBlockState(pos).getBlockSupportShape(this, pos);
    }

    @Override
    public VoxelShape getCollisionShape(BlockPos pos, CollisionContext context) {
      return getBlockState(pos).getCollisionShape(this, pos, context);
    }
  }
}
