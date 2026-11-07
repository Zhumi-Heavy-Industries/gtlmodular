package com.gtlmodular.client.renderer.stellar;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 屏幕空间特效管理器 —— 对应 GTO {@code com.gtocore.client.renderer.fx.FXManager} 的<b>子集</b>。
 *
 * <p>照搬 GTO 的三件套（{@code FX_LIST} / {@code KEYED_FX} / 每 stage 派发），
 * 只保留恒星炎炀锻炉需要的那部分；太阳风暴（{@code SolarStormFX} /
 * {@code SolarStormReactorFX} / {@code SolarSurfaceFX}）以及它们的
 * {@code beginTick()} / {@code release()} / 天气判定<b>全部去掉</b>。
 *
 * <h2>与 GTO 的差异（逐条）</h2>
 * <ol>
 *   <li>类名 {@code FXManager} ⇒ {@link StellarFxManager}，方法名去掉 {@code FX} 后缀：
 *       {@code dispatchFXs/dispatch}、{@code tickFXs/tick}、{@code upsertFX/upsert}、{@code clearFXs/clear}。</li>
 *   <li>{@code dispatchFXs} 里删掉 {@code AFTER_LEVEL} 分支的
 *       {@code SolarStormFX.render(...)} 与重复的 {@code ScreenSpaceSceneCapture.beginFrame()}。</li>
 *   <li>{@code tickFXs} 里删掉 {@code SolarSurfaceFX.beginTick()}；
 *       {@code release()} 的触发条件从「列表空 且 不是太阳风暴天气」简化成「列表空」——
 *       本模组只有漩涡一种特效，没有需要常驻捕获缓冲的天气效果。</li>
 *   <li>{@code clearFXs} 里删掉太阳风暴两个类的 {@code release()}。</li>
 * </ol>
 *
 * <p><b>线程安全</b>：{@code FX_LIST} 用 {@link CopyOnWriteArrayList}、{@code KEYED_FX} 用
 * {@link ConcurrentHashMap}（与 GTO 一致）—— 因为渲染线程遍历的同时，客户端线程的
 * {@code tick()} 可能在摘元素。
 */
@OnlyIn(Dist.CLIENT)
public final class StellarFxManager {

    /** 所有存活特效（渲染顺序 = 添加顺序）。 */
    private static final List<AbstractStellarFx> FX_LIST = new CopyOnWriteArrayList<>();

    /** 按 key 去重的索引（同一个机器实体每帧只维护一份特效）。 */
    private static final Map<Object, AbstractStellarFx> KEYED_FX = new ConcurrentHashMap<>();

    private StellarFxManager() {
    }

    /**
     * 每个 {@code RenderLevelStageEvent} 派发一次（注册到 <b>game 事件总线</b>）。
     *
     * <p>{@code AFTER_CUTOUT_BLOCKS} 是本特效唯一的舞台：
     * 先开一个新帧的屏幕捕获窗口 + 清空批队列，再让所有特效把自身顶点塞进批，
     * 最后统一 flush（一次 draw call）。
     */
    public static void dispatch(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            // ★ 在世界渲染阶段抓相机变换：AFTER_LEVEL 时 camera/PoseStack 可能已被重置。
            StellarForgeVortexFX.setFrameCamera(
                    event.getPoseStack() == null ? null : event.getPoseStack().last().pose(),
                    event.getCamera() == null ? null : event.getCamera().getPosition());
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            ScreenSpaceSceneCapture.beginFrame();
            StellarForgeVortexFX.beginBatchFrame();
            // ★ 2026-11-02：AFTER_LEVEL 下 RenderSystem 里的投影可能已被重置/换成 GUI 的，
            //   这里用事件给的 3D 投影矩阵，flush 时直接用它（否则特效会「钉在屏幕上跟着视角走」）。
            StellarForgeVortexFX.setFrameProjection(event.getProjectionMatrix());
        }
        for (AbstractStellarFx fx : FX_LIST) {
            if (fx.isDiscarded()) {
                continue;
            }
            fx.render(event.getStage(), event.getLevelRenderer(), event.getPoseStack(),
                    event.getProjectionMatrix(), event.getPartialTick(), event.getCamera(), event.getFrustum());
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            StellarForgeVortexFX.flushBatch(event.getLevelRenderer());
        }
    }

    /** 每客户端 tick 一次：推进 age、摘掉过期特效、把索引一起清干净。 */
    public static void tick() {
        List<AbstractStellarFx> discarded = new ArrayList<>();
        FX_LIST.removeIf(fx -> {
            fx.tick();
            if (fx.shouldDiscard()) {
                discarded.add(fx);
                return true;
            }
            return false;
        });
        // 注意顺序：先移除再 onDiscard —— onDiscard 会把 isDiscarded() 置 true，
        // 而下面清理 KEYED_FX 时正是靠这个标记兜住「同 key 已被换掉」的旧实例。
        discarded.forEach(AbstractStellarFx::onDiscard);
        if (!discarded.isEmpty()) {
            KEYED_FX.entrySet().removeIf(entry ->
                    discarded.contains(entry.getValue()) || entry.getValue().isDiscarded());
        }
        if (FX_LIST.isEmpty()) {
            ScreenSpaceSceneCapture.release();
        }
    }

    /** 直接入队（目前没有调用方，保留成与 GTO 同形的 API，方便以后加特效）。 */
    public static void add(AbstractStellarFx fx) {
        FX_LIST.add(fx);
    }

    /**
     * 「有则刷新、无则新建」—— 机器 TESR 每帧都会调这个，所以必须便宜且幂等。
     *
     * @param key     去重键（漩涡用的是 {@code StellarForgeVortexFX.Key}，已按维度 + 机器坐标去重）
     * @param factory 新建工厂
     * @param refresh 对现存/新建实例都执行的刷新动作
     * @return 实际生效的实例
     */
    @SuppressWarnings("unchecked")
    public static <T extends AbstractStellarFx> T upsert(Object key, Supplier<T> factory, Consumer<T> refresh) {
        AbstractStellarFx existing = KEYED_FX.get(key);
        T fx;
        if (existing == null || existing.isDiscarded() || !FX_LIST.contains(existing)) {
            fx = factory.get();
            KEYED_FX.put(key, fx);
            FX_LIST.add(fx);
        } else {
            fx = (T) existing;
        }
        refresh.accept(fx);
        return fx;
    }

    /** 全部清空（卸载世界 / 退出存档时调用），同时释放屏幕捕获缓冲。 */
    public static void clear() {
        List<AbstractStellarFx> discarded = new ArrayList<>(FX_LIST);
        FX_LIST.clear();
        KEYED_FX.clear();
        discarded.forEach(AbstractStellarFx::onDiscard);
        ScreenSpaceSceneCapture.release();
    }

    /** 当前存活特效数量（调试用；GTO 直接暴露列表，这里给个只读计数） */
    public static int size() {
        return FX_LIST.size();
    }

    /** 按 key 去重的索引大小（调试用） */
    public static int keyedSize() {
        return KEYED_FX.size();
    }
}
