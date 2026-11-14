/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.mixin;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Disables every render mixin when another mod owns the same code path.
 *
 * <p>This runs during mixin application, before any mod code executes, so it must not read the
 * config or touch {@code Bricklayer}: it consults the loading mod list only. The user-facing gate
 * ({@code CompatGate}) covers the rest.
 */
public final class BricklayerMixinPlugin implements IMixinConfigPlugin {
  private static final Set<String> CONFLICTS = Set.of("sodium", "embeddium", "rubidium");

  private boolean disabled;

  @Override
  public void onLoad(String mixinPackage) {
    LoadingModList mods = LoadingModList.get();
    if (mods == null) {
      return;
    }
    for (String id : CONFLICTS) {
      if (mods.getModFileById(id) != null) {
        disabled = true;
        break;
      }
    }
  }

  @Override
  public String getRefMapperConfig() {
    return null;
  }

  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    return !disabled;
  }

  @Override
  public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

  @Override
  public List<String> getMixins() {
    return null;
  }

  @Override
  public void preApply(
      String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

  @Override
  public void postApply(
      String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
