package com.gtlmodular.client.gui.terminal.print;

import java.util.ArrayList;
import java.util.List;

import com.gtlmodular.client.gui.terminal.print.TerminalTheme.Fam;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** 替换部件面板（39 档）：4 家族分组、真实方块图标、点格即选中返回。 */
public final class TerminalTierPanel {

    private TerminalTierPanel() {}

    private static final String[] KEYS = {"sc", "sepm", "cal", "coil"};
    private static final String[] CN = {"恒星热力容器", "太空电梯模块", "部件装配外壳", "线圈"};
    private static final String[][] FALLBACK = {
            {"恒星炽热外壳", "进阶恒星炽热外壳", "终极恒星炽热外壳"},
            {"动力模块 Ⅰ", "动力模块 Ⅱ", "动力模块 Ⅲ", "动力模块 Ⅳ", "动力模块 Ⅴ"},
            {"LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UXV", "OpV", "MAX"},
            {"超级热容", "白铜", "坎塔尔合金", "镍铬合金", "钌钨钼合金", "高速钢-G", "铑钯合金", "凯金合金",
                    "三钛", "渊狱合金", "泰坦钢", "精金", "超能硅岩-塔兰", "星辉", "无尽", "海珀珍", "永恒"}
    };
    private static final int COLS = 6;
    private static final float CW = 76, CH = 18, GX = 4, GY = 22;

    /** 网页端 v32 的展示名与格数（SEPM 固定 5 档，总 39 档）。 */
    private static final String[][] WEB_LABELS = {
            {"恒星炽热外壳", "进阶恒星炽热外壳", "终极恒星炽热外壳"},
            {"动力模块 Ⅰ", "动力模块 Ⅱ", "动力模块 Ⅲ", "动力模块 Ⅳ", "动力模块 Ⅴ"},
            {"LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UXV", "OpV", "MAX"},
            {"超级热容", "白铜", "坎塔尔合金", "镍铬合金", "钌钨钼合金", "高速钢-G", "铑钯合金", "凯金合金",
                    "三钛", "渊狱合金", "泰坦钢", "精金", "超能硅岩-塔兰", "星辉", "无尽", "海珀珍", "永恒"}
    };

    /** 取某家族的方块（取不到就返回空数组）。 */
    public static Block[] blocks(String key) {
        try {
            var lazy = org.gtlcore.gtlcore.common.block.BlockMap.tierBlockMap.get(key);
            return lazy == null ? new Block[0] : lazy.get();
        } catch (Throwable t) {
            return new Block[0];
        }
    }

    public static String name(String key, int i) {
        String[] web = WEB_LABELS[index(key)];
        if (i >= 0 && i < web.length) return web[i];
        Block[] b = blocks(key);
        if (i >= 0 && i < b.length) return shortName(key, b[i].getName().getString());
        return "?";
    }

    /** 网页版格子里是短标签（LV / 白铜 / 动力模块 Ⅱ），不是方块全名。 */
    private static String shortName(String key, String full) {
        if (full == null) return "?";
        String n = full;
        if ("cal".equals(key)) {
            int p = n.indexOf('(');
            int q = n.indexOf('（');
            if (p > 0) n = n.substring(p + 1).replace(")", "");
            else if (q > 0) n = n.substring(q + 1).replace("）", "");
            else if (n.startsWith("部件装配线外壳")) n = n.substring("部件装配线外壳".length());
        } else if ("coil".equals(key)) {
            n = n.replace("线圈方块", "").replace("线圈", "");
        } else if ("sepm".equals(key)) {
            n = n.replace("太空电梯动力模块", "动力模块 ");
        }
        // 少数方块名带一个前导 d（贴图变体），格子里去掉，避免 "d动力模块" 这种
        if (n.length() > 1 && (n.charAt(0) == 'd' || n.charAt(0) == 'D')
                && (Character.isIdeographic(n.charAt(1)) || n.charAt(1) == 'Ω')) {
            n = n.substring(1);
        }
        return n.trim().isEmpty() ? full : n.trim();
    }

    private static String[] fallback(String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return FALLBACK[i];
        return new String[0];
    }

    private static int size(String key) {
        int idx = index(key);
        String[] web = WEB_LABELS[idx];
        Block[] b = blocks(key);
        // 网页端是 3/5/14/17 共 39 档；真实方块可能多出 SEPM 6-7 档，界面按网页收口
        return Math.min(web.length, b.length > 0 ? b.length : web.length);
    }

    /** 真实档位总数（界面文案用，不再写死 39）。 */
    public static int totalTiers() {
        int total = 0;
        for (String key : KEYS) total += size(key);
        return total;
    }

    public static void draw(GuiGraphics g, TerminalScreen s) {
        TerminalDraw.rect(g, 23, 17, 520, 284, 0x381F2427);
        TerminalDraw.rect(g, 20, 14, 520, 284, TerminalTheme.PAPER);
        TerminalDraw.outline(g, 20, 14, 520, 284, TerminalTheme.INK);
        TerminalDraw.rect(g, 20, 14, 520, 12, TerminalTheme.INK);
        g.flush();
        TerminalFonts.drawMixed(g, Fam.MONO, 5.5f,
                "REPLACE / 替换部件 · " + totalTiers() + " 档 · 点击即选中",
                26, 17, 1.1f, 0xFFFFFFFF);
        // 网页 .tier-x：right:5px、宽 10 → 文字左缘 524；top 1.5
        TerminalFonts.draw(g, Fam.MONO, 7f, "×", 524, 15.5f, TerminalTheme.PAPER);
        s.hit(522, 14, 14, 12, "返回", s::closeTier, false);

        // 面板内容区高度固定（14..298），实际档位数可能多于设计的 39；先算总行数再压行距，
        // 行距下限 18（图标固定 16×16，再小会串行），并用 scissor 兜底防止溢出到页脚。
        int rowsTotal = 0;
        for (String key : KEYS) rowsTotal += Math.max(1, (size(key) + 5) / 6);
        // 网页实测：面板 (20,14)，分组标题 (33, 39/78/117/200)，首格 (33,51)。
        // 分组标题盒高 7.2，标题→格子的间距 12；格子高 18、列距 4。
        float top = 39f;
        float bottom = 14f + 284f - 26f;
        // 底部留 8px 给固定的说明行，避免压缩行距后最后一行文字贴到说明上
        float avail = bottom - top - KEYS.length * 12f - (KEYS.length - 1) * 5f - 8f;
        float gy = Math.max(20f, Math.min(GY, avail / Math.max(1, rowsTotal)));
        float ch = 18f;

        g.enableScissor(Math.round(s.originX + 22 * s.scale), Math.round(s.originY + 27 * s.scale),
                Math.round(s.originX + 538 * s.scale), Math.round(s.originY + 296 * s.scale));
        try {
            // scissor 边界会清批；先落一笔不可见色块，保证后续文字在色块之后出队。
            TerminalDraw.rect(g, 22, 28, 1, 1, TerminalTheme.PAPER);
            float y = top;
            for (String key : KEYS) {
                int n = size(key);
                TerminalFonts.drawMixed(g, Fam.MONO, 5f, CN[index(key)] + " · " + key.toUpperCase(),
                        33, y, 1.2f, TerminalTheme.INK_FAINT);
                y += 12;
                Block[] blocks = blocks(key);
                for (int j = 0; j < n; j++) {
                    float cx = 33 + (j % COLS) * (CW + GX);
                    float cy = y + (j / COLS) * gy;
                    int rawIdx = rawIndex(key, j);
                    boolean on = s.family.equals(key) && s.tierIndex == rawIdx;
                    boolean hover = s.isHover(cx, cy, CW, ch);
                    TerminalDraw.rect(g, cx, cy, CW, ch, on ? 0x0F3A5BC2 : 0x00FFFFFF);
                    TerminalDraw.outline(g, cx, cy, CW, ch,
                            on ? TerminalTheme.COBALT : (hover ? TerminalTheme.INK_SOFT : TerminalTheme.RULE));

                    String label = name(key, j);
                    float textX = cx + 22;
                    if (rawIdx >= 0 && rawIdx < blocks.length) {
                        s.itemScaled(g, blocks[rawIdx], (int) cx + 4, (int) cy + 1,
                                Math.min(1f, Math.max(0.75f, (ch - 1f) / 16f)));
                    } else {
                        TerminalDraw.rect(g, cx + 4, cy + 1, 14, 14, on ? TerminalTheme.COBALT : 0x668B9795);
                    }
                    // 文字按实际格高居中：ch 会因档位多而被压缩（gy 下限 18 → ch 15），
                    // 固定 +5 在压缩后就会偏上。
                    TerminalFonts.drawMixed(g, Fam.CN, 6f, label, textX, cy + Math.max(2f, (ch - 6f) / 2f), 0f,
                            on ? TerminalTheme.COBALT : TerminalTheme.INK_SOFT);

                    final String fk = key;
                    final int ti = rawIdx;
                    s.hit(cx, cy, CW, ch, label + " (" + (j + 1) + "/" + n + ")", () -> s.applyTier(fk, ti), false);
                }
                y += ((n + COLS - 1) / COLS) * gy + 5;
            }
        } finally {
            g.disableScissor();
        }

        TerminalFonts.drawMixed(g, Fam.MONO, 5f,
                "方块来自 BlockMap.tierBlockMap · 点击即时返回",
                32, 281, 0.8f, TerminalTheme.INK_FAINT);
    }

    /** 当前网页展示的格数（SEPM 收口到 5）。 */
    public static int count(String key) {
        return size(key);
    }

    /** 真实 BlockMap 下标 → 展示序再取名；用于配置里存的是 raw index 的场景。 */
    public static String nameForRaw(String key, int raw) {
        return name(key, displayIndex(key, raw));
    }

    private static int displayIndex(String key, int raw) {
        int n = size(key);
        for (int i = 0; i < n; i++) if (rawIndex(key, i) == raw) return i;
        return Math.max(0, Math.min(raw, n - 1));
    }

    /** 展示序 → 真实 BlockMap 下标（CAL 的网页顺序与 BlockMap 原始顺序不同）。 */
    private static int rawIndex(String key, int displayIndex) {
        if (!"cal".equals(key)) return displayIndex;
        Block[] b = blocks(key);
        String want = WEB_LABELS[index(key)][Math.max(0, Math.min(displayIndex, WEB_LABELS[index(key)].length - 1))];
        for (int i = 0; i < b.length; i++) {
            if (shortName(key, b[i].getName().getString()).equalsIgnoreCase(want)) return i;
        }
        return displayIndex;
    }

    private static int index(String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return i;
        return 0;
    }

    public static ItemStack icon(String key, int tier) {
        Block[] b = blocks(key);
        return b.length == 0 || tier < 0 || tier >= b.length ? ItemStack.EMPTY : new ItemStack(b[tier]);
    }

    public static List<String> names(String key) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < size(key); i++) out.add(name(key, i));
        return out;
    }
}
