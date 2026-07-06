package com.l.gpom.core;

import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.util.OptionalModRuntime;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Iterator;

public final class ArchitectureCraftCompatibilityTransformer implements IClassTransformer {
    private static final String BLOCK_ARCHITECTURE = "com.elytradev.architecture.common.block.BlockArchitecture";
    private static final String BLOCK_SHAPE = "com.elytradev.architecture.common.block.BlockShape";
    private static final String RENDER_TARGET_WORLD = "com.elytradev.architecture.client.render.target.RenderTargetWorld";
    private static final String TILE_SAWBENCH = "com.elytradev.architecture.common.tile.TileSawbench";
    private static final String HELPER = "com/l/gpom/compat/architecturecraft/ArchitectureCraftHitboxCompat";
    private static final String MATERIAL_HELPER = "com/l/gpom/compat/architecturecraft/ArchitectureCraftMaterialCompat";
    private static final String RAYTRACE_DESC = "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/RayTraceResult;";
    private static final String BOUNDING_BOX_DESC = "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/AxisAlignedBB;";
    private static final String SELECTED_BOX_DESC = "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/AxisAlignedBB;";
    private static final String SIDE_RENDER_DESC = "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/EnumFacing;)Z";
    private static final String LIGHT_VERTEX_DESC = "(Lcom/elytradev/architecture/common/helpers/Vector3;)V";
    private static final String ACCEPTABLE_MATERIAL_DESC = "(Lnet/minecraft/block/Block;)Z";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if (className == null) {
            return basicClass;
        }

        try {
            if (GpomEarlyConfig.architectureCraftFastShapeLightingEnabled()
                    && !OptionalModRuntime.ausmPresent()
                    && RENDER_TARGET_WORLD.equals(className)) {
                return patchRenderTargetWorld(basicClass);
            }
            if (GpomEarlyConfig.architectureCraftAccurateHitboxesEnabled()
                    && (BLOCK_ARCHITECTURE.equals(className) || BLOCK_SHAPE.equals(className))) {
                return patchArchitectureBlock(
                        basicClass,
                        BLOCK_SHAPE.equals(className),
                        GpomEarlyConfig.architectureCraftParentMaterialOcclusionEnabled()
                );
            }
            if (GpomEarlyConfig.architectureCraftAdditionalSawbenchMaterialsEnabled()
                    && TILE_SAWBENCH.equals(className)) {
                return patchTileSawbench(basicClass);
            }
        } catch (Throwable ignored) {
        }
        return basicClass;
    }

    private static byte[] patchRenderTargetWorld(byte[] basicClass) {
        ClassNode node = readNode(basicClass);
        replaceMethod(node, fastLightVertexMethod());
        return writeNode(node);
    }

    private static byte[] patchTileSawbench(byte[] basicClass) {
        ClassNode node = readNode(basicClass);
        replaceMethod(node, acceptableMaterialMethod());
        return writeNode(node);
    }

    private static byte[] patchArchitectureBlock(byte[] basicClass, boolean shapeBlock, boolean parentMaterialOcclusion) {
        ClassNode node = readNode(basicClass);
        replaceMethod(node, rayTraceMethod("func_180636_a"));
        replaceMethod(node, rayTraceMethod("collisionRayTrace"));
        replaceMethod(node, boundingBoxMethod("func_185496_a"));
        replaceMethod(node, boundingBoxMethod("getBoundingBox"));
        replaceMethod(node, boundingBoxMethod("func_180646_a"));
        replaceMethod(node, boundingBoxMethod("getCollisionBoundingBox"));
        replaceMethod(node, selectedBoxMethod("func_180640_a"));
        replaceMethod(node, selectedBoxMethod("getSelectedBoundingBox"));
        if (shapeBlock && parentMaterialOcclusion) {
            replaceMethod(node, sideMethod("func_176225_a"));
            replaceMethod(node, sideMethod("shouldSideBeRendered"));
        }
        return writeNode(node);
    }

    private static MethodNode fastLightVertexMethod() {
        MethodNode method = new MethodNode(Opcodes.ACC_PROTECTED, "lightVertex", LIGHT_VERTEX_DESC, null, null);
        InsnList instructions = method.instructions;
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "com/elytradev/architecture/client/render/target/RenderTargetWorld",
                "brLightVertex",
                LIGHT_VERTEX_DESC,
                false
        ));
        instructions.add(new InsnNode(Opcodes.RETURN));
        return method;
    }

    private static MethodNode rayTraceMethod(String name) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, RAYTRACE_DESC, null, null);
        InsnList instructions = method.instructions;
        loadArgs(instructions, 5);
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HELPER,
                "rayTraceShape",
                "(Lnet/minecraft/block/Block;" + RAYTRACE_DESC.substring(1),
                false
        ));
        instructions.add(new InsnNode(Opcodes.ARETURN));
        return method;
    }

    private static MethodNode boundingBoxMethod(String name) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, BOUNDING_BOX_DESC, null, null);
        InsnList instructions = method.instructions;
        loadArgs(instructions, 3);
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HELPER,
                "boundingBox",
                "(Lnet/minecraft/block/Block;" + BOUNDING_BOX_DESC.substring(1),
                false
        ));
        instructions.add(new InsnNode(Opcodes.ARETURN));
        return method;
    }

    private static MethodNode selectedBoxMethod(String name) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, SELECTED_BOX_DESC, null, null);
        InsnList instructions = method.instructions;
        loadArgs(instructions, 3);
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HELPER,
                "selectedBoundingBox",
                "(Lnet/minecraft/block/Block;" + SELECTED_BOX_DESC.substring(1),
                false
        ));
        instructions.add(new InsnNode(Opcodes.ARETURN));
        return method;
    }

    private static MethodNode sideMethod(String name) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, SIDE_RENDER_DESC, null, null);
        InsnList instructions = method.instructions;
        loadArgs(instructions, 4);
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HELPER,
                "baseShouldSideBeRendered",
                "(Lnet/minecraft/block/Block;" + SIDE_RENDER_DESC.substring(1),
                false
        ));
        instructions.add(new InsnNode(Opcodes.IRETURN));
        return method;
    }

    private static MethodNode acceptableMaterialMethod() {
        MethodNode method = new MethodNode(Opcodes.ACC_PROTECTED, "isAcceptableMaterial", ACCEPTABLE_MATERIAL_DESC, null, null);
        InsnList instructions = method.instructions;
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                MATERIAL_HELPER,
                "isAcceptableSawbenchMaterial",
                ACCEPTABLE_MATERIAL_DESC,
                false
        ));
        instructions.add(new InsnNode(Opcodes.IRETURN));
        return method;
    }

    private static void loadArgs(InsnList instructions, int count) {
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        for (int index = 1; index <= count; index++) {
            instructions.add(new VarInsnNode(Opcodes.ALOAD, index));
        }
    }

    private static void replaceMethod(ClassNode node, MethodNode replacement) {
        for (Iterator<MethodNode> iterator = node.methods.iterator(); iterator.hasNext(); ) {
            MethodNode method = iterator.next();
            if (method.name.equals(replacement.name) && method.desc.equals(replacement.desc)) {
                iterator.remove();
            }
        }
        node.methods.add(replacement);
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
