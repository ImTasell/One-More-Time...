/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.mixin;

import dev.example.bricklayer.Bricklayer;
import dev.example.bricklayer.compat.CompatGate;
import dev.example.bricklayer.core.grid.SectionSnapshot;
import dev.example.bricklayer.sched.BricklayerScheduler;
import dev.example.bricklayer.upload.CompiledMesh;
import dev.example.bricklayer.upload.SectionUploader;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carries the snapshot and dirty version on the section itself, and sends both rebuild routes
 * through {@link BricklayerScheduler}.
 *
 * <p>Two entry points, matching vanilla 1.21.1 exactly:
 *
 * <ul>
 *   <li>{@code rebuildSectionAsync(SectionRenderDispatcher, RenderRegionCache)} for background
 *       rebuilds,
 *   <li>{@code compileSync(RenderRegionCache)} for the sections that must be ready this frame.
 * </ul>
 *
 * <p>If the scheduler refuses a task (queue full, gate off, in-flight cap) the hook does not
 * cancel, so vanilla compiles the section exactly as it would without the mod.
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
  public void bricklayer$clearPlayerChanged() {
    bricklayer$playerChanged = false;
  }

  @Override
  public boolean bricklayer$vanillaOnly() {
    return bricklayer$vanillaOnly;
  }

  @Override
  public void bricklayer$setVanillaOnly(boolean vanillaOnly) {
    bricklayer$vanillaOnly = vanillaOnly;
  }

  @Unique
  private SectionRenderDispatcher.RenderSection bricklayer$self() {
    return (SectionRenderDispatcher.RenderSection) (Object) this;
  }

  @Inject(method = "rebuildSectionAsync", at = @At("HEAD"), cancellable = true)
  private void bricklayer$rebuildSectionAsync(
      SectionRenderDispatcher dispatcher, RenderRegionCache cache, CallbackInfo ci) {
    if (!CompatGate.active() || bricklayer$vanillaOnly) {
      return;
    }
    ClientLevel level = Minecraft.getInstance().level;
    if (level == null) {
      return;
    }
    SectionRenderDispatcher.RenderSection self = bricklayer$self();
    SectionPos pos = SectionPos.of(self.getOrigin());
    if (Bricklayer.get().scheduler().submit(level, pos, BricklayerScheduler.score(self), self)) {
      ci.cancel();
    }
  }

  @Inject(method = "compileSync", at = @At("HEAD"), cancellable = true)
  private void bricklayer$compileSync(RenderRegionCache cache, CallbackInfo ci) {
    if (!CompatGate.active() || bricklayer$vanillaOnly) {
      return;
    }
    ClientLevel level = Minecraft.getInstance().level;
    if (level == null) {
      return;
    }
    SectionRenderDispatcher.RenderSection self = bricklayer$self();
    SectionPos pos = SectionPos.of(self.getOrigin());
    try (SectionBufferBuilderPack buffers = new SectionBufferBuilderPack()) {
      CompiledMesh mesh = Bricklayer.get().scheduler().submitImmediate(level, pos, buffers, self);
      if (mesh != null) {
        SectionUploader.upload(mesh);
        ci.cancel();
      }
    }
  }

  /**
   * Every dirty notification invalidates the cached snapshot, and a change that came from the
   * player flags the section for synchronous compilation.
   */
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
