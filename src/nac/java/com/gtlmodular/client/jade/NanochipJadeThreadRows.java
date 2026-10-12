package com.gtlmodular.client.jade;

import com.gregtechceu.gtceu.utils.FormattingUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import snownee.jade.api.ITooltip;
import snownee.jade.api.ui.BoxStyle;

import java.util.Locale;

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
        int running = data.getInt(NanochipJadeKeys.KEY_THREAD_RUNNING);
        if (running <= 0) {
            return;
        }
        ListTag list = data.getList(NanochipJadeKeys.KEY_THREAD_LIST, Tag.TAG_COMPOUND);
        int mirrored = data.getInt(NanochipJadeKeys.KEY_MIRRORED_SLOT);
        int cap = Math.max(1, NanochipJadeKeys.DISPLAY_LINES - (nativeBarDrawn ? 1 : 0));
        if (nativeBarDrawn && NATIVE_BAR_CAPTION) {
            tooltip.add(Component.translatable(LANG_NATIVE_HEAD, mirrored,
                    FormattingUtil.formatNumbers(data.getLong(NanochipJadeKeys.KEY_MIRRORED_PARALLELS))));
        }
        int appended = 0;
        int labelIndex = nativeBarDrawn ? 1 : 0;
        for (int i = 0; i < list.size() && appended < cap; i++) {
            CompoundTag entry = list.getCompound(i);
            if (nativeBarDrawn && entry.getInt(NanochipJadeKeys.KEY_SLOT) == mirrored) {
                continue;
            }
            appendThreadLabel(tooltip, labelIndex);
            appendThreadBar(tooltip, entry);
            appended++;
            labelIndex++;
        }
        int shown = appended + (nativeBarDrawn ? 1 : 0);
        // 汇总取全部批次，不能把截断后的显示行数当作整机总量。
        appendEquivalenceLine(tooltip, running,
                data.getLong(NanochipJadeKeys.KEY_THREAD_RUNS));
        if (shown < running) {
            tooltip.add(Component.translatable(LANG_MORE_UNLISTED, running - shown));
        }
    }

    public static void appendAllRows(ITooltip tooltip, CompoundTag data) {
        if (tooltip == null || data == null) {
            return;
        }
        int running = data.getInt(NanochipJadeKeys.KEY_THREAD_RUNNING);
        if (running <= 0) {
            return;
        }
        ListTag list = data.getList(NanochipJadeKeys.KEY_THREAD_LIST, Tag.TAG_COMPOUND);
        int shown = 0;
        for (int i = 0; i < list.size() && shown < NanochipJadeKeys.DISPLAY_LINES; i++) {
            CompoundTag entry = list.getCompound(i);
            // 标签使用列表下标以保持连续，稳定编号仍供原生条归属判定使用。
            appendThreadLabel(tooltip, i);
            appendThreadBar(tooltip, entry);
            shown++;
        }
        appendEquivalenceLine(tooltip, running,
                data.getLong(NanochipJadeKeys.KEY_THREAD_RUNS));
        if (shown < running) {
            tooltip.add(Component.translatable(LANG_MORE_UNLISTED, running - shown));
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

    /** 条内只放时间以控制提示框宽度；不足一秒时保留小数，避免整条显示为零秒。 */
    public static void appendThreadBar(ITooltip tooltip, CompoundTag entry) {
        if (tooltip == null || entry == null) {
            return;
        }
        int slot = entry.getInt(NanochipJadeKeys.KEY_SLOT);
        int progress = entry.getInt(NanochipJadeKeys.KEY_PROGRESS);
        int duration = entry.getInt(NanochipJadeKeys.KEY_DURATION);
        long parallels = entry.getLong(NanochipJadeKeys.KEY_PARALLELS);
        float ratio = duration <= 0 ? 0.0F : (float) progress / (float) duration;
        if (ratio < 0.0F) {
            ratio = 0.0F;
        } else if (ratio > 1.0F) {
            ratio = 1.0F;
        }
        String progressText;
        String durationText;
        if (duration > TICKS_PER_SECOND) {
            progressText = Integer.toString(progress / TICKS_PER_SECOND);
            durationText = Integer.toString(duration / TICKS_PER_SECOND);
        } else {
            int divisor = duration > 0 ? duration : 1;
            progressText = String.format(Locale.ROOT, "%.1f", progress / (double) TICKS_PER_SECOND);
            durationText = String.format(Locale.ROOT, "%.1f", divisor / (double) TICKS_PER_SECOND);
        }
        Component barText = OVERLAY_TEXT
                ? Component.translatable(LANG_BAR, progressText, durationText)
                : Component.translatable(LANG_BAR_SHORT, slot);
        tooltip.add(tooltip.getElementHelper().progress(
                ratio,
                barText,
                tooltip.getElementHelper().progressStyle()
                        .color(BAR_ARGB)
                        .textColor(TEXT_ARGB),
                BoxStyle.DEFAULT,
                true));
        appendThreadParallel(tooltip, parallels);
        if (!OVERLAY_TEXT) {
            tooltip.append(Component.translatable("gtlmodular.nanochip.threads.line",
                    slot, progress, duration, FormattingUtil.formatNumbers(parallels)));
        }
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

