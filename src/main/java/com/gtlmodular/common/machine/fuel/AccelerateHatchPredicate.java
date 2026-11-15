package com.gtlmodular.common.machine.fuel;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.data.fuel.AccelerateHatches;

/**
 * 「加速仓装得上除增强版以外的多方块」的结构侧策略。
 *
 * <h2>职责边界</h2>
 * <p>本类只回答两件事：<b>现在要不要给结构补加速仓分支</b>、<b>补成什么样子</b>。
 * 真正的注入点在 {@code PredicatesAbilitiesMixin}，实际生效（让加速仓改配方时长）在
 * {@link AccelerateHatchLogic}。
 *
 * <h2>为什么排除增强版核心（GTL-Extend）的机器</h2>
 * <p>加速仓的口径是「除增强版以外的结构都接受」。增强版接管的机器由
 * {@link ExtendHatchPredicate} 用固定名单判定，其结构构建期会把机器凭据压栈；
 * 因此这里只需在窗口内不追加兜底分支即可，不需要另建一套机器识别。
 * 这些机器上仍可安装线程仓 / 超频仓（见 {@code PredicatesExtendHatchMixin}）。
 *
 * <p>「结构侧排除」与「生效侧开关」是两件事：前者在结构谓词构建期问一次，
 * 后者在运行期问一次（见 {@link #runtimeEnabled()}）。旧存档里已经摆在增强版机器上的
 * 加速仓，运行期仍按仓的百分比加速，不会因为这次口径收紧而突然失效。
 *
 * <h2>为什么不复用 {@link AccelerateHatches#predicate()}</h2>
 * <p>那个谓词带 {@code setMaxGlobalLimited(1)} + {@code setPreviewCount(1)}，是给「结构作者主动声明
 * 一台加速仓」用的。本类是给<b>除增强版以外</b>的结构追加的兜底分支：不能让预览里凭空多出一台加速仓，
 * 也不能因为这层兜底再去参与一次全局配额计数（结构自己已经写过的话会叠两次）。
 * 因此兜底分支不带 previewCount、不带数量上限 —— 匹配范围与原谓词一致，附加语义为零。
 */
public final class AccelerateHatchPredicate {

    private AccelerateHatchPredicate() {
    }

    /** 配置总开关：玩家可以在配置里关掉「除增强版以外的多方块都接受加速仓」。 */
    public static boolean enabled() {
        return GTLModularConfig.isAccelerateHatchAnyMultiblock();
    }

    /**
     * 结构构建期是否应该给这次 {@code abilities(...)} 追加兜底分支。
     *
     * <p>两个条件缺一不可：配置开着，且当前构建的不是增强版核心接管的机器。
     * 判定复用 {@link ExtendHatchPredicate#inExtendBuild()} —— 它的凭据窗口正是
     * {@code Predicates.abilities} 被调用的那一刻，窗口外（普通机器 / 非结构构建）恒为 false。
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
     * 这组能力里是否已经包含加速仓 —— 包含则说明结构作者自己写了，兜底分支不要再叠。
     */
    public static boolean alreadyIncludes(PartAbility[] abilities) {
        if (abilities == null) {
            return false;
        }
        for (PartAbility ability : abilities) {
            if (ability == AccelerateHatches.ACCELERATE_HATCH) {
                return true;
            }
        }
        return false;
    }

    /**
     * 兜底分支：匹配任何一台加速仓，不设数量上限、不进结构预览。
     *
     * <p>与结构原谓词是 OR 关系，所以「原本能放仓的格子」只是多了一种可选方块。
     */
    public static TraceabilityPredicate branch() {
        return com.gregtechceu.gtceu.api.pattern.Predicates.abilities(AccelerateHatches.ACCELERATE_HATCH);
    }

    /**
     * 把兜底分支挂到结构原有的仓室谓词上。
     *
     * @param original 结构原本的仓室谓词（{@code Predicates.abilities(...)} 的返回值）
     * @return 追加了加速仓分支的新谓词；{@code original} 为 null 时原样返回 null
     */
    public static TraceabilityPredicate union(TraceabilityPredicate original) {
        if (original == null) {
            return null;
        }
        return original.or(branch());
    }
}
