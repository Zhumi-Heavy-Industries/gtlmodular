package com.gtlmodular.common.machine.engine;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gtlmodular.GTLModular;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * ★ <b>跨配方线程调度器</b>（GTO {@code ICrossRecipeMachine.Logic} 的等价物）。
 *
 * <h2>一句话</h2>
 * 一台机器同时跑多条配方，每条是一个 {@link Thread}，各有自己的 {@code progress}／{@code duration}／
 * {@code use}。调度器只管三件事：推进、摘掉跑完的、有空位就开新线程。
 *
 * <h2>与旧版的关键差别</h2>
 * 开线程从「{@code findBatch()} 找配方 → 引擎再 {@code consumeInputs()}」改成
 * <b>{@link Host#openThread()} 一步完成</b>：内部走
 * {@link CrossRecipeTrait#lookupRecipe(RecipeCharge)}，由 {@link RecipeCharge} 的
 * {@code check → commit → rollback} 统一接管预留与扣料。这样「看到的库存」和「扣掉的库存」
 * 是同一次调用，不会出现两者之间被别的逻辑插一脚的窗口。
 *
 * <h2>每个 server tick 的三步顺序</h2>
 * <ol>
 *   <li>逐条推进：{@link Host#tickInputs(Thread)} 成功才 {@code progress++}；不够原地等；</li>
 *   <li>涨满的先摘表再 {@link Host#finishOutputs(Thread)}，保证收尾异常不会重复出料；</li>
 *   <li>有空位就 {@link Host#openThread()}，返回 null 立即停（断档），不回退成整机不跑。</li>
 * </ol>
 *
 * <p><b>不碰 UI 字段</b>：进度回写由调用方在 tick 之后做（见 {@link ThreadedRecipeLogic#serverTick()}）。
 * 存档仍写进调用方给的追加键，缺键/坏条都安全回退。
 *
 * @see Thread
 * @see CrossRecipeTrait
 * @see RecipeCharge
 */
public final class ConcurrentRecipeEngine {

    /** 线程表要「机器侧」回答的五件事（GTO Logic 的宿主口）。 */
    public interface Host {

        /** 宿主机器（供实现方取配方类型／能力仓；引擎本体不使用）。 */
        IRecipeLogicMachine machine();

        /** 同时能跑几条；1 就等于关掉并发。 */
        int slots();

        /**
         * 开一条新线程：<b>找配方 + 资源预留 + 扣料</b>一次完成。
         *
         * <p>返回 null 表示找不到/扣不齐，调度器立刻停止本 tick 的补槽。
         */
        Thread openThread();

        /** 每 tick 的输入（EU、tick 流体之类），不够返回 false，这条原地等。 */
        boolean tickInputs(Thread thread);

        /** 线程收尾：afterWorking / postWorking / 出料。 */
        void finishOutputs(Thread thread);
    }

    /** 存档键（追加键，老存档没有就回落为空线程表）。 */
    public static final String TAG_BATCHES = "gtlmodular_print_threads";

    private static final String TAG_PROGRESS = "progress";
    private static final String TAG_ID = "id";
    private static final String TAG_RECIPE = "recipe";

    /** 线程表（下标 = 位置，不是稳定 ID；稳定编号见 {@link Thread#getID()}）。 */
    private final List<Thread> threads = new ArrayList<>();

    public int size() {
        return threads.size();
    }

    public Thread get(int index) {
        return threads.get(index);
    }

    public boolean isEmpty() {
        return threads.isEmpty();
    }

    /** 停机、拆机、切模式时清干净；已扣的料不退回（与 GTCEu 基类一致）。 */
    public void clear() {
        threads.clear();
    }

    /** 交回原逻辑时的清空（语义同 {@link #clear()}）。 */
    public void resetRecipeLogic() {
        threads.clear();
    }

    /** 每个 server tick：推进 → 摘掉涨满的 → 有空位就开新线程。 */
    public void tick(Host host) {
        if (host == null) {
            return;
        }

        Iterator<Thread> iterator = threads.iterator();
        while (iterator.hasNext()) {
            Thread thread = iterator.next();
            if (host.tickInputs(thread)) {
                thread.progress++;
            }
            if (thread.finished()) {
                iterator.remove();
                host.finishOutputs(thread);
            }
        }

        int slots = Math.max(1, host.slots());
        while (threads.size() < slots) {
            Thread thread = host.openThread();
            if (thread == null) {
                break;
            }
            threads.add(thread);
        }
    }

    /** 存每条线程的配方本体、单独存的配方 id、进度。 */
    public void save(CompoundTag parent) {
        if (parent == null) {
            return;
        }
        ListTag list = new ListTag();
        for (Thread thread : threads) {
            CompoundTag entry = new CompoundTag();
            entry.putInt(TAG_PROGRESS, thread.progress);
            entry.putString(TAG_ID, thread.recipe.getId() == null ? "" : thread.recipe.getId().toString());
            GTRecipeSerializer.CODEC.encodeStart(NbtOps.INSTANCE, thread.recipe)
                    .result()
                    .ifPresent(encoded -> entry.put(TAG_RECIPE, encoded));
            list.add(entry);
        }
        parent.put(TAG_BATCHES, list);
    }

    /** 读档：先清空再恢复；单条坏档只跳过那一条，绝不外抛。 */
    public void load(CompoundTag parent) {
        threads.clear();
        if (parent == null) {
            return;
        }
        ListTag list = parent.getList(TAG_BATCHES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                CompoundTag entry = list.getCompound(i);
                Tag encoded = entry.get(TAG_RECIPE);
                if (encoded == null) {
                    GTLModular.LOGGER.debug("[gtlmodular] 线程引擎：存档第 {} 条没有配方本体键（{}），跳过该条",
                            i, TAG_RECIPE);
                    continue;
                }
                GTRecipe recipe = GTRecipeSerializer.CODEC.parse(NbtOps.INSTANCE, encoded)
                        .result()
                        .orElse(null);
                if (recipe == null) {
                    GTLModular.LOGGER.debug("[gtlmodular] 线程引擎：存档第 {} 条的配方解析失败，跳过该条", i);
                    continue;
                }
                ResourceLocation id = ResourceLocation.tryParse(entry.getString(TAG_ID));
                if (id == null) {
                    GTLModular.LOGGER.debug("[gtlmodular] 线程引擎：存档第 {} 条的配方 id 缺失或不合法，跳过该条", i);
                    continue;
                }
                recipe.setId(id);
                threads.add(Thread.restore(recipe, entry.getInt(TAG_PROGRESS), Thread.nextId()));
            } catch (RuntimeException e) {
                GTLModular.LOGGER.debug("[gtlmodular] 线程引擎：恢复存档第 {} 条时异常（{}），跳过该条",
                        i, e.toString());
            }
        }
        if (!threads.isEmpty()) {
            GTLModular.LOGGER.debug("[gtlmodular] 线程引擎：从存档恢复 {} 条线程", threads.size());
        }
    }
}
