/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.snapshot;

import dev.example.bricklayer.meta.VanillaStates;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

/**
 * Decoded block-state ids for chunk sections, valid until the section changes.
 *
 * <p>Meshing a section reads its 26 neighbours as well; without a cache every edit would redecode
 * roughly two million states. The cache is keyed by {@link LevelChunkSection} identity and
 * invalidated by the {@code LevelChunk#setBlockState} mixin, so an edited section is decoded again
 * while untouched neighbours are reused. Everything is cleared on level unload and logout.
 *
 * <p>Main thread only.
 */
public final class DecodeCache {
  private static final int[] AIR = new int[4096];

  private final Map<LevelChunkSection, int[]> states = new IdentityHashMap<>();

  /** Decoded state ids of a section, in {@code (y << 8) | (z << 4) | x} order. */
  public int[] get(LevelChunkSection section) {
    if (section == null) {
      return AIR;
    }
    if (section.hasOnlyAir()) {
      return AIR;
    }
    int[] cached = states.get(section);
    if (cached != null) {
      return cached;
    }
    int[] decoded = new int[4096];
    PalettedContainer<BlockState> container = section.getStates();
    for (int y = 0; y < 16; y++) {
      for (int z = 0; z < 16; z++) {
        for (int x = 0; x < 16; x++) {
          decoded[(y << 8) | (z << 4) | x] = VanillaStates.id(container.get(x, y, z));
        }
      }
    }
    states.put(section, decoded);
    return decoded;
  }

  public void invalidate(LevelChunkSection section) {
    if (section != null) {
      states.remove(section);
    }
  }

  public void clear() {
    states.clear();
  }

  public int size() {
    return states.size();
  }
}
