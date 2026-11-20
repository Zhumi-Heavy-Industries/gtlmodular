package com.gtlmodular.client.gui.terminal.print;

/**
 * 哦鲸鲸终端 · v31 印刷档案版 —— 设计常量。
 *
 * <p>全部数值取自 {@code whale-terminal-ui/交付给Java/01_三页元素坐标表.md} 与
 * {@code build_v4.py}（560×340 设计分辨率，1 设计像素 = 1 GUI 像素）。
 */
public final class TerminalTheme {

    private TerminalTheme() {}

    // ── 画布
    public static final int W = 560;
    public static final int H = 340;

    // ── 纸与墨（00 文档第五节）
    public static final int PAPER = 0xFFEFF1EF;
    public static final int PAPER2 = 0xFFE7EAE8;
    public static final int INK = 0xFF1F2427;
    public static final int INK_SOFT = 0xFF55605F;
    public static final int INK_FAINT = 0xFF8B9795;
    public static final int COBALT = 0xFF3A5BC2;
    public static final int RED = 0xFFB43A34;

    /** 主分隔线 rgba(INK,.26) */
    public static final int RULE = 0x421F2427;
    /** 次分隔线 rgba(INK,.13) */
    public static final int RULE_SOFT = 0x211F2427;
    /** 四角裁切 rgba(INK,.5) */
    public static final int CROP = 0x801F2427;
    /** 深色背景（页面外） */
    public static final int BACKDROP = 0xFF15181A;

    // ── CMYK 色票
    public static final int CMYK_C = 0xFF2EA9DF;
    public static final int CMYK_M = 0xFFE4007C;
    public static final int CMYK_Y = 0xFFFFF200;
    public static final int CMYK_K = 0xFF1F2427;

    // ── 舱室 8 色（01 文档 2.3 表）
    public static final int CH_INPUT = 0xFFFF3B30;
    public static final int CH_OUTPUT = 0xFF2F80ED;
    public static final int CH_ENERGY = 0xFFC8A018;
    public static final int CH_MAINTENANCE = 0xFF8A8A8A;
    public static final int CH_PARALLEL = 0xFF7F4FBF;
    public static final int CH_MUFFLER = 0xFFB86A18;
    public static final int CH_DATA = 0xFF1F9BA8;
    public static final int CH_OTHER = 0xFF6B7674;

    /** 面板底（选项页两块面板 #F4F5F3） */
    public static final int PANEL_BG = 0xFFF4F5F3;
    /** 报告页"冲突"类别色 */
    public static final int AMBER = 0xFFC08A2E;

    // ── 字体族
    public enum Fam {
        /** 中文衬线（宋体）：标题、正文中文 */
        CN,
        /** 拉丁衬线正体（Georgia）：Scan Report、`S` 水印 */
        SERIF,
        /** 拉丁衬线斜体（Georgia Italic）：玩家签名、水印 M/T */
        SERIF_IT,
        /** 等宽（Consolas）：全部标签/编号/数值/英文 */
        MONO
    }

    // ── 设计字号阶梯（E.2 实测：15 档）
    public static final float[] SIZES = {5f, 5.5f, 6f, 6.5f, 7f, 7.5f, 8f, 9f, 12f, 13f, 14f, 15f, 16f, 18f, 19f, 23f, 26f};

    // ── 常用字号快捷常量
    public static final float S_MICRO = 5f;
    public static final float S_LABEL = 6f;
    public static final float S_BODY = 6.5f;
    public static final float S_ROW = 7f;
    public static final float S_CARD = 7.5f;
    public static final float S_VALUE = 8f;
    public static final float S_CN_ROW = 9f;
    public static final float S_TITLE_OPTIONS = 18f;
    public static final float S_TITLE_MAIN = 26f;
    public static final float S_BLOCK_TITLE = 23f;

    // ── 三页
    public enum Page {
        MAIN("00", "主界面", "MAIN"),
        OPTIONS("01", "选项页", "OPTIONS"),
        REPORT("02", "报告页", "REPORT");

        public final String num;
        public final String cn;
        public final String en;

        Page(String num, String cn, String en) {
            this.num = num;
            this.cn = cn;
            this.en = en;
        }
    }

    // ── 字体回退顺序里的文件（Windows 本机 = 与设计稿同一套字）
    public static final String[] CN_FONTS = {
            "C:/Windows/Fonts/STSONG.TTF",   // 华文宋体：设计稿浏览器实际回退到的中文衬线
            "C:/Windows/Fonts/simsun.ttc",
    };
    public static final String[] SERIF_FONTS = {
            "C:/Windows/Fonts/georgia.ttf",
            "C:/Windows/Fonts/times.ttf",
    };
    public static final String[] SERIF_IT_FONTS = {
            "C:/Windows/Fonts/georgiai.ttf",
            "C:/Windows/Fonts/timesi.ttf",
    };
    public static final String[] MONO_FONTS = {
            "C:/Windows/Fonts/consola.ttf",
            "C:/Windows/Fonts/cour.ttf",
    };
}
