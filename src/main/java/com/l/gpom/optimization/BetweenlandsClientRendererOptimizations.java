package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.core.TargetedModVersions;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

public final class BetweenlandsClientRendererOptimizations {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("gpom.betweenlands.lazyClientEntityRenderers", "true"));
    private static final boolean TILE_RENDERERS_ENABLED = Boolean.parseBoolean(System.getProperty("gpom.betweenlands.lazyClientTileRenderers", "true"));
    private static final String[][] ENTITY_RENDERERS = {
            {"thebetweenlands.common.entity.mobs.EntityAngler", "thebetweenlands.client.render.entity.RenderAngler", "false"},
            {"thebetweenlands.common.entity.mobs.EntityOlm", "thebetweenlands.client.render.entity.RenderOlm", "false"},
            {"thebetweenlands.common.entity.mobs.EntityMireSnail", "thebetweenlands.client.render.entity.RenderMireSnail", "false"},
            {"thebetweenlands.common.entity.mobs.EntityMireSnailEgg", "thebetweenlands.client.render.entity.RenderMireSnailEgg", "false"},
            {"thebetweenlands.common.entity.mobs.EntityBloodSnail", "thebetweenlands.client.render.entity.RenderBloodSnail", "false"},
            {"thebetweenlands.common.entity.projectiles.EntitySnailPoisonJet", "thebetweenlands.client.render.entity.RenderSnailPoisonJet", "false"},
            {"thebetweenlands.common.entity.mobs.EntitySwampHag", "thebetweenlands.client.render.entity.RenderSwampHag", "false"},
            {"thebetweenlands.common.entity.mobs.EntityChiromaw", "thebetweenlands.client.render.entity.RenderChiromaw", "false"},
            {"thebetweenlands.common.entity.mobs.EntityDragonFly", "thebetweenlands.client.render.entity.RenderDragonFly", "false"},
            {"thebetweenlands.common.entity.mobs.EntityLurker", "thebetweenlands.client.render.entity.RenderLurker", "false"},
            {"thebetweenlands.common.entity.mobs.EntityFrog", "thebetweenlands.client.render.entity.RenderFrog", "false"},
            {"thebetweenlands.common.entity.mobs.EntityGiantToad", "thebetweenlands.client.render.entity.RenderGiantToad", "false"},
            {"thebetweenlands.common.entity.mobs.EntitySporeling", "thebetweenlands.client.render.entity.RenderSporeling", "false"},
            {"thebetweenlands.common.entity.mobs.EntityTermite", "thebetweenlands.client.render.entity.RenderTermite", "false"},
            {"thebetweenlands.common.entity.mobs.EntityLeech", "thebetweenlands.client.render.entity.RenderLeech", "false"},
            {"thebetweenlands.common.entity.EntitySwordEnergy", "thebetweenlands.client.render.entity.RenderSwordEnergy", "false"},
            {"thebetweenlands.common.entity.EntityShockwaveBlock", "thebetweenlands.client.render.entity.RenderShockwaveBlock", "false"},
            {"thebetweenlands.common.entity.mobs.EntityGecko", "thebetweenlands.client.render.entity.RenderGecko", "false"},
            {"thebetweenlands.common.entity.mobs.EntityWight", "thebetweenlands.client.render.entity.RenderWight", "false"},
            {"thebetweenlands.common.entity.EntityShockwaveSwordItem", "thebetweenlands.client.render.entity.RenderShockwaveSwordItem", "true"},
            {"thebetweenlands.common.entity.mobs.EntityFirefly", "thebetweenlands.client.render.entity.RenderFirefly", "false"},
            {"thebetweenlands.common.entity.mobs.EntityGasCloud", "thebetweenlands.client.render.entity.RenderGasCloud", "false"},
            {"thebetweenlands.common.entity.mobs.EntitySludge", "thebetweenlands.client.render.entity.RenderSludge", "false"},
            {"thebetweenlands.common.entity.projectiles.EntityBLArrow", "thebetweenlands.client.render.entity.RenderBLArrow", "false"},
            {"thebetweenlands.common.entity.mobs.EntityDarkDruid", "thebetweenlands.client.render.entity.RenderDarkDruid", "false"},
            {"thebetweenlands.common.entity.mobs.EntityVolatileSoul", "thebetweenlands.client.render.entity.RenderVolatileSoul", "false"},
            {"thebetweenlands.common.entity.mobs.EntityTarBeast", "thebetweenlands.client.render.entity.RenderTarBeast", "false"},
            {"thebetweenlands.common.entity.mobs.EntitySiltCrab", "thebetweenlands.client.render.entity.RenderSiltCrab", "false"},
            {"thebetweenlands.common.entity.mobs.EntityPyrad", "thebetweenlands.client.render.entity.RenderPyrad", "false"},
            {"thebetweenlands.common.entity.projectiles.EntityPyradFlame", "thebetweenlands.client.render.entity.RenderPyradFlame", "false"},
            {"thebetweenlands.common.entity.mobs.EntityPeatMummy", "thebetweenlands.client.render.entity.RenderPeatMummy", "false"},
            {"thebetweenlands.common.entity.mobs.EntityTarminion", "thebetweenlands.client.render.entity.RenderTarminion", "false"},
            {"thebetweenlands.common.entity.projectiles.EntityThrownTarminion", "thebetweenlands.client.render.entity.RenderThrownTarminion", "false"},
            {"thebetweenlands.common.entity.EntityRopeNode", "thebetweenlands.client.render.entity.RenderRopeNode", "false"},
            {"thebetweenlands.common.entity.EntityGrapplingHookNode", "thebetweenlands.client.render.entity.RenderGrapplingHookNode", "false"},
            {"thebetweenlands.common.entity.mobs.EntityMummyArm", "thebetweenlands.client.render.entity.RenderMummyArm", "false"},
            {"thebetweenlands.common.entity.projectiles.EntityAngryPebble", "thebetweenlands.client.render.entity.RenderAngryPebble", "true"},
            {"thebetweenlands.common.entity.mobs.EntityFortressBoss", "thebetweenlands.client.render.entity.RenderFortressBoss", "false"},
            {"thebetweenlands.common.entity.mobs.EntityFortressBossSpawner", "thebetweenlands.client.render.entity.RenderFortressBossSpawner", "false"},
            {"thebetweenlands.common.entity.mobs.EntityFortressBossBlockade", "thebetweenlands.client.render.entity.RenderFortressBossBlockade", "false"},
            {"thebetweenlands.common.entity.mobs.EntityFortressBossProjectile", "thebetweenlands.client.render.entity.RenderFortressBossProjectile", "false"},
            {"thebetweenlands.common.entity.mobs.EntityFortressBossTurret", "thebetweenlands.client.render.entity.RenderFortressBossTurret", "false"},
            {"thebetweenlands.common.entity.mobs.EntityFortressBossTeleporter", "thebetweenlands.client.render.entity.RenderFortressBossTeleporter", "false"},
            {"thebetweenlands.common.entity.rowboat.EntityWeedwoodRowboat", "thebetweenlands.client.render.entity.RenderWeedwoodRowboat", "false"},
            {"thebetweenlands.common.entity.projectiles.EntityElixir", "thebetweenlands.client.render.entity.RenderElixir", "false"},
            {"thebetweenlands.common.entity.mobs.EntityDreadfulMummy", "thebetweenlands.client.render.entity.RenderDreadfulMummy", "false"},
            {"thebetweenlands.common.entity.projectiles.EntitySludgeBall", "thebetweenlands.client.render.entity.RenderSludgeBall", "false"},
            {"thebetweenlands.common.entity.mobs.EntityDarkLight", "thebetweenlands.client.render.entity.RenderDarkLight", "false"},
            {"thebetweenlands.common.entity.mobs.EntitySporeJet", "thebetweenlands.client.render.entity.RenderSporeJet", "false"},
            {"thebetweenlands.common.entity.mobs.EntitySmollSludge", "thebetweenlands.client.render.entity.RenderSmollSludge", "false"},
            {"thebetweenlands.common.entity.mobs.EntityGreebling", "thebetweenlands.client.render.entity.RenderGreebling", "false"},
            {"thebetweenlands.common.entity.EntityVolarkite", "thebetweenlands.client.render.entity.RenderVolarkite", "false"},
            {"thebetweenlands.common.entity.mobs.EntityBoulderSprite", "thebetweenlands.client.render.entity.RenderBoulderSprite", "false"},
            {"thebetweenlands.common.entity.mobs.EntitySpiritTreeFaceSmallBase", "thebetweenlands.client.render.entity.RenderSpiritTreeFaceSmall", "false"},
            {"thebetweenlands.common.entity.mobs.EntitySpiritTreeFaceLarge", "thebetweenlands.client.render.entity.RenderSpiritTreeFaceLarge", "false"},
            {"thebetweenlands.common.entity.mobs.EntityTamedSpiritTreeFace", "thebetweenlands.client.render.entity.RenderSpiritTreeFaceSmall", "false"},
            {"thebetweenlands.common.entity.projectiles.EntitySapSpit", "thebetweenlands.client.render.entity.RenderSapSpit", "false"},
            {"thebetweenlands.common.entity.EntitySpikeWave", "thebetweenlands.client.render.entity.RenderSpikeWave", "false"},
            {"thebetweenlands.common.entity.EntityRootGrabber", "thebetweenlands.client.render.entity.RenderRootGrabber", "false"},
            {"thebetweenlands.common.entity.EntitySpiritTreeFaceMask", "thebetweenlands.client.render.entity.RenderSpiritTreeFaceMask", "false"},
            {"thebetweenlands.common.entity.mobs.EntityRootSprite", "thebetweenlands.client.render.entity.RenderRootSprite", "false"},
            {"thebetweenlands.common.entity.mobs.EntitySludgeWorm", "thebetweenlands.client.render.entity.RenderSludgeWorm", "false"},
            {"thebetweenlands.common.entity.mobs.EntityLargeSludgeWorm", "thebetweenlands.client.render.entity.RenderLargeSludgeWorm", "false"},
            {"thebetweenlands.common.entity.mobs.EntityTinySludgeWorm", "thebetweenlands.client.render.entity.RenderTinySludgeWorm", "false"},
            {"thebetweenlands.common.entity.EntityTinyWormEggSac", "thebetweenlands.client.render.entity.RenderTinyWormEggSac", "false"},
            {"thebetweenlands.common.entity.EntityLurkerSkinRaft", "thebetweenlands.client.render.entity.RenderLurkerSkinRaft", "false"},
            {"thebetweenlands.common.entity.mobs.EntityShambler", "thebetweenlands.client.render.entity.RenderShambler", "false"},
            {"thebetweenlands.common.entity.mobs.EntityWallLamprey", "thebetweenlands.client.render.entity.RenderWallLamprey", "false"},
            {"thebetweenlands.common.entity.mobs.EntityWallLivingRoot", "thebetweenlands.client.render.entity.RenderWallLivingRoot", "false"},
            {"thebetweenlands.common.entity.mobs.EntitySludgeMenace", "thebetweenlands.client.render.entity.RenderSludgeMenace", "false"},
            {"thebetweenlands.common.entity.mobs.EntityMovingSpawnerHole", "thebetweenlands.client.render.entity.RenderMovingSpawnerHole", "false"},
            {"thebetweenlands.common.entity.mobs.EntityCryptCrawler", "thebetweenlands.client.render.entity.RenderCryptCrawler", "false"},
            {"thebetweenlands.common.entity.mobs.EntityBarrishee", "thebetweenlands.client.render.entity.RenderBarrishee", "false"},
            {"thebetweenlands.common.entity.mobs.EntityAshSprite", "thebetweenlands.client.render.entity.RenderAshSprite", "false"},
            {"thebetweenlands.common.entity.EntityDecayPitTarget", "thebetweenlands.client.render.entity.RenderDecayPitTarget", "false"},
            {"thebetweenlands.common.entity.mobs.EntitySludgeJet", "thebetweenlands.client.render.entity.RenderSludgeJet", "false"},
            {"thebetweenlands.common.entity.EntityTriggeredFallingBlock", "thebetweenlands.client.render.entity.RenderTriggeredFallingBlock", "false"},
            {"thebetweenlands.common.entity.projectiles.EntitySludgeWallJet", "thebetweenlands.client.render.entity.RenderSludgeWallJet", "false"},
            {"thebetweenlands.common.entity.EntityTriggeredSludgeWallJet", "thebetweenlands.client.render.entity.RenderTriggeredSludgeWallJet", "false"},
            {"thebetweenlands.common.entity.EntitySplodeshroom", "thebetweenlands.client.render.entity.RenderSplodeshroom", "false"},
            {"thebetweenlands.common.entity.projectiles.EntityPredatorArrowGuide", "thebetweenlands.client.render.entity.RenderPredatorArrowGuide", "false"},
            {"thebetweenlands.common.entity.EntityCCGroundSpawner", "thebetweenlands.client.render.entity.RenderCCGroundSpawner", "false"},
            {"thebetweenlands.common.entity.mobs.EntityEmberlingShaman", "thebetweenlands.client.render.entity.RenderEmberlingShaman", "false"},
            {"thebetweenlands.common.entity.mobs.EntityFlameJet", "thebetweenlands.client.render.entity.RenderFlameJet", "false"},
            {"thebetweenlands.common.entity.EntityMovingWall", "thebetweenlands.client.render.entity.RenderMovingWall", "false"},
            {"thebetweenlands.common.entity.mobs.EntityEmberling", "thebetweenlands.client.render.entity.RenderEmberling", "false"},
            {"thebetweenlands.common.entity.EntityGalleryFrame", "thebetweenlands.client.render.entity.RenderGalleryFrame", "false"},
            {"thebetweenlands.common.entity.mobs.EntityMultipartDummy", "thebetweenlands.client.render.entity.RenderMultipartDummy", "false"},
            {"thebetweenlands.common.entity.mobs.EntityEmberlingWild", "thebetweenlands.client.render.entity.RenderEmberlingWild", "false"},
            {"thebetweenlands.common.entity.draeton.EntityDraeton", "thebetweenlands.client.render.entity.RenderDraeton", "false"},
            {"thebetweenlands.common.entity.mobs.EntityChiromawGreeblingRider", "thebetweenlands.client.render.entity.RenderChiromawGreeblingRider", "false"},
            {"thebetweenlands.common.entity.mobs.EntityGreeblingVolarpadFloater", "thebetweenlands.client.render.entity.RenderGreeblingVolarpadFloater", "false"},
            {"thebetweenlands.common.entity.projectiles.EntityBetweenstonePebble", "thebetweenlands.client.render.entity.RenderBetweenstonePebbleProjectile", "false"},
            {"thebetweenlands.common.entity.mobs.EntityChiromawMatriarch", "thebetweenlands.client.render.entity.RenderChiromawMatriarch", "false"},
            {"thebetweenlands.common.entity.projectiles.EntityChiromawDroppings", "thebetweenlands.client.render.entity.RenderChiromawDroppings", "false"},
            {"thebetweenlands.common.entity.EntityShock", "thebetweenlands.client.render.entity.RenderNothing", "false"},
            {"thebetweenlands.common.entity.EntityBLLightningBolt", "thebetweenlands.client.render.entity.RenderNothing", "false"},
            {"thebetweenlands.common.entity.mobs.EntityChiromawHatchling", "thebetweenlands.client.render.entity.RenderChiromawHatchling", "false"},
            {"thebetweenlands.common.entity.mobs.EntityChiromawTame", "thebetweenlands.client.render.entity.RenderChiromawTame", "false"},
            {"thebetweenlands.common.entity.EntityGreeblingCorpse", "thebetweenlands.client.render.entity.RenderGreeblingCorpse", "false"},
            {"thebetweenlands.common.entity.mobs.EntityAnadia", "thebetweenlands.client.render.entity.RenderAnadia", "false"},
            {"thebetweenlands.common.entity.projectiles.EntityBLFishHook", "thebetweenlands.client.render.entity.RenderBLFishHook", "false"},
            {"thebetweenlands.common.entity.projectiles.EntityFishingSpear", "thebetweenlands.client.render.entity.RenderFishingSpear", "false"},
            {"thebetweenlands.common.entity.EntityFishingTackleBoxSeat", "thebetweenlands.client.render.entity.RenderNothing", "false"},
            {"thebetweenlands.common.entity.mobs.EntityStalker", "thebetweenlands.client.render.entity.RenderStalker", "false"},
            {"thebetweenlands.common.entity.EntityResurrection", "thebetweenlands.client.render.entity.RenderNothing", "false"},
            {"thebetweenlands.common.entity.EntityFalseXPOrb", "net.minecraft.client.renderer.entity.RenderXPOrb", "false"},
            {"thebetweenlands.common.entity.mobs.EntitySwarm", "thebetweenlands.client.render.entity.RenderSwarm", "false"},
            {"thebetweenlands.common.entity.mobs.EntityGreeblingCoracle", "thebetweenlands.client.render.entity.RenderGreeblingCoracle", "false"},
            {"thebetweenlands.common.entity.EntityFishVortex", "thebetweenlands.client.render.entity.RenderNothing", "false"},
            {"thebetweenlands.common.entity.mobs.EntityRockSnot", "thebetweenlands.client.render.entity.RenderRockSnot", "false"},
            {"thebetweenlands.common.entity.mobs.EntityRockSnotTendril", "thebetweenlands.client.render.entity.RenderRockSnotTendril", "false"},
            {"thebetweenlands.common.entity.mobs.EntityPuffin", "thebetweenlands.client.render.entity.RenderPuffin", "false"},
            {"thebetweenlands.common.entity.mobs.EntityJellyfish", "thebetweenlands.client.render.entity.RenderJellyfish", "false"},
            {"thebetweenlands.common.entity.mobs.EntityJellyfishCave", "thebetweenlands.client.render.entity.RenderJellyfishCave", "false"},
            {"thebetweenlands.common.entity.mobs.EntityBubblerCrab", "thebetweenlands.client.render.entity.RenderBubblerCrab", "false"},
            {"thebetweenlands.common.entity.mobs.EntityFreshwaterUrchin", "thebetweenlands.client.render.entity.RenderFreshwaterUrchin", "false"},
            {"thebetweenlands.common.entity.projectiles.EntityBubblerCrabBubble", "thebetweenlands.client.render.entity.RenderBubblerCrabBubble", "false"},
            {"thebetweenlands.common.entity.mobs.EntityCaveFish", "thebetweenlands.client.render.entity.RenderCaveFish", "false"},
            {"thebetweenlands.common.entity.projectiles.EntityGlowingGoop", "thebetweenlands.client.render.entity.RenderGlowingGoop", "true"},
            {"thebetweenlands.common.entity.EntityMistBridge", "thebetweenlands.client.render.entity.RenderNothing", "false"},
    };

    private BetweenlandsClientRendererOptimizations() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerEntityRenderers() {
        long startedAt = StartupProfiler.beginProbe();
        try {
            if (!ENABLED || !TargetedModVersions.isBetweenlandsClass("thebetweenlands.client.proxy.ClientProxy")) {
                return;
            }

            ClassLoader loader = BetweenlandsClientRendererOptimizations.class.getClassLoader();
            for (String[] plan : ENTITY_RENDERERS) {
                Class<?> entityClass = Class.forName(plan[0], false, loader);
                RenderingRegistry.registerEntityRenderingHandler(
                        (Class<? extends Entity>) entityClass,
                        new LazyRenderFactory(plan[1], Boolean.parseBoolean(plan[2]), loader)
                );
            }
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[StartupProfiler] Betweenlands lazy entity renderer registration failed", throwable);
            throw new RuntimeException("Failed to register Betweenlands lazy entity renderers", throwable);
        } finally {
            StartupProfiler.endProbeAlways("BL ClientProxy.preInit lazy entity renderer manifest", startedAt);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerTileEntityRenderer(String tileClassName, String rendererClassName) {
        if (!TILE_RENDERERS_ENABLED || !TargetedModVersions.isBetweenlandsClass("thebetweenlands.client.proxy.ClientProxy")) {
            return;
        }

        try {
            ClassLoader loader = BetweenlandsClientRendererOptimizations.class.getClassLoader();
            Class<?> tileClass = Class.forName(tileClassName, false, loader);
            ClientRegistry.bindTileEntitySpecialRenderer(
                    (Class<? extends TileEntity>) tileClass,
                    new LazyTileEntitySpecialRenderer(rendererClassName, loader)
            );
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[StartupProfiler] Betweenlands lazy tile renderer registration failed for {} -> {}", tileClassName, rendererClassName, throwable);
            throw new RuntimeException("Failed to register Betweenlands lazy tile renderer " + rendererClassName, throwable);
        }
    }

    private static final class LazyRenderFactory implements IRenderFactory<Entity> {
        private static final ConcurrentHashMap<String, Constructor<?>> CONSTRUCTORS = new ConcurrentHashMap<>();
        private static volatile RenderItem renderItem;

        private final String renderClassName;
        private final boolean needsRenderItem;
        private final ClassLoader loader;

        private LazyRenderFactory(String renderClassName, boolean needsRenderItem, ClassLoader loader) {
            this.renderClassName = renderClassName;
            this.needsRenderItem = needsRenderItem;
            this.loader = loader;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Render<? super Entity> createRenderFor(RenderManager manager) {
            try {
                Constructor<?> constructor = CONSTRUCTORS.computeIfAbsent(renderClassName + '|' + needsRenderItem, ignored -> findConstructor());
                if (needsRenderItem) {
                    return (Render<? super Entity>) constructor.newInstance(manager, getRenderItem());
                }
                return (Render<? super Entity>) constructor.newInstance(manager);
            } catch (Throwable throwable) {
                throw new RuntimeException("Failed to create lazy Betweenlands renderer " + renderClassName, throwable);
            }
        }

        private static RenderItem getRenderItem() {
            RenderItem cached = renderItem;
            if (cached != null) {
                return cached;
            }
            try {
                Minecraft minecraft = invokeNoArgStatic(Minecraft.class, Minecraft.class, "getMinecraft", "func_71410_x");
                cached = invokeNoArg(minecraft, RenderItem.class, "getRenderItem", "func_175599_af");
                renderItem = cached;
                return cached;
            } catch (ReflectiveOperationException exception) {
                throw new RuntimeException("Failed to resolve Minecraft RenderItem for lazy Betweenlands renderer", exception);
            }
        }

        private static <T> T invokeNoArgStatic(Class<?> owner, Class<T> returnType, String... methodNames) throws ReflectiveOperationException {
            for (String methodName : methodNames) {
                try {
                    Method method = owner.getDeclaredMethod(methodName);
                    method.setAccessible(true);
                    return returnType.cast(method.invoke(null));
                } catch (NoSuchMethodException ignored) {
                    // Try the next runtime name. Cleanroom may expose either MCP or SRG names.
                }
            }
            throw new NoSuchMethodException(owner.getName() + '.' + String.join("/", methodNames));
        }

        private static <T> T invokeNoArg(Object owner, Class<T> returnType, String... methodNames) throws ReflectiveOperationException {
            Class<?> ownerClass = owner.getClass();
            for (String methodName : methodNames) {
                try {
                    Method method = ownerClass.getDeclaredMethod(methodName);
                    method.setAccessible(true);
                    return returnType.cast(method.invoke(owner));
                } catch (NoSuchMethodException ignored) {
                    // Try the next runtime name. Cleanroom may expose either MCP or SRG names.
                }
            }
            throw new NoSuchMethodException(ownerClass.getName() + '.' + String.join("/", methodNames));
        }

        private Constructor<?> findConstructor() {
            try {
                Class<?> renderClass = Class.forName(renderClassName, true, loader);
                Constructor<?> constructor = needsRenderItem
                        ? renderClass.getConstructor(RenderManager.class, RenderItem.class)
                        : renderClass.getConstructor(RenderManager.class);
                constructor.setAccessible(true);
                return constructor;
            } catch (Throwable throwable) {
                throw new RuntimeException("Failed to resolve lazy Betweenlands renderer constructor " + renderClassName, throwable);
            }
        }
    }

    private static final class LazyTileEntitySpecialRenderer extends TileEntitySpecialRenderer<TileEntity> {
        private static final ConcurrentHashMap<String, Constructor<?>> CONSTRUCTORS = new ConcurrentHashMap<>();

        private final String rendererClassName;
        private final ClassLoader loader;
        private volatile TileEntitySpecialRenderer<TileEntity> delegate;

        private LazyTileEntitySpecialRenderer(String rendererClassName, ClassLoader loader) {
            this.rendererClassName = rendererClassName;
            this.loader = loader;
        }

        @Override
        public void setRendererDispatcher(TileEntityRendererDispatcher rendererDispatcherIn) {
            super.setRendererDispatcher(rendererDispatcherIn);
            TileEntitySpecialRenderer<TileEntity> current = delegate;
            if (current != null) {
                current.setRendererDispatcher(rendererDispatcherIn);
            }
        }

        @Override
        public void render(TileEntity tileEntity, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
            getDelegate().render(tileEntity, x, y, z, partialTicks, destroyStage, alpha);
        }

        @Override
        public void renderTileEntityFast(TileEntity tileEntity, double x, double y, double z, float partialTicks, int destroyStage, float alpha, BufferBuilder buffer) {
            getDelegate().renderTileEntityFast(tileEntity, x, y, z, partialTicks, destroyStage, alpha, buffer);
        }

        @Override
        public boolean isGlobalRenderer(TileEntity tileEntity) {
            return getDelegate().isGlobalRenderer(tileEntity);
        }

        @SuppressWarnings("unchecked")
        private TileEntitySpecialRenderer<TileEntity> getDelegate() {
            TileEntitySpecialRenderer<TileEntity> current = delegate;
            if (current != null) {
                return current;
            }

            synchronized (this) {
                current = delegate;
                if (current != null) {
                    return current;
                }
                try {
                    Constructor<?> constructor = CONSTRUCTORS.computeIfAbsent(rendererClassName, ignored -> findConstructor());
                    current = (TileEntitySpecialRenderer<TileEntity>) constructor.newInstance();
                    current.setRendererDispatcher(rendererDispatcher);
                    delegate = current;
                    return current;
                } catch (Throwable throwable) {
                    throw new RuntimeException("Failed to create lazy Betweenlands tile renderer " + rendererClassName, throwable);
                }
            }
        }

        private Constructor<?> findConstructor() {
            try {
                Class<?> rendererClass = Class.forName(rendererClassName, true, loader);
                Constructor<?> constructor = rendererClass.asSubclass(TileEntitySpecialRenderer.class).getConstructor();
                constructor.setAccessible(true);
                return constructor;
            } catch (Throwable throwable) {
                throw new RuntimeException("Failed to resolve lazy Betweenlands tile renderer constructor " + rendererClassName, throwable);
            }
        }
    }
}
