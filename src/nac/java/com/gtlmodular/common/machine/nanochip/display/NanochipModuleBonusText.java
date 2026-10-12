package com.gtlmodular.common.machine.nanochip.display;

import com.gregtechceu.gtceu.api.GTValues;
import com.gtlmodular.common.machine.nanochip.NanochipCalibration.Family;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/** 保留可翻译组件直到客户端绘制，使专服和两种界面采用同一语言与贡献口径。 */
public final class NanochipModuleBonusText {
    private static final String PREFIX = "gtlmodular.nanochip.module_bonuses.";

    private NanochipModuleBonusText() {}

    public static List<Component> lines(NanochipModuleBonusSnapshot snapshot) {
        List<Component> lines = new ArrayList<>();
        lines.add(text("title").withStyle(ChatFormatting.GOLD));
        lines.add(text("timing_scope").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(text("full_mode", text(snapshot.fullMode() ? "enabled" : "disabled"),
                snapshot.tier() >= 0 && snapshot.tier() < GTValues.VN.length
                        ? GTValues.VN[snapshot.tier()] : Integer.toString(snapshot.tier()),
                snapshot.floorTicks()));
        lines.add(text("threads", snapshot.baseThreads(), snapshot.additionalThreads(), snapshot.totalThreads()));
        if (!snapshot.fullMode()) {
            lines.add(text("admission_limit", snapshot.directParallelLimit()));
        }
        lines.add(text("scope").withStyle(ChatFormatting.DARK_GRAY));
        if (snapshot.modules().isEmpty()) lines.add(text("none"));
        for (NanochipModuleBonusSnapshot.Module module : snapshot.modules()) {
            Component name = Component.translatableWithFallback(module.nameKey(), module.fallbackName());
            lines.add(text("entry", name, module.position()));
            lines.add(text("row", number(module.eu()), number(module.duration()),
                    text(module.formed() ? "formed" : "unformed")));
            if (!module.families().isEmpty()) {
                MutableComponent families = Component.empty();
                for (Family family : module.families()) {
                    if (!families.getSiblings().isEmpty()) families.append(" / ");
                    families.append(family(family));
                }
                lines.add(text("families", families));
            }
            if (module.organizerFamily() != null && module.formed()) {
                lines.add(text("water", number(module.waterStrength()), number(module.effectiveWaterStrength()),
                        family(module.organizerFamily()), text(module.fullCalibration() ? "enabled" : "disabled")));
                if (module.waterStrength() <= 0.0D) lines.add(text("inactive", text("no_water")));
            }
            if (module.board() && module.formed()) {
                lines.add(text("impurity", number(module.impurity()), number(module.impurityMultiplier())));
            }
            if (module.unlocksFullMode()) {
                lines.add(text("matrix", text(module.formed() ? "enabled" : "disabled")));
            }
            if (module.parallelFactor() != 1.0D) {
                lines.add(text("parallel_definition", number(module.parallelFactor()), module.parallelCount(),
                        snapshot.directParallelLimit()));
            }
        }
        lines.add(text("limits").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(text("floor", snapshot.floorTicks()).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(text("unclamped").withStyle(ChatFormatting.DARK_GRAY));
        for (NanochipModuleBonusSnapshot.Total total : snapshot.totals()) {
            lines.add(text("total", family(total.family()), number(total.eu()), number(total.duration())));
            if (total.rawEu() != total.eu() || total.rawDuration() != total.duration()) {
                lines.add(text("before_limits", number(total.rawEu()), number(total.rawDuration())));
            }
        }
        return List.copyOf(lines);
    }

    private static Component family(Family family) {
        return text("family." + family.key());
    }

    private static String number(double value) {
        return Double.isFinite(value) ? BigDecimal.valueOf(value).round(new MathContext(8))
                .stripTrailingZeros().toString() : Double.toString(value);
    }

    private static MutableComponent text(String suffix, Object... arguments) {
        return Component.translatable(PREFIX + suffix, arguments).withStyle(ChatFormatting.GRAY);
    }
}
