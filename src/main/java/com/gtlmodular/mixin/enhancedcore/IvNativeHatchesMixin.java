package com.gtlmodular.mixin.enhancedcore;

import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.gtl.enhancedcore.common.recipe.iv.IvNativeHatches", remap = false)
public abstract class IvNativeHatchesMixin {

    @Shadow
    @Final
    @Mutable
    private TraceabilityPredicate buffer;

    @Inject(method = "<init>", at = @At("TAIL"), require = 0, remap = false)
    private void gtlmodular$acceptSuperZhuMiAssembly(CallbackInfo ci) {
        try {
            Block theirs = ForgeRegistries.BLOCKS.getValue(
                    new ResourceLocation("gtladditions", "me_super_pattern_buffer"));
            Block ours = ForgeRegistries.BLOCKS.getValue(
                    new ResourceLocation("gtlmodular", "super_me_pattern_assembly"));
            // 任一方块缺失时保留原谓词，避免可用结构被无效候选替换。
            if (theirs == null || ours == null) {
                return;
            }
            this.buffer = Predicates.blocks(theirs, ours)
                    .setMinGlobalLimited(1)
                    .setPreviewCount(1);
        } catch (Throwable ignored) {
        }
    }
}
