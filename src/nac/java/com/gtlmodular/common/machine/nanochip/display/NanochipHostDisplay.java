package com.gtlmodular.common.machine.nanochip.display;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine;
import com.gtlmodular.common.machine.nanochip.NanochipCalibration;
import com.gtlmodular.common.machine.nanochip.NanochipCalibrationPanel;
import com.gtlmodular.common.machine.nanochip.NanochipMirrorBindGuard;
import com.gtlmodular.common.machine.nanochip.NanochipModuleKind;
import com.gtlmodular.common.machine.nanochip.NanochipStructureRefresh;
import com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongBinaryOperator;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine.FULL_PARALLEL_CAP;
import static com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine.LANG_PARALLEL_TOTAL;
import static com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine.LEGACY_CROSS_RECIPE_PARALLEL_CAP;
import static com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine.NAC_PER_THREAD_FULL_PARALLEL;
import static com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine.NAC_SATURATING_PARALLEL;
import static com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine.SHOW_STRUCTURE_REFRESH_BUTTON;
import static com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine.SHOW_VERBOSE_HOST_LINES;

/** 同步字段由机器持有；延迟读口让刷新后的显示继续使用同一份状态。 */
public final class NanochipHostDisplay {

    private final NanochipAssemblyComplexMachine host;
    private final NanochipMirrorBindGuard mirrorBindGuard;
    private final BooleanSupplier hasRegisteredParts;
    private final Runnable updateSyncedTexts;
    private final Consumer<List<Component>> gridAccountLine;
    private final LongBinaryOperator saturatingMul;
    private final Supplier<String> syncedMode;
    private final LongSupplier syncedParallelShown;
    private final Supplier<String> syncedVacuumText;

    private long lastUiRefreshTick = -1L;

    private static final String LANG_UPSTREAM_PARALLEL = "gtceu.multiblock.parallel";

    private static final String LANG_UPSTREAM_THREADS = "gtladditions.multiblock.threads";

    private static final String LANG_MAX_ENERGY_PER_TICK = "gtceu.multiblock.max_energy_per_tick";

    public NanochipHostDisplay(NanochipAssemblyComplexMachine host, NanochipMirrorBindGuard mirrorBindGuard,
                               BooleanSupplier hasRegisteredParts, Runnable updateSyncedTexts,
                               Consumer<List<Component>> gridAccountLine, LongBinaryOperator saturatingMul,
                               Supplier<String> syncedMode, LongSupplier syncedParallelShown,
                               Supplier<String> syncedVacuumText) {
        this.host = host;
        this.mirrorBindGuard = mirrorBindGuard;
        this.hasRegisteredParts = hasRegisteredParts;
        this.updateSyncedTexts = updateSyncedTexts;
        this.gridAccountLine = gridAccountLine;
        this.saturatingMul = saturatingMul;
        this.syncedMode = syncedMode;
        this.syncedParallelShown = syncedParallelShown;
        this.syncedVacuumText = syncedVacuumText;
    }

    /** 展示电网余额直接使用 BigInteger，避免容器的 long 截断掩盖真实余额。 */
    private void replaceMaxPowerWithGridTotal(List<Component> textList) {
        if (textList == null || !host.isFormed()) {
            return;
        }
        UUID user = host.gridPowerUser();
        if (user == null || !NanochipModContext.config().isNanochipGridPowerEnabled()) {
            return;
        }
        java.math.BigInteger total;
        try {
            total = WirelessEnergyManager.getUserEU(user);
        } catch (RuntimeException e) {
            return;
        }
        if (total == null) {
            return;
        }
        Object tierArg = null;
        int seenAt = -1;
        for (int i = 0; i < textList.size(); i++) {
            Component line = textList.get(i);
            if (line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                    && LANG_MAX_ENERGY_PER_TICK.equals(tc.getKey())) {
                Object[] args = tc.getArgs();
                if (args != null && args.length >= 2) {
                    tierArg = args[1];
                }
                seenAt = i;
                break;
            }
        }
        if (seenAt < 0) {
            return;
        }
        textList.remove(seenAt);
        // 语言键已带色码，再在 Java 中加样式会覆盖原有分段颜色。
        net.minecraft.network.chat.MutableComponent value = Component
                .literal(com.gtladd.gtladditions.utils.CommonUtils.formatBigIntegerFixed(total));
        net.minecraft.network.chat.MutableComponent row = tierArg == null
                ? Component.translatable(LANG_MAX_ENERGY_PER_TICK, value)
                : Component.translatable(LANG_MAX_ENERGY_PER_TICK, value, tierArg);
        textList.add(seenAt, row);
    }

    private void filterUpstreamParallelLines(List<Component> textList) {
        if (textList == null || !host.isFormed()) {
            return;
        }
        for (Iterator<Component> it = textList.iterator(); it.hasNext(); ) {
            Component line = it.next();
            if (line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
                if (LANG_UPSTREAM_PARALLEL.equals(tc.getKey())
                        || LANG_UPSTREAM_THREADS.equals(tc.getKey())) {
                    it.remove();
                }
            }
        }
        long threads = Math.max(1L, (long) host.getMultipleThreads());
        long threadsInUse = safeRunningThreadsForDisplay();
        long totalRuns = safeTotalRunsForDisplay();
        long perRecipe = Math.max(1L, totalRuns / threadsInUse);
        long total = saturatingMul.applyAsLong(threadsInUse, perRecipe);
        textList.add(parallelTotalLine(threadsInUse, perRecipe, total));
        textList.add(threadsLine(threads));
    }

    public void addParallelDisplay(List<Component> textList) {
        if (!host.isFormed()) {
            return;
        }
        long threads = Math.max(1L, (long) host.getMultipleThreads());
        long threadsInUse = safeRunningThreadsForDisplay();
        long totalRuns = safeTotalRunsForDisplay();
        long perRecipe = Math.max(1L, totalRuns / threadsInUse);
        long total = saturatingMul.applyAsLong(threadsInUse, perRecipe);
        final long gtoKinds = threads;
        final long parallelShown = totalRuns > 0L ? perRecipe : FULL_PARALLEL_CAP;
        int parallelAt = -1;
        int threadsAt = -1;
        for (int i = 0; i < textList.size(); i++) {
            Component line = textList.get(i);
            if (!(line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents)) {
                continue;
            }
            if (LANG_UPSTREAM_PARALLEL.equals(contents.getKey())) {
                parallelAt = i;
            } else if (LANG_UPSTREAM_THREADS.equals(contents.getKey())) {
                threadsAt = i;
            }
        }
        int insertAt;
        if (parallelAt >= 0 && threadsAt >= 0) {
            insertAt = Math.min(parallelAt, threadsAt);
            textList.remove(Math.max(parallelAt, threadsAt));
            textList.remove(Math.min(parallelAt, threadsAt));
        } else if (parallelAt >= 0 || threadsAt >= 0) {
            insertAt = parallelAt >= 0 ? parallelAt : threadsAt;
            textList.remove(insertAt);
        } else {
            insertAt = textList.size();
        }
        textList.add(insertAt, gtoThreadsLine(gtoKinds));
        textList.add(insertAt + 1, gtoPerRecipeLine(parallelShown));
        textList.add(insertAt + 2, gtoIndependentThreadLine());
    }

    private long safeRunningThreadsForDisplay() {
        try {
            RecipeLogic logic = host.getRecipeLogic();
            if (logic instanceof NanochipThreadedRecipeLogic threaded) {
                return Math.max(1L, (long) threaded.runningThreadCount());
            }
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 总量显示：在用线程数取不到，按 1 计：{}", e.toString());
        }
        return 1L;
    }

    private long safeTotalRunsForDisplay() {
        try {
            RecipeLogic logic = host.getRecipeLogic();
            if (logic instanceof NanochipThreadedRecipeLogic threaded) {
                return Math.max(0L, threaded.totalRunsAtOnce());
            }
        } catch (RuntimeException e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 总量显示：各线程并行之和取不到，按 0 计：{}", e.toString());
        }
        return 0L;
    }

    private static Component parallelTotalLine(long threadsInUse, long perRecipe, long total) {
        return Component.translatable(LANG_PARALLEL_TOTAL,
                        FormattingUtil.formatNumbers(threadsInUse),
                        FormattingUtil.formatNumbers(perRecipe),
                        FormattingUtil.formatNumbers(total))
                .withStyle(ChatFormatting.GRAY);
    }

    /** 颜色由语言键提供，避免 Java 样式覆盖参数的独立配色。 */
    private static Component gtoThreadsLine(long kinds) {
        return Component.translatable("gtlmodular.nanochip.parallel.kinds",
                FormattingUtil.formatNumbers(kinds));
    }

    private static Component gtoPerRecipeLine(long perRecipe) {
        return Component.translatable("gtlmodular.nanochip.parallel.per",
                FormattingUtil.formatNumbers(perRecipe));
    }

    private Component gtoIndependentThreadLine() {
        return Component.translatable(host.getMultipleThreads() > 1
                        ? "gtlmodular.nanochip.threads.independent.on"
                        : "gtlmodular.nanochip.threads.independent.off")
                .withStyle(ChatFormatting.GRAY);
    }

    private static Component threadsLine(long threads) {
        return Component.translatable(LANG_UPSTREAM_THREADS, FormattingUtil.formatNumbers(threads))
                .withStyle(ChatFormatting.GRAY);
    }

    /** 附加配置页保留父类机器界面，避免覆盖主页面的进度与模式控件。 */
    public void attachConfigurators(ConfiguratorPanel panel) {
        // 配置页在客户端绘制，只读同步字段才能与服务端校准判定一致。
        panel.attachConfigurators(new NanochipCalibrationPanel.Configurator(
                () -> NanochipCalibrationPanel.section(host::syncedHistoryProgress, host::syncedHistoryTotal,
                        host::syncedShareOf, host::syncedUsesFullWindowDenominator,
                        host::syncedActiveFamilyOrdinal, host::syncedCalibrationDenominator,
                        host::syncedFamilyCount,
                        host::syncedTierOf,
                        () -> host.clearCalibration())));
        if (SHOW_STRUCTURE_REFRESH_BUTTON) {
            panel.attachConfigurators(new NanochipStructureRefresh.RefreshConfigurator(host));
        }
    }

    public void addDisplayText(List<Component> textList) {
        NanochipLogisticsText.appendMode(textList, false);
        // 零件尚未恢复时电压快照不可靠，暂不展示等级与功率，避免误报降级。
        if (host.isFormed() && !hasRegisteredParts.getAsBoolean()) {
            replaceUnreadyTierDisplay(textList);
        }
        if (host.isFormed() && !hasRegisteredParts.getAsBoolean()) {
            textList.add(Component.literal("  · 结构部件未就绪（能源仓 / 样板总成尚未重新连接）")
                    .withStyle(ChatFormatting.YELLOW));
            textList.add(Component.literal("    最大配方等级 / 最大功率暂不显示（避免误读成降级）；"
                            + "请点左侧「立即更新结构」按钮，或拆放一次控制器")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (!host.isFormed()) {
            textList.add(Component.literal("纳米芯片组装复合体：结构未成形")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        replaceMaxPowerWithGridTotal(textList);
        addMirrorBindingDisplay(textList);
        // 界面只刷新同步文案，连接扫描由周期任务负责。
        if (host.getLevel() instanceof ServerLevel) {
            long t = host.getLevel().getGameTime();
            if (lastUiRefreshTick < 0 || t - lastUiRefreshTick >= 60) {
                lastUiRefreshTick = t;
                updateSyncedTexts.run();
            }
        }
        boolean full = "全流程档".equals(syncedMode.get());
        if (false) {
            textList.add(Component.literal("运行档位: ")
                    .append(Component.literal(syncedMode.get()).withStyle(full ? ChatFormatting.GREEN : ChatFormatting.YELLOW))
                    .append(Component.literal(full
                            ? " · 组装矩阵在线 · " + (NAC_PER_THREAD_FULL_PARALLEL
                                    ? "每条配方各自满并行 · 无总池摊分"
                                    : (NAC_SATURATING_PARALLEL
                                            ? "总池口径 · 并行上限饱和（int " + FULL_PARALLEL_CAP + "）"
                                            : "总池口径 · 旧硬压并行 钳 " + LEGACY_CROSS_RECIPE_PARALLEL_CAP))
                            + " · 时长下限 5 秒"
                            : " · 样板总成直通 · 并行上限 " + syncedParallelShown.getAsLong()
                                    + (NAC_PER_THREAD_FULL_PARALLEL ? "（去总池：实际按可容纳量收敛）" : "")
                                    + " · 时长下限 20 秒")
                            .withStyle(ChatFormatting.GRAY))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (SHOW_VERBOSE_HOST_LINES) {
            textList.add(Component.literal("  · 跨配方线程来源: ")
                    .append(Component.literal(full
                            ? "全流程档 2<<(电压等级-LV)（组装矩阵解锁）"
                            : "直通档基线（配置 nanochipComplex.directCrossRecipeThreads）")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(host.getThreadPartMachine() == null
                            ? " · 未装 Ω-天球分歧引擎"
                            : " · 已装 Ω-天球分歧引擎（追加 " + host.effectiveExtraThreads() + "）")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .withStyle(ChatFormatting.GRAY));
            if (!full) {
                textList.add(Component.literal("  · 装上「")
                        .append(Component.literal(NanochipModuleKind.ASSEMBLY_MATRIX.nameZh()).withStyle(ChatFormatting.GOLD))
                        .append(Component.literal("」模块即可解锁全流程档（每条配方各自满并行 + 跨配方线程）")
                                .withStyle(ChatFormatting.GRAY))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        textList.add(Component.literal("模块: "
                        + host.formedModuleCount() + " / " + host.getMaxModuleCount())
                .withStyle(ChatFormatting.WHITE));
        gridAccountLine.accept(textList);
        if (SHOW_VERBOSE_HOST_LINES) {
            textList.add(Component.literal("真空传送带: ")
                    .append(Component.literal(syncedVacuumText.get()).withStyle(ChatFormatting.LIGHT_PURPLE))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (SHOW_VERBOSE_HOST_LINES) {
            textList.add(NanochipLogisticsText.vacuumRouting());
        }
        if (SHOW_VERBOSE_HOST_LINES) {
            textList.add(Component.literal("中央供电: ")
                    .append(Component.literal(FormattingUtil.formatNumbers(host.getTotalStoredEU())
                                    + " / " + FormattingUtil.formatNumbers(host.getTotalCapacityEU()) + " EU")
                            .withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" · 模块实付 ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(FormattingUtil.formatNumbers(host.getModuleDrawPerTick()) + " EU/t")
                            .withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" · 模块自带能源仓时走自己的，没装就走这里")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (host.getLevel() instanceof ServerLevel) {
            textList.addAll(NanochipModuleBonusText.lines(host.moduleBonusSnapshot()));
        }
        // 频繁变化的线程进度放在面板末尾，便于持续查看。
        Object logicRef = host.getRecipeLogic();
        if (logicRef instanceof NanochipThreadedRecipeLogic threaded) {
            if (host.getLevel() instanceof ServerLevel) {
                textList.addAll(NanochipInputText.lines(threaded.inputDiagnosticSnapshot()));
            }
            threaded.appendThreadDisplayText(textList);
        }
    }

    /** 按语言键定位上游信息，避免依赖会随附加组件变化的行号。 */
    private void replaceUnreadyTierDisplay(List<Component> textList) {
        boolean tierLineSeen = false;
        for (Iterator<Component> it = textList.iterator(); it.hasNext(); ) {
            Component line = it.next();
            if (line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
                String key = tc.getKey();
                if ("gtceu.multiblock.max_recipe_tier".equals(key)) {
                    tierLineSeen = true;
                    it.remove();
                } else if ("gtceu.multiblock.max_energy_per_tick".equals(key)) {
                    it.remove();
                }
            }
        }
        if (tierLineSeen) {
            textList.add(Component.literal("最大配方等级：待定（能源仓未重连，部件就绪后自动恢复）")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private void addMirrorBindingDisplay(List<Component> textList) {
        if (!SHOW_VERBOSE_HOST_LINES) {
            return;
        }
        NanochipMirrorBindGuard.State st = NanochipMirrorBindGuard.readState(host);
        if (st == null) {
            textList.add(Component.translatable("gtlmodular.nanochip.mirror.unread")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (st.proxiesTotal() > 0) {
            textList.add(Component.translatable("gtlmodular.nanochip.mirror.count",
                    String.valueOf(st.proxiesTotal())).withStyle(ChatFormatting.GRAY));
            return;
        }
        if (st.lost()) {
            textList.add(Component.translatable("gtlmodular.nanochip.mirror.lost",
                            String.valueOf(st.recordedTotal()),
                            String.valueOf(mirrorBindGuard.attemptsForDiag()),
                            String.valueOf(NanochipMirrorBindGuard.MAX_CONSECUTIVE_FAILURES),
                            String.valueOf(NanochipMirrorBindGuard.MAX_CONSECUTIVE_FAILURES))
                    .withStyle(ChatFormatting.GRAY));
            textList.add(Component.translatable("gtlmodular.nanochip.mirror.lost.hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        textList.add(Component.translatable("gtlmodular.nanochip.mirror.none")
                .withStyle(ChatFormatting.GRAY));
    }

    public String modeText(boolean fullMode) {
        return fullMode ? "全流程档" : "直通档";
    }

    public String familyCounts(NanochipCalibration calibration) {
        StringBuilder counts = new StringBuilder();
        for (NanochipCalibration.Family family : NanochipCalibration.Family.values()) {
            counts.append(calibration.countOf(family)).append(',');
        }
        return counts.toString();
    }

    public String familyTiers(NanochipCalibration calibration) {
        // 族数量与档位必须采用同一 ordinal 顺序，否则面板会把档位显示到另一族。
        StringBuilder tiers = new StringBuilder();
        for (NanochipCalibration.Family family : NanochipCalibration.Family.values()) {
            tiers.append(calibration.tierOf(family)).append(',');
        }
        return tiers.toString();
    }

    public String moduleText() {
        StringBuilder sb = new StringBuilder();
        for (var kind : com.gtlmodular.common.machine.nanochip.registration.NanochipModules.all()) {
            int n = host.countModulesById(kind.id());
            if (n <= 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(kind.nameZh()).append(" ×").append(n);
        }
        return sb.length() == 0 ? "未安装模块" : sb.toString();
    }
}
