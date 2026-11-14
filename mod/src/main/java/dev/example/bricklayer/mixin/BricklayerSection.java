/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.mixin;

import dev.example.bricklayer.core.grid.SectionSnapshot;
import javax.annotation.Nullable;

/**
 * Duck interface added to {@code SectionRenderDispatcher$RenderSection} by {@code
 * RenderSectionMixin}.
 *
 * <p>The snapshot and the version cannot live on the compile task because the task is created after
 * the dirty hook runs, and they must not live in a map because that allocates per section per edit.
 * An {@code @Unique} field on the section is the cheapest place that survives from {@code setDirty}
 * to the rebuild.
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

  void bricklayer$clearPlayerChanged();

  /** Marks the section as vanilla-only after a failure, until it is dirtied again. */
  boolean bricklayer$vanillaOnly();

  void bricklayer$setVanillaOnly(boolean vanillaOnly);
}
