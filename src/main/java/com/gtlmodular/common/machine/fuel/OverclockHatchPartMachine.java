package com.gtlmodular.common.machine.fuel;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredPartMachine;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * ★ <b>超频仓</b>（{@code gtlmodular:overclock_hatch_*}）—— 把机器超频的「时间倍率」调到更激进的值。
 *
 * <p>对应 GTO {@code gtocore:overclock_hatch_*}：每升一档仍使用 4 倍功率，但耗时倍率由仓决定：
 * {@code durationFactor = 100 / (tier - 6) / 100}。UV=0.50、UEV=0.25（无损）、MAX=0.125（越超越省电）。
 *
 * <p>本仓只回答「时间倍率」，真正的超频执行交给 {@link OverclockProfiles#apply}
 * （内部构造 {@code new OverclockingLogic(durationFactor, 4.0, true)}）。
 *
 * @see IOverclockHatchPart
 * @author 小爱与大肥鱼
 */
public class OverclockHatchPartMachine extends TieredPartMachine implements IMultiPart, IOverclockHatchPart {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(OverclockHatchPartMachine.class, MultiblockPartMachine.MANAGED_FIELD_HOLDER);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    public OverclockHatchPartMachine(IMachineBlockEntity holder, int tier) {
        super(holder, tier);
    }

    // ------------------------------------------------------------------
    // 档位数值
    // ------------------------------------------------------------------

    /**
     * 某档的耗时倍率。
     *
     * <p>GTO 原式是 {@code 1/(tier-6)}（UV=0.5、UEV=0.25、MAX=0.125）。
     * 但 GTCEu 的有损超频默认就是 ×0.5（比 GTO 的 ×0.55 快），若 UV 也取 0.5 则装了等于没装
     * ⇒ <b>UV 特例取 0.45</b>，保证在 GTCEu 有损超频上也有提升。
     * 其余档位保持 GTO 原值（都比 0.5 更小，本身就是提升）。
     *
     * @param tier 电压档位（UV=8 … MAX=14）
     */
    public static double durationFactorForTier(int tier) {
        if (tier <= GTValues.UV) {
            return 0.45; // UV（及更低）：GTCEu 有损=0.5，取 0.45 才有效
        }
        int d = Math.max(1, tier - 6);
        return 1.0 / d;
    }

    @Override
    public double getOverclockDurationFactor() {
        return durationFactorForTier(getTier());
    }

    // ------------------------------------------------------------------
    // 界面 / 提示
    // ------------------------------------------------------------------

    /** 与 GTO 对齐：加速/超频仓不可被多台多方块共享。 */
    @Override
    public boolean canShared() {
        return false;
    }

    @Override
    public void addMultiText(List<Component> textList) {
        textList.add(Component.translatable("gtlmodular.overclock_hatch.display",
                        num(getOverclockDurationFactor() * 100.0))
                .withStyle(ChatFormatting.AQUA));
    }

    private static String num(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return Long.toString((long) v);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
