package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/** 主机与仓室复用同一份校准面板，避免显示口径和交互逻辑分歧。 */
public final class NanochipCalibrationPanel {

    private NanochipCalibrationPanel() {}

    /** 默认使用分档布局；关闭后采用 legacySection，以保留另一种界面尺寸选择。 */
    public static final boolean NEW_LAYOUT = true;

    private static final String ICON = "gtceu:textures/item/portable_debug_scanner.png";

    private static final int WIDTH = 152;

    private static final int LABEL_H = 10;
    private static final int ROW_GAP = 3;
    private static final int SECTION_GAP = 6;
    private static final int PAD_Y = 3;
    private static final int STRIPE_W = 2;
    private static final int BAND_PAD_X = 4;
    private static final int BAND_TEXT_X = BAND_PAD_X + STRIPE_W;
    private static final int FAMILY_COL_W = 54;
    private static final int FAMILY_GAP_X = 2;
    private static final int SEG_X = BAND_TEXT_X + FAMILY_COL_W + FAMILY_GAP_X;
    private static final int SEG_W = 13;
    private static final int SEG_GAP = 2;
    private static final int SEG_H = 7;
    private static final int SEG_STRIDE = SEG_W + SEG_GAP;
    /** 基础电路的历史条与档位格使用同列宽度，便于比较各族。 */
    private static final int SEG_SPAN = 3 * SEG_W + 2 * SEG_GAP;
    private static final int STATE_X = SEG_X + SEG_SPAN + 5;

    private static final int TITLE_Y = 2;
    private static final int BAR_X = 4;
    private static final int BAR_Y = 14;
    private static final int BAR_W = 144;
    private static final int BAR_H = 8;
    private static final int HIST_Y = 23;
    private static final int DEN_Y = 33;

    private static final int BAND_Y = DEN_Y + LABEL_H + SECTION_GAP;
    private static final int BAND_TITLE_Y = BAND_Y + PAD_Y;
    private static final int FAMILY_ROW0_Y = BAND_TITLE_Y + LABEL_H + ROW_GAP;
    private static final int FAMILY_PITCH = LABEL_H;

    private static final int EFF_X = 4;
    private static final int EFF_W = WIDTH - 2 * EFF_X;
    private static final int EFF_TEXT_X = EFF_X + 2;
    private static final int EFF_ROWS = 3;

    private static final int BTN_X = 4;
    private static final int BTN_W = WIDTH - 2 * BTN_X;
    private static final int BTN_H = 18;
    private static final int ARMED_MARK_H = 2;

    private static final int COL_PANEL = 0x33000000;
    private static final int COL_PANEL_INNER = 0x26000000;
    private static final int COL_ACCENT = 0xCC66CCFF;
    private static final int COL_DANGER = 0x40FF5555;
    private static final int COL_NONE = 0x00000000;

    private static final String LIT_BAND_TITLE = "§8各族校准（3 格 ＝ 第 1/2/3 档）";
    private static final String LIT_NOT_YET = "未达";
    private static final String LIT_HISTORY_ONLY = "仅历史";
    private static final String LIT_ACTIVE = "生效";
    private static final String LIT_TIER_PRE = "第 ";
    private static final String LIT_TIER_POST = " 档";
    private static final String LIT_GAP = "还差 ";
    private static final String LIT_GAP_MID = " 个到 ";
    private static final String LIT_EU = "EU";
    private static final String LIT_TIME = "时长";
    private static final String LIT_T3 = "特效";
    private static final String LIT_T3_ON = "已生效";
    private static final String LIT_NO_TIER1 = "§8还没有任何族达到第 1 档";
    private static final String LIT_SHARE = "§7占比 §b";
    private static final String LIT_NONE = "§8无";
    /** 未知数量须与真实零值区分，否则会显示误导性的下一档缺口。 */
    private static final long UNKNOWN_FAMILY_COUNT = -1L;

    public static WidgetGroup section(Supplier<Double> progress, Supplier<Long> count,
                                      IntFunction<Double> share, Supplier<Boolean> fullWindow) {
        return section(progress, count, share, fullWindow, () -> -1, () -> 0.0D,
                ordinal -> UNKNOWN_FAMILY_COUNT, null);
    }

    public static WidgetGroup section(Supplier<Double> progress, Supplier<Long> count,
                                      IntFunction<Double> share, Supplier<Boolean> fullWindow,
                                      Supplier<Integer> activeOrdinal, Supplier<Double> denominator,
                                      IntFunction<Long> familyCount) {
        return section(progress, count, share, fullWindow, activeOrdinal, denominator, familyCount, null);
    }

    public static WidgetGroup section(Supplier<Double> progress, Supplier<Long> count,
                                      IntFunction<Double> share, Supplier<Boolean> fullWindow,
                                      Supplier<Integer> activeOrdinal, Supplier<Double> denominator,
                                      IntFunction<Long> familyCount, Runnable clearAction) {
        // 未提供逐族档位时返回未知值，避免缺少数据被画成未校准。
        return section(progress, count, share, fullWindow, activeOrdinal, denominator, familyCount,
                ordinal -> -1, clearAction);
    }

    /** 档位由调用方提供的服务端同步值决定，界面不能另算一套判定。 */
    public static WidgetGroup section(Supplier<Double> progress, Supplier<Long> count,
                                      IntFunction<Double> share, Supplier<Boolean> fullWindow,
                                      Supplier<Integer> activeOrdinal, Supplier<Double> denominator,
                                      IntFunction<Long> familyCount, IntFunction<Integer> tierOf,
                                      Runnable clearAction) {
        if (!NEW_LAYOUT) {
            return legacySection(progress, count, share, fullWindow, activeOrdinal, denominator,
                    familyCount, clearAction);
        }
        return newSection(progress, count, share, fullWindow, activeOrdinal, denominator,
                familyCount, tierOf, clearAction);
    }

    private static WidgetGroup newSection(Supplier<Double> progress, Supplier<Long> count,
                                          IntFunction<Double> share, Supplier<Boolean> fullWindow,
                                          Supplier<Integer> activeOrdinal, Supplier<Double> denominator,
                                          IntFunction<Long> familyCount, IntFunction<Integer> tierOf,
                                          Runnable clearAction) {
        NanochipCalibration.Family[] families = NanochipCalibration.Family.barFamilies();
        int bandH = PAD_Y + LABEL_H + ROW_GAP + families.length * FAMILY_PITCH + PAD_Y;
        int bandBottom = BAND_Y + bandH;
        int effY = bandBottom + SECTION_GAP;
        int effH = 2 * PAD_Y + EFF_ROWS * LABEL_H + (EFF_ROWS - 1) * ROW_GAP;
        int effBottom = effY + effH;
        int btnY = effBottom + SECTION_GAP;
        int height = clearAction == null ? effBottom + PAD_Y : btnY + BTN_H + PAD_Y;
        WidgetGroup group = new WidgetGroup(0, 0, WIDTH, height);

        group.addWidget(new LabelWidget(4, TITLE_Y,
                Component.translatable("gtlmodular.nanochip.calibration.header").getString()));
        group.addWidget(new ProgressWidget(progress::get, BAR_X, BAR_Y, BAR_W, BAR_H,
                new ProgressTexture(barTexture(0x40000000), barTexture(0xFF3FBFBF)))
                .setDynamicHoverTips(v -> String.format(java.util.Locale.ROOT, "%.1f％", v * 100.0D)));
        group.addWidget(new LabelWidget(4, HIST_Y, () -> String.format(java.util.Locale.ROOT,
                "历史 %s / %s 个电路",
                com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(count.get()),
                com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(
                        (long) NanochipCalibration.HISTORY_SIZE))));
        group.addWidget(new LabelWidget(4, DEN_Y,
                () -> Component.translatable(NanochipCalibration
                        .denominatorModeKey(fullWindow.get())).getString())
                .setHoverTooltips(Component.translatable("gtlmodular.nanochip.calibration.denominator.tip")));

        group.addWidget(new ImageWidget(0, BAND_Y, WIDTH, bandH, barTexture(COL_PANEL)));
        group.addWidget(new ImageWidget(0, BAND_Y, STRIPE_W, bandH, barTexture(COL_ACCENT)));
        group.addWidget(new LabelWidget(BAND_TEXT_X, BAND_TITLE_Y, LIT_BAND_TITLE)
                .setHoverTooltips(Component.translatable("gtlmodular.nanochip.calibration.single")));

        for (int i = 0; i < families.length; i++) {
            NanochipCalibration.Family family = families[i];
            final int ordinal = family.ordinal();
            final int y = FAMILY_ROW0_Y + i * FAMILY_PITCH;
            final int segY = y + (LABEL_H - SEG_H) / 2;

            // ProgressWidget 能持续读取当前生效族，使强调条随状态变化。
            group.addWidget(new ProgressWidget(
                    () -> activeOrdinal.get() == ordinal ? 1.0D : 0.0D,
                    STRIPE_W, y, STRIPE_W, LABEL_H,
                    new ProgressTexture(barTexture(COL_NONE), barTexture(COL_ACCENT))));

            group.addWidget(new LabelWidget(BAND_TEXT_X, y, family.nameZh())
                    .setHoverTooltips(rowHover(family, ordinal, share, familyCount, denominator, tierOf)));

            if (family.calibratable()) {
                for (int seg = 0; seg < 3; seg++) {
                    final int tier = seg + 1;
                    group.addWidget(new ProgressWidget(
                            () -> segmentFill(tierOf.apply(ordinal), share.apply(ordinal), family, tier),
                            SEG_X + seg * SEG_STRIDE, segY, SEG_W, SEG_H,
                            new ProgressTexture(barTexture(COL_PANEL_INNER),
                                    barTexture(0xFF000000 | family.color())))
                            .setDynamicHoverTips(v -> cellTip(family, tier, v)));
                }
            } else {
                group.addWidget(new ProgressWidget(() -> share.apply(ordinal), SEG_X, segY, SEG_SPAN, SEG_H,
                        new ProgressTexture(barTexture(COL_PANEL_INNER),
                                barTexture(0xFF000000 | family.color())))
                        .setDynamicHoverTips(v -> cellTip(family, 0, v)));
            }

            group.addWidget(new LabelWidget(STATE_X, y,
                    () -> familyStateText(tierOf.apply(ordinal), ordinal, family, activeOrdinal.get()))
                    .setHoverTooltips(rowHover(family, ordinal, share, familyCount, denominator, tierOf)));
        }

        group.addWidget(new ImageWidget(EFF_X, effY, EFF_W, effH, barTexture(COL_PANEL_INNER)));
        group.addWidget(new LabelWidget(EFF_TEXT_X, effY + PAD_Y, () -> activeFamilyLine(activeOrdinal.get()))
                .setHoverTooltips(Component.translatable("gtlmodular.nanochip.calibration.active.none")));
        group.addWidget(new LabelWidget(EFF_TEXT_X, effY + PAD_Y + LABEL_H + ROW_GAP,
                () -> effectsLine(activeOrdinal.get(), tierOf))
                .setHoverTooltips(Component.translatable("gtlmodular.nanochip.calibration.effects.tip")));
        group.addWidget(new LabelWidget(EFF_TEXT_X, effY + PAD_Y + 2 * (LABEL_H + ROW_GAP),
                () -> nextTierShort(activeOrdinal.get(), denominator.get(),
                        activeOrdinal.get() < 0 ? 0L : familyCount.apply(activeOrdinal.get()), tierOf))
                .setHoverTooltips(Component.translatable("gtlmodular.nanochip.calibration.single")));

        if (clearAction != null) {
            ImageWidget armedMark = new ImageWidget(BTN_X, btnY + BTN_H, BTN_W, ARMED_MARK_H,
                    barTexture(COL_DANGER));
            armedMark.setVisible(clearArmed());
            group.addWidget(armedMark);
            // 文字随确认状态读取，避免按钮显示与实际点击行为不一致。
            TextTexture text = new TextTexture(() -> Component.translatable(clearArmed()
                    ? "gtlmodular.nanochip.calibration.reset.armed"
                    : "gtlmodular.nanochip.calibration.reset").getString());
            text.setWidth(BTN_W);
            // 通过持有器让点击回调更新悬停，避免 lambda 捕获尚未初始化的按钮。
            HoverSlot slot = new HoverSlot();
            ButtonWidget button = new ButtonWidget(BTN_X, btnY, BTN_W, BTN_H,
                    new GuiTextureGroup(GuiTextures.BUTTON, text),
                    data -> onClearClick(data, clearAction, armedMark, slot));
            slot.widget = button;
            button.setHoverTooltips(Component.translatable(clearArmed()
                    ? "gtlmodular.nanochip.calibration.reset.armed.tip"
                    : "gtlmodular.nanochip.calibration.reset.tip"));
            group.addWidget(button);
        }
        return group;
    }

    private static WidgetGroup legacySection(Supplier<Double> progress, Supplier<Long> count,
                                             IntFunction<Double> share, Supplier<Boolean> fullWindow,
                                             Supplier<Integer> activeOrdinal, Supplier<Double> denominator,
                                             IntFunction<Long> familyCount, Runnable clearAction) {
        NanochipCalibration.Family[] families = NanochipCalibration.Family.barFamilies();
        int rowH = 10;
        int rowsTop = 44;
        int extraTop = rowsTop + families.length * rowH + 2;
        int buttonY = extraTop + 3 * rowH + 2;
        int height = buttonY + (clearAction == null ? 2 : 18);
        WidgetGroup group = new WidgetGroup(0, 0, WIDTH, height);
        group.addWidget(new LabelWidget(4, 2,
                Component.translatable("gtlmodular.nanochip.calibration.header").getString()));

        group.addWidget(new ProgressWidget(progress::get, 4, 14, 144, 8,
                new ProgressTexture(barTexture(0x40000000), barTexture(0xFF3FBFBF)))
                .setDynamicHoverTips(v -> String.format(java.util.Locale.ROOT, "%.1f％", v * 100.0D)));
        group.addWidget(new LabelWidget(4, 23, () -> String.format(
                java.util.Locale.ROOT, "历史 %d / %d 个电路",
                count.get(), NanochipCalibration.HISTORY_SIZE)));
        group.addWidget(new LabelWidget(4, 33,
                () -> Component.translatable(NanochipCalibration
                        .denominatorModeKey(fullWindow.get())).getString())
                .setHoverTooltips(Component.translatable("gtlmodular.nanochip.calibration.denominator.tip")));

        for (int i = 0; i < families.length; i++) {
            NanochipCalibration.Family family = families[i];
            int ordinal = family.ordinal();
            int y = rowsTop + i * rowH;
            group.addWidget(new LabelWidget(4, y, family.nameZh()));
            group.addWidget(new ProgressWidget(() -> share.apply(ordinal), 62, y, 50, 7,
                    new ProgressTexture(barTexture(0x30000000), barTexture(0xFF000000 | family.color())))
                    .setDynamicHoverTips(v -> String.format(java.util.Locale.ROOT, "%s %.2f％",
                            family.nameZh(), v * 100.0D)));
            group.addWidget(new LabelWidget(116, y, () -> String.format(
                    java.util.Locale.ROOT, "%.1f％", share.apply(ordinal) * 100.0D)));
        }

        group.addWidget(new LabelWidget(4, extraTop, () -> activeFamilyLineLegacy(activeOrdinal.get()))
                .setHoverTooltips(Component.translatable("gtlmodular.nanochip.calibration.single")));
        group.addWidget(new LabelWidget(4, extraTop + rowH,
                () -> effectsLineLegacy(activeOrdinal.get()))
                .setHoverTooltips(Component.translatable("gtlmodular.nanochip.calibration.effects.tip")));
        group.addWidget(new LabelWidget(4, extraTop + 2 * rowH, () -> {
            int ordinal = activeOrdinal.get();
            double den = denominator.get();
            long have = ordinal < 0 ? 0L : familyCount.apply(ordinal);
            return nextTierLineLegacy(ordinal, den, have);
        }));

        if (clearAction != null) {
            group.addWidget(new ButtonWidget(4, buttonY, WIDTH - 8, 16,
                    new ColorRectTexture(0xFF3A3A3A),
                    data -> onClearClick(data, clearAction))
                    .setHoverTooltips(Component.translatable(clearArmed()
                            ? "gtlmodular.nanochip.calibration.reset.armed.tip"
                            : "gtlmodular.nanochip.calibration.reset.tip")));
            group.addWidget(new LabelWidget(0, buttonY + 4, () -> Component.translatable(clearArmed()
                    ? "gtlmodular.nanochip.calibration.reset.armed"
                    : "gtlmodular.nanochip.calibration.reset").getString())
                    .setTextColor(0xFFFFFF));
        }
        return group;
    }

    private static double segmentFill(int syncedTier, double share, NanochipCalibration.Family family,
                                      int tier) {
        if (syncedTier >= tier) {
            return 1.0D;
        }
        if (syncedTier != tier - 1) {
            return 0.0D;
        }
        double lo = tier == 1 ? 0.0D : family.threshold(tier - 1);
        double hi = family.threshold(tier);
        if (hi <= lo) {
            return 1.0D;
        }
        double t = (share - lo) / (hi - lo);
        return t < 0.0D ? 0.0D : Math.min(1.0D, t);
    }

    private static String familyStateText(int tier, int ordinal, NanochipCalibration.Family family,
                                          int activeOrdinal) {
        if (!family.calibratable()) {
            return "§8" + LIT_HISTORY_ONLY;
        }
        if (activeOrdinal == ordinal) {
            return "§b§l" + LIT_ACTIVE + "§r §b" + tier + LIT_TIER_POST;
        }
        if (tier >= 1) {
            return "§8" + LIT_TIER_PRE + "§b" + tier + "§8" + LIT_TIER_POST;
        }
        return "§8" + LIT_NOT_YET;
    }

    private static Component[] rowHover(NanochipCalibration.Family family, int ordinal,
                                        IntFunction<Double> share, IntFunction<Long> familyCount,
                                        Supplier<Double> denominator, IntFunction<Integer> tierOf) {
        double s = share.apply(ordinal);
        long have = familyCount.apply(ordinal);
        double den = denominator.get();
        String pct = String.format(java.util.Locale.ROOT, "%.2f", s * 100.0D) + "％";
        String line1 = "§7" + family.nameZh() + " §8· §7" + LIT_SHARE + pct
                + (den > 0.0D
                        ? "§7（" + com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(have)
                                + " / " + com.gregtechceu.gtceu.utils.FormattingUtil
                                        .formatNumbers((long) Math.ceil(den)) + "）"
                        : "§7（窗口里还没有记录）");
        String line2 = nextTierLine(ordinal, den, have, tierOf);
        return new Component[] {
                Component.literal(line1),
                Component.literal(line2.isEmpty() ? "§8" + LIT_HISTORY_ONLY : line2)
        };
    }

    private static String cellTip(NanochipCalibration.Family family, int tier, double value) {
        double pctShown = value * 100.0D;
        if (tier == 0) {
            return String.format(java.util.Locale.ROOT,
                    "§7历史占比 §b%.2f％§8（本族只进历史条，不参与校准）", pctShown);
        }
        return String.format(java.util.Locale.ROOT, "§7第 %d 档阈值 §b%.0f％§8（本格 = 该档进度）",
                tier, family.threshold(tier) * 100.0D);
    }

    /** 无生效族时用短文本，避免超出面板宽度。 */
    private static String activeFamilyLine(int ordinal) {
        NanochipCalibration.Family family = familyByOrdinal(ordinal);
        if (family == null) {
            return Component.translatable("gtlmodular.nanochip.calibration.active", LIT_NONE).getString();
        }
        return Component.translatable("gtlmodular.nanochip.calibration.active",
                "§b§l" + family.nameZh()).getString();
    }

    private static String activeFamilyLineLegacy(int ordinal) {
        NanochipCalibration.Family family = familyByOrdinal(ordinal);
        if (family == null) {
            return Component.translatable("gtlmodular.nanochip.calibration.active.none").getString();
        }
        return Component.translatable("gtlmodular.nanochip.calibration.active", family.nameZh()).getString();
    }

    /** 可见行用短标签，完整效果放在悬停中，避免面板溢出。 */
    private static String effectsLine(int ordinal, IntFunction<Integer> tierOf) {
        NanochipCalibration.Family family = familyByOrdinal(ordinal);
        if (family == null) {
            return Component.translatable("gtlmodular.nanochip.calibration.effects.none").getString();
        }
        int tier = tierOf.apply(ordinal);
        if (tier < 1) {
            return Component.translatable("gtlmodular.nanochip.calibration.effects.none").getString();
        }
        String tail;
        if (family.specialStyleEffects()) {
            double time = switch (tier) {
                case 1 -> 0.80D;
                case 2 -> 0.60D;
                default -> 0.50D;
            };
            tail = "§a" + LIT_TIME + "§b§l" + mult(time);
            if (tier >= 3) {
                tail = tail + "§r§8/§a" + LIT_T3 + "§b§l" + LIT_T3_ON;
            }
        } else if (tier >= 3) {
            tail = "§a" + LIT_EU + "§b§l" + mult(0.8D) + "§r§8/§a" + LIT_T3 + "§b§l" + LIT_T3_ON;
        } else if (tier == 2) {
            tail = "§a" + LIT_EU + "§b§l" + mult(0.8D) + "§r§8/§a" + LIT_TIME + "§b§l" + mult(0.8D);
        } else {
            tail = "§a" + LIT_EU + "§b§l" + mult(0.8D);
        }
        return Component.translatable("gtlmodular.nanochip.calibration.effects", tail).getString();
    }

    private static String effectsLineLegacy(int ordinal) {
        NanochipCalibration.Family family = familyByOrdinal(ordinal);
        if (family == null) {
            return Component.translatable("gtlmodular.nanochip.calibration.effects.none").getString();
        }
        return Component.translatable("gtlmodular.nanochip.calibration.effects",
                Component.translatable(family.specialStyleEffects()
                        ? "gtlmodular.nanochip.calibration.effect.duration.special"
                        : "gtlmodular.nanochip.calibration.effect.duration.normal",
                        family.nameZh())).getString();
    }

    private static String mult(double m) {
        return "×" + String.format(java.util.Locale.ROOT, "%.1f", m);
    }

    private static String nextTierShort(int ordinal, double denominator, long familyCount,
                                        IntFunction<Integer> tierOf) {
        NanochipCalibration.Family family = familyByOrdinal(ordinal);
        if (family == null) {
            return LIT_NO_TIER1;
        }
        if (!family.calibratable()) {
            return "§8本族只进历史条，不参与校准";
        }
        int tier = tierOf.apply(ordinal);
        if (tier >= 3 || denominator <= 0.0D) {
            return Component.translatable("gtlmodular.nanochip.calibration.next.max").getString();
        }
        int next = tier + 1;
        double needPct = family.threshold(next);
        if (needPct <= 0.0D) {
            return Component.translatable("gtlmodular.nanochip.calibration.next.max").getString();
        }
        long needTotal = (long) Math.ceil(needPct * denominator);
        long missing = Math.max(0L, needTotal - familyCount);
        return "§8" + LIT_GAP + "§b"
                + com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(missing)
                + "§8" + LIT_GAP_MID + "§b" + LIT_TIER_PRE + next + LIT_TIER_POST;
    }

    /** 下一档基于同步档位计算缺口，避免客户端判定与服务端状态分歧。 */
    private static String nextTierLine(int ordinal, double denominator, long familyCount,
                                       IntFunction<Integer> tierOf) {
        NanochipCalibration.Family family = familyByOrdinal(ordinal);
        if (family == null || denominator <= 0.0D || !family.calibratable()) {
            return "";
        }
        int tier = tierOf.apply(ordinal);
        if (tier >= 3) {
            return Component.translatable("gtlmodular.nanochip.calibration.next.max").getString();
        }
        int next = tier + 1;
        double needPct = family.threshold(next);
        if (needPct <= 0.0D) {
            return Component.translatable("gtlmodular.nanochip.calibration.next.max").getString();
        }
        long needTotal = (long) Math.ceil(needPct * denominator);
        long missing = Math.max(0L, needTotal - familyCount);
        return Component.translatable("gtlmodular.nanochip.calibration.next",
                family.nameZh(), String.valueOf(next),
                com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(missing),
                com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(familyCount),
                String.format(java.util.Locale.ROOT, "%.0f", needPct * 100.0D)).getString();
    }

    private static String nextTierLineLegacy(int ordinal, double denominator, long familyCount) {
        NanochipCalibration.Family family = familyByOrdinal(ordinal);
        if (family == null || denominator <= 0.0D || !family.calibratable()) {
            return "";
        }
        double pct = familyCount / denominator;
        int tier = 0;
        for (int t = 1; t <= 3; t++) {
            if (family.threshold(t) > 0.0D && pct >= family.threshold(t)) {
                tier = t;
            }
        }
        if (tier >= 3) {
            return Component.translatable("gtlmodular.nanochip.calibration.next.max").getString();
        }
        int next = tier + 1;
        double needPct = family.threshold(next);
        if (needPct <= 0.0D) {
            return Component.translatable("gtlmodular.nanochip.calibration.next.max").getString();
        }
        long needTotal = (long) Math.ceil(needPct * denominator);
        long missing = Math.max(0L, needTotal - familyCount);
        return Component.translatable("gtlmodular.nanochip.calibration.next",
                family.nameZh(), String.valueOf(next),
                com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(missing),
                com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(familyCount),
                String.format(java.util.Locale.ROOT, "%.0f", needPct * 100.0D)).getString();
    }

    private static NanochipCalibration.Family familyByOrdinal(int ordinal) {
        if (ordinal < 0) {
            return null;
        }
        for (NanochipCalibration.Family f : NanochipCalibration.Family.barFamilies()) {
            if (f.ordinal() == ordinal) {
                return f;
            }
        }
        return null;
    }

    /** 面板会重建，确认状态放在组件实例外，避免重建时丢失已预备的状态。 */
    private static volatile boolean CLEAR_ARMED = false;

    private static boolean clearArmed() {
        return CLEAR_ARMED;
    }

    private static void onClearClick(com.lowdragmc.lowdraglib.gui.util.ClickData data, Runnable clearAction) {
        if (data == null) {
            return;
        }
        if (data.isRemote) {
            CLEAR_ARMED = !CLEAR_ARMED;
            return;
        }
        if (!CLEAR_ARMED) {
            return;
        }
        CLEAR_ARMED = false;
        clearAction.run();
    }

    /** 持有器允许点击回调更新已创建的控件，避免在初始化 lambda 中自引用。 */
    private static final class HoverSlot {
        ButtonWidget widget;
    }

    private static void onClearClick(com.lowdragmc.lowdraglib.gui.util.ClickData data, Runnable clearAction,
                                     Widget armedMark, HoverSlot slot) {
        onClearClick(data, clearAction, armedMark);
        if (slot != null && slot.widget != null) {
            slot.widget.setHoverTooltips(Component.translatable(clearArmed()
                    ? "gtlmodular.nanochip.calibration.reset.armed.tip"
                    : "gtlmodular.nanochip.calibration.reset.tip"));
        }
    }

    private static void onClearClick(com.lowdragmc.lowdraglib.gui.util.ClickData data, Runnable clearAction,
                                     Widget armedMark) {
        if (data == null) {
            return;
        }
        if (data.isRemote) {
            CLEAR_ARMED = !CLEAR_ARMED;
            armedMark.setVisible(CLEAR_ARMED);
            return;
        }
        if (!CLEAR_ARMED) {
            return;
        }
        CLEAR_ARMED = false;
        clearAction.run();
    }

    private static IGuiTexture barTexture(int argb) {
        ColorRectTexture rect = new ColorRectTexture();
        rect.color = argb;
        return rect;
    }

    /** 配置页按需创建，避免同一 Widget 实例在多处挂载时发生父节点冲突。 */
    public static final class Configurator implements IFancyConfigurator {

        private final Supplier<Widget> builder;

        public Configurator(Supplier<Widget> builder) {
            this.builder = builder;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gtlmodular.nanochip.calibration.title");
        }

        @Override
        public IGuiTexture getIcon() {
            return new ResourceTexture(ICON);
        }

        @Override
        public List<Component> getTooltips() {
            return List.of(Component.translatable("gtlmodular.nanochip.calibration.title"));
        }

        @Override
        public Widget createConfigurator() {
            return builder.get();
        }
    }

}
