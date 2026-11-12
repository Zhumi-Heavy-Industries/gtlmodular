package com.gtlmodular.common.data.fuel;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.client.renderer.machine.WorkableTieredHullMachineRenderer;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularMachines;
import com.gtlmodular.common.machine.fuel.OverclockHatchPartMachine;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * ★ <b>超频仓 7 档</b>（UV ~ MAX，对齐 GTO {@code gtocore:overclock_hatch_*}）。
 *
 * <h2>数值（GTO 口径）</h2>
 * <p>每升一档仍使用 4 倍功率，仓只替换「耗时倍率」{@code = 1/(tier-6)}：
 * UV=50% / UHV=33.3% / UEV=25%(无损) / UIV=20% / UXV=16.7% / OpV=14.3% / MAX=12.5%。
 * 详见 {@link OverclockHatchPartMachine}。
 *
 * <h2>能力 + 范围</h2>
 * <p>7 档共用 {@link #OVERCLOCK_HATCH}；凡结构里允许放线程仓的机器都附带可插超频仓。
 *
 * @author 小爱与大肥鱼
 */
public final class OverclockHatches {

    private OverclockHatches() {
    }

    public static final PartAbility OVERCLOCK_HATCH = new PartAbility("overclock_hatch");

    /** 7 档电压（UV ~ MAX）。 */
    public static final int[] TIERS = {
            GTValues.UV, GTValues.UHV, GTValues.UEV, GTValues.UIV,
            GTValues.UXV, GTValues.OpV, GTValues.MAX
    };

    private static final String[] SUFFIXES = {"uv", "uhv", "uev", "uiv", "uxv", "opv", "max"};

    public static final List<MachineDefinition> REGISTERED = new ArrayList<>(7);

    /** 结构谓词：凡可插线程仓的机器都应 {@code .or(OverclockHatches.predicate())}。 */
    public static com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate predicate() {
        return com.gregtechceu.gtceu.api.pattern.Predicates.abilities(OVERCLOCK_HATCH)
                .setMaxGlobalLimited(1)
                .setPreviewCount(1);
    }

    public static void register() {
        for (int i = 0; i < TIERS.length; i++) {
            REGISTERED.add(registerOne(TIERS[i], SUFFIXES[i], i + 1));
        }
        GTLModular.LOGGER.info("[gtlmodular] 已注册超频仓 {} 档（UV~MAX，对齐 GTO 时间倍率口径）", REGISTERED.size());
    }

    private static MachineDefinition registerOne(int tier, String suffix, int mk) {
        String id = "overclock_hatch_" + suffix;
        ResourceLocation overlay = new ResourceLocation(GTLModular.MOD_ID,
                "block/machines/overclock_hatch/overclock_hatch_mk" + mk);
        double pct = OverclockHatchPartMachine.durationFactorForTier(tier) * 100.0;

        return GTLModularMachines.REGISTRATE
                .machine(id, holder -> new OverclockHatchPartMachine(holder, tier))
                .rotationState(RotationState.ALL)
                .abilities(OVERCLOCK_HATCH)
                .tier(tier)
                .renderer(() -> new WorkableTieredHullMachineRenderer(tier, overlay))
                .langValue(GTValues.VN[tier] + " Overclock Hatch")
                .tooltips(Component.translatable("gtlmodular.overclock_hatch.tooltip",
                        fmt(pct)))
                .register();
    }

    /** 100.0 → "100"，25.0 → "25"，33.333→"33.33"，12.5→"12.5"。 */
    private static String fmt(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return Long.toString((long) v);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
