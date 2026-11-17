package com.gtlmodular.common.machine.fuel;

import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.data.fuel.AccelerateHatches;

/**
 * 「加速仓装得上除增强版以外的多方块」的结构侧策略。
 *
 * <h2>职责边界</h2>
 * <p>本类只回答两个问题：<b>这台机器的结构要不要给加速仓留位置</b>、<b>留成什么样</b>。
 * 真正的结构改写在 {@code FactoryBlockPatternGainHatchQuotaMixin} 的结构收尾阶段完成；
 * 实际生效（让加速仓改配方时长）在 {@link AccelerateHatchLogic}。
 *
 * <h2>为什么不在 {@code Predicates.abilities} 上追加</h2>
 * <p>曾经的做法是在 {@code abilities(...)} 的返回值上 or 一个加速仓分支。这个位置有两个硬伤：
 * <ol>
 *   <li><b>会重复追加</b>：{@code Predicates.autoAbilities(...)} 内部对每种能力各调一次
 *       {@code abilities(...)}（输入/输出能量、物品、流体、维护、并行…），一台机器能调 6~8 次，
 *       于是同一个结构里被追加了 6~8 份加速仓分支。</li>
 *   <li><b>会被结构作者的下限覆盖</b>：{@code setMinGlobalLimited} / {@code setExactLimit}
 *       会遍历整个 limited 列表并逐项覆盖，追加进去的分支会被设成「最少 1 台」，
 *       导致不放加速仓就判结构不完整。</li>
 * </ol>
 * 两件事都只能在结构构建完成之后处理，因此挪到结构收尾阶段（见
 * {@link #attachToStructure(TraceabilityPredicate)}）。
 *
 * <h2>为什么排除增强版核心（GTL-Extend）的机器</h2>
 * <p>加速仓的口径是「除增强版以外的结构都接受」。增强版接管的机器由
 * {@link ExtendHatchPredicate} 用固定名单判定，其结构构建期会把机器凭据压栈；
 * 窗口内一律不追加即可，不需要另建一套机器识别。
 * 这些机器上仍可安装超频仓（见 {@code PredicatesExtendHatchMixin}）。
 *
 * <p>「结构侧排除」与「生效侧开关」是两件事：前者在结构构建期问一次，
 * 后者在运行期问一次（见 {@link #runtimeEnabled()}）。旧存档里已经摆在增强版机器上的
 * 加速仓，运行期仍按仓的百分比加速，不会因为这次口径收紧而突然失效。
 *
 * <h2>为什么不复用 {@link AccelerateHatches#predicate()}</h2>
 * <p>那个谓词带 {@code setPreviewCount(1)}，是给「结构作者主动声明一台加速仓」用的。
 * 本类是给<b>除增强版以外</b>的结构追加的兜底分支：不能让预览里凭空多出一台加速仓，
 * 因此兜底分支不带 previewCount —— 匹配范围与原谓词一致，附加语义只有「上限 1 台」。
 */
public final class AccelerateHatchPredicate {

    private AccelerateHatchPredicate() {
    }

    /** 配置总开关：玩家可以在配置里关掉「除增强版以外的多方块都接受加速仓」。 */
    public static boolean enabled() {
        return GTLModularConfig.isAccelerateHatchAnyMultiblock();
    }

    /**
     * 当前这次结构构建是否应该给加速仓留位置。
     *
     * <p>两个条件缺一不可：配置开着，且当前构建的不是增强版核心接管的机器。
     * 判定复用 {@link ExtendHatchPredicate#inExtendBuild()} ——
     * 窗口外（普通机器 / 非结构构建）恒为 false。
     */
    public static boolean shouldExtend() {
        return enabled() && !ExtendHatchPredicate.inExtendBuild();
    }

    /**
     * 运行期是否让加速仓生效（只看配置开关，不看结构排除）。
     *
     * <p>拆开是为了不让口径收紧影响旧存档：仓一旦已经在机器里，就应该继续加速。
     */
    public static boolean runtimeEnabled() {
        return enabled();
    }

    /**
     * 兜底分支：允许放加速仓，<b>最多 1 台</b>，<b>非必需</b>，不进结构预览。
     *
     * <h2>为什么是「最多 1 台」</h2>
     * <p>加速仓改的是整台机器配方耗时的百分比，装两台不会更快
     * （{@link AccelerateHatchLogic#find} 只取第一台），放开数量只会让玩家以为能叠。
     *
     * <h2>为什么不设最小值</h2>
     * <p>口径是「最多一个，不是必须的」：不放加速仓时结构照常成型。
     *
     * <h2>为什么上限只设在加速仓这一支上</h2>
     * <p>{@code setMaxGlobalLimited} 会把整个谓词列表里的每一项都设成同一个上限，
     * 所以必须先在加速仓自己的谓词上设好，再 OR 进结构 —— 对
     * {@code original.or(branch())} 的结果调用会把结构原有的其它仓也一起限成 1 台。
     */
    public static TraceabilityPredicate branch() {
        return com.gregtechceu.gtceu.api.pattern.Predicates.abilities(AccelerateHatches.ACCELERATE_HATCH)
                .setMaxGlobalLimited(1);
    }

    /**
     * 把加速仓位置挂到一个已经建好的结构谓词上。
     *
     * <h2>调用方必须保证幂等</h2>
     * <p>同一个结构谓词对象只应挂一次。结构收尾阶段会对每个格子调用它，
     * 而「这一格有没有加速仓位置」由调用方通过 {@link #branch()} 的返回是否已在
     * 列表中判断（见 {@code HatchQuotaRepair#attachGainHatch}）。
     *
     * @param original 结构某一格最终的谓词，不能为 {@code null}
     * @return 追加了加速仓分支的新谓词
     */
    public static TraceabilityPredicate attachToStructure(TraceabilityPredicate original) {
        if (original == null) {
            return null;
        }
        return original.or(branch());
    }
}
