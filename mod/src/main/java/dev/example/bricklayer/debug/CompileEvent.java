/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.debug;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/**
 * JFR event {@code bricklayer.Compile}.
 *
 * <p>Registered by JFR itself the first time the class initialises. Recording is a no-op when
 * flight recording is off, so the cost of leaving it in a release build is one boolean read per
 * section.
 */
@Name("bricklayer.Compile")
@Label("Chunk section compile")
@Category("Bricklayer")
public final class CompileEvent extends Event {
  @Label("Section key")
  public long sectionKey;

  @Label("Duration (ns)")
  public long nanos;

  @Label("Quads")
  public int quads;

  @Label("Deferred cells")
  public int deferredCells;

  public static void record(long sectionKey, long nanos, int quads, int deferredCells) {
    if (!jdk.jfr.FlightRecorder.isInitialized()) {
      return;
    }
    CompileEvent event = new CompileEvent();
    event.sectionKey = sectionKey;
    event.nanos = nanos;
    event.quads = quads;
    event.deferredCells = deferredCells;
    event.commit();
  }
}
