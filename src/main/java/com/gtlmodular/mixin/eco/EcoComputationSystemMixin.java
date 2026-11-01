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
