/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.mixin;

import com.mojang.blaze3d.vertex.MeshData;
import dev.example.bricklayer.core.grid.SectionSnapshot;
import javax.annotation.Nullable;
import net.minecraft.core.SectionPos;

/**
 * Duck interface added to {@code SectionRenderDispatcher$RenderSection} by {@code
 * RenderSectionMixin}.
 *
 * <p>The snapshot cannot live on the rebuild task because the task is created after the dirty hook
 * runs, and it must not live in a map because that allocates per section per edit. An
 * {@code @Unique} field on the section is the cheapest place that survives from {@code setDirty} to
 * {@code compile}.
 */
public interface BricklayerSection {
  @Nullable
  SectionSnapshot bricklayer$snapshot();

  void bricklayer$setSnapshot(@Nullable SectionSnapshot snapshot);

  /** Bumps the section's dirty version and returns the new value. */
  int bricklayer$bumpVersion();

  int bricklayer$version();

  /** True when a player edit dirtied this section, so it must compile synchronously. */
  boolean bricklayer$playerChanged();

  void bricklayer$setPlayerChanged(boolean playerChanged);

  /** Marks the section as vanilla-only after a failure, until it is dirtied again. */
  boolean bricklayer$vanillaOnly();

  void bricklayer$setVanillaOnly(boolean vanillaOnly);

  /** Section position this section belongs to. */
  SectionPos bricklayer$sectionPos();

  /** Uploads one layer's mesh data into the section's vertex buffer. Render thread only. */
  void bricklayer$upload(int layer, @Nullable MeshData data);

  /** Applies the visibility face-pair mask to the section's occlusion graph entry. */
  void bricklayer$setVisibility(long pairs);
}
