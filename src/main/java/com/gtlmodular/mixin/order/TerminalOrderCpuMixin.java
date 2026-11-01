package com.gtlmodular.mixin.order;

import com.gtlmodular.common.item.terminal.TerminalOrderMarker;

import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.service.CraftingService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

/**
 * GTO 式订单收尾：订单标记没有真实样板供应器，AE 会一直等最终产物。
 * 当所有子材料都已合成、waitingFor 为空时，直接把订单 job 标记完成，
 * 让材料留在 AE 网络里，而不是卡死。
 */
@Mixin(CraftingCpuLogic.class)
public abstract class TerminalOrderCpuMixin {

    @Shadow(remap = false)
    private void finishJob(boolean success) {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    public abstract GenericStack getFinalJobOutput();

    @Shadow(remap = false)
    public abstract void getAllWaitingFor(Set<AEKey> waitingFor);

    @Inject(method = "tickCraftingLogic", at = @At("TAIL"), remap = false)
    private void gtlmodular$finishOrderJob(IEnergyService energy, CraftingService service, CallbackInfo ci) {
        GenericStack output = getFinalJobOutput();
        if (!TerminalOrderMarker.isMarker(output)) return;
        Set<AEKey> waiting = new HashSet<>();
        getAllWaitingFor(waiting);
        if (waiting.isEmpty()) {
            finishJob(true);
        }
    }
}
