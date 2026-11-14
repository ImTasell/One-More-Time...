/*
 * Bricklayer - a replacement chunk-section mesher for Minecraft 1.21.1 (NeoForge).
 * Copyright (c) 2026 the Bricklayer contributors. See LICENSE for licence terms.
 */
package dev.example.bricklayer;

import com.mojang.logging.LogUtils;
import dev.example.bricklayer.compat.CompatGate;
import dev.example.bricklayer.config.BricklayerConfig;
import dev.example.bricklayer.core.grid.SnapshotPool;
import dev.example.bricklayer.core.meta.FaceVisibilityTable;
import dev.example.bricklayer.core.meta.MetaTable;
import dev.example.bricklayer.core.quad.QuadStore;
import dev.example.bricklayer.debug.BricklayerCommand;
import dev.example.bricklayer.debug.DebugOverlay;
import dev.example.bricklayer.meta.FaceVisibilityOracle;
import dev.example.bricklayer.model.BricklayerModels;
import dev.example.bricklayer.sched.BricklayerScheduler;
import dev.example.bricklayer.snapshot.DecodeCache;
import dev.example.bricklayer.upload.MeshBufferPool;
import dev.example.bricklayer.upload.UploadGovernor;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;

/**
 * Client-only entry point.
 *
 * <p>Registered with {@code dist = Dist.CLIENT}, so dedicated servers never load the class and the
 * mixins only ever apply in a client environment.
 *
 * <p>Everything that is shared between the main thread, the mesh workers and the render thread
 * hangs off this class as a published volatile reference. The tables are rebuilt on the main thread
 * after a model bake and swapped in whole, so a worker never observes a half-built table.
 */
@Mod(value = Bricklayer.MOD_ID, dist = Dist.CLIENT)
public final class Bricklayer {
  public static final String MOD_ID = "bricklayer";
  public static final Logger LOGGER = LogUtils.getLogger();

  private static Bricklayer instance;

  private static volatile MetaTable metaTable = MetaTable.EMPTY;
  private static volatile QuadStore quadStore = QuadStore.empty();
  private static volatile FaceVisibilityTable faceVisibility =
      new FaceVisibilityTable(MetaTable.EMPTY);
  private static final AtomicInteger RESOURCE_GENERATION = new AtomicInteger(1);

  private final BricklayerScheduler scheduler;
  private final SnapshotPool snapshotPool;
  private final UploadGovernor uploadGovernor;
  private final MeshBufferPool bufferPool;
  private final DecodeCache decodeCache;
  private final FaceVisibilityOracle visibilityOracle = new FaceVisibilityOracle();

  public Bricklayer(IEventBus modBus, ModContainer container) {
    instance = this;

    container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, BricklayerConfig.SPEC);
    BricklayerConfig.get();

    this.decodeCache = new DecodeCache();
    this.snapshotPool = new SnapshotPool(BricklayerConfig.get().maxInFlight());
    this.scheduler = new BricklayerScheduler(snapshotPool, decodeCache);
    this.uploadGovernor = new UploadGovernor();
    this.bufferPool = new MeshBufferPool();

    CompatGate.evaluate();

    modBus.addListener(this::onBakingCompleted);
    // Assumption: these are game-bus events in NeoForge 21.1.x; verify against the event list.
    NeoForge.EVENT_BUS.addListener(Bricklayer::onRegisterReloadListeners);
    NeoForge.EVENT_BUS.addListener(BricklayerCommand::register);
    NeoForge.EVENT_BUS.addListener(DebugOverlay::onDebugText);
    NeoForge.EVENT_BUS.addListener(this::onLevelTick);

    LOGGER.info("Bricklayer loaded ({} mode)", CompatGate.mode());
  }

  public static Bricklayer get() {
    if (instance == null) {
      throw new IllegalStateException("Bricklayer accessed before construction");
    }
    return instance;
  }

  // ---------------------------------------------------------------------------------------------
  // Published tables
  // ---------------------------------------------------------------------------------------------

  public static MetaTable metaTable() {
    return metaTable;
  }

  public static QuadStore quadStore() {
    return quadStore;
  }

  public static FaceVisibilityTable faceVisibility() {
    return faceVisibility;
  }

  public static int resourceGeneration() {
    return RESOURCE_GENERATION.get();
  }

  /** Publishes a freshly built set of tables and bumps the resource generation. */
  public static void publish(BricklayerModels.Result result) {
    MetaTable previous = metaTable;
    faceVisibility = new FaceVisibilityTable(result.meta());
    quadStore = result.quads();
    metaTable = result.meta();
    int generation = RESOURCE_GENERATION.incrementAndGet();
    if (previous != result.meta()) {
      dev.example.bricklayer.core.mesh.Scratch.get().clearTintMemo();
    }
    LOGGER.info(
        "Published mesher tables: {} states, {} models, {} quads (generation {})",
        result.meta().size(),
        result.quads().modelCount(),
        result.quads().quadCount(),
        generation);
  }

  private void onBakingCompleted(ModelEvent.BakingCompleted event) {
    Minecraft minecraft = Minecraft.getInstance();
    try {
      publish(BricklayerModels.rebuild(event.getModelManager(), minecraft.getBlockColors()));
    } catch (RuntimeException | LinkageError failure) {
      // A broken table build must never take the client down: fall back to vanilla meshing.
      LOGGER.error("Failed to build mesher tables, Bricklayer will stay disabled", failure);
      CompatGate.disableForSession("table build failed: " + failure);
    }
  }

  private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
    // The model bake is the real reload hook; this listener only clears the decode cache so a
    // resource reload cannot mesh with stale state ids.
    event.registerReloadListener(
        (preparationBarrier,
            resourceManager,
            preparationsProfiler,
            reloadProfiler,
            backgroundExecutor,
            gameExecutor) ->
            preparationBarrier
                .wait(null)
                .thenRunAsync(
                    () -> {
                      if (instance != null) {
                        instance.decodeCache.clear();
                      }
                    },
                    gameExecutor));
  }

  private void onLevelTick(LevelTickEvent.Post event) {
    if (event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel) {
      uploadGovernor.endFrame();
      bufferPool.trimIdle();
    }
  }

  public BricklayerScheduler scheduler() {
    return scheduler;
  }

  public SnapshotPool snapshotPool() {
    return snapshotPool;
  }

  public UploadGovernor uploadGovernor() {
    return uploadGovernor;
  }

  public MeshBufferPool bufferPool() {
    return bufferPool;
  }

  public DecodeCache decodeCache() {
    return decodeCache;
  }

  public FaceVisibilityOracle visibilityOracle() {
    return visibilityOracle;
  }

  /** Overlay texture value vanilla uses for chunk geometry; kept here so the constant is shared. */
  public static int overlay() {
    return OverlayTexture.NO_OVERLAY;
  }
}
