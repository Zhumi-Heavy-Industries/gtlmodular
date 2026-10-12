package com.gtlmodular.mixin.independent;

import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gtlmodular.common.machine.independent.io.EnergyDebitContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// GTLCore 覆写先写入电量再通知订阅者，必须在通知可能抛异常之前保存本批次的实际扣款。
@Mixin(value = NotifiableEnergyContainer.class, priority = 1100, remap = false)
public abstract class IndependentEnergyObservationMixin implements EnergyDebitContext.ObservedContainer {
    @Inject(method = "setEnergyStored", at = @At(value = "INVOKE",
            target = "Lcom/gregtechceu/gtceu/api/machine/trait/NotifiableEnergyContainer;checkOutputSubscription()V"), require = 1)
    private void gtlmodular$recordEnergy(long stored, CallbackInfo ci) {
        EnergyDebitContext.changed(this, stored);
    }
}
