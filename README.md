# Bricklayer

> **This is a joke mod.** It replaces the chunk-section mesher — the code that turns blocks into GPU
> geometry — with a hand-written pipeline, on the theory that a bad idea executed precisely is still a
> bad idea. It is not a replacement for Sodium and it is not faster than your GPU driver's opinion of
> vanilla. Install it in a world you can throw away.

Bricklayer reimplements the client-side rebuild task of `SectionRenderDispatcher` for Minecraft
1.21.1 on NeoForge. World generation, light propagation, server chunk ticking, drawing, frustum
culling and translucent resorting all stay vanilla.

## What it actually does

- **Hybrid meshing.** Static models are cached as flat quad arrays and meshed by a pure-Java core with
  zero allocation in the hot path. Fluids, block-entity renderers, translucent blocks, multiparts that
  cannot be proven static and any flagged modded block are handed to vanilla's own
  `BlockRenderDispatcher` **inside the same task**, into the same vertex buffers.
- **No greedy meshing.** Quads reference texture-atlas UVs that cannot tile, and per-vertex AO and
  light break merge conditions. The wins come from early culling, flat arrays, cached quads and zero
  allocation instead.
- **Two worker threads by default**, sized from your CPU count, with a bounded priority queue and a
  per-frame upload budget.
- **A parity tool.** `/bricklayer parity` compiles the section under you twice and compares the vertex
  streams, because a hand-ported ambient-occlusion algorithm absolutely will be wrong the first time.

## Incompatibilities

Bricklayer disables itself when another mod owns the same code path. The mixin config plugin checks
this during mixin application, before any mod code runs:

- **Sodium**
- **Embeddium**
- **Rubidium**
- (and anything else that replaces `SectionRenderDispatcher`, which is most of them)

It also disables itself permanently for the session after repeated failures (`breakerThreshold`
exceptions inside 60 seconds), and it never catches `OutOfMemoryError`.

## Client-only

The mod is annotated `@Mod(dist = Dist.CLIENT)`, so dedicated servers never load it. There is nothing
to configure on a server and nothing to install on one.

## Config

`config/bricklayer-client.toml`, all keys in the `general`, `scheduling` and `debug` sections:

| Key | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Master switch. Turning it off restores vanilla meshing exactly. |
| `workerThreads` | `0` | Mesh threads. `0` = `max(1, cpus - 2)`. |
| `uploadBudgetMs` | `2.0` | Milliseconds of mesh upload allowed per frame. Halves when frames exceed 25 ms. |
| `maxInFlight` | `0` | Concurrent tasks. `0` = `workers * 2`. |
| `aoMode` | `PARITY` | `PARITY` runs the full AO port; `FAST` uses cubic weights for partial quads. |
| `rawSink` | `false` | Write vertices straight into the builder buffer. Needs the access transformers. |
| `debugOverlay` | `true` | Add Bricklayer lines to the F3 screen. |
| `breakerThreshold` | `8` | Failures within 60 s before the mod disables itself for the session. |
| `verbose` | `false` | Verbose logging. |

## Commands

All client-side, no permissions needed:

- `/bricklayer stats` — queue depth, mean compile time, deferred ratio, cache and pool sizes.
- `/bricklayer parity` — compile the section under you through both paths and compare.
- `/bricklayer dump [pos]` — write the snapshot, meta flags and per-face visibility to
  `bricklayer-dump-<key>.txt` in the game directory.
- `/bricklayer toggle` — flip the gate for this session (does not persist; edit `enabled` for that).
- `/bricklayer reload` — clear the breaker, scheduler and buffer pools.

## Filing a useful bug report

A report without the following will be closed with a link to this section:

1. Minecraft, NeoForge and Bricklayer versions, and the **full** mod list.
2. `latest.log` — not a screenshot of the log.
3. The output of `/bricklayer dump` and `/bricklayer parity` standing in the broken section.
4. A spark profile link if it is a performance report.
5. Confirmation that the bug does **not** happen with `enabled=false`.

## Building

Requires JDK 21.

```bash
./gradlew spotlessCheck :core:test :mod:build
```

The jar lands in `mod/build/libs/`. `:core` is pure Java 21 with no Minecraft imports, so its tests run
in about a second without launching the game:

```bash
./gradlew :core:test
```

Benchmarks on synthetic sections:

```bash
./gradlew :core:jmh
```

## Layout

```
core/   pure Java: grid, meta tables, quad store, AO, mesher, scheduler primitives
mod/    NeoForge glue: config, tables, snapshot, getter, pipeline, scheduler, upload, mixins, debug
```

Everything with real logic lives in `core`; `mod` only adapts Minecraft objects into core's flat
arrays and back.

## Licence

GNU General Public License v3.0. See [LICENSE](LICENSE).
