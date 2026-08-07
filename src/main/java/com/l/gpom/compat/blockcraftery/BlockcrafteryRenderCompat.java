package com.l.gpom.compat.blockcraftery;

import com.l.gpom.client.ClientAccess;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.compat.framed.FramedMaterialData;
import com.l.gpom.compat.framed.FramedQuadProvenance;
import com.l.gpom.util.ReflectionLookup;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BlockcrafteryRenderCompat {
    private static final ConcurrentMap<Class<?>, Field> DATA_FIELDS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> ADD_GEOMETRY_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> ADD_ITEM_MODEL_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> STATE_PROPERTY_METHODS = new ConcurrentHashMap<>();

    private BlockcrafteryRenderCompat() {
    }

    public static BlockRenderLayer effectiveRenderLayer(BlockRenderLayer currentLayer, IBlockState copied) {
        if (currentLayer == null || copied == null) {
            return BlockRenderLayer.SOLID;
        }

        try {
            Block block = MinecraftMappingCompat.blockStateBlock(copied);
            BlockRenderLayer framedLayer = FramedMaterialData.framedRenderLayer(block, copied);
            return framedLayer == currentLayer ? currentLayer : null;
        } catch (RuntimeException | LinkageError ignored) {
            try {
                Block block = MinecraftMappingCompat.blockStateBlock(copied);
                return MinecraftMappingCompat.blockRenderLayer(block) == currentLayer ? currentLayer : null;
            } catch (RuntimeException | LinkageError ignoredAgain) {
                return null;
            }
        }
    }

    public static boolean shouldRenderCopiedBlockInCurrentLayer(IBlockState copied) {
        BlockRenderLayer currentLayer = MinecraftMappingCompat.currentRenderLayer();
        if (copied == null || currentLayer == null) {
            return false;
        }

        Block block;
        try {
            block = MinecraftMappingCompat.blockStateBlock(copied);
        } catch (RuntimeException | LinkageError ignored) {
            return currentLayer == BlockRenderLayer.SOLID;
        }

        BlockRenderLayer declaredLayer = FramedMaterialData.framedRenderLayer(block, copied);
        if (declaredLayer != null) {
            if (declaredLayer == currentLayer) {
                return true;
            }
            if (currentLayer == BlockRenderLayer.CUTOUT_MIPPED
                    && declaredLayer == BlockRenderLayer.CUTOUT) {
                return true;
            }
            if (currentLayer == BlockRenderLayer.SOLID && declaredLayer != BlockRenderLayer.SOLID) {
                return false;
            }
        }

        try {
            return MinecraftMappingCompat.blockCanRenderInLayer(block, copied, currentLayer);
        } catch (RuntimeException | LinkageError ignored) {
            return declaredLayer == null && currentLayer == BlockRenderLayer.SOLID;
        }
    }

    private static BlockRenderLayer declaredRenderLayer(Block block) {
        try {
            return MinecraftMappingCompat.blockRenderLayer(block);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    public static List<BakedQuad> getQuads(Object model, IBlockState state, EnumFacing side, long rand) {
        List<BakedQuad> result = new ArrayList<>();
        try {
            if (state instanceof IExtendedBlockState) {
                appendBlockQuads(model, (IExtendedBlockState) state, side, rand, result);
            }
            if (state == null) {
                addItemModel(model, result, side);
            }
        } catch (RuntimeException | LinkageError ignored) {
            result.clear();
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void appendBlockQuads(
            Object model,
            IExtendedBlockState hostState,
            EnumFacing side,
            long rand,
            List<BakedQuad> result
    ) {
        IBlockState copied = copiedState(hostState);
        String key = cacheKey(copied, hostState, side, MinecraftMappingCompat.currentRenderLayer());
        Map<String, List<BakedQuad>> cache = data(model);
        if (cache != null && cache.containsKey(key)) {
            Collection<BakedQuad> cached = cache.get(key);
            if (cached != null) {
                result.addAll(cached);
            }
            return;
        }

        List<BakedQuad> generated = new ArrayList<>();
        TextureAtlasSprite[] sprites = new TextureAtlasSprite[] {particle(model)};
        int[] tints = new int[] {0};
        boolean useFallbackTexture = true;

        Block copiedBlock = copied == null ? null : MinecraftMappingCompat.blockStateBlock(copied);
        Object airValue = MinecraftMappingCompat.staticFieldValue(Blocks.class, "blocks.air", "field_150350_a", "AIR");
        Block air = airValue instanceof Block ? (Block) airValue : ForgeRegistries.BLOCKS.getValue(new net.minecraft.util.ResourceLocation("minecraft", "air"));
        if (copiedBlock != null && copiedBlock != air) {
            IBakedModel copiedModel = ClientAccess.modelForState(ClientAccess.minecraft(), copied);
            if (copiedModel != null) {
                sprites[0] = particle(copiedModel);
                List<BakedQuad> copiedQuads = modelQuads(copiedModel, copied, side, rand);
                if (!copiedQuads.isEmpty()) {
                    // The copied quads provide material sprites and tint only.
                    // addGeometry must still create the editable block's shape.
                    sprites = new TextureAtlasSprite[copiedQuads.size()];
                    tints = new int[copiedQuads.size()];
                    for (int index = 0; index < copiedQuads.size(); index++) {
                        BakedQuad quad = copiedQuads.get(index);
                        tints[index] = quadHasTintIndex(quad) ? quadTintIndex(quad) : -1;
                        sprites[index] = quadSprite(quad);
                    }
                }
                useFallbackTexture = false;
            }
        }

        BlockRenderLayer layer = MinecraftMappingCompat.currentRenderLayer();
        if (generated.isEmpty() && ((useFallbackTexture && layer == BlockRenderLayer.CUTOUT_MIPPED)
                || (!useFallbackTexture && rendersInLayer(copied, layer)))) {
            for (int index = 0; index < sprites.length; index++) {
                int firstGenerated = generated.size();
                addGeometry(model, generated, side, copied, hostState, repeatedSprites(sprites[index]), tints[index]);
                registerQuads(generated.subList(firstGenerated, generated.size()), copied, side,
                        FramedQuadProvenance.Source.HOST_FALLBACK);
            }
        }

        if (cache != null) {
            cache.put(key, generated);
        }
        result.addAll(generated);
    }

    private static void registerQuads(Collection<BakedQuad> quads, IBlockState material, EnumFacing side,
                                      FramedQuadProvenance.Source source) {
        if (quads == null || material == null) {
            return;
        }
        for (BakedQuad quad : quads) {
            FramedQuadProvenance.register(quad, 0, material, side, source);
        }
    }

    private static boolean rendersInLayer(IBlockState copied, BlockRenderLayer layer) {
        if (copied == null || layer == null) {
            return false;
        }
        try {
            Block block = MinecraftMappingCompat.blockStateBlock(copied);
            BlockRenderLayer framedLayer = FramedMaterialData.framedRenderLayer(block, copied);
            return framedLayer == layer
                    || (layer == BlockRenderLayer.CUTOUT_MIPPED && framedLayer == BlockRenderLayer.CUTOUT);
        } catch (RuntimeException | LinkageError ignored) {
            try {
                Block block = MinecraftMappingCompat.blockStateBlock(copied);
                return MinecraftMappingCompat.blockRenderLayer(block) == layer;
            } catch (RuntimeException | LinkageError ignoredAgain) {
                return false;
            }
        }
    }

    private static IBlockState copiedState(IExtendedBlockState hostState) {
        try {
            Block hostBlock = MinecraftMappingCompat.blockStateBlock((IBlockState) hostState);
            Method method = cachedMethod(STATE_PROPERTY_METHODS, hostBlock.getClass(), "getStateProperty");
            Object property = method.invoke(hostBlock);
            if (!(property instanceof IUnlistedProperty)) {
                return null;
            }
            Object value = hostState.getValue((IUnlistedProperty<?>) property);
            return value instanceof IBlockState ? (IBlockState) value : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<BakedQuad>> data(Object model) {
        try {
            Object value = cachedField(DATA_FIELDS, model.getClass(), "data").get(model);
            return value instanceof Map ? (Map<String, List<BakedQuad>>) value : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static TextureAtlasSprite particle(Object model) {
        Object value = MinecraftMappingCompat.invoke(model, "bakedModel.getParticleTexture",
                MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS,
                "func_177554_e", "getParticleTexture");
        return value instanceof TextureAtlasSprite ? (TextureAtlasSprite) value : ClientAccess.missingSprite(ClientAccess.minecraft());
    }

    @SuppressWarnings("unchecked")
    private static List<BakedQuad> modelQuads(Object model, IBlockState state, EnumFacing side, long rand) {
        Object value = MinecraftMappingCompat.invoke(model, "bakedModel.getQuads",
                new Class<?>[]{IBlockState.class, EnumFacing.class, long.class}, new Object[]{state, side, rand},
                "func_188616_a", "getQuads");
        return value instanceof List ? (List<BakedQuad>) value : java.util.Collections.<BakedQuad>emptyList();
    }

    private static TextureAtlasSprite quadSprite(BakedQuad quad) {
        Object value = MinecraftMappingCompat.invoke(quad, "bakedQuad.getSprite",
                MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS,
                "func_187508_a", "getSprite");
        return value instanceof TextureAtlasSprite ? (TextureAtlasSprite) value : ClientAccess.missingSprite(ClientAccess.minecraft());
    }

    private static boolean quadHasTintIndex(BakedQuad quad) {
        Object value = MinecraftMappingCompat.invoke(quad, "bakedQuad.hasTintIndex",
                MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS,
                "func_178212_b", "hasTintIndex");
        return value instanceof Boolean && (Boolean) value;
    }

    private static int quadTintIndex(BakedQuad quad) {
        Object value = MinecraftMappingCompat.invoke(quad, "bakedQuad.getTintIndex",
                MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS,
                "func_178211_c", "getTintIndex");
        return value instanceof Number ? ((Number) value).intValue() : -1;
    }

    private static TextureAtlasSprite[] repeatedSprites(TextureAtlasSprite sprite) {
        return new TextureAtlasSprite[] {sprite, sprite, sprite, sprite, sprite, sprite};
    }

    @SuppressWarnings("unchecked")
    private static void addGeometry(
            Object model,
            List<BakedQuad> quads,
            EnumFacing side,
            IBlockState material,
            IBlockState state,
            TextureAtlasSprite[] sprites,
            int tint
    ) {
        try {
            Method method = cachedMethod(
                    ADD_GEOMETRY_METHODS,
                    model.getClass(),
                    "addGeometry",
                    List.class,
                    EnumFacing.class,
                    IBlockState.class,
                    TextureAtlasSprite[].class,
                    int.class
            );
            method.invoke(model, quads, side, state, sprites, tint);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
    }

    private static void addItemModel(Object model, List<BakedQuad> quads, EnumFacing side) {
        try {
            Method method = cachedMethod(
                    ADD_ITEM_MODEL_METHODS,
                    model.getClass(),
                    "addItemModel",
                    List.class,
                    EnumFacing.class
            );
            method.invoke(model, quads, side);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
    }

    private static String cacheKey(IBlockState copied, IBlockState host, EnumFacing side, BlockRenderLayer layer) {
        return String.valueOf(copied)
                + '_'
                + host
                + '_'
                + (side == null ? "null" : side.toString())
                + (layer == null ? "null" : layer.toString());
    }

    private static Method cachedMethod(
            ConcurrentMap<Class<?>, Method> cache,
            Class<?> type,
            String name,
            Class<?>... parameterTypes
    ) throws ReflectiveOperationException {
        Method method = cache.get(type);
        if (method != null) {
            return method;
        }

        Method resolved = ReflectionLookup.findMethod(type, new String[] {name}, parameterTypes);
        Method previous = cache.putIfAbsent(type, resolved);
        return previous == null ? resolved : previous;
    }

    private static Field cachedField(ConcurrentMap<Class<?>, Field> cache, Class<?> type, String name)
            throws ReflectiveOperationException {
        Field field = cache.get(type);
        if (field != null) {
            return field;
        }

        Field resolved = ReflectionLookup.findField(type, new String[] {name});
        Field previous = cache.putIfAbsent(type, resolved);
        return previous == null ? resolved : previous;
    }
}
