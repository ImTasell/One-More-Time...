/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.model;

import java.util.List;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.WeightedBakedModel;
import net.minecraft.util.random.WeightedEntry;

/**
 * Reads the variant list out of a weighted model.
 *
 * <p>Vanilla keeps the list private; {@code accesstransformer.cfg} opens {@code
 * WeightedBakedModel.list}. The entries are public {@code WeightedEntry.Wrapper}s, so no other
 * field needs opening.
 */
final class WeightedModelAccess {
  private WeightedModelAccess() {}

  static List<WeightedEntry.Wrapper<BakedModel>> entries(WeightedBakedModel weighted) {
    return weighted.list;
  }

  static BakedModel model(WeightedEntry.Wrapper<BakedModel> entry) {
    return entry.data();
  }

  static int weight(WeightedEntry.Wrapper<BakedModel> entry) {
    return entry.getWeight().asInt();
  }
}
