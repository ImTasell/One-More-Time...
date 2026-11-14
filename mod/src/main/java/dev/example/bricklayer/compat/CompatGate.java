/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer.compat;

import dev.example.bricklayer.Bricklayer;
import dev.example.bricklayer.config.BricklayerConfig;
import java.util.ArrayDeque;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.LoadingModList;

/**
 * One place that answers "may Bricklayer run?".
 *
 * <p>Every mixin checks the gate before doing anything, and the gate has four outcomes so the
 * reason a user sees in chat matches the reason the mod actually disabled itself.
 */
public final class CompatGate {
  /** Other mods that own the same code path; Bricklayer never fights them. */
  private static final List<String> CONFLICTS = List.of("sodium", "embeddium", "rubidium");

  private static volatile Mode mode = Mode.ACTIVE;
  private static volatile String detail = "";
  private static final ArrayDeque<Long> FAILURES = new ArrayDeque<>();
  private static final long FAILURE_WINDOW_NANOS = 60_000_000_000L;

  private CompatGate() {}

  public enum Mode {
    ACTIVE,
    DISABLED_CONFLICT,
    DISABLED_BREAKER,
    DISABLED_USER
  }

  public static Mode mode() {
    return mode;
  }

  public static String detail() {
    return detail;
  }

  public static boolean active() {
    return mode == Mode.ACTIVE && BricklayerConfig.get().enabled();
  }

  /** Re-evaluates the gate from config and the loaded mod list. */
  public static Mode evaluate() {
    if (!BricklayerConfig.get().enabled()) {
      mode = Mode.DISABLED_USER;
      detail = "disabled in config";
      return mode;
    }
    if (mode == Mode.DISABLED_BREAKER) {
      return mode;
    }
    String conflict = findConflict();
    if (conflict != null) {
      mode = Mode.DISABLED_CONFLICT;
      detail = conflict;
      Bricklayer.LOGGER.warn(
          "Bricklayer is disabled because {} is installed and owns section meshing", conflict);
      return mode;
    }
    mode = Mode.ACTIVE;
    detail = "";
    return mode;
  }

  @Nullable
  private static String findConflict() {
    LoadingModList mods = LoadingModList.get();
    if (mods == null) {
      return null;
    }
    for (String id : CONFLICTS) {
      if (mods.getModFileById(id) != null) {
        return id;
      }
    }
    return null;
  }

  /**
   * Records one failed task. When the failure count inside the window reaches the configured
   * threshold the gate latches to {@link Mode#DISABLED_BREAKER} for the rest of the session.
   */
  public static synchronized void recordFailure(String where, Throwable failure) {
    long now = System.nanoTime();
    FAILURES.addLast(now);
    while (!FAILURES.isEmpty() && now - FAILURES.peekFirst() > FAILURE_WINDOW_NANOS) {
      FAILURES.pollFirst();
    }
    if (FAILURES.size() >= BricklayerConfig.get().breakerThreshold()
        && mode != Mode.DISABLED_BREAKER) {
      mode = Mode.DISABLED_BREAKER;
      detail = where + ": " + failure;
      Bricklayer.LOGGER.error(
          "Bricklayer tripped its circuit breaker after {} failures in 60s; last failure in {}",
          FAILURES.size(),
          where,
          failure);
      notifyPlayer();
    }
  }

  /** Used when a subsystem fails in a way that must not leave the mod half-active. */
  public static void disableForSession(String reason) {
    mode = Mode.DISABLED_BREAKER;
    detail = reason;
  }

  public static synchronized void resetBreaker() {
    FAILURES.clear();
    if (mode == Mode.DISABLED_BREAKER) {
      mode = Mode.ACTIVE;
      detail = "";
      evaluate();
    }
  }

  private static void notifyPlayer() {
    var minecraft = net.minecraft.client.Minecraft.getInstance();
    if (minecraft.player != null) {
      minecraft.player.displayClientMessage(
          Component.translatable("bricklayer.gate.disabled_breaker"), false);
    }
  }
}
