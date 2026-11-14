/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.model;

import java.util.List;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.WeightedBakedModel;

/**
 * Reads the variant list out of a weighted model.
 *
 * <p>Vanilla keeps the list private; {@code accesstransformer.cfg} opens {@code
 * WeightedBakedModel.list}, {@code WeightedBakedModel.totalWeight} and the inner {@code
 * WeightedModel} fields. This class is the only place that touches them, so a mapping change breaks
 * one file rather than the whole build.
 */
final class WeightedModelAccess {
  private WeightedModelAccess() {}

  static List<WeightedBakedModel.WeightedModel> entries(WeightedBakedModel weighted) {
    return weighted.list;
  }

  static BakedModel model(WeightedBakedModel.WeightedModel entry) {
    return entry.model;
  }

  static int weight(WeightedBakedModel.WeightedModel entry) {
    return entry.weight;
  }
}
