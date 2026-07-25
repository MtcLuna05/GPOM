package com.l.gpom.compat.sfm.integration.provider;

import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityProvider;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import com.l.gpom.compat.sfm.integration.capability.GpomAspectHandler;
import com.l.gpom.compat.sfm.integration.resource.ThaumcraftAspectResourceType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aspects.IEssentiaTransport;

public final class ThaumcraftAspectProvider implements SFMBlockCapabilityProvider<GpomAspectHandler> {
    @Override
    public boolean matchesCapabilityKind(SFMBlockCapabilityKind<?> capabilityKind) {
        return ThaumcraftAspectResourceType.CAPABILITY_KIND.equals(capabilityKind);
    }

    @Override
    public SFMBlockCapabilityResult<GpomAspectHandler> getCapability(SFMBlockCapabilityKind<GpomAspectHandler> kind,
                                                                     World world,
                                                                     BlockPos pos,
                                                                     IBlockState state,
                                                                     TileEntity tile,
                                                                     EnumFacing side) {
        if (tile instanceof IEssentiaTransport && ((IEssentiaTransport) tile).isConnectable(side)) {
            return SFMBlockCapabilityResult.of(new EssentiaTransportAspectHandler((IEssentiaTransport) tile, side));
        }
        if (tile instanceof IAspectContainer) {
            return SFMBlockCapabilityResult.of(new AspectContainerHandler((IAspectContainer) tile));
        }
        return SFMBlockCapabilityResult.empty();
    }

    @Override
    public int priority() {
        return 100;
    }

    private static final class AspectContainerHandler implements GpomAspectHandler {
        private final IAspectContainer container;

        private AspectContainerHandler(IAspectContainer container) {
            this.container = container;
        }

        @Override
        public int getSlots() {
            Aspect[] aspects = aspects();
            return Math.max(1, aspects.length);
        }

        @Override
        public ThaumcraftAspectResourceType.AspectStack getStackInSlot(int slot) {
            Aspect[] aspects = aspects();
            if (slot < 0 || slot >= aspects.length) {
                return ThaumcraftAspectResourceType.AspectStack.EMPTY;
            }
            Aspect aspect = aspects[slot];
            return new ThaumcraftAspectResourceType.AspectStack(aspect, container.containerContains(aspect));
        }

        @Override
        public ThaumcraftAspectResourceType.AspectStack extract(int slot, long amount, boolean simulate) {
            ThaumcraftAspectResourceType.AspectStack current = getStackInSlot(slot);
            if (current.isEmpty() || amount <= 0) {
                return ThaumcraftAspectResourceType.AspectStack.EMPTY;
            }
            int extracted = amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.min(current.amount, (int) amount);
            if (!simulate && extracted > 0 && !container.takeFromContainer(current.aspect, extracted)) {
                return ThaumcraftAspectResourceType.AspectStack.EMPTY;
            }
            return new ThaumcraftAspectResourceType.AspectStack(current.aspect, extracted);
        }

        @Override
        public ThaumcraftAspectResourceType.AspectStack insert(int slot, ThaumcraftAspectResourceType.AspectStack stack, boolean simulate) {
            if (stack == null || stack.isEmpty() || !container.doesContainerAccept(stack.aspect)) {
                return stack;
            }
            if (slot >= 0) {
                ThaumcraftAspectResourceType.AspectStack current = getStackInSlot(slot);
                if (!current.isEmpty() && current.aspect != stack.aspect) {
                    return stack;
                }
            }
            if (simulate) {
                return ThaumcraftAspectResourceType.AspectStack.EMPTY;
            }
            int remainder = container.addToContainer(stack.aspect, stack.amount);
            return new ThaumcraftAspectResourceType.AspectStack(stack.aspect, remainder);
        }

        @Override
        public long getMaxAmount(int slot) {
            return Integer.MAX_VALUE;
        }

        private Aspect[] aspects() {
            AspectList list = container.getAspects();
            return list == null ? new Aspect[0] : list.getAspects();
        }
    }

    private static final class EssentiaTransportAspectHandler implements GpomAspectHandler {
        private final IEssentiaTransport transport;
        private final EnumFacing side;

        private EssentiaTransportAspectHandler(IEssentiaTransport transport, EnumFacing side) {
            this.transport = transport;
            this.side = side;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ThaumcraftAspectResourceType.AspectStack getStackInSlot(int slot) {
            if (slot != 0) {
                return ThaumcraftAspectResourceType.AspectStack.EMPTY;
            }
            Aspect aspect = transport.getEssentiaType(side);
            int amount = transport.getEssentiaAmount(side);
            return new ThaumcraftAspectResourceType.AspectStack(aspect, amount);
        }

        @Override
        public ThaumcraftAspectResourceType.AspectStack extract(int slot, long amount, boolean simulate) {
            ThaumcraftAspectResourceType.AspectStack current = getStackInSlot(slot);
            if (slot != 0 || current.isEmpty() || amount <= 0 || !transport.canOutputTo(side)) {
                return ThaumcraftAspectResourceType.AspectStack.EMPTY;
            }
            int requested = amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.min(current.amount, (int) amount);
            int extracted = simulate ? requested : transport.takeEssentia(current.aspect, requested, side);
            return new ThaumcraftAspectResourceType.AspectStack(current.aspect, extracted);
        }

        @Override
        public ThaumcraftAspectResourceType.AspectStack insert(int slot, ThaumcraftAspectResourceType.AspectStack stack, boolean simulate) {
            if (slot != 0 || stack == null || stack.isEmpty() || !transport.canInputFrom(side)) {
                return stack;
            }
            if (simulate) {
                return ThaumcraftAspectResourceType.AspectStack.EMPTY;
            }
            int accepted = transport.addEssentia(stack.aspect, stack.amount, side);
            return new ThaumcraftAspectResourceType.AspectStack(stack.aspect, Math.max(0, stack.amount - accepted));
        }

        @Override
        public long getMaxAmount(int slot) {
            return Integer.MAX_VALUE;
        }
    }
}
