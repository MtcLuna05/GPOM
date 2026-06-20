# GPOM Feature Catalog

This catalog tracks GPOM features, not only startup optimizations. It is intended as the high-level inventory for maintainers and pack operators. Use `docs/FEATURE_LOG.md` for measured run history, validation notes, crashes, and active decisions.

Current live context, 2026-06-20:

- Target pack: MeatballCraft, Dimensional Ascension on Minecraft 1.12.2 Cleanroom/Forge.
- GPOM jar in the tested Prism instance was rebuilt and installed after the HEI search-race fix with SHA-256 `213b59d613147d6b1593cb4c7a75f837ce0bac737ced29175866ae5b11ee80d0`.
- Probe, deep HEI profiler, runtime sink profiler, and coarse startup-summary output are disabled in the live config for speed. The main-menu startup timer remains enabled.
- `docs/gpom-early.properties` and the live MeatballCraft instance config are aligned to the same fast no-probe profile as of 2026-06-20.
- The baseline from the earlier Celeritas-switch status was `263.251 s` average (`242.169 s`, `290.494 s`, `257.090 s`), so the current average saving is about `77.501 s` or `29.4%`.
- HEI search tree concurrency is intentionally conservative now: `gpom.hei.searchWorkers=1`, `gpom.hei.deferSearchBlock=false` by default, and `gpom.hei.parallelSearchBuild=false` by default. This avoids the HEI `ObjectOpenHashSet`/`StringUtil.intern` race seen on 2026-06-20.

## Configuration Model

GPOM reads `config/gpom-early.properties` before Forge's normal config system exists. The file uses Java properties syntax.

Operational rules:

- Boolean values are `true` or `false`.
- List values are comma-separated.
- Mod-id lists are case-insensitive unless a key explicitly expects class names or script names.
- `*` in an allowlist means every candidate is eligible.
- Denylists always override allowlists.
- Worker values of `0` mean GPOM chooses an automatic bounded worker count.
- Missing keys are appended with defaults on startup so existing configs learn new options without overwriting local edits.
- Broad `*` allowlists are pack-profile choices, not safe universal defaults.
- For namespace/runtime-name compatibility, do not hard-code direct MCP or SRG names only. Try the preferred name and fall back by alternate name, type, or shape when a feature touches obfuscated/deobfuscated Minecraft internals.

Important common keys:

```properties
gpom.logging.enabled=true
gpom.logging.fmlScheduler.enabled=false
gpom.logging.optimizationInfo.enabled=false
gpom.logging.cacheInfo.enabled=false
gpom.logging.asyncProbeLogs.enabled=false
gpom.logging.asyncProbeLogs.queueSize=8192
gpom.cacheInvalidation.denylist=ausm,gpom
```

## Threaded FML Lifecycle Loading

Feature area: startup performance.

GPOM can dispatch Forge lifecycle handlers on a bounded worker pool. The supported phases are Construction, PreInit, Init, PostInit, and LoadComplete. Each phase can be enabled independently, can use dependency-aware DAG scheduling, and has its own allowlist, denylist, worker count, and diagnostic error-continuation flag.

Key files:

- `FmlParallelLoadingScheduler`
- `FmlParallelLoadingContext`
- `FmlConstructionSafety`
- `MixinLoadControllerStartupProfiler`
- `GpomEarlyConfig`

Key config shape:

```properties
fml.parallel.workers=0
fml.parallel.construct.enabled=false
fml.parallel.construct.workers=0
fml.parallel.construct.allowlist=
fml.parallel.construct.denylist=aether_legacy
fml.parallel.construct.continueOnModError=false
fml.parallel.construct.dag.enabled=false
fml.parallel.preInit.enabled=false
fml.parallel.preInit.workers=0
fml.parallel.preInit.allowlist=
fml.parallel.preInit.denylist=<pack denylist>
fml.parallel.preInit.continueOnModError=false
fml.parallel.preInit.dag.enabled=false
fml.parallel.init.enabled=false
fml.parallel.init.workers=0
fml.parallel.init.allowlist=
fml.parallel.init.denylist=<pack denylist>
fml.parallel.init.continueOnModError=false
fml.parallel.init.dag.enabled=false
fml.parallel.postInit.enabled=false
fml.parallel.postInit.workers=0
fml.parallel.postInit.allowlist=*
fml.parallel.postInit.denylist=<pack denylist>
fml.parallel.postInit.continueOnModError=false
fml.parallel.postInit.dag.enabled=false
fml.parallel.loadComplete.enabled=false
fml.parallel.loadComplete.workers=0
fml.parallel.loadComplete.allowlist=
fml.parallel.loadComplete.denylist=
fml.parallel.loadComplete.continueOnModError=false
fml.parallel.loadComplete.dag.enabled=false
fml.parallel.registrySerialization.enabled=true
fml.parallel.clientLifecycleOpenGlScan.enabled=true
fml.parallel.autoQuarantineGlErrors.enabled=false
fml.parallel.autoQuarantineGlErrors.includeRelatedMods=true
```

Behavior details:

- The scheduler creates bounded fixed worker pools rather than one thread per mod.
- Mods are traversed in Forge's original sorted order.
- DAG scheduling preserves declared dependencies and ordering edges while allowing independent handlers to overlap.
- Denylisted or unsafe handlers run on the main thread.
- For Construction, non-threaded handlers act as barriers so in-flight construction workers drain before the serial handler runs.
- Each worker receives a cloned event instance where the event type supports cloning.
- Worker event handlers receive a thread-local active mod container so Forge context is correct for the running mod.
- Mod-state writes are committed deterministically after worker completion.
- Catchable Java failures can optionally be logged and continued for diagnostics, but `continueOnModError` should remain `false` for play.
- Native aborts and `System.exit` cannot be recovered.

Live MeatballCraft posture:

- Broad lifecycle threading is enabled in the live profile with `allowlist=*` and empirical phase denylists.

Risk level: high when broad allowlists are used. Deny failing mods by phase first; patch only stable, high-value failures.

## Construction Annotation and Safety Shortcuts

Feature area: startup performance and Forge compatibility.

GPOM replaces repeated Forge annotation scans and several construction-time slow paths with cached/generic paths that preserve Forge ownership rules.

Key capabilities:

- Builds a global annotation index from `ASMDataTable.getAll(...)` rather than repeatedly scanning per mod container.
- Handles `@NetworkCheckHandler` discovery from already-loaded mod classes and registers default `NetworkModHolder` data when no custom checker exists.
- Handles `@SidedProxy` injection with owner filtering, class loading, type validation, and serialized proxy writes.
- Handles `@Mod.Instance` and `@Mod.Metadata` field injection through cached annotation lookup while preserving Forge assignment semantics.
- Handles `@Mod.EventBusSubscriber` registration with narrowed synchronization around actual listener/EventBus mutation.
- Has exact or generic fallback denylists for mods that need stock Forge behavior.

Key config:

```properties
gpom.construction.genericSidedProxies.denylist=thaumcraft,aether_legacy,architecturecraft
gpom.construction.genericAutomaticSubscribers.denylist=thaumcraft,thaumcraftfix,chisel,ctm,unlimitedchiselworks,thebetweenlands,twilightforest,erebus,plustic,aether_legacy,superfactorymanager
```

Safety fences serialize known shared mutation points:

- `ModClassLoader.addFile`
- `ModClassLoader.clearNegativeCacheFor`
- `MinecraftForge.preloadCrashClasses`
- `NetworkRegistry.register`
- `ProxyInjector.inject`
- EventBus listener registration
- `ConfigManager.sync`
- `FMLModContainer.processFieldAnnotations`

Operational notes:

- Chisel, CTM, Unlimited Chisel Works, Thaumcraft, The Betweenlands, Twilight Forest, Erebus, PlusTiC, Aether Legacy, Super Factory Manager, and similar ordering-sensitive mods should stay on stock subscriber behavior unless retested.
- The current rule for all Minecraft runtime names applies strongly here: prefer try/fallback reflection or type/shape detection over one direct mapped name.

Risk level: medium to high. The generic paths are faster but sit in Forge construction internals.

## Registry Event Parallel Dispatcher

Feature area: startup performance.

This is separate from lifecycle threading. It parallelizes selected `RegistryEvent.Register` listeners while serializing actual registry writes through controlled commit paths.

Key config:

```properties
gpom.registry.parallelRegisterEvents.enabled=false
gpom.registry.parallelRegisterEvents.registries=minecraft:recipes,minecraft:blocks,minecraft:items,minecraft:entities,ebwizardry:spells
gpom.registry.parallelRegisterEvents.recipes.enabled=false
gpom.registry.parallelRegisterEvents.workers=0
gpom.registry.parallelRegisterEvents.queuedCommit=true
gpom.registry.parallelRegisterEvents.proxyEventRegistry=true
gpom.registry.parallelRegisterEvents.proxyEventRegistryDenylist=moarsigns@minecraft:recipes,cyclopscore@minecraft:recipes,integrateddynamics@minecraft:recipes
gpom.registry.parallelRegisterEvents.immediateCommitRegistries=minecraft:items,minecraft:entities
gpom.registry.parallelRegisterEvents.proxyImmediateRegistries=false
gpom.registry.parallelRegisterEvents.orderedWaveRegistries=minecraft:items
gpom.registry.parallelRegisterEvents.immediateCommitWaitDiagnosticsMillis=5000
gpom.registry.parallelRegisterEvents.dependencyGating=true
gpom.registry.parallelRegisterEvents.allowlist=*
gpom.registry.parallelRegisterEvents.denylist=<pack denylist>
gpom.registry.parallelRegisterEvents.deepDiagnostics=false
```

Behavior details:

- Dependency gating avoids running handlers whose dependencies or known ordering constraints are still active.
- Proxy event registries can capture registrations off-thread and commit later.
- Queued commits keep real Forge registry writes on a controlled path.
- Immediate commit registries are used where the proxy path is not safe or where the registry needs immediate visibility.
- Per-mod/per-registry deny entries use `modid@registry:name`.
- `minecraft:recipes` is separately gated and disabled by default because recipe registration has strong ordering/visibility constraints.

Live MeatballCraft posture:

- Registry parallelism is enabled with `workers=6`, broad `allowlist=*`, and a large empirical denylist.
- `minecraft:recipes` parallelism remains disabled by `gpom.registry.parallelRegisterEvents.recipes.enabled=false` even if recipes appear in the registry list.

Risk level: high. Add narrow `modid@registry` denylists for failures rather than disabling the whole dispatcher unless the failure is systemic.

## HEI Startup and Recipe-System Features

Feature area: startup performance and recipe browsing QoL.

GPOM has multiple HEI/JEI paths. Some are pure performance, some are QoL additions.

Core HEI startup keys:

```properties
gpom.hei.recipeProgressBar.enabled=true
gpom.hei.recipeProgressBar.stepSize=256
gpom.hei.searchWorkers=0
gpom.hei.fastPreInitPluginDiscovery.enabled=false
gpom.hei.fastPreInitPluginDiscovery.workers=0
gpom.hei.fastPreInitPluginDiscovery.deepProbes=false
gpom.hei.parallelPluginRegistration.enabled=true
gpom.hei.parallelPluginRegistration.workers=6
gpom.hei.parallelPluginRegistration.overlapSerial=true
gpom.hei.parallelPluginRegistration.allowlist=*
gpom.hei.parallelPluginRegistration.denylist=mezz.jei.plugins.jei.JEIInternalPlugin,mezz.jei.plugins.modsupport.ModSupportPlugin,com.l.gpom.compat.hei.GpomHeiQoLPlugin,lumien.randomthings.handler.compability.jei.RandomThingsPlugin
```

Performance capabilities:

- Fast pre-init plugin discovery defines JEI plugin classes on a bounded worker pool and constructs plugin instances serially.
- Experimental HEI plugin registration can run allowlisted `IModPlugin.register` calls on workers while serial plugins run on the client thread.
- HEI registry surfaces touched by plugin threading are synchronized where GPOM has exact knowledge.
- HEI `RecipeRegistry` ingestion has bulk visible-cache suppression, recipe-handler caching, and known-runtime fast paths.
- Unsupported runtime recipe classes can be skipped when no handler exists, avoiding expensive repeated failed handler lookups.
- `RecipeMap` ingredient-helper subtype and unique-id calls are cached during bulk registry ingestion.
- HEI item-stack list, Forestry, ExtraTrees, EnderIO, Thermal Expansion, JER, and Environmental Tech paths have targeted caches or fast paths.

Current HEI search safety state:

- The 2026-06-20 crash was `ArrayIndexOutOfBoundsException` inside fastutil `ObjectOpenHashSet` from HEI `StringUtil.intern` while `RecipeRegistry.isCategoryVisible` queried search results.
- The cause was unsafe deferred/asynchronous search-tree finalization while HEI queried recipe categories.
- GPOM now defaults `gpom.hei.deferSearchBlock=false` and `gpom.hei.parallelSearchBuild=false` in source, and the live config pins `gpom.hei.searchWorkers=1`.
- Do not re-enable deferred HEI search blocking unless `StringUtil.intern` and the suffix-tree build/query path are made explicitly thread-safe or fully joined before any query.

Targeted HEI caches and fast paths:

```properties
gpom.hei.jerVillagerTradeCache.enabled=true
gpom.hei.jerVillagerTradeCache.samples=32
gpom.hei.jerLootDropCache.enabled=true
gpom.hei.fastForestryBottler.enabled=true
gpom.hei.forestryBottlerRecipeCache.enabled=true
gpom.hei.compressForestryBottlerRecipes.enabled=true
gpom.hei.skipUnsupportedRuntimeRecipes.enabled=true
gpom.hei.skipUnsupportedRuntimeRecipes.classes=
gpom.hei.fastEnderIOTank.enabled=true
gpom.hei.compressEnderIOTankFluidRecipes.enabled=true
gpom.hei.extraTreesLumbermillRecipeCache.enabled=true
gpom.hei.fastThermalTransposerContainers.enabled=true
gpom.hei.thermalTransposerContainerCache.enabled=true
```

QoL features:

```properties
gpom.hei.extendedCraftingLowerTierTransfer.enabled=true
gpom.hei.draconicFusionTransfer.enabled=true
gpom.hei.craftableRecipesFirst.enabled=true
```

QoL details:

- ExtendedCrafting lower-tier transfer adds HEI transfer buttons for lower-tier ExtendedCrafting table recipes in higher-tier table GUIs and maps the lower recipe into the centered sub-grid.
- Draconic Fusion transfer stages catalyst and injector ingredients through a server-validated packet.
- Craftable-recipes-first sorts visible recipe output so recipes whose required item inputs are present in the player's inventory appear first. It intentionally ignores non-item requirements.
- Thaumcraft research-click bridge allows required-research hints to open the relevant HEI/category path where possible.
- Salis Mundus recipe support is provided through the GPOM HEI QoL plugin when the recipe is available and research gating allows display.

Known current sinks after latest probes were disabled:

- HEI LoadComplete remains a major startup sink.
- Latest post-fix no-probe run showed HEI total `21.470 s` and LoadComplete wall `23.824 s`; the previous post-fix run showed HEI `17.700 s` and LoadComplete `19.711 s`.
- Remaining high-value HEI targets include EnderIO Machines, ExtraUtils2, VanillaPlugin/central recipe registry, Environmental Tech VoidMiner, and Thermal Expansion Transposer wrappers.

Risk level: medium to high. Exact plugin threading and recipe registry patches should fail closed and be denied by class or plugin when unstable.

## CraftTweaker Features

Feature area: startup performance and log hygiene.

Key config:

```properties
gpom.crafttweaker.fastZenRegister=false
gpom.crafttweaker.fastZenRegister.parallelClassLoad=false
gpom.crafttweaker.fastZenRegister.classLoadWorkers=0
gpom.crafttweaker.fastZenRegister.deepProbes=false
gpom.crafttweaker.lazyItemList=true
gpom.crafttweaker.suppressFunctionTypeStdout=true
gpom.crafttweaker.parallelScriptParsing.enabled=false
gpom.crafttweaker.parallelScriptParsing.workers=0
gpom.crafttweaker.parallelScriptParsing.allowlist=*
gpom.crafttweaker.parallelScriptParsing.denylist=
gpom.crafttweaker.parallelScriptParsing.offThreadZenParse=false
gpom.crafttweaker.parallelScriptParsing.suppressGlobalDebugCompileLogs=true
gpom.crafttweaker.parallelScriptParsing.batchAllowedScripts=false
gpom.crafttweaker.parallelScriptParsing.deepProbes=false
```

Capabilities:

- Fast ZenRegister handling avoids repeated slow class registration work.
- Optional ZenRegister class loading can define classes on workers but keeps registry writes serial.
- Script source loading can be parallelized by priority bucket while preserving CraftTweaker's priority and alphabetical ordering.
- Off-thread Zen parsing remains disabled by default because parsing can touch shared state depending on scripts and integrations.
- Global function-type stdout spam can be suppressed while keeping useful script errors.
- Lazy item-list handling reduces expensive eager item stack enumeration.

Live MeatballCraft posture:

- Parallel script parsing is enabled with batching.
- `PeriodicTable.zs` is denied after a prior `ore yellorite` input failure.
- `offThreadZenParse=false` remains the safe posture.

Risk level: medium. Parallel source loading is safer than off-thread Zen parsing.

## PreInit Class Prewarm

Feature area: startup performance.

Key config:

```properties
gpom.preInitClassPrewarm.enabled=false
gpom.preInitClassPrewarm.allowlist=
gpom.preInitClassPrewarm.workers=1
gpom.preInitClassPrewarm.deferMinCompletedHandlers=32
gpom.preInitClassPrewarm.deferUntilSerialMillis=1000
gpom.preInitClassPrewarm.pauseDuringSerialHandlers=true
gpom.preInitClassPrewarm.pauseDuringBlockingWaits=true
gpom.preInitClassPrewarm.maxClassesPerMod=384
gpom.preInitClassPrewarm.chunkSize=32
gpom.preInitClassPrewarm.includeAnonClasses=false
gpom.preInitClassPrewarm.extraPrefixes=
gpom.preInitClassPrewarm.noInitAllowlist=
gpom.preInitClassPrewarm.noInitPrefixes=
gpom.preInitClassPrewarm.initializeClasses=false
gpom.preInitClassPrewarm.initializeAllowlist=
gpom.preInitClassPrewarm.explicitClasses=
```

Capabilities:

- Preloads class definitions for allowlisted mods during PreInit so later handlers hit warmer class metadata and IO.
- Can pause during serial handlers to avoid adding CPU contention during main-thread-only work.
- Can use no-static-init class loading by default to avoid executing mod code early.
- Can be configured with extra prefixes and explicit classes for targeted testing.

Live MeatballCraft posture:

- Enabled for a narrow allowlist: `erebus`, `environmentaltech`, `extrautils2`, `integrateddynamics`, `teslacorelib`, and `twilightforest`.
- Uses `2` workers and pauses during serial handlers.
- This contributes to nondeterministic timings but is accepted for speed in the current profile.

Risk level: medium. Keep static initialization disabled unless a specific class set is proven safe.

## Runtime Caches and Cache Invalidation

Feature area: startup performance.

GPOM stores compressed primitive/NBT cache data under the instance cache directory, typically `caches/gpom/<purpose>/`. Caches validate signatures and should fall back to original mod behavior on mismatch.

Key cache areas:

- HEI item-stack list cache.
- HEI/JER villager trade and loot drop caches.
- Forestry Bottler recipe cache.
- ExtraTrees Lumbermill recipe cache.
- Thermal Expansion Transposer container cache.
- Gendustry config cache.
- OpenComputers settings cache.
- SFM lightweight search cache.

Key config:

```properties
gpom.cacheInvalidation.denylist=ausm,gpom
gpom.gendustryConfigCache=true
gpom.openComputersSettingsCache=true
gpom.sfm.lightweightSearchCache.enabled=true
gpom.sfm.lightweightSearchCache.useHeiIngredients=true
gpom.sfm.lightweightSearchCache.workers=0
```

Operational notes:

- `gpom.cacheInvalidation.denylist` should only contain jars that do not affect the content signatures relevant to the cache being protected.
- `ausm,gpom` are denied in the live development profile because both are actively rebuilt and do not register content relevant to most GPOM caches.
- Do not use Java object serialization for cache content.

Risk level: low to medium. Bad cache signatures should fall back, not crash.

## Loading Screen and Main Menu Features

Feature area: user-visible startup/world-load UI.

Key config:

```properties
gpom.earlySplash.enabled=false
gpom.earlySplash.packName=Minecraft
gpom.worldLoadingScreen.enabled=true
gpom.mainMenuStartupTime.enabled=false
```

Capabilities:

- Early splash is an optional pre-Minecraft display for very early launch time before Minecraft creates its display.
- World loading overlay is passive over vanilla world-loading screens. GPOM does not replace or cancel vanilla loading screens; it hooks state and draws over vanilla when vanilla is already rendering.
- The world-loading overlay resets viewport, projection, scissor, depth, fog, lighting, texture, and color state before drawing to avoid top-left/scaled-frame artifacts.
- Main-menu startup timer adds the last measured startup duration to the main menu branding when enabled.
- Custom Main Menu startup overlay injection uses screen object width/height reflection with SRG/MCP fallback rather than direct-only field names.

Live MeatballCraft posture:

- Main-menu startup timer is enabled.
- Non-HEI probes are disabled.
- Forge's startup screen must not be disabled.

Risk level: low to medium. Visual-only code should fail closed and never alter registries, networking, saves, or world generation.

## Startup and Runtime Profilers

Feature area: diagnostics.

Key config:

```properties
gpom.startupProfiler.logs.enabled=false
gpom.startupProfiler.logs.boot.enabled=false
gpom.startupProfiler.logs.phaseLifecycle.enabled=false
gpom.startupProfiler.logs.modDetails.enabled=false
gpom.startupProfiler.logs.phaseSummary.enabled=false
gpom.startupProfiler.logs.phaseDigest.enabled=false
gpom.startupProfiler.logs.memoryDetails.enabled=false
gpom.startupProfiler.logs.probes.enabled=false
gpom.startupProfiler.logs.probeSummary.enabled=false
gpom.startupProfiler.logs.wallDiagnostics.enabled=false
gpom.startupProfiler.logs.stackSamples.enabled=false
gpom.startupProfiler.logs.resourceLoadOrder.enabled=false
gpom.startupProfiler.logs.nonFmlGaps.enabled=false
gpom.startupProfiler.logs.constructCriticalPath.enabled=false
gpom.startupProfiler.logs.preInitCriticalPath.enabled=false
gpom.startupProfiler.logs.loadCompleteCriticalPath.enabled=false
gpom.startupProfiler.logs.postPreInitProbeSummary.enabled=false
gpom.startupProfiler.probeLogs.enabled=false
gpom.startupProfiler.probeHighVolumeEventBusPosts=false
gpom.startupProfiler.probePrefixAllowlist=
gpom.startupProfiler.topCount=40
gpom.startupProfiler.postPreInitProgressBars=true
gpom.startupProfiler.postPreInitProgressSteps=96
gpom.runtimeSinkProfiler.enabled=false
gpom.runtimeSinkProfiler.summaryIntervalSeconds=10
gpom.runtimeSinkProfiler.topCount=12
gpom.runtimeSinkProfiler.slowThresholdMillis=50
gpom.runtimeSinkProfiler.immediateSlowLogs.enabled=true
gpom.runtimeSinkProfiler.forgeEvents.enabled=true
gpom.runtimeSinkProfiler.forgeEvents.profileAll=false
```

Capabilities:

- Phase lifecycle timing.
- Per-mod handler timing.
- Compact phase summaries and digests.
- Critical-path summaries for Construction, PreInit, and LoadComplete DAG scheduling.
- High-volume targeted probes, including HEI, CraftTweaker, OpenComputers, Gendustry, and registry-event paths.
- Async probe logging to prevent logging IO from dominating startup timing.
- Runtime sink profiler for gameplay-time slow forge events and immediate slow logs.
- World lifecycle profiler with delayed memory snapshots and optional deep attribution.

Live MeatballCraft posture:

- Raw probes, HEI hot-method/plugin profilers, runtime sink profiling, async probe logging, and coarse startup summaries were disabled after the 2026-06-20 post-fix measurements to reduce log overhead.
- The main-menu startup timer stays enabled as the low-overhead way to compare total startup.

Risk level: low, but high-volume probes can materially change timing and log size.

## World Lifecycle and Memory Cleanup Features

Feature area: gameplay stability and leak cleanup.

Key config:

```properties
gpom.worldLifecycleProfiler.enabled=false
gpom.worldLifecycleProfiler.forceGcBeforeSnapshots=false
gpom.worldLifecycleProfiler.delayedSnapshotMillis=2000,10000,25000
gpom.worldLifecycleProfiler.deepAttribution.enabled=false
gpom.worldLifecycleProfiler.deepAttribution.maxEntries=8
gpom.journeymap.cleanupLeaks=true
gpom.journeymap.cleanupLeaksOnDimensionHandoff=false
gpom.betterPortals.cleanupClientWorlds=false
```

Capabilities:

- Client world-load, unload, and dimension-switch snapshots for memory/state debugging.
- JourneyMap cleanup on client world unload, disconnect, and Minecraft `loadWorld` handoffs.
- BetterPortals client view-world cleanup through BetterPortals' own reset path when enabled.
- Client-side `World.notifyBlockUpdate` listener snapshot so listeners that remove themselves during chunk data handling cannot shrink the live listener list mid-loop.

JourneyMap cleanup details:

- Stops mapping tasks.
- Clears JourneyMap task queues/caches.
- Releases retained chunk metadata.
- Nulls static world/provider references.
- Runs on the client thread to avoid deadlocks against JourneyMap's own mapping reset.
- No-ops when JourneyMap is absent or internals change.

Risk level: low to medium. Cleanup is reflection-only and optional, but must remain client-thread safe.

## BetterPortals and Aether Compatibility

Feature area: compatibility and stability.

Key config:

```properties
gpom.betterPortals.fixMissingNewTarget=true
gpom.betterPortals.remapLegacyAetherBridge=true
gpom.betterPortals.skipLegacyAetherBridgeIfMissing=true
gpom.betterPortals.fixGuavaAddCallback=true
gpom.betterPortals.skipUnsafeThirdPartyTransition=true
gpom.betterPortals.cleanupClientWorlds=false
gpom.betterPortals.journeymapWaypointTeleportTransition=true
gpom.betterPortals.journeymapWaypointTeleportRequireActiveView=true
```

Capabilities:

- Adds missing `Frustum` target metadata to legacy BetterPortals `@At("NEW")` mixin redirects on newer Mixin stacks.
- Rewrites old two-argument Guava `Futures.addCallback(...)` calls to the executor-taking overload with `MoreExecutors.directExecutor()`.
- Remaps BetterPortals' optional legacy Aether bridge from `com.legacy.aether` to the installed `com.gildedgames.the_aether` package when present.
- Skips the legacy Aether bridge if no compatible Aether classes are present.
- Makes Aether Skyroot-water portal activation temporarily scan as air while BetterPortals links the glowstone frame.
- Suppresses Aether's immediate Skyroot-bucket water placement after a successful BetterPortals portal link so water cannot overwrite the new portal.
- Supports JourneyMap waypoint teleport transitions through BetterPortals where active view requirements are met.

Ownership note:

- GPOM does not own BetterPortals/AUSM see-through rendering semantics. AUSM or BetterPortals config should own see-through portal render compatibility.

Risk level: medium. Patches are narrow and version/shape checked, but they touch portal linking and client-world lifecycle.

## Baubles, Aether, and Cosmetic Armor Side Slots

Feature area: inventory QoL.

Key config:

```properties
gpom.baubles.sideSlots.enabled=false
gpom.baubles.sideSlots.visibleRows=7
gpom.baubles.sideSlots.columns=2
gpom.baubles.sideSlots.preferRight=false
gpom.baubles.sideSlots.shiftRightClickEquip=true
gpom.baubles.sideSlots.aether.enabled=false
gpom.baubles.sideSlots.cosmeticArmor.enabled=false
```

Capabilities:

- Adds real handler-backed Baubles slots to the vanilla player inventory container on both logical sides so server/client slot counts stay symmetric.
- Slots stay hidden until the GPOM side panel opens.
- The Baubles keybind and Baubles inventory button open the vanilla inventory with the GPOM side panel visible.
- Creative players keep the real creative inventory screen; side-rail slots stay hidden unless explicitly open.
- Supports paging and configurable row/column layout.
- Shift-left quick-equip runs only while the rail is open and only from normal inventory/hotbar slots.
- Optional Aether Legacy accessory slots can be appended to the same rail using Aether's native slot validation and icons.
- Optional CosmeticArmorReworked armor slots can be appended to the same rail with server-routed slot writes to avoid ghost stacks.
- CosmeticArmorReworked per-bauble visibility toggles are synced to rail positions and hidden when the rail closes.

Risk level: medium. Container slot count and packet symmetry are critical; keep this disabled by default for broad packs.

## Just Enough Calculation Features

Feature area: crafting QoL.

Key config:

```properties
gpom.jecalculation.pinnedCraftOverlay.enabled=true
gpom.jecalculation.fuzzyVolatileItemNbt.enabled=true
```

Capabilities:

- Adds a pin button to Just Enough Calculation's craft screen.
- When pinned, renders JEC's crafting calculator as a compact draggable mini-window over normal container screens.
- Routes clicks, scroll, and key input only while the pointer/input belongs to the overlay bounds.
- Uses reflection and optional bridges so it no-ops when JEC is absent or internals change.
- Fuzzy volatile item NBT support lets JEC compare volatile-NBT stacks more usefully for craft planning.

Risk level: low to medium. Client-only UI; should fail closed.

## JourneyMap Features

Feature area: map QoL and memory cleanup.

Key config:

```properties
gpom.journeymap.waypointDimensionDropup.enabled=true
gpom.journeymap.cleanupLeaks=true
gpom.journeymap.cleanupLeaksOnDimensionHandoff=false
```

Capabilities:

- Replaces the waypoint manager's dimension cycling button with a scrollable dropup selector.
- Writes JourneyMap's existing selected-dimension field and refreshes through JourneyMap's own filtering path.
- Cleans retained JourneyMap client world references on unload/disconnect/loadWorld handoffs.

Risk level: low to medium. Reflection-only optional integration.

## Framed Block and Rendering Compatibility

Feature area: rendering correctness, hitboxes, and shader ownership.

Key config:

```properties
gpom.architecturecraft.fastShapeLighting=true
gpom.architecturecraft.accurateHitboxes=true
gpom.architecturecraft.parentMaterialOcclusion.enabled=false
gpom.blockcraftery.accurateHitboxes=true
gpom.blockcraftery.parentMaterialOcclusion.enabled=true
gpom.blockcraftery.modelRenderLayerCompat=true
```

Capabilities:

- Blockcraftery accurate hitboxes and copied-material occlusion/side-render behavior.
- ArchitectureCraft accurate hitboxes and optional parent-material occlusion.
- Blockcraftery baked-model render-layer compatibility when AUSM is absent.
- ArchitectureCraft fast shape lighting when AUSM is absent.
- GPOM detects AUSM through classpath resource checks without a hard dependency.

Ownership split with AUSM:

- GPOM owns Blockcraftery and ArchitectureCraft hitbox and placement compatibility.
- AUSM owns shader render semantics when present.
- GPOM skips Blockcraftery baked-model layer ASM and ArchitectureCraft fast-shape-lighting ASM when AUSM is present to avoid double-patching.

Risk level: medium. Rendering and model paths are sensitive to transform order.

## Thaumcraft Features and Fixes

Feature area: gameplay compatibility and HEI integration.

Capabilities:

- Thaumonomicon inventory detection fallback can trigger missing first-step/thaumonomicon state when the normal trigger fails.
- Research reload/debug command work existed during investigation but was removed once no longer needed.
- Thaumcraft HEI research-click bridge can open the relevant recipe/category path from required-research hints where possible.
- Salis Mundus HEI recipe support is provided through the GPOM HEI QoL plugin path.
- Thaumcraft aspect cache investigation added safer refresh behavior around itemstack aspect lookup where values were stale.

Operational note:

- Any Thaumcraft/NBT/player-data integration must avoid direct-only MCP/SRG names. Use try/fallback lookup, NBT method alternatives, and graceful no-op behavior.

Risk level: medium. Research state touches player data and client-server sync.

## SFM Lightweight Search Cache

Feature area: gameplay startup/login performance.

Key config:

```properties
gpom.sfm.lightweightSearchCache.enabled=true
gpom.sfm.lightweightSearchCache.useHeiIngredients=true
gpom.sfm.lightweightSearchCache.workers=0
```

Capabilities:

- Replaces or defers Super Factory Manager's heavy search cache path with a lightweight cache builder.
- Can seed from HEI ingredients when available.
- Uses bounded worker threads for cache construction.
- Mixins/transformers no-op when SFM classes are absent.

Known past issue:

- A previous crash showed `NoClassDefFoundError: vswe/superfactory/util/SearchUtil`; SFM hooks must only apply when the class resource is present.

Risk level: medium. Item-search cache speedups must not lose items or build from incomplete ingredient lists.

## AE2 and Pattern Diagnostics

Feature area: diagnostics and compatibility.

Key config:

```properties
gpom.ae2.patternDiagnostics.enabled=false
gpom.ae2.patternDiagnostics.maxFailures=200
gpom.ae2.patternDiagnostics.logMismatchedOutputs=true
gpom.ae2.patternDiagnostics.skipRecipeFunctions=true
```

Capabilities:

- Logs AE2 pattern diagnostic failures up to a configured cap.
- Can log mismatched outputs and skip recipe function paths during diagnostics.
- AE2 Mouse Tweaks terminal compatibility was removed from GPOM after it proved unnecessary/fragile.

Risk level: low when diagnostics are disabled; medium when probing recipe function paths.

## Missing Mapping and Registry Repair Features

Feature area: world/save compatibility.

Key config:

```properties
gpom.enderio.repairMissingTileEntityMappings=true
gpom.registry.repairThaumicWondersMissingMappings=false
gpom.registry.ignoreMissingSoundEventNamespaces=erebus
gpom.registry.failMissingBlockItemNamespaces=
```

Capabilities:

- Repairs missing EnderIO tile entity class-to-id mappings from EnderIO's own metadata during late startup.
- Can ignore stale missing sound events for configured namespaces, currently `erebus`, to avoid hidden Forge confirmation gates for removed sounds.
- Can fail hard on missing block/item namespaces when configured for diagnostics.
- Thaumic Wonders missing mapping repair remains disabled by default because prior testing showed those missing registries were intentionally disabled and not the cause of the observed issue.

Risk level: medium to high. Block/item mapping behavior can affect existing worlds; sound-event ignores are lower risk.

## Log and Error Spam Suppression

Feature area: log hygiene and startup IO reduction.

Key config:

```properties
gpom.vintageFix.suppressUcwModelErrorSpam=true
gpom.vintageFix.skipUcwDefinitionEarlyModelLoad=true
gpom.ctm.tolerateUnknownRenderLayer=true
gpom.ctm.suppressTextureMetadataErrorSpam=true
gpom.ucw.suppressTextureStitchStdout=true
gpom.crafttweaker.suppressFunctionTypeStdout=true
```

Capabilities:

- Replaces repeated VintageFix/Unlimited Chisel Works model stack traces with one concise namespace summary.
- Skips some early UCW definition model loads that only generate repeated missing model noise.
- Lets CTM tolerate unknown render-layer names such as `BLOOM` and continue texture metadata processing.
- Suppresses CTM texture metadata spam and UCW texture-stitch stdout chatter.
- Suppresses CraftTweaker function-type stdout spam while preserving useful errors.

Risk level: low. Suppression should preserve at least one useful summary line.

## Mod-Specific Startup Optimizations

Feature area: startup performance.

Implemented or configured mod-specific paths include:

- AgriCraft fast JSON IO, fast resource scanning, and optional channel refresh after bulk placement.
- Astral Sorcery deferred asset library reload.
- Betweenlands startup probes and targeted class/resource profiling.
- EnderIO fast spawner entity validation and HEI tank grouping/fast paths.
- Erebus deferred composter registry and ore config work.
- Gendustry config cache and optional call profiler.
- NuclearCraft manufactory/log-crafting result caches and optional fast metal recipes.
- OpenComputers settings cache, fast Lua architecture selection, and optional call/integration profilers.
- Railcraft lazy item conditions, module-container deferral controls, and lazy cart config.
- Scannable redundant config ore-cache rebuild skip.
- LoliASM thread-safe crash-state registry replacement.

Representative keys:

```properties
gpom.agricraft.fastJsonIo=true
gpom.agricraft.fastResourceScan=true
gpom.agricraft.skipJsonWriteback=true
gpom.agricraft.refreshChannelsAfterBulkPlacement=false
gpom.astralSorcery.deferAssetLibraryReload=true
gpom.enderio.fastSpawnerEntityValidation=true
gpom.erebus.deferComposterRegistry=true
gpom.erebus.deferOreConfigs=true
gpom.gendustryConfigCache=true
gpom.nuclearcraft.cacheManufactoryLogCraftingResults=true
gpom.openComputersSettingsCache=true
gpom.openComputers.fastLuaSelection=true
gpom.railcraftLazyItemConditions=false
gpom.railcraft.deferModuleIC2Containers=false
gpom.railcraft.deferModuleContainers=false
gpom.railcraft.deferSelectedModuleContainers=false
gpom.railcraft.lazyCartConfig=false
gpom.scannable.skipRedundantConfigOreCacheRebuilds=true
gpom.loliasm.threadSafeStatefulRegistry=true
```

Risk level: varies by feature. Exact-version ASM should verify jar/class shape and fail closed.

## Safety Model

- GPOM does not fetch remote code.
- GPOM does not load executable code from runtime cache/config paths.
- Runtime caches use primitive/NBT-style data, not Java object serialization.
- Client-only classes must not load from common or dedicated-server paths.
- Optional integrations should detect mod classes/resources before transforming or registering.
- Exact-version patches should check target class/jar/bytecode shape and fail closed.
- Minecraft runtime names must be handled with try/fallback logic instead of direct-only MCP or SRG names.
- Broad threading and registry parallelism are pack-specific experiments, not universal safe defaults.
- Never overwrite the installed GPOM jar while Minecraft is running; Cleanroom can lazily read classes from the jar and observe a mutated file.
