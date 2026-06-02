package com.l.cleanroomoptimizations.profiling;

import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.IContextSetter;
import net.minecraftforge.fml.common.eventhandler.IEventListener;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.tslat.aoa3.client.render.entities.EntityRenders;
import net.tslat.aoa3.common.registration.BlockRegister;
import net.tslat.aoa3.common.registration.ItemRegister;

public final class AoAConstructionOptimizations {
    private static final boolean ENABLED = Boolean.parseBoolean(
            System.getProperty("cleanroomoptimizations.aoa3ConstructionOptimizations", "true")
    );
    private static final String ENTITY_RENDERS = "net.tslat.aoa3.client.render.entities.EntityRenders";
    private static final String BLOCK_REGISTER = "net.tslat.aoa3.common.registration.BlockRegister";
    private static final String ITEM_REGISTER = "net.tslat.aoa3.common.registration.ItemRegister";

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
        if (BLOCK_REGISTER.equals(targetClass.getName())) {
            registerBlockRegister(owner);
            return true;
        }
        if (ITEM_REGISTER.equals(targetClass.getName())) {
            registerItemRegister(owner);
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

    private static void registerBlockRegister(ModContainer owner) {
        registerModelRegistry(owner, new ModelRegistryInvoker() {
            @Override
            public void invoke(ModelRegistryEvent event) {
                BlockRegister.registerItemBlockRenders(event);
            }
        });
        registerRegistry(owner, Block.class, new RegisterInvoker() {
            @Override
            public void invoke(RegistryEvent.Register event) {
                BlockRegister.registerBlocks(event);
            }
        });
        registerRegistry(owner, Item.class, new RegisterInvoker() {
            @Override
            public void invoke(RegistryEvent.Register event) {
                BlockRegister.registerItemBlocks(event);
            }
        });
        registerMissingMappings(owner, Block.class, new MissingMappingsInvoker() {
            @Override
            public void invoke(RegistryEvent.MissingMappings event) {
                BlockRegister.remapMissing(event);
            }
        });
    }

    private static void registerItemRegister(ModContainer owner) {
        registerModelRegistry(owner, new ModelRegistryInvoker() {
            @Override
            public void invoke(ModelRegistryEvent event) {
                ItemRegister.registerItemRenders(event);
            }
        });
        registerRegistry(owner, Item.class, new RegisterInvoker() {
            @Override
            public void invoke(RegistryEvent.Register event) {
                ItemRegister.registerItems(event);
            }
        });
        registerMissingMappings(owner, Item.class, new MissingMappingsInvoker() {
            @Override
            public void invoke(RegistryEvent.MissingMappings event) {
                ItemRegister.remapMissing(event);
            }
        });
    }

    private static void registerModelRegistry(final ModContainer owner, final ModelRegistryInvoker invoker) {
        ModelRegistryEvent event = new ModelRegistryEvent();
        event.getListenerList().register(0, EventPriority.NORMAL, new IEventListener() {
            @Override
            public void invoke(Event event) {
                setOwner(event, owner);
                invoker.invoke((ModelRegistryEvent) event);
            }
        });
    }

    private static void registerRegistry(final ModContainer owner, final Class<?> registryType, final RegisterInvoker invoker) {
        RegistryEvent.Register event = new RegistryEvent.Register(null, registryFor(registryType));
        event.getListenerList().register(0, EventPriority.NORMAL, new IEventListener() {
            @Override
            public void invoke(Event event) {
                RegistryEvent.Register registryEvent = (RegistryEvent.Register) event;
                if (registryEvent.getRegistry() == null || registryEvent.getRegistry().getRegistrySuperType() != registryType) {
                    return;
                }
                setOwner(event, owner);
                invoker.invoke(registryEvent);
            }
        });
    }

    private static void registerMissingMappings(final ModContainer owner, final Class<?> registryType, final MissingMappingsInvoker invoker) {
        RegistryEvent.MissingMappings event = new RegistryEvent.MissingMappings(null, registryFor(registryType), java.util.Collections.emptyList());
        event.getListenerList().register(0, EventPriority.NORMAL, new IEventListener() {
            @Override
            public void invoke(Event event) {
                RegistryEvent.MissingMappings mappingEvent = (RegistryEvent.MissingMappings) event;
                if (mappingEvent.getRegistry() == null || mappingEvent.getRegistry().getRegistrySuperType() != registryType) {
                    return;
                }
                setOwner(event, owner);
                invoker.invoke(mappingEvent);
            }
        });
    }

    private static void setOwner(Event event, ModContainer owner) {
        if (event instanceof IContextSetter) {
            ((IContextSetter) event).setModContainer(owner);
        }
    }

    private static IForgeRegistry<?> registryFor(Class<?> registryType) {
        if (registryType == Block.class) {
            return ForgeRegistries.BLOCKS;
        }
        if (registryType == Item.class) {
            return ForgeRegistries.ITEMS;
        }
        throw new IllegalArgumentException("Unsupported AoA registry type: " + registryType.getName());
    }

    private interface ModelRegistryInvoker {
        void invoke(ModelRegistryEvent event);
    }

    private interface RegisterInvoker {
        void invoke(RegistryEvent.Register event);
    }

    private interface MissingMappingsInvoker {
        void invoke(RegistryEvent.MissingMappings event);
    }
}
