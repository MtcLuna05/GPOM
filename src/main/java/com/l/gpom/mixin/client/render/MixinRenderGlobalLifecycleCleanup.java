package com.l.gpom.mixin.client.render;

import com.l.gpom.util.ReflectionFields;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Map;

@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobalLifecycleCleanup {
    @Inject(method = "setWorldAndLoadRenderers", at = @At("HEAD"))
    private void gpom$clearPerWorldStateBeforeWorldChange(WorldClient newWorld, CallbackInfo ci) {
        Object self = this;
        Object currentWorld = ReflectionFields.get(self, "world", "world", "field_72769_h", "k");
        if (currentWorld == null || currentWorld == newWorld) {
            return;
        }

        clearCollection(self, "chunksToUpdate", "chunksToUpdate", "field_175009_l", "l");
        clearCollection(self, "renderInfos", "renderInfos", "field_72755_R", "m");
        clearCollection(self, "setTileEntities", "setTileEntities", "field_181024_n", "n");
        clearMap(self, "damagedBlocks", "damagedBlocks", "field_72738_E", "x");
        clearMap(self, "mapSoundPositions", "mapSoundPositions", "field_147593_P", "y");
        clearCollection(self, "setLightUpdates", "setLightUpdates", "field_184387_ae", "ae");
    }

    private static void clearCollection(Object owner, String purpose, String... names) {
        Object value = ReflectionFields.get(owner, purpose, names);
        if (value instanceof Collection) {
            ((Collection<?>) value).clear();
        }
    }

    private static void clearMap(Object owner, String purpose, String... names) {
        Object value = ReflectionFields.get(owner, purpose, names);
        if (value instanceof Map) {
            ((Map<?, ?>) value).clear();
        }
    }
}
