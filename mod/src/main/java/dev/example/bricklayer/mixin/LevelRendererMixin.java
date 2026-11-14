/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.mixin;

import dev.example.bricklayer.Bricklayer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clears everything that belongs to the previous world when the renderer is reset.
 *
 * <p>Queued tasks reference snapshots of chunks that no longer exist, the decode cache is keyed by
 * section instances that are gone, and pooled buffers may be large; all three go.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
  @Inject(method = "allChanged", at = @At("HEAD"))
  private void bricklayer$onAllChanged(CallbackInfo ci) {
    Bricklayer.get().scheduler().reset();
    Bricklayer.get().decodeCache().clear();
    Bricklayer.get().bufferPool().clear();
    Bricklayer.get().uploadGovernor().reset();
  }
}
