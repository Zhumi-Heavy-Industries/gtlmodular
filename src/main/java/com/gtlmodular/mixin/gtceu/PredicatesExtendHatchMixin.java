package com.gtlmodular.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gtlmodular.common.machine.fuel.ExtendHatchPredicate;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让本模组的线程仓 / 超频仓能装到<b>增强版核心（GTL-Extend）的机器</b>上。
 *
 * <h2>为什么卡在 {@code Predicates.abilities} 上</h2>
 * <p>增强版把全部机器的结构与仓室谓词写在自己 jar 的 {@code MultiBlockMachineA} 一个文件里，
 * 我们既不能改它、也不该复制它的结构。所有结构声明「哪一格能放仓」都要经过
 * {@code Predicates.abilities(PartAbility...)}（已核实是唯一的仓室谓词入口），
 * 所以在这一层追加两条能力分支即可，结构形状一个字不动。
 *
 * <h2>为什么不能无差别追加（与加速仓的区别）</h2>
 * <p>加速仓是「除增强版以外的多方块」，而这两个仓按口径只给<b>增强版核心的机器</b>：
 * <ul>
 *   <li>线程仓要驱动本模组的线程引擎才有意义，挂到不认识它的机器上只是占一格；</li>
 *   <li>超频仓改的是超频口径，只对已经接了 {@code OverclockProfiles} 的链路有效（见 {@link ExtendHatchPredicate}）。</li>
 * </ul>
 * 因此这里必须先判断「当前正在构建的结构属于哪台机器」。
 *
 * <h2>怎么知道当前结构是谁</h2>
 * <p>结构工厂是 {@code Supplier<BlockPattern>}，构建发生在
 * {@code MultiblockMachineDefinition#getPattern()} 里；注入点在 {@code Predicates.abilities}
 * 本身拿不到 definition。因此改为：由 {@link ExtendHatchPredicate} 在结构构建期间
 * 用一个「当前正在构建的机器 id」凭据做判断，凭据由
 * {@code gtlmodular.mixin.gtceu.MultiblockDefinitionExtendHatchMixin} 在
 * {@code setPatternFactory} 阶段登记。
 *
 * <p>凭据拿不到时<b>一律不追加</b>：宁可维持原状，也不能把仓放给不该放的机器。
 */
@Mixin(value = Predicates.class, remap = false)
public abstract class PredicatesExtendHatchMixin {

    @Inject(method = "abilities", at = @At("RETURN"), cancellable = true, remap = false)
    private static void gtlmodular$allowExtendHatches(PartAbility[] abilities,
                                                      CallbackInfoReturnable<TraceabilityPredicate> cir) {
        if (!ExtendHatchPredicate.shouldExtend(abilities)) {
            return;
        }
        TraceabilityPredicate original = cir.getReturnValue();
        if (original == null) {
            return;
        }
        cir.setReturnValue(ExtendHatchPredicate.union(original));
    }
}
