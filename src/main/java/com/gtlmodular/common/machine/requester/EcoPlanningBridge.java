package com.gtlmodular.common.machine.requester;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import com.gtlmodular.GTLModular;
import net.minecraft.world.level.Level;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.Future;

/**
 * <b>ECO（neoecoae）计算子系统的可选桥接</b>。
 *
 * <h2>为什么需要它</h2>
 * <p>装了 ECO 之后，网络里的合成 CPU 可能是「计算集群」。ECO 用 mixin 把集群接进了
 * AE2 标准的 {@code CraftingService.getCpus()} / {@code submitJob}，所以<b>下单</b>这条路
 * 本来就是通的。但 <b>算方案</b>这条路不是：ECO 的快速规划器（能处理循环配方、
 * 替代配方、大数量）只在两处被调用 ——
 * <pre>
 *   neoecoae.mixins.ae2.menu.CraftConfirmMenuMixin      // ECO 自己的确认界面
 *   neoecoae.mixins.compat.merequester.MERequesterPlanningMixin  // merequester
 * </pre>
 * 我们的补货牌走的是 {@code ICraftingService.beginCraftingCalculation}（AE2 原生），
 * 于是遇到 GTL 常见的循环配方就会算不出方案。这里按 merequester 那份 mixin 的
 * 判断条件，用反射调 ECO 的公开 API，把规划也交给 ECO。
 *
 * <h2>为什么用反射，而不是直接依赖</h2>
 * <p>本模组<b>不依赖</b> neoecoae（{@code libs/} 里没有它）。写成直接 import 会在
 * 没装 ECO 的整合包里 {@code NoClassDefFoundError}。所以这里全程按类名反射，
 * 类不在就返回 null，调用方自动退回 AE2 原生规划。
 *
 * <h2>判定条件（与 ECO 自己的 merequester 兼容 mixin 逐条对齐）</h2>
 * <ol>
 *   <li>actionSource 非空；</li>
 *   <li>{@code ECOCraftingNetworkSettings.of(grid)} 非空（说明这个网络有 ECO 设置）；</li>
 *   <li>{@code neoecoae$isFastPlannerEnabled()} 为真；</li>
 *   <li>{@code neoecoae$hasComputationHost(actionSource)} 为真（网络里真有计算主机）。</li>
 * </ol>
 */
final class EcoPlanningBridge {

    /** ECO 是否存在于运行时（只在首次检查时打一次日志）。 */
    private static Boolean available;

    private static MethodHandle settingsOf;         // ECOCraftingNetworkSettings.of(IGrid)
    private static MethodHandle isFastPlanner;      // neoecoae$isFastPlannerEnabled()
    private static MethodHandle hasHost;            // neoecoae$hasComputationHost(IActionSource)
    private static MethodHandle optionsFrom;        // ECOPlannerOptions.from(ECOCraftingNetworkSettings)
    private static MethodHandle planningBegin;      // ECOPlanningService.begin(Level, IGrid, IActionSource, AEKey, long, CalculationStrategy, ECOPlannerOptions)

    private EcoPlanningBridge() {
    }

    /**
     * 尝试用 ECO 的规划器算这一单。
     *
     * @return ECO 的 future；<b>null</b> 表示“不要用 ECO”，调用方应退回 AE2 原生规划。
     */
    static Future<ICraftingPlan> begin(Level level, IGrid grid, IActionSource source,
                                       AEKey key, long amount, CalculationStrategy strategy) {
        try {
            if (!resolve()) return null;
            if (level == null || grid == null || source == null) return null;

            Object settings = settingsOf.invoke(grid);
            if (settings == null) return null;
            if (!(boolean) isFastPlanner.invoke(settings)) return null;
            if (!(boolean) hasHost.invoke(settings, source)) return null;

            Object options = optionsFrom.invoke(settings);
            Object request = planningBegin.invoke(level, grid, source, key, amount, strategy, options);
            if (request instanceof Future<?> future) {
                @SuppressWarnings("unchecked")
                Future<ICraftingPlan> typed = (Future<ICraftingPlan>) future;
                return typed;
            }
            return null;
        } catch (Throwable t) {
            // 反射失败/版本不匹配：安静退回 AE2 原生规划，不要影响下单。
            GTLModular.LOGGER.debug("[ME库存补充仓] ECO 规划器不可用，退回 AE2 原生规划", t);
            return null;
        }
    }

    /** 反射解析 ECO 的入口；类不存在则标记为不可用。 */
    private static boolean resolve() {
        if (available != null) return available;
        try {
            ClassLoader cl = EcoPlanningBridge.class.getClassLoader();

            Class<?> settingsClass = Class.forName(
                    "cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings", false, cl);
            Class<?> optionsClass = Class.forName(
                    "cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions", false, cl);
            Class<?> plannerClass = Class.forName(
                    "cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningService", false, cl);
            // begin(...) 声明的返回类型是 ECOPlanningService$Request，不是 Future；
            // MethodHandle 查找对**声明类型**敏感，必须用真实类型，否则 NoSuchMethodException。
            Class<?> requestClass = Class.forName(
                    "cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningService$Request", false, cl);

            MethodHandles.Lookup lookup = MethodHandles.publicLookup();

            settingsOf = lookup.findStatic(settingsClass, "of",
                    MethodType.methodType(settingsClass, IGrid.class));
            isFastPlanner = lookup.findVirtual(settingsClass, "neoecoae$isFastPlannerEnabled",
                    MethodType.methodType(boolean.class));
            hasHost = lookup.findVirtual(settingsClass, "neoecoae$hasComputationHost",
                    MethodType.methodType(boolean.class, IActionSource.class));
            optionsFrom = lookup.findStatic(optionsClass, "from",
                    MethodType.methodType(optionsClass, settingsClass));
            planningBegin = lookup.findStatic(plannerClass, "begin",
                    MethodType.methodType(requestClass,
                            Level.class, IGrid.class, IActionSource.class,
                            AEKey.class, long.class, CalculationStrategy.class, optionsClass))
                    // 统一成 Future 形状，调用方不用关心 ECO 的 Request 类型。
                    .asType(MethodType.methodType(Future.class,
                            Level.class, IGrid.class, IActionSource.class,
                            AEKey.class, long.class, CalculationStrategy.class, optionsClass));

            available = true;
            GTLModular.LOGGER.info("[ME库存补充仓] 已接入 ECO（neoecoae）计算子系统：规划与下单都走计算集群");
        } catch (Throwable t) {
            available = false;
            GTLModular.LOGGER.debug("[ME库存补充仓] 未检测到 ECO（neoecoae），使用 AE2 原生合成规划");
        }
        return available;
    }
}
