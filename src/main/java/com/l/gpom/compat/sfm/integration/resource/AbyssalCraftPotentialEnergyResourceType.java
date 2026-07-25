package com.l.gpom.compat.sfm.integration.resource;

import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer;
import com.l.gpom.compat.sfm.integration.SfmMagicalCapabilityIntegration;
import com.l.gpom.compat.sfm.integration.capability.GpomPotentialEnergyStorage;
import net.minecraft.util.ResourceLocation;

public final class AbyssalCraftPotentialEnergyResourceType extends IntegerSfmResourceType<GpomPotentialEnergyStorage> {
    public static final SFMBlockCapabilityKind<GpomPotentialEnergyStorage> CAPABILITY_KIND =
            new SFMBlockCapabilityKind<GpomPotentialEnergyStorage>(() -> SfmMagicalCapabilityIntegration.PE_CAPABILITY);

    public AbyssalCraftPotentialEnergyResourceType(ResourceTypeContainer container) {
        super(container, CAPABILITY_KIND, new ResourceLocation("abyssalcraft", "pe"));
    }

    @Override
    public GpomPotentialEnergyStorage createHandlerForBufferBlock(BufferBlockEntityContents contents) {
        return new BufferPotentialEnergyStorage(contents);
    }

    @Override
    public Integer getStackInSlot(GpomPotentialEnergyStorage storage, int slot) {
        return storage.getEnergy();
    }

    @Override
    public Integer extract(GpomPotentialEnergyStorage storage, int slot, long amount, boolean simulate) {
        return storage.extractEnergy(toInt(amount), simulate);
    }

    @Override
    public boolean canExtract(GpomPotentialEnergyStorage storage, int slot) {
        return storage.canExtractEnergy();
    }

    @Override
    public int getSlots(GpomPotentialEnergyStorage storage) {
        return 1;
    }

    @Override
    public long getMaxStackSizeForSlot(GpomPotentialEnergyStorage storage, int slot) {
        return storage.getMaxEnergy();
    }

    @Override
    public Integer insert(GpomPotentialEnergyStorage storage, int slot, Integer stack, boolean simulate) {
        return stack - storage.receiveEnergy(stack, simulate);
    }

    @Override
    public boolean canInsert(GpomPotentialEnergyStorage storage, int slot) {
        return storage.canReceiveEnergy();
    }

    @Override
    public boolean matchesCapabilityHandler(Object handler) {
        return handler instanceof GpomPotentialEnergyStorage;
    }

    private static final class BufferPotentialEnergyStorage implements GpomPotentialEnergyStorage {
        private final BufferScalarStorage storage;

        private BufferPotentialEnergyStorage(BufferBlockEntityContents contents) {
            storage = new BufferScalarStorage(contents, contents.tier.getIntScalarMaxStackSize());
        }

        @Override public int getEnergy() { return storage.getAmount(); }
        @Override public int getMaxEnergy() { return storage.getCapacity(); }
        @Override public boolean canReceiveEnergy() { return storage.canReceive(); }
        @Override public boolean canExtractEnergy() { return true; }
        @Override public int receiveEnergy(int amount, boolean simulate) { return storage.receive(amount, simulate); }
        @Override public int extractEnergy(int amount, boolean simulate) { return storage.extract(amount, simulate); }
    }
}
