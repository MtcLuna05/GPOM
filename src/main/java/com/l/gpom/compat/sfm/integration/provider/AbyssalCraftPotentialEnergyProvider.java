package com.l.gpom.compat.sfm.integration.provider;

import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityProvider;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import com.l.gpom.compat.sfm.integration.capability.GpomPotentialEnergyStorage;
import com.l.gpom.compat.sfm.integration.resource.AbyssalCraftPotentialEnergyResourceType;
import com.shinoow.abyssalcraft.api.energy.IEnergyContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class AbyssalCraftPotentialEnergyProvider implements SFMBlockCapabilityProvider<GpomPotentialEnergyStorage> {
    @Override
    public boolean matchesCapabilityKind(SFMBlockCapabilityKind<?> capabilityKind) {
        return AbyssalCraftPotentialEnergyResourceType.CAPABILITY_KIND.equals(capabilityKind);
    }

    @Override
    public SFMBlockCapabilityResult<GpomPotentialEnergyStorage> getCapability(SFMBlockCapabilityKind<GpomPotentialEnergyStorage> kind,
                                                                              World world,
                                                                              BlockPos pos,
                                                                              IBlockState state,
                                                                              TileEntity tile,
                                                                              EnumFacing side) {
        if (tile instanceof IEnergyContainer) {
            return SFMBlockCapabilityResult.of(new PotentialEnergyStorage((IEnergyContainer) tile));
        }
        return SFMBlockCapabilityResult.empty();
    }

    @Override
    public int priority() {
        return 100;
    }

    private static final class PotentialEnergyStorage implements GpomPotentialEnergyStorage {
        private final IEnergyContainer container;

        private PotentialEnergyStorage(IEnergyContainer container) {
            this.container = container;
        }

        @Override
        public int getEnergy() {
            return Math.max(0, (int) Math.floor(container.getContainedEnergy()));
        }

        @Override
        public int getMaxEnergy() {
            return Math.max(0, container.getMaxEnergy());
        }

        @Override
        public boolean canReceiveEnergy() {
            return container.canAcceptPE();
        }

        @Override
        public boolean canExtractEnergy() {
            return container.canTransferPE();
        }

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            if (amount <= 0 || !canReceiveEnergy()) {
                return 0;
            }
            int accepted = Math.min(amount, Math.max(0, getMaxEnergy() - getEnergy()));
            if (!simulate && accepted > 0) {
                container.addEnergy(accepted);
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            if (amount <= 0 || !canExtractEnergy()) {
                return 0;
            }
            int extracted = Math.min(amount, getEnergy());
            if (!simulate && extracted > 0) {
                extracted = Math.max(0, (int) Math.floor(container.consumeEnergy(extracted)));
            }
            return extracted;
        }
    }
}
