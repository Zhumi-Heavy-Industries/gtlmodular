package com.gtlmodular.client.jade;

import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

import static com.gtlmodular.client.jade.NanochipThreadedJadeProvider.KEY_CALIB_DUR_MUL;
import static com.gtlmodular.client.jade.NanochipThreadedJadeProvider.KEY_CALIB_EU_MUL;
import static com.gtlmodular.client.jade.NanochipThreadedJadeProvider.KEY_DISTINCT_RECIPES;
import static com.gtlmodular.client.jade.NanochipThreadedJadeProvider.KEY_OUTPUT_COUNT;
import static com.gtlmodular.client.jade.NanochipThreadedJadeProvider.KEY_OUTPUT_ID;

final class NanochipJadeDisplay {

    private static final String LANG_CALIB_REDUCTION = "gtlmodular.nanochip.jade.calibration_reduction";

    private static final String LANG_DISTINCT_RECIPES = "gtlmodular.nanochip.jade.distinct_recipes";

    private static final String LANG_OUTPUTS_HEADER = "gtlmodular.nanochip.jade.thread_outputs";

    private static final String LANG_OUTPUTS_LINE = "gtlmodular.nanochip.jade.thread_outputs.line";

    private static final String LANG_OUTPUTS_NONE = "gtlmodular.nanochip.jade.thread_outputs.none";

    private static final String GTCEU_DIVIDER_TEXT = "=========================";

    private static void filterUpstreamLines(ITooltip tooltip) {
        int size = tooltip.size();
        if (size <= 0) {
            return;
        }
        java.util.List<snownee.jade.api.ui.IElement> kept = new java.util.ArrayList<>(size);
        boolean dropped = false;
        for (int i = 0; i < size; i++) {

            java.util.List<snownee.jade.api.ui.IElement> row =
                    tooltip.get(i, snownee.jade.api.ui.IElement.Align.LEFT);
            if (row == null || row.isEmpty()) {
                continue;
            }
            StringBuilder joined = new StringBuilder();
            for (snownee.jade.api.ui.IElement part : row) {
                try {
                    String m = part == null ? null : part.getMessage();
                    if (m != null) {
                        joined.append(m);
                    }
                } catch (RuntimeException ignored) {

                }
            }
            if (isUpstreamParallelLine(joined.toString())) {
                dropped = true;
                continue;
            }
            kept.addAll(row);
        }
        if (!dropped) {
            return;
        }
        tooltip.clear();

        tooltip.add(kept);
    }

    private static boolean isUpstreamParallelLine(String message) {
        if (message.isEmpty()) {
            return false;
        }

        if ((message.startsWith("同时处理至多") && message.endsWith("个配方"))
                || (message.startsWith("Simultaneously processes up to") && message.endsWith("recipes"))) {
            return true;
        }

        if (message.startsWith("拥有") && message.endsWith("个跨配方线程")) {
            return true;
        }
        return message.contains("cross-recipe thread") || message.contains("cross recipe thread");
    }

    // 仅追加自己的行，重建 tooltip 会破坏其他 provider 的图标与布局。
    static void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        try {
            if (tooltip == null || accessor == null) {
                return;
            }
            CompoundTag data = accessor.getServerData();
            if (data == null || !data.contains(NanochipJadeKeys.KEY_THREAD_LIMIT)) {
                return;
            }

            int limit = data.getInt(NanochipJadeKeys.KEY_THREAD_LIMIT);
            int running = data.getInt(NanochipJadeKeys.KEY_THREAD_RUNNING);
            tooltip.add(Component.translatable(NanochipThreadedRecipeLogic.LANG_THREADS, limit, running));

            appendDistinctRecipes(tooltip, data);
            if (running <= 0) {

                appendCalibrationReduction(tooltip, data);
                return;
            }
            tooltip.add(Component.translatable(NanochipThreadedRecipeLogic.LANG_THREADS_RUNS,
                    FormattingUtil.formatNumbers(data.getLong(NanochipJadeKeys.KEY_THREAD_RUNS))));

            if (NanochipJadeThreadRows.OUR_OWN_BARS
                    || !NanochipJadeThreadRows.NATIVE_BAR_FOR_FIRST_THREAD) {

                appendGtceuDivider(tooltip);
                NanochipJadeThreadRows.appendAllRows(tooltip, data);
            }

            appendThreadOutputs(tooltip, data);

            appendCalibrationReduction(tooltip, data);
        } catch (Throwable t) {

            NanochipModContext.logger().debug("[gtlmodular] NAC Jade 线程状态显示失败（本次跳过）：{}", t.toString());
        }
    }

    private static void appendCalibrationReduction(ITooltip tooltip, CompoundTag data) {

        if (!data.contains(KEY_CALIB_EU_MUL) || !data.contains(KEY_CALIB_DUR_MUL)) {
            return;
        }
        double euMul = data.getDouble(KEY_CALIB_EU_MUL);
        double durMul = data.getDouble(KEY_CALIB_DUR_MUL);

        tooltip.add(Component.translatable(LANG_CALIB_REDUCTION,
                percentOfReduction(euMul), percentOfReduction(durMul)));
    }

    private static void appendDistinctRecipes(ITooltip tooltip, CompoundTag data) {

        if (!data.contains(KEY_DISTINCT_RECIPES)) {
            return;
        }
        int threads = data.getInt(NanochipJadeKeys.KEY_THREAD_RUNNING);
        int distinct = data.getInt(KEY_DISTINCT_RECIPES);
        tooltip.add(Component.translatable(LANG_DISTINCT_RECIPES, threads, distinct));
    }

    private static void appendGtceuDivider(ITooltip tooltip) {
        tooltip.add(Component.literal(GTCEU_DIVIDER_TEXT));
    }

    private static void appendThreadOutputs(ITooltip tooltip, CompoundTag data) {

        if (!data.contains(NanochipJadeKeys.KEY_THREAD_LIST)) {
            return;
        }
        int running = data.getInt(NanochipJadeKeys.KEY_THREAD_RUNNING);
        if (running <= 0) {
            return;
        }
        ListTag list = data.getList(NanochipJadeKeys.KEY_THREAD_LIST, Tag.TAG_COMPOUND);

        if (list.size() <= 0) {
            return;
        }

        appendGtceuDivider(tooltip);
        tooltip.add(Component.translatable(LANG_OUTPUTS_HEADER));
        int listed = 0;
        for (int i = 0; i < list.size() && listed < NanochipJadeKeys.OUTPUT_LINES; i++) {
            CompoundTag entry = list.getCompound(i);

            String outputId = entry.getString(KEY_OUTPUT_ID);
            long count = entry.getLong(KEY_OUTPUT_COUNT);
            if (outputId.isEmpty() || count <= 0L) {
                tooltip.add(Component.translatable(LANG_OUTPUTS_NONE, i));
            } else {

                IElement icon = itemIconOf(tooltip, outputId);
                if (icon != null) {
                    tooltip.add(icon);
                }

                Component line = Component.translatable(LANG_OUTPUTS_LINE, i,
                        FormattingUtil.formatNumbers(count),
                        displayNameOf(outputId));
                if (icon != null) {

                    tooltip.append(line);
                } else {

                    tooltip.add(line);
                }
            }
            listed++;
        }

        if (listed < running) {
            tooltip.add(Component.translatable(NanochipJadeThreadRows.LANG_MORE_UNLISTED, running - listed));
        }
    }

    private static IElement itemIconOf(ITooltip tooltip, String outputId) {
        if (outputId == null || outputId.isEmpty()) {
            return null;
        }
        try {

            ResourceLocation id = ResourceLocation.tryParse(outputId);
            if (id == null) {
                return null;
            }

            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(id);
            if (item == null) {
                return null;
            }

            ItemStack stack = new ItemStack(item);
            if (stack.isEmpty()) {
                return null;
            }

            IElementHelper helper = tooltip.getElementHelper();
            return helper == null ? null : helper.item(stack);
        } catch (Throwable t) {

            return null;
        }
    }

    private static String translatedNameOf(String descriptionId) {
        try {
            return Component.translatable(descriptionId).getString();
        } catch (RuntimeException e) {
            return descriptionId;
        }
    }

    private static String displayNameOf(String outputId) {
        String byRegistry = descriptionIdOfRegistryName(outputId);
        return translatedNameOf(byRegistry == null ? outputId : byRegistry);
    }

    private static String descriptionIdOfRegistryName(String outputId) {
        if (outputId == null || outputId.isEmpty()) {
            return null;
        }
        try {
            ResourceLocation id = ResourceLocation.tryParse(outputId);
            if (id == null) {
                return null;
            }
            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(id);
            if (item == null) {
                return null;
            }
            ItemStack stack = new ItemStack(item);
            return stack.isEmpty() ? null : stack.getDescriptionId();
        } catch (Throwable t) {
            return null;
        }
    }

    // 负减免表示额外耗能，不能钳成零而掩盖真实倍率。
    private static String percentOfReduction(double multiplier) {
        return String.format(java.util.Locale.ROOT, "%.1f％", (1.0D - multiplier) * 100.0D);
    }
}
