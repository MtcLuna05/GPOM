package com.l.gpom.mixin.client;

import com.l.gpom.client.ClientNullPlayerStateGuard;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Queue;

@Mixin(value = Minecraft.class, remap = false)
public abstract class MixinMinecraftScheduledTaskNullPlayerGuard {
    @Redirect(
            method = {
                    "runGameLoop()V",
                    "func_71411_J()V"
            },
            at = @At(value = "INVOKE", target = "Ljava/util/Queue;isEmpty()Z"),
            require = 0
    )
    private boolean gpom$pauseClientScheduledTasksUntilPlayerExists(Queue<?> queue) {
        if (ClientNullPlayerStateGuard.isWorldBoundWithoutPlayer((Minecraft) (Object) this)) {
            return true;
        }
        return queue.isEmpty();
    }
}
