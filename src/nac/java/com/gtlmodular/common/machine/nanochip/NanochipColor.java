package com.gtlmodular.common.machine.nanochip;

import net.minecraft.world.item.DyeColor;

/** 枚举顺序同时服务染料映射与颜色序号存档，必须保持稳定。 */
public enum NanochipColor {

    WHITE("白色"),
    ORANGE("橙色"),
    MAGENTA("品红色"),
    LIGHT_BLUE("淡蓝色"),
    YELLOW("黄色"),
    LIME("黄绿色"),
    PINK("粉红色"),
    GRAY("灰色"),
    LIGHT_GRAY("淡灰色"),
    CYAN("青色"),
    PURPLE("紫色"),
    BLUE("蓝色"),
    BROWN("棕色"),
    GREEN("绿色"),
    RED("红色"),
    BLACK("黑色");

    private final String nameZh;

    NanochipColor(String nameZh) {
        this.nameZh = nameZh;
    }

    public String nameZh() {
        return nameZh;
    }

    public DyeColor dye() {
        DyeColor[] all = DyeColor.values();
        return all[Math.floorMod(ordinal(), all.length)];
    }

    /** 方块调制色与文字色用途不同，不能共用文字配色而使纹理变暗。 */
    public int paintRgb() {
        return switch (this) {
            case WHITE -> 0xFFFFFF;
            case ORANGE -> 0xFF8000;
            case MAGENTA -> 0xFF00FF;
            case LIGHT_BLUE -> 0x6080FF;
            case YELLOW -> 0xFFFF00;
            case LIME -> 0x80FF80;
            case PINK -> 0xFFC0C0;
            case GRAY -> 0x808080;
            case LIGHT_GRAY -> 0xC0C0C0;
            case CYAN -> 0x00FFFF;
            case PURPLE -> 0x800080;
            case BLUE -> 0x0020FF;
            case BROWN -> 0x604000;
            case GREEN -> 0x00FF00;
            case RED -> 0xFF0000;
            case BLACK -> 0x202020;
        };
    }

    public int paintArgb() {
        return 0xFF000000 | paintRgb();
    }

    public int textColor() {
        return dye().getTextColor();
    }

    public String sprayCanTexture() {
        return "gtceu:textures/item/" + dye().getName() + "_dye_spray_can.png";
    }

    public NanochipColor next() {
        NanochipColor[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static final int INDEX_UNPAINTED = -1;

    public static final String UNPAINTED_NAME_ZH = "未染色";

    /** 负序号代表未染色，不能用 floorMod 将它映射成黑色通道。 */
    public static NanochipColor fromIndex(int index) {
        if (index < 0) {
            return null;
        }
        NanochipColor[] all = values();
        return all[index % all.length];
    }

    public static NanochipColor fromIndexOr(int index, NanochipColor fallback) {
        NanochipColor c = fromIndex(index);
        return c == null ? fallback : c;
    }

    public static String nameZhOfIndex(int index) {
        NanochipColor c = fromIndex(index);
        return c == null ? UNPAINTED_NAME_ZH : c.nameZh();
    }

    public static NanochipColor of(DyeColor dye) {
        if (dye == null) {
            return WHITE;
        }
        return fromIndex(dye.getId());
    }

    public static NanochipColor ofArgb(int argb) {
        int rgb = argb & 0xFFFFFF;
        NanochipColor best = null;
        int bestDist = Integer.MAX_VALUE;
        for (NanochipColor c : values()) {
            int crgb = c.paintRgb();
            int dist = Math.abs(((rgb >> 16) & 0xFF) - ((crgb >> 16) & 0xFF))
                    + Math.abs(((rgb >> 8) & 0xFF) - ((crgb >> 8) & 0xFF))
                    + Math.abs((rgb & 0xFF) - (crgb & 0xFF));
            if (dist == 0) {
                return c;
            }
            if (dist < bestDist) {
                bestDist = dist;
                best = c;
            }
        }
        if (bestDist <= SPRAY_MATCH_TOLERANCE) {
            return best;
        }
        for (DyeColor dye : DyeColor.values()) {
            if ((dye.getTextColor() & 0xFFFFFF) == rgb || (dye.getFireworkColor() & 0xFFFFFF) == rgb) {
                return of(dye);
            }
        }
        return null;
    }

    /** 容许同一染料的不同 RGB 来源，但超出容差时不强行匹配通道。 */
    public static final int SPRAY_MATCH_TOLERANCE = 192;

    public String hexCode() {
        return String.format("#%06X", textColor());
    }
}
