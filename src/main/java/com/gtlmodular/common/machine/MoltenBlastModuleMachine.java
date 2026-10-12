package com.gtlmodular.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gtlmodular.common.config.GTLModularConfig;
import com.gtlmodular.common.machine.boost.CoilBoostLogic;
import com.gtladd.gtladditions.api.machine.feature.IThreadModifierPart;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;

import java.util.List;
import java.util.function.BiPredicate;

public class MoltenBlastModuleMachine extends MoltenModuleMachineBase<MoltenBlastModuleMachine>
        implements com.gtladd.gtladditions.api.machine.IThreadModifierMachine {

    public static final int BASE_THREADS = 64;

    public static final int THREAD_MULTIPLIER = 20;

    private IThreadModifierPart threadPart;

    @Override
    public IThreadModifierPart getThreadPartMachine() {
        return threadPart;
    }

    @Override
    public void setThreadPartMachine(IThreadModifierPart part) {
        this.threadPart = part;
    }

    /** 线程仓已乘过上游策略倍率，需先还原再应用本机倍率，避免重复放大。 */
    @Override
    public int getAdditionalThread() {
        if (threadPart == null) {
            return 0;
        }
        int base = threadPart.getThreadCount();
        int strategy = com.gtladd.gtladditions.utils.ThreadMultiplierStrategy.INSTANCE
                .getAdditionalMultiplier(getDefinition());
        int perArray = strategy > 0 ? base / strategy : base;
        return (int) Math.min(Integer.MAX_VALUE, (long) perArray * THREAD_MULTIPLIER);
    }

    public int totalThreads() {
        return BASE_THREADS + getAdditionalThread();
    }

    /** 炼矿单元自行显示线程数，避免父类重复添加同一行。 */
    protected boolean showsThreadLine() {
        return getAdditionalThread() > 0;
    }

    /** 上游合并预算按 getMaxParallel 乘基础线程数计算，需折入本机实际线程数。 */
    @Override
    public int getMaxParallel() {
        int hatchParallel = super.getMaxParallel();
        int threads = totalThreads();
        if (threads <= BASE_THREADS) {
            return hatchParallel;
        }
        long value = (long) hatchParallel * threads / BASE_THREADS;
        return (int) Math.min(Integer.MAX_VALUE, value);
    }

    private static final BiPredicate<CompoundTag, IRecipeLogicMachine> BLAST_TEMPERATURE_CHECK = (data, machine) -> {
        if (!(machine instanceof MoltenBlastModuleMachine blast) || !data.contains("ebf_temp")) {
            return true;
        }
        int required = data.getInt("ebf_temp");
        if (blast.effectiveTemperature() >= required) {
            return true;
        }
        RecipeResult.of(machine, RecipeResult.FAIL_NO_ENOUGH_TEMPERATURE);
        return false;
    };

    public MoltenBlastModuleMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    /** 说明数字和流体名从配置读取，避免玩家修改配置后提示仍显示默认值。 */
    public static void appendConfiguredTooltips(ItemStack stack, List<Component> lines) {
        lines.add(Component.translatable("gtlmodular.blast.tooltip.duration",
                Component.literal(trimNumber(GTLModularConfig.getCoilDiscountDurationMultiplier()))
                        .withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gtlmodular.blast.tooltip.temperature",
                Component.literal(String.valueOf(TEMPERATURE_PER_VOLTAGE_TIER))
                        .withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.GRAY));
        appendFluidTaxTooltip(lines);
        lines.add(Component.translatable("gtlmodular.blast.tooltip.cross_recipe")
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gtlmodular.blast.tooltip.parallel_hatch")
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gtlmodular.blast.tooltip.thread_hatch")
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gtlmodular.blast.tooltip.thread_multiplier",
                Component.literal(String.valueOf(THREAD_MULTIPLIER)).withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new MoltenModuleRecipeLogic(this, BLAST_TEMPERATURE_CHECK);
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        applyCoilDiscount();
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        getRecipeLogic().setReduction(1.0, 1.0);
    }

    private void applyCoilDiscount() {
        int coilTemp = hostCoilTemperature();
        double durationMultiplier = GTLModularConfig.getCoilDiscountDurationMultiplier();
        if (coilTemp <= 0) {
            getRecipeLogic().setReduction(1.0, durationMultiplier);
            return;
        }
        double euFactor = CoilBoostLogic.euFactorForTemperature(coilTemp);
        getRecipeLogic().setReduction(euFactor, durationMultiplier);
    }

    /** 模块没有自身线圈，主机连接或更换线圈后需要重新折算减免。 */
    @Override
    public void onHostLinked() {
        if (isFormed()) {
            applyCoilDiscount();
        }
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        if (isFormed() && getRecipeLogic() != null) {
            applyCoilDiscount();
        }
        super.addDisplayText(textList);
        if (isFormed()) {
            addHeatCapacityLine(textList);
            if (showsThreadLine()) {
                textList.add(Component.translatable("gtladditions.multiblock.threads",
                        Component.literal(String.valueOf(totalThreads())).withStyle(ChatFormatting.GOLD))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    protected void addHeatCapacityLine(List<Component> textList) {
        // 客户端结构缓存可能尚未更新，显示温度须取主机同步值。
        int coilTemp = hostCoilTemperature();
        if (coilTemp <= 0) {
            textList.add(Component.literal("热容: ")
                    .append(Component.literal("无线圈（主机未连接/未成形）").withStyle(ChatFormatting.RED))
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        textList.add(Component.translatable("gtceu.multiblock.blast_furnace.max_temperature",
                Component.literal(FormattingUtil.formatNumbers(coilTemp) + "K")
                        .withStyle(ChatFormatting.RED))
                .withStyle(ChatFormatting.GRAY));
        textList.add(Component.literal("判定炉温: ")
                .append(Component.literal(FormattingUtil.formatNumbers(effectiveTemperature()) + " K")
                        .withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GRAY));
        if (getLastTempFailRequired() != Integer.MIN_VALUE) {
            textList.add(Component.literal("温度判定: 本机实读线圈 ")
                    .append(Component.literal(FormattingUtil.formatNumbers(getLastTempFailCoilTemp()) + " K")
                            .withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" · 判定炉温 " + FormattingUtil.formatNumbers(getLastTempFailMax()) + " K")
                            .withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(getLastTempFailRequired() >= 0
                            ? " · 上次失败配方需 " + FormattingUtil.formatNumbers(getLastTempFailRequired()) + " K"
                            : " · 上次失败配方无 ebf_temp（非温度原因）")
                            .withStyle(ChatFormatting.RED))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
