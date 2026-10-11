package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.config.GTLModularConfig;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

/**
 * 纳米芯片组装复合体（NAC）的<b>纳米校准</b>系统。
 *
 * <p>完全按 GTNH 2.9.0 的规格移植（{@code GT5U.gui.text.nac.info.calibration.*} 原文）：
 * <ul>
 *   <li>历史窗口 = <b>100 批 × 每批 1000 个电路 = 10 万个电路</b>；
 *       ★ 2026-11-01 起用 <b>{@code byte[HISTORY_SIZE]} 的逐条族序号环形缓冲</b>表示
 *       （窗口恒等于「最近 100,000 条记录」，逐条精确覆盖 —— 见下面
 *       「★★ 2026-11-01【缺陷定案 · 窗口表示重写】」一节；此前是「100 批 × 每族计数」，
 *       那套表示有「占比上限 99.999%、100% 数学上不可达」的缺陷）；</li>
 *   <li>各族电路占比 = 该族在窗口里的数量 / <b>判定分母</b>。分母有两种口径，由配置项
 *       {@code nanochipComplex.calibrationUseFullWindow} 切换（见 {@link #denominator()}）：
 *       <b>★ 2026-09-21 队长令「改」后默认 = {@code false}</b>：分母是「已记录数」
 *       （本移植版早期行为 —— 窗口没攒满时族条涨得看得见；攒满 10 万后与原版口径完全一致）；
 *       切成 {@code true} 则回到原版口径：分母固定 {@link #HISTORY_SIZE}（100000），原版
 *       {@code CircuitCalibration.getCurrentCalibration} 把它硬编码成 {@code 100000.0d}（三处
 *       {@code ldc2_w 100000.0d} + {@code ddiv}），即「阈值占比 × 整个历史窗口」——
 *       队长 2026-09-21 报的"条不动"就是这个口径下的正常现象（一次产出按阶梯 1~8 个 × 并行，占比只到 0.004px）。
 *       两种口径都<b>不动</b>窗口容量、批次数、族集合与下面这套阈值；</li>
 *   <li>占比达到阈值 → 该族「校准」生效，共 3 档；<b>同时只能存在一个校准，档位高者优先</b>；</li>
 *   <li>晶体 / 湿件 / 生物 / 光学 四族的阈值为 <b>20% / 40% / 60%</b>；</li>
 *   <li>特殊族（原版 specials 段）阈值为 <b>1% / 5% / 20%</b>，效果是组装矩阵时长
 *       −20% / −40%（累计）/ −50%（累计），第 1 档<b>不减 EU</b>。</li>
 * </ul>
 *
 * <p>★★ <b>2026-09-21 队长两道令（本类都落了）</b>★★
 * <ol>
 *   <li>「<b>那个阈值肯定要设的</b>」⇒ 奇异（{@link Family#EXOTIC}）/ 寰宇（{@link Family#COSMIC}）/
 *       超因果（{@link Family#TEMPORAL}，原「超时空」）三族的阈值从 {@code 0.0/0.0/0.0}
 *       （只进历史条）改为 <b>0.01 / 0.05 / 0.20</b>；</li>
 *   <li>「<b>第5个是合并成一条</b>」⇒ 原「超时空」（{@code temporals}）与「特殊」（{@code specials}）
 *       <b>合并成校准条上的一条「超因果电路」</b>：承载常量为 {@link Family#TEMPORAL}
 *       （{@code nameZh = 超因果电路}），{@link Family#SPECIAL} <b>保留常量但退出校准条与生效判定</b>
 *       （只为保住枚举序号 ⇒ 存档按 ordinal 定位族，逐位兼容 —— ★ 2026-11-01 起新格式是
 *       {@code RingRLE} 的<b>低 4 位</b>，旧格式是 {@code B<n>} int 数组，两者都按同一套 ordinal），
 *       {@link Family#of(ResourceLocation)} 对 {@code pico}/{@code quantum} 改归 {@code TEMPORAL}。</li>
 * </ol>
 * 依据：队长令（2026-09-21）+ 设计文档 D-1（{@code _census/三族纳米配方_设计.md:70,:84}
 * 「校准族口径：<b>复用 SPECIAL</b>（阈值 1%/5%/20%，维基逐字有据）」）。副产物：三台组织器
 * （奇异 / 寰宇 / 超因果）的「满校准翻倍」（{@code specialActive}，只要求「生效族 == 本组织器族
 * 且 该族 ≥ 3 档」）从此<b>可达</b>；但「同时只能存在一个校准」这条原版规格不变
 * ⇒ 三台里同一时刻最多只有一台能翻倍。
 *
 * <p>⚠️ <b>与 NH 的有意分歧（如实登记，不是漏判）</b>：NH 字节码里
 * {@code PRIMITIVE/EXOTIC/COSMIC/TEMPORAL} 的阈值本是 <b>0.2/0.4/0.6 且「效果为空」</b>
 * （我方体检 {@code _census/check_nac_mechanics_parity.js} 的 X5 行原文），校准条也是 9 段；
 * 队长的两道令把它变成 <b>「三族 1%/5%/20% + 有效果」</b> 与 <b>「8 段（超时空与特殊并成一条超因果）」</b>。
 * 若要回到原版九段：把 {@link Family#barFamilies()} 换回 {@code values()}，
 * 并把 {@link Family#of(ResourceLocation)} 的 {@code pico}/{@code quantum} 分支改回 {@code SPECIAL}
 * —— <b>本方案完全可逆</b>（枚举常量一个都没删）。
 *
 * <p><b>效果曲线分两类</b>（由 {@link Family#specialStyleEffects()} 决定）：
 * 普通族（晶体/湿件/生物/光学）第 1 档「所有模块 EU ×0.8」、第 2 档「该族电路在组装矩阵里时长 ×0.8」、
 * 第 3 档为该族专属特效（激光源仓生效 / 不消耗生长培养基 / 培养基全免 / 水加成翻倍）；
 * 「特殊曲线」族（{@link Family#EXOTIC}/{@link Family#COSMIC}/{@link Family#TEMPORAL}，
 * 按 D-1「三族复用 SPECIAL」）第 1 档<b>不减 EU</b>，三档时长 −20% / −40% / −50% 累计。
 * 两者都由本类给出标志位、机器侧读取。
 *
 * <p>★★ <b>2026-11-01【缺陷定案 · 窗口表示重写】</b>★★
 *
 * <h4>① 缺陷现象（队长原话 + 实机日志逐字）</h4>
 * 队长：「<b>我一次做 1M 的超因果电路，为什么我还是不能让这个纳米校准显示 100%</b>」、
 * 「那个校准固定的 10 万<b>只是算他的产物</b>，不可能达不到，他只要产出了 10 万个同系列的电路，
 * 那它就应该达到 100%」。日志逐字：
 * <pre>
 * 校准记账 @…：超因果电路 +125000     ⇒ 本族 99376 / 历史 99376 · 分母 100000（原版固定 100000） ⇒ 占比 99.376%（生效档位 3）
 * 校准记账 @…：超因果电路 +2147483647 ⇒ 本族 99023 / 历史 99023 · 分母 100000                     ⇒ 占比 99.023%
 * </pre>
 *
 * <h4>② 根因（已查实，不再重新论证）</h4>
 * 旧表示是 {@code int[100 批][族数]}（每批 1000 条，共 10 万）。轮转时
 * {@code subtractBatch(batches[cursor])} 把<b>最老那一整批 1000 条</b>一起减掉并清零，
 * 而其中<b>还有 {@code 1000 - cursorFill} 条仍在窗口内</b> ⇒ 任一刻窗口只有 {@code 99,000 + cursorFill} 条
 * ⇒ 固定分母 100,000 下占比上限 <b>99.999%</b>，<b>100% 数学上不可达</b>；
 * 日志里的 99,376 / 99,023 全部形如 {@code 99,000 + k}，逐条印证。
 * 之所以不能「一行修好」：要精确减回那截尾巴，必须知道最老那一批里<b>每条记录属于哪个族</b>——
 * 而旧表示只存了「每族计数」、<b>没存记录的先后顺序</b> ⇒ 只能换窗口的表示。
 *
 * <h4>③ 新表示（唯一事实源）</h4>
 * <pre>
 * private final byte[] ring = new byte[HISTORY_SIZE];   // 逐条：该条记录所属族的 ordinal（0..15）
 * private int ringPos;      // 下一条要写的位置（满窗时它就是当前最旧记录的位置）
 * private int totalAll;     // 窗口内有效条数 0..HISTORY_SIZE
 * private final int[] familyTotals = new int[Family.values().length];   // O(1) 派生缓存（面板每 tick 读）
 * </pre>
 * 写一条记录 = 覆盖 {@code ring[ringPos]} 那一格，然后 {@code ringPos = (ringPos + 1) % HISTORY_SIZE}
 * ⇒ 窗口<b>恒等于「最近 100,000 条记录」</b>，不多不少 ⇒ 固定分母 10 万下
 * <b>100% 必然可达</b>，且各族占比精确（被覆盖那一格所属的族<b>精确减 1</b>，不做任何按比例近似）。
 * <b>唯一事实源是 {@code ring}</b>，{@code familyTotals}/{@code totalAll} 只是它的派生量。
 * 旧的分批表示（{@code batches} / {@code subtractBatch} / {@code recomputeAggregates} /
 * {@code cursor} / {@code cursorFill}）<b>整组删除</b>，不保留「两套并存的真相源」。
 *
 * <h4>④ 不变量（{@code record}/{@code load}/{@code clear} 之后恒成立）</h4>
 * <ul>
 *   <li>{@code totalAll == Σ familyTotals[ord]}（{@link #recountFamilyTotals()} 由 ring 重算保证一致）；</li>
 *   <li>{@code totalAll ==} ring 里的<b>有效槽位数</b>：窗口没满时有效区恒为 {@code [0, totalAll)}
 *       且 {@code ringPos == totalAll}；满窗时 100,000 格全部有效；</li>
 *   <li>{@code recorded == min(累计写入条数, HISTORY_SIZE)}（用 long 相加后夹逼，绝不 int 回绕）；</li>
 *   <li>{@code ringPos ∈ [0, HISTORY_SIZE)}；「逐条覆盖最旧」那段只可能在 {@code totalAll == HISTORY_SIZE}
 *       时进入 ⇒ 每个被减的槽位都是有效记录 ⇒ {@code familyTotals} 永不为负；</li>
 *   <li>{@code batchProgress() == (ringPos % BATCH_SIZE) / BATCH_SIZE}，仍表示「当前批已填比例」。</li>
 * </ul>
 *
 * <h4>⑤ 存档 / 旧档迁移</h4>
 * <b>写</b>：旧键名 {@code Cursor}（= {@code ringPos}）、{@code Fill}（= {@code ringPos % BATCH_SIZE}）、
 * {@code Recorded} 保留（别的代码/工具仍按它们读），<b>新增</b> {@code Total}（= {@code totalAll}）与
 * {@code RingRLE}（{@code int[]} 游程编码，见 {@link #encodeRingRle()}：每个 int 打包
 * {@code (runLength << 4) | familyOrdinal}，单族时只占 1 个 int，体积远小于旧格式）。
 * <b>读</b>：有 {@code RingRLE} ⇒ 解回 ring，再按 {@code Total}/{@code Cursor}/{@code Recorded} 夹逼后
 * <b>从 ring 重算 totals</b>（不信任存档里的数）；<b>没有</b> {@code RingRLE}（旧档）⇒
 * {@link #migrateLegacyBatches(CompoundTag)} 读旧的 {@code B<n>} 稀疏批数组按族成块搬进 ring
 * （旧档压根没有顺序信息 ⇒ 这是唯一可行的迁移，只影响「顺序」、不影响任何计数）。
 * 一切外部输入（旧档 / 手改档 / 坏档）都夹逼；数组长度不符或 RLE 解出的总长
 * {@code != HISTORY_SIZE} ⇒ 当作空窗口，<b>绝不越界、绝不卡服</b>。
 *
 * <h4>⑥ 一行回退</h4>
 * <pre>git checkout -- gtl-modular/src/main/java/com/gtlmodular/common/machine/nanochip/NanochipCalibration.java</pre>
 * —— 回到「100 批 × 每族计数」的旧表示。代价<b>如实登记</b>：旧代码读得懂旧档，但<b>读不懂新代码写出的档</b>
 * （新档没有 {@code B<n>} 键 ⇒ 旧 {@code load()} 得到空窗口；旧代码不读 {@code Total}/{@code RingRLE}）。
 * 即回退 = 「能编译、能跑，但校准窗口清零」，不是无损回退。
 *
 * <h4>⑦ 代价（如实登记）</h4>
 * 每台 NAC 的校准对象内存从 {@code int[100][9]} ≈ 3.6 KB 涨到 {@code byte[100000]} ≈ 97.7 KB
 * （+94 KB/台）。换来的是「窗口精确 = 最近 10 万条」这条语义，以及 100% 可达。
 */
public final class NanochipCalibration {

    /** 每批电路数。 */
    public static final int BATCH_SIZE = 1000;

    /** 批次数（环形缓冲长度）。 */
    public static final int BATCH_COUNT = 100;

    /** 历史窗口总电路数 = 100,000。 */
    public static final int HISTORY_SIZE = BATCH_SIZE * BATCH_COUNT;

    /**
     * 电路族（对应 GTNH 校准进度条的 9 段：primitives / crystals / wetwares / bios /
     * opticals / exotics / cosmics / temporals / specials）。
     */
    public enum Family {

        /** 基础电路（电子电路 ~ 处理器系列）：只进历史条，不参与校准。 */
        PRIMITIVE("primitives", "基础电路", 0.0D, 0.0D, 0.0D),

        /** 晶体电路。 */
        CRYSTAL("crystals", "晶体电路", 0.20D, 0.40D, 0.60D),

        /** 湿件电路。 */
        WETWARE("wetwares", "湿件电路", 0.20D, 0.40D, 0.60D),

        /** 生物电路（原版 Bio Circuits）。 */
        BIOWARE("bios", "生物活性电路", 0.20D, 0.40D, 0.60D),

        /** 光学电路。 */
        OPTICAL("opticals", "光学电路", 0.20D, 0.40D, 0.60D),

        /**
         * 异域电路（原版 Exotics Circuits）。
         *
         * <p>★ <b>2026-09-21 队长令</b>：「<b>那个阈值肯定要设的</b>」⇒ 阈值从
         * {@code 0.0/0.0/0.0}（只进历史条）改为与 {@code SPECIAL} 一致的三档
         * <b>0.01 / 0.05 / 0.20</b>。依据：
         * <ul>
         *   <li>设计文档 D-1（{@code _census/三族纳米配方_设计.md:70,:84}
         *       「校准族口径：<b>复用 SPECIAL</b>（阈值 1%/5%/20%，维基逐字有据）」）；</li>
         *   <li>NH 校准条九段里 exotics 那一段在本包的对应族就是它，而奇异优化组织器
         *       （{@code NanochipModuleKind.EXOTIC_ORGANIZER}）的「满校准翻倍」走
         *       {@code specialActive(EXOTIC)} ⇒ 阈值恒 0 时那台<b>永远拿不到翻倍</b>。</li>
         * </ul>
         * ⚠️ 效果曲线：本族按 D-1「复用 SPECIAL」走**特殊曲线**（第 1 档<b>不减 EU</b>、
         * 三档时长 −20/−40/−50% 累计，见 {@link #specialStyleEffects()}）——
         * 这是与 NH 的**有意分歧**（NH 里 exotics 段阈值 0.2/0.4/0.6 且效果为空）。
         */
        EXOTIC("exotics", "奇异电路", 0.01D, 0.05D, 0.20D),

        /**
         * 宇宙电路（原版 Cosmics Circuits）。阈值与效果曲线依据同 {@link #EXOTIC}
         * （2026-09-21 队长令「那个阈值肯定要设的」+ 设计文档 D-1「三族复用 SPECIAL」）。
         */
        COSMIC("cosmics", "寰宇电路", 0.01D, 0.05D, 0.20D),

        /**
         * 时空电路（原版 temporals 段，英文原文 <b>Temporally Transcendent Circuits</b>）。
         *
         * <p>校对来源（2026-09-14，GTNH 2.9.0-beta-2 的 {@code gregtech-5.09.54.20.jar}）：
         * <pre>
         * GT5U.gui.text.nac.bar.label.temporals=Temporally Transcendent Circuits
         * </pre>
         * 原版校准条的 9 段依次是 primitives / crystals / wetwares / bios / opticals / exotics /
         * cosmics / temporals / specials，本枚举与之<b>一一对应</b>（序号一个都没动 ——
         * 这是存档里「族序号」的键 —— ★ 2026-11-01 起新格式 {@code RingRLE} 的低 4 位存的就是它，
         * 旧格式 {@code B<n>} 数组的下标也是它）。显示名允许到 6 个汉字（校准页列宽按 6 字留）。
         *
         * <p>★★ <b>2026-09-21 队长令「第5个是合并成一条」</b>★★：本常量<b>承载校准条上那条
         * 「超因果电路」</b> —— 原「超时空」（temporals，本常量）与「特殊」（specials）合并成一条，
         * 于是：
         * <ul>
         *   <li>{@code nameZh} 由「超时空电路」改为「<b>超因果电路</b>」（队长点名要的名字）；</li>
         *   <li>阈值 <b>0.01 / 0.05 / 0.20</b>（队长令「那个阈值肯定要设的」+ D-1）；</li>
         *   <li>效果走 {@code SPECIAL} 那套曲线（{@link #specialStyleEffects()}）：第 1 档不减 EU、
         *       三档时长 −20%/−40%/−50% 累计 ⇒ Pico/Quantum 的加成<b>不会因为并段而消失</b>；</li>
         *   <li>超因果优化组织器（{@code NanochipModuleKind.SUPRACAUSAL_ORGANIZER}，
         *       {@code organizerFamily() = TEMPORAL}）的「满校准翻倍」从此<b>可达</b>。</li>
         * </ul>
         */
        TEMPORAL("temporals", "超因果电路", 0.01D, 0.05D, 0.20D),

        /**
         * 特殊电路（Pico / Quantum）：阈值 1% / 5% / 20%。
         *
         * <p>★★ <b>2026-09-21 队长令「第5个是合并成一条」</b>★★：本族已并入
         * {@link #TEMPORAL}（「超因果电路」）—— 常量<b>保留</b>只为**枚举序号/NBT 兼容**
         * （{@link NanochipCalibration#save()}/{@link NanochipCalibration#load(CompoundTag)}
         * 存的是族 ordinal：★ 2026-11-01 起新格式 {@code RingRLE} 低 4 位，旧档 {@code B<n>} int 数组下标，
         * 删常量会让老存档错位），
         * 它<b>不再出现在校准条上</b>（{@link #barFamilies()}）、不再参与「哪一族生效」的判定
         * （{@link NanochipCalibration#activeFamily()}），{@link #of(ResourceLocation)} 也不再返回它。
         * 老存档里落在本列的历史计数会留在隐藏列（不再展示、不再生效）。
         */
        SPECIAL("specials", "特殊电路", 0.01D, 0.05D, 0.20D);

        private final String key;
        private final String nameZh;
        private final double t1;
        private final double t2;
        private final double t3;

        Family(String key, String nameZh, double t1, double t2, double t3) {
            this.key = key;
            this.nameZh = nameZh;
            this.t1 = t1;
            this.t2 = t2;
            this.t3 = t3;
        }

        public String key() {
            return key;
        }

        public String nameZh() {
            return nameZh;
        }

        /** 该族是否可以「校准」（阈值为 0 的族只进历史条）。 */
        public boolean calibratable() {
            return t1 > 0.0D;
        }

        /** 达到第 {@code tier}(1..3) 档所需占比；0 表示无此档。 */
        public double threshold(int tier) {
            return switch (tier) {
                case 1 -> t1;
                case 2 -> t2;
                case 3 -> t3;
                default -> 0.0D;
            };
        }

        /**
         * 该族是否已<b>并入别族</b>（隐藏列：只留常量保枚举序号/NBT 兼容，
         * 不再展示在校准条上、不再参与「哪一族生效」的判定）。
         *
         * <p>2026-09-21 队长令「第5个是合并成一条」⇒ {@link #SPECIAL} 并入 {@link #TEMPORAL}。
         */
        public boolean mergedAway() {
            return this == SPECIAL;
        }

        /**
         * 该族是否走「<b>特殊电路</b>」那套效果曲线（第 1 档<b>不减 EU</b>、三档时长 −20/−40/−50% 累计）。
         *
         * <p>★ 2026-09-21 队长令 + 设计文档 D-1（{@code _census/三族纳米配方_设计.md:70,:84}
         * 「校准族口径：<b>复用 SPECIAL</b>」）⇒ 奇异 / 寰宇 / 超因果三族按本曲线生效
         * （它们与 {@link #SPECIAL} 共用同一套效果；{@link #TEMPORAL} 还额外承载了并进来的
         * Pico/Quantum，所以那批的 −20/−40/−50% **不会消失**）。
         *
         * <p>⚠️ <b>与 NH 的有意分歧</b>：NH 里这三段阈值 0.2/0.4/0.6 且「效果为空」，
         * 我们按队长令改成 1%/5%/20% **并给效果**（不是漏判，见类注释）。
         */
        public boolean specialStyleEffects() {
            return this == SPECIAL || this == EXOTIC || this == COSMIC || this == TEMPORAL;
        }

        /**
         * ★ 2026-09-21 队长令「<b>第5个是合并成一条</b>」：<b>校准条上真正要画的族</b>。
         *
         * <p>= {@link #values()} 去掉 {@link #mergedAway()} 的那些 ⇒ 合并后是 <b>8 行</b>
         * （基础 / 晶体 / 湿件 / 生物 / 光学 / 奇异 / 寰宇 / <b>超因果</b>）。
         * 面板、以及任何"九段"遍历都必须走这里；<b>NBT 里存的族序号（★ 新格式 {@code RingRLE} 低 4 位 /
         * 旧档 {@code B<n>} 下标）与 {@code syncedFamilyCounts} 仍按 {@link #values()} 的 ordinal 下标</b>
         * （客户端 {@code syncedCountOf(ordinal)} 依赖它），两者别混。
         */
        public static Family[] barFamilies() {
            Family[] all = values();
            // 先数再建（不用 `all.length - 1`）：将来若再并掉一族，这里不会数组越界，
            // 而是自然少一行 —— 面板每帧都要读它，宁可不省这一次计数。
            int n = 0;
            for (Family f : all) {
                if (!f.mergedAway()) {
                    n++;
                }
            }
            Family[] out = new Family[n];
            int i = 0;
            for (Family f : all) {
                if (!f.mergedAway()) {
                    out[i++] = f;
                }
            }
            return out;
        }

        /** 按 id 猜族（NAC 记录产出电路时用）。 */
        public static Family of(ResourceLocation id) {
            if (id == null) {
                return PRIMITIVE;
            }
            String p = id.getPath().toLowerCase(Locale.ROOT);
            if (p.contains("pico") || p.contains("quantum")) {
                // ★ 2026-09-21 队长令「第5个是合并成一条」：原 SPECIAL 那一段并进「超因果」⇒
                //   Pico/Quantum 产出的归类、1%/5%/20% 阈值与时长 −20/−40/−50% 累计曲线
                //   全部由 TEMPORAL 接管（Family.specialStyleEffects()），**不会凭空消失**。
                return TEMPORAL;
            }
            if (p.contains("wetware")) {
                return WETWARE;
            }
            if (p.contains("bioware") || p.contains("bio_processor") || p.contains("bioprocessor")) {
                return BIOWARE;
            }
            if (p.contains("optical")) {
                return OPTICAL;
            }
            if (p.contains("crystal")) {
                return CRYSTAL;
            }
            if (p.contains("exotic")) {
                return EXOTIC;
            }
            if (p.contains("cosmic")) {
                return COSMIC;
            }
            if (p.contains("temporal") || p.contains("suprachronal") || p.contains("spacetime")
                    // ★ 2026-09-18：GTL 的第五族叫 **supracausal（超因果）**，原先漏了它 ⇒
                    //   三新族的 supracausal_* 电路会被判成 PRIMITIVE（校准条归类与倍率都会错）。
                    //   归 TEMPORAL —— 它就是 NH 校准条 9 段里 temporals 那一段在本包的对应族，
                    //   2026-09-21 起也承载并进来的 specials 那一段（显示名「超因果电路」）。
                    || p.contains("supracausal")) {
                return TEMPORAL;
            }
            return PRIMITIVE;
        }

        /** 按物品栈猜族。 */
        public static Family of(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return PRIMITIVE;
            }
            return of(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        }

        /** 进度条用的族颜色（原版那根多色条的分段配色思路）。 */
        public int color() {
            return switch (this) {
                case PRIMITIVE -> 0x9E9E9E;
                case CRYSTAL -> 0x7FE7FF;
                case WETWARE -> 0x8AE234;
                case BIOWARE -> 0xFF8FD8;
                case OPTICAL -> 0xFFD54F;
                case EXOTIC -> 0xB388FF;
                case COSMIC -> 0x536DFE;
                case TEMPORAL -> 0xFF6E40;
                case SPECIAL -> 0xFFFFFF;
            };
        }
    }

    // ---------- ★★ 2026-11-14【校准计数白名单】纯静态匹配工具（唯一调用者：NanochipAssemblyComplexMachine 的入口闸）★★ ----------

    /**
     * ★★ 2026-11-14【校准计数白名单】<b>白名单匹配</b>（纯静态、只读、无副作用）★★
     *
     * <h4>队长原话（两道令，逐字）</h4>
     * <blockquote>「<b>那就弄白名单，把他们的ID手动添加到校准的名册里，只有被允许的才能增加校准的数量</b>」<br>
     * 「<b>只有最终产出电路才算</b>」</blockquote>
     *
     * <h4>规则（与 {@code GTLModularConfig.NANOCHIP_CALIBRATION_RECIPE_WHITELIST} 的 comment 逐字对应）</h4>
     * <ol>
     *   <li>{@code id} 为 {@code null} ⇒ <b>不命中</b>（调用方另行限流打一行 debug；本方法不抛、不猜）；</li>
     *   <li>名册里的项 <b>以 {@code *} 结尾 ⇒ 前缀匹配</b>：
     *       {@code "abc*"} 命中 {@code "abcdef"}（判定用 {@code startsWith(去掉 * 之后的前缀)}）；</li>
     *   <li>名册里的项 <b>不以 {@code *} 结尾 ⇒ 精确匹配</b>：
     *       {@code "abc"} 只命中 {@code "abc"}，<b>不</b>命中 {@code "abcdef"}；</li>
     *   <li>哨兵项 {@code "*"}（只有一颗星、前缀为空）⇒ {@code startsWith("")} 恒真 ⇒ <b>全放行</b>
     *       （= 恢复"全部计入"，玩家可用它当开关，不必改代码）；</li>
     *   <li>{@code entries} 为 {@code null} 或空 ⇒ <b>不命中</b>（"一条都没登记"就一条都不计）
     *       —— ⚠ 注意：<b>配置项的"空列表"语义不在这里</b>，它在
     *       {@code GTLModularConfig.getNanochipCalibrationRecipeWhitelist()} 里已经被翻译成
     *       "回退到内置默认名单"（刻意不是全禁，理由见配置项 comment 第 4 条）；
     *       本方法只忠实执行"给我什么名册，就按什么名册判"，不做任何默认值兜底。</li>
     * </ol>
     *
     * <p>⚠️ <b>刻意不做</b>：不去 {@code #} 后缀、不做大小写折叠、不做命名空间补全。
     * 本模组这批配方的 id 是 {@code ResourceLocation} 的 {@code namespace:path} 形态
     * （{@link ResourceLocation#toString()} 恒为 {@code namespace:path}，例：
     * {@code NanochipCandidateSearch.java:147} 直接 {@code recipe.getId().toString()} 当排序键、
     * {@code NanochipConcurrentBatches.java:251} 把它原样存 NBT 再
     * {@code ResourceLocation.tryParse} 读回 ⇒ 逐字往返，没有 {@code #} 之类后缀）。
     * 若将来真有带后缀的 id 要放行，正确做法是在名册里<b>逐字写上那个后缀</b>（精确项），
     * 而不是在本方法里悄悄截断（截断 = 把两条不同的配方混成一条）。
     *
     * <p><b>一行回退</b>：删掉本方法 + {@code recordProduction} 里那段入口闸（+ 那两个限流游标字段）
     * ⇒ 逐字回到"全部计入"；或不删代码，把
     * {@code NanochipAssemblyComplexMachine.CALIBRATION_WHITELIST_ENABLED} 置 {@code false}。
     *
     * <p><b>本方法不参与任何判定</b>：不碰并行 / 准入 / 扣料 / 模式切换 / 族判定 / 数量口径 / 窗口与阈值，
     * 只回答"这个 id 在不在名册里"。
     *
     * @param id      配方 id（{@code GTRecipe.getId()}，可为 {@code null}）
     * @param entries 名册（{@code null}/空 = 不命中）；每项 {@code namespace:path}，或以 {@code *} 结尾的前缀项
     * @return {@code true} = 命中（允许计入校准）
     */
    public static boolean whitelistMatches(ResourceLocation id, Collection<String> entries) {
        if (id == null || entries == null || entries.isEmpty()) {
            return false;   // 边界①②⑤：无 id / 无名册 ⇒ 不命中（绝不猜）
        }
        String full = id.toString();   // 归一化成 `namespace:path`（ResourceLocation.toString 就是这个格式）
        for (String entry : entries) {
            if (entry == null || entry.isEmpty()) {
                continue;              // 空串项跳过（写错的一行不该把别的行带偏）
            }
            if (entry.endsWith("*")) {
                // 前缀项：去掉结尾那颗星再 startsWith（"*" 本身 ⇒ 前缀为空 ⇒ 恒真 = 全放行）
                if (full.startsWith(entry.substring(0, entry.length() - 1))) {
                    return true;
                }
            } else if (full.equals(entry)) {
                return true;           // 精确项：逐字相等才算
            }
        }
        return false;
    }

    // ---------- ★★ 2026-11-01【窗口表示重写】唯一事实源 = ring ★★ ----------
    //
    // 旧表示 int[BATCH_COUNT][族数] 已**整组删除**（batches / subtractBatch / recomputeAggregates /
    // cursor / cursorFill）—— 「最老的一整批 1000 条一起减掉」正是「占比上限 99.999%、100% 不可达」
    // 的根因（详见类注释 ①②）。现在只有一套真相源：ring。
    //
    // 为什么 familyTotals/totalAll 仍然保留（★ 2026-09-18 那条性能理由没变）：
    // 主机的 updateSyncedTexts() 是**每 tick** 跑的，一次要问 summary()/activeTier()/breakdown()/
    // 各族 countOf()/total()；若这些读数改成扫 ring，就是每 tick 每台 O(100,000) ⇒ 必卡。
    // 所以这两个量继续做**增量维护**（写入时同步加减），读数保持 O(1)；
    // 但它们是**派生量**，权威来源只有 ring —— load() 一律从 ring 重算（见 recountFamilyTotals()），
    // 绝不出现「两套并存的真相源」。

    /** 逐条记录所属族的环形缓冲（<b>唯一事实源</b>）：每格 = 该条记录所属族的 ordinal（0..15）。 */
    private final byte[] ring = new byte[HISTORY_SIZE];

    /**
     * 下一条要写入的槽位：满窗时它就是<b>当前最旧记录</b>的位置（逐条覆盖从这里开始）；
     * 窗口没满时恒等于 {@link #totalAll}（= 第一个空位的下标）。
     */
    private int ringPos;

    /** 窗口里的电路总数 = familyTotals 之和 = ring 的有效槽位数（0..HISTORY_SIZE）。 */
    private int totalAll;

    /** 历史里记录过的电路总数（封顶 HISTORY_SIZE）；与窗口内容无关的单调量（只进 NBT）。 */
    private int recorded;

    /** 各族在窗口里的数量（派生缓存；恒等于对 ring 的有效槽位逐格计数）。 */
    private final int[] familyTotals = new int[Family.values().length];

    /** 夹逼到 {@code [lo, hi]}（外部输入 NBT 一律先过这里）。 */
    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    /**
     * 从 ring 重算 {@link #familyTotals} 与 {@link #totalAll}（<b>读档是唯一调用者</b>）。
     *
     * <p>有效槽位的定义（与 record() 的写入顺序严格对应）：窗口没满
     * （{@code totalAll < HISTORY_SIZE}）时是 {@code [0, totalAll)} —— 记完即 {@code ringPos == totalAll}；
     * 满窗时是全部 {@code [0, HISTORY_SIZE)}。这条定义保证「重算出的 {@code totalAll}」与
     * 重算前给出的一致，从而 {@code totalAll == Σ familyTotals} 恒成立。
     *
     * <p>⚠️ 调用前必须保证 ring 里 {@code [0, totalAll)} 的每格都是合法族 ordinal
     * （{@link #decodeRingRle(int[])} / {@link #migrateLegacyBatches(CompoundTag)} 已经夹逼过）
     * —— 否则这里是唯一可能 {@code ArrayIndexOutOfBoundsException} 的地方。
     */
    private void recountFamilyTotals() {
        Arrays.fill(familyTotals, 0);
        int valid = clamp(totalAll, 0, HISTORY_SIZE);
        for (int i = 0; i < valid; i++) {
            familyTotals[ring[i]]++;
        }
        totalAll = valid;
    }

    /** RLE 低 4 位放族 ordinal（{@code Family} 只有 9 个常量，恒 < 16），高位放游程长度。 */
    private static final int RLE_SHIFT = 4;

    /** 族 ordinal 的位掩码。 */
    private static final int RLE_ORD_MASK = (1 << RLE_SHIFT) - 1;

    /**
     * 从 {@code from} 开始、与 {@code ring[from]} 取值相同的连续槽位的结束下标（不含）。
     * 返回值恒 {@code > from}（至少 1 格）⇒ 调用方用 {@code i = runEnd(i)} 递推不会死循环。
     */
    private int runEnd(int from) {
        byte v = ring[from];
        int j = from + 1;
        while (j < HISTORY_SIZE && ring[j] == v) {
            j++;
        }
        return j;
    }

    /**
     * 把整条 {@link #ring}（10 万字节）做游程编码：每个 int = {@code (runLength << 4) | familyOrdinal}。
     *
     * <p>体积：单族稳态只有 <b>1 个 int</b>（远小于旧格式 {@code B<n>} 稀疏批数组最多 100 个
     * 9 元素 int 数组）；混族时正比于「游程数」。{@code runLength ≤ 100000}，
     * {@code << 4} 后 1,600,000 < {@code Integer.MAX_VALUE} ⇒ 不溢出。
     *
     * <p>两遍扫描（先数游程再定长分配）：存档是<b>每次区块保存都跑</b>的，若直接开一个
     * {@code int[HISTORY_SIZE]} 当缓冲，单族稳态也要白白制造 400 KB 垃圾。
     */
    private int[] encodeRingRle() {
        int runs = 0;
        for (int i = 0; i < HISTORY_SIZE; i = runEnd(i)) {
            runs++;
        }
        int[] out = new int[runs];
        int n = 0;
        for (int i = 0; i < HISTORY_SIZE; i = runEnd(i)) {
            out[n++] = ((runEnd(i) - i) << RLE_SHIFT) | (ring[i] & RLE_ORD_MASK);
        }
        return out;
    }

    /**
     * 解 {@code RingRLE} 回 {@link #ring}。
     *
     * @return {@code true} = 干净解出且总长恰好 {@link #HISTORY_SIZE}；{@code false} = 坏档（调用方当空窗口处理）
     */
    private boolean decodeRingRle(int[] rle) {
        if (rle == null || rle.length == 0) {
            return false;
        }
        int pos = 0;
        for (int packed : rle) {
            int len = packed >>> RLE_SHIFT;
            int ord = packed & RLE_ORD_MASK;
            // ★ 坏档防线：len<=0（手改/错位）、ord 越出 Family 范围（会让重算时 familyTotals[...] 越界
            //   ⇒ ArrayIndexOutOfBoundsException 打断服务器 tick）、总长超窗 ⇒ 一律判坏档。
            if (len <= 0 || ord >= Family.values().length || pos + len > HISTORY_SIZE) {
                return false;
            }
            Arrays.fill(ring, pos, pos + len, (byte) ord);
            pos += len;
        }
        return pos == HISTORY_SIZE;
    }

    /**
     * ★ <b>旧档迁移</b>（存档里没有 {@code RingRLE} 键时走这里）：把旧的 {@code B<n>} 稀疏批数组
     * 搬进 {@link #ring}。
     *
     * <p>⚠️ <b>这是迁移近似，且只影响「顺序」、不影响「计数」</b>：旧档只存了「每批每族的计数」，
     * 压根没有「这一批里第几条是哪个族」的信息 ⇒ 唯一可行的搬法是<b>把每批的每族计数按族成块
     * 连续写进 ring</b>（第 0 批最旧，族按 ordinal 升序）。因此：
     * <ul>
     *   <li>{@code familyTotals} / {@code total()} 与旧档的逐批求和<b>严格相等</b>（一个数都不变）；</li>
     *   <li>只有「ring 槽位 → 记录先后」这一层是编造的（旧档没有该信息，任何实现都还原不出它），
     *       它只影响「哪一族先被覆盖」，即占比随新产出变化的先后，<b>不影响任何计数与档位判定</b>；</li>
     *   <li>旧档的窗口本来就只有 {@code 99,000 + cursorFill} 条（根因见类注释 ②）⇒ 迁移后
     *       {@code totalAll} 同样是这个数，之后每产出一条就精确补一格，<b>攒满 100,000 后 100% 可达</b>。</li>
     * </ul>
     * 外部输入的夹逼都在这里做：计数为负 ⇒ 当 0；总条数超窗 ⇒ 截断；数组长度不足 ⇒ 缺的当 0。
     */
    private void migrateLegacyBatches(CompoundTag tag) {
        int written = 0;
        for (int b = 0; b < BATCH_COUNT && written < HISTORY_SIZE; b++) {
            String key = "B" + b;
            if (!tag.contains(key)) {
                continue;
            }
            int[] arr = tag.getIntArray(key);
            int n = Math.min(arr.length, Family.values().length);
            for (int ord = 0; ord < n && written < HISTORY_SIZE; ord++) {
                int c = clamp(arr[ord], 0, HISTORY_SIZE - written);
                if (c <= 0) {
                    continue;
                }
                Arrays.fill(ring, written, written + c, (byte) ord);
                written += c;
            }
        }
        totalAll = written;
        // 满窗时 0 号槽就是「最旧」那一格（与全新满窗的约定一致：ringPos 指向最旧记录）。
        ringPos = written % HISTORY_SIZE;
        recountFamilyTotals();
    }

    // ---------- 记录 ----------

    /** 记录一个产出的电路（单条 ⇒ 直接委托批量版本，保证只有一处写入逻辑）。 */
    public void record(Family family) {
        record(family, 1);
    }

    /**
     * 批量记录（并行产出时用）。
     *
     * <p>★ 2026-11-01 起写入 {@link #ring}（逐条族序号），<b>窗口恒等于「最近 {@link #HISTORY_SIZE} 条」</b>：
     * <ol>
     *   <li><b>快路</b>：{@code amount >= HISTORY_SIZE} ⇒ 整窗必将全是该族 ⇒
     *       {@code Arrays.fill} 一次性搞定，O(HISTORY_SIZE) 但只有两次 fill。
     *       <b>这条必须有</b>：一次 {@code Integer.MAX_VALUE} 的调用（并行无限时的真实输入）
     *       若走逐格循环就是 21 亿次迭代 ⇒ 卡服；</li>
     *   <li><b>慢路</b>（{@code amount < HISTORY_SIZE}，循环量 ≤ 10 万）：
     *       先填空位（窗口没满时），再逐条覆盖最旧 —— 覆盖时对<b>被顶掉那一格所属的族精确减 1</b>，
     *       不做任何按比例近似。</li>
     * </ol>
     *
     * <p>② 段只可能在 {@code totalAll == HISTORY_SIZE} 时进入 ⇒ 此时<b>每一格都是有效记录</b>，
     * {@code familyTotals} 不会被减成负数（这是「不变量」而不是「恰好」：窗口没满时
     * {@code space > 0} 且 {@code put = min(space, remaining)} 一旦把窗口填满就 {@code remaining == 0}）。
     */
    public void record(Family family, int amount) {
        if (family == null || amount <= 0) {
            return;
        }
        // ★ 用 long 相加再夹逼：amount 可达 Integer.MAX_VALUE，int 加法会回绕成负数
        //   ⇒ 会污染「已记录数」。这行在快路之前执行（快路同样要累加 recorded）。
        recorded = (int) Math.min((long) HISTORY_SIZE, (long) recorded + (long) amount);
        final int ord = family.ordinal();
        // ---- 快路：一次写入量 ≥ 整窗 ⇒ 整窗恒等于该族，O(1)（绝不能退化成 21 亿次循环）----
        if (amount >= HISTORY_SIZE) {
            Arrays.fill(ring, (byte) ord);
            Arrays.fill(familyTotals, 0);
            familyTotals[ord] = HISTORY_SIZE;
            totalAll = HISTORY_SIZE;
            // 整窗同族 ⇒ 0 号槽就是最旧那一格，下一条写这里。
            ringPos = 0;
            return;
        }
        // ---- 慢路（amount < HISTORY_SIZE ⇒ 循环量 ≤ 10 万，可接受）----
        int remaining = amount;
        // ① 先填空位：窗口没满时空位恒为 [ringPos, ringPos + space)（且 ringPos == totalAll）。
        //    这里仍按「可能回绕」写（span = 到数组末尾的距离），这样即使不变量被外部破坏
        //    也不会越界；正常输入下 span == put，第二段 fill 永不执行。
        int space = HISTORY_SIZE - totalAll;
        if (space > 0) {
            int put = Math.min(space, remaining);
            int span = Math.min(put, HISTORY_SIZE - ringPos);
            Arrays.fill(ring, ringPos, ringPos + span, (byte) ord);
            if (put > span) {
                Arrays.fill(ring, 0, put - span, (byte) ord);
            }
            familyTotals[ord] += put;
            totalAll += put;
            ringPos = (ringPos + put) % HISTORY_SIZE;
            remaining -= put;
        }
        // ② 窗口已满：逐条覆盖最旧。★ 精确减回（不做任何近似）——每个被覆盖的槽位都属于一条真实记录。
        while (remaining > 0) {
            int span = Math.min(remaining, HISTORY_SIZE - ringPos);
            for (int i = 0; i < span; i++) {
                familyTotals[ring[ringPos + i]]--;
            }
            Arrays.fill(ring, ringPos, ringPos + span, (byte) ord);
            familyTotals[ord] += span;
            ringPos = (ringPos + span) % HISTORY_SIZE;
            remaining -= span;
        }
    }

    /**
     * 各族在历史窗口里的数量（= 最近 {@link #HISTORY_SIZE} 条记录里该族的条数）。
     *
     * <p>★【2026-09-18 性能】读 O(1) 增量缓存 {@link #familyTotals}（不要改成扫 ring：每 tick 调用）。
     * ★【2026-11-01 表示重写】缓存的<b>权威来源</b>是 {@link #ring}：缓存在
     * {@code record(Family,int)} 写入时同步加减、在 {@code load()} 里由
     * {@link #recountFamilyTotals()} 从 ring 重算 ⇒ 恒等于「对 ring 有效槽位逐格计数」，
     * 也恒等于 {@code totalAll} 的构成分量。
     */
    public int countOf(Family family) {
        return familyTotals[family.ordinal()];
    }

    /**
     * 历史窗口里的电路总数 = 各族之和 = ring 的有效槽位数（0..{@link #HISTORY_SIZE}）。
     *
     * <p>★【2026-09-18 性能】读 O(1) 增量缓存 {@link #totalAll}。
     * ★【2026-11-01】窗口恒等于「最近 100,000 条记录」⇒ 产满 10 万条同族电路后本值 = 100000，
     * 固定分母下占比即可达 <b>100%</b>（旧表示的上限是 99.999%）。
     */
    public int total() {
        return totalAll;
    }

    // ---------- 判定分母（原版硬编码 100000 / 可切回「已记录数」） ----------

    /** 是否使用原版那套分母（分母固定 = 整个历史窗口 100000）。 */
    public static boolean useFullWindowDenominator() {
        return GTLModularConfig.isNanochipCalibrationUseFullWindow();
    }

    /**
     * 判定分母：{@code fullWindow = true} 时是原版硬编码的 {@link #HISTORY_SIZE}（100000），
     * 否则是窗口里当前已记录的数量。
     *
     * <p>静态版本供客户端用（客户端手上只有同步下来的「已记录数」，没有环形缓冲本体）。
     */
    public static double denominatorFor(long historyTotal, boolean fullWindow) {
        return fullWindow ? (double) HISTORY_SIZE : (double) Math.max(0L, historyTotal);
    }

    /** 当前判定分母（服务端权威）。 */
    public double denominator() {
        return denominatorFor(total(), useFullWindowDenominator());
    }

    /** 当前分母模式的界面文案 key（客户端按 lang 解析）。 */
    public static String denominatorModeKey(boolean fullWindow) {
        return fullWindow
                ? "gtlmodular.nanochip.calibration.denominator.vanilla"
                : "gtlmodular.nanochip.calibration.denominator.recorded";
    }

    /**
     * 该族占判定分母的比例（0..1）。
     *
     * <p>分母口径见 {@link #denominator()}：★ 2026-09-21 队长令「改」后默认是「已记录数」；
     * 切成原版口径时分母固定 100000（第一档 20% = 20000 个电路）。
     * <b>阈值与档位判定逻辑本身不随分母改动</b>。
     */
    public double percentage(Family family) {
        double den = denominator();
        return den <= 0.0D ? 0.0D : countOf(family) / den;
    }

    /** 该族当前的校准档位（0 = 未校准，1..3）。 */
    public int tierOf(Family family) {
        if (!family.calibratable()) {
            return 0;
        }
        double pct = percentage(family);
        int tier = 0;
        for (int t = 1; t <= 3; t++) {
            if (family.threshold(t) > 0.0D && pct >= family.threshold(t)) {
                tier = t;
            }
        }
        return tier;
    }

    /** 当前生效的校准（档位最高的那一个；同档取数量多的）。 */
    public Family activeFamily() {
        Family best = null;
        int bestTier = 0;
        int bestCount = -1;
        for (Family f : Family.values()) {
            if (f.mergedAway()) {
                // ★ 2026-09-21：已并入「超因果」的隐藏列（SPECIAL）不再参与"哪一族生效"的判定。
                continue;
            }
            int t = tierOf(f);
            if (t <= 0) {
                continue;
            }
            int c = countOf(f);
            if (t > bestTier || (t == bestTier && c > bestCount)) {
                best = f;
                bestTier = t;
                bestCount = c;
            }
        }
        return best;
    }

    /** 当前生效档位（0 = 无校准）。 */
    public int activeTier() {
        Family f = activeFamily();
        return f == null ? 0 : tierOf(f);
    }

    // ---------- 增益 ----------

    /**
     * 第 1 档：所有模块 EU ×0.8。
     *
     * <p><b>必须排除特殊电路</b>：原版只有晶体 / 湿件 / 生物 / 光学 四族的第 1 档写的是
     * {@code 0.8x EU Multiplier to all Modules}；special 的第 1 档**只有**时长减免（−20%），
     * 没有任何 EU 减免 —— 见 {@code GT5U.gui.text.nac.info.calibration.special.effect1/2/3}。
     * 早期实现只判 {@code activeTier() >= 1}，等于给特殊电路白送一份 ×0.8 EU（2026-09-14 对照原版时修正）。
     *
     * <p>★ 2026-09-21：奇异 / 寰宇 / 超因果三族按 D-1「复用 SPECIAL」也算"特殊曲线"
     * （{@link Family#specialStyleEffects()}）⇒ 它们同样<b>不吃这份 EU 减免</b>。
     */
    public double euMultiplier() {
        Family active = activeFamily();
        if (active == null || active == Family.SPECIAL || active.specialStyleEffects()) {
            return 1.0D;
        }
        return tierOf(active) >= 1 ? 0.8D : 1.0D;
    }

    /**
     * 第 2 档：该族电路时长 ×0.8；「特殊曲线」族的 3 档是累计式
     * （−20% → −40% → −50%，见 GTNH 原文）。
     *
     * <p>★ 2026-09-21：奇异 / 寰宇 / 超因果三族走同一条累计曲线（D-1「复用 SPECIAL」）；
     * 并进超因果的原 Pico/Quantum 因此**保留**它们原本的 −20/−40/−50%。
     */
    public double durationMultiplier(Family recipeFamily) {
        Family active = activeFamily();
        if (active == null || recipeFamily == null || active != recipeFamily) {
            return 1.0D;
        }
        int tier = tierOf(active);
        if (active == Family.SPECIAL || active.specialStyleEffects()) {
            return switch (tier) {
                case 1 -> 0.80D;
                case 2 -> 0.60D;
                case 3 -> 0.50D;
                default -> 1.0D;
            };
        }
        return tier >= 2 ? 0.8D : 1.0D;
    }

    /** 第 3 档是否生效于指定族（各族专属特效的开关）。 */
    public boolean specialActive(Family family) {
        return activeFamily() == family && tierOf(family) >= 3;
    }

    // ---------- 界面文案 ----------

    /** 一行摘要（同步到客户端显示）。 */
    public String summary() {
        Family active = activeFamily();
        if (active == null) {
            return "未校准 · 历史 " + total() + " / " + HISTORY_SIZE + " 个电路";
        }
        return active.nameZh() + " 第 " + tierOf(active) + " 档 · 占比 "
                + String.format(Locale.ROOT, "%.2f", percentage(active) * 100.0D) + "%";
    }

    /** 各族占比明细（进度条文本）。 */
    public String breakdown() {
        StringBuilder sb = new StringBuilder();
        for (Family f : Family.values()) {
            if (f.mergedAway()) {
                // ★ 2026-09-21：合并后条上不再有「特殊电路」那一行 ⇒ 明细里也不列它。
                continue;
            }
            int c = countOf(f);
            if (c <= 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("｜");
            }
            sb.append(f.nameZh()).append(' ')
                    .append(String.format(Locale.ROOT, "%.2f", percentage(f) * 100.0D)).append('%');
        }
        return sb.toString();
    }

    /**
     * 当前批次进度（0..1）—— 语义<b>不变</b>：仍是「当前批已填比例」。
     *
     * <p>★ 2026-11-01：新表示里没有 {@code cursorFill} 这个字段了，
     * 「当前批」由 {@code ringPos % BATCH_SIZE} 给出（{@link #HISTORY_SIZE} 恒是
     * {@link #BATCH_SIZE} 的整数倍 ⇒ {@code ringPos} 跨批边界与旧批节奏一致）。
     */
    public double batchProgress() {
        return (ringPos % BATCH_SIZE) / (double) BATCH_SIZE;
    }

    // ---------- ★★ 0.3.132【重构 · 重置】★★ ----------

    /**
     * ★★ 0.3.132【队长令：校准必须能"重置"】★★ —— <b>清空整个历史窗口</b>（所有族的档位/占比一并归零）。
     *
     * <h4>为什么必须是一个显式方法（而不是"拆机器就没了"）</h4>
     * 队长原话：「即便是把主机拆掉重放，里面的校准也依然会保存着，这样的话，那玩家就算再放一台新的，
     * 也无法去把校准给重置掉了」「只清这一台机器」。
     * 本类原本<b>没有任何</b> reset/clear 入口（全类无 reset/clear，命令只有只读自检，配置项只换分母）
     * ⇒ 这是**功能缺失**，本方法把它补上；归属仍是<b>控制器实例</b>（只清这一台，不碰别台、不碰存档键）。
     *
     * <h4>★ 必须连 ring 一起清（最容易写错的一点）</h4>
     * {@link #ring} 是窗口的<b>唯一事实源</b>，{@code familyTotals[]}/{@code totalAll} 只是它的
     * O(1) 派生缓存。若只清缓存、不清 ring，等 {@code ringPos} 绕回来，逐条覆盖那段会把
     * 被顶掉那一格所属的族从已归零的缓存里<b>减掉</b> ⇒ <b>减出负数</b>、占比变负、档位判定错乱。
     * ⇒ 这里 <b>{@code Arrays.fill(ring, (byte) 0)}</b>，并把 {@code ringPos}/{@code totalAll}/
     * {@code recorded} 同时归零。
     *
     * <h4>不变量（改后仍成立）</h4>
     * {@code total() == Σ familyTotals[]}、{@code total() == ring 的有效槽位数}、
     * {@code recorded == min(累计写入, HISTORY_SIZE)}；清空后 {@code ringPos == totalAll == 0}。
     *
     * <h4>纪律</h4>
     * <ul>
     *   <li><b>只清窗口</b>：不动窗口容量/批次数/族集合/阈值/三档累积关系（守本类顶部那两条口径纪律）；</li>
     *   <li><b>不动 {@code SPECIAL} 枚举常量与 ordinal 语义</b>（NBT 里的族序号靠它们）；</li>
     *   <li><b>纯服务端动作</b>：调用方负责"清完立刻重刷同步"，否则界面最多停 60 tick 显示旧值（等于撒谎）。</li>
     * </ul>
     *
     * @return 清空前的历史总电路数（给回执/日志用；调用后本对象已归零）
     */
    public int clear() {
        int before = totalAll;
        // ① 清 ring（**不能省**：它是唯一事实源，缓存是派生的）
        Arrays.fill(ring, (byte) 0);
        // ② 派生缓存归零
        Arrays.fill(familyTotals, 0);
        totalAll = 0;
        recorded = 0;
        // ③ 写入位置归零（从"最旧的一格"重新开始记）
        ringPos = 0;
        return before;
    }

    // ---------- 存档 ----------

    /**
     * 写入 NBT。
     *
     * <p>格式（★ 2026-11-01）：
     * <ul>
     *   <li><b>旧键名保留</b>（别的代码/工具/回退版本仍按它们读）：
     *       {@code Cursor} = {@code ringPos}、{@code Fill} = {@code ringPos % BATCH_SIZE}、
     *       {@code Recorded} = {@code recorded}；</li>
     *   <li><b>新键</b>：{@code Total} = {@code totalAll}、{@code RingRLE} = {@code ring} 的游程编码
     *       （{@code int[]}，每个 int = {@code (runLength << 4) | familyOrdinal}）。</li>
     * </ul>
     * 体积：单族稳态 {@code RingRLE} 只有 1 个 int；旧格式的 {@code B<n>} 稀疏批数组（最多 100 个
     * 9 元素数组）<b>不再写</b>。
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Cursor", ringPos);
        tag.putInt("Fill", ringPos % BATCH_SIZE);
        tag.putInt("Recorded", recorded);
        tag.putInt("Total", totalAll);
        tag.putIntArray("RingRLE", encodeRingRle());
        return tag;
    }

    /**
     * 从 NBT 读取（读档 = <b>全量替换</b>）。
     *
     * <p>★ 2026-11-01 两条路径 + 一律夹逼（NBT 是<b>外部输入</b>：旧档 / 手改档 / 坏档）：
     * <ol>
     *   <li><b>有 {@code RingRLE}</b>：解回 {@link #ring}，按 {@code Total}/{@code Cursor}/{@code Recorded}
     *       夹逼后<b>从 ring 重算</b> totals（不信任存档里的数）；
     *       解不出（长度不符 / 游程总长 ≠ {@link #HISTORY_SIZE} / ordinal 越界）⇒ <b>空窗口</b>：</li>
     *   <li><b>没有 {@code RingRLE}</b>（旧档）⇒ {@link #migrateLegacyBatches(CompoundTag)}
     *       把旧的 {@code B<n>} 按族成块搬进 ring（迁移近似只影响顺序、不影响计数，见该方法注释）。</li>
     * </ol>
     * 两条路径都保证：{@code ringPos ∈ [0, HISTORY_SIZE)}、{@code totalAll ∈ [0, HISTORY_SIZE]}、
     * {@code recorded ∈ [0, HISTORY_SIZE]}、{@code totalAll == Σ familyTotals}、
     * 「窗口没满 ⇒ {@code ringPos == totalAll}」；<b>绝不越界、绝不卡服</b>。
     * 对**本版本写出的合法存档**逐位等价（{@code Total}/{@code Cursor} 本就在合法区间内）。
     */
    public void load(CompoundTag tag) {
        // ① 先整体置成「空窗口」—— 读档是全量替换，任何失败路径的落点都是它。
        Arrays.fill(ring, (byte) 0);
        Arrays.fill(familyTotals, 0);
        totalAll = 0;
        ringPos = 0;
        recorded = 0;

        // ② 外部输入一律夹逼
        recorded = clamp(tag.getInt("Recorded"), 0, HISTORY_SIZE);
        if (tag.contains("RingRLE")) {
            if (decodeRingRle(tag.getIntArray("RingRLE"))) {
                totalAll = clamp(tag.getInt("Total"), 0, HISTORY_SIZE);
                ringPos = clamp(tag.getInt("Cursor"), 0, HISTORY_SIZE - 1);
                // ★ 不变量修复：窗口没满时有效区恒为 [0, totalAll) ⇒ 写入点只能是 totalAll
                //   （手改档里对不上的 Cursor 在这里被纠正，而不是留着一个会算错的位置）。
                if (totalAll < HISTORY_SIZE) {
                    ringPos = totalAll;
                }
                recountFamilyTotals();
            }
            // 解不出 ⇒ 保持空窗口（Total/Cursor 一个都不信，只留夹逼过的 recorded）。
            return;
        }
        // ③ 旧档迁移（没有 RingRLE 键）
        migrateLegacyBatches(tag);
    }
}
