package com.l.gpom.core;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class BuildingGadgetsFramedCopyPasteTransformer implements IClassTransformer {
    private static final String COPY_PASTE_CLASS = "com.direwolf20.buildinggadgets.common.items.gadgets.GadgetCopyPaste";
    private static final String BLOCK_BUILD_ENTITY_CLASS = "com.direwolf20.buildinggadgets.common.entities.BlockBuildEntity";
    private static final String CONSTRUCTION_BLOCK_ENTITY_CLASS = "com.direwolf20.buildinggadgets.common.entities.ConstructionBlockEntity";
    private static final String CONSTRUCTION_TILE_CLASS = "com/direwolf20/buildinggadgets/common/blocks/ConstructionBlockTileEntity";
    private static final String HOOKS = "com/l/gpom/compat/buildinggadgets/BuildingGadgetsFramedCopyPasteHooks";
    private static final String WORLD = "Lnet/minecraft/world/World;";
    private static final String BLOCK_POS = "Lnet/minecraft/util/math/BlockPos;";
    private static final String ITEM_STACK = "Lnet/minecraft/item/ItemStack;";
    private static final String ENTITY_PLAYER = "Lnet/minecraft/entity/player/EntityPlayer;";
    private static final String BLOCK_STATE = "Lnet/minecraft/block/state/IBlockState;";
    private static final String TILE_ENTITY = "Lnet/minecraft/tileentity/TileEntity;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !GpomEarlyConfig.buildingGadgetsFramedCopyPasteCompatEnabled()) {
            return basicClass;
        }
        String className = transformedName != null ? transformedName : name;
        if (className == null) {
            return basicClass;
        }
        try {
            if (COPY_PASTE_CLASS.equals(className)) {
                return patchGadgetCopyPaste(basicClass);
            }
            if (BLOCK_BUILD_ENTITY_CLASS.equals(className)) {
                return patchBlockBuildEntity(basicClass);
            }
            if (CONSTRUCTION_BLOCK_ENTITY_CLASS.equals(className)) {
                return patchConstructionBlockEntity(basicClass);
            }
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[GPOM BuildingGadgets Framed CopyPaste] Failed to patch {}; continuing with original bytecode", className, throwable);
        }
        return basicClass;
    }

    private static byte[] patchGadgetCopyPaste(byte[] basicClass) {
        ClassNode node = readNode(basicClass);
        boolean changed = false;
        for (MethodNode method : node.methods) {
            if ("findBlocks".equals(method.name)
                    && method.desc.startsWith("(" + WORLD + BLOCK_POS + BLOCK_POS + ITEM_STACK + ENTITY_PLAYER)
                    && method.desc.endsWith(")Z")) {
                changed |= patchFindBlocks(method);
            } else if ("placeBlock".equals(method.name)
                    && method.desc.startsWith("(" + WORLD + BLOCK_POS + ENTITY_PLAYER + BLOCK_STATE)
                    && method.desc.endsWith("Ljava/util/Map;)V")) {
                changed |= patchPlaceBlock(method);
            }
        }
        if (changed) {
            GPOM.LOGGER.info("[GPOM BuildingGadgets Framed CopyPaste] Patched GadgetCopyPaste");
        }
        return changed ? writeNode(node) : basicClass;
    }

    private static boolean patchFindBlocks(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && "net/minecraft/world/World".equals(call.owner)
                        && "func_175625_s".equals(call.name)
                        && ("(" + BLOCK_POS + ")" + TILE_ENTITY).equals(call.desc)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = HOOKS;
                    call.name = "allowFramedTileCopy";
                    call.desc = "(" + WORLD + BLOCK_POS + ")" + TILE_ENTITY;
                    call.itf = false;
                    changed = true;
                }
            }
        }

        int copiedLocal = method.maxLocals;
        boolean injectedReturn = false;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() != Opcodes.IRETURN) {
                continue;
            }
            InsnList hook = new InsnList();
            hook.add(new InsnNode(Opcodes.DUP));
            hook.add(new VarInsnNode(Opcodes.ISTORE, copiedLocal));
            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
            hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
            hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
            hook.add(new VarInsnNode(Opcodes.ALOAD, 3));
            hook.add(new VarInsnNode(Opcodes.ALOAD, 4));
            hook.add(new VarInsnNode(Opcodes.ALOAD, 5));
            hook.add(new VarInsnNode(Opcodes.ILOAD, copiedLocal));
            hook.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    HOOKS,
                    "storeCopiedTileData",
                    "(" + WORLD + BLOCK_POS + BLOCK_POS + ITEM_STACK + ENTITY_PLAYER + "Ljava/lang/Object;Z)V",
                    false
            ));
            method.instructions.insertBefore(insn, hook);
            injectedReturn = true;
            changed = true;
        }
        if (injectedReturn) {
            method.maxLocals = Math.max(method.maxLocals, copiedLocal + 1);
        }
        return changed;
    }

    private static boolean patchPlaceBlock(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) insn;
            if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !"net/minecraft/world/World".equals(call.owner)
                    || !"func_72838_d".equals(call.name)
                    || !"(Lnet/minecraft/entity/Entity;)Z".equals(call.desc)) {
                continue;
            }
            InsnList hook = new InsnList();
            hook.add(new InsnNode(Opcodes.DUP2));
            hook.add(new InsnNode(Opcodes.POP));
            hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
            hook.add(new VarInsnNode(Opcodes.ALOAD, 3));
            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
            hook.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    HOOKS,
                    "queueTileDataForPlacement",
                    "(" + WORLD + BLOCK_POS + ENTITY_PLAYER + "Ljava/lang/Object;)V",
                    false
            ));
            method.instructions.insertBefore(insn, hook);
            changed = true;
            break;
        }
        return changed;
    }

    private static byte[] patchBlockBuildEntity(byte[] basicClass) {
        ClassNode node = readNode(basicClass);
        boolean changed = false;
        for (MethodNode method : node.methods) {
            if ("setDespawning".equals(method.name) && "()V".equals(method.desc)) {
                changed |= patchSetDespawning(node, method);
            }
        }
        if (changed) {
            GPOM.LOGGER.info("[GPOM BuildingGadgets Framed CopyPaste] Patched BlockBuildEntity");
        }
        return changed ? writeNode(node) : basicClass;
    }

    private static boolean patchSetDespawning(ClassNode node, MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) insn;
            if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && CONSTRUCTION_TILE_CLASS.equals(call.owner)
                    && "setActualBlockState".equals(call.name)
                    && ("(" + BLOCK_STATE + ")Z").equals(call.desc)) {
                AbstractInsnNode afterPop = call.getNext();
                if (afterPop != null && afterPop.getOpcode() == Opcodes.POP) {
                    method.instructions.insert(afterPop, attachConstructionTileHook(node));
                    changed = true;
                }
                continue;
            }
            if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !"net/minecraft/block/Block".equals(call.owner)
                    || !"func_189540_a".equals(call.name)
                    || !("(" + BLOCK_STATE + WORLD + BLOCK_POS + "Lnet/minecraft/block/Block;" + BLOCK_POS + ")V").equals(call.desc)) {
                continue;
            }
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
            hook.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "world", WORLD));
            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
            hook.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "setPos", BLOCK_POS));
            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
            hook.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "setBlock", BLOCK_STATE));
            hook.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    HOOKS,
                    "applyPendingTileData",
                    "(" + WORLD + BLOCK_POS + BLOCK_STATE + ")V",
                    false
            ));
            method.instructions.insert(insn, hook);
            changed = true;
        }
        return changed;
    }

    private static byte[] patchConstructionBlockEntity(byte[] basicClass) {
        ClassNode node = readNode(basicClass);
        boolean changed = false;
        for (MethodNode method : node.methods) {
            if ("setDespawning".equals(method.name) && "()V".equals(method.desc)) {
                changed |= patchConstructionSetDespawning(node, method);
            }
        }
        if (changed) {
            GPOM.LOGGER.info("[GPOM BuildingGadgets Framed CopyPaste] Patched ConstructionBlockEntity");
        }
        return changed ? writeNode(node) : basicClass;
    }

    private static boolean patchConstructionSetDespawning(ClassNode node, MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) insn;
            if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && "net/minecraft/world/World".equals(call.owner)
                    && "func_175656_a".equals(call.name)
                    && ("(" + BLOCK_POS + BLOCK_STATE + ")Z").equals(call.desc)) {
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "world", WORLD));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "setPos", BLOCK_POS));
                hook.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        HOOKS,
                        "preserveConstructionTileDataBeforeReplace",
                        "(" + WORLD + BLOCK_POS + ")V",
                        false
                ));
                method.instructions.insertBefore(insn, hook);
                changed = true;
                continue;
            }
            if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && CONSTRUCTION_TILE_CLASS.equals(call.owner)
                    && "setActualBlockState".equals(call.name)
                    && ("(" + BLOCK_STATE + ")Z").equals(call.desc)) {
                AbstractInsnNode afterPop = call.getNext();
                if (afterPop != null && afterPop.getOpcode() == Opcodes.POP) {
                    method.instructions.insert(afterPop, attachConstructionTileHook(node));
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static InsnList attachConstructionTileHook(ClassNode node) {
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
        hook.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "world", WORLD));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
        hook.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "setPos", BLOCK_POS));
        hook.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HOOKS,
                "attachPendingTileDataToConstructionTile",
                "(" + WORLD + BLOCK_POS + ")V",
                false
        ));
        return hook;
    }

    private static ClassNode readNode(byte[] basicClass) {
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        return node;
    }

    private static byte[] writeNode(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
