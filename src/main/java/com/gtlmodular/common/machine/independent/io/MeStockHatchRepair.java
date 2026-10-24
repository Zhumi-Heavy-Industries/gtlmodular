package com.gtlmodular.common.machine.independent.io;

import appeng.api.networking.IManagedGridNode;
import com.gregtechceu.gtceu.integration.ae2.machine.MEBusPartMachine;
import net.minecraft.server.level.ServerLevel;
import org.gtlcore.gtlcore.common.machine.multiblock.part.MEDualHatchStockPartMachine;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 「ME 库存输入总成」掉线自愈。
 * <p>
 * 这台仓供料前先看自己的 AE 节点：{@code isOnline()=false} 或网格为空时一律原样返回、一滴不给，
 * 而读档后它的节点偶发不会重新挂回网络（表现为「网络里全是货、机器却 0 候选」，拆一下方块就好）。
 * 这里在仓已停用之外、只对「载入中的服务端 + 确实掉线」的仓做一次节点重建（destroy → create），
 * 限频限次；节点本身正常时一行代码都不碰。
 */
public final class MeStockHatchRepair {

    private static final int COOLDOWN_TICKS = 100;
    private static final int MAX_ATTEMPTS = 8;

    private final Map<Object, State> states = new IdentityHashMap<>();

    private static final class State {
        long lastAttempt = Long.MIN_VALUE;
        int attempts;
        boolean gaveUp;
    }

    /** 每次扫描/派活前调用；返回本 tick 是否真的重连过。 */
    public boolean tick(Object machine) {
        if (!(machine instanceof com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine controller)) {
            return false;
        }
        var parts = controller.getParts();
        if (parts == null || parts.isEmpty()) return false;
        boolean repaired = false;
        for (var part : parts) {
            if (!(part instanceof MEDualHatchStockPartMachine hatch)) continue;
            repaired |= repairIfOffline(hatch);
        }
        return repaired;
    }

    private boolean repairIfOffline(MEDualHatchStockPartMachine hatch) {
        if (hatch.isOnline()) return false;
        if (!(hatch.getLevel() instanceof ServerLevel serverLevel)) return false;
        State state = states.computeIfAbsent(hatch, key -> new State());
        if (state.gaveUp) return false;
        long now = serverLevel.getGameTime();
        if (state.lastAttempt != Long.MIN_VALUE && now - state.lastAttempt < COOLDOWN_TICKS) return false;
        if (state.attempts >= MAX_ATTEMPTS) {
            state.gaveUp = true;
            com.gtlmodular.GTLModular.LOGGER.warn("[independent] ME 库存输入总成持续离线 @{}：已重连 {} 次仍未上线，"
                    + "请检查该仓是否连着 ME 网络、网络是否供电（在线=否时它一滴料都不供）",
                    hatch.getPos(), state.attempts);
            return false;
        }
        state.lastAttempt = now;
        state.attempts++;
        try {
            IManagedGridNode node = hatch.getMainNode();
            if (node == null) return false;
            node.destroy();
            node.create(serverLevel, hatch.getPos());
            com.gtlmodular.GTLModular.LOGGER.info("[independent] ME 库存输入总成掉线，已重建 AE 节点 @{}（第 {} 次，在线={}）",
                    hatch.getPos(), state.attempts, hatch.isOnline() ? "是" : "否");
            return true;
        } catch (RuntimeException | LinkageError failure) {
            state.gaveUp = true;
            com.gtlmodular.GTLModular.LOGGER.warn("[independent] ME 库存输入总成节点重建失败 @{}：{}（保持原样）",
                    hatch.getPos(), failure.toString());
            return false;
        }
    }

    /** 掉线仓的现状，供日志用。 */
    public static String offlineNote(Object machine) {
        if (!(machine instanceof com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine controller)) {
            return "";
        }
        var parts = controller.getParts();
        if (parts == null) return "";
        int offline = 0;
        for (var part : parts) {
            if (part instanceof MEBusPartMachine bus && !bus.isOnline()) offline++;
        }
        return offline == 0 ? "" : "（有 " + offline + " 个 ME 仓掉线，见「ME 库存输入总成掉线」日志）";
    }
}
