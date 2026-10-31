package com.gtlmodular.client.gui.terminal;

import com.gtlmodular.GTLModular;
import com.gtlmodular.client.ShanhaiStructureHighlightClient;
import com.gtlmodular.common.item.terminal.ShanhaiTerminalReport;
import com.gtlmodular.network.ShanhaiStructureHighlightPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 哦鲸鲸终端 · 完整报告屏（仿 eco / Neo ECO AE 的浅色终端风格）。
 *
 * <p>同一类别 + 同一材料会合并成一行；点击分组行展开/收起每个坐标，
 * 避免几百条同材料报告占满列表。
 */
public final class WhaleTerminalReportScreen extends Screen {

    private static final int PANEL_W = 440;
    private static final int PANEL_H = 272;
    private static final int ROW_H = 20;
    private static final int BOX_SECONDS = 30;
    private static final int BOX_MAX = 4096;
    private static final ResourceLocation WHALE_LOGO =
            new ResourceLocation(GTLModular.MOD_ID, "textures/gui/whale_logo.png");

    private final ShanhaiTerminalReport report;
    private final List<Group> filtered = new ArrayList<>();
    private final Set<Long> expandedKeys = new HashSet<>();

    /** 同一类别、同一材料的合并行。 */
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

    /** 列表中的一行：pos == null 表示合并后的分组行，否则是展开后的坐标行。 */
    private record VisibleRow(Group group, BlockPos pos) {}

    private int left;
    private int top;
    private int listTop;
    private int listBottom;
    private double scroll;
    private int filter = -1;

    private int btnCopyX;
    private int btnBoxesX;
    private int btnFilterX;
    private int btnCloseX;
    private int btnY;
    private int btnH = 20;

    private Component notice = Component.empty();
    private long noticeUntil;

    public WhaleTerminalReportScreen(ShanhaiTerminalReport report) {
        super(Component.translatable(titleKey(report.type())));
        this.report = report;
    }

    private static String titleKey(ShanhaiTerminalReport.Type type) {
        return "gui.gtlmodular.whale_terminal.report.title." + type.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.gtlmodular.whale_terminal." + key, args);
    }

    @Override
    protected void init() {
        super.init();
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        listTop = top + 74;
        listBottom = top + PANEL_H - 46;
        btnY = top + PANEL_H - 30;
        btnCopyX = left + 10;
        btnBoxesX = btnCopyX + 104;
        btnFilterX = btnBoxesX + 108;
        btnCloseX = left + PANEL_W - 96;
        applyFilter();
    }

    private void applyFilter() {
        filtered.clear();
        Map<Long, Group> groups = new LinkedHashMap<>();
        for (int i = 0; i < report.lines().size(); i++) {
            ShanhaiTerminalReport.Line line = report.lines().get(i);
            if (filter >= 0 && line.category() != filter) continue;
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
        for (Group group : filtered) total += group.positions.size();
        return total;
    }

    private Component filterLabel() {
        if (filter < 0 || filter >= report.categories().size()) {
            return tr("report.filter_all", report.lines().size());
        }
        return tr("report.filter", report.categories().get(filter).label(), positionCount());
    }

    private void cycleFilter() {
        if (report.categories().isEmpty()) return;
        filter++;
        if (filter >= report.categories().size()) filter = -1;
        applyFilter();
    }

    private Component countsText() {
        int[] counts = report.counts();
        return switch (report.type()) {
            case SCAN -> tr("report.counts.scan", at(counts, 0), at(counts, 1), at(counts, 2),
                    at(counts, 3), at(counts, 4));
            case BUILD -> tr("report.counts.build", at(counts, 0), at(counts, 1), at(counts, 2));
            case CRAFT -> tr("report.counts.craft", at(counts, 0), at(counts, 1), at(counts, 2),
                    at(counts, 3));
            case MODULES -> tr("report.counts.modules", at(counts, 0));
        };
    }

    private static int at(int[] values, int index) {
        return index >= 0 && index < values.length ? values[index] : 0;
    }

    private String posText(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private List<VisibleRow> visibleRows() {
        List<VisibleRow> rows = new ArrayList<>();
        for (Group group : filtered) {
            rows.add(new VisibleRow(group, null));
            if (group.expandable() && expandedKeys.contains(group.key)) {
                for (BlockPos pos : group.positions) rows.add(new VisibleRow(group, pos));
            }
        }
        return rows;
    }

    private int maxScroll() {
        return Math.max(0, visibleRows().size() * ROW_H - (listBottom - listTop));
    }

    // ---------- 操作 ----------
    private void copyAll() {
        StringBuilder text = new StringBuilder();
        int count = 0;
        for (Group group : filtered) {
            for (BlockPos pos : group.positions) {
                if (text.length() > 0) text.append('\n');
                text.append(posText(pos));
                count++;
            }
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(text.toString());
        setNotice(tr("report.copied", count));
    }

    private void showBoxes() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        int color = 0x7FE3FF;
        if (filter >= 0 && filter < report.categories().size()) {
            color = report.categories().get(filter).color();
        }
        List<ShanhaiStructureHighlightPacket.Marker> markers = new ArrayList<>();
        for (Group group : filtered) {
            for (BlockPos pos : group.positions) {
                if (markers.size() >= BOX_MAX) break;
                if (pos.equals(BlockPos.ZERO)) continue;
                markers.add(new ShanhaiStructureHighlightPacket.Marker(pos, color));
            }
        }
        if (markers.isEmpty()) {
            setNotice(tr("report.boxes_none"));
            return;
        }
        ShanhaiStructureHighlightClient.highlightAsync(minecraft.level.dimension(),
                System.currentTimeMillis() + BOX_SECONDS * 1000L, markers);
        setNotice(tr("report.boxes_shown", markers.size(), BOX_SECONDS));
    }

    private void setNotice(Component value) {
        notice = value;
        noticeUntil = System.currentTimeMillis() + 2500L;
    }

    // ---------- 渲染 ----------
    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0xB0101018);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        EcoStyle.panel(graphics, left, top, left + PANEL_W, top + PANEL_H);
        drawHeader(graphics);
        drawRows(graphics, mouseX, mouseY);
        drawFooter(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawHeader(GuiGraphics graphics) {
        graphics.fill(left + 6, top + 6, left + 44, top + 44, EcoStyle.PANEL_DEEP);
        graphics.fill(left + 7, top + 7, left + 43, top + 43, EcoStyle.DEEP);
        graphics.fill(left + 6, top + 6, left + 44, top + 7, EcoStyle.GOLD);
        graphics.blit(WHALE_LOGO, left + 10, top + 10, 0, 0, 28, 28, 32, 32);
        EcoStyle.title(graphics, font, getTitle(), left + 54, top + 10);
        graphics.drawString(font, countsText(), left + 54, top + 24, EcoStyle.TEXT, false);
        if (!report.summary().getString().isEmpty()) {
            graphics.drawString(font, firstLine(report.summary(), PANEL_W - 70),
                    left + 54, top + 36, EcoStyle.TEXT_DIM, false);
        }
        graphics.fill(left + 6, top + 46, left + PANEL_W - 6, top + 47, EcoStyle.GOLD);
        graphics.fill(left + 6, top + 47, left + PANEL_W - 6, top + 48, EcoStyle.PANEL_DEEP);
        if (!report.target().equals(BlockPos.ZERO)) {
            graphics.drawString(font, tr("report.target", posText(report.target())),
                    left + 10, top + 54, EcoStyle.TEXT_DIM, false);
        }
    }

    private void drawRows(GuiGraphics graphics, int mouseX, int mouseY) {
        List<VisibleRow> rows = visibleRows();
        int ability = listBottom - listTop;
        int content = rows.size() * ROW_H;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, content - ability)));
        int first = (int) (scroll / ROW_H);
        int last = Math.min(rows.size(), first + ability / ROW_H + 2);

        graphics.enableScissor(left + 6, listTop, left + PANEL_W - 6, listBottom);
        for (int i = first; i < last; i++) {
            int y = listTop + i * ROW_H - (int) scroll;
            if (y + ROW_H < listTop || y > listBottom) continue;
            VisibleRow row = rows.get(i);
            boolean hover = mouseX >= left + 8 && mouseX < left + PANEL_W - 8
                    && mouseY >= y && mouseY < y + ROW_H;
            EcoStyle.slot(graphics, left + 8, y, PANEL_W - 16, ROW_H - 2, hover);

            int color = 0xFF7FE3FF;
            if (row.group().category >= 0 && row.group().category < report.categories().size()) {
                color = 0xFF000000 | report.categories().get(row.group().category).color();
            }
            graphics.fill(left + 8, y, left + 10, y + ROW_H - 2, color);

            ItemStack icon = iconOf(row.group().name);
            if (!icon.isEmpty()) graphics.renderItem(icon, left + 13, y + 1);
            if (row.pos() == null) {
                boolean expanded = expandedKeys.contains(row.group().key);
                graphics.drawString(font, categoryLabel(row.group().category),
                        left + 32, y + 5, EcoStyle.TEXT_DARK, false);
                String name = firstLine(nameOf(row.group().name), 124);
                graphics.drawString(font, name, left + 104, y + 5, EcoStyle.TEXT_DARK, false);
                String count = "×" + row.group().positions.size();
                graphics.drawString(font, count, left + PANEL_W - 20 - font.width(count),
                        y + 5, EcoStyle.TEXT_DARK, false);
                if (row.group().expandable()) {
                    String arrow = expanded ? "−" : "+";
                    graphics.drawString(font, arrow, left + 22, y + 5,
                            expanded ? EcoStyle.GOLD : EcoStyle.TEXT_DARK, false);
                }
            } else {
                graphics.drawString(font, "  ↳", left + 30, y + 5, EcoStyle.TEXT_DIM, false);
                graphics.drawString(font, firstLine(nameOf(row.group().name), 124),
                        left + 104, y + 5, EcoStyle.TEXT_DARK, false);
                String pos = posText(row.pos());
                graphics.drawString(font, pos, left + PANEL_W - 20 - font.width(pos),
                        y + 5, EcoStyle.TEXT_DARK, false);
            }
        }
        graphics.disableScissor();

        if (content > ability) {
            int barH = Math.max(16, ability * ability / content);
            int barY = listTop + (int) ((ability - barH) * scroll / Math.max(1, content - ability));
            graphics.fill(left + PANEL_W - 5, listTop, left + PANEL_W - 3, listBottom, EcoStyle.PANEL_DEEP);
            graphics.fill(left + PANEL_W - 5, barY, left + PANEL_W - 3, barY + barH, EcoStyle.GOLD);
        }
        if (rows.isEmpty()) {
            graphics.drawString(font, tr("report.empty"), left + 16, listTop + 8, EcoStyle.TEXT_DIM, false);
        }
        if (report.omitted() > 0) {
            graphics.drawString(font, tr("report.omitted", report.omitted()),
                    left + 16, listBottom - 12, EcoStyle.TEXT_DIM, false);
        }
    }

    private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        drawButton(graphics, btnCopyX, 104, tr("report.copy_all"), mouseX, mouseY);
        drawButton(graphics, btnBoxesX, 104, tr("report.show_boxes"), mouseX, mouseY);
        drawButton(graphics, btnFilterX, btnCloseX - btnFilterX - 6, filterLabel(), mouseX, mouseY);
        drawButton(graphics, btnCloseX, 86,
                Component.translatable("gui.gtlmodular.whale_terminal.close"), mouseX, mouseY);

        if (!notice.getString().isEmpty() && System.currentTimeMillis() < noticeUntil) {
            int w = font.width(notice) + 12;
            int y = btnY - 24;
            graphics.fill(left + 10, y, left + 10 + w, y + 16, EcoStyle.DEEP);
            graphics.fill(left + 10, y, left + 10 + w, y + 1, EcoStyle.GOLD);
            graphics.drawString(font, notice, left + 16, y + 4, EcoStyle.WHITE, false);
        }
    }

    private void drawButton(GuiGraphics graphics, int x, int w, Component label, int mouseX, int mouseY) {
        boolean hover = EcoStyle.hit(mouseX, mouseY, x, btnY, w, btnH);
        EcoStyle.button(graphics, x, btnY, w, btnH, hover, true);
        EcoStyle.centered(graphics, font, label, x, w, btnY + 6, EcoStyle.TEXT);
    }

    private Component nameOf(int index) {
        List<Component> names = report.names();
        return index >= 0 && index < names.size() ? names.get(index) : Component.empty();
    }

    private ItemStack iconOf(int nameIndex) {
        List<List<Item>> icons = report.icons();
        if (nameIndex < 0 || nameIndex >= icons.size() || icons.get(nameIndex).isEmpty()) return ItemStack.EMPTY;
        return new ItemStack(icons.get(nameIndex).get(0));
    }

    private Component categoryLabel(int index) {
        List<ShanhaiTerminalReport.Category> categories = report.categories();
        return index >= 0 && index < categories.size() ? categories.get(index).label() : Component.empty();
    }

    private String firstLine(Component text, int width) {
        List<FormattedCharSequence> lines = font.split(text, Math.max(1, width));
        if (lines.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        lines.get(0).accept((index, style, codePoint) -> {
            builder.appendCodePoint(codePoint);
            return true;
        });
        return builder.toString();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (EcoStyle.hit(mouseX, mouseY, btnCopyX, btnY, 104, btnH)) { copyAll(); return true; }
            if (EcoStyle.hit(mouseX, mouseY, btnBoxesX, btnY, 104, btnH)) { showBoxes(); return true; }
            if (EcoStyle.hit(mouseX, mouseY, btnFilterX, btnY, btnCloseX - btnFilterX - 6, btnH)) {
                cycleFilter();
                return true;
            }
            if (EcoStyle.hit(mouseX, mouseY, btnCloseX, btnY, 86, btnH)) { onClose(); return true; }

            if (mouseX >= left + 8 && mouseX < left + PANEL_W - 8
                    && mouseY >= listTop && mouseY < listBottom) {
                List<VisibleRow> rows = visibleRows();
                int index = (int) ((mouseY - listTop + scroll) / ROW_H);
                if (index >= 0 && index < rows.size()) {
                    VisibleRow row = rows.get(index);
                    if (row.pos() == null && row.group().expandable()) {
                        if (!expandedKeys.remove(row.group().key)) expandedKeys.add(row.group().key);
                        return true;
                    }
                    BlockPos pos = row.pos() == null ? row.group().positions.get(0) : row.pos();
                    Minecraft.getInstance().keyboardHandler.setClipboard(posText(pos));
                    setNotice(tr("report.copied_one", posText(pos)));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= left && mouseX < left + PANEL_W && mouseY >= listTop && mouseY < listBottom) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - delta * ROW_H * 2));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
