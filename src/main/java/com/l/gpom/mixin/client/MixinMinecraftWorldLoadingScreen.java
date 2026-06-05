package com.l.gpom.mixin.client;

import com.l.gpom.client.GpomWorldLoadingScreen;
import com.l.gpom.client.WorldLoadingProgress;
import net.minecraft.client.LoadingScreenRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenWorking;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.WorldSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(value = Minecraft.class, remap = false)
public abstract class MixinMinecraftWorldLoadingScreen {
    private static Field gpom$loadingScreenField;
    private static Field gpom$worldField;
    private static Field gpom$playerField;
    private static Field gpom$currentScreenField;
    private static Method gpom$displayGuiScreenMethod;
    private static Method gpom$displaySavingStringMethod;
    private static Method gpom$displayLoadingStringMethod;

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
        if (!WorldLoadingProgress.isActive()) {
            return;
        }
        if (worldClient == null) {
            gpom$drawImmediate("Closing previous world", "Clearing old client world", 18);
        } else {
            gpom$drawImmediate("Creating client world", "Binding world renderer and particle systems", 72);
        }
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
        if (worldClient != null) {
            gpom$drawImmediate("Loading renderers", "Preparing chunk render state", 78);
        }
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
        gpom$drawImmediate("Creating player", "Applying capabilities and client state", 84);
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
        if (worldClient != null) {
            gpom$drawImmediate("Entering world", "Waiting for first client frame", 96);
        }
    }

    @Inject(
            method = {
                    "displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V",
                    "func_147108_a(Lnet/minecraft/client/gui/GuiScreen;)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void gpom$keepWorldLoadingScreenVisible(GuiScreen screen, CallbackInfo ci) {
        if (WorldLoadingProgress.isActive()
                && !(screen instanceof GpomWorldLoadingScreen)
                && gpom$shouldReplaceScreen(screen)) {
            gpom$installWorldLoadingScreen();
            gpom$drawImmediate("Loading world", "Preparing client handoff", -1);
            ci.cancel();
            return;
        }

        if (screen == null && WorldLoadingProgress.isActive() && gpom$getWorld() != null) {
            gpom$drawImmediate("Entering world", "Waiting for first client frame", 98);
        }
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
        if (WorldLoadingProgress.isActive()
                && gpom$getWorld() != null
                && gpom$getPlayer() != null
                && gpom$getCurrentScreen() == null) {
            WorldLoadingProgress.finish("client tick has world and player");
        }
    }

    private void gpom$drawImmediate(String stage, String detail, int progress) {
        if (!WorldLoadingProgress.isActive()) {
            return;
        }
        WorldLoadingProgress.update(stage, detail, progress);
        LoadingScreenRenderer renderer = gpom$getLoadingScreen();
        if (renderer != null) {
            gpom$callLoadingScreen(renderer, stage, detail);
        }
        WorldLoadingProgress.safeRenderCurrentMinecraft(progress, true);
    }

    private void gpom$showWorldLoadingScreen() {
        if (!WorldLoadingProgress.isActive()) {
            return;
        }

        try {
            GuiScreen current = gpom$getCurrentScreen();
            if (current instanceof GpomWorldLoadingScreen) {
                return;
            }

            Method method = gpom$displayGuiScreenMethod;
            if (method == null) {
                method = gpom$findMethod(Minecraft.class, new Class<?>[]{GuiScreen.class}, "func_147108_a", "displayGuiScreen");
                gpom$displayGuiScreenMethod = method;
            }
            method.invoke(this, new GpomWorldLoadingScreen());
        } catch (Throwable ignored) {
        }
    }

    private boolean gpom$shouldReplaceScreen(GuiScreen screen) {
        if (screen instanceof GuiDownloadTerrain || screen instanceof GuiScreenWorking) {
            return true;
        }
        return false;
    }

    private void gpom$installWorldLoadingScreen() {
        try {
            GuiScreen replacement = new GpomWorldLoadingScreen();
            Field field = gpom$currentScreenField;
            if (field == null) {
                field = gpom$findMinecraftField("field_71462_r", "currentScreen");
                gpom$currentScreenField = field;
            }
            field.set(this, replacement);

            Minecraft minecraft = (Minecraft) (Object) this;
            ScaledResolution resolution = new ScaledResolution(minecraft);
            replacement.setWorldAndResolution(
                    minecraft,
                    Math.max(1, resolution.getScaledWidth()),
                    Math.max(1, resolution.getScaledHeight())
            );
        } catch (Throwable ignored) {
        }
    }

    private static void gpom$callLoadingScreen(LoadingScreenRenderer renderer, String title, String detail) {
        try {
            Method method = gpom$displaySavingStringMethod;
            if (method == null) {
                method = gpom$findMethod(renderer.getClass(), new Class<?>[]{String.class}, "func_73720_a", "displaySavingString");
                gpom$displaySavingStringMethod = method;
            }
            method.invoke(renderer, title);
        } catch (Throwable ignored) {
        }

        try {
            Method method = gpom$displayLoadingStringMethod;
            if (method == null) {
                method = gpom$findMethod(renderer.getClass(), new Class<?>[]{String.class}, "func_73719_c", "displayLoadingString");
                gpom$displayLoadingStringMethod = method;
            }
            method.invoke(renderer, detail);
        } catch (Throwable ignored) {
        }
    }

    private LoadingScreenRenderer gpom$getLoadingScreen() {
        try {
            Field field = gpom$loadingScreenField;
            if (field == null) {
                field = gpom$findMinecraftField("field_71461_s", "loadingScreen");
                gpom$loadingScreenField = field;
            }
            return (LoadingScreenRenderer) field.get(this);
        } catch (Throwable ignored) {
            return null;
        }
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

    private static Method gpom$findMethod(Class<?> owner, Class<?>[] parameterTypes, String... names) throws NoSuchMethodException {
        for (String name : names) {
            try {
                Method method = owner.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(owner.getName() + "#" + String.join("/", names));
    }
}
