package com.luna.gpom.core;

import com.luna.gpom.GPOM;
import com.luna.gpom.config.GpomEarlyConfig;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Applies optional-mod hot-loop fixes only when each exact target class is actually being defined. */
public final class OptionalModExceptionLoopTransformer implements IClassTransformer {
    private static final String GENDUSTRY = "net.bdew.gendustry.fluids.FluidSourceRegistry";
    private static final String GENDUSTRY_RESOURCE = "net/bdew/gendustry/fluids/FluidSourceRegistry.class";
    private static final String LAVACOW = "com.Fishmod.mod_LavaCow.util.ModEventHandler";
    private static final String LAVACOW_RESOURCE = "com/Fishmod/mod_LavaCow/util/ModEventHandler.class";
    private static final String HAMMERCORE_ITEM_COLOR = "com.zeitheron.hammercore.client.utils.ItemColorHelper";
    private static final String HAMMERCORE_ITEM_COLOR_RESOURCE =
            "com/zeitheron/hammercore/client/utils/ItemColorHelper.class";
    private static final String AE2_EXPORT_BUS = "appeng.parts.automation.PartExportBus";
    private static final String AE2_EXPORT_BUS_RESOURCE = "appeng/parts/automation/PartExportBus.class";
    private static final String CAREER_BEES_ACCELERATION =
            "com.rwtema.careerbees.effects.EffectAcceleration";
    private static final String CAREER_BEES_ACCELERATION_RESOURCE =
            "com/rwtema/careerbees/effects/EffectAcceleration.class";
    private static final String ADVANCED_ROCKETRY_ATMOSPHERE_TYPES =
            "zmaster587.advancedRocketry.dimension.DimensionProperties$AtmosphereTypes";
    private static final String ADVANCED_ROCKETRY_ATMOSPHERE_TYPES_RESOURCE =
            "zmaster587/advancedRocketry/dimension/DimensionProperties$AtmosphereTypes.class";
    private static final String ADVANCED_ROCKETRY_TEMPS =
            "zmaster587.advancedRocketry.dimension.DimensionProperties$Temps";
    private static final String ADVANCED_ROCKETRY_TEMPS_RESOURCE =
            "zmaster587/advancedRocketry/dimension/DimensionProperties$Temps.class";
    private static final String ALFHEIM_DEDUPLICATED_LONG_QUEUE =
            "dev.redstudio.alfheim.utils.DeduplicatedLongQueue";
    private static final String ALFHEIM_DEDUPLICATED_LONG_QUEUE_RESOURCE =
            "dev/redstudio/alfheim/utils/DeduplicatedLongQueue.class";
    private static final String THAUMCRAFT_BIOME_HANDLER = "thaumcraft.common.world.biomes.BiomeHandler";
    private static final String THAUMCRAFT_BIOME_HANDLER_RESOURCE =
            "thaumcraft/common/world/biomes/BiomeHandler.class";
    private static final String UNIVERSAL_TWEAKS_ENTITY_AABB =
            "mod.acgaming.universaltweaks.util.UTEntityAABBUtil";
    private static final String UNIVERSAL_TWEAKS_ENTITY_AABB_RESOURCE =
            "mod/acgaming/universaltweaks/util/UTEntityAABBUtil.class";
    private static final String CYCLIC_VACUUM =
            "com.lothrazar.cyclicmagic.block.collector.TileEntityVacuum";
    private static final String CYCLIC_VACUUM_RESOURCE =
            "com/lothrazar/cyclicmagic/block/collector/TileEntityVacuum.class";
    private static final String CYCLIC_INVENTORY_TRANSFER =
            "com.lothrazar.cyclicmagic.util.UtilInventoryTransfer";
    private static final String CYCLIC_INVENTORY_TRANSFER_RESOURCE =
            "com/lothrazar/cyclicmagic/util/UtilInventoryTransfer.class";
    private static final String CYCLOPS_TILE_HELPERS =
            "org.cyclops.cyclopscore.helper.TileHelpers";
    private static final String CYCLOPS_TILE_HELPERS_RESOURCE =
            "org/cyclops/cyclopscore/helper/TileHelpers.class";
    private static final String UNIVERSAL_TWEAKS_ENTITY_RADIUS =
            "mod.acgaming.universaltweaks.tweaks.performance.entityradiuscheck.UTEntityRadiusCheck";
    private static final String UNIVERSAL_TWEAKS_ENTITY_RADIUS_RESOURCE =
            "mod/acgaming/universaltweaks/tweaks/performance/entityradiuscheck/UTEntityRadiusCheck.class";
    private static final String HELPERS = "com/luna/gpom/optimization/OptionalModExceptionLoopOptimizations";
    private static final String MAPPING = "com/luna/gpom/compat/minecraft/MinecraftMappingCompat";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        String className = transformedName != null ? transformedName : name;
        try {
            if (GENDUSTRY.equals(className)
                    && GpomEarlyConfig.gendustryFluidSourceLookupEnabled()
                    && GpomMixinConfigPlugin.resourcePresentInNamedJar(
                    GENDUSTRY_RESOURCE, "gendustry-1.6.5.8-mc1.12.2.jar")) {
                return transformGendustry(basicClass);
            }
            if (LAVACOW.equals(className)
                    && GpomEarlyConfig.lavaCowInheritedTargetFieldLookupEnabled()
                    && GpomMixinConfigPlugin.resourcePresentInNamedJar(
                    LAVACOW_RESOURCE, "Fish's Undead Rising-1.6.0.jar")) {
                return transformLavaCow(basicClass);
            }
            if (HAMMERCORE_ITEM_COLOR.equals(className)
                    && GpomEarlyConfig.hammerCoreItemColorNegativeLookupCacheEnabled()
                    && GpomMixinConfigPlugin.resourcePresentInNamedJar(
                    HAMMERCORE_ITEM_COLOR_RESOURCE, "HammerLib-1.12.2-12.2.50.jar")) {
                return transformHammerCoreItemColor(basicClass);
            }
            if (AE2_EXPORT_BUS.equals(className)
                    && GpomEarlyConfig.ae2BdlibPowerNegativeLookupCacheEnabled()
                    && GpomMixinConfigPlugin.resourcePresentInNamedJar(
                    AE2_EXPORT_BUS_RESOURCE, "ae2-uelu-0.56.7-cleanroom.1.jar")) {
                return transformAe2(basicClass);
            }
            if (CAREER_BEES_ACCELERATION.equals(className)
                    && GpomMixinConfigPlugin.resourcePresentInNamedJar(
                    CAREER_BEES_ACCELERATION_RESOURCE, "careerbees-0.4.0.jar")) {
                return transformCareerBees(basicClass);
            }
            if (ADVANCED_ROCKETRY_ATMOSPHERE_TYPES.equals(className)
                    && GpomMixinConfigPlugin.resourcePresentInNamedJar(
                    ADVANCED_ROCKETRY_ATMOSPHERE_TYPES_RESOURCE, "advancedrocketry-2.2.12.jar")) {
                return transformAdvancedRocketryEnumLookup(basicClass,
                        "getAtmosphereTypeFromValue", ADVANCED_ROCKETRY_ATMOSPHERE_TYPES.replace('.', '/'));
            }
            if (ADVANCED_ROCKETRY_TEMPS.equals(className)
                    && GpomMixinConfigPlugin.resourcePresentInNamedJar(
                    ADVANCED_ROCKETRY_TEMPS_RESOURCE, "advancedrocketry-2.2.12.jar")) {
                return transformAdvancedRocketryEnumLookup(basicClass,
                        "getTempFromValue", ADVANCED_ROCKETRY_TEMPS.replace('.', '/'));
            }
            if (ALFHEIM_DEDUPLICATED_LONG_QUEUE.equals(className)
                    && GpomMixinConfigPlugin.resourcePresentInNamedJar(
                    ALFHEIM_DEDUPLICATED_LONG_QUEUE_RESOURCE, "Alfheim-1.6.jar")) {
                return transformAlfheimQueue(basicClass);
            }
            if (THAUMCRAFT_BIOME_HANDLER.equals(className)
                    && GpomMixinConfigPlugin.resourcePresentInNamedJar(
                    THAUMCRAFT_BIOME_HANDLER_RESOURCE, "Thaumcraft-1.12.2-6.1.BETA26.jar")) {
                return transformThaumcraftBiomeHandler(basicClass);
            }
            if (UNIVERSAL_TWEAKS_ENTITY_AABB.equals(className)
                    && GpomMixinConfigPlugin.resourcePresentInNamedJar(
                    UNIVERSAL_TWEAKS_ENTITY_AABB_RESOURCE, "UniversalTweaks-1.12.2-1.20.1.jar")) {
                return transformUniversalTweaksEntityAabb(basicClass);
            }
            if (CYCLIC_VACUUM.equals(className)
                    && GpomMixinConfigPlugin.resourcePresentInNamedJar(
                    CYCLIC_VACUUM_RESOURCE, "Cyclic-1.12.2-1.20.14.jar")) {
                return transformCyclicVacuum(basicClass);
            }
            if (CYCLIC_INVENTORY_TRANSFER.equals(className)
                    && GpomEarlyConfig.cyclicVacuumInventoryTransferEnabled()
                    && GpomMixinConfigPlugin.resourcePresentInNamedJar(
                    CYCLIC_INVENTORY_TRANSFER_RESOURCE, "Cyclic-1.12.2-1.20.14.jar")) {
                return transformCyclicInventoryTransfer(basicClass);
            }
            if (CYCLOPS_TILE_HELPERS.equals(className)
                    && GpomMixinConfigPlugin.resourcePresentInNamedJar(
                    CYCLOPS_TILE_HELPERS_RESOURCE, "CyclopsCore-1.12.2-1.6.7.jar")) {
                return transformCyclopsTileHelpers(basicClass);
            }
            if (UNIVERSAL_TWEAKS_ENTITY_RADIUS.equals(className)
                    && GpomMixinConfigPlugin.resourcePresentInNamedJar(
                    UNIVERSAL_TWEAKS_ENTITY_RADIUS_RESOURCE, "UniversalTweaks-1.12.2-1.20.1.jar")) {
                return transformUniversalTweaksEntityRadius(basicClass);
            }
        } catch (Throwable ignored) {
            // Optional integrations fail closed: the original class bytes remain valid.
        }
        return basicClass;
    }

    private static byte[] transformGendustry(byte[] basicClass) {
        ClassNode node = read(basicClass);
        MethodNode target = find(node, "getValue", "(Lnet/minecraft/item/ItemStack;)I");
        if (target == null || !containsCall(target, "scala/runtime/NonLocalReturnControl", "value$mcI$sp")) {
            return basicClass;
        }

        target.instructions.clear();
        target.tryCatchBlocks.clear();
        if (target.localVariables != null) {
            target.localVariables.clear();
        }
        InsnList code = target.instructions;
        LabelNode stackPresent = new LabelNode();
        LabelNode itemPresent = new LabelNode();
        LabelNode itemEntryPresent = new LabelNode();
        LabelNode exactMissing = new LabelNode();
        LabelNode wildcardMissing = new LabelNode();

        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new JumpInsnNode(Opcodes.IFNONNULL, stackPresent));
        code.add(new InsnNode(Opcodes.ICONST_0));
        code.add(new InsnNode(Opcodes.IRETURN));
        code.add(stackPresent);
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MAPPING, "itemStackItem",
                "(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/Item;", false));
        code.add(new VarInsnNode(Opcodes.ASTORE, 2));
        code.add(new VarInsnNode(Opcodes.ALOAD, 2));
        code.add(new JumpInsnNode(Opcodes.IFNONNULL, itemPresent));
        code.add(new InsnNode(Opcodes.ICONST_0));
        code.add(new InsnNode(Opcodes.IRETURN));
        code.add(itemPresent);
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "net/bdew/gendustry/fluids/FluidSourceRegistry", "values",
                "()Lscala/collection/mutable/Map;", false));
        code.add(new VarInsnNode(Opcodes.ALOAD, 2));
        code.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "scala/collection/mutable/Map", "get",
                "(Ljava/lang/Object;)Lscala/Option;", true));
        code.add(new VarInsnNode(Opcodes.ASTORE, 3));
        code.add(new VarInsnNode(Opcodes.ALOAD, 3));
        code.add(new JumpInsnNode(Opcodes.IFNULL, wildcardMissing));
        code.add(new VarInsnNode(Opcodes.ALOAD, 3));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "scala/Option", "isDefined", "()Z", false));
        code.add(new JumpInsnNode(Opcodes.IFNE, itemEntryPresent));
        code.add(new InsnNode(Opcodes.ICONST_0));
        code.add(new InsnNode(Opcodes.IRETURN));
        code.add(itemEntryPresent);
        code.add(new VarInsnNode(Opcodes.ALOAD, 3));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "scala/Option", "get", "()Ljava/lang/Object;", false));
        code.add(new TypeInsnNode(Opcodes.CHECKCAST, "scala/collection/mutable/Map"));
        code.add(new VarInsnNode(Opcodes.ASTORE, 4));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MAPPING, "itemStackDamage",
                "(Lnet/minecraft/item/ItemStack;)I", false));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf",
                "(I)Ljava/lang/Integer;", false));
        code.add(new VarInsnNode(Opcodes.ASTORE, 5));
        addMapLookup(code, 4, 5, 6, exactMissing);
        code.add(new VarInsnNode(Opcodes.ALOAD, 6));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "scala/Option", "get", "()Ljava/lang/Object;", false));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "scala/runtime/BoxesRunTime", "unboxToInt",
                "(Ljava/lang/Object;)I", false));
        code.add(new InsnNode(Opcodes.IRETURN));
        code.add(exactMissing);
        code.add(new LdcInsnNode(Integer.valueOf(32767)));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf",
                "(I)Ljava/lang/Integer;", false));
        code.add(new VarInsnNode(Opcodes.ASTORE, 5));
        addMapLookup(code, 4, 5, 6, wildcardMissing);
        code.add(new VarInsnNode(Opcodes.ALOAD, 6));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "scala/Option", "get", "()Ljava/lang/Object;", false));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "scala/runtime/BoxesRunTime", "unboxToInt",
                "(Ljava/lang/Object;)I", false));
        code.add(new InsnNode(Opcodes.IRETURN));
        code.add(wildcardMissing);
        code.add(new InsnNode(Opcodes.ICONST_0));
        code.add(new InsnNode(Opcodes.IRETURN));
        return writeWithFrames(node);
    }

    private static void addMapLookup(InsnList code, int mapLocal, int keyLocal, int optionLocal, LabelNode missing) {
        code.add(new VarInsnNode(Opcodes.ALOAD, mapLocal));
        code.add(new VarInsnNode(Opcodes.ALOAD, keyLocal));
        code.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "scala/collection/mutable/Map", "get",
                "(Ljava/lang/Object;)Lscala/Option;", true));
        code.add(new VarInsnNode(Opcodes.ASTORE, optionLocal));
        code.add(new VarInsnNode(Opcodes.ALOAD, optionLocal));
        code.add(new JumpInsnNode(Opcodes.IFNULL, missing));
        code.add(new VarInsnNode(Opcodes.ALOAD, optionLocal));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "scala/Option", "isDefined", "()Z", false));
        code.add(new JumpInsnNode(Opcodes.IFEQ, missing));
    }

    private static byte[] transformLavaCow(byte[] basicClass) {
        ClassNode node = read(basicClass);
        MethodNode target = find(node, "onEntityJoinWorld",
                "(Lnet/minecraftforge/event/entity/EntityJoinWorldEvent;)V");
        if (target == null) {
            return basicClass;
        }
        LabelNode loopContinue = null;
        for (AbstractInsnNode instruction : target.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() == Opcodes.INVOKEINTERFACE
                        && "java/util/Iterator".equals(call.owner)
                        && "hasNext".equals(call.name)
                        && "()Z".equals(call.desc)) {
                    loopContinue = precedingLabel(instruction);
                }
            }
        }
        if (loopContinue == null) {
            return basicClass;
        }

        int replacements = 0;
        boolean nullGuardInserted = false;
        for (AbstractInsnNode instruction : target.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && "java/lang/Class".equals(call.owner)
                        && "getDeclaredField".equals(call.name)
                        && "(Ljava/lang/String;)Ljava/lang/reflect/Field;".equals(call.desc)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = HELPERS;
                    call.name = "findInheritedDeclaredFieldOrNull";
                    call.desc = "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Field;";
                    call.itf = false;
                    AbstractInsnNode storeInstruction = nextOpcode(call);
                    if (storeInstruction instanceof VarInsnNode
                            && storeInstruction.getOpcode() == Opcodes.ASTORE) {
                        int fieldLocal = ((VarInsnNode) storeInstruction).var;
                        InsnList guard = new InsnList();
                        guard.add(new VarInsnNode(Opcodes.ALOAD, fieldLocal));
                        guard.add(new JumpInsnNode(Opcodes.IFNULL, loopContinue));
                        target.instructions.insert(storeInstruction, guard);
                        nullGuardInserted = true;
                    }
                    replacements++;
                }
            }
        }
        return replacements == 1 && nullGuardInserted ? writePreservingFrames(node) : basicClass;
    }

    private static byte[] transformHammerCoreItemColor(byte[] basicClass) {
        ClassNode node = read(basicClass);
        MethodNode target = find(node, "getCustomColor", "(I)I");
        if (target == null
                || !containsCall(target, "java/lang/Class", "forName")
                || !containsCall(target, "com/zeitheron/hammercore/client/utils/ItemColorHelper", "getColorFromStack")) {
            return basicClass;
        }

        target.instructions.clear();
        target.tryCatchBlocks.clear();
        if (target.localVariables != null) {
            target.localVariables.clear();
        }
        LabelNode fallback = new LabelNode();
        LabelNode quarkMissing = new LabelNode();
        InsnList code = target.instructions;
        code.add(new org.objectweb.asm.tree.FieldInsnNode(
                Opcodes.GETSTATIC,
                "com/zeitheron/hammercore/client/utils/ItemColorHelper",
                "target",
                "Lnet/minecraft/item/ItemStack;"
        ));
        code.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HELPERS,
                "hammerCoreUsesCustomEnchantColor",
                "(Ljava/lang/Object;)Z",
                false
        ));
        code.add(new JumpInsnNode(Opcodes.IFNE, fallback));
        code.add(new org.objectweb.asm.tree.FieldInsnNode(
                Opcodes.GETSTATIC,
                "com/zeitheron/hammercore/client/utils/ItemColorHelper",
                "target",
                "Lnet/minecraft/item/ItemStack;"
        ));
        code.add(new VarInsnNode(Opcodes.ILOAD, 0));
        code.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HELPERS,
                "hammerCoreQuarkColor",
                "(Ljava/lang/Object;I)Ljava/lang/Integer;",
                false
        ));
        code.add(new InsnNode(Opcodes.DUP));
        code.add(new JumpInsnNode(Opcodes.IFNULL, quarkMissing));
        code.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/Integer",
                "intValue",
                "()I",
                false
        ));
        code.add(new InsnNode(Opcodes.IRETURN));
        code.add(quarkMissing);
        code.add(new FrameNode(
                Opcodes.F_SAME1,
                0,
                null,
                1,
                new Object[] {"java/lang/Integer"}
        ));
        code.add(new InsnNode(Opcodes.POP));
        code.add(fallback);
        code.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        code.add(new org.objectweb.asm.tree.FieldInsnNode(
                Opcodes.GETSTATIC,
                "com/zeitheron/hammercore/client/utils/ItemColorHelper",
                "target",
                "Lnet/minecraft/item/ItemStack;"
        ));
        code.add(new VarInsnNode(Opcodes.ILOAD, 0));
        code.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "com/zeitheron/hammercore/client/utils/ItemColorHelper",
                "getColorFromStack",
                "(Lnet/minecraft/item/ItemStack;I)I",
                false
        ));
        code.add(new InsnNode(Opcodes.IRETURN));
        return writePreservingFrames(node);
    }

    private static byte[] transformAe2(byte[] basicClass) {
        ClassNode node = read(basicClass);
        MethodNode target = find(node, "getBdlibPowerSlot",
                "(Lnet/minecraft/tileentity/TileEntity;)Ljava/lang/Object;");
        if (target == null || !containsCall(target, "java/lang/Class", "getMethod")) {
            return basicClass;
        }
        target.instructions.clear();
        target.tryCatchBlocks.clear();
        if (target.localVariables != null) {
            target.localVariables.clear();
        }
        target.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        target.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPERS, "ae2BdlibPowerSlot",
                "(Ljava/lang/Object;)Ljava/lang/Object;", false));
        target.instructions.add(new InsnNode(Opcodes.ARETURN));
        return writePreservingFrames(node);
    }

    private static byte[] transformCareerBees(byte[] basicClass) {
        ClassNode node = read(basicClass);
        MethodNode target = find(node, "doEffectBase",
                "(Lforestry/api/apiculture/IBeeGenome;Lforestry/api/genetics/IEffectData;"
                        + "Lforestry/api/apiculture/IBeeHousing;"
                        + "Lcom/rwtema/careerbees/effects/settings/IEffectSettingsHolder;)"
                        + "Lforestry/api/genetics/IEffectData;");
        if (target == null) {
            return basicClass;
        }
        int replacements = 0;
        int mutableLocal = target.maxLocals;
        AbstractInsnNode mutableInitializationPoint = null;
        TypeInsnNode positionAllocation = null;
        MethodInsnNode positionConstructor = null;
        for (AbstractInsnNode instruction : target.instructions.toArray()) {
            if (instruction instanceof VarInsnNode
                    && instruction.getOpcode() == Opcodes.ISTORE
                    && ((VarInsnNode) instruction).var == 11
                    && mutableInitializationPoint == null) {
                mutableInitializationPoint = instruction;
            }
            if (instruction instanceof TypeInsnNode
                    && instruction.getOpcode() == Opcodes.NEW
                    && "net/minecraft/util/math/BlockPos".equals(((TypeInsnNode) instruction).desc)) {
                positionAllocation = (TypeInsnNode) instruction;
            }
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.getOpcode() == Opcodes.INVOKESPECIAL
                    && "net/minecraft/util/math/BlockPos".equals(call.owner)
                    && "<init>".equals(call.name)
                    && "(III)V".equals(call.desc)) {
                positionConstructor = call;
            }
            if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && "net/minecraft/world/World".equals(call.owner)
                    && "func_180495_p".equals(call.name)
                    && "(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;".equals(call.desc)) {
                call.setOpcode(Opcodes.INVOKESTATIC);
                call.owner = HELPERS;
                call.name = "careerBeesLoadedBlockState";
                call.desc = "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)"
                        + "Lnet/minecraft/block/state/IBlockState;";
                call.itf = false;
                replacements++;
            }
        }
        if (replacements == 0 || mutableInitializationPoint == null
                || positionAllocation == null || positionConstructor == null
                || positionAllocation.getNext() == null
                || positionAllocation.getNext().getOpcode() != Opcodes.DUP) {
            return basicClass;
        }

        InsnList initializeMutablePosition = new InsnList();
        initializeMutablePosition.add(new TypeInsnNode(Opcodes.NEW,
                "net/minecraft/util/math/BlockPos$MutableBlockPos"));
        initializeMutablePosition.add(new InsnNode(Opcodes.DUP));
        initializeMutablePosition.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "net/minecraft/util/math/BlockPos$MutableBlockPos", "<init>", "()V", false));
        initializeMutablePosition.add(new VarInsnNode(Opcodes.ASTORE, mutableLocal));
        target.instructions.insert(mutableInitializationPoint, initializeMutablePosition);
        target.maxLocals = mutableLocal + 1;

        AbstractInsnNode duplicate = positionAllocation.getNext();
        target.instructions.set(positionAllocation, new VarInsnNode(Opcodes.ALOAD, mutableLocal));
        target.instructions.remove(duplicate);
        positionConstructor.setOpcode(Opcodes.INVOKEVIRTUAL);
        positionConstructor.owner = "net/minecraft/util/math/BlockPos$MutableBlockPos";
        positionConstructor.name = "func_181079_c";
        positionConstructor.desc = "(III)Lnet/minecraft/util/math/BlockPos$MutableBlockPos;";
        positionConstructor.itf = false;

        GPOM.LOGGER.info("[GPOM CareerBees] Patched {} loaded-chunk lookup(s) and reused the scan position",
                replacements);
        return writeWithFrames(node);
    }

    private static byte[] transformAdvancedRocketryEnumLookup(byte[] basicClass,
                                                               String methodName,
                                                               String owner) {
        ClassNode node = read(basicClass);
        MethodNode target = find(node, methodName, "(I)L" + owner + ";");
        if (target == null) {
            return basicClass;
        }
        String arrayDescriptor = "[L" + owner + ";";
        int replacements = 0;
        for (AbstractInsnNode instruction : target.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.getOpcode() == Opcodes.INVOKESTATIC
                    && owner.equals(call.owner)
                    && "values".equals(call.name)
                    && ("()" + arrayDescriptor).equals(call.desc)) {
                target.instructions.set(call,
                        new FieldInsnNode(Opcodes.GETSTATIC, owner, "$VALUES", arrayDescriptor));
                replacements++;
            }
        }
        if (replacements != 1) {
            return basicClass;
        }
        GPOM.LOGGER.info("[GPOM Advanced Rocketry] Reused {} backing values array", owner);
        return writePreservingFrames(node);
    }

    private static byte[] transformAlfheimQueue(byte[] basicClass) {
        ClassNode node = read(basicClass);
        MethodNode constructor = find(node, "<init>", "(I)V");
        MethodNode dequeue = find(node, "dequeue", "()J");
        MethodNode resetDeduplication = find(node, "newDeduplicationSet", "()V");
        if (constructor == null || dequeue == null || resetDeduplication == null
                || !containsCall(dequeue, "it/unimi/dsi/fastutil/longs/LongArrayFIFOQueue", "dequeueLong")
                || !containsCall(resetDeduplication, "it/unimi/dsi/fastutil/longs/LongOpenHashSet", "<init>")) {
            return basicClass;
        }

        String originalQueue = "it/unimi/dsi/fastutil/longs/LongArrayFIFOQueue";
        String retainingQueue = "com/luna/gpom/optimization/alfheim/CapacityRetainingLongArrayFIFOQueue";
        int queueAllocations = 0;
        int queueConstructors = 0;
        for (AbstractInsnNode instruction : constructor.instructions.toArray()) {
            if (instruction instanceof TypeInsnNode
                    && instruction.getOpcode() == Opcodes.NEW
                    && originalQueue.equals(((TypeInsnNode) instruction).desc)) {
                ((TypeInsnNode) instruction).desc = retainingQueue;
                queueAllocations++;
            }
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() == Opcodes.INVOKESPECIAL
                        && originalQueue.equals(call.owner)
                        && "<init>".equals(call.name)
                        && "(I)V".equals(call.desc)) {
                    call.owner = retainingQueue;
                    queueConstructors++;
                }
            }
        }
        if (queueAllocations != 1 || queueConstructors != 1) {
            return basicClass;
        }

        resetDeduplication.instructions.clear();
        resetDeduplication.tryCatchBlocks.clear();
        if (resetDeduplication.localVariables != null) {
            resetDeduplication.localVariables.clear();
        }
        resetDeduplication.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        resetDeduplication.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,
                "dev/redstudio/alfheim/utils/DeduplicatedLongQueue", "set",
                "Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;"));
        resetDeduplication.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "it/unimi/dsi/fastutil/longs/LongOpenHashSet", "clear", "()V", false));
        resetDeduplication.instructions.add(new InsnNode(Opcodes.RETURN));
        GPOM.LOGGER.info("[GPOM Alfheim] Installed GPOM-owned capacity-retaining lighting queue and reused set");
        return writeWithFrames(node);
    }

    private static byte[] transformThaumcraftBiomeHandler(byte[] basicClass) {
        ClassNode node = read(basicClass);
        int patched = 0;
        patched += insertMapMissReturn(find(node, "getBiomeAuraModifier",
                "(Lnet/minecraft/world/biome/Biome;)F"), new LdcInsnNode(Float.valueOf(0.5F)), Opcodes.FRETURN, 1);
        patched += insertMapMissReturn(find(node, "getRandomBiomeTag",
                "(ILjava/util/Random;)Lthaumcraft/api/aspects/Aspect;"),
                new InsnNode(Opcodes.ACONST_NULL), Opcodes.ARETURN, 1);
        patched += insertMapMissReturn(find(node, "getBiomeSupportsGreatwood", "(I)F"),
                new InsnNode(Opcodes.FCONST_0), Opcodes.FRETURN, 2);
        if (patched != 4) {
            return basicClass;
        }
        GPOM.LOGGER.info("[GPOM Thaumcraft] Replaced expected missing biome-info exceptions with direct returns");
        return writeWithFrames(node);
    }

    private static byte[] transformUniversalTweaksEntityAabb(byte[] basicClass) {
        ClassNode node = read(basicClass);
        MethodNode query = find(node, "getEntitiesInAABBexcluding",
                "(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;"
                        + "Lnet/minecraft/util/math/AxisAlignedBB;Lcom/google/common/base/Predicate;D)Ljava/util/List;");
        MethodNode target = find(node, "getEntitiesWithinAABBForEntity",
                "(Lnet/minecraft/world/chunk/Chunk;Lnet/minecraft/entity/Entity;"
                        + "Lnet/minecraft/util/math/AxisAlignedBB;Ljava/util/List;"
                        + "Lcom/google/common/base/Predicate;D)V");
        if (query == null || target == null || !containsCall(target,
                "net/minecraft/util/ClassInheritanceMultiMap", "iterator")) {
            return basicClass;
        }

        int patched = 0;
        for (AbstractInsnNode instruction : target.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !"net/minecraft/util/ClassInheritanceMultiMap".equals(call.owner)
                    || !"isEmpty".equals(call.name)
                    || !"()Z".equals(call.desc)) {
                continue;
            }
            AbstractInsnNode following = nextOpcode(call);
            if (!(following instanceof JumpInsnNode) || following.getOpcode() != Opcodes.IFNE) {
                return basicClass;
            }

            LabelNode skip = new LabelNode();
            InsnList reserve = new InsnList();
            reserve.add(new VarInsnNode(Opcodes.ALOAD, 3));
            reserve.add(new TypeInsnNode(Opcodes.INSTANCEOF, "java/util/ArrayList"));
            reserve.add(new JumpInsnNode(Opcodes.IFEQ, skip));
            reserve.add(new VarInsnNode(Opcodes.ALOAD, 3));
            reserve.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/util/ArrayList"));
            reserve.add(new VarInsnNode(Opcodes.ALOAD, 3));
            reserve.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                    "java/util/List", "size", "()I", true));
            reserve.add(new VarInsnNode(Opcodes.ALOAD, 7));
            reserve.add(new VarInsnNode(Opcodes.ILOAD, 10));
            reserve.add(new InsnNode(Opcodes.AALOAD));
            reserve.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                    "net/minecraft/util/ClassInheritanceMultiMap", "size", "()I", false));
            reserve.add(new InsnNode(Opcodes.ICONST_3));
            reserve.add(new InsnNode(Opcodes.ISHL));
            reserve.add(new IntInsnNode(Opcodes.BIPUSH, 64));
            reserve.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/lang/Math", "max", "(II)I", false));
            reserve.add(new InsnNode(Opcodes.IADD));
            reserve.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                    "java/util/ArrayList", "ensureCapacity", "(I)V", false));
            reserve.add(skip);
            target.instructions.insert(following, reserve);
            patched++;
        }
        if (patched != 1) {
            return basicClass;
        }

        if (GpomEarlyConfig.skipDroppedItemEntityCollisionsEnabled()) {
            LabelNode authoritativeQuery = new LabelNode();
            InsnList clientItemGuard = new InsnList();
            clientItemGuard.add(new VarInsnNode(Opcodes.ALOAD, 1));
            clientItemGuard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPERS,
                    "skipItemEntityCollisionQuery", "(Ljava/lang/Object;)Z", false));
            clientItemGuard.add(new JumpInsnNode(Opcodes.IFEQ, authoritativeQuery));
            clientItemGuard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "java/util/Collections", "emptyList", "()Ljava/util/List;", false));
            clientItemGuard.add(new InsnNode(Opcodes.ARETURN));
            clientItemGuard.add(authoritativeQuery);
            query.instructions.insert(clientItemGuard);
        }

        GPOM.LOGGER.info(GpomEarlyConfig.skipDroppedItemEntityCollisionsEnabled()
                ? "[GPOM UniversalTweaks] Pre-sized multipart results and skipped dropped-item entity collisions"
                : "[GPOM UniversalTweaks] Pre-sized multipart entity-query results");
        return writeWithFrames(node);
    }

    private static byte[] transformCyclicVacuum(byte[] basicClass) {
        ClassNode node = read(basicClass);
        MethodNode target = find(node, "func_73660_a", "()V");
        MethodNode processItem = find(node, "processItemOnGround",
                "(Lnet/minecraft/entity/item/EntityItem;)V");
        if (target == null || !containsCall(target,
                "com/lothrazar/cyclicmagic/block/collector/TileEntityVacuum", "updateCollection")) {
            return basicClass;
        }

        LabelNode serverSide = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPERS,
                "skipClientTileTick", "(Ljava/lang/Object;)Z", false));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, serverSide));
        guard.add(new InsnNode(Opcodes.RETURN));
        guard.add(serverSide);
        target.instructions.insert(guard);
        int singletonInputs = 0;
        if (GpomEarlyConfig.cyclicVacuumInventoryTransferEnabled() && processItem != null) {
            TypeInsnNode allocation = null;
            for (AbstractInsnNode instruction : processItem.instructions.toArray()) {
                if (instruction instanceof TypeInsnNode
                        && instruction.getOpcode() == Opcodes.NEW
                        && "com/lothrazar/cyclicmagic/block/collector/TileEntityVacuum$1"
                        .equals(((TypeInsnNode) instruction).desc)) {
                    allocation = (TypeInsnNode) instruction;
                    continue;
                }
                if (!(instruction instanceof MethodInsnNode) || allocation == null) {
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() != Opcodes.INVOKESPECIAL
                        || !"com/lothrazar/cyclicmagic/block/collector/TileEntityVacuum$1".equals(call.owner)
                        || !"<init>".equals(call.name)) {
                    continue;
                }
                AbstractInsnNode duplicate = nextOpcode(allocation);
                AbstractInsnNode ownerLoad = duplicate != null ? nextOpcode(duplicate) : null;
                if (duplicate == null || duplicate.getOpcode() != Opcodes.DUP
                        || ownerLoad == null || ownerLoad.getOpcode() != Opcodes.ALOAD
                        || ((VarInsnNode) ownerLoad).var != 0) {
                    break;
                }
                processItem.instructions.remove(allocation);
                processItem.instructions.remove(duplicate);
                processItem.instructions.remove(ownerLoad);
                call.setOpcode(Opcodes.INVOKESTATIC);
                call.owner = "java/util/Collections";
                call.name = "singletonList";
                call.desc = "(Ljava/lang/Object;)Ljava/util/List;";
                call.itf = false;
                singletonInputs++;
                break;
            }
        }
        GPOM.LOGGER.info("[GPOM Cyclic] Disabled client vacuum work and optimized {} singleton transfer input(s)",
                singletonInputs);
        return writeWithFrames(node);
    }

    private static byte[] transformCyclicInventoryTransfer(byte[] basicClass) {
        ClassNode node = read(basicClass);
        MethodNode target = find(node, "dumpToIInventory",
                "(Ljava/util/List;Lnet/minecraft/inventory/IInventory;II)Ljava/util/ArrayList;");
        if (target == null) {
            return basicClass;
        }
        target.instructions.clear();
        target.tryCatchBlocks.clear();
        if (target.localVariables != null) {
            target.localVariables.clear();
        }
        target.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        target.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        target.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        target.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        target.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "com/luna/gpom/optimization/CyclicInventoryOptimizations", "dumpToIInventory",
                "(Ljava/util/List;Lnet/minecraft/inventory/IInventory;II)Ljava/util/ArrayList;", false));
        target.instructions.add(new InsnNode(Opcodes.ARETURN));
        GPOM.LOGGER.info("[GPOM Cyclic] Installed allocation-light vacuum inventory transfer");
        return writeWithFrames(node);
    }

    private static byte[] transformCyclopsTileHelpers(byte[] basicClass) {
        ClassNode node = read(basicClass);
        MethodNode target = find(node, "getSafeTile",
                "(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;"
                        + "Ljava/lang/Class;)Ljava/lang/Object;");
        if (target == null || !containsCall(target, "java/lang/Class", "cast")) {
            return basicClass;
        }

        target.instructions.clear();
        target.tryCatchBlocks.clear();
        if (target.localVariables != null) {
            target.localVariables.clear();
        }
        LabelNode wrongType = new LabelNode();
        target.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        target.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        target.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "net/minecraft/world/IBlockAccess", "func_175625_s",
                "(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/tileentity/TileEntity;", true));
        target.instructions.add(new VarInsnNode(Opcodes.ASTORE, 3));
        target.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        target.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        target.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/Class", "isInstance", "(Ljava/lang/Object;)Z", false));
        target.instructions.add(new JumpInsnNode(Opcodes.IFEQ, wrongType));
        target.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        target.instructions.add(new InsnNode(Opcodes.ARETURN));
        target.instructions.add(wrongType);
        target.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        target.instructions.add(new InsnNode(Opcodes.ARETURN));
        GPOM.LOGGER.info("[GPOM CyclopsCore] Replaced expected safe-tile ClassCastExceptions with isInstance");
        return writeWithFrames(node);
    }

    private static byte[] transformUniversalTweaksEntityRadius(byte[] basicClass) {
        ClassNode node = read(basicClass);
        MethodNode target = find(node, "initCollisionTargets", "()V");
        if (target == null) {
            return basicClass;
        }

        String originalMap = "it/unimi/dsi/fastutil/objects/Reference2DoubleOpenHashMap";
        String optimizedMap = "com/luna/gpom/optimization/EntityRadiusCollisionMap";
        int allocations = 0;
        int constructors = 0;
        for (AbstractInsnNode instruction : target.instructions.toArray()) {
            if (instruction instanceof TypeInsnNode) {
                TypeInsnNode type = (TypeInsnNode) instruction;
                if (type.getOpcode() == Opcodes.NEW && originalMap.equals(type.desc)) {
                    type.desc = optimizedMap;
                    allocations++;
                }
            } else if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() == Opcodes.INVOKESPECIAL
                        && originalMap.equals(call.owner)
                        && "<init>".equals(call.name)
                        && "()V".equals(call.desc)) {
                    call.owner = optimizedMap;
                    constructors++;
                }
            }
        }
        if (allocations != 1 || constructors != 1) {
            return basicClass;
        }
        GPOM.LOGGER.info("[GPOM UniversalTweaks] Installed direct vanilla-item collision radius lookup");
        return writeWithFrames(node);
    }

    private static int insertMapMissReturn(MethodNode method,
                                           AbstractInsnNode defaultValue,
                                           int returnOpcode,
                                           int expectedCalls) {
        if (method == null) {
            return 0;
        }
        int patched = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !"java/util/HashMap".equals(call.owner)
                    || !"get".equals(call.name)
                    || !"(Ljava/lang/Object;)Ljava/lang/Object;".equals(call.desc)) {
                continue;
            }
            LabelNode present = new LabelNode();
            InsnList guard = new InsnList();
            guard.add(new InsnNode(Opcodes.DUP));
            guard.add(new JumpInsnNode(Opcodes.IFNONNULL, present));
            guard.add(new InsnNode(Opcodes.POP));
            guard.add(defaultValue.clone(null));
            guard.add(new InsnNode(returnOpcode));
            guard.add(present);
            method.instructions.insert(call, guard);
            patched++;
        }
        return patched == expectedCalls ? patched : 0;
    }

    private static boolean containsCall(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (owner.equals(call.owner) && name.equals(call.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static LabelNode precedingLabel(AbstractInsnNode instruction) {
        for (AbstractInsnNode current = instruction.getPrevious(); current != null; current = current.getPrevious()) {
            if (current instanceof LabelNode) {
                return (LabelNode) current;
            }
        }
        return null;
    }

    private static AbstractInsnNode nextOpcode(AbstractInsnNode instruction) {
        for (AbstractInsnNode current = instruction.getNext(); current != null; current = current.getNext()) {
            if (current.getOpcode() >= 0) {
                return current;
            }
        }
        return null;
    }

    private static MethodNode find(ClassNode node, String name, String descriptor) {
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
        return node;
    }

    private static byte[] writeWithFrames(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] writePreservingFrames(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
