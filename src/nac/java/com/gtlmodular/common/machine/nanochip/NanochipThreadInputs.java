package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic.InputNeed;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.gtlcore.gtlcore.api.capability.IMERecipeHandler;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPatternRecipeHandlePart;

import java.util.ArrayList;
import java.util.List;

import static com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic.INPUT_WIRING_REBUILD_GATED;

final class NanochipThreadInputs {

    private final IRecipeLogicMachine machine;
    private final NanochipConcurrentBatches batches;

    int diagHasProxies = -1;

    int diagWireParts = -1;

    int diagWireCached = -1;

    int lastSearchRaw = -1;

    boolean inputWiringRefreshedThisTick;

    private int inputWiringRefreshes;

    private int inputWiringBudget = INPUT_WIRING_REFRESH_BUDGET;

    private int inputWiringCooldown;

    private long inputWiringLastRefreshTick = -1L;

    private int inputWiringPartsBefore = -1;

    private int inputWiringPartsAfter = -1;

    private int inputWiringCachedBefore = -1;

    private int inputWiringCachedAfter = -1;

    private int inputWiringFutile;

    private String inputWiringStopReason;

    /** 诊断只读有限槽位，避免样板仓的大量槽位把观测本身变成性能负担。 */
    private static final int INPUT_FACE_SCAN_LIMIT = 64;

    static final long NO_SLOT_BOUND = -1L;

    private static final int PER_SLOT_SCAN_LIMIT = 256;

    private static final int INPUT_WIRING_REFRESH_INTERVAL = 100;

    private static final int INPUT_WIRING_FUTILE_MAX = 2;

    /** 重建可能重复注册输入变化监听器，因此必须限制总次数。 */
    private static final int INPUT_WIRING_REFRESH_BUDGET = 32;

    NanochipThreadInputs(IRecipeLogicMachine machine, NanochipConcurrentBatches batches) {
        this.machine = machine;
        this.batches = batches;
    }

    String inputFaceText() {
        StringBuilder sb = new StringBuilder(96);
        try {
            if (!(machine instanceof IRecipeCapabilityMachine capabilityMachine)) {
                return "（不是 gtlcore 能力机器）";
            }
            List<MEPatternRecipeHandlePart> parts = capabilityMachine.getMEPatternRecipeHandleParts();
            if (parts == null || parts.isEmpty()) {
                return "（没有样板仓接线）";
            }
            int scannedSlots = 0;
            int stockSlots = 0;
            long firstSlotAmount = 0L;
            long allSlotsAmount = 0L;
            for (int p = 0; p < parts.size(); p++) {
                IMERecipeHandler<?, ?>[] handlers = parts.get(p).getMERecipeHandlers();
                if (handlers == null) {
                    continue;
                }
                for (IMERecipeHandler<?, ?> handler : handlers) {
                    int[] active = handler.getActiveSlots();
                    if (active == null) {
                        continue;
                    }
                    for (int s = 0; s < active.length && scannedSlots < INPUT_FACE_SCAN_LIMIT; s++, scannedSlots++) {
                        long amount = stackMapAmount(handler.getSingleSlotStackMap(active[s]));
                        if (amount <= 0L) {
                            continue;
                        }
                        stockSlots++;
                        allSlotsAmount = allSlotsAmount > Long.MAX_VALUE - amount
                                ? Long.MAX_VALUE : allSlotsAmount + amount;
                        if (firstSlotAmount <= 0L) {
                            firstSlotAmount = amount;
                        }
                    }
                }
            }
            sb.append("参与槽=").append(scannedSlots)
                    .append(scannedSlots >= INPUT_FACE_SCAN_LIMIT ? "+（扫到上限，未扫完）" : "")
                    .append("；有货槽=").append(stockSlots)
                    .append("；首可用槽可得量=").append(firstSlotAmount)
                    .append("；全槽汇总可得量=").append(allSlotsAmount);
        } catch (Throwable e) {
            sb.append("（读取失败：").append(e.getClass().getSimpleName()).append("）");
        }
        return sb.toString();
    }

    private static long stackMapAmount(it.unimi.dsi.fastutil.objects.Object2LongMap<?> map) {
        if (map == null || map.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (long amount : map.values()) {
            if (amount > 0L) {
                total = total > Long.MAX_VALUE - amount ? Long.MAX_VALUE : total + amount;
            }
        }
        return total;
    }

    /** MEPatternRecipeHandlePart#handleRecipe 使用单个槽完成输入，容量上界只能取单槽最大值，不能跨槽求和。 */
    long perSlotInputBound(GTRecipe recipe) {
        try {
            if (recipe == null || !(machine instanceof IRecipeCapabilityMachine capabilityMachine)) {
                return NO_SLOT_BOUND;
            }
            List<InputNeed<ItemStack>> itemNeeds = itemNeedsOf(recipe);
            List<InputNeed<FluidStack>> fluidNeeds = fluidNeedsOf(recipe);
            if (itemNeeds == null || fluidNeeds == null) {
                return NO_SLOT_BOUND;
            }
            if (itemNeeds.isEmpty() && fluidNeeds.isEmpty()) {
                return NO_SLOT_BOUND;
            }
            List<MEPatternRecipeHandlePart> parts = capabilityMachine.getMEPatternRecipeHandleParts();
            if (parts == null || parts.isEmpty()) {
                return NO_SLOT_BOUND;
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
                        return best;
                    }
                    scanned++;
                    long capacity = slotCraftCapacityAcrossSides(itemNeeds, fluidNeeds, handlers, slot);
                    if (capacity > best) {
                        best = capacity;
                    }
                }
            }
            return best;
        } catch (Throwable e) {
            return NO_SLOT_BOUND;
        }
    }

    /** 物品与流体由同一槽完成，必须按相同下标取两侧容量的较小值。 */
    private long slotCraftCapacityAcrossSides(List<InputNeed<ItemStack>> itemNeeds,
                                              List<InputNeed<FluidStack>> fluidNeeds,
                                              IMERecipeHandler<?, ?>[] handlers, int slot) {
        long best = Long.MAX_VALUE;
        boolean measured = false;
        for (IMERecipeHandler<?, ?> handler : handlers) {
            if (handler == null) {
                continue;
            }
            RecipeCapability<?> cap = handler.getCapability();
            if (cap == ItemRecipeCapability.CAP) {
                if (itemNeeds.isEmpty()) {
                    continue;
                }
                best = Math.min(best, slotCraftCapacity(itemNeeds, handler.getSingleSlotStackMap(slot)));
                measured = true;
            } else if (cap == FluidRecipeCapability.CAP) {
                if (fluidNeeds.isEmpty()) {
                    continue;
                }
                best = Math.min(best, slotCraftCapacity(fluidNeeds, handler.getSingleSlotStackMap(slot)));
                measured = true;
            }
        }
        return measured && best != Long.MAX_VALUE ? Math.max(0L, best) : 0L;
    }

    @SuppressWarnings("unchecked")
    private static <T> long slotCraftCapacity(List<InputNeed<T>> needs, Object2LongMap<?> stock) {
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

    private static List<InputNeed<ItemStack>> itemNeedsOf(GTRecipe recipe) {
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
    private static List<InputNeed<FluidStack>> fluidNeedsOf(GTRecipe recipe) {
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

    void refreshInputWiringIfStale() {
        if (inputWiringCooldown > 0) {
            inputWiringCooldown--;
        }
        if (!batches.isEmpty() || lastSearchRaw != 0) {
            return;
        }
        if (inputWiringBudget <= 0 || inputWiringCooldown > 0) {
            return;
        }
        if (!(machine instanceof IRecipeCapabilityMachine capabilityMachine)) {
            return;
        }
        if (INPUT_WIRING_REBUILD_GATED) {
            int partsNow = mePatternPartCount(capabilityMachine);
            if (partsNow > 0) {
                inputWiringCooldown = INPUT_WIRING_REFRESH_INTERVAL;
                return;
            }
        }
        inputWiringCooldown = INPUT_WIRING_REFRESH_INTERVAL;
        inputWiringBudget--;
        inputWiringRefreshes++;
        inputWiringLastRefreshTick = currentGameTime();
        inputWiringPartsBefore = mePatternPartCount(capabilityMachine);
        inputWiringCachedBefore = mePatternCachedRecipeCount(capabilityMachine);
        // 重建可能抛异常，先清掉结果读数，避免诊断误用上一次成功值。
        inputWiringPartsAfter = -1;
        inputWiringCachedAfter = -1;
        try {
            capabilityMachine.upDate();
            inputWiringRefreshedThisTick = true;
        } catch (RuntimeException e) {
            inputWiringBudget = 0;
            inputWiringStopReason = "重建抛异常 ⇒ 已永久停手（" + e.getClass().getSimpleName() + "）";
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：输入侧接线重建失败（保持原样，不影响派活）：{}",
                    e.toString());
            return;
        }
        inputWiringPartsAfter = mePatternPartCount(capabilityMachine);
        inputWiringCachedAfter = mePatternCachedRecipeCount(capabilityMachine);
        if (!INPUT_WIRING_REBUILD_GATED) {
            return;
        }
        if (inputWiringPartsAfter == inputWiringPartsBefore
                && inputWiringCachedAfter == inputWiringCachedBefore) {
            inputWiringFutile++;
            if (inputWiringFutile >= INPUT_WIRING_FUTILE_MAX) {
                inputWiringBudget = 0;
                inputWiringStopReason = "重建前后一字未变 ⇒ 已永久停手（接线数 "
                        + inputWiringPartsAfter + "、已缓存配方 " + inputWiringCachedAfter + "）";
            }
        } else {
            inputWiringFutile = 0;
        }
    }

    private int mePatternPartCount(IRecipeCapabilityMachine capabilityMachine) {
        try {
            List<MEPatternRecipeHandlePart> parts = capabilityMachine.getMEPatternRecipeHandleParts();
            return parts == null ? -1 : parts.size();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private int mePatternCachedRecipeCount(IRecipeCapabilityMachine capabilityMachine) {
        try {
            List<MEPatternRecipeHandlePart> parts = capabilityMachine.getMEPatternRecipeHandleParts();
            if (parts == null) {
                return -1;
            }
            int cached = 0;
            for (MEPatternRecipeHandlePart part : parts) {
                ObjectSet<GTRecipe> set = part.getCachedGTRecipe();
                if (set != null) {
                    cached += set.size();
                }
            }
            return cached;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private long currentGameTime() {
        try {
            var level = machine.self().getLevel();
            return level == null ? -1L : level.getGameTime();
        } catch (RuntimeException e) {
            return -1L;
        }
    }

    String inputSideText() {
        StringBuilder sb = new StringBuilder(80);
        try {
            diagHasProxies = machine.hasProxies() ? 1 : 0;
        } catch (RuntimeException e) {
            diagHasProxies = -1;
        }
        sb.append("能力代理=").append(diagHasProxies < 0 ? "没读到" : (diagHasProxies == 1 ? "有" : "无"));
        try {
            if (machine instanceof IRecipeCapabilityMachine capabilityMachine) {
                List<MEPatternRecipeHandlePart> parts = capabilityMachine.getMEPatternRecipeHandleParts();
                diagWireParts = parts == null ? -1 : parts.size();
                int cached = 0;
                if (parts != null) {
                    for (MEPatternRecipeHandlePart part : parts) {
                        ObjectSet<GTRecipe> set = part.getCachedGTRecipe();
                        if (set != null) {
                            cached += set.size();
                        }
                    }
                }
                diagWireCached = cached;
            }
        } catch (RuntimeException e) {
            diagWireParts = -1;
            diagWireCached = -1;
        }
        sb.append("；样板仓接线=").append(diagWireParts < 0 ? "没读到" : String.valueOf(diagWireParts));
        sb.append("；已缓存配方=").append(diagWireCached < 0 ? "没读到" : String.valueOf(diagWireCached));
        sb.append("；接线重建=").append(inputWiringRefreshes).append("次")
                .append(inputWiringRefreshedThisTick ? "（本 tick 刚建过）" : "")
                .append("（余 ").append(Math.max(0, inputWiringBudget)).append(" 次）");
        if (inputWiringLastRefreshTick >= 0L) {
            sb.append("；上次重建@").append(inputWiringLastRefreshTick);
            sb.append("；重建前后[接线 ").append(inputWiringPartsBefore)
                    .append("→").append(inputWiringPartsAfter)
                    .append("；已缓存配方 ").append(inputWiringCachedBefore)
                    .append("→").append(inputWiringCachedAfter).append(']');
        }
        if (inputWiringStopReason != null) {
            sb.append("；接线重建已停手（").append(inputWiringStopReason).append("）");
        } else if (inputWiringFutile > 0) {
            sb.append("；重建已证伪 ").append(inputWiringFutile).append(" 次（连续 ")
                    .append(INPUT_WIRING_FUTILE_MAX).append(" 次即停手）");
        }
        return sb.toString();
    }
}
