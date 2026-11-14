/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.mixin;

import dev.example.bricklayer.Bricklayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drops the decode-cache entry for the section a successful block change touched.
 *
 * <p>Without this, re-meshing an edited area would reuse stale state ids for the edited section
 * while still reusing decoded neighbours, which is exactly the trade the cache exists for.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
  @Inject(method = "setBlockState", at = @At("RETURN"))
  private void bricklayer$invalidateDecodeCache(
      BlockPos pos, BlockState state, boolean isMoving, CallbackInfoReturnable<BlockState> cir) {
    if (cir.getReturnValue() == null) {
      // Null means nothing changed (same state, or the chunk refused the write).
      return;
    }
    LevelChunk chunk = (LevelChunk) (Object) this;
    LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(pos.getY()));
    Bricklayer.get().decodeCache().invalidate(section);
  }
}
