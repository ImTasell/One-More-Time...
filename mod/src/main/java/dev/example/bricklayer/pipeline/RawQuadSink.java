/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.pipeline;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import dev.example.bricklayer.core.quad.QuadSink;
import dev.example.bricklayer.core.quad.QuadStore;
import org.lwjgl.system.MemoryUtil;

/**
 * Writes BLOCK vertices straight into a {@link ByteBufferBuilder} reservation.
 *
 * <p>Off by default ({@code rawSink=false}); {@link BufferQuadSink} is the reference path. This
 * exists because the safe path goes through the builder's vertex chain, which costs a handful of
 * virtual calls per vertex, and the raw path removes them.
 *
 * <p>The vertex layout is {@code DefaultVertexFormat.BLOCK}, 32 bytes:
 *
 * <pre>
 *  0  float x, y, z            (12 bytes)
 * 12  byte  r, g, b, a         (4)
 * 16  float u, v               (8)
 * 24  short u2, v2 (light)     (4)
 * 28  byte  nx, ny, nz, pad    (4)
 * </pre>
 *
 * <p>Assumption: 1.21.1's {@code ByteBufferBuilder#reserve(int)} returns a raw address that {@code
 * MemoryUtil} can write to. That is the API the access transformers in this project target; verify
 * it against the decompiled sources before enabling the option.
 */
public final class RawQuadSink implements QuadSink {
  private static final int VERTEX_BYTES = 32;

  private final ByteBufferBuilder buffer;
  private int vertices;

  public RawQuadSink(ByteBufferBuilder buffer) {
    this.buffer = buffer;
  }

  @Override
  public void quad(
      int[] data, int offset, float[] shade, int[] light, float r, float g, float b, float a) {
    for (int v = 0; v < 4; v++) {
      int base = offset + v * QuadStore.INTS_PER_VERTEX;
      long address = buffer.reserve(VERTEX_BYTES);

      MemoryUtil.memPutFloat(address, Float.intBitsToFloat(data[base]));
      MemoryUtil.memPutFloat(address + 4, Float.intBitsToFloat(data[base + 1]));
      MemoryUtil.memPutFloat(address + 8, Float.intBitsToFloat(data[base + 2]));

      int color = data[base + 3];
      float alpha = ((color >>> 24) & 0xFF) / 255.0f * a;
      float red = ((color >> 16) & 0xFF) / 255.0f * r * shade[v];
      float green = ((color >> 8) & 0xFF) / 255.0f * g * shade[v];
      float blue = (color & 0xFF) / 255.0f * b * shade[v];
      MemoryUtil.memPutByte(address + 12, clampByte(red));
      MemoryUtil.memPutByte(address + 13, clampByte(green));
      MemoryUtil.memPutByte(address + 14, clampByte(blue));
      MemoryUtil.memPutByte(address + 15, clampByte(alpha));

      MemoryUtil.memPutFloat(address + 16, Float.intBitsToFloat(data[base + 4]));
      MemoryUtil.memPutFloat(address + 20, Float.intBitsToFloat(data[base + 5]));

      int packedLight = light[v];
      MemoryUtil.memPutShort(address + 24, (short) (packedLight & 0xFFFF));
      MemoryUtil.memPutShort(address + 26, (short) ((packedLight >> 16) & 0xFFFF));

      int normal = data[base + 7];
      MemoryUtil.memPutByte(address + 28, (byte) (normal & 0xFF));
      MemoryUtil.memPutByte(address + 29, (byte) ((normal >> 8) & 0xFF));
      MemoryUtil.memPutByte(address + 30, (byte) ((normal >> 16) & 0xFF));
      MemoryUtil.memPutByte(address + 31, (byte) 0);

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

  private static byte clampByte(float value) {
    int scaled = Math.round(value * 255.0f);
    return (byte) (scaled < 0 ? 0 : Math.min(scaled, 255));
  }
}
