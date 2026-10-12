package com.gtlmodular.common.machine.nanochip.display;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class NanochipInputText {
    private static final String PREFIX = "gtlmodular.nanochip.thread_tuning.";

    private NanochipInputText() {}

    public static List<Component> lines(NanochipInputSnapshot snapshot) {
        List<Component> rows = new ArrayList<>();
        rows.add(line("title").copy().withStyle(ChatFormatting.GOLD));
        rows.add(line("effective", option(snapshot.optionsTick(), snapshot.fanOut()),
                option(snapshot.optionsTick(), snapshot.perSlot())));
        rows.add(line("options_tick", snapshot.optionsTick() < 0 ? line("unknown") : snapshot.optionsTick()));
        var sample = snapshot.sample();
        if (sample == null) {
            rows.add(line("no_sample"));
            return List.copyOf(rows);
        }
        rows.add(line("sample", sample.recipeId(), sample.tick(),
                line(sample.complete() ? "complete" : "incomplete")));
        rows.add(line("scope", sample.recipeTick(), sample.stockSlots(), sample.probeSlots()));
        for (var material : sample.materials()) {
            String unit = material.fluid() ? "fluid_amount" : "item_amount";
            rows.add(line("need", line(unit, material.name(), material.perCraft())));
            rows.add(line("first_slot", line(unit, material.name(), number(material.first()))));
            rows.add(line("all_slots", line(unit, material.name(), number(material.aggregate()))));
        }
        rows.add(line("slot_bounds", number(sample.firstParallel()), number(sample.bestParallel())));
        rows.add(line("bounds_tip"));
        return List.copyOf(rows);
    }

    private static Object number(Long number) {
        return number == null ? line("unknown") : number;
    }

    private static Component option(long tick, boolean value) {
        return line(tick < 0 ? "unknown" : value ? "on" : "off");
    }

    private static Component line(String key, Object... args) {
        return Component.translatable(PREFIX + key, args).withStyle(ChatFormatting.GRAY);
    }
}
