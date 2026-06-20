# General Purpose Optimization Mod

General Purpose Optimization Mod, or GPOM, is a Minecraft 1.12.2 Cleanroom/Forge optimization coremod for heavily modded packs. It targets startup, registry, HEI, world-entry, compatibility, diagnostics, and selected gameplay/UI bottlenecks with exact-version ASM, mixins, caches, and experimental threaded FML lifecycle dispatch.

The repository README is intentionally short. The maintained feature and validation details live in docs so this file does not drift from the operational profile.

## Documentation

- [Feature catalog](docs/FEATURE_CATALOG.md): maintained inventory of GPOM features, config model, risk levels, and pack-specific compatibility notes.
- [Feature log](docs/FEATURE_LOG.md): current measured status, validation notes, latest crashes/fixes, and active tuning decisions. Historical status blocks older than today are intentionally removed from this file.
- [Sample speed config](docs/gpom-early.properties): documented `config/gpom-early.properties` profile aligned with the tested MeatballCraft speed posture.
- [Resume state](docs/GPOM_CHAT_RESUME_STATE.json): machine-readable handoff state for continuing work without losing current constraints.

## Building

GPOM uses Gradle and compiles Java sources with `--release 8`. The Gradle toolchain is configured for Java 25 because this repository is developed against Cleanroom's current Java toolchain.

Some exact-version helpers directly reference MeatballCraft mod classes at compile time. Point Gradle at a Minecraft instance containing the required mod jars:

```bash
MEATBALL_MINECRAFT_DIR="/path/to/instance/minecraft" ./gradlew build
```

Equivalent Gradle property form:

```bash
./gradlew -Pmeatball_minecraft_dir="/path/to/instance/minecraft" build
```

If neither is set, the build falls back to the common Flatpak PrismLauncher MeatballCraft instance path under `$HOME`.

## Runtime Config

GPOM reads `config/gpom-early.properties` from the Minecraft instance directory before Forge's normal config system exists. For the current tested profile and key meanings, use [docs/gpom-early.properties](docs/gpom-early.properties) together with [docs/FEATURE_CATALOG.md](docs/FEATURE_CATALOG.md).

The active MeatballCraft profile intentionally favors faster average startup over perfectly deterministic launch-to-launch timings. Forge's startup screen must remain enabled.
