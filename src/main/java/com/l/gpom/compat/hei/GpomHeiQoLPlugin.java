package com.l.gpom.compat.hei;

import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.GPOM;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferRegistry;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraftforge.fml.common.Loader;

import java.util.Collections;

@JEIPlugin
public final class GpomHeiQoLPlugin implements IModPlugin {
    private static final String EXTENDED_BASIC = "extendedcrafting:table_crafting_3x3";
    private static final String EXTENDED_ADVANCED = "extendedcrafting:table_crafting_5x5";
    private static final String EXTENDED_ELITE = "extendedcrafting:table_crafting_7x7";
    private static final String DRACONIC_FUSION = "DraconicEvolution.Fusion";

    @Override
    public void register(IModRegistry registry) {
        if (registry == null) {
            return;
        }

        GPOM.LOGGER.info("[GPOM HEI QoL] Registering HEI QoL plugin");
        IRecipeTransferRegistry transfers = registry.getRecipeTransferRegistry();
        if (transfers == null) {
            GPOM.LOGGER.warn("[GPOM HEI QoL] HEI recipe transfer registry is unavailable");
            return;
        }

        if (Loader.isModLoaded("thaumcraft")) {
            registerThaumcraftRecipes(registry);
        }

        IRecipeTransferHandlerHelper transferHelper = registry.getJeiHelpers().recipeTransferHandlerHelper();
        if (GpomEarlyConfig.heiExtendedCraftingLowerTierTransferEnabled()
                && Loader.isModLoaded("extendedcrafting")) {
            registerExtendedCraftingAliases(transfers, transferHelper);
        }
        if (GpomEarlyConfig.heiDraconicFusionTransferEnabled()
                && Loader.isModLoaded("draconicevolution")) {
            registerDraconicFusionTransfer(transfers, transferHelper);
        }
    }

    private static void registerThaumcraftRecipes(IModRegistry registry) {
        try {
            SalisMundusRecipeWrapper salisMundus = SalisMundusRecipeWrapper.createOrNull();
            if (salisMundus == null) {
                GPOM.LOGGER.warn("[GPOM HEI QoL] Could not register Salis Mundus HEI recipe because one or more Thaumcraft items are unavailable");
                return;
            }
            registry.addRecipes(Collections.singletonList(salisMundus), VanillaRecipeCategoryUid.CRAFTING);
            GPOM.LOGGER.info("[GPOM HEI QoL] Registered Salis Mundus crafting recipe");
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[GPOM HEI QoL] Could not register Salis Mundus HEI recipe", throwable);
        }
    }

    private static void registerExtendedCraftingAliases(IRecipeTransferRegistry transfers,
                                                        IRecipeTransferHandlerHelper transferHelper) {
        Class<? extends Container> basic = containerClass("com.blakebr0.extendedcrafting.client.container.ContainerBasicTable");
        Class<? extends Container> advanced = containerClass("com.blakebr0.extendedcrafting.client.container.ContainerAdvancedTable");
        Class<? extends Container> elite = containerClass("com.blakebr0.extendedcrafting.client.container.ContainerEliteTable");
        Class<? extends Container> ultimate = containerClass("com.blakebr0.extendedcrafting.client.container.ContainerUltimateTable");

        register(transfers, advanced, EXTENDED_BASIC, 5, 3);

        register(transfers, elite, EXTENDED_BASIC, 7, 3);
        register(transfers, elite, EXTENDED_ADVANCED, 7, 5);

        register(transfers, ultimate, EXTENDED_BASIC, 9, 3);
        register(transfers, ultimate, EXTENDED_ADVANCED, 9, 5);
        register(transfers, ultimate, EXTENDED_ELITE, 9, 7);

        String message = "Open a compatible ExtendedCrafting table to transfer this recipe.";
        registerDisabled(transfers, ContainerPlayer.class, EXTENDED_BASIC, transferHelper, message);
        registerDisabled(transfers, ContainerPlayer.class, EXTENDED_ADVANCED, transferHelper, message);
        registerDisabled(transfers, ContainerPlayer.class, EXTENDED_ELITE, transferHelper, message);
        registerDisabled(transfers, ContainerWorkbench.class, EXTENDED_BASIC, transferHelper, message);
        registerDisabled(transfers, ContainerWorkbench.class, EXTENDED_ADVANCED, transferHelper, message);
        registerDisabled(transfers, ContainerWorkbench.class, EXTENDED_ELITE, transferHelper, message);
        registerDisabled(transfers, basic, EXTENDED_ADVANCED, transferHelper, message);
        registerDisabled(transfers, basic, EXTENDED_ELITE, transferHelper, message);
        registerDisabled(transfers, advanced, EXTENDED_ELITE, transferHelper, message);
        GPOM.LOGGER.info("[GPOM HEI QoL] Registered ExtendedCrafting lower-tier table transfer handlers");
    }

    private static void registerDraconicFusionTransfer(IRecipeTransferRegistry transfers,
                                                       IRecipeTransferHandlerHelper transferHelper) {
        Class<? extends Container> fusion = containerClass("com.brandon3055.draconicevolution.inventory.ContainerFusionCraftingCore");
        if (fusion != null) {
            transfers.addRecipeTransferHandler(new DraconicFusionTransferHandler(transferHelper, fusion), DRACONIC_FUSION);
        }

        String message = "Open a Draconic Fusion Crafting Core to transfer this recipe.";
        registerDisabled(transfers, ContainerPlayer.class, DRACONIC_FUSION, transferHelper, message);
        registerDisabled(transfers, ContainerWorkbench.class, DRACONIC_FUSION, transferHelper, message);
        GPOM.LOGGER.info("[GPOM HEI QoL] Registered Draconic Evolution Fusion transfer handler");
    }

    private static void register(IRecipeTransferRegistry transfers,
                                 Class<? extends Container> containerClass,
                                 String category,
                                 int tableWidth,
                                 int recipeWidth) {
        if (containerClass != null) {
            transfers.addRecipeTransferHandler(new ExtendedCraftingTableTransferInfo(
                    containerClass,
                    category,
                    tableWidth,
                    recipeWidth));
        }
    }

    private static void registerDisabled(IRecipeTransferRegistry transfers,
                                         Class<? extends Container> containerClass,
                                         String category,
                                         IRecipeTransferHandlerHelper transferHelper,
                                         String message) {
        if (containerClass != null) {
            transfers.addRecipeTransferHandler(
                    new DisabledRecipeTransferHandler(containerClass, transferHelper, message),
                    category);
        }
    }

    private static Class<? extends Container> containerClass(String name) {
        try {
            return Class.forName(name).asSubclass(Container.class);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
