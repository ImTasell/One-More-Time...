/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.debug;

import dev.example.bricklayer.Bricklayer;
import dev.example.bricklayer.compat.CompatGate;
import dev.example.bricklayer.config.BricklayerConfig;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

/** Adds three lines to the F3 screen when the debug overlay is enabled. */
public final class DebugOverlay {
  private DebugOverlay() {}

  public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
    if (!BricklayerConfig.get().debugOverlay()) {
      return;
    }
    var scheduler = Bricklayer.get().scheduler();
    var governor = Bricklayer.get().uploadGovernor();
    event
        .getLeft()
        .add(
            String.format(
                "Bricklayer: %s  workers=%d  queued=%d  inFlight=%d",
                CompatGate.mode(),
                scheduler.workerCount(),
                scheduler.queueDepth(),
                scheduler.inFlight()));
    event
        .getLeft()
        .add(
            String.format(
                "  compile=%.3fms  deferred=%.1f%%  uploads=%d  queuedUploads=%d",
                scheduler.meanCompileMs(),
                scheduler.deferredRatio() * 100.0,
                governor.uploadsTotal(),
                governor.queued()));
    event
        .getLeft()
        .add(
            String.format(
                "  visCache=%d  decodeCache=%d  frame=%.2fms",
                Bricklayer.faceVisibility().size(),
                Bricklayer.get().decodeCache().size(),
                governor.lastFrameMs()));
  }
}
