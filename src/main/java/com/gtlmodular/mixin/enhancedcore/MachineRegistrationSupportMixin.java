package com.gtlmodular.mixin.enhancedcore;

import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.gtl.enhancedcore.common.registration.MachineRegistrationSupport", remap = false)
public abstract class MachineRegistrationSupportMixin {

    @Inject(method = "requiredBlock", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void gtlmodular$acceptSuperZhuMiAssembly(
            String name, CallbackInfoReturnable<TraceabilityPredicate> cir) {
        try {
            if (!"gtladditions:me_super_pattern_buffer".equals(name)) {
                return;
            }
            Block theirs = ForgeRegistries.BLOCKS.getValue(
                    new ResourceLocation("gtladditions", "me_super_pattern_buffer"));
            Block ours = ForgeRegistries.BLOCKS.getValue(
                    new ResourceLocation("gtlmodular", "super_me_pattern_assembly"));
            // 任一方块缺失时保留原谓词，避免扩大为无法匹配的候选。
            if (theirs == null || ours == null) {
                return;
            }
            cir.setReturnValue(Predicates.blocks(theirs, ours));
        } catch (Throwable ignored) {
        }
    }
}
