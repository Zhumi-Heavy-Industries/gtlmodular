package com.gtlmodular.mixin.gtceu;

import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gtlmodular.common.machine.fuel.AccelerateHatchPredicate;
import com.gtlmodular.common.machine.fuel.HatchQuotaRepair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 结构构建完成后的增益仓收尾：<b>去重、复位下限、按需补加速仓位置</b>。
 *
 * <h2>为什么必须是这一层</h2>
 * <p>{@code FactoryBlockPattern.build()} 是所有多方块结构（gtceu / gtlcore / 增强版 /
 * 第三方附属）构建的<b>唯一</b>出口，构建后每个格子拿到的谓词就是最终生效的那份。
 *
 * <p>不能更早的原因见 {@link HatchQuotaRepair} 的类注释，两条：
 * 在 {@code Predicates.abilities} 上追加会被 {@code autoAbilities} 的重复调用放大成多份，
 * 且会被结构作者的 {@code setExactLimit} 覆盖成「最少 1 台」。
 *
 * <h2>与 {@code MultiblockDefinitionExtendHatchMixin} 的分工</h2>
 * <p>增强版核心接管的机器在自己的凭据窗口里已经收尾过一次（那里能拿到机器 id，
 * 判定最准）。本注入覆盖剩下的全部结构 —— 窗口外的调用 {@code inExtendBuild()} 为
 * false，{@code shouldExtend()} 才会为真，才补加速仓。两边都调用同一个
 * {@link HatchQuotaRepair#normalizePattern}，界面口径一致，且天然幂等：
 * 已经有加速仓分支的格子不会再追加。
 *
 * <h2>保留原有功能</h2>
 * <ul>
 *   <li>只调整「增益仓」相关的分支，其它仓的谓词、数量、预览一概不碰；</li>
 *   <li>不新增格子，只在已有仓室位上增加可选项，结构形状与成型判定不变；</li>
 *   <li>加速仓分支不带 {@code previewCount}，结构预览里不会凭空多出一台；</li>
 *   <li>读不到结构数组时原样返回，退化为「与改造前一致」。</li>
 * </ul>
 */
@Mixin(value = FactoryBlockPattern.class, remap = false)
public abstract class FactoryBlockPatternGainHatchQuotaMixin {

    @Inject(method = "build", at = @At("RETURN"), remap = false)
    private void gtlmodular$normalizeGainHatches(CallbackInfoReturnable<BlockPattern> cir) {
        // 每台机器只问一次配置与排除条件，遍历与判定都在 HatchQuotaRepair 里。
        HatchQuotaRepair.normalizePattern(cir.getReturnValue(), AccelerateHatchPredicate.shouldExtend());
    }
}
