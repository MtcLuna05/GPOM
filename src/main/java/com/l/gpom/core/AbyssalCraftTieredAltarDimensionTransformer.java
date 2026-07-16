package com.l.gpom.core;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class AbyssalCraftTieredAltarDimensionTransformer implements IClassTransformer {
    private static final String ITEM_STACK = "Lnet/minecraft/item/ItemStack;";
    private static final String CONDITION = "Lcom/shinoow/abyssalcraft/api/necronomicon/condition/IUnlockCondition;";
    private static final String SUPER_ITEM_BLOCK = "com/shinoow/abyssalcraft/common/blocks/itemblock/ItemMetadataPEContainerBlock";
    private static final String HOOKS = "com/l/gpom/compat/abyssalcraft/AbyssalCraftTieredAltarDimensionCompat";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !GpomEarlyConfig.abyssalCraftTieredAltarDimensionsEnabled()) {
            return basicClass;
        }
        String className = transformedName != null ? transformedName : name;
        if (!isTarget(className)) {
            return basicClass;
        }
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = transformClass(className, node);
            if (!changed) {
                return basicClass;
            }
            ClassWriter writer = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            GPOM.LOGGER.info("[GPOM AbyssalCraft Tiered Altars] Patched {}", className);
            return writer.toByteArray();
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[GPOM AbyssalCraft Tiered Altars] Failed to patch {}; continuing with original bytecode", className, throwable);
            return basicClass;
        }
    }

    private static boolean isTarget(String className) {
        return "com.shinoow.abyssalcraft.common.blocks.itemblock.ItemTieredEnergyCollectorBlock".equals(className)
                || "com.shinoow.abyssalcraft.common.blocks.itemblock.ItemTieredEnergyContainerBlock".equals(className)
                || "com.shinoow.abyssalcraft.common.blocks.itemblock.ItemTieredEnergyRelayBlock".equals(className)
                || "com.shinoow.abyssalcraft.api.ritual.RitualRegistry".equals(className)
                || "com.shinoow.abyssalcraft.common.blocks.tile.TileEntityRitualAltar".equals(className)
                || (GpomEarlyConfig.abyssalCraftTieredAltarLoadedCrossChunkFormationEnabled()
                && "com.shinoow.abyssalcraft.lib.util.RitualUtil".equals(className));
    }

    private static boolean transformClass(String className, ClassNode node) {
        if ("com.shinoow.abyssalcraft.api.ritual.RitualRegistry".equals(className)) {
            return patchRitualRegistry(node);
        }
        if ("com.shinoow.abyssalcraft.common.blocks.tile.TileEntityRitualAltar".equals(className)) {
            return patchRitualAltarTile(node);
        }
        if ("com.shinoow.abyssalcraft.lib.util.RitualUtil".equals(className)) {
            return patchRitualUtil(node);
        }
        for (MethodNode method : node.methods) {
            if ("getUnlockCondition".equals(method.name) && ("(" + ITEM_STACK + ")" + CONDITION).equals(method.desc)) {
                rewriteGetUnlockCondition(method);
                return true;
            }
        }
        return false;
    }

    private static boolean patchRitualUtil(ClassNode node) {
        for (MethodNode method : node.methods) {
            if ("tryAltar".equals(method.name)
                    && "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;ILnet/minecraft/entity/player/EntityPlayer;)Z".equals(method.desc)) {
                return replaceSameChunkCheck(method);
            }
        }
        return false;
    }

    private static void rewriteGetUnlockCondition(MethodNode method) {
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "net/minecraft/item/ItemStack",
                "func_77960_j",
                "()I",
                false
        ));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                SUPER_ITEM_BLOCK,
                "getUnlockCondition",
                "(" + ITEM_STACK + ")" + CONDITION,
                false
        ));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HOOKS,
                "unlockConditionForTier",
                "(I" + CONDITION + ")" + CONDITION,
                false
        ));
        instructions.add(new InsnNode(Opcodes.ARETURN));

        method.instructions.clear();
        method.tryCatchBlocks.clear();
        method.localVariables = null;
        method.instructions.add(instructions);
        method.maxStack = 3;
        method.maxLocals = 2;
    }

    private static boolean patchRitualRegistry(ClassNode node) {
        boolean changed = false;
        for (MethodNode method : node.methods) {
            if ("canPerformAction".equals(method.name) && "(II)Z".equals(method.desc)) {
                addConfiguredRitualActionFastPath(method);
                changed = true;
            } else if ("sameBookType".equals(method.name) && "(II)Z".equals(method.desc)) {
                addConfiguredSameBookTypeFastPath(method);
                changed = true;
            } else if ("areRitualsSame".equals(method.name)
                    && ("(Lcom/shinoow/abyssalcraft/api/ritual/NecronomiconRitual;II[Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)Z").equals(method.desc)) {
                boolean replaced = replaceRitualDimensionCheck(method);
                if (replaced) {
                    addRitualCandidateProbe(method);
                }
                changed |= replaced;
            }
        }
        return changed;
    }

    private static boolean patchRitualAltarTile(ClassNode node) {
        for (MethodNode method : node.methods) {
            if ("performRitual".equals(method.name)
                    && "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/player/EntityPlayer;)V".equals(method.desc)) {
                addPerformRitualProbe(method);
                return true;
            }
        }
        return false;
    }

    private static void addConfiguredRitualActionFastPath(MethodNode method) {
        LabelNode originalLogic = new LabelNode();
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HOOKS,
                "canPerformRitualAction",
                "(II)Z",
                false
        ));
        instructions.add(new JumpInsnNode(Opcodes.IFEQ, originalLogic));
        instructions.add(new InsnNode(Opcodes.ICONST_1));
        instructions.add(new InsnNode(Opcodes.IRETURN));
        instructions.add(originalLogic);
        method.instructions.insert(instructions);
        method.maxStack = Math.max(method.maxStack, 2);
    }

    private static void addConfiguredSameBookTypeFastPath(MethodNode method) {
        LabelNode originalLogic = new LabelNode();
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HOOKS,
                "sameRitualBookType",
                "(II)Z",
                false
        ));
        instructions.add(new JumpInsnNode(Opcodes.IFEQ, originalLogic));
        instructions.add(new InsnNode(Opcodes.ICONST_1));
        instructions.add(new InsnNode(Opcodes.IRETURN));
        instructions.add(originalLogic);
        method.instructions.insert(instructions);
        method.maxStack = Math.max(method.maxStack, 2);
    }

    private static void addRitualCandidateProbe(MethodNode method) {
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 5));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HOOKS,
                "probeRitualCandidate",
                "(Lcom/shinoow/abyssalcraft/api/ritual/NecronomiconRitual;II[Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)V",
                false
        ));
        method.instructions.insert(instructions);
        method.maxStack = Math.max(method.maxStack, 5);
    }

    private static void addPerformRitualProbe(MethodNode method) {
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new FieldInsnNode(
                Opcodes.GETFIELD,
                "net/minecraft/world/World",
                "field_73011_w",
                "Lnet/minecraft/world/WorldProvider;"
        ));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "net/minecraft/world/WorldProvider",
                "getDimension",
                "()I",
                false
        ));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "net/minecraft/entity/player/EntityPlayer",
                "func_184614_ca",
                "()Lnet/minecraft/item/ItemStack;",
                false
        ));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HOOKS,
                "probePerformRitual",
                "(ILjava/lang/Object;Ljava/lang/Object;)V",
                false
        ));
        method.instructions.insert(instructions);
        method.maxStack = Math.max(method.maxStack, 3);
    }

    private static boolean replaceRitualDimensionCheck(MethodNode method) {
        AbstractInsnNode cursor = firstOpcode(method, Opcodes.ALOAD);
        if (!(cursor instanceof VarInsnNode) || ((VarInsnNode) cursor).var != 1) {
            return false;
        }
        AbstractInsnNode getDimension = nextReal(cursor);
        AbstractInsnNode loadCurrentDimension = nextReal(getDimension);
        AbstractInsnNode jumpWhenEqual = nextReal(loadCurrentDimension);
        AbstractInsnNode secondLoadRitual = nextReal(jumpWhenEqual);
        AbstractInsnNode secondGetDimension = nextReal(secondLoadRitual);
        AbstractInsnNode loadAnyDimension = nextReal(secondGetDimension);
        AbstractInsnNode jumpWhenNotAny = nextReal(loadAnyDimension);

        if (!(getDimension instanceof MethodInsnNode)
                || !isRitualGetDimension((MethodInsnNode) getDimension)
                || !(loadCurrentDimension instanceof VarInsnNode)
                || ((VarInsnNode) loadCurrentDimension).var != 2
                || !(jumpWhenEqual instanceof JumpInsnNode)
                || jumpWhenEqual.getOpcode() != Opcodes.IF_ICMPEQ
                || !(secondLoadRitual instanceof VarInsnNode)
                || ((VarInsnNode) secondLoadRitual).var != 1
                || !(secondGetDimension instanceof MethodInsnNode)
                || !isRitualGetDimension((MethodInsnNode) secondGetDimension)
                || loadAnyDimension == null
                || loadAnyDimension.getOpcode() != Opcodes.ICONST_M1
                || !(jumpWhenNotAny instanceof JumpInsnNode)
                || jumpWhenNotAny.getOpcode() != Opcodes.IF_ICMPNE) {
            return false;
        }

        LabelNode continueLabel = ((JumpInsnNode) jumpWhenEqual).label;
        LabelNode failLabel = ((JumpInsnNode) jumpWhenNotAny).label;
        InsnList replacement = new InsnList();
        replacement.add(new VarInsnNode(Opcodes.ALOAD, 1));
        replacement.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "com/shinoow/abyssalcraft/api/ritual/NecronomiconRitual",
                "getDimension",
                "()I",
                false
        ));
        replacement.add(new VarInsnNode(Opcodes.ILOAD, 2));
        replacement.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HOOKS,
                "ritualDimensionMatches",
                "(II)Z",
                false
        ));
        replacement.add(new JumpInsnNode(Opcodes.IFNE, continueLabel));
        replacement.add(new JumpInsnNode(Opcodes.GOTO, failLabel));

        method.instructions.insertBefore(cursor, replacement);
        removeInclusive(method, cursor, jumpWhenNotAny);
        method.maxStack = Math.max(method.maxStack, 2);
        return true;
    }

    private static boolean replaceSameChunkCheck(MethodNode method) {
        for (AbstractInsnNode cursor = method.instructions.getFirst(); cursor != null; cursor = cursor.getNext()) {
            if (!(cursor instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) cursor;
            if (call.getOpcode() == Opcodes.INVOKESTATIC
                    && "com/shinoow/abyssalcraft/lib/util/RitualUtil".equals(call.owner)
                    && "sameChunk".equals(call.name)
                    && "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/player/EntityPlayer;)Z".equals(call.desc)) {
                call.owner = HOOKS;
                call.name = "sameChunkOrLoadedRitualStructure";
                return true;
            }
        }
        return false;
    }

    private static boolean isRitualGetDimension(MethodInsnNode node) {
        return "com/shinoow/abyssalcraft/api/ritual/NecronomiconRitual".equals(node.owner)
                && "getDimension".equals(node.name)
                && "()I".equals(node.desc);
    }

    private static AbstractInsnNode firstOpcode(MethodNode method, int opcode) {
        AbstractInsnNode cursor = method.instructions.getFirst();
        while (cursor != null && cursor.getOpcode() != opcode) {
            cursor = cursor.getNext();
        }
        return cursor;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode node) {
        if (node == null) {
            return null;
        }
        AbstractInsnNode cursor = node.getNext();
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getNext();
        }
        return cursor;
    }

    private static void removeInclusive(MethodNode method, AbstractInsnNode first, AbstractInsnNode last) {
        AbstractInsnNode cursor = first;
        while (cursor != null) {
            AbstractInsnNode next = cursor.getNext();
            method.instructions.remove(cursor);
            if (cursor == last) {
                return;
            }
            cursor = next;
        }
    }

    private static final class SafeClassWriter extends ClassWriter {
        private SafeClassWriter(int flags) {
            super(flags);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            return "java/lang/Object";
        }
    }
}
