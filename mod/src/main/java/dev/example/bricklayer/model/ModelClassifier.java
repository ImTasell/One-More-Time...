/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.model;

import java.util.List;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.MultiPartBakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.client.resources.model.WeightedBakedModel;

/**
 * Static-model check.
 *
 * <p>A model is cacheable when it is a vanilla {@code SimpleBakedModel}, a multipart of those, or a
 * weighted wrapper of those, and its quads do not depend on {@code ModelData}. Anything else
 * (custom {@code BakedModel} implementations, NeoForge models that read model data, block-entity
 * driven models) is deferred to vanilla.
 *
 * <p>Multipart models are accepted because their conditions depend only on the state, and the store
 * is built once per state id: {@code getQuads} is evaluated with that exact state, so the selection
 * is already resolved when the quads are copied.
 */
public final class ModelClassifier {
  private ModelClassifier() {}

  public static boolean isStatic(BakedModel model) {
    if (model instanceof SimpleBakedModel) {
      return true;
    }
    if (model instanceof MultiPartBakedModel) {
      return true;
    }
    if (model instanceof WeightedBakedModel weighted) {
      List<WeightedBakedModel.WeightedModel> entries = WeightedModelAccess.entries(weighted);
      if (entries.isEmpty()) {
        return false;
      }
      for (WeightedBakedModel.WeightedModel entry : entries) {
        if (!isStatic(WeightedModelAccess.model(entry))) {
          return false;
        }
      }
      return true;
    }
    return false;
  }
}
