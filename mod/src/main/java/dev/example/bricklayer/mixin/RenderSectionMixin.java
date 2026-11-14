/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.mixin;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.example.bricklayer.Bricklayer;
import dev.example.bricklayer.compat.CompatGate;
import dev.example.bricklayer.core.grid.SectionSnapshot;
import dev.example.bricklayer.sched.BricklayerScheduler;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carries the snapshot and dirty version on the section itself.
 *
 * <p>Assumption: the target is {@code SectionRenderDispatcher$RenderSection} and it has a {@code
 * setDirty(boolean)} method in 1.21.1. If the signature differs, only this file changes; the
 * include list in {@code bricklayer.mixins.json} is the other place to check.
 */
@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class RenderSectionMixin implements BricklayerSection {
  @Unique @Nullable private SectionSnapshot bricklayer$snapshot;
  @Unique private int bricklayer$version;
  @Unique private boolean bricklayer$playerChanged;
  @Unique private boolean bricklayer$vanillaOnly;

  @Override
  @Nullable
  public SectionSnapshot bricklayer$snapshot() {
    return bricklayer$snapshot;
  }

  @Override
  public void bricklayer$setSnapshot(@Nullable SectionSnapshot snapshot) {
    bricklayer$snapshot = snapshot;
  }

  @Override
  public int bricklayer$bumpVersion() {
    return ++bricklayer$version;
  }

  @Override
  public int bricklayer$version() {
    return bricklayer$version;
  }

  @Override
  public boolean bricklayer$playerChanged() {
    return bricklayer$playerChanged;
  }

  @Override
  public void bricklayer$setPlayerChanged(boolean playerChanged) {
    bricklayer$playerChanged = playerChanged;
  }

  @Override
  public boolean bricklayer$vanillaOnly() {
    return bricklayer$vanillaOnly;
  }

  @Override
  public void bricklayer$setVanillaOnly(boolean vanillaOnly) {
    bricklayer$vanillaOnly = vanillaOnly;
  }

  /**
   * Every dirty notification invalidates the section's cached snapshot, and a change that came from
   * the player flags the section for synchronous compilation.
   */
  /** Render layers whose buffers this section owns, in the mesher's layer order. */
  @Unique
  private static final RenderType[] bricklayer$layers = {
    RenderType.solid(), RenderType.cutoutMipped(), RenderType.cutout()
  };

  @Override
  public SectionPos bricklayer$sectionPos() {
    SectionRenderDispatcher.RenderSection self =
        (SectionRenderDispatcher.RenderSection) (Object) this;
    return SectionPos.of(self.getOrigin());
  }

  @Override
  public void bricklayer$upload(int layer, @Nullable MeshData data) {
    if (data == null || layer < 0 || layer >= bricklayer$layers.length) {
      return;
    }
    SectionRenderDispatcher.RenderSection self =
        (SectionRenderDispatcher.RenderSection) (Object) this;
    // Assumption: RenderSection has a buffer per render type. If 1.21.1 stores one VertexBuffer per
    // render type in a map instead, only this lookup changes.
    VertexBuffer buffer = self.getBuffer(bricklayer$layers[layer]);
    if (buffer == null) {
      data.close();
      return;
    }
    buffer.bind();
    buffer.upload(data);
    VertexBuffer.unbind();
  }

  @Override
  public void bricklayer$setVisibility(long pairs) {
    SectionRenderDispatcher.RenderSection self =
        (SectionRenderDispatcher.RenderSection) (Object) this;
    // Assumption: vanilla's VisibilitySet exposes setVisible(Direction, Direction) and the section
    // accepts one. The mesher's mask uses the same layout, so the conversion is a double loop.
    net.minecraft.client.renderer.chunk.VisibilitySet set =
        new net.minecraft.client.renderer.chunk.VisibilitySet();
    for (int from = 0; from < 6; from++) {
      for (int to = 0; to < 6; to++) {
        if ((pairs & (1L << (from * 6 + to))) != 0) {
          set.setVisible(
              net.minecraft.core.Direction.values()[from],
              net.minecraft.core.Direction.values()[to]);
        }
      }
    }
    self.setVisibility(set);
  }

  /**
   * Hands a section rebuild to the scheduler instead of vanilla's queue.
   *
   * <p>Assumption: the entry point is {@code rebuildSectionAsync} on {@code RenderSection}. The
   * handler deliberately declares no target arguments: Mixin allows a handler to omit trailing
   * parameters, so a signature drift in this method costs a recompile of one line rather than a
   * rewritten hook.
   *
   * <p>If the scheduler refuses the task (queue full, gate off, in-flight cap reached) the hook
   * does not cancel, so vanilla compiles the section as it would have without the mod.
   */
  @Inject(method = "rebuildSectionAsync", at = @At("HEAD"), cancellable = true)
  private void bricklayer$redirectRebuild(CallbackInfo ci) {
    if (!CompatGate.active()) {
      return;
    }
    if (bricklayer$vanillaOnly) {
      return;
    }
    var minecraft = net.minecraft.client.Minecraft.getInstance();
    var level = minecraft.level;
    if (level == null) {
      return;
    }
    var camera = minecraft.gameRenderer.getMainCamera().getPosition();
    SectionPos pos = bricklayer$sectionPos();
    double score = BricklayerScheduler.score(pos, camera.x, camera.y, camera.z, true, false, 0L);
    if (Bricklayer.get().scheduler().submit(level, pos, score)) {
      ci.cancel();
    }
  }

  @Inject(method = "setDirty", at = @At("TAIL"))
  private void bricklayer$onSetDirty(boolean playerChanged, CallbackInfo ci) {
    bricklayer$bumpVersion();
    if (bricklayer$snapshot != null) {
      Bricklayer.get().snapshotPool().release(bricklayer$snapshot);
      bricklayer$snapshot = null;
    }
    if (playerChanged) {
      bricklayer$playerChanged = true;
    }
    bricklayer$vanillaOnly = false;
  }
}
