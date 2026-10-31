package com.gtlmodular.common.machine.print;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import it.unimi.dsi.fastutil.ints.Int2LongFunction;

/**
 * 单方块「一次性工具 3D 打印机」（LV / MV 两台共用这一份实现）。
 *
 * <p>直接继承 GTCEu 的 {@link SimpleTieredMachine}（与研磨机/组装机同款单方块工作机）：
 * 白拿输入/输出总线、能量缓存、超频、配方 UI、自动输出、扳手拆卸等既有行为；
 * 区别只有两处：
 * <ol>
 *   <li>配方表 = {@code gtlmodular:disposable_tool_printing}（模具不消耗 + 72mB 材料流体 → 脆弱工具）；</li>
 *   <li>流体罐比 GTCEu 默认值大（16 桶），因为一条配方要 72mB，模具一次成型后要连续打很多把，
 *       罐太小会频繁停线。</li>
 * </ol>
 *
 * <p>LV 与 MV 的差别只在 {@code tier}（以及 GTCEu 自带的超频逻辑：MV 跑 LV 配方会多一次超频）。
 * 能量/IO 面由 GTCEu 的电压机壳自动处理，正面用本模组自绘的 3D 打印机脸
 * （{@code gtlmodular:block/machines/disposable_tool_printer}，静态 + 工作动画）。
 */
public class DisposableToolPrinterMachine extends SimpleTieredMachine {

    /**
     * 流体罐容量（mB）。
     *
     * <p>★ 2026-10-31 队长口径：<b>LV 8000 mB</b>，MV 保持 16000 mB。
     * 描述里的「流体容量」也直接读这个函数（见 {@code singleTooltips}），两边永远一致。
     */
    public static final Int2LongFunction TANK_SIZE = tier -> tier <= 1 ? 8_000L : 16_000L;

    public DisposableToolPrinterMachine(IMachineBlockEntity holder, int tier) {
        super(holder, tier, TANK_SIZE);
    }
}
