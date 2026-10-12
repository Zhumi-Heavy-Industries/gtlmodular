package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferProxyPartMachine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** 记录的镜像坐标与实际入链对象可能不同步，只对已有坐标调用上游绑定入口，并限制恢复次数。 */
public final class NanochipMirrorBindGuard {

    /** 默认启用恢复；关闭时 tick 直接返回，避免遍历或修改绑定状态。 */
    public static final boolean ENABLED = true;

    public static final int RETRY_INTERVAL_TICKS = 100;

    /** 限制总尝试次数，避免一整局反复重建无效绑定。 */
    public static final int MAX_ATTEMPTS = 8;

    /** 连续无效后停机，避免恢复过程反复震荡。 */
    public static final int MAX_CONSECUTIVE_FAILURES = 3;

    private static final int DIAG_MAX_BUFFERS = 4;

    private static final int DIAG_INTERVAL_TICKS = 600;

    private static final int DIAG_BUDGET = 16;

    private boolean warnedHalt;

    private int attempts;

    private int consecutiveFailures;

    private boolean halted;

    private int cooldown;

    private long lastAttemptTick = -1L;

    private String lastOutcome;

    private int diagCooldown;

    private int diagBudget = DIAG_BUDGET;

    private String diagLast;

    private NanochipMirrorBindGuard() {
    }

    public static NanochipMirrorBindGuard create() {
        return new NanochipMirrorBindGuard();
    }

    public void tick(NanochipAssemblyComplexMachine host) {
        if (!ENABLED) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        try {
            State now = readState(host);
            if (now == null) {
                return; // 零件表尚未就绪不能解读为没有绑定，等待服务端结构状态可读后再判断。
            }
            diagnose(host, now);
            if (now.lostCount() <= 0) {
                consecutiveFailures = 0;
                return;
            }
            if (halted || attempts >= MAX_ATTEMPTS || cooldown > 0 || now.level == null) {
                return;
            }
            cooldown = RETRY_INTERVAL_TICKS;
            attempts++;
            lastAttemptTick = now.level.getGameTime();
            int sent = 0;
            for (LostRecord lost : now.lostRecords()) {
                for (BlockPos pos : lost.positions()) {
                    sent += rebind(lost.buffer(), now.level, pos);
                }
            }
            State after = readState(host);
            boolean ok = after != null && after.lostCount() <= 0 && after.proxiesTotal > 0;
            int proxiesNow = after == null ? now.proxiesTotal : after.proxiesTotal;
            lastOutcome = "请求重建 " + sent + " 处 → 已入链 " + proxiesNow + " 条";
            if (ok) {
                consecutiveFailures = 0;
                NanochipModContext.logger().info("[gtlmodular] NAC 镜像绑定恢复 @{}：{}（上一刻 记录={} 已入链=0）",
                        host.getPos(), lastOutcome, now.recordedTotal);
            } else {
                consecutiveFailures++;
                NanochipModContext.logger().info("[gtlmodular] NAC 镜像绑定恢复 @{}：{}（记录={}，第 {} 次尝试，"
                                + "连续无效 {} / {}）", host.getPos(), lastOutcome, now.recordedTotal,
                        attempts, consecutiveFailures, MAX_CONSECUTIVE_FAILURES);
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    halt(host, "连续 " + consecutiveFailures + " 次「已记录坐标却仍解析不到镜像部件」");
                }
            }
        } catch (RuntimeException | LinkageError e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 镜像绑定读取/恢复失败（保持原样，不影响派活）：{}",
                    e.toString());
        }
    }

    /** 显示与恢复共用只读快照，避免显示查询意外触发绑定动作。 */
    public static State readState(NanochipAssemblyComplexMachine host) {
        if (!host.isFormed()) {
            return null;
        }
        Level level = host.getLevel();
        if (level == null) {
            return null;
        }
        List<MEPatternBufferPartMachineBase> buffers = new ArrayList<>(2);
        List<IMultiPart> parts = host.getParts();
        if (parts != null) {
            for (IMultiPart part : parts) {
                if (part instanceof MEPatternBufferPartMachineBase buffer && !buffers.contains(buffer)) {
                    buffers.add(buffer);
                }
            }
        }
        if (buffers.isEmpty()) {
            return null; // 没有可读对象不等于绑定数为零，保留未读取状态。
        }
        State st = new State(level, buffers.size());
        for (MEPatternBufferPartMachineBase buffer : buffers) {
            int recorded;
            int proxies;
            List<BlockPos> positions = null;
            try {
                positions = buffer.getBoundProxyPositions();
                recorded = positions == null ? 0 : positions.size();
            } catch (RuntimeException e) {
                recorded = -1;
            }
            try {
                Set<MEPatternBufferProxyPartMachine> connected = buffer.getProxies();
                proxies = connected == null ? -1 : connected.size();
            } catch (RuntimeException e) {
                proxies = -1;
            }
            st.recordedTotal += Math.max(0, recorded);
            st.proxiesTotal += Math.max(0, proxies);
            st.readFailure |= recorded < 0 || proxies < 0;
            if (st.bufferNotes.size() < DIAG_MAX_BUFFERS) {
                st.bufferNotes.add(buffer.getPos().toShortString()
                        + "[记录=" + (recorded < 0 ? "未读到" : recorded)
                        + ",已入链=" + (proxies < 0 ? "未读到" : proxies) + ']');
            }
            if (recorded > 0 && proxies == 0 && positions != null) {
                st.lostRecords.add(new LostRecord(buffer, positions));
                st.lostCount++;
            }
        }
        return st;
    }

    /** 只连接已有记录且位置上类型正确的代理，避免凭空创建绑定关系。 */
    private static int rebind(MEPatternBufferPartMachineBase buffer, Level level, BlockPos pos) {
        if (pos == null || buffer == null) {
            return 0;
        }
        try {
            MetaMachine machine = MetaMachine.getMachine(level, pos);
            if (machine instanceof MEPatternBufferProxyPartMachine proxy) {
                proxy.setBuffer(buffer.getPos());
                return 1;
            }
        } catch (RuntimeException | LinkageError e) {
            NanochipModContext.logger().debug("[gtlmodular] NAC 镜像绑定恢复：位置 {} 跳过（{}）", pos, e.toString());
        }
        return 0;
    }

    private void halt(NanochipAssemblyComplexMachine host, String reason) {
        halted = true;
        if (warnedHalt) {
            return;
        }
        warnedHalt = true;
        NanochipModContext.logger().warn("[gtlmodular] NAC 镜像绑定恢复 @{}：已停机不再尝试（{}；累计 {} 次尝试）。"
                        + "这一招在这个环境不管用 —— 请用闪存重新绑定镜像部件"
                        + "（或先确认镜像部件本身还在、且与样板总成互相在结构里）。",
                host.getPos(), reason, attempts);
    }

    private void diagnose(NanochipAssemblyComplexMachine host, State st) {
        if (!NanochipModContext.logger().isInfoEnabled()) {
            return;
        }
        String key = st.bufferCount + "/" + st.recordedTotal + "/" + st.proxiesTotal + "/"
                + st.lostCount + (st.readFailure ? "/R" : "/-");
        if (diagCooldown > 0) {
            diagCooldown--;
        }
        boolean changed = !key.equals(diagLast);
        if ((changed || diagCooldown <= 0) && diagBudget > 0) {
            diagBudget--;
            NanochipModContext.logger().info("[gtlmodular] NAC 镜像绑定诊断 @{}：{}", host.getPos(), describe(host, st));
        }
        diagLast = key;
        diagCooldown = DIAG_INTERVAL_TICKS;
    }

    public String describe(NanochipAssemblyComplexMachine host, State st) {
        if (st == null) {
            return "镜像绑定=未读取（未成形 / 零件表为空） @" + host.getPos();
        }
        StringBuilder sb = new StringBuilder(180);
        sb.append("样板总成=").append(st.bufferCount).append(" 台")
                .append("，上游镜像表：记录=").append(st.recordedTotal)
                .append(" / 已入链=").append(st.proxiesTotal)
                .append(st.readFailure ? "（有格未读到）" : "")
                .append("，断链台数=").append(st.lostCount)
                .append("，明细=").append(st.bufferNotes)
                .append("，上次尝试=").append(lastAttemptTick < 0 ? "无" : String.valueOf(lastAttemptTick))
                .append("（").append(lastOutcome == null ? "尚未尝试" : lastOutcome).append("）")
                .append("，尝试=").append(attempts).append('/').append(MAX_ATTEMPTS)
                .append(halted ? "（已停机）" : "")
                .append("，gameTime=").append(st.levelForDiag() == null ? -1L : st.levelForDiag().getGameTime());
        return sb.toString();
    }

    public int attemptsForDiag() {
        return attempts;
    }

    public boolean haltedForDiag() {
        return halted;
    }

    public static final class State {

        private final Level level;

        private int bufferCount;

        private int recordedTotal;

        private int proxiesTotal;

        private boolean readFailure;

        private int lostCount;

        private final List<LostRecord> lostRecords = new ArrayList<>(2);

        private final List<String> bufferNotes = new ArrayList<>(DIAG_MAX_BUFFERS);

        private State(Level level, int bufferCount) {
            this.level = level;
            this.bufferCount = bufferCount;
        }

        public boolean lost() {
            return lostCount > 0;
        }

        public int lostCount() {
            return lostCount;
        }

        public List<LostRecord> lostRecords() {
            return Collections.unmodifiableList(lostRecords);
        }

        public int bufferCount() {
            return bufferCount;
        }

        public int recordedTotal() {
            return recordedTotal;
        }

        public int proxiesTotal() {
            return proxiesTotal;
        }

        public boolean readFailure() {
            return readFailure;
        }

        public List<String> bufferNotes() {
            return Collections.unmodifiableList(bufferNotes);
        }

        private Level levelForDiag() {
            return level;
        }
    }

    public record LostRecord(MEPatternBufferPartMachineBase buffer, List<BlockPos> positions) {

        /** 复制列表，避免快照被上游集合的后续变更改写。 */
        public LostRecord {
            positions = positions == null ? List.of() : List.copyOf(positions);
        }
    }
}
