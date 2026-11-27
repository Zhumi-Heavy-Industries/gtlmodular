package com.gtlmodular.common.machine.engine;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtlmodular.GTLModular;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * ★ 0.3.528：<b>共享配方预取队列</b> —— 独立线程开时，N 条线程共用一个候选源。
 *
 * <h2>线程安全边界（最重要）</h2>
 * 后台是一条 Java 21 <b>虚拟线程</b>（{@code Thread.ofVirtual()}），它<b>只</b>读全局静态配方表
 * （{@link BatchFinder#recipesFor(GTRecipeType)} 的 ConcurrentHashMap 缓存，配方表注册后只读），
 * 按轮换游标展开后放进 {@link ConcurrentLinkedQueue}（<b>不去重</b>：同配方开时本来就需要多条候选；
 * 去重由主线程 {@code BatchFinder} 按 {@code repeated} 开关做）。
 * <p><b>绝不</b>在虚拟线程里碰：ME 样板部件、物品/流体 handler、能量容器、
 * {@code matchRecipeInput} / {@code handleTickRecipeIO} —— 那些是世界状态，只能在服务器主线程。
 * 主线程从队列 {@code poll()} 拿到候选后，仍走 {@link BatchFinder} 原来的匹配 / 扣料 / commit 全链。
 *
 * <h2>为什么用虚拟线程</h2>
 * 展开一张大配方表（几百到几千条）是一次性的 CPU 轻活，但它发生在「有空位要开线程」的
 * tick 里，会插在服务器主线程的关键路径上。放到虚拟线程里预取，主线程只在队列里
 * {@code poll()}（O(1) 无锁），把这段展开开销挪出 tick。虚拟线程由 JVM 管理，
 * 一台机器一条，机器拆掉 / 失形时 {@link #stop()} 优雅退出，不留操作系统线程。
 *
 * <h2>进度</h2>
 * {@link #queued()} 是当前排队条数，{@link #prefetched()} 是累计入队数、
 * {@link #consumed()} 是累计被主线程取走数，供 Jade 显示「预取 N · 排队 M」。
 */
final class RecipePrefetchQueue {

    /** 队列最大积压：超了就不再展开，防止配方表几千条把内存吃满。 */
    private static final int MAX_QUEUE = 256;

    /** 虚拟线程两轮预取之间的 park 时间（纳秒）；只为让出 CPU，不需要精确计时。 */
    private static final long IDLE_PARK_NANOS = 50_000L;

    private final ConcurrentLinkedQueue<GTRecipe> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicLong prefetched = new AtomicLong();
    private final AtomicLong consumed = new AtomicLong();

    /** 主线程写入「本轮该预取哪几张表」的快照；虚拟线程只读引用，不读世界状态。 */
    private volatile List<GTRecipeType> scanTypes = List.of();

    /** 每轮展开后重排用的轮换游标（虚拟线程独占，无需同步）。 */
    private int rotate = 0;

    /** 全写 java.lang.Thread：本包有同名的 {@link ConcurrentRecipeEngine.Thread}，不能裸写 Thread。 */
    private volatile java.lang.Thread worker;

    /** 主线程每 tick 调一次：按需启动 / 停止虚拟线程。 */
    void tick(boolean needPrefetch) {
        if (needPrefetch && enabled.get() && running.compareAndSet(false, true)) {
            startWorker();
        } else if (!needPrefetch && running.getAndSet(false)) {
            stopWorker();
        }
    }

    /**
     * ★ 为什么用反射：本工程 {@code options.release = 17}，javac 只暴露 Java 17 API，
     * 直接写 {@code Thread.ofVirtual()} 编译不过。但整合包实跑 Java 21.0.12（已查证），
     * 运行时反射调用完全可用。反射只在启动线程时做一次，不在热路径，开销可忽略。
     * 任何失败（Java 17 环境 / API 缺失）都退回「不预取」，由 BatchFinder 原扫描兜底，功能不丢。
     */
    private void startWorker() {
        try {
            // 等价于 Thread.ofVirtual().name("gtlmodular-recipe-prefetch").unstarted(runnable)
            Class<?> threadClass = java.lang.Thread.class;
            java.lang.reflect.Method ofVirtual = threadClass.getMethod("ofVirtual");
            Object builder = ofVirtual.invoke(null);
            java.lang.reflect.Method name = builder.getClass().getMethod("name", String.class);
            name.invoke(builder, "gtlmodular-recipe-prefetch");
            java.lang.reflect.Method unstarted = builder.getClass().getMethod("unstarted", Runnable.class);
            java.lang.Thread t = (java.lang.Thread) unstarted.invoke(builder, (Runnable) this::runLoop);
            this.worker = t;
            t.start();
        } catch (RuntimeException | ReflectiveOperationException | LinkageError e) {
            running.set(false);
            this.worker = null;
            GTLModular.LOGGER.debug("[gtlmodular] 虚拟线程预取不可用，回退主线程扫描：{}", e.toString());
        }
    }

    private void stopWorker() {
        java.lang.Thread t = this.worker;
        this.worker = null;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    private void runLoop() {
        try {
            while (running.get()) {
                List<GTRecipeType> types = this.scanTypes;
                boolean didWork = false;
                if (!types.isEmpty() && queue.size() < MAX_QUEUE) {
                    for (int i = 0; i < types.size(); i++) {
                        GTRecipeType type = types.get(i);
                        if (type == null) {
                            continue;
                        }
                        List<GTRecipe> recipes = BatchFinder.recipesFor(type);
                        if (recipes == null || recipes.isEmpty()) {
                            continue;
                        }
                        int n = recipes.size();
                        // 从轮换游标开始扫，避免每轮都从头把同一批配方塞满队列。
                        int start = rotate % n;
                        for (int k = 0; k < n && running.get() && queue.size() < MAX_QUEUE; k++) {
                            GTRecipe recipe = recipes.get((start + k) % n);
                            if (recipe == null || recipe.isFuel()) {
                                continue;
                            }
                            if (queue.offer(recipe)) {
                                prefetched.incrementAndGet();
                                didWork = true;
                            }
                        }
                        rotate = (start + n) % n;
                    }
                }
                if (!didWork) {
                    LockSupport.parkNanos(IDLE_PARK_NANOS);
                }
            }
        } catch (Throwable t) {
            // 后台线程任何异常都不能冒出去（uncaught 会刷日志）；退回不预取。
            GTLModular.LOGGER.debug("[gtlmodular] 配方预取线程结束：{}", t.toString());
        } finally {
            running.set(false);
        }
    }

    /**
     * 主线程设置预取范围（只写引用，配方表对象是全局只读的）。
     *
     * <p>★ 0.3.531：每 tick 都会被调用，先逐项比对；类型表没变就<b>不分配新 List、不发布新引用</b>，
     * 让虚拟线程读到的 {@code scanTypes} 引用保持稳定。
     */
    void setScanTypes(GTRecipeType[] types) {
        int n = types == null ? 0 : types.length;
        List<GTRecipeType> cur = this.scanTypes;
        if (n == 0) {
            if (!cur.isEmpty()) {
                this.scanTypes = List.of();
            }
            return;
        }
        if (cur.size() == n) {
            boolean same = true;
            for (int i = 0; i < n; i++) {
                if (cur.get(i) != types[i]) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return;
            }
        }
        this.scanTypes = List.of(types);
    }

    /** 主线程取一条预取候选；空了返回 null，由调用方回退原扫描。 */
    GTRecipe poll() {
        GTRecipe recipe = queue.poll();
        if (recipe != null) {
            consumed.incrementAndGet();
        }
        return recipe;
    }

    int queued() {
        return queue.size();
    }

    long prefetched() {
        return prefetched.get();
    }

    long consumed() {
        return consumed.get();
    }

    /** 切模式 / 拆机 / 结构失形：清空队列并停线程。 */
    void reset() {
        enabled.set(false);
        running.set(false);
        java.lang.Thread t = this.worker;
        this.worker = null;
        if (t != null) {
            LockSupport.unpark(t);
        }
        queue.clear();
        prefetched.set(0);
        consumed.set(0);
        scanTypes = List.of();
    }

    /** 重新启用（切回独立线程开时）。 */
    void enable() {
        enabled.set(true);
    }
}
