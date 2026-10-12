package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * <b>「纳米校准」面板 —— 主机与仓室共用的同一份渲染</b>（2026-09-21 抽出来，0.1.24）。
 *
 * <h3>为什么要抽出来</h3>
 * 这段面板（总历史条 + 族的占比条 + 判定分母行）原本<b>只</b>写在
 * {@code NanochipVacuumHatchMachine.CalibrationConfigurator} 里，挂在前真空传送仓的 fancy 配置页上。
 * 队长 2026-09-21 的要求是「<b>这个纳米校准，我希望能在主机那边看到</b>」——
 * 主机自己也要挂同一张页。两处各写一份 ⇒ 早晚长歪（字体/配色/行距/族顺序任何一处改漏都看不出来），
 * 所以这里抽成<b>唯一一份</b>实现，两边都用它。
 *
 * <h3>为什么用 Supplier 而不是直接传机器</h3>
 * 主机与仓室的取数口径<b>故意不同</b>（都必须走**同步字段**，因为 fancy 配置页在客户端也要画）：
 * <ul>
 *   <li>仓室：{@code hostComplex()} 拿到主机 ≠ null 时读主机的 {@code synced*}，否则退回本机配置；</li>
 *   <li>主机：直接读自己的 {@code synced*}。</li>
 * </ul>
 * ⇒ 本类<b>不认识</b>任何机器类型，只吃取值回调。这样两边的"取数口径"差异留在各自那一行，
 * 版面与配色却是同一份代码。
 *
 * <h3>版面预算（保持原样，一个字没改）</h3>
 * 面板宽 152：族名 ≤ 6 个汉字 = 54px（占 6..60）→ 档位格 62..105 → 状态列 110..152；
 * 族行行距 10px；分母模式单独占一行（y=33）—— 面板是<b>向上展开</b>的，越矮顶边越低。
 * （取自 {@code NanochipVacuumHatchMachine.CalibrationConfigurator} 的原有注释，未改口径。）
 *
 * <h3>★★ 0.3.133【纳米校准 UI 重设计】★★</h3>
 * 队长原话：「让一个子代理在后面<b>重新跑这个 UI 设计</b>，昨天我已经让你学过风格了」。
 * 风格依据 = ① 本工程 {@code COL_*} 色板 + {@code 3/6} 节奏（{@code _census/UI设计规范.md}、
 * {@code _census/UI设计规范_v2.md}）② AE2 的机器可读语义规范（{@code assets/ae2/screens/schema.json}）
 * ③ <b>「数据能源」的 31 个 AE2 界面 json</b>（{@code _census/_dataenergistics/assets/ae2/screens/}，
 * 本轮定位到的第一参照）。完整设计见 {@code _census/纳米校准UI重设计.md}。
 *
 * <p>这一版解决队长逐字列出的四条不满：
 * <ol>
 *   <li><b>看不出还差多少、看不到档位边界</b> ⇒ 族条拆成 <b>3 格</b>，一格一档：
 *       第 k 格填满 ⇔ 已达第 k 档；当前档那一格按"本档内进度"部分填充 ⇒ 空白宽度就是"离下一档的距离"；</li>
 *   <li><b>{@code 0.0％} 那一列没意义</b> ⇒ 整列换成 <b>状态列</b>（{@code 仅历史 / 未达 / 第 N 档 / 生效 N 档}），
 *       精确占比移到该行悬停；</li>
 *   <li><b>「本档功效」那一行太长、折行难读</b> ⇒ 拆成 <b>短标签 + 悬停长文</b>：
 *       可见行只有 {@code EU×0.8/时长×0.8} 这种"标签×倍率"（用 {@code /} 分隔，照数据能源
 *       {@code channel_overview} 的写法），完整说明走既有键 {@code …calibration.effects.tip}；</li>
 *   <li><b>信息密度低、观感朴素</b> ⇒ 加 <b>一级分组底板</b>（面 + 2px 竖条，v1 模板）+ <b>二级内嵌块</b>
 *       （{@code COL_PANEL_INNER}，v2 §3②）+ <b>文字 {@code §} 分层</b>（列头/标签弱、值与档位 {@code §b§l}）
 *       + 按钮换成 GT 标准按钮（居中实时文字）。</li>
 * </ol>
 *
 * <h4>回退（一行）</h4>
 * {@link #NEW_LAYOUT} 改 {@code false} ⇒ 走 {@link #legacySection}，那是<b>改动前的旧版面逐字副本</b>
 * （含它自己的客户端档位复算），行为与外观与改动前完全一致 ✓
 *
 * <h4>纪律</h4>
 * <ul>
 *   <li>只走 {@code Supplier}/{@code IntFunction} <b>同步字段</b>取数；本类**不认识**机器类型；</li>
 *   <li>★ <b>客户端绝不自己复算判定</b>：每族档位来自服务端同步的 {@code syncedFamilyTiers}
 *       （见 {@code NanochipAssemblyComplexMachine#syncedTierOf}）。旧版面在客户端重写了 {@code tierOf}
 *       的算式，本轮已在新版面里删除（只在 {@code legacySection} 里留着）；</li>
 *   <li>每帧重建 ⇒ 新增读数必须 O(1)（`split(",")` 出 9 段，与既有 {@code syncedCountOf} 同阶）；</li>
 *   <li>颜色只走两条路：{@code ColorRectTexture} 画块/条 + {@code §} 色码写文字；零新贴图 ✓</li>
 *   <li>「重置校准」的<b>两步确认</b>语义逐字保留（第一次=预备态、第二次才清、预备态在客户端静态字段）。</li>
 * </ul>
 */
public final class NanochipCalibrationPanel {

    private NanochipCalibrationPanel() {}

    /**
     * ★ 新版面开关（0.3.133）。{@code false} ⇒ 逐字回到改动前的旧版面（{@link #legacySection}）。
     *
     * <p>留着它是因为：新版面把面板从 176 高推到 223 高，队长万一觉得不合适，
     * <b>改这一个字</b>就能立刻回到旧观感，不必回滚整个提交 ✓
     */
    public static final boolean NEW_LAYOUT = true;

    /** 配置页图标（与仓室那张页保持一致，队长认脸）。 */
    private static final String ICON = "gtceu:textures/item/portable_debug_scanner.png";

    // ==========================================================================================
    // ★★ 0.3.133【重设计】版式常量 —— 全部由节奏常量推导，禁止在方法体里手写 y（v1 §2 硬规则）
    //
    //   x 栅格（总预算 = 152）：
    //     0..2    族板 2px 竖条（COL_ACCENT）        ← v1 §2 例外表「竖条宽 2px」
    //     2..4    ★ 生效行的额外 2px 强调条（叠成 4px）
    //     6       板内文字轨（BAND_PAD_X 4 + 竖条 2） ← v1 §2「文字再进 2px 避开 2px 竖条」
    //     6..60   族名（≤ 6 汉字 = 54px）             ← 既有硬约束
    //     62..105 ★ 档位格（3 × 13 + 2 × 2 = 43）
    //     110..152 ★ 状态列（42）
    //     4..148  顶部读块（既有轨）／功效内嵌块（文字再进 2px ⇒ 6）
    // ==========================================================================================

    /** 面板宽度（预算见类注释）。 */
    private static final int WIDTH = 152;

    /** 一行文字高（v1 §2）。★ 也是本页「族行行距 10px」这条既有硬约束的来源。 */
    private static final int LABEL_H = 10;
    /** 板内相邻行间距（v1 §2）。 */
    private static final int ROW_GAP = 3;
    /** 板↔板 / 块↔块间距（v1 §2）。 */
    private static final int SECTION_GAP = 6;
    /** 底板内上下内边距（v1 §2）。 */
    private static final int PAD_Y = 3;
    /** 分组竖条宽（v1 §2 明示例外）。 */
    private static final int STRIPE_W = 2;
    /** 板内左内边距（v1 §2）。 */
    private static final int BAND_PAD_X = 4;
    /** 板内文字轨 = 内边距 + 避开竖条（v1 §2）。 */
    private static final int BAND_TEXT_X = BAND_PAD_X + STRIPE_W;
    /** 族名列宽（6 个汉字 × 9px）。 */
    private static final int FAMILY_COL_W = 54;
    /** 族名与档位格之间的留白。 */
    private static final int FAMILY_GAP_X = 2;
    /** 档位格起点。 */
    private static final int SEG_X = BAND_TEXT_X + FAMILY_COL_W + FAMILY_GAP_X;
    /** 单格宽（3 格可读下限：13px 能看清"满/空/半"）。 */
    private static final int SEG_W = 13;
    /** 格间留白（与竖条同值 2px ⇒ 视觉上就是"3 格"）。 */
    private static final int SEG_GAP = 2;
    /** 格高（沿用既有族条高 7px）。 */
    private static final int SEG_H = 7;
    /** 格步距。 */
    private static final int SEG_STRIDE = SEG_W + SEG_GAP;
    /** 3 格总宽（基础电路那根"纯历史条"用同样的宽度，保持列对齐 ⇒ v2 §2「同类控件同列」）。 */
    private static final int SEG_SPAN = 3 * SEG_W + 2 * SEG_GAP;
    /** 状态列起点。 */
    private static final int STATE_X = SEG_X + SEG_SPAN + 5;

    // ---- 顶部读块（★ 坐标与旧版面逐字相同，队长没提意见的地方不动） ----
    private static final int TITLE_Y = 2;
    private static final int BAR_X = 4;
    private static final int BAR_Y = 14;
    private static final int BAR_W = 144;
    private static final int BAR_H = 8;
    private static final int HIST_Y = 23;
    private static final int DEN_Y = 33;

    // ---- 族板（一级分组底板） ----
    /** 板顶 = 分母行底 + 板间距。 */
    private static final int BAND_Y = DEN_Y + LABEL_H + SECTION_GAP;
    /** 板标题（= 列头）y。 */
    private static final int BAND_TITLE_Y = BAND_Y + PAD_Y;
    /** 第 1 行族的 y（标题 → 首行 = ROW_GAP，v2 §2 例外表）。 */
    private static final int FAMILY_ROW0_Y = BAND_TITLE_Y + LABEL_H + ROW_GAP;
    /** 族行行距。★ = LABEL_H（既有"行距 10px"硬约束；v1 的 ROW_GAP 在这里让位给 3 格的视觉分隔）。 */
    private static final int FAMILY_PITCH = LABEL_H;

    // ---- 功效内嵌块（二级内嵌面） ----
    private static final int EFF_X = 4;
    private static final int EFF_W = WIDTH - 2 * EFF_X;
    /** 内嵌块内文字轨（与族名同轨 ⇒ 全页只有 x=4（顶部）与 x=6（板内）两条文字轨）。 */
    private static final int EFF_TEXT_X = EFF_X + 2;
    /** 内嵌块行数 = 当前生效 / 本档功效 / 下一档。 */
    private static final int EFF_ROWS = 3;

    // ---- 重置按钮 ----
    private static final int BTN_X = 4;
    private static final int BTN_W = WIDTH - 2 * BTN_X;
    /** 按钮高（v1 §2 ROW_H）。 */
    private static final int BTN_H = 18;
    /** 预备态危险标记的高度。 */
    private static final int ARMED_MARK_H = 2;

    // ---- 颜色（全部沿用 v1/v2 既有记号，零新增色号） ----
    /** 一级分组底板（v1 §1 / v2 §1.2）。 */
    private static final int COL_PANEL = 0x33000000;
    /** 二级内嵌块 + 档位格底（v2 §1.2）。 */
    private static final int COL_PANEL_INNER = 0x26000000;
    /** 分组竖条 / 生效强调（v1 §1 / v2 §1.2）。 */
    private static final int COL_ACCENT = 0xCC66CCFF;
    /** 危险动作标记（v1 §1「危险按钮底」，先例 NanochipModuleMachine:2311）。 */
    private static final int COL_DANGER = 0x40FF5555;
    /** 透明（用于"不该画"的那一半 ProgressTexture，例如生效行强调条的空态）。 */
    private static final int COL_NONE = 0x00000000;

    // ---- ★ 内联短标签（待补语言键；见 _census/纳米校准UI重设计.md §5.2 的 L-01…L-13） ----
    //
    //   为什么内联：任务书明令「其他文件一律不许动」⇒ 不能改 lang/*.json。
    //   本类**本来就有**内联中文字面量的先例（旧版面的 "历史 %d / %d 个电路"），
    //   且族名 Family.nameZh() 本身就是硬编码在枚举里的中文 ⇒ 不是新引入的做法 ✓
    //   拿到 lang 授权后，把这些常量逐字换成 Component.translatable(…) 即可。
    /** L-01 族板标题 / 列头。 */
    private static final String LIT_BAND_TITLE = "§8各族校准（3 格 ＝ 第 1/2/3 档）";
    /** L-04 可校准但 0 档。 */
    private static final String LIT_NOT_YET = "未达";
    /** L-05 基础电路：只进历史条。 */
    private static final String LIT_HISTORY_ONLY = "仅历史";
    /** 生效标记词。 */
    private static final String LIT_ACTIVE = "生效";
    /** L-02 档位前缀 / 后缀。 */
    private static final String LIT_TIER_PRE = "第 ";
    private static final String LIT_TIER_POST = " 档";
    /** L-09 「还差 N 个到第 X 档」。 */
    private static final String LIT_GAP = "还差 ";
    private static final String LIT_GAP_MID = " 个到 ";
    /** L-07 / L-06 / L-08 功效短标签。 */
    private static final String LIT_EU = "EU";
    private static final String LIT_TIME = "时长";
    private static final String LIT_T3 = "特效";
    private static final String LIT_T3_ON = "已生效";
    /** L-10 空态。 */
    private static final String LIT_NO_TIER1 = "§8还没有任何族达到第 1 档";
    /** L-11 行悬停第 1 行。 */
    private static final String LIT_SHARE = "§7占比 §b";
    /** 无生效族时给既有键 {@code …calibration.active} 的短实参。 */
    private static final String LIT_NONE = "§8无";
    /**
     * ★ 0.3.132【防复发】「每族现有数量」读不到真值时的<b>哨兵</b>：{@code -1} = <b>未知</b>。
     *
     * <p>它必须与「真的 0 个」<b>可区分</b> —— 照 B22 对档位的那条纪律办：宁可显示"未知"，
     * 也绝不吐一个看着像真值的 0（见 {@link #section(Supplier, Supplier, IntFunction, Supplier)} 的说明）。
     */
    private static final long UNKNOWN_FAMILY_COUNT = -1L;

    /**
     * 画「纳米校准」区（总历史条 + <b>8</b> 个族 + 判定分母行）。
     *
     * <p>★ 2026-09-21 队长令「第5个是合并成一条」：原「超时空」与「特殊」并成一条
     * <b>「超因果电路」</b> ⇒ 条上从 9 段变 <b>8 段</b>（基础 / 晶体 / 湿件 / 生物 / 光学 /
     * 奇异 / 寰宇 / 超因果）。这里必须走 {@link NanochipCalibration.Family#barFamilies()}，
     * <b>不能</b>再用 {@code Family.values()}（后者仍含只保枚举序号的隐藏列 {@code SPECIAL}）。
     * 取数仍按 {@code family.ordinal()} —— 与主机 {@code syncedFamilyCounts} 的下标口径一致。
     *
     * <p>★★ 0.3.132【防复发 · 与 B22 同一条纪律】★★ 这条 4 参兜底<b>读不到</b>三样真值
     * （「生效族 / 判定分母 / 每族数量」—— 它们全在主机上），所以一律给<b>可区分的哨兵</b>，
     * <b>绝不</b>再写 {@code 1.0D / 0L} 去冒充真值（旧写法正是真空仓页「族数量全 0」那条实机 bug 的来源，
     * 见 {@code NanochipVacuumHatchMachine.CalibrationConfigurator#calibrationSection}）：
     * <ul>
     *   <li>{@code activeOrdinal = -1}（既有语义："没有任何族达到第 1 档"）；</li>
     *   <li>{@code denominator = 0.0} ⇒ 既有守卫接管：族行悬停走「（窗口里还没有记录）」、
     *       {@code nextTierLine} 返回空串 ⇒ <b>不显示</b>"还差 N 个"这种需要分母的读数；</li>
     *   <li>{@code familyCount = }{@code UNKNOWN_FAMILY_COUNT}（{@code -1}）⇒ 不冒充"0 个"；
     *       又因为上面分母恒 0，这个哨兵<b>永远不会被画出来</b>（两处渲染点分别有
     *       {@code denominator > 0} 与 {@code activeOrdinal >= 0} 的前置守卫）；</li>
     *   <li>{@code clearAction = null}（清除只属于主机）。</li>
     * </ul>
     * ⇒ 真正要显示数据的调用方（主机面板 + 真空仓页）<b>必须</b>走 9 参重载传真回调。
     *
     * @param progress   历史窗口填充度 0~1
     * @param count      历史窗口里的电路总数
     * @param share      第 {@code ordinal} 个族的占比 0~1
     * @param fullWindow 判定分母是否 = 原版硬编码的 100000
     */
    public static WidgetGroup section(Supplier<Double> progress, Supplier<Long> count,
                                      IntFunction<Double> share, Supplier<Boolean> fullWindow) {
        return section(progress, count, share, fullWindow, () -> -1, () -> 0.0D,
                ordinal -> UNKNOWN_FAMILY_COUNT, null);
    }

    /**
     * ★★ 0.3.132【校准重构】带「生效族 / 本档功效 / 下一档」三个读数的那一版 ★★。
     *
     * <p>队长原话：「玩家无法直观地看到这个校准了以后到底都能有什么功效，这也需要有提示」
     * ⇒ 在原来"总历史条 + 8 族条 + 分母行"之外补三段**纯读数**（不改任何判定）：
     * <ol>
     *   <li><b>当前生效</b>：是哪个族（{@code activeOrdinal} = 服务端同步下来的那一格）；</li>
     *   <li><b>本档功效</b>：照实列出 EU ×0.8 / 该族时长 / 该族专属特效；</li>
     *   <li><b>下一档</b>：还差多少个本族电路（用与判定**同一把尺子**的分母算）。</li>
     * </ol>
     *
     * @param activeOrdinal 服务端同步的"生效族"ordinal（-1 = 无）—— **不在客户端复算**（见字段 javadoc）
     * @param denominator   本刻真正使用的分母（{@code fullWindow ? 100000 : historyTotal}）
     * @param familyCount   第 {@code ordinal} 族的现有计数（同步值）
     */
    public static WidgetGroup section(Supplier<Double> progress, Supplier<Long> count,
                                      IntFunction<Double> share, Supplier<Boolean> fullWindow,
                                      Supplier<Integer> activeOrdinal, Supplier<Double> denominator,
                                      IntFunction<Long> familyCount) {
        return section(progress, count, share, fullWindow, activeOrdinal, denominator, familyCount, null);
    }

    /**
     * ★★ 0.3.132【队长令：把「清除电路历史」与「纳米校准」合并到同一个 UI】★★
     *
     * <p>队长原话（逐字）：「后面记得把清除电路历史跟纳米校准这两个按钮合并到一个UI里，
     * 这是属于UI设计的，然后把这个按钮给隐藏了」。
     * ⇒ 清除按钮**做进本面板内部**（不再是左列那个独立图标），左列只保留「纳米校准」一张页。
     *
     * <h4>两步确认（破坏性动作的安全默认）</h4>
     * 第一次点 = 进入预备态（按钮文字与悬停换掉、按钮下沿出现危险标记）；
     * 第二次点才真正清除。预备态放**客户端本地静态字段** ⇒ 关掉界面 / 重开游戏自动取消
     * （绝不会出现"上次那一下"隔天生效）。
     *
     * <h4>两侧都会进 onClick</h4>
     * 判 {@code data.isRemote} 分侧（先例：{@code NanochipStructureRefresh.RefreshConfigurator}）：
     * 预备态只在客户端翻转；真正的清除只在**服务端**那一次执行。
     *
     * @param clearAction 真正执行"清空本机校准历史"的动作（{@code null} = 不画这个按钮，
     *                    例如真空仓那份共享面板 —— 清除只属于主机）
     */
    public static WidgetGroup section(Supplier<Double> progress, Supplier<Long> count,
                                      IntFunction<Double> share, Supplier<Boolean> fullWindow,
                                      Supplier<Integer> activeOrdinal, Supplier<Double> denominator,
                                      IntFunction<Long> familyCount, Runnable clearAction) {
        // ★ 0.3.132【防复发】这条重载**不知道每族档位**（= 仓室那份共享面板用的），
        //   改前这里返回**常量 0**，而主机调用点少传了一个实参 ⇒ Java 重载解析静默落到这条
        //   ⇒ 面板上所有读档位的地方全拿到 0，产出「占比 100% / 生效 0 档 / 本档功效：无 /
        //     还差 0 个到第 1 档」这种自相矛盾的可见态（实机 bug，已修）。
        //   ⇒ 现在返回 **-1（未知）**：它与"真的 0 档"在语义上**可区分**，
        //     且面板的每处档位消费点都按 `tier >= 1` 判定 ⇒ 显示为"未达"，**不再冒充第 0 档**。
        //   ⚠ 这条兜底存在的理由是仓室页要复用同一份版面；主机**必须**走下面那条带真回调的重载。
        return section(progress, count, share, fullWindow, activeOrdinal, denominator, familyCount,
                ordinal -> -1, clearAction);
    }

    /**
     * ★★ 0.3.133【重设计】完整签名 ★★ —— 比上一条多一个 <b>每族档位</b>回调。
     *
     * <p>为什么不在这里自己算档位：{@code NanochipAssemblyComplexMachine} 的
     * {@code syncedActiveFamilyOrdinal} 字段 javadoc 写得很清楚 ——
     * 「{@code activeFamily()} 的规则是『档位最高优先，同档比数量，且跳过 mergedAway 的族』，
     * 这三条若在客户端重写一遍，迟早与判定漂移（"显示即事实"就破了）」
     * ⇒ **档位一律读服务端同步值**（{@code NanochipAssemblyComplexMachine#syncedTierOf}）。
     *
     * @param tierOf 第 {@code ordinal} 族当前档位（0~3，**服务端同步值**，不在客户端复算）
     */
    public static WidgetGroup section(Supplier<Double> progress, Supplier<Long> count,
                                      IntFunction<Double> share, Supplier<Boolean> fullWindow,
                                      Supplier<Integer> activeOrdinal, Supplier<Double> denominator,
                                      IntFunction<Long> familyCount, IntFunction<Integer> tierOf,
                                      Runnable clearAction) {
        if (!NEW_LAYOUT) {
            return legacySection(progress, count, share, fullWindow, activeOrdinal, denominator,
                    familyCount, clearAction);
        }
        return newSection(progress, count, share, fullWindow, activeOrdinal, denominator,
                familyCount, tierOf, clearAction);
    }

    // ==========================================================================================
    // ★★ 0.3.133【重设计】新版面 ★★
    // ==========================================================================================

    private static WidgetGroup newSection(Supplier<Double> progress, Supplier<Long> count,
                                          IntFunction<Double> share, Supplier<Boolean> fullWindow,
                                          Supplier<Integer> activeOrdinal, Supplier<Double> denominator,
                                          IntFunction<Long> familyCount, IntFunction<Integer> tierOf,
                                          Runnable clearAction) {
        NanochipCalibration.Family[] families = NanochipCalibration.Family.barFamilies();
        int bandH = PAD_Y + LABEL_H + ROW_GAP + families.length * FAMILY_PITCH + PAD_Y;
        int bandBottom = BAND_Y + bandH;
        int effY = bandBottom + SECTION_GAP;
        int effH = 2 * PAD_Y + EFF_ROWS * LABEL_H + (EFF_ROWS - 1) * ROW_GAP;
        int effBottom = effY + effH;
        int btnY = effBottom + SECTION_GAP;
        int height = clearAction == null ? effBottom + PAD_Y : btnY + BTN_H + PAD_Y;
        WidgetGroup group = new WidgetGroup(0, 0, WIDTH, height);

        // ---------- ① 顶部读块（坐标与旧版面逐字相同；只把历史计数加了千分位） ----------
        group.addWidget(new LabelWidget(4, TITLE_Y,
                Component.translatable("gtlmodular.nanochip.calibration.header").getString()));
        group.addWidget(new ProgressWidget(progress::get, BAR_X, BAR_Y, BAR_W, BAR_H,
                new ProgressTexture(barTexture(0x40000000), barTexture(0xFF3FBFBF)))
                .setDynamicHoverTips(v -> String.format(java.util.Locale.ROOT, "%.1f％", v * 100.0D)));
        group.addWidget(new LabelWidget(4, HIST_Y, () -> String.format(java.util.Locale.ROOT,
                "历史 %s / %s 个电路",
                com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(count.get()),
                com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(
                        (long) NanochipCalibration.HISTORY_SIZE))));
        // 判定分母模式（配置项 nanochipComplex.calibrationUseFullWindow）：
        // 原版 = 分母硬编码 100000（整窗口）；否则 = 已记录数。悬停给出两种手感差别。
        group.addWidget(new LabelWidget(4, DEN_Y,
                () -> Component.translatable(NanochipCalibration
                        .denominatorModeKey(fullWindow.get())).getString())
                .setHoverTooltips(Component.translatable("gtlmodular.nanochip.calibration.denominator.tip")));

        // ---------- ② 族板：一级分组底板（面 + 2px 竖条 + 列头）----------
        group.addWidget(new ImageWidget(0, BAND_Y, WIDTH, bandH, barTexture(COL_PANEL)));
        group.addWidget(new ImageWidget(0, BAND_Y, STRIPE_W, bandH, barTexture(COL_ACCENT)));
        // 列头：弱色（§8），照数据能源 star_shard_config.json:29-56 的 column heading 手法。
        group.addWidget(new LabelWidget(BAND_TEXT_X, BAND_TITLE_Y, LIT_BAND_TITLE)
                .setHoverTooltips(Component.translatable("gtlmodular.nanochip.calibration.single")));

        // ---------- ③ 族行 × 8 ----------
        for (int i = 0; i < families.length; i++) {
            NanochipCalibration.Family family = families[i];
            final int ordinal = family.ordinal();
            final int y = FAMILY_ROW0_Y + i * FAMILY_PITCH;
            final int segY = y + (LABEL_H - SEG_H) / 2;   // 行内垂直居中（v1 §2 硬规则）

            // 生效行强调条：行首 2px，叠在板竖条右侧 ⇒ 该行成为 4px 强调。
            // 用 ProgressWidget（值可逐帧重读）而不是 ImageWidget ⇒ 生效族换了立刻跟着动（v2 §4 动态纪律）。
            group.addWidget(new ProgressWidget(
                    () -> activeOrdinal.get() == ordinal ? 1.0D : 0.0D,
                    STRIPE_W, y, STRIPE_W, LABEL_H,
                    new ProgressTexture(barTexture(COL_NONE), barTexture(COL_ACCENT))));

            // 族名（弱色由 § 码给在悬停/状态列里；名字本身保持默认色 = 数据层，照数据能源 B7）
            group.addWidget(new LabelWidget(BAND_TEXT_X, y, family.nameZh())
                    .setHoverTooltips(rowHover(family, ordinal, share, familyCount, denominator, tierOf)));

            // 档位格：可校准族 = 3 格（一格一档）；基础电路 = 一根纯历史条（无刻度 = 不参与校准）
            if (family.calibratable()) {
                for (int seg = 0; seg < 3; seg++) {
                    final int tier = seg + 1;
                    group.addWidget(new ProgressWidget(
                            () -> segmentFill(tierOf.apply(ordinal), share.apply(ordinal), family, tier),
                            SEG_X + seg * SEG_STRIDE, segY, SEG_W, SEG_H,
                            new ProgressTexture(barTexture(COL_PANEL_INNER),
                                    barTexture(0xFF000000 | family.color())))
                            .setDynamicHoverTips(v -> cellTip(family, tier, v)));
                }
            } else {
                group.addWidget(new ProgressWidget(() -> share.apply(ordinal), SEG_X, segY, SEG_SPAN, SEG_H,
                        new ProgressTexture(barTexture(COL_PANEL_INNER),
                                barTexture(0xFF000000 | family.color())))
                        .setDynamicHoverTips(v -> cellTip(family, 0, v)));
            }

            // 状态列（★ 取代旧版面的「0.0％」列）：纯文字，颜色只做冗余强化（去掉颜色仍读得懂）
            group.addWidget(new LabelWidget(STATE_X, y,
                    () -> familyStateText(tierOf.apply(ordinal), ordinal, family, activeOrdinal.get()))
                    .setHoverTooltips(rowHover(family, ordinal, share, familyCount, denominator, tierOf)));
        }

        // ---------- ④ 功效内嵌块：二级内嵌面（v2 §3②） ----------
        group.addWidget(new ImageWidget(EFF_X, effY, EFF_W, effH, barTexture(COL_PANEL_INNER)));
        group.addWidget(new LabelWidget(EFF_TEXT_X, effY + PAD_Y, () -> activeFamilyLine(activeOrdinal.get()))
                .setHoverTooltips(Component.translatable("gtlmodular.nanochip.calibration.active.none")));
        group.addWidget(new LabelWidget(EFF_TEXT_X, effY + PAD_Y + LABEL_H + ROW_GAP,
                () -> effectsLine(activeOrdinal.get(), tierOf))
                .setHoverTooltips(Component.translatable("gtlmodular.nanochip.calibration.effects.tip")));
        group.addWidget(new LabelWidget(EFF_TEXT_X, effY + PAD_Y + 2 * (LABEL_H + ROW_GAP),
                () -> nextTierShort(activeOrdinal.get(), denominator.get(),
                        activeOrdinal.get() < 0 ? 0L : familyCount.apply(activeOrdinal.get()), tierOf))
                .setHoverTooltips(Component.translatable("gtlmodular.nanochip.calibration.single")));

        // ---------- ⑤ 重置按钮（两步确认，见方法 javadoc） ----------
        if (clearAction != null) {
            // 预备态危险标记：按钮下沿 2px。★ 只读不点 ⇒ 不会抢按钮的点击。
            ImageWidget armedMark = new ImageWidget(BTN_X, btnY + BTN_H, BTN_W, ARMED_MARK_H,
                    barTexture(COL_DANGER));
            armedMark.setVisible(clearArmed());
            group.addWidget(armedMark);
            // 文字随确认状态读取，避免按钮显示与实际点击行为不一致。
            TextTexture text = new TextTexture(() -> Component.translatable(clearArmed()
                    ? "gtlmodular.nanochip.calibration.reset.armed"
                    : "gtlmodular.nanochip.calibration.reset").getString());
            text.setWidth(BTN_W);
            // 悬停槽：让点击回调能反过来改按钮自己的悬停文案（"点了立刻变"，v2 §4）。
            // 用持有器而不是在 lambda 里自引用 `button`（后者在 Java 里是"变量可能未初始化"✗）。
            HoverSlot slot = new HoverSlot();
            ButtonWidget button = new ButtonWidget(BTN_X, btnY, BTN_W, BTN_H,
                    new GuiTextureGroup(GuiTextures.BUTTON, text),
                    data -> onClearClick(data, clearAction, armedMark, slot));
            slot.widget = button;
            button.setHoverTooltips(Component.translatable(clearArmed()
                    ? "gtlmodular.nanochip.calibration.reset.armed.tip"
                    : "gtlmodular.nanochip.calibration.reset.tip"));
            group.addWidget(button);
        }
        return group;
    }

    // ==========================================================================================
    // ★★ 旧版面（0.3.132，逐字副本）—— NEW_LAYOUT = false 时走这里，一行回退 ★★
    // ==========================================================================================

    private static WidgetGroup legacySection(Supplier<Double> progress, Supplier<Long> count,
                                             IntFunction<Double> share, Supplier<Boolean> fullWindow,
                                             Supplier<Integer> activeOrdinal, Supplier<Double> denominator,
                                             IntFunction<Long> familyCount, Runnable clearAction) {
        NanochipCalibration.Family[] families = NanochipCalibration.Family.barFamilies();
        int rowH = 10;
        int rowsTop = 44;
        // 族条之后追加：空 2px + 三段读数（每段 10px）+ 清除按钮那一行（18px）+ 底部留白
        int extraTop = rowsTop + families.length * rowH + 2;
        int buttonY = extraTop + 3 * rowH + 2;
        int height = buttonY + (clearAction == null ? 2 : 18);
        WidgetGroup group = new WidgetGroup(0, 0, WIDTH, height);
        group.addWidget(new LabelWidget(4, 2,
                Component.translatable("gtlmodular.nanochip.calibration.header").getString()));

        // 总历史进度（0 → 100,000 个电路）
        group.addWidget(new ProgressWidget(progress::get, 4, 14, 144, 8,
                new ProgressTexture(barTexture(0x40000000), barTexture(0xFF3FBFBF)))
                .setDynamicHoverTips(v -> String.format(java.util.Locale.ROOT, "%.1f％", v * 100.0D)));
        group.addWidget(new LabelWidget(4, 23, () -> String.format(
                java.util.Locale.ROOT, "历史 %d / %d 个电路",
                count.get(), NanochipCalibration.HISTORY_SIZE)));
        // 判定分母模式（配置项 nanochipComplex.calibrationUseFullWindow）：
        // 原版 = 分母硬编码 100000（整窗口）；否则 = 已记录数。悬停给出两种手感差别。
        group.addWidget(new LabelWidget(4, 33,
                () -> Component.translatable(NanochipCalibration
                        .denominatorModeKey(fullWindow.get())).getString())
                .setHoverTooltips(Component.translatable("gtlmodular.nanochip.calibration.denominator.tip")));

        for (int i = 0; i < families.length; i++) {
            NanochipCalibration.Family family = families[i];
            int ordinal = family.ordinal();
            int y = rowsTop + i * rowH;
            group.addWidget(new LabelWidget(4, y, family.nameZh()));
            group.addWidget(new ProgressWidget(() -> share.apply(ordinal), 62, y, 50, 7,
                    new ProgressTexture(barTexture(0x30000000), barTexture(0xFF000000 | family.color())))
                    .setDynamicHoverTips(v -> String.format(java.util.Locale.ROOT, "%s %.2f％",
                            family.nameZh(), v * 100.0D)));
            group.addWidget(new LabelWidget(116, y, () -> String.format(
                    java.util.Locale.ROOT, "%.1f％", share.apply(ordinal) * 100.0D)));
        }

        // ---------- ★ 3 段"功效"读数（纯只读；数值全部来自同步字段 + 静态常量） ----------
        group.addWidget(new LabelWidget(4, extraTop, () -> activeFamilyLineLegacy(activeOrdinal.get()))
                .setHoverTooltips(Component.translatable("gtlmodular.nanochip.calibration.single")));
        group.addWidget(new LabelWidget(4, extraTop + rowH,
                () -> effectsLineLegacy(activeOrdinal.get()))
                .setHoverTooltips(Component.translatable("gtlmodular.nanochip.calibration.effects.tip")));
        group.addWidget(new LabelWidget(4, extraTop + 2 * rowH, () -> {
            int ordinal = activeOrdinal.get();
            double den = denominator.get();
            long have = ordinal < 0 ? 0L : familyCount.apply(ordinal);
            return nextTierLineLegacy(ordinal, den, have);
        }));

        // ---------- ★ 清除按钮（做进面板内部，见方法 javadoc） ----------
        if (clearAction != null) {
            group.addWidget(new ButtonWidget(4, buttonY, WIDTH - 8, 16,
                    new ColorRectTexture(0xFF3A3A3A),
                    data -> onClearClick(data, clearAction))
                    .setHoverTooltips(Component.translatable(clearArmed()
                            ? "gtlmodular.nanochip.calibration.reset.armed.tip"
                            : "gtlmodular.nanochip.calibration.reset.tip")));
            // 按钮上的文字（预备态变红提示）—— LDLib 的 ButtonWidget 文字由 texture 画，
            // 这里用一行 Label 叠在按钮上，颜色随预备态变（读的是同一个静态标记）。
            group.addWidget(new LabelWidget(0, buttonY + 4, () -> Component.translatable(clearArmed()
                    ? "gtlmodular.nanochip.calibration.reset.armed"
                    : "gtlmodular.nanochip.calibration.reset").getString())
                    .setTextColor(0xFFFFFF));
        }
        return group;
    }

    // ==========================================================================================
    // 渲染辅助（★ 全部纯函数：只读传入的同步值 + NanochipCalibration 的静态常量，不做任何判定）
    // ==========================================================================================

    /**
     * 第 {@code tier} 格（1..3）的填充比例 —— <b>判定归服务端，这一格只负责"把判定画出来"</b>。
     *
     * <ul>
     *   <li>{@code syncedTier >= tier} ⇒ 这一档已达 ⇒ <b>满格</b>；</li>
     *   <li>{@code syncedTier != tier - 1} ⇒ 还没轮到这一档 ⇒ <b>空格</b>；</li>
     *   <li>否则 ⇒ 该族正处在第 {@code tier - 1} 档 ⇒ 这一格按
     *       {@code (占比 − 上一档阈值) / (本档阈值 − 上一档阈值)} 部分填充
     *       ⇒ <b>这格的空白宽度就是"离下一档还差多少"</b>。</li>
     * </ul>
     *
     * <p>阈值是 {@link NanochipCalibration.Family#threshold(int)}（纯静态常量，两侧都有）；
     * 占比是同步值派生的 {@code syncedShareOf}。⇒ <b>客户端没有复算任何判定</b> ✓
     * 成本 O(1)（3 次浮点运算），每帧最多 24 次 ✓
     */
    private static double segmentFill(int syncedTier, double share, NanochipCalibration.Family family,
                                      int tier) {
        if (syncedTier >= tier) {
            return 1.0D;
        }
        if (syncedTier != tier - 1) {
            return 0.0D;
        }
        double lo = tier == 1 ? 0.0D : family.threshold(tier - 1);
        double hi = family.threshold(tier);
        if (hi <= lo) {
            return 1.0D;
        }
        double t = (share - lo) / (hi - lo);
        return t < 0.0D ? 0.0D : Math.min(1.0D, t);
    }

    /**
     * 状态列（★ 取代旧版面的「0.0％」列）—— <b>纯文字，四态互斥</b>。
     *
     * <p>照数据能源的两条做法：① 状态 = 一段文字（键二选一），不是"两种颜色"
     * （{@code lang/zh_cn.json:1085-1087} 的 {@code beam.status.online/offline/faulted}）；
     * ② 空态必须分开写（同上 {@code :1113}「暂无绑定设备」/ {@code :1161}「目标：未记录」）。
     *
     * <p>最长态 {@code 生效 3 档} = 37px ≤ 状态列宽 42px ✓
     */
    private static String familyStateText(int tier, int ordinal, NanochipCalibration.Family family,
                                          int activeOrdinal) {
        if (!family.calibratable()) {
            return "§8" + LIT_HISTORY_ONLY;
        }
        if (activeOrdinal == ordinal) {
            return "§b§l" + LIT_ACTIVE + "§r §b" + tier + LIT_TIER_POST;
        }
        if (tier >= 1) {
            return "§8" + LIT_TIER_PRE + "§b" + tier + "§8" + LIT_TIER_POST;
        }
        return "§8" + LIT_NOT_YET;
    }

    /**
     * 族行悬停（挂在族名与状态列上 ⇒ 整行可读）。
     *
     * <p>第 1 行给"占比 + 现有 / 分母"（照数据能源 {@code "已存储：%s / %s"} 的成对写法，
     * {@code lang/zh_cn.json:1135}）；第 2 行直接用<b>既有键</b> {@code …calibration.next}
     * 的完整长句（族名 / 档位 / 还差几个 / 现有几个 / 阈值）——
     * <b>长句进悬停</b>正是本轮解决"这一行太长折行"的手法 ✓
     */
    private static Component[] rowHover(NanochipCalibration.Family family, int ordinal,
                                        IntFunction<Double> share, IntFunction<Long> familyCount,
                                        Supplier<Double> denominator, IntFunction<Integer> tierOf) {
        double s = share.apply(ordinal);
        long have = familyCount.apply(ordinal);
        double den = denominator.get();
        String pct = String.format(java.util.Locale.ROOT, "%.2f", s * 100.0D) + "％";
        String line1 = "§7" + family.nameZh() + " §8· §7" + LIT_SHARE + pct
                + (den > 0.0D
                        ? "§7（" + com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(have)
                                + " / " + com.gregtechceu.gtceu.utils.FormattingUtil
                                        .formatNumbers((long) Math.ceil(den)) + "）"
                        : "§7（窗口里还没有记录）");
        String line2 = nextTierLine(ordinal, den, have, tierOf);
        return new Component[] {
                Component.literal(line1),
                Component.literal(line2.isEmpty() ? "§8" + LIT_HISTORY_ONLY : line2)
        };
    }

    /** 档位格的悬停：可校准族给"第 k 档阈值"，基础电路给"历史占比（不参与校准）"。 */
    private static String cellTip(NanochipCalibration.Family family, int tier, double value) {
        double pctShown = value * 100.0D;
        if (tier == 0) {
            return String.format(java.util.Locale.ROOT,
                    "§7历史占比 §b%.2f％§8（本族只进历史条，不参与校准）", pctShown);
        }
        return String.format(java.util.Locale.ROOT, "§7第 %d 档阈值 §b%.0f％§8（本格 = 该档进度）",
                tier, family.threshold(tier) * 100.0D);
    }

    /**
     * 「当前生效：<族>」/「当前生效：无」。
     *
     * <p>★ 与旧版面的差别：不再用那条 168px 长的 {@code …active.none}
     * （{@code §7当前生效：§8无（没有任何族达到第 1 档）} 在 152 宽里会折行），
     * 改成把 {@code §8无} 当作既有键 {@code …active} 的实参 ⇒ 只有 54px ✓
     * 而完整的那句话留在悬停里（{@link #newSection} 里挂 {@code …active.none}）✓
     */
    private static String activeFamilyLine(int ordinal) {
        NanochipCalibration.Family family = familyByOrdinal(ordinal);
        if (family == null) {
            return Component.translatable("gtlmodular.nanochip.calibration.active", LIT_NONE).getString();
        }
        return Component.translatable("gtlmodular.nanochip.calibration.active",
                "§b§l" + family.nameZh()).getString();
    }

    /**
     * 旧版面的「当前生效」行（逐字保留，含那条长键）。
     */
    private static String activeFamilyLineLegacy(int ordinal) {
        NanochipCalibration.Family family = familyByOrdinal(ordinal);
        if (family == null) {
            return Component.translatable("gtlmodular.nanochip.calibration.active.none").getString();
        }
        return Component.translatable("gtlmodular.nanochip.calibration.active", family.nameZh()).getString();
    }

    /**
     * ★ 新版面的「本档功效」行 —— <b>短标签 + 悬停长文</b>（队长第 3 条不满的正解）。
     *
     * <p>可见行 = 既有键 {@code …calibration.effects}（{@code §7本档功效：§f}）+ 一段
     * <b>"标签×倍率"</b>串；多个字段用 {@code /} 分隔 —— 照数据能源
     * {@code "频道 总:%s / 物理:%s / 虚拟:%s / 剩余:%s"}（{@code lang/zh_cn.json:1115}）的既有写法。
     *
     * <p>实测宽度（前缀 45px + 尾串）—— 全部 ≤ 144px，<b>单行不折</b>：
     * <pre>
     *   特殊族 T1/T2：时长×0.8 / ×0.6              = 87px
     *   特殊族 T3   ：时长×0.5/特效已生效            = 142px
     *   普通族 T1   ：EU×0.8                       = 81px
     *   普通族 T2   ：EU×0.8/时长×0.8                = 129px
     *   普通族 T3   ：EU×0.8/特效已生效              = 132px
     * </pre>
     *
     * <p>倍率取值<b>逐字对账</b> {@link NanochipCalibration}：普通族 EU 0.8（{@code euMultiplier}）、
     * 普通族时长 0.8（{@code durationMultiplier} 的 {@code tier >= 2}）、
     * 特殊曲线族时长 0.80/0.60/0.50 且 <b>第 1 档不减 EU</b>、第 3 档有专属特效（{@code specialActive}）。
     * 完整说明（含光学/奇异/寰宇/超因果的专属特效名）走既有键 {@code …effects.tip} ⇒ 长文一个字没丢 ✓
     */
    private static String effectsLine(int ordinal, IntFunction<Integer> tierOf) {
        NanochipCalibration.Family family = familyByOrdinal(ordinal);
        if (family == null) {
            return Component.translatable("gtlmodular.nanochip.calibration.effects.none").getString();
        }
        int tier = tierOf.apply(ordinal);
        if (tier < 1) {
            return Component.translatable("gtlmodular.nanochip.calibration.effects.none").getString();
        }
        String tail;
        if (family.specialStyleEffects()) {
            // 特殊曲线族：三档只有时长在变（第 1 档不减 EU）
            double time = switch (tier) {
                case 1 -> 0.80D;
                case 2 -> 0.60D;
                default -> 0.50D;
            };
            tail = "§a" + LIT_TIME + "§b§l" + mult(time);
            if (tier >= 3) {
                tail = tail + "§r§8/§a" + LIT_T3 + "§b§l" + LIT_T3_ON;
            }
        } else if (tier >= 3) {
            tail = "§a" + LIT_EU + "§b§l" + mult(0.8D) + "§r§8/§a" + LIT_T3 + "§b§l" + LIT_T3_ON;
        } else if (tier == 2) {
            tail = "§a" + LIT_EU + "§b§l" + mult(0.8D) + "§r§8/§a" + LIT_TIME + "§b§l" + mult(0.8D);
        } else {
            tail = "§a" + LIT_EU + "§b§l" + mult(0.8D);
        }
        return Component.translatable("gtlmodular.nanochip.calibration.effects", tail).getString();
    }

    /**
     * 旧版面的「本档功效」行（逐字保留：那条长键 + 长句）。
     *
     * <p>★ 取值口径（逐字取证，见 {@code NanochipCalibration}）：
     * <ul>
     *   <li>普通族（晶体/湿件/生物/光学）：T1 ⇒ 所有模块 EU ×0.8；T2 ⇒ 该族时长 ×0.8；
     *       T3 ⇒ 该族专属特效（{@code tier >= 2 ? 0.8D : 1.0D}，**只有一档**）；</li>
     *   <li>特殊曲线族（奇异/寰宇/超因果）：T1/T2/T3 ⇒ 该族时长 ×0.8 / ×0.6 / ×0.5（累计）；
     *       **T1 不减 EU**。</li>
     * </ul>
     */
    private static String effectsLineLegacy(int ordinal) {
        NanochipCalibration.Family family = familyByOrdinal(ordinal);
        if (family == null) {
            return Component.translatable("gtlmodular.nanochip.calibration.effects.none").getString();
        }
        return Component.translatable("gtlmodular.nanochip.calibration.effects",
                Component.translatable(family.specialStyleEffects()
                        ? "gtlmodular.nanochip.calibration.effect.duration.special"
                        : "gtlmodular.nanochip.calibration.effect.duration.normal",
                        family.nameZh())).getString();
    }

    /** {@code 0.8D → "×0.8"}（一位小数，与判定常量逐字同源）。 */
    private static String mult(double m) {
        return "×" + String.format(java.util.Locale.ROOT, "%.1f", m);
    }

    /**
     * ★ 新版面的「下一档」行 —— <b>短句</b>：{@code §8还差 §b27,655§8 个到 §b第 2 档}（116px），
     * 或整行直接用既有键 {@code …next.max}（{@code §7下一档：§a已是最高档（第 3 档）}，131px）。
     *
     * <p>缺口算式与 {@code NanochipCalibration.tierOf} <b>同一把尺子</b>：
     * {@code need = ceil(threshold(下一档) × 分母) − 本族现有数}；
     * 档位本身<b>读同步值</b>，不在客户端判定 ✓
     * 措辞照数据能源的余量写法（{@code lang/zh_cn.json:1296/1307} 的"剩余 N"）✓
     */
    private static String nextTierShort(int ordinal, double denominator, long familyCount,
                                        IntFunction<Integer> tierOf) {
        NanochipCalibration.Family family = familyByOrdinal(ordinal);
        if (family == null) {
            return LIT_NO_TIER1;
        }
        if (!family.calibratable()) {
            return "§8本族只进历史条，不参与校准";
        }
        int tier = tierOf.apply(ordinal);
        if (tier >= 3 || denominator <= 0.0D) {
            return Component.translatable("gtlmodular.nanochip.calibration.next.max").getString();
        }
        int next = tier + 1;
        double needPct = family.threshold(next);
        if (needPct <= 0.0D) {
            return Component.translatable("gtlmodular.nanochip.calibration.next.max").getString();
        }
        long needTotal = (long) Math.ceil(needPct * denominator);
        long missing = Math.max(0L, needTotal - familyCount);
        return "§8" + LIT_GAP + "§b"
                + com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(missing)
                + "§8" + LIT_GAP_MID + "§b" + LIT_TIER_PRE + next + LIT_TIER_POST;
    }

    /**
     * 「下一档」的<b>完整长句</b>（既有键 {@code …calibration.next} / {@code …next.max}）—— <b>给悬停用</b>。
     *
     * <p>★ 与旧版面的唯一差别：档位不再由客户端复算，改读服务端同步值
     * （旧版面的客户端复算见 {@code legacySection} 用的 {@code nextTierLineLegacy}）。
     * 缺口语义不变：{@code need = ceil(threshold(下一档) × 分母) − 本族现有数}，
     * 其中 {@code 分母 = fullWindow ? 100000 : 历史总数}（与 {@code denominator()} 逐字同源）。
     * 已是第 3 档（或该族不可校准）⇒ 显示「已是最高档」。
     */
    private static String nextTierLine(int ordinal, double denominator, long familyCount,
                                       IntFunction<Integer> tierOf) {
        NanochipCalibration.Family family = familyByOrdinal(ordinal);
        if (family == null || denominator <= 0.0D || !family.calibratable()) {
            return "";
        }
        int tier = tierOf.apply(ordinal);
        if (tier >= 3) {
            return Component.translatable("gtlmodular.nanochip.calibration.next.max").getString();
        }
        int next = tier + 1;
        double needPct = family.threshold(next);
        if (needPct <= 0.0D) {
            return Component.translatable("gtlmodular.nanochip.calibration.next.max").getString();
        }
        long needTotal = (long) Math.ceil(needPct * denominator);
        long missing = Math.max(0L, needTotal - familyCount);
        return Component.translatable("gtlmodular.nanochip.calibration.next",
                family.nameZh(), String.valueOf(next),
                com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(missing),
                com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(familyCount),
                String.format(java.util.Locale.ROOT, "%.0f", needPct * 100.0D)).getString();
    }

    /**
     * <b>旧版面</b>的「下一档」（逐字保留，含客户端档位复算）。
     *
     * <p>⚠ 如实登记：这一段在**客户端**重写了 {@code tierOf} 的算式
     * （{@code for t=1..3 if pct >= threshold(t)}）——它**只在 NEW_LAYOUT = false 时才会执行**。
     * 新版面已经改走服务端同步档位（见 {@link #nextTierLine}），本方法是为了"一行回退"才原样留着的。
     */
    private static String nextTierLineLegacy(int ordinal, double denominator, long familyCount) {
        NanochipCalibration.Family family = familyByOrdinal(ordinal);
        if (family == null || denominator <= 0.0D || !family.calibratable()) {
            return "";
        }
        // 当前档位：由「本族数量 / 分母」与阈值比出来（与服务端 tierOf 同一算式）
        double pct = familyCount / denominator;
        int tier = 0;
        for (int t = 1; t <= 3; t++) {
            if (family.threshold(t) > 0.0D && pct >= family.threshold(t)) {
                tier = t;
            }
        }
        if (tier >= 3) {
            return Component.translatable("gtlmodular.nanochip.calibration.next.max").getString();
        }
        int next = tier + 1;
        double needPct = family.threshold(next);
        if (needPct <= 0.0D) {
            return Component.translatable("gtlmodular.nanochip.calibration.next.max").getString();
        }
        long needTotal = (long) Math.ceil(needPct * denominator);
        long missing = Math.max(0L, needTotal - familyCount);
        return Component.translatable("gtlmodular.nanochip.calibration.next",
                family.nameZh(), String.valueOf(next),
                com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(missing),
                com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(familyCount),
                String.format(java.util.Locale.ROOT, "%.0f", needPct * 100.0D)).getString();
    }

    /** 由 ordinal 反查族（-1 / 越界 / 已合并的隐藏列都返回 null）。 */
    private static NanochipCalibration.Family familyByOrdinal(int ordinal) {
        if (ordinal < 0) {
            return null;
        }
        for (NanochipCalibration.Family f : NanochipCalibration.Family.barFamilies()) {
            if (f.ordinal() == ordinal) {
                return f;
            }
        }
        return null;
    }

    // ==========================================================================================
    // 清除按钮的两步确认
    // ==========================================================================================

    /**
     * ★ 清除按钮的两步确认状态机（**客户端本地**，见方法 javadoc 的取舍）。
     *
     * <p>为什么是 {@code static}：这个面板每次打开都重建（LDLib 在客户端也会建），
     * 状态若挂在 widget 实例上就会"点一次就丢"。而放静态 ⇒ 关掉界面/重开游戏自动取消
     * （安全默认：绝不会出现"上次那一下"隔天生效）。
     */
    private static volatile boolean CLEAR_ARMED = false;

    private static boolean clearArmed() {
        return CLEAR_ARMED;
    }

    /**
     * 清除按钮的点击处理：<b>两侧都会进来</b>，按 {@code isRemote} 分侧。
     * <ul>
     *   <li>客户端：翻转预备态（不做事）；</li>
     *   <li>服务端：仅当已预备才真正清除，并立刻解除预备键。</li>
     * </ul>
     */
    private static void onClearClick(com.lowdragmc.lowdraglib.gui.util.ClickData data, Runnable clearAction) {
        if (data == null) {
            return;
        }
        if (data.isRemote) {
            CLEAR_ARMED = !CLEAR_ARMED;
            return;
        }
        if (!CLEAR_ARMED) {
            return;
        }
        CLEAR_ARMED = false;
        clearAction.run();
    }

    /**
     * 新版面的点击处理：除两条确认语义外，还<b>立刻</b>把两个视觉信号翻过来
     * （危险标记的可见性 + 按钮悬停文案）⇒ 满足 v2 §4「动态控件不允许要求玩家"关掉重开"」✓
     */
    /**
     * ★ 让点击回调能**反过来改按钮自己的悬停文案**（"点了立刻变文案"）。
     *
     * <p>为什么用持有器而不是在 lambda 里自引用 `button`：Java 的 lambda 捕获要求变量
     * **最终或有效最终**，而 `button` 在 lambda 创建之后才赋值 ⇒ 直接自引用会编译失败
     * （"variable button might not have been initialized"）。⇒ 用一个可变持有器把引用传进去。
     */
    private static final class HoverSlot {
        ButtonWidget widget;
    }

    private static void onClearClick(com.lowdragmc.lowdraglib.gui.util.ClickData data, Runnable clearAction,
                                     Widget armedMark, HoverSlot slot) {
        // 复用 3 参版本的全部逻辑（分侧、预备态、真清除）
        onClearClick(data, clearAction, armedMark);
        // 再把按钮自己的悬停文案刷成与最新预备态一致（否则要点第二次才变）
        if (slot != null && slot.widget != null) {
            slot.widget.setHoverTooltips(Component.translatable(clearArmed()
                    ? "gtlmodular.nanochip.calibration.reset.armed.tip"
                    : "gtlmodular.nanochip.calibration.reset.tip"));
        }
    }

    private static void onClearClick(com.lowdragmc.lowdraglib.gui.util.ClickData data, Runnable clearAction,
                                     Widget armedMark) {
        if (data == null) {
            return;
        }
        if (data.isRemote) {
            CLEAR_ARMED = !CLEAR_ARMED;
            armedMark.setVisible(CLEAR_ARMED);
            return;
        }
        if (!CLEAR_ARMED) {
            return;
        }
        CLEAR_ARMED = false;
        clearAction.run();
    }

    /** 空的条底 / 已填充的条色（与仓室那份逐字一致）。 */
    private static IGuiTexture barTexture(int argb) {
        ColorRectTexture rect = new ColorRectTexture();
        rect.color = argb;
        return rect;
    }

    /**
     * fancy 配置页（左列那个可按的图标 + 展开后的面板）—— 主机与仓室共用同一张脸。
     *
     * <p>为什么做成"按需现建"（{@code Supplier<Widget>}）而不是构造时就建好：
     * LDLib 在<b>客户端</b>也会调 {@code createConfigurator()}，而面板里的值是每帧回调读的，
     * 每次打开都重新建一份最省心，也不会把过期数值钉在对象里。
     */
    public static final class Configurator implements IFancyConfigurator {

        private final Supplier<Widget> builder;

        public Configurator(Supplier<Widget> builder) {
            this.builder = builder;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gtlmodular.nanochip.calibration.title");
        }

        @Override
        public IGuiTexture getIcon() {
            return new ResourceTexture(ICON);
        }

        @Override
        public List<Component> getTooltips() {
            return List.of(Component.translatable("gtlmodular.nanochip.calibration.title"));
        }

        @Override
        public Widget createConfigurator() {
            return builder.get();
        }
    }

    // ==========================================================================================
    // ★★ 0.3.132【校准重构 · 清除入口】已并入 {@code section(...)} ★★
    // ==========================================================================================
    //
    // 队长令（逐字）：「把清除电路历史跟纳米校准这两个按钮合并到一个UI里，这是属于UI设计的，
    // 然后把这个按钮给隐藏了」⇒ 原先清除做成**左列独立图标按钮**（ResetConfigurator）的做法已删除，
    // 改为 `section(..., clearAction)` 里的**面板内按钮**，左列只留「纳米校准」一张页。
    //
    // ⚠ 那条"两步确认"的状态机没丢，现在落在本类的 `CLEAR_ARMED` + `onClearClick`。
    // ⚠ 清空动作本身仍在机器侧（`NanochipAssemblyComplexMachine#clearCalibration`）——
    //   本类不认识机器类型，只吃一个 `Runnable`（同"取值回调"那条纪律）。
    //
    // ★★ 0.3.133【重设计】★★：旧版面（含它自己的客户端档位复算与 50px 灰条 + "0.0％" 列）
    //   逐字保留在 `legacySection(...)` + `*Legacy(...)` 方法里，`NEW_LAYOUT = false` 即回退。
}
