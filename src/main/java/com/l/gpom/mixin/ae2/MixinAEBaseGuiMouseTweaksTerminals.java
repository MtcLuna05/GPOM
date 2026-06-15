package com.l.gpom.mixin.ae2;

import com.l.gpom.compat.ae2.MouseTweaksAe2TerminalCompat;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(targets = "appeng.client.gui.AEBaseGui", remap = false)
public abstract class MixinAEBaseGuiMouseTweaksTerminals {
    /**
     * AE2 UEL ships Mouse Tweaks API hooks but disables them globally. Enable the hook only for
     * ME-terminal screens, where AE2's own handleMouseClick path already knows how to move ME slots.
     */
    @Overwrite(remap = false)
    public boolean MT_isMouseTweaksDisabled() {
        return !GpomEarlyConfig.mouseTweaksAe2TerminalsEnabled()
                || !MouseTweaksAe2TerminalCompat.hasTerminalSlots(this);
    }

    /**
     * Keep Mouse Tweaks away from AE2 pattern/fake/config slots while allowing terminal storage
     * slots and the bound player inventory/hotbar slots needed for left-drag-to-move behavior.
     * RMB is always ignored here so normal AE2/Minecraft clicks place one item per click instead
     * of Mouse Tweaks repeatedly placing items while the button is held.
     */
    @Overwrite(remap = false)
    public boolean MT_isIgnored(Slot slot) {
        return !GpomEarlyConfig.mouseTweaksAe2TerminalsEnabled()
                || MouseTweaksAe2TerminalCompat.isIgnored(slot);
    }
}
