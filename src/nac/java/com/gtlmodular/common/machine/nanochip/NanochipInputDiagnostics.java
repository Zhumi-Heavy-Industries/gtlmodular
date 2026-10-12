package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic.InputNeed;
import com.gtlmodular.common.machine.nanochip.display.NanochipInputSnapshot.Material;
import com.gtlmodular.common.machine.nanochip.display.NanochipInputSnapshot.Sample;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPatternRecipeHandlePart;

import java.util.ArrayList;
import java.util.List;

/** 诊断单独限频，不以观测结果参与准入，也不调用会准备或消费输入的 handler 方法。 */
final class NanochipInputDiagnostics {
    private static final int SAMPLE_INTERVAL = 20;
    private static final int STOCK_LIMIT = 64;
    private static final int MATERIAL_LIMIT = 16;
    private final IRecipeLogicMachine machine;
    private GTRecipe recipe;
    private long recipeTick = -1;
    private long lastRequest = -1;
    private long lastAttempt = -1;
    private Sample cached;

    NanochipInputDiagnostics(IRecipeLogicMachine machine) {
        this.machine = machine;
    }

    void observe(GTRecipe candidate) {
        recipe = candidate;
        recipeTick = gameTime();
    }

    void clear() {
        recipe = null;
        cached = null;
        lastAttempt = -1;
        lastRequest = -1;
        recipeTick = -1;
    }

    Sample sample() {
        long now = gameTime();
        if (now < 0) return null;
        lastRequest = now;
        if (due(now)) collect(now);
        return cached;
    }

    long probeBound(GTRecipe candidate) {
        long now = gameTime();
        // 有正在查看的界面时复用准入本来就要做的单槽探测，保留采样发生在扣料前的时间。
        if (candidate == recipe && lastRequest >= 0 && now >= lastRequest
                && now - lastRequest <= SAMPLE_INTERVAL * 2 && due(now)) {
            return collect(now);
        }
        return NanochipInputProbe.scan(machine, candidate, null).bound();
    }

    private boolean due(long now) {
        return lastAttempt < 0 || now < lastAttempt || now - lastAttempt >= SAMPLE_INTERVAL;
    }

    private long collect(long now) {
        lastAttempt = now;
        if (recipe == null) {
            cached = null;
            return NanochipThreadInputs.NO_SLOT_BOUND;
        }
        Survey survey = null;
        try {
            survey = new Survey(recipe);
        } catch (RuntimeException | LinkageError ignored) {
            // 自定义材料无法展示时仍执行原探测，不能让显示失败影响准入。
        }
        Survey observer = survey;
        NanochipInputProbe.Result result = NanochipInputProbe.scan(machine, recipe, observer == null ? null
                : observer::accept);
        try {
            if (survey != null) survey.readFirst(machine, recipe);
            cached = survey == null ? missing(now) : survey.finish(recipe, now, recipeTick, result);
        } catch (RuntimeException | LinkageError ignored) {
            cached = missing(now);
        }
        return result.bound();
    }

    private Sample missing(long now) {
        return new Sample(recipe.getId() == null ? "?" : recipe.getId().toString(), now, recipeTick,
                0, 0, null, null, false, List.of());
    }

    String cachedDescription() {
        return cached == null ? "未按需采样（打开主机面板或 Jade 详情）"
                : "配方=" + cached.recipeId() + "；采样@" + cached.tick()
                + "；库存槽=" + cached.stockSlots() + "；探测槽=" + cached.probeSlots()
                + "；首槽输入并行估算=" + cached.firstParallel() + "；最大单槽输入并行估算=" + cached.bestParallel()
                + (cached.complete() ? "" : "；不完整");
    }

    private long gameTime() {
        var level = machine.self().getLevel();
        return level == null || level.isClientSide ? -1 : level.getGameTime();
    }

    private static final class Survey {
        private final List<InputNeed<ItemStack>> items;
        private final List<InputNeed<FluidStack>> fluids;
        private final List<Material> labels = new ArrayList<>();
        private final long[] aggregate;
        private final Long[] first;
        private Long firstBound;
        private boolean complete = true;
        private boolean ambiguous;
        private int stockSlots;

        Survey(GTRecipe recipe) {
            items = NanochipInputProbe.itemNeedsOf(recipe);
            fluids = NanochipInputProbe.fluidNeedsOf(recipe);
            if (items == null || fluids == null) throw new IllegalArgumentException("Unsupported ingredient");
            for (InputNeed<ItemStack> need : items) {
                ItemStack[] choices = ((Ingredient) need.test).getItems();
                addLabel(choices.length == 0 ? unknown() : choices[0].getHoverName(), choices.length, false, need.perCraft);
            }
            for (InputNeed<FluidStack> need : fluids) {
                FluidStack[] choices = ((FluidIngredient) need.test).getStacks();
                addLabel(choices.length == 0 ? unknown() : choices[0].getDisplayName(), choices.length, true, need.perCraft);
            }
            aggregate = new long[labels.size()];
            first = new Long[labels.size()];
            // 概率、不消耗材料及额外能力不能靠可消耗库存推导完整的可行并行。
            if (recipe.inputs.entrySet().stream().anyMatch(e ->
                    (e.getKey() != ItemRecipeCapability.CAP && e.getKey() != FluidRecipeCapability.CAP)
                            || e.getValue().stream().anyMatch(c -> c.chance != c.maxChance))) complete = false;
            if (labels.isEmpty() || labels.size() > MATERIAL_LIMIT) complete = false;
        }

        private void addLabel(Component name, int choices, boolean fluid, long amount) {
            if (choices == 0) complete = false;
            labels.add(new Material(choices > 1 ? Component.translatable(
                    "gtlmodular.nanochip.thread_tuning.alternatives", name, choices) : name,
                    fluid, amount, null, 0L));
        }

        void accept(Object2LongMap<?> itemStock, Object2LongMap<?> fluidStock, int count) {
            try {
                if ((!items.isEmpty() && itemStock == null) || (!fluids.isEmpty() && fluidStock == null)) {
                    ambiguous = true;
                    complete = false;
                }
                if (count <= STOCK_LIMIT) {
                    stockSlots++;
                    addAmounts(items, itemStock, 0, aggregate);
                    addAmounts(fluids, fluidStock, items.size(), aggregate);
                } else complete = false;
                // 重叠谓词可重复计算同一份库存，不能把这种估算冒充可行并行。
                if (overlaps(items, itemStock) || overlaps(fluids, fluidStock)) ambiguous = true;
            } catch (RuntimeException | LinkageError ignored) {
                complete = false;
                ambiguous = true;
            }
        }

        void readFirst(IRecipeLogicMachine machine, GTRecipe recipe) {
            if (!(machine instanceof IRecipeCapabilityMachine capable)
                    || !(capable.getActiveRecipeHandle(recipe) instanceof MEPatternRecipeHandlePart part)) {
                complete = false;
                return;
            }
            Object2LongMap<ItemStack> itemStock = part.getMEContent(ItemRecipeCapability.CAP, recipe);
            Object2LongMap<FluidStack> fluidStock = part.getMEContent(FluidRecipeCapability.CAP, recipe);
            long[] amounts = new long[labels.size()];
            addAmounts(items, itemStock, 0, amounts);
            addAmounts(fluids, fluidStock, items.size(), amounts);
            for (int i = 0; i < amounts.length; i++) first[i] = amounts[i];
            if (overlaps(items, itemStock) || overlaps(fluids, fluidStock)) ambiguous = true;
            long itemBound = capacity(items, itemStock);
            long fluidBound = capacity(fluids, fluidStock);
            long bound = Math.min(itemBound, fluidBound);
            firstBound = bound == Long.MAX_VALUE ? null : bound;
        }

        Sample finish(GTRecipe recipe, long now, long recipeTick, NanochipInputProbe.Result result) {
            List<Material> rows = new ArrayList<>();
            for (int i = 0; i < Math.min(MATERIAL_LIMIT, labels.size()); i++) {
                Material label = labels.get(i);
                rows.add(new Material(label.name(), label.fluid(), label.perCraft(), first[i], result.slots() == 0 ? null : aggregate[i]));
            }
            return new Sample(recipe.getId() == null ? "?" : recipe.getId().toString(), now, recipeTick,
                    stockSlots, result.slots(), ambiguous ? null : firstBound,
                    ambiguous || result.slots() == 0 || result.bound() < 0 ? null : result.bound(),
                    complete && !ambiguous && result.slots() > 0 && result.complete(), rows);
        }

        @SuppressWarnings("unchecked")
        private <T> void addAmounts(List<InputNeed<T>> needs, Object2LongMap<?> map, int offset, long[] target) {
            if (map == null) return;
            for (int i = 0; i < needs.size(); i++) {
                for (var entry : Object2LongMaps.fastIterable(map)) {
                    long amount = entry.getLongValue();
                    if (amount > 0 && needs.get(i).test.test((T) entry.getKey())) {
                        if (target[offset + i] > Long.MAX_VALUE - amount) {
                            target[offset + i] = Long.MAX_VALUE;
                            complete = false;
                        } else target[offset + i] += amount;
                    }
                }
            }
        }

        @SuppressWarnings("unchecked")
        private static <T> boolean overlaps(List<InputNeed<T>> needs, Object2LongMap<?> map) {
            if (map == null) return false;
            for (var entry : Object2LongMaps.fastIterable(map)) {
                if (entry.getLongValue() <= 0) continue;
                int matches = 0;
                for (var need : needs) if (need.test.test((T) entry.getKey()) && ++matches > 1) return true;
            }
            return false;
        }

        private static <T> long capacity(List<InputNeed<T>> needs, Object2LongMap<?> map) {
            return needs.isEmpty() ? Long.MAX_VALUE : NanochipInputProbe.slotCraftCapacity(needs, map);
        }

        private static Component unknown() {
            return Component.translatable("gtlmodular.nanochip.thread_tuning.unknown");
        }
    }
}
