package com.l.gpom.core;

import com.l.gpom.GPOM;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public final class ForgeRegistrySerializationTransformer implements IClassTransformer {
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if (className != null && className.contains("com.l.gpom.")) {
            return basicClass;
        }
        if (!registrySerializationEnabled()) {
            return basicClass;
        }
        if ("net.minecraftforge.registries.ForgeRegistry".equals(className)) {
            return synchronizeForgeRegistry(basicClass);
        }
        if ("net.minecraftforge.registries.GameData".equals(className)) {
            return synchronizeGameData(basicClass);
        }
        if ("net.minecraftforge.oredict.OreDictionary".equals(className)) {
            return synchronizeOreDictionary(basicClass);
        }
        return basicClass;
    }

    private static byte[] synchronizeForgeRegistry(byte[] basicClass) {
        try {
            ClassNode node = read(basicClass);
            int synchronizedMethods = 0;
            int snapshotMethods = 0;
            for (MethodNode method : node.methods) {
                if ((isForgeRegistryMutation(method) || isForgeRegistryAccess(method)) && markSynchronized(method)) {
                    synchronizedMethods++;
                }
                if (snapshotEscapingForgeRegistryView(method)) {
                    snapshotMethods++;
                }
            }
            if (synchronizedMethods <= 0 && snapshotMethods <= 0) {
                return basicClass;
            }
            GPOM.LOGGER.info(
                    "[FmlParallelLoading] Serialized {} ForgeRegistry method(s) and snapshotted {} escaping view method(s)",
                    synchronizedMethods,
                    snapshotMethods
            );
            return write(node);
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[FmlParallelLoading] Failed to install ForgeRegistry serialization; continuing without it", throwable);
            return basicClass;
        }
    }

    private static byte[] synchronizeGameData(byte[] basicClass) {
        try {
            ClassNode node = read(basicClass);
            int changed = 0;
            for (MethodNode method : node.methods) {
                if (isGameDataRegistryMutation(method) && markSynchronized(method)) {
                    changed++;
                }
            }
            if (changed <= 0) {
                return basicClass;
            }
            GPOM.LOGGER.info("[FmlParallelLoading] Serialized {} GameData registry mutation method(s)", changed);
            return write(node);
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[FmlParallelLoading] Failed to install GameData registry serialization; continuing without it", throwable);
            return basicClass;
        }
    }

    private static byte[] synchronizeOreDictionary(byte[] basicClass) {
        try {
            ClassNode node = read(basicClass);
            int changed = 0;
            for (MethodNode method : node.methods) {
                if (isOreDictionaryAccess(method) && markSynchronized(method)) {
                    changed++;
                }
            }
            if (changed <= 0) {
                return basicClass;
            }
            GPOM.LOGGER.info("[FmlParallelLoading] Serialized {} OreDictionary access method(s)", changed);
            return write(node);
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[FmlParallelLoading] Failed to install OreDictionary serialization; continuing without it", throwable);
            return basicClass;
        }
    }

    private static ClassNode read(byte[] basicClass) {
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        return node;
    }

    private static byte[] write(ClassNode node) {
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean markSynchronized(MethodNode method) {
        if ((method.access & Opcodes.ACC_SYNCHRONIZED) != 0) {
            return false;
        }
        method.access |= Opcodes.ACC_SYNCHRONIZED;
        return true;
    }

    private static boolean isForgeRegistryMutation(MethodNode method) {
        String name = method.name;
        String desc = method.desc;
        if ("register".equals(name) && "(Lnet/minecraftforge/registries/IForgeRegistryEntry;)V".equals(desc)) {
            return true;
        }
        if ("registerAll".equals(name) && "([Lnet/minecraftforge/registries/IForgeRegistryEntry;)V".equals(desc)) {
            return true;
        }
        if ("add".equals(name)
                && ("(ILnet/minecraftforge/registries/IForgeRegistryEntry;)I".equals(desc)
                || "(ILnet/minecraftforge/registries/IForgeRegistryEntry;Ljava/lang/String;)I".equals(desc))) {
            return true;
        }
        if ("setSlaveMap".equals(name) && "(Lnet/minecraft/util/ResourceLocation;Ljava/lang/Object;)V".equals(desc)) {
            return true;
        }
        if ("addAlias".equals(name)
                && "(Lnet/minecraft/util/ResourceLocation;Lnet/minecraft/util/ResourceLocation;)V".equals(desc)) {
            return true;
        }
        if ("addDummy".equals(name) && "(Lnet/minecraft/util/ResourceLocation;)V".equals(desc)) {
            return true;
        }
        if ("clear".equals(name) && "()V".equals(desc)) {
            return true;
        }
        if ("remove".equals(name)
                && "(Lnet/minecraft/util/ResourceLocation;)Lnet/minecraftforge/registries/IForgeRegistryEntry;".equals(desc)) {
            return true;
        }
        if ("block".equals(name) && "(I)V".equals(desc)) {
            return true;
        }
        if (("freeze".equals(name) || "unfreeze".equals(name)) && "()V".equals(desc)) {
            return true;
        }
        if ("loadIds".equals(name)
                && "(Ljava/util/Map;Ljava/util/Map;Ljava/util/Map;Ljava/util/Map;Lnet/minecraftforge/registries/ForgeRegistry;Lnet/minecraft/util/ResourceLocation;)V".equals(desc)) {
            return true;
        }
        return "processMissingEvent".equals(name)
                && "(Lnet/minecraft/util/ResourceLocation;Lnet/minecraftforge/registries/ForgeRegistry;Ljava/util/List;Ljava/util/Map;Ljava/util/Map;Ljava/util/Collection;Ljava/util/Collection;Z)V".equals(desc);
    }

    private static boolean isForgeRegistryAccess(MethodNode method) {
        String name = method.name;
        String desc = method.desc;
        if ("iterator".equals(name) && "()Ljava/util/Iterator;".equals(desc)) {
            return true;
        }
        if ("containsKey".equals(name) && "(Lnet/minecraft/util/ResourceLocation;)Z".equals(desc)) {
            return true;
        }
        if ("containsValue".equals(name) && "(Lnet/minecraftforge/registries/IForgeRegistryEntry;)Z".equals(desc)) {
            return true;
        }
        if ("getValue".equals(name)
                && ("(Lnet/minecraft/util/ResourceLocation;)Lnet/minecraftforge/registries/IForgeRegistryEntry;".equals(desc)
                || "(I)Lnet/minecraftforge/registries/IForgeRegistryEntry;".equals(desc))) {
            return true;
        }
        if ("getKey".equals(name)
                && "(Lnet/minecraftforge/registries/IForgeRegistryEntry;)Lnet/minecraft/util/ResourceLocation;".equals(desc)) {
            return true;
        }
        if ("getKeys".equals(name) && "()Ljava/util/Set;".equals(desc)) {
            return true;
        }
        if ("getValues".equals(name) && "()Ljava/util/List;".equals(desc)) {
            return true;
        }
        if ("getValuesCollection".equals(name) && "()Ljava/util/Collection;".equals(desc)) {
            return true;
        }
        if ("getEntries".equals(name) && "()Ljava/util/Set;".equals(desc)) {
            return true;
        }
        if ("getSlaveMap".equals(name) && "(Lnet/minecraft/util/ResourceLocation;Ljava/lang/Class;)Ljava/lang/Object;".equals(desc)) {
            return true;
        }
        if ("getID".equals(name)
                && ("(Lnet/minecraftforge/registries/IForgeRegistryEntry;)I".equals(desc)
                || "(Lnet/minecraft/util/ResourceLocation;)I".equals(desc))) {
            return true;
        }
        if ("getRaw".equals(name) && "(I)Lnet/minecraftforge/registries/IForgeRegistryEntry;".equals(desc)) {
            return true;
        }
        if ("makeSnapshot".equals(name) && "()Lnet/minecraftforge/registries/ForgeRegistry$Snapshot;".equals(desc)) {
            return true;
        }
        return "getOverrideOwners".equals(name) && "()Ljava/util/Map;".equals(desc);
    }

    private static boolean snapshotEscapingForgeRegistryView(MethodNode method) {
        String helperMethod;
        String helperDesc;
        if ("iterator".equals(method.name) && "()Ljava/util/Iterator;".equals(method.desc)) {
            helperMethod = "iteratorSnapshot";
            helperDesc = "(Ljava/util/Iterator;)Ljava/util/Iterator;";
        } else if (("getKeys".equals(method.name) || "getEntries".equals(method.name)) && "()Ljava/util/Set;".equals(method.desc)) {
            helperMethod = "immutableSetSnapshot";
            helperDesc = "(Ljava/util/Set;)Ljava/util/Set;";
        } else if ("getValuesCollection".equals(method.name) && "()Ljava/util/Collection;".equals(method.desc)) {
            helperMethod = "immutableCollectionSnapshot";
            helperDesc = "(Ljava/util/Collection;)Ljava/util/Collection;";
        } else {
            return false;
        }

        int changed = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == Opcodes.ARETURN) {
                InsnList snapshot = new InsnList();
                snapshot.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/l/gpom/optimization/ForgeRegistrySnapshotOptimizations",
                        helperMethod,
                        helperDesc,
                        false
                ));
                method.instructions.insertBefore(insn, snapshot);
                changed++;
            }
        }
        return changed > 0;
    }

    private static boolean isGameDataRegistryMutation(MethodNode method) {
        String name = method.name;
        String desc = method.desc;
        if ("register_impl".equals(name)
                && "(Lnet/minecraftforge/registries/IForgeRegistryEntry;)Lnet/minecraftforge/registries/IForgeRegistryEntry;".equals(desc)) {
            return true;
        }
        if ("registerEntity".equals(name)
                && "(ILnet/minecraft/util/ResourceLocation;Ljava/lang/Class;Ljava/lang/String;)V".equals(desc)) {
            return true;
        }
        if ("freezeData".equals(name) && "()V".equals(desc)) {
            return true;
        }
        if ("revertToFrozen".equals(name) && "()V".equals(desc)) {
            return true;
        }
        if ("revert".equals(name)
                && "(Lnet/minecraftforge/registries/RegistryManager;Lnet/minecraft/util/ResourceLocation;Z)V".equals(desc)) {
            return true;
        }
        if ("injectSnapshot".equals(name)
                && "(Ljava/util/Map;ZZ)Lcom/google/common/collect/Multimap;".equals(desc)) {
            return true;
        }
        return "fireRegistryEvents".equals(name)
                && ("()V".equals(desc) || "(Ljava/util/function/Predicate;)V".equals(desc));
    }

    private static boolean isOreDictionaryAccess(MethodNode method) {
        if ((method.access & Opcodes.ACC_STATIC) == 0 || "<clinit>".equals(method.name)) {
            return false;
        }

        String name = method.name;
        return "initVanillaEntries".equals(name)
                || "getOreID".equals(name)
                || "getOreName".equals(name)
                || "getOreIDs".equals(name)
                || "getOres".equals(name)
                || "doesOreNameExist".equals(name)
                || "getOreNames".equals(name)
                || "registerOre".equals(name)
                || "registerOreImpl".equals(name)
                || "rebakeMap".equals(name);
    }

    private static boolean registrySerializationEnabled() {
        String override = System.getProperty("gpom.fml.registrySerialization");
        if (override != null) {
            return Boolean.parseBoolean(override.trim());
        }

        File file = new File(new File(System.getProperty("user.dir", "."), "config"), "gpom-early.properties");
        if (!file.isFile()) {
            return true;
        }

        Properties properties = new Properties();
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
            properties.load(input);
        } catch (IOException ignored) {
            return true;
        }
        return Boolean.parseBoolean(properties.getProperty("fml.parallel.registrySerialization.enabled", "true").trim());
    }
}
