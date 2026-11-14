/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.meta;

import dev.example.bricklayer.core.meta.CellFlags;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turns one {@link BlockState} into a {@link CellFlags} word.
 *
 * <p>The classification is deliberately conservative: anything that is not a plain static model on
 * a solid, cutout or cutout-mipped layer is flagged {@code FORCE_VANILLA} and rendered by vanilla's
 * {@code BlockRenderDispatcher} in the same task. That keeps fluids, block entities, translucent
 * geometry and exotic modded models pixel-identical to vanilla.
 */
public final class StateClassifier {
  private static final BlockPos PROBE = BlockPos.ZERO;

  private StateClassifier() {}

  public static int classify(BlockState state) {
    int flags = 0;

    if (state.isAir()) {
      return CellFlags.AIR | CellFlags.AO_CLEAR;
    }

    if (BlockStateAccess.isVisOpaque(state)) {
      flags |= CellFlags.VIS_OPAQUE;
    }
    if (BlockStateAccess.isOpaqueFull(state, EmptyBlockGetter.INSTANCE, PROBE)) {
      flags |= CellFlags.OPAQUE_FULL;
    }
    if (BlockStateAccess.isAoClear(state)) {
      flags |= CellFlags.AO_CLEAR;
    }
    if (state.getRenderShape() == RenderShape.MODEL) {
      flags |= CellFlags.USES_AO;
    }
    if (BlockStateAccess.isEmissive(state, EmptyBlockGetter.INSTANCE, PROBE)) {
      flags |= CellFlags.EMISSIVE;
    }
    if (!state.getFluidState().isEmpty()) {
      flags |= CellFlags.FLUID | CellFlags.FORCE_VANILLA;
    }
    if (state.hasBlockEntity()) {
      flags |= CellFlags.BE_RENDERER | CellFlags.FORCE_VANILLA;
    }
    if (state.is(Blocks.TRIPWIRE)) {
      // Tripwire changes its model per surrounding state far more often than a static check can
      // prove; vanilla's own renderer handles it.
      flags |= CellFlags.FORCE_VANILLA;
    }

    RenderType layer = ItemBlockRenderTypes.getChunkRenderType(state);
    if (layer == RenderType.solid()) {
      flags |= CellFlags.LAYER_SOLID;
    } else if (layer == RenderType.cutoutMipped()) {
      flags |= CellFlags.LAYER_CUTOUT_MIPPED;
    } else if (layer == RenderType.cutout()) {
      flags |= CellFlags.LAYER_CUTOUT;
    } else {
      // Translucent and anything modded: the translucent sort-state machinery stays vanilla.
      flags |= CellFlags.FORCE_VANILLA;
    }

    if (state.getRenderShape() != RenderShape.MODEL) {
      flags |= CellFlags.FORCE_VANILLA;
    }

    return flags;
  }
}
