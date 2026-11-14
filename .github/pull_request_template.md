## What changed

<!-- One or two sentences. Link the issue this closes. -->

## Subsystem

- [ ] `core/grid`
- [ ] `core/meta`
- [ ] `core/quad`
- [ ] `core/light`
- [ ] `core/mesh`
- [ ] `core/sched`
- [ ] `mod/snapshot`
- [ ] `mod/scheduler` / upload
- [ ] mixins
- [ ] CI / build

## Rendering changes

Paste the `/bricklayer parity` result for the section you tested:

```
```

## Checklist

- [ ] `./gradlew spotlessCheck :core:test` passes
- [ ] New or changed config keys are documented in `README.md`
- [ ] Mixin changes keep `injectors.defaultRequire` at 1 and are covered by the canary
- [ ] No new Minecraft imports in `core`
