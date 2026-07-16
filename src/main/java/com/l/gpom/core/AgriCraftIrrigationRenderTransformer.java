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
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ListIterator;

public final class AgriCraftIrrigationRenderTransformer implements IClassTransformer {
    private static final String RENDER_SPRINKLER = "com.infinityraider.agricraft.renderers.blocks.RenderSprinkler";
    private static final String RENDER_SPRINKLER_INTERNAL = "com/infinityraider/agricraft/renderers/blocks/RenderSprinkler";
    private static final String CONFIG_INTERNAL = "com/l/gpom/config/GpomEarlyConfig";
    private static final String AGRICRAFT_CONFIG_INTERNAL = "com/infinityraider/agricraft/reference/AgriCraftConfig";
    private static final String BASE_ICONS_INTERNAL = "com/infinityraider/agricraft/utility/BaseIcons";
    private static final String TESSELLATOR_DESC = "Lcom/infinityraider/infinitylib/render/tessellation/ITessellator;";
    private static final String SPRITE_DESC = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;";
    private static final String STATIC_DESC = "(" + TESSELLATOR_DESC
            + "Lnet/minecraft/block/state/IBlockState;"
            + "Lcom/infinityraider/agricraft/blocks/irrigation/BlockSprinkler;"
            + "Lnet/minecraft/util/EnumFacing;)V";
    private static final String DYNAMIC_DESC = "(" + TESSELLATOR_DESC
            + "Lnet/minecraft/world/World;"
            + "Lnet/minecraft/util/math/BlockPos;"
            + "DDD"
            + "Lcom/infinityraider/agricraft/blocks/irrigation/BlockSprinkler;"
            + "Lcom/infinityraider/agricraft/tiles/irrigation/TileEntitySprinkler;"
            + "FIF)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !GpomEarlyConfig.agriCraftStaticSprinklerHeadEnabled()) {
            return basicClass;
        }
        String className = transformedName != null ? transformedName : name;
        if (!RENDER_SPRINKLER.equals(className)) {
            return basicClass;
        }

        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, ClassReader.EXPAND_FRAMES);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if ("renderWorldBlockStatic".equals(method.name) && STATIC_DESC.equals(method.desc)) {
                    changed |= injectStaticHeadAfterConnector(method);
                } else if ("renderWorldBlockDynamic".equals(method.name) && DYNAMIC_DESC.equals(method.desc)) {
                    injectSkipDynamicHead(method);
                    changed = true;
                }
            }
            if (!hasMethod(node, "hasDynamicRendering", "()Z")) {
                node.methods.add(hasDynamicRenderingOverride());
                changed = true;
            }
            if (!changed) {
                return basicClass;
            }

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
                @Override
                protected String getCommonSuperClass(String type1, String type2) {
                    return "java/lang/Object";
                }
            };
            node.accept(writer);
            GPOM.LOGGER.info("[GPOM AgriCraft Render] Patched static sprinkler head rendering");
            return writer.toByteArray();
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[GPOM AgriCraft Render] Failed to patch {}; continuing with original bytecode", className, throwable);
            return basicClass;
        }
    }

    private static boolean injectStaticHeadAfterConnector(MethodNode method) {
        ListIterator<AbstractInsnNode> iterator = method.instructions.iterator();
        while (iterator.hasNext()) {
            AbstractInsnNode insn = iterator.next();
            if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL || !(insn instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) insn;
            if (RENDER_SPRINKLER_INTERNAL.equals(call.owner)
                    && "renderConnector".equals(call.name)
                    && ("(" + TESSELLATOR_DESC + SPRITE_DESC + ")V").equals(call.desc)) {
                method.instructions.insert(insn, staticHeadDraw());
                return true;
            }
        }
        return false;
    }

    private static void injectSkipDynamicHead(MethodNode method) {
        LabelNode runOriginal = new LabelNode();
        InsnList guard = new InsnList();
        addStaticSprinklerEnabledCheck(guard, runOriginal);
        guard.add(new InsnNode(Opcodes.RETURN));
        guard.add(runOriginal);
        method.instructions.insert(guard);
    }

    private static MethodNode hasDynamicRenderingOverride() {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "hasDynamicRendering", "()Z", null, null);
        LabelNode runOriginal = new LabelNode();
        addStaticSprinklerEnabledCheck(method.instructions, runOriginal);
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(runOriginal);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                "com/infinityraider/infinitylib/render/block/RenderBlockWithTileBase",
                "hasDynamicRendering",
                "()Z",
                false
        ));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        return method;
    }

    private static boolean hasMethod(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) {
                return true;
            }
        }
        return false;
    }

    private static InsnList staticHeadDraw() {
        LabelNode skip = new LabelNode();
        InsnList list = new InsnList();
        addStaticSprinklerEnabledCheck(list, skip);
        list.add(new VarInsnNode(Opcodes.ALOAD, 0));
        list.add(new VarInsnNode(Opcodes.ALOAD, 1));
        list.add(new InsnNode(Opcodes.FCONST_0));
        list.add(new FieldInsnNode(
                Opcodes.GETSTATIC,
                BASE_ICONS_INTERNAL,
                "IRON_BLOCK",
                "L" + BASE_ICONS_INTERNAL + ";"
        ));
        list.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                BASE_ICONS_INTERNAL,
                "getIcon",
                "()" + SPRITE_DESC,
                false
        ));
        list.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                RENDER_SPRINKLER_INTERNAL,
                "renderHead",
                "(" + TESSELLATOR_DESC + "F" + SPRITE_DESC + ")V",
                false
        ));
        list.add(skip);
        return list;
    }

    private static void addStaticSprinklerEnabledCheck(InsnList list, LabelNode skip) {
        list.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                CONFIG_INTERNAL,
                "agriCraftStaticSprinklerHeadEnabled",
                "()Z",
                false
        ));
        list.add(new JumpInsnNode(Opcodes.IFEQ, skip));
        list.add(new FieldInsnNode(
                Opcodes.GETSTATIC,
                AGRICRAFT_CONFIG_INTERNAL,
                "disableParticles",
                "Z"
        ));
        list.add(new JumpInsnNode(Opcodes.IFEQ, skip));
    }
}
