package com.l.gpom.compat.sfm.integration;

import ca.teamdman.sfm.common.capability.SFMBlockCapabilityProvider;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityProviderContainer;
import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer;
import com.l.gpom.GPOM;
import com.l.gpom.Reference;
import com.l.gpom.compat.sfm.integration.capability.GpomAspectHandler;
import com.l.gpom.compat.sfm.integration.capability.GpomManaStorage;
import com.l.gpom.compat.sfm.integration.capability.GpomPotentialEnergyStorage;
import com.l.gpom.compat.sfm.integration.provider.AbyssalCraftPotentialEnergyProvider;
import com.l.gpom.compat.sfm.integration.provider.BotaniaManaProvider;
import com.l.gpom.compat.sfm.integration.provider.ThaumcraftAspectProvider;
import com.l.gpom.compat.sfm.integration.resource.AbyssalCraftPotentialEnergyResourceType;
import com.l.gpom.compat.sfm.integration.resource.BotaniaManaResourceType;
import com.l.gpom.compat.sfm.integration.resource.ThaumcraftAspectResourceType;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.nbt.NBTBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.concurrent.Callable;

public final class SfmMagicalCapabilityIntegration {
    public static final String SFM_MODID = "superfactorymanager";
    public static final String BOTANIA_MODID = "botania";
    public static final String THAUMCRAFT_MODID = "thaumcraft";
    public static final String ABYSSALCRAFT_MODID = "abyssalcraft";

    @CapabilityInject(GpomManaStorage.class)
    public static Capability<GpomManaStorage> MANA_CAPABILITY = null;
    @CapabilityInject(GpomAspectHandler.class)
    public static Capability<GpomAspectHandler> ASPECT_CAPABILITY = null;
    @CapabilityInject(GpomPotentialEnergyStorage.class)
    public static Capability<GpomPotentialEnergyStorage> PE_CAPABILITY = null;

    private static boolean registered;

    private SfmMagicalCapabilityIntegration() {
    }

    public static void registerIfNeeded() {
        if (registered || !GpomEarlyConfig.sfmCustomResourcesEnabled() || !Loader.isModLoaded(SFM_MODID)) {
            return;
        }
        registered = true;
        registerCapabilities();
        MinecraftForge.EVENT_BUS.register(new SfmMagicalCapabilityIntegration());
        GPOM.LOGGER.info("[GPOM SFM] Registered magical capability integration hooks");
    }

    @SubscribeEvent
    public void registerResourceTypes(RegistryEvent.Register<ResourceTypeContainer> event) {
        if (botaniaManaEnabled()) {
            event.getRegistry().register(container("botania_mana", BotaniaManaResourceType::new));
        }
        if (thaumcraftAspectsEnabled()) {
            event.getRegistry().register(container("aspect", ThaumcraftAspectResourceType::new));
        }
        if (abyssalCraftPotentialEnergyEnabled()) {
            event.getRegistry().register(container("abyssalcraft_pe", AbyssalCraftPotentialEnergyResourceType::new));
        }
    }

    @SubscribeEvent
    public void registerCapabilityProviders(RegistryEvent.Register<SFMBlockCapabilityProviderContainer> event) {
        if (botaniaManaEnabled()) {
            event.getRegistry().register(provider("botania_mana", new BotaniaManaProvider()));
        }
        if (thaumcraftAspectsEnabled()) {
            event.getRegistry().register(provider("thaumcraft_aspect", new ThaumcraftAspectProvider()));
        }
        if (abyssalCraftPotentialEnergyEnabled()) {
            event.getRegistry().register(provider("abyssalcraft_pe", new AbyssalCraftPotentialEnergyProvider()));
        }
    }

    private static boolean botaniaManaEnabled() {
        return GpomEarlyConfig.sfmCustomBotaniaManaEnabled() && Loader.isModLoaded(BOTANIA_MODID);
    }

    private static boolean thaumcraftAspectsEnabled() {
        return GpomEarlyConfig.sfmCustomThaumcraftAspectsEnabled() && Loader.isModLoaded(THAUMCRAFT_MODID);
    }

    private static boolean abyssalCraftPotentialEnergyEnabled() {
        return GpomEarlyConfig.sfmCustomAbyssalCraftPotentialEnergyEnabled() && Loader.isModLoaded(ABYSSALCRAFT_MODID);
    }

    private static ResourceTypeContainer container(String path, ResourceFactory factory) {
        ResourceTypeContainer container = new ResourceTypeContainer() {
            private final ResourceType<?, ?, ?> resourceType = factory.create(this);

            @Override
            public ResourceType<?, ?, ?> get() {
                return resourceType;
            }
        };
        container.setRegistryName(new ResourceLocation("sfm", path));
        return container;
    }

    private static SFMBlockCapabilityProviderContainer provider(String path, SFMBlockCapabilityProvider<?> provider) {
        SFMBlockCapabilityProviderContainer container = new SFMBlockCapabilityProviderContainer() {
            @Override
            public SFMBlockCapabilityProvider<?> get() {
                return provider;
            }
        };
        container.setRegistryName(new ResourceLocation(Reference.MOD_ID, path));
        return container;
    }

    private static void registerCapabilities() {
        CapabilityManager.INSTANCE.register(GpomManaStorage.class, new EmptyStorage<GpomManaStorage>(), emptyMana());
        CapabilityManager.INSTANCE.register(GpomAspectHandler.class, new EmptyStorage<GpomAspectHandler>(), emptyAspect());
        CapabilityManager.INSTANCE.register(GpomPotentialEnergyStorage.class, new EmptyStorage<GpomPotentialEnergyStorage>(), emptyPotentialEnergy());
    }

    private static Callable<GpomManaStorage> emptyMana() {
        return EmptyManaStorage::new;
    }

    private static Callable<GpomAspectHandler> emptyAspect() {
        return EmptyAspectHandler::new;
    }

    private static Callable<GpomPotentialEnergyStorage> emptyPotentialEnergy() {
        return EmptyPotentialEnergyStorage::new;
    }

    private interface ResourceFactory {
        ResourceTypeContainer.ResourceType<?, ?, ?> create(ResourceTypeContainer container);
    }

    private static final class EmptyStorage<T> implements Capability.IStorage<T> {
        @Override
        public NBTBase writeNBT(Capability<T> capability, T instance, EnumFacing side) {
            return null;
        }

        @Override
        public void readNBT(Capability<T> capability, T instance, EnumFacing side, NBTBase nbt) {
        }
    }

    private static final class EmptyManaStorage implements GpomManaStorage {
        @Override public int getMana() { return 0; }
        @Override public int getMaxMana() { return 0; }
        @Override public boolean canReceiveMana() { return false; }
        @Override public boolean canExtractMana() { return false; }
        @Override public int receiveMana(int amount, boolean simulate) { return 0; }
        @Override public int extractMana(int amount, boolean simulate) { return 0; }
    }

    private static final class EmptyAspectHandler implements GpomAspectHandler {
        @Override public int getSlots() { return 0; }
        @Override public ThaumcraftAspectResourceType.AspectStack getStackInSlot(int slot) { return ThaumcraftAspectResourceType.AspectStack.EMPTY; }
        @Override public ThaumcraftAspectResourceType.AspectStack extract(int slot, long amount, boolean simulate) { return ThaumcraftAspectResourceType.AspectStack.EMPTY; }
        @Override public ThaumcraftAspectResourceType.AspectStack insert(int slot, ThaumcraftAspectResourceType.AspectStack stack, boolean simulate) { return stack; }
        @Override public long getMaxAmount(int slot) { return 0; }
    }

    private static final class EmptyPotentialEnergyStorage implements GpomPotentialEnergyStorage {
        @Override public int getEnergy() { return 0; }
        @Override public int getMaxEnergy() { return 0; }
        @Override public boolean canReceiveEnergy() { return false; }
        @Override public boolean canExtractEnergy() { return false; }
        @Override public int receiveEnergy(int amount, boolean simulate) { return 0; }
        @Override public int extractEnergy(int amount, boolean simulate) { return 0; }
    }
}
