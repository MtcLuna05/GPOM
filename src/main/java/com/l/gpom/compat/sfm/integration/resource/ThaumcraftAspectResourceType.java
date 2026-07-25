package com.l.gpom.compat.sfm.integration.resource;

import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer;
import com.l.gpom.compat.sfm.integration.SfmMagicalCapabilityIntegration;
import com.l.gpom.compat.sfm.integration.capability.GpomAspectHandler;
import net.minecraft.util.ResourceLocation;
import thaumcraft.api.aspects.Aspect;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ThaumcraftAspectResourceType extends ResourceTypeContainer.ResourceType<ThaumcraftAspectResourceType.AspectStack, Aspect, GpomAspectHandler> {
    public static final SFMBlockCapabilityKind<GpomAspectHandler> CAPABILITY_KIND =
            new SFMBlockCapabilityKind<GpomAspectHandler>(() -> SfmMagicalCapabilityIntegration.ASPECT_CAPABILITY);

    public ThaumcraftAspectResourceType(ResourceTypeContainer container) {
        super(container, CAPABILITY_KIND);
    }

    @Override
    public GpomAspectHandler createHandlerForBufferBlock(BufferBlockEntityContents contents) {
        return new BufferAspectHandler(contents, contents.tier.getIntMaxStackSize());
    }

    @Override
    public long getAmount(AspectStack stack) {
        return stack.amount;
    }

    @Override
    public AspectStack getStackInSlot(GpomAspectHandler handler, int slot) {
        return handler.getStackInSlot(slot);
    }

    @Override
    public AspectStack extract(GpomAspectHandler handler, int slot, long amount, boolean simulate) {
        return handler.extract(slot, amount, simulate);
    }

    @Override
    public int getSlots(GpomAspectHandler handler) {
        return handler.getSlots();
    }

    @Override
    public long getMaxStackSize(AspectStack stack) {
        return Integer.MAX_VALUE;
    }

    @Override
    public long getMaxStackSizeForSlot(GpomAspectHandler handler, int slot) {
        return handler.getMaxAmount(slot);
    }

    @Override
    public AspectStack insert(GpomAspectHandler handler, int slot, AspectStack stack, boolean simulate) {
        return handler.insert(slot, stack, simulate);
    }

    @Override
    public boolean isEmpty(AspectStack stack) {
        return stack == null || stack.isEmpty();
    }

    @Override
    public AspectStack getEmptyStack() {
        return AspectStack.EMPTY;
    }

    @Override
    public boolean matchesStackType(Object stack) {
        return stack instanceof AspectStack;
    }

    @Override
    public boolean matchesCapabilityHandler(Object handler) {
        return handler instanceof GpomAspectHandler;
    }

    @Override
    public Stream<ResourceLocation> getTagsForStack(AspectStack stack) {
        return Stream.empty();
    }

    @Override
    public boolean registryKeyExists(ResourceLocation key) {
        return getItemFromRegistryKey(key) != null;
    }

    @Override
    public ResourceLocation getRegistryKeyForStack(AspectStack stack) {
        return getRegistryKeyForItem(stack.aspect);
    }

    @Override
    public ResourceLocation getRegistryKeyForItem(Aspect aspect) {
        return aspect == null ? new ResourceLocation("thaumcraft", "empty") : new ResourceLocation("thaumcraft", aspect.getTag());
    }

    @Override
    public Aspect getItemFromRegistryKey(ResourceLocation key) {
        if (key == null || !"thaumcraft".equals(key.getNamespace())) {
            return null;
        }
        return Aspect.getAspect(key.getPath());
    }

    @Override
    public Set<ResourceLocation> getRegistryKeys() {
        return Aspect.aspects.values().stream()
                .map(this::getRegistryKeyForItem)
                .collect(Collectors.toSet());
    }

    @Override
    public Iterable<Aspect> getItems() {
        return Collections.unmodifiableCollection(Aspect.aspects.values());
    }

    @Override
    public Aspect getItem(AspectStack stack) {
        return stack.aspect;
    }

    @Override
    public AspectStack copy(AspectStack stack) {
        return stack == null ? AspectStack.EMPTY : new AspectStack(stack.aspect, stack.amount);
    }

    @Override
    public AspectStack withCount(AspectStack stack, long count) {
        return new AspectStack(stack.aspect, count > Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, (int) count));
    }

    @Override
    protected AspectStack setCount(AspectStack stack, long count) {
        return withCount(stack, count);
    }

    @Override
    public Optional<Long> getMetaForStack(AspectStack stack) {
        return Optional.empty();
    }

    public static final class AspectStack {
        public static final AspectStack EMPTY = new AspectStack(null, 0);

        public final Aspect aspect;
        public final int amount;

        public AspectStack(Aspect aspect, int amount) {
            this.aspect = aspect;
            this.amount = Math.max(0, amount);
        }

        public boolean isEmpty() {
            return aspect == null || amount <= 0;
        }
    }

    private static final class BufferAspectHandler implements GpomAspectHandler {
        private final BufferBlockEntityContents contents;
        private final int capacity;
        private AspectStack stack = AspectStack.EMPTY;

        private BufferAspectHandler(BufferBlockEntityContents contents, int capacity) {
            this.contents = contents;
            this.capacity = capacity;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public AspectStack getStackInSlot(int slot) {
            return slot == 0 ? stack : AspectStack.EMPTY;
        }

        @Override
        public AspectStack extract(int slot, long amount, boolean simulate) {
            if (slot != 0 || stack.isEmpty()) {
                return AspectStack.EMPTY;
            }
            int extracted = amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.min(stack.amount, Math.max(0, (int) amount));
            AspectStack result = new AspectStack(stack.aspect, extracted);
            if (!simulate && extracted > 0) {
                stack = new AspectStack(stack.aspect, stack.amount - extracted);
            }
            return result;
        }

        @Override
        public AspectStack insert(int slot, AspectStack incoming, boolean simulate) {
            if (slot != 0 || incoming == null || incoming.isEmpty()) {
                return incoming;
            }
            if (!stack.isEmpty() && stack.aspect != incoming.aspect) {
                return incoming;
            }
            if (stack.isEmpty() && !contents.isEmpty()) {
                return incoming;
            }
            int accepted = Math.min(incoming.amount, capacity - stack.amount);
            if (!simulate && accepted > 0) {
                stack = new AspectStack(incoming.aspect, stack.amount + accepted);
            }
            return new AspectStack(incoming.aspect, incoming.amount - accepted);
        }

        @Override
        public long getMaxAmount(int slot) {
            return slot == 0 ? capacity : 0;
        }
    }
}
