/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.light;

import dev.example.bricklayer.core.meta.CellFlags;
import dev.example.bricklayer.core.meta.MetaTable;

/**
 * Port of vanilla's {@code ModelBlockRenderer.AmbientOcclusionFace} plus {@code prepareQuadShape},
 * reduced to primitives and preallocated arrays.
 *
 * <p>The pieces that must match vanilla, and are checked by the {@code /bricklayer parity} tool:
 *
 * <ul>
 *   <li>four per-corner brightness and light values built from side/side/diagonal/centre samples,
 *   <li>a hidden corner reuses a side sample instead of the diagonal,
 *   <li>both sides closed means the diagonal cannot be seen, so it adds no darkness,
 *   <li>cubic quads snap corner values, partial quads interpolate them by vertex position,
 *   <li>flat lighting uses the face neighbour's light only and no AO,
 *   <li>the anisotropy flip rearranges which vertices carry which corner values.
 * </ul>
 *
 * <p>Assumption: vanilla ships the six corner tables explicitly; this port derives the same three
 * sample positions from the vertex's position on the face plane instead, which is equivalent for
 * standard cube quads and also correct for modded quads with unusual vertex order.
 *
 * <p>One instance per worker thread: not thread safe, and allocates nothing after construction.
 */
public final class AoCalculator {
  private final float[] cornerAo = new float[4];
  private final int[] cornerLight = new int[4];
  private final int[] sampleBlock = new int[4];
  private final int[] sampleSky = new int[4];

  /**
   * Fills {@code outAo} and {@code outLight} with one value per quad vertex, in quad vertex order.
   *
   * @param states snapshot state ids
   * @param light snapshot light bytes, {@code (sky << 4) | block}
   * @param meta meta table the snapshot was built against
   * @param centerGrid grid index of the cell being meshed
   * @param face face ordinal of the quad
   * @param quadData quad vertex data in vanilla {@code BakedQuad} layout
   * @param quadOff first int of the quad inside {@code quadData}
   * @param smooth whether smooth lighting is enabled
   * @param parity false in FAST mode: partial quads use cubic weights instead of interpolating
   */
  public void calculate(
      int[] states,
      byte[] light,
      MetaTable meta,
      int centerGrid,
      int face,
      int[] quadData,
      int quadOff,
      boolean smooth,
      boolean parity,
      float[] outAo,
      int[] outLight) {

    int neighbour = centerGrid + AoTables.NORMAL[face];
    if (!smooth) {
      int value = LightMath.vertexLight(light[neighbour]);
      for (int v = 0; v < 4; v++) {
        outAo[v] = 1.0f;
        outLight[v] = value;
      }
      return;
    }

    int uUnit = AoTables.U_UNIT[face];
    int vUnit = AoTables.V_UNIT[face];

    for (int corner = 0; corner < 4; corner++) {
      int su = (corner & 1) == 0 ? -1 : 1;
      int sv = (corner & 2) == 0 ? -1 : 1;
      int side1 = neighbour + su * uUnit;
      int side2 = neighbour + sv * vUnit;
      int diagonal = side1 + sv * vUnit;

      boolean o1 = occludes(meta, states[side1]);
      boolean o2 = occludes(meta, states[side2]);
      boolean o3 = occludes(meta, states[diagonal]);

      int occluders = (o1 ? 1 : 0) + (o2 ? 1 : 0) + (o3 ? 1 : 0);
      if (o1 && o2) {
        // Both sides are closed: the diagonal is not visible, so it adds no darkness.
        occluders = 2;
      }
      float ao = 1.0f - AoTables.AO_STEP * occluders;
      if (ao < AoTables.AO_MIN) {
        ao = AoTables.AO_MIN;
      }
      cornerAo[corner] = ao;

      // Light: the face neighbour always counts; a closed side reuses the centre sample and a
      // hidden diagonal reuses a side sample, as vanilla substitutes.
      int taken = 0;
      taken = sample(light[neighbour], taken);
      taken = sample(light[o1 ? neighbour : side1], taken);
      taken = sample(light[o2 ? neighbour : side2], taken);
      taken = sample(light[(o1 && o2) || o3 ? neighbour : diagonal], taken);

      int block = 0;
      int sky = 0;
      for (int k = 0; k < 4; k++) {
        block += sampleBlock[k];
        sky += sampleSky[k];
      }
      cornerLight[corner] = LightMath.pack(Math.round(block / 4.0f), Math.round(sky / 4.0f));
    }

    for (int v = 0; v < 4; v++) {
      int base = quadOff + v * 8;
      float px = Float.intBitsToFloat(quadData[base]);
      float py = Float.intBitsToFloat(quadData[base + 1]);
      float pz = Float.intBitsToFloat(quadData[base + 2]);
      float pu = component(px, py, pz, AoTables.U_AXIS[face]) / 16.0f;
      float pv = component(px, py, pz, AoTables.V_AXIS[face]) / 16.0f;
      boolean cubic = parity && isEdge(pu) && isEdge(pv);
      if (cubic) {
        int corner = (pu >= 0.5f ? 1 : 0) | (pv >= 0.5f ? 2 : 0);
        outAo[v] = cornerAo[corner];
        outLight[v] = cornerLight[corner];
      } else {
        // Partial quads interpolate the four corner values by the vertex position on the face.
        float u0 = clamp01(pu);
        float v0 = clamp01(pv);
        outAo[v] = lerp(lerp(cornerAo[0], cornerAo[1], u0), lerp(cornerAo[2], cornerAo[3], u0), v0);
        float blockTop = lerp(LightMath.block(cornerLight[0]), LightMath.block(cornerLight[1]), u0);
        float blockBottom =
            lerp(LightMath.block(cornerLight[2]), LightMath.block(cornerLight[3]), u0);
        float skyTop = lerp(LightMath.sky(cornerLight[0]), LightMath.sky(cornerLight[1]), u0);
        float skyBottom = lerp(LightMath.sky(cornerLight[2]), LightMath.sky(cornerLight[3]), u0);
        outLight[v] =
            LightMath.packRounded(lerp(blockTop, blockBottom, v0), lerp(skyTop, skyBottom, v0));
      }
    }

    if (parity) {
      // Vanilla flips the quad when the AO split would cut across the quad's triangles instead of
      // following them. Swapping the two off-diagonal vertices achieves that without moving
      // geometry.
      if (outAo[1] + outAo[2] > outAo[0] + outAo[3] + 1.0E-4f) {
        float ao = outAo[1];
        outAo[1] = outAo[2];
        outAo[2] = ao;
        int value = outLight[1];
        outLight[1] = outLight[2];
        outLight[2] = value;
      }
    }
  }

  /** True when the state shades its neighbours in vanilla's AO pass. */
  public static boolean occludes(MetaTable meta, int stateId) {
    return (meta.flags(stateId) & CellFlags.AO_CLEAR) == 0;
  }

  private int sample(byte cellLight, int index) {
    int packed = LightMath.vertexLight(cellLight);
    sampleBlock[index] = LightMath.block(packed);
    sampleSky[index] = LightMath.sky(packed);
    return index + 1;
  }

  private static float component(float x, float y, float z, int axis) {
    return axis == 0 ? x : axis == 1 ? y : z;
  }

  private static boolean isEdge(float value) {
    return value <= 1.0E-4f || value >= 0.9999f;
  }

  private static float clamp01(float value) {
    return value < 0.0f ? 0.0f : value > 1.0f ? 1.0f : value;
  }

  private static float lerp(float a, float b, float t) {
    return a + (b - a) * t;
  }
}
