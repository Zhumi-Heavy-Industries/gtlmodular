package com.gtlmodular.common.machine.nanochip.vacuum;

import com.gtlmodular.common.machine.nanochip.registration.NanochipModuleSpec;
import com.gtlmodular.common.machine.nanochip.registration.NanochipModules;
import com.gtlmodular.common.machine.nanochip.NanochipModuleMachine;
import com.gtlmodular.common.machine.nanochip.NanochipModuleKind;
import com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine;
import com.gtlmodular.common.machine.nanochip.NanochipSplitterRules;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;

public final class NanochipModuleVacuum {

    private final NanochipModuleMachine module;
    private final NanochipSplitterRules splitterRules = new NanochipSplitterRules();
    private long lastRouterTick = Long.MIN_VALUE;

    public NanochipModuleVacuum(NanochipModuleMachine module) {
        this.module = module;
    }

    public NanochipSplitterRules splitterRules() {
        return splitterRules;
    }

    public void tickRouter(NanochipModuleKind kind) {
        tickRouter(NanochipModules.builtin(kind));
    }

    public void tickRouter(NanochipModuleSpec kind) {
        if (!kind.isRouter()) {
            return;
        }
        Level level = module.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        NanochipAssemblyComplexMachine nac = module.getHost();
        if (!module.hostConnected() || nac == null || splitterRules.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        if (lastRouterTick != Long.MIN_VALUE && now - lastRouterTick < 20L) {
            return;
        }
        lastRouterTick = now;
        int signal = level.getBestNeighborSignal(module.getPos());
        boolean moved = false;
        for (NanochipSplitterRules.Rule rule : splitterRules.rules()) {
            if (!rule.effective()) {
                continue;
            }
            if (rule.redstoneThreshold() > 0 && signal < rule.redstoneThreshold()) {
                continue;
            }
            long amount = nac.vacuumNetwork().transfer(rule.in(), rule.out(),
                    rule.itemFilter(), NanochipSplitterRules.MAX_TRANSFER_PER_RULE);
            if (amount > 0L) {
                moved = true;
            }
        }
        if (moved) {
            nac.markVacuumDirty();
        }
    }

    public void save(CompoundTag tag) {
        tag.put("GTLModularSplitterRules", splitterRules.save());
    }

    public void load(CompoundTag tag) {
        if (tag.contains("GTLModularSplitterRules")) {
            splitterRules.load(tag.getCompound("GTLModularSplitterRules"));
        }
    }
}
