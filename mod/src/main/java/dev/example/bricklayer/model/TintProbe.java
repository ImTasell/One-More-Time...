/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.model;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/**
 * A {@code BlockAndTintGetter} with nothing in it, used only to ask a block colour provider for a
 * colour at a given height and see whether the answer changes with Y.
 */
final class TintProbe implements net.minecraft.world.level.BlockAndTintGetter {
  static final TintProbe INSTANCE = new TintProbe();

  private TintProbe() {}

  @Override
  public BlockState getBlockState(BlockPos pos) {
    return Blocks.AIR.defaultBlockState();
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
  public float getShade(Direction direction, boolean shaded) {
    return 1.0f;
  }

  @Override
  public LevelLightEngine getLightEngine() {
    throw new UnsupportedOperationException("tint probe has no light engine");
  }

  @Override
  public int getBlockTint(BlockPos pos, ColorResolver resolver) {
    return resolver.getColor(null, pos.getX(), pos.getZ());
  }

  @Override
  public int getLightEmission(BlockPos pos) {
    return 0;
  }

  /** Unused but part of the interface. */
  static ClientLevel levelOrNull() {
    return null;
  }
}
