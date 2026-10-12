package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 同一结构错误会在多台机器上反复出现，按结论和原因折叠日志以降低写盘开销。 */
public final class NanochipStructureLogGate {

    public static final long UNCHANGED_LOG_INTERVAL_TICKS = 200L;

    private static final int SAMPLES_PER_CONCLUSION = 2;

    private static final Map<String, Integer> PENDING_COUNTS = new LinkedHashMap<>();

    private static final Map<String, List<String>> PENDING_SAMPLES = new LinkedHashMap<>();

    private static boolean verboseNoticeLogged;

    /** 时间差参与限流，不能使用会使减法溢出的 Long.MIN_VALUE 哨兵。 */
    private static long lastSummaryTick = -10_000L;

    private String lastConclusion;

    private String lastDetail;

    private long lastInfoTick = -10_000L;

    public synchronized boolean shouldEmitInfo(String conclusion, String detail, String label, long nowTick) {
        if (NanochipModContext.config().isNanochipVerboseStructureLogs()) {
            if (!verboseNoticeLogged) {
                verboseNoticeLogged = true;
                NanochipModContext.logger().info("[gtlmodular] NAC 结构日志已进入「全打」模式（配置 nanochipComplex.verboseStructureLogs=true）："
                        + "结构结论与逐格原因一律 INFO、不折叠、不降 debug；"
                        + "排障结束后把该项改成 false（在游戏内配置界面「NAC 校准与排障」里点一下、保存即生效，无需重启；只有手改 config/gtlmodular-common.toml 才要重启）即可恢复降噪。");
            }
            this.lastConclusion = conclusion;
            this.lastDetail = detail;
            this.lastInfoTick = nowTick;
            return true;
        }
        boolean changed = this.lastConclusion == null
                || !this.lastConclusion.equals(conclusion)
                || !Objects.equals(this.lastDetail, detail);
        this.lastConclusion = conclusion;
        this.lastDetail = detail;
        if (!changed && nowTick - this.lastInfoTick < UNCHANGED_LOG_INTERVAL_TICKS) {
            return false;
        }
        this.lastInfoTick = nowTick;
        if (changed) {
            // 状态或原因变化必须立即输出，避免折叠掩盖新的故障。
            emitSummaryIfDue(nowTick);
            return true;
        }
        fold(conclusion, label, nowTick);
        return false;
    }

    /** 重载后清空本机记忆，使本次会话的第一条结构状态可见。 */
    public synchronized void reset() {
        this.lastConclusion = null;
        this.lastDetail = null;
        this.lastInfoTick = -10_000L;
    }

    private static synchronized void fold(String conclusion, String label, long nowTick) {
        PENDING_COUNTS.merge(conclusion, 1, Integer::sum);
        List<String> samples = PENDING_SAMPLES.computeIfAbsent(conclusion, k -> new ArrayList<>());
        if (samples.size() < SAMPLES_PER_CONCLUSION) {
            samples.add(label);
        }
        emitSummaryIfDue(nowTick);
    }

    private static synchronized void emitSummaryIfDue(long nowTick) {
        if (PENDING_COUNTS.isEmpty()) {
            return;
        }
        if (nowTick - lastSummaryTick < UNCHANGED_LOG_INTERVAL_TICKS) {
            return;
        }
        lastSummaryTick = nowTick;
        StringBuilder sb = new StringBuilder();
        int total = 0;
        for (Map.Entry<String, Integer> entry : PENDING_COUNTS.entrySet()) {
            int count = entry.getValue();
            total += count;
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(entry.getKey()).append(' ').append(count).append(" 台");
            List<String> samples = PENDING_SAMPLES.get(entry.getKey());
            if (samples != null && !samples.isEmpty()) {
                sb.append("（例：").append(String.join("、", samples));
                if (count > samples.size()) {
                    sb.append("；另有 ").append(count - samples.size()).append(" 台同类结论已折叠");
                }
                sb.append('）');
            }
        }
        NanochipModContext.logger().info("[gtlmodular] NAC 结构结论未变汇总（最近 {} 秒，{} 台次重复结论已折叠成这一条）：{}",
                UNCHANGED_LOG_INTERVAL_TICKS / 20L, total, sb);
        PENDING_COUNTS.clear();
        PENDING_SAMPLES.clear();
    }

    public static synchronized int pendingFoldedCount() {
        int total = 0;
        for (int n : PENDING_COUNTS.values()) {
            total += n;
        }
        return total;
    }
}
