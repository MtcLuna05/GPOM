package com.l.gpom.core;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class EnderIOFarmerCompatibilityTransformer implements IClassTransformer {
    private static final String FARM_LOGIC = "crazypants.enderio.machines.machine.farm.FarmLogic";
    private static final String COMMUNE = "crazypants.enderio.base.farming.registry.Commune";
    private static final String HELPER = "com/l/gpom/compat/enderio/EnderIOFarmerCompat";
    private static final String LOOTING_DESC =
            "(Lcrazypants/enderio/api/farm/IFarmingTool;)I";
    private static final String HARVEST_DESC =
            "(Lcrazypants/enderio/api/farm/IFarmer;Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/block/state/IBlockState;)Lcrazypants/enderio/api/farm/IHarvestResult;";
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        String className = transformedName != null ? transformedName : name;
        if (className == null) {
            return basicClass;
        }

        try {
            if (FARM_LOGIC.equals(className)
                    && GpomEarlyConfig.enderIOFarmerTinkersLuckEnabled()) {
                return transformClass(className, basicClass, this::patchTinkersLuck);
            }
            if (COMMUNE.equals(className)
                    && (GpomEarlyConfig.enderIOFarmerAgriCraftCropSticksEnabled()
                    || GpomEarlyConfig.enderIOFarmerOptimizedHarvestEnabled())) {
                return transformClass(className, basicClass, this::patchHarvestDispatch);
            }
        } catch (Throwable throwable) {
            logFailure(className, throwable);
        }
        return basicClass;
    }

    private static byte[] transformClass(
            String className,
            byte[] basicClass,
            ClassPatch patch
    ) {
        ClassReader reader = new ClassReader(basicClass);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);
        boolean changed = patch.apply(node);
        if (!changed) {
            if (LOGGED.add("missing:" + className)) {
                GPOM.LOGGER.warn("[GPOM EnderIO Farmer] Expected method was not patched in {}", className);
            }
            return basicClass;
        }
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        if (LOGGED.add("patched:" + className)) {
            GPOM.LOGGER.info("[GPOM EnderIO Farmer] Patched {} at natural class load", className);
        }
        return writer.toByteArray();
    }

    private boolean patchTinkersLuck(ClassNode node) {
        for (MethodNode method : node.methods) {
            if (!"getLootingValue".equals(method.name) || !LOOTING_DESC.equals(method.desc)) {
                continue;
            }
            if (hasHelperCall(method, "applyTinkersLuck")) {
                return true;
            }
            boolean changed = false;
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null;
                 instruction = instruction.getNext()) {
                if (instruction.getOpcode() != Opcodes.IRETURN) {
                    continue;
                }
                InsnList injected = new InsnList();
                injected.add(new VarInsnNode(Opcodes.ALOAD, 0));
                injected.add(new VarInsnNode(Opcodes.ALOAD, 1));
                injected.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        HELPER,
                        "applyTinkersLuck",
                        "(ILcrazypants/enderio/api/farm/IFarmer;"
                                + "Lcrazypants/enderio/api/farm/IFarmingTool;)I",
                        false
                ));
                method.instructions.insertBefore(instruction, injected);
                changed = true;
            }
            return changed;
        }
        return false;
    }

    private boolean patchHarvestDispatch(ClassNode node) {
        for (MethodNode method : node.methods) {
            if (!"harvestBlock".equals(method.name) || !HARVEST_DESC.equals(method.desc)) {
                continue;
            }
            if (hasHelperCall(method, "harvestWithCachedHandlers")) {
                return true;
            }
            method.instructions.clear();
            method.tryCatchBlocks.clear();
            if (method.localVariables != null) {
                method.localVariables.clear();
            }
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
            method.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    HELPER,
                    "harvestWithCachedHandlers",
                    "(Lcrazypants/enderio/base/farming/registry/Commune;"
                            + "Lcrazypants/enderio/api/farm/IFarmer;Lnet/minecraft/util/math/BlockPos;"
                            + "Lnet/minecraft/block/state/IBlockState;)Lcrazypants/enderio/api/farm/IHarvestResult;",
                    false
            ));
            method.instructions.add(new InsnNode(Opcodes.ARETURN));
            return true;
        }
        return false;
    }

    private static boolean hasHelperCall(MethodNode method, String methodName) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (HELPER.equals(call.owner) && methodName.equals(call.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void logFailure(String className, Throwable throwable) {
        if (LOGGED.add("failure:" + className)) {
            GPOM.LOGGER.warn("[GPOM EnderIO Farmer] Failed to patch {}", className, throwable);
        }
    }

    @FunctionalInterface
    private interface ClassPatch {
        boolean apply(ClassNode node);
    }
}
