package com.gtlmodular.client.jade;

import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gtlmodular.common.machine.display.ThreadDisplaySnapshot;
import com.gtlmodular.common.machine.display.ThreadDisplayText;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import snownee.jade.api.ITooltip;
import snownee.jade.api.ui.BoxStyle;

import java.util.Objects;

public final class NanochipJadeThreadRows {

    /** 默认由 provider 自绘全部线程，避免多条显示完全依赖原生条的追加注入。 */
    public static final boolean OUR_OWN_BARS = true;

    /** 与 mixin 共用开关，避免原生条追加和 provider 自绘同时重复显示。 */
    public static final boolean NATIVE_BAR_FOR_FIRST_THREAD = true;

    /** 原生条不带线程编号与并行，需补说明才能对应到具体批次。 */
    public static final boolean NATIVE_BAR_CAPTION = true;

    public static final boolean OVERLAY_TEXT = true;

    public static final int BAR_ARGB = 0xFF4CBB17;

    /** 白字在深绿进度条上更易辨认。 */
    public static final int TEXT_ARGB = 0xFFFFFFFF;

    public static final int TICKS_PER_SECOND = 20;

    public static final String LANG_BAR = "gtlmodular.nanochip.threads.bar";

    /** 并行单独成行，避免与时间挤在条内而撑宽整个提示框。 */
    public static final String LANG_PARALLEL = "gtlmodular.nanochip.threads.parallel";

    public static final String LANG_THREAD_LABEL = "gtlmodular.nanochip.threads.bar.short";

    /** 保留兼容常量别名，让两条显示路径共用同一语言键。 */
    public static final String LANG_BAR_SHORT = LANG_THREAD_LABEL;

    public static final String LANG_MORE = "gtlmodular.nanochip.threads.more";

    public static final String LANG_MORE_UNLISTED = "gtlmodular.nanochip.threads.more.unlisted";

    public static final String LANG_MACHINE_EQUIV = "gtlmodular.nanochip.threads.machine_equiv";

    public static final String LANG_NATIVE_HEAD = "gtlmodular.nanochip.threads.native";

    private NanochipJadeThreadRows() {
    }

    /** 只有原生进度数据存在才跳过镜像批次，避免等待中的线程在两条路径都不显示。 */
    public static boolean hasNativeProgressKeys(CompoundTag data) {
        return data != null && (data.contains("Progress") || data.contains("MaxProgress"));
    }

    public static void appendAfterNativeBar(ITooltip tooltip, CompoundTag data, boolean nativeBarDrawn) {
        if (tooltip == null || data == null) {
            return;
        }
        ThreadDisplaySnapshot snapshot = NanochipJadeThreadData.read(data);
        int shown = 0;
        if (nativeBarDrawn) {
            for (ThreadDisplaySnapshot.Thread thread : snapshot.threads()) {
                if (thread.label() != null && Objects.equals(thread.label(), snapshot.mirroredLabel())) {
                    if (NATIVE_BAR_CAPTION) {
                        ThreadDisplayText.lines(thread).forEach(tooltip::add);
                    }
                    shown++;
                    break;
                }
            }
        }
        for (ThreadDisplaySnapshot.Thread thread : snapshot.threads()) {
            if (shown >= NanochipJadeKeys.DISPLAY_LINES) {
                break;
            }
            if (nativeBarDrawn && thread.label() != null
                    && Objects.equals(thread.label(), snapshot.mirroredLabel())) {
                continue;
            }
            appendThreadBar(tooltip, thread);
            shown++;
        }
        appendTail(tooltip, snapshot, shown);
    }

    public static void appendAllRows(ITooltip tooltip, CompoundTag data) {
        if (tooltip != null && data != null) {
            appendAllRows(tooltip, NanochipJadeThreadData.read(data));
        }
    }

    static void appendAllRows(ITooltip tooltip, ThreadDisplaySnapshot snapshot) {
        int shown = 0;
        for (ThreadDisplaySnapshot.Thread thread : snapshot.threads()) {
            if (shown >= NanochipJadeKeys.DISPLAY_LINES) {
                break;
            }
            appendThreadBar(tooltip, thread);
            shown++;
        }
        appendTail(tooltip, snapshot, shown);
    }

    private static void appendTail(ITooltip tooltip, ThreadDisplaySnapshot snapshot, int shown) {
        if (snapshot.running() != null && snapshot.totalRuns() != null) {
            appendEquivalenceLine(tooltip, snapshot.running(), snapshot.totalRuns());
        }
        if (snapshot.running() != null && shown < snapshot.running()) {
            tooltip.add(ThreadDisplayText.more(snapshot.running() - shown));
        }
    }

    /** 标签独占一行，避免编号与时间一起拉宽进度条。 */
    public static void appendThreadLabel(ITooltip tooltip, int displayIndex) {
        if (tooltip == null) {
            return;
        }
        tooltip.add(Component.translatable(LANG_THREAD_LABEL, displayIndex));
    }

    /** 等价并行按实际在用批次及各批次并行之和展示，不能用线程上限推算。 */
    public static void appendEquivalenceLine(ITooltip tooltip, int running, long totalRuns) {
        if (tooltip == null || running <= 0) {
            return;
        }
        tooltip.add(Component.translatable(LANG_MACHINE_EQUIV,
                running,
                running,
                FormattingUtil.formatNumbers(totalRuns)));
    }

    public static void appendThreadBar(ITooltip tooltip, CompoundTag entry) {
        if (tooltip != null && entry != null) {
            appendThreadBar(tooltip, NanochipJadeThreadData.readThread(entry));
        }
    }

    private static void appendThreadBar(ITooltip tooltip, ThreadDisplaySnapshot.Thread thread) {
        ThreadDisplayText.lines(thread).forEach(tooltip::add);
        if (thread.duration() == null || thread.progress() == null || thread.duration() <= 0) {
            return;
        }
        float ratio = Math.max(0.0F, Math.min(1.0F, (float) thread.progress() / thread.duration()));
        Component barText = OVERLAY_TEXT ? ThreadDisplayText.progressTime(thread)
                : Component.translatable(LANG_BAR_SHORT, ThreadDisplayText.number(thread.label()));
        int color = thread.state() == ThreadDisplaySnapshot.State.WORKING ? BAR_ARGB : 0xFFD9A441;
        tooltip.add(tooltip.getElementHelper().progress(ratio, barText,
                tooltip.getElementHelper().progressStyle().color(color).textColor(TEXT_ARGB),
                BoxStyle.DEFAULT, true));
    }

    /** 缺失或无效并行数据时省略该行，避免显示虚构的并行数。 */
    public static void appendThreadParallel(ITooltip tooltip, long parallels) {
        if (tooltip == null || parallels <= 0L) {
            return;
        }
        tooltip.add(Component.translatable(LANG_PARALLEL,
                FormattingUtil.formatNumbers(parallels)));
    }
}
