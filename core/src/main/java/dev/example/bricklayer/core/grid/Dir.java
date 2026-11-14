/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.grid;

/**
 * The six face directions of a section cell, in vanilla {@code Direction} ordinal order.
 *
 * <p>{@link #offset()} is the delta in the flattened 18x18x18 snapshot grid for one step in this
 * direction, so a neighbour lookup is a single array index addition in the mesher's hot loop.
 */
public enum Dir {
  DOWN(0, 0, -1, 0),
  UP(1, 0, 1, 0),
  NORTH(2, 0, 0, -1),
  SOUTH(3, 0, 0, 1),
  WEST(4, -1, 0, 0),
  EAST(5, 1, 0, 0);

  /** All directions, indexed by ordinal. */
  public static final Dir[] VALUES = values();

  private final int ordinal;
  private final int dx;
  private final int dy;
  private final int dz;
  private final int offset;

  Dir(int ordinal, int dx, int dy, int dz) {
    this.ordinal = ordinal;
    this.dx = dx;
    this.dy = dy;
    this.dz = dz;
    this.offset = SectionGrid.step(dx, dy, dz);
  }

  /** Vanilla ordinal, 0 (down) through 5 (east). */
  public int id() {
    return ordinal;
  }

  public int dx() {
    return dx;
  }

  public int dy() {
    return dy;
  }

  public int dz() {
    return dz;
  }

  /** Grid-index delta for one step along this direction. */
  public int offset() {
    return offset;
  }

  public Dir opposite() {
    return switch (this) {
      case DOWN -> UP;
      case UP -> DOWN;
      case NORTH -> SOUTH;
      case SOUTH -> NORTH;
      case WEST -> EAST;
      case EAST -> WEST;
    };
  }

  /** True when this direction points along the Y axis. */
  public boolean vertical() {
    return dy != 0;
  }

  public static Dir byId(int id) {
    return VALUES[id];
  }
}
