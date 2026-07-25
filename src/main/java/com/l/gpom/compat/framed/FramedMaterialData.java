package com.l.gpom.compat.framed;

import com.l.gpom.GPOM;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public final class FramedMaterialData {
    private static final String TAG = "gpom:material_state";
    private static final int VERSION = 2;
    private static final int PROBE_LIMIT = 256;
    private static final String RANDOM_THINGS_LUMINOUS_BLOCK = "lumien.randomthings.lib.ILuminousBlock";
    private static final Set<String> PROBED_MATERIALS = ConcurrentHashMap.newKeySet();
    private static final Map<Class<?>, Boolean> LUMINOUS_BLOCK_TYPES = new ConcurrentHashMap<>();
    private static final AtomicInteger PROBE_COUNT = new AtomicInteger();

    private FramedMaterialData() {
    }

    public static void read(FramedMaterialDataAccess access, NBTTagCompound root) {
        if (access == null || root == null) {
            return;
        }
        NBTTagCompound data = compound(root, TAG);
        NBTTagCompound normalized = data == null ? null : normalizeData(data, root);
        refreshDerivedFeatures(normalized, access);
        access.gpom$setFramedMaterialData(normalized);
        if (normalized != null) {
            probe("read", string(normalized, "source"), savedState(normalized, "primary"), access, normalized);
        }
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

    public static void refreshBlockcraftery(FramedMaterialDataAccess access, IBlockState copiedState) {
        refresh(access, "blockcraftery", copiedState, null);
    }

    public static void refreshArchitectureCraft(
            FramedMaterialDataAccess access,
            IBlockState baseState,
            IBlockState secondaryState
    ) {
        refresh(access, "architecturecraft", baseState, secondaryState);
    }

    public static NBTTagCompound data(Object tile) {
        return tile instanceof FramedMaterialDataAccess ? ((FramedMaterialDataAccess) tile).gpom$getFramedMaterialData() : null;
    }

    public static MaterialStates states(Object tile, String expectedSource) {
        NBTTagCompound data = data(tile);
        if (data == null || !expectedSource.equals(string(data, "source"))) {
            return MaterialStates.EMPTY;
        }
        NBTTagCompound normalized = normalizeData(data, null);
        if (tile instanceof FramedMaterialDataAccess && normalized != data) {
            ((FramedMaterialDataAccess) tile).gpom$setFramedMaterialData(normalized);
        }
        return new MaterialStates(true, savedState(normalized, "primary"), savedState(normalized, "secondary"));
    }

    public static IBlockState authoritativeBlockcrafteryState(Object tile, IBlockState legacyState) {
        MaterialStates saved = states(tile, "blockcraftery");
        return saved.present() && isUsableMaterialState(saved.primary()) ? saved.primary() : legacyState;
    }

    public static int inheritedLightValue(
            int fallback,
            Block hostBlock,
            IBlockState hostState,
            IBlockAccess world,
            BlockPos pos
    ) {
        TileEntity tile = MinecraftMappingCompat.worldTileEntity(world, pos);
        NBTTagCompound data = data(tile);
        String source = string(data, "source");
        if ("architecturecraft".equals(source)) {
            MaterialStates states = states(tile, source);
            return Math.max(fallback, Math.max(
                    stateLightValue(states.primary(), world, pos),
                    stateLightValue(states.secondary(), world, pos)
            ));
        }
        IBlockState inherited = FramedBlockEffectiveState.state(world, pos);
        return inherited == null ? fallback : Math.max(fallback, stateLightValue(inherited, world, pos));
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

        NBTTagCompound existing = access.gpom$getFramedMaterialData();
        if (existing != null) {
            NBTTagCompound refreshed = createData(source, primaryState, secondaryState, access);
            NBTTagCompound data = normalizeData(refreshed == null ? existing : refreshed, root);
            copyInternalItem(data, root);
            NBTTagCompound features = featureTag(primaryState, secondaryState, access);
            if (features != null) {
                setTag(data, "features", features);
            }
            setTag(root, TAG, data);
            access.gpom$setFramedMaterialData(data);
            probe("write-existing", source, primaryState, access, data);
            return;
        }

        NBTTagCompound data = createData(source, primaryState, secondaryState, access);
        if (data == null) {
            return;
        }

        copyInternalItem(data, root);
        data = normalizeData(data, root);
        setTag(root, TAG, data);
        access.gpom$setFramedMaterialData(data);
        probe("write-new", source, primaryState, access, data);
    }

    private static void refresh(
            FramedMaterialDataAccess access,
            String source,
            IBlockState primaryState,
            IBlockState secondaryState
    ) {
        if (access == null) {
            return;
        }
        NBTTagCompound data = normalizeData(createData(source, primaryState, secondaryState, access), null);
        access.gpom$setFramedMaterialData(data);
        probe("refresh", source, primaryState, access, data);
    }

    private static void probe(
            String phase,
            String source,
            IBlockState primaryState,
            FramedMaterialDataAccess access,
            NBTTagCompound data
    ) {
        if (!GpomEarlyConfig.optimizationInfoLogsEnabled()) {
            return;
        }
        if (!"blockcraftery".equals(source) || primaryState == null || data == null) {
            return;
        }
        Block block = MinecraftMappingCompat.blockStateBlock(primaryState);
        ResourceLocation registryName = MinecraftMappingCompat.blockRegistryName(block);
        String material = registryName == null ? "" : registryName.toString().toLowerCase(java.util.Locale.ROOT);
        if (!probeMaterial(material)) {
            return;
        }

        String position = "unknown";
        if (access instanceof TileEntity) {
            BlockPos pos = MinecraftMappingCompat.tileEntityPos((TileEntity) access);
            if (pos != null) {
                position = MinecraftMappingCompat.blockPosX(pos)
                        + "," + MinecraftMappingCompat.blockPosY(pos)
                        + "," + MinecraftMappingCompat.blockPosZ(pos);
            }
        }
        String key = phase + '|' + position + '|' + material;
        if (!PROBED_MATERIALS.add(key) || PROBE_COUNT.getAndIncrement() >= PROBE_LIMIT) {
            return;
        }

        NBTTagCompound primary = compound(data, "primary");
        NBTTagCompound features = compound(data, "features");
        GPOM.LOGGER.info(
                "[GPOM Framed Material Probe] phase={} pos={} material={} emission={} bloom={} "
                        + "declaredLayer={} framedLayer={} renderLayers={} payload=true",
                phase,
                position,
                material,
                integer(features, "emission"),
                booleanValue(features, "bloom"),
                string(primary, "declaredLayer"),
                string(primary, "framedLayer"),
                string(primary, "renderLayers")
        );
    }

    private static boolean probeMaterial(String material) {
        return material.contains("luminous")
                || material.contains("lumen")
                || material.contains("fused")
                || material.contains("clear")
                || material.contains("glass")
                || material.contains("quartz");
    }

    private static boolean isUsableMaterialState(IBlockState state) {
        Block block = MinecraftMappingCompat.blockStateBlock(state);
        ResourceLocation name = MinecraftMappingCompat.blockRegistryName(block);
        return name != null && !"minecraft:air".equals(name.toString());
    }

    private static NBTTagCompound createData(
            String source,
            IBlockState primaryState,
            IBlockState secondaryState,
            FramedMaterialDataAccess access
    ) {
        NBTTagCompound data = new NBTTagCompound();
        setInteger(data, "version", VERSION);
        setString(data, "source", source);
        boolean hasState = putState(data, "primary", primaryState);
        hasState |= putState(data, "secondary", secondaryState);
        if (!hasState) {
            return null;
        }
        NBTTagCompound features = featureTag(primaryState, secondaryState, access);
        if (features != null) {
            setTag(data, "features", features);
        }
        return data;
    }

    private static NBTTagCompound featureTag(
            IBlockState primaryState,
            IBlockState secondaryState,
            FramedMaterialDataAccess access
    ) {
        if (primaryState == null) {
            return null;
        }
        IBlockAccess world = null;
        BlockPos pos = null;
        if (access instanceof TileEntity) {
            TileEntity tile = (TileEntity) access;
            world = MinecraftMappingCompat.tileEntityWorld(tile);
            pos = MinecraftMappingCompat.tileEntityPos(tile);
        }

        NBTTagCompound features = new NBTTagCompound();
        setInteger(features, "emission", Math.max(
                stateVisualEmission(primaryState, world, pos),
                stateVisualEmission(secondaryState, world, pos)
        ));
        setBoolean(features, "bloom",
                stateHasBloomLayer(primaryState) || stateHasBloomLayer(secondaryState));
        return features;
    }

    private static void refreshDerivedFeatures(NBTTagCompound data, FramedMaterialDataAccess access) {
        if (data == null) {
            return;
        }
        NBTTagCompound features = featureTag(
                savedState(data, "primary"),
                savedState(data, "secondary"),
                access
        );
        if (features != null) {
            setTag(data, "features", features);
        }
    }

    private static void copyInternalItem(NBTTagCompound data, NBTTagCompound root) {
        NBTTagCompound stack = compound(root, "stack");
        if (stack != null) {
            setTag(data, "internalItem", MinecraftMappingCompat.nbtCopy(stack));
        }
    }

    /** Canonicalize legacy Blockcraftery payloads before they become authoritative. */
    private static NBTTagCompound normalizeData(NBTTagCompound data, NBTTagCompound tileRoot) {
        if (data == null || !"blockcraftery".equals(string(data, "source"))) {
            return data;
        }

        String legacyId = stackId(tileRoot);
        NBTTagCompound primary = compound(data, "primary");
        String primaryId = string(primary, "id");
        if (isEnderIoGlassFamily(legacyId)
                && isEnderIoGlassFamily(primaryId)
                && !sameGlassFamily(legacyId, primaryId)) {
            setString(primary, "id", legacyId);
            int stackMeta = stackMeta(tileRoot);
            if (stackMeta >= 0) {
                setInteger(primary, "meta", stackMeta);
            }
        }

        canonicalizeStateTag(primary);
        canonicalizeStateTag(compound(data, "secondary"));
        return data;
    }

    private static void canonicalizeStateTag(NBTTagCompound state) {
        if (state == null) {
            return;
        }
        String id = string(state, "id").toLowerCase(java.util.Locale.ROOT);
        if (!isEnderIoGlassFamily(id)) {
            return;
        }

        ResourceLocation name;
        try {
            name = new ResourceLocation(id);
        } catch (RuntimeException ignored) {
            return;
        }
        Object rawBlock = MinecraftMappingCompat.invoke(ForgeRegistries.BLOCKS, "blockRegistry.getValue",
                new Class<?>[]{ResourceLocation.class}, new Object[]{name},
                "func_82594_a", "getObject", "getValue");
        if (!(rawBlock instanceof Block)) {
            return;
        }
        Object rawState = MinecraftMappingCompat.invoke(rawBlock, "block.getStateFromMeta",
                new Class<?>[]{int.class}, new Object[]{integer(state, "meta")},
                "func_176203_a", "getStateFromMeta");
        if (!(rawState instanceof IBlockState)) {
            return;
        }
        NBTTagCompound canonical = stateTag((IBlockState) rawState);
        setString(state, "id", string(canonical, "id"));
        setInteger(state, "meta", integer(canonical, "meta"));
        setBoolean(state, "opaqueCube", booleanValue(canonical, "opaqueCube"));
        setBoolean(state, "hasTileEntity", booleanValue(canonical, "hasTileEntity"));
        setString(state, "declaredLayer", string(canonical, "declaredLayer"));
        setString(state, "framedLayer", string(canonical, "framedLayer"));
        setString(state, "renderLayers", string(canonical, "renderLayers"));
        setInteger(state, "emission", integer(canonical, "emission"));
        setBoolean(state, "bloom", booleanValue(canonical, "bloom"));
        NBTTagCompound properties = compound(canonical, "properties");
        if (properties != null) {
            setTag(state, "properties", properties);
        }
    }

    private static boolean isEnderIoGlassFamily(String id) {
        String normalized = id == null ? "" : id.toLowerCase(java.util.Locale.ROOT);
        return normalized.endsWith("block_fused_glass") || normalized.endsWith("block_fused_quartz");
    }

    private static boolean sameGlassFamily(String first, String second) {
        return (first.toLowerCase(java.util.Locale.ROOT).endsWith("block_fused_glass")
                && second.toLowerCase(java.util.Locale.ROOT).endsWith("block_fused_glass"))
                || (first.toLowerCase(java.util.Locale.ROOT).endsWith("block_fused_quartz")
                && second.toLowerCase(java.util.Locale.ROOT).endsWith("block_fused_quartz"));
    }

    private static String stackId(NBTTagCompound root) {
        NBTTagCompound stack = compound(root, "stack");
        return string(stack, "id");
    }

    private static int stackMeta(NBTTagCompound root) {
        NBTTagCompound stack = compound(root, "stack");
        int damage = integer(stack, "Damage");
        return damage != 0 ? damage : integer(stack, "meta");
    }

    private static IBlockState savedState(NBTTagCompound data, String key) {
        NBTTagCompound saved = compound(data, key);
        String id = string(saved, "id");
        if (id.isEmpty()) {
            return null;
        }

        ResourceLocation name;
        try {
            name = new ResourceLocation(id);
        } catch (RuntimeException ignored) {
            return null;
        }
        Object rawBlock = MinecraftMappingCompat.invoke(ForgeRegistries.BLOCKS, "blockRegistry.getValue",
                new Class<?>[]{ResourceLocation.class}, new Object[]{name},
                "func_82594_a", "getObject", "getValue");
        if (!(rawBlock instanceof Block)) {
            return null;
        }

        Block block = (Block) rawBlock;
        Object rawState = MinecraftMappingCompat.invoke(block, "block.getStateFromMeta",
                new Class<?>[]{int.class}, new Object[]{integer(saved, "meta")},
                "func_176203_a", "getStateFromMeta");
        if (!(rawState instanceof IBlockState)) {
            return null;
        }
        IBlockState state = (IBlockState) rawState;
        NBTTagCompound properties = compound(saved, "properties");
        if (properties == null) {
            return state;
        }

        Object rawProperties = MinecraftMappingCompat.invoke(state, "blockState.getProperties",
                MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS,
                "func_177228_b", "getProperties");
        if (!(rawProperties instanceof Map)) {
            return state;
        }
        for (Object rawProperty : ((Map<?, ?>) rawProperties).keySet()) {
            if (!(rawProperty instanceof IProperty)) {
                continue;
            }
            IProperty<?> property = (IProperty<?>) rawProperty;
            String propertyName = propertyName(property);
            String savedValue = string(properties, propertyName);
            Comparable<?> value = allowedValue(property, savedValue);
            if (value != null) {
                Object updated = MinecraftMappingCompat.invoke(state, "blockState.withProperty",
                        new Class<?>[]{IProperty.class, Comparable.class}, new Object[]{property, value},
                        "func_177226_a", "withProperty");
                if (updated instanceof IBlockState) {
                    state = (IBlockState) updated;
                }
            }
        }
        return state;
    }

    private static Comparable<?> allowedValue(IProperty<?> property, String savedValue) {
        if (savedValue.isEmpty()) {
            return null;
        }
        Object raw = MinecraftMappingCompat.invoke(property, "property.getAllowedValues",
                MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS,
                "func_177700_c", "getAllowedValues");
        if (!(raw instanceof Collection)) {
            return null;
        }
        for (Object candidate : (Collection<?>) raw) {
            if (candidate instanceof Comparable && savedValue.equals(propertyValueName(property, candidate))) {
                return (Comparable<?>) candidate;
            }
        }
        return null;
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
        BlockRenderLayer framedLayer = framedRenderLayer(block, state);
        if (framedLayer != null) {
            setString(tag, "framedLayer", framedLayer.name());
        }
        setString(tag, "renderLayers", renderLayers(block, state));
        setInteger(tag, "emission", stateVisualEmission(state, null, null));
        setBoolean(tag, "bloom", stateHasBloomLayer(state));

        NBTTagCompound properties = propertiesTag(state);
        if (properties != null) {
            setTag(tag, "properties", properties);
        }
        return tag;
    }

    public static BlockRenderLayer framedRenderLayer(Block block, IBlockState state) {
        ResourceLocation name = MinecraftMappingCompat.blockRegistryName(block);
        String id = name != null ? name.toString().toLowerCase(java.util.Locale.ROOT) : "";
        return MinecraftMappingCompat.blockRenderLayer(block);
    }

    public static int visualEmission(IBlockState state) {
        return stateVisualEmission(state, null, null);
    }

    public static boolean hasBloom(IBlockState state) {
        return stateHasBloomLayer(state);
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

    private static boolean stateHasBloomLayer(IBlockState state) {
        Block block = MinecraftMappingCompat.blockStateBlock(state);
        if (block == null) {
            return false;
        }
        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            if ("BLOOM".equals(layer.name()) && MinecraftMappingCompat.blockCanRenderInLayer(block, state, layer)) {
                return true;
            }
        }
        return luminousBlockShouldGlow(block, state);
    }

    private static int stateVisualEmission(IBlockState state, IBlockAccess world, BlockPos pos) {
        Block block = MinecraftMappingCompat.blockStateBlock(state);
        int lightValue = stateLightValue(state, world, pos);
        return block != null && luminousBlockShouldGlow(block, state) ? 15 : lightValue;
    }

    private static int stateLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        Block block = MinecraftMappingCompat.blockStateBlock(state);
        if (block == null) {
            return 0;
        }
        Object value = world != null && pos != null
                ? MinecraftMappingCompat.invoke(block, "block.getLightValueAt",
                new Class<?>[]{IBlockState.class, IBlockAccess.class, BlockPos.class}, new Object[]{state, world, pos},
                "getLightValue")
                : MinecraftMappingCompat.invoke(block, "block.getLightValue",
                new Class<?>[]{IBlockState.class}, new Object[]{state},
                "func_149750_m", "getLightValue");
        return value instanceof Number ? Math.max(0, Math.min(15, ((Number) value).intValue())) : 0;
    }

    private static boolean luminousBlockShouldGlow(Block block, IBlockState state) {
        if (block == null || state == null || !isRandomThingsLuminousBlock(block.getClass())) {
            return false;
        }
        Object value = MinecraftMappingCompat.invoke(block, "randomThings.shouldGlow",
                new Class<?>[]{IBlockState.class, int.class}, new Object[]{state, 0},
                "shouldGlow");
        return Boolean.TRUE.equals(value);
    }

    private static boolean isRandomThingsLuminousBlock(Class<?> type) {
        Boolean cached = LUMINOUS_BLOCK_TYPES.get(type);
        if (cached != null) {
            return cached;
        }
        boolean luminous = hasNamedInterface(type, RANDOM_THINGS_LUMINOUS_BLOCK);
        Boolean previous = LUMINOUS_BLOCK_TYPES.putIfAbsent(type, luminous);
        return previous == null ? luminous : previous;
    }

    private static boolean hasNamedInterface(Class<?> type, String interfaceName) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Class<?> implemented : current.getInterfaces()) {
                if (interfaceName.equals(implemented.getName())
                        || hasNamedInterface(implemented, interfaceName)) {
                    return true;
                }
            }
        }
        return false;
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
        if (value instanceof String) {
            return (String) value;
        }
        Object field = MinecraftMappingCompat.fieldValue(property, "property.name",
                "field_177703_b", "name");
        return field instanceof String ? (String) field : null;
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

    private static String string(NBTTagCompound tag, String key) {
        if (tag == null || key == null) {
            return "";
        }
        Object value = MinecraftMappingCompat.invoke(tag, "nbt.getString",
                new Class<?>[]{String.class}, new Object[]{key},
                "func_74779_i", "getString");
        return value instanceof String ? (String) value : "";
    }

    private static int integer(NBTTagCompound tag, String key) {
        if (tag == null || key == null) {
            return 0;
        }
        Object value = MinecraftMappingCompat.invoke(tag, "nbt.getInteger",
                new Class<?>[]{String.class}, new Object[]{key},
                "func_74762_e", "getInteger");
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static boolean booleanValue(NBTTagCompound tag, String key) {
        if (tag == null || key == null) {
            return false;
        }
        Object value = MinecraftMappingCompat.invoke(tag, "nbt.getBoolean",
                new Class<?>[]{String.class}, new Object[]{key},
                "func_74767_n", "getBoolean");
        return value instanceof Boolean && (Boolean) value;
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

    public static final class MaterialStates {
        private static final MaterialStates EMPTY = new MaterialStates(false, null, null);

        private final boolean present;
        private final IBlockState primary;
        private final IBlockState secondary;

        private MaterialStates(boolean present, IBlockState primary, IBlockState secondary) {
            this.present = present;
            this.primary = primary;
            this.secondary = secondary;
        }

        public boolean present() {
            return present;
        }

        public IBlockState primary() {
            return primary;
        }

        public IBlockState secondary() {
            return secondary;
        }
    }
}
