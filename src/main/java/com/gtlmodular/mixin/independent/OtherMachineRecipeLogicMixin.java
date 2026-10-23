package com.gtlmodular.mixin.independent;

import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.common.machine.independent.control.*;
import com.gtlmodular.common.machine.independent.persistence.IndependentBatchPersistence;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RecipeLogic.class, remap = false, priority = 1500)
public abstract class OtherMachineRecipeLogicMixin implements IndependentLogicAccess {
    @Shadow protected GTRecipe lastRecipe;
    @Shadow protected GTRecipe lastOriginRecipe;
    @Shadow protected int progress;
    @Shadow protected int duration;
    @Shadow protected boolean recipeDirty;
    @Shadow private boolean isActive;
    @Shadow protected TickableSubscription subscription;
    @Unique private IndependentMachineController gtlmodular$independent;
    @Unique private final UpstreamRecipeSetup<GTRecipe> gtlmodular$upstreamSetup = new UpstreamRecipeSetup<>();

    @Override public IndependentMachineController gtlmodular$controller() { return gtlmodular$independent; }
    @Override public UpstreamRecipeSetup<GTRecipe> gtlmodular$upstreamSetup() { return gtlmodular$upstreamSetup; }

    @Override
    public IndependentMachineController gtlmodular$controller(boolean create) {
        if (gtlmodular$independent == null && create) {
            gtlmodular$independent = new IndependentMachineController((RecipeLogic) (Object) this);
        }
        return gtlmodular$independent;
    }

    @Override
    public void gtlmodular$clearUpstreamCache() {
        gtlmodular$upstreamSetup.clear();
        lastRecipe = null;
        lastOriginRecipe = null;
        progress = 0;
        duration = 0;
        isActive = false;
        recipeDirty = false;
        ((RecipeLogic) (Object) this).lastFailedMatches = null;
    }

    @Override
    public void gtlmodular$subscribeIndependent() {
        RecipeLogic logic = (RecipeLogic) (Object) this;
        if (!logic.getMachine().isRemote()) {
            subscription = logic.getMachine().subscribeServerTick(subscription, logic::serverTick);
        }
    }

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtlmodular$tick(CallbackInfo ci) {
        gtlmodular$upstreamSetup.clear();
        RecipeLogic logic = (RecipeLogic) (Object) this;
        var controller = gtlmodular$controller(gtlmodular$eligibleCached(logic));
        if (controller != null && controller.tick()) ci.cancel();
    }

    @Unique private long gtlmodular$eligibleGeneration = Long.MIN_VALUE;
    @Unique private boolean gtlmodular$eligible;

    /**
     * 资格判定按「设置代际号」缓存：机器类与机器 ID 对一台机器是常量，
     * 只有配置重载才会改变结果。全服每台机器每 tick 都走一次原判定
     * （拼 ID 字符串 + 规则求值）在机器多时是线性开销，这里压成一个 long 比较。
     */
    @Unique
    private boolean gtlmodular$eligibleCached(RecipeLogic logic) {
        long generation = IndependentMachineRegistry.generation();
        if (generation != gtlmodular$eligibleGeneration) {
            gtlmodular$eligibleGeneration = generation;
            gtlmodular$eligible = IndependentMachineRegistry.eligible(logic);
        }
        return gtlmodular$eligible;
    }

    @Inject(method = "onMachineLoad", at = @At("TAIL"), require = 1)
    private void gtlmodular$loadMachine(CallbackInfo ci) {
        IndependentMachineRegistry.loaded((RecipeLogic) (Object) this);
    }

    @Inject(method = "updateTickSubscription", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtlmodular$subscription(CallbackInfo ci) {
        RecipeLogic logic = (RecipeLogic) (Object) this;
        if (gtlmodular$independent != null && gtlmodular$independent.ownsWork()
                || IndependentMachineRegistry.eligible(logic)) {
            if (!logic.getMachine().isRemote() && !logic.getMachine().isInValid()) {
                gtlmodular$subscribeIndependent();
                ci.cancel();
            }
        }
    }

    @Inject(method = "saveCustomPersistedData", at = @At("TAIL"), require = 1)
    private void gtlmodular$save(CompoundTag tag, boolean forDrop, CallbackInfo ci) {
        if (gtlmodular$independent != null) gtlmodular$independent.save(tag, forDrop);
    }

    @Inject(method = "loadCustomPersistedData", at = @At("TAIL"), require = 1)
    private void gtlmodular$load(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(IndependentBatchPersistence.ROOT_KEY)) gtlmodular$controller(true).load(tag);
    }

    @Inject(method = {"resetRecipeLogic", "inValid", "interruptRecipe"}, at = @At("HEAD"), cancellable = true, require = 3)
    private void gtlmodular$preserve(CallbackInfo ci) {
        if (gtlmodular$independent != null && gtlmodular$independent.ownsWork()) {
            gtlmodular$independent.invalidateRuntime();
            ci.cancel();
        }
    }

    @Inject(method = {"findAndHandleRecipe", "handleRecipeWorking", "onRecipeFinish"}, at = @At("HEAD"), cancellable = true, require = 3)
    private void gtlmodular$isolate(CallbackInfo ci) {
        if (gtlmodular$independent != null && gtlmodular$independent.ownsWork()) ci.cancel();
    }

    @Inject(method = "checkMatchedRecipeAvailable", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtlmodular$stopFailedCache(GTRecipe recipe, CallbackInfoReturnable<Boolean> cir) {
        if (IndependentLogicAccess.blockStarts((RecipeLogic) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "setupRecipe", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtlmodular$stopSetup(GTRecipe recipe, CallbackInfo ci) {
        RecipeLogic logic = (RecipeLogic) (Object) this;
        if (!gtlmodular$upstreamSetup.consume(recipe) && IndependentLogicAccess.blockStarts(logic)) {
            // Mutable 的旧单配方完成后可直接 setup；它尚未扣新料，取消后清除旧的完成进度。
            if (gtlmodular$independent == null || !gtlmodular$independent.ownsWork()) {
                gtlmodular$clearUpstreamCache();
                if (!logic.isSuspend()) logic.setStatus(RecipeLogic.Status.IDLE);
            }
            ci.cancel();
        }
    }
}
