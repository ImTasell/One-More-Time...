/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.upload;

import com.mojang.blaze3d.vertex.MeshData;
import javax.annotation.Nullable;

/**
 * One compiled section on its way from a worker to the render thread.
 *
 * <p>The worker filled the {@link MeshData} buffers; the render thread is the only one allowed to
 * upload them into a {@code VertexBuffer}, and it does so only if the section's dirty version still
 * matches.
 */
public final class CompiledSection {
  public final long sectionKey;
  public final int version;
  public final int resourceGeneration;
  public final int[] layerVertexCounts;
  public final long visibilityPairs;
  public final int[] bePositions;
  public final int quadCount;
  public final int deferredCellCount;
  public final long compileNanos;
  private final MeshData[] meshData;

  private CompiledSection(
      long sectionKey,
      int version,
      int resourceGeneration,
      int[] layerVertexCounts,
      long visibilityPairs,
      int[] bePositions,
      MeshData[] meshData,
      int quadCount,
      int deferredCellCount,
      long compileNanos) {
    this.sectionKey = sectionKey;
    this.version = version;
    this.resourceGeneration = resourceGeneration;
    this.layerVertexCounts = layerVertexCounts;
    this.visibilityPairs = visibilityPairs;
    this.bePositions = bePositions;
    this.meshData = meshData;
    this.quadCount = quadCount;
    this.deferredCellCount = deferredCellCount;
    this.compileNanos = compileNanos;
  }

  public static CompiledSection of(
      long sectionKey,
      int version,
      int resourceGeneration,
      int[] layerVertexCounts,
      long visibilityPairs,
      int[] bePositions,
      MeshData[] meshData,
      int quadCount,
      int deferredCellCount,
      long compileNanos) {
    return new CompiledSection(
        sectionKey,
        version,
        resourceGeneration,
        layerVertexCounts,
        visibilityPairs,
        bePositions,
        meshData,
        quadCount,
        deferredCellCount,
        compileNanos);
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
