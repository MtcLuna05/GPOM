package com.l.gpom.compat.thaumcraft;

import com.l.gpom.GPOM;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ThaumcraftResearchClientProbe {
    private static final String FIRST_STEPS = "FIRSTSTEPS";
    private static final String GOT_THAUMONOMICON = "!gotthaumonomicon";
    private static final String FIRST_STEPS_CRAFT_REFERENCE = "[#]405059183";
    private static final int LOG_LIMIT = Integer.getInteger("gpom.thaumcraftClientProbeLogLimit", 80);
    private static final ThaumcraftResearchClientProbe INSTANCE = new ThaumcraftResearchClientProbe();

    private static boolean registered;
    private static boolean disabled;
    private static int logCount;
    private static String lastScreenClass = "";
    private static int ticksSinceProbe;

    private static volatile Method getMinecraftMethod;
    private static volatile Field currentScreenField;
    private static volatile Field playerField;
    private static volatile Method getKnowledgeMethod;
    private static volatile Method isResearchKnownMethod;
    private static volatile Method isResearchCompleteMethod;
    private static volatile Method getResearchStageMethod;
    private static volatile Method getResearchListMethod;
    private static volatile Method getResearchMethod;
    private static volatile Method getResearchCategoryMethod;
    private static volatile Method knowsResearchStrictMethod;

    private ThaumcraftResearchClientProbe() {
    }

    public static void register() {
        if (registered || !Loader.isModLoaded("thaumcraft")) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        GPOM.LOGGER.info("[Thaumcraft Client Probe] Registered research browser/client knowledge probe");
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (disabled || event == null || event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            Object minecraft = minecraft();
            Object screen = currentScreen(minecraft);
            Object player = clientPlayer(minecraft);
            if (screen == null || player == null) {
                lastScreenClass = "";
                ticksSinceProbe = 0;
                return;
            }
            String screenClass = screen.getClass().getName();
            if (!screenClass.equals("thaumcraft.client.gui.GuiResearchBrowser")
                    && !screenClass.equals("thaumcraft.client.gui.GuiResearchPage")) {
                lastScreenClass = screenClass;
                ticksSinceProbe = 0;
                return;
            }
            ticksSinceProbe++;
            if (!screenClass.equals(lastScreenClass) || ticksSinceProbe % 60 == 1) {
                lastScreenClass = screenClass;
                probe(screen, player);
            }
        } catch (ReflectiveOperationException throwable) {
            disabled = true;
            GPOM.LOGGER.warn("[Thaumcraft Client Probe] Disabled after reflective failure", throwable);
        } catch (Throwable throwable) {
            disabled = true;
            GPOM.LOGGER.warn("[Thaumcraft Client Probe] Disabled after failure", throwable);
        }
    }

    private static void probe(Object screen, Object player) throws ReflectiveOperationException {
        if (logCount >= LOG_LIMIT) {
            return;
        }
        logCount++;
        Object knowledge = knowledge(player);
        boolean gotThaumonomicon = isResearchKnown(knowledge, GOT_THAUMONOMICON);
        boolean firstKnown = isResearchKnown(knowledge, FIRST_STEPS);
        boolean firstComplete = isResearchComplete(knowledge, FIRST_STEPS);
        int firstStage = getResearchStage(knowledge, FIRST_STEPS);
        boolean craftRefKnown = isResearchKnown(knowledge, FIRST_STEPS_CRAFT_REFERENCE);
        Set<?> researchList = researchList(knowledge);
        Object firstEntry = research(FIRST_STEPS);

        GPOM.LOGGER.info(
                "[Thaumcraft Client Probe] screen={} gotThaumonomicon={} firstKnown={} firstComplete={} firstStage={} craftRefKnown={} researchListSize={} hasFirstInList={} hasTildeFirst={} firstEntry={}",
                screen.getClass().getName(),
                gotThaumonomicon,
                firstKnown,
                firstComplete,
                firstStage,
                craftRefKnown,
                researchList == null ? -1 : researchList.size(),
                researchList != null && researchList.contains(FIRST_STEPS),
                researchList != null && researchList.contains("~" + FIRST_STEPS),
                describeResearchEntry(firstEntry)
        );

        if (screen.getClass().getName().equals("thaumcraft.client.gui.GuiResearchBrowser")) {
            probeBrowser(screen, player);
        } else {
            probePage(screen);
        }
    }

    private static void probeBrowser(Object screen, Object player) throws ReflectiveOperationException {
        String selectedCategory = String.valueOf(fieldValue(screen.getClass(), screen, "selectedCategory"));
        Collection<?> renderedResearch = collectionField(screen, "research");
        Collection<?> categoriesTC = collectionField(screen, "categoriesTC");
        Collection<?> categoriesOther = collectionField(screen, "categoriesOther");
        Collection<?> invisible = collectionField(screen, "invisible");
        Object firstEntry = research(FIRST_STEPS);
        Object firstCategory = firstEntry == null ? null : researchCategory(String.valueOf(invoke(firstEntry, "getCategory")));

        GPOM.LOGGER.info(
                "[Thaumcraft Client Probe] browser selectedCategory={} visibleResearchCount={} categoriesTC={} categoriesOther={} invisibleContainsFirst={} firstInVisibleList={} firstCategory={}",
                selectedCategory,
                renderedResearch == null ? -1 : renderedResearch.size(),
                categoriesTC,
                categoriesOther,
                invisible != null && invisible.contains(FIRST_STEPS),
                containsResearchKey(renderedResearch, FIRST_STEPS),
                describeCategory(firstCategory, player)
        );
    }

    private static void probePage(Object screen) throws ReflectiveOperationException {
        Object entry = fieldValue(screen.getClass(), screen, "research");
        Object currentStage = fieldValue(screen.getClass(), screen, "currentStage");
        Object isComplete = fieldValue(screen.getClass(), screen, "isComplete");
        Object hasAllRequisites = fieldValue(screen.getClass(), screen, "hasAllRequisites");
        Object hasItem = fieldValue(screen.getClass(), screen, "hasItem");
        Object hasCraft = fieldValue(screen.getClass(), screen, "hasCraft");
        Object hasResearch = fieldValue(screen.getClass(), screen, "hasResearch");
        Object hasKnow = fieldValue(screen.getClass(), screen, "hasKnow");

        GPOM.LOGGER.info(
                "[Thaumcraft Client Probe] page entry={} currentStage={} isComplete={} hasAllRequisites={} hasItem={} hasCraft={} hasResearch={} hasKnow={}",
                describeResearchEntry(entry),
                currentStage,
                isComplete,
                hasAllRequisites,
                arraySummary(hasItem),
                arraySummary(hasCraft),
                arraySummary(hasResearch),
                arraySummary(hasKnow)
        );
    }

    private static boolean containsResearchKey(Collection<?> entries, String key) throws ReflectiveOperationException {
        if (entries == null) {
            return false;
        }
        for (Object entry : entries) {
            Object value = invoke(entry, "getKey");
            if (key.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static String describeResearchEntry(Object entry) throws ReflectiveOperationException {
        if (entry == null) {
            return "<missing>";
        }
        Object key = invoke(entry, "getKey");
        Object category = invoke(entry, "getCategory");
        Object parents = invoke(entry, "getParents");
        Object meta = invoke(entry, "getMeta");
        Object stages = invoke(entry, "getStages");
        return "key=" + key
                + " category=" + category
                + " parents=" + arraySummary(parents)
                + " meta=" + arraySummary(meta)
                + " stages=" + arrayLength(stages);
    }

    private static String describeCategory(Object category, Object player) throws ReflectiveOperationException {
        if (category == null) {
            return "<missing>";
        }
        Object key = fieldValue(category.getClass(), category, "key");
        Object researchKey = fieldValue(category.getClass(), category, "researchKey");
        Object research = fieldValue(category.getClass(), category, "research");
        boolean gateComplete = researchKey == null || knowsResearchStrict(player, String.valueOf(researchKey));
        int researchCount = research instanceof Map ? ((Map<?, ?>) research).size() : -1;
        return "key=" + key + " researchKey=" + researchKey + " gateComplete=" + gateComplete + " entries=" + researchCount;
    }

    private static Object minecraft() throws ReflectiveOperationException {
        Method method = getMinecraftMethod;
        if (method == null) {
            method = findStaticMethod(Class.forName("net.minecraft.client.Minecraft"), "func_71410_x", "getMinecraft");
            method.setAccessible(true);
            getMinecraftMethod = method;
        }
        return method.invoke(null);
    }

    private static Object currentScreen(Object minecraft) throws ReflectiveOperationException {
        Field field = currentScreenField;
        if (field == null) {
            field = findField(minecraft.getClass(), "field_71462_r", "currentScreen");
            field.setAccessible(true);
            currentScreenField = field;
        }
        return field.get(minecraft);
    }

    private static Object clientPlayer(Object minecraft) throws ReflectiveOperationException {
        Field field = playerField;
        if (field == null) {
            field = findField(minecraft.getClass(), "field_71439_g", "player");
            field.setAccessible(true);
            playerField = field;
        }
        return field.get(minecraft);
    }

    private static Object knowledge(Object player) throws ReflectiveOperationException {
        Method method = getKnowledgeMethod;
        if (method == null) {
            method = Class.forName("thaumcraft.api.capabilities.ThaumcraftCapabilities")
                    .getMethod("getKnowledge", Class.forName("net.minecraft.entity.player.EntityPlayer"));
            method.setAccessible(true);
            getKnowledgeMethod = method;
        }
        return method.invoke(null, player);
    }

    private static boolean isResearchKnown(Object knowledge, String key) throws ReflectiveOperationException {
        Method method = isResearchKnownMethod;
        if (method == null) {
            method = knowledge.getClass().getMethod("isResearchKnown", String.class);
            method.setAccessible(true);
            isResearchKnownMethod = method;
        }
        return (Boolean) method.invoke(knowledge, key);
    }

    private static boolean isResearchComplete(Object knowledge, String key) throws ReflectiveOperationException {
        Method method = isResearchCompleteMethod;
        if (method == null) {
            method = knowledge.getClass().getMethod("isResearchComplete", String.class);
            method.setAccessible(true);
            isResearchCompleteMethod = method;
        }
        return (Boolean) method.invoke(knowledge, key);
    }

    private static int getResearchStage(Object knowledge, String key) throws ReflectiveOperationException {
        Method method = getResearchStageMethod;
        if (method == null) {
            method = knowledge.getClass().getMethod("getResearchStage", String.class);
            method.setAccessible(true);
            getResearchStageMethod = method;
        }
        Object value = method.invoke(knowledge, key);
        return value instanceof Integer ? (Integer) value : -1;
    }

    private static Set<?> researchList(Object knowledge) throws ReflectiveOperationException {
        Method method = getResearchListMethod;
        if (method == null) {
            method = knowledge.getClass().getMethod("getResearchList");
            method.setAccessible(true);
            getResearchListMethod = method;
        }
        Object value = method.invoke(knowledge);
        return value instanceof Set ? (Set<?>) value : null;
    }

    private static Object research(String key) throws ReflectiveOperationException {
        Method method = getResearchMethod;
        if (method == null) {
            method = Class.forName("thaumcraft.api.research.ResearchCategories")
                    .getMethod("getResearch", String.class);
            method.setAccessible(true);
            getResearchMethod = method;
        }
        return method.invoke(null, key);
    }

    private static Object researchCategory(String key) throws ReflectiveOperationException {
        Method method = getResearchCategoryMethod;
        if (method == null) {
            method = Class.forName("thaumcraft.api.research.ResearchCategories")
                    .getMethod("getResearchCategory", String.class);
            method.setAccessible(true);
            getResearchCategoryMethod = method;
        }
        return method.invoke(null, key);
    }

    private static boolean knowsResearchStrict(Object player, String key) throws ReflectiveOperationException {
        Method method = knowsResearchStrictMethod;
        if (method == null) {
            method = Class.forName("thaumcraft.api.capabilities.ThaumcraftCapabilities")
                    .getMethod("knowsResearchStrict", Class.forName("net.minecraft.entity.player.EntityPlayer"), String[].class);
            method.setAccessible(true);
            knowsResearchStrictMethod = method;
        }
        Object value = method.invoke(null, player, new String[]{key});
        return value instanceof Boolean && (Boolean) value;
    }

    private static Collection<?> collectionField(Object target, String fieldName) throws ReflectiveOperationException {
        Object value = fieldValue(target.getClass(), target, fieldName);
        return value instanceof Collection ? (Collection<?>) value : null;
    }

    private static Object fieldValue(Class<?> start, Object target, String fieldName) throws ReflectiveOperationException {
        Field field = findField(start, fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Method findStaticMethod(Class<?> type, String... names) throws NoSuchMethodException {
        for (String name : names) {
            try {
                return type.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + String.join("/", names));
    }

    private static Method findMethod(Class<?> type, String... names) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            for (String name : names) {
                try {
                    return current.getDeclaredMethod(name);
                } catch (NoSuchMethodException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchMethodException(type.getName() + "." + String.join("/", names));
    }

    private static Field findField(Class<?> type, String... names) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            for (String name : names) {
                try {
                    return current.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchFieldException(type.getName() + "." + String.join("/", names));
    }

    private static String arraySummary(Object value) {
        if (value == null || !value.getClass().isArray()) {
            return String.valueOf(value);
        }
        int length = arrayLength(value);
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < length && index < 8; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(java.lang.reflect.Array.get(value, index));
        }
        if (length > 8) {
            builder.append(", ...");
        }
        return builder.append(']').toString();
    }

    private static int arrayLength(Object value) {
        return value != null && value.getClass().isArray() ? java.lang.reflect.Array.getLength(value) : -1;
    }
}
