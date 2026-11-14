/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.quad;

/**
 * Receives finished quads, one call per quad, with the per-vertex light and brightness already
 * resolved.
 *
 * <p>Positions, UVs, normals and the baked vertex colour come straight from the quad store, so a
 * sink only has to multiply the colour by the tint and the per-vertex shade and write a vanilla
 * BLOCK vertex. One sink exists per render layer; the glue layer wraps each layer's {@code
 * BufferBuilder}.
 */
public interface QuadSink {
  /**
   * Emits one quad.
   *
   * @param data quad vertex data, 4 vertices of 8 ints in vanilla {@code BakedQuad#getVertices()}
   *     layout (position, colour, UV, light, normal)
   * @param offset first int of the quad inside {@code data}
   * @param shade per-vertex brightness multiplier to fold into the colour (AO and directional
   *     shade)
   * @param light per-vertex packed vanilla light value, {@code block << 4 | sky << 20}
   * @param r tint red multiplier
   * @param g tint green multiplier
   * @param b tint blue multiplier
   * @param a tint alpha multiplier
   */
  void quad(int[] data, int offset, float[] shade, int[] light, float r, float g, float b, float a);

  /** Vertices written since the last {@link #reset()}. */
  int vertexCount();

  /** Clears the vertex count for the next section. */
  default void reset() {}
}
