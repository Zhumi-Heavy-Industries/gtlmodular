package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.diag.SwallowedErrors;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic.InputNeed;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.gtlcore.gtlcore.api.capability.IMERecipeHandler;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPatternRecipeHandlePart;

import java.util.ArrayList;
import java.util.List;

import static com.gtlmodular.common.machine.nanochip.NanochipThreadInputs.NO_SLOT_BOUND;

final class NanochipInputProbe {
    private static final int PER_SLOT_SCAN_LIMIT = 256;

    private NanochipInputProbe() {}

    /** MEPatternRecipeHandlePart#handleRecipe 使用单个槽完成输入，容量上界只能取单槽最大值，不能跨槽求和。 */
    record Result(long bound, int slots, boolean complete) {}

    interface Observer {
        void slot(Object2LongMap<?> items, Object2LongMap<?> fluids, int scanned);
    }

    static Result scan(IRecipeLogicMachine machine, GTRecipe recipe, Observer observer) {
        try {
            if (recipe == null || !(machine instanceof IRecipeCapabilityMachine capabilityMachine)) {
                return new Result(NO_SLOT_BOUND, 0, false);
            }
            List<InputNeed<ItemStack>> itemNeeds = itemNeedsOf(recipe);
            List<InputNeed<FluidStack>> fluidNeeds = fluidNeedsOf(recipe);
            if (itemNeeds == null || fluidNeeds == null) {
                return new Result(NO_SLOT_BOUND, 0, false);
            }
            if (itemNeeds.isEmpty() && fluidNeeds.isEmpty()) {
                return new Result(NO_SLOT_BOUND, 0, false);
            }
            List<MEPatternRecipeHandlePart> parts = capabilityMachine.getMEPatternRecipeHandleParts();
            if (parts == null || parts.isEmpty()) {
                return new Result(NO_SLOT_BOUND, 0, false);
            }
            long best = 0L;
            int scanned = 0;
            for (MEPatternRecipeHandlePart part : parts) {
                IMERecipeHandler<?, ?>[] handlers = part.getMERecipeHandlers();
                if (handlers == null) {
                    continue;
                }
                int[] active = activeSlotsOf(handlers);
                if (active == null) {
                    continue;
                }
                for (int slot : active) {
                    if (scanned >= PER_SLOT_SCAN_LIMIT) {
                        // 达到扫描上限时只采用已扫描的容量，避免未读库存虚增并行。
                        return new Result(best, scanned, false);
                    }
                    scanned++;
                    long capacity = slotCraftCapacityAcrossSides(itemNeeds, fluidNeeds, handlers, slot, observer, scanned);
                    if (capacity > best) {
                        best = capacity;
                    }
                }
            }
            return new Result(best, scanned, true);
        } catch (Throwable e) {
            SwallowedErrors.report("NanochipInputProbe#scan", e);
            return new Result(NO_SLOT_BOUND, 0, false);
        }
    }

    /** 物品与流体由同一槽完成，必须按相同下标取两侧容量的较小值。 */
    static long slotCraftCapacityAcrossSides(List<InputNeed<ItemStack>> itemNeeds,
                                              List<InputNeed<FluidStack>> fluidNeeds,
                                              IMERecipeHandler<?, ?>[] handlers, int slot, Observer observer, int scanned) {
        long best = Long.MAX_VALUE;
        boolean measured = false;
        Object2LongMap<?> itemStock = null;
        Object2LongMap<?> fluidStock = null;
        for (IMERecipeHandler<?, ?> handler : handlers) {
            if (handler == null) {
                continue;
            }
            RecipeCapability<?> cap = handler.getCapability();
            if (cap == ItemRecipeCapability.CAP) {
                if (itemNeeds.isEmpty()) {
                    continue;
                }
                itemStock = handler.getSingleSlotStackMap(slot);
                best = Math.min(best, slotCraftCapacity(itemNeeds, itemStock));
                measured = true;
            } else if (cap == FluidRecipeCapability.CAP) {
                if (fluidNeeds.isEmpty()) {
                    continue;
                }
                fluidStock = handler.getSingleSlotStackMap(slot);
                best = Math.min(best, slotCraftCapacity(fluidNeeds, fluidStock));
                measured = true;
            }
        }
        if (observer != null) observer.slot(itemStock, fluidStock, scanned);
        return measured && best != Long.MAX_VALUE ? Math.max(0L, best) : 0L;
    }

    @SuppressWarnings("unchecked")
    static <T> long slotCraftCapacity(List<InputNeed<T>> needs, Object2LongMap<?> stock) {
        if (needs == null || needs.isEmpty() || stock == null || stock.isEmpty()) {
            return 0L;
        }
        long capacity = Long.MAX_VALUE;
        for (InputNeed<T> need : needs) {
            long available = 0L;
            for (Object2LongMap.Entry<?> entry : Object2LongMaps.fastIterable(stock)) {
                long amount = entry.getLongValue();
                if (amount > available && need.test.test((T) entry.getKey())) {
                    available = amount;   // 同一需求只取一个匹配库存条目的最大量，不能把替代物品合并当成一份可消费库存。
                }
            }
            if (available < need.perCraft) {
                return 0L;
            }
            capacity = Math.min(capacity, available / need.perCraft);
        }
        return capacity == Long.MAX_VALUE ? 0L : capacity;
    }

    /** 同一 ME 样板仓各 handler 的活跃槽集合一致，取一份即可避免重复遍历。 */
    private static int[] activeSlotsOf(IMERecipeHandler<?, ?>[] handlers) {
        for (IMERecipeHandler<?, ?> handler : handlers) {
            if (handler == null) {
                continue;
            }
            int[] active = handler.getActiveSlots();
            if (active != null) {
                return active;
            }
        }
        return null;
    }

    static List<InputNeed<ItemStack>> itemNeedsOf(GTRecipe recipe) {
        List<InputNeed<ItemStack>> needs = new ArrayList<>(2);
        List<Content> contents = recipe.getInputContents(ItemRecipeCapability.CAP);
        if (contents == null || contents.isEmpty()) {
            return needs;
        }
        for (Content content : contents) {
            if (content == null || content.chance <= 0) {
                continue;
            }
            Ingredient ingredient = ItemRecipeCapability.CAP.of(content.content);
            if (ingredient == null) {
                return null;
            }
            long perCraft;
            if (ingredient instanceof org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient longIngredient) {
                perCraft = longIngredient.getActualAmount();
            } else if (ingredient instanceof SizedIngredient sized) {
                perCraft = sized.getAmount();
            } else {
                perCraft = 1L;
            }
            if (perCraft <= 0L) {
                return null;
            }
            needs.add(new InputNeed<>(ingredient, perCraft));
        }
        return needs;
    }

    /** 流体谓词使用 LDLib FluidStack，库存键必须采用相同类型才能匹配。 */
    static List<InputNeed<FluidStack>> fluidNeedsOf(GTRecipe recipe) {
        List<InputNeed<FluidStack>> needs = new ArrayList<>(2);
        List<Content> contents = recipe.getInputContents(FluidRecipeCapability.CAP);
        if (contents == null || contents.isEmpty()) {
            return needs;
        }
        for (Content content : contents) {
            if (content == null || content.chance <= 0) {
                continue;
            }
            FluidIngredient fluid = FluidRecipeCapability.CAP.of(content.content);
            if (fluid == null) {
                return null;
            }
            long perCraft = fluid.getAmount();
            if (perCraft <= 0L) {
                return null;
            }
            needs.add(new InputNeed<>(fluid, perCraft));
        }
        return needs;
    }

}
