package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.core.TargetedModVersions;
import com.l.gpom.util.ReflectionFields;
import com.google.common.collect.SetMultimap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.asm.ModAnnotation.EnumHolder;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class EnderIOConstructionOptimizations {
    private static final boolean ENABLED = Boolean.parseBoolean(
            System.getProperty("gpom.enderIOConstructionOptimizations", "true")
    );
    private static final Method EVENT_BUS_REGISTER_METHOD = findEventBusRegisterMethod();
    private static final ConcurrentHashMap<String, Boolean> FALLBACK_LOGS = new ConcurrentHashMap<>();
    private static final EnumSet<Side> DEFAULT_SIDES = EnumSet.allOf(Side.class);

    private static final String[][] ENDER_IO_SUBSCRIBERS = {
            {"crazypants.enderio.api.capacitor.CapabilityCapacitorData", "register", "crazypants.enderio.base.events.EnderIOLifecycleEvent$PreInit"},
            {"crazypants.enderio.base.Log", "preInit", "crazypants.enderio.base.events.EnderIOLifecycleEvent$Config$Post"},
            {"crazypants.enderio.base.TileEntityEio", "onServerTick", "net.minecraftforge.fml.common.gameevent.TickEvent$ServerTickEvent", "onClientTick", "net.minecraftforge.fml.common.gameevent.TickEvent$ClientTickEvent"},
            {"crazypants.enderio.base.autosave.BaseHandlers", "register", "crazypants.enderio.base.events.EnderIOLifecycleEvent$PreInit"},
            {"crazypants.enderio.base.block.charge.EntityPrimedCharge", "onEntityRegister", "net.minecraftforge.event.RegistryEvent$Register", "onPreInit", "crazypants.enderio.base.events.EnderIOLifecycleEvent$PreInit"},
            {"crazypants.enderio.base.block.coldfire.BlockColdFire", "onClick", "net.minecraftforge.event.entity.player.PlayerInteractEvent$LeftClickBlock"},
            {"crazypants.enderio.base.block.coldfire.ColdFireStateMapper", "init", "net.minecraftforge.client.event.ModelRegistryEvent"},
            {"crazypants.enderio.base.block.darksteel.anvil.BlockBrokenAnvil"},
            {"crazypants.enderio.base.block.darksteel.anvil.BlockDarkSteelAnvil"},
            {"crazypants.enderio.base.block.darksteel.door.DarkSteelDoorStateMapper", "init", "net.minecraftforge.client.event.ModelRegistryEvent"},
            {"crazypants.enderio.base.block.holy.HolyChunkData", "onSave", "net.minecraftforge.event.world.ChunkDataEvent$Save", "onLoad", "net.minecraftforge.event.world.ChunkDataEvent$Load"},
            {"crazypants.enderio.base.block.infinity.InfinityFogDropHandler", "onDrop", "net.minecraftforge.event.entity.living.LivingDropsEvent"},
            {"crazypants.enderio.base.block.lever.LeverStateMapper", "init", "net.minecraftforge.client.event.ModelRegistryEvent"},
            {"crazypants.enderio.base.block.painted.PaintedDoorStateMapper", "init", "net.minecraftforge.client.event.ModelRegistryEvent"},
            {"crazypants.enderio.base.capacitor.CapacitorKey", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.capacitor.CapacitorKeyRegistry", "registerRegistry", "net.minecraftforge.event.RegistryEvent$NewRegistry", "onPlayerLoggon", "net.minecraftforge.fml.common.gameevent.PlayerEvent$PlayerLoggedInEvent", "onPlayerLogout", "net.minecraftforge.fml.common.network.FMLNetworkEvent$ClientDisconnectionFromServerEvent"},
            {"crazypants.enderio.base.conduit.geom.ConduitGeometryUtil", "preInit", "crazypants.enderio.base.events.EnderIOLifecycleEvent$Config$Post"},
            {"crazypants.enderio.base.config.command.CommandConfig", "onStarting", "crazypants.enderio.base.events.EnderIOLifecycleEvent$ServerStarting$Dedicated"},
            {"crazypants.enderio.base.diagnostics.ModInterferenceWarner", "onEvent", "crazypants.enderio.base.events.EnderIOLifecycleEvent$ServerAboutToStart$Pre"},
            {"crazypants.enderio.base.enchantment.EnchantmentRepellent", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.enchantment.EnchantmentShimmer", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.enchantment.EnchantmentSoulBound", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.enchantment.EnchantmentWitherArrow", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.enchantment.EnchantmentWitherWeapon", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.enchantment.HandlerSoulBound", "onPlayerDeath", "net.minecraftforge.event.entity.player.PlayerDropsEvent", "onPlayerDeathLate", "net.minecraftforge.event.entity.player.PlayerDropsEvent", "onPlayerClone", "net.minecraftforge.event.entity.player.PlayerEvent$Clone", "onPlayerCloneLast", "net.minecraftforge.event.entity.player.PlayerEvent$Clone"},
            {"crazypants.enderio.base.events.ModSoundRegisterEvent", "registerSounds", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.farming.FarmersRegistry", "registerFarmers", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.farming.fertilizer.Fertilizer", "registerRegistry", "net.minecraftforge.event.RegistryEvent$NewRegistry", "registerFertilizer", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.farming.fertilizer.NoFertilizer", "registerFertilizer", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.farming.registry.Registry", "registerRegistry", "net.minecraftforge.event.RegistryEvent$NewRegistry"},
            {"crazypants.enderio.base.filter.capability.CapabilityFilterHolder", "create", "crazypants.enderio.base.events.EnderIOLifecycleEvent$PreInit"},
            {"crazypants.enderio.base.filter.recipes.FilterRecipes", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.fluid.Fluids", "registerFluids", "net.minecraftforge.event.RegistryEvent$Register", "registerBlocks", "net.minecraftforge.event.RegistryEvent$Register", "registerItems", "net.minecraftforge.event.RegistryEvent$Register", "registerRenderers", "net.minecraftforge.client.event.ModelRegistryEvent", "onIconLoad", "net.minecraftforge.client.event.TextureStitchEvent$Pre"},
            {"crazypants.enderio.base.gui.IoConfigRenderer", "init", "crazypants.enderio.base.events.EnderIOLifecycleEvent$PreInit"},
            {"crazypants.enderio.base.gui.handler.GuiHelper", "init", "crazypants.enderio.base.events.EnderIOLifecycleEvent$Init$Normal"},
            {"crazypants.enderio.base.gui.tooltip.TooltipHandlerBurnTime", "init", "crazypants.enderio.base.events.EnderIOLifecycleEvent$PreInit"},
            {"crazypants.enderio.base.gui.tooltip.TooltipHandlerFluid", "init", "crazypants.enderio.base.events.EnderIOLifecycleEvent$PreInit"},
            {"crazypants.enderio.base.gui.tooltip.TooltipHandlerGrinding", "init", "crazypants.enderio.base.events.EnderIOLifecycleEvent$PreInit"},
            {"crazypants.enderio.base.handler.DevVersionWarningHandler", "onPlayerLoggon", "net.minecraftforge.fml.common.gameevent.PlayerEvent$PlayerLoggedInEvent"},
            {"crazypants.enderio.base.handler.FovZoomHandler", "onFov", "net.minecraftforge.client.event.EntityViewRenderEvent$FOVModifier"},
            {"crazypants.enderio.base.handler.KeyTracker", "onKeyInput", "net.minecraftforge.fml.common.gameevent.InputEvent$KeyInputEvent"},
            {"crazypants.enderio.base.handler.RecipeButtonHandler", "onGuiInit", "net.minecraftforge.client.event.GuiScreenEvent$InitGuiEvent$Post"},
            {"crazypants.enderio.base.handler.ServerTickHandler", "flush", "crazypants.enderio.base.events.EnderIOLifecycleEvent$ServerStopped$Post", "onWorldTick", "net.minecraftforge.fml.common.gameevent.TickEvent$WorldTickEvent", "onServerTick", "net.minecraftforge.fml.common.gameevent.TickEvent$ServerTickEvent"},
            {"crazypants.enderio.base.handler.SplashTextHandler", "handle", "net.minecraftforge.client.event.GuiScreenEvent$InitGuiEvent$Pre"},
            {"crazypants.enderio.base.handler.darksteel.DarkSteelController", "onPlayerTick", "net.minecraftforge.fml.common.gameevent.TickEvent$PlayerTickEvent", "onPlayerTickServer", "net.minecraftforge.fml.common.gameevent.TickEvent$PlayerTickEvent", "onFall", "net.minecraftforge.event.entity.living.LivingFallEvent", "onClientTick", "net.minecraftforge.fml.common.gameevent.TickEvent$ClientTickEvent"},
            {"crazypants.enderio.base.handler.darksteel.DarkSteelRepairRecipe", "handleAnvilEvent", "net.minecraftforge.event.AnvilUpdateEvent"},
            {"crazypants.enderio.base.handler.darksteel.PlayerAOEAttributeHandler", "handleConstruct", "net.minecraftforge.event.entity.EntityEvent$EntityConstructing", "handleJoin", "net.minecraftforge.event.entity.EntityJoinWorldEvent", "onHighlight", "net.minecraftforge.client.event.DrawBlockHighlightEvent"},
            {"crazypants.enderio.base.handler.darksteel.StateController", "onTracking", "net.minecraftforge.event.entity.player.PlayerEvent$StartTracking", "onLogin", "net.minecraftforge.fml.common.gameevent.PlayerEvent$PlayerLoggedInEvent", "onRespawn", "net.minecraftforge.fml.common.gameevent.PlayerEvent$PlayerRespawnEvent", "onChangedDimension", "net.minecraftforge.fml.common.gameevent.PlayerEvent$PlayerChangedDimensionEvent"},
            {"crazypants.enderio.base.handler.darksteel.SwordHandler", "onEnderTeleport", "net.minecraftforge.event.entity.living.EnderTeleportEvent", "onEntityDrop", "net.minecraftforge.event.entity.living.LivingDropsEvent"},
            {"crazypants.enderio.base.handler.darksteel.UpgradeRegistry", "registerRegistry", "net.minecraftforge.event.RegistryEvent$NewRegistry", "onItemDesctroyed", "net.minecraftforge.event.entity.player.PlayerDestroyItemEvent"},
            {"crazypants.enderio.base.handler.darksteel.UpgradeRenderManager", "onPlayerRenderPre", "net.minecraftforge.client.event.RenderPlayerEvent$Pre"},
            {"crazypants.enderio.base.handler.darksteel.gui.DSUContainerProxy$setTab", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.handler.darksteel.gui.DSUContainerProxy$updateItemName", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.init.ClientProxy", "onModelRegistryEvent", "net.minecraftforge.client.event.ModelRegistryEvent"},
            {"crazypants.enderio.base.init.ModObjectRegistry", "registerRegistry", "net.minecraftforge.event.RegistryEvent$NewRegistry", "registerRegistry", "crazypants.enderio.base.events.EnderIOLifecycleEvent$PreInit", "registerBlocksEarly", "crazypants.enderio.base.init.RegisterModObject", "registerBlocks", "net.minecraftforge.event.RegistryEvent$Register", "registerAddonBlocks", "net.minecraftforge.event.RegistryEvent$Register", "registerTileEntities", "net.minecraftforge.event.RegistryEvent$Register", "registerItems", "net.minecraftforge.event.RegistryEvent$Register", "registerOredict", "net.minecraftforge.event.RegistryEvent$Register", "init", "crazypants.enderio.base.events.EnderIOLifecycleEvent$Init$Pre"},
            {"crazypants.enderio.base.init.ModTileEntity", "registerBlocksEarly", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.init.TickTimer", "onTick", "net.minecraftforge.fml.common.gameevent.TickEvent$ServerTickEvent", "onTick", "net.minecraftforge.fml.common.gameevent.TickEvent$ClientTickEvent"},
            {"crazypants.enderio.base.integration.IntegrationRegistry", "registerRegistry", "net.minecraftforge.event.RegistryEvent$NewRegistry"},
            {"crazypants.enderio.base.integration.actuallyadditions.ActuallyadditionsUtil", "registerFertilizer", "net.minecraftforge.event.RegistryEvent$Register", "registerHoes", "crazypants.enderio.base.events.EnderIOLifecycleEvent$Init$Pre"},
            {"crazypants.enderio.base.integration.ae2.AE2Util", "registerHoes", "crazypants.enderio.base.events.EnderIOLifecycleEvent$Init$Pre"},
            {"crazypants.enderio.base.integration.basemetals.BaseMetalsUtil", "registerHoes", "crazypants.enderio.base.events.EnderIOLifecycleEvent$Init$Pre"},
            {"crazypants.enderio.base.integration.bigreactors.BRProxy", "init", "crazypants.enderio.base.events.EnderIOLifecycleEvent$Init$Normal"},
            {"crazypants.enderio.base.integration.bop.BoPUtil", "registerFarmers", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.integration.botania.BotaniaUtil", "registerFarmers", "net.minecraftforge.event.RegistryEvent$Register", "registerFertilizer", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.integration.botany.BotanyUtil", "registerFarmers", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.integration.chickens.ChickensUtil", "preConfig", "crazypants.enderio.base.events.EnderIOLifecycleEvent$Config$Pre"},
            {"crazypants.enderio.base.integration.chiselsandbits.CABIMC", "init", "crazypants.enderio.base.events.EnderIOLifecycleEvent$Init$Normal"},
            {"crazypants.enderio.base.integration.draconic.DraconicUtil", "registerHoes", "crazypants.enderio.base.events.EnderIOLifecycleEvent$Init$Pre"},
            {"crazypants.enderio.base.integration.exu2.ExU2Util", "registerFarmers", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.integration.gardencore.GardencoreUtil", "registerFertilizer", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.integration.ic2c.IC2cUtil", "registerFarmers", "net.minecraftforge.event.RegistryEvent$Register", "registerTreetaps", "crazypants.enderio.base.events.EnderIOLifecycleEvent$Init$Pre"},
            {"crazypants.enderio.base.integration.ic2e.IC2eUtil", "registerFarmers", "net.minecraftforge.event.RegistryEvent$Register", "registerTools", "crazypants.enderio.base.events.EnderIOLifecycleEvent$Init$Pre"},
            {"crazypants.enderio.base.integration.immersiveengineering.ImmersiveEngineeringUtil", "registerFarmers", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.integration.magicalcrops.MagicalcropsUtil", "registerFertilizer", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.integration.matteroverdrive.MatterOverdriveUtil", "registerHoes", "crazypants.enderio.base.events.EnderIOLifecycleEvent$Init$Pre"},
            {"crazypants.enderio.base.integration.mekanism.MekanismUtil", "registerHoes", "crazypants.enderio.base.events.EnderIOLifecycleEvent$Init$Pre"},
            {"crazypants.enderio.base.integration.metallurgy.MetallurgyUtil", "registerFertilizer", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.integration.mfr.MFRUtil", "registerFarmers", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.integration.mysticalagriculture.MysticalAgricultureUtil", "registerHoes", "crazypants.enderio.base.events.EnderIOLifecycleEvent$Init$Pre"},
            {"crazypants.enderio.base.integration.natura.NaturaUtil", "registerFarmers", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.integration.railcraft.RailcraftUtil", "registerHoes", "crazypants.enderio.base.events.EnderIOLifecycleEvent$Init$Pre"},
            {"crazypants.enderio.base.integration.te.TEUtil", "registerHoes", "crazypants.enderio.base.events.EnderIOLifecycleEvent$Init$Pre"},
            {"crazypants.enderio.base.integration.techreborn.TechRebornUtil", "registerFarmers", "net.minecraftforge.event.RegistryEvent$Register", "registerHoes", "crazypants.enderio.base.events.EnderIOLifecycleEvent$Init$Pre"},
            {"crazypants.enderio.base.integration.thaumcraft.ThaumcraftUtil", "onPost", "crazypants.enderio.base.events.EnderIOLifecycleEvent$PostInit$Post", "registerDarkSteelUpgrades", "net.minecraftforge.event.RegistryEvent$Register", "registerHoes", "crazypants.enderio.base.events.EnderIOLifecycleEvent$Init$Pre", "registerFarmers", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.integration.thaumicadditions.ThaumicadditionsUtil", "registerFarmers", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.integration.tic.TicUtil", "registerFarmers", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.integration.top.TOPUtil", "create", "crazypants.enderio.base.events.EnderIOLifecycleEvent$PreInit", "registerDarkSteelUpgrades", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.invpanel.capability.CapabilityDatabaseHandler", "create", "crazypants.enderio.base.events.EnderIOLifecycleEvent$PreInit"},
            {"crazypants.enderio.base.item.conduitprobe.ConduitProbeOverlayRenderer", "renderOverlay", "net.minecraftforge.client.event.RenderGameOverlayEvent$Post"},
            {"crazypants.enderio.base.item.conduitprobe.ToolTickHandler", "onMouseEvent", "net.minecraftforge.client.event.MouseEvent"},
            {"crazypants.enderio.base.item.darksteel.DarkShieldRecipes", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.item.darksteel.ItemDarkSteelCrook", "onHarvest", "net.minecraftforge.event.world.BlockEvent$HarvestDropsEvent"},
            {"crazypants.enderio.base.item.darksteel.ItemInventoryCharger", "onTick", "net.minecraftforge.fml.common.gameevent.TickEvent$PlayerTickEvent"},
            {"crazypants.enderio.base.item.darksteel.upgrade.anvil.AnvilUpgrade", "registerDarkSteelUpgrades", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.item.darksteel.upgrade.direct.DirectUpgrade", "registerDarkSteelUpgrades", "net.minecraftforge.event.RegistryEvent$Register", "blockDropEvent", "net.minecraftforge.event.world.BlockEvent$HarvestDropsEvent", "attackEntityEvent", "net.minecraftforge.event.entity.player.AttackEntityEvent", "livingDropsEvent", "net.minecraftforge.event.entity.living.LivingDropsEvent"},
            {"crazypants.enderio.base.item.darksteel.upgrade.elytra.ElytraUpgrade", "registerDarkSteelUpgrades", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.item.darksteel.upgrade.energy.EnergyUpgrade", "registerDarkSteelUpgrades", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.item.darksteel.upgrade.explosive.ExplosiveCarpetUpgrade", "registerDarkSteelUpgrades", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.item.darksteel.upgrade.explosive.ExplosiveDepthUpgrade", "registerDarkSteelUpgrades", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.item.darksteel.upgrade.explosive.ExplosiveUpgrade", "registerDarkSteelUpgrades", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.item.darksteel.upgrade.flippers.SwimUpgrade", "registerDarkSteelUpgrades", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.item.darksteel.upgrade.glider.GliderUpgrade", "registerDarkSteelUpgrades", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.item.darksteel.upgrade.hoe.HoeUpgrade", "registerDarkSteelUpgrades", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.item.darksteel.upgrade.jump.JumpUpgrade", "registerDarkSteelUpgrades", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.item.darksteel.upgrade.nightvision.NightVisionUpgrade", "registerDarkSteelUpgrades", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.item.darksteel.upgrade.padding.PaddingHandler", "onPlaySoundEvent", "net.minecraftforge.client.event.sound.PlaySoundEvent"},
            {"crazypants.enderio.base.item.darksteel.upgrade.padding.PaddingUpgrade", "registerDarkSteelUpgrades", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.item.darksteel.upgrade.sound.SoundDetector", "onSound", "net.minecraftforge.client.event.sound.PlaySoundSourceEvent", "onClientTick", "net.minecraftforge.fml.common.gameevent.TickEvent$ClientTickEvent"},
            {"crazypants.enderio.base.item.darksteel.upgrade.sound.SoundDetectorUpgrade", "registerDarkSteelUpgrades", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.item.darksteel.upgrade.speed.SpeedController", "handleFovUpdate", "net.minecraftforge.client.event.FOVUpdateEvent"},
            {"crazypants.enderio.base.item.darksteel.upgrade.speed.SpeedUpgrade", "registerDarkSteelUpgrades", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.item.darksteel.upgrade.spoon.SpoonUpgrade", "registerDarkSteelUpgrades", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.item.darksteel.upgrade.stepassist.StepAssistUpgrade", "registerDarkSteelUpgrades", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.item.darksteel.upgrade.storage.StorageContainerProxy$setTab", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.item.darksteel.upgrade.storage.StorageUpgrade", "registerDarkSteelUpgrades", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.item.darksteel.upgrade.travel.TravelUpgrade", "registerDarkSteelUpgrades", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.item.eggs.EntityOwlEgg", "onEntityRegister", "net.minecraftforge.event.RegistryEvent$Register", "onPreInit", "crazypants.enderio.base.events.EnderIOLifecycleEvent$PreInit"},
            {"crazypants.enderio.base.item.magnet.MagnetController", "onPlayerTick", "net.minecraftforge.fml.common.gameevent.TickEvent$PlayerTickEvent"},
            {"crazypants.enderio.base.item.spawner.BrokenSpawnerHandler", "onBreakEvent", "net.minecraftforge.event.world.BlockEvent$BreakEvent", "onHarvestDropsEvent", "net.minecraftforge.event.world.BlockEvent$HarvestDropsEvent", "onServerTick", "net.minecraftforge.fml.common.gameevent.TickEvent$ServerTickEvent"},
            {"crazypants.enderio.base.item.yetawrench.YetaWrenchOverlayRenderer", "renderOverlay", "net.minecraftforge.client.event.RenderGameOverlayEvent$Post"},
            {"crazypants.enderio.base.loot.AnvilCapacitorRecipe", "handleAnvilEvent", "net.minecraftforge.event.AnvilUpdateEvent"},
            {"crazypants.enderio.base.loot.Loot", "preInit", "crazypants.enderio.base.events.EnderIOLifecycleEvent$PreInit"},
            {"crazypants.enderio.base.loot.LootManager", "onLootTableLoad", "net.minecraftforge.event.LootTableLoadEvent"},
            {"crazypants.enderio.base.machine.entity.EntityFallingMachine", "onEntityRegister", "net.minecraftforge.event.RegistryEvent$Register", "onPreInit", "crazypants.enderio.base.events.EnderIOLifecycleEvent$PreInit"},
            {"crazypants.enderio.base.machine.recipes.MachineRecipes", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.material.glass.EnderIOGlassesStateMapper", "init", "net.minecraftforge.client.event.ModelRegistryEvent"},
            {"crazypants.enderio.base.material.material.MaterialCraftingHandler", "on", "net.minecraftforge.event.world.BlockEvent$NeighborNotifyEvent", "onWorldTick", "net.minecraftforge.fml.common.gameevent.TickEvent$WorldTickEvent"},
            {"crazypants.enderio.base.material.recipes.MaterialOredicts", "registerOredict", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.network.ExecPacket", "registerRegistry", "net.minecraftforge.event.RegistryEvent$NewRegistry"},
            {"crazypants.enderio.base.paint.PaintTooltipUtil", "addTooltip", "net.minecraftforge.event.entity.player.ItemTooltipEvent"},
            {"crazypants.enderio.base.paint.YetaUtil", "onTick", "net.minecraftforge.fml.common.gameevent.TickEvent$ClientTickEvent"},
            {"crazypants.enderio.base.paint.render.PaintRegistry", "register", "crazypants.enderio.base.events.EnderIOLifecycleEvent$PreInit"},
            {"crazypants.enderio.base.potion.PotionConfusion", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.potion.PotionFloating", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.potion.PotionWithering", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.base.power.wireless.WirelessChargerController", "onPlayerTick", "net.minecraftforge.fml.common.gameevent.TickEvent$PlayerTickEvent"},
            {"crazypants.enderio.base.recipe.alloysmelter.AlloyRecipeManager", "create", "crazypants.enderio.base.events.EnderIOLifecycleEvent$PostInit$Pre", "remap", "crazypants.enderio.base.events.EnderIOLifecycleEvent$ModIdMappingEvent"},
            {"crazypants.enderio.base.recipe.alloysmelter.VanillaSmeltingRecipe", "create", "crazypants.enderio.base.events.EnderIOLifecycleEvent$PostInit$Pre"},
            {"crazypants.enderio.base.render.pipeline.BlockStateWrapperBase", "invalidate", "net.minecraftforge.client.event.ModelBakeEvent"},
            {"crazypants.enderio.base.render.pipeline.EnderItemOverrideList", "invalidate", "net.minecraftforge.client.event.ModelBakeEvent"},
            {"crazypants.enderio.base.render.registry.ItemModelRegistry", "bakeModels", "net.minecraftforge.client.event.ModelBakeEvent"},
            {"crazypants.enderio.base.render.registry.SmartModelAttacher", "registerBlockItemModels", "net.minecraftforge.client.event.ModelRegistryEvent", "registerColoredBlocksAndItems", "crazypants.enderio.base.events.EnderIOLifecycleEvent$Init$Post", "bakeModels", "net.minecraftforge.client.event.ModelBakeEvent"},
            {"crazypants.enderio.base.render.registry.TextureResolver", "onIconLoad", "net.minecraftforge.client.event.TextureStitchEvent$Pre"},
            {"crazypants.enderio.base.render.util.DynaTextureProvider$Unloader", "unload", "net.minecraftforge.event.world.WorldEvent$Unload"},
            {"crazypants.enderio.base.sound.SoundRegistry", "registerSounds", "crazypants.enderio.base.events.ModSoundRegisterEvent"},
            {"crazypants.enderio.base.teleport.ChunkTicket", "onPreInit", "crazypants.enderio.base.events.EnderIOLifecycleEvent$PreInit", "onWorldUnload", "net.minecraftforge.event.world.WorldEvent$Unload", "onServerTick", "net.minecraftforge.fml.common.gameevent.TickEvent$ServerTickEvent"},
            {"crazypants.enderio.base.teleport.TravelController", "onRender", "net.minecraftforge.client.event.RenderWorldLastEvent", "onClientTick", "net.minecraftforge.fml.common.gameevent.TickEvent$ClientTickEvent"},
            {"crazypants.enderio.base.transceiver.ServerChannelRegister", "onWorldCaps", "net.minecraftforge.event.AttachCapabilitiesEvent", "preInit", "crazypants.enderio.base.events.EnderIOLifecycleEvent$PreInit", "onServerAboutToStart", "crazypants.enderio.base.events.EnderIOLifecycleEvent$ServerAboutToStart$Pre", "onServerStopped", "crazypants.enderio.base.events.EnderIOLifecycleEvent$ServerStopped$Pre"},
            {"crazypants.enderio.conduit.me.EnderIOConduitsAppliedEnergistics", "registerConduits", "crazypants.enderio.base.init.RegisterModObject"},
            {"crazypants.enderio.conduit.oc.EnderIOConduitsOpenComputers", "registerConduits", "crazypants.enderio.base.init.RegisterModObject"},
            {"crazypants.enderio.conduit.refinedstorage.EnderIOConduitsRefinedStorage", "registerBlocksEarly", "crazypants.enderio.base.init.RegisterModObject"},
            {"crazypants.enderio.conduits.autosave.ConduitHandlers", "register", "crazypants.enderio.base.events.EnderIOLifecycleEvent$PreInit"},
            {"crazypants.enderio.conduits.handler.ConduitBreakSpeedHandler", "onBreakSpeed", "net.minecraftforge.event.entity.player.PlayerEvent$BreakSpeed"},
            {"crazypants.enderio.conduits.init.ClientProxy", "onModelRegistryEvent", "net.minecraftforge.client.event.ModelRegistryEvent"},
            {"crazypants.enderio.conduits.init.ConduitObject", "registerBlocksEarly", "crazypants.enderio.base.init.RegisterModObject"},
            {"crazypants.enderio.conduits.init.ConduitTileEntity", "registerBlocksEarly", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.conduits.render.BlockStateWrapperConduitBundle", "invalidate", "net.minecraftforge.client.event.ModelBakeEvent"},
            {"crazypants.enderio.conduits.render.ConduitInOutRenderer", "load", "crazypants.enderio.base.events.EnderIOLifecycleEvent$PreInit"},
            {"crazypants.enderio.integration.forestry.EnderIOIntegrationForestry", "registerFarmers", "crazypants.enderio.base.init.RegisterModObject"},
            {"crazypants.enderio.integration.tic.EnderIOIntegrationTic", "registerBlocksEarly", "crazypants.enderio.base.init.RegisterModObject", "registerRenderers", "net.minecraftforge.client.event.ModelRegistryEvent"},
            {"crazypants.enderio.integration.tic.EnderIOIntegrationTicLate"},
            {"crazypants.enderio.invpanel.autosave.InvPanelHandlers", "register", "crazypants.enderio.base.events.EnderIOLifecycleEvent$PreInit"},
            {"crazypants.enderio.invpanel.capacitor.CapacitorKey", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.invpanel.chest.EnumChestSize", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.invpanel.init.InvpanelObject", "registerBlocksEarly", "crazypants.enderio.base.init.RegisterModObject"},
            {"crazypants.enderio.invpanel.init.InvpanelTileEntity", "registerBlocksEarly", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.machines.autosave.MachineHandlers", "register", "crazypants.enderio.base.events.EnderIOLifecycleEvent$PreInit"},
            {"crazypants.enderio.machines.capacitor.CapacitorKey", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.machines.darksteel.upgrade.solar.SolarUpgrade", "registerDarkSteelUpgrades", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.machines.init.MachineObject", "registerBlocksEarly", "crazypants.enderio.base.init.RegisterModObject"},
            {"crazypants.enderio.machines.init.MachineTileEntity", "registerBlocksEarly", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.machines.machine.alloy.ContainerAlloySmelterProxy$doSetMode", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.machines.machine.crafter.ContainerCrafterProxy$setBufferStacks", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.machines.machine.killera.FakePlayerKillerJoe", "onSummonAid", "net.minecraftforge.event.entity.living.ZombieEvent$SummonAidEvent"},
            {"crazypants.enderio.machines.machine.obelisk.inhibitor.InhibitorHandler", "onTeleport", "crazypants.enderio.api.teleport.TeleportEntityEvent", "onEnderTeleport", "net.minecraftforge.event.entity.living.EnderTeleportEvent"},
            {"crazypants.enderio.machines.machine.obelisk.render.ObeliskRenderManager", "onModelRegister", "net.minecraftforge.client.event.ModelRegistryEvent"},
            {"crazypants.enderio.machines.machine.obelisk.weather.BlockWeatherObelisk", "onEntityRegister", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.machines.machine.obelisk.xp.ContainerExperienceObeliskProxy$doAddXP", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.machines.machine.obelisk.xp.ContainerExperienceObeliskProxy$doDrainXP", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.machines.machine.solar.SolarType", "registerRegistry", "crazypants.enderio.base.init.RegisterModObject"},
            {"crazypants.enderio.machines.machine.teleport.telepad.TeleportEntityRenderHandler", "onEntityRender", "net.minecraftforge.client.event.RenderLivingEvent$Post", "onEntityRender", "net.minecraftforge.client.event.RenderLivingEvent$Pre"},
            {"crazypants.enderio.machines.machine.teleport.telepad.gui.ContainerDialingDeviceProxy$doTeleport", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.machines.machine.vacuum.chest.ContainerVacuumChestProxy$doOpenFilterGui", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.powertools.capacitor.CapacitorKey", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.powertools.init.PowerToolObject", "registerBlocksEarly", "crazypants.enderio.base.init.RegisterModObject"},
            {"crazypants.enderio.powertools.init.PowerToolTileEntity", "registerBlocksEarly", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.powertools.machine.capbank.network.ClientNetworkManager", "unload", "net.minecraftforge.event.world.WorldEvent$Unload"},
            {"crazypants.enderio.powertools.machine.monitor.ContainerPowerMonitorProxy$doSetConfig", "register", "net.minecraftforge.event.RegistryEvent$Register"},
            {"crazypants.enderio.powertools.recipes.PowerToolRecipes", "register", "net.minecraftforge.event.RegistryEvent$Register"},
    };

    private static final Map<String, SubscriberPlan> SUBSCRIBERS = buildSubscribers();

    private EnderIOConstructionOptimizations() {
    }

    public static boolean tryInjectAutomaticSubscribers(ModContainer mod, ASMDataTable asmData, Side side) {
        if (!ENABLED || mod == null || asmData == null || !isSupportedEnderIOModId(mod.getModId())) {
            return false;
        }
        if (EVENT_BUS_REGISTER_METHOD == null) {
            logFallbackOnce("missing-eventbus-register", "EnderIO automatic subscriber fast path disabled: EventBus private register method was not found");
            return false;
        }

        try {
            Set<String> classNames = selectedSubscriberClassNames(mod, asmData, side);
            if (classNames.isEmpty()) {
                return false;
            }

            ClassLoader classLoader = Loader.instance().getModClassLoader();
            List<ResolvedSubscriber> subscribers = new ArrayList<>(classNames.size());
            for (String className : classNames) {
                SubscriberPlan plan = SUBSCRIBERS.get(className);
                if (plan == null) {
                    logFallbackOnce(className, "EnderIO whole automatic subscriber fast path missing manifest row for " + className);
                    return false;
                }
                if (!TargetedModVersions.isEnderIOClass(className)) {
                    return false;
                }

                Class<?> targetClass = Class.forName(className, false, classLoader);
                if (!TargetedModVersions.isEnderIOClass(targetClass)) {
                    return false;
                }

                ResolvedHandler[] handlers = resolveHandlers(targetClass, plan);
                if (handlers == null) {
                    return false;
                }
                subscribers.add(new ResolvedSubscriber(targetClass, handlers));
            }

            EventBus eventBus = MinecraftForge.EVENT_BUS;
            for (ResolvedSubscriber subscriber : subscribers) {
                registerResolved(eventBus, mod, subscriber.targetClass, subscriber.handlers);
            }
            return true;
        } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException | LinkageError | RuntimeException e) {
            logFallbackOnce(mod.getModId() + "-whole-inject", "EnderIO whole automatic subscriber fast path failed for " + mod.getModId() + "; falling back to Forge registration", e);
            return false;
        }
    }

    public static boolean tryRegisterAutomaticSubscriber(EventBus eventBus, String modId, Object target, ModContainer owner) {
        if (!ENABLED || eventBus == null || !isSupportedEnderIOModId(modId) || !(target instanceof Class)) {
            return false;
        }

        Class<?> targetClass = (Class<?>) target;
        if (!TargetedModVersions.isEnderIOClass(targetClass)) {
            return false;
        }

        SubscriberPlan plan = SUBSCRIBERS.get(targetClass.getName());
        if (plan == null) {
            return false;
        }

        if (EVENT_BUS_REGISTER_METHOD == null) {
            logFallbackOnce("missing-eventbus-register", "EnderIO automatic subscriber fast path disabled: EventBus private register method was not found");
            return false;
        }

        try {
            ResolvedHandler[] handlers = resolveHandlers(targetClass, plan);
            if (handlers == null) {
                return false;
            }
            registerResolved(eventBus, owner, targetClass, handlers);
            return true;
        } catch (IllegalAccessException | InvocationTargetException | LinkageError | RuntimeException e) {
            logFallbackOnce(targetClass.getName(), "EnderIO automatic subscriber fast path failed for " + targetClass.getName() + "; falling back to Forge registration", e);
            return false;
        }
    }

    private static Set<String> selectedSubscriberClassNames(ModContainer mod, ASMDataTable asmData, Side side) {
        SetMultimap<String, ASMDataTable.ASMData> annotations = asmData.getAnnotationsFor(mod);
        Set<ASMDataTable.ASMData> modAnnotations = annotations.get(net.minecraftforge.fml.common.Mod.class.getName());
        Set<ASMDataTable.ASMData> subscriberAnnotations = annotations.get(net.minecraftforge.fml.common.Mod.EventBusSubscriber.class.getName());
        Set<String> classNames = new LinkedHashSet<>(subscriberAnnotations.size());

        for (ASMDataTable.ASMData data : subscriberAnnotations) {
            if (!subscriberMatchesCurrentSide(data, side)) {
                continue;
            }

            String ownerModId = annotationModId(data);
            if (isNullOrEmpty(ownerModId)) {
                ownerModId = ASMDataTable.getOwnerModID(modAnnotations, data);
            }
            if (mod.getModId().equals(ownerModId)) {
                classNames.add(data.getClassName());
            }
        }
        return classNames;
    }

    private static boolean subscriberMatchesCurrentSide(ASMDataTable.ASMData data, Side side) {
        Object value = data.getAnnotationInfo().get("value");
        if (!(value instanceof List)) {
            return true;
        }

        @SuppressWarnings("unchecked")
        List<Object> configuredSides = (List<Object>) value;
        if (configuredSides.isEmpty()) {
            return DEFAULT_SIDES.contains(side);
        }

        for (Object configuredSide : configuredSides) {
            Side selectedSide = sideFromAnnotationValue(configuredSide);
            if (selectedSide == side) {
                return true;
            }
        }
        return false;
    }

    private static Side sideFromAnnotationValue(Object value) {
        if (value instanceof EnumHolder) {
            return Side.valueOf(((EnumHolder) value).getValue());
        }
        return Side.valueOf(String.valueOf(value));
    }

    private static String annotationModId(ASMDataTable.ASMData data) {
        Object modId = data.getAnnotationInfo().get("modid");
        return modId instanceof String ? (String) modId : null;
    }

    private static boolean isNullOrEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private static void registerResolved(EventBus eventBus, ModContainer owner, Class<?> targetClass, ResolvedHandler[] handlers)
            throws InvocationTargetException, IllegalAccessException {
        Map<Object, ModContainer> listenerOwners = listenerOwners(eventBus);
        if (listenerOwners != null) {
            listenerOwners.put(targetClass, owner);
        }

        Map<Object, ?> listeners = listeners(eventBus);
        if (listeners != null && listeners.containsKey(targetClass)) {
            return;
        }

        for (ResolvedHandler handler : handlers) {
            EVENT_BUS_REGISTER_METHOD.invoke(eventBus, handler.eventType, targetClass, handler.method, owner);
        }
    }

    private static ResolvedHandler[] resolveHandlers(Class<?> targetClass, SubscriberPlan plan) {
        ClassLoader classLoader = targetClass.getClassLoader();
        List<ResolvedHandler> handlers = new ArrayList<>(plan.handlers.length);
        for (HandlerPlan handler : plan.handlers) {
            Class<? extends Event> eventType;
            Method method;
            try {
                eventType = Class.forName(handler.eventClassName, false, classLoader).asSubclass(Event.class);
                method = targetClass.getDeclaredMethod(handler.methodName, eventType);
            } catch (ClassNotFoundException | NoSuchMethodException | ClassCastException | LinkageError e) {
                // SideOnly can strip client/server-only handlers from the runtime class. Forge's reflective
                // registration only sees methods that survived side stripping, so skipping is equivalent.
                continue;
            }

            int modifiers = method.getModifiers();
            if (!java.lang.reflect.Modifier.isPublic(modifiers) || !java.lang.reflect.Modifier.isStatic(modifiers)) {
                logFallbackOnce(targetClass.getName() + '#' + handler.methodName, "EnderIO automatic subscriber fast path found non-public/static handler " + targetClass.getName() + '#' + handler.methodName);
                return null;
            }
            if (method.getAnnotation(SubscribeEvent.class) == null) {
                logFallbackOnce(targetClass.getName() + '#' + handler.methodName, "EnderIO automatic subscriber fast path found handler without @SubscribeEvent " + targetClass.getName() + '#' + handler.methodName);
                return null;
            }
            handlers.add(new ResolvedHandler(eventType, method));
        }
        return handlers.toArray(new ResolvedHandler[0]);
    }

    private static boolean isSupportedEnderIOModId(String modId) {
        return modId != null && modId.startsWith("enderio");
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, ModContainer> listenerOwners(EventBus eventBus) {
        return (Map<Object, ModContainer>) ReflectionFields.get(eventBus, "listenerOwners", "listenerOwners");
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, ?> listeners(EventBus eventBus) {
        return (Map<Object, ?>) ReflectionFields.get(eventBus, "listeners", "listeners");
    }

    private static Method findEventBusRegisterMethod() {
        try {
            Method method = EventBus.class.getDeclaredMethod("register", Class.class, Object.class, Method.class, ModContainer.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Map<String, SubscriberPlan> buildSubscribers() {
        Map<String, SubscriberPlan> subscribers = new HashMap<>();
        for (String[] row : ENDER_IO_SUBSCRIBERS) {
            HandlerPlan[] handlers = new HandlerPlan[(row.length - 1) / 2];
            for (int i = 1, handlerIndex = 0; i < row.length; i += 2, handlerIndex++) {
                handlers[handlerIndex] = new HandlerPlan(row[i], row[i + 1]);
            }
            subscribers.put(row[0], new SubscriberPlan(handlers));
        }
        return subscribers;
    }

    private static void logFallbackOnce(String key, String message) {
        if (FALLBACK_LOGS.putIfAbsent(key, Boolean.TRUE) == null) {
            GPOM.LOGGER.warn(message);
        }
    }

    private static void logFallbackOnce(String key, String message, Throwable throwable) {
        if (FALLBACK_LOGS.putIfAbsent(key, Boolean.TRUE) == null) {
            GPOM.LOGGER.warn(message, throwable);
        }
    }

    private static final class SubscriberPlan {
        private final HandlerPlan[] handlers;

        private SubscriberPlan(HandlerPlan[] handlers) {
            this.handlers = handlers;
        }
    }

    private static final class HandlerPlan {
        private final String methodName;
        private final String eventClassName;

        private HandlerPlan(String methodName, String eventClassName) {
            this.methodName = methodName;
            this.eventClassName = eventClassName;
        }
    }

    private static final class ResolvedHandler {
        private final Class<? extends Event> eventType;
        private final Method method;

        private ResolvedHandler(Class<? extends Event> eventType, Method method) {
            this.eventType = eventType;
            this.method = method;
        }
    }

    private static final class ResolvedSubscriber {
        private final Class<?> targetClass;
        private final ResolvedHandler[] handlers;

        private ResolvedSubscriber(Class<?> targetClass, ResolvedHandler[] handlers) {
            this.targetClass = targetClass;
            this.handlers = handlers;
        }
    }
}
