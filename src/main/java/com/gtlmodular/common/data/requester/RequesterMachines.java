package com.gtlmodular.common.data.requester;

import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularMachines;
import com.gtlmodular.common.machine.requester.RequesterMachine;
import net.minecraft.network.chat.Component;

/**
 * <b>ME库存补充仓</b>（独立请求器方块）的注册。
 *
 * <p>单方块、不接配方表：它不"加工"，只读 AE 网络 + 下单。
 * 外壳用 GTCEu 的 tier 机壳 + 自绘图版前脸（{@code gtlmodular:block/machine/part/requester}）。
 *
 * <p>电压档取 <b>EV(4)</b>：与 GTCEu 的 ME 总线/仓室同档（{@code GTAEMachines} 用 tier 4），
 * 这样它的 AE 待机耗电口径与既有 ME 仓完全一致，不会出现"比总线还费电"的怪现象。
 *
 * <p>注册时机：{@link GTLModularMachines#init()}（GTM 机器注册窗口内），与其它机器同一条链。
 */
public final class RequesterMachines {

    /** EV —— 与 GTCEu ME 总线同档。 */
    private static final int TIER = 4;

    public static MachineDefinition REQUESTER;

    private RequesterMachines() {
    }

    public static void register() {
        REQUESTER = GTLModularMachines.REGISTRATE
                .machine("me_requester", RequesterMachine::new)
                .rotationState(RotationState.ALL)
                .tier(TIER)
                .overlayTieredHullRenderer("me_requester")
                .langValue("ME库存补充仓")
                .tooltips(
                        Component.literal("§7盯住 AE 网络库存，不够就下单合成"),
                        Component.literal("§7产物直接回到网络（不需要收货人）"),
                        Component.literal("§7可接入物品与流体，支持 JEI 拖拽"),
                        Component.literal("§8不依赖 merequester；支持 gtlcore 无线 ME 网络"))
                .register();
        GTLModular.LOGGER.info("[gtlmodular] 已注册 ME库存补充仓（独立请求器方块）：{}", REQUESTER.getId());
    }
}
