package com.gtlmodular.common.machine.nanochip;

import net.minecraft.world.item.DyeColor;

/**
 * 真空传送带的 16 种颜色（原版 GTNH NAC 用 16 色染料网络做「线路」）。
 *
 * <p>顺序与 {@link DyeColor} 的 {@code getId()} 完全一致，因此 {@code values()[color.getId()]}
 * 就是标准映射；界面里直接用 {@link #textColor()} 上色。
 *
 * <p>原版规则（GT5U {@code GT5U.tooltip.nac.hatch.vc.base.*}）：
 * <pre>
 * Vacuum Conveyor Connections are 1 to 1
 * Outputs from recipes with inputs from a colored Vacuum Conveyor Input
 * will be placed in a Vacuum Conveyor Output of the corresponding color
 * </pre>
 * 即：<b>同色的输出 → 同色的输入，一对一</b>。移植版把「管子」省掉，颜色本身就是线。
 *
 * <h2>★ 2026-09-17 第六轮：引入"未染色"（= 原版 {@code colorization == -1}）</h2>
 * <p><b>NH 是唯一标准，以下是逐条字节码取证</b>：
 * <pre>
 * // GT5U BaseMetaTileEntity.getColorization()（也见 BaseMetaPipeEntity.getColorization()）
 * 0: aload_0
 * 1: invokevirtual getColorRaw:()B
 * 4: iconst_1
 * 5: isub                       // colorization = colorRaw - 1
 * 6: i2b
 * 7: ireturn
 * // ⇒ colorRaw 的默认值 0 ⇒ getColorization() == -1 == "未染色"（喷漆罐没碰过的默认态）
 *
 * // GT5U MTEVacuumConveyorPipe.checkConnections()
 * 23: aload_1
 * 24: invokeinterface IGregTechTileEntity.getColorization:()B
 * 29: iconst_m1
 * 30: if_icmpne 34            // colorization != -1 才继续
 * 33: return                  // colorization == -1 ⇒ mConnections = 0 ⇒ 不建任何连接
 * </pre>
 * <p>所以"未染色"不是"白色"，而是<b>第 17 种状态</b>：它既不与白色互通，也不与任何东西互通。
 * 移植版用 {@link #INDEX_UNPAINTED} = {@code -1} 表示它（与 NH 的 -1 同值），
 * {@link #fromIndex(int)} 对负数返回 {@code null}（"没有颜色"），由调用方各自处置。
 *
 * <p>⚠️ <b>不要</b>把 {@code -1} 折进 {@code floorMod} 里（那会得到 BLACK）—— 见
 * {@link #fromIndex(int)} 的实现与注释：老代码正是这么写的，于是"未染色"被当成了黑色。
 */
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

    /** 染料枚举（用于贴图/颜色值）。 */
    public DyeColor dye() {
        DyeColor[] all = DyeColor.values();
        return all[Math.floorMod(ordinal(), all.length)];
    }

    /**
     * 染方块用的 RGB —— **照抄 GT5U 的 {@code gregtech.api.enums.Dyes} 原版值**（已从 GTNH 包解出常量）。
     *
     * <p>为什么不能用 {@link #textColor()}：MC 的 {@code DyeColor.getTextColor()} 是**给文字用的偏暗色**
     * （绿色 = {@code 0x5E7C16} 暗橄榄），拿它乘白色机壳会又暗又平、看不出机壳花纹，
     * 玩家反馈"整块均匀染色，不像 GTNH 那种只是染上去"。GT5U 用的是明亮的经典染料值
     * （绿 {@code 0x00FF00}、青 {@code 0x00FFFF}、橙 {@code 0xFF8000}…），乘完仍保留明暗对比。
     *
     * <p>顺序与 {@link DyeColor} / 本枚举一致（16 色）。
     */
    public int paintRgb() {
        // 【2026-09-14 深夜 · 逐个核对字节码后的更正】
        // 以前有三个值是我"按印象填的"，与原版不一致；现在全部改成 gt5u Dyes 里的 int 常量原值
        // （取证：javap -c gregtech/api/enums/Dyes.class，枚举构造器里紧挨着显示名的那个 int）：
        //   BLUE  = 8447   = 0x0020FF   （旧值 0x0000FF 错）
        //   BROWN = 6307840 = 0x604000  （旧值 0x603000 错）
        //   LIME  = 8454016 = 0x80FF80  （旧值 0x80FF00 错）
        // 其余 13 色核对一致；BLACK = 0x202020 也是原版值（不是纯黑）。
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

    /** 给方块染色用的 ARGB（不透明）。 */
    public int paintArgb() {
        return 0xFF000000 | paintRgb();
    }

    /** 文字颜色（0xRRGGBB）。 */
    public int textColor() {
        return dye().getTextColor();
    }

    /**
     * 本颜色对应的<b>喷漆罐图标</b>（GTCEu 自带 16 色 {@code *_dye_spray_can.png}）。
     *
     * <p>原版 NAC 的端口/管道就是靠喷漆罐染色的（GT5U {@code gt.behaviour.paintspray.*}、
     * 任务书「端口和管道均需染色，可使用无限喷漆罐」），所以配置页标签直接用喷漆罐当图标，
     * 换色时把 {@code ResourceTexture.imageLocation} 一改，图标就跟着变色。
     * 16 个 {@link DyeColor} 的 {@code getName()} 与 GTCEu 贴图命名一一对应。
     */
    public String sprayCanTexture() {
        return "gtceu:textures/item/" + dye().getName() + "_dye_spray_can.png";
    }

    /** 循环到下一个颜色（界面按钮用）。 */
    public NanochipColor next() {
        NanochipColor[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /**
     * <b>未染色</b>的序号 —— <b>与 NH 的 {@code colorization == -1} 同值</b>（见类注释的取证）。
     *
     * <p>所有"颜色字段"（{@code NanochipPipeMachine.colorIndex} /
     * {@code NanochipVacuumHatchMachine.colorIndex}）都用它表示"没被染过色"，
     * 并且<b>以它为默认值</b>（NH 的 {@code colorRaw} 默认 0 ⇒ {@code getColorization() == -1}）。
     */
    public static final int INDEX_UNPAINTED = -1;

    /** "未染色"的中文名（界面/提示用）。 */
    public static final String UNPAINTED_NAME_ZH = "未染色";

    /**
     * 由序号取色；<b>负数一律返回 {@code null}</b>（= 未染色，"没有颜色"）。
     *
     * <p>⚠️ 负数<b>不能</b>走 {@code Math.floorMod}：{@code floorMod(-1, 16) == 15 == BLACK}，
     * 于是"未染色"会被悄悄当成黑色 —— 那是一个真实存在过的缺陷来源。
     * 越界（≥ 16）仍按取模处理（读档容错），只有负数表示"没有颜色"。
     *
     * @return 该序号的颜色；{@code index < 0} 时返回 {@code null}
     */
    public static NanochipColor fromIndex(int index) {
        if (index < 0) {
            return null;
        }
        NanochipColor[] all = values();
        return all[index % all.length];
    }

    /** 由序号取色，负数/越界时兜底成 {@code fallback}（给"不接受 null"的存档读入用）。 */
    public static NanochipColor fromIndexOr(int index, NanochipColor fallback) {
        NanochipColor c = fromIndex(index);
        return c == null ? fallback : c;
    }

    /** 序号 → 显示名（未染色时用 {@link #UNPAINTED_NAME_ZH}）。 */
    public static String nameZhOfIndex(int index) {
        NanochipColor c = fromIndex(index);
        return c == null ? UNPAINTED_NAME_ZH : c.nameZh();
    }

    /** 由染料取色。 */
    public static NanochipColor of(DyeColor dye) {
        if (dye == null) {
            return WHITE;
        }
        return fromIndex(dye.getId());
    }

    /**
     * 把外部传进来的 ARGB/RGB <b>映射到最接近的一种通道颜色</b>；拿不准就返回 {@code null}
     * （"宁可不染，也不要乱染"）。
     *
     * <p>用于"GT 喷漆罐 / {@code IPaintable#setPaintingColor} 想把颜色灌进来"的场景。
     * 匹配顺序（照 2026-09-15 的需求实现）：
     * <ol>
     *   <li><b>完全相等优先</b>：某色的 {@link #paintRgb()} 与传入值逐位相同 ⇒ 直接返回它；</li>
     *   <li>否则取<b>各通道差值之和最小</b>的那个，且必须 ≤ {@link #SPRAY_MATCH_TOLERANCE}；</li>
     *   <li>还不够就拿"这是不是某个原版染料的颜色"兜一次（GTCEu / 原版工具常传
     *       {@link DyeColor#getTextColor()} 或 {@code getFireworkColor()}，那两个值与 GT5U 的
     *       明亮染料值差得很远，比如绿：原版文字色 {@code 0x5E7C16} vs 我们的 {@code 0x00FF00}），
     *       命中就按那个染料取色。</li>
     * </ol>
     * 都匹配不上 ⇒ {@code null}。
     *
     * @param argb 传进来的颜色，高 8 位（alpha）忽略
     */
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
                return c;                       // ① 完全相等
            }
            if (dist < bestDist) {
                bestDist = dist;
                best = c;
            }
        }
        if (bestDist <= SPRAY_MATCH_TOLERANCE) {
            return best;                        // ② 最近的，且在容差内
        }
        for (DyeColor dye : DyeColor.values()) {  // ③ 原版染料色兜底
            if ((dye.getTextColor() & 0xFFFFFF) == rgb || (dye.getFireworkColor() & 0xFFFFFF) == rgb) {
                return of(dye);
            }
        }
        return null;                            // ④ 认不出来 ⇒ 不染
    }

    /**
     * 颜色匹配容差（三个通道差值之和，最大 765）。192 ≈ 平均每通道 64 ——
     * 够容纳"同一个染料的不同来源值"（原版染料色与 GT5U 染料值之间），又不至于把随便什么颜色
     * 都硬塞进 16 色里。超过就 {@code null}。
     */
    public static final int SPRAY_MATCH_TOLERANCE = 192;

    /** 界面涂色用的 {@code §x} 风格颜色码（十六进制文本组件）。 */
    public String hexCode() {
        return String.format("#%06X", textColor());
    }
}
