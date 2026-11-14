/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.upload;

import com.mojang.blaze3d.vertex.MeshData;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * One compiled section on its way from a worker to the render thread.
 *
 * <p>Named {@code CompiledMesh} to stay unambiguous next to vanilla's {@code
 * SectionRenderDispatcher.CompiledSection}, which the upload stage builds from this.
 *
 * <p>The worker filled the {@link MeshData} buffers; the render thread is the only one allowed to
 * upload them into a {@code VertexBuffer}, and it does so only if the section's dirty version still
 * matches.
 */
public final class CompiledMesh {
  public final long sectionKey;
  public final int version;
  public final int resourceGeneration;
  public final int[] layerVertexCounts;
  public final long visibilityPairs;
  public final int quadCount;
  public final int deferredCellCount;
  public final long compileNanos;

  /** The render section the task belongs to, so upload needs no global lookup. */
  @Nullable public final Object owner;

  /** Render types that actually received geometry, in mesher layer order. */
  public final List<RenderType> nonEmptyLayers;

  /** Renderable block entities of the section. */
  public final List<BlockEntity> blockEntities;

  private final MeshData[] meshData;

  private CompiledMesh(
      long sectionKey,
      int version,
      int resourceGeneration,
      int[] layerVertexCounts,
      long visibilityPairs,
      List<RenderType> nonEmptyLayers,
      List<BlockEntity> blockEntities,
      MeshData[] meshData,
      int quadCount,
      int deferredCellCount,
      long compileNanos,
      @Nullable Object owner) {
    this.sectionKey = sectionKey;
    this.version = version;
    this.resourceGeneration = resourceGeneration;
    this.layerVertexCounts = layerVertexCounts;
    this.visibilityPairs = visibilityPairs;
    this.nonEmptyLayers = nonEmptyLayers;
    this.blockEntities = blockEntities;
    this.meshData = meshData;
    this.quadCount = quadCount;
    this.deferredCellCount = deferredCellCount;
    this.compileNanos = compileNanos;
    this.owner = owner;
  }

  public static CompiledMesh of(
      long sectionKey,
      int version,
      int resourceGeneration,
      int[] layerVertexCounts,
      long visibilityPairs,
      List<RenderType> nonEmptyLayers,
      List<BlockEntity> blockEntities,
      MeshData[] meshData,
      int quadCount,
      int deferredCellCount,
      long compileNanos,
      @Nullable Object owner) {
    return new CompiledMesh(
        sectionKey,
        version,
        resourceGeneration,
        layerVertexCounts,
        visibilityPairs,
        nonEmptyLayers,
        blockEntities,
        meshData,
        quadCount,
        deferredCellCount,
        compileNanos,
        owner);
  }

  /** Mesh data for a layer, or null when that layer is empty. */
  @Nullable
  public MeshData layer(int index) {
    return index >= 0 && index < meshData.length ? meshData[index] : null;
  }

  public int layerCount() {
    return meshData.length;
  }

  /** Releases the buffers that were never uploaded. */
  public void close() {
    for (MeshData data : meshData) {
      if (data != null) {
        data.close();
      }
    }
  }
}
