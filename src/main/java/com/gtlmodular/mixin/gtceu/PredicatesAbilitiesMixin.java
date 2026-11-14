package com.gtlmodular.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gtlmodular.common.machine.fuel.AccelerateHatchPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 让加速仓能装到「任何多方块」上 —— 结构侧的<b>唯一</b>注入点。
 *
 * <h2>为什么是这个点</h2>
 * 所有多方块结构（本模组、gtceu、gtlcore、增强版核心、第三方附属）声明「哪一格可以放仓」时，
 * 走的都是 {@code Predicates.abilities(PartAbility...)}(已按字节码核实该类常量池里有这个方法，
 * 且它是全库唯一的仓室谓词入口)。每个结构再各自 {@code .or(...)} 自己的例外，
 * 因此<b>在 abilities 的返回值上追加一个加速仓分支</b>，就等于给所有结构都加了一格。
 *
 * <h2>为什么把加速仓并进返回值里、而不是另外 OR 一次</h2>
 * 结构形如 {@code casing.or(abilities(IMPORT_ITEMS)).where('C', ...)} —— abilities 的返回是
 * 谓词树的一个节点。追加分支必须回到同一个节点上，才能保证「原本允许放仓的格」多出一种可选方块，
 * 而不会变成「多出一个可放加速仓的新位置」。
 *
 * <h2>只加方块，不动数量限制</h2>
 * 追加的分支是 unlimited 的（不限台数），因为「装得上」是玩家的目标；
 * 数量上限属于各机器自己的口径（{@link AccelerateHatchPredicate} 里只放宽匹配，不改上限）。
 *
 * <h2>纪律</h2>
 * <ul>
 *   <li>注入失败（本仓 {@code defaultRequire = 0}）时，行为逐字回到改造前 ——
 *       加速仓只是「有的机器装不上」，绝不会影响其他仓的匹配。</li>
 *   <li>本类<b>不改任何既有方块集合</b>：只在 OR 树上追加一个「匹配加速仓」的分支，
 *       原本能放的仓一个不少，原本不能放的方块一个不多（加速仓除外）。</li>
 *   <li>P 位 / 只读结构的机器不受影响：它们的结构本来就没调 abilities、或者调了也不含仓位。</li>
 * </ul>
 */
@Mixin(value = Predicates.class, remap = false)
public abstract class PredicatesAbilitiesMixin {

    private static final Logger GTLMODULAR$LOGGER = LoggerFactory.getLogger("GTL-Modular");

    @Inject(method = "abilities", at = @At("RETURN"), cancellable = true, remap = false)
    private static void gtlmodular$allowAccelerateHatch(PartAbility[] abilities,
                                                       CallbackInfoReturnable<TraceabilityPredicate> cir) {
        if (!AccelerateHatchPredicate.enabled()) {
            return;
        }
        TraceabilityPredicate original = cir.getReturnValue();
        if (original == null) {
            return;
        }
        if (AccelerateHatchPredicate.alreadyIncludes(abilities)) {
            // 结构作者自己已经写了加速仓谓词（本模组那 9 处），不要再叠一层，
            // 否则同一能力出现两条 limited 分支，全局配额会被算两次。
            return;
        }
        try {
            cir.setReturnValue(AccelerateHatchPredicate.union(original));
        } catch (RuntimeException failed) {
            // 结构构建是启动期一次性动作，这里失败不能拖垮整个注册流程。
            GTLMODULAR$LOGGER.warn("[gtlmodular] 加速仓结构补丁跳过一次：{}", failed.toString());
        }
    }
}
