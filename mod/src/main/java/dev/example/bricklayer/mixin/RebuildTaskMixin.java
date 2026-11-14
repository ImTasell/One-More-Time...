/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.mixin;

import com.mojang.blaze3d.vertex.MeshData;
import dev.example.bricklayer.Bricklayer;
import dev.example.bricklayer.compat.CompatGate;
import dev.example.bricklayer.core.grid.SectionSnapshot;
import dev.example.bricklayer.pipeline.MeshPipeline;
import dev.example.bricklayer.upload.CompiledSection;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderSectionRegion;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Runs the Bricklayer pipeline in place of vanilla's terrain loop.
 *
 * <p>Verification checklist for this file (spec section 5.9): the compile method's exact name and
 * parameter list, and the shape of {@code SectionRenderDispatcher$CompileResults} in 1.21.1. The
 * current assumption is:
 *
 * <pre>
 * CompileResults compile(float x, float y, float z, SectionBufferBuilderPack buffers, RenderChunkRegion region)
 * </pre>
 *
 * <p>On any failure the hook returns without cancelling, so vanilla compiles the section and the
 * player sees correct geometry. {@code OutOfMemoryError} is never caught.
 */
@Mixin(SectionRenderDispatcher.RenderSection.RebuildTask.class)
public abstract class RebuildTaskMixin {
  /**
   * Captures the snapshot on the main thread, at the moment vanilla builds the task, so the compile
   * hook never reads the world off-thread. The section's {@code this$0} capture is assigned by the
   * time the constructor returns.
   */
  @Inject(method = "<init>", at = @At("RETURN"))
  private void bricklayer$captureSnapshot(CallbackInfo ci) {
    if (!CompatGate.active()) {
      return;
    }
    SectionRenderDispatcher.RenderSection section = bricklayer$section();
    if (section == null) {
      return;
    }
    BricklayerSection access = (BricklayerSection) section;
    if (access.bricklayer$snapshot() != null) {
      return;
    }
    var level = net.minecraft.client.Minecraft.getInstance().level;
    if (level == null) {
      return;
    }
    access.bricklayer$setSnapshot(
        Bricklayer.get().scheduler().capture(level, access.bricklayer$sectionPos()));
  }

  @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
  private void bricklayer$compile(
      float x,
      float y,
      float z,
      SectionBufferBuilderPack buffers,
      RenderSectionRegion region,
      CallbackInfoReturnable<SectionRenderDispatcher.CompileResults> cir) {

    if (!CompatGate.active()) {
      return;
    }
    SectionRenderDispatcher.RenderSection section = bricklayer$section();
    if (section == null || ((BricklayerSection) section).bricklayer$vanillaOnly()) {
      return;
    }

    SectionSnapshot snapshot = ((BricklayerSection) section).bricklayer$snapshot();
    if (snapshot == null) {
      return;
    }

    SectionPos pos = SectionPos.of(snapshot.sectionKey);
    CompiledSection compiled = null;
    try {
      MeshPipeline pipeline = new MeshPipeline();
      compiled =
          pipeline.mesh(region.getLevel(), pos, snapshot, buffers, Bricklayer.resourceGeneration());
      if (compiled == null) {
        return;
      }
      cir.setReturnValue(bricklayer$toCompileResults(compiled));
    } catch (RuntimeException | LinkageError failure) {
      // Mark the section vanilla-only for this edit cycle, log once per exception class, and let
      // vanilla do the work.
      CompatGate.recordFailure("rebuild " + snapshot.sectionKey, failure);
      ((BricklayerSection) section).bricklayer$setVanillaOnly(true);
      if (compiled != null) {
        compiled.close();
      }
    }
  }

  /** The owning RenderSection; the rebuild task keeps it in a field in 1.21.1. */
  @Nullable
  private SectionRenderDispatcher.RenderSection bricklayer$section() {
    // Assumption: RenderSection.RebuildTask has a field of type RenderSection named "this$0" (an
    // inner class capture). If it is named differently, add an accessor mixin here.
    try {
      var field = this.getClass().getDeclaredField("this$0");
      field.setAccessible(true);
      return (SectionRenderDispatcher.RenderSection) field.get(this);
    } catch (ReflectiveOperationException | RuntimeException failure) {
      return null;
    }
  }

  /**
   * Builds vanilla's result object from the compiled section.
   *
   * <p>Assumption: {@code CompileResults} is constructible from the block-entity lists, a {@code
   * VisibilitySet} and a {@code MeshData.SortState} (the AT opens the constructor). The translucent
   * sort state is produced by vanilla's own {@code MeshData} path, so resorting behaves exactly as
   * before.
   */
  private SectionRenderDispatcher.CompileResults bricklayer$toCompileResults(
      CompiledSection compiled) {
    MeshData sortSource = compiled.layer(2);
    MeshData.SortState sortState = sortSource == null ? null : sortSource.sortState();
    List<BlockEntity> blockEntities = Collections.emptyList();
    return new SectionRenderDispatcher.CompileResults(
        sortState, blockEntities, compiled.bePositions);
  }
}
