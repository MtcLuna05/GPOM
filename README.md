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
- Optional DAG scheduling for each FML phase, preserving declared mod ordering edges while allowing independent handlers to run concurrently.
- Per-phase allowlists, denylists, worker counts, and diagnostic `continueOnModError` controls.
- Construction safety fences around known Forge global-state mutations: classloader updates, network registration, proxy injection, automatic event-subscriber registration, config sync, and annotation processing.
- Generic construction shortcuts for Forge annotation work, with per-mod fallbacks for sided proxies and automatic subscribers when a mod needs stock Forge behavior.
- Registry serialization for threaded registry mutation paths, keeping Forge registry internals single-writer while lifecycle handlers run in parallel.
- Experimental parallel registry-event dispatcher for selected Forge registries, with dependency gating, queued commits, proxy event registries, and per-registry/mod denylists.
- Startup profiling for FML phases, per-mod timing, targeted probes, stack sampling, resource reloads, and high-cost HEI paths.
- Exact-version mod optimizations for AoA3, EnderIO, Betweenlands, Railcraft/TechReborn probes, Thaumcraft, CraftTweaker, Thermal Expansion, Forestry, ExtraTrees, JER, and related startup sinks.
- CraftTweaker startup optimizations, including fast ZenRegister handling, optional parallel class loading, parallel script loading/batching, lazy item-list handling, and suppression of high-volume function-type stdout spam.
- HEI startup optimizations, including item-stack cache, fast pre-init plugin discovery, synchronized exact-allowlist plugin threading with optional serial/threaded overlap, JER villager/loot caches, Forestry Bottler cache, ExtraTrees Lumbermill cache, Thermal Expansion Transposer cache, and EnderIO Tank fast path.
- Optional early splash window before Minecraft creates its display.
- Optional passive world-loading overlay for the blank/early `0%` singleplayer world-entry wait.
- Local compressed runtime caches under the instance directory at `caches/gpom/<purpose>/`.
- Cache invalidation denylist for jars that should not invalidate GPOM runtime caches while actively developed or non-content-bearing.
- VintageFix/Unlimited Chisel Works/model-log spam suppression that keeps one concise GPOM line per noisy namespace instead of repeated stack traces.
- Server-safe bootstrap routing through Forge sided proxies plus side-checked early splash bridging, so common/server startup does not load client-only classes.
- Optional Baubles side-slot inventory panel that adds real Baubles slots to the vanilla inventory instead of relying on Baubles' expanded GUI.
- Experimental Forge Multipart/AE2 compatibility switches. These are disabled by default because multipart world data can be destructive if a bridge is disabled after use.
- Title-screen GPOM version display.

## Latest Changelog

### 2026-06-10

- Added `CommonProxy` / `ClientProxy` sided bootstrap so common pre-init registrations run through side-aware Forge entrypoints.
- Added `GpomSide` and `EarlySplashBridge` so coremod and scheduler paths can update the early splash on client launches without loading `EarlySplashWindow` on dedicated servers.
- Guarded Baubles EventBus replacement and side-slot networking so client-only handlers do not load on servers.
- Simplified Baubles side slots to use the vanilla `GuiInventory` path: GPOM appends Baubles slots to `ContainerPlayer`, draws only the side panel/button, and redirects Baubles key/button entrypoints to vanilla inventory side slots.
- Removed the active expanded-Baubles-GUI mixin path from the mixin config; this avoids depending on `GuiPlayerExpanded` / `ContainerPlayerExpanded`, which BringMeTheRings can make unsafe to transform.
- Removed the manual post-render hover/durability overlay replay. Vanilla `GuiContainer` owns item hover, durability bars, and tooltips again.
- Synced CosmeticArmorReworked per-bauble visibility toggles with the GPOM side rail. The toggle buttons are moved beside side slots while the panel is open, hidden when the panel is closed, and missing supported buttons are created reflectively for extra Baubles handler slots.
- Fixed a Cleanroom runtime crash by replacing the last direct mapped `Slot.isEnabled()` call with SRG-safe reflective `func_111238_b` access.
- Creative players now stay on the real creative inventory path when opening Baubles through GPOM, and GPOM removes mirrored Baubles slots from the creative survival tab instead of opening a fake survival inventory over the hotbar.
- Fixed the creative-tab cleanup crash by avoiding direct `CreativeTabs.INVENTORY` linkage and resolving the survival inventory tab label through mapped/SRG reflective fallbacks.
- Added `erebus` to the default PreInit parallel denylist after the MeatballCraft crash in Erebus armor/registry setup. Parallel PreInit remains available; the fix is scoped to the broken mod.
- Validated `./gradlew build` and a dedicated-server smoke run with `./gradlew runServer --args nogui`; the server reached the normal EULA stop without client-side classloading crashes.

## Config File

GPOM reads `config/gpom-early.properties` from the Minecraft instance directory. This loader runs before Forge's normal config system, so it intentionally uses a simple Java properties file.

If a newer GPOM jar adds missing keys, the early config loader appends those keys with default values instead of silently using hidden defaults forever. Existing user-edited values are preserved.

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
fml.parallel.<phase>.dag.enabled=false
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

Construction annotation shortcuts can be disabled per mod when a mod requires stock Forge injection behavior:

```properties
gpom.construction.genericSidedProxies.denylist=thaumcraft
gpom.construction.genericAutomaticSubscribers.denylist=thaumcraft
```

## Registry Event Parallelism

The registry dispatcher is a separate experiment from lifecycle threading. It targets selected `RegistryEvent.Register` events and preserves Forge registry writes through queued or immediate commit paths.

```properties
gpom.registry.parallelRegisterEvents.enabled=false
gpom.registry.parallelRegisterEvents.registries=minecraft:recipes,minecraft:blocks,minecraft:items,minecraft:entities,ebwizardry:spells
gpom.registry.parallelRegisterEvents.workers=0
gpom.registry.parallelRegisterEvents.queuedCommit=true
gpom.registry.parallelRegisterEvents.proxyEventRegistry=true
gpom.registry.parallelRegisterEvents.proxyEventRegistryDenylist=
gpom.registry.parallelRegisterEvents.immediateCommitRegistries=minecraft:items,minecraft:entities
gpom.registry.parallelRegisterEvents.proxyImmediateRegistries=false
gpom.registry.parallelRegisterEvents.orderedWaveRegistries=minecraft:items
gpom.registry.parallelRegisterEvents.dependencyGating=true
gpom.registry.parallelRegisterEvents.allowlist=*
gpom.registry.parallelRegisterEvents.denylist=
```

Registry parallelism is invasive. Use per-mod/per-registry denylist entries such as `modid@minecraft:items` for handlers that require exact vanilla ordering or mutate unguarded side state.

## CraftTweaker Options

```properties
gpom.crafttweaker.fastZenRegister=false
gpom.crafttweaker.fastZenRegister.parallelClassLoad=false
gpom.crafttweaker.fastZenRegister.classLoadWorkers=0
gpom.crafttweaker.parallelScriptParsing.enabled=false
gpom.crafttweaker.parallelScriptParsing.workers=0
gpom.crafttweaker.parallelScriptParsing.allowlist=*
gpom.crafttweaker.parallelScriptParsing.denylist=
gpom.crafttweaker.parallelScriptParsing.offThreadZenParse=false
gpom.crafttweaker.parallelScriptParsing.batchAllowedScripts=false
gpom.crafttweaker.suppressFunctionTypeStdout=true
```

The script parser preserves CraftTweaker ordering by respecting script priority and alphabetical ordering. Keep `offThreadZenParse=false` unless a pack has been specifically validated with off-thread Zen parsing.

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
gpom.hei.fastPreInitPluginDiscovery.enabled=false
gpom.hei.fastPreInitPluginDiscovery.workers=0
gpom.hei.parallelPluginRegistration.enabled=false
gpom.hei.parallelPluginRegistration.workers=0
gpom.hei.parallelPluginRegistration.overlapSerial=false
gpom.hei.parallelPluginRegistration.allowlist=
gpom.hei.parallelPluginRegistration.denylist=
```

Plugin threading is exact-allowlist territory. GPOM synchronizes targeted HEI registry surfaces, but plugins can still touch unrelated shared state. Keep the allowlist narrow and deny any plugin that crashes, corrupts outputs, or causes missing recipes. `overlapSerial=true` submits allowlisted threaded plugins first, runs serial plugins on the client thread while workers are active, then joins worker results before HEI recipe-registry construction.

Targeted HEI caches and fast paths:

```properties
gpom.hei.jerVillagerTradeCache.enabled=true
gpom.hei.jerVillagerTradeCache.samples=32
gpom.hei.jerLootDropCache.enabled=true
gpom.hei.fastForestryBottler.enabled=true
gpom.hei.forestryBottlerRecipeCache.enabled=true
gpom.hei.fastEnderIOTank.enabled=true
gpom.hei.extraTreesLumbermillRecipeCache.enabled=true
gpom.hei.fastThermalTransposerContainers.enabled=true
gpom.hei.thermalTransposerContainerCache.enabled=true
```

Caches are compressed for load speed and storage size, not security. GPOM validates cache signatures and falls back to original mod behavior when versions, registries, recipe signatures, or cache contents do not match.

Cache invalidation can ignore actively developed non-content jars:

```properties
gpom.cacheInvalidation.denylist=ausm,gpom
```

This should only contain mods that do not register content relevant to the cache being protected.

## Log Suppression

```properties
gpom.vintageFix.suppressUcwModelErrorSpam=true
gpom.vintageFix.skipUcwDefinitionEarlyModelLoad=true
gpom.ctm.tolerateUnknownRenderLayer=true
gpom.ctm.suppressTextureMetadataErrorSpam=true
gpom.ucw.suppressTextureStitchStdout=true
gpom.crafttweaker.suppressFunctionTypeStdout=true
```

These switches remove known high-volume startup spam while leaving one GPOM summary line for suppressed VintageFix model/texture namespaces. The CTM compatibility switches also let CTM keep reading texture metadata when only the render-layer name is unknown, such as `BLOOM`.

## Baubles Side Slots

```properties
gpom.baubles.sideSlots.enabled=false
gpom.baubles.sideSlots.visibleRows=7
gpom.baubles.sideSlots.columns=2
gpom.baubles.sideSlots.preferRight=false
gpom.baubles.sideSlots.shiftRightClickEquip=true
```

When enabled, GPOM adds Baubles handler slots to the vanilla player inventory container and renders them as a paged Curios-like side panel. The panel defaults to the left of the vanilla inventory, can be paged when the handler has more slots than fit, and uses Baubles' existing slot-type icon art for empty slots.

The Baubles keybind and Baubles inventory button are redirected to the vanilla inventory side panel. Creative players keep the real creative inventory screen, with mirrored Baubles slots removed from the creative survival tab. GPOM intentionally does not use Baubles' expanded inventory screen for this feature because expanded-screen transformers from other mods can fail before GPOM can safely render the UI. Server-side quick-equip validation remains required for shift-click support.

If CosmeticArmorReworked is present, GPOM reflectively syncs its small per-bauble cosmetic visibility toggles with the side rail instead of letting them remain behind in the original expanded-screen positions. Buttons are hidden when the rail is closed. BringMeTheRings and other slot-expansion mods are supported through the real Baubles handler slots, but cosmetic toggles only appear for slots backed by CosmeticArmorReworked's own cosmetic inventory.

## Multipart Compatibility

```properties
gpom.multipartCompat.enabled=false
gpom.multipartCompat.ae2.enabled=false
gpom.multipartCompat.ae2.registerPart=false
gpom.multipartCompat.ae2.placementConverter.enabled=false
gpom.multipartCompat.ae2.sidePartPlacement.enabled=false
gpom.multipartCompat.ae2.blockConverter.enabled=false
gpom.multipartCompat.ae2.disabledWarning.enabled=true
```

The AE2/Forge Multipart bridge is experimental and must remain off for normal play profiles unless a test world is dedicated to that feature. Disabling the bridge after placing bridged parts can make existing multipart data unsafe, so GPOM can warn when the feature is off.

## Safety Model

- GPOM does not open sockets, fetch remote code, or load classes from cache/config paths.
- ASM and reflection targets are hardcoded and exact-version checked where the optimization depends on third-party internals.
- Runtime caches use primitive/NBT data formats, not Java object serialization.
- Common and server bootstrap paths must not directly import client-only classes; GPOM uses sided proxies or reflective side guards for those boundaries.
- Threaded loading is a stability risk, not a security feature. Treat broad `*` allowlists as pack-specific experiments.

## Operational Notes

- Do not replace the GPOM jar while Minecraft is running. Cleanroom can lazily load classes from the jar after startup, so mutating the jar in place can create false classloading failures.
- Helper scripts use `PRISM_ROOT` and `PRISM_INSTANCE_ID` when set; otherwise they default to the common Flatpak PrismLauncher data path and `MeatballCraft, Dimensional Ascension`.
- After changing threaded phase allowlists, validate both menu startup and existing-world entry.
- If a threaded mod fails, put its mod id in that phase's denylist first. Only patch the mod when the failure is stable and worth the gain.
- Use `docs/OPTIMIZATION_LOG.md` for measured changes and `docs/MULTITHREADED_LOADING_FEASIBILITY.md` for threading design notes.

## License

GPOM is licensed under the MIT License. See `LICENSE`.
