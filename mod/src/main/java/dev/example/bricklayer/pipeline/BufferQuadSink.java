/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.pipeline;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.example.bricklayer.core.quad.QuadSink;
import dev.example.bricklayer.core.quad.QuadStore;

/**
 * Writes the mesher's quads into a vanilla {@link BufferBuilder} as BLOCK vertices.
 *
 * <p>This is the {@code SafeSink} of the specification: all writes go through the builder's public
 * vertex API, so no access transformer is needed. {@code RawSink} (writing into the builder's
 * {@code ByteBufferBuilder} reservation directly) is a possible optimisation behind the {@code
 * rawSink} config key, but the safe path is the default and the reference implementation.
 */
public final class BufferQuadSink implements QuadSink {
  private final BufferBuilder builder;
  private int vertices;

  public BufferQuadSink(BufferBuilder builder) {
    this.builder = builder;
  }

  @Override
  public void quad(
      int[] data, int offset, float[] shade, int[] light, float r, float g, float b, float a) {
    for (int v = 0; v < 4; v++) {
      int base = offset + v * QuadStore.INTS_PER_VERTEX;
      float x = Float.intBitsToFloat(data[base]);
      float y = Float.intBitsToFloat(data[base + 1]);
      float z = Float.intBitsToFloat(data[base + 2]);
      float u = Float.intBitsToFloat(data[base + 4]);
      float vTex = Float.intBitsToFloat(data[base + 5]);
      int packedNormal = data[base + 7];

      int color = data[base + 3];
      float alpha = ((color >>> 24) & 0xFF) / 255.0f * a;
      float red = ((color >> 16) & 0xFF) / 255.0f * r * shade[v];
      float green = ((color >> 8) & 0xFF) / 255.0f * g * shade[v];
      float blue = (color & 0xFF) / 255.0f * b * shade[v];
      int packedColor =
          (clampByte(alpha) << 24)
              | (clampByte(red) << 16)
              | (clampByte(green) << 8)
              | clampByte(blue);

      // Vanilla packs the normal as three signed bytes in the low 24 bits of the vertex int.
      float nx = (byte) (packedNormal & 0xFF) / 127.0f;
      float ny = (byte) ((packedNormal >> 8) & 0xFF) / 127.0f;
      float nz = (byte) ((packedNormal >> 16) & 0xFF) / 127.0f;

      builder
          .addVertex(x, y, z)
          .setColor(packedColor)
          .setUv(u, vTex)
          .setLight(light[v])
          .setNormal(nx, ny, nz);
      vertices++;
    }
  }

  @Override
  public int vertexCount() {
    return vertices;
  }

  @Override
  public void reset() {
    vertices = 0;
  }

  private static int clampByte(float value) {
    int scaled = Math.round(value * 255.0f);
    return scaled < 0 ? 0 : Math.min(scaled, 255);
  }

  /** The vertex format every sink writes; kept next to the writer so the two cannot drift. */
  public static com.mojang.blaze3d.vertex.VertexFormat format() {
    return DefaultVertexFormat.BLOCK;
  }
}
