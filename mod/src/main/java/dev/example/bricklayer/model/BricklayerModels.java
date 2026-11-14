/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.model;

import dev.example.bricklayer.core.meta.CellFlags;
import dev.example.bricklayer.core.meta.MetaTable;
import dev.example.bricklayer.core.quad.QuadStore;
import dev.example.bricklayer.meta.BlockStateAccess;
import dev.example.bricklayer.meta.StateClassifier;
import dev.example.bricklayer.meta.VanillaStates;
import java.util.List;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.WeightedBakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds the immutable tables the mesher consumes, once per model bake, on the main thread.
 *
 * <p>Every registered block state is classified, its baked model is checked against the static
 * rules, and its quads are copied into one flat {@link QuadStore}. Because the store is keyed by
 * state id, multipart conditions and state-dependent model data are already resolved here; only
 * weighted multi-variant models need a per-position draw at mesh time, and every variant is cached
 * as its own sub-model for that.
 */
public final class BricklayerModels {
  private BricklayerModels() {}

  /** Freshly built tables, published together so no reader sees a partial pair. */
  public record Result(MetaTable meta, QuadStore quads) {}

  public static Result rebuild(
      ModelManager models, net.minecraft.client.color.block.BlockColors blockColors) {
    int stateCount = VanillaStates.count();
    int[] flags = new int[stateCount];
    float[] shade = new float[stateCount];
    int[] modelIds = new int[stateCount];
    QuadStore.Builder builder = new QuadStore.Builder();
    RandomSource random = RandomSource.create(42L);

    for (int stateId = 0; stateId < stateCount; stateId++) {
      BlockState state = VanillaStates.byId(stateId);
      modelIds[stateId] = -1;
      if (state == null) {
        flags[stateId] = CellFlags.FORCE_VANILLA;
        shade[stateId] = 1.0f;
        continue;
      }

      int stateFlags = StateClassifier.classify(state);
      shade[stateId] =
          BlockStateAccess.shadeBrightness(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO);

      if ((stateFlags & (CellFlags.AIR | CellFlags.FORCE_VANILLA)) != 0) {
        flags[stateId] = stateFlags;
        continue;
      }

      BakedModel model = models.getModel(BlockModelShaper.stateToModelLocation(state));
      if (model == null || !ModelClassifier.isStatic(model)) {
        flags[stateId] = stateFlags | CellFlags.FORCE_VANILLA;
        continue;
      }

      if (tintVariesWithHeight(blockColors, state)) {
        stateFlags |= CellFlags.TINT_POSITIONAL;
      }

      int layer = layerOf(ItemBlockRenderTypes.getChunkRenderType(state));
      boolean[] hasTint = new boolean[1];
      int firstModel = extract(builder, model, state, layer, random, hasTint);
      if (firstModel < 0) {
        flags[stateId] = stateFlags | CellFlags.FORCE_VANILLA;
        continue;
      }
      if (hasTint[0]) {
        stateFlags |= CellFlags.HAS_TINT;
      }
      flags[stateId] = stateFlags | CellFlags.STATIC_MODEL;
      modelIds[stateId] = firstModel;
    }

    return new Result(new MetaTable(flags, shade, modelIds), builder.build());
  }

  /**
   * Copies one state's quads into the store and returns the first model index, or -1 when the state
   * produced no cacheable geometry at all.
   */
  private static int extract(
      QuadStore.Builder builder,
      BakedModel model,
      BlockState state,
      int layer,
      RandomSource random,
      boolean[] hasTint) {

    List<BakedModel> variants;
    int[] weights;
    if (model instanceof WeightedBakedModel weighted) {
      List<WeightedBakedModel.WeightedModel> entries = WeightedModelAccess.entries(weighted);
      if (entries.isEmpty()) {
        return -1;
      }
      variants = new java.util.ArrayList<>(entries.size());
      weights = new int[entries.size()];
      for (int i = 0; i < entries.size(); i++) {
        variants.add(WeightedModelAccess.model(entries.get(i)));
        weights[i] = WeightedModelAccess.weight(entries.get(i));
      }
    } else {
      variants = List.of(model);
      weights = new int[] {1};
    }

    int first = -1;
    int total = 0;
    for (int i = 0; i < variants.size(); i++) {
      int modelIndex = builder.beginModel();
      if (i == 0) {
        first = modelIndex;
      }
      total += weights[i];
      copyQuads(builder, modelIndex, variants.get(i), state, layer, random, hasTint);
    }
    if (variants.size() > 1) {
      builder.variants(first, first, variants.size(), total, weights);
    }
    return first;
  }

  private static void copyQuads(
      QuadStore.Builder builder,
      int modelIndex,
      BakedModel model,
      BlockState state,
      int layer,
      RandomSource random,
      boolean[] hasTint) {
    for (int face = 0; face <= 6; face++) {
      Direction direction = face < 6 ? Direction.values()[face] : null;
      List<net.minecraft.client.renderer.block.model.BakedQuad> quads =
          model.getQuads(state, direction, random);
      for (net.minecraft.client.renderer.block.model.BakedQuad quad : quads) {
        if (quad.getTintIndex() >= 0) {
          hasTint[0] = true;
        }
        // Assumption: BakedQuad#getDirection doubles as the cull face, as it does throughout
        // vanilla's model pipeline.
        int quadFace = quad.getDirection().ordinal();
        builder.addQuad(
            modelIndex,
            quadFace,
            quadFace,
            layer,
            quad.getTintIndex(),
            quad.isShaded(),
            quad.getVertices(),
            0);
      }
    }
  }

  private static int layerOf(RenderType type) {
    if (type == RenderType.solid()) {
      return 0;
    }
    if (type == RenderType.cutoutMipped()) {
      return 1;
    }
    return 2;
  }

  /**
   * True when the block's colour provider depends on the world position's height. Vanilla providers
   * never do, but a modded one might, and the mesher memoises tints per column.
   */
  private static boolean tintVariesWithHeight(
      net.minecraft.client.color.block.BlockColors blockColors, BlockState state) {
    try {
      int low = blockColors.getColor(state, TintProbe.INSTANCE, new BlockPos(0, 0, 0), 0);
      int high = blockColors.getColor(state, TintProbe.INSTANCE, new BlockPos(0, 255, 0), 0);
      return low != high;
    } catch (RuntimeException ignored) {
      // A provider that insists on a live level: treat the tint as positional, which is the safe
      // choice because it bypasses the memo.
      return true;
    }
  }
}
