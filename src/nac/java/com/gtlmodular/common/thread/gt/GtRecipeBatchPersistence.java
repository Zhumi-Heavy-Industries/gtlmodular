package com.gtlmodular.common.thread.gt;

import com.gtlmodular.common.thread.BatchState;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * 沿用普通配方 CODEC 的批次格式；WirelessGTRecipe 还需要额外的扩展状态，不能依靠本适配器完整恢复。
 */
public final class GtRecipeBatchPersistence {
    private final BatchNbtKeys keys;
    private final IntFunction<ResourceLocation> fallbackId;
    private final Diagnostics diagnostics;

    public GtRecipeBatchPersistence(BatchNbtKeys keys, IntFunction<ResourceLocation> fallbackId,
                                    Diagnostics diagnostics) {
        this.keys = Objects.requireNonNull(keys);
        this.fallbackId = Objects.requireNonNull(fallbackId);
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    public interface Diagnostics {
        void missingRecipe(int index);

        void invalidRecipe(int index);

        void readRunsFailed(RuntimeException exception);

        void writeRunsFailed(long runs, RuntimeException exception);
    }

    public void save(CompoundTag tag, Iterable<? extends BatchState<GTRecipe>> batches) {
        ListTag list = new ListTag();
        for (BatchState<GTRecipe> batch : batches) {
            GTRecipe recipe = batch.payload();
            CompoundTag entry = new CompoundTag();
            entry.putInt(keys.progress(), batch.progress());
            entry.putString(keys.id(), recipe.getId() == null ? "" : recipe.getId().toString());
            entry.putLong(keys.runs(), runsOf(recipe));
            GTRecipeSerializer.CODEC.encodeStart(NbtOps.INSTANCE, recipe)
                    .result()
                    .ifPresent(encoded -> entry.put(keys.recipe(), encoded));
            list.add(entry);
        }
        tag.put(keys.batches(), list);
    }

    // 接收方先清空旧表，再逐条接收已扣料的批次，避免恢复依赖开批准入或当前槽数。
    public <B extends BatchState<GTRecipe>> void load(CompoundTag tag, BatchState.Factory<GTRecipe, B> factory,
                                                     Consumer<B> restored) {
        ListTag list = tag.getList(keys.batches(), Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Tag encoded = entry.get(keys.recipe());
            if (encoded == null) {
                diagnostics.missingRecipe(i);
                continue;
            }
            GTRecipe recipe = GTRecipeSerializer.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElse(null);
            if (recipe == null) {
                diagnostics.invalidRecipe(i);
                continue;
            }
            if (recipe.getId() == null) {
                recipe.setId(restoredId(entry, i));
            }
            long runs = entry.getLong(keys.runs());
            // 缺失或非正的运行份数不能覆盖配方扩展自身的默认值。
            if (runs > 0L) {
                applyRuns(recipe, runs);
            }
            restored.accept(factory.create(recipe, Math.max(1, recipe.duration), entry.getInt(keys.progress())));
        }
    }

    private ResourceLocation restoredId(CompoundTag entry, int index) {
        String stored = entry.getString(keys.id());
        ResourceLocation parsed = stored.isEmpty() ? null : ResourceLocation.tryParse(stored);
        // 使用原始条目位置，避免跳过坏条后改变兜底 ID。
        return parsed != null ? parsed : fallbackId.apply(index);
    }

    private long runsOf(GTRecipe recipe) {
        long runs = realParallels(recipe);
        return runs > 0L ? runs : Math.max(1L, (long) recipe.parallels);
    }

    private long realParallels(GTRecipe recipe) {
        try {
            return IGTRecipe.of(recipe).getRealParallels();
        } catch (RuntimeException e) {
            diagnostics.readRunsFailed(e);
            return -1L;
        }
    }

    private void applyRuns(GTRecipe recipe, long runs) {
        try {
            IGTRecipe.of(recipe).setRealParallels(runs);
        } catch (RuntimeException e) {
            diagnostics.writeRunsFailed(runs, e);
        }
    }
}
