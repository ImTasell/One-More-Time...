/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.mixin;

import dev.example.bricklayer.Bricklayer;
import dev.example.bricklayer.compat.CompatGate;
import dev.example.bricklayer.upload.SectionUploader;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Owns the per-frame upload slot.
 *
 * <p>Section compilation is diverted from {@code RenderSectionMixin}; what is left for the
 * dispatcher is flushing results. {@code uploadAllPendingUploads} runs on the render thread every
 * frame, which is exactly where a {@code VertexBuffer} may be touched, so the governor drains there
 * under its millisecond budget. Vanilla then uploads its own pending work as usual.
 */
@Mixin(SectionRenderDispatcher.class)
public abstract class SectionRenderDispatcherMixin {
  @Inject(method = "uploadAllPendingUploads", at = @At("HEAD"))
  private void bricklayer$drainUploads(CallbackInfo ci) {
    if (!CompatGate.active()) {
      return;
    }
    Bricklayer.get()
        .uploadGovernor()
        .drain(
            mesh -> {
              if (!SectionUploader.upload(mesh)) {
                mesh.close();
              }
            });
  }
}
