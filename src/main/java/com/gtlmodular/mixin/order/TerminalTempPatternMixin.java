package com.gtlmodular.mixin.order;

import com.gtlmodular.common.item.terminal.TerminalOrderMarker;
import com.gtlmodular.common.item.terminal.TerminalTempPatternHolder;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Set;

@Mixin(CraftingService.class)
public abstract class TerminalTempPatternMixin implements TerminalTempPatternHolder {

    @Unique
    private IPatternDetails gtlmodular$tempPattern;

    @Override
    public void gtlmodular$setTempPattern(IPatternDetails pattern) {
        this.gtlmodular$tempPattern = pattern;
    }

    @Override
    public IPatternDetails gtlmodular$getTempPattern() {
        return this.gtlmodular$tempPattern;
    }

    @Inject(method = "getCraftingFor", at = @At("RETURN"), cancellable = true, remap = false)
    private void gtlmodular$appendOrderPattern(AEKey what,
                                               CallbackInfoReturnable<Collection<IPatternDetails>> cir) {
        IPatternDetails pattern = gtlmodular$tempPattern;
        if (pattern == null || !TerminalOrderMarker.isMarker(what)) return;
        Collection<IPatternDetails> existing = cir.getReturnValue();
        if (existing != null && !existing.isEmpty()) return;
        cir.setReturnValue(Set.of(pattern));
    }
}
