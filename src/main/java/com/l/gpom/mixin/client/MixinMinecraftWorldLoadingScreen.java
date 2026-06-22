package com.l.gpom.mixin.client;

import com.l.gpom.client.WorldLoadingProgress;
import com.l.gpom.client.ClientDimensionHandoffCleanup;
import com.l.gpom.client.RenderUpdateDeduplicator;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.compat.betterportals.BetterPortalsClientWorldCleanup;
import com.l.gpom.compat.journeymap.JourneyMapLeakCleanup;
import com.l.gpom.profiling.RuntimeSinkProfiler;
import com.l.gpom.profiling.WorldLifecycleProfiler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenWorking;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.WorldSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(value = Minecraft.class, remap = false)
public abstract class MixinMinecraftWorldLoadingScreen {
    private static Field gpom$worldField;
    private static Field gpom$playerField;
    private static Field gpom$currentScreenField;
    private long gpom$loadWorldStartedAt;
    private long gpom$loadRenderersStartedAt;
    private long gpom$createPlayerStartedAt;
    private long gpom$runTickStartedAt;
    private long gpom$runGameLoopStartedAt;

    @Inject(
            method = {
                    "runGameLoop()V",
                    "func_71411_J()V"
            },
            at = @At("HEAD"),
            require = 0
    )
    private void gpom$beginRunGameLoopProbe(CallbackInfo ci) {
        gpom$runGameLoopStartedAt = RuntimeSinkProfiler.begin();
    }

    @Inject(
            method = {
                    "runGameLoop()V",
                    "func_71411_J()V"
            },
            at = @At("RETURN"),
            require = 0
    )
    private void gpom$endRunGameLoopProbe(CallbackInfo ci) {
        RuntimeSinkProfiler.end("minecraft", "runGameLoop frame", gpom$runGameLoopStartedAt);
        gpom$runGameLoopStartedAt = 0L;
    }

    @Inject(
            method = {
                    "launchIntegratedServer(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;)V",
                    "func_71371_a(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;)V"
            },
            at = @At("HEAD"),
            require = 0
    )
    private void gpom$beginIntegratedWorldLoad(String folderName, String worldName, WorldSettings settings, CallbackInfo ci) {
        WorldLoadingProgress.beginIntegrated(folderName, worldName);
        gpom$drawImmediate("Loading world", "Preparing save", 8);
    }

    @Inject(
            method = {
                    "launchIntegratedServer(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;)V",
                    "func_71371_a(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/integrated/IntegratedServer;startServerThread()V"),
            require = 0
    )
    private void gpom$startingIntegratedServer(String folderName, String worldName, WorldSettings settings, CallbackInfo ci) {
        gpom$drawImmediate("Starting integrated server", "Launching server thread", 28);
    }

    @Inject(
            method = {
                    "launchIntegratedServer(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;)V",
                    "func_71371_a(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/integrated/IntegratedServer;serverIsInRunLoop()Z"),
            require = 0
    )
    private void gpom$waitingForServerRunLoop(String folderName, String worldName, WorldSettings settings, CallbackInfo ci) {
        gpom$drawImmediate("Waiting for server", "Preparing dimensions and spawn data", 42);
    }

    @Inject(
            method = {
                    "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V",
                    "func_71353_a(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V"
            },
            at = @At("HEAD"),
            require = 0
    )
    private void gpom$loadWorldStarted(WorldClient worldClient, String message, CallbackInfo ci) {
        gpom$loadWorldStartedAt = RuntimeSinkProfiler.begin();
        WorldClient previousWorld = gpom$getWorld();
        WorldLifecycleProfiler.beginLoadWorld((Minecraft) (Object) this, previousWorld, worldClient, message);
        if (previousWorld != null && previousWorld != worldClient) {
            ClientDimensionHandoffCleanup.cleanup("Minecraft loadWorld boundary");
            BetterPortalsClientWorldCleanup.cleanup("Minecraft loadWorld boundary");
            if (GpomEarlyConfig.journeyMapCleanupLeaksOnDimensionHandoffEnabled()) {
                JourneyMapLeakCleanup.cleanup("Minecraft loadWorld boundary");
            } else {
                JourneyMapLeakCleanup.suppressClientWorldUnloadCleanup("Minecraft loadWorld boundary");
            }
        }
        if (worldClient == null) {
            if (!WorldLoadingProgress.isActive()) {
                WorldLoadingProgress.beginLeaving(message);
            }
            gpom$drawImmediate("Closing previous world", "Clearing old client world", 18);
            return;
        }

        if (previousWorld != null && previousWorld != worldClient && !WorldLoadingProgress.isActive()) {
            WorldLoadingProgress.beginDimensionSwitch(gpom$worldLabel(previousWorld), gpom$worldLabel(worldClient));
            gpom$drawImmediate("Changing dimension", "Preparing client handoff", 54);
            return;
        }

        if (!WorldLoadingProgress.isActive()) {
            WorldLoadingProgress.beginClientWorld(gpom$worldLabel(worldClient), message, 64);
        }
        gpom$drawImmediate("Creating client world", "Binding world renderer and particle systems", 72);
    }

    @Inject(
            method = {
                    "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V",
                    "func_71353_a(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;setWorldAndLoadRenderers(Lnet/minecraft/client/multiplayer/WorldClient;)V"),
            require = 0
    )
    private void gpom$loadRenderers(WorldClient worldClient, String message, CallbackInfo ci) {
        gpom$loadRenderersStartedAt = RuntimeSinkProfiler.begin();
        if (worldClient != null) {
            gpom$drawImmediate("Loading renderers", "Preparing chunk render state", 78);
        }
    }

    @Inject(
            method = {
                    "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V",
                    "func_71353_a(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;setWorldAndLoadRenderers(Lnet/minecraft/client/multiplayer/WorldClient;)V", shift = At.Shift.AFTER),
            require = 0
    )
    private void gpom$loadedRenderers(WorldClient worldClient, String message, CallbackInfo ci) {
        RuntimeSinkProfiler.end("worldLoad", "RenderGlobal.setWorldAndLoadRenderers", gpom$loadRenderersStartedAt);
        gpom$loadRenderersStartedAt = 0L;
    }

    @Inject(
            method = {
                    "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V",
                    "func_71353_a(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;createPlayer(Lnet/minecraft/world/World;Lnet/minecraft/stats/StatisticsManager;Lnet/minecraft/stats/RecipeBook;)Lnet/minecraft/client/entity/EntityPlayerSP;"),
            require = 0
    )
    private void gpom$createClientPlayer(WorldClient worldClient, String message, CallbackInfo ci) {
        gpom$createPlayerStartedAt = RuntimeSinkProfiler.begin();
        gpom$drawImmediate("Creating player", "Applying capabilities and client state", 84);
    }

    @Inject(
            method = {
                    "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V",
                    "func_71353_a(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;createPlayer(Lnet/minecraft/world/World;Lnet/minecraft/stats/StatisticsManager;Lnet/minecraft/stats/RecipeBook;)Lnet/minecraft/client/entity/EntityPlayerSP;", shift = At.Shift.AFTER),
            require = 0
    )
    private void gpom$createdClientPlayer(WorldClient worldClient, String message, CallbackInfo ci) {
        RuntimeSinkProfiler.end("worldLoad", "PlayerControllerMP.createPlayer", gpom$createPlayerStartedAt);
        gpom$createPlayerStartedAt = 0L;
    }

    @Inject(
            method = {
                    "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V",
                    "func_71353_a(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V"
            },
            at = @At("RETURN"),
            require = 0
    )
    private void gpom$loadWorldFinished(WorldClient worldClient, String message, CallbackInfo ci) {
        RuntimeSinkProfiler.end("worldLoad", "Minecraft.loadWorld " + gpom$worldLoadLabel(worldClient, message), gpom$loadWorldStartedAt);
        gpom$loadWorldStartedAt = 0L;
        WorldLifecycleProfiler.endLoadWorld((Minecraft) (Object) this, gpom$getWorld(), gpom$getPlayer(), gpom$getCurrentScreen());
        if (worldClient != null) {
            gpom$drawImmediate("Entering world", "Waiting for first client frame", 96);
            WorldLoadingProgress.markWaitingForFirstWorldRender();
        }
    }

    @Inject(
            method = {
                    "displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V",
                    "func_147108_a(Lnet/minecraft/client/gui/GuiScreen;)V"
            },
            at = @At("HEAD"),
            require = 0
    )
    private void gpom$observeWorldLoadingScreenChange(GuiScreen screen, CallbackInfo ci) {
        if (screen == null && WorldLoadingProgress.isSuspendedForBlockingScreen()) {
            WorldLoadingProgress.resumeAfterBlockingScreen("Loading world", "Continuing after Forge confirmation", 42);
            return;
        }

        if (WorldLoadingProgress.isActive() && WorldLoadingProgress.isBlockingForgeScreen(screen)) {
            WorldLoadingProgress.suspendForBlockingScreen("Forge blocking screen displayed: " + screen.getClass().getName());
            return;
        }

        if (gpom$isMainMenuScreen(screen) && WorldLoadingProgress.isActive()) {
            gpom$drawImmediate("Returning to menu", "Main menu ready", 100);
            WorldLoadingProgress.finish("main menu displayed");
            return;
        }

        if (WorldLoadingProgress.isActive() && gpom$isVanillaWorldLoadingScreen(screen)) {
            gpom$drawImmediate("Loading world", "Preparing client handoff", -1);
            return;
        }

        if (!WorldLoadingProgress.isActive() && gpom$isVanillaWorldLoadingScreen(screen)) {
            WorldLoadingProgress.beginVanillaLoadingIfNeeded(
                    "Minecraft.displayGuiScreen(" + screen.getClass().getSimpleName() + ")",
                    "Loading world",
                    "Preparing client handoff",
                    -1
            );
            gpom$drawImmediate("Loading world", "Preparing client handoff", -1);
            return;
        }

        if (screen == null && WorldLoadingProgress.isActive() && gpom$getWorld() != null) {
            gpom$drawImmediate("Entering world", "Waiting for first client frame", 98);
            WorldLoadingProgress.markWaitingForFirstWorldRender();
        }
    }

    @Inject(
            method = {
                    "displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V",
                    "func_147108_a(Lnet/minecraft/client/gui/GuiScreen;)V"
            },
            at = @At("RETURN"),
            require = 0
    )
    private void gpom$renderResumedWorldLoadingScreen(GuiScreen screen, CallbackInfo ci) {
        if (screen == null && WorldLoadingProgress.isActive() && gpom$getWorld() == null) {
            gpom$drawImmediate("Loading world", "Continuing after Forge confirmation", 42);
        }
    }

    @Inject(
            method = {
                    "runTick()V",
                    "func_71407_l()V"
            },
            at = @At("HEAD"),
            require = 0
    )
    private void gpom$beginRunTickProbe(CallbackInfo ci) {
        RenderUpdateDeduplicator.nextClientTick();
        gpom$runTickStartedAt = RuntimeSinkProfiler.begin();
    }

    @Inject(
            method = {
                    "runTick()V",
                    "func_71407_l()V"
            },
            at = @At("RETURN"),
            require = 0
    )
    private void gpom$finishWorldLoadingAfterClientTick(CallbackInfo ci) {
        RuntimeSinkProfiler.end("minecraft", "runTick client", gpom$runTickStartedAt);
        gpom$runTickStartedAt = 0L;
        WorldLifecycleProfiler.clientTick((Minecraft) (Object) this);
        if (WorldLoadingProgress.isActive()
                && gpom$getWorld() != null
                && gpom$getPlayer() != null
                && gpom$getCurrentScreen() == null) {
            WorldLoadingProgress.markWaitingForFirstWorldRender();
            if (WorldLoadingProgress.finishIfFirstWorldRenderTimedOut(15_000L)) {
                return;
            }
        }
        if (WorldLoadingProgress.isActive()) {
            WorldLoadingProgress.safeRenderCurrentMinecraft(-1, false);
        }
    }

    private void gpom$drawImmediate(String stage, String detail, int progress) {
        if (!WorldLoadingProgress.isActive()) {
            return;
        }
        if (!WorldLoadingProgress.canRenderOverCurrentScreen()) {
            return;
        }
        WorldLoadingProgress.update(stage, detail, progress);
        WorldLoadingProgress.safeRenderCurrentMinecraft(progress, true);
    }

    private boolean gpom$isVanillaWorldLoadingScreen(GuiScreen screen) {
        return screen instanceof GuiDownloadTerrain || screen instanceof GuiScreenWorking;
    }

    private boolean gpom$isMainMenuScreen(GuiScreen screen) {
        if (screen instanceof GuiMainMenu) {
            return true;
        }
        return screen != null && screen.getClass().getName().equals("lumien.custommainmenu.gui.GuiCustom");
    }

    private WorldClient gpom$getWorld() {
        try {
            Field field = gpom$worldField;
            if (field == null) {
                field = gpom$findMinecraftField("field_71441_e", "world");
                gpom$worldField = field;
            }
            return (WorldClient) field.get(this);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object gpom$getPlayer() {
        try {
            Field field = gpom$playerField;
            if (field == null) {
                field = gpom$findMinecraftField("field_71439_g", "player");
                gpom$playerField = field;
            }
            return field.get(this);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private GuiScreen gpom$getCurrentScreen() {
        try {
            Field field = gpom$currentScreenField;
            if (field == null) {
                field = gpom$findMinecraftField("field_71462_r", "currentScreen");
                gpom$currentScreenField = field;
            }
            return (GuiScreen) field.get(this);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String gpom$worldLabel(WorldClient world) {
        if (world == null) {
            return "";
        }
        try {
            return "Dimension " + world.provider.getDimension();
        } catch (Throwable ignored) {
            return world.getClass().getSimpleName();
        }
    }

    private String gpom$worldLoadLabel(WorldClient world, String message) {
        if (world == null) {
            return message == null || message.isEmpty() ? "unload" : "unload message=\"" + message + "\"";
        }
        String label = gpom$worldLabel(world);
        if (message == null || message.isEmpty()) {
            return label;
        }
        return label + " message=\"" + message + "\"";
    }

    private static Field gpom$findMinecraftField(String... names) throws NoSuchFieldException {
        Class<?> type = Minecraft.class;
        for (String name : names) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(String.join("/", names));
    }
}
