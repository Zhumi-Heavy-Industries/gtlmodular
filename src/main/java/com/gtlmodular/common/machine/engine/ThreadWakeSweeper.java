package com.gtlmodular.common.machine.engine;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * ★ 2026-11-08：<b>通用独立线程引擎 · 唤醒巡检器</b>。
 *
 * <h2>为什么需要它</h2>
 * 线程引擎改成 GTO 式「按需订阅」后（见 {@link ThreadedRecipeLogic#updateTickSubscription()}），
 * 机器没批次、没料时会主动退订。正常唤醒走 GTCEu/gtlcore 的既有链路：
 * <ul>
 *   <li>多方块成形时给每个配方 handler 注册了 {@code recipeLogic::updateTickSubscription} 的
 *       changed-listener ⇒ 玩家放料/抽料会叫醒；</li>
 *   <li>gtlcore 样板仓的 buffer-changed listener 也指向
 *       {@code RecipeLogic.updateTickSubscription} ⇒ AE 推料/写样板会叫醒；</li>
 *   <li>基类 {@code onMachineLoad()} / {@code resetRecipeLogic()} / {@code setStatus()}、
 *       gtlcore 的切表钩子也会调。</li>
 * </ul>
 * 但这些链路是“别人保证”的：任何一条漏了、或第三方机器没接，机器就会退订后永久不接单。
 * 本类只做一件事 —— <b>每 {@code threading.wakeSweepTicks}（默认 10）tick 扫一遍注册表，
 * 把“没订阅但按规则应该订阅”的机器重新订上</b>；它不推进任何批次，不碰进度。
 *
 * <h2>成本</h2>
 * 每 N tick 遍历一次 {@link ThreadedRecipeLogic}（WeakHashMap，不阻止机器回收），
 * 对每台先看订阅状态，只有没订阅的才调 {@code wantsTick()}（一次能力表只读判断）。
 * 真正有活的机器都在订阅里，不会被这里重复处理。
 *
 * <h2>开关</h2>
 * {@code threading.demandSubscribe=false} ⇒ 引擎回到“永久订阅”，本类整体空转（{@code needsWake} 恒 false）。
 *
 * @see ThreadedRecipeLogic#needsWake()
 * @see ThreadedRecipeLogic#updateTickSubscription()
 */
public final class ThreadWakeSweeper {

    /** 所有线程引擎逻辑（弱引用；机器被回收后自动消失）。 */
    private static final Set<ThreadedRecipeLogic> REGISTERED =
            Collections.newSetFromMap(new WeakHashMap<>());

    private static int tickCounter;

    private ThreadWakeSweeper() {}

    /** 逻辑构造时登记（幂等）。 */
    public static void register(ThreadedRecipeLogic logic) {
        if (logic != null) {
            REGISTERED.add(logic);
        }
    }

    /** Forge 服务端 tick（{@code Phase.END}）：按间隔巡检，只补订阅、不推进。 */
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        int interval = Math.max(1, GTLModularConfig.getThreadingWakeSweepTicks());
        if (++tickCounter < interval) {
            return;
        }
        tickCounter = 0;
        for (ThreadedRecipeLogic logic : REGISTERED) {
            if (logic == null) {
                continue;
            }
            try {
                if (logic.needsWake()) {
                    logic.updateTickSubscription();
                    GTLModular.LOGGER.debug("[gtlmodular] 线程巡检：重新订阅 @{}", logic.sweepPos());
                }
            } catch (Throwable t) {
                GTLModular.LOGGER.error("[gtlmodular] 线程巡检：@{} 唤醒异常（本 tick 跳过）",
                        logic.sweepPos(), t);
            }
        }
    }

    /** 停服/换世界：静态集合不能把上一个世界的机器带过去。 */
    public static void onServerStopped(ServerStoppedEvent event) {
        REGISTERED.clear();
        tickCounter = 0;
    }
}
