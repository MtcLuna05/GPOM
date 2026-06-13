package com.l.gpom.mixin.client;

import com.l.gpom.compat.betterportals.BetterPortalsAetherPortalBreakCleanup;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Mixin(value = World.class, remap = false)
public abstract class MixinWorldNotifyBlockUpdateSnapshot {
    private static Method gpom$listenerNotifyBlockUpdateMethod;
    private static boolean gpom$listenerNotifyBlockUpdateUnavailable;

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

        Method notifyMethod = gpom$listenerNotifyBlockUpdateMethod();
        if (notifyMethod == null) {
            return;
        }

        List<IWorldEventListener> snapshot = new ArrayList<>(field_73021_x);
        for (IWorldEventListener listener : snapshot) {
            gpom$notifyBlockUpdate(notifyMethod, listener, (World) (Object) this, pos, oldState, newState, flags);
        }
        ci.cancel();
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

    private static void gpom$notifyBlockUpdate(Method method, IWorldEventListener listener, World world, BlockPos pos, IBlockState oldState, IBlockState newState, int flags) {
        try {
            method.invoke(listener, world, pos, oldState, newState, flags);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RuntimeException(cause);
        } catch (IllegalAccessException exception) {
            throw new RuntimeException(exception);
        }
    }

}
