# General Purpose Optimization Mod

General Purpose Optimization Mod, or GPOM, is a Minecraft 1.12.2 Cleanroom/Forge optimization coremod for heavily modded packs. It targets startup and world-entry bottlenecks with exact-version ASM, mixins, caches, and experimental threaded FML lifecycle dispatch.

The shipped defaults are conservative. The MeatballCraft validation profile enables broader experiments through `config/gpom-early.properties`.

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

## Features

- Threaded FML lifecycle phases: `FMLConstructionEvent`, `FMLPreInitializationEvent`, `FMLInitializationEvent`, `FMLPostInitializationEvent`, and `FMLLoadCompleteEvent` can be enabled independently.
- Per-phase allowlists, denylists, worker counts, and diagnostic `continueOnModError` controls.
- Construction safety fences around known Forge global-state mutations: classloader updates, network registration, proxy injection, automatic event-subscriber registration, config sync, and annotation processing.
- Registry serialization for threaded registry mutation paths, keeping Forge registry internals single-writer while lifecycle handlers run in parallel.
- Startup profiling for FML phases, per-mod timing, targeted probes, stack sampling, resource reloads, and high-cost HEI paths.
- Exact-version mod optimizations for AoA3, EnderIO, Betweenlands, Railcraft/TechReborn probes, Thaumcraft, CraftTweaker, Thermal Expansion, Forestry, ExtraTrees, JER, and related startup sinks.
- HEI startup optimizations, including item-stack cache, coarse parsed-recipe progress, synchronized exact-allowlist plugin threading, JER villager trade cache, Forestry Bottler cache, ExtraTrees Lumbermill cache, Thermal Expansion Transposer cache, and EnderIO Tank fast path.
- Optional early splash window before Minecraft creates its display.
- Optional passive world-loading overlay for the blank/early `0%` singleplayer world-entry wait.
- Local compressed runtime caches under the instance directory at `caches/gpom/<purpose>/`.
- Title-screen GPOM version display.

## Config File

GPOM reads `config/gpom-early.properties` from the Minecraft instance directory. This loader runs before Forge's normal config system, so it intentionally uses a simple Java properties file.

Basic rules:

- Boolean values are `true` or `false`.
- Lists are comma-separated and case-insensitive for mod ids.
- `*` in an allowlist means every loaded mod is eligible.
- Denylists always win over allowlists.
- Worker values of `0` use GPOM's automatic sizing based on CPU and physical memory.
- `continueOnModError=true` is diagnostic-only. Use it to discover unsafe threaded mods, then return it to `false` for normal play.

## FML Threading

Each lifecycle phase has the same core key shape:

```properties
fml.parallel.<phase>.enabled=false
fml.parallel.<phase>.workers=0
fml.parallel.<phase>.allowlist=
fml.parallel.<phase>.denylist=
fml.parallel.<phase>.continueOnModError=false
```

Valid phase names are:

- `construct`
- `preInit`
- `init`
- `postInit`
- `loadComplete`

Shared worker fallback:

```properties
fml.parallel.workers=0
```

Registry serialization:

```properties
fml.parallel.registrySerialization.enabled=true
```

Construction is the most invasive phase because mod instances, proxies, classloader state, network holders, config classes, and automatic subscribers are created there. Broad construction threading should stay pack-profiled and denylist-driven.

## Loading Screens

Early splash:

```properties
gpom.earlySplash.enabled=false
gpom.earlySplash.packName=Minecraft
```

World loading overlay:

```properties
gpom.worldLoadingScreen.enabled=false
```

Both are visual-only. They should fail closed and must not alter world generation, networking, registries, or save data.

## HEI Options

Parsed recipe progress:

```properties
gpom.hei.recipeProgressBar.enabled=true
gpom.hei.recipeProgressBar.stepSize=256
```

HEI plugin threading:

```properties
gpom.hei.parallelPluginRegistration.enabled=false
gpom.hei.parallelPluginRegistration.workers=0
gpom.hei.parallelPluginRegistration.allowlist=
gpom.hei.parallelPluginRegistration.denylist=
```

Plugin threading is exact-allowlist territory. GPOM synchronizes targeted HEI registry surfaces, but plugins can still touch unrelated shared state. Keep the allowlist narrow and deny any plugin that crashes, corrupts outputs, or causes missing recipes.

Targeted HEI caches and fast paths:

```properties
gpom.hei.jerVillagerTradeCache.enabled=true
gpom.hei.jerVillagerTradeCache.samples=32
gpom.hei.fastForestryBottler.enabled=true
gpom.hei.forestryBottlerRecipeCache.enabled=true
gpom.hei.fastEnderIOTank.enabled=true
gpom.hei.extraTreesLumbermillRecipeCache.enabled=true
gpom.hei.fastThermalTransposerContainers.enabled=true
gpom.hei.thermalTransposerContainerCache.enabled=true
```

Caches are compressed for load speed and storage size, not security. GPOM validates cache signatures and falls back to original mod behavior when versions, registries, recipe signatures, or cache contents do not match.

## Safety Model

- GPOM does not open sockets, fetch remote code, or load classes from cache/config paths.
- ASM and reflection targets are hardcoded and exact-version checked where the optimization depends on third-party internals.
- Runtime caches use primitive/NBT data formats, not Java object serialization.
- Threaded loading is a stability risk, not a security feature. Treat broad `*` allowlists as pack-specific experiments.

## Operational Notes

- Do not replace the GPOM jar while Minecraft is running. Cleanroom can lazily load classes from the jar after startup, so mutating the jar in place can create false classloading failures.
- Helper scripts use `PRISM_ROOT` and `PRISM_INSTANCE_ID` when set; otherwise they default to the common Flatpak PrismLauncher data path and `MeatballCraft, Dimensional Ascension`.
- After changing threaded phase allowlists, validate both menu startup and existing-world entry.
- If a threaded mod fails, put its mod id in that phase's denylist first. Only patch the mod when the failure is stable and worth the gain.
- Use `docs/OPTIMIZATION_LOG.md` for measured changes and `docs/MULTITHREADED_LOADING_FEASIBILITY.md` for threading design notes.

## License

GPOM is licensed under the MIT License. See `LICENSE`.
