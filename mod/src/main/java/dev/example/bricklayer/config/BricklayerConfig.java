/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * CLIENT config, plus an immutable snapshot of the resolved values.
 *
 * <p>Hot paths read {@link #get()} once per task rather than touching the config system: a config
 * value can be edited at runtime and the snapshot is replaced on the next read, so no worker ever
 * blocks on config IO.
 */
public final class BricklayerConfig {
  public static final ModConfigSpec SPEC;

  private static final ModConfigSpec.BooleanValue ENABLED;
  private static final ModConfigSpec.IntValue WORKER_THREADS;
  private static final ModConfigSpec.DoubleValue UPLOAD_BUDGET_MS;
  private static final ModConfigSpec.IntValue MAX_IN_FLIGHT;
  private static final ModConfigSpec.EnumValue<AoMode> AO_MODE;
  private static final ModConfigSpec.BooleanValue RAW_SINK;
  private static final ModConfigSpec.BooleanValue DEBUG_OVERLAY;
  private static final ModConfigSpec.IntValue BREAKER_THRESHOLD;
  private static final ModConfigSpec.BooleanValue VERBOSE;

  private static volatile Values cached;

  static {
    ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

    builder
        .comment("Master switch. Defaults to on; see README for the incompatibility list.")
        .push("general");
    ENABLED = builder.comment("Enable Bricklayer meshing.").define("enabled", true);
    WORKER_THREADS =
        builder
            .comment("Mesh worker threads. 0 = auto (max(1, cpus - 2)).")
            .defineInRange("workerThreads", 0, 0, 16);
    AO_MODE =
        builder
            .comment(
                "PARITY runs the full ambient-occlusion port; FAST skips partial-face "
                    + "interpolation.")
            .defineEnum("aoMode", AoMode.PARITY);
    builder.pop();

    builder.comment("Scheduling and uploads").push("scheduling");
    UPLOAD_BUDGET_MS =
        builder
            .comment("Milliseconds of mesh upload allowed per frame.")
            .defineInRange("uploadBudgetMs", 2.0, 0.25, 16.0);
    MAX_IN_FLIGHT =
        builder
            .comment("Maximum concurrently meshed sections. 0 = workers x 2.")
            .defineInRange("maxInFlight", 0, 0, 64);
    builder.pop();

    builder.comment("Diagnostics").push("debug");
    RAW_SINK =
        builder
            .comment(
                "Write vertices straight into the builder buffer (needs the ATs; off by "
                    + "default).")
            .define("rawSink", false);
    DEBUG_OVERLAY =
        builder.comment("Add Bricklayer lines to the F3 screen.").define("debugOverlay", true);
    BREAKER_THRESHOLD =
        builder
            .comment(
                "Failures within 60 seconds before Bricklayer disables itself for the session.")
            .defineInRange("breakerThreshold", 8, 1, 1000);
    VERBOSE = builder.comment("Verbose logging.").define("verbose", false);
    builder.pop();

    SPEC = builder.build();
  }

  private BricklayerConfig() {}

  /** Immutable view of the config, recomputed at most once per change. */
  public record Values(
      boolean enabled,
      int workerThreads,
      double uploadBudgetMs,
      int maxInFlight,
      AoMode aoMode,
      boolean rawSink,
      boolean debugOverlay,
      int breakerThreshold,
      boolean verbose) {}

  public enum AoMode {
    PARITY,
    FAST
  }

  public static Values get() {
    Values values = cached;
    if (values == null) {
      values = read();
      cached = values;
    }
    return values;
  }

  /** Called by the config system whenever a value changes. */
  public static void invalidate() {
    cached = null;
  }

  private static Values read() {
    int workers = WORKER_THREADS.get();
    if (workers <= 0) {
      workers = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
    }
    int maxInFlight = MAX_IN_FLIGHT.get();
    if (maxInFlight <= 0) {
      maxInFlight = workers * 2;
    }
    return new Values(
        ENABLED.get(),
        workers,
        UPLOAD_BUDGET_MS.get(),
        maxInFlight,
        AO_MODE.get(),
        RAW_SINK.get(),
        DEBUG_OVERLAY.get(),
        BREAKER_THRESHOLD.get(),
        VERBOSE.get());
  }
}
