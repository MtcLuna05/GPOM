package com.l.gpom.compat.sfm.integration.resource;

import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.resourcetype.IntegerResourceType;
import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer;
import net.minecraft.util.ResourceLocation;

public abstract class IntegerSfmResourceType<CAP> extends IntegerResourceType<CAP> {
    protected IntegerSfmResourceType(ResourceTypeContainer container, SFMBlockCapabilityKind<CAP> capabilityKind,
                                     ResourceLocation registryKey) {
        super(container, capabilityKind, registryKey);
    }

    protected static int toInt(long amount) {
        return amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, (int) amount);
    }

    public static final class BufferScalarStorage {
        private final BufferBlockEntityContents contents;
        private final int capacity;
        private int amount;

        public BufferScalarStorage(BufferBlockEntityContents contents, int capacity) {
            this.contents = contents;
            this.capacity = capacity;
        }

        public int getAmount() {
            return amount;
        }

        public int getCapacity() {
            return capacity;
        }

        public boolean canReceive() {
            return amount > 0 || contents.isEmpty();
        }

        public int receive(int requested, boolean simulate) {
            if (!canReceive()) {
                return 0;
            }
            int accepted = Math.min(Math.max(0, requested), Math.max(0, capacity - amount));
            if (!simulate && accepted > 0) {
                amount += accepted;
            }
            return accepted;
        }

        public int extract(int requested, boolean simulate) {
            int extracted = Math.min(Math.max(0, requested), amount);
            if (!simulate && extracted > 0) {
                amount -= extracted;
            }
            return extracted;
        }
    }
}
