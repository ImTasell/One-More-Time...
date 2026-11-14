/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.getter;

import dev.example.bricklayer.core.grid.SectionGrid;
import dev.example.bricklayer.core.grid.SectionSnapshot;
import dev.example.bricklayer.meta.VanillaStates;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/**
 * The only Minecraft world view a worker is allowed to touch.
 *
 * <p>Block and fluid states come from the snapshot, block entities from the references the
 * snapshotter captured, light from the snapshot's packed byte array, and tint and shade delegate to
 * the client level exactly as {@code RenderChunkRegion} does.
 *
 * <p>Threading: one instance per task; never shared.
 */
public final class SnapshotBlockGetter implements BlockAndTintGetter {
  private final ClientLevel level;
  private final SectionSnapshot snapshot;
  private final SectionPos origin;
  private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

  public SnapshotBlockGetter(ClientLevel level, SectionPos origin, SectionSnapshot snapshot) {
    this.level = level;
    this.origin = origin;
    this.snapshot = snapshot;
  }

  @Override
  public BlockState getBlockState(BlockPos pos) {
    int x = pos.getX() - origin.minBlockX();
    int y = pos.getY() - origin.minBlockY();
    int z = pos.getZ() - origin.minBlockZ();
    if (x < -1 || x > 16 || y < -1 || y > 16 || z < -1 || z > 16) {
      // Deferred renderers occasionally peek one block further; the world is the authority there.
      return level.getBlockState(pos);
    }
    BlockState state = VanillaStates.byId(snapshot.state[SectionGrid.idx(x, y, z)]);
    return state == null ? Blocks.AIR.defaultBlockState() : state;
  }

  @Override
  public FluidState getFluidState(BlockPos pos) {
    return getBlockState(pos).getFluidState();
  }

  @Nullable
  @Override
  public BlockEntity getBlockEntity(BlockPos pos) {
    int x = pos.getX() - origin.minBlockX();
    int y = pos.getY() - origin.minBlockY();
    int z = pos.getZ() - origin.minBlockZ();
    if (x < 0 || x > 15 || y < 0 || y > 15 || z < 0 || z > 15) {
      return null;
    }
    int cell = SectionGrid.cellIndex(x, y, z);
    for (int i = 0; i < snapshot.beCount; i++) {
      if (snapshot.bePositions[i] == cell) {
        return level.getBlockEntity(pos);
      }
    }
    return null;
  }

  @Override
  public int getBrightness(LightLayer layer, BlockPos pos) {
    int x = pos.getX() - origin.minBlockX();
    int y = pos.getY() - origin.minBlockY();
    int z = pos.getZ() - origin.minBlockZ();
    if (x < -1 || x > 16 || y < -1 || y > 16 || z < -1 || z > 16) {
      return level.getBrightness(layer, pos);
    }
    int packed = snapshot.light[SectionGrid.idx(x, y, z)] & 0xFF;
    return layer == LightLayer.SKY ? (packed >> 4) & 15 : packed & 15;
  }

  @Override
  public int getRawBrightness(BlockPos pos, int amount) {
    int sky = Math.max(0, getBrightness(LightLayer.SKY, pos) - amount);
    return sky << 20 | getBrightness(LightLayer.BLOCK, pos) << 4;
  }

  @Override
  public float getShade(Direction direction, boolean shaded) {
    return level.getShade(direction, shaded);
  }

  @Override
  public LevelLightEngine getLightEngine() {
    return level.getLightEngine();
  }

  @Override
  public int getBlockTint(BlockPos pos, ColorResolver resolver) {
    // RenderChunkRegion delegates tinting to the level so biome blending keeps working.
    return level.getBlockTint(pos, resolver);
  }

  @Override
  public int getHeight() {
    return level.getHeight();
  }

  @Override
  public int getMinBuildHeight() {
    return level.getMinBuildHeight();
  }

  /** Section-local cursor helper for deferred renderers that need a reusable position. */
  public BlockPos.MutableBlockPos cursor(int localX, int localY, int localZ) {
    return cursor.set(
        origin.minBlockX() + localX, origin.minBlockY() + localY, origin.minBlockZ() + localZ);
  }

  /** Client level this snapshot came from, for callers that need one. */
  public ClientLevel level() {
    return level;
  }
}
