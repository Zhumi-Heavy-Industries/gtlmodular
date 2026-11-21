package com.gtlmodular.client.gui.terminal.print;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.gtlmodular.client.ShanhaiTerminalReportClient;
import com.gtlmodular.client.gui.terminal.print.TerminalTheme.Fam;
import com.gtlmodular.client.gui.terminal.print.TerminalTheme.Page;
import com.gtlmodular.common.item.ShanhaiUltimateTerminalConfig;
import com.gtlmodular.common.item.terminal.ShanhaiChamberClassifier;
import com.gtlmodular.common.item.terminal.ShanhaiTerminalReport;
import com.gtlmodular.network.GTLModularNetwork;
import com.gtlmodular.network.OpenEcoSubsystemPacket;
import com.gtlmodular.network.UltimateTerminalConfigPacket;
import com.gtlmodular.network.UltimateTerminalConfigPacket.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * 哦鲸鲸终端 · v31 三页自绘界面。
 *
 * <p>整页 {@code drawManaged} 单批；三页 + 两个弹层共用一套命中表；准星/涟漪/视差都在本类收口。
 */
public final class TerminalScreen extends Screen {

    // ── 命中表
    public record Hit(float x, float y, float w, float h, String label, boolean ink, Runnable action) {}

    public record ReportRow(String category, String name, int count, int color,
                            List<BlockPos> coords, boolean expandable) {}

    public record Target(float x, float y, float w, float h, String label, boolean ink) {}

    private final InteractionHand hand;
    private final boolean preview;

    public Page page = Page.MAIN;
    public float scale = 1f;
    public float originX;
    public float originY;
    public float mouseX;
    public float mouseY;
    public float seconds;

    public String playerName = "wangxiaoai";
    public String machineName = "待扫描目标";
    public String boundAeText = "OVERWORLD 128, 64, -37";
    public String tierName = "恒星炽热外壳";
    public String family = "sc";
    public int tierIndex;
    public int repeat = 1;
    public int placed = 5;
    public int placeCount = 96;
    public int conflictCount = 12;
    public int missingCount = 8;
    public boolean boundAe = true;
    public boolean boundEco;
    public boolean switchesView;
    public boolean tierOpen;
    public boolean layerOpen;
    public boolean showBoxes;
    public int layerDraft = 1;
    public int filter;
    public int reportScroll;
    public int reportOmitted;
    public final int[] flags = {1, 1, 0, 1, 0, 0, 1, 0};
    public final boolean[] chamberOn = {true, true, true, false, true, false, true, false};
    public List<ReportRow> reportRows = new ArrayList<>();
    public boolean[] expanded = new boolean[0];

    private final List<Hit> hits = new ArrayList<>();
    private int overlayHitStart = -1;
    private final TerminalReticle reticle = new TerminalReticle();
    private final MineMenu menu = new MineMenu();
    private long openedAt;
    private long lastPerfLogAt;
    private boolean reportDefaultApplied;
    private Page drawnPage;
    private long pageSwitchAt;
    private Page prevDrawnPage = Page.MAIN;
    /** 网页 vt-reg 抖动：最后一次点击的目标与时刻。 */
    private float regTargetX, regTargetY, regAt = -100f;
    private final TerminalTransition transition = new TerminalTransition();
    private @Nullable ShanhaiTerminalReport report;
    /** 聊天报告链接打开时注入的指定报告；非空则直接停在报告页，不依赖手持终端物品。 */
    private final @Nullable ShanhaiTerminalReport forcedReport;

    public TerminalScreen(InteractionHand hand, boolean preview) {
        this(hand, preview, null);
    }

    /**
     * 聊天报告链接专用入口：注入指定报告并直接打开报告页。
     * 复用本类已与网页端对齐的 print.TerminalReport，不再走旧烘焙版报告屏。
     */
    public TerminalScreen(@Nullable ShanhaiTerminalReport forcedReport) {
        this(null, false, forcedReport);
    }

    private TerminalScreen(InteractionHand hand, boolean preview, @Nullable ShanhaiTerminalReport forcedReport) {
        super(Component.literal("哦鲸鲸终端"));
        this.hand = hand;
        this.preview = preview;
        this.forcedReport = forcedReport;
        if (forcedReport != null) this.page = Page.REPORT;
    }

    private static final class MineMenu {
        float px, py, tx, ty;
    }

    // ────────────────────────────── 生命周期

    @Override
    protected void init() {
        super.init();
        this.scale = Math.min(1f, Math.min((width - 8f) / TerminalTheme.W, (height - 8f) / TerminalTheme.H));
        // 原点必须落在整数 GUI 像素上：半像素起始会让整张纸的字形被重采样，看着整体发糊。
        this.originX = Math.round((width - TerminalTheme.W * scale) / 2f);
        this.originY = Math.round((height - TerminalTheme.H * scale) / 2f);
        this.openedAt = System.nanoTime();
        hideCursor();
        loadState();
    }

    private boolean cursorHidden;

    /** 网页端 .fx-sheet{cursor:none}：进终端隐藏系统指针，只留四角准星。 */
    private void hideCursor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;
        org.lwjgl.glfw.GLFW.glfwSetInputMode(mc.getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_CURSOR, org.lwjgl.glfw.GLFW.GLFW_CURSOR_HIDDEN);
        cursorHidden = true;
    }

    private void restoreCursor() {
        if (!cursorHidden) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() != null) {
            org.lwjgl.glfw.GLFW.glfwSetInputMode(mc.getWindow().getWindow(),
                    org.lwjgl.glfw.GLFW.GLFW_CURSOR, org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL);
        }
        cursorHidden = false;
    }

    private void loadState() {
        // 主机名先回到中性态：只有真正绑定到已加载控制器时才显示机器名，
        // 否则（预览/未手持/报告直达）一律保持网页端的「待扫描目标」。
        machineName = "待扫描目标";
        if (preview) {
            reportRows = sampleRows();
            expanded = new boolean[reportRows.size()];
            if (expanded.length > 0) expanded[0] = true;
            return;
        }
        // 聊天报告链接：注入指定报告并直接停在报告页；不依赖手持终端物品。
        if (forcedReport != null) {
            if (Minecraft.getInstance().player != null) {
                playerName = Minecraft.getInstance().player.getGameProfile().getName();
            }
            applyReport(forcedReport);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) playerName = mc.player.getGameProfile().getName();
        ItemStack stack = heldStack();
        if (stack.isEmpty()) {
            reportRows = sampleRows();
            expanded = new boolean[reportRows.size()];
            return;
        }
        repeat = Math.max(1, ShanhaiUltimateTerminalConfig.getRepeatCount(stack));
        flags[0] = ShanhaiUltimateTerminalConfig.isMirrored(stack) ? 1 : 0;
        flags[1] = ShanhaiUltimateTerminalConfig.isReplaceMode(stack) ? 1 : 0;
        flags[2] = ShanhaiUltimateTerminalConfig.isAbsoluteReplaceMode(stack) ? 1 : 0;
        flags[3] = ShanhaiUltimateTerminalConfig.isAeMode(stack) ? 1 : 0;
        flags[4] = ShanhaiUltimateTerminalConfig.isOrderMode(stack) ? 1 : 0;
        flags[5] = ShanhaiUltimateTerminalConfig.isNoChamberMode(stack) ? 1 : 0;
        flags[6] = ShanhaiUltimateTerminalConfig.isDismantleMode(stack) ? 1 : 0;
        flags[7] = ShanhaiUltimateTerminalConfig.isModuleCheckMode(stack) ? 1 : 0;
        family = ShanhaiUltimateTerminalConfig.getReplacementFamily(stack);
        tierIndex = ShanhaiUltimateTerminalConfig.getReplacementTier(stack, family);
        tierIndex = Math.max(0, Math.min(tierIndex, TerminalTierPanel.count(family) - 1));
        tierName = TerminalTierPanel.nameForRaw(family, tierIndex);
        boundAe = ShanhaiUltimateTerminalConfig.getBoundAe(stack) != null;
        boundEco = ShanhaiUltimateTerminalConfig.getBoundEco(stack) != null;
        var ae = ShanhaiUltimateTerminalConfig.getBoundAe(stack);
        if (ae != null) boundAeText = ae.dimension().location() + " " + ae.pos().toShortString();
        if (family == null || family.isEmpty()) family = "sc";

        // 主机名：读物品上绑定的控制器（本次会话最近一次扫描写入的坐标）
        machineName = "待扫描目标";
        var ctrl = ShanhaiUltimateTerminalConfig.getBoundController(stack);
        if (ctrl != null && mc.level != null) {
            var machine = com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(mc.level, ctrl.pos());
            if (machine instanceof com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine controller) {
                var def = controller.getDefinition();
                if (def != null) machineName = stripColorCodes(def.asStack().getHoverName().getString());
            }
        }

        applyReport(ShanhaiTerminalReportClient.latestScan());
    }

    /**
     * 把一份报告填充到计数/清单/舱室状态字段。
     * 正常路径传 latestScan()；聊天报告链接注入路径传 forcedReport，两条入口看到一致报告页。
     */
    private void applyReport(ShanhaiTerminalReport r) {
        report = r;
        placed = 0;
        if (r != null) {
            int[] c = r.counts();
            if (c.length >= 5) {
                missingCount = Math.max(0, c[0]);
                conflictCount = Math.max(0, c[2]);
                placeCount = Math.max(0, c[4]);
            } else if (c.length >= 3) {
                placeCount = c[0];
                conflictCount = c[1];
                missingCount = c[2];
            }
            long groupStart = System.nanoTime();
            reportRows = groupReport(r);
            long groupMs = (System.nanoTime() - groupStart) / 1_000_000L;
            if (groupMs >= 100L) {
                com.gtlmodular.GTLModular.LOGGER.warn(
                        "[TerminalPerf] report group: {} ms, input lines={}, grouped rows={}",
                        groupMs, r.lines().size(), reportRows.size());
            }
            reportOmitted = r.omitted();

            // 已放舱室：报告类别里出现过的舱室类型
            for (int i = 0; i < chamberOn.length; i++) {
                ShanhaiChamberClassifier.Type type = ShanhaiChamberClassifier.Type.values()[i];
                String label = Component.translatable(type.translationKey()).getString();
                int typeColor = type.color() & 0xFFFFFF;
                boolean found = r.categories().stream()
                        .anyMatch(cat -> cat.label().getString().equals(label)
                                || (cat.color() & 0xFFFFFF) == typeColor);
                chamberOn[i] = found;
                if (found) placed++;
            }
        } else {
            reportRows = new ArrayList<>();
            reportOmitted = 0;
            placeCount = 0;
            conflictCount = 0;
            missingCount = 0;
            java.util.Arrays.fill(chamberOn, false);
        }
        expanded = new boolean[reportRows.size()];
        if (expanded.length > 0) expanded[0] = true; // 网页端默认展开第一行
    }

    private ItemStack heldStack() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return ItemStack.EMPTY;
        if (hand != null) {
            ItemStack s = mc.player.getItemInHand(hand);
            if (!s.isEmpty()) return s;
        }
        for (ItemStack s : mc.player.getInventory().items) {
            if (s.getItem() == com.gtlmodular.common.data.GTLModularItems.WHALE_TERMINAL.get()) return s;
        }
        return ItemStack.EMPTY;
    }

    private static List<ReportRow> groupReport(ShanhaiTerminalReport report) {
        // 最多 MAX_LINES 条。旧实现对每条 line 顺序扫描既有 rows，并且 rows.indexOf + record 重建，
        // 报告中有 N 个不同材料时是 O(N²)；长扫描结果点开报告会卡住客户端主线程。
        // category/name 本来就是 report 表中的去重索引，直接打包成 long key，单遍聚合。
        final class Aggregate {
            final String category;
            final String name;
            final int color;
            final List<BlockPos> coords = new ArrayList<>();
            int count;
            Aggregate(String category, String name, int color) {
                this.category = category;
                this.name = name;
                this.color = color;
            }
        }
        java.util.LinkedHashMap<Long, Aggregate> grouped = new java.util.LinkedHashMap<>();
        var cats = report.categories();
        var names = report.names();
        // 名称索引已由网络报告去重；每个长文本只解 Component / 截断一次。
        List<String> displayNames = new ArrayList<>(names.size());
        for (Component name : names) displayNames.add(displayName(name.getString()));
        List<String> categoryNames = new ArrayList<>(cats.size());
        for (ShanhaiTerminalReport.Category category : cats) {
            categoryNames.add(stripColorCodes(category.label().getString()));
        }
        for (ShanhaiTerminalReport.Line line : report.lines()) {
            if (line.category() < 0 || line.category() >= cats.size()
                    || line.name() < 0 || line.name() >= names.size()) continue;
            long key = ((long) line.category() << 32) | (line.name() & 0xFFFFFFFFL);
            Aggregate row = grouped.get(key);
            if (row == null) {
                String category = categoryNames.get(line.category());
                String name = displayNames.get(line.name());
                row = new Aggregate(category, name, cats.get(line.category()).color());
                grouped.put(key, row);
            }
            row.count++;
            row.coords.add(line.pos());
        }
        List<ReportRow> rows = new ArrayList<>(grouped.size());
        for (Aggregate row : grouped.values()) {
            rows.add(new ReportRow(row.category, row.name, row.count, row.color, row.coords, true));
        }
        return rows;
    }

    private static String displayName(String name) {
        if (name == null) return "";
        int cut = name.indexOf('、');
        if (cut > 0) name = name.substring(0, cut);
        // 真实物品名带 MC 颜色码（§8/§r 等）；AWT 字体不解析，会把 § 和码字都画出来
        // （实机截图「§ 8ULV § r巨型输出总线」）。网页原型是纯文本，这里在显示层剥离以对齐。
        name = stripColorCodes(name);
        return TerminalFonts.clip(Fam.CN, 8f, name, 330f);
    }

    /** 剥离 Minecraft § 颜色码及其后的一个格式字符（§8/§r/§b/§l…），供所有真实数据显示前调用。 */
    public static String stripColorCodes(String s) {
        if (s == null || s.indexOf('§') < 0) return s == null ? "" : s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) { i++; continue; } // 跳过 § 与紧跟的格式字符
            sb.append(c);
        }
        return sb.toString();
    }

    private static List<ReportRow> sampleRows() {
        List<ReportRow> rows = new ArrayList<>();
        rows.add(new ReportRow("可放置", "恒星炎炀容器 Ⅶ", 24, TerminalTheme.COBALT,
                List.of(new BlockPos(128, 64, -37), new BlockPos(128, 65, -37), new BlockPos(129, 64, -37)), true));
        rows.add(new ReportRow("可放置", "太空电梯模块", 8, TerminalTheme.COBALT, List.of(), false));
        rows.add(new ReportRow("冲突", "组件装配外壳", 6, TerminalTheme.AMBER,
                List.of(new BlockPos(130, 64, -37)), false));
        rows.add(new ReportRow("缺失", "线圈 Ⅸ", 15, TerminalTheme.RED,
                List.of(new BlockPos(131, 64, -37)), false));
        return rows;
    }

    // ────────────────────────────── 渲染

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (cursorHidden) hideCursor();
        // 报告可能在屏幕打开后才到达：第一次真正画报告页时补展开第一行
        if (page == Page.REPORT && !reportDefaultApplied && !reportRows.isEmpty()) {
            reportDefaultApplied = true;
            if (expanded.length != reportRows.size()) expanded = new boolean[reportRows.size()];
            expanded[0] = true;
        }
        long renderStart = System.nanoTime();
        this.seconds = (renderStart - openedAt) / 1_000_000_000f;
        // 把纸张缩放交给字体系统做超采样：字号按「GUI 缩放 × 纸张缩放」烘焙，
        // 上屏才严格 1:1，不会在窗口偏小时被二次缩采样糊掉。
        TerminalFonts.setSheetScale(scale);
        if (page != drawnPage) {
            drawnPage = page;
            pageSwitchAt = renderStart;
            // 网页 go(n)：按目标页序号选变体（kinds[n % 3]），并重置 820ms 忙碌窗口；
            // 同时记下旧页，交叉淡化时要把旧页也画出来。
            transition.begin(this.seconds, page.ordinal(), prevDrawnPage.ordinal());
            prevDrawnPage = page;
        }
        // 内容进场：网页 .entering .sheet>* 的 fx-in（0.55s 从下方 5px 淡入，元素错峰）
        float pageShift = transition.shiftY(this.seconds, 0);
        this.mouseX = (mouseX - originX) / scale;
        this.mouseY = (mouseY - originY) / scale;
        hits.clear();

        // 视差缓动
        float mx = (this.mouseX / (float) TerminalTheme.W - 0.5f);
        float my = (this.mouseY / (float) TerminalTheme.H - 0.5f);
        menu.tx = -mx * 13f;
        menu.ty = -my * 8f;
        menu.px += (menu.tx - menu.px) * 0.10f;
        menu.py += (menu.ty - menu.py) * 0.10f;

        g.fill(0, 0, width, height, TerminalTheme.BACKDROP);
        g.pose().pushPose();
        try {
        g.pose().translate(originX, originY, 0);
        g.pose().scale(scale, scale, 1f);

        // ── 交叉淡化（网页 .sw-slot：旧页 leaving 淡出 + 新页 active 淡入，0.5s 内真重叠）
        // MC 的 GuiGraphics 没有整页 alpha。等效做法：
        //   1) 画旧页 → 用纸色以 fadeIn 盖一遍（旧页显得 1-fadeIn）
        //   2) 把新页画到另一层 → 再用纸色以 1-fadeIn 盖一遍（新页显得 fadeIn）
        // 已验证：纸上看到的颜色 = ink*a + paper*(1-a)，与网页 slot opacity 逐点一致。
        // 注意新页的纸底不能用不透明 PAPER，否则会把旧页死死盖住，所以它一样要洗。
        boolean crossfading = transition.crossfading(this.seconds);
        int prevPage = transition.previousPage();
        float fadeIn = transition.crossfade(this.seconds);
        if (crossfading && prevPage >= 0 && prevPage != page.ordinal()) {
            final Page oldPage = Page.values()[prevPage];
            // 旧页完整画一遍，再用纸色盖掉 fadeIn 那么多 → 剩余 (1-fadeIn) 可见
            g.drawManaged(() -> {
                g.pose().pushPose();
                try {
                    drawPageBody(g, oldPage);
                } finally {
                    g.pose().popPose();
                }
            });
            g.drawManaged(() -> washPage(g, fadeIn));
            // 旧页的洗白必须落地，否则接着画的新页会与它同批排序混乱
            g.flush();
        }

        // 网页 vt-reg：点击后内容整体抖 0.28s（25%→(1,-1)，55%→(-1,1)，100%→0）
        float regX = regOffsetX();
        float regY = regOffsetY();

        // 新页：纸底 + 页框先落（与网页 .grain/.crop 在前一致），再画内容
        g.drawManaged(() -> {
            g.pose().pushPose();
            try {
                g.pose().translate(regX, transition.shiftY(this.seconds, 0) + regY, 0);
                TerminalDraw.rect(g, 0, 0, TerminalTheme.W, TerminalTheme.H, TerminalTheme.PAPER);
                TerminalFrame.draw(g, page);
                navHits();
            } finally {
                g.pose().popPose();
            }
        });
        g.drawManaged(() -> {
            g.pose().pushPose();
            try {
                g.pose().translate(regX, transition.shiftY(this.seconds, 3) + regY, 0);
                switch (page) {
                    case MAIN -> drawMain(g);
                    case OPTIONS -> TerminalOptions.draw(g, this);
                    case REPORT -> TerminalReport.draw(g, this);
                }
            } finally {
                g.pose().popPose();
            }
        });
        if (crossfading) {
            // 新页也从 fadeIn 开始（等效于 .sw-slot.active 从 0 淡到 1）。
            // 必须先 flush，否则新页的纸底/色块与洗白层可能按 RenderType 重新排序，
            // 出现「先洗后画」导致新页完全不淡入。
            g.flush();
            g.drawManaged(() -> washPage(g, 1f - fadeIn));
        }
        if (transition.active(this.seconds)) {
            // 删除原来的纸白遮罩（网页 v32 原话：那层 opacity .92 的全屏遮罩是「瞬间黑屏」真凶）。
            // 现在换成网页同款四层：光幕 .12 峰值 + 构造标记三层闪切 + 变体（scan/mosaic/wipe）。
            g.drawManaged(() -> transition.draw(g, this.seconds));
        }
        overlayHitStart = -1;
        if (layerOpen || tierOpen) {
            g.drawManaged(() -> {
                overlayHitStart = hits.size();
                if (layerOpen) TerminalLayerPopup.draw(g, this);
                if (tierOpen) TerminalTierPanel.draw(g, this);
            });
        }
        Target target = lockedTarget();
        g.drawManaged(() -> reticle.draw(g, this.mouseX, this.mouseY, seconds,
                target == null ? null : new TerminalReticle.Target(target.x, target.y, target.w, target.h,
                        target.label, target.ink)));
        } finally {
            g.pose().popPose();
        }
        long elapsed = System.nanoTime() - renderStart;
        long now = System.nanoTime();
        if (elapsed >= 40_000_000L && now - lastPerfLogAt >= 3_000_000_000L) {
            lastPerfLogAt = now;
            com.gtlmodular.GTLModular.LOGGER.warn(
                    "[TerminalPerf] GUI render {} ms, page={}, reportRows={}, tierOpen={}, layerOpen={}",
                    elapsed / 1_000_000L, page, reportRows.size(), tierOpen, layerOpen);
        }
    }

    /** 画一整页（纸底 + 页框 + 内容），不带任何进场位移。交叉淡化时要画旧页。 */
    private void drawPageBody(GuiGraphics g, Page p) {
        TerminalDraw.rect(g, 0, 0, TerminalTheme.W, TerminalTheme.H, TerminalTheme.PAPER);
        TerminalFrame.draw(g, p);
        switch (p) {
            case MAIN -> drawMain(g);
            case OPTIONS -> TerminalOptions.draw(g, this);
            case REPORT -> TerminalReport.draw(g, this);
        }
    }

    /** 用纸色以 coverage 不透明度盖住整页：等效于把整页 opacity 设为 (1-coverage)。 */
    private static void washPage(GuiGraphics g, float coverage) {
        if (coverage <= 0.002f) return;
        int a = Math.round(255f * Math.max(0f, Math.min(1f, coverage)));
        TerminalDraw.rect(g, 0, 0, TerminalTheme.W, TerminalTheme.H, (a << 24) | 0xEFF1EF);
    }

    private void navHits() {
        float x = 36;
        for (Page p : Page.values()) {
            float numW = TerminalFonts.mixedWidth(Fam.MONO, 6f, p.num, 0f);
            float nameW = TerminalFonts.mixedWidth(Fam.MONO, 7f, p.cn, 1.2f);
            final Page target = p;
            // 命中区盖住「下划线 + 编号 + 名称」整块（y318..334），
            // 与准星吸附框（目标外扩 4px）一致。原为 y321 h12，鼠标落在页签上方
            // 几像素就会看到准星已吸附、点下去却落空（实机反馈「选中不到页面切换」）。
            hit(x - 4, 318, numW + 5 + nameW + 14, 16, p.cn, () -> {
                this.page = target;
                this.tierOpen = false;
                this.layerOpen = false;
            }, true);
            x += numW + 5 + nameW + 26;
        }
    }

    private void drawMain(GuiGraphics g) {
        // 水印
        TerminalFonts.draw(g, Fam.SERIF_IT, 38f, "M", 196, 30, alpha(TerminalTheme.INK, 13));
        TerminalFonts.draw(g, Fam.SERIF_IT, 30f, "T", 352, 6, alpha(TerminalTheme.INK, 13));
        TerminalFonts.draw(g, Fam.SERIF, 26f, "A", 2, 250, alpha(TerminalTheme.INK, 26));
        TerminalFonts.draw(g, Fam.SERIF, 16f, "L", 14, 40, alpha(TerminalTheme.INK, 31));

        // CONFIG 徽标（网页 .chip-d 实测：黑底 x36 w65.4 h10，红块 x76.8 w15.7，两段文字 top 46）
        // CSS .chip-d{padding:2px 6px} + .chip-d em{padding:0 3px;margin-left:4px}：
        // 文字左边 = 36+6 = 42；红块左 = 42+cw+4；红块后仍有 6px 黑底拖尾。
        float cw = TerminalFonts.trackedWidth(Fam.MONO, 5.5f, "CONFIG", 1.8f);
        float cw06 = TerminalFonts.trackedWidth(Fam.MONO, 5.5f, "06", 1.8f);
        float redX = 42 + cw + 4;
        float redW = cw06 + 6;
        TerminalDraw.rect(g, 36, 44, redX + redW + 6 - 36, 10, TerminalTheme.INK);
        TerminalDraw.rect(g, redX, 44, redW, 10, TerminalTheme.RED);
        g.flush();
        TerminalFonts.drawTracked(g, Fam.MONO, 5.5f, "CONFIG", 42, 46, 1.8f, 0xFFFFFFFF);
        // 网页 CSS：.chip-d 黑底纸白字，.chip-d em(06) 红底 #B43A34、纸白字
        TerminalFonts.drawTracked(g, Fam.MONO, 5.5f, "06", redX + 3, 46, 1.8f, 0xFFFFFFFF);
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "06 / GTL MODULAR", 110, 46, 1.6f, TerminalTheme.INK_FAINT);

        TerminalDraw.rect(g, 36, 66, 2, 30, TerminalTheme.COBALT);
        TerminalFonts.drawMixedHeavy(g, Fam.CN, 26f,
                TerminalFonts.clip(Fam.CN, 26f, machineName, 196f), 46, 64, 2f, TerminalTheme.INK);
        TerminalFonts.draw(g, Fam.SERIF_IT, 13f, playerName, 46, 102, TerminalTheme.INK_SOFT);
        float sigW = TerminalFonts.width(Fam.SERIF_IT, 13f, playerName);
        // 网页 .kicker 实测 OPERATOR 盒 (114.6,108)：签名起点 46 + 签名宽 + 8，top 108
        TerminalFonts.drawMixed(g, Fam.MONO, 5f, "OPERATOR", 46 + sigW + 8, 108, 2f, TerminalTheme.INK_FAINT);
        TerminalDraw.hline(g, 36, 266, 126, TerminalTheme.RULE);

        // 三卡
        String[] titles = {"开始扫描", "配置舱室", "选择档位"};
        // 网页端第三卡固定显示总档位数与「需扫描」，不显示已选，避免暗示已完成
        String tierSub = TerminalTierPanel.totalTiers() + " 档可选 · 需扫描";
        String[] subs = {"手持终端 · 世界扫描", "8 类 · 6 必需", tierSub};
        for (int i = 0; i < 3; i++) {
            float x = 36 + i * 78;
            boolean hover = isHover(x, 136, 72, 36);
            TerminalDraw.outline(g, x, 136, 72, 36, hover ? TerminalTheme.COBALT : TerminalTheme.RULE);
            if (hover) TerminalDraw.rect(g, x, 136, 72, 36, 0x0D3A5BC2);
            // 网页 .qa 实测：.t1 (x+6,y+7) 7.5px；.t2 (x+6,y+23) 5.5px 且 letter-spacing .6；.ar 右内边距 6
            TerminalFonts.drawMixed(g, Fam.CN, 7.5f, titles[i], x + 6, 143, 0f, TerminalTheme.INK);
            TerminalFonts.drawMixed(g, Fam.MONO, 5.5f, subs[i], x + 6, 159, 0.6f, TerminalTheme.INK_SOFT);
            TerminalFonts.draw(g, Fam.MONO, 9f, "›", x + 61, 150, TerminalTheme.COBALT);
            final int idx = i;
            hit(x, 136, 72, 36, titles[i], () -> {
                if (idx == 0) {                     // 手持扫描：收起界面并提示
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null && !preview) {
                        mc.player.displayClientMessage(
                                Component.literal("§b[哦鲸鲸终端] 终端收起 · 请左键控制器开始扫描"), true);
                    }
                    onClose();
                }
                else if (idx == 1) page = Page.OPTIONS;
                else tierOpen = true;
            }, false);
        }

        echo(g, 36, 179);
        bind(g, 36, 190, "BIND 01 · AE NETWORK", "01", boundAe,
                "AE 网络 · " + (boundAe ? "已绑定" : "未绑定"),
                boundAe ? boundAeText : "点击绑定 / 打开子系统　›", false, null, true);
        // v32 源码 BIND 02 不是 .bind.on，表头恒为 INK 黑；状态文字仍按真实绑定
        bind(g, 36, 232, "BIND 02 · ECO SUBSYSTEM", "02", false,
                "ECO 子系统 · " + (boundEco ? "已绑定" : "未绑定"),
                boundEco ? "已连接" : "点击绑定 / 打开子系统　›", true,
                () -> {
                    if (!preview && hand != null) {
                        GTLModularNetwork.CHANNEL.sendToServer(new OpenEcoSubsystemPacket(hand));
                        onClose();
                    }
                });

        // 大圆盘（视差）+ SYSTEM 卡（1.5 层）
        // 档位面板会盖住整块内容区，打开时跳过圆盘，省掉每帧上万次 fill
        if (!tierOpen) {
            g.pose().pushPose();
            try {
                g.pose().translate(menu.px, menu.py, 0);
                TerminalPlate.draw(g, seconds, isHover(264, 26, 270, 270), flags);
            } finally {
                g.pose().popPose();
            }
        }
        // 圆盘文字必须在 SYSTEM 卡纸底之前落地；卡的第一笔是色块，flush 后顺序安全。
        g.flush();

        g.pose().pushPose();
        try {
            g.pose().translate(menu.px * 1.5f, menu.py * 1.5f, 0);
            systemCard(g, 300, 62);
        } finally {
            g.pose().popPose();
        }
    }

    private void echo(GuiGraphics g, float x, float y) {
        // 网页 .echo: letter-spacing .6px，字号6.5——所有段统一带字距，否则三组数值会逐段左移。
        final float ls = 0.6f;
        String pre = "上次扫描 · 可放置 ";
        float cx = x;
        TerminalFonts.drawMixed(g, Fam.MONO, 6.5f, pre, cx, y, ls, TerminalTheme.INK_SOFT);
        cx += TerminalFonts.mixedWidth(Fam.MONO, 6.5f, pre, ls);
        cx = echoValue(g, cx, y, String.valueOf(placeCount));
        TerminalFonts.drawMixed(g, Fam.MONO, 6.5f, " 冲突 ", cx, y, ls, TerminalTheme.INK_SOFT);
        cx += TerminalFonts.mixedWidth(Fam.MONO, 6.5f, " 冲突 ", ls);
        cx = echoValue(g, cx, y, String.valueOf(conflictCount));
        TerminalFonts.drawMixed(g, Fam.MONO, 6.5f, " 缺失 ", cx, y, ls, TerminalTheme.INK_SOFT);
        cx += TerminalFonts.mixedWidth(Fam.MONO, 6.5f, " 缺失 ", ls);
        cx = echoValue(g, cx, y, String.valueOf(missingCount));
        TerminalFonts.drawMixed(g, Fam.MONO, 6.5f, " 查看详情 ›", cx, y, ls, TerminalTheme.COBALT);
        hit(x, y - 2, 230, 12, "查看详情", () -> page = Page.REPORT, false);
    }

    private float echoValue(GuiGraphics g, float x, float y, String v) {
        TerminalFonts.drawMixedHeavy(g, Fam.MONO, 6.5f, v, x, y, 0.6f, TerminalTheme.INK);
        return x + TerminalFonts.mixedWidth(Fam.MONO, 6.5f, v, 0.6f);
    }

    private void bind(GuiGraphics g, float x, float y, String title, String num, boolean on,
                      String status, String coord, boolean action, Runnable click) {
        bind(g, x, y, title, num, on, status, coord, action, click, false);
    }

    /**
     * @param pulseOn 网页 BIND 01 的菱形带 {@code .dot-pulse}（3s opacity 1↔.4），BIND 02 不带
     */
    private void bind(GuiGraphics g, float x, float y, String title, String num, boolean on,
                      String status, String coord, boolean action, Runnable click, boolean pulseOn) {
        boolean hover = action && isHover(x, y, 230, 36);
        TerminalDraw.rect(g, x, y, 230, 36, 0xE6EFF1EF);
        TerminalDraw.outline(g, x, y, 230, 36, hover ? TerminalTheme.COBALT : 0x801F2427);
        TerminalDraw.rect(g, x, y, 230, 9, on || hover ? TerminalTheme.COBALT : TerminalTheme.INK);
        g.flush();
        TerminalFonts.drawMixed(g, Fam.MONO, 5f, title, x + 4, y + 1.5f, 1.2f, TerminalTheme.PAPER);
        // 网页 .bind .bh em{right:4px}：编号右对齐到卡片内边距 4
        TerminalFonts.drawRightMixed(g, Fam.MONO, 5f, num, x + 226, y + 1.5f, TerminalTheme.PAPER);
        g.pose().pushPose();
        // 网页 .dot{left:5px;top:14px;5×5} 旋转 45° 后包围盒 (x+5..x+12, y+14..y+21)，中心 (x+8.5,y+17.5)
        g.pose().translate(x + 8.5f, y + 17.5f, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(45f));
        if (on) {
            // 网页 BIND 01 的 .dot 带 .dot-pulse：3s ease-in-out infinite，opacity 1↔.4
            float pulse = 1f;
            if (pulseOn) {
                float cyc = (seconds % 3f) / 3f;
                // 0%,100%→1；50%→.4，ease-in-out 近似为余弦
                pulse = 0.7f + 0.3f * (float) Math.cos(cyc * Math.PI * 2.0);
            }
            int a = Math.round(255f * Math.max(0.4f, Math.min(1f, pulse)));
            TerminalDraw.rect(g, -2.5f, -2.5f, 5, 5, (a << 24) | 0x3A5BC2);
        } else {
            TerminalDraw.outline(g, -2.5f, -2.5f, 5, 5, TerminalTheme.INK);
        }
        g.pose().popPose();
        // 网页：.bind .st{top:12px} 盒 y+12.5；未绑定色是 INK_SOFT(85,96,95)，已绑定是 INK。
        // 之前一律用 INK 导致「未绑定」显得过重（与网页对照不一致）。
        TerminalFonts.drawMixed(g, Fam.CN, 8f, TerminalFonts.clip(Fam.CN, 8f, status, 200f), x + 15, y + 12, 0f,
                on ? TerminalTheme.INK : TerminalTheme.INK_SOFT);
        TerminalFonts.drawMixed(g, Fam.MONO, 5.5f, TerminalFonts.clip(Fam.MONO, 5.5f, coord, 200f),
                x + 15, y + 23, 0.4f, hover ? TerminalTheme.COBALT : TerminalTheme.INK_FAINT);
        if (action && click != null) hit(x, y, 230, 36, status, click, false);
    }

    private void systemCard(GuiGraphics g, float x, float y) {
        TerminalDraw.rect(g, x + 2, y + 2, 132, 46, 0x381F2427);
        TerminalDraw.rect(g, x, y, 132, 46, 0xF0EFF1EF);
        TerminalDraw.outline(g, x, y, 132, 46, TerminalTheme.INK);
        TerminalDraw.rect(g, x, y, 132, 9, TerminalTheme.INK);
        g.flush();
        TerminalFonts.drawMixed(g, Fam.MONO, 5f, "SYSTEM / 系统状态", x + 4, y + 1.5f, 1.2f, TerminalTheme.PAPER);
        // 网页实测：配置行 y+12；5/8 钴蓝起点 x+21.3；「· 扫描未执行」紧跟
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "配置 ", x + 5, y + 12, 0f, TerminalTheme.INK);
        float cfgX = x + 5 + TerminalFonts.mixedWidth(Fam.MONO, 6f, "配置 ", 0f);
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, placed + "/8", cfgX, y + 12, 0f, TerminalTheme.COBALT);
        float cfgW = TerminalFonts.mixedWidth(Fam.MONO, 6f, placed + "/8", 0f);
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, report == null ? " · 扫描未执行" : " · 已扫描",
                cfgX + cfgW, y + 12, 0f, TerminalTheme.INK);

        float ox = menu.px * 1.5f, oy = menu.py * 1.5f;
        boolean goHover = isHover(x + 4 + ox, y + 19 + oy, 124, 12);
        if (goHover) TerminalDraw.rect(g, x + 4, y + 19, 124, 12, 0x123A5BC2);
        // 网页 .cfg-go-label (x+6,y+21)、.cfg-go-ar 右内边距 2
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, placed >= 8 ? "开始扫描" : "完成配置",
                x + 6, y + 21, 0f, TerminalTheme.COBALT);
        TerminalFonts.draw(g, Fam.MONO, 7f, "›", x + 122, y + 20, TerminalTheme.COBALT);
        hit(x + 4 + ox, y + 19 + oy, 124, 12, placed >= 8 ? "开始扫描" : "完成配置",
                () -> { if (placed >= 8) onClose(); else page = Page.OPTIONS; }, false);

        TerminalDraw.hline(g, x + 4, x + 128, y + 31, TerminalTheme.RULE);
        // 网页 .layer-open：结构层数(x+6,y+35)、读数(x+57,y+34) 8px、/ 648(x+85,y+35) 5.5px、› 右 2
        TerminalFonts.drawMixed(g, Fam.MONO, 6f, "结构层数", x + 6, y + 35, 0f, TerminalTheme.INK_SOFT);
        String read = String.format("%03d", repeat);
        TerminalFonts.drawMixed(g, Fam.MONO, 8f, read, x + 57, y + 34, 0f, TerminalTheme.COBALT);
        TerminalFonts.drawMixed(g, Fam.MONO, 5.5f, "/ 648", x + 85, y + 35, 0f, TerminalTheme.INK_FAINT);
        TerminalFonts.draw(g, Fam.MONO, 7f, "›", x + 122, y + 34, TerminalTheme.COBALT);
        hit(x + 4 + ox, y + 31 + oy, 124, 13, "结构层数", () -> {
            layerDraft = Math.max(1, repeat);
            layerOpen = true;
        }, false);
    }

    // ────────────────────────────── 命中 / 输入

    public void hit(float x, float y, float w, float h, String label, Runnable action, boolean ink) {
        hits.add(new Hit(x, y, w, h, label, ink, action));
    }

    private Target lockedTarget() {
        // 弹层打开时只吸附弹层之后登记的命中，与 mouseClicked 使用同一条分界线，
        // 否则准星会吸到被面板盖住、点不动的底层元素。
        int start = (tierOpen || layerOpen) && overlayHitStart >= 0 ? overlayHitStart : 0;
        for (int i = hits.size() - 1; i >= start; i--) {
            Hit h = hits.get(i);
            if (contains(h)) return new Target(h.x, h.y, h.w, h.h, h.label, h.ink);
        }
        return null;
    }

    private boolean contains(Hit h) {
        return mouseX >= h.x && mouseX <= h.x + h.w && mouseY >= h.y && mouseY <= h.y + h.h;
    }

    public boolean isHover(float x, float y, float w, float h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    @Override
    public void removed() {
        restoreCursor();
        super.removed();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        float x = (float) ((mx - originX) / scale);
        float y = (float) ((my - originY) / scale);
        int start = (tierOpen || layerOpen) && overlayHitStart >= 0 ? overlayHitStart : 0;
        for (int i = hits.size() - 1; i >= start; i--) {
            Hit h = hits.get(i);
            if (x >= h.x && x <= h.x + h.w && y >= h.y && y <= h.y + h.h) {
                reticle.click(x, y, h.label, h.ink);
                // 网页 .item.reg / vt-reg：点击后该元素抖动 0.28s（套印错位感）
                regTargetX = h.x;
                regTargetY = h.y;
                regAt = seconds;
                h.action.run();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /** 网页 {@code @keyframes vt-reg} 的抖动偏移：25%→(1,-1) 55%→(-1,1) 100%→(0,0)，总 0.28s。 */
    public float regOffsetX() {
        return regOffset(false);
    }

    public float regOffsetY() {
        return regOffset(true);
    }

    private float regOffset(boolean vertical) {
        float age = seconds - regAt;
        if (age < 0f || age > 0.28f) return 0f;
        float u = age / 0.28f;
        float vx, vy;
        if (u < 0.25f) {
            float k = u / 0.25f;
            vx = k; vy = -k;
        } else if (u < 0.55f) {
            float k = (u - 0.25f) / 0.30f;
            vx = 1f - 2f * k;
            vy = -1f + 2f * k;
        } else {
            float k = (u - 0.55f) / 0.45f;
            vx = -1f + k;
            vy = 1f - k;
        }
        return vertical ? vy : vx;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (page == Page.REPORT && !tierOpen && !layerOpen) {
            reportScroll = (int) Math.max(0, Math.min(maxScroll(), reportScroll - delta * 24));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private int maxScroll() {
        float total = 0;
        for (int i = 0; i < reportRows.size(); i++) {
            if (!rowVisible(reportRows.get(i))) continue;
            total += 21 + (expanded[i] ? 48 : 0);
        }
        return (int) Math.max(0, total - TerminalReport.LIST_CLIP_H);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (tierOpen) { tierOpen = false; return true; }
            if (layerOpen) { layerOpen = false; return true; }
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ────────────────────────────── 动作

    public int onCount() {
        int n = 0;
        for (int f : flags) if (f != 0) n++;
        return n;
    }

    public void toggleFlag(int i) {
        flags[i] = flags[i] == 0 ? 1 : 0;
        sendSwitch(i, flags[i] != 0);
    }

    private void sendSwitch(int i, boolean on) {
        if (preview || hand == null) return;
        Field f = switch (i) {
            case 0 -> Field.MIRROR;
            case 1 -> Field.REPLACE;
            case 2 -> Field.ABSOLUTE_REPLACE;
            case 3 -> Field.AE_MODE;
            case 4 -> Field.ORDER_MODE;
            case 5 -> Field.NO_CHAMBER;
            case 6 -> Field.DISMANTLE;
            default -> Field.MODULE_CHECK;
        };
        GTLModularNetwork.CHANNEL.sendToServer(new UltimateTerminalConfigPacket(hand, f, on ? 1 : 0, ""));
    }

    public void applyLayer() {
        repeat = Math.max(1, Math.min(TerminalLayerPopup.MAX, layerDraft));
        layerOpen = false;
        if (!preview && hand != null) {
            GTLModularNetwork.CHANNEL.sendToServer(
                    new UltimateTerminalConfigPacket(hand, Field.REPEAT, repeat, ""));
        }
    }

    public void closeLayer() {
        layerOpen = false;
    }

    /** 点刻度尺按比例设值（刻度尺 x=308..488）。 */
    public void layerFromMouse() {
        float p = (mouseX - 310f) / 176f;
        p = Math.max(0f, Math.min(1f, p));
        layerDraft = Math.max(1, Math.min(TerminalLayerPopup.MAX, Math.round(1 + p * (TerminalLayerPopup.MAX - 1))));
    }

    public void closeTier() {
        tierOpen = false;
    }

    public void applyTier(String fam, int tier) {
        family = fam;
        tierIndex = tier;
        tierName = TerminalTierPanel.nameForRaw(fam, tier);
        tierOpen = false;
        if (!preview && hand != null) {
            GTLModularNetwork.CHANNEL.sendToServer(
                    new UltimateTerminalConfigPacket(hand, Field.REPLACEMENT, tier, fam));
        }
    }

    public void toggleRow(int i) {
        if (i >= 0 && i < expanded.length) {
            expanded[i] = !expanded[i];
            reportScroll = Math.min(reportScroll, maxScroll());
        }
    }

    public void copyReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("Scan Report\n");
        for (ReportRow r : reportRows) {
            sb.append(r.category()).append('\t').append(r.name()).append('\t').append(r.count());
            if (!r.coords().isEmpty()) sb.append('\t').append(TerminalReport.fmt(r.coords().get(0)));
            sb.append('\n');
        }
        TerminalReport.copyToClipboard(sb.toString());
    }

    public void toggleBoxes() {
        showBoxes = !showBoxes;
        if (!showBoxes || preview) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || reportRows.isEmpty()) return;
        List<com.gtlmodular.network.ShanhaiStructureHighlightPacket.Marker> markers = new ArrayList<>();
        for (ReportRow row : reportRows) {
            if (!rowVisible(row)) continue;
            for (BlockPos pos : row.coords()) {
                if (markers.size() >= 4096) break;
                markers.add(new com.gtlmodular.network.ShanhaiStructureHighlightPacket.Marker(pos, row.color()));
            }
        }
        if (!markers.isEmpty()) {
            com.gtlmodular.client.ShanhaiStructureHighlightClient.highlightAsync(
                    mc.level.dimension(), System.currentTimeMillis() + 30_000L, markers);
        }
    }

    private boolean rowVisible(ReportRow row) {
        if (filter <= 0) return true;
        java.util.List<String> cats = distinctCategories();
        int i = filter - 1;
        return i < cats.size() && cats.get(i).equals(row.category());
    }

    /**
     * 真实报告的分类标签是仓室名（输入仓室 / 其他仓室 …），不是
     * 「可放置 / 冲突 / 缺失」——那三个是网页原型的假数据。
     *
     * <p>之前筛选器直接拿 {"可放置".equals(row.category())} 比，永远不成立，
     * 一按整页就空白。现在：0 = 全部；1..N = 按真实出现的分类逐个筛（标签取该分类名）。
     */
    public String filterName() {
        if (filter <= 0) return "全部";
        java.util.List<String> cats = distinctCategories();
        int i = filter - 1;
        return i < cats.size() ? cats.get(i) : "全部";
    }

    /** 报告里实际出现过的分类，按首次出现顺序去重。 */
    public java.util.List<String> distinctCategories() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (ReportRow row : reportRows) {
            if (row.category() != null && !out.contains(row.category())) out.add(row.category());
        }
        return out;
    }

    /** 某个分类在报告里的行数（不是方块总数，与摘要行「分类 数量」一致）。 */
    public int countOf(String category) {
        int n = 0;
        for (ReportRow row : reportRows) {
            if (category != null && category.equals(row.category())) n++;
        }
        return n;
    }

    public void cycleFilter() {
        filter = (filter + 1) % (distinctCategories().size() + 1);
        reportScroll = Math.min(reportScroll, maxScroll());
    }

    public String targetText() {
        if (report != null) return "TARGET " + report.target().toShortString();
        return "TARGET 128,64,-37";
    }

    /** 最近一次扫描耗时（服务端 planner 计时，0=未计时）。 */
    public long scanMs() {
        return report == null ? 0L : Math.max(0L, report.elapsedMs());
    }

    /** 最近一次扫描目标坐标；无报告时与网页原型一致显示占位。 */
    public String scanTarget() {
        return report == null ? "128,64,-37" : TerminalReport.fmt(report.target());
    }

    public void item(GuiGraphics g, Block block, int x, int y) {
        if (block == null) return;
        g.renderItem(new ItemStack(block), x, y);
    }

    /** 行高被压缩时按比例缩图标，避免 16×16 串到下一行。 */
    public void itemScaled(GuiGraphics g, Block block, int x, int y, float scale) {
        if (block == null) return;
        if (scale >= 0.999f) {
            g.renderItem(new ItemStack(block), x, y);
            return;
        }
        g.pose().pushPose();
        try {
            g.pose().translate(x, y, 0);
            g.pose().scale(scale, scale, 1f);
            g.renderItem(new ItemStack(block), 0, 0);
        } finally {
            g.pose().popPose();
        }
    }

    private static int alpha(int c, int a) {
        return (a << 24) | (c & 0x00FFFFFF);
    }
}
