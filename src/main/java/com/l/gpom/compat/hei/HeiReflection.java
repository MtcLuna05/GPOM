package com.l.gpom.compat.hei;

import com.l.gpom.util.ReflectionLookup;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

final class HeiReflection {
    private HeiReflection() {
    }

    @SuppressWarnings("unchecked")
    static List<Slot> containerSlots(Container container) {
        Object value = fieldValue(container, "field_75151_b", "inventorySlots");
        return value instanceof List ? (List<Slot>) value : Collections.emptyList();
    }

    static int containerWindowId(Container container) {
        Object value = fieldValue(container, "field_75152_c", "windowId");
        return value instanceof Integer ? (Integer) value : -1;
    }

    static Container openContainer(EntityPlayerMP player) {
        Object value = fieldValue(player, "field_71070_bA", "openContainer");
        return value instanceof Container ? (Container) value : null;
    }

    static Object slotInventory(Slot slot) {
        return fieldValue(slot, "field_75224_c", "inventory");
    }

    static EntityPlayerMP serverPlayer(MessageContext context) {
        if (context == null || context.getServerHandler() == null) {
            return null;
        }
        Object value = fieldValue(context.getServerHandler(), "field_147369_b", "player");
        return value instanceof EntityPlayerMP ? (EntityPlayerMP) value : null;
    }

    static void detectAndSendChanges(Container container) {
        invoke(container, new Class<?>[0], "func_75142_b", "detectAndSendChanges");
    }

    static void updateHeldItem(EntityPlayerMP player) {
        invoke(player, new Class<?>[0], "func_71113_k", "updateHeldItem");
    }

    static void sendContainerToPlayer(EntityPlayerMP player, Container container) {
        invoke(player, new Class<?>[] {Container.class}, "func_71110_a", "sendContainerToPlayer", container);
    }

    private static Object fieldValue(Object target, String... names) {
        if (target == null) {
            return null;
        }
        try {
            Field field = findField(target.getClass(), names);
            return field == null ? null : field.get(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> owner, String... names) {
        try {
            return ReflectionLookup.findField(owner, names);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object invoke(Object target, Class<?>[] parameterTypes, String srgName, String mcpName, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            Method method = findMethod(target.getClass(), parameterTypes, srgName, mcpName);
            return method == null ? null : method.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> owner, Class<?>[] parameterTypes, String... names) {
        try {
            return ReflectionLookup.findMethod(owner, names, parameterTypes);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
