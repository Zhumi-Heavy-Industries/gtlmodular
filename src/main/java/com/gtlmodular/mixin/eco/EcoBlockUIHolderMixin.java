package com.gtlmodular.mixin.eco;

import com.gtlmodular.common.item.RemoteTerminalGuard;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * eco 的 BlockUIHolder 默认要求玩家距方块 8 格内（distanceToSqr <= 64）。
 * 持有「哦鲸鲸终端」时直接视为有效，从而让终端能远程打开已绑定的计算子系统界面。
 *
 * <p>目标类缺失时由 {@link com.gtlmodular.mixin.EcoMixinPlugin} 跳过，不影响未装 eco 的整合包。
 */
@Mixin(targets = "cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory.BlockUIMenuType$BlockUIHolder", remap = false)
public abstract class EcoBlockUIHolderMixin {

    @Shadow
    @Final
    public Player player;

    @Shadow
    @Final
    public BlockPos pos;

    @Inject(method = "isInvalid", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlmodular$allowRemoteTerminal(CallbackInfoReturnable<Boolean> cir) {
        if (RemoteTerminalGuard.hasTerminal(this.player)) {
            cir.setReturnValue(false);
        }
    }
}
