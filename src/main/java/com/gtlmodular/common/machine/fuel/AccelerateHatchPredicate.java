package com.gtlmodular.common.machine.fuel;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.data.fuel.AccelerateHatches;

/**
 * 「加速仓装得上任何多方块」的结构侧策略。
 *
 * <h2>职责边界</h2>
 * <p>本类只回答两件事：<b>现在要不要给结构补加速仓分支</b>、<b>补成什么样子</b>。
 * 真正的注入点在 {@code PredicatesAbilitiesMixin}，实际生效（让加速仓改配方时长）在
 * {@link AccelerateHatchLogic}。两侧共用这一个开关，不会出现「装得上但不生效」或反之。
 *
 * <h2>为什么不复用 {@link AccelerateHatches#predicate()}</h2>
 * <p>那个谓词带 {@code setMaxGlobalLimited(1)} + {@code setPreviewCount(1)}，是给「结构作者主动声明
 * 一台加速仓」用的。本类是给<b>所有</b>结构追加的兜底分支：不能让预览里凭空多出一台加速仓，
 * 也不能因为这层兜底再去参与一次全局配额计数（结构自己已经写过的话会叠两次）。
 * 因此兜底分支不带 previewCount、不带数量上限 —— 匹配范围与原谓词一致，附加语义为零。
 */
public final class AccelerateHatchPredicate {

    private AccelerateHatchPredicate() {
    }

    /** 总开关：玩家可以在配置里关掉「任何多方块都接受加速仓」。 */
    public static boolean enabled() {
        return GTLModularConfig.isAccelerateHatchAnyMultiblock();
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
