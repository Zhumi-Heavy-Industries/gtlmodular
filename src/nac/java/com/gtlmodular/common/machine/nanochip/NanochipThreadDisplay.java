package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gtlmodular.common.machine.display.ThreadDisplaySnapshot;
import com.gtlmodular.common.machine.display.ThreadDisplayText;
import com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic.ThreadRec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic.DISPLAY_LINES;

final class NanochipThreadDisplay {

    private final NanochipThreadedRecipeLogic logic;
    private final IRecipeLogicMachine machine;
    private final NanochipConcurrentBatches batches;
    private final NanochipBatchRuntime batchRuntime;
    private final Function<NanochipConcurrentBatches.Batch, ThreadRec> threadView;
    // 单机 WORKING 不能代表每条批次都推进，显示只记录原有 tickInputs 的返回值。
    private final Map<GTRecipe, Boolean> tickResults = new IdentityHashMap<>();
    private String unadvancedReason = "gtlmodular.nanochip.thread_details.pending";

    NanochipThreadDisplay(NanochipThreadedRecipeLogic logic, IRecipeLogicMachine machine,
                          NanochipConcurrentBatches batches, NanochipBatchRuntime batchRuntime,
                          Function<NanochipConcurrentBatches.Batch, ThreadRec> threadView) {
        this.logic = logic;
        this.machine = machine;
        this.batches = batches;
        this.batchRuntime = batchRuntime;
        this.threadView = threadView;
    }

    int distinctRunningRecipeCount() {
        int size = batches.size();
        if (size <= 0) {
            return 0;
        }
        Set<ResourceLocation> ids = new HashSet<>(size);
        Map<GTRecipe, Boolean> seenNoId = new IdentityHashMap<>();
        int distinct = 0;
        for (int i = 0; i < size; i++) {
            NanochipConcurrentBatches.Batch batch = batches.get(i);
            GTRecipe recipe = batch == null ? null : batch.recipe;
            if (recipe == null) {
                continue;
            }
            ResourceLocation id = recipe.getId();
            if (id != null) {
                if (ids.add(id)) {
                    distinct++;
                }
            } else if (seenNoId.put(recipe, Boolean.TRUE) == null) {
                // 无 ID 时按引用去重，避免把不同匿名配方合成一种。
                distinct++;
            }
        }
        return distinct;
    }

    /** Jade 需识别原生进度条对应的批次，避免附加条重复显示同一批次。 */
    int mirroredThreadLabel() {
        return batches.isEmpty() ? -1 : threadLabelOf(batches.get(0).recipe);
    }

    long mirroredThreadParallels() {
        return batches.isEmpty() ? 0L : Math.max(1L, realParallelsOf(batches.get(0).recipe));
    }

    List<Map.Entry<Integer, ThreadRec>> snapshot() {
        List<Map.Entry<Integer, ThreadRec>> out = new ArrayList<>(batches.size());
        for (int i = 0; i < batches.size(); i++) {
            NanochipConcurrentBatches.Batch batch = batches.get(i);
            out.add(Map.entry(threadLabelOf(batch.recipe), threadView.apply(batch)));
        }
        // 优先显示接近完成的批次，同进度使用稳定编号，便于持续观察。
        out.sort((a, b) -> {
            int pa = a.getValue().progress();
            int pb = b.getValue().progress();
            if (pa != pb) {
                return Integer.compare(pb, pa);
            }
            return Integer.compare(a.getKey(), b.getKey());
        });
        return out;
    }

    /** 批次完成后列表下标会移动，显示编号须独立于下标才能持续识别同一批次。 */
    int threadLabelOf(GTRecipe recipe) {
        return batchRuntime.threadLabelOf(recipe);
    }

    static String firstItemOutputId(GTRecipe recipe) {
        Content content = firstItemOutputContent(recipe);
        if (content == null) {
            return null;
        }
        ItemStack stack = itemStackOfContent(content.content);
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ResourceLocation id;
        try {
            id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        } catch (RuntimeException e) {
            return null;
        }
        return id == null ? null : id.toString();
    }

    /** 产出内容已含批次并行，显示数量不能再乘一次并行数。 */
    static long firstItemOutputAmount(GTRecipe recipe) {
        Content content = firstItemOutputContent(recipe);
        return content == null ? 0L : producedAmountOfContent(content.content);
    }

    private static Content firstItemOutputContent(GTRecipe recipe) {
        if (recipe == null) {
            return null;
        }
        List<Content> outs;
        try {
            outs = recipe.getOutputContents(ItemRecipeCapability.CAP);
        } catch (RuntimeException e) {
            return null;
        }
        if (outs == null || outs.isEmpty()) {
            return null;
        }
        for (Content c : outs) {
            if (c != null && itemStackOfContent(c.content) != null) {
                return c;
            }
        }
        return null;
    }

    private static ItemStack itemStackOfContent(Object content) {
        if (content instanceof ItemStack stack) {
            return stack.isEmpty() ? null : stack;
        }
        if (content instanceof ItemStack[] stacks) {
            for (ItemStack stack : stacks) {
                if (stack != null && !stack.isEmpty()) {
                    return stack;
                }
            }
            return null;
        }
        if (content instanceof Ingredient ingredient) {
            try {
                ItemStack[] stacks = ingredient.getItems();
                if (stacks != null) {
                    for (ItemStack stack : stacks) {
                        if (stack != null && !stack.isEmpty()) {
                            return stack;
                        }
                    }
                }
            } catch (RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    /** Ingredient 的样例栈数量可能为 1，须优先读取实际数量接口。 */
    private static long producedAmountOfContent(Object content) {
        if (content instanceof org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient longIngredient) {
            return longIngredient.getActualAmount();
        }
        if (content instanceof SizedIngredient sized) {
            return sized.getAmount();
        }
        ItemStack stack = itemStackOfContent(content);
        return stack == null ? 1L : Math.max(1L, (long) stack.getCount());
    }

    long totalRunsAtOnce() {
        long total = 0L;
        for (int i = 0; i < batches.size(); i++) {
            long runs = Math.max(1L, realParallelsOf(batches.get(i).recipe));
            total = total > Long.MAX_VALUE - runs ? Long.MAX_VALUE : total + runs;
        }
        return total;
    }

    void beginTick() {
        tickResults.clear();
        unadvancedReason = "gtlmodular.nanochip.thread_details.pending";
    }

    void machineBlocked() {
        unadvancedReason = "gtlmodular.nanochip.thread_details.machine_blocked";
    }

    void recordTick(GTRecipe recipe, boolean advanced) {
        tickResults.put(recipe, advanced);
    }

    ThreadDisplaySnapshot displaySnapshot() {
        List<ThreadDisplaySnapshot.Thread> rows = new ArrayList<>();
        boolean suspended = logic.isSuspend();
        boolean available = machine.isRecipeLogicAvailable();
        for (Map.Entry<Integer, ThreadRec> entry : snapshot()) {
            ThreadRec rec = entry.getValue();
            Boolean advanced = tickResults.get(rec.recipe());
            String reason = suspended ? "gtlmodular.nanochip.thread_details.suspended"
                    : !available ? "gtlmodular.nanochip.thread_details.unavailable"
                    : rec.progress() >= rec.duration() ? NanochipRecipeOutputs.OUTPUT_FULL
                    : advanced == null ? unadvancedReason
                    : !advanced ? "gtlmodular.nanochip.thread_details.blocked" : null;
            ThreadDisplaySnapshot.State state = reason == null
                    ? ThreadDisplaySnapshot.State.WORKING : ThreadDisplaySnapshot.State.WAITING;
            // 最终配方已经缩放过并行、模块与校准，读数不能再乘一次。
            rows.add(new ThreadDisplaySnapshot.Thread(entry.getKey(), RecipeHelper.getInputEUt(rec.recipe()),
                    rec.duration(), rec.progress(), rec.parallels(), state, reason,
                    firstItemOutputId(rec.recipe()), firstItemOutputAmount(rec.recipe())));
        }
        return new ThreadDisplaySnapshot(logic.getThreadLimit(), batches.size(), totalRunsAtOnce(),
                distinctRunningRecipeCount(), mirroredThreadLabel(), mirroredThreadParallels(), rows);
    }

    /** 批次表由服务端维护，客户端不能从空的本地表推导运行数量。 */
    void appendThreadDisplayText(List<Component> textList) {
        if (textList == null
                || machine.self().getLevel() == null
                || machine.self().getLevel().isClientSide) {
            return;
        }
        ThreadDisplaySnapshot snapshot = displaySnapshot();
        textList.addAll(ThreadDisplayText.summary(snapshot));
        ThreadDisplayText.appendThreads(textList, snapshot, DISPLAY_LINES);
    }

    static long realParallelsOf(GTRecipe recipe) {
        if (recipe == null) {
            return 0L;
        }
        try {
            long real = IGTRecipe.of(recipe).getRealParallels();
            return real > 0L ? real : Math.max(1L, recipe.parallels);
        } catch (RuntimeException e) {
            return Math.max(1L, recipe.parallels);
        }
    }
}
