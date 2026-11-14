# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Hybrid chunk-section mesher: cached static quads on a pure-Java fast path, with fluids, translucent
  blocks, block entities and unprovable models deferred to vanilla's renderer inside the same task.
- Flat quad store, struct-of-arrays meta table, lock-free face visibility cache, AO and smooth-light
  port, and a visibility flood fill replacing `VisGraph`.
- Priority scheduler with per-section dirty versions, bounded queue and backpressure.
- Budgeted upload governor and per-layer buffer pool.
- `/bricklayer stats`, `parity`, `dump`, `toggle` and `reload`, plus F3 overlay lines and a
  `bricklayer.Compile` JFR event.
- Self-disabling gate for Sodium, Embeddium and Rubidium, and a failure circuit breaker.
- CI: formatting, core tests, dual-OS build matrix, a nightly NeoForge canary, tag-driven releases with
  build provenance, CodeQL, labeler and manual JMH benchmarks.

[Unreleased]: https://github.com/your-github-username/bricklayer/commits/main
