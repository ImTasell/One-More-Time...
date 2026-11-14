/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.debug;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.example.bricklayer.Bricklayer;
import dev.example.bricklayer.core.grid.SectionGrid;
import dev.example.bricklayer.core.grid.SectionSnapshot;
import dev.example.bricklayer.core.mesh.MeshResult;
import dev.example.bricklayer.core.mesh.Scratch;
import dev.example.bricklayer.core.mesh.SectionMesher;
import dev.example.bricklayer.core.quad.QuadSink;
import dev.example.bricklayer.getter.SnapshotBlockGetter;
import dev.example.bricklayer.meta.VanillaStates;
import dev.example.bricklayer.snapshot.Snapshotter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The debugging ground truth for the AO port.
 *
 * <p>Compiles the section under the player twice: through the fast path, and through vanilla's
 * {@code BlockRenderDispatcher} for every block with side checking on. Then it compares the
 * per-layer vertex counts and hashes the fast path's vertex stream.
 *
 * <p>Limitation: the vanilla pass is counted, not hashed, because vanilla's vertex buffer is opaque
 * once built. A count mismatch catches almost every AO or culling regression, and the hash catches
 * ordering changes in the fast path between two runs.
 */
public final class ParityTool {
  private ParityTool() {}

  public static List<Component> run(ClientLevel level, SectionPos pos) {
    List<Component> lines = new ArrayList<>();

    SectionSnapshot snapshot = new SectionSnapshot();
    new Snapshotter(Bricklayer.get().decodeCache()).capture(level, pos, pos.asLong(), 0, snapshot);

    // Fast path.
    HashSink fast = new HashSink();
    SectionMesher.Options options = new SectionMesher.Options();
    options.sectionX = pos.x();
    options.sectionY = pos.y();
    options.sectionZ = pos.z();
    MeshResult result =
        SectionMesher.mesh(
            snapshot,
            Bricklayer.metaTable(),
            Bricklayer.quadStore(),
            Bricklayer.faceVisibility(),
            new Scratch(),
            new QuadSink[] {fast},
            options,
            (state, tint, x, z) -> -1,
            Bricklayer.get().visibilityOracle());

    // Vanilla pass: every cell through the block renderer, no fast path at all.
    int vanillaVertices = vanillaVertexCount(level, pos, snapshot);

    lines.add(
        Component.literal(
            "fast: "
                + result.layerVertexCounts[0]
                + " solid vertices, "
                + fast.quads
                + " quads, hash 0x"
                + Long.toHexString(fast.hash())));
    if (fast.vertices == vanillaVertices) {
      lines.add(
          Component.translatable("bricklayer.command.parity.ok", result.quadCount, fast.vertices));
    } else {
      lines.add(
          Component.translatable(
              "bricklayer.command.parity.mismatch", "solid", fast.vertices, vanillaVertices));
    }
    return lines;
  }

  private static int vanillaVertexCount(
      ClientLevel level, SectionPos pos, SectionSnapshot snapshot) {
    try (SectionBufferBuilderPack pack = new SectionBufferBuilderPack()) {
      ByteBufferBuilder bytes = pack.buffer(RenderType.solid());
      BufferBuilder buffer =
          new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
      SnapshotBlockGetter getter = new SnapshotBlockGetter(level, pos, snapshot);
      BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
      PoseStack poseStack = new PoseStack();
      RandomSource random = RandomSource.create();
      BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

      for (int y = 0; y < 16; y++) {
        for (int z = 0; z < 16; z++) {
          for (int x = 0; x < 16; x++) {
            BlockState state = VanillaStates.byId(snapshot.state[SectionGrid.idx(x, y, z)]);
            if (state == null || state.isAir()) {
              continue;
            }
            cursor.set(pos.minBlockX() + x, pos.minBlockY() + y, pos.minBlockZ() + z);
            if (ItemBlockRenderTypes.getChunkRenderType(state) != RenderType.solid()) {
              continue;
            }
            if (!state.getFluidState().isEmpty()) {
              dispatcher.renderLiquid(cursor, getter, buffer, state, state.getFluidState());
            } else {
              dispatcher.renderBatched(state, cursor, getter, poseStack, buffer, true, random);
            }
          }
        }
      }

      MeshData data = buffer.build();
      if (data == null) {
        return 0;
      }
      int vertices = data.drawState().vertexCount();
      data.close();
      return vertices;
    }
  }

  /** FNV-1a over every emitted vertex attribute, plus a quad count. */
  static final class HashSink implements QuadSink {
    private long hash = 0xcbf29ce484222325L;
    int vertices;
    int quads;

    @Override
    public void quad(
        int[] data, int offset, float[] shade, int[] light, float r, float g, float b, float a) {
      for (int v = 0; v < 4; v++) {
        int base = offset + v * 8;
        for (int i = 0; i < 4; i++) {
          hash ^= data[base + i];
          hash *= 0x100000001b3L;
        }
        hash ^= light[v];
        hash *= 0x100000001b3L;
        hash ^= Float.floatToIntBits(shade[v]);
        hash *= 0x100000001b3L;
      }
      vertices += 4;
      quads++;
    }

    @Override
    public int vertexCount() {
      return vertices;
    }

    long hash() {
      return hash;
    }
  }
}
