package com.gtlmodular.common.machine.nanochip.display;

import com.gtlmodular.common.machine.nanochip.registration.NanochipModuleSpec;
import com.gtlmodular.common.machine.nanochip.registration.NanochipModules;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine;
import com.gtlmodular.common.machine.nanochip.NanochipModuleKind;
import com.gtlmodular.common.machine.nanochip.NanochipModuleMachine;
import com.gtlmodular.common.machine.nanochip.NanochipModuleUpkeep;
import com.gtlmodular.common.machine.nanochip.NanochipSplitterRules;
import com.gtlmodular.common.machine.nanochip.NanochipStructureRefresh;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class NanochipModuleDisplay {

    private final NanochipModuleMachine machine;
    private final BooleanSupplier syncedHostConnected;
    private final Supplier<String> syncedHostInfo;
    private final BooleanSupplier syncedDrawingFromHost;

    public NanochipModuleDisplay(NanochipModuleMachine machine, BooleanSupplier syncedHostConnected,
                                 Supplier<String> syncedHostInfo, BooleanSupplier syncedDrawingFromHost) {
        this.machine = machine;
        this.syncedHostConnected = syncedHostConnected;
        this.syncedHostInfo = syncedHostInfo;
        this.syncedDrawingFromHost = syncedDrawingFromHost;
    }

    public void addDisplayText(List<Component> textList, NanochipModuleKind kind) {
        addDisplayText(textList, NanochipModules.builtin(kind));
    }

    public void addDisplayText(List<Component> textList, NanochipModuleSpec kind) {
        textList.add(Component.literal("NAC 模块: ")
                .append(Component.literal(kind.nameZh()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" · " + kind.nameEn()).withStyle(ChatFormatting.DARK_GRAY))
                .withStyle(ChatFormatting.GRAY));
        textList.add(Component.translatable("gtlmodular.nanochip.module.ui.role")
                .append(Component.translatable(kind.langKey("role")).withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GRAY));
        textList.add(Component.literal("复合体连接: ")
                .append(Component.literal(syncedHostConnected.getAsBoolean() ? "已连接" : "未连接")
                        .withStyle(syncedHostConnected.getAsBoolean() ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(Component.literal(syncedHostInfo.get()).withStyle(ChatFormatting.GRAY))
                .withStyle(ChatFormatting.GRAY));
        boolean fromHost = syncedDrawingFromHost.getAsBoolean();
        textList.add(Component.literal("供电: ")
                .append(Component.literal(fromHost ? "复合体中央供电" : "本机能源仓，或未成形")
                        .withStyle(fromHost ? ChatFormatting.AQUA : ChatFormatting.GRAY))
                .append(Component.literal(fromHost && kind.energyPriority() < 0
                        ? " · 最低优先级：别的模块等电时让电" : "")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .withStyle(ChatFormatting.GRAY));
        if (fromHost) {
            long cap = machine.getBufferCapacityEU();
            long cur = machine.getBufferEU();
            textList.add(Component.literal("能量缓存: ")
                    .append(Component.literal(cap > 0L
                                    ? com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(cur)
                                      + " / " + com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(cap) + " EU"
                                    : "等主机设定容量")
                            .withStyle(cap > 0L ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY))
                    .append(Component.literal(" · 优先级 " + (kind.energyPriority() == -1 ? "-1：最后补" : kind.energyPriority() == 1 ? "1" : Integer.toString(kind.energyPriority())))
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (kind.hasUpkeep()) {
            // 净化水支持多个等级，耗材说明不能只显示种类参数中的一级水。
            boolean waterFamily = NanochipModuleUpkeep.isPurifiedWaterFamily(kind.upkeepFluidId());
            textList.add(Component.literal("运行耗材: ")
                    .append(Component.literal(kind.upkeepMbPerSecond() + " mB/s ").withStyle(ChatFormatting.GOLD))
                    .append((waterFamily
                            ? Component.translatable("gtlmodular.nanochip.module.upkeep.water_any_tier")
                            : NanochipModuleUpkeep.fluidDisplayName(kind.upkeepFluidId(), kind.upkeepFluidId()))
                            .copy().withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(kind.features().biologicalExemption()
                            ? " · 湿件/生物满校准可豁免" : " · 从本机流体输入仓扣，检测到哪一级用哪一级")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (kind.isRouter()) {
            textList.add(Component.literal("分流规则: ")
                    .append(Component.literal(machine.splitterRules().rules().size() + " / "
                            + NanochipSplitterRules.MAX_RULES).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" · 打开本仓 GUI 左侧「分流规则」页编辑；红石阈值读本机四周信号")
                            .withStyle(ChatFormatting.GRAY))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (kind.features().board()) {
            String level = machine.impurity() <= 15.0D ? "省电" : (machine.impurity() < 65.0D ? "正常" : "多耗电");
            textList.add(Component.literal("浸泡液杂质: ")
                    .append(Component.literal(String.format(java.util.Locale.ROOT, "%.2f%%", machine.impurity()))
                            .withStyle(machine.impurity() <= 15.0D ? ChatFormatting.GREEN
                                    : (machine.impurity() < 65.0D ? ChatFormatting.YELLOW : ChatFormatting.RED)))
                    .append(Component.literal(" · " + level + " · EU ×"
                            + String.format(java.util.Locale.ROOT, "%.3f", machine.impurityEuMultiplier()))
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" 杂质重置阈值 " + machine.autoFlushThreshold() + "%")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (kind.isWaterScaledOrganizer()) {
            int grade = machine.highestPurifiedWaterGrade();
            textList.add(Component.literal("净化水等级: ")
                    .append(Component.literal(grade <= 0 ? "未供水" : grade + " 级")
                            .withStyle(grade <= 0 ? ChatFormatting.RED : ChatFormatting.AQUA))
                    .append(Component.literal(grade <= 0
                            ? " · 本模块不给加成，需供 1~16 级净化水"
                            : " · 加成强度 ×" + String.format(java.util.Locale.ROOT, "%.2f", machine.waterStrength()))
                            .withStyle(ChatFormatting.GRAY))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (kind.features().laser()) {
            int laserTier = machine.laserHatchTier();
            textList.add(Component.literal("激光源仓: ")
                    .append(Component.literal(laserTier <= 0 ? "未安装" : "等级 " + laserTier
                            + " · " + machine.laserAmperage() + " A")
                            .withStyle(laserTier <= 0 ? ChatFormatting.DARK_GRAY : ChatFormatting.AQUA))
                    .append(Component.literal(" · 晶体满校准后：EU ÷ 每 256A 一档，时长 ÷ 等级−9")
                            .withStyle(ChatFormatting.GRAY))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** 附加配置页保留父类机器界面，避免覆盖模式和进度控件。 */
    public void attachConfigurators(ConfiguratorPanel panel) {
        panel.attachConfigurators(new NanochipStructureRefresh.RefreshConfigurator(machine));
    }

    public static String hostInfo(NanochipAssemblyComplexMachine h, boolean connected) {
        if (connected && h != null) {
            return " · " + (h.fullMode() ? "全流程档" : "直通档")
                    + " · 已识别 " + h.formedModuleCount() + " 个模块";
        } else if (h != null) {
            return "复合体未成形，或尚未把本机登记进模块集合";
        } else {
            return "尚未被复合体登记；复合体成形后会自动登记";
        }
    }
}
