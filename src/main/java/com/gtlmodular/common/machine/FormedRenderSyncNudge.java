package com.gtlmodular.common.machine;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gtlmodular.GTLModular;

/**
 * <b>「重进世界后舱室材质不跟机器连接」的有界补丁</b>（2026-09-26 取证后实现）。
 *
 * <h3>机制（全部来自字节码实证）</h3>
 * <ol>
 *   <li>成型后舱室之所以「与机器材质连接」，是 gtceu 在<b>烘焙期</b>把舱室的模型换成控制器的模型：
 *       {@code MachineRenderer.renderMachine(...)} 判断 {@code part.replacePartModelWhenFormed()}（默认 true）
 *       → {@code IPartRenderer.renderReplacedPartMachine(quads, part, ...)}：
 *       <pre>
 *       for (IMultiController controller : part.getControllers()) {
 *           if (controller 的渲染器 instanceof IControllerRenderer cr) { cr.renderPartModel(quads, controller, part, ...); return true; }
 *           if (controller 的渲染器 instanceof MachineRenderer  mr) { mr.renderBaseModel(quads, 控制器定义, controller.self(), ...); return true; }
 *       }
 *       return false;   // ← 拿不到控制器 ⇒ 退回 renderBaseModel()，画成【舱室自己的】材质
 *       </pre>
 *       注意：这条链<b>完全不看 {@code isFormed()}</b>，只看 {@code part.getControllers()}。</li>
 *   <li>{@code getControllers()} 读的是 {@code MultiblockPartMachine.controllerPositions}——
 *       一个 {@code @DescSynced Set&lt;BlockPos&gt;}，即「谁把我收进了结构」这份名单是<b>同步</b>来的，
 *       而且它<b>不持久化</b>（存档 NBT 里没有这个键，gtlcore 现存机器的真实键表可证）。</li>
 *   <li>于是重进世界时的顺序是：客户端先加载区块并<b>烘焙网格</b>（此时名单还是空的 ⇒ 烤成自己的材质），
 *       名单随后才由同步到达；而 gtceu 只会在「该方块自己变化」时重绘，<b>不会</b>在名单到位后回来补一次烘焙。
 *       ⇒ 表现就是「很容易（时序不定）重进游戏后舱室变回自己的材质」。</li>
 * </ol>
 *
 * <h3>补丁做法</h3>
 * 服务端在结构把我们收进名单后，在有界窗口内（{@link #WINDOW_TICKS}，每 {@link #INTERVAL} tick 一次）：
 * <ol>
 *   <li>{@code getSyncStorage().markAllDirty()} —— LDLib 的打脏入口，把本机的 <b>sync 字段</b>
 *       （含 {@code controllerPositions}）强制纳入下一次下发的同步包。
 *       {@code markAllDirty()} 只遍历 {@code getSyncFields()}，不会把持久化字段一起写盘。</li>
 *   <li>{@code scheduleRenderUpdate()} —— 服务端 {@code sendBlockUpdated(pos, state, state, 1)}，
 *       客户端收到后会把所在区块段标脏重烘焙（{@code IMachineBlockEntity.scheduleRenderUpdate} 实证）。</li>
 * </ol>
 * 两条合起来覆盖两种失败时序：<b>名单根本没送到</b>（第 1 条）与 <b>送到了但网格已烤过</b>（第 2 条）。
 * 窗口有界 ⇒ 每个舱室每次加载最多补 8 次，不会持续占用带宽。
 */
final class FormedRenderSyncNudge {

    /** 补丁窗口（tick）≈ 8 秒：足够覆盖「控制器重成形 → 名单同步 → 客户端烘焙」这条链。 */
    static final int WINDOW_TICKS = 160;

    /** 两次补发之间的间隔（tick）：1 秒一次，够密也不会刷屏。 */
    static final int INTERVAL = 20;

    private FormedRenderSyncNudge() {
    }

    /**
     * 每次服务端 tick 调一次。
     *
     * @param machine  本机
     * @param ticksLeft 剩余窗口（首次调用请传 {@link #WINDOW_TICKS}）
     * @param who      日志里用的名字
     * @return 新的剩余窗口；{@code <= 0} 表示窗口用完，调用方可以停止再调
     */
    static int tick(MetaMachine machine, int ticksLeft, String who) {
        if (ticksLeft <= 0 || machine.isRemote()) {
            return 0;
        }
        if (!(machine instanceof IMultiPart part)) {
            return 0;
        }
        // 还没被控制器收进结构：什么都不做，窗口继续耗（控制器重成形需要几 tick）
        if (part.getControllers().isEmpty()) {
            return ticksLeft - 1;
        }
        int left = ticksLeft - 1;
        if (left % INTERVAL == 0) {
            machine.getSyncStorage().markAllDirty();
            machine.scheduleRenderUpdate();
            GTLModular.LOGGER.info("[gtlmodular] 成型同步补丁: {} pos={} 控制器数={} 剩余窗口={}",
                    who, machine.getPos(), part.getControllers().size(), left);
        }
        return left;
    }
}
