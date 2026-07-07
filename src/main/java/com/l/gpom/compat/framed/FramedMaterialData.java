package com.l.gpom.compat.framed;

import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;

import java.util.Map;
import java.util.TreeMap;

public final class FramedMaterialData {
    private static final String TAG = "gpom:material_state";
    private static final int VERSION = 1;

    private FramedMaterialData() {
    }

    public static void read(FramedMaterialDataAccess access, NBTTagCompound root) {
        if (access == null || root == null) {
            return;
        }
        NBTTagCompound data = compound(root, TAG);
        access.gpom$setFramedMaterialData(data == null ? null : data);
    }

    public static void writeBlockcraftery(FramedMaterialDataAccess access, NBTTagCompound root, IBlockState copiedState) {
        write(access, root, "blockcraftery", copiedState, null);
    }

    public static void writeArchitectureCraft(
            FramedMaterialDataAccess access,
            NBTTagCompound root,
            IBlockState baseState,
            IBlockState secondaryState
    ) {
        write(access, root, "architecturecraft", baseState, secondaryState);
    }

    public static NBTTagCompound data(Object tile) {
        return tile instanceof FramedMaterialDataAccess ? ((FramedMaterialDataAccess) tile).gpom$getFramedMaterialData() : null;
    }

    private static void write(
            FramedMaterialDataAccess access,
            NBTTagCompound root,
            String source,
            IBlockState primaryState,
            IBlockState secondaryState
    ) {
        if (access == null || root == null) {
            return;
        }

        NBTTagCompound data = new NBTTagCompound();
        setInteger(data, "version", VERSION);
        setString(data, "source", source);
        boolean hasState = putState(data, "primary", primaryState);
        hasState |= putState(data, "secondary", secondaryState);
        if (!hasState) {
            NBTTagCompound existing = access.gpom$getFramedMaterialData();
            if (existing == null) {
                return;
            }
            data = existing;
        }

        setTag(root, TAG, data);
        access.gpom$setFramedMaterialData(data);
    }

    private static boolean putState(NBTTagCompound root, String key, IBlockState state) {
        NBTTagCompound tag = stateTag(state);
        if (tag == null) {
            return false;
        }
        setTag(root, key, tag);
        return true;
    }

    private static NBTTagCompound stateTag(IBlockState state) {
        Block block = MinecraftMappingCompat.blockStateBlock(state);
        ResourceLocation name = MinecraftMappingCompat.blockRegistryName(block);
        if (name == null) {
            return null;
        }

        NBTTagCompound tag = new NBTTagCompound();
        setString(tag, "id", name.toString());
        setInteger(tag, "meta", MinecraftMappingCompat.blockMetaFromState(block, state));
        setBoolean(tag, "opaqueCube", MinecraftMappingCompat.blockStateIsOpaqueCube(state));
        setBoolean(tag, "hasTileEntity", MinecraftMappingCompat.blockHasTileEntity(block));
        BlockRenderLayer declaredLayer = MinecraftMappingCompat.blockRenderLayer(block);
        if (declaredLayer != null) {
            setString(tag, "declaredLayer", declaredLayer.name());
        }
        setString(tag, "renderLayers", renderLayers(block, state));

        NBTTagCompound properties = propertiesTag(state);
        if (properties != null) {
            setTag(tag, "properties", properties);
        }
        return tag;
    }

    private static String renderLayers(Block block, IBlockState state) {
        StringBuilder builder = new StringBuilder();
        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            if (!MinecraftMappingCompat.blockCanRenderInLayer(block, state, layer)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(layer.name());
        }
        return builder.toString();
    }

    private static NBTTagCompound propertiesTag(IBlockState state) {
        Object raw = MinecraftMappingCompat.invoke(state, "blockState.getProperties",
                MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS,
                "func_177228_b", "getProperties");
        if (!(raw instanceof Map)) {
            return null;
        }

        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            String name = propertyName(entry.getKey());
            String value = propertyValueName(entry.getKey(), entry.getValue());
            if (name != null && value != null) {
                sorted.put(name, value);
            }
        }
        if (sorted.isEmpty()) {
            return null;
        }

        NBTTagCompound properties = new NBTTagCompound();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            setString(properties, entry.getKey(), entry.getValue());
        }
        return properties;
    }

    private static String propertyName(Object property) {
        Object value = MinecraftMappingCompat.invoke(property, "property.getName",
                MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS,
                "func_177701_a", "getName");
        return value instanceof String ? (String) value : null;
    }

    private static String propertyValueName(Object property, Object value) {
        if (!(value instanceof Comparable)) {
            return null;
        }
        Object name = MinecraftMappingCompat.invoke(property, "property.getName(value)",
                new Class<?>[]{Comparable.class}, new Object[]{value},
                "func_177702_a", "getName");
        return name instanceof String ? (String) name : String.valueOf(value);
    }

    private static NBTTagCompound compound(NBTTagCompound tag, String key) {
        Object value = MinecraftMappingCompat.invoke(tag, "nbt.getCompoundTag",
                new Class<?>[]{String.class}, new Object[]{key},
                "func_74775_l", "getCompoundTag");
        return value instanceof NBTTagCompound && !hasNoTags((NBTTagCompound) value) ? (NBTTagCompound) value : null;
    }

    private static boolean hasNoTags(NBTTagCompound tag) {
        Object value = MinecraftMappingCompat.invoke(tag, "nbt.hasNoTags",
                MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS,
                "func_82582_d", "hasNoTags");
        return value instanceof Boolean && (Boolean) value;
    }

    private static void setTag(NBTTagCompound tag, String key, NBTBase value) {
        MinecraftMappingCompat.invoke(tag, "nbt.setTag",
                new Class<?>[]{String.class, NBTBase.class}, new Object[]{key, value},
                "func_74782_a", "setTag");
    }

    private static void setString(NBTTagCompound tag, String key, String value) {
        MinecraftMappingCompat.invoke(tag, "nbt.setString",
                new Class<?>[]{String.class, String.class}, new Object[]{key, value},
                "func_74778_a", "setString");
    }

    private static void setInteger(NBTTagCompound tag, String key, int value) {
        MinecraftMappingCompat.invoke(tag, "nbt.setInteger",
                new Class<?>[]{String.class, int.class}, new Object[]{key, value},
                "func_74768_a", "setInteger");
    }

    private static void setBoolean(NBTTagCompound tag, String key, boolean value) {
        MinecraftMappingCompat.invoke(tag, "nbt.setBoolean",
                new Class<?>[]{String.class, boolean.class}, new Object[]{key, value},
                "func_74757_a", "setBoolean");
    }
}
