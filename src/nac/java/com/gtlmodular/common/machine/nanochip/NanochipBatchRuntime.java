package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic.BatchState;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.gtlcore.gtlcore.api.recipe.IParallelLogic;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

final class NanochipBatchRuntime {
    private final IRecipeLogicMachine machine;
    private final Supplier<Map<RecipeCapability<?>, Object2IntMap<?>>> cacheFactory;
    // 同 ID 的不同配方对象也必须各持一份概率缓存；批次完成后的下标变化不能错配附注。
    private final Map<GTRecipe, BatchState> states = new IdentityHashMap<>();
    private long batchSeqCounter;

    NanochipBatchRuntime(IRecipeLogicMachine machine,
                         Supplier<Map<RecipeCapability<?>, Object2IntMap<?>>> cacheFactory) {
        this.machine = machine;
        this.cacheFactory = cacheFactory;
    }

    void opened(GTRecipe recipe) {
        BatchState state = stateOf(recipe);
        state.caches = cacheFactory.get();
        state.reservation = receiptOf(recipe);
        state.seq = ++batchSeqCounter;
    }

    void remove(GTRecipe recipe) {
        states.remove(recipe);
    }

    void clear() {
        // 清表和读档只丢弃运行期附注；显示编号仍沿用该逻辑实例的递增计数。
        states.clear();
    }

    int threadLabelOf(GTRecipe recipe) {
        if (recipe == null) {
            return 0;
        }
        BatchState state = stateOf(recipe);
        if (state.seq <= 0L) {
            state.seq = ++batchSeqCounter;
        }
        return (int) (state.seq % Integer.MAX_VALUE);
    }

    Map<RecipeCapability<?>, Object2IntMap<?>> cachesOf(GTRecipe recipe) {
        BatchState state = stateOf(recipe);
        if (state.caches == null) {
            state.caches = cacheFactory.get();
        }
        return state.caches;
    }

    GTRecipe overlayWithReservations(GTRecipe candidate, NanochipConcurrentBatches batches,
                                     boolean reserveNonTickInputs) {
        GTRecipe probe = candidate.copy();
        for (int i = 0; i < batches.size(); i++) {
            BatchState state = states.get(batches.get(i).recipe);
            // 读档不恢复预留，缺附注时也不重新掷概率或计算预留。
            if (state == null || state.reservation == null) {
                continue;
            }
            // 非 tick 输入已在开批时扣除；只有原策略要求时才再次叠入准入试算。
            if (reserveNonTickInputs) {
                appendContents(probe.inputs, state.reservation.inputs);
            }
            appendContents(probe.outputs, state.reservation.outputs);
            appendContents(probe.tickInputs, state.reservation.tickInputs);
            appendContents(probe.tickOutputs, state.reservation.tickOutputs);
        }
        return probe;
    }

    private BatchState stateOf(GTRecipe recipe) {
        BatchState state = states.get(recipe);
        if (state == null) {
            state = new BatchState();
            states.put(recipe, state);
        }
        return state;
    }

    private GTRecipe receiptOf(GTRecipe recipe) {
        if (recipe == null) {
            return null;
        }
        try {
            return IParallelLogic.getRecipeOutputChance(machine, recipe.copy());
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 独立线程：概率产出结算失败，预留按未结算记（偏保守）：{}",
                    e.toString());
            return recipe.copy();
        }
    }

    private static void appendContents(Map<RecipeCapability<?>, List<Content>> target,
                                       Map<RecipeCapability<?>, List<Content>> source) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        source.forEach((capability, contents) -> {
            if (capability != null && contents != null && !contents.isEmpty()) {
                target.computeIfAbsent(capability, ignored -> new ArrayList<>()).addAll(contents);
            }
        });
    }
}
