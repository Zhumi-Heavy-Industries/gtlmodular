package com.gtlmodular.common.machine.engine;

import com.gregtechceu.gtceu.api.capability.IParallelHatch;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.machine.thread.IThreadHatchPart;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ★ 2026-11-26：<b>跨配方状态中枢</b>（GTO {@code CrossRecipeTrait} 的等价物）。
 *
 * <p>GTO 把「线程数 / 并行额度 / 两个开关 / 已用配方 / 部件引用」集中在一个 trait 里，
 * 由 {@code onPartScan} 在结构成型时收集、由 {@code onStructureInvalid} 作废；线程逻辑与
 * 找配方算法都只问这个 trait，不再各自遍历部件表。本类照这个形状落地：
 * <ul>
 *   <li>部件引用缓存（线程仓 / 并行仓）在每 tick 的第一次读取时统一收集一次；</li>
 *   <li>{@link #getThread()} / {@link #getParallel()} / {@link #isRepeatedRecipes()} /
 *       {@link #isIndependentThread()} 是唯一的额度读数口；</li>
 *   <li>{@link #lookupRecipe(RecipeCharge)} 是唯一的开线程入口，把 {@link BatchFinder} 藏在后面；</li>
 *   <li>{@link #lastRecipes} 记录最近开过的配方 id，供去重与诊断（GTO 的 {@code lastRecipes}）；</li>
 *   <li>{@link #getRealRecipe}（0.3.514）把改写后的配方还原成配方表原始 id（GTO 同款钩子）；</li>
 *   <li>{@link #maxParallel} / {@link #availableParallel} / {@link #lastParallel}（0.3.514）
 *       是 GTO 三个并行读数，每轮扫描刷新、供 Jade/诊断读取。</li>
 * </ul>
 *
 * <p>结构失形由 {@link #onStructureInvalid()} 清缓存；机器类没有统一的 onPartScan 钩子，
 * 所以收集时机由 {@link #refresh()} 的 tick 戳控制，语义上等价于「结构视图在一 tick 内不变」。
 */
public final class CrossRecipeTrait {

    private final IRecipeLogicMachine machine;
    private final IThreadedRecipeMachine threaded;
    private final ConcurrentRecipeEngine engine;

    /** GTO 的 {@code threadHatchPartMachine} / 并行仓引用，集中在这里缓存。 */
    private final List<IThreadHatchPart> threadHatches = new ArrayList<>();

    private final List<IParallelHatch> parallelHatches = new ArrayList<>();

    /** GTO 的 {@code lastRecipes}：本工程用配方 id 代替定义对象，避免依赖 GTM 内部结构。 */
    public final ObjectSet<ResourceLocation> lastRecipes = new ObjectOpenHashSet<>();

    /**
     * ★ GTO {@code getRealRecipe} 的还原映射（0.3.514）：改写后的配方 id → 配方表原始 id。
     *
     * <p>{@link BatchFinder} 找到的 {@code selected} 经过修改器/准确并行改写，
     * 它的 id 可能已被换掉；这里记下「selected → raw」的对应关系，让
     * {@link #lastRecipes} 与去重/显示都按<b>原始配方</b>的身份记账（GTO 同款口径）。
     */
    private final Map<ResourceLocation, ResourceLocation> realRecipeIds = new HashMap<>();

    /** GTO 的三个并行读数（0.3.514）：本轮上限 / 当前可用 / 上次实际并行。 */
    public long maxParallel = 0L;
    public long availableParallel = 0L;
    public long lastParallel = 0L;

    private BatchFinder finder;

    private long checkedTick = Long.MIN_VALUE;
    private int cachedThreads = 1;
    private int cachedParallel = 1;
    private boolean cachedRepeated = true;
    /** ★ 0.3.524：独立线程默认关（对齐 GTO 未装无线能源仓的初始态）；refresh() 会覆盖为部件现值。 */
    private boolean cachedIndependent = false;

    public CrossRecipeTrait(IRecipeLogicMachine machine, IThreadedRecipeMachine threaded,
                            ConcurrentRecipeEngine engine) {
        this.machine = machine;
        this.threaded = threaded;
        this.engine = engine;
    }

    /** {@link BatchFinder} 构造完成后回填，避免构造期循环依赖。 */
    public void attachFinder(BatchFinder finder) {
        this.finder = finder;
    }

    /**
     * GTO {@code onPartScan} 的等价物：结构扫描到一个部件时收进缓存。
     * 本工程由 {@link #refresh()} 统一调用；外部也可显式补登记。
     */
    public void onPartScan(IMultiPart part) {
        if (part instanceof IThreadHatchPart hatch) {
            threadHatches.add(hatch);
        }
        if (part instanceof IParallelHatch parallel) {
            parallelHatches.add(parallel);
        }
    }

    /** 结构失形 / 复位：部件引用与已用配方全部作废。 */
    public void onStructureInvalid() {
        threadHatches.clear();
        parallelHatches.clear();
        lastRecipes.clear();
        realRecipeIds.clear();
        maxParallel = 0L;
        availableParallel = 0L;
        lastParallel = 0L;
        checkedTick = Long.MIN_VALUE;
        cachedThreads = 1;
        cachedParallel = 1;
        cachedRepeated = true;
        cachedIndependent = false;
    }

    private void refresh() {
        long now;
        try {
            var self = machine.self();
            now = self == null || self.getLevel() == null ? Long.MIN_VALUE : self.getOffsetTimer();
        } catch (Throwable t) {
            now = Long.MIN_VALUE;
        }
        if (now != Long.MIN_VALUE && now == checkedTick) {
            return;
        }
        checkedTick = now;
        threadHatches.clear();
        parallelHatches.clear();
        if (machine.self() instanceof IMultiController controller && controller.isFormed()) {
            List<IMultiPart> parts = controller.getParts();
            if (parts != null) {
                for (IMultiPart part : parts) {
                    onPartScan(part);
                }
            }
        }
        long threads = 0L;
        for (IThreadHatchPart hatch : threadHatches) {
            threads += Math.max(0, hatch.getThreads());
            if (threads >= Integer.MAX_VALUE) {
                break;
            }
        }
        cachedThreads = threads > 0 ? (int) Math.min(Integer.MAX_VALUE, threads) : Math.max(1, threaded.baseThreads());
        long parallel = Math.max(1, threaded.parallelMultiplier());
        if (threaded.usesParallelHatch()) {
            int maxHatch = 1;
            for (IParallelHatch hatch : parallelHatches) {
                maxHatch = Math.max(maxHatch, hatch.getCurrentParallel());
            }
            parallel *= maxHatch;
        }
        cachedParallel = (int) Math.min(Integer.MAX_VALUE, parallel);
        cachedRepeated = threaded.threadHatchRepeatedRecipes();
        cachedIndependent = threaded.threadHatchIndependent();
    }

    /** 当前线程数（线程仓设定之和；没装仓则回落机器固定值）。 */
    public int getThread() {
        refresh();
        return Math.max(1, cachedThreads);
    }

    /** 每条线程可吃的并行额度（线程倍率 × 并行仓）。 */
    public int getParallel() {
        refresh();
        return Math.max(1, cachedParallel);
    }

    public boolean isRepeatedRecipes() {
        refresh();
        return cachedRepeated;
    }

    public boolean isIndependentThread() {
        refresh();
        return cachedIndependent;
    }

    /**
     * ★ GTO 口径的<b>实际并发槽数</b>。
     *
     * <p>独立线程开（GTO：装了无线能源仓）⇒ 线程数 N，每条各跑一条配方；
     * 独立线程关（GTO：没有无线能源仓）⇒ <b>只有 1 条线程</b>——GTO Wiki 原文
     * 「不再有多线程，最大并行数乘以线程数」。两者产能相同，差别只在并发条数。
     */
    public int slotCount() {
        return isIndependentThread() ? getThread() : 1;
    }

    /**
     * ★ GTO 口径的<b>单条线程并行额度</b>。
     *
     * <p>独立线程开 ⇒ 每条各自 {@link #getParallel()}；关 ⇒ 唯一那条吃下
     * {@code getParallel() × 线程数}，把 N 条的额度并到 1 条上，产能不打折。
     * long 乘再钳到 int，避免倍率乘爆。
     */
    public int parallelBudget() {
        int per = getParallel();
        if (isIndependentThread()) {
            return per;
        }
        long merged = (long) per * getThread();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, merged));
    }

    /** 当前显示用的配方（第 0 条线程）。 */
    @Nullable
    public GTRecipe getRecipe() {
        return engine.isEmpty() ? null : engine.get(0).recipe;
    }

    /**
     * ★ 0.3.515：GTO {@code ParallelProvider} 口径的「当前配方并行」。
     *
     * <p>GTO 工作时取 {@code getLastRecipe().parallels}（第 0 条线程那条配方套过修改器后的并行），
     * 空闲时取并行仓设定（{@code getOriginParallel()}）。这里第 0 条线程在跑就返回它的
     * {@code recipe.parallels}；没有则返回 0，由调用方退回并行上限。
     */
    public long currentRecipeParallel() {
        GTRecipe recipe = getRecipe();
        return recipe == null ? 0L : Math.max(0L, recipe.parallels);
    }

    /**
     * ★ 0.3.515：GTO {@code ParallelProvider.getOriginParallel()} —— 并行仓的<b>原始设定</b>
     * （不含线程倍率）。用于算「批处理/超频补偿 = current / origin」。
     */
    public long originParallel() {
        refresh();
        long origin = 1L;
        if (threaded.usesParallelHatch()) {
            for (IParallelHatch hatch : parallelHatches) {
                long cur = hatch.getCurrentParallel();
                if (cur > origin) {
                    origin = cur;
                }
            }
        }
        return Math.max(1L, origin);
    }

    /**
     * ★ GTO {@code getRealRecipe(RecipeHandlerUnit, GTRecipe)} 的等价物（0.3.514）。
     *
     * <p>{@code raw} 是配方表里的原始配方，{@code selected} 是经修改器/准确并行改写后的副本
     * （它的 id 可能已被换掉）。本方法把两者登记进还原表，并返回<b>原始配方 id</b>——
     * 让去重与诊断都按配方定义记账，而不是按某次改写的临时 id。
     *
     * <p>GTO 用这个钩子把 {@code RecipeHandlerUnit} 里的处理态配方还原成
     * {@code GTRecipeDefinition}；本工程不依赖 GTM 内部结构，用 id 映射达到同一目的。
     */
    public ResourceLocation getRealRecipe(@Nullable GTRecipe raw, @Nullable GTRecipe selected) {
        ResourceLocation rawId = raw == null ? null : raw.getId();
        ResourceLocation selectedId = selected == null ? null : selected.getId();
        if (rawId != null && selectedId != null && !rawId.equals(selectedId)) {
            if (realRecipeIds.size() >= 256) {
                realRecipeIds.clear();   // 上限保护：机器长期换配方不该无限攒映射
            }
            realRecipeIds.put(selectedId, rawId);
        }
        return rawId != null ? rawId : selectedId;
    }

    /** 把（可能被改写的）配方 id 还原成配方表原始 id；没登记过就原样返回。 */
    public ResourceLocation realIdOf(@Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        ResourceLocation real = realRecipeIds.get(id);
        return real != null ? real : id;
    }

    /**
     * ★ 0.3.521：复用的去重集合。{@link #runningRecipeIds()} 每 tick 每轮扫描都会调用，
     * 旧版每次 new 一个 {@link ObjectOpenHashSet}；调用方只在本次 lookup 内同步读取、
     * 不会跨 tick 持有，因此复用一个实例即可（每次先 clear），省掉热路径的分配与 GC。
     */
    private final ObjectSet<ResourceLocation> runningIdsScratch = new ObjectOpenHashSet<>();

    /** 在跑线程占用的配方 id（GTO {@code duplicateCheck} 口径：按原始配方身份）。 */
    public ObjectSet<ResourceLocation> runningRecipeIds() {
        ObjectSet<ResourceLocation> ids = runningIdsScratch;
        ids.clear();
        for (int i = 0; i < engine.size(); i++) {
            GTRecipe recipe = engine.get(i).recipe;
            if (recipe != null && recipe.getId() != null) {
                ids.add(realIdOf(recipe.getId()));
            }
        }
        return ids;
    }

    /**
     * ★ 每轮扫描前刷新三个并行读数（0.3.514）：
     * {@code maxParallel} = 整机本轮并行上限 = 单条额度 × 槽数（两种独立模式下这个值相同）；
     * {@code availableParallel} = 上限 − 在跑线程已占；{@code lastParallel} 在开线程时单独记。
     */
    public void refreshParallelStats() {
        long cap = (long) parallelBudget() * slotCount();
        maxParallel = Math.min(Integer.MAX_VALUE, Math.max(1L, cap));
        long used = 0L;
        for (int i = 0; i < engine.size(); i++) {
            used += engine.get(i).use;
        }
        availableParallel = Math.max(0L, maxParallel - Math.max(0L, used));
    }

    /** 唯一的开线程入口（GTO {@code lookupRecipe(@Nullable RecipeCharge)}）。 */
    @Nullable
    public GTRecipe lookupRecipe(@Nullable RecipeCharge charge) {
        return finder == null ? null : finder.lookupRecipe(charge);
    }

    /**
     * 记住这条线程开的配方（去重/诊断用，最多保留 64 条）；按原始配方身份记账。
     * 同时刷新 {@code lastParallel} / {@code availableParallel} 两个读数。
     */
    public void noteThreadOpened(@Nullable GTRecipe raw, @Nullable GTRecipe selected) {
        ResourceLocation id = getRealRecipe(raw, selected);
        if (id != null) {
            if (lastRecipes.size() >= 64) {
                lastRecipes.clear();
            }
            lastRecipes.add(id);
        }
        long use = selected == null ? 1L : Math.max(1L, selected.parallels);
        lastParallel = use;
        availableParallel = Math.max(0L, availableParallel - use);
    }
}
