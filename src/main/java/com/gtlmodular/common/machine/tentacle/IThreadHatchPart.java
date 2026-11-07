package com.gtlmodular.common.machine.tentacle;

/**
 * ★ 2026-11-01<b>GTO「线程仓」接口</b> —— 结构里的线程仓对控制器开的口子。
 *
 * <p>对应 GTO {@code gtocore:thread_hatch_<tier>}（{@code GTOMachines.THREAD_HATCH}，
 * 部件类 {@code com.gtolib.api.machine.impl.part.ThreadPartMachine}）的语义：
 * 一台线程仓给机器提供<b>可配置</b>条数的跨配方线程，<b>档位越高上限越大</b>。
 *
 * <h2>线程数口径（GTO 原文）</h2>
 * <p>上限取证：{@code _gto_src/src/main/java/com/gtocore/common/data/GTOMachines.java:281-288}：
 * <pre>
 * THREAD_HATCH = registerTieredMachines("thread_hatch", ...)
 *     .tooltips(Component.translatable("gtocore.machine.thread_hatch.tooltip.0", 1 &lt;&lt; (tier - LuV)))
 *     ... UV, UHV, UEV, UIV, UXV, OpV, MAX
 * </pre>
 * 即 <b>上限 = {@code 1 << (tier - LuV)}</b>：UV=4 / UHV=8 / UEV=16 / UIV=32 / UXV=64 /
 * OpV=128 / MAX=256；实际值由玩家在界面里从 1 调到该上限（GTO 的
 * {@code AmountConfigurationPartMachine} 就是干这个的，0.3.282 起本模组补齐）。
 *
 * <h2>控制器怎么用</h2>
 * <p>控制器（本例是 {@link NyarlathotepsTentacleMachine}）在成形后遍历
 * {@code getParts()}，对每个实现本接口的部件累加 {@link #getThreads()}（<b>当前设定值</b>，
 * 不是满档），<b>没有线程仓时按 1 条</b>。结构里线程仓的数量由各结构的谓词自己限制
 * （GTO 的奈亚拉托提普之触是 {@code setMaxGlobalLimited(1)}，见
 * {@code NyarlathotepsTentacleStructures}）。
 *
 * <p><b>为什么不用 gtladditions 的 {@code IThreadModifierPart}</b>：那是「Ω-天球分歧引擎」
 * 的接口，线程数 = 星规矩阵数量 × 64 × 线程倍率，与本机 GTO 口径（固定档位 + 可配置）不是一回事；
 * 0.3.279 起结构已不再接受那台仓，0.3.281 起连机器类也不再依赖它。
 */
public interface IThreadHatchPart {

    /**
     * 本线程仓<b>当前设定</b>能提供的跨配方线程数（1 ~ {@link #getMaxThreads()}）。
     *
     * @return 线程数，恒 &ge; 1
     */
    int getThreads();

    /**
     * 本线程仓的档位上限（GTO 口径 {@code 1 << (tier - LuV)}）。
     *
     * <p>默认回落成 {@link #getThreads()}：只读实现（历史/测试替身）不必额外实现它。
     */
    default int getMaxThreads() {
        return getThreads();
    }

    /**
     * 「重复配方」开关（GTO {@code repeatedRecipes}，默认 <b>true</b>）。
     *
     * <p>开 = 多条线程<b>可以</b>同时跑同一条配方；关 = 同一配方只允许占一条线程。
     * 控制器取「所有制线程仓都为真」才允许重复（{@link NyarlathotepsTentacleMachine}）。
     */
    default boolean isRepeatedRecipes() {
        return true;
    }

    /**
     * 「独立线程」开关（GTO {@code iThread}，默认 <b>true</b>）。
     *
     * <p>开 = 各线程尽量挑<b>不同</b>的配方（控制器逐线程匹配时排除已被占用的配方，
     * 取不到就留空线程）。
     *
     * <p>★ 0.3.286：<b>不再要求</b>结构里有无线能源仓 —— 开关打开即生效
     * （上一版照 GTO 的 {@code gtocore.machine.wireless_enable} 文案加过这个前置，
     * 用户要求去掉；判定类 {@code TentacleWirelessEnergy} 已随之删除）。
     */
    default boolean isIndependentThread() {
        return false;
    }
}
