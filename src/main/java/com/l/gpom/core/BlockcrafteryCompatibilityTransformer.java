package com.l.gpom.core;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockcrafteryCompatibilityTransformer implements IClassTransformer {
    private static final Set<String> PATCHED_TILE_CLASSES = ConcurrentHashMap.newKeySet();
    private static final String CUBE = "epicsquid.blockcraftery.block.BlockEditableCube";
    private static final String SLAB = "epicsquid.blockcraftery.block.BlockEditableSlab";
    private static final String STAIRS = "epicsquid.blockcraftery.block.BlockEditableStairs";
    private static final String SLANT = "epicsquid.blockcraftery.block.BlockEditableSlant";
    private static final String CORNER = "epicsquid.blockcraftery.block.BlockEditableCorner";
    private static final String FENCE = "epicsquid.blockcraftery.block.BlockEditableFence";
    private static final String WALL = "epicsquid.blockcraftery.block.BlockEditableWall";
    private static final String DOOR = "epicsquid.blockcraftery.block.BlockEditableDoor";
    private static final String TRAP_DOOR = "epicsquid.blockcraftery.block.BlockEditableTrapDoor";
    private static final String PRESSURE_PLATE = "epicsquid.blockcraftery.block.BlockEditablePressurePlate";
    private static final String MODEL_EDITABLE = "epicsquid.blockcraftery.model.BakedModelEditable";
    private static final String TILE_EDITABLE_BLOCK = "epicsquid.blockcraftery.tile.TileEditableBlock";
    private static final String WORLD = "net.minecraft.world.World";
    private static final String HELPER = "com/l/gpom/compat/blockcraftery/BlockcrafteryCompat";
    private static final String RENDER_HELPER = "com/l/gpom/compat/blockcraftery/BlockcrafteryRenderCompat";
    private static final String FRAMED_HELPER = "com/l/gpom/compat/framed/FramedMaterialData";
    private static final String FRAMED_ACCESS = "com/l/gpom/compat/framed/FramedMaterialDataAccess";
    private static final String DOUBLE_SLOPE_HELPER =
            "com/l/gpom/compat/blockcraftery/BlockcrafteryDoubleSlopeCompat";
    private static final String DOUBLE_SLOPE_ACCESS =
            "com/l/gpom/compat/blockcraftery/BlockcrafteryDoubleSlopeAccess";
    private static final String NBT_COMPOUND = "Lnet/minecraft/nbt/NBTTagCompound;";
    private static final String BLOCK_STATE = "Lnet/minecraft/block/state/IBlockState;";
    private static final String RAYTRACE_DESC = "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/RayTraceResult;";
    private static final String SELECTED_BOX_DESC = "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/AxisAlignedBB;";
    private static final String BOUNDING_BOX_DESC = "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/AxisAlignedBB;";
    private static final String SIDE_RENDER_DESC = "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/EnumFacing;)Z";
    private static final String GET_QUADS_DESC = "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/EnumFacing;J)Ljava/util/List;";
    private static final String RENDER_LAYER = "Lnet/minecraft/util/BlockRenderLayer;";
    private static final String MAY_PLACE_DESC = "(Lnet/minecraft/block/Block;Lnet/minecraft/util/math/BlockPos;ZLnet/minecraft/util/EnumFacing;Lnet/minecraft/entity/Entity;)Z";
    private static final String LIGHT_VALUE_DESC = "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;)I";

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
            boolean hitboxes = GpomEarlyConfig.blockcrafteryAccurateHitboxesEnabled();
            boolean parentMaterialOcclusion = GpomEarlyConfig.blockcrafteryParentMaterialOcclusionEnabled();
            boolean modelNullSideCull = false;
            boolean modelLayerCompat = GpomEarlyConfig.blockcrafteryModelRenderLayerCompatEnabled();
            boolean framedMaterialStorage = GpomEarlyConfig.framedMaterialStateStorageEnabled();
            boolean doubleSlopes = GpomEarlyConfig.blockcrafteryDoubleSlopesEnabled();
            if (TILE_EDITABLE_BLOCK.equals(className) && !framedMaterialStorage
                    && PATCHED_TILE_CLASSES.add("disabled:" + className)) {
                GPOM.LOGGER.warn(
                        "[GPOM Framed Material Probe] Blockcraftery tile storage disabled; "
                                + "emission, Bloom, and CTM metadata will not be attached"
                );
            }
            if (!hitboxes && !modelLayerCompat && !modelNullSideCull && !framedMaterialStorage && !doubleSlopes) {
                return basicClass;
            }
            if ((framedMaterialStorage || doubleSlopes) && TILE_EDITABLE_BLOCK.equals(className)) {
                return patchTileEditableBlock(basicClass, framedMaterialStorage, doubleSlopes);
            }
            if (framedMaterialStorage && isAnyEditableBlock(className)) {
                basicClass = patchEditableFramedVisuals(basicClass);
            }
            if (hitboxes && CUBE.equals(className)) {
                return patchCube(basicClass, parentMaterialOcclusion);
            }
            if (doubleSlopes && SLANT.equals(className)) {
                basicClass = patchDoubleSlopeBlock(basicClass);
            }
            if ((hitboxes || doubleSlopes) && (SLANT.equals(className) || CORNER.equals(className))) {
                return patchShapedBlock(basicClass);
            }
            if (hitboxes && WORLD.equals(className)) {
                return patchWorld(basicClass);
            }
            if ((modelLayerCompat || modelNullSideCull || framedMaterialStorage || doubleSlopes)
                    && MODEL_EDITABLE.equals(className)) {
                return patchEditableModel(
                        basicClass,
                        modelLayerCompat,
                        modelNullSideCull,
                        framedMaterialStorage || doubleSlopes
                );
            }
        } catch (Throwable throwable) {
            if (TILE_EDITABLE_BLOCK.equals(className) || MODEL_EDITABLE.equals(className)) {
                GPOM.LOGGER.warn(
                        "[GPOM Framed Material Probe] Failed to patch Blockcraftery target {}",
                        className,
                        throwable
                );
            }
        }
        return basicClass;
    }

    private static byte[] patchTileEditableBlock(
            byte[] basicClass,
            boolean framedMaterialStorage,
            boolean doubleSlopes
    ) {
        ClassNode node = readNode(basicClass);
        boolean accessChanged = framedMaterialStorage && addFramedMaterialAccess(node);
        boolean readChanged = framedMaterialStorage && patchTileRead(node);
        boolean writeChanged = framedMaterialStorage && patchTileWrite(node);
        boolean mutationChanged = framedMaterialStorage && patchTileMaterialMutation(node);
        boolean doubleAccessChanged = doubleSlopes && addDoubleSlopeAccess(node);
        boolean doubleReadChanged = doubleSlopes && patchDoubleSlopeRead(node);
        boolean doubleWriteChanged = doubleSlopes && patchDoubleSlopeWrite(node);
        boolean doubleActivateChanged = doubleSlopes && patchDoubleSlopeActivate(node);
        boolean doubleBreakChanged = doubleSlopes && patchDoubleSlopeBreak(node);
        boolean changed = accessChanged || readChanged || writeChanged || mutationChanged
                || doubleAccessChanged || doubleReadChanged || doubleWriteChanged
                || doubleActivateChanged || doubleBreakChanged;
        if (PATCHED_TILE_CLASSES.add(node.name)) {
            GPOM.LOGGER.info(
                    "[GPOM Framed Material Probe] Blockcraftery tile patched "
                            + "access={} read={} write={} mutation={} doubleAccess={} doubleRead={} "
                            + "doubleWrite={} doubleActivate={} doubleBreak={} changed={}",
                    accessChanged,
                    readChanged,
                    writeChanged,
                    mutationChanged,
                    doubleAccessChanged,
                    doubleReadChanged,
                    doubleWriteChanged,
                    doubleActivateChanged,
                    doubleBreakChanged,
                    changed
            );
        }
        return changed ? writeNode(node) : basicClass;
    }

    private static byte[] patchEditableFramedVisuals(byte[] basicClass) {
        ClassNode node = readNode(basicClass);
        boolean changed = patchLightValue(node, "getLightValue");
        return changed ? writeNode(node) : basicClass;
    }

    private static boolean patchLightValue(ClassNode node, String name) {
        boolean changed = false;
        for (MethodNode method : node.methods) {
            if (!name.equals(method.name) || !LIGHT_VALUE_DESC.equals(method.desc)
                    || hasStaticCall(method, FRAMED_HELPER, "inheritedLightValue")) {
                continue;
            }
            int returnLocal = method.maxLocals;
            method.maxLocals = returnLocal + 1;
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (instruction.getOpcode() != Opcodes.IRETURN) {
                    continue;
                }
                InsnList injected = new InsnList();
                injected.add(new VarInsnNode(Opcodes.ISTORE, returnLocal));
                injected.add(new VarInsnNode(Opcodes.ILOAD, returnLocal));
                injected.add(new VarInsnNode(Opcodes.ALOAD, 0));
                injected.add(new VarInsnNode(Opcodes.ALOAD, 1));
                injected.add(new VarInsnNode(Opcodes.ALOAD, 2));
                injected.add(new VarInsnNode(Opcodes.ALOAD, 3));
                injected.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        FRAMED_HELPER,
                        "inheritedLightValue",
                        "(ILnet/minecraft/block/Block;" + BLOCK_STATE
                                + "Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;)I",
                        false
                ));
                method.instructions.insertBefore(instruction, injected);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean patchTileMaterialMutation(ClassNode node) {
        boolean changed = false;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("activate")) {
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof org.objectweb.asm.tree.FieldInsnNode)) {
                    continue;
                }
                org.objectweb.asm.tree.FieldInsnNode field = (org.objectweb.asm.tree.FieldInsnNode) instruction;
                if (field.getOpcode() != Opcodes.PUTFIELD
                        || !field.owner.equals(node.name)
                        || !field.name.equals("state")
                        || !field.desc.equals(BLOCK_STATE)) {
                    continue;
                }
                InsnList injected = new InsnList();
                injected.add(new VarInsnNode(Opcodes.ALOAD, 0));
                injected.add(new VarInsnNode(Opcodes.ALOAD, 0));
                injected.add(new org.objectweb.asm.tree.FieldInsnNode(
                        Opcodes.GETFIELD,
                        node.name,
                        "state",
                        BLOCK_STATE
                ));
                injected.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        FRAMED_HELPER,
                        "refreshBlockcraftery",
                        "(L" + FRAMED_ACCESS + ";" + BLOCK_STATE + ")V",
                        false
                ));
                method.instructions.insert(instruction, injected);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean addFramedMaterialAccess(ClassNode node) {
        boolean changed = false;
        if (!node.interfaces.contains(FRAMED_ACCESS)) {
            node.interfaces.add(FRAMED_ACCESS);
            changed = true;
        }
        if (findField(node, "gpom$framedMaterialData", NBT_COMPOUND) == null) {
            node.fields.add(new FieldNode(
                    Opcodes.ACC_PRIVATE,
                    "gpom$framedMaterialData",
                    NBT_COMPOUND,
                    null,
                    null
            ));
            changed = true;
        }
        if (findMethod(node, "gpom$getFramedMaterialData", "()" + NBT_COMPOUND) == null) {
            node.methods.add(framedMaterialGetter());
            changed = true;
        }
        if (findMethod(node, "gpom$setFramedMaterialData", "(" + NBT_COMPOUND + ")V") == null) {
            node.methods.add(framedMaterialSetter());
            changed = true;
        }
        return changed;
    }

    private static boolean patchTileRead(ClassNode node) {
        boolean changed = false;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("func_145839_a") || !method.desc.equals("(" + NBT_COMPOUND + ")V")) {
                continue;
            }
            // Cleanroom may invoke a core transformer more than once for the
            // same class name. Do not append a second framed-state read path.
            if (hasStaticCall(method, FRAMED_HELPER, "authoritativeBlockcrafteryState")) {
                continue;
            }

            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (instruction.getOpcode() != Opcodes.RETURN) {
                    continue;
                }

                int authoritativeStateLocal = method.maxLocals;
                method.maxLocals = authoritativeStateLocal + 1;
                InsnList injected = new InsnList();
                injected.add(new VarInsnNode(Opcodes.ALOAD, 0));
                injected.add(new VarInsnNode(Opcodes.ALOAD, 1));
                injected.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        FRAMED_HELPER,
                        "read",
                        "(L" + FRAMED_ACCESS + ";" + NBT_COMPOUND + ")V",
                        false
                ));
                injected.add(new VarInsnNode(Opcodes.ALOAD, 0));
                injected.add(new VarInsnNode(Opcodes.ALOAD, 0));
                injected.add(new org.objectweb.asm.tree.FieldInsnNode(
                        Opcodes.GETFIELD,
                        node.name,
                        "state",
                        BLOCK_STATE
                ));
                injected.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        FRAMED_HELPER,
                        "authoritativeBlockcrafteryState",
                        "(Ljava/lang/Object;" + BLOCK_STATE + ")" + BLOCK_STATE,
                        false
                ));
                injected.add(new VarInsnNode(Opcodes.ASTORE, authoritativeStateLocal));
                injected.add(new VarInsnNode(Opcodes.ALOAD, 0));
                injected.add(new VarInsnNode(Opcodes.ALOAD, authoritativeStateLocal));
                injected.add(new org.objectweb.asm.tree.FieldInsnNode(
                        Opcodes.PUTFIELD,
                        node.name,
                        "state",
                        BLOCK_STATE
                ));
                injected.add(new VarInsnNode(Opcodes.ALOAD, 0));
                injected.add(new VarInsnNode(Opcodes.ALOAD, 0));
                injected.add(new org.objectweb.asm.tree.FieldInsnNode(
                        Opcodes.GETFIELD,
                        node.name,
                        "state",
                        BLOCK_STATE
                ));
                injected.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        FRAMED_HELPER,
                        "refreshBlockcraftery",
                        "(L" + FRAMED_ACCESS + ";" + BLOCK_STATE + ")V",
                        false
                ));
                method.instructions.insertBefore(instruction, injected);
                changed = true;
                break;
            }
        }
        return changed;
    }

    private static boolean hasStaticCall(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode methodInsn = (MethodInsnNode) instruction;
                if (methodInsn.getOpcode() == Opcodes.INVOKESTATIC
                        && owner.equals(methodInsn.owner)
                        && name.equals(methodInsn.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean patchTileWrite(ClassNode node) {
        boolean changed = false;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("func_189515_b") || !method.desc.equals("(" + NBT_COMPOUND + ")" + NBT_COMPOUND)) {
                continue;
            }

            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (instruction.getOpcode() != Opcodes.ARETURN) {
                    continue;
                }

                int returnLocal = method.maxLocals;
                method.maxLocals = returnLocal + 1;
                InsnList injected = new InsnList();
                injected.add(new VarInsnNode(Opcodes.ASTORE, returnLocal));
                injected.add(new VarInsnNode(Opcodes.ALOAD, 0));
                injected.add(new VarInsnNode(Opcodes.ALOAD, returnLocal));
                injected.add(new VarInsnNode(Opcodes.ALOAD, 0));
                injected.add(new org.objectweb.asm.tree.FieldInsnNode(
                        Opcodes.GETFIELD,
                        node.name,
                        "state",
                        BLOCK_STATE
                ));
                injected.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        FRAMED_HELPER,
                        "writeBlockcraftery",
                        "(L" + FRAMED_ACCESS + ";" + NBT_COMPOUND + BLOCK_STATE + ")V",
                        false
                ));
                injected.add(new VarInsnNode(Opcodes.ALOAD, returnLocal));
                method.instructions.insertBefore(instruction, injected);
                changed = true;
                break;
            }
        }
        return changed;
    }

    private static MethodNode framedMaterialGetter() {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "gpom$getFramedMaterialData",
                "()" + NBT_COMPOUND,
                null,
                null
        );
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new org.objectweb.asm.tree.FieldInsnNode(
                Opcodes.GETFIELD,
                TILE_EDITABLE_BLOCK.replace('.', '/'),
                "gpom$framedMaterialData",
                NBT_COMPOUND
        ));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        return method;
    }

    private static MethodNode framedMaterialSetter() {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "gpom$setFramedMaterialData",
                "(" + NBT_COMPOUND + ")V",
                null,
                null
        );
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new org.objectweb.asm.tree.FieldInsnNode(
                Opcodes.PUTFIELD,
                TILE_EDITABLE_BLOCK.replace('.', '/'),
                "gpom$framedMaterialData",
                NBT_COMPOUND
        ));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        return method;
    }

    private static boolean addDoubleSlopeAccess(ClassNode node) {
        boolean changed = false;
        if (!node.interfaces.contains(DOUBLE_SLOPE_ACCESS)) {
            node.interfaces.add(DOUBLE_SLOPE_ACCESS);
            changed = true;
        }
        if (findField(node, "gpom$doubleSlopeData", NBT_COMPOUND) == null) {
            node.fields.add(new FieldNode(
                    Opcodes.ACC_PRIVATE,
                    "gpom$doubleSlopeData",
                    NBT_COMPOUND,
                    null,
                    null
            ));
            changed = true;
        }
        if (findMethod(node, "gpom$getDoubleSlopeData", "()" + NBT_COMPOUND) == null) {
            MethodNode getter = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "gpom$getDoubleSlopeData",
                    "()" + NBT_COMPOUND,
                    null,
                    null
            );
            getter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            getter.instructions.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.GETFIELD,
                    node.name,
                    "gpom$doubleSlopeData",
                    NBT_COMPOUND
            ));
            getter.instructions.add(new InsnNode(Opcodes.ARETURN));
            node.methods.add(getter);
            changed = true;
        }
        if (findMethod(node, "gpom$setDoubleSlopeData", "(" + NBT_COMPOUND + ")V") == null) {
            MethodNode setter = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "gpom$setDoubleSlopeData",
                    "(" + NBT_COMPOUND + ")V",
                    null,
                    null
            );
            setter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            setter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            setter.instructions.add(new org.objectweb.asm.tree.FieldInsnNode(
                    Opcodes.PUTFIELD,
                    node.name,
                    "gpom$doubleSlopeData",
                    NBT_COMPOUND
            ));
            setter.instructions.add(new InsnNode(Opcodes.RETURN));
            node.methods.add(setter);
            changed = true;
        }
        return changed;
    }

    private static boolean patchDoubleSlopeRead(ClassNode node) {
        for (MethodNode method : node.methods) {
            if (!method.name.equals("func_145839_a")
                    || !method.desc.equals("(" + NBT_COMPOUND + ")V")
                    || hasStaticCall(method, DOUBLE_SLOPE_HELPER, "read")) {
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                 instruction = instruction.getNext()) {
                if (instruction.getOpcode() != Opcodes.RETURN) {
                    continue;
                }
                InsnList injected = new InsnList();
                injected.add(new VarInsnNode(Opcodes.ALOAD, 0));
                injected.add(new VarInsnNode(Opcodes.ALOAD, 1));
                injected.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        DOUBLE_SLOPE_HELPER,
                        "read",
                        "(L" + DOUBLE_SLOPE_ACCESS + ";" + NBT_COMPOUND + ")V",
                        false
                ));
                method.instructions.insertBefore(instruction, injected);
                return true;
            }
        }
        return false;
    }

    private static boolean patchDoubleSlopeWrite(ClassNode node) {
        for (MethodNode method : node.methods) {
            if (!method.name.equals("func_189515_b")
                    || !method.desc.equals("(" + NBT_COMPOUND + ")" + NBT_COMPOUND)
                    || hasStaticCall(method, DOUBLE_SLOPE_HELPER, "write")) {
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                 instruction = instruction.getNext()) {
                if (instruction.getOpcode() != Opcodes.ARETURN) {
                    continue;
                }
                int rootLocal = method.maxLocals++;
                InsnList injected = new InsnList();
                injected.add(new VarInsnNode(Opcodes.ASTORE, rootLocal));
                injected.add(new VarInsnNode(Opcodes.ALOAD, 0));
                injected.add(new VarInsnNode(Opcodes.ALOAD, rootLocal));
                injected.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        DOUBLE_SLOPE_HELPER,
                        "write",
                        "(L" + DOUBLE_SLOPE_ACCESS + ";" + NBT_COMPOUND + ")V",
                        false
                ));
                injected.add(new VarInsnNode(Opcodes.ALOAD, rootLocal));
                method.instructions.insertBefore(instruction, injected);
                return true;
            }
        }
        return false;
    }

    private static boolean patchDoubleSlopeActivate(ClassNode node) {
        final String descriptor = "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
                + BLOCK_STATE + "Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/EnumHand;"
                + "Lnet/minecraft/util/EnumFacing;FFF)Z";
        for (MethodNode method : node.methods) {
            if (!method.name.equals("activate") || !method.desc.equals(descriptor)
                    || hasStaticCall(method, DOUBLE_SLOPE_HELPER, "activate")) {
                continue;
            }
            LabelNode originalCode = new LabelNode();
            InsnList injected = new InsnList();
            injected.add(new VarInsnNode(Opcodes.ALOAD, 0));
            injected.add(new VarInsnNode(Opcodes.ALOAD, 1));
            injected.add(new VarInsnNode(Opcodes.ALOAD, 2));
            injected.add(new VarInsnNode(Opcodes.ALOAD, 3));
            injected.add(new VarInsnNode(Opcodes.ALOAD, 4));
            injected.add(new VarInsnNode(Opcodes.ALOAD, 5));
            injected.add(new VarInsnNode(Opcodes.ALOAD, 6));
            injected.add(new VarInsnNode(Opcodes.FLOAD, 7));
            injected.add(new VarInsnNode(Opcodes.FLOAD, 8));
            injected.add(new VarInsnNode(Opcodes.FLOAD, 9));
            injected.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    DOUBLE_SLOPE_HELPER,
                    "activate",
                    "(L" + DOUBLE_SLOPE_ACCESS + ";" + descriptor.substring(1, descriptor.indexOf(')'))
                            + ")Ljava/lang/Boolean;",
                    false
            ));
            injected.add(new InsnNode(Opcodes.DUP));
            injected.add(new JumpInsnNode(Opcodes.IFNULL, originalCode));
            injected.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    "java/lang/Boolean",
                    "booleanValue",
                    "()Z",
                    false
            ));
            injected.add(new InsnNode(Opcodes.IRETURN));
            injected.add(originalCode);
            injected.add(new InsnNode(Opcodes.POP));
            method.instructions.insert(injected);
            return true;
        }
        return false;
    }

    private static boolean patchDoubleSlopeBreak(ClassNode node) {
        final String descriptor = "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
                + BLOCK_STATE + "Lnet/minecraft/entity/player/EntityPlayer;)V";
        for (MethodNode method : node.methods) {
            if (!method.name.equals("breakBlock") || !method.desc.equals(descriptor)
                    || hasStaticCall(method, DOUBLE_SLOPE_HELPER, "beforeBreak")) {
                continue;
            }
            InsnList injected = new InsnList();
            injected.add(new VarInsnNode(Opcodes.ALOAD, 0));
            injected.add(new VarInsnNode(Opcodes.ALOAD, 1));
            injected.add(new VarInsnNode(Opcodes.ALOAD, 2));
            injected.add(new VarInsnNode(Opcodes.ALOAD, 3));
            injected.add(new VarInsnNode(Opcodes.ALOAD, 4));
            injected.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    DOUBLE_SLOPE_HELPER,
                    "beforeBreak",
                    "(L" + DOUBLE_SLOPE_ACCESS + ";" + descriptor.substring(1, descriptor.indexOf(')'))
                            + ")V",
                    false
            ));
            method.instructions.insert(injected);
            return true;
        }
        return false;
    }

    private static byte[] patchWorld(byte[] basicClass) {
        ClassNode node = readNode(basicClass);
        boolean changed = patchMayPlaceMethod(node, "mayPlace");
        changed |= patchMayPlaceMethod(node, "func_190527_a");
        return changed ? writeNode(node) : basicClass;
    }

    private static byte[] patchEditableModel(
            byte[] basicClass,
            boolean modelLayerCompat,
            boolean modelNullSideCull,
            boolean framedMaterialStorage
    ) {
        ClassNode node = readNode(basicClass);
        boolean changed = false;
        if (modelNullSideCull || framedMaterialStorage) {
            replaceMethod(node, modelGetQuadsMethod("func_188616_a"));
            replaceMethod(node, modelGetQuadsMethod("getQuads"));
            changed = true;
        } else if (modelLayerCompat) {
            changed |= patchModelGetQuadsMethod(node, "func_188616_a");
            changed |= patchModelGetQuadsMethod(node, "getQuads");
        }
        if (modelLayerCompat || modelNullSideCull) {
            changed |= synchronizeMethod(node, "func_188616_a", GET_QUADS_DESC);
            changed |= synchronizeMethod(node, "getQuads", GET_QUADS_DESC);
        }
        return changed ? writeNode(node) : basicClass;
    }

    private static byte[] patchDoubleSlopeBlock(byte[] basicClass) {
        ClassNode node = readNode(basicClass);
        replaceMethod(node, doubleSlopeStateContainerMethod());
        boolean extendedState = patchDoubleSlopeExtendedState(node);
        boolean collision = addDoubleSlopeCollisionMethod(node);
        if (PATCHED_TILE_CLASSES.add("double-slope:" + node.name)) {
            GPOM.LOGGER.info(
                    "[GPOM Double Slope] Blockcraftery slant patched stateContainer=true extendedState={} collision={}",
                    extendedState,
                    collision
            );
        }
        return writeNode(node);
    }

    private static MethodNode doubleSlopeStateContainerMethod() {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "func_180661_e",
                "()Lnet/minecraft/block/state/BlockStateContainer;",
                null,
                null
        );
        InsnList instructions = method.instructions;
        instructions.add(new InsnNode(Opcodes.ICONST_3));
        instructions.add(new org.objectweb.asm.tree.TypeInsnNode(
                Opcodes.ANEWARRAY,
                "net/minecraft/block/properties/IProperty"
        ));
        instructions.add(new InsnNode(Opcodes.DUP));
        instructions.add(new InsnNode(Opcodes.ICONST_0));
        instructions.add(new org.objectweb.asm.tree.FieldInsnNode(
                Opcodes.GETSTATIC,
                "epicsquid/mysticallib/block/BlockSlantBase",
                "VERT",
                "Lnet/minecraft/block/properties/PropertyInteger;"
        ));
        instructions.add(new InsnNode(Opcodes.AASTORE));
        instructions.add(new InsnNode(Opcodes.DUP));
        instructions.add(new InsnNode(Opcodes.ICONST_1));
        instructions.add(new org.objectweb.asm.tree.FieldInsnNode(
                Opcodes.GETSTATIC,
                "epicsquid/mysticallib/block/BlockSlantBase",
                "DIR",
                "Lnet/minecraft/block/properties/PropertyInteger;"
        ));
        instructions.add(new InsnNode(Opcodes.AASTORE));
        instructions.add(new InsnNode(Opcodes.DUP));
        instructions.add(new InsnNode(Opcodes.ICONST_2));
        instructions.add(new org.objectweb.asm.tree.FieldInsnNode(
                Opcodes.GETSTATIC,
                "epicsquid/blockcraftery/block/BlockEditableCube",
                "LIGHT",
                "Lnet/minecraft/block/properties/PropertyBool;"
        ));
        instructions.add(new InsnNode(Opcodes.AASTORE));
        instructions.add(new VarInsnNode(Opcodes.ASTORE, 1));

        instructions.add(new InsnNode(Opcodes.ICONST_2));
        instructions.add(new org.objectweb.asm.tree.TypeInsnNode(
                Opcodes.ANEWARRAY,
                "net/minecraftforge/common/property/IUnlistedProperty"
        ));
        instructions.add(new InsnNode(Opcodes.DUP));
        instructions.add(new InsnNode(Opcodes.ICONST_0));
        instructions.add(new org.objectweb.asm.tree.FieldInsnNode(
                Opcodes.GETSTATIC,
                "epicsquid/blockcraftery/block/BlockEditableSlant",
                "STATEPROP",
                "Lepicsquid/blockcraftery/block/BlockEditableSlant$UnlistedPropertyState;"
        ));
        instructions.add(new InsnNode(Opcodes.AASTORE));
        instructions.add(new InsnNode(Opcodes.DUP));
        instructions.add(new InsnNode(Opcodes.ICONST_1));
        instructions.add(new org.objectweb.asm.tree.FieldInsnNode(
                Opcodes.GETSTATIC,
                "com/l/gpom/compat/blockcraftery/BlockcrafteryDoubleSlopeStateProperty",
                "INSTANCE",
                "Lcom/l/gpom/compat/blockcraftery/BlockcrafteryDoubleSlopeStateProperty;"
        ));
        instructions.add(new InsnNode(Opcodes.AASTORE));
        instructions.add(new VarInsnNode(Opcodes.ASTORE, 2));

        instructions.add(new org.objectweb.asm.tree.TypeInsnNode(
                Opcodes.NEW,
                "net/minecraftforge/common/property/ExtendedBlockState"
        ));
        instructions.add(new InsnNode(Opcodes.DUP));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                "net/minecraftforge/common/property/ExtendedBlockState",
                "<init>",
                "(Lnet/minecraft/block/Block;[Lnet/minecraft/block/properties/IProperty;"
                        + "[Lnet/minecraftforge/common/property/IUnlistedProperty;)V",
                false
        ));
        instructions.add(new InsnNode(Opcodes.ARETURN));
        return method;
    }

    private static boolean patchDoubleSlopeExtendedState(ClassNode node) {
        final String descriptor = "(" + BLOCK_STATE + "Lnet/minecraft/world/IBlockAccess;"
                + "Lnet/minecraft/util/math/BlockPos;)" + BLOCK_STATE;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("getExtendedState") || !method.desc.equals(descriptor)
                    || hasStaticCall(method, DOUBLE_SLOPE_HELPER, "attachSecondary")) {
                continue;
            }
            int returnLocal = method.maxLocals++;
            boolean changed = false;
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                 instruction = instruction.getNext()) {
                if (instruction.getOpcode() != Opcodes.ARETURN) {
                    continue;
                }
                InsnList injected = new InsnList();
                injected.add(new VarInsnNode(Opcodes.ASTORE, returnLocal));
                injected.add(new VarInsnNode(Opcodes.ALOAD, returnLocal));
                injected.add(new VarInsnNode(Opcodes.ALOAD, 2));
                injected.add(new VarInsnNode(Opcodes.ALOAD, 3));
                injected.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        DOUBLE_SLOPE_HELPER,
                        "attachSecondary",
                        "(" + BLOCK_STATE + "Lnet/minecraft/world/IBlockAccess;"
                                + "Lnet/minecraft/util/math/BlockPos;)" + BLOCK_STATE,
                        false
                ));
                method.instructions.insertBefore(instruction, injected);
                changed = true;
            }
            return changed;
        }
        return false;
    }

    private static boolean addDoubleSlopeCollisionMethod(ClassNode node) {
        final String descriptor = "(" + BLOCK_STATE + "Lnet/minecraft/world/World;"
                + "Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/AxisAlignedBB;"
                + "Ljava/util/List;Lnet/minecraft/entity/Entity;Z)V";
        if (findMethod(node, "func_185477_a", descriptor) != null) {
            return false;
        }
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "func_185477_a", descriptor, null, null);
        LabelNode originalCollision = new LabelNode();
        InsnList instructions = method.instructions;
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 5));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                DOUBLE_SLOPE_HELPER,
                "addFullCollisionIfDoubled",
                "(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;"
                        + "Lnet/minecraft/util/math/AxisAlignedBB;Ljava/util/List;)Z",
                false
        ));
        instructions.add(new JumpInsnNode(Opcodes.IFEQ, originalCollision));
        instructions.add(new InsnNode(Opcodes.RETURN));
        instructions.add(originalCollision);
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 5));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 6));
        instructions.add(new VarInsnNode(Opcodes.ILOAD, 7));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                "epicsquid/mysticallib/block/BlockTESlantBase",
                "func_185477_a",
                descriptor,
                false
        ));
        instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(method);
        return true;
    }

    private static boolean synchronizeMethod(ClassNode node, String name, String descriptor) {
        boolean changed = false;
        for (MethodNode method : node.methods) {
            if (method.name.equals(name)
                    && method.desc.equals(descriptor)
                    && (method.access & Opcodes.ACC_SYNCHRONIZED) == 0) {
                method.access |= Opcodes.ACC_SYNCHRONIZED;
                changed = true;
            }
        }
        return changed;
    }

    private static byte[] patchShapedBlock(byte[] basicClass) {
        ClassNode node = readNode(basicClass);
        replaceMethod(node, shapedRayTraceMethod("func_180636_a"));
        replaceMethod(node, shapedRayTraceMethod("collisionRayTrace"));
        replaceMethod(node, shapedSelectedBoxMethod("func_180640_a"));
        replaceMethod(node, shapedSelectedBoxMethod("getSelectedBoundingBox"));
        return writeNode(node);
    }

    private static byte[] patchCube(byte[] basicClass, boolean parentMaterialOcclusion) {
        ClassNode node = readNode(basicClass);
        replaceMethod(node, cubeRayTraceMethod("func_180636_a"));
        replaceMethod(node, cubeRayTraceMethod("collisionRayTrace"));
        replaceMethod(node, cubeBoundingBoxMethod("func_185496_a"));
        replaceMethod(node, cubeBoundingBoxMethod("getBoundingBox"));
        replaceMethod(node, cubeSelectedBoxMethod("func_180640_a"));
        replaceMethod(node, cubeSelectedBoxMethod("getSelectedBoundingBox"));
        if (parentMaterialOcclusion) {
            replaceMethod(node, cubeSideMethod("func_176225_a", "copiedShouldSideBeRendered"));
            replaceMethod(node, cubeSideMethod("shouldSideBeRendered", "copiedShouldSideBeRendered"));
            replaceMethod(node, cubeSideMethod("doesSideBlockRendering", "copiedDoesSideBlockRendering"));
        }
        return writeNode(node);
    }

    private static MethodNode shapedRayTraceMethod(String name) {
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

    private static MethodNode shapedSelectedBoxMethod(String name) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, SELECTED_BOX_DESC, null, null);
        InsnList instructions = method.instructions;
        loadArgs(instructions, 3);
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HELPER,
                "selectedShapeBox",
                "(Lnet/minecraft/block/Block;" + SELECTED_BOX_DESC.substring(1),
                false
        ));
        instructions.add(new InsnNode(Opcodes.ARETURN));
        return method;
    }

    private static MethodNode cubeRayTraceMethod(String name) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, RAYTRACE_DESC, null, null);
        InsnList instructions = method.instructions;
        loadArgs(instructions, 5);
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HELPER,
                "rayTraceCopiedBlock",
                "(Lnet/minecraft/block/Block;" + RAYTRACE_DESC.substring(1),
                false
        ));
        instructions.add(new InsnNode(Opcodes.ARETURN));
        return method;
    }

    private static MethodNode cubeBoundingBoxMethod(String name) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, BOUNDING_BOX_DESC, null, null);
        InsnList instructions = method.instructions;
        loadArgs(instructions, 3);
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HELPER,
                "copiedBoundingBoxOrFull",
                "(Lnet/minecraft/block/Block;" + BOUNDING_BOX_DESC.substring(1),
                false
        ));
        instructions.add(new InsnNode(Opcodes.ARETURN));
        return method;
    }

    private static MethodNode cubeSelectedBoxMethod(String name) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, SELECTED_BOX_DESC, null, null);
        InsnList instructions = method.instructions;
        loadArgs(instructions, 3);
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HELPER,
                "copiedSelectedBoxOrFull",
                "(Lnet/minecraft/block/Block;" + SELECTED_BOX_DESC.substring(1),
                false
        ));
        instructions.add(new InsnNode(Opcodes.ARETURN));
        return method;
    }

    private static MethodNode cubeSideMethod(String name, String helperMethod) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, SIDE_RENDER_DESC, null, null);
        InsnList instructions = method.instructions;
        loadArgs(instructions, 4);
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HELPER,
                helperMethod,
                "(Lnet/minecraft/block/Block;" + SIDE_RENDER_DESC.substring(1),
                false
        ));
        instructions.add(new InsnNode(Opcodes.IRETURN));
        return method;
    }

    private static MethodNode modelGetQuadsMethod(String name) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, GET_QUADS_DESC, null, null);
        InsnList instructions = method.instructions;
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        instructions.add(new VarInsnNode(Opcodes.LLOAD, 3));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                RENDER_HELPER,
                "getQuads",
                "(Ljava/lang/Object;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/EnumFacing;J)Ljava/util/List;",
                false
        ));
        instructions.add(new InsnNode(Opcodes.ARETURN));
        return method;
    }

    private static boolean isEditableBlock(String className) {
        return SLAB.equals(className)
                || STAIRS.equals(className)
                || FENCE.equals(className)
                || WALL.equals(className)
                || DOOR.equals(className)
                || TRAP_DOOR.equals(className)
                || PRESSURE_PLATE.equals(className);
    }

    private static boolean isAnyEditableBlock(String className) {
        return CUBE.equals(className)
                || SLANT.equals(className)
                || CORNER.equals(className)
                || isEditableBlock(className);
    }

    private static boolean patchMayPlaceMethod(ClassNode node, String name) {
        boolean changed = false;
        for (MethodNode method : node.methods) {
            if (!method.name.equals(name) || !method.desc.equals(MAY_PLACE_DESC)) {
                continue;
            }

            LabelNode originalCode = new LabelNode();
            InsnList instructions = new InsnList();
            instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
            instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
            instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
            instructions.add(new VarInsnNode(Opcodes.ALOAD, 5));
            instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    HELPER,
                    "mayPlaceBlockcrafteryShape",
                    "(Lnet/minecraft/world/World;Lnet/minecraft/block/Block;Lnet/minecraft/util/math/BlockPos;ZLnet/minecraft/util/EnumFacing;Lnet/minecraft/entity/Entity;)Ljava/lang/Boolean;",
                    false
            ));
            instructions.add(new InsnNode(Opcodes.DUP));
            instructions.add(new JumpInsnNode(Opcodes.IFNULL, originalCode));
            instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false));
            instructions.add(new InsnNode(Opcodes.IRETURN));
            instructions.add(originalCode);
            instructions.add(new FrameNode(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Boolean"}));
            instructions.add(new InsnNode(Opcodes.POP));
            method.instructions.insert(instructions);
            changed = true;
        }
        return changed;
    }

    private static boolean patchModelGetQuadsMethod(ClassNode node, String name) {
        boolean changed = false;
        for (MethodNode method : node.methods) {
            if (!method.name.equals(name) || !method.desc.equals(GET_QUADS_DESC)) {
                continue;
            }

            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (!isMethod(instruction, "net/minecraftforge/client/MinecraftForgeClient", "getRenderLayer", "()" + RENDER_LAYER)) {
                    continue;
                }

                AbstractInsnNode loadCopiedState = instruction.getNext();
                AbstractInsnNode getCopiedBlock = loadCopiedState == null ? null : loadCopiedState.getNext();
                AbstractInsnNode getCopiedLayer = getCopiedBlock == null ? null : getCopiedBlock.getNext();
                AbstractInsnNode skipBranch = getCopiedLayer == null ? null : getCopiedLayer.getNext();
                if (!(loadCopiedState instanceof VarInsnNode)
                        || ((VarInsnNode) loadCopiedState).getOpcode() != Opcodes.ALOAD
                        || ((VarInsnNode) loadCopiedState).var != 6
                        || !isStateGetBlock(getCopiedBlock)
                        || !isBlockGetRenderLayer(getCopiedLayer)
                        || !(skipBranch instanceof JumpInsnNode)
                        || skipBranch.getOpcode() != Opcodes.IF_ACMPNE) {
                    continue;
                }

                JumpInsnNode originalSkip = (JumpInsnNode) skipBranch;
                InsnList replacement = new InsnList();
                replacement.add(new VarInsnNode(Opcodes.ALOAD, 6));
                replacement.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        RENDER_HELPER,
                        "shouldRenderCopiedBlockInCurrentLayer",
                        "(Lnet/minecraft/block/state/IBlockState;)Z",
                        false
                ));
                replacement.add(new JumpInsnNode(Opcodes.IFEQ, originalSkip.label));

                method.instructions.insertBefore(instruction, replacement);
                method.instructions.remove(instruction);
                method.instructions.remove(loadCopiedState);
                method.instructions.remove(getCopiedBlock);
                method.instructions.remove(getCopiedLayer);
                method.instructions.remove(skipBranch);
                changed = true;
                break;
            }
        }
        return changed;
    }

    private static boolean patchModelNullSideCullMethod(ClassNode node, String name) {
        for (MethodNode method : node.methods) {
            if (!method.name.equals(name) || !method.desc.equals(GET_QUADS_DESC)) {
                continue;
            }

            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof VarInsnNode)
                        || instruction.getOpcode() != Opcodes.ASTORE
                        || ((VarInsnNode) instruction).var != 6) {
                    continue;
                }

                LabelNode originalCode = new LabelNode();
                InsnList injected = new InsnList();
                injected.add(new VarInsnNode(Opcodes.ALOAD, 2));
                injected.add(new JumpInsnNode(Opcodes.IFNONNULL, originalCode));
                injected.add(new VarInsnNode(Opcodes.ALOAD, 6));
                injected.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        RENDER_HELPER,
                        "isNonSolidCopiedMaterial",
                        "(Lnet/minecraft/block/state/IBlockState;)Z",
                        false
                ));
                injected.add(new JumpInsnNode(Opcodes.IFEQ, originalCode));
                injected.add(new VarInsnNode(Opcodes.ALOAD, 5));
                injected.add(new InsnNode(Opcodes.ARETURN));
                injected.add(originalCode);
                injected.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                method.instructions.insert(instruction, injected);
                return true;
            }
        }
        return false;
    }

    private static boolean isBlockGetRenderLayer(AbstractInsnNode instruction) {
        return isMethod(instruction, "net/minecraft/block/Block", "func_180664_k", "()" + RENDER_LAYER)
                || isMethod(instruction, "net/minecraft/block/Block", "getRenderLayer", "()" + RENDER_LAYER);
    }

    private static boolean isStateGetBlock(AbstractInsnNode instruction) {
        return isMethod(instruction, "net/minecraft/block/state/IBlockState", "func_177230_c", "()Lnet/minecraft/block/Block;")
                || isMethod(instruction, "net/minecraft/block/state/IBlockState", "getBlock", "()Lnet/minecraft/block/Block;");
    }

    private static boolean isMethod(AbstractInsnNode instruction, String owner, String name, String desc) {
        if (!(instruction instanceof MethodInsnNode)) {
            return false;
        }
        MethodInsnNode method = (MethodInsnNode) instruction;
        return method.owner.equals(owner) && method.name.equals(name) && method.desc.equals(desc);
    }

    private static FieldNode findField(ClassNode node, String name, String desc) {
        for (FieldNode field : node.fields) {
            if (field.name.equals(name) && field.desc.equals(desc)) {
                return field;
            }
        }
        return null;
    }

    private static MethodNode findMethod(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) {
                return method;
            }
        }
        return null;
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
        // Injected framed-material calls change the operand stack at existing
        // return points. Recompute stack-map frames as well as max stack so
        // Cleanroom's verifier does not retain stale frames from the original
        // Blockcraftery method.
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
