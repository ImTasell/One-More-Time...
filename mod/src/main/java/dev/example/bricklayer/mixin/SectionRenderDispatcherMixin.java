/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.mixin;

import dev.example.bricklayer.Bricklayer;
import dev.example.bricklayer.compat.CompatGate;
import net.minecraft.client.renderer.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Owns the per-frame slot that used to compile sections on the main thread.
 *
 * <p>Section rebuilds are handed to {@code BricklayerScheduler} from {@code RenderSectionMixin};
 * this mixin spends the same per-frame slot uploading finished meshes, which is the only place a
 * {@code VertexBuffer} may be touched.
 *
 * <p>Assumption: 1.21.1 exposes {@code SectionRenderDispatcher#runTask()}. If it moved, the hook
 * name here is the only change; {@code injectors.defaultRequire = 1} makes a moved hook fail loudly
 * at load rather than silently doing nothing.
 *
 * <p>Drawing, frustum culling, translucent resorting and the occlusion graph stay vanilla: only the
 * compile step and its scheduling are replaced.
 */
@Mixin(SectionRenderDispatcher.class)
public abstract class SectionRenderDispatcherMixin {

  @Inject(method = "runTask", at = @At("HEAD"), cancellable = true)
  private void bricklayer$runTask(CallbackInfo ci) {
    if (!CompatGate.active()) {
      return;
    }
    // Vanilla's per-frame task pump would compile sections on the main thread; the workers own that
    // work now. This hook uses the same per-frame slot to upload finished meshes within the frame's
    // millisecond budget, which is the only place uploads are allowed to happen.
    Bricklayer.get()
        .uploadGovernor()
        .drain(
            compiled -> {
              if (!dev.example.bricklayer.upload.SectionUploader.upload(compiled)) {
                compiled.close();
              }
            });
    ci.cancel();
  }
}
