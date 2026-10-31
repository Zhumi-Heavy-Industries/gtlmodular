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

/**
 * 模块A：熔岩高炉单元（M3 机器重做）
 *
 * <p>基类 gtlcore {@code CoilWorkableElectricMultipleRecipesMachine}（继承 GTM 线圈机），
 * 并经本模组基座 {@link MoltenModuleMachineBase} 实现 gtlcore 模块语义（成形时连接
 * 「地心涌动能量转换枢纽」，结构失效/移除时注销）：
 * <ul>
 *   <li><b>并行</b>：由并行控制仓提供 —— 基类 getMaxParallel → gtlcore
 *       GTLRecipeModifiers.getHatchParallel（读仓内设定，无仓/未成形 = 1）；结构 X 位
 *       已允许 ParallelHatch 能力部件。</li>
 *   <li><b>线圈温度降耗 + 全局加速</b>：成形后按线圈温度折算 EU/t 倍率（温度档公式见
 *       {@link CoilBoostLogic}），通过 {@code getRecipeLogic().setReduction(euFactor,
 *       durationMultiplier)} 注入 —— EU/t 只降不动耗时规则见配置：耗时倍率默认 0.1
 *       （配方耗时 ×0.1，与超级冶炼炉同款语义）；结构失效还原为 (1.0, 1.0)。</li>
 *   <li>EBF 温度/无损超频仍由定义级 ebfOverclock 修正器承担；双流体税由定义级回调实现。</li>
 * </ul>
 */
public class MoltenBlastModuleMachine extends MoltenModuleMachineBase<MoltenBlastModuleMachine>
        implements com.gtladd.gtladditions.api.machine.IThreadModifierMachine {

    /** gtlcore 多配方合并预算里的基础线程数（预算 = 并行 × 64）。 */
    public static final int BASE_THREADS = 64;

    /** 天球分歧引擎的线程倍率（本机口径：星阵基础线程再 ×20）。 */
    public static final int THREAD_MULTIPLIER = 20;

    /** 结构里的天球分歧引擎仓（由 {@code addedToController} 回调塞进来）。 */
    private IThreadModifierPart threadPart;

    @Override
    public IThreadModifierPart getThreadPartMachine() {
        return threadPart;
    }

    @Override
    public void setThreadPartMachine(IThreadModifierPart part) {
        this.threadPart = part;
    }

    /**
     * 天球分歧引擎提供的额外线程：{@code 星阵数 × 64 × 20}。
     *
     * <p>仓自身的 {@code getThreadCount()} 已经乘过 gtladditions 的策略倍率（本机不在它的表里，
     * 默认 ×2），所以先除掉策略倍率还原成「星阵数 × 64」，再按本机的 ×20 计。
     */
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

    /** 本机跨配方线程总数 = 基础 64 + 天球分歧引擎额外线程。 */
    public int totalThreads() {
        return BASE_THREADS + getAdditionalThread();
    }

    /**
     * 界面里的「跨配方线程」行是否由本类画。
     * 天基炼矿单元（模块C）覆写为 false —— 它自己画（它的线程由动力模块决定，
     * 这样机器界面与外部 Jade 显示同一口径）。
     */
    protected boolean showsThreadLine() {
        return getAdditionalThread() > 0;
    }

    /**
     * 把线程数折进 gtlcore 的合并预算（预算 = {@code getMaxParallel() × 64}）：
     * 需要 {@code 并行仓值 × 线程总数} 的预算，所以这里返回 {@code 并行仓值 × 线程总数 / 64}。
     */
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

    /**
     * 高炉单元自己的温度判定（不再借用 gtlcore 的 {@code EBF_CHECK}，把规则写死在自家代码里）：
     * <b>有效炉温 = 主机线圈温度 + 100 × max(0, 电压等级 − MV) ≥ 配方 ebf_temp</b>。
     * 不满足时照旧置 {@code FAIL_NO_ENOUGH_TEMPERATURE}（界面显示「温度不足」）。
     */
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

    /**
     * 物品说明（挂到 {@code MachineDefinition#setTooltipBuilder}）：<b>数字按当前配置现算</b>。
     *
     * <p>说明里这几项都能在 {@code config/gtlmodular-common.toml} 里改，写死会与实际不符：
     * <ul>
     *   <li>耗时倍率 → {@code coilDiscount.durationMultiplier}（默认 0.1）</li>
     *   <li>每秒双流体扣量 → {@code dualFluidTax.ivBaselineMbPerSecond}（默认 83 mB）</li>
     *   <li>每档电压的倍率 → {@code dualFluidTax.perTierMultiplier}（默认 4）</li>
     * </ul>
     * 流体名也按配置里的 id 现取（默认 岩浆 / 液态烈焰），配置改了说明跟着变。
     */
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
        // 结构失效：减免还原（下次成形会重新按新线圈折算）
        getRecipeLogic().setReduction(1.0, 1.0);
    }

    /** 成形后注入：EU/t 按主机线圈温度折算；耗时 × durationMultiplier（默认 0.1）。 */
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

    /**
     * 主机连接/主机重建（含玩家换了大楼线圈）后重新折算降耗 —— 模块自身没有线圈，
     * 温度全靠 {@code MoltenModuleMachineBase.getCoilType()} 代理主机，主机换线圈后必须重算，
     * 否则要拆结构才生效。
     */
    @Override
    public void onHostLinked() {
        if (isFormed()) {
            applyCoilDiscount();
        }
    }

    /**
     * 界面显示与「超级冶炼炉」保持一致：基类已画出「最大功率 / 最大配方等级 / 当前机器模式 /
     * 同时处理至多 N 个配方（装了并行仓时） / 状态 / 无锁定配方」，这里只补它特有的
     * <b>热容行</b>（{@code 热容：<线圈温度>K}，数字红色）。
     */
    @Override
    public void addDisplayText(List<Component> textList) {
        // 线圈/主机都可能在中途变化：每帧按当前主机线圈重算一次，界面上的热容与降耗永远跟手
        if (isFormed() && getRecipeLogic() != null) {
            applyCoilDiscount();
        }
        super.addDisplayText(textList);
        if (isFormed()) {
            addHeatCapacityLine(textList);
            // 装了天球分歧引擎才显示线程行（没装时界面与原来完全一致）；天基炼矿单元自己画
            if (showsThreadLine()) {
                textList.add(Component.translatable("gtladditions.multiblock.threads",
                        Component.literal(String.valueOf(totalThreads())).withStyle(ChatFormatting.GOLD))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    /** 热容行（超级冶炼炉同款文案与配色）+ 判定炉温（排查温度不足用）；天基炼矿单元覆写为空。 */
    protected void addHeatCapacityLine(List<Component> textList) {
        // 温度一律取主机同步值（客户端自己读的线圈可能还是旧结构判定的残留）
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
        // 判定炉温 = 线圈 + 每级电压 100K（超过 MV 后每级 +100）
        textList.add(Component.literal("判定炉温: ")
                .append(Component.literal(FormattingUtil.formatNumbers(effectiveTemperature()) + " K")
                        .withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GRAY));
        // 温度不足诊断：服务端真正用到的三个数（本机实读线圈 / 判定炉温 / 配方需求），
        // 用来区分「模块没读到主机线圈」和「配方确实更热」。
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
