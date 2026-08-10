package com.l.gpom.mixin.performance;

import com.l.gpom.optimization.GameRuleValueParsingOptimizations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/** Avoids repeated exception-backed parsing while preserving vanilla's cached field semantics. */
@Pseudo
@Mixin(targets = "net.minecraft.world.GameRules$Value", remap = false)
public abstract class MixinGameRulesValueUnchangedFastPath {
    @Shadow(remap = false)
    private String field_82762_a;

    @Shadow(remap = false)
    private boolean field_82760_b;

    @Shadow(remap = false)
    private int field_82761_c;

    @Shadow(remap = false)
    private double field_82759_d;

    /**
     * @author GPOM
     * @reason Cache exact vanilla parse outcomes so non-numeric rule values throw only once per distinct string.
     */
    @Overwrite(remap = false)
    public void func_82757_a(String value) {
        if (value != null && value.equals(field_82762_a)) {
            return;
        }
        field_82762_a = value;
        if (value == null) {
            field_82760_b = false;
            field_82761_c = 0;
            Double.parseDouble((String) null);
            return;
        }
        GameRuleValueParsingOptimizations.ParsedValue parsed = GameRuleValueParsingOptimizations.parse(value);
        field_82760_b = parsed.booleanValue;
        field_82761_c = parsed.integerValue;
        if (parsed.hasDouble) {
            field_82759_d = parsed.doubleValue;
        }
    }
}
