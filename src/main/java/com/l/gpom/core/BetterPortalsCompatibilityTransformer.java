package com.l.gpom.core;

import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.Loader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class BetterPortalsCompatibilityTransformer implements IClassTransformer {
    private static final String ENTITY_RENDERER_NO_OF = "de.johni0702.minecraft.view.impl.mixin.MixinEntityRenderer_NoOF";
    private static final String ENTITY_RENDERER_OF = "de.johni0702.minecraft.view.impl.mixin.MixinEntityRenderer_OF";
    private static final String BETTER_PORTALS_ROOT = "de.johni0702.minecraft.";
    private static final String KOTLIN_EXTENSIONS = ".ExtensionsKt";
    private static final String AETHER_PACKAGE = "de.johni0702.minecraft.betterportals.impl.aether.";
    private static final String AETHER_HAS_AETHER = "de.johni0702.minecraft.betterportals.impl.aether.common.ExtensionsKt$hasAether$2";
    private static final String AETHER_BLOCK_REGISTER = "de.johni0702.minecraft.betterportals.impl.aether.common.ExtensionsKt$initAether$2";
    private static final String AETHER_PORTAL_BLOCK = "de.johni0702.minecraft.betterportals.impl.aether.common.blocks.BlockBetterAetherPortal";
    private static final String PLAYER_CHUNK_MAP_MIXIN = "de.johni0702.minecraft.view.impl.mixin.MixinPlayerChunkMap";
    private static final String AETHER_SKYROOT_BUCKET = "com.gildedgames.the_aether.items.tools.ItemSkyrootBucket";
    private static final String AETHER_SKYROOT_BUCKET_INTERNAL = "com/gildedgames/the_aether/items/tools/ItemSkyrootBucket";
    private static final String THIS_INTERNAL = "com/l/gpom/core/BetterPortalsCompatibilityTransformer";
    private static final String LEGACY_AETHER_PREFIX = "com/legacy/aether/";
    private static final String CURRENT_AETHER_PREFIX = "com/gildedgames/the_aether/";
    private static final String LEGACY_AETHER_BLOCKS = LEGACY_AETHER_PREFIX + "blocks/BlocksAether";
    private static final String CURRENT_AETHER_BLOCKS = CURRENT_AETHER_PREFIX + "blocks/BlocksAether";
    private static final String LEGACY_AETHER_PORTAL = LEGACY_AETHER_PREFIX + "blocks/portal/BlockAetherPortal";
    private static final String CURRENT_AETHER_PORTAL = CURRENT_AETHER_PREFIX + "blocks/portal/BlockAetherPortal";
    private static final String BLOCK_DESC = "Lnet/minecraft/block/Block;";
    private static final String AETHER_TRY_SPAWN_DESC = "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)Z";
    private static final String AETHER_RENDER_TYPE_DESC = "(Lnet/minecraft/block/state/IBlockState;)Lnet/minecraft/util/EnumBlockRenderType;";
    private static final String ENUM_BLOCK_RENDER_TYPE = "net/minecraft/util/EnumBlockRenderType";
    private static final String ENUM_BLOCK_RENDER_TYPE_DESC = "Lnet/minecraft/util/EnumBlockRenderType;";
    private static final String FUTURES = "com/google/common/util/concurrent/Futures";
    private static final String MORE_EXECUTORS = "com/google/common/util/concurrent/MoreExecutors";
    private static final String OLD_ADD_CALLBACK_DESC = "(Lcom/google/common/util/concurrent/ListenableFuture;Lcom/google/common/util/concurrent/FutureCallback;)V";
    private static final String NEW_ADD_CALLBACK_DESC = "(Lcom/google/common/util/concurrent/ListenableFuture;Lcom/google/common/util/concurrent/FutureCallback;Ljava/util/concurrent/Executor;)V";
    private static final String SKYROOT_TRY_PLACE_LIQUID_DESC = "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/math/BlockPos;)Z";
    private static final String SERVER_WORLD_MANAGER_INTERNAL = "de/johni0702/minecraft/view/impl/server/ServerWorldManager";
    private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
    private static final String FRUSTUM_TARGET = "net.minecraft.client.renderer.culling.Frustum";
    private static final int AETHER_PORTAL_BREAK_SCAN_LIMIT = 512;
    private static final long AETHER_PORTAL_LINK_SKIP_NANOS = 5_000_000_000L;
    private static final Map<String, Long> RECENT_AETHER_PORTAL_LINKS = new java.util.LinkedHashMap<String, Long>();

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }

        String className = normalizedClassName(transformedName != null ? transformedName : name);
        boolean patchEntityRenderer = GpomEarlyConfig.betterPortalsMissingNewTargetFixEnabled()
                && (ENTITY_RENDERER_NO_OF.equals(className) || ENTITY_RENDERER_OF.equals(className));
        boolean patchLegacyAether = GpomEarlyConfig.betterPortalsSkipLegacyAetherBridgeIfMissingEnabled()
                && AETHER_HAS_AETHER.equals(className);
        boolean patchGuavaAddCallback = GpomEarlyConfig.betterPortalsGuavaAddCallbackFixEnabled()
                && isBetterPortalsExtensionsClass(className);
        boolean remapLegacyAether = GpomEarlyConfig.betterPortalsRemapLegacyAetherBridgeEnabled()
                && isBetterPortalsAetherClass(className)
                && shouldRemapBetterPortalsAetherBridge();
        boolean patchAetherPortalAssignment = remapLegacyAether && AETHER_BLOCK_REGISTER.equals(className);
        boolean patchAetherPortalWaterActivation = remapLegacyAether && AETHER_PORTAL_BLOCK.equals(className);
        boolean patchAetherSkyrootBucketWaterPlacement = GpomEarlyConfig.betterPortalsRemapLegacyAetherBridgeEnabled()
                && AETHER_SKYROOT_BUCKET.equals(className)
                && classResourceExists(CURRENT_AETHER_PORTAL);
        boolean patchPlayerChunkMapMissingWorldManager = PLAYER_CHUNK_MAP_MIXIN.equals(className);
        if (!patchEntityRenderer && !patchLegacyAether && !patchGuavaAddCallback && !remapLegacyAether
                && !patchAetherPortalAssignment && !patchAetherPortalWaterActivation
                && !patchAetherSkyrootBucketWaterPlacement && !patchPlayerChunkMapMissingWorldManager) {
            return basicClass;
        }

        try {
            boolean changed = false;
            byte[] transformed = basicClass;
            if (patchEntityRenderer || patchLegacyAether || patchGuavaAddCallback || patchPlayerChunkMapMissingWorldManager) {
                ClassNode node = new ClassNode();
                new ClassReader(transformed).accept(node, 0);
                if (patchEntityRenderer) {
                    for (MethodNode method : node.methods) {
                        changed |= patchMethodAnnotations(method);
                    }
                }
                if (patchLegacyAether) {
                    for (MethodNode method : node.methods) {
                        changed |= patchLegacyAetherProbe(method);
                    }
                }
                if (patchGuavaAddCallback) {
                    for (MethodNode method : node.methods) {
                        changed |= patchGuavaAddCallback(method);
                    }
                }
                if (patchPlayerChunkMapMissingWorldManager) {
                    for (MethodNode method : node.methods) {
                        changed |= patchPlayerChunkMapMissingWorldManager(method);
                    }
                }
                if (changed) {
                    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    node.accept(writer);
                    transformed = writer.toByteArray();
                }
            }
            if (remapLegacyAether) {
                transformed = remapLegacyAetherBridge(transformed);
                changed = true;
            }
            if (patchAetherPortalAssignment) {
                byte[] patched = patchAetherPortalAssignment(transformed);
                if (patched != transformed) {
                    transformed = patched;
                    changed = true;
                }
            }
            if (patchAetherPortalWaterActivation) {
                byte[] patched = patchAetherPortalWaterActivation(transformed);
                if (patched != transformed) {
                    transformed = patched;
                    changed = true;
                }
            }
            if (patchAetherSkyrootBucketWaterPlacement) {
                byte[] patched = patchAetherSkyrootBucketWaterPlacement(transformed);
                if (patched != transformed) {
                    transformed = patched;
                    changed = true;
                }
            }
            return changed ? transformed : basicClass;
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    public static void installBetterPortalsAetherPortalBlock(Object block) {
        if (block == null) {
            logAetherPortalProbe("skip installing BetterPortals Aether portal block: block=null");
            return;
        }
        logAetherPortalProbe("installing BetterPortals Aether portal block " + describeObjectForProbe(block)
                + " registry=" + registryNameForProbe(block));
        installBetterPortalsAetherPortalBlock(block, CURRENT_AETHER_BLOCKS.replace('/', '.'));
        installBetterPortalsAetherPortalBlock(block, LEGACY_AETHER_BLOCKS.replace('/', '.'));
    }

    public static boolean tryToLinkBetterPortalsAetherPortal(Object portalBlock, Object world, Object pos) {
        logAetherPortalProbe("try-link start portal=" + describeObjectForProbe(portalBlock)
                + " world=" + describeWorldForProbe(world)
                + " pos=" + describePosForProbe(pos));
        if (portalBlock == null || world == null || pos == null) {
            logAetherPortalProbe("try-link skipped: missing portal/world/pos");
            return false;
        }
        if (isClientWorld(world)) {
            markRecentAetherPortalLink(world, pos);
            logAetherPortalProbe("try-link client prediction accepted; server performs actual portal placement");
            return true;
        }

        Object originalState;
        boolean clearedActivationWater;
        try {
            originalState = getBlockState(world, pos);
            clearedActivationWater = isAetherActivationWater(originalState);
            logAetherPortalProbe("try-link state-before pos=" + describePosForProbe(pos)
                    + " state=" + describeStateForProbe(originalState)
                    + " activationWater=" + clearedActivationWater);
            if (clearedActivationWater) {
                setBlockState(world, pos, getDefaultState(staticField("net.minecraft.init.Blocks", "AIR", "field_150350_a")), 2);
                logAetherPortalProbe("try-link cleared activation water state-after="
                        + describeStateForProbe(getBlockState(world, pos)));
            }
        } catch (ReflectiveOperationException exception) {
            logAetherPortalProbe("try-link failed while preparing block state: " + describeThrowableForProbe(exception));
            return false;
        }

        try {
            boolean linked = invokeBetterPortalsLink(portalBlock, world, pos);
            if (linked) {
                markRecentAetherPortalLink(world, pos);
                int removedWater = removeAetherActivationWaterNearLinkedPortal(world, pos, portalBlock);
                logAetherPortalProbe("try-link succeeded post-state=" + describeStateForProbe(safeBlockStateForProbe(world, pos))
                        + " portalEntities=" + countPortalEntitiesForProbe(world)
                        + " activationWaterRemoved=" + removedWater);
                return true;
            }
            logAetherPortalProbe("try-link returned false clearedActivationWater=" + clearedActivationWater);
        } catch (InvocationTargetException exception) {
            restoreAetherActivationWater(world, pos, originalState, clearedActivationWater);
            logAetherPortalProbe("try-link threw from BetterPortals: " + describeThrowableForProbe(
                    exception.getCause() != null ? exception.getCause() : exception));
            rethrowUnchecked(exception.getCause() != null ? exception.getCause() : exception);
        } catch (ReflectiveOperationException exception) {
            restoreAetherActivationWater(world, pos, originalState, clearedActivationWater);
            logAetherPortalProbe("try-link failed reflectively: " + describeThrowableForProbe(exception));
            return false;
        } catch (RuntimeException exception) {
            restoreAetherActivationWater(world, pos, originalState, clearedActivationWater);
            logAetherPortalProbe("try-link failed at runtime: " + describeThrowableForProbe(exception));
            throw exception;
        } catch (Error error) {
            restoreAetherActivationWater(world, pos, originalState, clearedActivationWater);
            logAetherPortalProbe("try-link failed with error: " + describeThrowableForProbe(error));
            throw error;
        }

        restoreAetherActivationWater(world, pos, originalState, clearedActivationWater);
        logAetherPortalProbe("try-link failed; restoredActivationWater=" + clearedActivationWater
                + " currentState=" + describeStateForProbe(safeBlockStateForProbe(world, pos)));
        return false;
    }

    public static boolean tryPlaceSkyrootWaterUnlessRecentAetherPortalLink(
            Object bucket,
            Object player,
            Object world,
            Object stack,
            Object pos
    ) {
        if (consumeRecentAetherPortalLink(world, pos, 1)) {
            logAetherPortalProbe("skyroot bucket water placement skipped after successful portal link"
                    + " world=" + describeWorldForProbe(world)
                    + " pos=" + describePosForProbe(pos));
            return true;
        }

        try {
            Method method = findMethod(bucket.getClass(), "tryPlaceContainedLiquid", player, world, stack, pos);
            if (method == null) {
                logAetherPortalProbe("skyroot bucket fallback failed: tryPlaceContainedLiquid missing");
                return false;
            }
            Object result = method.invoke(bucket, player, world, stack, pos);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable throwable) {
            logAetherPortalProbe("skyroot bucket fallback failed: " + describeThrowableForProbe(throwable));
            return false;
        }
    }

    public static boolean safeBetterPortalsServerWorldManagerNeedsUpdate(Object worldManager) {
        if (worldManager == null) {
            return false;
        }
        try {
            Method method = findMethod(worldManager.getClass(), "getNeedsUpdate");
            Object result = method == null ? null : method.invoke(worldManager);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void markRecentAetherPortalLink(Object world, Object pos) {
        String key = portalLinkKey(world, pos);
        if (key == null) {
            return;
        }
        long now = System.nanoTime();
        synchronized (RECENT_AETHER_PORTAL_LINKS) {
            pruneRecentAetherPortalLinks(now);
            RECENT_AETHER_PORTAL_LINKS.put(key, now);
            while (RECENT_AETHER_PORTAL_LINKS.size() > 256) {
                Iterator<String> iterator = RECENT_AETHER_PORTAL_LINKS.keySet().iterator();
                if (!iterator.hasNext()) {
                    break;
                }
                iterator.next();
                iterator.remove();
            }
        }
    }

    private static boolean consumeRecentAetherPortalLink(Object world, Object pos, int radius) {
        int[] coords = posCoords(pos);
        if (world == null || coords == null) {
            return false;
        }
        long now = System.nanoTime();
        synchronized (RECENT_AETHER_PORTAL_LINKS) {
            pruneRecentAetherPortalLinks(now);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        String key = portalLinkKey(world, coords[0] + dx, coords[1] + dy, coords[2] + dz);
                        Long linkedAt = RECENT_AETHER_PORTAL_LINKS.remove(key);
                        if (linkedAt != null && now - linkedAt <= AETHER_PORTAL_LINK_SKIP_NANOS) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static void pruneRecentAetherPortalLinks(long now) {
        Iterator<Map.Entry<String, Long>> iterator = RECENT_AETHER_PORTAL_LINKS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            Long linkedAt = entry.getValue();
            if (linkedAt == null || now - linkedAt > AETHER_PORTAL_LINK_SKIP_NANOS) {
                iterator.remove();
            }
        }
    }

    private static String portalLinkKey(Object world, Object pos) {
        int[] coords = posCoords(pos);
        if (world == null || coords == null) {
            return null;
        }
        return portalLinkKey(world, coords[0], coords[1], coords[2]);
    }

    private static String portalLinkKey(Object world, int x, int y, int z) {
        return System.identityHashCode(world) + ":" + x + ":" + y + ":" + z;
    }

    private static int[] posCoords(Object pos) {
        if (pos == null) {
            return null;
        }
        Integer x = intValueForProbe(invokeValueForProbe(pos, "func_177958_n", "getX"));
        Integer y = intValueForProbe(invokeValueForProbe(pos, "func_177956_o", "getY"));
        Integer z = intValueForProbe(invokeValueForProbe(pos, "func_177952_p", "getZ"));
        if (x == null || y == null || z == null) {
            return null;
        }
        return new int[]{x, y, z};
    }

    private static Integer intValueForProbe(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    public static void handleBetterPortalsAetherPortalNeighborChange(Object portalBlock, Object world, Object pos, Object fromPos) {
        try {
            if (portalBlock == null || world == null || pos == null) {
                return;
            }
            if (isClientWorld(world)) {
                return;
            }

            Set<Object> portalCluster = collectConnectedPortalBlocks(world, pos, portalBlock);
            if (portalCluster.isEmpty()) {
                tryInvokeValidatePortalOrDestroy(portalBlock, world, pos);
                return;
            }

            Boolean stillValid = isAnyAetherPortalShapeValid(world, portalCluster, portalBlock);
            if (stillValid == null) {
                tryInvokeValidatePortalOrDestroy(portalBlock, world, pos);
                return;
            }
            if (stillValid) {
                tryInvokeValidatePortalOrDestroy(portalBlock, world, pos);
                return;
            }

            int removedBlocks = destroyPortalBlocks(world, portalCluster, portalBlock);
            int removedEntities = killPortalEntitiesNear(portalBlock, world, portalCluster);
            logAetherPortalProbe("neighbor-change removed stale Aether portal cluster"
                    + " changedFrom=" + describePosForProbe(fromPos)
                    + " trigger=" + describePosForProbe(pos)
                    + " scannedBlocks=" + portalCluster.size()
                    + " removedBlocks=" + removedBlocks
                    + " removedEntities=" + removedEntities);
        } catch (Throwable throwable) {
            logAetherPortalProbe("neighbor-change fallback after GPOM cleanup failure: "
                    + describeThrowableForProbe(throwable));
            tryInvokeValidatePortalOrDestroy(portalBlock, world, pos);
        }
    }

    public static boolean isBetterPortalsAetherBridgeAvailable() {
        if (!isModLoaded("aether_legacy")) {
            return false;
        }
        if (classResourceExists(LEGACY_AETHER_PORTAL)) {
            return true;
        }
        return GpomEarlyConfig.betterPortalsRemapLegacyAetherBridgeEnabled()
                && classResourceExists(CURRENT_AETHER_PORTAL);
    }

    private static boolean shouldRemapBetterPortalsAetherBridge() {
        return !classResourceExists(LEGACY_AETHER_PORTAL)
                && classResourceExists(CURRENT_AETHER_PORTAL);
    }

    private static boolean isModLoaded(String modId) {
        try {
            return Loader.isModLoaded(modId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean classResourceExists(String internalName) {
        String resource = internalName + ".class";
        ClassLoader ownLoader = BetterPortalsCompatibilityTransformer.class.getClassLoader();
        if (ownLoader != null && ownLoader.getResource(resource) != null) {
            return true;
        }
        if (Launch.classLoader != null && Launch.classLoader.getResource(resource) != null) {
            return true;
        }
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        return contextLoader != null && contextLoader.getResource(resource) != null;
    }

    private static boolean isBetterPortalsAetherClass(String className) {
        return className != null && className.startsWith(AETHER_PACKAGE);
    }

    private static boolean isBetterPortalsExtensionsClass(String className) {
        return className != null
                && className.startsWith(BETTER_PORTALS_ROOT)
                && className.endsWith(KOTLIN_EXTENSIONS);
    }

    private static byte[] remapLegacyAetherBridge(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassRemapper(writer, new Remapper() {
            @Override
            public String map(String internalName) {
                if (internalName != null && internalName.startsWith(LEGACY_AETHER_PREFIX)) {
                    return CURRENT_AETHER_PREFIX + internalName.substring(LEGACY_AETHER_PREFIX.length());
                }
                return internalName;
            }
        }), 0);
        return writer.toByteArray();
    }

    private static byte[] patchAetherPortalAssignment(byte[] basicClass) {
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);

        boolean changed = false;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null;
                 instruction = instruction.getNext()) {
                if (!(instruction instanceof FieldInsnNode)) {
                    continue;
                }

                FieldInsnNode field = (FieldInsnNode) instruction;
                if (field.getOpcode() != Opcodes.PUTSTATIC
                        || !BLOCK_DESC.equals(field.desc)
                        || !"aether_portal".equals(field.name)
                        || !isAetherBlocksOwner(field.owner)) {
                    continue;
                }

                method.instructions.set(field, new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        THIS_INTERNAL,
                        "installBetterPortalsAetherPortalBlock",
                        "(Ljava/lang/Object;)V",
                        false
                ));
                changed = true;
            }
        }

        if (!changed) {
            return basicClass;
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean isAetherBlocksOwner(String owner) {
        return LEGACY_AETHER_BLOCKS.equals(owner) || CURRENT_AETHER_BLOCKS.equals(owner);
    }

    private static byte[] patchAetherPortalWaterActivation(byte[] basicClass) {
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);

        boolean changed = false;
        for (MethodNode method : node.methods) {
            if ("func_176548_d".equals(method.name) && AETHER_TRY_SPAWN_DESC.equals(method.desc)) {
                clearMethod(method);

                InsnList replacement = new InsnList();
                replacement.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                replacement.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
                replacement.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 2));
                replacement.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        THIS_INTERNAL,
                        "tryToLinkBetterPortalsAetherPortal",
                        "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z",
                        false
                ));
                replacement.add(new InsnNode(Opcodes.IRETURN));
                method.instructions.add(replacement);
                method.maxStack = 3;
                method.maxLocals = 3;
                changed = true;
                continue;
            }

            if ("func_149645_b".equals(method.name) && AETHER_RENDER_TYPE_DESC.equals(method.desc)) {
                clearMethod(method);

                InsnList replacement = new InsnList();
                replacement.add(new FieldInsnNode(
                        Opcodes.GETSTATIC,
                        ENUM_BLOCK_RENDER_TYPE,
                        "MODEL",
                        ENUM_BLOCK_RENDER_TYPE_DESC
                ));
                replacement.add(new InsnNode(Opcodes.ARETURN));
                method.instructions.add(replacement);
                method.maxStack = 1;
                method.maxLocals = 2;
                changed = true;
                continue;
            }

        }

        if (!changed) {
            return basicClass;
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] patchAetherSkyrootBucketWaterPlacement(byte[] basicClass) {
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);

        boolean changed = false;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null;
                 instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) {
                    continue;
                }

                MethodInsnNode call = (MethodInsnNode) instruction;
                if (!AETHER_SKYROOT_BUCKET_INTERNAL.equals(call.owner)
                        || !"tryPlaceContainedLiquid".equals(call.name)
                        || !SKYROOT_TRY_PLACE_LIQUID_DESC.equals(call.desc)) {
                    continue;
                }

                method.instructions.set(call, new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        THIS_INTERNAL,
                        "tryPlaceSkyrootWaterUnlessRecentAetherPortalLink",
                        "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z",
                        false
                ));
                changed = true;
            }
        }

        if (!changed) {
            return basicClass;
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void clearMethod(MethodNode method) {
        method.tryCatchBlocks.clear();
        if (method.localVariables != null) {
            method.localVariables.clear();
        }
        while (method.instructions.size() > 0) {
            method.instructions.remove(method.instructions.getFirst());
        }
    }

    private static String normalizedClassName(String className) {
        return className == null ? null : className.replace('/', '.');
    }

    private static void installBetterPortalsAetherPortalBlock(Object block, String className) {
        try {
            Class<?> blocksClass = loadClass(className);
            if (blocksClass == null) {
                logAetherPortalProbe("portal field install skipped: class missing " + className);
                return;
            }
            if (setPortalField(blocksClass, block, "aether_portal")) {
                logAetherPortalProbe("portal field installed: " + className + ".aether_portal");
                return;
            }
            if (setPortalField(blocksClass, block, "aetherPortal")) {
                logAetherPortalProbe("portal field installed: " + className + ".aetherPortal");
                return;
            }
            if (setPortalField(blocksClass, block, "AETHER_PORTAL")) {
                logAetherPortalProbe("portal field installed: " + className + ".AETHER_PORTAL");
                return;
            }
            for (Field field : blocksClass.getDeclaredFields()) {
                if (field.getName().toLowerCase(java.util.Locale.ROOT).contains("portal")
                        && setPortalField(field, block)) {
                    logAetherPortalProbe("portal field installed: " + className + "." + field.getName());
                    return;
                }
            }
            logAetherPortalProbe("portal field install failed: no compatible field in " + className);
        } catch (Throwable ignored) {
            logAetherPortalProbe("portal field install failed for " + className + ": " + describeThrowableForProbe(ignored));
        }
    }

    private static boolean isClientWorld(Object world) {
        return booleanField(world, "field_72995_K", "isRemote");
    }

    private static Object getBlockState(Object world, Object pos) throws ReflectiveOperationException {
        return invokeNamed(world, new String[]{"func_180495_p", "getBlockState"}, pos);
    }

    private static void setBlockState(Object world, Object pos, Object state, int flags) throws ReflectiveOperationException {
        invokeNamed(world, new String[]{"func_180501_a", "setBlockState"}, pos, state, flags);
    }

    private static Object getDefaultState(Object block) throws ReflectiveOperationException {
        return invokeNamed(block, new String[]{"func_176223_P", "getDefaultState"});
    }

    private static Object getBlockFromState(Object state) throws ReflectiveOperationException {
        return invokeNamed(state, new String[]{"func_177230_c", "getBlock"});
    }

    private static Object getMaterialFromState(Object state) throws ReflectiveOperationException {
        return invokeNamed(state, new String[]{"func_185904_a", "getMaterial"});
    }

    private static boolean isAetherActivationWater(Object state) {
        if (state == null) {
            return false;
        }
        try {
            Object block = getBlockFromState(state);
            return sameStaticField(block, "net.minecraft.init.Blocks", "WATER", "field_150355_j")
                    || sameStaticField(block, "net.minecraft.init.Blocks", "FLOWING_WATER", "field_150358_i")
                    || sameStaticField(getMaterialFromState(state), "net.minecraft.block.material.Material", "WATER", "field_151586_h")
                    || registryNameForProbe(block).toLowerCase(Locale.ROOT).contains("water");
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static int removeAetherActivationWaterNearLinkedPortal(Object world, Object pos, Object portalBlock) {
        Set<Object> candidates = new HashSet<>();
        try {
            addNearbyWaterCandidates(candidates, pos, 4);
            int portalBlocks = 0;
            for (int dx = -6; dx <= 6; dx++) {
                for (int dy = -6; dy <= 6; dy++) {
                    for (int dz = -6; dz <= 6; dz++) {
                        Object scanPos = offsetPos(pos, dx, dy, dz);
                        if (isLinkedPortalState(getBlockState(world, scanPos), portalBlock)) {
                            portalBlocks++;
                            addNearbyWaterCandidates(candidates, scanPos, 2);
                        }
                    }
                }
            }

            Object air = getDefaultState(staticField("net.minecraft.init.Blocks", "AIR", "field_150350_a"));
            int removed = 0;
            for (Object waterPos : candidates) {
                if (isAetherActivationWater(safeBlockStateForProbe(world, waterPos))) {
                    setBlockState(world, waterPos, air, 3);
                    removed++;
                }
            }
            if (removed > 0 || portalBlocks > 0) {
                logAetherPortalProbe("activation-water cleanup scannedPortalBlocks=" + portalBlocks
                        + " candidates=" + candidates.size()
                        + " removed=" + removed);
            }
            return removed;
        } catch (ReflectiveOperationException exception) {
            logAetherPortalProbe("activation-water cleanup failed: " + describeThrowableForProbe(exception));
            return 0;
        }
    }

    private static void addNearbyWaterCandidates(Set<Object> candidates, Object pos, int radius)
            throws ReflectiveOperationException {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    candidates.add(offsetPos(pos, dx, dy, dz));
                }
            }
        }
    }

    private static boolean isLinkedPortalState(Object state, Object portalBlock) {
        if (state == null || portalBlock == null) {
            return false;
        }
        try {
            return getBlockFromState(state) == portalBlock;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static Set<Object> collectConnectedPortalBlocks(Object world, Object startPos, Object portalBlock) {
        Set<Object> visited = new HashSet<>();
        Deque<Object> queue = new ArrayDeque<>();
        try {
            if (!isLinkedPortalState(getBlockState(world, startPos), portalBlock)) {
                return visited;
            }
            visited.add(startPos);
            queue.add(startPos);
            while (!queue.isEmpty() && visited.size() < AETHER_PORTAL_BREAK_SCAN_LIMIT) {
                Object current = queue.removeFirst();
                enqueuePortalNeighbor(world, portalBlock, current, 1, 0, 0, visited, queue);
                enqueuePortalNeighbor(world, portalBlock, current, -1, 0, 0, visited, queue);
                enqueuePortalNeighbor(world, portalBlock, current, 0, 1, 0, visited, queue);
                enqueuePortalNeighbor(world, portalBlock, current, 0, -1, 0, visited, queue);
                enqueuePortalNeighbor(world, portalBlock, current, 0, 0, 1, visited, queue);
                enqueuePortalNeighbor(world, portalBlock, current, 0, 0, -1, visited, queue);
            }
        } catch (ReflectiveOperationException exception) {
            logAetherPortalProbe("neighbor-change portal-cluster scan failed: " + describeThrowableForProbe(exception));
        }
        return visited;
    }

    private static void enqueuePortalNeighbor(
            Object world,
            Object portalBlock,
            Object pos,
            int dx,
            int dy,
            int dz,
            Set<Object> visited,
            Deque<Object> queue
    ) throws ReflectiveOperationException {
        if (visited.size() >= AETHER_PORTAL_BREAK_SCAN_LIMIT) {
            return;
        }
        Object neighbor = offsetPos(pos, dx, dy, dz);
        if (visited.contains(neighbor) || !isLinkedPortalState(getBlockState(world, neighbor), portalBlock)) {
            return;
        }
        visited.add(neighbor);
        queue.addLast(neighbor);
    }

    private static Boolean isAnyAetherPortalShapeValid(Object world, Set<Object> portalCluster, Object portalBlock) {
        Class<?> portalSizeClass = loadClass(CURRENT_AETHER_PREFIX.replace('/', '.') + "blocks.portal.AetherPortalSize");
        if (portalSizeClass == null) {
            return null;
        }
        Object axisX;
        Object axisZ;
        try {
            axisX = staticField("net.minecraft.util.EnumFacing$Axis", "X");
            axisZ = staticField("net.minecraft.util.EnumFacing$Axis", "Z");
        } catch (ReflectiveOperationException exception) {
            logAetherPortalProbe("neighbor-change Aether axis lookup failed: " + describeThrowableForProbe(exception));
            return null;
        }

        for (Object portalPos : portalCluster) {
            if (!isLinkedPortalState(safeBlockStateForProbe(world, portalPos), portalBlock)) {
                continue;
            }
            Boolean valid = isAetherPortalShapeValid(portalSizeClass, world, portalPos, axisX);
            if (valid == null) {
                return null;
            }
            if (valid) {
                return true;
            }
            valid = isAetherPortalShapeValid(portalSizeClass, world, portalPos, axisZ);
            if (valid == null) {
                return null;
            }
            if (valid) {
                return true;
            }
        }
        return false;
    }

    private static Boolean isAetherPortalShapeValid(Class<?> portalSizeClass, Object world, Object pos, Object axis) {
        try {
            Constructor<?> constructor = findConstructor(portalSizeClass, world, pos, axis);
            if (constructor == null) {
                logAetherPortalProbe("neighbor-change AetherPortalSize constructor missing");
                return null;
            }
            Object portalSize = constructor.newInstance(world, pos, axis);
            Method isValid = findMethod(portalSizeClass, "isValid");
            if (isValid == null) {
                logAetherPortalProbe("neighbor-change AetherPortalSize.isValid missing");
                return null;
            }
            Object result = isValid.invoke(portalSize);
            if (!(result instanceof Boolean) || !(Boolean) result) {
                return false;
            }
            int width = intField(portalSize, "width");
            int height = intField(portalSize, "height");
            int portalBlockCount = intField(portalSize, "portalBlockCount");
            return width > 0 && height > 0 && portalBlockCount >= width * height;
        } catch (ReflectiveOperationException exception) {
            logAetherPortalProbe("neighbor-change AetherPortalSize validation failed: "
                    + describeThrowableForProbe(exception));
            return null;
        }
    }

    private static int destroyPortalBlocks(Object world, Set<Object> portalCluster, Object portalBlock) {
        int removed = 0;
        try {
            Object air = getDefaultState(staticField("net.minecraft.init.Blocks", "AIR", "field_150350_a"));
            for (Object portalPos : portalCluster) {
                if (!isLinkedPortalState(safeBlockStateForProbe(world, portalPos), portalBlock)) {
                    continue;
                }
                setBlockState(world, portalPos, air, 3);
                removed++;
            }
        } catch (ReflectiveOperationException exception) {
            logAetherPortalProbe("neighbor-change portal cleanup failed: " + describeThrowableForProbe(exception));
        }
        return removed;
    }

    private static int killPortalEntitiesNear(Object portalBlock, Object world, Set<Object> portalCluster) {
        try {
            Object entityType = invokeNamed(portalBlock, new String[]{"getEntityType"});
            if (!(entityType instanceof Class<?>)) {
                return 0;
            }
            Class<?> aabbClass = loadClass("net.minecraft.util.math.AxisAlignedBB");
            if (aabbClass == null) {
                return 0;
            }
            Constructor<?> aabbConstructor = findConstructor(aabbClass, firstPortalPos(portalCluster));
            Method getEntities = findMethod(world.getClass(), "func_72872_a", entityType, aabbConstructor == null ? null : safeNewAabb(aabbConstructor, firstPortalPos(portalCluster)));
            if (aabbConstructor == null || getEntities == null) {
                return 0;
            }

            Set<Object> killed = new HashSet<>();
            for (Object portalPos : portalCluster) {
                Object aabb = safeNewAabb(aabbConstructor, portalPos);
                if (aabb == null) {
                    continue;
                }
                Object result = getEntities.invoke(world, entityType, aabb);
                if (!(result instanceof Iterable<?>)) {
                    continue;
                }
                for (Object entity : (Iterable<?>) result) {
                    if (entity == null || !killed.add(entity)) {
                        continue;
                    }
                    Method setDead = findMethod(entity.getClass(), "func_70106_y");
                    if (setDead != null) {
                        setDead.invoke(entity);
                    }
                }
            }
            return killed.size();
        } catch (ReflectiveOperationException exception) {
            logAetherPortalProbe("neighbor-change portal-entity cleanup failed: " + describeThrowableForProbe(exception));
            return 0;
        }
    }

    private static Object firstPortalPos(Set<Object> portalCluster) {
        return portalCluster.isEmpty() ? null : portalCluster.iterator().next();
    }

    private static Object safeNewAabb(Constructor<?> constructor, Object pos) {
        try {
            return pos == null ? null : constructor.newInstance(pos);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean invokeBetterPortalsLink(Object portalBlock, Object world, Object pos)
            throws ReflectiveOperationException {
        Method method = findMethod(portalBlock.getClass(), "tryToLinkPortals", world, pos);
        if (method == null) {
            throw new NoSuchMethodException(portalBlock.getClass().getName() + ".tryToLinkPortals");
        }
        logAetherPortalProbe("invoking BetterPortals link method " + method);
        Object result = method.invoke(portalBlock, world, pos);
        logAetherPortalProbe("BetterPortals link result=" + result);
        return result instanceof Boolean && (Boolean) result;
    }

    private static void tryInvokeValidatePortalOrDestroy(Object portalBlock, Object world, Object pos) {
        try {
            Method method = findMethod(portalBlock.getClass(), "validatePortalOrDestroy", world, pos);
            if (method != null) {
                method.invoke(portalBlock, world, pos);
            }
        } catch (ReflectiveOperationException exception) {
            logAetherPortalProbe("neighbor-change BetterPortals validation failed: "
                    + describeThrowableForProbe(exception));
        }
    }

    private static void restoreAetherActivationWater(Object world, Object pos, Object originalState, boolean shouldRestore) {
        if (!shouldRestore || world == null || pos == null || originalState == null) {
            return;
        }
        try {
            Object currentState = getBlockState(world, pos);
            if (currentState == null || isAirState(currentState) || isAetherActivationWater(currentState)) {
                setBlockState(world, pos, originalState, 2);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static Object safeBlockStateForProbe(Object world, Object pos) {
        try {
            return world == null || pos == null ? null : getBlockState(world, pos);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void logAetherPortalProbe(String message) {
        try {
            System.out.println("[GPOM BetterPortals Aether] " + message);
        } catch (Throwable ignored) {
        }
    }

    private static String describeWorldForProbe(Object world) {
        if (world == null) {
            return "null";
        }
        Object provider = fieldValueForProbe(world, "field_73011_w", "provider");
        Object dimension = provider == null ? null : invokeValueForProbe(provider, "getDimension");
        Object dimensionType = provider == null ? null : invokeValueForProbe(provider, "func_186058_p", "getDimensionType");
        return describeObjectForProbe(world)
                + "{client=" + isClientWorld(world)
                + ", dimension=" + safeToStringForProbe(dimension)
                + ", dimensionType=" + safeToStringForProbe(dimensionType)
                + ", provider=" + describeObjectForProbe(provider)
                + "}";
    }

    private static String describePosForProbe(Object pos) {
        if (pos == null) {
            return "null";
        }
        Object x = invokeValueForProbe(pos, "func_177958_n", "getX");
        Object y = invokeValueForProbe(pos, "func_177956_o", "getY");
        Object z = invokeValueForProbe(pos, "func_177952_p", "getZ");
        return describeObjectForProbe(pos)
                + "{x=" + safeToStringForProbe(x)
                + ", y=" + safeToStringForProbe(y)
                + ", z=" + safeToStringForProbe(z)
                + "}";
    }

    private static String describeStateForProbe(Object state) {
        if (state == null) {
            return "null";
        }
        Object block = null;
        Object material = null;
        try {
            block = getBlockFromState(state);
        } catch (Throwable ignored) {
        }
        try {
            material = getMaterialFromState(state);
        } catch (Throwable ignored) {
        }
        return describeObjectForProbe(state)
                + "{block=" + describeObjectForProbe(block)
                + ", blockRegistry=" + registryNameForProbe(block)
                + ", material=" + describeObjectForProbe(material)
                + "}";
    }

    private static int countPortalEntitiesForProbe(Object world) {
        Object entities = fieldValueForProbe(world, "field_72996_f", "loadedEntityList");
        if (!(entities instanceof Iterable)) {
            return -1;
        }
        int count = 0;
        try {
            for (Object entity : (Iterable<?>) entities) {
                if (entity != null && entity.getClass().getName().contains("betterportals")) {
                    count++;
                }
            }
        } catch (Throwable ignored) {
            return -1;
        }
        return count;
    }

    private static String describeObjectForProbe(Object object) {
        if (object == null) {
            return "null";
        }
        return object.getClass().getName()
                + "@"
                + Integer.toHexString(System.identityHashCode(object))
                + "("
                + safeToStringForProbe(object)
                + ")";
    }

    private static String registryNameForProbe(Object object) {
        if (object == null) {
            return "null";
        }
        Object registryName = invokeValueForProbe(object, "getRegistryName");
        if (registryName != null) {
            return safeToStringForProbe(registryName);
        }
        Object unlocalizedName = invokeValueForProbe(object, "func_149739_a", "getUnlocalizedName");
        if (unlocalizedName != null) {
            return safeToStringForProbe(unlocalizedName);
        }
        return "unknown";
    }

    private static Object fieldValueForProbe(Object target, String... fieldNames) {
        if (target == null) {
            return null;
        }
        try {
            for (String name : fieldNames) {
                Field field = findField(target.getClass(), name);
                if (field != null) {
                    field.setAccessible(true);
                    return field.get(target);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object invokeValueForProbe(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        try {
            for (String name : methodNames) {
                Method method = findMethod(target.getClass(), name);
                if (method != null) {
                    return method.invoke(target);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String describeThrowableForProbe(Throwable throwable) {
        if (throwable == null) {
            return "null";
        }
        Throwable actual = throwable instanceof InvocationTargetException
                && ((InvocationTargetException) throwable).getCause() != null
                ? ((InvocationTargetException) throwable).getCause()
                : throwable;
        String message = actual.getMessage();
        return actual.getClass().getName() + (message == null || message.isEmpty() ? "" : ": " + message);
    }

    private static String safeToStringForProbe(Object object) {
        if (object == null) {
            return "null";
        }
        try {
            String value = String.valueOf(object).replace('\n', ' ').replace('\r', ' ');
            return value.length() > 240 ? value.substring(0, 240) + "..." : value;
        } catch (Throwable ignored) {
            return "<toString failed>";
        }
    }

    private static boolean isAirState(Object state) throws ReflectiveOperationException {
        return state == null || sameStaticField(getBlockFromState(state), "net.minecraft.init.Blocks", "AIR", "field_150350_a");
    }

    private static Object offsetPos(Object pos, int dx, int dy, int dz) throws ReflectiveOperationException {
        return invokeNamed(pos, new String[]{"func_177982_a", "add"}, dx, dy, dz);
    }

    private static Object invokeNamed(Object target, String[] names, Object... args) throws ReflectiveOperationException {
        for (String name : names) {
            Method method = findMethod(target.getClass(), name, args);
            if (method != null) {
                return method.invoke(target, args);
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + java.util.Arrays.toString(names));
    }

    private static Method findMethod(Class<?> owner, String name, Object... args) {
        for (Method method : owner.getMethods()) {
            if (name.equals(method.getName()) && accepts(method.getParameterTypes(), args)) {
                method.setAccessible(true);
                return method;
            }
        }
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (name.equals(method.getName()) && accepts(method.getParameterTypes(), args)) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private static Constructor<?> findConstructor(Class<?> owner, Object... args) {
        for (Constructor<?> constructor : owner.getConstructors()) {
            if (accepts(constructor.getParameterTypes(), args)) {
                constructor.setAccessible(true);
                return constructor;
            }
        }
        for (Constructor<?> constructor : owner.getDeclaredConstructors()) {
            if (accepts(constructor.getParameterTypes(), args)) {
                constructor.setAccessible(true);
                return constructor;
            }
        }
        return null;
    }

    private static boolean accepts(Class<?>[] parameterTypes, Object[] args) {
        if (parameterTypes.length != args.length) {
            return false;
        }
        for (int index = 0; index < parameterTypes.length; index++) {
            Object arg = args[index];
            if (arg != null && !wrap(parameterTypes[index]).isInstance(arg)) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return Void.class;
    }

    private static boolean sameStaticField(Object value, String className, String... fieldNames) {
        if (value == null) {
            return false;
        }
        try {
            return value == staticField(className, fieldNames);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static Object staticField(String className, String... fieldNames) throws ReflectiveOperationException {
        Class<?> owner = loadClass(className);
        if (owner == null) {
            throw new ClassNotFoundException(className);
        }
        for (String name : fieldNames) {
            Field field = findField(owner, name);
            if (field != null) {
                field.setAccessible(true);
                return field.get(null);
            }
        }
        throw new NoSuchFieldException(className + "." + java.util.Arrays.toString(fieldNames));
    }

    private static boolean booleanField(Object target, String... fieldNames) {
        if (target == null) {
            return false;
        }
        try {
            for (String name : fieldNames) {
                Field field = findField(target.getClass(), name);
                if (field != null) {
                    field.setAccessible(true);
                    return field.getBoolean(target);
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static int intField(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            throw new NoSuchFieldException(target.getClass().getName() + "." + fieldName);
        }
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static Field findField(Class<?> owner, String name) {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            try {
                return type.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static void rethrowUnchecked(Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        }
        if (throwable instanceof Error) {
            throw (Error) throwable;
        }
        throw new RuntimeException(throwable);
    }

    private static boolean setPortalField(Class<?> owner, Object block, String name) {
        try {
            return setPortalField(owner.getDeclaredField(name), block);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean setPortalField(Field field, Object block) {
        try {
            if (!Modifier.isStatic(field.getModifiers()) || !field.getType().isInstance(block)) {
                return false;
            }
            field.setAccessible(true);
            field.set(null, block);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Class<?> loadClass(String className) {
        try {
            if (Launch.classLoader != null) {
                return Class.forName(className, false, Launch.classLoader);
            }
        } catch (Throwable ignored) {
        }
        try {
            ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
            if (contextLoader != null) {
                return Class.forName(className, false, contextLoader);
            }
        } catch (Throwable ignored) {
        }
        try {
            return Class.forName(className, false, BetterPortalsCompatibilityTransformer.class.getClassLoader());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean patchMethodAnnotations(MethodNode method) {
        if (method == null || method.visibleAnnotations == null) {
            return false;
        }

        boolean changed = false;
        for (AnnotationNode annotation : method.visibleAnnotations) {
            if (annotation == null || !REDIRECT.equals(annotation.desc)) {
                continue;
            }
            AnnotationNode at = nestedAnnotation(annotation, "at");
            if (isNewInjectionPoint(at) && stringValue(at, "target") == null) {
                putValue(at, "target", FRUSTUM_TARGET);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean patchLegacyAetherProbe(MethodNode method) {
        if (method == null || !"invoke".equals(method.name) || !"()Z".equals(method.desc)) {
            return false;
        }

        method.tryCatchBlocks.clear();
        if (method.localVariables != null) {
            method.localVariables.clear();
        }
        while (method.instructions.size() > 0) {
            method.instructions.remove(method.instructions.getFirst());
        }

        InsnList replacement = new InsnList();
        replacement.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                THIS_INTERNAL,
                "isBetterPortalsAetherBridgeAvailable",
                "()Z",
                false
        ));
        replacement.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(replacement);
        method.maxStack = 1;
        method.maxLocals = 1;
        return true;
    }

    private static boolean patchGuavaAddCallback(MethodNode method) {
        if (method == null || !"logFailure".equals(method.name)) {
            return false;
        }

        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }

            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.getOpcode() != Opcodes.INVOKESTATIC
                    || !FUTURES.equals(call.owner)
                    || !"addCallback".equals(call.name)
                    || !OLD_ADD_CALLBACK_DESC.equals(call.desc)) {
                continue;
            }

            method.instructions.insertBefore(call, new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    MORE_EXECUTORS,
                    "directExecutor",
                    "()Ljava/util/concurrent/Executor;",
                    false
            ));
            call.desc = NEW_ADD_CALLBACK_DESC;
            return true;
        }
        return false;
    }

    private static boolean patchPlayerChunkMapMissingWorldManager(MethodNode method) {
        if (method == null
                || !"updateMovingPlayerWithViews".equals(method.name)
                || !"(Lnet/minecraft/entity/player/EntityPlayerMP;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V".equals(method.desc)) {
            return false;
        }

        boolean changed = false;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }

            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !SERVER_WORLD_MANAGER_INTERNAL.equals(call.owner)
                    || !"getNeedsUpdate".equals(call.name)
                    || !"()Z".equals(call.desc)) {
                continue;
            }

            method.instructions.set(call, new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    THIS_INTERNAL,
                    "safeBetterPortalsServerWorldManagerNeedsUpdate",
                    "(Ljava/lang/Object;)Z",
                    false
            ));
            changed = true;
        }
        return changed;
    }

    private static boolean isNewInjectionPoint(AnnotationNode annotation) {
        return annotation != null
                && AT.equals(annotation.desc)
                && "NEW".equals(stringValue(annotation, "value"));
    }

    private static AnnotationNode nestedAnnotation(AnnotationNode annotation, String key) {
        Object value = value(annotation, key);
        return value instanceof AnnotationNode ? (AnnotationNode) value : null;
    }

    private static String stringValue(AnnotationNode annotation, String key) {
        Object value = value(annotation, key);
        if (!(value instanceof String)) {
            return null;
        }
        String string = (String) value;
        return string.isEmpty() ? null : string;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation == null || annotation.values == null || key == null) {
            return null;
        }
        for (int index = 0; index + 1 < annotation.values.size(); index += 2) {
            Object name = annotation.values.get(index);
            if (key.equals(name)) {
                return annotation.values.get(index + 1);
            }
        }
        return null;
    }

    private static void putValue(AnnotationNode annotation, String key, Object value) {
        if (annotation.values == null) {
            annotation.values = new ArrayList<>();
        }
        List<Object> values = annotation.values;
        for (int index = 0; index + 1 < values.size(); index += 2) {
            if (key.equals(values.get(index))) {
                values.set(index + 1, value);
                return;
            }
        }
        values.add(key);
        values.add(value);
    }

}
