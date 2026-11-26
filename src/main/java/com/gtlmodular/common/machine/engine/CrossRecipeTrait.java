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
import java.util.List;

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
 *   <li>{@link #lastRecipes} 记录最近开过的配方 id，供去重与诊断（GTO 的 {@code lastRecipes}）。</li>
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

    private BatchFinder finder;

    private long checkedTick = Long.MIN_VALUE;
    private int cachedThreads = 1;
    private int cachedParallel = 1;
    private boolean cachedRepeated = true;
    private boolean cachedIndependent = true;

    /** 简单线程循环计时（GTO 的 {@code TickTimeMonitor} 口径：只计数，不做纳秒采样）。 */
    private long monitoredTicks;

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
        checkedTick = Long.MIN_VALUE;
        cachedThreads = 1;
        cachedParallel = 1;
        cachedRepeated = true;
        cachedIndependent = true;
    }

    /** 每 tick 的循环计数（诊断用）。 */
    public void onEngineTick() {
        monitoredTicks++;
    }

    public long monitoredTicks() {
        return monitoredTicks;
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

    /** 已在跑的线程数（GTO 的 {@code useThread}）。 */
    public int useThread() {
        return engine.size();
    }

    /** 还能开几条（GTO 的 {@code availableThread}）。 */
    public int availableThread() {
        return Math.max(0, getThread() - useThread());
    }

    /** 当前显示用的配方（第 0 条线程）。 */
    @Nullable
    public GTRecipe getRecipe() {
        return engine.isEmpty() ? null : engine.get(0).recipe;
    }

    /** 在跑线程占用的配方 id（GTO {@code duplicateCheck} 用的同一份口径）。 */
    public ObjectSet<ResourceLocation> runningRecipeIds() {
        ObjectSet<ResourceLocation> ids = new ObjectOpenHashSet<>();
        for (int i = 0; i < engine.size(); i++) {
            GTRecipe recipe = engine.get(i).recipe;
            if (recipe != null && recipe.getId() != null) {
                ids.add(recipe.getId());
            }
        }
        return ids;
    }

    /** 唯一的开线程入口（GTO {@code lookupRecipe(@Nullable RecipeCharge)}）。 */
    @Nullable
    public GTRecipe lookupRecipe(@Nullable RecipeCharge charge) {
        return finder == null ? null : finder.lookupRecipe(charge);
    }

    /** 记住这条线程开的配方（去重/诊断用，最多保留 64 条）。 */
    public void noteThreadOpened(GTRecipe recipe) {
        ResourceLocation id = recipe == null ? null : recipe.getId();
        if (id != null) {
            if (lastRecipes.size() >= 64) {
                lastRecipes.clear();
            }
            lastRecipes.add(id);
        }
    }
}
