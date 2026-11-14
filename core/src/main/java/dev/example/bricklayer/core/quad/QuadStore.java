/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.quad;

import java.util.Arrays;

/**
 * Every static model's quads, flattened.
 *
 * <p>Quads are stored as the raw vanilla vertex ints copied straight out of {@code
 * BakedQuad#getVertices()}: 4 vertices of 8 ints (position x/y/z as float bits, colour, u, v as
 * float bits, light, normal). Metadata lives in parallel byte arrays, and a range table answers
 * "where are the quads of model M for cull-face F".
 *
 * <p>The range table is {@code faceStart[model * 7 + slot]}, where {@code slot} is the cull-face
 * ordinal and slot 6 means "no cull face". Quads are sorted into that layout at build time, so the
 * end of a range is always the next entry and no second table is needed.
 *
 * <p>Published through a volatile reference after a resource reload; never mutated afterwards.
 */
public final class QuadStore {
  /** Ints per vertex in a baked quad. */
  public static final int INTS_PER_VERTEX = 8;

  /** Ints per quad. */
  public static final int INTS_PER_QUAD = 4 * INTS_PER_VERTEX;

  /** Range slot for quads without a cull face. */
  public static final int NO_CULL = 6;

  /** Range slots per model. */
  public static final int SLOTS_PER_MODEL = 7;

  /** Quad metadata bit: the quad takes part in directional shading. */
  public static final int QUAD_SHADE = 1;

  private final int[] data;
  private final byte[] tintIndex;
  private final byte[] face;
  private final byte[] cullFace;
  private final byte[] layer;
  private final byte[] flags;
  private final int[] faceStart;
  private final int modelCount;
  private final int quadCount;
  private final int[] variantBase;
  private final int[] variantCount;
  private final int[] variantTotal;
  private final int[] variantCumBase;
  private final int[] variantCum;

  private QuadStore(Builder b) {
    this.data = b.data;
    this.tintIndex = b.tintIndex;
    this.face = b.face;
    this.cullFace = b.cullFace;
    this.layer = b.layers;
    this.flags = b.qflags;
    this.faceStart = b.faceStart;
    this.modelCount = b.modelCount;
    this.quadCount = b.quadCount;
    this.variantBase = b.variantBase;
    this.variantCount = b.variantCount;
    this.variantTotal = b.variantTotal;
    this.variantCumBase = b.variantCumBase;
    this.variantCum = b.variantCum;
  }

  public static QuadStore empty() {
    return new Builder().build();
  }

  public int modelCount() {
    return modelCount;
  }

  public int quadCount() {
    return quadCount;
  }

  public int[] data() {
    return data;
  }

  public byte[] tintIndex() {
    return tintIndex;
  }

  public byte[] face() {
    return face;
  }

  public byte[] cullFace() {
    return cullFace;
  }

  public byte[] layer() {
    return layer;
  }

  public byte[] flags() {
    return flags;
  }

  /** First quad index of a model's cull-face slot. */
  public int rangeStart(int model, int slot) {
    return faceStart[model * SLOTS_PER_MODEL + slot];
  }

  /** One past the last quad index of a model's cull-face slot. */
  public int rangeEnd(int model, int slot) {
    return faceStart[model * SLOTS_PER_MODEL + slot + 1];
  }

  /** First variant sub-model of a weighted model (the model itself when unweighted). */
  public int variantBase(int model) {
    return variantBase[model];
  }

  public int variantCount(int model) {
    return variantCount[model];
  }

  /**
   * Resolves the sub-model to use for a world position.
   *
   * @param model base model index from the meta table
   * @param worldSeed {@link VariantSelector#positionSeed} of the cell
   */
  public int variantModel(int model, long worldSeed) {
    int count = variantCount[model];
    if (count <= 1) {
      return model;
    }
    int index =
        VariantSelector.select(
            worldSeed, variantCum, variantCumBase[model], count, variantTotal[model]);
    return variantBase[model] + index;
  }

  /** Builder used once per resource reload by the glue layer. */
  public static final class Builder {
    private int[] data = new int[INTS_PER_QUAD * 1024];
    private byte[] tintIndex = new byte[1024];
    private byte[] face = new byte[1024];
    private byte[] cullFace = new byte[1024];
    private byte[] layers = new byte[1024];
    private byte[] qflags = new byte[1024];
    private int[] key = new int[1024];
    private int quadCount;

    private int modelCount;
    private int[] variantBase = new int[16];
    private int[] variantCount = new int[16];
    private int[] variantTotal = new int[16];
    private int[] variantCumBase = new int[16];
    private int[] variantCum = new int[64];
    private int variantCumSize;

    private int[] faceStart = new int[1];

    /** Registers a new model and returns its index. */
    public int beginModel() {
      if (modelCount == variantBase.length) {
        int grown = modelCount * 2;
        variantBase = Arrays.copyOf(variantBase, grown);
        variantCount = Arrays.copyOf(variantCount, grown);
        variantTotal = Arrays.copyOf(variantTotal, grown);
        variantCumBase = Arrays.copyOf(variantCumBase, grown);
      }
      variantBase[modelCount] = modelCount;
      variantCount[modelCount] = 1;
      variantTotal[modelCount] = 0;
      variantCumBase[modelCount] = 0;
      return modelCount++;
    }

    /**
     * Marks a model as a weighted model whose variants are the contiguous sub-models starting at
     * {@code base}.
     */
    public void variants(int model, int base, int count, int totalWeight, int[] weights) {
      variantBase[model] = base;
      variantCount[model] = count;
      variantTotal[model] = totalWeight;
      variantCumBase[model] = variantCumSize;
      int running = 0;
      for (int i = 0; i < count; i++) {
        running += weights[i];
        if (variantCumSize == variantCum.length) {
          variantCum = Arrays.copyOf(variantCum, variantCumSize * 2);
        }
        variantCum[variantCumSize++] = running;
      }
    }

    /**
     * Adds one baked quad.
     *
     * @param model owning model index
     * @param quadFace the direction the quad faces, 0..5
     * @param cull the cull face, or -1 for a quad that is always emitted
     * @param layer index of the render layer this quad belongs to
     * @param tint the tint index, or -1
     * @param shaded whether directional shading applies
     * @param source source array in {@code BakedQuad#getVertices()} layout
     * @param sourceOffset first int of the quad inside {@code source}
     */
    public void addQuad(
        int model,
        int quadFace,
        int cull,
        int layer,
        int tint,
        boolean shaded,
        int[] source,
        int sourceOffset) {
      if (quadCount == tintIndex.length) {
        int grown = quadCount * 2;
        tintIndex = Arrays.copyOf(tintIndex, grown);
        face = Arrays.copyOf(face, grown);
        cullFace = Arrays.copyOf(cullFace, grown);
        layers = Arrays.copyOf(layers, grown);
        qflags = Arrays.copyOf(qflags, grown);
        key = Arrays.copyOf(key, grown);
      }
      if (data.length < (quadCount + 1) * INTS_PER_QUAD) {
        data = Arrays.copyOf(data, data.length * 2);
      }
      System.arraycopy(source, sourceOffset, data, quadCount * INTS_PER_QUAD, INTS_PER_QUAD);
      int slot = cull < 0 ? NO_CULL : cull;
      tintIndex[quadCount] = (byte) tint;
      face[quadCount] = (byte) quadFace;
      cullFace[quadCount] = (byte) cull;
      this.layers[quadCount] = (byte) layer;
      qflags[quadCount] = (byte) (shaded ? QUAD_SHADE : 0);
      key[quadCount] = model * SLOTS_PER_MODEL + slot;
      quadCount++;
    }

    public QuadStore build() {
      int slots = modelCount * SLOTS_PER_MODEL;
      faceStart = new int[slots + 1];
      for (int i = 0; i < quadCount; i++) {
        faceStart[key[i] + 1]++;
      }
      for (int i = 1; i <= slots; i++) {
        faceStart[i] += faceStart[i - 1];
      }

      int[] dataOut = new int[Math.max(1, quadCount) * INTS_PER_QUAD];
      byte[] tintOut = new byte[Math.max(1, quadCount)];
      byte[] faceOut = new byte[Math.max(1, quadCount)];
      byte[] cullOut = new byte[Math.max(1, quadCount)];
      byte[] layerOut = new byte[Math.max(1, quadCount)];
      byte[] flagsOut = new byte[Math.max(1, quadCount)];
      int[] cursor = Arrays.copyOf(faceStart, slots);
      for (int i = 0; i < quadCount; i++) {
        int dest = cursor[key[i]]++;
        System.arraycopy(data, i * INTS_PER_QUAD, dataOut, dest * INTS_PER_QUAD, INTS_PER_QUAD);
        tintOut[dest] = tintIndex[i];
        faceOut[dest] = face[i];
        cullOut[dest] = cullFace[i];
        layerOut[dest] = this.layers[i];
        flagsOut[dest] = qflags[i];
      }

      this.data = dataOut;
      this.tintIndex = tintOut;
      this.face = faceOut;
      this.cullFace = cullOut;
      this.layers = layerOut;
      this.qflags = flagsOut;
      this.variantCum = Arrays.copyOf(variantCum, Math.max(1, variantCumSize));
      return new QuadStore(this);
    }
  }
}
