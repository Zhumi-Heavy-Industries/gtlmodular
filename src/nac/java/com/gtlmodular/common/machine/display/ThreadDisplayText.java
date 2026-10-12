package com.gtlmodular.common.machine.display;

import com.gregtechceu.gtceu.utils.FormattingUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 两个界面共用格式与状态语义，避免客户端再推算并行、倍率或付款。 */
public final class ThreadDisplayText {
    private static final String PREFIX = "gtlmodular.nanochip.thread_details.";

    private ThreadDisplayText() {}

    public static List<Component> summary(ThreadDisplaySnapshot snapshot) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gtlmodular.nanochip.threads.status",
                number(snapshot.limit()), number(snapshot.running())));
        if (snapshot.running() != null && snapshot.running() > 0) {
            lines.add(Component.translatable("gtlmodular.nanochip.threads.runs", number(snapshot.totalRuns())));
        }
        return lines;
    }

    public static List<Component> lines(ThreadDisplaySnapshot.Thread thread) {
        Component row = Component.translatable(PREFIX + "row", number(thread.label()),
                number(thread.euPerTick()), number(thread.parallels())).withStyle(ChatFormatting.GRAY);
        Component timing = Component.translatable(PREFIX + "timing", number(thread.duration()),
                seconds(thread.duration()), number(thread.progress()), number(thread.duration()))
                .withStyle(ChatFormatting.GRAY);
        Component state = switch (thread.state()) {
            case WORKING -> Component.translatable(PREFIX + "working").withStyle(ChatFormatting.GREEN);
            case WAITING -> Component.translatable(PREFIX + "waiting", thread.waitingReason() == null
                    ? unknown() : Component.translatable(thread.waitingReason())).withStyle(ChatFormatting.YELLOW);
            case UNKNOWN -> unknown();
        };
        return List.of(row, timing, state);
    }

    public static Component progressTime(ThreadDisplaySnapshot.Thread thread) {
        return Component.translatable("gtlmodular.nanochip.threads.bar",
                seconds(thread.progress()), seconds(thread.duration()));
    }

    public static void appendThreads(List<Component> lines, ThreadDisplaySnapshot snapshot, int limit) {
        int shown = Math.min(Math.max(0, limit), snapshot.threads().size());
        for (int i = 0; i < shown; i++) {
            lines.addAll(lines(snapshot.threads().get(i)));
        }
        if (snapshot.running() != null && snapshot.running() > shown) {
            lines.add(more(snapshot.running() - shown));
        }
    }

    public static Component more(int count) {
        return Component.translatable(PREFIX + "more", number(count));
    }

    public static Component number(Number value) {
        return value == null ? unknown()
                : Component.literal(FormattingUtil.formatNumbers(value.longValue())).withStyle(ChatFormatting.AQUA);
    }

    public static Component seconds(Integer ticks) {
        return ticks == null ? unknown() : Component.literal(BigDecimal.valueOf(ticks)
                .divide(BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString()).withStyle(ChatFormatting.AQUA);
    }

    public static Component unknown() {
        return Component.translatable(PREFIX + "unknown").withStyle(ChatFormatting.GRAY);
    }
}
