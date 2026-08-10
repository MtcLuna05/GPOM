package com.l.gpom.compat.hei;

import com.l.gpom.client.ClientAccess;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fluids.FluidStack;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class WootMobDropsRecipeWrapper implements IRecipeWrapper {
    private static final String[] LOOTING_LABELS = {"No Looting", "Looting I", "Looting II", "Looting III"};
    private static final int NAVIGATION_Y = 67;
    private static final int NAVIGATION_HEIGHT = 13;
    private static final int NAVIGATION_WIDTH = 13;
    private static final int PREVIOUS_X = 97;
    private static final int NEXT_X = 140;
    private static final int PAGE_CENTER_X = 125;

    private final String mobName;
    private final String displayName;
    private int farmTier;
    private final ItemStack mobShard;
    private final ItemStack factoryCap;
    private final ItemStack controller;
    private final ItemStack factory;
    private final List<DropInfo> drops;
    private final List<UpgradeInfo> bloodMagicUpgrades;
    private final List<FluidStack> lifeEssenceOutputs;
    private int currentDropPage;
    private WeakReference<IGuiItemStackGroup> boundItemStacks;

    WootMobDropsRecipeWrapper(String mobName,
                              String displayName,
                              int farmTier,
                              ItemStack mobShard,
                              ItemStack factoryCap,
                              ItemStack controller,
                              ItemStack factory,
                              List<DropInfo> drops,
                              List<UpgradeInfo> bloodMagicUpgrades,
                              List<FluidStack> lifeEssenceOutputs) {
        this.mobName = mobName;
        this.displayName = displayName;
        this.farmTier = farmTier;
        this.mobShard = copy(mobShard);
        this.factoryCap = copy(factoryCap);
        this.controller = copy(controller);
        this.factory = copy(factory);
        this.drops = Collections.unmodifiableList(new ArrayList<>(drops));
        this.bloodMagicUpgrades = Collections.unmodifiableList(new ArrayList<>(bloodMagicUpgrades));
        this.lifeEssenceOutputs = immutableFluidCopies(lifeEssenceOutputs);
        this.boundItemStacks = new WeakReference<>(null);
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        List<ItemStack> inputs = new ArrayList<>(4 + bloodMagicUpgrades.size() * 3);
        inputs.add(copy(mobShard));
        if (!MinecraftMappingCompat.itemStackIsEmpty(factoryCap)) {
            inputs.add(copy(factoryCap));
        }
        if (!MinecraftMappingCompat.itemStackIsEmpty(controller)) {
            inputs.add(copy(controller));
        }
        if (!MinecraftMappingCompat.itemStackIsEmpty(factory)) {
            inputs.add(copy(factory));
        }
        for (UpgradeInfo upgrade : bloodMagicUpgrades) {
            inputs.addAll(upgrade.getStacks());
        }
        // The category renders the programmed shard and required cap. Controller/factory remain
        // hidden lookup inputs so HEI's Uses key (U) opens this category for either block.
        ingredients.setInputs(VanillaTypes.ITEM, inputs);
        List<List<ItemStack>> outputs = new ArrayList<>(drops.size() + 1);
        for (DropInfo drop : drops) {
            outputs.add(drop.getStacks());
        }
        if (!MinecraftMappingCompat.itemStackIsEmpty(factoryCap)) {
            outputs.add(Collections.singletonList(copy(factoryCap)));
        }
        ingredients.setOutputLists(VanillaTypes.ITEM, outputs);
        if (!lifeEssenceOutputs.isEmpty()) {
            ingredients.setOutputLists(VanillaTypes.FLUID,
                    Collections.singletonList(getLifeEssenceOutputs()));
        }
        WootJeiDiagnostics.log("Indexed wrapper ingredients: mob={}, inputs={}, outputSlots={}, "
                        + "outputVariants={}, fluidVariants={}, bloodMagicUpgrades={}",
                mobName, inputs.size(), outputs.size(), outputVariantCount(outputs),
                lifeEssenceOutputs.size(), bloodMagicUpgrades.size());
    }

    @Override
    public void drawInfo(Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
        FontRenderer font = MinecraftMappingCompat.minecraftFontRenderer(minecraft);
        if (font == null) {
            return;
        }
        String title = MinecraftMappingCompat.fontTrimStringToWidth(font, displayName, recipeWidth - 52);
        MinecraftMappingCompat.fontDrawString(font, title, 24, 2, 0x404040);

        int resolvedTier = getFarmTier();
        String tier = resolvedTier > 0 ? "Farm Tier " + roman(resolvedTier) : "Farm Tier unknown";
        tier = MinecraftMappingCompat.fontTrimStringToWidth(font, tier, recipeWidth - 52);
        MinecraftMappingCompat.fontDrawString(font, tier, 24, 13, 0x707070);
        drawDropPageControls(font, mouseX, mouseY);
        WootMobRenderCompat.render(minecraft, mobName, mouseX, mouseY);
    }

    @Override
    public List<String> getTooltipStrings(int mouseX, int mouseY) {
        if (getDropPageCount() <= 1) {
            return Collections.emptyList();
        }
        if (isInside(mouseX, mouseY, PREVIOUS_X) && currentDropPage > 0) {
            return Collections.singletonList("Previous drop page");
        }
        if (isInside(mouseX, mouseY, NEXT_X) && currentDropPage + 1 < getDropPageCount()) {
            return Collections.singletonList("Next drop page");
        }
        return Collections.emptyList();
    }

    @Override
    public boolean handleClick(Minecraft minecraft, int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || getDropPageCount() <= 1) {
            return false;
        }
        if (isInside(mouseX, mouseY, PREVIOUS_X) && currentDropPage > 0) {
            setDropPage(currentDropPage - 1);
            return true;
        }
        if (isInside(mouseX, mouseY, NEXT_X) && currentDropPage + 1 < getDropPageCount()) {
            setDropPage(currentDropPage + 1);
            return true;
        }
        return false;
    }

    String getMobName() {
        return mobName;
    }

    String getDisplayName() {
        return displayName;
    }

    int getFarmTier() {
        if (farmTier <= 0
                && MinecraftMappingCompat.minecraftWorld(MinecraftMappingCompat.minecraftInstance()) != null) {
            farmTier = WootMobDropsJeiIntegration.resolveFarmTier(mobName);
        }
        return farmTier;
    }

    ItemStack getMobShard() {
        return copy(mobShard);
    }

    ItemStack getFactoryCap() {
        return copy(factoryCap);
    }

    List<DropInfo> getAllDrops() {
        return drops;
    }

    List<DropInfo> getVisibleDrops() {
        int from = currentDropPage * WootMobDropsRecipeCategory.DROPS_PER_PAGE;
        int to = Math.min(drops.size(), from + WootMobDropsRecipeCategory.DROPS_PER_PAGE);
        return from < to ? drops.subList(from, to) : Collections.<DropInfo>emptyList();
    }

    int getCurrentDropPage() {
        return currentDropPage + 1;
    }

    int getDropPageCount() {
        return Math.max(1, (drops.size() + WootMobDropsRecipeCategory.DROPS_PER_PAGE - 1)
                / WootMobDropsRecipeCategory.DROPS_PER_PAGE);
    }

    void selectDropPageForFocus(IFocus<?> focus) {
        if (focus == null || focus.getMode() != IFocus.Mode.OUTPUT || !(focus.getValue() instanceof ItemStack)) {
            return;
        }
        ItemStack focused = (ItemStack) focus.getValue();
        for (int dropIndex = 0; dropIndex < drops.size(); dropIndex++) {
            for (ItemStack alternative : drops.get(dropIndex).getStacks()) {
                if (MinecraftMappingCompat.itemStacksSameItemAndTags(focused, alternative)) {
                    currentDropPage = dropIndex / WootMobDropsRecipeCategory.DROPS_PER_PAGE;
                    return;
                }
            }
        }
    }

    void bindDropSlots(IGuiItemStackGroup itemStacks) {
        boundItemStacks = new WeakReference<>(itemStacks);
        refreshVisibleDropSlots();
    }

    List<UpgradeInfo> getBloodMagicUpgrades() {
        return bloodMagicUpgrades;
    }

    List<FluidStack> getLifeEssenceOutputs() {
        List<FluidStack> copies = new ArrayList<>(lifeEssenceOutputs.size());
        for (FluidStack stack : lifeEssenceOutputs) {
            copies.add(stack.copy());
        }
        return copies;
    }

    static String roman(int tier) {
        switch (tier) {
            case 1:
                return "I";
            case 2:
                return "II";
            case 3:
                return "III";
            case 4:
                return "IV";
            default:
                return Integer.toString(tier);
        }
    }

    private void setDropPage(int page) {
        int lastPage = getDropPageCount() - 1;
        currentDropPage = Math.max(0, Math.min(page, lastPage));
        refreshVisibleDropSlots();
    }

    private void refreshVisibleDropSlots() {
        IGuiItemStackGroup itemStacks = boundItemStacks.get();
        if (itemStacks == null) {
            return;
        }
        List<DropInfo> visible = getVisibleDrops();
        for (int i = 0; i < WootMobDropsRecipeCategory.DROPS_PER_PAGE; i++) {
            if (i < visible.size()) {
                itemStacks.set(i + 1, visible.get(i).getStacks());
            } else {
                itemStacks.set(i + 1, Collections.<ItemStack>emptyList());
            }
        }
    }

    private void drawDropPageControls(FontRenderer font, int mouseX, int mouseY) {
        int pageCount = getDropPageCount();
        if (pageCount <= 1) {
            return;
        }
        drawNavigationButton(font, PREVIOUS_X, "<", currentDropPage > 0,
                isInside(mouseX, mouseY, PREVIOUS_X));
        drawNavigationButton(font, NEXT_X, ">", currentDropPage + 1 < pageCount,
                isInside(mouseX, mouseY, NEXT_X));
        String pageLabel = (currentDropPage + 1) + "/" + pageCount;
        int labelX = PAGE_CENTER_X - ClientAccess.stringWidth(font, pageLabel) / 2;
        MinecraftMappingCompat.fontDrawString(font, pageLabel, labelX, NAVIGATION_Y + 2, 0x707070);
    }

    private static void drawNavigationButton(FontRenderer font, int x, String arrow,
                                             boolean enabled, boolean hovered) {
        int border = enabled ? (hovered ? 0xFFB8B8B8 : 0xFF8A8A8A) : 0xFF555555;
        int fill = enabled ? (hovered ? 0xFF555555 : 0xFF383838) : 0xFF242424;
        int text = enabled ? 0xFFFFFFFF : 0xFF777777;
        ClientAccess.drawRect(x, NAVIGATION_Y, x + NAVIGATION_WIDTH, NAVIGATION_Y + NAVIGATION_HEIGHT, border);
        ClientAccess.drawRect(x + 1, NAVIGATION_Y + 1, x + NAVIGATION_WIDTH - 1,
                NAVIGATION_Y + NAVIGATION_HEIGHT - 1, fill);
        MinecraftMappingCompat.fontDrawString(font, arrow, x + 4, NAVIGATION_Y + 2, text);
    }

    private static boolean isInside(int mouseX, int mouseY, int x) {
        return mouseX >= x && mouseX < x + NAVIGATION_WIDTH
                && mouseY >= NAVIGATION_Y && mouseY < NAVIGATION_Y + NAVIGATION_HEIGHT;
    }

    private static ItemStack copy(ItemStack stack) {
        ItemStack copy = MinecraftMappingCompat.itemStackCopy(stack);
        return copy == null ? MinecraftMappingCompat.emptyStack() : copy;
    }

    private static int outputVariantCount(List<List<ItemStack>> outputs) {
        int count = 0;
        for (List<ItemStack> alternatives : outputs) {
            count += alternatives.size();
        }
        return count;
    }

    private static List<FluidStack> immutableFluidCopies(List<FluidStack> fluids) {
        List<FluidStack> copies = new ArrayList<>(fluids.size());
        for (FluidStack fluid : fluids) {
            if (fluid != null && fluid.amount > 0) {
                copies.add(fluid.copy());
            }
        }
        return Collections.unmodifiableList(copies);
    }

    static final class DropInfo {
        private List<ItemStack> stacks;
        private String alternativeDescription;
        private List<String> specialTooltip = Collections.emptyList();
        private final int[] chances = {-1, -1, -1, -1};
        private final List<Map<Integer, Integer>> sizes = new ArrayList<>(4);

        DropInfo(ItemStack stack) {
            setStacks(Collections.singletonList(stack), "");
            for (int i = 0; i < 4; i++) {
                sizes.add(Collections.<Integer, Integer>emptyMap());
            }
        }

        ItemStack getStack() {
            return stacks.isEmpty() ? MinecraftMappingCompat.emptyStack() : copy(stacks.get(0));
        }

        List<ItemStack> getStacks() {
            List<ItemStack> copies = new ArrayList<>(stacks.size());
            for (ItemStack stack : stacks) {
                copies.add(copy(stack));
            }
            return copies;
        }

        void setStacks(List<ItemStack> alternatives, String description) {
            List<ItemStack> normalized = new ArrayList<>(alternatives.size());
            for (ItemStack alternative : alternatives) {
                if (MinecraftMappingCompat.itemStackIsEmpty(alternative)) {
                    continue;
                }
                ItemStack normalizedStack = copy(alternative);
                MinecraftMappingCompat.itemStackSetCount(normalizedStack, 1);
                normalized.add(normalizedStack);
            }
            this.stacks = Collections.unmodifiableList(normalized);
            this.alternativeDescription = description == null ? "" : description;
        }

        void setLootingData(int looting, int chance, Map<Integer, Integer> sizeChances) {
            if (looting < 0 || looting >= chances.length) {
                return;
            }
            chances[looting] = chance;
            sizes.set(looting, Collections.unmodifiableMap(new LinkedHashMap<>(sizeChances)));
        }

        void setSpecialTooltip(List<String> lines) {
            specialTooltip = Collections.unmodifiableList(new ArrayList<>(lines));
        }

        void setTableDerivedData(int[] minimum, int[] maximum, List<String> notes) {
            List<String> tooltip = new ArrayList<>();
            tooltip.add(TextFormatting.DARK_AQUA + "Potential output from the final loot table");
            tooltip.add(TextFormatting.DARK_GRAY
                    + "Not sampled through Woot; learned Woot data takes precedence");
            for (int looting = 0; looting < LOOTING_LABELS.length; looting++) {
                int min = looting < minimum.length ? Math.max(0, minimum[looting]) : 0;
                int max = looting < maximum.length ? Math.max(min, maximum[looting]) : min;
                String amount = min == max ? Integer.toString(min) : min + "-" + max;
                tooltip.add(TextFormatting.GRAY + LOOTING_LABELS[looting] + ": "
                        + TextFormatting.WHITE + amount + " item(s) per selected table entry");
            }
            for (String note : notes) {
                tooltip.add(TextFormatting.DARK_GRAY + note);
            }
            tooltip.add(TextFormatting.DARK_GRAY
                    + "Death-event and hardcoded drops may still require Woot learning");
            setSpecialTooltip(tooltip);
        }

        void appendTooltip(List<String> tooltip) {
            if (!specialTooltip.isEmpty()) {
                tooltip.addAll(specialTooltip);
                return;
            }
            tooltip.add(TextFormatting.DARK_GRAY + "Woot drop data");
            if (!alternativeDescription.isEmpty()) {
                tooltip.add(TextFormatting.GRAY + alternativeDescription);
            }
            for (int looting = 0; looting < chances.length; looting++) {
                if (chances[looting] < 0) {
                    continue;
                }
                tooltip.add(TextFormatting.GRAY + LOOTING_LABELS[looting] + ": "
                        + TextFormatting.WHITE + chances[looting] + "% drop chance");
                Map<Integer, Integer> sizeChances = sizes.get(looting);
                if (!sizeChances.isEmpty()) {
                    tooltip.add(TextFormatting.DARK_GRAY + "  Stack sizes: " + formatSizes(sizeChances));
                }
            }
        }

        private static String formatSizes(Map<Integer, Integer> sizes) {
            StringBuilder text = new StringBuilder();
            for (Map.Entry<Integer, Integer> entry : sizes.entrySet()) {
                if (text.length() > 0) {
                    text.append(", ");
                }
                text.append(entry.getKey()).append(" (").append(entry.getValue()).append("%)");
            }
            return text.toString();
        }
    }

    static final class UpgradeInfo {
        private final List<ItemStack> stacks;
        private final List<String> tooltip;

        UpgradeInfo(List<ItemStack> stacks, List<String> tooltip) {
            List<ItemStack> normalized = new ArrayList<>(stacks.size());
            for (ItemStack stack : stacks) {
                if (!MinecraftMappingCompat.itemStackIsEmpty(stack)) {
                    normalized.add(copy(stack));
                }
            }
            this.stacks = Collections.unmodifiableList(normalized);
            this.tooltip = Collections.unmodifiableList(new ArrayList<>(tooltip));
        }

        List<ItemStack> getStacks() {
            List<ItemStack> copies = new ArrayList<>(stacks.size());
            for (ItemStack stack : stacks) {
                copies.add(copy(stack));
            }
            return copies;
        }

        void appendTooltip(List<String> target) {
            target.addAll(tooltip);
        }
    }
}
