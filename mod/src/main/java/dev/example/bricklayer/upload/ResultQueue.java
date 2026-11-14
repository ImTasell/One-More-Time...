/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.upload;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Lock-free handoff of compiled meshes from mesh workers to the render thread. */
public final class ResultQueue {
  private final Queue<CompiledMesh> queue = new ConcurrentLinkedQueue<>();
  private volatile long dropped;

  public void add(CompiledMesh mesh) {
    queue.add(mesh);
  }

  public CompiledMesh poll() {
    return queue.poll();
  }

  public int size() {
    return queue.size();
  }

  public long droppedCount() {
    return dropped;
  }

  public void recordDrop(CompiledMesh mesh) {
    mesh.close();
    dropped++;
  }

  /** Discards everything queued, e.g. on a world change. */
  public void clear() {
    CompiledMesh mesh;
    while ((mesh = queue.poll()) != null) {
      mesh.close();
    }
  }
}
