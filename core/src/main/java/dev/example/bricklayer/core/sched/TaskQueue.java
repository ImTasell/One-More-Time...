/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.core.sched;

import dev.example.bricklayer.core.grid.SectionSnapshot;
import java.util.Iterator;
import java.util.PriorityQueue;

/**
 * Bounded priority queue of mesh tasks.
 *
 * <p>Bounded so a world change cannot queue unbounded work: when the queue is full the farthest
 * section (highest score) is evicted, which is the task whose result would be least visible.
 */
public final class TaskQueue {
  /** One unit of work: a section, a version and the captured snapshot. */
  public static final class Task implements Comparable<Task> {
    public final long sectionKey;
    public final int version;
    public final long submittedNanos;
    public final SectionSnapshot snapshot;
    private double score;

    public Task(long sectionKey, int version, SectionSnapshot snapshot, double score) {
      this.sectionKey = sectionKey;
      this.version = version;
      this.snapshot = snapshot;
      this.score = score;
      this.submittedNanos = System.nanoTime();
    }

    public double score() {
      return score;
    }

    public void score(double score) {
      this.score = score;
    }

    @Override
    public int compareTo(Task other) {
      int byScore = Double.compare(score, other.score);
      return byScore != 0 ? byScore : Long.compare(sectionKey, other.sectionKey);
    }
  }

  private final PriorityQueue<Task> queue = new PriorityQueue<>();
  private final int capacity;
  private long evicted;

  public TaskQueue(int capacity) {
    this.capacity = Math.max(1, capacity);
  }

  public synchronized boolean offer(Task task) {
    if (queue.size() >= capacity) {
      Task worst = null;
      for (Iterator<Task> it = queue.iterator(); it.hasNext(); ) {
        Task candidate = it.next();
        if (worst == null || candidate.score() > worst.score()) {
          worst = candidate;
        }
      }
      if (worst != null && worst.score() <= task.score()) {
        evicted++;
        return false;
      }
      if (worst != null) {
        queue.remove(worst);
        evicted++;
      }
    }
    return queue.add(task);
  }

  public synchronized Task poll() {
    return queue.poll();
  }

  public synchronized Task peek() {
    return queue.peek();
  }

  public synchronized int size() {
    return queue.size();
  }

  public synchronized long evictedCount() {
    return evicted;
  }

  public synchronized void clear() {
    queue.clear();
  }

  /** Re-scores a queued task, e.g. when the camera moved. */
  public synchronized void reprioritize(Task task, double score) {
    if (queue.remove(task)) {
      task.score(score);
      queue.add(task);
    }
  }
}
