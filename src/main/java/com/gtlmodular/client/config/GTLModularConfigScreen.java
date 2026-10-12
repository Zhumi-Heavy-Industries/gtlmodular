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

/**
 * 游戏内配置界面（Mods → GTL更多实用设备 → 配置）。
 *
 * <p><b>本模组所有配置项都在这里</b>，分七组：双流体税 / 线圈减免 / 猪咪黑奴挖矿机 / 猪咪样板总成 /
 * NAC 物流模式（2026-09-19 新增：原版(NH) ⇄ 简单）/ NAC 孤儿封装件（2026-09-19 新增：JEI 隐藏 42 件孤儿）/
 * NAC 校准与排障（★ 2026-09-21 队长令「做」新增：校准分母口径 + 结构日志全打）。
 * 数字框支持①直接键入 ②鼠标悬停滚轮 ±1 微调（Shift = ±10）；开关点一下切换。
 * 点「保存」即实时写入（机器逻辑实时读取，无需重启），Esc 放弃。
 *
 * <p>★ 2026-09-21 落盘口径（javap 取证，写在这里省得以后再猜）：Forge 的 TOML 配置器
 * {@code ConfigFileTypeHandler.lambda$reader$1} 建文件时链了 nightconfig 的 {@code autosave()}
 * ⇒ 本界面每次写值都会<b>立刻</b>改写 {@code config/gtlmodular-common.toml}（不是等退出游戏），
 * 所以玩家改完界面<b>不需要</b>再去手改 toml，也不会出现「下次启动又被旧 toml 里的旧值覆盖」。
 * 反过来说：<b>手改 toml 仍然要重启</b>（Forge 只在启动时读文件）。
 *
 * <p>布局自适应：两列排布，窗口窄时按当前宽度分列，不会顶出屏幕。
 */
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

    /** 当前列的起始 x / 当前列可用的右边界。 */
    private int columnX;
    private int columnRight;
    private int cursorY;
    private int columnIndex;

    private GTLModularConfigScreen(Screen parent) {
        super(Component.literal("GTL更多实用设备 · 配置"));
        this.parent = parent;
    }

    /** 供 Forge ConfigGUI 扩展点调用。 */
    public static Screen create(Minecraft minecraft, Screen parent) {
        return new GTLModularConfigScreen(parent);
    }

    // ================= 行模型 =================

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

    // ================= 布局 =================

    @Override
    protected void init() {
        int columnWidth = LABEL_W + BOX_W + 24;
        int usable = this.width - MARGIN_X * 2;
        int columns = Math.max(1, Math.min(2, usable / columnWidth));
        // ★ 2026-09-21 队长令「做」又添 2 行（校准分母 / 结构日志全打）⇒ 20.0 → 22.0，
        //   与 2026-09-19 加「JEI 隐藏孤儿件」那次同款（那次 19.0 → 20.0）。
        //   ⚠ 这个数只是估算（breakColumn 实际上不按它切分），真正管「放不放得下」的是下面的换列断点。
        int perColumn = (int) Math.ceil(22.0 / columns);   // 22 个数值/开关行 + 分组标题
        this.columnX = MARGIN_X;
        this.columnRight = columnX + columnWidth;
        this.cursorY = MARGIN_TOP;
        this.columnIndex = 0;

        // ---- 第 1 组：双流体税 ----
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

        // ---- 第 2 组：线圈减免 ----
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

        // ★ 2026-09-21（队长令「做」）：换列断点从「第 1 组之后」挪到「第 2 组之后」。
        //   依据（实测这台机器，不是猜）：实例 options.txt 里 guiScale=3、fullscreen=true，
        //   显示器 1920×1080 ⇒ 界面只有 640×360（页脚在 height-40=320、按钮在 height-26=334）。
        //   旧断点（左列只放第 1 组）下右列已排到 y=298..314，本轮再往右列添「NAC 校准与排障」
        //   一组（标题 14 + 2 行 36 = 50px）⇒ 新行会压到页脚与「保存并关闭」按钮，最后一行还会掉出屏幕，
        //   队长等于还是看不见这两个开关（这正是他要的「界面开关」）。把「线圈减免」留在左列后：
        //   左列最后一行 y=292..308、右列只剩 5 组最后一行 y=226..242，两列都离页脚 12px 以上。
        //   窄窗口（列宽不够）时本调用是无操作 ⇒ 单列模式的顺序仍按下面的插入顺序，一点没变。
        breakColumn(perColumn);

        // ---- 第 3 组：猪咪黑奴挖矿机 ----
        section("猪咪黑奴挖矿机");
        addDouble("能耗倍率", GTLModularConfig.getStripperEnergyMultiplier(), 1.0,
                GTLModularConfig::setStripperEnergyMultiplier);
        addDouble("耗时倍率", GTLModularConfig.getStripperDurationMultiplier(), 1.0,
                GTLModularConfig::setStripperDurationMultiplier);

        // ---- 第 4 组：猪咪样板总成 ----
        section("猪咪样板总成");
        addBool("样板身份闸", GTLModularConfig.isPatternIdentityGateEnabled(), true,
                GTLModularConfig::setPatternIdentityGateEnabled);
        addBool("自动切换配方模式", GTLModularConfig.isAutoRecipeModeSwitchEnabled(), true,
                GTLModularConfig::setAutoRecipeModeSwitchEnabled);

        NanochipConfigPage.append(this, GTLModularConfig.NANOCHIP);

        // ---- 底部按钮 ----
        int buttonY = this.height - 26;
        addRenderableWidget(Button.builder(Component.literal("保存并关闭"), b -> saveAndClose())
                .bounds(this.width / 2 - 110, buttonY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("恢复默认"), b -> resetDefaults())
                .bounds(this.width / 2 + 10, buttonY, 100, 20).build());
    }

    /**
     * 换列：一次调用把<b>后续各组</b>整体排到右侧第二列（列宽不够时本调用是无操作 ⇒ 保持单列）。
     *
     * <p>★ 2026-09-21：断点从「第 1 组之后」挪到「第 2 组之后」，理由（含实测数字）写在
     * {@code init()} 的调用处。{@code perColumn} 只是目标行数估算，本方法<b>不按它切分</b>；
     * 保留这个参数是为了不动调用点签名。
     */
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

    /**
     * 带多行说明（tooltip）的开关。
     *
     * <p>★ 2026-09-19 新增：NAC 物流模式开关的两种取值差别较大（物流路径 + 结构硬要求 + 产出数量），
     * 只靠 132px 宽的中文标签说不清，所以在按钮上挂一段<b>中文 + 英文镜像</b>的说明。
     */
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

    // ================= 渲染 / 交互 =================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
        // ★ 2026-09-21：页脚原写「即实时生效（机器逻辑实时读取，无需重启）」——对多数项成立，但**不是所有项都成立**：
        //   NAC 物流模式影响的是**注册期构建一次**的结构谓词（真空仓下限），必须重启；
        //   产出则**已经与模式无关**（两种模式走同一套阶梯：处理器 8 / 装配 4 / 电脑 2 / 主机 1，运行期缩放已停用）⇒ 这句话不再与产出挂钩。
        //   这里把例外写进页脚，具体项上仍各自标「⚠ 需要重启」。
        graphics.drawCenteredString(this.font, "§7改完点「保存并关闭」即生效（机器逻辑实时读取；标注「⚠ 需要重启」的项除外）",
                this.width / 2, this.height - 40, 0xAAAAAA);
    }

    /** 悬停滚轮微调：鼠标在输入框上就能调。 */
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
