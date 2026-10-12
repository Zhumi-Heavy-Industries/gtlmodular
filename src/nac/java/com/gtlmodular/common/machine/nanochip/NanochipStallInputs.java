package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.gtlcore.api.capability.IMERecipeHandler;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternPartMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPatternRecipeHandlePart;
import org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** 只读现有接线和库存；不能用模拟匹配取代采样，模拟也会准备 handler 或填充配方缓存。 */
final class NanochipStallInputs {
    private static final int SLOT_LIMIT = 256;
    private static final int CONTENT_LIMIT = 64;
    private final Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<String> stocks = new ArrayList<>();
    private final List<String> caches = new ArrayList<>();
    private boolean present;
    private boolean complete = true;
    private int slots;

    record Snapshot(String materials, String caches, boolean present, boolean complete) {}

    static Snapshot capture(NanochipAssemblyComplexMachine host) {
        NanochipStallInputs survey = new NanochipStallInputs();
        try {
            if (host instanceof IRecipeCapabilityMachine capable) {
                List<MEPatternRecipeHandlePart> parts = capable.getMEPatternRecipeHandleParts();
                if (parts != null) {
                    for (int i = 0; i < parts.size() && survey.slots < SLOT_LIMIT; i++) {
                        survey.patternPart(parts.get(i), i);
                    }
                }
                List<RecipeHandlePart> normal = capable.getNormalRecipeHandlePart(IO.IN);
                if (normal != null) for (RecipeHandlePart part : normal) survey.normalPart(part, "普通输入");
                RecipeHandlePart shared = capable.getSharedRecipeHandlePart();
                if (shared != null) survey.normalPart(shared, "共享输入");
            } else {
                survey.complete = false;
                survey.caches.add("主机没有上游配方接线接口");
            }
            // 能力代理可能包含未进入上游接线表的仓，分列以暴露“有库存但未接通”。
            for (var cell : host.getCapabilitiesProxy().cellSet()) {
                if (cell.getRowKey() == IO.IN || cell.getRowKey() == IO.BOTH) {
                    for (var handler : cell.getValue()) survey.normalHandler(handler, "仅能力代理");
                }
            }
        } catch (RuntimeException | LinkageError e) {
            survey.complete = false;
            survey.caches.add("读取失败=" + e.getClass().getSimpleName());
        }
        if (survey.slots >= SLOT_LIMIT || survey.seen.isEmpty()) survey.complete = false;
        Collections.sort(survey.stocks);
        Collections.sort(survey.caches);
        return new Snapshot(survey.stocks.toString(), survey.caches.toString(), survey.present, survey.complete);
    }

    private void patternPart(MEPatternRecipeHandlePart part, int index) {
        for (IMERecipeHandler<?, ?> handler : part.getMERecipeHandlers()) {
            if (handler == null || !seen.add(handler)) continue;
            String face = "ME接线" + index + location(handler) + "/" + handler.getCapability().name;
            int[] active = handler.getActiveSlots();
            if (active == null) {
                complete = false;
                continue;
            }
            for (int slot : active) {
                if (slots++ >= SLOT_LIMIT) return;
                Object2LongMap<?> stock = handler.getSingleSlotStackMap(slot);
                List<String> values = new ArrayList<>();
                int count = 0;
                for (var entry : stock.object2LongEntrySet()) {
                    if (++count > CONTENT_LIMIT) {
                        complete = false;
                        break;
                    }
                    add(values, entry.getKey(), entry.getLongValue());
                }
                Collections.sort(values);
                stocks.add(face + "/槽" + slot + "=" + values);
                // getCachedGTRecipe 在部分总成上会清理缓存；逐槽原始表才能保持只读。
                caches.add(face + "/槽" + slot + "=" + slotCache(handler, slot));
            }
        }
    }

    private String slotCache(Object handler, int slot) {
        if (handler instanceof MachineTrait trait && trait.getMachine() instanceof IMEPatternPartMachine part) {
            var recipes = part.getMETrait().getSlot2RecipesCache().get(slot);
            return recipes == null ? "[]" : recipeIds(recipes);
        }
        complete = false;
        return "接口未暴露逐槽缓存";
    }

    private String recipeIds(Iterable<GTRecipe> recipes) {
        Set<String> ids = new TreeSet<>();
        for (GTRecipe recipe : recipes) {
            if (ids.size() >= CONTENT_LIMIT) {
                complete = false;
                ids.add("…缓存明细截断");
                break;
            }
            ids.add(recipe.getId() == null ? "?" : recipe.getId().toString());
        }
        return ids.toString();
    }

    private void normalPart(RecipeHandlePart part, String kind) {
        for (var entry : part.getHandlerFastIterable()) {
            for (IRecipeHandler<?> handler : entry.getValue()) normalHandler(handler, kind);
        }
    }

    private void normalHandler(IRecipeHandler<?> handler, String kind) {
        if (handler.getCapability() != ItemRecipeCapability.CAP && handler.getCapability() != FluidRecipeCapability.CAP) return;
        if (slots >= SLOT_LIMIT || !seen.add(handler)) return;
        // ME 库存必须按槽读取，通用 getContents 可能只返回搜索用的限量视图。
        if (handler instanceof IMERecipeHandler<?, ?>) {
            complete = false;
            caches.add(kind + location(handler) + "=ME handler 未在主机样板接线表内");
            return;
        }
        List<String> values = new ArrayList<>();
        for (Object content : handler.getContents()) {
            if (slots++ >= SLOT_LIMIT) break;
            if (content instanceof ItemStack stack) add(values, stack, stack.getCount());
            else if (content instanceof FluidStack stack) add(values, stack, stack.getAmount());
            else complete = false;
        }
        Collections.sort(values);
        stocks.add(kind + location(handler) + "/" + handler.getCapability().name + "=" + values);
    }

    private void add(List<String> values, Object content, long amount) {
        if (amount <= 0) return;
        if (content instanceof ItemStack stack && !stack.isEmpty()) {
            present = true;
            values.add(BuiltInRegistries.ITEM.getKey(stack.getItem()) + "×" + amount + "个/tag="
                    + (stack.getTag() == null ? 0 : stack.getTag().hashCode()));
        } else if (content instanceof FluidStack stack && !stack.isEmpty()) {
            present = true;
            values.add(BuiltInRegistries.FLUID.getKey(stack.getFluid()) + "×" + amount + "mB/tag="
                    + (stack.getTag() == null ? 0 : stack.getTag().hashCode()));
        } else complete = false;
    }

    private static String location(Object handler) {
        return handler instanceof MachineTrait trait ? "@" + trait.getMachine().getPos() : "/" + handler.getClass().getSimpleName();
    }
}
