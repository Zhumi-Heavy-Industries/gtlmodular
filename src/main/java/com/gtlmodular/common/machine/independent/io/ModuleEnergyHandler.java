package com.gtlmodular.common.machine.independent.io;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.machine.MoltenModuleMachineBase;

import java.util.List;

/**
 * 模块机的电由主机供给，机器自己的能力代理里没有能源仓处理器；
 * 独立批次按 tick 扣电时必须有一个能真实走 {@code host.consumeEU} 的处理器，
 * 否则 EU 内容永远无人接收，批量会一直卡在「tick 不足[输入/电]」。
 */
public final class ModuleEnergyHandler implements IRecipeHandler<Long> {

    private final MoltenModuleMachineBase<?> module;

    public ModuleEnergyHandler(MoltenModuleMachineBase<?> module) {
        this.module = module;
    }

    @Override
    public List<Long> handleRecipeInner(IO io, GTRecipe recipe, List<Long> left, String slotName, boolean simulate) {
        if (io != IO.IN) return left;
        long remaining = 0L;
        for (Long value : left) {
            long want = value == null ? 0L : Math.max(0L, value);
            if (want == 0L) continue;
            long paid = simulate ? Math.min(want, available()) : debit(want);
            remaining += want - paid;
        }
        return remaining == 0L ? null : List.of(remaining);
    }

    private long available() {
        var host = module.getHost();
        return host == null ? 0L : Math.max(0L, host.getTotalStoredEU());
    }

    private long debit(long want) {
        var container = module.getEnergyContainer();
        if (container == null) return 0L;
        long moved = -container.changeEnergy(-want);
        return Math.max(0L, moved);
    }

    @Override
    public List<Object> getContents() {
        return List.of();
    }

    @Override
    public double getTotalContentAmount() {
        return available();
    }

    @Override
    public RecipeCapability<Long> getCapability() {
        return EURecipeCapability.CAP;
    }
}
