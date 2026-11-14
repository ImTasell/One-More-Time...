/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 The Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.upload;

import dev.example.bricklayer.Bricklayer;
import dev.example.bricklayer.compat.CompatGate;
import dev.example.bricklayer.mixin.BricklayerSection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;

/**
 * Applies a finished section to vanilla's renderer, on the render thread only.
 *
 * <p>Uploads happen here and nowhere else: {@code VertexBuffer} state belongs to the render thread.
 * A section whose dirty version moved on while the task was in flight is discarded instead of
 * uploaded, so a fast edit followed by an older result can never win the race.
 *
 * <p>Assumption (spec 5.9): {@code SectionRenderDispatcher#getRenderSection(long)} resolves a
 * section by its key, and {@code RenderSection} exposes per-layer vertex buffers to the duck
 * interface added by {@code RenderSectionMixin}. Both live behind {@link BricklayerSection}, so a
 * mapping change is contained to that mixin.
 */
public final class SectionUploader {
  private SectionUploader() {}

  /**
   * Uploads one compiled section.
   *
   * @return true when the section was accepted and uploaded
   */
  public static boolean upload(CompiledSection compiled) {
    if (!CompatGate.active()) {
      compiled.close();
      return false;
    }
    if (compiled.resourceGeneration != Bricklayer.resourceGeneration()) {
      compiled.close();
      return false;
    }
    if (!Bricklayer.get().scheduler().versions().matches(compiled.sectionKey, compiled.version)) {
      Bricklayer.get().uploadGovernor().discard(compiled);
      return false;
    }

    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.level == null || minecraft.levelRenderer == null) {
      compiled.close();
      return false;
    }

    SectionRenderDispatcher dispatcher = minecraft.levelRenderer.getSectionRenderDispatcher();
    if (dispatcher == null) {
      compiled.close();
      return false;
    }
    SectionRenderDispatcher.RenderSection section =
        dispatcher.getRenderSection(SectionPos.of(compiled.sectionKey).asLong());
    if (section == null) {
      compiled.close();
      return false;
    }

    BricklayerSection access = (BricklayerSection) section;
    access.bricklayer$setSnapshot(null);
    for (int layer = 0; layer < compiled.layerCount(); layer++) {
      access.bricklayer$upload(layer, compiled.layer(layer));
    }
    access.bricklayer$setVisibility(compiled.visibilityPairs);
    return true;
  }
}
