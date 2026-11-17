package com.gtlmodular.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.machine.fuel.AccelerateHatchLogic;
import com.gtlmodular.common.machine.fuel.OverclockHatchLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 所有配方逻辑的<b>统一生效点</b>：配方进入计时后，把加速仓 / 超频仓套到 {@code duration} 上。
 *
 * <h2>为什么是 setupRecipe</h2>
 * <p>加速仓在原生链路上的入口是 {@code doModifyRecipe → IMultiPart#modifyRecipe}，
 * 但那条路只有一部分机器走：
 * <ul>
 *   <li>gtlcore 用 {@code @Overwrite} 换掉了 {@code doModifyRecipe}，不再遍历部件；</li>
 *   <li>{@code isa_mill} 一类的机器根本不继承 {@code WorkableMultiblockMachine}；</li>
 *   <li>gtladditions 的 Mutable 系、增强版的原生引擎各有自己的配方链。</li>
 * </ul>
 * {@code setupRecipe} 则是 {@link RecipeLogic} 上<b>所有</b>配方逻辑共用的必经一步，
 * 覆盖面最广，且每次开跑只走一次。
 *
 * <h2>为什么注在 RETURN</h2>
 * <p>正常返回时 {@code duration} 才写好；提前 return 的分支（燃料不匹配、输入不够、
 * {@code beforeWorking} 拒绝）机器本轮根本没跑配方，也不该被加速。注在 RETURN 天然跳过它们。
 *
 * <h2>幂等：为什么先比再写</h2>
 * <p>同一个 {@link GTRecipe} 对象会被 {@code setupRecipe} 反复喂进来
 * （{@code onRecipeFinish} 里会再调一次）。每次都乘一次百分比会让时长递进塌缩
 * （实测 99 → 66 → 1，表现为「越跑越快」）。所以：
 * <ol>
 *   <li>{@link AccelerateHatchLogic#applyIfNeeded} 自己按「上一次结果值」去重；</li>
 *   <li>{@link OverclockHatchLogic#applyForLevels} 只在标准超频链路没处理过
 *       （{@link OverclockHatchLogic#wasHandled}）时才补算，避免与
 *       {@code RecipeHelper#applyOverclock} 的倍率替换叠乘两次；</li>
 *   <li>本类最后只在时长<b>真的变了</b>时才写回 {@code this.duration}。</li>
 * </ol>
 *
 * <h2>优先级 1300</h2>
 * <p>要排在 gtlcore / gtladditions / 增强版对 {@code setupRecipe} 的改写<b>之后</b>执行，
 * 否则算出来的时长会被它们再覆盖回去。
 */
@Mixin(value = RecipeLogic.class, remap = false, priority = 1300)
public abstract class RecipeLogicAccelerateSetupMixin {

    @Shadow(remap = false)
    protected int duration;

    /** 原生链路默认的「每档耗时倍率」（完美超频 0.25）。 */
    private static final double DEFAULT_OC_FACTOR = 0.25D;

    @Inject(method = "setupRecipe", at = @At("RETURN"), remap = false)
    private void gtlmodular$applyGainHatches(GTRecipe recipe, CallbackInfo ci) {
        if (recipe == null) {
            return;
        }
        RecipeLogic self = (RecipeLogic) (Object) this;
        int before = recipe.duration;

        // 加速仓：按玩家设定的百分比缩短。
        GTRecipe adjusted = AccelerateHatchLogic.applyIfNeeded(self.getMachine(), recipe);

        // 超频仓：标准超频链路（RecipeHelper#applyOverclock）已经替换过倍率的机器不要再补算，
        // 否则同一档数会被乘两次。
        if (OverclockHatchLogic.enabled() && !OverclockHatchLogic.wasHandled(recipe)) {
            long maxEUt = maxOverclockVoltage(self.getMachine());
            if (maxEUt > 0L) {
                adjusted = OverclockHatchLogic.applyForLevels(
                        self.getMachine(), adjusted, maxEUt, DEFAULT_OC_FACTOR);
            }
        }

        if (adjusted == null || adjusted.duration == before) {
            return;
        }
        this.duration = Math.max(1, adjusted.duration);
    }

    /** 机器可用的超频电压；不支持超频的机器返回 0（= 不处理超频仓）。 */
    private static long maxOverclockVoltage(MetaMachine machine) {
        if (machine instanceof IOverclockMachine oc) {
            return oc.getOverclockVoltage();
        }
        return 0L;
    }
}
