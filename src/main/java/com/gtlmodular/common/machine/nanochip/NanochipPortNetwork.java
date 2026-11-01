package com.gtlmodular.common.machine.nanochip;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * <b>NAC 总成的无线路由表</b>（NAC 重做 P2）。
 *
 * <p>一条「线」= <b>同一个维度 + 同频 + 同色</b>（规划书 §4.4 第 2/6 条）：
 * <ul>
 *   <li>频率隔离不同的 NAC（防串台）；</li>
 *   <li>颜色隔离同一台 NAC 内的线路；</li>
 *   <li><b>未染色 = 断线</b>（{@code colorIndex == -1} 的部件根本不进表）。</li>
 * </ul>
 *
 * <h2>为什么是「静态表 + 部件自注册」</h2>
 * 总成是<b>无线</b>的：玩家不会把它们用方块连起来，所以没有"邻居可查"。
 * 唯一能把「主机上的发件总成」和「模块上的收件总成」联系起来的，就是它们自己上报的
 * （维度, 频率, 颜色）。因此这里维护一张<b>服务端全局表</b>：
 * <ul>
 *   <li>{@link NanochipPortPartMachine} 在 {@code onLoad} 时 {@link #refresh}、在 {@code onUnload} 时
 *       {@link #unregister}，改频率/颜色时再 {@link #refresh} —— 与 {@code NanochipPipeMachine}
 *       的「拓扑纪元」同一思路：<b>不扫描世界</b>，只维护增量；</li>
 *   <li>表里存的是机器对象引用，键是 {@link ChannelKey}；卸载必移除，不会随区块加载泄漏。</li>
 * </ul>
 * 全程只在服务端线程访问（机器的 load/unload/tick 都在主线程），不需要加锁。
 *
 * <h2>一条线的合法性（规划书 §4.3 / §4.4 第 3 条）</h2>
 * <b>一条线只允许一个发件总成</b>；出现第二个 ⇒ {@link Line#conflict()} 为真 ⇒
 * <b>整条线罢工</b>（发件不推、收件不收），双方界面与日志报「线路冲突」。
 * 收件总成可以多个（1 发 N 收），投递时<b>按数量平摊</b>，余数按位置顺位补。
 *
 * <h2>与真空网络的差别</h2>
 * {@link NanochipVacuumNetwork} 把虚拟件存在<b>控制器</b>上（跟着多方块存档走）；
 * 本表<b>不存物品</b>——物品始终待在发件总成的 16 格缓冲里，
 * 推不出去就留在原处（{@code pending}），所以「绝不丢件」是结构保证，不需要额外的虚拟库存。
 */
public final class NanochipPortNetwork {

    /** 投递节拍：每 10 tick（0.5 秒）尝试一次；相位按方块坐标错开，避免所有总成同一 tick 抢线程。 */
    public static final int TRANSFER_INTERVAL = 10;

    /** 线路冲突的日志限流（同一波特 200 tick 内只报一次，避免刷屏）。 */
    public static final int CONFLICT_LOG_INTERVAL = 200;

    /** 「线」的键：维度 + 频率 + 颜色序号（0~15；未染色根本不建键）。 */
    public record ChannelKey(ResourceKey<Level> dimension, int frequency, int color) {
        @Override
        public String toString() {
            return dimension.location() + "@" + frequency + "/" + color;
        }
    }

    /** 一条线在当前时刻的成员快照。{@code senders}/{@code receivers} 均按方块坐标排序（均分余数的"顺位"）。 */
    public record Line(List<NanochipSenderAssemblyMachine> senders,
                       List<NanochipReceiverAssemblyMachine> receivers) {

        /** 一条线只能有一个发件总成；≥2 即冲突（整线罢工）。 */
        public boolean conflict() {
            return senders.size() > 1;
        }

        public boolean hasReceiver() {
            return !receivers.isEmpty();
        }
    }

    private static final Line EMPTY_LINE = new Line(List.of(), List.of());

    /** 键 → 该线上的全部总成（含收/发）。 */
    private static final Map<ChannelKey, List<NanochipPortPartMachine>> CHANNELS = new HashMap<>();

    /** 机器 → 它当前登记的键（用于改频率/颜色时精确摘除旧键）。 */
    private static final Map<NanochipPortPartMachine, ChannelKey> REGISTERED = new IdentityHashMap<>();

    /** 按方块坐标排序：让「余数按位置顺位补」在同一 tick 内是确定的。 */
    private static final Comparator<NanochipPortPartMachine> BY_POS =
            Comparator.comparingLong(port -> port.getPos().asLong());

    private NanochipPortNetwork() {
    }

    /**
     * 让本机登记到它<b>现在</b>的线上（幂等）。改频率/颜色、区块加载时都调这个。
     *
     * <p>客户端调用是空操作：路由表只在服务端存在（客户端不 tick 投递）。
     */
    public static void refresh(NanochipPortPartMachine port) {
        if (port == null || port.isRemote()) {
            return;
        }
        ChannelKey now = port.channelKey();
        ChannelKey old = REGISTERED.get(port);
        if (Objects.equals(now, old)) {
            return;
        }
        if (old != null) {
            removeFrom(old, port);
            REGISTERED.remove(port);
        }
        if (now != null) {
            CHANNELS.computeIfAbsent(now, k -> new ArrayList<>()).add(port);
            REGISTERED.put(port, now);
        }
    }

    /** 机器卸载/拆除时摘除。 */
    public static void unregister(NanochipPortPartMachine port) {
        if (port == null) {
            return;
        }
        ChannelKey old = REGISTERED.remove(port);
        if (old != null) {
            removeFrom(old, port);
        }
    }

    private static void removeFrom(ChannelKey key, NanochipPortPartMachine port) {
        List<NanochipPortPartMachine> list = CHANNELS.get(key);
        if (list == null) {
            return;
        }
        list.remove(port);
        if (list.isEmpty()) {
            CHANNELS.remove(key);
        }
    }

    /** 取一条线的当前快照；键为 {@code null} / 线不存在 ⇒ 空线。 */
    public static Line resolve(ChannelKey key) {
        if (key == null) {
            return EMPTY_LINE;
        }
        List<NanochipPortPartMachine> members = CHANNELS.get(key);
        if (members == null || members.isEmpty()) {
            return EMPTY_LINE;
        }
        List<NanochipSenderAssemblyMachine> senders = new ArrayList<>(2);
        List<NanochipReceiverAssemblyMachine> receivers = new ArrayList<>(2);
        // 防御：正常路径靠 onUnload 摘除；万一某台机器的卸载回调没走到（退出世界/异常），
        // 这里顺手把它从表里清掉，避免表里留下"已经不在世界里"的幽灵成员。
        for (java.util.Iterator<NanochipPortPartMachine> it = members.iterator(); it.hasNext(); ) {
            NanochipPortPartMachine port = it.next();
            if (port.getLevel() == null || port.isInValid()) {
                it.remove();
                REGISTERED.remove(port);
                continue;
            }
            if (port instanceof NanochipSenderAssemblyMachine sender) {
                senders.add(sender);
            } else if (port instanceof NanochipReceiverAssemblyMachine receiver) {
                receivers.add(receiver);
            }
        }
        if (members.isEmpty()) {
            CHANNELS.remove(key);
        }
        if (senders.isEmpty() && receivers.isEmpty()) {
            return EMPTY_LINE;
        }
        senders.sort(BY_POS);
        receivers.sort(BY_POS);
        return new Line(List.copyOf(senders), List.copyOf(receivers));
    }

    /** 调试/自检用：当前登记了多少条线。 */
    public static int channelCount() {
        return CHANNELS.size();
    }

    /** 调试/自检用：某条线上登记了多少台总成。 */
    public static int memberCount(ChannelKey key) {
        List<NanochipPortPartMachine> list = key == null ? null : CHANNELS.get(key);
        return list == null ? 0 : list.size();
    }
}
