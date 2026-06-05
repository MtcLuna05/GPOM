package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.core.TargetedModVersions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BetweenlandsItemRenderOptimizations {
    private static final String DRUID_ALTAR_RENDERER = "thebetweenlands.client.render.tile.RenderDruidAltar";
    private static final String DRUID_ALTAR_TILE = "thebetweenlands.common.tile.TileEntityDruidAltar";
    private static final Set<String> LOGGED_RENDER_DECISIONS = ConcurrentHashMap.newKeySet();
    private static Method itemStackIsEmpty;
    private static Method itemStackGetItem;
    private static Method druidAltarItemRender;
    private static Method lazyTileRendererGetDelegate;
    private static Method tileEntitySetPos;
    private static Method tileEntitySetWorld;
    private static volatile TileEntity druidAltarClientTile;
    private static volatile TileEntitySpecialRenderer<?> druidAltarRenderer;
    private static final Map<Class<?>, TileEntity> CLIENT_TILE_BY_RENDERER = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> TESR_RENDER_METHODS = new ConcurrentHashMap<>();
    private static final Map<String, TileEntitySpecialRenderer<?>> RENDERER_BY_CLASS_NAME = new ConcurrentHashMap<>();

    private BetweenlandsItemRenderOptimizations() {
    }

    public static void renderItemStack(TileEntitySpecialRenderer<?> renderer, ItemStack stack, double x, double y, double z,
                                       float partialTicks, int destroyStage, float alpha) {
        if (renderer == null) {
            return;
        }
        if (TargetedModVersions.isBetweenlandsClass("thebetweenlands.client.render.tile.RenderItemStackAsTileEntity")
                && isDruidAltar(stack)) {
            TileEntitySpecialRenderer<?> druidRenderer = resolveDruidAltarRenderer(renderer);
            if (druidRenderer != null && renderDruidAltarItem(druidRenderer, x, y, z)) {
                return;
            }

            TileEntity clientTile = getDruidAltarClientTile(renderer);
            if (clientTile != null) {
                renderOriginal(renderer, clientTile, x, y, z, partialTicks, destroyStage, alpha);
                return;
            }
        }

        if (TargetedModVersions.isBetweenlandsClass("thebetweenlands.client.render.tile.RenderItemStackAsTileEntity")) {
            TileEntitySpecialRenderer<?> targetRenderer = resolveConcreteRenderer(renderer, stack);
            TileEntitySpecialRenderer<?> effectiveRenderer = targetRenderer == null ? renderer : targetRenderer;
            renderOriginal(effectiveRenderer, null, stack, x, y, z, partialTicks, destroyStage, alpha);
            return;
        }
        renderOriginal(renderer, null, stack, x, y, z, partialTicks, destroyStage, alpha);
    }

    private static boolean isDruidAltar(ItemStack stack) {
        try {
            if (stack == null || isStackEmpty(stack)) {
                return false;
            }
            Object item = getItem(stack);
            if (!(item instanceof net.minecraft.item.Item)) {
                return false;
            }
            ResourceLocation registryName = ((net.minecraft.item.Item) item).getRegistryName();
            if (registryName != null && "thebetweenlands:druid_altar".equals(registryName.toString())) {
                return true;
            }
            String stackText = String.valueOf(stack);
            return stackText.indexOf("thebetweenlands:druid_altar") >= 0
                    || stackText.indexOf("thebetweenlands.druid_altar") >= 0
                    || stackText.indexOf("druid_altar") >= 0;
        } catch (Throwable ignored) {
            String stackText = String.valueOf(stack);
            return stackText.indexOf("thebetweenlands:druid_altar") >= 0
                    || stackText.indexOf("thebetweenlands.druid_altar") >= 0
                    || stackText.indexOf("druid_altar") >= 0;
        }
    }

    private static boolean isDruidAltarRenderer(TileEntitySpecialRenderer<?> renderer) {
        return DRUID_ALTAR_RENDERER.equals(renderer.getClass().getName());
    }

    private static TileEntitySpecialRenderer<?> unwrapDruidAltarRenderer(TileEntitySpecialRenderer<?> renderer) {
        if (isDruidAltarRenderer(renderer)) {
            return renderer;
        }

        try {
            Object delegate = getLazyRendererDelegate(renderer);
            if (delegate instanceof TileEntitySpecialRenderer
                    && isDruidAltarRenderer((TileEntitySpecialRenderer<?>) delegate)) {
                logUnwrappedRenderer(renderer, (TileEntitySpecialRenderer<?>) delegate);
                return (TileEntitySpecialRenderer<?>) delegate;
            }
        } catch (Throwable ignored) {
            logRenderDecision("unwrap-druid-altar", false);
        }
        return null;
    }

    private static Object getLazyRendererDelegate(TileEntitySpecialRenderer<?> renderer) throws ReflectiveOperationException {
        Method method = lazyTileRendererGetDelegate;
        if (method == null) {
            method = findMethod(renderer.getClass(), new String[] {"getDelegate"});
            lazyTileRendererGetDelegate = method;
        }
        return method == null ? null : method.invoke(renderer);
    }

    private static TileEntitySpecialRenderer<?> resolveDruidAltarRenderer(TileEntitySpecialRenderer<?> renderer) {
        TileEntitySpecialRenderer<?> unwrapped = unwrapDruidAltarRenderer(renderer);
        if (unwrapped != null) {
            return unwrapped;
        }

        TileEntitySpecialRenderer<?> cached = druidAltarRenderer;
        if (cached != null) {
            logResolvedRenderer("cached", cached);
            return cached;
        }

        synchronized (BetweenlandsItemRenderOptimizations.class) {
            cached = druidAltarRenderer;
            if (cached != null) {
                logResolvedRenderer("cached", cached);
                return cached;
            }

            cached = findStaticDruidAltarRenderer();
            if (cached == null) {
                cached = instantiateDruidAltarRenderer();
            }
            if (cached != null) {
                druidAltarRenderer = cached;
                logResolvedRenderer("resolved", cached);
            }
            return cached;
        }
    }

    private static TileEntitySpecialRenderer<?> findStaticDruidAltarRenderer() {
        try {
            Class<?> rendererClass = Class.forName(DRUID_ALTAR_RENDERER);
            for (String fieldName : new String[] {"instance", "INSTANCE"}) {
                try {
                    Field field = rendererClass.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (value instanceof TileEntitySpecialRenderer) {
                        return (TileEntitySpecialRenderer<?>) value;
                    }
                } catch (NoSuchFieldException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static TileEntitySpecialRenderer<?> instantiateDruidAltarRenderer() {
        try {
            Object instance = Class.forName(DRUID_ALTAR_RENDERER).newInstance();
            if (!(instance instanceof TileEntitySpecialRenderer)) {
                return null;
            }
            TileEntitySpecialRenderer<?> renderer = (TileEntitySpecialRenderer<?>) instance;
            attachRendererDispatcher(renderer);
            return renderer;
        } catch (Throwable throwable) {
            logInstantiateRendererFailure(throwable);
            return null;
        }
    }

    private static void attachRendererDispatcher(TileEntitySpecialRenderer<?> renderer) {
        try {
            Class<?> dispatcherClass = Class.forName("net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher");
            Object dispatcher = staticFieldValue(dispatcherClass, "instance", "field_147556_a");
            if (dispatcher == null) {
                return;
            }
            Method method = findMethod(
                    TileEntitySpecialRenderer.class,
                    new String[] {"func_147497_a", "setRendererDispatcher"},
                    dispatcherClass
            );
            if (method != null) {
                method.invoke(renderer, dispatcher);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object staticFieldValue(Class<?> type, String... names) throws IllegalAccessException {
        for (String name : names) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(null);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static Object fieldValue(Object owner, String... names) throws IllegalAccessException {
        Class<?> current = owner.getClass();
        while (current != null) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(owner);
                } catch (NoSuchFieldException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static void setFieldValue(Object owner, Object value, String... names) throws IllegalAccessException {
        Class<?> current = owner.getClass();
        while (current != null) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    field.set(owner, value);
                    return;
                } catch (NoSuchFieldException ignored) {
                }
            }
            current = current.getSuperclass();
        }
    }

    private static boolean renderDruidAltarItem(TileEntitySpecialRenderer<?> renderer, double x, double y, double z) {
        try {
            Method method = druidAltarItemRender;
            if (method == null) {
                method = findMethod(renderer.getClass(), "renderTileAsItem", double.class, double.class, double.class);
                druidAltarItemRender = method;
            }
            if (method == null) {
                logRenderDecision("missing-druid-item-render", false);
                return false;
            }
            pushRenderState();
            try {
                method.invoke(renderer, x, y, z);
            } finally {
                popRenderState();
            }
            logRenderDecision("druid-altar", true);
            return true;
        } catch (Throwable ignored) {
            logRenderDecision("druid-altar", false);
            return false;
        }
    }

    private static void logResolvedRenderer(String source, TileEntitySpecialRenderer<?> renderer) {
        String key = "druid-altar-renderer-" + source + ":" + renderer.getClass().getName();
        if (LOGGED_RENDER_DECISIONS.add(key)) {
            GPOM.LOGGER.info("[Betweenlands Item Renderer] Using {} {} for thebetweenlands:druid_altar item rendering",
                    source, renderer.getClass().getName());
        }
    }

    private static void logInstantiateRendererFailure(Throwable throwable) {
        String key = "druid-altar-renderer-instantiate-failed";
        if (LOGGED_RENDER_DECISIONS.add(key)) {
            GPOM.LOGGER.warn("[Betweenlands Item Renderer] Could not instantiate {} for item rendering",
                    DRUID_ALTAR_RENDERER, throwable);
        }
    }

    private static TileEntity getDruidAltarClientTile(TileEntitySpecialRenderer<?> renderer) {
        TileEntity tile = druidAltarClientTile;
        if (tile != null) {
            logSyntheticTile(renderer, tile);
            return tile;
        }

        try {
            Object instance = Class.forName(DRUID_ALTAR_TILE).newInstance();
            if (instance instanceof TileEntity) {
                tile = (TileEntity) instance;
                druidAltarClientTile = tile;
                logSyntheticTile(renderer, tile);
                return tile;
            }
            logSyntheticTileFailure(renderer, null);
        } catch (Throwable e) {
            logSyntheticTileFailure(renderer, e);
        }
        return null;
    }

    private static boolean renderWithSyntheticTile(TileEntitySpecialRenderer<?> renderer, ItemStack stack, double x, double y, double z,
                                                   float partialTicks, int destroyStage, float alpha) {
        TileEntitySpecialRenderer<?> targetRenderer = resolveConcreteRenderer(renderer, stack);
        if (targetRenderer == null) {
            targetRenderer = renderer;
        }

        TileEntity tile = getSyntheticClientTile(targetRenderer, stack);
        if (tile == null) {
            return false;
        }

        prepareSyntheticClientTile(tile, stack);
        renderOriginal(targetRenderer, tile, x, y, z, partialTicks, destroyStage, alpha);
        logGenericSyntheticTile(targetRenderer, tile, stack);
        return true;
    }

    private static TileEntitySpecialRenderer<?> resolveConcreteRenderer(TileEntitySpecialRenderer<?> renderer, ItemStack stack) {
        try {
            Object delegate = getLazyRendererDelegate(renderer);
            if (delegate instanceof TileEntitySpecialRenderer) {
                return (TileEntitySpecialRenderer<?>) delegate;
            }
        } catch (Throwable throwable) {
            logDelegateResolutionFailure(renderer, stack, throwable);
        }

        String rendererClassName = lazyRendererClassName(renderer);
        if (rendererClassName == null) {
            return renderer;
        }
        if (!isSafeBetweenlandsRendererClassName(rendererClassName)) {
            logDelegateResolutionFailure(renderer, stack, null);
            return renderer;
        }

        try {
            TileEntitySpecialRenderer<?> cached = RENDERER_BY_CLASS_NAME.get(rendererClassName);
            if (cached != null) {
                return cached;
            }
            Object instance = Class.forName(rendererClassName, true, renderer.getClass().getClassLoader()).newInstance();
            if (!(instance instanceof TileEntitySpecialRenderer)) {
                logDelegateResolutionFailure(renderer, stack, null);
                return renderer;
            }
            TileEntitySpecialRenderer<?> resolved = (TileEntitySpecialRenderer<?>) instance;
            attachRendererDispatcher(resolved);
            TileEntitySpecialRenderer<?> existing = RENDERER_BY_CLASS_NAME.putIfAbsent(rendererClassName, resolved);
            TileEntitySpecialRenderer<?> result = existing == null ? resolved : existing;
            logResolvedGenericRenderer(renderer, result, stack);
            return result;
        } catch (Throwable throwable) {
            logDelegateResolutionFailure(renderer, stack, throwable);
            return renderer;
        }
    }

    private static boolean isSafeBetweenlandsRendererClassName(String className) {
        return className != null
                && className.startsWith("thebetweenlands.client.render.tile.")
                && className.indexOf('/') < 0
                && className.indexOf('\\') < 0
                && className.indexOf("..") < 0;
    }

    private static String lazyRendererClassName(TileEntitySpecialRenderer<?> renderer) {
        try {
            Object value = fieldValue(renderer, "rendererClassName");
            return value instanceof String ? (String) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static TileEntity getSyntheticClientTile(TileEntitySpecialRenderer<?> renderer, ItemStack stack) {
        Class<?> rendererClass = renderer.getClass();
        TileEntity cached = CLIENT_TILE_BY_RENDERER.get(rendererClass);
        if (cached != null) {
            return cached;
        }

        Class<?> tileClass = findRenderedTileClass(rendererClass);
        if (tileClass == null || tileClass == TileEntity.class || !TileEntity.class.isAssignableFrom(tileClass)) {
            logGenericSyntheticTileFailure(renderer, stack, null, "could not infer renderer tile type");
            return null;
        }

        try {
            TileEntity tile = (TileEntity) tileClass.newInstance();
            TileEntity existing = CLIENT_TILE_BY_RENDERER.putIfAbsent(rendererClass, tile);
            return existing == null ? tile : existing;
        } catch (Throwable throwable) {
            logGenericSyntheticTileFailure(renderer, stack, throwable, "could not instantiate " + tileClass.getName());
            return null;
        }
    }

    private static void prepareSyntheticClientTile(TileEntity tile, ItemStack stack) {
        try {
            setTilePos(tile, new BlockPos(0, 0, 0));
            World world = clientWorld();
            if (world != null) {
                setTileWorld(tile, world);
            }
            setFieldValue(tile, Integer.valueOf(0), "blockMetadata", "field_145847_g");
            logPreparedSyntheticTile(tile, stack, world != null);
        } catch (Throwable throwable) {
            logPrepareSyntheticTileFailure(tile, stack, throwable);
        }
    }

    private static World clientWorld() {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null) {
                return null;
            }
            Object world = fieldValue(minecraft, "world", "field_71441_e");
            return world instanceof World ? (World) world : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void setTilePos(TileEntity tile, BlockPos pos) throws ReflectiveOperationException {
        Method method = tileEntitySetPos;
        if (method == null) {
            method = findMethod(TileEntity.class, new String[] {"func_174878_a", "setPos"}, BlockPos.class);
            tileEntitySetPos = method;
        }
        if (method != null) {
            method.invoke(tile, pos);
        }
    }

    private static void setTileWorld(TileEntity tile, World world) throws ReflectiveOperationException {
        Method method = tileEntitySetWorld;
        if (method == null) {
            method = findMethod(TileEntity.class, new String[] {"func_145834_a", "setWorld", "setWorldObj"}, World.class);
            tileEntitySetWorld = method;
        }
        if (method != null) {
            method.invoke(tile, world);
        }
    }

    private static Class<?> findRenderedTileClass(Class<?> rendererClass) {
        Class<?> current = rendererClass;
        while (current != null) {
            Type genericSuperclass = current.getGenericSuperclass();
            Class<?> tileClass = tileClassFromType(genericSuperclass);
            if (tileClass != null) {
                return tileClass;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Class<?> tileClassFromType(Type type) {
        if (!(type instanceof ParameterizedType)) {
            return null;
        }

        ParameterizedType parameterizedType = (ParameterizedType) type;
        Type rawType = parameterizedType.getRawType();
        if (!(rawType instanceof Class) || !TileEntitySpecialRenderer.class.isAssignableFrom((Class<?>) rawType)) {
            return null;
        }

        Type[] arguments = parameterizedType.getActualTypeArguments();
        if (arguments.length != 1 || !(arguments[0] instanceof Class)) {
            return null;
        }
        return (Class<?>) arguments[0];
    }

    private static void renderOriginal(TileEntitySpecialRenderer<?> renderer, TileEntity tile, double x, double y, double z,
                                       float partialTicks, int destroyStage, float alpha) {
        renderOriginal(renderer, tile, null, x, y, z, partialTicks, destroyStage, alpha);
    }

    private static void renderOriginal(TileEntitySpecialRenderer<?> renderer, TileEntity tile, ItemStack stack, double x, double y, double z,
                                       float partialTicks, int destroyStage, float alpha) {
        try {
            renderVirtual(renderer, tile, x, y, z, partialTicks, destroyStage, alpha);
        } catch (Throwable e) {
            logRenderFailure(renderer, tile, stack, e);
            throwUnchecked(e);
        }
    }

    private static void renderVirtual(TileEntitySpecialRenderer<?> renderer, TileEntity tile, double x, double y, double z,
                                      float partialTicks, int destroyStage, float alpha) throws ReflectiveOperationException {
        Method method = TESR_RENDER_METHODS.computeIfAbsent(renderer.getClass(), type ->
                findMethod(type, new String[] {"func_192841_a", "render"},
                        TileEntity.class, double.class, double.class, double.class, float.class, int.class, float.class));
        if (method == null) {
            throw new NoSuchMethodException(renderer.getClass().getName() + ".func_192841_a/render");
        }
        pushRenderState();
        try {
            method.invoke(renderer, tile, x, y, z, partialTicks, destroyStage, alpha);
        } finally {
            popRenderState();
        }
    }

    private static void pushRenderState() {
        try {
            GlStateManager.pushAttrib();
        } catch (Throwable ignored) {
        }
        try {
            GlStateManager.pushMatrix();
        } catch (Throwable ignored) {
        }
    }

    private static void popRenderState() {
        try {
            GlStateManager.popMatrix();
        } catch (Throwable ignored) {
        }
        try {
            GlStateManager.popAttrib();
        } catch (Throwable ignored) {
        }
        restoreGuiRenderBaseline();
    }

    private static void restoreGuiRenderBaseline() {
        try {
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        } catch (Throwable ignored) {
        }
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        } catch (Throwable ignored) {
        }
        try {
            GlStateManager.enableTexture2D();
        } catch (Throwable ignored) {
        }
        try {
            GlStateManager.colorMask(true, true, true, true);
        } catch (Throwable ignored) {
        }
        try {
            GlStateManager.resetColor();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        } catch (Throwable ignored) {
            try {
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            } catch (Throwable ignoredAgain) {
            }
        }
    }

    private static void logUnwrappedRenderer(TileEntitySpecialRenderer<?> wrapper, TileEntitySpecialRenderer<?> delegate) {
        String key = "unwrap-druid-altar:" + wrapper.getClass().getName();
        if (LOGGED_RENDER_DECISIONS.add(key)) {
            GPOM.LOGGER.info("[Betweenlands Item Renderer] Unwrapped {} to {} for thebetweenlands:druid_altar item rendering",
                    wrapper.getClass().getName(), delegate.getClass().getName());
        }
    }

    private static void logSyntheticTile(TileEntitySpecialRenderer<?> renderer, TileEntity tile) {
        String key = "synthetic-druid-altar:" + renderer.getClass().getName();
        if (LOGGED_RENDER_DECISIONS.add(key)) {
            GPOM.LOGGER.info("[Betweenlands Item Renderer] Rendering thebetweenlands:druid_altar through {} with client-only {} instead of a null TESR tile",
                    renderer.getClass().getName(), tile.getClass().getName());
        }
    }

    private static void logSyntheticTileFailure(TileEntitySpecialRenderer<?> renderer, Throwable throwable) {
        String key = "synthetic-druid-altar-failed:" + renderer.getClass().getName();
        if (!LOGGED_RENDER_DECISIONS.add(key)) {
            return;
        }
        if (throwable == null) {
            GPOM.LOGGER.warn("[Betweenlands Item Renderer] Could not instantiate {} for thebetweenlands:druid_altar rendered by {}",
                    DRUID_ALTAR_TILE, renderer.getClass().getName());
        } else {
            GPOM.LOGGER.warn("[Betweenlands Item Renderer] Could not instantiate {} for thebetweenlands:druid_altar rendered by {}",
                    DRUID_ALTAR_TILE, renderer.getClass().getName(), throwable);
        }
    }

    private static void logGenericSyntheticTile(TileEntitySpecialRenderer<?> renderer, TileEntity tile, ItemStack stack) {
        String itemId = stackRegistryName(stack);
        String key = "synthetic-generic:" + renderer.getClass().getName() + ":" + itemId;
        if (LOGGED_RENDER_DECISIONS.add(key)) {
            GPOM.LOGGER.info("[Betweenlands Item Renderer] Rendering {} through {} with client-only {} instead of a null TESR tile",
                    itemId, renderer.getClass().getName(), tile.getClass().getName());
        }
    }

    private static void logGenericSyntheticTileFailure(TileEntitySpecialRenderer<?> renderer, ItemStack stack, Throwable throwable, String reason) {
        String itemId = stackRegistryName(stack);
        String key = "synthetic-generic-failed:" + renderer.getClass().getName() + ":" + itemId + ":" + reason;
        if (!LOGGED_RENDER_DECISIONS.add(key)) {
            return;
        }
        if (throwable == null) {
            GPOM.LOGGER.warn("[Betweenlands Item Renderer] Could not create client-only TESR tile for {} rendered by {}: {}",
                    itemId, renderer.getClass().getName(), reason);
        } else {
            GPOM.LOGGER.warn("[Betweenlands Item Renderer] Could not create client-only TESR tile for {} rendered by {}: {}",
                    itemId, renderer.getClass().getName(), reason, throwable);
        }
    }

    private static void logPreparedSyntheticTile(TileEntity tile, ItemStack stack, boolean hasWorld) {
        String itemId = stackRegistryName(stack);
        String key = "synthetic-generic-prepared:" + tile.getClass().getName() + ":" + itemId + ":" + hasWorld;
        if (LOGGED_RENDER_DECISIONS.add(key)) {
            GPOM.LOGGER.info("[Betweenlands Item Renderer] Prepared client-only {} for {} item rendering with world={} and metadata=0",
                    tile.getClass().getName(), itemId, hasWorld);
        }
    }

    private static void logPrepareSyntheticTileFailure(TileEntity tile, ItemStack stack, Throwable throwable) {
        String itemId = stackRegistryName(stack);
        String key = "synthetic-generic-prepare-failed:" + tile.getClass().getName() + ":" + itemId;
        if (LOGGED_RENDER_DECISIONS.add(key)) {
            GPOM.LOGGER.warn("[Betweenlands Item Renderer] Could not prepare client-only {} for {} item rendering",
                    tile.getClass().getName(), itemId, throwable);
        }
    }

    private static void logResolvedGenericRenderer(TileEntitySpecialRenderer<?> wrapper, TileEntitySpecialRenderer<?> renderer, ItemStack stack) {
        String itemId = stackRegistryName(stack);
        String key = "synthetic-generic-renderer:" + wrapper.getClass().getName() + ":" + renderer.getClass().getName() + ":" + itemId;
        if (LOGGED_RENDER_DECISIONS.add(key)) {
            GPOM.LOGGER.info("[Betweenlands Item Renderer] Resolved {} to {} for {} synthetic TESR item rendering",
                    wrapper.getClass().getName(), renderer.getClass().getName(), itemId);
        }
    }

    private static void logDelegateResolutionFailure(TileEntitySpecialRenderer<?> renderer, ItemStack stack, Throwable throwable) {
        String itemId = stackRegistryName(stack);
        String key = "synthetic-generic-delegate-failed:" + renderer.getClass().getName() + ":" + itemId;
        if (!LOGGED_RENDER_DECISIONS.add(key)) {
            return;
        }
        if (throwable == null) {
            GPOM.LOGGER.warn("[Betweenlands Item Renderer] Could not resolve concrete renderer behind {} for {}",
                    renderer.getClass().getName(), itemId);
        } else {
            GPOM.LOGGER.warn("[Betweenlands Item Renderer] Could not resolve concrete renderer behind {} for {}",
                    renderer.getClass().getName(), itemId, throwable);
        }
    }

    private static void logRenderFailure(TileEntitySpecialRenderer<?> renderer, TileEntity tile, ItemStack stack, Throwable throwable) {
        String rendererName = renderer == null ? "<null>" : renderer.getClass().getName();
        String tileName = tile == null ? "<null>" : tile.getClass().getName();
        String itemId = stackRegistryName(stack);
        String key = "render-failed:" + rendererName + ":" + tileName + ":" + itemId;
        if (LOGGED_RENDER_DECISIONS.add(key)) {
            GPOM.LOGGER.warn("[Betweenlands Item Renderer] Renderer failed for item={}, renderer={}, tile={}. This path preserves the original Betweenlands item-render null tile contract unless explicitly special-cased.",
                    itemId, rendererName, tileName, throwable);
        }
    }

    private static String stackRegistryName(ItemStack stack) {
        try {
            if (stack == null || isStackEmpty(stack)) {
                return "<empty>";
            }
            Object item = getItem(stack);
            if (item instanceof net.minecraft.item.Item) {
                ResourceLocation registryName = ((net.minecraft.item.Item) item).getRegistryName();
                if (registryName != null) {
                    return registryName.toString();
                }
            }
        } catch (Throwable ignored) {
        }
        return String.valueOf(stack);
    }

    private static void logRenderDecision(String key, boolean usedDedicatedRenderer) {
        if (!LOGGED_RENDER_DECISIONS.add(key)) {
            return;
        }
        if (usedDedicatedRenderer) {
            GPOM.LOGGER.info("[Betweenlands Item Renderer] Rendering thebetweenlands:druid_altar with RenderDruidAltar.renderTileAsItem instead of a null TESR tile");
        } else {
            GPOM.LOGGER.warn("[Betweenlands Item Renderer] Could not route thebetweenlands:druid_altar to RenderDruidAltar.renderTileAsItem; falling back to the original TESR item render");
        }
    }

    private static void throwUnchecked(Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        }
        if (throwable instanceof Error) {
            throw (Error) throwable;
        }
        throw new RuntimeException(throwable);
    }

    private static boolean isStackEmpty(ItemStack stack) throws ReflectiveOperationException {
        Method method = itemStackIsEmpty;
        if (method == null) {
            method = findMethod(stack.getClass(), "func_190926_b", "isEmpty");
            itemStackIsEmpty = method;
        }
        return method != null && Boolean.TRUE.equals(method.invoke(stack));
    }

    private static Object getItem(ItemStack stack) throws ReflectiveOperationException {
        Method method = itemStackGetItem;
        if (method == null) {
            method = findMethod(stack.getClass(), "func_77973_b", "getItem");
            itemStackGetItem = method;
        }
        return method == null ? null : method.invoke(stack);
    }

    private static Method findMethod(Class<?> type, String... names) {
        return findMethod(type, names, new Class<?>[0]);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        return findMethod(type, new String[] {name}, parameterTypes);
    }

    private static Method findMethod(Class<?> type, String[] names, Class<?>... parameterTypes) {
        Class<?> current = type;
        while (current != null) {
            for (String name : names) {
                try {
                    Method method = current.getDeclaredMethod(name, parameterTypes);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
