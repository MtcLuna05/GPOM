package com.l.gpom.mixin.client;

import com.l.gpom.compat.betterportals.BetterPortalsAetherPortalBreakCleanup;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.List;

@Mixin(value = World.class, remap = false)
public abstract class MixinWorldNotifyBlockUpdateSnapshot {
    private static Method gpom$listenerNotifyBlockUpdateMethod;
    private static MethodHandle gpom$listenerNotifyBlockUpdateHandle;
    private static boolean gpom$listenerNotifyBlockUpdateUnavailable;
    private static final ThreadLocal<IWorldEventListener[]> gpom$listenerSnapshot = ThreadLocal.withInitial(() -> new IWorldEventListener[8]);

    @Shadow(aliases = "eventListeners")
    protected List<IWorldEventListener> field_73021_x;

    @Inject(
            method = {
                    "notifyBlockUpdate(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/block/state/IBlockState;I)V",
                    "func_184138_a(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/block/state/IBlockState;I)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void gpom$snapshotBlockUpdateListeners(BlockPos pos, IBlockState oldState, IBlockState newState, int flags, CallbackInfo ci) {
        BetterPortalsAetherPortalBreakCleanup.onBlockUpdate((World) (Object) this, pos, oldState, newState);

        if (!GpomEarlyConfig.fastNotifyBlockUpdateEnabled()) {
            return;
        }

        MethodHandle notifyHandle = gpom$listenerNotifyBlockUpdateHandle();
        if (notifyHandle == null) {
            return;
        }

        List<IWorldEventListener> listeners = field_73021_x;
        if (listeners == null) {
            return;
        }

        int size = listeners.size();
        if (size <= 0) {
            ci.cancel();
            return;
        }

        IWorldEventListener[] snapshot = gpom$snapshotListeners(listeners, size);
        if (snapshot == null) {
            return;
        }
        for (int i = 0; i < size; i++) {
            IWorldEventListener listener = snapshot[i];
            if (listener != null) {
                gpom$notifyBlockUpdate(notifyHandle, listener, (World) (Object) this, pos, oldState, newState, flags);
                snapshot[i] = null;
            }
        }
        ci.cancel();
    }

    private static IWorldEventListener[] gpom$snapshotListeners(List<IWorldEventListener> listeners, int size) {
        IWorldEventListener[] snapshot = gpom$listenerSnapshot.get();
        if (snapshot.length < size) {
            snapshot = new IWorldEventListener[Math.max(size, snapshot.length * 2)];
            gpom$listenerSnapshot.set(snapshot);
        }
        try {
            for (int i = 0; i < size; i++) {
                snapshot[i] = listeners.get(i);
            }
        } catch (RuntimeException exception) {
            for (int i = 0; i < size && i < snapshot.length; i++) {
                snapshot[i] = null;
            }
            return null;
        }
        return snapshot;
    }

    private static MethodHandle gpom$listenerNotifyBlockUpdateHandle() {
        if (gpom$listenerNotifyBlockUpdateUnavailable) {
            return null;
        }
        MethodHandle handle = gpom$listenerNotifyBlockUpdateHandle;
        if (handle != null) {
            return handle;
        }
        Method method = gpom$listenerNotifyBlockUpdateMethod();
        if (method == null) {
            return null;
        }
        try {
            handle = MethodHandles.publicLookup().unreflect(method);
        } catch (IllegalAccessException exception) {
            try {
                handle = MethodHandles.lookup().unreflect(method);
            } catch (IllegalAccessException ignored) {
                gpom$listenerNotifyBlockUpdateUnavailable = true;
                return null;
            }
        }
        gpom$listenerNotifyBlockUpdateHandle = handle;
        return handle;
    }

    private static Method gpom$listenerNotifyBlockUpdateMethod() {
        if (gpom$listenerNotifyBlockUpdateUnavailable) {
            return null;
        }
        Method method = gpom$listenerNotifyBlockUpdateMethod;
        if (method != null) {
            return method;
        }
        method = gpom$findListenerNotifyBlockUpdateMethod("func_184376_a");
        if (method == null) {
            method = gpom$findListenerNotifyBlockUpdateMethod("notifyBlockUpdate");
        }
        if (method == null) {
            gpom$listenerNotifyBlockUpdateUnavailable = true;
            return null;
        }
        method.setAccessible(true);
        gpom$listenerNotifyBlockUpdateMethod = method;
        return method;
    }

    private static Method gpom$findListenerNotifyBlockUpdateMethod(String name) {
        try {
            return IWorldEventListener.class.getDeclaredMethod(
                    name,
                    World.class,
                    BlockPos.class,
                    IBlockState.class,
                    IBlockState.class,
                    int.class
            );
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static void gpom$notifyBlockUpdate(MethodHandle method, IWorldEventListener listener, World world, BlockPos pos, IBlockState oldState, IBlockState newState, int flags) {
        try {
            method.invoke(listener, world, pos, oldState, newState, flags);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Error error) {
            throw error;
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

}
