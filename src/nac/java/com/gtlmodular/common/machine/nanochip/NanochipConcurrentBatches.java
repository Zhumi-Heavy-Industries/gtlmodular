package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.thread.BatchPolicy;
import com.gtlmodular.common.thread.BatchState;
import com.gtlmodular.common.thread.ConcurrentBatchEngine;
import com.gtlmodular.common.thread.gt.BatchNbtKeys;
import com.gtlmodular.common.thread.gt.GtRecipeBatchPersistence;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import net.minecraft.nbt.CompoundTag;

/**
 * 保留 NAC 的批次类型与读写入口，使已有展示和联动代码继续访问内核持有的同一份状态。
 */
public final class NanochipConcurrentBatches {

    public static final class Batch implements BatchState<GTRecipe> {

        public final GTRecipe recipe;

        /** 起跑时固定时长，避免后续配方对象变化使进度条跳动。 */
        public final int duration;

        public int progress;

        private Batch(GTRecipe recipe) {
            this(recipe, Math.max(1, recipe.duration), 0);
        }

        private Batch(GTRecipe recipe, int duration, int progress) {
            this.recipe = recipe;
            this.duration = duration;
            this.progress = progress;
        }

        @Override
        public GTRecipe payload() {
            return recipe;
        }

        @Override
        public int duration() {
            return duration;
        }

        @Override
        public int progress() {
            return progress;
        }

        @Override
        public void setProgress(int progress) {
            this.progress = progress;
        }

        public boolean finished() {
            return progress >= duration;
        }

        public float percent() {
            return Math.min(1.0F, (float) progress / (float) duration);
        }
    }

    public interface Host {

        IRecipeLogicMachine machine();

        int slots();

        GTRecipe findBatch();

        // 只有扣料成功才能入表；跨 handler 的回滚保证由上游负责。
        boolean consumeInputs(GTRecipe recipe);

        /** 输入不足必须返回 false，让已扣料的批次原地等待。 */
        boolean tickInputs(GTRecipe recipe);

        void finishOutputs(GTRecipe recipe);
    }

    /** 保留原键以读取已有批次存档；旧档缺键时按空表恢复，不补扣物料。 */
    public static final String TAG_BATCHES = "gtlmodular_nanochip_threads";

    private static final String TAG_PROGRESS = "progress";

    // 配方 CODEC 不保存 ID，必须单独存一份。
    private static final String TAG_ID = "id";

    private static final String TAG_RECIPE = "recipe";

    // 概率产出按运行份数结算，不能只恢复配方内容。
    private static final String TAG_RUNS = "runs";

    private static final GtRecipeBatchPersistence PERSISTENCE = new GtRecipeBatchPersistence(
            new BatchNbtKeys(TAG_BATCHES, TAG_RECIPE, TAG_ID, TAG_PROGRESS, TAG_RUNS),
            index -> NanochipModContext.registryId("nanochip_batch_" + index),
            new GtRecipeBatchPersistence.Diagnostics() {
                @Override
                public void missingRecipe(int index) {
                    NanochipModContext.logger().debug("[gtlmodular] NAC 并发批次：存档第 {} 条没有配方本体键（{}），跳过该条",
                            index, TAG_RECIPE);
                }

                @Override
                public void invalidRecipe(int index) {
                    NanochipModContext.logger().debug("[gtlmodular] NAC 并发批次：存档第 {} 条的配方解析失败，跳过该条", index);
                }

                @Override
                public void readRunsFailed(RuntimeException exception) {
                    NanochipModContext.logger().debug("[gtlmodular] NAC 并发批次：读取 realParallels 失败（{}），改用并行字段",
                            exception.toString());
                }

                @Override
                public void writeRunsFailed(long runs, RuntimeException exception) {
                    NanochipModContext.logger().debug("[gtlmodular] NAC 并发批次：写回 realParallels={} 失败（{}），该条批次照常恢复",
                            runs, exception.toString());
                }
            });

    private final ConcurrentBatchEngine<GTRecipe, Batch> batches =
            new ConcurrentBatchEngine<GTRecipe, Batch>(Batch::new);

    public int size() {
        return batches.size();
    }

    public Batch get(int index) {
        return batches.get(index);
    }

    public boolean isEmpty() {
        return batches.isEmpty();
    }

    /** 重置必须丢弃待运行批次；此入口不返还已扣物料，调用方不能把它当作撤销。 */
    public void clear() {
        batches.clear();
    }

    public boolean tick(Host host) {
        return batches.tick(host == null ? null : new HostPolicy(host));
    }

    private record HostPolicy(Host host) implements BatchPolicy<GTRecipe> {
        @Override
        public int slots() {
            return host.slots();
        }

        @Override
        public GTRecipe findBatch() {
            return host.findBatch();
        }

        @Override
        public int duration(GTRecipe recipe) {
            return recipe.duration;
        }

        @Override
        public boolean consumeInputs(GTRecipe recipe) {
            return host.consumeInputs(recipe);
        }

        @Override
        public boolean tickInputs(GTRecipe recipe) {
            return host.tickInputs(recipe);
        }

        @Override
        public void finishOutputs(GTRecipe recipe) {
            host.finishOutputs(recipe);
        }
    }

    // 普通配方 CODEC 不保存 ID 和运行份数，额外记录它们才能继续按原配方结算概率产出。
    public void save(CompoundTag tag) {
        PERSISTENCE.save(tag, batches);
    }

    // 已扣料的批次直接恢复，不能再次走开批准入；坏条也不能妨碍其余批次恢复。
    public void load(CompoundTag tag) {
        batches.clear();
        PERSISTENCE.load(tag, Batch::new, batches::restoreBatch);
        if (!batches.isEmpty()) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 并发批次：从存档恢复 {} 条批次", batches.size());
        }
    }
}
