package com.gtlmodular.common.machine.nanochip.display;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.nanochip.NanochipColor;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

public final class NanochipLogisticsText {

    private NanochipLogisticsText() {
    }

    public static MutableComponent deprecated() {
        return Component.translatable("gtlmodular.nanochip.logistics.deprecated")
                .withStyle(ChatFormatting.YELLOW);
    }

    public static void appendMode(List<Component> text, boolean hasVacuumRouting) {
        boolean simple = NanochipModContext.config().isNanochipSimpleLogistics();
        text.add(Component.translatable(simple
                ? "gtlmodular.nanochip.logistics.simple"
                : "gtlmodular.nanochip.logistics.nh").withStyle(ChatFormatting.GRAY));
        if (!simple || hasVacuumRouting) {
            text.add(deprecated());
        }
    }

    public static MutableComponent vacuumRouting() {
        return Component.translatable(NanochipModContext.config().isVacuumRequiresPipes()
                        ? "gtlmodular.nanochip.vacuum.routing.pipes"
                        : "gtlmodular.nanochip.vacuum.routing.channel")
                .append(" · ").append(deprecated());
    }

    public static MutableComponent colorName(NanochipColor color) {
        return Component.translatable(color == null
                        ? "gtlmodular.nanochip.vacuum.color.none"
                        : "color.minecraft." + color.dye().getName())
                .withStyle(style -> style.withColor(color == null ? 0xFFFFFF : color.textColor()));
    }

    public static MutableComponent colorChanged(NanochipColor color) {
        // 动作栏宽度有限，完整推荐说明放在物品提示和配置页。
        return Component.translatable("gtlmodular.nanochip.vacuum.color.current", colorName(color))
                .append(" · ").append(Component.translatable("gtlmodular.nanochip.logistics.deprecated.short")
                        .withStyle(ChatFormatting.YELLOW));
    }
}
