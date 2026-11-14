/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.snapshot;

import dev.example.bricklayer.Bricklayer;
import dev.example.bricklayer.core.grid.SectionGrid;
import dev.example.bricklayer.core.grid.SectionSnapshot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Main-thread capture of a section and its one-block border.
 *
 * <p>Block states come out of the {@link DecodeCache}, so re-meshing an edited area only decodes
 * the sections that actually changed. Light is left zeroed on purpose: workers read it through the
 * light engine, the same cross-thread read vanilla's own compile task performs, and any light
 * change dirties the section through the normal path, so a racy read self-corrects with a re-mesh.
 */
public final class Snapshotter {
  private final DecodeCache cache;

  public Snapshotter(DecodeCache cache) {
    this.cache = cache;
  }

  /**
   * Fills {@code snapshot} with the section at {@code pos} and its border.
   *
   * @return the same snapshot, for chaining
   */
  public SectionSnapshot capture(
      ClientLevel level, SectionPos pos, long sectionKey, int version, SectionSnapshot snapshot) {
    snapshot.sectionKey = sectionKey;
    snapshot.version = version;
    snapshot.resourceGeneration = Bricklayer.resourceGeneration();
    snapshot.beCount = 0;

    int minY = level.getMinBuildHeight();
    int maxY = level.getMaxBuildHeight();

    for (int y = -1; y <= 16; y++) {
      int worldY = pos.minBlockY() + y;
      boolean outside = worldY < minY || worldY >= maxY;
      for (int z = -1; z <= 16; z++) {
        for (int x = -1; x <= 16; x++) {
          int grid = SectionGrid.idx(x, y, z);
          if (outside) {
            snapshot.state[grid] = 0;
            continue;
          }
          int worldX = pos.minBlockX() + x;
          int worldZ = pos.minBlockZ() + z;
          LevelChunk chunk =
              level.getChunk(
                  SectionPos.blockToSectionCoord(worldX), SectionPos.blockToSectionCoord(worldZ));
          LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(worldY));
          if (section == null) {
            snapshot.state[grid] = 0;
            continue;
          }
          int[] decoded = cache.get(section);
          int localX = worldX & 15;
          int localY = worldY & 15;
          int localZ = worldZ & 15;
          snapshot.state[grid] = decoded[(localY << 8) | (localZ << 4) | localX];
        }
      }
    }

    LevelChunk chunk = level.getChunk(pos.x(), pos.z());
    LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(pos.minBlockY()));
    snapshot.empty = section != null && section.hasOnlyAir();

    // Block entities in this section, captured as section-local cell indices.
    if (!snapshot.empty && chunk.getBlockEntities() != null) {
      for (BlockPos bePos : chunk.getBlockEntities().keySet()) {
        if (SectionPos.blockToSectionCoord(bePos.getY()) != pos.y()) {
          continue;
        }
        int localX = bePos.getX() & 15;
        int localY = bePos.getY() & 15;
        int localZ = bePos.getZ() & 15;
        snapshot.addBlockEntity(SectionGrid.cellIndex(localX, localY, localZ));
      }
    }

    if (snapshot.state[SectionGrid.INTERIOR_ORIGIN] == 0 && snapshot.beCount == 0) {
      boolean allAir = true;
      for (int cell = 0; cell < SectionGrid.CELLS; cell++) {
        if (snapshot
                .state[
                SectionGrid.INTERIOR_ORIGIN
                    + dev.example.bricklayer.core.mesh.VisFlood.gridDelta(cell)]
            != 0) {
          allAir = false;
          break;
        }
      }
      snapshot.empty = allAir;
    }

    return snapshot;
  }
}
