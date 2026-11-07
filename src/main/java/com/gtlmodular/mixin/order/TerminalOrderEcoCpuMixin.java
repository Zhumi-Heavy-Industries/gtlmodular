package com.gtlmodular.mixin.order;

import com.gtlmodular.common.item.terminal.TerminalAutoBuildService;
import com.gtlmodular.common.item.terminal.TerminalOrderMarker;

import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.me.service.CraftingService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

/**
 * ECO CPU 版订单收尾：ECO 使用自己的 ECOCraftingCPULogic，不会走标准
 * CraftingCpuLogic 的收尾逻辑；这里在 tick 末尾做同样的伪订单完成处理。
 */
@Mixin(targets = "cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPULogic", remap = false)
public abstract class TerminalOrderEcoCpuMixin {

    @Shadow(remap = false)
    protected abstract void finishJob(boolean success);

    @Shadow(remap = false)
    public abstract GenericStack getFinalJobOutput();

    @Shadow(remap = false)
    public abstract void getAllWaitingFor(Set<AEKey> waitingFor);

    @Inject(method = "tickCraftingLogic", at = @At("TAIL"), remap = false)
    private void gtlmodular$finishEcoOrder(IEnergyService energy, CraftingService service, CallbackInfo ci) {
        GenericStack output = getFinalJobOutput();
        if (!TerminalOrderMarker.isMarker(output)) return;
        Set<AEKey> waiting = new HashSet<>();
        getAllWaitingFor(waiting);
        if (waiting.isEmpty()) {
            finishJob(true);
            TerminalAutoBuildService.completeOrder(output);
        }
    }
}
