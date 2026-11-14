/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.upload;

import dev.example.bricklayer.Bricklayer;
import dev.example.bricklayer.compat.CompatGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Applies a finished mesh to vanilla's renderer, on the render thread only.
 *
 * <p>Uploads happen here and nowhere else: {@code VertexBuffer} state belongs to the render thread.
 * The path is entirely vanilla's own machinery — {@code
 * SectionRenderDispatcher#uploadSectionLayer(MeshData, VertexBuffer)} for the buffers and a {@code
 * CompiledSection} for the result — so translucent resorting, occlusion and the debug counters keep
 * working.
 *
 * <p>A section whose dirty version moved on while the task was in flight is discarded instead of
 * uploaded, so a fast edit followed by an older result can never win the race.
 */
public final class SectionUploader {
  private SectionUploader() {}

  /** The render types the mesher writes, in layer order. */
  public static final RenderType[] LAYERS = {
    RenderType.solid(), RenderType.cutoutMipped(), RenderType.cutout()
  };

  /**
   * Uploads one compiled mesh.
   *
   * @return true when the section was accepted and uploaded
   */
  public static boolean upload(CompiledMesh mesh) {
    if (!CompatGate.active()
        || mesh.resourceGeneration != Bricklayer.resourceGeneration()
        || !(mesh.owner instanceof SectionRenderDispatcher.RenderSection section)) {
      mesh.close();
      return false;
    }
    if (!Bricklayer.get().scheduler().versions().matches(mesh.sectionKey, mesh.version)) {
      Bricklayer.get().uploadGovernor().discard(mesh);
      return false;
    }

    Minecraft minecraft = Minecraft.getInstance();
    SectionRenderDispatcher dispatcher =
        minecraft.levelRenderer == null
            ? null
            : minecraft.levelRenderer.getSectionRenderDispatcher();
    if (dispatcher == null) {
      mesh.close();
      return false;
    }

    SectionRenderDispatcher.CompiledSection result = new SectionRenderDispatcher.CompiledSection();
    result.visibilitySet = toVisibilitySet(mesh.visibilityPairs);
    result.renderableBlockEntities.addAll(mesh.blockEntities);
    // The mesher never emits the translucent layer; vanilla keeps that sort state.
    result.transparencyState = null;

    for (int layer = 0; layer < LAYERS.length; layer++) {
      var data = mesh.layer(layer);
      if (data == null) {
        continue;
      }
      if (data.drawState().vertexCount() == 0) {
        data.close();
        continue;
      }
      result.hasBlocks.add(LAYERS[layer]);
      dispatcher.uploadSectionLayer(data, section.getBuffer(LAYERS[layer]));
    }

    section.setCompiled(result);
    return true;
  }

  /** Converts the core's {@code from * 6 + to} mask into vanilla's {@code from + to * 6} bitset. */
  private static VisibilitySet toVisibilitySet(long pairs) {
    VisibilitySet set = new VisibilitySet();
    Direction[] directions = Direction.values();
    for (int from = 0; from < 6; from++) {
      for (int to = 0; to < 6; to++) {
        if ((pairs & (1L << (from * 6 + to))) != 0) {
          set.set(directions[from], directions[to], true);
        }
      }
    }
    return set;
  }

  /** Renderable block entities of a section, resolved from the level on the render thread. */
  public static void collectBlockEntities(CompiledMesh mesh, java.util.List<BlockEntity> out) {
    out.addAll(mesh.blockEntities);
  }
}
