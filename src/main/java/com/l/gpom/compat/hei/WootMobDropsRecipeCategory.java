package com.l.gpom.compat.hei;

import com.l.gpom.client.ClientAccess;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeCategory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

final class WootMobDropsRecipeCategory implements IRecipeCategory<WootMobDropsRecipeWrapper> {
    static final String UID = "gpom.woot.mob_drops";
    static final int DROPS_PER_PAGE = 16;

    private static final int COLUMNS = 8;
    private static final int OUTPUT_X = 1;
    private static final int OUTPUT_Y = 25;
    private static final int SLOT_SPACING = 19;
    private static final int BLOOD_MAGIC_Y = 65;
    private static final int FACTORY_CAP_X = 77;

    private final IDrawable background;
    private final IDrawable slot;

    WootMobDropsRecipeCategory(IGuiHelper guiHelper) {
        WootJeiDiagnostics.log("Constructing category {}", UID);
        this.background = guiHelper.createBlankDrawable(154, 84);
        this.slot = guiHelper.getSlotDrawable();
    }

    @Override
    public String getUid() {
        return UID;
    }

    @Override
    public String getTitle() {
        return ClientAccess.i18nFormat("gpom.jei.woot.mob_drops.title");
    }

    @Override
    public String getModName() {
        return "Woot";
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout,
                          final WootMobDropsRecipeWrapper wrapper,
                          IIngredients ingredients) {
        wrapper.selectDropPageForFocus(recipeLayout.getFocus());
        WootJeiDiagnostics.log("Rendering category recipe: mob={}, drops={}, visiblePage={}/{}",
                wrapper.getMobName(), wrapper.getAllDrops().size(),
                wrapper.getCurrentDropPage(), wrapper.getDropPageCount());
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        itemStacks.init(0, true, 1, 1);
        itemStacks.setBackground(0, slot);
        itemStacks.set(0, wrapper.getMobShard());

        for (int i = 0; i < DROPS_PER_PAGE; i++) {
            int slotIndex = i + 1;
            int x = OUTPUT_X + (i % COLUMNS) * SLOT_SPACING;
            int y = OUTPUT_Y + (i / COLUMNS) * SLOT_SPACING;
            itemStacks.init(slotIndex, false, x, y);
            itemStacks.setBackground(slotIndex, slot);
        }
        wrapper.bindDropSlots(itemStacks);

        List<WootMobDropsRecipeWrapper.UpgradeInfo> upgrades = wrapper.getBloodMagicUpgrades();
        int firstUpgradeSlot = DROPS_PER_PAGE + 1;
        for (int i = 0; i < upgrades.size(); i++) {
            int slotIndex = firstUpgradeSlot + i;
            itemStacks.init(slotIndex, true, 1 + i * SLOT_SPACING, BLOOD_MAGIC_Y);
            itemStacks.setBackground(slotIndex, slot);
            itemStacks.set(slotIndex, upgrades.get(i).getStacks());
        }

        if (!wrapper.getLifeEssenceOutputs().isEmpty()) {
            IGuiFluidStackGroup fluids = recipeLayout.getFluidStacks();
            fluids.init(0, false, 59, BLOOD_MAGIC_Y, 16, 16,
                    maximumFluidAmount(wrapper.getLifeEssenceOutputs()), false, null);
            fluids.set(0, wrapper.getLifeEssenceOutputs());
            fluids.addTooltipCallback((slotIndex, input, ingredient, tooltip) -> {
                tooltip.add(TextFormatting.DARK_GRAY + "Woot Sanguine Urn output");
                tooltip.add(TextFormatting.GRAY + "Amount shown is per simulated mob and factory cycle");
            });
        }

        int factoryCapSlot = DROPS_PER_PAGE + 1 + upgrades.size();
        ItemStack factoryCap = wrapper.getFactoryCap();
        if (!MinecraftMappingCompat.itemStackIsEmpty(factoryCap)) {
            itemStacks.init(factoryCapSlot, true, FACTORY_CAP_X, BLOOD_MAGIC_Y);
            itemStacks.setBackground(factoryCapSlot, slot);
            itemStacks.set(factoryCapSlot, factoryCap);
        }

        itemStacks.addTooltipCallback((slotIndex, input, ingredient, tooltip) -> {
            if (slotIndex == 0) {
                tooltip.add(TextFormatting.GRAY + "Woot mob: " + TextFormatting.WHITE + wrapper.getDisplayName());
                tooltip.add(TextFormatting.GRAY + "Registry name: " + TextFormatting.DARK_GRAY + wrapper.getMobName());
                if (wrapper.getFarmTier() > 0) {
                    tooltip.add(TextFormatting.GRAY + "Required farm tier: " + TextFormatting.WHITE
                            + WootMobDropsRecipeWrapper.roman(wrapper.getFarmTier()));
                }
                return;
            }
            int dropIndex = slotIndex - 1;
            List<WootMobDropsRecipeWrapper.DropInfo> visibleDrops = wrapper.getVisibleDrops();
            if (dropIndex >= 0 && dropIndex < visibleDrops.size()) {
                visibleDrops.get(dropIndex).appendTooltip(tooltip);
                return;
            }
            int upgradeIndex = slotIndex - firstUpgradeSlot;
            if (upgradeIndex >= 0 && upgradeIndex < upgrades.size()) {
                upgrades.get(upgradeIndex).appendTooltip(tooltip);
                return;
            }
            if (slotIndex == factoryCapSlot) {
                tooltip.add(TextFormatting.GRAY + "Required factory cap for this mob");
                tooltip.add(TextFormatting.DARK_GRAY + "Indexed as input and output for Uses/Recipes tier filtering");
            }
        });
    }

    private static int maximumFluidAmount(List<net.minecraftforge.fluids.FluidStack> fluids) {
        int maximum = 1;
        for (net.minecraftforge.fluids.FluidStack fluid : fluids) {
            if (fluid != null) {
                maximum = Math.max(maximum, fluid.amount);
            }
        }
        return maximum;
    }
}
