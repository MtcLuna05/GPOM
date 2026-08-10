package com.l.gpom.optimization;

import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Runtime helpers used by optional-mod transformers without eagerly linking their classes. */
public final class OptionalModExceptionLoopOptimizations {
    private static final Object MISSING_METHOD = new Object();
    private static final Object MISSING_FIELD = new Object();
    private static final Object MISSING_HAMMERCORE_QUARK_COLOR = new Object();
    private static final ConcurrentMap<Class<?>, Object> AE2_POWER_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<FieldLookupKey, Object> INHERITED_FIELDS = new ConcurrentHashMap<>();
    private static volatile Object hammerCoreQuarkColorAccess;

    private OptionalModExceptionLoopOptimizations() {
    }

    public static Field findInheritedDeclaredFieldOrNull(Class<?> owner, String name) {
        if (owner == null || name == null) {
            return null;
        }
        FieldLookupKey key = new FieldLookupKey(owner, name);
        Object cached = INHERITED_FIELDS.get(key);
        if (cached == null) {
            cached = resolveInheritedDeclaredField(owner, name);
            Object raced = INHERITED_FIELDS.putIfAbsent(key, cached);
            if (raced != null) {
                cached = raced;
            }
        }
        return cached == MISSING_FIELD ? null : (Field) cached;
    }

    public static boolean hammerCoreUsesCustomEnchantColor(Object stack) {
        if (!(stack instanceof ItemStack)) {
            return false;
        }
        Item item = MinecraftMappingCompat.itemStackItem((ItemStack) stack);
        return item != null && implementsNamedType(
                item.getClass(),
                "com.zeitheron.hammercore.internal.items.ICustomEnchantColorItem"
        );
    }

    public static Integer hammerCoreQuarkColor(Object stack, int previousColor) {
        if (stack == null) {
            return null;
        }
        Object access = hammerCoreQuarkColorAccess;
        if (access == null) {
            access = resolveHammerCoreQuarkColorAccess(stack.getClass().getClassLoader(), stack.getClass());
            hammerCoreQuarkColorAccess = access;
        }
        if (access == MISSING_HAMMERCORE_QUARK_COLOR) {
            return null;
        }
        try {
            Method[] methods = (Method[]) access;
            methods[0].invoke(null, stack);
            Object color = methods[1].invoke(null, previousColor);
            return color instanceof Number ? ((Number) color).intValue() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object resolveInheritedDeclaredField(Class<?> owner, String name) {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException | SecurityException ignored) {
            }
        }
        return MISSING_FIELD;
    }

    private static Object resolveHammerCoreQuarkColorAccess(ClassLoader loader, Class<?> stackClass) {
        try {
            Class<?> colorRunes = Class.forName("vazkii.quark.misc.feature.ColorRunes", false, loader);
            Method setTargetStack = null;
            Method getTargetColor = null;
            for (Method method : colorRunes.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                Class<?>[] parameters = method.getParameterTypes();
                if ("setTargetStack".equals(method.getName())
                        && parameters.length == 1
                        && parameters[0].isAssignableFrom(stackClass)) {
                    setTargetStack = method;
                } else if ("getTargetColor".equals(method.getName())
                        && parameters.length == 1
                        && parameters[0] == int.class
                        && (method.getReturnType() == int.class || Number.class.isAssignableFrom(method.getReturnType()))) {
                    getTargetColor = method;
                }
            }
            if (setTargetStack != null && getTargetColor != null) {
                return new Method[] {setTargetStack, getTargetColor};
            }
        } catch (Throwable ignored) {
        }
        return MISSING_HAMMERCORE_QUARK_COLOR;
    }

    private static boolean implementsNamedType(Class<?> type, String typeName) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Class<?> implemented : current.getInterfaces()) {
                if (typeName.equals(implemented.getName()) || implementsNamedType(implemented, typeName)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Object ae2BdlibPowerSlot(Object tile) {
        if (tile == null) {
            return null;
        }
        Class<?> tileClass = tile.getClass();
        Object cached = AE2_POWER_METHODS.get(tileClass);
        if (cached == null) {
            cached = resolvePowerMethod(tileClass);
            Object raced = AE2_POWER_METHODS.putIfAbsent(tileClass, cached);
            if (raced != null) {
                cached = raced;
            }
        }
        if (cached == MISSING_METHOD) {
            return null;
        }
        try {
            return ((Method) cached).invoke(tile);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object resolvePowerMethod(Class<?> tileClass) {
        try {
            return tileClass.getMethod("power");
        } catch (NoSuchMethodException | SecurityException ignored) {
            return MISSING_METHOD;
        }
    }

    private static final class FieldLookupKey {
        private final Class<?> owner;
        private final String name;
        private final int hash;

        private FieldLookupKey(Class<?> owner, String name) {
            this.owner = owner;
            this.name = name;
            this.hash = 31 * System.identityHashCode(owner) + name.hashCode();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof FieldLookupKey)) {
                return false;
            }
            FieldLookupKey other = (FieldLookupKey) object;
            return owner == other.owner && name.equals(other.name);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
