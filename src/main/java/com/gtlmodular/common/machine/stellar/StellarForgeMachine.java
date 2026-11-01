package com.gtlmodular.common.machine.stellar;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IExplosionMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtlmodular.GTLModular;
import net.minecraft.network.chat.Component;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.TierCasingMachine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 恒星炎炀锻炉（{@code gtceu:stellar_forge}）的机器类 —— 把 GTO {@code StellarForgeMachine} 的
 * <b>运行模式</b>搬到本包（结构/配方表/换壳仍走 gtlcore 原机的那一套）。
 *
 * <h2>来源与口径</h2>
 * <p>GTO 源码：{@code gtocore/common/machine/multiblock/electric/StellarForgeMachine.java}
 * （{@code gtocore-forge-1.20.1-26.10.1.jar} 字节码同形）。它的三条可见机制：
 * <ol>
 *   <li><b>配方等级不受能源仓限制</b>：{@code onStructureFormed()} 里把 {@code tier} 从
 *       「能源仓电压档」改成「恒星热力容器（SCTier）档 → 12/13/14（UXV/OpV/MAX）」。
 *       原版 {@code WorkableElectricMultiblockMachine.onStructureFormed()} 会把 tier 设成
 *       {@code floorTierByVoltage(getMaxVoltage())}（= 能源仓限制），本类在 {@code super} 之后覆盖它。</li>
 *   <li><b>连续运行优化</b>：首次运行之后的<b>后续配方时长减半</b>（{@code duration / 2}，至少 1t）。
 *       GTO 在 {@code getRealRecipe} 里对 {@code consecutiveRecipes > 1} 做 {@code duration / 2}；
 *       本包 GTCEu 1.4.4 的 {@code RecipeLogic.setupRecipe()} 是「先 {@code machine.beforeWorking(recipe)}
 *       再 {@code this.duration = recipe.duration}」⇒ 在 {@code beforeWorking} 里改 {@code recipe.duration}
 *       等价且安全（此刻 recipe 已是 {@code match.copy()} 的副本，不会污染配方表）。</li>
 *   <li><b>运行中供电不足 → 巨大爆炸</b>：GTO 在 {@code regressRecipe()} 里
 *       {@code setWorkingEnabled(false) + resetRecipeLogic() + doExplosion(1.0f)}（自定义球形爆炸半径 100）。
 *       本包没有 gtolib 的 regress 钩子，改为在配方逻辑进入 WAITING（供电不足就会 WAITING）且
 *       <b>已有进度</b>（{@code progress > 0}，即正在运行中掉电）时触发
 *       {@link IExplosionMachine#doExplosion(float)}（威力 20，GTCEu 口径，量级远大于 TNT）。</li>
 * </ol>
 *
 * <p><b>不移植的部分</b>：GTO 的「魔法模块」（{@code ManaTrait} / {@code MAGIC_MODULE} 结构件 /
 * 产魔配方 / 内部压力显示）—— 玩家前面已明确「不用管它的模块」。因此本类没有
 * {@code IManaMultiblock}，也没有 mana NBT。
 *
 * <p><b>持久化</b>：GTO 的 {@code consecutiveRecipes} 用 {@code @SaveToDisk} 存盘；本类只放内存
 * （拆结构 / 掉线重进会从 0 重新计）。这是本移植唯一的已知简化，记录在部署记录里。
 */
public class StellarForgeMachine extends TierCasingMachine implements IExplosionMachine {

    /** 爆炸威力（GTCEu {@code Level#explode} 的 power；TNT = 4）。 */
    private static final float EXPLOSION_POWER = 20.0F;

    /** 连续配方计数：>1 的配方时长减半（与 GTO {@code consecutiveRecipes} 同义）。 */
    private int consecutiveRecipes;

    /** 爆炸只触发一次，避免 doExplosion 之后同 tick 又被 WAITING 重入。 */
    private boolean exploded;

    public StellarForgeMachine(IMachineBlockEntity holder) {
        // "SCTier" = gtlcore 恒星热力容器三档判定写进 match context 的 key（结构 M 位用的就是它）。
        super(holder, "SCTier");
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        // 覆盖父类的「能源仓决定 tier」：改为 SCTier 档位决定（GTO: 3→MAX(14) / 2→OpV(13) / 其余→UXV(12)）。
        this.tier = switch (this.getCasingTier()) {
            case 3 -> GTValues.MAX;
            case 2 -> GTValues.OpV;
            default -> GTValues.UXV;
        };
        this.exploded = false;
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        this.consecutiveRecipes = 0;
        this.exploded = false;
    }

    @Override
    public boolean beforeWorking(@Nullable GTRecipe recipe) {
        if (!super.beforeWorking(recipe)) {
            return false;
        }
        if (recipe != null) {
            this.consecutiveRecipes++;
            if (this.consecutiveRecipes > 1) {
                recipe.duration = Math.max(1, recipe.duration / 2);
            }
        }
        return true;
    }

    @Override
    public void onWaiting() {
        super.onWaiting();
        if (this.exploded || !this.isFormed() || this.getLevel() == null || this.getLevel().isClientSide()) {
            return;
        }
        RecipeLogic logic = this.getRecipeLogic();
        // progress > 0 ⇒ 这条配方已经跑起来了（不是「从没供上电」），此时掉电才炸。
        if (logic == null || logic.getProgress() <= 0) {
            return;
        }
        EnergyContainerList energy = this.getEnergyContainer();
        if (energy == null || energy.getEnergyStored() <= 0L) {
            this.exploded = true;
            logic.setWorkingEnabled(false);
            logic.resetRecipeLogic();
            GTLModular.LOGGER.warn("[gtlmodular] 恒星炎炀锻炉运行中供电不足 ⇒ 爆炸一次（{}，pos {}）",
                    this.getDefinition().getId(), this.getPos());
            this.doExplosion(EXPLOSION_POWER);
        }
    }

    @Override
    public void addDisplayText(@NotNull List<Component> textList) {
        super.addDisplayText(textList);
        if (this.isFormed()) {
            textList.add(Component.translatable("gtlmodular.machine.stellar_forge.display.tier",
                    this.getCasingTier()));
        }
    }
}
