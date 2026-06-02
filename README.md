# Cleanroom Optimizations

This is a separate Minecraft 1.12.2 / Cleanroom project for optimization and leak-fix work.

## Scope

The goal is to improve runtime stability and performance in large modded instances without changing gameplay behavior. Changes should be small enough to isolate, easy to toggle or revert, and backed by before/after tests in both small and heavily modded worlds.

## Current State

- Basic Cleanroom/Forge 1.12.2 project scaffold.
- Forge mod entrypoint is present.
- Coremod loader is present for future mixins.
- Empty mixin configs are wired for development and packaged jars.
- No optimization patches have been implemented yet.

## First Targets

1. Dimension-switch cleanup: look for stale client world, chunk render, entity render, texture, or task references that survive rapid dimension changes.
2. Chunk render queue hygiene: audit pending compile/upload tasks, stale task cancellation, and references retained after world unload.
3. Resource reload cost: profile model, texture, language, and metadata reload paths in large modpacks.
4. Negative lookup caching: identify repeated missing-resource or missing-model lookups that can be cached safely.
5. Client memory retention: check common 1.12.2 static caches, event listeners, render managers, and thread queues after disconnect, reload, and dimension travel.
6. Large-modpack startup stalls: measure expensive client-only discovery paths and avoid repeated scans where possible.
7. Startup RAM spikes: identify mod-loading phases that temporarily inflate heap usage from the normal 2-5 GB range toward 10-12 GB, then look for avoidable bulk caches, duplicate recipe/model/asset lists, and short-lived allocations that can be streamed or cleared earlier.

## Constraints

- Do not break mod loading.
- Prefer client-only changes unless a server-side leak is clearly identified.
- Keep risky changes behind config switches once config is added.
- Avoid broad rewrites until profiling identifies a real bottleneck.
- Use targeted mixins over global patches.

## Verification Plan

- Start with a small development world to catch obvious regressions.
- Test a heavily modded instance after every invasive change.
- Exercise world join, disconnect, shaderless render, dimension travel, resource reload, and client shutdown.
- Watch memory before and after repeated dimension switches.
- Watch heap usage during startup and compare per-mod memory deltas against timing spikes.
- Compare chunk loading and frame-time spikes before and after each patch.

## Useful Commands

```bash
./gradlew compileJava
./gradlew runClient
./gradlew build
```

## New Chat Handoff

Continue from this project as an optimization-only mod. The scaffold is ready, but no performance patches are implemented yet. The next useful step is to profile or instrument dimension switching and chunk render task cleanup, then add the smallest mixin that removes one confirmed leak or stale task path.
