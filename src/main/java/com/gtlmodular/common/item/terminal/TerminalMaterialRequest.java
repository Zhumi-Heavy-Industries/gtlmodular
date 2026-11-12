package com.gtlmodular.common.item.terminal;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 一张结构计划对应的统一材料请求：订单、自动施工、报告共用同一份缺口。 */
public final class TerminalMaterialRequest {

    private static final ShanhaiTerminalMaterialService MATERIALS = new ShanhaiTerminalMaterialService();

    private final ShanhaiStructurePlan plan;
    private final Map<AEKey, Long> shortages;
    private final List<GenericStack> inputs;
    private final long totalAmount;

    private TerminalMaterialRequest(ShanhaiStructurePlan plan, Map<AEKey, Long> shortages) {
        this.plan = plan;
        this.shortages = Collections.unmodifiableMap(new java.util.LinkedHashMap<>(shortages));
        List<GenericStack> list = new ArrayList<>(shortages.size());
        long total = 0L;
        for (Map.Entry<AEKey, Long> entry : shortages.entrySet()) {
            list.add(new GenericStack(entry.getKey(), entry.getValue()));
            total = Math.min(Long.MAX_VALUE, total + Math.max(0L, entry.getValue()));
        }
        this.inputs = Collections.unmodifiableList(list);
        this.totalAmount = total;
    }

    public static TerminalMaterialRequest create(ServerPlayer player, ShanhaiStructurePlan plan,
                                                 ShanhaiTerminalAeBinding.Context ae) {
        return new TerminalMaterialRequest(plan, MATERIALS.shortages(plan, player, ae));
    }

    public ShanhaiStructurePlan plan() {
        return plan;
    }

    public Map<AEKey, Long> shortages() {
        return shortages;
    }

    public List<GenericStack> inputs() {
        return inputs;
    }

    public long totalAmount() {
        return totalAmount;
    }

    public int kindCount() {
        return shortages.size();
    }

    public boolean isEmpty() {
        return shortages.isEmpty();
    }
}
