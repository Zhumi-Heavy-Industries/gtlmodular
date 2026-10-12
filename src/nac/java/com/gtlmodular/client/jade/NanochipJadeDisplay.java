package com.gtlmodular.client.jade;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.diag.SwallowedErrors;
import com.gtlmodular.common.machine.display.ThreadDisplaySnapshot;
import com.gtlmodular.common.machine.display.ThreadDisplayText;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
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

final class NanochipJadeDisplay {

    private static final String LANG_CALIB_REDUCTION = "gtlmodular.nanochip.jade.calibration_reduction";

    private static final String LANG_DISTINCT_RECIPES = "gtlmodular.nanochip.jade.distinct_recipes";

    private static final String LANG_OUTPUTS_HEADER = "gtlmodular.nanochip.jade.thread_outputs";

    private static final String LANG_OUTPUTS_LINE = "gtlmodular.nanochip.jade.thread_outputs.line";

    private static final String LANG_OUTPUTS_NONE = "gtlmodular.nanochip.jade.thread_outputs.none";

    private static final String GTCEU_DIVIDER_TEXT = "=========================";

    // 仅追加自己的行，重建 tooltip 会破坏其他 provider 的图标与布局。
    static void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        try {
            if (tooltip == null || accessor == null) {
                return;
            }
            CompoundTag data = accessor.getServerData();
            if (!NanochipJadeThreadData.isIndependentThreaded(accessor)) {
                return;
            }

            ThreadDisplaySnapshot snapshot = NanochipJadeThreadData.read(data);
            ThreadDisplayText.summary(snapshot).forEach(tooltip::add);
            appendDistinctRecipes(tooltip, snapshot);
            if (snapshot.running() != null && snapshot.running() <= 0) {
                appendCalibrationReduction(tooltip, data);
                return;
            }

            if (NanochipJadeThreadRows.OUR_OWN_BARS
                    || !NanochipJadeThreadRows.NATIVE_BAR_FOR_FIRST_THREAD) {

                appendGtceuDivider(tooltip);
                NanochipJadeThreadRows.appendAllRows(tooltip, snapshot);
            }

            appendThreadOutputs(tooltip, snapshot);

            appendCalibrationReduction(tooltip, data);
        } catch (Throwable t) {
            SwallowedErrors.report("NanochipJadeDisplay#appendTooltip", t);

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

    private static void appendDistinctRecipes(ITooltip tooltip, ThreadDisplaySnapshot snapshot) {
        if (snapshot.distinctRecipes() == null) {
            return;
        }
        tooltip.add(Component.translatable(LANG_DISTINCT_RECIPES,
                ThreadDisplayText.number(snapshot.running()), ThreadDisplayText.number(snapshot.distinctRecipes())));
    }

    private static void appendGtceuDivider(ITooltip tooltip) {
        tooltip.add(Component.literal(GTCEU_DIVIDER_TEXT));
    }

    private static void appendThreadOutputs(ITooltip tooltip, ThreadDisplaySnapshot snapshot) {
        if (snapshot.threads().isEmpty()) {
            return;
        }
        appendGtceuDivider(tooltip);
        tooltip.add(Component.translatable(LANG_OUTPUTS_HEADER));
        int listed = 0;
        for (ThreadDisplaySnapshot.Thread thread : snapshot.threads()) {
            if (listed >= NanochipJadeKeys.OUTPUT_LINES) {
                break;
            }
            String outputId = thread.outputId();
            Long count = thread.outputCount();
            Component label = ThreadDisplayText.number(thread.label());
            if (outputId == null) {
                tooltip.add(Component.translatable(LANG_OUTPUTS_NONE, label));
            } else {
                IElement icon = itemIconOf(tooltip, outputId);
                if (icon != null) {
                    tooltip.add(icon);
                }
                Component line = Component.translatable(LANG_OUTPUTS_LINE, label,
                        ThreadDisplayText.number(count), displayNameOf(outputId));
                if (icon != null) {
                    tooltip.append(line);
                } else {
                    tooltip.add(line);
                }
            }
            listed++;
        }
        if (snapshot.running() != null && listed < snapshot.running()) {
            tooltip.add(ThreadDisplayText.more(snapshot.running() - listed));
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
            SwallowedErrors.report("NanochipJadeDisplay#itemIconOf", t);

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
            SwallowedErrors.report("NanochipJadeDisplay#descriptionIdOfRegistryName", t);
            return null;
        }
    }

    // 负减免表示额外耗能，不能钳成零而掩盖真实倍率。
    private static String percentOfReduction(double multiplier) {
        return String.format(java.util.Locale.ROOT, "%.1f％", (1.0D - multiplier) * 100.0D);
    }
}
