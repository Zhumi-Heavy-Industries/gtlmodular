package com.gtlmodular.common.machine.independent.io;

import appeng.api.stacks.GenericStack;
import com.gregtechceu.gtceu.integration.ae2.slot.IConfigurableSlotList;
import org.gtlcore.gtlcore.common.machine.multiblock.part.MEDualHatchStockPartMachine;

import java.util.ArrayList;
import java.util.List;

/**
 * 「ME 库存输入总成」（{@code MEDualHatchStockPartMachine}）的读数。
 * <p>
 * 这台仓不是样板仓，也不会自己造配方：它在匹配时按**配置槽里登记过的 key** 现从 ME 抽取，
 * 没登记的 key 一律抽不到；仓被停用（{@code isWorkingEnabled=false}）或脱网时也一律不供料。
 * 所以「网络里明明有货却匹配失败」时，最该看的是它到底登记了哪些、是否在用。
 */
public final class MeStockHatchDiagnostics {

    private MeStockHatchDiagnostics() {}

    /** 一台仓一行的读数；结构里没有这种仓时返回空串。 */
    public static String describe(Object machine) {
        StringBuilder text = new StringBuilder();
        for (var part : parts(machine)) {
            if (!(part instanceof MEDualHatchStockPartMachine hatch)) continue;
            if (text.length() > 0) text.append(" | ");
            text.append(describe(hatch));
        }
        return text.toString();
    }

    /** 某一台仓登记了多少个 key（物品+流体）。重放方块会把它清零。 */
    public static int configuredCount(MEDualHatchStockPartMachine hatch) {
        List<String> configured = new ArrayList<>();
        collect(hatch.aeItemHandler, configured);
        collect(hatch.aeFluidHandler, configured);
        return configured.size();
    }

    private static String describe(MEDualHatchStockPartMachine hatch) {        StringBuilder text = new StringBuilder("ME库存输入总成[");
        try {
            text.append("启用=").append(hatch.isWorkingEnabled() ? "是" : "否");
            text.append(" 在线=").append(hatch.isOnline() ? "是" : "否");
            text.append(" 自动拉取=").append(hatch.getAutoPullMode());
        } catch (RuntimeException | LinkageError failure) {
            text.append("状态读不到(").append(failure.getClass().getSimpleName()).append(')');
        }
        List<String> configured = new ArrayList<>();
        collect(hatch.aeItemHandler, configured);
        collect(hatch.aeFluidHandler, configured);
        text.append(" 配置=").append(configured.size()).append("项");
        if (!configured.isEmpty()) text.append(": ").append(String.join(", ", configured));
        text.append(']');
        return text.toString();
    }

    private static void collect(IConfigurableSlotList list, List<String> out) {
        if (list == null) return;
        try {
            int slots = list.getConfigurableSlots();
            for (int i = 0; i < slots; i++) {
                var slot = list.getConfigurableSlot(i);
                if (slot == null) continue;
                GenericStack config = slot.getConfig();
                if (config == null || config.what() == null) continue;
                GenericStack stock = slot.getStock();
                out.add(config.what().getId() + "×" + (stock == null ? 0L : stock.amount()));
            }
        } catch (RuntimeException | LinkageError failure) {
            out.add("配置读不到(" + failure.getClass().getSimpleName() + ")");
        }
    }

    private static List<?> parts(Object machine) {
        if (!(machine instanceof com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine controller)) {
            return List.of();
        }
        var parts = controller.getParts();
        return parts == null ? List.of() : parts;
    }
}
