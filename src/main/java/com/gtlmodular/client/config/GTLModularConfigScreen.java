package com.gtlmodular.client.config;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

public final class GTLModularConfigScreen extends Screen implements NanochipConfigPage.Rows {

    private static final int MARGIN_X = 16;
    private static final int MARGIN_TOP = 30;
    private static final int LABEL_W = 132;
    private static final int BOX_W = 96;
    private static final int ROW_H = 18;
    private static final int SECTION_H = 14;
    private static final int TOGGLE_W = 44;
    private static final int WIDGET_H = 16;

    private final Screen parent;

    private final List<IntRow> intRows = new ArrayList<>();
    private final List<DoubleRow> doubleRows = new ArrayList<>();
    private final List<StringRow> stringRows = new ArrayList<>();
    private final List<BoolRow> boolRows = new ArrayList<>();

    private int columnX;
    private int columnRight;
    private int cursorY;
    private int columnIndex;

    private GTLModularConfigScreen(Screen parent) {
        super(Component.literal("GTL更多实用设备 · 配置"));
        this.parent = parent;
    }

    public static Screen create(Minecraft minecraft, Screen parent) {
        return new GTLModularConfigScreen(parent);
    }

    private static final class IntRow {
        final EditBox box;
        final IntConsumer save;
        final int fallback;

        IntRow(EditBox box, IntConsumer save, int fallback) {
            this.box = box;
            this.save = save;
            this.fallback = fallback;
        }
    }

    private static final class DoubleRow {
        final EditBox box;
        final DoubleConsumer save;
        final double fallback;

        DoubleRow(EditBox box, DoubleConsumer save, double fallback) {
            this.box = box;
            this.save = save;
            this.fallback = fallback;
        }
    }

    private static final class StringRow {
        final EditBox box;
        final Consumer<String> save;
        final String fallback;

        StringRow(EditBox box, Consumer<String> save, String fallback) {
            this.box = box;
            this.save = save;
            this.fallback = fallback;
        }
    }

    private static final class BoolRow {
        final Button button;
        final String label;
        final Consumer<Boolean> save;
        boolean value;
        final boolean fallback;

        BoolRow(Button button, String label, Consumer<Boolean> save, boolean value, boolean fallback) {
            this.button = button;
            this.label = label;
            this.save = save;
            this.value = value;
            this.fallback = fallback;
            refresh();
        }

        void refresh() {
            button.setMessage(Component.literal(value ? "§a开" : "§7关"));
        }
    }

    @Override
    protected void init() {
        int columnWidth = LABEL_W + BOX_W + 24;
        int usable = this.width - MARGIN_X * 2;
        int columns = Math.max(1, Math.min(2, usable / columnWidth));
        int perColumn = (int) Math.ceil(22.0 / columns);
        this.columnX = MARGIN_X;
        this.columnRight = columnX + columnWidth;
        this.cursorY = MARGIN_TOP;
        this.columnIndex = 0;

        section("流体税（模块运行税）");
        addBool("启用双流体税", GTLModularConfig.isDualFluidTaxEnabled(), true,
                GTLModularConfig::setEnabled);
        addString("岩浆流体 id", GTLModularConfig.getLavaFluidId(), "minecraft:lava",
                GTLModularConfig::setLavaFluidId);
        addString("液态烈焰流体 id", GTLModularConfig.getBlazeFluidId(), "gtceu:blaze",
                GTLModularConfig::setBlazeFluidId);
        addInt("IV 每秒扣量 (mb)", GTLModularConfig.getIvBaselineMbPerSecond(), 83,
                GTLModularConfig::setIvBaselineMbPerSecond);
        addDouble("每高一档倍率 (×)", GTLModularConfig.getPerTierMultiplier(), 4.0,
                GTLModularConfig::setPerTierMultiplier);
        addInt("基准电压档", GTLModularConfig.getBaselineTier(), 5,
                GTLModularConfig::setBaselineTier);
        addBool("允许低于基准运行", GTLModularConfig.isAllowBelowBaseline(), false,
                GTLModularConfig::setAllowBelowBaseline);
        addInt("扣税间隔 (tick)", GTLModularConfig.getDrainIntervalTicks(), 20,
                GTLModularConfig::setDrainIntervalTicks);

        section("线圈减免（枢纽供能）");
        addBool("启用线圈减免", GTLModularConfig.isCoilDiscountEnabled(), true,
                GTLModularConfig::setCoilDiscountEnabled);
        addInt("基准温度 (K)", GTLModularConfig.getCoilDiscountBaseTemperature(), 5400,
                GTLModularConfig::setCoilDiscountBaseTemperature);
        addInt("每级温度 (K)", GTLModularConfig.getCoilDiscountTemperaturePerLevel(), 240,
                GTLModularConfig::setCoilDiscountTemperaturePerLevel);
        addDouble("每级减免系数", GTLModularConfig.getCoilDiscountFactorPerLevel(), 0.01,
                GTLModularConfig::setCoilDiscountFactorPerLevel);
        addDouble("最低减免系数", GTLModularConfig.getCoilDiscountMinFactor(), 0.5,
                GTLModularConfig::setCoilDiscountMinFactor);
        addDouble("减免时长倍率", GTLModularConfig.getCoilDiscountDurationMultiplier(), 1.0,
                GTLModularConfig::setCoilDiscountDurationMultiplier);

        // 两列布局在此分组，避免后续控件挤到页脚和保存按钮。
        breakColumn(perColumn);

        section("猪咪黑奴挖矿机");
        addDouble("能耗倍率", GTLModularConfig.getStripperEnergyMultiplier(), 1.0,
                GTLModularConfig::setStripperEnergyMultiplier);
        addDouble("耗时倍率", GTLModularConfig.getStripperDurationMultiplier(), 1.0,
                GTLModularConfig::setStripperDurationMultiplier);

        section("猪咪样板总成");
        addBool("样板身份闸", GTLModularConfig.isPatternIdentityGateEnabled(), true,
                GTLModularConfig::setPatternIdentityGateEnabled);
        addBool("自动切换配方模式", GTLModularConfig.isAutoRecipeModeSwitchEnabled(), true,
                GTLModularConfig::setAutoRecipeModeSwitchEnabled);

        NanochipConfigPage.append(this, GTLModularConfig.NANOCHIP);

        int buttonY = this.height - 26;
        addRenderableWidget(Button.builder(Component.literal("保存并关闭"), b -> saveAndClose())
                .bounds(this.width / 2 - 110, buttonY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("恢复默认"), b -> resetDefaults())
                .bounds(this.width / 2 + 10, buttonY, 100, 20).build());
    }

    private void breakColumn(int perColumn) {
        if (columnIndex == 0 && this.width - MARGIN_X * 2 > (LABEL_W + BOX_W + 24) * 2 - 1) {
            columnIndex = 1;
            columnX = MARGIN_X + (LABEL_W + BOX_W + 24);
            columnRight = columnX + LABEL_W + BOX_W;
            cursorY = MARGIN_TOP;
        }
    }

    @Override
    public void section(String title) {
        addRenderableWidget(new StringWidget(columnX, cursorY, LABEL_W + BOX_W, 10,
                Component.literal("§e§l" + title), this.font));
        cursorY += SECTION_H;
    }

    private void nextRow() {
        cursorY += ROW_H;
    }

    private void addBool(String label, boolean value, boolean fallback, Consumer<Boolean> save) {
        addBool(label, value, fallback, save, (String[]) null);
    }

    /** 标签宽度不足以解释物流模式差异，因此把详细说明放进提示。 */
    @Override
    public void addBool(String label, boolean value, boolean fallback, Consumer<Boolean> save,
                         String... tooltipLines) {
        final BoolRow[] holder = new BoolRow[1];
        Button button = Button.builder(Component.empty(), b -> {
            BoolRow row = holder[0];
            if (row == null) {
                return;
            }
            row.value = !row.value;
            row.refresh();
        }).bounds(columnX + LABEL_W, cursorY, TOGGLE_W, WIDGET_H).build();
        if (tooltipLines != null && tooltipLines.length > 0) {
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal(String.join("\n", tooltipLines))));
        }
        BoolRow row = new BoolRow(button, label, save, value, fallback);
        holder[0] = row;
        addRenderableWidget(button);
        boolRows.add(row);
        addRenderableWidget(new StringWidget(columnX, cursorY + 4, LABEL_W - 4, 10,
                Component.literal(label), this.font));
        nextRow();
    }

    private void addInt(String label, int value, int fallback, IntConsumer save) {
        EditBox box = new EditBox(this.font, columnX + LABEL_W, cursorY, BOX_W, WIDGET_H, Component.literal(label));
        box.setValue(String.valueOf(value));
        box.setFilter(s -> s.matches("-?\\d*"));
        box.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("悬停滚轮可微调（Shift = ±10）")));
        addRenderableWidget(box);
        addRenderableWidget(new StringWidget(columnX, cursorY + 4, LABEL_W - 4, 10,
                Component.literal(label), this.font));
        intRows.add(new IntRow(box, save, fallback));
        nextRow();
    }

    private void addDouble(String label, double value, double fallback, DoubleConsumer save) {
        EditBox box = new EditBox(this.font, columnX + LABEL_W, cursorY, BOX_W, WIDGET_H, Component.literal(label));
        box.setValue(trim(value));
        box.setFilter(s -> s.matches("-?\\d*(\\.\\d*)?"));
        box.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("悬停滚轮可微调（Shift = ±10）")));
        addRenderableWidget(box);
        addRenderableWidget(new StringWidget(columnX, cursorY + 4, LABEL_W - 4, 10,
                Component.literal(label), this.font));
        doubleRows.add(new DoubleRow(box, save, fallback));
        nextRow();
    }

    private void addString(String label, String value, String fallback, Consumer<String> save) {
        EditBox box = new EditBox(this.font, columnX + LABEL_W, cursorY, BOX_W, WIDGET_H, Component.literal(label));
        box.setValue(value);
        box.setMaxLength(128);
        addRenderableWidget(box);
        addRenderableWidget(new StringWidget(columnX, cursorY + 4, LABEL_W - 4, 10,
                Component.literal(label), this.font));
        stringRows.add(new StringRow(box, save, fallback));
        nextRow();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
        // 部分选项参与注册期结构构建，不能统一承诺保存后立即生效。
        graphics.drawCenteredString(this.font, "§7改完点「保存并关闭」即生效（机器逻辑实时读取；标注「⚠ 需要重启」的项除外）",
                this.width / 2, this.height - 40, 0xAAAAAA);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double step = (hasShiftDown() ? 10 : 1) * (delta > 0 ? 1 : -1);
        for (IntRow row : intRows) {
            if (row.box.isMouseOver(mouseX, mouseY)) {
                int v = parseInt(row.box.getValue()).orElse(row.fallback);
                row.box.setValue(String.valueOf((int) (v + step)));
                return true;
            }
        }
        for (DoubleRow row : doubleRows) {
            if (row.box.isMouseOver(mouseX, mouseY)) {
                double v = parseDouble(row.box.getValue()).orElse(row.fallback);
                row.box.setValue(trim(v + step));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void saveAndClose() {
        for (IntRow row : intRows) {
            row.save.accept(parseInt(row.box.getValue()).orElse(row.fallback));
        }
        for (DoubleRow row : doubleRows) {
            row.save.accept(parseDouble(row.box.getValue()).orElse(row.fallback));
        }
        for (StringRow row : stringRows) {
            String text = row.box.getValue();
            row.save.accept(text == null || text.isBlank() ? row.fallback : text.trim());
        }
        for (BoolRow row : boolRows) {
            row.save.accept(row.value);
        }
        GTLModularConfig.NANOCHIP.save();
        NanochipConfigRefresh.refresh();
        GTLModular.LOGGER.info("[gtlmodular] 配置已更新（{} 个数值 / {} 个文本 / {} 个开关），实时生效",
                intRows.size() + doubleRows.size(), stringRows.size(), boolRows.size());
        onClose();
    }

    private void resetDefaults() {
        for (IntRow row : intRows) {
            row.box.setValue(String.valueOf(row.fallback));
        }
        for (DoubleRow row : doubleRows) {
            row.box.setValue(trim(row.fallback));
        }
        for (StringRow row : stringRows) {
            row.box.setValue(row.fallback);
        }
        for (BoolRow row : boolRows) {
            row.value = row.fallback;
            row.refresh();
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private static String trim(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private static Optional<Integer> parseInt(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(text.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<Double> parseDouble(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Double.parseDouble(text.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
