package com.gtlmodular.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.integration.jade.provider.RecipeOutputProvider;
import com.gtlmodular.common.machine.engine.ThreadedJadeOutputs;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ★ 2026-11-08：<b>Jade「配方输出」= 各线程产物之和</b>。
 *
 * <h2>为什么用混入，而不是改 {@code getLastRecipe()}</h2>
 * GTCEu 的 {@code RecipeOutputProvider#write} 只读 {@code RecipeLogic.getLastRecipe()}
 * —— 单配方字段。本引擎的展示回写把<b>第 0 条批次</b>放进那个字段（GUI 进度条要用），
 * 于是 Jade 那行永远只显示一条线程。直接把字段换成「合成配方」会影响所有读取方
 * （配方锁、NAC 记账、其它模组），风险远大于收益。
 * ⇒ 只在本提供者写完 serverData 后<b>追加一次改写</b>：把
 * {@code OutputItems} / {@code OutputFluids} 换成
 * {@link ThreadedJadeOutputs} 求出的「各线程之和」。{@code lastRecipe} 一个字不改。
 *
 * <h2>边界</h2>
 * <ul>
 *   <li>只对实现了 {@code IThreadBatchLogic} 的机器生效（通用引擎 / NAC 外壳 / 地心模块）；
 *       其它机器原样交回上游；</li>
 *   <li>没有在跑批次 / 求和失败 ⇒ 什么都不改（上游那行照旧）；</li>
 *   <li>{@code required=false} + {@code defaultRequire=0}（见 {@code gtlmodular.mixins.json}）
 *       ⇒ 没织上也不影响启动，只是回到「只显示第 0 条」的旧观感。</li>
 * </ul>
 */
@Mixin(value = RecipeOutputProvider.class, remap = false)
public abstract class MixinRecipeOutputProvider {

    /**
     * 在 {@code write(CompoundTag, RecipeLogic)} 的末尾改写产物流水/物品列表。
     *
     * <p>方法名与签名逐字对齐 GTCEu 1.20.1-1.4.4（本地反编译证据：
     * {@code _lead_engine/ref/gtceu_jade_src/...RecipeOutputProvider.java}）。
     */
    @Inject(
            method = "write(Lnet/minecraft/nbt/CompoundTag;Lcom/gregtechceu/gtceu/api/machine/trait/RecipeLogic;)V",
            at = @At("TAIL"),
            remap = false)
    private void gtlmodular$sumThreadOutputs(CompoundTag data, RecipeLogic recipeLogic, CallbackInfo ci) {
        ThreadedJadeOutputs.rewrite(data, recipeLogic);
    }
}
