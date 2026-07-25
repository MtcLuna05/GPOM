package com.l.gpom.compat.sfm.integration.resource;

import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer;
import com.l.gpom.compat.sfm.integration.SfmMagicalCapabilityIntegration;
import com.l.gpom.compat.sfm.integration.capability.GpomManaStorage;
import net.minecraft.util.ResourceLocation;

public final class BotaniaManaResourceType extends IntegerSfmResourceType<GpomManaStorage> {
    public static final SFMBlockCapabilityKind<GpomManaStorage> CAPABILITY_KIND =
            new SFMBlockCapabilityKind<GpomManaStorage>(() -> SfmMagicalCapabilityIntegration.MANA_CAPABILITY);

    public BotaniaManaResourceType(ResourceTypeContainer container) {
        super(container, CAPABILITY_KIND, new ResourceLocation("botania", "mana"));
    }

    @Override
    public GpomManaStorage createHandlerForBufferBlock(BufferBlockEntityContents contents) {
        return new BufferManaStorage(contents);
    }

    @Override
    public Integer getStackInSlot(GpomManaStorage storage, int slot) {
        return storage.getMana();
    }

    @Override
    public Integer extract(GpomManaStorage storage, int slot, long amount, boolean simulate) {
        return storage.extractMana(toInt(amount), simulate);
    }

    @Override
    public boolean canExtract(GpomManaStorage storage, int slot) {
        return storage.canExtractMana();
    }

    @Override
    public int getSlots(GpomManaStorage storage) {
        return 1;
    }

    @Override
    public long getMaxStackSizeForSlot(GpomManaStorage storage, int slot) {
        return storage.getMaxMana();
    }

    @Override
    public Integer insert(GpomManaStorage storage, int slot, Integer stack, boolean simulate) {
        return stack - storage.receiveMana(stack, simulate);
    }

    @Override
    public boolean canInsert(GpomManaStorage storage, int slot) {
        return storage.canReceiveMana();
    }

    @Override
    public boolean matchesCapabilityHandler(Object handler) {
        return handler instanceof GpomManaStorage;
    }

    private static final class BufferManaStorage implements GpomManaStorage {
        private final BufferScalarStorage storage;

        private BufferManaStorage(BufferBlockEntityContents contents) {
            storage = new BufferScalarStorage(contents, contents.tier.getIntScalarMaxStackSize());
        }

        @Override public int getMana() { return storage.getAmount(); }
        @Override public int getMaxMana() { return storage.getCapacity(); }
        @Override public boolean canReceiveMana() { return storage.canReceive(); }
        @Override public boolean canExtractMana() { return true; }
        @Override public int receiveMana(int amount, boolean simulate) { return storage.receive(amount, simulate); }
        @Override public int extractMana(int amount, boolean simulate) { return storage.extract(amount, simulate); }
    }
}
