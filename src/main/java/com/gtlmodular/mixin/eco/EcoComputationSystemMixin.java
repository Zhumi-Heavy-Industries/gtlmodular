package com.gtlmodular.mixin.eco;

import com.gtlmodular.common.item.RemoteTerminalGuard;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * eco 的计算子系统主机在自己的 createUI() 里又独立做了一次
 * {@code isPlayerCloseEnough(level,pos,player)}（8 格），只绕过 BlockUIHolder 不够。
 *
 * <p>持有「哦鲸鲸终端」时让这个静态判定直接返回 true，主机 UI 才能在远处创建。

 * <p><b>★ 当前状态：未登记进 gtlmodular.mixins.json（不生效）。</b>
 *   原因：{@code EcoMixinPlugin.shouldApplyMixin} 对 {@code .eco.} 包下的 mixin 一律返回
 *   {@code false}（2026-10-27 起临时停用，用于排查 AE 下单卡住）。要启用需同时：
 *   ① 把本类加进 mixins.json；② 移除插件里 {@code .eco.} 的高度拦截。
 *   目标 API 已核实存在（neoecoae 20.5.7），恢复时无需改注入点。
 */
@Mixin(targets = "cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem", remap = false)
public abstract class EcoComputationSystemMixin {

    @Inject(method = "isPlayerCloseEnough", at = @At("HEAD"), cancellable = true, remap = false)
    private static void gtlmodular$allowRemoteHost(Level level, BlockPos pos, Player player,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (RemoteTerminalGuard.hasTerminal(player)) {
            cir.setReturnValue(true);
        }
    }
}
