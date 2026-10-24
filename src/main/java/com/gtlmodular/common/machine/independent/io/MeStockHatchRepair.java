package com.gtlmodular.common.machine.independent.io;

import com.gregtechceu.gtceu.integration.ae2.machine.MEBusPartMachine;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.gtlcore.gtlcore.common.machine.multiblock.part.MEDualHatchStockPartMachine;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeNetworkRuntime;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeSavedData;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 「ME 库存输入总成」掉线自愈。
 * <p>
 * 这台仓供料前先看自己的 AE 节点：节点不在线时对整条输入列表原样返回、一滴不给，
 * 读档后很容易看到「网络里 9.2E 的料、机器却 0 候选」。它现在走 gtlcore 的无线 ME 网络，
 * AE 的 {@code ManagedGridNode.create()} 在节点已初始化时会直接抛
 * 「The node has already been initialized」，所以不能靠重建节点修；正确做法是调用 gtlcore
 * 无线运行时自己的 {@code connectMemberNow} 把注册过的成员重新接上。
 * 没绑定过网络的仓、或核心不在线/核心未连到 ME 网络时，只把原因写清楚，不硬改玩家配置。
 */
public final class MeStockHatchRepair {

    private static final int COOLDOWN_TICKS = 100;
    private static final int MAX_ATTEMPTS = 8;

    private final Map<Object, State> states = new IdentityHashMap<>();

    private static final class State {
        long lastAttempt = Long.MIN_VALUE;
        int attempts;
        int warned;
        boolean gaveUp;
    }

    /** 每次扫描/派活前调用；返回本 tick 是否尝试过重连。 */
    public boolean tick(Object machine) {
        if (!(machine instanceof com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine controller)) {
            return false;
        }
        var parts = controller.getParts();
        if (parts == null || parts.isEmpty()) return false;
        boolean attempted = false;
        for (var part : parts) {
            if (!(part instanceof MEDualHatchStockPartMachine hatch)) continue;
            if (hatch.isOnline()) continue;
            attempted |= reconnect(hatch);
        }
        return attempted;
    }

    private boolean reconnect(MEDualHatchStockPartMachine hatch) {
        if (!(hatch.getLevel() instanceof ServerLevel level)) return false;
        MinecraftServer server = level.getServer();
        if (server == null) return false;
        State state = states.computeIfAbsent(hatch, key -> new State());
        long now = level.getGameTime();
        if (state.lastAttempt != Long.MIN_VALUE && now - state.lastAttempt < COOLDOWN_TICKS) return false;
        if (state.gaveUp) return false;
        state.lastAttempt = now;
        state.attempts++;
        try {
            WirelessAeSavedData data = WirelessAeSavedData.get(server);
            WirelessAeSavedData.MemberKey key = resolve(level, hatch);
            if (key == null) {
                warnOnce(hatch, state, "解析不到它的无线目标（可能不是无线目标方块）");
                state.gaveUp = true;
                return false;
            }
            UUID frequency = data.getMemberNetwork(key);
            if (frequency == null) {
                warnOnce(hatch, state, "它没有绑定任何无线ME网络——用「无线ME网络绑定工具」把它绑到同一网络即可");
                state.gaveUp = true;
                return false;
            }
            WirelessAeNetworkRuntime.ConnectionResult result =
                    WirelessAeNetworkRuntime.connectMemberNow(server, frequency, key);
            WirelessAeNetworkRuntime.invalidateTopology();
            if (result == WirelessAeNetworkRuntime.ConnectionResult.CONNECTED
                    || result == WirelessAeNetworkRuntime.ConnectionResult.ALREADY_CONNECTED) {
                com.gtlmodular.GTLModular.LOGGER.info("[independent] ME 库存输入总成已重连无线网络 @{}（网络 {}，结果 {}）",
                        hatch.getPos(), WirelessAeNetworkRuntime.shortFrequency(frequency), result);
                state.gaveUp = true;
                return true;
            }
            if (result == WirelessAeNetworkRuntime.ConnectionResult.CORE_MISSING
                    || result == WirelessAeNetworkRuntime.ConnectionResult.CORE_NOT_CONNECTED
                    || result == WirelessAeNetworkRuntime.ConnectionResult.TARGET_MISSING) {
                warnOnce(hatch, state, "无线网络核心不可用（" + result + "）——检查无线ME网络核心是否加载、是否连上 ME 网络");
                if (result != WirelessAeNetworkRuntime.ConnectionResult.TARGET_MISSING
                        && state.attempts >= MAX_ATTEMPTS) state.gaveUp = true;
                return true;
            }
            if (state.attempts >= MAX_ATTEMPTS) {
                warnOnce(hatch, state, "重连 " + state.attempts + " 次仍未上线（" + result + "）");
                state.gaveUp = true;
            }
            return true;
        } catch (RuntimeException | LinkageError failure) {
            warnOnce(hatch, state, "重连抛异常：" + failure);
            state.gaveUp = true;
            return false;
        }
    }

    private static WirelessAeSavedData.MemberKey resolve(ServerLevel level, MEDualHatchStockPartMachine hatch) {
        try {
            return WirelessAeNetworkRuntime.resolveWirelessTarget(level, hatch.getPos(), null);
        } catch (RuntimeException first) {
            try {
                return WirelessAeNetworkRuntime.resolveWirelessTarget(level, hatch.getPos(), hatch.getFrontFacing());
            } catch (RuntimeException second) {
                return null;
            }
        }
    }

    private static void warnOnce(MEDualHatchStockPartMachine hatch, State state, String reason) {
        state.warned++;
        com.gtlmodular.GTLModular.LOGGER.warn("[independent] ME 库存输入总成离线 @{}：{}（离线时它一滴料都不供）",
                hatch.getPos(), reason);
    }

    /** 掉线仓的现状，供日志/dump 用。 */
    public static String offlineNote(Object machine) {
        if (!(machine instanceof com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine controller)) {
            return "";
        }
        var parts = controller.getParts();
        if (parts == null) return "";
        int offline = 0;
        for (var part : parts) {
            if (part instanceof MEDualHatchStockPartMachine hatch && !hatch.isOnline()) offline++;
        }
        return offline == 0 ? "" : "（有 " + offline + " 台库存输入总成离线，离线时一滴料都不供）";
    }

    /** 结构里有没有这种 ME 仓（诊断用）。 */
    public static boolean isStockHatch(Object part) {
        return part instanceof MEBusPartMachine;
    }
}
