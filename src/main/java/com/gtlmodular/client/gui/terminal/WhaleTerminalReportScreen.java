package com.gtlmodular.client.gui.terminal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gtlmodular.client.ShanhaiStructureHighlightClient;
import com.gtlmodular.common.item.terminal.ShanhaiTerminalReport;
import com.gtlmodular.network.ShanhaiStructureHighlightPacket;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 哦鲸鲸终端 · 结构报告（纸白图录版，LDLib 控件重写）。
 *
 * <p>沿用旧屏的全部语义：同类别 + 同材料合并成一行、点击分组行展开/收起坐标、
 * 分类筛选循环、复制全部/单条坐标、世界方块高亮、通知条、空态与省略数量。
 *
 * <p>版面：表头分栏（类 / 材料 / 数量 / 坐标），行内左侧 2px 分类色条，
 * 右侧 +/- 展开；底部四个按钮。列表用自绘 Widget 做裁剪与滚动，
 * 其余（按钮、面板）都是 LDLib 控件。
 */
public final class WhaleTerminalReportScreen extends Screen {

    private static final int W = 500;
    private static final int H = 300;
    private static final int ROW_H = 18;
    private static final int BOX_SECONDS = 30;
    private static final int BOX_MAX = 4096;

    private static final int PAPER = 0xFFEFF1EF;
    private static final int INK = 0xFF1F2427;
    private static final int MUTED = 0xFF55605F;
    private static final int FAINT = 0xFF8B9795;
    private static final int COBALT = 0xFF3A5BC2;
    private static final int CLEAR = 0x00000000;

    private static final int LIST_X = 14;
    private static final int LIST_Y = 62;
    private static final int LIST_W = W - 28;
    private static final int LIST_H = 176;

    private final ShanhaiTerminalReport report;
    private final List<Group> filtered = new ArrayList<>();
    private final Set<Long> expandedKeys = new HashSet<>();

    private static final class Group {
        final long key;
        final int category;
        final int name;
        final List<BlockPos> positions = new ArrayList<>();

        Group(long key, int category, int name) {
            this.key = key;
            this.category = category;
            this.name = name;
        }

        boolean expandable() {
            return positions.size() > 1;
        }
    }

    private record VisibleRow(Group group, BlockPos pos) {}

    private ModularUI ui;
    private WidgetGroup root;
    private int left;
    private int top;
    private int scroll;
    private int filter = -1;
    private Component notice = Component.empty();
    private long noticeUntil;

    public WhaleTerminalReportScreen(ShanhaiTerminalReport report) {
        super(Component.translatable(titleKey(report.type())));
        this.report = report;
        applyFilter();
    }

    private static String titleKey(ShanhaiTerminalReport.Type type) {
        return "gui.gtlmodular.whale_terminal.report.title." + type.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.gtlmodular.whale_terminal." + key, args);
    }

    // ─────────────────────────── 数据
    private void applyFilter() {
        filtered.clear();
        Map<Long, Group> groups = new LinkedHashMap<>();
        for (ShanhaiTerminalReport.Line line : report.lines()) {
            if (filter >= 0 && line.category() != filter) {
                continue;
            }
            long key = ((long) line.category() << 32) | (line.name() & 0xFFFFFFFFL);
            Group group = groups.get(key);
            if (group == null) {
                group = new Group(key, line.category(), line.name());
                groups.put(key, group);
                filtered.add(group);
            }
            group.positions.add(line.pos());
        }
        scroll = 0;
    }

    private int positionCount() {
        int total = 0;
        for (Group g : filtered) {
            total += g.positions.size();
        }
        return total;
    }

    private List<VisibleRow> visibleRows() {
        List<VisibleRow> rows = new ArrayList<>();
        for (Group group : filtered) {
            rows.add(new VisibleRow(group, null));
            if (group.expandable() && expandedKeys.contains(group.key)) {
                for (BlockPos pos : group.positions) {
                    rows.add(new VisibleRow(group, pos));
                }
            }
        }
        return rows;
    }

    private int maxScroll() {
        return Math.max(0, visibleRows().size() * ROW_H - LIST_H);
    }

    private Component filterLabel() {
        if (filter < 0 || filter >= report.categories().size()) {
            return Component.literal("筛选：全部 " + report.lines().size());
        }
        return Component.literal("筛选：" + report.categories().get(filter).label().getString()
                + " " + positionCount());
    }

    private Component countsText() {
        int[] c = report.counts();
        return switch (report.type()) {
            case SCAN -> Component.literal("可放置 " + at(c, 0) + " · 冲突 " + at(c, 1) + " · 缺失 " + at(c, 2));
            case BUILD -> Component.literal("已建 " + at(c, 0) + " · 替换 " + at(c, 1) + " · 阻塞 " + at(c, 2));
            case CRAFT -> Component.literal("成品 " + at(c, 0) + " · 缺料 " + at(c, 1) + " · 失败 " + at(c, 2));
            case MODULES -> Component.literal("模块 " + at(c, 0));
        };
    }

    private static int at(int[] values, int index) {
        return index >= 0 && index < values.length ? values[index] : 0;
    }

    private String posText(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    // ─────────────────────────── 生命周期
    @Override
    protected void init() {
        super.init();
        left = (width - W) / 2;
        top = (height - H) / 2;
        if (ui == null) {
            Minecraft mc = Minecraft.getInstance();
            ui = new ModularUI(W, H, IUIHolder.EMPTY, mc.player);
            root = ui.mainGroup;
            root.setClientSideWidget();
        }
        build();
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0xB0151817);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        if (root == null) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0);
        root.drawInBackground(graphics, mouseX - left, mouseY - top, partialTick);
        graphics.pose().popPose();
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0);
        root.drawInForeground(graphics, mouseX - left, mouseY - top, partialTick);
        graphics.pose().popPose();
    }

    // ─────────────────────────── 版面
    private void build() {
        root.clearAllWidgets();
        root.setBackground(new ColorRectTexture(PAPER));
        root.addWidget(new ImageRect(0, 0, W, H, PAPER));
        root.addWidget(new ImageRect(0, 0, W, 1, 0x551F2427));
        root.addWidget(new ImageRect(0, H - 1, W, 1, 0x551F2427));

        header();
        root.addWidget(new ReportList(LIST_X, LIST_Y, LIST_W, LIST_H));
        footer();
        ui.initWidgets();
    }

    private void header() {
        label(16, 10, super.getTitle().getString(), INK);
        label(16, 24, countsText().getString(), MUTED);
        if (!report.target().equals(BlockPos.ZERO)) {
            label(W - 16 - Minecraft.getInstance().font.width("TARGET " + posText(report.target())), 24,
                    "TARGET " + posText(report.target()), FAINT);
        }
        root.addWidget(new ImageRect(14, 42, W - 28, 1, 0x441F2427));

        String[] cols = {"类", "材料", "数量", "坐标"};
        label(LIST_X + 26, 50, cols[0], FAINT);
        label(LIST_X + 78, 50, cols[1], FAINT);
        label(LIST_X + LIST_W - 96 - Minecraft.getInstance().font.width(cols[2]), 50, cols[2], FAINT);
        label(LIST_X + LIST_W - 8 - Minecraft.getInstance().font.width(cols[3]), 50, cols[3], FAINT);
        root.addWidget(new ImageRect(LIST_X, 60, LIST_W, 1, 0x2A1F2427));
    }

    private void footer() {
        int y = H - 30;
        button(14, y, 92, 18, "复制全部", false, this::copyAll);
        button(110, y, 92, 18, "显示方块", false, this::showBoxes);
        button(206, y, 176, 18, filterLabel().getString(), false, this::cycleFilter);
        button(W - 76, y, 62, 18, "关闭", true, this::onClose);
    }

    private void label(int x, int y, String text, int color) {
        var w = new com.lowdragmc.lowdraglib.gui.widget.LabelWidget(x, y, text);
        w.setTextColor(color);
        w.setDropShadow(false);
        w.setClientSideWidget();
        root.addWidget(w);
    }

    private void button(int x, int y, int w, int h, String caption, boolean solid, Runnable action) {
        ButtonWidget b = new ButtonWidget(x, y, w, h,
                solid ? new ColorRectTexture(INK) : new ColorBorderTexture(1, 0x881F2427),
                cd -> action.run());
        b.setHoverTexture(new ColorRectTexture(0x203A5BC2));
        b.setClientSideWidget();
        root.addWidget(b);
        label(x + (w - Minecraft.getInstance().font.width(caption)) / 2, y + (h - 8) / 2,
                caption, solid ? PAPER : INK);
    }

    /** 纯色矩形（用于边框线与底纸）。 */
    private static final class ImageRect extends Widget {
        private final int color;

        ImageRect(int x, int y, int w, int h, int color) {
            super(x, y, w, h);
            this.color = color;
            setClientSideWidget();
        }

        @Override
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            graphics.fill(getPositionX(), getPositionY(),
                    getPositionX() + getSize().width, getPositionY() + getSize().height, color);
        }
    }

    // ─────────────────────────── 列表
    private final class ReportList extends Widget {

        ReportList(int x, int y, int w, int h) {
            super(x, y, w, h);
            setClientSideWidget();
        }

        @Override
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            var font = Minecraft.getInstance().font;
            List<VisibleRow> rows = visibleRows();
            int x0 = getPositionX();
            int y0 = getPositionY();
            int w = getSize().width;
            int h = getSize().height;
            int max = Math.max(0, rows.size() * ROW_H - h);
            scroll = Math.max(0, Math.min(scroll, max));

            graphics.enableScissor(x0, y0, x0 + w, y0 + h);
            for (int i = 0; i < rows.size(); i++) {
                int y = y0 + i * ROW_H - scroll;
                if (y + ROW_H < y0 || y > y0 + h) {
                    continue;
                }
                VisibleRow row = rows.get(i);
                boolean hover = mouseX >= x0 && mouseX < x0 + w && mouseY >= y && mouseY < y + ROW_H - 1;
                if (hover) {
                    graphics.fill(x0, y, x0 + w, y + ROW_H - 1, 0x143A5BC2);
                }
                int color = categoryColor(row.group().category);
                graphics.fill(x0, y, x0 + 2, y + ROW_H - 1, color);

                ItemStack icon = row.pos() == null ? iconOf(row.group().name) : ItemStack.EMPTY;
                if (!icon.isEmpty()) {
                    graphics.renderItem(icon, x0 + 6, y + 1);
                }
                if (row.pos() == null) {
                    boolean expanded = expandedKeys.contains(row.group().key);
                    graphics.drawString(font, clip(Component.literal(categoryName(row.group().category)), 46), x0 + 26, y + 5, MUTED, false);
                    graphics.drawString(font, clip(nameOf(row.group().name), 160), x0 + 78, y + 5, INK, false);
                    String count = "×" + row.group().positions.size();
                    graphics.drawString(font, count, x0 + w - 96 - font.width(count), y + 5, INK, false);
                    if (row.group().expandable()) {
                        graphics.drawString(font, expanded ? "−" : "+", x0 + w - 26, y + 5,
                                expanded ? COBALT : FAINT, false);
                    }
                } else {
                    graphics.drawString(font, "↳", x0 + 64, y + 5, FAINT, false);
                    graphics.drawString(font, clip(nameOf(row.group().name), 160), x0 + 78, y + 5, INK, false);
                    graphics.drawString(font, posText(row.pos()), x0 + w - 78, y + 5, MUTED, false);
                }
                graphics.fill(x0, y + ROW_H - 1, x0 + w, y + ROW_H, 0x221F2427);
            }
            graphics.disableScissor();

            if (rows.isEmpty()) {
                graphics.drawString(font, "没有匹配的条目", x0 + 6, y0 + 8, FAINT, false);
            }
            if (report.omitted() > 0) {
                graphics.drawString(font, "另有 " + report.omitted() + " 条已省略",
                        x0 + 6, y0 + h + 4, FAINT, false);
            }
            if (!notice.getString().isEmpty() && System.currentTimeMillis() < noticeUntil) {
                int tw = font.width(notice) + 12;
                graphics.fill(x0, y0 + h - 18, x0 + tw, y0 + h - 2, 0xF21F2427);
                graphics.drawString(font, notice.getString(), x0 + 6, y0 + h - 14, PAPER, false);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int x0 = getPositionX();
            int y0 = getPositionY();
            int w = getSize().width;
            int h = getSize().height;
            if (button != 0 || mouseX < x0 || mouseX >= x0 + w || mouseY < y0 || mouseY >= y0 + h) {
                return false;
            }
            List<VisibleRow> rows = visibleRows();
            int index = (int) ((mouseY - y0 + scroll) / ROW_H);
            if (index < 0 || index >= rows.size()) {
                return false;
            }
            VisibleRow row = rows.get(index);
            if (row.pos() == null) {
                if (row.group().expandable()) {
                    if (!expandedKeys.remove(row.group().key)) {
                        expandedKeys.add(row.group().key);
                    }
                    return true;
                }
                BlockPos pos = row.group().positions.get(0);
                Minecraft.getInstance().keyboardHandler.setClipboard(posText(pos));
                setNotice("已复制 " + posText(pos));
                return true;
            }
            Minecraft.getInstance().keyboardHandler.setClipboard(posText(row.pos()));
            setNotice("已复制 " + posText(row.pos()));
            return true;
        }

        @Override
        public boolean mouseWheelMove(double mouseX, double mouseY, double delta) {
            int x0 = getPositionX();
            int y0 = getPositionY();
            if (mouseX < x0 || mouseX >= x0 + getSize().width || mouseY < y0 || mouseY >= y0 + getSize().height) {
                return false;
            }
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (delta * ROW_H * 2)));
            return true;
        }
    }

    // ─────────────────────────── 操作
    private void copyAll() {
        StringBuilder text = new StringBuilder();
        int count = 0;
        for (Group group : filtered) {
            for (BlockPos pos : group.positions) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(posText(pos));
                count++;
            }
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(text.toString());
        setNotice("已复制 " + count + " 条坐标");
    }

    private void showBoxes() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        int color = 0x7FE3FF;
        if (filter >= 0 && filter < report.categories().size()) {
            color = report.categories().get(filter).color();
        }
        List<ShanhaiStructureHighlightPacket.Marker> markers = new ArrayList<>();
        for (Group group : filtered) {
            for (BlockPos pos : group.positions) {
                if (markers.size() >= BOX_MAX) {
                    break;
                }
                if (pos.equals(BlockPos.ZERO)) {
                    continue;
                }
                markers.add(new ShanhaiStructureHighlightPacket.Marker(pos, color));
            }
        }
        if (markers.isEmpty()) {
            setNotice(tr("report.boxes_none").getString());
            return;
        }
        ShanhaiStructureHighlightClient.highlightAsync(minecraft.level.dimension(),
                System.currentTimeMillis() + BOX_SECONDS * 1000L, markers);
        setNotice("已显示 " + markers.size() + " 个方块 " + BOX_SECONDS + " 秒");
    }

    private void cycleFilter() {
        if (report.categories().isEmpty()) {
            return;
        }
        filter++;
        if (filter >= report.categories().size()) {
            filter = -1;
        }
        applyFilter();
        build();
    }

    private void setNotice(String text) {
        notice = Component.literal(text);
        noticeUntil = System.currentTimeMillis() + 2500L;
    }

    // ─────────────────────────── 小工具
    private int categoryColor(int index) {
        if (index >= 0 && index < report.categories().size()) {
            return 0xFF000000 | report.categories().get(index).color();
        }
        return COBALT;
    }

    private String categoryName(int index) {
        if (index >= 0 && index < report.categories().size()) {
            return report.categories().get(index).label().getString();
        }
        return "";
    }

    private Component nameOf(int index) {
        List<Component> names = report.names();
        return index >= 0 && index < names.size() ? names.get(index) : Component.empty();
    }

    private ItemStack iconOf(int nameIndex) {
        List<List<Item>> icons = report.icons();
        if (nameIndex < 0 || nameIndex >= icons.size() || icons.get(nameIndex).isEmpty()) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(icons.get(nameIndex).get(0));
    }

    private String clip(Component text, int maxPx) {
        var font = Minecraft.getInstance().font;
        List<FormattedCharSequence> lines = font.split(text, Math.max(1, maxPx));
        if (lines.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        lines.get(0).accept((i, style, cp) -> {
            sb.appendCodePoint(cp);
            return true;
        });
        return sb.toString();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (root != null && root.mouseClicked(mouseX - left, mouseY - top, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (root != null && root.mouseReleased(mouseX - left, mouseY - top, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (root != null && root.mouseWheelMove(mouseX - left, mouseY - top, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
