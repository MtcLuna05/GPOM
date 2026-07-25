package com.l.gpom.compat.sfm.integration.provider;

import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityProvider;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.compat.sfm.integration.capability.GpomManaStorage;
import com.l.gpom.compat.sfm.integration.resource.BotaniaManaResourceType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import vazkii.botania.api.mana.IManaBlock;
import vazkii.botania.api.mana.IManaCollector;
import vazkii.botania.api.mana.IManaPool;
import vazkii.botania.api.mana.IManaReceiver;

public final class BotaniaManaProvider implements SFMBlockCapabilityProvider<GpomManaStorage> {
    @Override
    public boolean matchesCapabilityKind(SFMBlockCapabilityKind<?> capabilityKind) {
        return BotaniaManaResourceType.CAPABILITY_KIND.equals(capabilityKind);
    }

    @Override
    public SFMBlockCapabilityResult<GpomManaStorage> getCapability(SFMBlockCapabilityKind<GpomManaStorage> kind,
                                                                   World world,
                                                                   BlockPos pos,
                                                                   IBlockState state,
                                                                   TileEntity tile,
                                                                   EnumFacing side) {
        if (tile instanceof IManaReceiver) {
            return SFMBlockCapabilityResult.of(new ReceiverManaStorage((IManaReceiver) tile, tile));
        }
        if (tile instanceof IManaBlock) {
            return SFMBlockCapabilityResult.of(new ReadOnlyManaStorage((IManaBlock) tile));
        }
        return SFMBlockCapabilityResult.empty();
    }

    @Override
    public int priority() {
        return 100;
    }

    private static final class ReceiverManaStorage implements GpomManaStorage {
        private final IManaReceiver receiver;
        private final TileEntity tile;

        private ReceiverManaStorage(IManaReceiver receiver, TileEntity tile) {
            this.receiver = receiver;
            this.tile = tile;
        }

        @Override
        public int getMana() {
            return Math.max(0, receiver.getCurrentMana());
        }

        @Override
        public int getMaxMana() {
            if (receiver instanceof IManaCollector) {
                return Math.max(0, ((IManaCollector) receiver).getMaxMana());
            }
            return receiver.isFull() ? getMana() : Integer.MAX_VALUE;
        }

        @Override
        public boolean canReceiveMana() {
            return !receiver.isFull();
        }

        @Override
        public boolean canExtractMana() {
            return receiver instanceof IManaPool && getMana() > 0;
        }

        @Override
        public int receiveMana(int amount, boolean simulate) {
            if (amount <= 0 || !canReceiveMana()) {
                return 0;
            }
            int accepted = Math.min(amount, Math.max(0, getMaxMana() - getMana()));
            if (!simulate && accepted > 0) {
                receiver.recieveMana(accepted);
                markDirty();
            }
            return accepted;
        }

        @Override
        public int extractMana(int amount, boolean simulate) {
            if (amount <= 0 || !canExtractMana()) {
                return 0;
            }
            int extracted = Math.min(amount, getMana());
            if (!simulate && extracted > 0) {
                receiver.recieveMana(-extracted);
                markDirty();
            }
            return extracted;
        }

        private void markDirty() {
            MinecraftMappingCompat.tileEntityMarkDirty(tile);
            World world = MinecraftMappingCompat.tileEntityWorld(tile);
            BlockPos pos = MinecraftMappingCompat.tileEntityPos(tile);
            if (world == null || pos == null) {
                return;
            }
            IBlockState state = MinecraftMappingCompat.worldBlockState(world, pos);
            if (state != null) {
                MinecraftMappingCompat.worldNotifyBlockUpdate(world, pos, state, state, 3);
            }
        }
    }

    private static final class ReadOnlyManaStorage implements GpomManaStorage {
        private final IManaBlock manaBlock;

        private ReadOnlyManaStorage(IManaBlock manaBlock) {
            this.manaBlock = manaBlock;
        }

        @Override public int getMana() { return Math.max(0, manaBlock.getCurrentMana()); }
        @Override public int getMaxMana() { return getMana(); }
        @Override public boolean canReceiveMana() { return false; }
        @Override public boolean canExtractMana() { return false; }
        @Override public int receiveMana(int amount, boolean simulate) { return 0; }
        @Override public int extractMana(int amount, boolean simulate) { return 0; }
    }
}
