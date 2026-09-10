package com.luna.gpom.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Remaps MCP-named calls in GPOM core helpers, which Forge's runtime remapper intentionally skips. */
public final class GpomRuntimeNameTransformer implements IClassTransformer {
    private static final String CYCLIC_HELPER = "com.luna.gpom.optimization.CyclicInventoryOptimizations";
    private static final String VERTEX_HELPER_PREFIX = "com.luna.gpom.optimization.ForgeVertexPack";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        String className = transformedName != null ? transformedName : name;
        if (!CYCLIC_HELPER.equals(className) && !className.startsWith(VERTEX_HELPER_PREFIX)) {
            return basicClass;
        }

        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        boolean changed = false;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode) {
                    changed |= remapMethod(className, (MethodInsnNode) instruction);
                } else if (instruction instanceof FieldInsnNode) {
                    changed |= remapField(className, (FieldInsnNode) instruction);
                }
            }
        }
        if (!changed) {
            return basicClass;
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean remapMethod(String className, MethodInsnNode call) {
        if (CYCLIC_HELPER.equals(className)) {
            if ("net/minecraft/item/ItemStack".equals(call.owner)) {
                String mapped = mapItemStackMethod(call.name);
                if (mapped != null) {
                    call.name = mapped;
                    return true;
                }
            } else if ("net/minecraft/inventory/IInventory".equals(call.owner)) {
                if ("getStackInSlot".equals(call.name)) {
                    call.name = "func_70301_a";
                    return true;
                }
                if ("setInventorySlotContents".equals(call.name)) {
                    call.name = "func_70299_a";
                    return true;
                }
            }
            return false;
        }

        if ("net/minecraft/client/renderer/vertex/VertexFormat".equals(call.owner)) {
            if ("getElement".equals(call.name)) call.name = "func_177348_c";
            else if ("getSize".equals(call.name)) call.name = "func_177338_f";
            else if ("getOffset".equals(call.name)) call.name = "func_181720_d";
            else if ("getElementCount".equals(call.name)) call.name = "func_177345_h";
            else return false;
            return true;
        }
        if ("net/minecraft/client/renderer/vertex/VertexFormatElement".equals(call.owner)) {
            if ("getElementCount".equals(call.name)) call.name = "func_177370_d";
            else if ("getType".equals(call.name)) call.name = "func_177367_b";
            else return false;
            return true;
        }
        if ("net/minecraft/client/renderer/vertex/VertexFormatElement$EnumType".equals(call.owner)
                && "getSize".equals(call.name)) {
            call.name = "func_177395_a";
            return true;
        }
        return false;
    }

    private static String mapItemStackMethod(String name) {
        if ("isEmpty".equals(name)) return "func_190926_b";
        if ("getItem".equals(name)) return "func_77973_b";
        if ("getItemDamage".equals(name)) return "func_77960_j";
        if ("areItemStackTagsEqual".equals(name)) return "func_77970_a";
        if ("getMaxStackSize".equals(name)) return "func_77976_d";
        if ("getCount".equals(name)) return "func_190916_E";
        if ("shrink".equals(name)) return "func_190918_g";
        if ("grow".equals(name)) return "func_190917_f";
        return null;
    }

    private static boolean remapField(String className, FieldInsnNode field) {
        if (CYCLIC_HELPER.equals(className)
                && "net/minecraft/item/ItemStack".equals(field.owner)
                && "EMPTY".equals(field.name)) {
            field.name = "field_190927_a";
            return true;
        }
        return false;
    }
}
