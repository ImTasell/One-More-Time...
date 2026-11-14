/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.pipeline;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.example.bricklayer.Bricklayer;
import dev.example.bricklayer.config.BricklayerConfig;
import dev.example.bricklayer.core.grid.SectionGrid;
import dev.example.bricklayer.core.grid.SectionSnapshot;
import dev.example.bricklayer.core.mesh.MeshResult;
import dev.example.bricklayer.core.mesh.Scratch;
import dev.example.bricklayer.core.mesh.SectionMesher;
import dev.example.bricklayer.core.mesh.TintResolver;
import dev.example.bricklayer.core.quad.QuadSink;
import dev.example.bricklayer.getter.SnapshotBlockGetter;
import dev.example.bricklayer.meta.VanillaStates;
import dev.example.bricklayer.upload.CompiledMesh;
import dev.example.bricklayer.upload.SectionUploader;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Glue between the pure mesher and Minecraft: fills the snapshot's light, runs the pipeline, then
 * replays every deferred cell through vanilla's own block renderer into the same layer buffers.
 *
 * <p>Because the fast path and the deferred path write into the same builders, a section that is
 * half static terrain and half fluids needs no merge step and no special case.
 *
 * <p>Runs on a worker thread; the only world reads are through {@link SnapshotBlockGetter} plus the
 * light engine's layer listeners, exactly as vanilla's section compiler does.
 */
public final class MeshPipeline {
  private final PoseStack poseStack = new PoseStack();
  private final RandomSource random = RandomSource.create();

  /**
   * Meshes one section.
   *
   * @param owner the render section the result belongs to, carried through to the upload stage
   * @return the compiled mesh, or null when the task must be dropped (resource generation changed)
   */
  @Nullable
  public CompiledMesh mesh(
      ClientLevel level,
      SectionPos pos,
      SectionSnapshot snapshot,
      SectionBufferBuilderPack buffers,
      int resourceGeneration,
      @Nullable Object owner) {

    BricklayerConfig.Values config = BricklayerConfig.get();
    SnapshotBlockGetter getter = new SnapshotBlockGetter(level, pos, snapshot);
    fillLight(level, pos, snapshot);

    SectionMesher.Options options = new SectionMesher.Options();
    options.smoothLight = Minecraft.getInstance().options.ambientOcclusion().get();
    // Vanilla always applies the per-face directional shade; the ambient-occlusion option only
    // controls the smooth-light pass above.
    options.diffuseLight = true;
    options.parity = config.aoMode() == BricklayerConfig.AoMode.PARITY;
    options.sectionX = pos.x();
    options.sectionY = pos.y();
    options.sectionZ = pos.z();

    Pass pass = Pass.open(buffers, config.rawSink(), level, snapshot, options);
    MeshResult result = pass.result;

    if (pass.raw && result.deferredCells.length > 0) {
      // The raw path cannot host vanilla's deferred renderers, which need a VertexConsumer. Re-run
      // the section through the safe path rather than mixing two incompatible writers.
      pass.discard();
      pass = Pass.open(buffers, false, level, snapshot, options);
      result = pass.result;
    }

    if (result.resourceGeneration != Bricklayer.resourceGeneration()) {
      pass.discard();
      return null;
    }

    renderDeferred(snapshot, getter, pass.builders, result);
    MeshData[] meshData = pass.finish();

    List<RenderType> nonEmpty = new ArrayList<>(3);
    for (int layer = 0; layer < SectionUploader.LAYERS.length; layer++) {
      MeshData data = meshData[layer];
      if (data != null && data.drawState().vertexCount() > 0) {
        nonEmpty.add(SectionUploader.LAYERS[layer]);
      }
    }

    return CompiledMesh.of(
        snapshot.sectionKey,
        snapshot.version,
        resourceGeneration,
        result.layerVertexCounts,
        result.visibilityPairs,
        nonEmpty,
        collectBlockEntities(level, pos, snapshot),
        meshData,
        result.quadCount,
        result.deferredCells.length,
        result.nanos,
        owner);
  }

  /** One meshing attempt plus the builders or raw buffers it wrote into. */
  private static final class Pass {
    final boolean raw;
    final BufferBuilder[] builders;
    final ByteBufferBuilder[] rawBuffers;
    final int[] rawVertexCounts;
    final MeshResult result;

    private Pass(
        boolean raw,
        BufferBuilder[] builders,
        ByteBufferBuilder[] rawBuffers,
        int[] rawVertexCounts,
        MeshResult result) {
      this.raw = raw;
      this.builders = builders;
      this.rawBuffers = rawBuffers;
      this.rawVertexCounts = rawVertexCounts;
      this.result = result;
    }

    static Pass open(
        SectionBufferBuilderPack pack,
        boolean raw,
        ClientLevel level,
        SectionSnapshot snapshot,
        SectionMesher.Options options) {

      BufferBuilder[] builders = new BufferBuilder[3];
      ByteBufferBuilder[] rawBuffers = new ByteBufferBuilder[3];
      QuadSink[] sinks = new QuadSink[3];

      for (int layer = 0; layer < 3; layer++) {
        if (raw) {
          rawBuffers[layer] = new ByteBufferBuilder(64 * 1024);
          sinks[layer] = new RawQuadSink(rawBuffers[layer]);
        } else {
          builders[layer] =
              new BufferBuilder(
                  pack.buffer(SectionUploader.LAYERS[layer]),
                  VertexFormat.Mode.QUADS,
                  DefaultVertexFormat.BLOCK);
          sinks[layer] = new BufferQuadSink(builders[layer]);
        }
      }

      MeshResult result =
          SectionMesher.mesh(
              snapshot,
              Bricklayer.metaTable(),
              Bricklayer.quadStore(),
              Bricklayer.faceVisibility(),
              Scratch.get(),
              sinks,
              options,
              tintResolver(level),
              Bricklayer.get().visibilityOracle());

      int[] counts = new int[3];
      for (int layer = 0; layer < 3; layer++) {
        counts[layer] = sinks[layer].vertexCount();
      }
      return new Pass(raw, builders, rawBuffers, counts, result);
    }

    /** Releases whatever was written when the pass is thrown away. */
    void discard() {
      for (int layer = 0; layer < 3; layer++) {
        if (rawBuffers[layer] != null) {
          rawBuffers[layer].close();
        }
      }
    }

    MeshData[] finish() {
      MeshData[] data = new MeshData[3];
      for (int layer = 0; layer < 3; layer++) {
        if (raw) {
          data[layer] = rawMesh(rawBuffers[layer], rawVertexCounts[layer]);
          rawBuffers[layer].close();
        } else {
          data[layer] = builders[layer].build();
        }
      }
      return data;
    }

    /**
     * Wraps a raw vertex buffer in a MeshData; the raw sink always writes quads with short indices.
     */
    private static MeshData rawMesh(ByteBufferBuilder buffer, int vertexCount) {
      MeshData.DrawState state =
          new MeshData.DrawState(
              DefaultVertexFormat.BLOCK,
              vertexCount,
              0,
              VertexFormat.Mode.QUADS,
              VertexFormat.IndexType.SHORT);
      return new MeshData(buffer.build(), state);
    }
  }

  /** Reads light through the light engine, the same cross-thread read vanilla's compiler does. */
  private static void fillLight(ClientLevel level, SectionPos pos, SectionSnapshot snapshot) {
    var sky = level.getLightEngine().getLayerListener(LightLayer.SKY);
    var block = level.getLightEngine().getLayerListener(LightLayer.BLOCK);
    BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    for (int y = -1; y <= 16; y++) {
      for (int z = -1; z <= 16; z++) {
        for (int x = -1; x <= 16; x++) {
          cursor.set(pos.minBlockX() + x, pos.minBlockY() + y, pos.minBlockZ() + z);
          int skyValue = sky.getLightValue(cursor);
          int blockValue = block.getLightValue(cursor);
          snapshot.light[SectionGrid.idx(x, y, z)] = (byte) ((skyValue << 4) | blockValue);
        }
      }
    }
  }

  private void renderDeferred(
      SectionSnapshot snapshot,
      SnapshotBlockGetter getter,
      @Nullable BufferBuilder[] builders,
      MeshResult result) {
    if (result.deferredCells.length == 0 || builders == null || builders[0] == null) {
      return;
    }
    BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
    for (int cell : result.deferredCells) {
      int localX = SectionGrid.cellX(cell);
      int localY = SectionGrid.cellY(cell);
      int localZ = SectionGrid.cellZ(cell);
      BlockPos worldPos = getter.cursor(localX, localY, localZ);
      BlockState state = getter.getBlockState(worldPos);
      if (state.isAir()) {
        continue;
      }
      BufferBuilder builder = builders[layerIndex(state)];
      if (!state.getFluidState().isEmpty()) {
        dispatcher.renderLiquid(worldPos, getter, builder, state, state.getFluidState());
      } else if (state.getRenderShape() == RenderShape.MODEL
          || state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) {
        dispatcher.renderBatched(state, worldPos, getter, poseStack, builder, true, random);
      }
    }
  }

  /** Renderable block entities of the section, resolved on the thread doing the meshing. */
  private static List<BlockEntity> collectBlockEntities(
      ClientLevel level, SectionPos pos, SectionSnapshot snapshot) {
    if (snapshot.beCount == 0) {
      return List.of();
    }
    List<BlockEntity> entities = new ArrayList<>(snapshot.beCount);
    BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
    for (int i = 0; i < snapshot.beCount; i++) {
      int cell = snapshot.bePositions[i];
      cursor.set(
          pos.minBlockX() + SectionGrid.cellX(cell),
          pos.minBlockY() + SectionGrid.cellY(cell),
          pos.minBlockZ() + SectionGrid.cellZ(cell));
      BlockEntity entity = level.getBlockEntity(cursor);
      if (entity != null && dispatcher.getRenderer(entity) != null) {
        entities.add(entity);
      }
    }
    return entities;
  }

  private static int layerIndex(BlockState state) {
    RenderType type = ItemBlockRenderTypes.getChunkRenderType(state);
    if (type == RenderType.solid()) {
      return 0;
    }
    if (type == RenderType.cutoutMipped()) {
      return 1;
    }
    return 2;
  }

  /** Biome and block tinting through vanilla's own colour providers. */
  private static TintResolver tintResolver(ClientLevel level) {
    BlockColors colors = Minecraft.getInstance().getBlockColors();
    return (stateId, tintIndex, localX, localZ) -> {
      BlockState state = VanillaStates.byId(stateId);
      if (state == null) {
        return -1;
      }
      BlockPos pos = new BlockPos(localX, 0, localZ);
      return colors.getColor(state, level, pos, tintIndex);
    };
  }
}
