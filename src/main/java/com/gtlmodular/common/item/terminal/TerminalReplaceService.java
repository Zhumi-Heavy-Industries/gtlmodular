package com.gtlmodular.common.item.terminal;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 替换模式执行：扫描完成后直接把替换位置交给施工器一次性执行。
 * 不缓存计划、不做第二次确认、不维护 tick 队列。
 */
public final class TerminalReplaceService {

    private static final ShanhaiTerminalMaterialService MATERIALS = new ShanhaiTerminalMaterialService();
    private static final ShanhaiStructureExecutor EXECUTOR = new ShanhaiStructureExecutor(MATERIALS);

    private TerminalReplaceService() {}

    public static ShanhaiStructureExecutor.Result execute(ServerPlayer player, ItemStack terminal,
                                                          ShanhaiStructurePlan plan,
                                                          ShanhaiTerminalAeBinding.Context ae) {
        List<ShanhaiStructurePlan.Entry> entries = replaceEntries(plan);
        if (entries.isEmpty()) return null;
        ShanhaiStructurePlan sub = new ShanhaiStructurePlan(
                plan.target(), plan.machineId(), plan.mirrored(), plan.repeatCount(), entries);
        return player.isCreative()
                ? EXECUTOR.executeCreative(player, sub, ae)
                : EXECUTOR.execute(player, sub, ae);
    }

    private static List<ShanhaiStructurePlan.Entry> replaceEntries(ShanhaiStructurePlan plan) {
        List<ShanhaiStructurePlan.Entry> entries = new ArrayList<>();
        for (ShanhaiStructurePlan.Entry entry : plan.entries()) {
            if (entry.requiresBuild()
                    && (entry.kind() == ShanhaiStructurePlan.Kind.REPLACE
                    || entry.kind() == ShanhaiStructurePlan.Kind.FORCE_REPLACE)) {
                entries.add(entry);
            }
        }
        return entries;
    }
}
