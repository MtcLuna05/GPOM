package com.l.cleanroomoptimizations.profiling;

import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.IContextSetter;
import net.minecraftforge.fml.common.eventhandler.IEventListener;
import net.tslat.aoa3.client.render.entities.EntityRenders;

public final class AoAConstructionOptimizations {
    private static final boolean ENABLED = Boolean.parseBoolean(
            System.getProperty("cleanroomoptimizations.aoa3ConstructionOptimizations", "true")
    );
    private static final String ENTITY_RENDERS = "net.tslat.aoa3.client.render.entities.EntityRenders";

    private AoAConstructionOptimizations() {
    }

    public static boolean tryRegisterAutomaticSubscriber(String modId, Object target, ModContainer owner) {
        if (!ENABLED || !"aoa3".equals(modId) || !(target instanceof Class)) {
            return false;
        }

        Class<?> targetClass = (Class<?>) target;
        if (ENTITY_RENDERS.equals(targetClass.getName())) {
            registerEntityRenders(owner);
            return true;
        }

        return false;
    }

    private static void registerEntityRenders(ModContainer owner) {
        ModelRegistryEvent event = new ModelRegistryEvent();
        event.getListenerList().register(0, EventPriority.NORMAL, new IEventListener() {
            @Override
            public void invoke(Event event) {
                if (event instanceof IContextSetter) {
                    ((IContextSetter) event).setModContainer(owner);
                }
                EntityRenders.registerEntityRenders((ModelRegistryEvent) event);
            }
        });
    }
}
