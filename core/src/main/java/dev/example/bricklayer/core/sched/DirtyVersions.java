/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.sched;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-section dirty counter.
 *
 * <p>A task carries the version its snapshot was taken at; a finished result is only accepted when
 * the version still matches. Otherwise the section changed while the task was in flight, the result
 * is dropped and the section requeued.
 */
public final class DirtyVersions {
  private final ConcurrentHashMap<Long, AtomicInteger> versions = new ConcurrentHashMap<>();

  /** Bumps and returns the new version for a section. */
  public int bump(long sectionKey) {
    return versions.computeIfAbsent(sectionKey, key -> new AtomicInteger()).incrementAndGet();
  }

  /** Current version, 0 for a section that has never been dirtied. */
  public int current(long sectionKey) {
    AtomicInteger version = versions.get(sectionKey);
    return version == null ? 0 : version.get();
  }

  /** True when {@code version} is still the current one. */
  public boolean matches(long sectionKey, int version) {
    return current(sectionKey) == version;
  }

  public void remove(long sectionKey) {
    versions.remove(sectionKey);
  }

  public int size() {
    return versions.size();
  }

  public void clear() {
    versions.clear();
  }
}
