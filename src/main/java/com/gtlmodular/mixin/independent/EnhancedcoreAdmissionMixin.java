package com.gtlmodular.mixin.independent;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtlmodular.common.machine.independent.control.IndependentLogicAccess;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.gtl.enhancedcore.common.recipe.iv.IvBuffers", remap = false)
public abstract class EnhancedcoreAdmissionMixin {
    // push 在扣除 AE 输入前先 bind；同时拦住绑定，避免收尾期间新建另一方的订单所有权。
    @Inject(method = "bind(Lorg/gtlcore/gtlcore/common/machine/multiblock/part/ae/MEPatternBufferPartMachine;Lcom/gregtechceu/gtceu/api/machine/multiblock/WorkableElectricMultiblockMachine;)Z",
            at = @At("HEAD"), cancellable = true, require = 1)
    private static void gtlmodular$deferAdmission(MEPatternBufferPartMachine buffer, WorkableElectricMultiblockMachine machine,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (IndependentLogicAccess.blocksExternal(machine)) cir.setReturnValue(false);
    }
}
