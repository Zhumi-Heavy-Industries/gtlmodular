package com.gtlmodular.common.data.fuel;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.client.renderer.machine.WorkableTieredHullMachineRenderer;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularMachines;
import com.gtlmodular.common.machine.fuel.AccelerateHatchPartMachine;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * ★ <b>加速仓 14 档</b>的注册（LV ~ MAX，对齐 GTO {@code gtocore:accelerate_hatch_*}）。
 *
 * <h2>数值</h2>
 * <p>每档把所在多方块配方耗时按百分比缩短，最快 = {@code 52 - 2×tier}：
 * LV=50% … MAX=24%（越低越快）；玩家可在 [最快%, 100%] 之间调。仓每低于配方一级，效果 -20%。
 * 详见 {@link AccelerateHatchPartMachine} 与 {@code docs/增益仓_四仓语义.md}。
 *
 * <h2>能力 + 覆盖范围</h2>
 * <p>14 档共用同一个自定义 {@code PartAbility}（{@link #ACCELERATE_HATCH}）。
 * 按用户口径：<b>凡结构里允许放线程仓的机器都同样允许放加速仓</b>，
 * 各结构谓词里 {@code .or(AccelerateHatches.predicate())} 一行即可。
 *
 * <h2>注册顺序</h2>
 * <p>与线程仓/超频仓同一条：{@code PartAbility.getAllBlocks()} 是懒记忆化快照，
 * 必须在结构谓词构造前注册（见 {@code GTLModularMachines#init()}）。
 *
 * @author 小爱与大肥鱼
 */
public final class AccelerateHatches {

    private AccelerateHatches() {
    }

    /** 自定义 {@code PartAbility}：14 档都挂在它下面。 */
    public static final PartAbility ACCELERATE_HATCH = new PartAbility("accelerate_hatch");

    /** 14 档电压（LV ~ MAX，tier 1..14）。 */
    public static final int[] TIERS = {
            GTValues.LV, GTValues.MV, GTValues.HV, GTValues.EV, GTValues.IV,
            GTValues.LuV, GTValues.ZPM, GTValues.UV, GTValues.UHV, GTValues.UEV,
            GTValues.UIV, GTValues.UXV, GTValues.OpV, GTValues.MAX
    };

    /** 14 档小写后缀（= GTValues.VN 小写）。 */
    private static final String[] SUFFIXES = {
            "lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv", "uhv", "uev", "uiv", "uxv", "opv", "max"
    };

    /** 已注册的全部（keep 顺序）。 */
    public static final List<MachineDefinition> REGISTERED = new ArrayList<>(14);

    /**
     * 结构谓词里用的标准「可插一台加速仓」谓词。
     *
     * <p>凡结构里允许放线程仓的机器，都应再 {@code .or(AccelerateHatches.predicate())} 一次。
     */
    public static com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate predicate() {
        return com.gregtechceu.gtceu.api.pattern.Predicates.abilities(ACCELERATE_HATCH)
                .setMaxGlobalLimited(1)
                .setPreviewCount(1);
    }

    /** 已在 {@code GTLModularMachines#init()} 里调用一次。 */
    public static void register() {
        for (int i = 0; i < TIERS.length; i++) {
            REGISTERED.add(registerOne(TIERS[i], SUFFIXES[i], i + 1));
        }
        GTLModular.LOGGER.info(
                "[gtlmodular] 已注册加速仓 {} 档（LV~MAX，对齐 GTO；最快 {}%~{}%）",
                REGISTERED.size(),
                AccelerateHatchPartMachine.minPercentage(GTValues.MAX),
                AccelerateHatchPartMachine.minPercentage(GTValues.LV));
    }

    private static MachineDefinition registerOne(int tier, String suffix, int mk) {
        String id = "accelerate_hatch_" + suffix;
        ResourceLocation overlay = new ResourceLocation(GTLModular.MOD_ID,
                "block/machines/accelerate_hatch/accelerate_hatch_mk" + mk);

        return GTLModularMachines.REGISTRATE
                .machine(id, holder -> new AccelerateHatchPartMachine(holder, tier))
                .rotationState(RotationState.ALL)
                .abilities(ACCELERATE_HATCH)
                .tier(tier)
                .renderer(() -> new WorkableTieredHullMachineRenderer(tier, overlay))
                .langValue(GTValues.VN[tier] + " Accelerate Hatch")
                .tooltips(Component.translatable("gtlmodular.accelerate_hatch.hatch.tooltip",
                        AccelerateHatchPartMachine.minPercentage(tier)))
                .register();
    }
}
