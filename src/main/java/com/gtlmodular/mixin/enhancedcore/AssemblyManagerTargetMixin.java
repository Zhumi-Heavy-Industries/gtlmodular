package com.gtlmodular.mixin.enhancedcore;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.compat.PatternAssemblyManagerTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 总成管理器（GTL-Enhancedcore）把「哪些机器算总成」写死在 {@code AssemblyManagerIndex.isTarget()} 里：
 * 只认 {@code MEPatternBufferPartMachineBase} + id {@code gtladditions:me_super_pattern_buffer}。
 * 传送列表、橙金标记与快捷传送都走这一个判定，所以在它前面补上猪咪样板总成。
 *
 * <p>目标类在旧版 Enhancedcore 里不存在（管理器是 4.x 之后才有的）：{@link Pseudo} 让整条注入
 * 在缺类时静默跳过，<b>不能直接引用目标类</b>，否则没装/旧版 Enhancedcore 的包会启动失败。
 *
 * <p>优先级 1300：排在 gtladditions（1000）与 Enhancedcore 自己（1200）之后，避免抢注入/改调用。
 * {@code require = 0}：上游若改了方法签名，只失效本兼容，不崩游戏。
 */
@Pseudo
@Mixin(targets = "com.gtl.enhancedcore.common.assembly.AssemblyManagerIndex", remap = false, priority = 1300)
public abstract class AssemblyManagerTargetMixin {

    private static int gtlmodular$diagBudget = 16;

    @Inject(method = "isTarget", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gtlmodular$acceptOwnAssemblies(MetaMachine machine, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (machine == null || !PatternAssemblyManagerTargets.isTarget(machine)) {
                return;
            }
            if (gtlmodular$diagBudget > 0) {
                gtlmodular$diagBudget--;
                GTLModular.LOGGER.info("[gtlmodular] 总成管理器已接纳猪咪总成：{} @ {}",
                        machine.getDefinition().getId(), machine.getPos());
            }
            cir.setReturnValue(true);
        } catch (Throwable ignored) {
            // 兼容补丁失败只影响总成管理器是否列出我们，绝不牵连同机其它逻辑。
        }
    }
}
