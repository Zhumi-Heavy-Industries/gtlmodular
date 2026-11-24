package com.gtlmodular.common.machine.stellar;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IExplosionMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
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
 *       「能源仓电压档」改成「恒星热力容器（SCTier）档 → 12/13/14（UXV/OpV/MAX）}」。</li>
 *   <li><b>连续运行优化</b>：首次运行之后的<b>后续配方时长减半</b>（{@code duration / 2}，至少 1t）。</li>
 *   <li><b>运行中供电不足 → 巨大爆炸</b>：在配方逻辑进入 WAITING 且<b>已有进度</b>时触发
 *       {@link IExplosionMachine#doExplosion(float)}（威力 20）。</li>
 * </ol>
 *
 * <h2>★ 2026-11-06：机器模式（配方表切换）走 GTCEu 标准做法</h2>
 * <p>「魔力生成」不是自定义开关，而是把 <b>第二张配方表</b>加进机器定义
 * （{@code StellarForgeDefinitionSetup} 里 {@code definition.setRecipeTypes(...)}：
 * {@code gtceu:stellar_forge} + {@code gtceu:magic_manufacturer}）。
 * GTCEu 的 {@code IFancyUIMachine#getSubTabs()} 会在 {@code getRecipeTypes().length > 1} 时
 * 自动挂上标准「机器模式」子页（{@code MachineModeFancyConfigurator}），
 * 玩家在那一页点选配方表即可；切换走 {@code WorkableMultiblockMachine#setActiveRecipeType(int)}
 * （字段 {@code activeRecipeType} 自带 {@code @Persisted}，存盘/重进保留）。
 *
 * <p><b>不移植的部分</b>：GTO 的「魔法模块」（{@code ManaTrait} / {@code MAGIC_MODULE} 结构件 /
 * 内部压力显示）—— 玩家已明确「不用管它的模块」。
 *
 * <p><b>持久化</b>：GTO 的 {@code consecutiveRecipes} 用 {@code @SaveToDisk} 存盘；本类只放内存
 * （拆结构 / 掉线重进会从 0 重新计）。
 */
public class StellarForgeMachine extends TierCasingMachine implements IExplosionMachine {

    /** 爆炸威力（GTCEu {@code Level#explode} 的 power；TNT = 4）。 */
    private static final float EXPLOSION_POWER = 20.0F;

    /** 连续配方计数：>1 的配方时长减半（与 GTO {@code consecutiveRecipes} 同义）。 */
    private int consecutiveRecipes;

    /** 爆炸只触发一次，避免 doExplosion 之后同 tick 又被 WAITING 重入。 */
    private boolean exploded;

    /** 魔力生成模式诊断：每 100 tick 打一条，最多 6 条（排查「配方不输出」用）。 */
    private TickableSubscription manaDiagSubscription;
    private int manaDiagTicks;
    private int manaDiagLogs;

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

    // ------------------------------------------------------------------
    // 魔力生成模式诊断（2026-11-06：玩家反馈「不会输出」，用于定位是配方没匹配还是仓/电压问题）
    // ------------------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        if (!this.isRemote()) {
            this.manaDiagSubscription = this.subscribeServerTick(this::manaDiagTick);
        }
    }

    @Override
    public void onUnload() {
        if (this.manaDiagSubscription != null) {
            this.manaDiagSubscription.unsubscribe();
            this.manaDiagSubscription = null;
        }
        super.onUnload();
    }

    private void manaDiagTick() {
        if (this.manaDiagLogs >= 6 || !this.isFormed() || this.getRecipeLogic() == null) {
            return;
        }
        GTRecipeType[] types = this.getRecipeTypes();
        if (types == null || types.length < 2 || this.getActiveRecipeType() != 1) {
            return;
        }
        if (++this.manaDiagTicks % 100 != 0) {
            return;
        }
        this.manaDiagLogs++;
        // gtlcore 自己的「仓室句柄」体系（ME 输出总成走这里，不走 vanilla capabilitiesProxy）
        try {
            if (this instanceof org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine rhp) {
                java.util.List<org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart> normalOut =
                        rhp.getNormalRecipeHandlePart(com.gregtechceu.gtceu.api.capability.recipe.IO.OUT);
                java.util.List<org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart> normalIn =
                        rhp.getNormalRecipeHandlePart(com.gregtechceu.gtceu.api.capability.recipe.IO.IN);
                java.util.List<?> meOut = rhp.getMEOutputRecipeHandleParts();
                java.util.List<?> mePattern = rhp.getMEPatternRecipeHandleParts();
                StringBuilder partIds = new StringBuilder();
                for (com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart part : this.getParts()) {
                    try {
                        partIds.append(part.self().getDefinition().getId()).append(',');
                    } catch (Throwable ignored) {
                        partIds.append("?,");
                    }
                }
                GTLModular.LOGGER.info(
                        "[gtlmodular][魔力生成诊断] gtlcore句柄: normalIn={} normalOut={} meOut={} mePattern={} shared={} 部件=[{}]",
                        normalIn == null ? 0 : normalIn.size(), normalOut == null ? 0 : normalOut.size(),
                        meOut == null ? 0 : meOut.size(), mePattern == null ? 0 : mePattern.size(),
                        rhp.getSharedRecipeHandlePart() != null, partIds);
            }
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular][魔力生成诊断] gtlcore 句柄采集失败", failure);
        }
        try {
            RecipeLogic logic = this.getRecipeLogic();
            int matched = 0;
            String firstRecipe = "-";
            try {
                var iterator = this.getRecipeType().searchRecipe(this);
                while (iterator != null && iterator.hasNext() && matched < 32) {
                    GTRecipe recipe = iterator.next();
                    if (matched == 0) {
                        firstRecipe = String.valueOf(recipe.getId());
                    }
                    matched++;
                }
            } catch (Throwable ignored) {
                // 搜索失败也照常打印下面的信息
            }
            var table = this.getCapabilitiesProxy();
            java.util.List<IRecipeHandler<?>> itemInHandlers = table == null ? null
                    : table.get(IO.IN, ItemRecipeCapability.CAP);
            int itemIn = handlerCount(itemInHandlers);
            int itemOut = handlerCount(table == null ? null : table.get(IO.OUT, ItemRecipeCapability.CAP))
                    + handlerCount(table == null ? null : table.get(IO.BOTH, ItemRecipeCapability.CAP));
            int fluidIn = handlerCount(table == null ? null : table.get(IO.IN, FluidRecipeCapability.CAP));
            int fluidOut = handlerCount(table == null ? null : table.get(IO.OUT, FluidRecipeCapability.CAP))
                    + handlerCount(table == null ? null : table.get(IO.BOTH, FluidRecipeCapability.CAP));
            long stored = 0L;
            EnergyContainerList energy = this.getEnergyContainer();
            if (energy != null) {
                stored = energy.getEnergyStored();
            }
            GTLModular.LOGGER.info(
                    "[gtlmodular][魔力生成诊断] status={} type={} matchedRecipes={} first={} maxV={} stored={} "
                            + "itemIn={} itemOut={} fluidIn={} fluidOut={} 输入总线内容={} pos={}",
                    logic.getStatus(), this.getRecipeType().registryName, matched, firstRecipe,
                    this.getMaxVoltage(), stored, itemIn, itemOut, fluidIn, fluidOut,
                    describeContents(itemInHandlers), this.getPos());
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular][魔力生成诊断] 采集失败", failure);
        }
    }


    /** 把输入总线里的前几格内容拼成一行（诊断用）。 */
    private static String describeContents(java.util.List<IRecipeHandler<?>> handlers) {
        if (handlers == null || handlers.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        int shown = 0;
        for (IRecipeHandler<?> handler : handlers) {
            java.util.List<Object> contents;
            try {
                contents = handler.getContents();
            } catch (Throwable ignored) {
                continue;
            }
            if (contents == null) {
                continue;
            }
            for (Object content : contents) {
                if (shown >= 8) {
                    builder.append("…");
                    return builder.toString();
                }
                String text = content instanceof net.minecraft.world.item.ItemStack stack
                        ? (stack.isEmpty() ? "空" : stack.getCount() + "x" + stack.getHoverName().getString())
                        : String.valueOf(content);
                builder.append(text).append(';');
                shown++;
            }
        }
        return builder.length() == 0 ? "-" : builder.toString();
    }

    private static int handlerCount(java.util.List<IRecipeHandler<?>> handlers) {
        return handlers == null ? 0 : handlers.size();
    }
}
