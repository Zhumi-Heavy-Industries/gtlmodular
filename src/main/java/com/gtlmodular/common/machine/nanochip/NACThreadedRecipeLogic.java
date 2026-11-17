package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic;
import com.gtlmodular.common.machine.engine.IThreadBatchLogic;
import com.gtlmodular.common.machine.engine.ThreadedRecipeLogic;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

/**
 * NAC 的类型适配外壳。
 *
 * <p>NAC 的父类 {@code GTLAddWorkableElectricMultipleRecipesMachine} 会把
 * {@code getRecipeLogic()} 硬转成 {@link GTLAddMultipleRecipesLogic}，
 * 所以 NAC 的 recipe logic 必须继承它；但真正的调度仍委托给通用
 * {@link ThreadedRecipeLogic} / {@code ConcurrentRecipeEngine}。
 *
 * <p>这样没有复制第二套引擎：所有机器共用同一份独立线程实现，
 * NAC 这一层只负责满足 gtladditions 的返回类型契约，并把状态/存读档转给通用引擎。
 */
public final class NACThreadedRecipeLogic extends GTLAddMultipleRecipesLogic implements IThreadBatchLogic {

    private final ThreadedRecipeLogic delegate;

    public NACThreadedRecipeLogic(NanochipAssemblyComplexMachine machine) {
        super(machine);
        this.delegate = new ThreadedRecipeLogic(machine);
    }

    @Override
    public void serverTick() {
        delegate.serverTick();
    }

    @Override
    public boolean isWorking() {
        return delegate.isWorking();
    }

    @Override
    public boolean isActive() {
        return delegate.isActive();
    }

    @Override
    public boolean isIdle() {
        return delegate.isIdle();
    }

    @Override
    public boolean isWaiting() {
        return delegate.isWaiting();
    }

    @Override
    public boolean isSuspend() {
        return delegate.isSuspend();
    }

    @Override
    public boolean isWorkingEnabled() {
        return delegate.isWorkingEnabled();
    }

    @Override
    public void setWorkingEnabled(boolean enabled) {
        delegate.setWorkingEnabled(enabled);
    }

    @Override
    public int getProgress() {
        return delegate.getProgress();
    }

    @Override
    public int getMaxProgress() {
        return delegate.getMaxProgress();
    }

    @Override
    public int getDuration() {
        return delegate.getDuration();
    }

    @Override
    public GTRecipe getLastRecipe() {
        return delegate.getLastRecipe();
    }

    @Override
    public GTRecipe getLastOriginRecipe() {
        return delegate.getLastOriginRecipe();
    }

    @Override
    public RecipeLogic.Status getStatus() {
        return delegate.getStatus();
    }

    @Override
    public double getProgressPercent() {
        return delegate.getProgressPercent();
    }

    @Override
    public long getTotalContinuousRunningTime() {
        return delegate.getTotalContinuousRunningTime();
    }

    @Override
    public Map<RecipeCapability<?>, Object2IntMap<?>> getChanceCaches() {
        return delegate.getChanceCaches();
    }

    @Override
    public List<Component> getFancyTooltip() {
        return delegate.getFancyTooltip();
    }

    @Override
    public void markLastRecipeDirty() {
        delegate.markLastRecipeDirty();
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        delegate.saveCustomPersistedData(tag, forDrop);
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        delegate.loadCustomPersistedData(tag);
    }

    // ------------------------------------------------------------------
    // IThreadBatchLogic：Jade 通用线程显示的三个只读读口。
    //
    // ★ 2026-11-08【D-1 修复】Jade 的 NanochipThreadedJadeProvider 早就有「通用线程引擎」
    //   分支（genericThreadedLogic → IThreadBatchLogic），但本外壳没实现该接口 ⇒ NAC 走不进
    //   那条分支；而旧分支认的 NanochipThreadedRecipeLogic 又已不在活路径（全树无 new）
    //   ⇒ 线程 Jade 行 / 猪咪样板总成的安全闸全部静默失效。
    //   本类只做三件事：把三个只读读数原样转发给 delegate（真正的调度在 engine 里），
    //   不新增字段、不改存档、不改调度。
    // ------------------------------------------------------------------

    @Override
    public int threadLimit() {
        return delegate.threadLimit();
    }

    @Override
    public int runningBatches() {
        return delegate.runningBatches();
    }

    @Override
    public List<ThreadedRecipeLogic.BatchView> snapshotBatches() {
        return delegate.snapshotBatches();
    }
}
