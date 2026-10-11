package com.gtlmodular.client.ctm;

/**
 * <b>连接纹理（CTM）的「邻居 → 变体索引」纯逻辑</b> —— 无世界、无渲染、无注册表访问，
 * 类加载期只装常量（不解析图案、不取注册表、不算格子，见 {@code _census/check_registration_path.js} 的口径）。
 *
 * <h2>为什么需要它</h2>
 * 取证结论（{@code _census/connected_texture_audit.md}）：GTNH 对<b>网格接口机壳</b>挂了
 * {@code method=ctm} 的 <b>47 格</b>连通纹理、对<b>复合体玻璃</b>挂了 {@code method=compact} 的 <b>5 格</b>；
 * 而我们一直画的那两张贴图**就是第 0 格（四周都不连通的孤立变体）**。
 * 但本整合包 **99 个 mod jar 里没有任何 CTM 消费端**（0 命中 {@code mcpatcher} / MCPatcher CTM 类），
 * 所以 {@code mcpatcher/ctm/*.properties} 是死文件 ⇒ 连接纹理只能由本模组自己算。
 *
 * <h2>索引语义的权威来源（三条，互相独立）</h2>
 * <ol>
 *   <li>{@code CTM-1.21-1.2.1+3.jar → assets/ctm/ctm_logic/optifine_full.json}：47 条规则 + 8 个 position 定义；</li>
 *   <li>审计 §5.2：对 GTNH 那 47 张 tile 的<b>逐像素</b>实测（正交签名 → 索引集合）；</li>
 *   <li>本文件的 {@code R_REQUIRED / R_FORBIDDEN} 由 {@code _census/check_ctm_model.js --emit-java}
 *       从 ① 生成，并由同脚本逐位复核（防手抄漂移）。</li>
 * </ol>
 *
 * <h2>位序（bit 0..7）= {@code optifine_full.json} 里 positions 数组的下标</h2>
 * <pre>
 *   0 TOP          4 BOTTOM        —— 正交：up / down
 *   1 TOP_RIGHT    5 BOTTOM_LEFT   —— 对角
 *   2 RIGHT        6 LEFT          —— 正交：right / left
 *   3 BOTTOM_RIGHT 7 TOP_LEFT      —— 对角
 * </pre>
 * position 的「上/右」是<b>面内坐标系</b>：{@code up} 正向 = 贴图 v 减小的方向（= 贴图图像的上边），
 * {@code right} 正向 = 贴图 u 增大的方向（= 贴图图像的右边）。每个面这两个方向在世界里的取值
 * 由 {@link CtmFaceFrame} 从<b>已烘焙 quad 的几何</b>反推（不写死 6 面表，避免约定抄错）。
 *
 * <h2>规则匹配语义</h2>
 * 按索引顺序取<b>首条</b>满足「{@code connected} 全部连通 且 {@code unconnected} 全部不连通」的规则；
 * 未被任一条提到的 position 属「不在乎」。脚本已验证：全部 256 个邻居组合都<b>有且仅有</b>一条规则匹配，
 * 所以「首匹配」不存在歧义。
 */
public final class CtmConnectivity {

    private CtmConnectivity() {
    }

    // ---------- position 位（bit 0..7 = positions 数组下标）----------

    public static final int P_TOP = 1;
    public static final int P_TOP_RIGHT = 1 << 1;
    public static final int P_RIGHT = 1 << 2;
    public static final int P_BOTTOM_RIGHT = 1 << 3;
    public static final int P_BOTTOM = 1 << 4;
    public static final int P_BOTTOM_LEFT = 1 << 5;
    public static final int P_LEFT = 1 << 6;
    public static final int P_TOP_LEFT = 1 << 7;

    /** 四个正交位（TOP|RIGHT|BOTTOM|LEFT）= 0x55。 */
    public static final int ORTHO = P_TOP | P_RIGHT | P_BOTTOM | P_LEFT;

    /**
     * 每个 position 在面内坐标系里的步进（下标 = position 位序）。
     * {@code STEP_RIGHT} 沿「贴图右边」，{@code STEP_UP} 沿「贴图上边」；合成即世界偏移。
     */
    public static final int[] STEP_RIGHT = { 0, +1, +1, +1, 0, -1, -1, -1 };
    public static final int[] STEP_UP = { +1, +1, 0, -1, -1, -1, 0, +1 };

    /** 机壳（{@code method=ctm}）的变体张数。 */
    public static final int CTM_TILES = 47;
    /** 玻璃（{@code method=compact}）的变体张数。 */
    public static final int COMPACT_TILES = 5;

    // ---------- 47 条规则（由 _census/check_ctm_model.js --emit-java 生成，勿手改）----------

    private static final int[] R_REQUIRED = {
            0x00, 0x04, 0x44, 0x40, 0x14, 0x50, 0x15, 0x54, 0x57, 0x5D, 0xF5, 0xD7,
            0x10, 0x1C, 0x7C, 0x70, 0x05, 0x41, 0x45, 0x51, 0xD5, 0x75, 0x7D, 0x5F,
            0x11, 0x1F, 0xFF, 0xF1, 0x1D, 0x74, 0x17, 0x5C, 0xF7, 0xDF, 0x77, 0xDD,
            0x01, 0x07, 0xC7, 0xC1, 0x47, 0xD1, 0xC5, 0x71, 0xFD, 0x7F, 0x55,
    };

    private static final int[] R_FORBIDDEN = {
            0x55, 0x51, 0x11, 0x15, 0x49, 0x25, 0x4A, 0x29, 0xA8, 0xA2, 0x0A, 0x28,
            0x45, 0x41, 0x01, 0x05, 0x52, 0x94, 0x92, 0xA4, 0x2A, 0x8A, 0x82, 0xA0,
            0x44, 0x40, 0x00, 0x04, 0x42, 0x09, 0x48, 0x21, 0x08, 0x20, 0x88, 0x22,
            0x54, 0x50, 0x10, 0x14, 0x90, 0x24, 0x12, 0x84, 0x02, 0x80, 0xAA,
    };

    /**
     * 由 8 位邻居掩码取机壳变体索引 0..46（首匹配；取不到时回退 0 = 孤立变体 = 现状外观）。
     *
     * @param mask 位序同 {@link #P_TOP} 等常量，只取低 8 位
     */
    public static int ctmIndex(int mask) {
        int m = mask & 0xFF;
        for (int i = 0; i < CTM_TILES; i++) {
            if ((m & R_REQUIRED[i]) == R_REQUIRED[i] && (m & R_FORBIDDEN[i]) == 0) {
                return i;
            }
        }
        return 0;
    }

    /**
     * 由 8 位掩码取玻璃（{@code compact} 5 格）变体索引。
     *
     * <h3>★ 2026-09-16 实机修正（真 bug）：2 与 3 曾经写反，导致线正好画在接缝上</h3>
     * 这 5 张 tile 的**实际像素**（逐张解码 16×16 PNG 得到，不是抄文字）：
     * <pre>
     *   tile 0：四边都有白线（2px）      ⇒ 孤立窗格
     *   tile 1：完全没有线（alpha 150 均质）⇒ 四边全连通
     *   tile 2：只有**左/右**两列有线   ⇒ 上下开口 ⇒ 用于「上下连通」
     *   tile 3：只有**上/下**两行有线   ⇒ 左右开口 ⇒ 用于「左右连通」
     *   tile 4：只有四角各 2×2 有线     ⇒ 角部/其它
     * </pre>
     * 判据一句话：<b>哪边连通，那张 tile 就必须在那边"无线"</b>。
     * 第一版照抄了审计文档 §5.3 的文字（写成「2=水平长条、3=竖直长条」）⇒ 选格**恰好相反**：
     * 左右相邻的玻璃会拿到 tile 2（左右有线）——线正好落在两块玻璃的接缝上 ⇒ 玩家看到"每格还是独立窗格/接缝"✗。
     * 审计文档的**原始数值**（tile 2 的 T/R/B/L = 0/236.8/0/236.8，即线在 R/L）与本解码一致，
     * 错的是那两行**文字标签**；现已同时改正代码、审计文字、以及 mcpatcher properties 里的注释，
     * 并在 `_census/check_ctm_model.js` 里加了**从 PNG 像素现算**的断言（不再依赖文字）。
     *
     * <p><b>只看 4 个正交邻居</b>，对角不参与。
     *
     * <p>⚠️ <b>2026-09-16 起本方法已不再是模型的选择逻辑</b>：NH（Angelica
     * {@code CompactCtmQuadProcessor}）的 compact 是「整面 + 四象限」两级，只有
     * {@code mask==0}/{@code mask==0xFF} 才整面（分别用第 0/1 张），其余一律切 4 个子块
     * —— 见 {@link #compactWholeSprite(int)} 与 {@link #compactQuadrantSprite(int, int, int)}
     * （规格 `_census/compact_quadrant_spec.md` §1.2/§2/§3）。本方法现在只剩两个用途：
     * <ol>
     *   <li><b>兜底</b>：某面切不出子块模板时（非平面/非 16 格 UV 的怪异几何），
     *       {@code ConnectedCtmBakedModel} 用它退回"整面选格"，保证**绝不少画 quad**；</li>
     *   <li>旧的像素自检（`_census/check_ctm_model.js` §⑩）回归用。</li>
     * </ol>
     */
    public static int compactIndex(int mask) {
        int o = mask & ORTHO;
        if (o == ORTHO) {
            return 1; // 四边全连通：那张完全没有线
        }
        if (o == (P_TOP | P_BOTTOM)) {
            return 2; // 上下连通 ⇒ 要「左右有线、上下无线」那张（= tile 2）
        }
        if (o == (P_LEFT | P_RIGHT)) {
            return 3; // 左右连通 ⇒ 要「上下有线、左右无线」那张（= tile 3）
        }
        if (o == 0) {
            return 0; // 孤立：四边都封
        }
        return 4; // 角部/其它（1 或 3 个正交邻居）
    }

    /**
     * <b>compact 的第 1 级：整面该用第几张 sprite</b>（只有 {@code mask==0} / {@code mask==0xFF} 走这里）。
     *
     * <p>逐条对应反编译的 {@code CompactCtmQuadProcessor.getCompactSpriteIndex}（javap 偏移 0-8）：
     * <pre>
     *   if (mask == 0xFF) return 1;   // SPRITE_ALL（四边 + 四角全连通）
     *   return 0;                     // SPRITE_DEFAULT（孤立）
     * </pre>
     * 与之配套的是 {@code processFace} 的分发（偏移 64-107）：<b>只有</b> {@code mask==0} 或
     * {@code mask==0xFF} 才整面渲染，其余一律 {@code renderSplitFace}（四象限）——
     * 所以「1/3 条正交连通整面取第 4 张」这种写法在 NH 里**不存在**
     * （规格 §1.2 的关键结论、§7.5 的观感差异根因）。
     *
     * @param mask 8 位邻居掩码（位序同 {@link #P_TOP} 等常量）
     * @return 第 0 张或第 1 张；调用方只应在 {@code mask} 为 {@code 0x00}/{@code 0xFF} 时调用
     */
    public static int compactWholeSprite(int mask) {
        return (mask & 0xFF) == 0xFF ? 1 : 0;
    }

    /**
     * <b>compact 的第 2 级：子象限 {@code (hu,hv)} 该用第几张 sprite</b> —— 朝向无关写法。
     *
     * <p>逐条对应 {@code getQuadrantSprite(q, mask)}（javap 偏移 0-115）：
     * <pre>
     *   两条边都连通 ⇒ 夹角对角也连通 ? 1(ALL) : 4(MIXED)
     *   只有 u 轴那条边连通 ⇒ 3(VERTICAL)
     *   只有 v 轴那条边连通 ⇒ 2(HORIZONTAL)
     *   两条都不通 ⇒ 0(DEFAULT)
     * </pre>
     * <b>位序映射（只依赖集合层级，不含符号；规格 §6.2 的"同名不同义"警告）</b>：
     * <pre>
     *   我们的 {P_LEFT, P_RIGHT}  = NH 的 {dir0, dir2} = 贴图 u 轴那对边 = 字节码的 bit0/bit4
     *   我们的 {P_TOP,  P_BOTTOM} = NH 的 {dir1, dir3} = 贴图 v 轴那对边 = 字节码的 bit2/bit6
     *   对角位一一对应（P_TOP_LEFT 等就是"两条边的夹角"，与 {@code QUAD_DIAG} 同义）
     * </pre>
     * ⇒ 因此**不能**把 NH 的 {@code 17 / 68 / QUAD_A / QUAD_B} 直接套到我们的掩码上
     * （同一个整数在两套命名下含义不同）；本写法绕开了这个问题，也与面朝向的正负号无关。
     *
     * @param mask 8 位邻居掩码（位序同 {@link #P_TOP} 等常量）
     * @param hu   该子块在 u 轴的半区：{@code 0} = u 小半区（贴图左半，靠 {@code P_LEFT} 边）、{@code 1} = 右半
     * @param hv   该子块在 v 轴的半区：{@code 0} = v 小半区（贴图上半，靠 {@code P_TOP} 边）、{@code 1} = 下半
     * @return sprite 下标 0..4（同一组 5 张里的第几张）
     */
    public static int compactQuadrantSprite(int mask, int hu, int hv) {
        int uBit = (hu == 0) ? P_LEFT : P_RIGHT;
        int vBit = (hv == 0) ? P_TOP : P_BOTTOM;
        int dBit = (hu == 0) ? (hv == 0 ? P_TOP_LEFT : P_BOTTOM_LEFT)
                : (hv == 0 ? P_TOP_RIGHT : P_BOTTOM_RIGHT);
        boolean nu = (mask & uBit) != 0;
        boolean nv = (mask & vBit) != 0;
        if (nu && nv) {
            // 对角位只在"两条边都连通"时才被读（规格 §3.3 最后一行）
            return (mask & dBit) != 0 ? 1 : 4;
        }
        if (nu) {
            return 3;
        }
        if (nv) {
            return 2;
        }
        return 0;
    }

    /**
     * 「只靠 4 个正交邻居就已确定的索引」——用于省掉 4 次对角查询。
     *
     * <p>返回 {@code >= 0} 表示无需查对角；{@code -1} 表示必须补查对角。
     * 返回非负时，其值与其他谓词无关（脚本已断言这 7 个正交签名在全部 256 组合下都是单值）。
     *
     * <p>注意：本方法只用于<b>剪枝</b>，连接位图仍然照实写 8 位（对角位留 0），
     * 因为 {@link #ctmIndex(int)} 对这些签名本来就是单值。
     *
     * <p>⚠️ <b>只对 47 格（{@code method=ctm}）成立</b>：compact 的象限选格会读对角位
     * （{@link #compactQuadrantSprite}），用它剪枝会永久丢掉第 4 张 —— 调用方
     * {@code ConnectedCtmBakedModel.computeMask} 已按 {@code !compact} 收窄。
     */
    public static int singletonIndex(int orthoMask) {
        int o = orthoMask & ORTHO;
        if (o == 0) {
            return 0;
        }
        if (o == (P_LEFT | P_RIGHT)) {
            return 2;
        }
        if (o == (P_TOP | P_BOTTOM)) {
            return 24;
        }
        if (o == P_RIGHT) {
            return 1;
        }
        if (o == P_LEFT) {
            return 3;
        }
        if (o == P_BOTTOM) {
            return 12;
        }
        if (o == P_TOP) {
            return 36;
        }
        return -1;
    }
}
