package com.gtlmodular.common.data.woodfactory;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.HatchPredicates;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * 集成木质生物质工厂（{@code gtlmodular:wood_biomass_factory}）的结构 —— GTO
 * {@code gtocore:wood_distillation}（中文名「木化工厂」）的 <b>1:1 照搬</b>。
 *
 * <h2>取证</h2>
 * <ul>
 *   <li>结构文件：GTO jar 内 {@code pattern/wood_distillation.mbs}
 *       （3,093 B，gzip 解开 100,870 B，<b>0 字节剩余</b> = 解析完整）。</li>
 *   <li>格式：{@code FF FF FF FF | 0F | ver=1 | charDir=2(LEFT) | stringDir=0(UP) | aisleDir=4(FRONT) |
 *       LEB(aisleCount=60) | LEB(stringsPerAisle=56)}，随后每行 {@code [1 字节行宽][字符]}，
 *       片尾插过道标记（值 56）；解析器 {@code _port/tools/parse_mbs.py}。</li>
 *   <li>尺寸：<b>28 (X) × 56 (Y) × 60 (Z) = 94,080 格</b>，实心 <b>10,400 格</b>
 *       （空气 83,680；控制器 {@code Y} 1 格）。</li>
 *   <li>朝向：<b>直转，不做任何轴颠倒</b>（charDir=LEFT / stringDir=UP / aisleDir=FRONT 与
 *       GTCEu 1.4.4 {@code FactoryBlockPattern.start()} 的默认轴完全一致；与奈亚拉托提普之触 /
 *       高维等离子聚变核心同一套字节码取证）。第 1 片 = 最背面（Z=0），第 60 片 = 正面（Z=59）；
 *       每片第 1 行 = 最低层（Y=0）；每行第 1 个字符 = 机器左手边（X 最小侧）。</li>
 *   <li>谓词绑定：GTO {@code MultiBlockB.java:74} 的 {@code WOOD_DISTILLATION}
 *       （{@code CoilCrossRecipeMultiblockMachine::createCoilParallel}）。</li>
 *   <li>控制器位置（mbs 坐标 → 本文件下标）：{@code [Z=58][Y=2][X=22]}，即控制器本体位于
 *       整机<b>正面偏下的控制舱</b>里（舱口朝 +Z）。</li>
 * </ul>
 *
 * <h2>符号表（GTO → 我方；★ = 新增方块，见 {@link WoodFactoryBlocks}）</h2>
 * <table border="1">
 *   <caption>26 个符号位 + 空格；格数取本文件实测</caption>
 *   <tr><th>符号</th><th>格数</th><th>GTO</th><th>我方</th></tr>
 *   <tr><td>{@code Y}</td><td>1</td><td>控制器</td><td>{@code gtlmodular:wood_biomass_factory}</td></tr>
 *   <tr><td>{@code C}</td><td>3476</td><td>压力容器机械方块</td>
 *       <td>★{@code gtlmodular:pressure_containment_casing}</td></tr>
 *   <tr><td>{@code A}</td><td>1816</td><td>应力结构外壳（GCYM）</td><td>{@code gtceu:stress_proof_casing}</td></tr>
 *   <tr><td>{@code G}</td><td>608</td><td>水密外壳（GCYM）</td><td>{@code gtceu:watertight_casing}</td></tr>
 *   <tr><td>{@code B}</td><td>603</td><td>抗氧化哈氏合金-N 机械方块</td>
 *       <td>{@code gtlcore:oxidation_resistant_hastelloy_n_mechanical_casing}</td></tr>
 *   <tr><td>{@code M}</td><td>601</td><td>防腐外壳（GCYM）</td><td>{@code gtceu:corrosion_proof_casing}</td></tr>
 *   <tr><td>{@code F}</td><td>432</td><td>不锈钢框架</td><td>{@code gtceu:stainless_steel_frame}</td></tr>
 *   <tr><td>{@code J}</td><td>416</td><td>惰性聚四氟乙烯外壳</td><td>{@code gtceu:inert_machine_casing}</td></tr>
 *   <tr><td>{@code E}</td><td>396</td><td>钌铱合金框架（Ruridit）</td><td>{@code gtceu:ruridit_frame}</td></tr>
 *   <tr><td>{@code D}</td><td>324</td><td>铱机械方块</td><td>{@code gtlcore:iridium_casing}</td></tr>
 *   <tr><td>{@code N}</td><td>302</td><td>哈氏合金-N75 油膜管道</td>
 *       <td>★{@code gtlmodular:hastelloy_n_75_pipe}</td></tr>
 *   <tr><td>{@code T}</td><td>269</td><td>加工机械方块</td><td>{@code gtlcore:process_machine_casing}</td></tr>
 *   <tr><td>{@code L}</td><td>192</td><td>硅岩合金框架</td><td>{@code gtceu:naquadah_alloy_frame}</td></tr>
 *   <tr><td>{@code K}</td><td>192</td><td>任意加热线圈</td><td>{@code Predicates.heatingCoils()}</td></tr>
 *   <tr><td>{@code S}</td><td>141</td><td>钨钢管道外壳</td><td>{@code gtceu:tungstensteel_pipe_casing}</td></tr>
 *   <tr><td>{@code O}</td><td>133</td><td>电解槽（GCYM）</td><td>{@code gtceu:electrolytic_cell}</td></tr>
 *   <tr><td>{@code I}</td><td>122</td><td>ZPM 气密外壳</td><td>{@code gtceu:zpm_hermetic_casing}</td></tr>
 *   <tr><td>{@code V}</td><td>92</td><td>油气输送密封管道机械方块</td>
 *       <td>★{@code gtlmodular:oil_gas_transportation_pipe_casing}</td></tr>
 *   <tr><td>{@code W}</td><td>56</td><td>聚四氟乙烯管道外壳</td><td>{@code gtceu:ptfe_pipe_casing}</td></tr>
 *   <tr><td>{@code U}</td><td>41</td><td>耐高压管道机械方块</td>
 *       <td>★{@code gtlmodular:high_pressure_pipe_casing}</td></tr>
 *   <tr><td>{@code P}</td><td>39</td><td>耐化学腐蚀管道机械方块</td>
 *       <td>★{@code gtlmodular:chemical_corrosion_resistant_pipe_casing}</td></tr>
 *   <tr><td>{@code H}</td><td>35</td><td>聚变玻璃</td><td>{@code gtceu:fusion_glass}</td></tr>
 *   <tr><td>{@code X}</td><td>20</td><td>高能机壳 + 激光仓 + 维护仓</td>
 *       <td>{@code gtceu:high_power_casing} + IO / 能源 / 激光 / 维护 舱面</td></tr>
 *   <tr><td>{@code R}</td><td>15</td><td>钢涡轮外壳</td><td>{@code gtceu:steel_turbine_casing}</td></tr>
 *   <tr><td>{@code Q}</td><td>7</td><td>消音仓</td><td>{@code PartAbility.MUFFLER}</td></tr>
 *   <tr><td>{@code a/b/t/d}</td><td>71</td><td>GTO 附属模块口（原为小写位，见下）</td>
 *       <td>同名方块 + 线程仓 / 超频仓 / 加速仓 / 并行仓 舱面</td></tr>
 * </table>
 *
 * <h2>★ 附属模块口（GTO 的 module 口 → 本模组的可装仓舱面）</h2>
 * <p>GTO 原机在控制器背后挂着 {@code pattern/wood_dist_crossrecipe.mbs} 这个<b>可拆卸附属模块</b>
 * （结构谓词里 {@code .port('Y', StructureModuleKeys.MODULE_OUT, BACK)} +
 * {@code .atPort(MODULE_OUT, Slot.optional(piece("wood_dist_crossrecipe", ...)))}，
 * 模块里才出现 {@code a/b/c/d/f/g/h/i/j/k/l/m/n/o} 这一批小写位）。
 * <b>本模组没有 GTO 的模块框架</b>（{@code StructureModuleKeys} / {@code Slot} / {@code Piece}
 * 都是 GTO 魔改 GTCEu 的东西），所以按队长口径把「模块口」实现成
 * <b>主结构正面那两片（Z=58/59）控制舱的 71 格舱面</b>：
 * 这两片里的 {@code A/T/B/D} 在数据里改写成小写 {@code a/t/d/b}，谓词在原方块之外再开放
 * <b>线程仓（≤1）/ 超频仓 / 加速仓 / 并行仓（≤1）/ 物品-流体的进出仓</b>。
 * 结构与方块一格未动，只是那 71 格多允许插仓 —— 也就是「模块口」。
 *
 * <h2>仓面口径（GTO 原机只有 X 位可装仓）</h2>
 * <p>{@code X}（20 格）：物品/流体进出、能源输入（≤8）、激光输入（≤4）、维护仓（恰好 1）——
 * 对应 GTO 的 {@code autoLaserAbilities(recipeTypes)} + {@code MAINTENANCE}。
 * 线程仓 / 超频仓只挂在模块口（GTO 原机的 {@code d} 位语义）。
 *
 * <p><b>方块解析时机</b>：{@code .pattern(...)} 在注册期只登记 memoize 的 supplier，{@link #build}
 * 到<b>第一次结构判定 / 首次打开 JEI 预览</b>才执行 —— 那时所有模组方块都已就位。
 */
public final class WoodFactoryStructures {

    /** X 跨度：每行 28 个字符。 */
    public static final int WIDTH = 28;
    /** Y 跨度：每片 56 行。 */
    public static final int HEIGHT = 56;
    /** GTO 模块并入后的升级结构维度。 */
    public static final int UPGRADED_WIDTH = 29;
    public static final int UPGRADED_LENGTH = 61;
    /** Z 跨度：共 60 片。 */
    public static final int LENGTH = 60;

    private WoodFactoryStructures() {
    }

    /** 结构数据（{@code [z][y]} 的 28 字符串），给自检与预览脚本用。 */
    public static String[][] slices() {
        return SLICES;
    }

    private static final String[][] SLICES = {
            { // Z=0
                    "    AAAAA                   ", // Y=0
                    "                            ", // Y=1
                    "                            ", // Y=2
                    "                            ", // Y=3
                    "                            ", // Y=4
                    "                            ", // Y=5
                    "                            ", // Y=6
                    "                            ", // Y=7
                    "                            ", // Y=8
                    "                            ", // Y=9
                    "                            ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=1
                    "  AAAAAAAAA                 ", // Y=0
                    "    AAAAA                   ", // Y=1
                    "                            ", // Y=2
                    "                            ", // Y=3
                    "                            ", // Y=4
                    "                            ", // Y=5
                    "     BBB                    ", // Y=6
                    "                            ", // Y=7
                    "                            ", // Y=8
                    "                            ", // Y=9
                    "                            ", // Y=10
                    "     BBB                    ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "     BBB                    ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "     BBB                    ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "     BBB                    ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "     BBB                    ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "     BBB                    ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "     BBB                    ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "     BBB                    ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=2
                    " AAAAAAAAAAA    AAAAAAAAAAA ", // Y=0
                    "  AAACCCAAA     AAAAAAAAAAA ", // Y=1
                    "  E  CCC  E                 ", // Y=2
                    "  E  CCC  E                 ", // Y=3
                    "  E  CCC  E        FTTTF    ", // Y=4
                    "  E  CCC  E                 ", // Y=5
                    "  EBBCCCBBE                 ", // Y=6
                    "  E  CCC  E        FTTTF    ", // Y=7
                    "  E  CCC  E                 ", // Y=8
                    "  E  CCC  E                 ", // Y=9
                    "  E  CCC  E        FTTTF    ", // Y=10
                    "  EBBCCCBBE                 ", // Y=11
                    "  E  CCC  E                 ", // Y=12
                    "  E  CCC  E                 ", // Y=13
                    "  E  CCC  E                 ", // Y=14
                    "  E  CCC  E                 ", // Y=15
                    "  EBBCCCBBE                 ", // Y=16
                    "  E  CCC  E                 ", // Y=17
                    "  E  CCC  E                 ", // Y=18
                    "  E  CCC  E                 ", // Y=19
                    "  E  CCC  E                 ", // Y=20
                    "  EBBCCCBBE                 ", // Y=21
                    "  E  CCC  E                 ", // Y=22
                    "  E  CCC  E                 ", // Y=23
                    "  E  CCC  E                 ", // Y=24
                    "  E  CCC  E                 ", // Y=25
                    "  EBBCCCBBE                 ", // Y=26
                    "  E  CCC  E                 ", // Y=27
                    "  E  CCC  E                 ", // Y=28
                    "  E  CCC  E                 ", // Y=29
                    "  E  CCC  E                 ", // Y=30
                    "  EBBCCCBBE                 ", // Y=31
                    "  E  CCC  E                 ", // Y=32
                    "  E  CCC  E                 ", // Y=33
                    "  E  CCC  E                 ", // Y=34
                    "  E  CCC  E                 ", // Y=35
                    "  EBBCCCBBE                 ", // Y=36
                    "  E  CCC  E                 ", // Y=37
                    "  E  CCC  E                 ", // Y=38
                    "  E  CCC  E                 ", // Y=39
                    "  E  CCC  E                 ", // Y=40
                    "  EBBCCCBBE                 ", // Y=41
                    "  E  CCC  E                 ", // Y=42
                    "  E  CCC  E                 ", // Y=43
                    "  E  CCC  E                 ", // Y=44
                    "  E  CCC  E                 ", // Y=45
                    "  EBBCCCBBE                 ", // Y=46
                    "  E  CCC  E                 ", // Y=47
                    "  E  CCC  E                 ", // Y=48
                    "     CCC                    ", // Y=49
                    "     CCC                    ", // Y=50
                    "     CCC                    ", // Y=51
                    "      C                     ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=3
                    " AAAAAAAAAAA    AAAAAAAAAAA ", // Y=0
                    "  AACIIICAA     AAAACCCAAAA ", // Y=1
                    "   CC   CC        L CCC L   ", // Y=2
                    "   CC   CC        L CCC L   ", // Y=3
                    "   CC   CC        LTCCCTL   ", // Y=4
                    "   CC G CC        L CCC L   ", // Y=5
                    "  BCCMNMCCB       L CCC L   ", // Y=6
                    "   CC   CC        LTCCCTL   ", // Y=7
                    "   CC   CC        L CCC L   ", // Y=8
                    "   CC   CC        L CCC L   ", // Y=9
                    "   CC G CC        LTCCCTL   ", // Y=10
                    "  BCCMNMCCB       L CCC L   ", // Y=11
                    "   CC   CC        L CCC L   ", // Y=12
                    "   CC   CC          CCC     ", // Y=13
                    "   CC   CC                  ", // Y=14
                    "   CC G CC                  ", // Y=15
                    "  BCCMNMCCB                 ", // Y=16
                    "   CC   CC                  ", // Y=17
                    "   CC   CC                  ", // Y=18
                    "   CC   CC                  ", // Y=19
                    "   CC G CC                  ", // Y=20
                    "  BCCMNMCCB                 ", // Y=21
                    "   CC   CC                  ", // Y=22
                    "   CC   CC                  ", // Y=23
                    "   CC   CC                  ", // Y=24
                    "   CC G CC                  ", // Y=25
                    "  BCCMNMCCB                 ", // Y=26
                    "   CC   CC                  ", // Y=27
                    "   CC   CC                  ", // Y=28
                    "   CC   CC                  ", // Y=29
                    "   CC G CC                  ", // Y=30
                    "  BCCMNMCCB                 ", // Y=31
                    "   CC   CC                  ", // Y=32
                    "   CC   CC                  ", // Y=33
                    "   CC   CC                  ", // Y=34
                    "   CC G CC                  ", // Y=35
                    "  BCCMNMCCB                 ", // Y=36
                    "   CC   CC                  ", // Y=37
                    "   CC   CC                  ", // Y=38
                    "   CC   CC                  ", // Y=39
                    "   CC G CC                  ", // Y=40
                    "  BCCMNMCCB                 ", // Y=41
                    "   CC   CC                  ", // Y=42
                    "   CC   CC                  ", // Y=43
                    "   CC   CC                  ", // Y=44
                    "   CC G CC                  ", // Y=45
                    "  BCCMNMCCB                 ", // Y=46
                    "   CC   CC                  ", // Y=47
                    "   CC   CC                  ", // Y=48
                    "   CC   CC                  ", // Y=49
                    "   CC   CC                  ", // Y=50
                    "   CC   CC                  ", // Y=51
                    "    CCCCC                   ", // Y=52
                    "     CCC                    ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=4
                    "AAAAAAAAAAAAA   AAAAAAAAAAA ", // Y=0
                    " AACIIIIICAA    AAACIIICAAA ", // Y=1
                    "   C     C         C   C    ", // Y=2
                    "   C     C         C G C    ", // Y=3
                    "   C     C       FTCMNMCTF  ", // Y=4
                    "   C  G  C         C   C    ", // Y=5
                    "  BCMMNMMCB        C G C    ", // Y=6
                    "   C     C       FTCMNMCTF  ", // Y=7
                    "   C     C         C   C    ", // Y=8
                    "   C     C         C G C    ", // Y=9
                    "   C  G  C       FTCMNMCTF  ", // Y=10
                    "  BCMMNMMCB        C   C    ", // Y=11
                    "   C     C         C   C    ", // Y=12
                    "   C     C         C   C    ", // Y=13
                    "   C     C          CCC     ", // Y=14
                    "   C  G  C                  ", // Y=15
                    "  BCMMNMMCB                 ", // Y=16
                    "   C     C                  ", // Y=17
                    "   C     C                  ", // Y=18
                    "   C     C                  ", // Y=19
                    "   C  G  C                  ", // Y=20
                    "  BCMMNMMCB                 ", // Y=21
                    "   C     C                  ", // Y=22
                    "   C     C                  ", // Y=23
                    "   C     C                  ", // Y=24
                    "   C  G  C                  ", // Y=25
                    "  BCMMNMMCB                 ", // Y=26
                    "   C     C                  ", // Y=27
                    "   C     C                  ", // Y=28
                    "   C     C                  ", // Y=29
                    "   C  G  C                  ", // Y=30
                    "  BCMMNMMCB                 ", // Y=31
                    "   C     C                  ", // Y=32
                    "   C     C                  ", // Y=33
                    "   C     C                  ", // Y=34
                    "   C  G  C                  ", // Y=35
                    "  BCMMNMMCB                 ", // Y=36
                    "   C     C                  ", // Y=37
                    "   C     C                  ", // Y=38
                    "   C     C                  ", // Y=39
                    "   C  G  C                  ", // Y=40
                    "  BCMMNMMCB                 ", // Y=41
                    "   C     C                  ", // Y=42
                    "   C     C                  ", // Y=43
                    "   C     C                  ", // Y=44
                    "   C  G  C                  ", // Y=45
                    "  BCMMNMMCB                 ", // Y=46
                    "   C     C                  ", // Y=47
                    "   C     C                  ", // Y=48
                    "   C     C                  ", // Y=49
                    "   C     C                  ", // Y=50
                    "   C     C                  ", // Y=51
                    "   C     C                  ", // Y=52
                    "    C   C                   ", // Y=53
                    "     CCC                    ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=5
                    "AAAAAAAAAAAAAF  AAAAAAAAAAA ", // Y=0
                    " ACIIIIIIICA F  AACIIIIICAA ", // Y=1
                    "  C       C  F    C     C   ", // Y=2
                    "  C       C  F    C  G  C   ", // Y=3
                    "  C       C  F   TCMMNMMCT  ", // Y=4
                    "  C   G   C  F    C     C   ", // Y=5
                    " BCMMMNMMMCB F    C  G  C   ", // Y=6
                    "  C       C  F   TCMMNMMCT  ", // Y=7
                    "  C       C  F    C     C   ", // Y=8
                    "  C       C  F    C  G  C   ", // Y=9
                    "  C   G   C  F   TCMMNMMCT  ", // Y=10
                    " BCMMMNMMMCB F    C     C   ", // Y=11
                    "  C       C  F    C     C   ", // Y=12
                    "  C       C  F    C     C   ", // Y=13
                    "  C       C  F     C   C    ", // Y=14
                    "  C   G   C  F      CCC     ", // Y=15
                    " BCMMMNMMMCB F              ", // Y=16
                    "  C       C                 ", // Y=17
                    "  C       C                 ", // Y=18
                    "  C       C                 ", // Y=19
                    "  C   G   C                 ", // Y=20
                    " BCMMMNMMMCB                ", // Y=21
                    "  C       C                 ", // Y=22
                    "  C       C                 ", // Y=23
                    "  C       C                 ", // Y=24
                    "  C   G   C                 ", // Y=25
                    " BCMMMNMMMCB                ", // Y=26
                    "  C       C                 ", // Y=27
                    "  C       C                 ", // Y=28
                    "  C       C                 ", // Y=29
                    "  C   G   C                 ", // Y=30
                    " BCMMMNMMMCB                ", // Y=31
                    "  C       C                 ", // Y=32
                    "  C       C                 ", // Y=33
                    "  C       C                 ", // Y=34
                    "  C   G   C                 ", // Y=35
                    " BCMMMNMMMCB                ", // Y=36
                    "  C       C                 ", // Y=37
                    "  C       C                 ", // Y=38
                    "  C       C                 ", // Y=39
                    "  C   G   C                 ", // Y=40
                    " BCMMMNMMMCB                ", // Y=41
                    "  C       C                 ", // Y=42
                    "  C       C                 ", // Y=43
                    "  C       C                 ", // Y=44
                    "  C   G   C                 ", // Y=45
                    " BCMMMNMMMCB                ", // Y=46
                    "  C       C                 ", // Y=47
                    "  C       C                 ", // Y=48
                    "  C       C                 ", // Y=49
                    "  C       C                 ", // Y=50
                    "  C       C                 ", // Y=51
                    "   C     C                  ", // Y=52
                    "   C     C                  ", // Y=53
                    "    C   C                   ", // Y=54
                    "     CCC                    ", // Y=55
            },
            { // Z=6
                    "AAAAAAAAAAAAA   AAAAAAAAAAA ", // Y=0
                    " ACIIIIIIICA    AACIIIIICAA ", // Y=1
                    "  C   S   C       C  S  C   ", // Y=2
                    "  C   S   C       CGGSGGC   ", // Y=3
                    "  C   S   C      TCNNSNNCT  ", // Y=4
                    "  CGGGSGGGC       C  S  C   ", // Y=5
                    " BCNNNSNNNCBF     CGGSGGC   ", // Y=6
                    "  C   S   CVV    TCNNSNNCT  ", // Y=7
                    "  C   S   C F     C  S  C   ", // Y=8
                    "  C   S   C       CGGSGGC   ", // Y=9
                    "  CGGGSGGGC      TCNNSNNCT  ", // Y=10
                    " BCNNNSNNNCB      C  S  C   ", // Y=11
                    "  C   S   C       C  S  C   ", // Y=12
                    "  C   S   C       C  S  C   ", // Y=13
                    "  C   S   C  F     C S C    ", // Y=14
                    "  CGGGSGGGCVVV      CQC     ", // Y=15
                    " BCNNNSNNNCB F              ", // Y=16
                    "  C   S   C                 ", // Y=17
                    "  C   S   C                 ", // Y=18
                    "  C   S   C                 ", // Y=19
                    "  CGGGSGGGC                 ", // Y=20
                    " BCNNNSNNNCB                ", // Y=21
                    "  C   S   C                 ", // Y=22
                    "  C   S   C                 ", // Y=23
                    "  C   S   C                 ", // Y=24
                    "  CGGGSGGGC                 ", // Y=25
                    " BCNNNSNNNCB                ", // Y=26
                    "  C   S   C                 ", // Y=27
                    "  C   S   C                 ", // Y=28
                    "  C   S   C                 ", // Y=29
                    "  CGGGSGGGC                 ", // Y=30
                    " BCNNNSNNNCB                ", // Y=31
                    "  C   S   C                 ", // Y=32
                    "  C   S   C                 ", // Y=33
                    "  C   S   C                 ", // Y=34
                    "  CGGGSGGGC                 ", // Y=35
                    " BCNNNSNNNCB                ", // Y=36
                    "  C   S   C                 ", // Y=37
                    "  C   S   C                 ", // Y=38
                    "  C   S   C                 ", // Y=39
                    "  CGGGSGGGC                 ", // Y=40
                    " BCNNNSNNNCB                ", // Y=41
                    "  C   S   C                 ", // Y=42
                    "  C   S   C                 ", // Y=43
                    "  C   S   C                 ", // Y=44
                    "  CGGGSGGGC                 ", // Y=45
                    " BCNNNSNNNCB                ", // Y=46
                    "  C   S   C                 ", // Y=47
                    "  C   S   C                 ", // Y=48
                    "  C   S   C                 ", // Y=49
                    "  C   S   C                 ", // Y=50
                    "  C   S   C                 ", // Y=51
                    "  CC  S  CC                 ", // Y=52
                    "   C  S  C                  ", // Y=53
                    "    C S C                   ", // Y=54
                    "     CQC                    ", // Y=55
            },
            { // Z=7
                    "AAAAAAAAAAAAA   AAAAAAAAAAA ", // Y=0
                    " ACIIIIIIICA    AACIIIIICAA ", // Y=1
                    "  C       C       C     C   ", // Y=2
                    "  C       C       C  G  C   ", // Y=3
                    "  C       C      TCMMNMMCT  ", // Y=4
                    "  C   G   C       C     C   ", // Y=5
                    " BCMMMNMMMCB      C  G  C   ", // Y=6
                    "  C       C V    TCMMNMMCT  ", // Y=7
                    "  C       C       C     C   ", // Y=8
                    "  C       C       C  G  C   ", // Y=9
                    "  C   G   C      TCMMNMMCT  ", // Y=10
                    " BCMMMNMMMCB      C     C   ", // Y=11
                    "  C       C       C     C   ", // Y=12
                    "  C       C       C     C   ", // Y=13
                    "  C       C        C   C    ", // Y=14
                    "  C   G   C  V      CCC     ", // Y=15
                    " BCMMMNMMMCB                ", // Y=16
                    "  C       C                 ", // Y=17
                    "  C       C                 ", // Y=18
                    "  C       C                 ", // Y=19
                    "  C   G   C                 ", // Y=20
                    " BCMMMNMMMCB                ", // Y=21
                    "  C       C                 ", // Y=22
                    "  C       C                 ", // Y=23
                    "  C       C                 ", // Y=24
                    "  C   G   C                 ", // Y=25
                    " BCMMMNMMMCB                ", // Y=26
                    "  C       C                 ", // Y=27
                    "  C       C                 ", // Y=28
                    "  C       C                 ", // Y=29
                    "  C   G   C                 ", // Y=30
                    " BCMMMNMMMCB                ", // Y=31
                    "  C       C                 ", // Y=32
                    "  C       C                 ", // Y=33
                    "  C       C                 ", // Y=34
                    "  C   G   C                 ", // Y=35
                    " BCMMMNMMMCB                ", // Y=36
                    "  C       C                 ", // Y=37
                    "  C       C                 ", // Y=38
                    "  C       C                 ", // Y=39
                    "  C   G   C                 ", // Y=40
                    " BCMMMNMMMCB                ", // Y=41
                    "  C       C                 ", // Y=42
                    "  C       C                 ", // Y=43
                    "  C       C                 ", // Y=44
                    "  C   G   C                 ", // Y=45
                    " BCMMMNMMMCB                ", // Y=46
                    "  C       C                 ", // Y=47
                    "  C       C                 ", // Y=48
                    "  C       C                 ", // Y=49
                    "  C       C                 ", // Y=50
                    "  C       C                 ", // Y=51
                    "   C     C                  ", // Y=52
                    "   C     C                  ", // Y=53
                    "    C   C                   ", // Y=54
                    "     CCC                    ", // Y=55
            },
            { // Z=8
                    "AAAAAAAAAAAAA   AAAAAAAAAAA ", // Y=0
                    " AACIIIIICAA    AAACIIICAAA ", // Y=1
                    "   C     C         C   C    ", // Y=2
                    "   C     C         C G C    ", // Y=3
                    "   C     C       FTCMNMCTF  ", // Y=4
                    "   C  G  C         C   C    ", // Y=5
                    "  BCMMNMMCB        C G C    ", // Y=6
                    "   C     C  V    FTCMNMCTF  ", // Y=7
                    "   C     C         C   C    ", // Y=8
                    "   C     C         C G C    ", // Y=9
                    "   C  G  C       FTCMNMCTF  ", // Y=10
                    "  BCMMNMMCB        C   C    ", // Y=11
                    "   C     C         C   C    ", // Y=12
                    "   C     C         C   C    ", // Y=13
                    "   C     C          CCC     ", // Y=14
                    "   C  G  C   V              ", // Y=15
                    "  BCMMNMMCB                 ", // Y=16
                    "   C     C                  ", // Y=17
                    "   C     C                  ", // Y=18
                    "   C     C                  ", // Y=19
                    "   C  G  C                  ", // Y=20
                    "  BCMMNMMCB                 ", // Y=21
                    "   C     C                  ", // Y=22
                    "   C     C                  ", // Y=23
                    "   C     C                  ", // Y=24
                    "   C  G  C                  ", // Y=25
                    "  BCMMNMMCB                 ", // Y=26
                    "   C     C                  ", // Y=27
                    "   C     C                  ", // Y=28
                    "   C     C                  ", // Y=29
                    "   C  G  C                  ", // Y=30
                    "  BCMMNMMCB                 ", // Y=31
                    "   C     C                  ", // Y=32
                    "   C     C                  ", // Y=33
                    "   C     C                  ", // Y=34
                    "   C  G  C                  ", // Y=35
                    "  BCMMNMMCB                 ", // Y=36
                    "   C     C                  ", // Y=37
                    "   C     C                  ", // Y=38
                    "   C     C                  ", // Y=39
                    "   C  G  C                  ", // Y=40
                    "  BCMMNMMCB                 ", // Y=41
                    "   C     C                  ", // Y=42
                    "   C     C                  ", // Y=43
                    "   C     C                  ", // Y=44
                    "   C  G  C                  ", // Y=45
                    "  BCMMNMMCB                 ", // Y=46
                    "   C     C                  ", // Y=47
                    "   C     C                  ", // Y=48
                    "   C     C                  ", // Y=49
                    "   C     C                  ", // Y=50
                    "   C     C                  ", // Y=51
                    "   C     C                  ", // Y=52
                    "    C   C                   ", // Y=53
                    "     CCC                    ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=9
                    " AAAAAAAAAAA    AAAAAAAAAAA ", // Y=0
                    "  AACIIICAA     AAAACCCAAAA ", // Y=1
                    "   CC   CC        L CCC L   ", // Y=2
                    "   CC   CC        L CCC L   ", // Y=3
                    "   CC   CC        LTCCCTL   ", // Y=4
                    "   CC G CC        L CCC L   ", // Y=5
                    "  BCCMNMCCB       L CCC L   ", // Y=6
                    "   CC   CC  VF    LTCCCTL   ", // Y=7
                    "   CC   CC        L CCC L   ", // Y=8
                    "   CC   CC        L CCC L   ", // Y=9
                    "   CC G CC        LTCCCTL   ", // Y=10
                    "  BCCMNMCCB       L CCC L   ", // Y=11
                    "   CC   CC        L CCC L   ", // Y=12
                    "   CC   CC          CCC     ", // Y=13
                    "   CC   CC   F              ", // Y=14
                    "   CC G CC   VF             ", // Y=15
                    "  BCCMNMCCB  FF             ", // Y=16
                    "   CC   CC                  ", // Y=17
                    "   CC   CC                  ", // Y=18
                    "   CC   CC                  ", // Y=19
                    "   CC G CC                  ", // Y=20
                    "  BCCMNMCCB                 ", // Y=21
                    "   CC   CC                  ", // Y=22
                    "   CC   CC                  ", // Y=23
                    "   CC   CC                  ", // Y=24
                    "   CC G CC                  ", // Y=25
                    "  BCCMNMCCB                 ", // Y=26
                    "   CC   CC                  ", // Y=27
                    "   CC   CC                  ", // Y=28
                    "   CC   CC                  ", // Y=29
                    "   CC G CC                  ", // Y=30
                    "  BCCMNMCCB                 ", // Y=31
                    "   CC   CC                  ", // Y=32
                    "   CC   CC                  ", // Y=33
                    "   CC   CC                  ", // Y=34
                    "   CC G CC                  ", // Y=35
                    "  BCCMNMCCB                 ", // Y=36
                    "   CC   CC                  ", // Y=37
                    "   CC   CC                  ", // Y=38
                    "   CC   CC                  ", // Y=39
                    "   CC G CC                  ", // Y=40
                    "  BCCMNMCCB                 ", // Y=41
                    "   CC   CC                  ", // Y=42
                    "   CC   CC                  ", // Y=43
                    "   CC   CC                  ", // Y=44
                    "   CC G CC                  ", // Y=45
                    "  BCCMNMCCB                 ", // Y=46
                    "   CC   CC                  ", // Y=47
                    "   CC   CC                  ", // Y=48
                    "   CC   CC                  ", // Y=49
                    "   CC   CC                  ", // Y=50
                    "   CC   CC                  ", // Y=51
                    "    CCCCC                   ", // Y=52
                    "     CCC                    ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=10
                    " AAAAAAAAAAA    AAAAAAAAAAA ", // Y=0
                    "  AAACCCAAA     AAAAAAAAAAA ", // Y=1
                    "  E  CCC  E                 ", // Y=2
                    "  E  CCC  E                 ", // Y=3
                    "  E  CCC  E        FTTTF    ", // Y=4
                    "  E  CCC  E                 ", // Y=5
                    "  EBBCCCBBE                 ", // Y=6
                    "  E  CCC  E V      FTTTF    ", // Y=7
                    "  E  CCC  E                 ", // Y=8
                    "  E  CCC  E                 ", // Y=9
                    "  E  CCC  E        FTTTF    ", // Y=10
                    "  EBBCCCBBE                 ", // Y=11
                    "  E  CCC  E                 ", // Y=12
                    "  E  CCC  E        FTTTF    ", // Y=13
                    "  E  CCC  E                 ", // Y=14
                    "  E  CCC  E  V              ", // Y=15
                    "  EBBCCCBBE        FTTTF    ", // Y=16
                    "  E  CCC  E                 ", // Y=17
                    "  E  CCC  E                 ", // Y=18
                    "  E  CCC  E        FTTTF    ", // Y=19
                    "  E  CCC  E                 ", // Y=20
                    "  EBBCCCBBE                 ", // Y=21
                    "  E  CCC  E                 ", // Y=22
                    "  E  CCC  E                 ", // Y=23
                    "  E  CCC  E                 ", // Y=24
                    "  E  CCC  E                 ", // Y=25
                    "  EBBCCCBBE                 ", // Y=26
                    "  E  CCC  E                 ", // Y=27
                    "  E  CCC  E                 ", // Y=28
                    "  E  CCC  E                 ", // Y=29
                    "  E  CCC  E                 ", // Y=30
                    "  EBBCCCBBE                 ", // Y=31
                    "  E  CCC  E                 ", // Y=32
                    "  E  CCC  E                 ", // Y=33
                    "  E  CCC  E                 ", // Y=34
                    "  E  CCC  E                 ", // Y=35
                    "  EBBCCCBBE                 ", // Y=36
                    "  E  CCC  E                 ", // Y=37
                    "  E  CCC  E                 ", // Y=38
                    "  E  CCC  E                 ", // Y=39
                    "  E  CCC  E                 ", // Y=40
                    "  EBBCCCBBE                 ", // Y=41
                    "  E  CCC  E                 ", // Y=42
                    "  E  CCC  E                 ", // Y=43
                    "  E  CCC  E                 ", // Y=44
                    "  E  CCC  E                 ", // Y=45
                    "  EBBCCCBBE                 ", // Y=46
                    "  E  CCC  E                 ", // Y=47
                    "  E  CCC  E                 ", // Y=48
                    "     CCC                    ", // Y=49
                    "     CCC                    ", // Y=50
                    "     CCC                    ", // Y=51
                    "      C                     ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=11
                    "  AAAAAAAAA     AAAAAAAAAAA ", // Y=0
                    "    AAAAA       AAAACCCAAAA ", // Y=1
                    "      TF          L CCC L   ", // Y=2
                    "      T           L CCC L   ", // Y=3
                    "      T           LTCCCTL   ", // Y=4
                    "      T           L CCC L   ", // Y=5
                    "     BBB          L CCC L   ", // Y=6
                    "     FT     V     LTCCCTL   ", // Y=7
                    "      T           L CCC L   ", // Y=8
                    "      T           L CCC L   ", // Y=9
                    "      T           LTCCCTL   ", // Y=10
                    "     BBB          L CCC L   ", // Y=11
                    "      TF          L CCC L   ", // Y=12
                    "      T           LTCCCTL   ", // Y=13
                    "      T           L CCC L   ", // Y=14
                    "      T      V    L CCC L   ", // Y=15
                    "     BBB          LTCCCTL   ", // Y=16
                    "     FT           L CCC L   ", // Y=17
                    "      T           L CCC L   ", // Y=18
                    "      T           LTCCCTL   ", // Y=19
                    "      T           L CCC L   ", // Y=20
                    "     BBB          L CCC L   ", // Y=21
                    "      TF            CCC     ", // Y=22
                    "      T                     ", // Y=23
                    "      T                     ", // Y=24
                    "      T                     ", // Y=25
                    "     BBB                    ", // Y=26
                    "     FT                     ", // Y=27
                    "      T                     ", // Y=28
                    "      T                     ", // Y=29
                    "      T                     ", // Y=30
                    "     BBB                    ", // Y=31
                    "      TF                    ", // Y=32
                    "      T                     ", // Y=33
                    "      T                     ", // Y=34
                    "      T                     ", // Y=35
                    "     BBB                    ", // Y=36
                    "     FT                     ", // Y=37
                    "      T                     ", // Y=38
                    "      T                     ", // Y=39
                    "      T                     ", // Y=40
                    "     BBB                    ", // Y=41
                    "       F                    ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "     BBB                    ", // Y=46
                    "     F                      ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=12
                    "    AAAAA       AAAAAAAAAAA ", // Y=0
                    "      A         AAACIIICAAA ", // Y=1
                    "                   C   C    ", // Y=2
                    "       F           C G C    ", // Y=3
                    "                 FTCMNMCTF  ", // Y=4
                    "     F             C   C    ", // Y=5
                    "      B            C G C    ", // Y=6
                    "            V    FTCMNMCTF  ", // Y=7
                    "     F             C   C    ", // Y=8
                    "                   C G C    ", // Y=9
                    "       F         FTCMNMCTF  ", // Y=10
                    "      B            C   C    ", // Y=11
                    "                   C G C    ", // Y=12
                    "       F         FTCMNMCTF  ", // Y=13
                    "                   C   C    ", // Y=14
                    "     F       V     C G C    ", // Y=15
                    "      B          FTCMNMCTF  ", // Y=16
                    "                   C   C    ", // Y=17
                    "     F             C G C    ", // Y=18
                    "                 FTCMNMCTF  ", // Y=19
                    "       F           C   C    ", // Y=20
                    "      B            C   C    ", // Y=21
                    "                   C   C    ", // Y=22
                    "       F            CCC     ", // Y=23
                    "                            ", // Y=24
                    "     F                      ", // Y=25
                    "      B                     ", // Y=26
                    "                            ", // Y=27
                    "     F                      ", // Y=28
                    "                            ", // Y=29
                    "       F                    ", // Y=30
                    "      B                     ", // Y=31
                    "                            ", // Y=32
                    "       F                    ", // Y=33
                    "                            ", // Y=34
                    "     F                      ", // Y=35
                    "      B                     ", // Y=36
                    "                            ", // Y=37
                    "     F                      ", // Y=38
                    "                            ", // Y=39
                    "       F                    ", // Y=40
                    "      B                     ", // Y=41
                    "                            ", // Y=42
                    "       F                    ", // Y=43
                    "                            ", // Y=44
                    "     F                      ", // Y=45
                    "      B                     ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=13
                    "     AAA        AAAAAAAAAAA ", // Y=0
                    "      A         AACIIIIICAA ", // Y=1
                    "                  C     C   ", // Y=2
                    "                  C  G  C   ", // Y=3
                    "     F F         TCMMNMMCT  ", // Y=4
                    "                  C     C   ", // Y=5
                    "      B           C  G  C   ", // Y=6
                    "            V    TCMMNMMCT  ", // Y=7
                    "                  C     C   ", // Y=8
                    "     F F          C  G  C   ", // Y=9
                    "                 TCMMNMMCT  ", // Y=10
                    "      B           C     C   ", // Y=11
                    "                  C  G  C   ", // Y=12
                    "                 TCMMNMMCT  ", // Y=13
                    "     F F          C     C   ", // Y=14
                    "             V    C  G  C   ", // Y=15
                    "      B          TCMMNMMCT  ", // Y=16
                    "                  C     C   ", // Y=17
                    "                  C  G  C   ", // Y=18
                    "     F F         TCMMNMMCT  ", // Y=19
                    "                  C     C   ", // Y=20
                    "      B           C     C   ", // Y=21
                    "                  C     C   ", // Y=22
                    "                   C   C    ", // Y=23
                    "     F F            CCC     ", // Y=24
                    "                            ", // Y=25
                    "      B                     ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "     F F                    ", // Y=29
                    "                            ", // Y=30
                    "      B                     ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "     F F                    ", // Y=34
                    "                            ", // Y=35
                    "      B                     ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "     F F                    ", // Y=39
                    "                            ", // Y=40
                    "      B                     ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "     F F                    ", // Y=44
                    "                            ", // Y=45
                    "      B                     ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=14
                    "    AAAAA       AAAAAAAAAAA ", // Y=0
                    "      A         AACIIIIICAA ", // Y=1
                    "                  C  S  C   ", // Y=2
                    "     F            CGGSGGC   ", // Y=3
                    "                 TCNNSNNCT  ", // Y=4
                    "       F          C  S  C   ", // Y=5
                    "      B           CGGSGGC   ", // Y=6
                    "            VF   TCNNSNNCT  ", // Y=7
                    "       F          C  S  C   ", // Y=8
                    "                  CGGSGGC   ", // Y=9
                    "     F           TCNNSNNCT  ", // Y=10
                    "      B           C  S  C   ", // Y=11
                    "                  CGGSGGC   ", // Y=12
                    "     F           TCNNSNNCT  ", // Y=13
                    "             F    C  S  C   ", // Y=14
                    "       F     VF   CGGSGGC   ", // Y=15
                    "      B      FF  TCNNSNNCT  ", // Y=16
                    "                  C  S  C   ", // Y=17
                    "       F          CGGSGGC   ", // Y=18
                    "                 TCNNSNNCT  ", // Y=19
                    "     F            C  S  C   ", // Y=20
                    "      B           C  S  C   ", // Y=21
                    "                  C  S  C   ", // Y=22
                    "     F             C S C    ", // Y=23
                    "                    CQC     ", // Y=24
                    "       F                    ", // Y=25
                    "      B                     ", // Y=26
                    "                            ", // Y=27
                    "       F                    ", // Y=28
                    "                            ", // Y=29
                    "     F                      ", // Y=30
                    "      B                     ", // Y=31
                    "                            ", // Y=32
                    "     F                      ", // Y=33
                    "                            ", // Y=34
                    "       F                    ", // Y=35
                    "      B                     ", // Y=36
                    "                            ", // Y=37
                    "       F                    ", // Y=38
                    "                            ", // Y=39
                    "     F                      ", // Y=40
                    "      B                     ", // Y=41
                    "                            ", // Y=42
                    "     F                      ", // Y=43
                    "                            ", // Y=44
                    "       F                    ", // Y=45
                    "      B                     ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=15
                    "  AAAAAAAAA     AAAAAAAAAAA ", // Y=0
                    "    AAAAA       AACIIIIICAA ", // Y=1
                    "     FT           C     C   ", // Y=2
                    "      T           C  G  C   ", // Y=3
                    "      T          TCMMNMMCT  ", // Y=4
                    "      T           C     C   ", // Y=5
                    "     BBB          C  G  C   ", // Y=6
                    "      TF    V    TCMMNMMCT  ", // Y=7
                    "      T           C     C   ", // Y=8
                    "      T           C  G  C   ", // Y=9
                    "      T          TCMMNMMCT  ", // Y=10
                    "     BBB          C     C   ", // Y=11
                    "     FT           C  G  C   ", // Y=12
                    "      T          TCMMNMMCT  ", // Y=13
                    "      T           C     C   ", // Y=14
                    "      T      V    C  G  C   ", // Y=15
                    "     BBB         TCMMNMMCT  ", // Y=16
                    "      TF          C     C   ", // Y=17
                    "      T           C  G  C   ", // Y=18
                    "      T          TCMMNMMCT  ", // Y=19
                    "      T           C     C   ", // Y=20
                    "     BBB          C     C   ", // Y=21
                    "     FT           C     C   ", // Y=22
                    "      T            C   C    ", // Y=23
                    "      T             CCC     ", // Y=24
                    "      T                     ", // Y=25
                    "     BBB                    ", // Y=26
                    "      TF                    ", // Y=27
                    "      T                     ", // Y=28
                    "      T                     ", // Y=29
                    "      T                     ", // Y=30
                    "     BBB                    ", // Y=31
                    "     FT                     ", // Y=32
                    "      T                     ", // Y=33
                    "      T                     ", // Y=34
                    "      T                     ", // Y=35
                    "     BBB                    ", // Y=36
                    "      TF                    ", // Y=37
                    "      T                     ", // Y=38
                    "      T                     ", // Y=39
                    "      T                     ", // Y=40
                    "     BBB                    ", // Y=41
                    "     F                      ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "     BBB                    ", // Y=46
                    "       F                    ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=16
                    " AAAAAAAAAAA    AAAAAAAAAAA ", // Y=0
                    "  AAACCCAAA     AAACIIICAAA ", // Y=1
                    "  E  CCC  E        C   C    ", // Y=2
                    "  E  CCC  E        C G C    ", // Y=3
                    "  E  CCC  E      FTCMNMCTF  ", // Y=4
                    "  E  CCC  E        C   C    ", // Y=5
                    "  EBBCCCBBE        C G C    ", // Y=6
                    "  E  CCC  E V    FTCMNMCTF  ", // Y=7
                    "  E  CCC  E        C   C    ", // Y=8
                    "  E  CCC  E        C G C    ", // Y=9
                    "  E  CCC  E      FTCMNMCTF  ", // Y=10
                    "  EBBCCCBBE        C   C    ", // Y=11
                    "  E  CCC  E        C G C    ", // Y=12
                    "  E  CCC  E      FTCMNMCTF  ", // Y=13
                    "  E  CCC  E        C   C    ", // Y=14
                    "  E  CCC  E  V     C G C    ", // Y=15
                    "  EBBCCCBBE      FTCMNMCTF  ", // Y=16
                    "  E  CCC  E        C   C    ", // Y=17
                    "  E  CCC  E        C G C    ", // Y=18
                    "  E  CCC  E      FTCMNMCTF  ", // Y=19
                    "  E  CCC  E        C   C    ", // Y=20
                    "  EBBCCCBBE        C   C    ", // Y=21
                    "  E  CCC  E        C   C    ", // Y=22
                    "  E  CCC  E         CCC     ", // Y=23
                    "  E  CCC  E                 ", // Y=24
                    "  E  CCC  E                 ", // Y=25
                    "  EBBCCCBBE                 ", // Y=26
                    "  E  CCC  E                 ", // Y=27
                    "  E  CCC  E                 ", // Y=28
                    "  E  CCC  E                 ", // Y=29
                    "  E  CCC  E                 ", // Y=30
                    "  EBBCCCBBE                 ", // Y=31
                    "  E  CCC  E                 ", // Y=32
                    "  E  CCC  E                 ", // Y=33
                    "  E  CCC  E                 ", // Y=34
                    "  E  CCC  E                 ", // Y=35
                    "  EBBCCCBBE                 ", // Y=36
                    "  E  CCC  E                 ", // Y=37
                    "  E  CCC  E                 ", // Y=38
                    "  E  CCC  E                 ", // Y=39
                    "  E  CCC  E                 ", // Y=40
                    "  EBBCCCBBE                 ", // Y=41
                    "  E  CCC  E                 ", // Y=42
                    "  E  CCC  E                 ", // Y=43
                    "  E  CCC  E                 ", // Y=44
                    "  E  CCC  E                 ", // Y=45
                    "  EBBCCCBBE                 ", // Y=46
                    "  E  CCC  E                 ", // Y=47
                    "  E  CCC  E                 ", // Y=48
                    "     CCC                    ", // Y=49
                    "     CCC                    ", // Y=50
                    "     CCC                    ", // Y=51
                    "      C                     ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=17
                    " AAAAAAAAAAA    AAAAAAAAAAA ", // Y=0
                    "  AACIIICAA     AAAACCCAAAA ", // Y=1
                    "   CC   CC        L CCC L   ", // Y=2
                    "   CC   CC        L CCC L   ", // Y=3
                    "   CC   CC        LTCCCTL   ", // Y=4
                    "   CC G CC        L CCC L   ", // Y=5
                    "  BCCMNMCCB       L CCC L   ", // Y=6
                    "   CC   CC  V     LTCCCTL   ", // Y=7
                    "   CC   CC        L CCC L   ", // Y=8
                    "   CC   CC        L CCC L   ", // Y=9
                    "   CC G CC        LTCCCTL   ", // Y=10
                    "  BCCMNMCCB       L CCC L   ", // Y=11
                    "   CC   CC        L CCC L   ", // Y=12
                    "   CC   CC        LTCCCTL   ", // Y=13
                    "   CC   CC        L CCC L   ", // Y=14
                    "   CC G CC   V    L CCC L   ", // Y=15
                    "  BCCMNMCCB       LTCCCTL   ", // Y=16
                    "   CC   CC        L CCC L   ", // Y=17
                    "   CC   CC        L CCC L   ", // Y=18
                    "   CC   CC        LTCCCTL   ", // Y=19
                    "   CC G CC        L CCC L   ", // Y=20
                    "  BCCMNMCCB       L CCC L   ", // Y=21
                    "   CC   CC          CCC     ", // Y=22
                    "   CC   CC                  ", // Y=23
                    "   CC   CC                  ", // Y=24
                    "   CC G CC                  ", // Y=25
                    "  BCCMNMCCB                 ", // Y=26
                    "   CC   CC                  ", // Y=27
                    "   CC   CC                  ", // Y=28
                    "   CC   CC                  ", // Y=29
                    "   CC G CC                  ", // Y=30
                    "  BCCMNMCCB                 ", // Y=31
                    "   CC   CC                  ", // Y=32
                    "   CC   CC                  ", // Y=33
                    "   CC   CC                  ", // Y=34
                    "   CC G CC                  ", // Y=35
                    "  BCCMNMCCB                 ", // Y=36
                    "   CC   CC                  ", // Y=37
                    "   CC   CC                  ", // Y=38
                    "   CC   CC                  ", // Y=39
                    "   CC G CC                  ", // Y=40
                    "  BCCMNMCCB                 ", // Y=41
                    "   CC   CC                  ", // Y=42
                    "   CC   CC                  ", // Y=43
                    "   CC   CC                  ", // Y=44
                    "   CC G CC                  ", // Y=45
                    "  BCCMNMCCB                 ", // Y=46
                    "   CC   CC                  ", // Y=47
                    "   CC   CC                  ", // Y=48
                    "   CC   CC                  ", // Y=49
                    "   CC   CC                  ", // Y=50
                    "   CC   CC                  ", // Y=51
                    "    CCCCC                   ", // Y=52
                    "     CCC                    ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=18
                    "AAAAAAAAAAAAA   AAAAAAAAAAA ", // Y=0
                    " AACIIIIICAA    AAAAAAAAAAA ", // Y=1
                    "   C     C                  ", // Y=2
                    "   C     C                  ", // Y=3
                    "   C     C         FTTTF    ", // Y=4
                    "   C  G  C                  ", // Y=5
                    "  BCMMNMMCB                 ", // Y=6
                    "   C     C  V      FTTTF    ", // Y=7
                    "   C     C                  ", // Y=8
                    "   C     C                  ", // Y=9
                    "   C  G  C         FTTTF    ", // Y=10
                    "  BCMMNMMCB                 ", // Y=11
                    "   C     C                  ", // Y=12
                    "   C     C         FTTTF    ", // Y=13
                    "   C     C                  ", // Y=14
                    "   C  G  C   V       V      ", // Y=15
                    "  BCMMNMMCB        FTTTF    ", // Y=16
                    "   C     C                  ", // Y=17
                    "   C     C                  ", // Y=18
                    "   C     C         FTTTF    ", // Y=19
                    "   C  G  C           V      ", // Y=20
                    "  BCMMNMMCB                 ", // Y=21
                    "   C     C                  ", // Y=22
                    "   C     C                  ", // Y=23
                    "   C     C                  ", // Y=24
                    "   C  G  C                  ", // Y=25
                    "  BCMMNMMCB                 ", // Y=26
                    "   C     C                  ", // Y=27
                    "   C     C                  ", // Y=28
                    "   C     C                  ", // Y=29
                    "   C  G  C                  ", // Y=30
                    "  BCMMNMMCB                 ", // Y=31
                    "   C     C                  ", // Y=32
                    "   C     C                  ", // Y=33
                    "   C     C                  ", // Y=34
                    "   C  G  C                  ", // Y=35
                    "  BCMMNMMCB                 ", // Y=36
                    "   C     C                  ", // Y=37
                    "   C     C                  ", // Y=38
                    "   C     C                  ", // Y=39
                    "   C  G  C                  ", // Y=40
                    "  BCMMNMMCB                 ", // Y=41
                    "   C     C                  ", // Y=42
                    "   C     C                  ", // Y=43
                    "   C     C                  ", // Y=44
                    "   C  G  C                  ", // Y=45
                    "  BCMMNMMCB                 ", // Y=46
                    "   C     C                  ", // Y=47
                    "   C     C                  ", // Y=48
                    "   C     C                  ", // Y=49
                    "   C     C                  ", // Y=50
                    "   C     C                  ", // Y=51
                    "   C     C                  ", // Y=52
                    "    C   C                   ", // Y=53
                    "     CCC                    ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=19
                    "AAAAAAAAAAAAA               ", // Y=0
                    " ACIIIIIIICA                ", // Y=1
                    "  C       C                 ", // Y=2
                    "  C       C                 ", // Y=3
                    "  C       C                 ", // Y=4
                    "  C   G   C                 ", // Y=5
                    " BCMMMNMMMCB                ", // Y=6
                    "  C       C VF              ", // Y=7
                    "  C       C                 ", // Y=8
                    "  C       C                 ", // Y=9
                    "  C   G   C                 ", // Y=10
                    " BCMMMNMMMCB                ", // Y=11
                    "  C       C                 ", // Y=12
                    "  C       C                 ", // Y=13
                    "  C       C  F              ", // Y=14
                    "  C   G   C  VF      V      ", // Y=15
                    " BCMMMNMMMCB FF             ", // Y=16
                    "  C       C                 ", // Y=17
                    "  C       C                 ", // Y=18
                    "  C       C                 ", // Y=19
                    "  C   G   C          V      ", // Y=20
                    " BCMMMNMMMCB                ", // Y=21
                    "  C       C                 ", // Y=22
                    "  C       C                 ", // Y=23
                    "  C       C                 ", // Y=24
                    "  C   G   C                 ", // Y=25
                    " BCMMMNMMMCB                ", // Y=26
                    "  C       C                 ", // Y=27
                    "  C       C                 ", // Y=28
                    "  C       C                 ", // Y=29
                    "  C   G   C                 ", // Y=30
                    " BCMMMNMMMCB                ", // Y=31
                    "  C       C                 ", // Y=32
                    "  C       C                 ", // Y=33
                    "  C       C                 ", // Y=34
                    "  C   G   C                 ", // Y=35
                    " BCMMMNMMMCB                ", // Y=36
                    "  C       C                 ", // Y=37
                    "  C       C                 ", // Y=38
                    "  C       C                 ", // Y=39
                    "  C   G   C                 ", // Y=40
                    " BCMMMNMMMCB                ", // Y=41
                    "  C       C                 ", // Y=42
                    "  C       C                 ", // Y=43
                    "  C       C                 ", // Y=44
                    "  C   G   C                 ", // Y=45
                    " BCMMMNMMMCB                ", // Y=46
                    "  C       C                 ", // Y=47
                    "  C       C                 ", // Y=48
                    "  C       C                 ", // Y=49
                    "  C       C                 ", // Y=50
                    "  C       C                 ", // Y=51
                    "   C     C                  ", // Y=52
                    "   C     C                  ", // Y=53
                    "    C   C                   ", // Y=54
                    "     CCC                    ", // Y=55
            },
            { // Z=20
                    "AAAAAAAAAAAAA       F F     ", // Y=0
                    " ACIIIIIIICA        F F     ", // Y=1
                    "  C   S   C         F F     ", // Y=2
                    "  C   S   C         F F     ", // Y=3
                    "  C   S   C         F F     ", // Y=4
                    "  CGGGSGGGC         F F     ", // Y=5
                    " BCNNNSNNNCB        F F     ", // Y=6
                    "  C   S   CVV       F F     ", // Y=7
                    "  C   S   C         F F     ", // Y=8
                    "  C   S   C         F F     ", // Y=9
                    "  CGGGSGGGC         F F     ", // Y=10
                    " BCNNNSNNNCB        F F     ", // Y=11
                    "  C   S   C         F F     ", // Y=12
                    "  C   S   C         F F     ", // Y=13
                    "  C   S   C         F F     ", // Y=14
                    "  CGGGSGGGCVVV      FVF     ", // Y=15
                    " BCNNNSNNNCB        F F     ", // Y=16
                    "  C   S   C         F F     ", // Y=17
                    "  C   S   C         F F     ", // Y=18
                    "  C   S   C         F F     ", // Y=19
                    "  CGGGSGGGC         FVF     ", // Y=20
                    " BCNNNSNNNCB        F F     ", // Y=21
                    "  C   S   C                 ", // Y=22
                    "  C   S   C                 ", // Y=23
                    "  C   S   C                 ", // Y=24
                    "  CGGGSGGGC                 ", // Y=25
                    " BCNNNSNNNCB                ", // Y=26
                    "  C   S   C                 ", // Y=27
                    "  C   S   C                 ", // Y=28
                    "  C   S   C                 ", // Y=29
                    "  CGGGSGGGC                 ", // Y=30
                    " BCNNNSNNNCB                ", // Y=31
                    "  C   S   C                 ", // Y=32
                    "  C   S   C                 ", // Y=33
                    "  C   S   C                 ", // Y=34
                    "  CGGGSGGGC                 ", // Y=35
                    " BCNNNSNNNCB                ", // Y=36
                    "  C   S   C                 ", // Y=37
                    "  C   S   C                 ", // Y=38
                    "  C   S   C                 ", // Y=39
                    "  CGGGSGGGC                 ", // Y=40
                    " BCNNNSNNNCB                ", // Y=41
                    "  C   S   C                 ", // Y=42
                    "  C   S   C                 ", // Y=43
                    "  C   S   C                 ", // Y=44
                    "  CGGGSGGGC                 ", // Y=45
                    " BCNNNSNNNCB                ", // Y=46
                    "  C   S   C                 ", // Y=47
                    "  C   S   C                 ", // Y=48
                    "  C   S   C                 ", // Y=49
                    "  C   S   C                 ", // Y=50
                    "  C   S   C                 ", // Y=51
                    "  CC  S  CC                 ", // Y=52
                    "   C  S  C                  ", // Y=53
                    "    C S C                   ", // Y=54
                    "     CQC                    ", // Y=55
            },
            { // Z=21
                    "AAAAAAAAAAAAA               ", // Y=0
                    " ACIIIIIIICA                ", // Y=1
                    "  C       C                 ", // Y=2
                    "  C       C                 ", // Y=3
                    "  C       C                 ", // Y=4
                    "  C   G   C                 ", // Y=5
                    " BCMMMNMMMCB                ", // Y=6
                    "  C       C V               ", // Y=7
                    "  C       C                 ", // Y=8
                    "  C       C                 ", // Y=9
                    "  C   G   C                 ", // Y=10
                    " BCMMMNMMMCB                ", // Y=11
                    "  C       C                 ", // Y=12
                    "  C       C                 ", // Y=13
                    "  C       C                 ", // Y=14
                    "  C   G   C  V       V      ", // Y=15
                    " BCMMMNMMMCB                ", // Y=16
                    "  C       C                 ", // Y=17
                    "  C       C                 ", // Y=18
                    "  C       C                 ", // Y=19
                    "  C   G   C          V      ", // Y=20
                    " BCMMMNMMMCB                ", // Y=21
                    "  C       C                 ", // Y=22
                    "  C       C                 ", // Y=23
                    "  C       C                 ", // Y=24
                    "  C   G   C                 ", // Y=25
                    " BCMMMNMMMCB                ", // Y=26
                    "  C       C                 ", // Y=27
                    "  C       C                 ", // Y=28
                    "  C       C                 ", // Y=29
                    "  C   G   C                 ", // Y=30
                    " BCMMMNMMMCB                ", // Y=31
                    "  C       C                 ", // Y=32
                    "  C       C                 ", // Y=33
                    "  C       C                 ", // Y=34
                    "  C   G   C                 ", // Y=35
                    " BCMMMNMMMCB                ", // Y=36
                    "  C       C                 ", // Y=37
                    "  C       C                 ", // Y=38
                    "  C       C                 ", // Y=39
                    "  C   G   C                 ", // Y=40
                    " BCMMMNMMMCB                ", // Y=41
                    "  C       C                 ", // Y=42
                    "  C       C                 ", // Y=43
                    "  C       C                 ", // Y=44
                    "  C   G   C                 ", // Y=45
                    " BCMMMNMMMCB                ", // Y=46
                    "  C       C                 ", // Y=47
                    "  C       C                 ", // Y=48
                    "  C       C                 ", // Y=49
                    "  C       C                 ", // Y=50
                    "  C       C                 ", // Y=51
                    "   C     C                  ", // Y=52
                    "   C     C                  ", // Y=53
                    "    C   C                   ", // Y=54
                    "     CCC                    ", // Y=55
            },
            { // Z=22
                    "AAAAAAAAAAAAA       AAA     ", // Y=0
                    " AACIIIIICAA        AAA     ", // Y=1
                    "   C     C           B      ", // Y=2
                    "   C     C           B      ", // Y=3
                    "   C     C           B      ", // Y=4
                    "   C  G  C           B      ", // Y=5
                    "  BCMMNMMCB         BBB     ", // Y=6
                    "   C     C  V        B      ", // Y=7
                    "   C     C           B      ", // Y=8
                    "   C     C           B      ", // Y=9
                    "   C  G  C                  ", // Y=10
                    "  BCMMNMMCB                 ", // Y=11
                    "   C     C                  ", // Y=12
                    "   C     C                  ", // Y=13
                    "   C     C   F              ", // Y=14
                    "   C  G  C   VVVVVVVVV      ", // Y=15
                    "  BCMMNMMCB  F              ", // Y=16
                    "   C     C                  ", // Y=17
                    "   C     C                  ", // Y=18
                    "   C     C                  ", // Y=19
                    "   C  G  C           V      ", // Y=20
                    "  BCMMNMMCB                 ", // Y=21
                    "   C     C                  ", // Y=22
                    "   C     C                  ", // Y=23
                    "   C     C                  ", // Y=24
                    "   C  G  C                  ", // Y=25
                    "  BCMMNMMCB                 ", // Y=26
                    "   C     C                  ", // Y=27
                    "   C     C                  ", // Y=28
                    "   C     C                  ", // Y=29
                    "   C  G  C                  ", // Y=30
                    "  BCMMNMMCB                 ", // Y=31
                    "   C     C                  ", // Y=32
                    "   C     C                  ", // Y=33
                    "   C     C                  ", // Y=34
                    "   C  G  C                  ", // Y=35
                    "  BCMMNMMCB                 ", // Y=36
                    "   C     C                  ", // Y=37
                    "   C     C                  ", // Y=38
                    "   C     C                  ", // Y=39
                    "   C  G  C                  ", // Y=40
                    "  BCMMNMMCB                 ", // Y=41
                    "   C     C                  ", // Y=42
                    "   C     C                  ", // Y=43
                    "   C     C                  ", // Y=44
                    "   C  G  C                  ", // Y=45
                    "  BCMMNMMCB                 ", // Y=46
                    "   C     C                  ", // Y=47
                    "   C     C                  ", // Y=48
                    "   C     C                  ", // Y=49
                    "   C     C                  ", // Y=50
                    "   C     C                  ", // Y=51
                    "   C     C                  ", // Y=52
                    "    C   C                   ", // Y=53
                    "     CCC                    ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=23
                    " AAAAAAAAAAA F    AAAAAAA   ", // Y=0
                    "  AACIIICAA  F    AAAAAAA   ", // Y=1
                    "   CC   CC   F      GGG     ", // Y=2
                    "   CC   CC   F      GGG     ", // Y=3
                    "   CC   CC   F      GGG     ", // Y=4
                    "   CC G CC   F      GGG     ", // Y=5
                    "  BCCMNMCCB FF    BBGGGBB   ", // Y=6
                    "   CC   CC  VF      GGG     ", // Y=7
                    "   CC   CC  FF      GGG     ", // Y=8
                    "   CC   CC   F       B      ", // Y=9
                    "   CC G CC   F              ", // Y=10
                    "  BCCMNMCCB  F              ", // Y=11
                    "   CC   CC   F              ", // Y=12
                    "   CC   CC   F              ", // Y=13
                    "   CC   CC   F              ", // Y=14
                    "   CC G CC   F       V      ", // Y=15
                    "  BCCMNMCCB  F              ", // Y=16
                    "   CC   CC                  ", // Y=17
                    "   CC   CC                  ", // Y=18
                    "   CC   CC                  ", // Y=19
                    "   CC G CC           V      ", // Y=20
                    "  BCCMNMCCB                 ", // Y=21
                    "   CC   CC                  ", // Y=22
                    "   CC   CC                  ", // Y=23
                    "   CC   CC                  ", // Y=24
                    "   CC G CC                  ", // Y=25
                    "  BCCMNMCCB                 ", // Y=26
                    "   CC   CC                  ", // Y=27
                    "   CC   CC                  ", // Y=28
                    "   CC   CC                  ", // Y=29
                    "   CC G CC                  ", // Y=30
                    "  BCCMNMCCB                 ", // Y=31
                    "   CC   CC                  ", // Y=32
                    "   CC   CC                  ", // Y=33
                    "   CC   CC                  ", // Y=34
                    "   CC G CC                  ", // Y=35
                    "  BCCMNMCCB                 ", // Y=36
                    "   CC   CC                  ", // Y=37
                    "   CC   CC                  ", // Y=38
                    "   CC   CC                  ", // Y=39
                    "   CC G CC                  ", // Y=40
                    "  BCCMNMCCB                 ", // Y=41
                    "   CC   CC                  ", // Y=42
                    "   CC   CC                  ", // Y=43
                    "   CC   CC                  ", // Y=44
                    "   CC G CC                  ", // Y=45
                    "  BCCMNMCCB                 ", // Y=46
                    "   CC   CC                  ", // Y=47
                    "   CC   CC                  ", // Y=48
                    "   CC   CC                  ", // Y=49
                    "   CC   CC                  ", // Y=50
                    "   CC   CC                  ", // Y=51
                    "    CCCCC                   ", // Y=52
                    "     CCC                    ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=24
                    " AAAAAAAAAAA     AAAAAAAAA  ", // Y=0
                    "  AAACCCAAA      AAAMMMAAA  ", // Y=1
                    "  E  CCC  E       GG   GG   ", // Y=2
                    "  E  CCC  E       GG W GG   ", // Y=3
                    "  E  CCC  E       GG   GG   ", // Y=4
                    "  E  CCC  E       GG W GG   ", // Y=5
                    "  EBBCCCBBE      BGG   GGB  ", // Y=6
                    "  E  CCC  E V     GG W GG   ", // Y=7
                    "  E  CCC  E       GG   GG   ", // Y=8
                    "  E  CCC  E         GGG     ", // Y=9
                    "  E  CCC  E         F F     ", // Y=10
                    "  EBBCCCBBE         F F     ", // Y=11
                    "  E  CCC  E         F F     ", // Y=12
                    "  E  CCC  E         F F     ", // Y=13
                    "  E  CCC  E         F F     ", // Y=14
                    "  E  CCC  E         FVF     ", // Y=15
                    "  EBBCCCBBE         F F     ", // Y=16
                    "  E  CCC  E         F F     ", // Y=17
                    "  E  CCC  E         F F     ", // Y=18
                    "  E  CCC  E         F F     ", // Y=19
                    "  E  CCC  E         FVF     ", // Y=20
                    "  EBBCCCBBE         F F     ", // Y=21
                    "  E  CCC  E                 ", // Y=22
                    "  E  CCC  E                 ", // Y=23
                    "  E  CCC  E                 ", // Y=24
                    "  E  CCC  E                 ", // Y=25
                    "  EBBCCCBBE                 ", // Y=26
                    "  E  CCC  E                 ", // Y=27
                    "  E  CCC  E                 ", // Y=28
                    "  E  CCC  E                 ", // Y=29
                    "  E  CCC  E                 ", // Y=30
                    "  EBBCCCBBE                 ", // Y=31
                    "  E  CCC  E                 ", // Y=32
                    "  E  CCC  E                 ", // Y=33
                    "  E  CCC  E                 ", // Y=34
                    "  E  CCC  E                 ", // Y=35
                    "  EBBCCCBBE                 ", // Y=36
                    "  E  CCC  E                 ", // Y=37
                    "  E  CCC  E                 ", // Y=38
                    "  E  CCC  E                 ", // Y=39
                    "  E  CCC  E                 ", // Y=40
                    "  EBBCCCBBE                 ", // Y=41
                    "  E  CCC  E                 ", // Y=42
                    "  E  CCC  E                 ", // Y=43
                    "  E  CCC  E                 ", // Y=44
                    "  E  CCC  E                 ", // Y=45
                    "  EBBCCCBBE                 ", // Y=46
                    "  E  CCC  E                 ", // Y=47
                    "  E  CCC  E                 ", // Y=48
                    "     CCC                    ", // Y=49
                    "     CCC                    ", // Y=50
                    "     CCC                    ", // Y=51
                    "      C                     ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=25
                    "  AAAAAAAAA     AAAAAAAAAAA ", // Y=0
                    "    AAAAA       AAMMMMMMMAA ", // Y=1
                    "                 G       G  ", // Y=2
                    "                 G   W   G  ", // Y=3
                    "                 G       G  ", // Y=4
                    "                 G   W   G  ", // Y=5
                    "     BBB        BG       GB ", // Y=6
                    "            V    G   W   G  ", // Y=7
                    "                 G       G  ", // Y=8
                    "                  GG   GG   ", // Y=9
                    "                    GGG     ", // Y=10
                    "     BBB                    ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                     V      ", // Y=15
                    "     BBB                    ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                     V      ", // Y=20
                    "     BBB                    ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "     BBB                    ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "     BBB                    ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "     BBB                    ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "     BBB                    ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "     BBB                    ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=26
                    "    AAAAA       AAAAAAAAAAA ", // Y=0
                    "      A         AAMMMMMMMAA ", // Y=1
                    "                 G       G  ", // Y=2
                    "                 G   W   G  ", // Y=3
                    "                 G       G  ", // Y=4
                    "                 G   W   G  ", // Y=5
                    "                BG       GB ", // Y=6
                    "            V    G   W   G  ", // Y=7
                    "                 G       G  ", // Y=8
                    "                  G     G   ", // Y=9
                    "                   GGGGG    ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                     V      ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                     V      ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=27
                    "     AAAAAAAAAAAAAAAAAAAAAAA", // Y=0
                    "      A        AAMMMMMMMMMAA", // Y=1
                    "                G         G ", // Y=2
                    "                G    W    G ", // Y=3
                    "                G         G ", // Y=4
                    "                G    W    G ", // Y=5
                    "               BG         GB", // Y=6
                    "            V   G    W    G ", // Y=7
                    "                G         G ", // Y=8
                    "                 G       G  ", // Y=9
                    "                  GGGGGGG   ", // Y=10
                    "                     V      ", // Y=11
                    "                     V      ", // Y=12
                    "                     V      ", // Y=13
                    "                     V      ", // Y=14
                    "                     V      ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                     V      ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=28
                    "     AAAAAAAAAAAAAAAAAAAAAAA", // Y=0
                    "      AAAAAAAAAAAMMMMMMMMMAA", // Y=1
                    "            F  BG    W    GB", // Y=2
                    "            F  BGWWWWWWWWWGB", // Y=3
                    "            F  BG    W    GB", // Y=4
                    "            F  BGWWWWWWWWWGB", // Y=5
                    "            VVVVG    W    GB", // Y=6
                    "            V  BGWWWWWWWWWGB", // Y=7
                    "               BG    W    GB", // Y=8
                    "               BBG   W   GBB", // Y=9
                    "                  GGGGGGG   ", // Y=10
                    "                    F F     ", // Y=11
                    "                    F F     ", // Y=12
                    "                    F F     ", // Y=13
                    "                    F F     ", // Y=14
                    "                    F F     ", // Y=15
                    "                    F F     ", // Y=16
                    "                    F F     ", // Y=17
                    "                    F F     ", // Y=18
                    "                    F F     ", // Y=19
                    "                    FVF     ", // Y=20
                    "                    F F     ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=29
                    "     AAAAAAAAAAAAAAAAAAAAAAA", // Y=0
                    "      A        AAMMMMMMMMMAA", // Y=1
                    "                G         G ", // Y=2
                    "                G    W    G ", // Y=3
                    "                G         G ", // Y=4
                    "                G    W    G ", // Y=5
                    "               BG         GB", // Y=6
                    "                G    W    G ", // Y=7
                    "                G         G ", // Y=8
                    "                 G       G  ", // Y=9
                    "                  GGGGGGG   ", // Y=10
                    "                     V      ", // Y=11
                    "                     V      ", // Y=12
                    "                     V      ", // Y=13
                    "                     V      ", // Y=14
                    "                     V      ", // Y=15
                    "                     V      ", // Y=16
                    "                     V      ", // Y=17
                    "                     V      ", // Y=18
                    "                     V      ", // Y=19
                    "                     V      ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=30
                    "    AAAAA       AAAAAAAAAAA ", // Y=0
                    "    AAAAA       AAMMMMMMMAA ", // Y=1
                    "                 G       G  ", // Y=2
                    "                 G   W   G  ", // Y=3
                    "                 G       G  ", // Y=4
                    "                 G   W   G  ", // Y=5
                    "                BG       GB ", // Y=6
                    "                 G   W   G  ", // Y=7
                    "                 G       G  ", // Y=8
                    "                  G     G   ", // Y=9
                    "                   GGGGG    ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=31
                    "  AAAAAAAAA     AAAAAAAAAAA ", // Y=0
                    "  AAAAAAAAA     AAMMMMMMMAA ", // Y=1
                    "                 G       G  ", // Y=2
                    "                 G   W   G  ", // Y=3
                    "                 G       G  ", // Y=4
                    "                 G   W   G  ", // Y=5
                    "                BG       GB ", // Y=6
                    "                 G   W   G  ", // Y=7
                    "                 G       G  ", // Y=8
                    "                  GG   GG   ", // Y=9
                    "                    GGG     ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=32
                    " AAAAAAAAAAA     AAAAAAAAA  ", // Y=0
                    " AAAAAAAAAAA     AAAMMMAAA  ", // Y=1
                    "    F F F         GG   GG   ", // Y=2
                    "   GGGGGGG        GG W GG   ", // Y=3
                    "    F F F         GG   GG   ", // Y=4
                    "    F F F         GG W GG   ", // Y=5
                    "   GGGGGGG       BGG   GGB  ", // Y=6
                    "    F F F         GG W GG   ", // Y=7
                    "                  GG   GG   ", // Y=8
                    "                    GGG     ", // Y=9
                    "                            ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=33
                    " AAAAAAAAAAA      AAAAAAA   ", // Y=0
                    " AAJJJAJJJAA      AAAAAAA   ", // Y=1
                    "   JJJ JJJ          GGG     ", // Y=2
                    "  GJJJ JJJG         GGG     ", // Y=3
                    "   JJJ JJJ          GGG     ", // Y=4
                    "   JJJ JJJ          GGG     ", // Y=5
                    "  GJJJ JJJG       BBGGGBB   ", // Y=6
                    "   JJJ JJJ          GGG     ", // Y=7
                    "   LJL JJJ          GGG     ", // Y=8
                    "       LJL           B      ", // Y=9
                    "                            ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=34
                    "AAAAAAAAAAAAA       AAA     ", // Y=0
                    "AAAJOJAJOJAAA       AAA     ", // Y=1
                    "  FJOJ JOJF          B      ", // Y=2
                    "  GJOJRJOJG          B      ", // Y=3
                    "  FJOJ JOJF          B      ", // Y=4
                    "  FJOJ JOJF          B      ", // Y=5
                    "  GJOJ JOJG         BUB     ", // Y=6
                    "  FJOJ JOJF          B      ", // Y=7
                    "   JJJ JOJ           B      ", // Y=8
                    "       JJJ           B      ", // Y=9
                    "                            ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=35
                    "AAAAAAAAAAAAA       AAA     ", // Y=0
                    "AAAJJJAJJJAAA        A      ", // Y=1
                    "   JJJ JJJ                  ", // Y=2
                    "  GJJJRJJJG                 ", // Y=3
                    "   JJJ JJJ                  ", // Y=4
                    "   JJJ JJJ    F   F         ", // Y=5
                    "  GJJJ JJJG          U      ", // Y=6
                    "   JJJ JJJ                  ", // Y=7
                    "   LJL JJJ                  ", // Y=8
                    "       LJL                  ", // Y=9
                    "                            ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=36
                    "AAAAAAAAAAAAA F   F AAA     ", // Y=0
                    "AAAAAAAAAAAAA F   F  A      ", // Y=1
                    "     R        F   F  U      ", // Y=2
                    "  G  RR   G   F   F  U      ", // Y=3
                    "    PPPPP     F   F  U      ", // Y=4
                    "        PUUUUUUUUUUUUU      ", // Y=5
                    "   GGGGGPG           U      ", // Y=6
                    "    P   P                   ", // Y=7
                    "        P                   ", // Y=8
                    "        P                   ", // Y=9
                    "                            ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=37
                    "AAAAAAAAAAAAA   AAAAAAAAAAA ", // Y=0
                    "AAAJJJAJJJAAA   AAAAAAAAAAA ", // Y=1
                    "   JJJ JJJ                  ", // Y=2
                    "  GJJJRJJJG                 ", // Y=3
                    "   JJJ JJJ                  ", // Y=4
                    "   LJL JJJ    F   F  U      ", // Y=5
                    "       JJJ                  ", // Y=6
                    "    P  LJL                  ", // Y=7
                    "                            ", // Y=8
                    "        P                   ", // Y=9
                    "                            ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=38
                    "AAAAAAAAAAAAA   AAAAAAAAAAA ", // Y=0
                    "AAAJOJAJOJAAA   AAAAAAAAAAA ", // Y=1
                    "  FJOJ JOJF                 ", // Y=2
                    "  GJOJRJOJG                 ", // Y=3
                    "  FJOJ JOJF                 ", // Y=4
                    "   JJJ JOJF          U      ", // Y=5
                    "    P  JOJ                  ", // Y=6
                    "    P  JJJ                  ", // Y=7
                    "        P                   ", // Y=8
                    "        P                   ", // Y=9
                    "                            ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=39
                    "AAAAAAAAAAAAA   AAAAAAAAAAA ", // Y=0
                    "AAAJJJAJJJAAA   AAAAAAAAAAA ", // Y=1
                    "   JJJ JJJ                  ", // Y=2
                    "  GJJJRJJJG         CCC     ", // Y=3
                    "   JJJ JJJ         CCCCC    ", // Y=4
                    "   LJL JJJ         CCUCC    ", // Y=5
                    "       JJJ         CCCCC    ", // Y=6
                    "    P  LJL          CCC     ", // Y=7
                    "                            ", // Y=8
                    "        P                   ", // Y=9
                    "                            ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=40
                    "AAAAAAAAAAAAA   AAAAAAAAAAA ", // Y=0
                    "AAAAAAAAAAAAA   AAAAAAAAAAA ", // Y=1
                    "    F   F          CCCCC    ", // Y=2
                    "  G F R F G       CC   CC   ", // Y=3
                    "    F   F         C     C   ", // Y=4
                    "    F   F         C  N  C   ", // Y=5
                    "    F   F         C     C   ", // Y=6
                    "    P   F         CC   CC   ", // Y=7
                    "        F          CCCCC    ", // Y=8
                    "        P                   ", // Y=9
                    "                            ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=41
                    "AAAAAAAAAAAAA   AAAAAAAAAAA ", // Y=0
                    "AAAJJJAJJJAAA   AAAACCCAAAA ", // Y=1
                    "   JJJ JJJ        CCKLKCC   ", // Y=2
                    "  GJJJRJJJG       C  O  C   ", // Y=3
                    "   JJJ JJJ       CK  O  KC  ", // Y=4
                    "   LJL JJJ       CLOONOOLC  ", // Y=5
                    "       JJJ       CK  O  KC  ", // Y=6
                    "    P  LJL        C  O  C   ", // Y=7
                    "                  CCKLKCC   ", // Y=8
                    "        P           CCC     ", // Y=9
                    "                            ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=42
                    "AAAAAAAAAAAAA   AAAAAAAAAAA ", // Y=0
                    "AAAJOJAJOJAAA   AAAACCCAAAA ", // Y=1
                    "  FJOJ JOJF       CCKLKCC   ", // Y=2
                    "  GJOJRJOJG       C  O  C   ", // Y=3
                    "  FJOJ JOJF      CK     KC  ", // Y=4
                    "   JJJ JOJF      CLO N OLC  ", // Y=5
                    "    P  JOJ       CK     KC  ", // Y=6
                    "    P  JJJ        C  O  C   ", // Y=7
                    "        P         CCKLKCC   ", // Y=8
                    "        P           CCC     ", // Y=9
                    "                            ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=43
                    "AAAAAAAAAAAAA   AAAAAAAAAAA ", // Y=0
                    "AAAJJJAJJJAAA   AAAACCCAAAA ", // Y=1
                    "   JJJ JJJ      TTCCKLKCCTT ", // Y=2
                    "  GJJJRJJJG     TTC  O  CTT ", // Y=3
                    "   JJJ JJJ      TCK  O  KCT ", // Y=4
                    "   LJL JJJ      TCLOONOOLCT ", // Y=5
                    "       JJJ      TCK  O  KCT ", // Y=6
                    "    P  LJL      TFC  O  CFT ", // Y=7
                    "                 FCCKLKCCF  ", // Y=8
                    "        P           CCC     ", // Y=9
                    "                            ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=44
                    "AAAAAAAAAAAAA   AAAAAAAAAAA ", // Y=0
                    "AAAAAAAAAAAAA   AAAACCCAAAA ", // Y=1
                    "    F   F         CCKLKCC   ", // Y=2
                    "  G F R F G       C  O  C   ", // Y=3
                    "    F   F        CK     KC  ", // Y=4
                    "    F   F        CLO N OLC  ", // Y=5
                    "    F   F        CK     KC  ", // Y=6
                    "    P   F         C  O  C   ", // Y=7
                    "        F         CCKLKCC   ", // Y=8
                    "        P           CCC     ", // Y=9
                    "                            ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=45
                    "AAAAAAAAAAAAA F AAAAAAAAAAA ", // Y=0
                    "AAAJJJAJJJAAA F AAAACCCAAAA ", // Y=1
                    "   JJJ JJJ F  F   CCKLKCC   ", // Y=2
                    "  GJJJRJJJGF  F   C  O  C   ", // Y=3
                    "   JJJ JJJ F  F  CK  O  KC  ", // Y=4
                    "   JJJ JJJ F  F  CLOONOOLC  ", // Y=5
                    "   LJL JJJ       CK  O  KC  ", // Y=6
                    "    P  JJJ        C  O  C   ", // Y=7
                    "       LJL        CCKLKCC   ", // Y=8
                    "        P           CCC     ", // Y=9
                    "                            ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=46
                    "AAAAAAAAAAAAA   AAAAAAAAAAA ", // Y=0
                    "AAAJOJAJOJAAA   AAAACCCAAAA ", // Y=1
                    "  FJOJ JOJF       CCKLKCC   ", // Y=2
                    "  GJOJRJOJG       C  O  C   ", // Y=3
                    "  FJOJ JOJFF  F  CK     KC  ", // Y=4
                    "  FJOJ JOJUUUUUU CLO N OLC  ", // Y=5
                    "   JJJ JOJ       CK     KC  ", // Y=6
                    "    P  JOJ        C  O  C   ", // Y=7
                    "       JJJ        CCKLKCC   ", // Y=8
                    "        P           CCC     ", // Y=9
                    "                            ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=47
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAA ", // Y=0
                    "AAAJJJAJJJAAA F AAAACCCAAAA ", // Y=1
                    "   JJJ JJJ F  F TTCCKLKCCTT ", // Y=2
                    "  GJJJ JJJGF  F TTC  O  CTT ", // Y=3
                    "   JJJ JJJ F  F TCK  O  KCT ", // Y=4
                    "   JJJ JJJ F  FUTCLOONOOLCT ", // Y=5
                    "   LJL JJJ      TCK  O  KCT ", // Y=6
                    "    P  JJJ      TFC  O  CFT ", // Y=7
                    "       LJL       FCCKLKCCF  ", // Y=8
                    "        P           CCC     ", // Y=9
                    "                            ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=48
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAA ", // Y=0
                    "AAAAAAAAAAAAAAAAAAAACCCAAAA ", // Y=1
                    "    F   F         CCKLKCC   ", // Y=2
                    "   GGGGGGG        C  O  C   ", // Y=3
                    "    F   F        CK     KC  ", // Y=4
                    "    F   F      U CLO N OLC  ", // Y=5
                    "                 CK     KC  ", // Y=6
                    "    P             C  O  C   ", // Y=7
                    "                  CCKLKCC   ", // Y=8
                    "        P           CCC     ", // Y=9
                    "                            ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=49
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAA ", // Y=0
                    "AAADDDADDDAAA   AAAACCCAAAA ", // Y=1
                    "   DDD DDD        CCKLKCC   ", // Y=2
                    "   DDD DDD        C  O  C   ", // Y=3
                    "  EDDD DDDE      CK  O  KC  ", // Y=4
                    "  EDDD DDDE    U CLOONOOLC  ", // Y=5
                    "  EDDD DDDE      CK  O  KC  ", // Y=6
                    "  EDDD DDDE       C  O  C   ", // Y=7
                    "  EDDD DDDE       CCKLKCC   ", // Y=8
                    "   DDD DDD          CCC     ", // Y=9
                    "   DDD DDD                  ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=50
                    "AAAAAAAAAAAAA   AAAAAAAAAAA ", // Y=0
                    "AADKKKDKKKDAA   AAAAAAAAAAA ", // Y=1
                    "  D   D   D        CCCCC    ", // Y=2
                    "  DKKKDKKKD       CC   CC   ", // Y=3
                    "  D   D   D       C     C   ", // Y=4
                    "  DKKKDKKKD    U  C  N  C   ", // Y=5
                    "  D   D   D       C     C   ", // Y=6
                    "  DKKKDKKKD       CC   CC   ", // Y=7
                    "  D   D   D        CCCCC    ", // Y=8
                    "  DKKKDKKKD                 ", // Y=9
                    "  D   D   D                 ", // Y=10
                    "   DDD DDD                  ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=51
                    "AAAAAAAAAAAAA F AAAAAAAAAAA ", // Y=0
                    "AADKOKDKOKDAA F AAAAAAAAAAA ", // Y=1
                    "  D O D O D   F F           ", // Y=2
                    "  HKOKDKOKH   F F   CCC     ", // Y=3
                    "  H O D O H   FFF  CCCCC    ", // Y=4
                    "  HKOKDKOKH   FUF  CCUCC    ", // Y=5
                    "  H O D O H        CCCCC    ", // Y=6
                    "  HKOKDKOKH         CCC     ", // Y=7
                    "  H O D O H                 ", // Y=8
                    "  HKOKDKOKH                 ", // Y=9
                    "  D O D O D                 ", // Y=10
                    "   DQD DQD                  ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=52
                    "AAAAAAAAAAAAA   AAAAAAAAAAA ", // Y=0
                    "AADKKKDKKKDAA   AAAAAAAAAAA ", // Y=1
                    "  D   D   D                 ", // Y=2
                    "  DKKKDKKKD                 ", // Y=3
                    "  D   D   D     F  F        ", // Y=4
                    "  DKKKDKKKD    UUUUUUU      ", // Y=5
                    "  D   D   D                 ", // Y=6
                    "  DKKKDKKKD                 ", // Y=7
                    "  D   D   D                 ", // Y=8
                    "  DKKKDKKKD                 ", // Y=9
                    "  D   D   D                 ", // Y=10
                    "   DDD DDD                  ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=53
                    "AAAAAAAAAAAAA   AAAAAAAAAAA ", // Y=0
                    "AAADDDDDDDAAA   AAAAAAAAAAA ", // Y=1
                    "   DDDDDDD      F  F        ", // Y=2
                    "   DDDDDDD      F  F        ", // Y=3
                    "  CDDDDDDDC     F  F        ", // Y=4
                    "   DDDDDDD      F  F        ", // Y=5
                    "  CDDDDDDDC                 ", // Y=6
                    "   DDDDDDD                  ", // Y=7
                    "  CDDDDDDDC                 ", // Y=8
                    "   DDDDDDD                  ", // Y=9
                    "   DDDDDDD                  ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=54
                    " AAAAAAAAAAA         AAA    ", // Y=0
                    " AAADKKKDAAA                ", // Y=1
                    "    D   D                   ", // Y=2
                    "    DKKKD                   ", // Y=3
                    "   CD   DC                  ", // Y=4
                    "    DKKKD                   ", // Y=5
                    "   CD   DC                  ", // Y=6
                    "    DKKKD                   ", // Y=7
                    "   CD   DC                  ", // Y=8
                    "    DKKKD                   ", // Y=9
                    "    D   D                   ", // Y=10
                    "     DDD                    ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=55
                    " AAAAAAAAAAA        FAAAF   ", // Y=0
                    " AAADKOKDAAA        F   F   ", // Y=1
                    "    D O D           F   F   ", // Y=2
                    "    HKOKH           F   F   ", // Y=3
                    "    H O H           F   F   ", // Y=4
                    "    HKOKH                   ", // Y=5
                    "    H O H                   ", // Y=6
                    "    HKOKH                   ", // Y=7
                    "    H O H                   ", // Y=8
                    "    HKOKH                   ", // Y=9
                    "    D O D                   ", // Y=10
                    "     DQD                    ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=56
                    "  AAAAAAAAA        AAAAAAA  ", // Y=0
                    "  AADKKKDAA        BDDDDDB  ", // Y=1
                    "    D   D          BDDDDDB  ", // Y=2
                    "    DKKKD          BDDDDDB  ", // Y=3
                    "    D   D          BDDDDDB  ", // Y=4
                    "    DKKKD           F   F   ", // Y=5
                    "    D   D                   ", // Y=6
                    "    DKKKD                   ", // Y=7
                    "    D   D                   ", // Y=8
                    "    DKKKD                   ", // Y=9
                    "    D   D                   ", // Y=10
                    "     DDD                    ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=57
                    "   AAAAAAA         AAAAAAA  ", // Y=0
                    "   AADDDAA          XINIX   ", // Y=1
                    "     DDD            XINIX   ", // Y=2
                    "     DHD            XINIX   ", // Y=3
                    "    EDHDE          BDDDDDB  ", // Y=4
                    "    EDHDE           T   T   ", // Y=5
                    "    EDHDE                   ", // Y=6
                    "    EDHDE                   ", // Y=7
                    "    EDHDE                   ", // Y=8
                    "     DHD                    ", // Y=9
                    "     DDD                    ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=58
                    "    aaaaa         taaaaaaat ", // Y=0
                    "    aaaaa         t XXXXX t ", // Y=1
                    "                  t XXYXX t ", // Y=2
                    "                  t XXXXX t ", // Y=3
                    "                  tbdddddbt ", // Y=4
                    "                  ttttttttt ", // Y=5
                    "                            ", // Y=6
                    "                            ", // Y=7
                    "                            ", // Y=8
                    "                            ", // Y=9
                    "                            ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
            { // Z=59
                    "                   aaaaaaa  ", // Y=0
                    "                   bt   tb  ", // Y=1
                    "                   bt   tb  ", // Y=2
                    "                   bt   tb  ", // Y=3
                    "                   btbbbtb  ", // Y=4
                    "                    t   t   ", // Y=5
                    "                            ", // Y=6
                    "                            ", // Y=7
                    "                            ", // Y=8
                    "                            ", // Y=9
                    "                            ", // Y=10
                    "                            ", // Y=11
                    "                            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                            ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                            ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                            ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                            ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                            ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                            ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                            ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
            },
    };

    /**
     * 结构数据里允许出现的符号全集（必须与 {@link #build} 里的 {@code .where(...)} 一一对应）。
     * 加字符时两处都要改。
     */
    /** GTO 模块并入后新增的结构符号（数字/标点，都有对应 .where 绑定）。 */
    private static final String UPGRADED_EXTRA_SYMBOLS = "!#$%&()*0123456789=?~@";

    private static final String DECLARED_SYMBOLS = " ABCDEFGHIJKLMNOPQRSTUVWXYabdt";

    /** 结构总格数（自检用）。 */
    public static final int CELLS = WIDTH * HEIGHT * LENGTH;

    /**
     * 构建结构。{@code FactoryBlockPattern.start()} 用 GTCEu 1.4.4 的默认轴
     * {@code (LEFT, UP, FRONT)} —— 与 mbs 头部 {@code charDir=LEFT / stringDir=UP / aisleDir=FRONT} 一致，
     * 所以<b>直填，不重排</b>。
     */
    public static BlockPattern build(MultiblockMachineDefinition definition) {
        verifySymbols(SLICES, "基础", WIDTH, LENGTH, "");

        // ── X 位：GTO 的 autoLaserAbilities(recipeTypes) + MAINTENANCE ──
        // 20 格，够放 进出仓 / 能源 / 激光 / 维护。
        TraceabilityPredicate mainHatchFace = blocksOf("gtceu", "high_power_casing")
                .or(ability(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                .or(ability(PartAbility.EXPORT_ITEMS).setPreviewCount(1))
                .or(ability(PartAbility.IMPORT_FLUIDS).setPreviewCount(1))
                .or(ability(PartAbility.EXPORT_FLUIDS).setPreviewCount(1))
                .or(ability(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(8, 0))
                .or(ability(PartAbility.INPUT_LASER).setMaxGlobalLimited(4, 1))
                .or(ability(PartAbility.MAINTENANCE).setExactLimit(1));

        // ── 附属模块口：线程仓 / 超频仓 / 加速仓 / 并行仓 + IO ──
        TraceabilityPredicate moduleHatches = ability(com.gtlmodular.common.data.thread.ThreadHatchMachines.THREAD_HATCH)
                .setMaxGlobalLimited(1)
                .or(com.gtlmodular.common.data.fuel.OverclockHatches.predicate())
                .or(com.gtlmodular.common.data.fuel.AccelerateHatches.predicate())
                .or(ability(PartAbility.PARALLEL_HATCH).setMaxGlobalLimited(1).setPreviewCount(1))
                .or(ability(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                .or(ability(PartAbility.EXPORT_ITEMS).setPreviewCount(1))
                .or(ability(PartAbility.IMPORT_FLUIDS).setPreviewCount(1))
                .or(ability(PartAbility.EXPORT_FLUIDS).setPreviewCount(1));

        FactoryBlockPattern pattern = FactoryBlockPattern.start();
        for (String[] slice : SLICES) {
            pattern.aisle(slice);
        }
        return pattern
                .where(' ', Predicates.any())
                .where('Y', Predicates.controller(Predicates.blocks(definition.get())))
                // 应力结构外壳（GCYM）
                .where('A', blocksOf("gtceu", "stress_proof_casing"))
                // 抗氧化哈氏合金-N 机械方块（gtlcore 已有同 id 镜像）
                .where('B', blocksOf("gtlcore", "oxidation_resistant_hastelloy_n_mechanical_casing"))
                // ★ 压力容器机械方块（GTO 专有 ⇒ 本模组自造）
                .where('C', Predicates.blocks(WoodFactoryBlocks.PRESSURE_CONTAINMENT_CASING.get()))
                // 铱机械方块（gtlcore 同 id）
                .where('D', blocksOf("gtlcore", "iridium_casing"))
                // Ruridit / 不锈钢 / 硅岩合金框架（材料框架，按材料现取，取不到退回同名 id）
                .where('E', frame("ruridit"))
                .where('F', frame("stainless_steel"))
                .where('G', blocksOf("gtceu", "watertight_casing"))
                .where('H', blocksOf("gtceu", "fusion_glass"))
                .where('I', blocksOf("gtceu", "zpm_hermetic_casing"))
                .where('J', blocksOf("gtceu", "inert_machine_casing"))
                // 任意加热线圈（GTCEu 会把 CoilType 写进 match context，机器侧据此算并行）
                .where('K', Predicates.heatingCoils())
                .where('L', frame("naquadah_alloy"))
                .where('M', blocksOf("gtceu", "corrosion_proof_casing"))
                // ★ 哈氏合金-N75 油膜管道（GTO 专有）
                .where('N', Predicates.blocks(WoodFactoryBlocks.HASTELLOY_N_75_PIPE.get()))
                .where('O', blocksOf("gtceu", "electrolytic_cell"))
                // ★ 耐化学腐蚀管道机械方块（GTO 专有）
                .where('P', Predicates.blocks(
                        WoodFactoryBlocks.CHEMICAL_CORROSION_RESISTANT_PIPE_CASING.get()))
                .where('Q', Predicates.abilities(PartAbility.MUFFLER))
                .where('R', blocksOf("gtceu", "steel_turbine_casing"))
                .where('S', blocksOf("gtceu", "tungstensteel_pipe_casing"))
                .where('T', blocksOf("gtlcore", "process_machine_casing"))
                // ★ 耐高压管道机械方块（GTO 专有）
                .where('U', Predicates.blocks(WoodFactoryBlocks.HIGH_PRESSURE_PIPE_CASING.get()))
                // ★ 油气输送密封管道机械方块（GTO 专有）
                .where('V', Predicates.blocks(
                        WoodFactoryBlocks.OIL_GAS_TRANSPORTATION_PIPE_CASING.get()))
                .where('W', blocksOf("gtceu", "ptfe_pipe_casing"))
                .where('X', mainHatchFace)
                // ── 附属模块口（Z=58/59 控制舱面，原 A/T/B/D 改写为小写）──
                .where('a', blocksOf("gtceu", "stress_proof_casing").or(moduleHatches))
                .where('b', blocksOf("gtlcore", "oxidation_resistant_hastelloy_n_mechanical_casing")
                        .or(moduleHatches))
                .where('t', blocksOf("gtlcore", "process_machine_casing").or(moduleHatches))
                .where('d', blocksOf("gtlcore", "iridium_casing").or(moduleHatches))
                .build();
    }

    /** 结构自检：数据里出现的每个字符都必须在 {@link #DECLARED_SYMBOLS} 里。 */
    /**
     * 结构自检：数据里出现的每个字符都必须在 {@link #DECLARED_SYMBOLS} 里。
     * 基础与升级切片各自校验：二者的宽度/片数可能不同（GTO 模块并入后扩展了结构）。
     */
    private static void verifySymbols(String[][] slices, String tag, int width, int length,
                                      String extraSymbols) {
        if (slices.length != length) {
            throw new IllegalStateException("木质生物质工厂 " + tag + "结构：共 " + slices.length + " 片，应为 " + length);
        }
        String allowed = DECLARED_SYMBOLS + extraSymbols;
        for (int z = 0; z < slices.length; z++) {
            String[] rows = slices[z];
            if (rows.length != HEIGHT) {
                throw new IllegalStateException("木质生物质工厂 " + tag + "结构：Z=" + z + " 只有 " + rows.length
                        + " 行，应为 " + HEIGHT);
            }
            for (int y = 0; y < rows.length; y++) {
                String row = rows[y];
                if (row.length() != width) {
                    throw new IllegalStateException("木质生物质工厂 " + tag + "结构：SLICES[" + z + "][" + y + "] 行长 "
                            + row.length() + "，应为 " + width);
                }
                for (int x = 0; x < row.length(); x++) {
                    char c = row.charAt(x);
                    if (c != ' ' && allowed.indexOf(c) < 0) {
                        throw new IllegalStateException(String.format(
                                "木质生物质工厂 " + tag + "结构：符号 '%c'（0x%02X）在 SLICES[%d][%d] 第 %d 列出现，"
                                        + "但没有对应的 .where(...) 绑定。allowed=%s。",
                                c, (int) c, z, y, x, allowed));
                    }
                }
            }
        }
    }

    /** 按 id 取方块谓词；取不到时 WARN 并退回 {@code any()}（不让注册期 NPE）。 */
    private static TraceabilityPredicate blocksOf(String namespace, String path) {
        Block block = net.minecraftforge.registries.ForgeRegistries.BLOCKS
                .getValue(new net.minecraft.resources.ResourceLocation(namespace, path));
        if (block == null || block == Blocks.AIR) {
            GTLModular.LOGGER.warn("[gtlmodular] 集成木质生物质工厂：方块 {}/{} 不存在，该符号退回 any()",
                    namespace, path);
            return Predicates.any();
        }
        return Predicates.blocks(block);
    }

    /** 材料框架（{@code gtceu:<material>_frame}），取不到时退回 {@code any()}。 */
    private static TraceabilityPredicate frame(String material) {
        return blocksOf("gtceu", material + "_frame");
    }

    /** 能力谓词（低开销版：预览只实例化标准仓，语义等价）。 */
    private static TraceabilityPredicate ability(PartAbility ability) {
        return HatchPredicates.lowCostAbilities(ability);
    }

    /** GTO 可选模块并入后的切片：下列数字/符号格为二级结构格。 */
    private static final String[][] UPGRADED_SLICES = {
            {
                    "    AAAAA     666666   666666",
                    "              8    8   8    8",
                    "              8    8   8    8",
                    "              8    8   8    8",
                    "              8    8   8    8",
                    "              8    8   8    8",
                    "              666666   666666",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "  AAAAAAAAA   666666   666666",
                    "    AAAAA     $$$$$%   %$$$$$",
                    "                   %   %     ",
                    "              $$$$$%   %$$$$$",
                    "                   %   %     ",
                    "              $$$$$%   %$$$$$",
                    "     BBB      6%%%%6   6%%%%6",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "     BBB                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "     BBB                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "     BBB                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "     BBB                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "     BBB                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "     BBB                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "     BBB                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "     BBB                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    " AAAAAAAAAAA  66AAAAAAAAAAA66",
                    "  AAACCCAAA   $&AAAAAAAAAAA&$",
                    "  E  CCC  E    &&&&8   8&&&& ",
                    "  E  CCC  E   $&&&&8   8&&&&$",
                    "  E  CCC  E    &&&&FTTTF&&&& ",
                    "  E  CCC  E   $&&&&8   8&&&&$",
                    "  EBBCCCBBE   6%6666   6666%6",
                    "  E  CCC  E        FTTTF     ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E        FTTTF     ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "     CCC                     ",
                    "     CCC                     ",
                    "     CCC                     ",
                    "      C                      ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    " AAAAAAAAAAA  66AAAAAAAAAAA66",
                    "  AACIIICAA   $&AAAACCCAAAA&$",
                    "   CC   CC     &% L CCC L %& ",
                    "   CC   CC    $&% L CCC L %&$",
                    "   CC   CC     &% LTCCCTL %& ",
                    "   CC G CC    $&% L CCC L %&$",
                    "  BCCMNMCCB   6%6 L CCC L 6%6",
                    "   CC   CC        LTCCCTL    ",
                    "   CC   CC        L CCC L    ",
                    "   CC   CC        L CCC L    ",
                    "   CC G CC        LTCCCTL    ",
                    "  BCCMNMCCB       L CCC L    ",
                    "   CC   CC        L CCC L    ",
                    "   CC   CC          CCC      ",
                    "   CC   CC                   ",
                    "   CC G CC                   ",
                    "  BCCMNMCCB                  ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC G CC                   ",
                    "  BCCMNMCCB                  ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC G CC                   ",
                    "  BCCMNMCCB                  ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC G CC                   ",
                    "  BCCMNMCCB                  ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC G CC                   ",
                    "  BCCMNMCCB                  ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC G CC                   ",
                    "  BCCMNMCCB                  ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC G CC                   ",
                    "  BCCMNMCCB                  ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "    CCCCC                    ",
                    "     CCC                     ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAA 66AAAAAAAAAAA66",
                    " AACIIIIICAA  $&AAACIIICAAA&$",
                    "   C     C     &%  C   C  %& ",
                    "   C     C    $&%  C G C  %&$",
                    "   C     C     &%FTCMNMCTF%& ",
                    "   C  G  C    $&%  C   C  %&$",
                    "  BCMMNMMCB   6%6  C G C  6%6",
                    "   C     C       FTCMNMCTF   ",
                    "   C     C         C   C     ",
                    "   C     C         C G C     ",
                    "   C  G  C       FTCMNMCTF   ",
                    "  BCMMNMMCB        C   C     ",
                    "   C     C         C   C     ",
                    "   C     C         C   C     ",
                    "   C     C          CCC      ",
                    "   C  G  C                   ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C  G  C                   ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C  G  C                   ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C  G  C                   ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C  G  C                   ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C  G  C                   ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C  G  C                   ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "    C   C                    ",
                    "     CCC                     ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAAF66AAAAAAAAAAA66",
                    " ACIIIIIIICA F$&AACIIIIICAA&$",
                    "  C       C  F &% C     C %& ",
                    "  C       C  F$&% C  G  C %&$",
                    "  C       C  F &%TCMMNMMCT%& ",
                    "  C   G   C  F$&% C     C %&$",
                    " BCMMMNMMMCB F6%6 C  G  C 6%6",
                    "  C       C  F   TCMMNMMCT   ",
                    "  C       C  F    C     C    ",
                    "  C       C  F    C  G  C    ",
                    "  C   G   C  F   TCMMNMMCT   ",
                    " BCMMMNMMMCB F    C     C    ",
                    "  C       C  F    C     C    ",
                    "  C       C  F    C     C    ",
                    "  C       C  F     C   C     ",
                    "  C   G   C  F      CCC      ",
                    " BCMMMNMMMCB F               ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C                  ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C                  ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C                  ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C                  ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C                  ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C                  ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "    C   C                    ",
                    "     CCC                     ",
            },
            {
                    "AAAAAAAAAAAAA 66AAAAAAAAAAA66",
                    " ACIIIIIIICA  $&AACIIIIICAA&$",
                    "  C   S   C    &% C  S  C %& ",
                    "  C   S   C   $&% CGGSGGC %&$",
                    "  C   S   C    &%TCNNSNNCT%& ",
                    "  CGGGSGGGC   $&% C  S  C %&$",
                    " BCNNNSNNNCBF 6%6 CGGSGGC 6%6",
                    "  C   S   CVV    TCNNSNNCT   ",
                    "  C   S   C F     C  S  C    ",
                    "  C   S   C       CGGSGGC    ",
                    "  CGGGSGGGC      TCNNSNNCT   ",
                    " BCNNNSNNNCB      C  S  C    ",
                    "  C   S   C       C  S  C    ",
                    "  C   S   C       C  S  C    ",
                    "  C   S   C  F     C S C     ",
                    "  CGGGSGGGCVVV      CQC      ",
                    " BCNNNSNNNCB F               ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  CGGGSGGGC                  ",
                    " BCNNNSNNNCB                 ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  CGGGSGGGC                  ",
                    " BCNNNSNNNCB                 ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  CGGGSGGGC                  ",
                    " BCNNNSNNNCB                 ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  CGGGSGGGC                  ",
                    " BCNNNSNNNCB                 ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  CGGGSGGGC                  ",
                    " BCNNNSNNNCB                 ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  CGGGSGGGC                  ",
                    " BCNNNSNNNCB                 ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  CC  S  CC                  ",
                    "   C  S  C                   ",
                    "    C S C                    ",
                    "     CQC                     ",
            },
            {
                    "AAAAAAAAAAAAA 66AAAAAAAAAAA66",
                    " ACIIIIIIICA  8%AACIIIIICAA%8",
                    "  C       C   8%8 C     C 8%8",
                    "  C       C   8%8 C  G  C 8%8",
                    "  C       C   8%8TCMMNMMCT8%8",
                    "  C   G   C   8%8 C     C 8%8",
                    " BCMMMNMMMCB  666 C  G  C 666",
                    "  C       C V    TCMMNMMCT   ",
                    "  C       C       C     C    ",
                    "  C       C       C  G  C    ",
                    "  C   G   C      TCMMNMMCT   ",
                    " BCMMMNMMMCB      C     C    ",
                    "  C       C       C     C    ",
                    "  C       C       C     C    ",
                    "  C       C        C   C     ",
                    "  C   G   C  V      CCC      ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C                  ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C                  ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C                  ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C                  ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C                  ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C                  ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "    C   C                    ",
                    "     CCC                     ",
            },
            {
                    "AAAAAAAAAAAAA   AAAAAAAAAAA  ",
                    " AACIIIIICAA    AAACIIICAAA  ",
                    "   C     C         C   C     ",
                    "   C     C         C G C     ",
                    "   C     C       FTCMNMCTF   ",
                    "   C  G  C         C   C     ",
                    "  BCMMNMMCB        C G C     ",
                    "   C     C  V    FTCMNMCTF   ",
                    "   C     C         C   C     ",
                    "   C     C         C G C     ",
                    "   C  G  C       FTCMNMCTF   ",
                    "  BCMMNMMCB        C   C     ",
                    "   C     C         C   C     ",
                    "   C     C         C   C     ",
                    "   C     C          CCC      ",
                    "   C  G  C   V               ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C  G  C                   ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C  G  C                   ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C  G  C                   ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C  G  C                   ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C  G  C                   ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C  G  C                   ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "    C   C                    ",
                    "     CCC                     ",
                    "                             ",
            },
            {
                    " AAAAAAAAAAA    AAAAAAAAAAA  ",
                    "  AACIIICAA     AAAACCCAAAA  ",
                    "   CC   CC        L CCC L    ",
                    "   CC   CC        L CCC L    ",
                    "   CC   CC        LTCCCTL    ",
                    "   CC G CC        L CCC L    ",
                    "  BCCMNMCCB       L CCC L    ",
                    "   CC   CC  VF    LTCCCTL    ",
                    "   CC   CC        L CCC L    ",
                    "   CC   CC        L CCC L    ",
                    "   CC G CC        LTCCCTL    ",
                    "  BCCMNMCCB       L CCC L    ",
                    "   CC   CC        L CCC L    ",
                    "   CC   CC          CCC      ",
                    "   CC   CC   F               ",
                    "   CC G CC   VF              ",
                    "  BCCMNMCCB  FF              ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC G CC                   ",
                    "  BCCMNMCCB                  ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC G CC                   ",
                    "  BCCMNMCCB                  ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC G CC                   ",
                    "  BCCMNMCCB                  ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC G CC                   ",
                    "  BCCMNMCCB                  ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC G CC                   ",
                    "  BCCMNMCCB                  ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC G CC                   ",
                    "  BCCMNMCCB                  ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "    CCCCC                    ",
                    "     CCC                     ",
                    "                             ",
                    "                             ",
            },
            {
                    " AAAAAAAAAAA    AAAAAAAAAAA  ",
                    "  AAACCCAAA     AAAAAAAAAAA  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E        FTTTF     ",
                    "  E  CCC  E                  ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E V      FTTTF     ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E        FTTTF     ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E        FTTTF     ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E  V               ",
                    "  EBBCCCBBE        FTTTF     ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E        FTTTF     ",
                    "  E  CCC  E                  ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "     CCC                     ",
                    "     CCC                     ",
                    "     CCC                     ",
                    "      C                      ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "  AAAAAAAAA     AAAAAAAAAAA  ",
                    "    AAAAA       AAAACCCAAAA  ",
                    "      TF          L CCC L    ",
                    "      T           L CCC L    ",
                    "      T           LTCCCTL    ",
                    "      T           L CCC L    ",
                    "     BBB          L CCC L    ",
                    "     FT     V     LTCCCTL    ",
                    "      T           L CCC L    ",
                    "      T           L CCC L    ",
                    "      T           LTCCCTL    ",
                    "     BBB          L CCC L    ",
                    "      TF          L CCC L    ",
                    "      T           LTCCCTL    ",
                    "      T           L CCC L    ",
                    "      T      V    L CCC L    ",
                    "     BBB          LTCCCTL    ",
                    "     FT           L CCC L    ",
                    "      T           L CCC L    ",
                    "      T           LTCCCTL    ",
                    "      T           L CCC L    ",
                    "     BBB          L CCC L    ",
                    "      TF            CCC      ",
                    "      T                      ",
                    "      T                      ",
                    "      T                      ",
                    "     BBB                     ",
                    "     FT                      ",
                    "      T                      ",
                    "      T                      ",
                    "      T                      ",
                    "     BBB                     ",
                    "      TF                     ",
                    "      T                      ",
                    "      T                      ",
                    "      T                      ",
                    "     BBB                     ",
                    "     FT                      ",
                    "      T                      ",
                    "      T                      ",
                    "      T                      ",
                    "     BBB                     ",
                    "       F                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "     BBB                     ",
                    "     F                       ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "    AAAAA       AAAAAAAAAAA  ",
                    "      A         AAACIIICAAA  ",
                    "                   C   C     ",
                    "       F           C G C     ",
                    "                 FTCMNMCTF   ",
                    "     F             C   C     ",
                    "      B            C G C     ",
                    "            V    FTCMNMCTF   ",
                    "     F             C   C     ",
                    "                   C G C     ",
                    "       F         FTCMNMCTF   ",
                    "      B            C   C     ",
                    "                   C G C     ",
                    "       F         FTCMNMCTF   ",
                    "                   C   C     ",
                    "     F       V     C G C     ",
                    "      B          FTCMNMCTF   ",
                    "                   C   C     ",
                    "     F             C G C     ",
                    "                 FTCMNMCTF   ",
                    "       F           C   C     ",
                    "      B            C   C     ",
                    "                   C   C     ",
                    "       F            CCC      ",
                    "                             ",
                    "     F                       ",
                    "      B                      ",
                    "                             ",
                    "     F                       ",
                    "                             ",
                    "       F                     ",
                    "      B                      ",
                    "                             ",
                    "       F                     ",
                    "                             ",
                    "     F                       ",
                    "      B                      ",
                    "                             ",
                    "     F                       ",
                    "                             ",
                    "       F                     ",
                    "      B                      ",
                    "                             ",
                    "       F                     ",
                    "                             ",
                    "     F                       ",
                    "      B                      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "     AAA        AAAAAAAAAAA  ",
                    "      A         AACIIIIICAA  ",
                    "                  C     C    ",
                    "                  C  G  C    ",
                    "     F F         TCMMNMMCT   ",
                    "                  C     C    ",
                    "      B           C  G  C    ",
                    "            V    TCMMNMMCT   ",
                    "                  C     C    ",
                    "     F F          C  G  C    ",
                    "                 TCMMNMMCT   ",
                    "      B           C     C    ",
                    "                  C  G  C    ",
                    "                 TCMMNMMCT   ",
                    "     F F          C     C    ",
                    "             V    C  G  C    ",
                    "      B          TCMMNMMCT   ",
                    "                  C     C    ",
                    "                  C  G  C    ",
                    "     F F         TCMMNMMCT   ",
                    "                  C     C    ",
                    "      B           C     C    ",
                    "                  C     C    ",
                    "                   C   C     ",
                    "     F F            CCC      ",
                    "                             ",
                    "      B                      ",
                    "                             ",
                    "                             ",
                    "     F F                     ",
                    "                             ",
                    "      B                      ",
                    "                             ",
                    "                             ",
                    "     F F                     ",
                    "                             ",
                    "      B                      ",
                    "                             ",
                    "                             ",
                    "     F F                     ",
                    "                             ",
                    "      B                      ",
                    "                             ",
                    "                             ",
                    "     F F                     ",
                    "                             ",
                    "      B                      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "    AAAAA       AAAAAAAAAAA  ",
                    "      A         AACIIIIICAA  ",
                    "                  C  S  C    ",
                    "     F            CGGSGGC    ",
                    "                 TCNNSNNCT   ",
                    "       F          C  S  C    ",
                    "      B           CGGSGGC    ",
                    "            VF   TCNNSNNCT   ",
                    "       F          C  S  C    ",
                    "                  CGGSGGC    ",
                    "     F           TCNNSNNCT   ",
                    "      B           C  S  C    ",
                    "                  CGGSGGC    ",
                    "     F           TCNNSNNCT   ",
                    "             F    C  S  C    ",
                    "       F     VF   CGGSGGC    ",
                    "      B      FF  TCNNSNNCT   ",
                    "                  C  S  C    ",
                    "       F          CGGSGGC    ",
                    "                 TCNNSNNCT   ",
                    "     F            C  S  C    ",
                    "      B           C  S  C    ",
                    "                  C  S  C    ",
                    "     F             C S C     ",
                    "                    CQC      ",
                    "       F                     ",
                    "      B                      ",
                    "                             ",
                    "       F                     ",
                    "                             ",
                    "     F                       ",
                    "      B                      ",
                    "                             ",
                    "     F                       ",
                    "                             ",
                    "       F                     ",
                    "      B                      ",
                    "                             ",
                    "       F                     ",
                    "                             ",
                    "     F                       ",
                    "      B                      ",
                    "                             ",
                    "     F                       ",
                    "                             ",
                    "       F                     ",
                    "      B                      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "  AAAAAAAAA   66AAAAAAAAAAA66",
                    "    AAAAA     8%AACIIIIICAA%8",
                    "     FT       8%8 C     C 8%8",
                    "      T       8%8 C  G  C 8%8",
                    "      T       8%8TCMMNMMCT8%8",
                    "      T       8%8 C     C 8%8",
                    "     BBB      666 C  G  C 666",
                    "      TF    V    TCMMNMMCT   ",
                    "      T           C     C    ",
                    "      T           C  G  C    ",
                    "      T          TCMMNMMCT   ",
                    "     BBB          C     C    ",
                    "     FT           C  G  C    ",
                    "      T          TCMMNMMCT   ",
                    "      T           C     C    ",
                    "      T      V    C  G  C    ",
                    "     BBB         TCMMNMMCT   ",
                    "      TF          C     C    ",
                    "      T           C  G  C    ",
                    "      T          TCMMNMMCT   ",
                    "      T           C     C    ",
                    "     BBB          C     C    ",
                    "     FT           C     C    ",
                    "      T            C   C     ",
                    "      T             CCC      ",
                    "      T                      ",
                    "     BBB                     ",
                    "      TF                     ",
                    "      T                      ",
                    "      T                      ",
                    "      T                      ",
                    "     BBB                     ",
                    "     FT                      ",
                    "      T                      ",
                    "      T                      ",
                    "      T                      ",
                    "     BBB                     ",
                    "      TF                     ",
                    "      T                      ",
                    "      T                      ",
                    "      T                      ",
                    "     BBB                     ",
                    "     F                       ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "     BBB                     ",
                    "       F                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    " AAAAAAAAAAA  66AAAAAAAAAAA66",
                    "  AAACCCAAA   $&AAACIIICAAA&$",
                    "  E  CCC  E    &%  C   C  %& ",
                    "  E  CCC  E   $&%  C G C  %&$",
                    "  E  CCC  E    &%FTCMNMCTF%& ",
                    "  E  CCC  E   $&%  C   C  %&$",
                    "  EBBCCCBBE   6%6  C G C  6%6",
                    "  E  CCC  E V    FTCMNMCTF   ",
                    "  E  CCC  E        C   C     ",
                    "  E  CCC  E        C G C     ",
                    "  E  CCC  E      FTCMNMCTF   ",
                    "  EBBCCCBBE        C   C     ",
                    "  E  CCC  E        C G C     ",
                    "  E  CCC  E      FTCMNMCTF   ",
                    "  E  CCC  E        C   C     ",
                    "  E  CCC  E  V     C G C     ",
                    "  EBBCCCBBE      FTCMNMCTF   ",
                    "  E  CCC  E        C   C     ",
                    "  E  CCC  E        C G C     ",
                    "  E  CCC  E      FTCMNMCTF   ",
                    "  E  CCC  E        C   C     ",
                    "  EBBCCCBBE        C   C     ",
                    "  E  CCC  E        C   C     ",
                    "  E  CCC  E         CCC      ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "     CCC                     ",
                    "     CCC                     ",
                    "     CCC                     ",
                    "      C                      ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    " AAAAAAAAAAA  66AAAAAAAAAAA66",
                    "  AACIIICAA   $&AAAACCCAAAA&$",
                    "   CC   CC     &% L CCC L %& ",
                    "   CC   CC    $&% L CCC L %&$",
                    "   CC   CC     &% LTCCCTL %& ",
                    "   CC G CC    $&% L CCC L %&$",
                    "  BCCMNMCCB   6%6 L CCC L 6%6",
                    "   CC   CC  V     LTCCCTL    ",
                    "   CC   CC        L CCC L    ",
                    "   CC   CC        L CCC L    ",
                    "   CC G CC        LTCCCTL    ",
                    "  BCCMNMCCB       L CCC L    ",
                    "   CC   CC        L CCC L    ",
                    "   CC   CC        LTCCCTL    ",
                    "   CC   CC        L CCC L    ",
                    "   CC G CC   V    L CCC L    ",
                    "  BCCMNMCCB       LTCCCTL    ",
                    "   CC   CC        L CCC L    ",
                    "   CC   CC        L CCC L    ",
                    "   CC   CC        LTCCCTL    ",
                    "   CC G CC        L CCC L    ",
                    "  BCCMNMCCB       L CCC L    ",
                    "   CC   CC          CCC      ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC G CC                   ",
                    "  BCCMNMCCB                  ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC G CC                   ",
                    "  BCCMNMCCB                  ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC G CC                   ",
                    "  BCCMNMCCB                  ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC G CC                   ",
                    "  BCCMNMCCB                  ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC G CC                   ",
                    "  BCCMNMCCB                  ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "    CCCCC                    ",
                    "     CCC                     ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAA 66AAAAAAAAAAA66",
                    " AACIIIIICAA  $&AAAAAAAAAAA&$",
                    "   C     C     &%         %& ",
                    "   C     C    $&%         %&$",
                    "   C     C     &%  FTTTF  %& ",
                    "   C  G  C    $&%         %&$",
                    "  BCMMNMMCB   6%6         6%6",
                    "   C     C  V      FTTTF     ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C  G  C         FTTTF     ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C         FTTTF     ",
                    "   C     C                   ",
                    "   C  G  C   V       V       ",
                    "  BCMMNMMCB        FTTTF     ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C         FTTTF     ",
                    "   C  G  C           V       ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C  G  C                   ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C  G  C                   ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C  G  C                   ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C  G  C                   ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C  G  C                   ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "    C   C                    ",
                    "     CCC                     ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAA 66           66",
                    " ACIIIIIIICA  $&           &$",
                    "  C       C    &%         %& ",
                    "  C       C   $&%         %&$",
                    "  C       C    &%         %& ",
                    "  C   G   C   $&%         %&$",
                    " BCMMMNMMMCB  6%6         6%6",
                    "  C       C VF               ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C                  ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C  F               ",
                    "  C   G   C  VF      V       ",
                    " BCMMMNMMMCB FF              ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C          V       ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C                  ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C                  ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C                  ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C                  ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C                  ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "    C   C                    ",
                    "     CCC                     ",
            },
            {
                    "AAAAAAAAAAAAA 6666  F F  6666",
                    " ACIIIIIIICA  $&&%  F F  %&&$",
                    "  C   S   C    &&%  F F  %&& ",
                    "  C   S   C   $&&%  F F  %&&$",
                    "  C   S   C    &&%  F F  %&& ",
                    "  CGGGSGGGC   $&&%  F F  %&&$",
                    " BCNNNSNNNCB  6%66  F F  66%6",
                    "  C   S   CVV       F F      ",
                    "  C   S   C         F F      ",
                    "  C   S   C         F F      ",
                    "  CGGGSGGGC         F F      ",
                    " BCNNNSNNNCB        F F      ",
                    "  C   S   C         F F      ",
                    "  C   S   C         F F      ",
                    "  C   S   C         F F      ",
                    "  CGGGSGGGCVVV      FVF      ",
                    " BCNNNSNNNCB        F F      ",
                    "  C   S   C         F F      ",
                    "  C   S   C         F F      ",
                    "  C   S   C         F F      ",
                    "  CGGGSGGGC         FVF      ",
                    " BCNNNSNNNCB        F F      ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  CGGGSGGGC                  ",
                    " BCNNNSNNNCB                 ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  CGGGSGGGC                  ",
                    " BCNNNSNNNCB                 ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  CGGGSGGGC                  ",
                    " BCNNNSNNNCB                 ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  CGGGSGGGC                  ",
                    " BCNNNSNNNCB                 ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  CGGGSGGGC                  ",
                    " BCNNNSNNNCB                 ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  C   S   C                  ",
                    "  CC  S  CC                  ",
                    "   C  S  C                   ",
                    "    C S C                    ",
                    "     CQC                     ",
            },
            {
                    "AAAAAAAAAAAAA 6666       6666",
                    " ACIIIIIIICA  $$$%       %$$$",
                    "  C       C      %       %   ",
                    "  C       C   $$$%       %$$$",
                    "  C       C      %       %   ",
                    "  C   G   C   $$$%       %$$$",
                    " BCMMMNMMMCB  6%%6       6%%6",
                    "  C       C V                ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C                  ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C  V       V       ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C          V       ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C                  ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C                  ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C                  ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C                  ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C   G   C                  ",
                    " BCMMMNMMMCB                 ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "  C       C                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "    C   C                    ",
                    "     CCC                     ",
            },
            {
                    "AAAAAAAAAAAAA 6666  AAA  6666",
                    " AACIIIIICAA  8  8  AAA  8  8",
                    "   C     C    8  8   B   8  8",
                    "   C     C    8  8   B   8  8",
                    "   C     C    8  8   B   8  8",
                    "   C  G  C    8  8   B   8  8",
                    "  BCMMNMMCB   6666  BBB  6666",
                    "   C     C  V        B       ",
                    "   C     C           B       ",
                    "   C     C           B       ",
                    "   C  G  C                   ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C   F               ",
                    "   C  G  C   VVVVVVVVV       ",
                    "  BCMMNMMCB  F               ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C  G  C           V       ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C  G  C                   ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C  G  C                   ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C  G  C                   ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C  G  C                   ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C  G  C                   ",
                    "  BCMMNMMCB                  ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "   C     C                   ",
                    "    C   C                    ",
                    "     CCC                     ",
                    "                             ",
            },
            {
                    " AAAAAAAAAAA F    AAAAAAA    ",
                    "  AACIIICAA  F    AAAAAAA    ",
                    "   CC   CC   F      GGG      ",
                    "   CC   CC   F      GGG      ",
                    "   CC   CC   F      GGG      ",
                    "   CC G CC   F      GGG      ",
                    "  BCCMNMCCB FF    BBGGGBB    ",
                    "   CC   CC  VF      GGG      ",
                    "   CC   CC  FF      GGG      ",
                    "   CC   CC   F       B       ",
                    "   CC G CC   F               ",
                    "  BCCMNMCCB  F               ",
                    "   CC   CC   F               ",
                    "   CC   CC   F               ",
                    "   CC   CC   F               ",
                    "   CC G CC   F       V       ",
                    "  BCCMNMCCB  F               ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC G CC           V       ",
                    "  BCCMNMCCB                  ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC G CC                   ",
                    "  BCCMNMCCB                  ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC G CC                   ",
                    "  BCCMNMCCB                  ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC G CC                   ",
                    "  BCCMNMCCB                  ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC G CC                   ",
                    "  BCCMNMCCB                  ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC G CC                   ",
                    "  BCCMNMCCB                  ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "   CC   CC                   ",
                    "    CCCCC                    ",
                    "     CCC                     ",
                    "                             ",
                    "                             ",
            },
            {
                    " AAAAAAAAAAA     AAAAAAAAA   ",
                    "  AAACCCAAA      AAAMMMAAA   ",
                    "  E  CCC  E       GG   GG    ",
                    "  E  CCC  E       GG W GG    ",
                    "  E  CCC  E       GG   GG    ",
                    "  E  CCC  E       GG W GG    ",
                    "  EBBCCCBBE      BGG   GGB   ",
                    "  E  CCC  E V     GG W GG    ",
                    "  E  CCC  E       GG   GG    ",
                    "  E  CCC  E         GGG      ",
                    "  E  CCC  E         F F      ",
                    "  EBBCCCBBE         F F      ",
                    "  E  CCC  E         F F      ",
                    "  E  CCC  E         F F      ",
                    "  E  CCC  E         F F      ",
                    "  E  CCC  E         FVF      ",
                    "  EBBCCCBBE         F F      ",
                    "  E  CCC  E         F F      ",
                    "  E  CCC  E         F F      ",
                    "  E  CCC  E         F F      ",
                    "  E  CCC  E         FVF      ",
                    "  EBBCCCBBE         F F      ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "  EBBCCCBBE                  ",
                    "  E  CCC  E                  ",
                    "  E  CCC  E                  ",
                    "     CCC                     ",
                    "     CCC                     ",
                    "     CCC                     ",
                    "      C                      ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "  AAAAAAAAA     AAAAAAAAAAA  ",
                    "    AAAAA       AAMMMMMMMAA  ",
                    "                 G       G   ",
                    "                 G   W   G   ",
                    "                 G       G   ",
                    "                 G   W   G   ",
                    "     BBB        BG       GB  ",
                    "            V    G   W   G   ",
                    "                 G       G   ",
                    "                  GG   GG    ",
                    "                    GGG      ",
                    "     BBB                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                     V       ",
                    "     BBB                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                     V       ",
                    "     BBB                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "     BBB                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "     BBB                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "     BBB                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "     BBB                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "     BBB                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "    AAAAA       AAAAAAAAAAA  ",
                    "      A         AAMMMMMMMAA  ",
                    "                 G       G   ",
                    "                 G   W   G   ",
                    "                 G       G   ",
                    "                 G   W   G   ",
                    "                BG       GB  ",
                    "            V    G   W   G   ",
                    "                 G       G   ",
                    "                  G     G    ",
                    "                   GGGGG     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                     V       ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                     V       ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "   ! AAAAAAAAAAAAAAAAAAAAAAA ",
                    "   !!!A!!!     AAMMMMMMMMMAA ",
                    "   !11111!      G         G  ",
                    "   !19991!      G    W    G  ",
                    "   !11111!      G         G  ",
                    "   !!!!!!!      G    W    G  ",
                    "               BG         GB ",
                    "            V   G    W    G  ",
                    "                G         G  ",
                    "                 G       G   ",
                    "                  GGGGGGG    ",
                    "                     V       ",
                    "                     V       ",
                    "                     V       ",
                    "                     V       ",
                    "                     V       ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                     V       ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "   !!AAAAAAAAAAAAAAAAAAAAAAA ",
                    "   !!!AAAAAAAAAAAMMMMMMMMMAA ",
                    "   1#555#1  F  BG    W    GB ",
                    "   1#555#1  F  BGWWWWWWWWWGB ",
                    "   1#555#1  F  BG    W    GB ",
                    "   !#555#!  F  BGWWWWWWWWWGB ",
                    "    #555#   VVVVG    W    GB ",
                    "            V  BGWWWWWWWWWGB ",
                    "               BG    W    GB ",
                    "               BBG   W   GBB ",
                    "                  GGGGGGG    ",
                    "                    F F      ",
                    "                    F F      ",
                    "                    F F      ",
                    "                    F F      ",
                    "                    F F      ",
                    "                    F F      ",
                    "                    F F      ",
                    "                    F F      ",
                    "                    F F      ",
                    "                    FVF      ",
                    "                    F F      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "   !!AAAAAAAAAAAAAAAAAAAAAAA ",
                    "   !!!A        AAMMMMMMMMMAA ",
                    "   1255521      G         G  ",
                    "   9255529      G    W    G  ",
                    "   1255521      G         G  ",
                    "   !25552!      G    W    G  ",
                    "    25552      BG         GB ",
                    "                G    W    G  ",
                    "                G         G  ",
                    "                 G       G   ",
                    "                  GGGGGGG    ",
                    "                     V       ",
                    "                     V       ",
                    "                     V       ",
                    "                     V       ",
                    "                     V       ",
                    "                     V       ",
                    "                     V       ",
                    "                     V       ",
                    "                     V       ",
                    "                     V       ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "   !AAAAA       AAAAAAAAAAA  ",
                    "   !AAAAA!      AAMMMMMMMAA  ",
                    "   1#555#1       G       G   ",
                    "   1#555#1       G   W   G   ",
                    "   1#555#1       G       G   ",
                    "   !#555#!       G   W   G   ",
                    "    #555#       BG       GB  ",
                    "                 G   W   G   ",
                    "                 G       G   ",
                    "                  G     G    ",
                    "                   GGGGG     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "  AAAAAAAAA     AAAAAAAAAAA  ",
                    "  AAAAAAAAA     AAMMMMMMMAA  ",
                    "   !11111!       G       G   ",
                    "   !19991!       G   W   G   ",
                    "   !11111!       G       G   ",
                    "   !!!!!!!       G   W   G   ",
                    "                BG       GB  ",
                    "                 G   W   G   ",
                    "                 G       G   ",
                    "                  GG   GG    ",
                    "                    GGG      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    " AAAAAAAAAAA     AAAAAAAAA   ",
                    " AAAAAAAAAAA     AAAMMMAAA   ",
                    "    F F F         GG   GG    ",
                    "   GGGGGGG        GG W GG    ",
                    "    F F F         GG   GG    ",
                    "    F F F         GG W GG    ",
                    "   GGGGGGG       BGG   GGB   ",
                    "    F F F         GG W GG    ",
                    "                  GG   GG    ",
                    "                    GGG      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    " AAAAAAAAAAA      AAAAAAA    ",
                    " AAJJJAJJJAA      AAAAAAA    ",
                    "   JJJ JJJ          GGG      ",
                    "  GJJJ JJJG         GGG      ",
                    "   JJJ JJJ          GGG      ",
                    "   JJJ JJJ          GGG      ",
                    "  GJJJ JJJG       BBGGGBB    ",
                    "   JJJ JJJ          GGG      ",
                    "   LJL JJJ          GGG      ",
                    "       LJL           B       ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAA       AAA      ",
                    "AAAJOJAJOJAAA       AAA      ",
                    "  FJOJ JOJF          B       ",
                    "  GJOJRJOJG          B       ",
                    "  FJOJ JOJF          B       ",
                    "  FJOJ JOJF          B       ",
                    "  GJOJ JOJG         BUB      ",
                    "  FJOJ JOJF          B       ",
                    "   JJJ JOJ           B       ",
                    "       JJJ           B       ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAA       AAA      ",
                    "AAAJJJAJJJAAA        A       ",
                    "   JJJ JJJ            7      ",
                    "  GJJJRJJJG           7      ",
                    "   JJJ JJJ                   ",
                    "   JJJ JJJ    F   F          ",
                    "  GJJJ JJJG          U       ",
                    "   JJJ JJJ                   ",
                    "   LJL JJJ                   ",
                    "       LJL                   ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAA F   F AAA   666",
                    "AAAAAAAAAAAAA F   F  A7   6 6",
                    "11   R        F   F  U    6 6",
                    "11G  RR   G   F   F  U7   6 6",
                    "11  PPPPP     F   F  U    666",
                    "11      PUUUUUUUUUUUUU       ",
                    "   GGGGGPG           U       ",
                    "    P   P                    ",
                    "        P                    ",
                    "        P                    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAA   AAAAAAAAAAA66",
                    "AAAJJJAJJJAAA   AAAAAAAAAAA7 ",
                    "11 JJJ JJJ            888888 ",
                    "11GJJJRJJJG           777777 ",
                    "11 JJJ JJJ                6 6",
                    "11 LJL JJJ    F   F  U       ",
                    "       JJJ                   ",
                    "    P  LJL                   ",
                    "                             ",
                    "        P                    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAA   AAAAAAAAAAA66",
                    "AAAJOJAJOJAAA   AAAAAAAAAAA76",
                    "11FJOJ JOJF               686",
                    "21GJOJRJOJG               676",
                    "21FJOJ JOJF               666",
                    "11 JJJ JOJF          U       ",
                    "    P  JOJ                   ",
                    "    P  JJJ                   ",
                    "        P                    ",
                    "        P                    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAA   AAAAAAAAAAA66",
                    "AAAJJJAJJJAAA   AAAAAAAAAAA7 ",
                    "11 JJJ JJJ                 8 ",
                    "11GJJJRJJJG         CCC    7 ",
                    "11 JJJ JJJ         CCCCC    6",
                    "11 LJL JJJ         CCUCC     ",
                    "       JJJ         CCCCC     ",
                    "    P  LJL          CCC      ",
                    "                             ",
                    "        P                    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAA   AAAAAAAAAAA 6",
                    "AAAAAAAAAAAAA   AAAAAAAAAAA76",
                    "11  F   F          CCCCC   86",
                    "21G F R F G       CC   CC  76",
                    "21  F   F         C     C   6",
                    "11  F   F         C  N  C    ",
                    "    F   F         C     C    ",
                    "    P   F         CC   CC    ",
                    "        F          CCCCC     ",
                    "        P                    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAA   AAAAAAAAAAA  ",
                    "AAAJJJAJJJAAA   AAAACCCAAAA7 ",
                    "11 JJJ JJJ        CCKLKCC  8 ",
                    "11GJJJRJJJG       C  O  C  7 ",
                    "11 JJJ JJJ       CK  O  KC   ",
                    "11 LJL JJJ       CLOONOOLC   ",
                    "       JJJ       CK  O  KC   ",
                    "    P  LJL        C  O  C    ",
                    "                  CCKLKCC    ",
                    "        P           CCC      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAA   AAAAAAAAAAA  ",
                    "AAAJOJAJOJAAA   AAAACCCAAAA7 ",
                    "11FJOJ JOJF       CCKLKCC  8 ",
                    "21GJOJRJOJG       C  O  C  7 ",
                    "21FJOJ JOJF      CK     KC   ",
                    "11 JJJ JOJF      CLO N OLC   ",
                    "    P  JOJ       CK     KC   ",
                    "    P  JJJ        C  O  C    ",
                    "        P         CCKLKCC    ",
                    "        P           CCC      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAA   AAAAAAAAAAA  ",
                    "AAAJJJAJJJAAA   AAAACCCAAAA7 ",
                    "11 JJJ JJJ      TTCCKLKCCTT8 ",
                    "11GJJJRJJJG     TTC  O  CTT7 ",
                    "11 JJJ JJJ      TCK  O  KCT  ",
                    "11 LJL JJJ      TCLOONOOLCT  ",
                    "       JJJ      TCK  O  KCT  ",
                    "    P  LJL      TFC  O  CFT  ",
                    "                 FCCKLKCCF   ",
                    "        P           CCC      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAA   AAAAAAAAAAA  ",
                    "AAAAAAAAAAAAA   AAAACCCAAAA7 ",
                    "11  F   F         CCKLKCC  8 ",
                    "91G F R F G       C  O  C  7 ",
                    "91  F   F        CK     KC   ",
                    "11  F   F        CLO N OLC   ",
                    "    F   F        CK     KC   ",
                    "    P   F         C  O  C    ",
                    "        F         CCKLKCC    ",
                    "        P           CCC      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAA F AAAAAAAAAAA  ",
                    "AAAJJJAJJJAAA F AAAACCCAAAA7 ",
                    "11 JJJ JJJ F  F   CCKLKCC  8 ",
                    "91GJJJRJJJGF  F   C  O  C  7 ",
                    "91 JJJ JJJ F  F  CK  O  KC   ",
                    "11 JJJ JJJ F  F  CLOONOOLC   ",
                    "   LJL JJJ       CK  O  KC   ",
                    "    P  JJJ        C  O  C    ",
                    "       LJL        CCKLKCC    ",
                    "        P           CCC      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAA   AAAAAAAAAAA  ",
                    "AAAJOJAJOJAAA   AAAACCCAAAA7 ",
                    "11FJOJ JOJF       CCKLKCC  8 ",
                    "11GJOJRJOJG       C  O  C  7 ",
                    "11FJOJ JOJFF  F  CK     KC   ",
                    "11FJOJ JOJUUUUUU CLO N OLC   ",
                    "   JJJ JOJ       CK     KC   ",
                    "    P  JOJ        C  O  C    ",
                    "       JJJ        CCKLKCC    ",
                    "        P           CCC      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAA  ",
                    "AAAJJJAJJJAAA F AAAACCCAAAA7 ",
                    "11 JJJ JJJ F  F TTCCKLKCCTT8 ",
                    "21GJJJ JJJGF  F TTC  O  CTT7 ",
                    "21 JJJ JJJ F  F TCK  O  KCT  ",
                    "11 JJJ JJJ F  FUTCLOONOOLCT  ",
                    "   LJL JJJ      TCK  O  KCT  ",
                    "    P  JJJ      TFC  O  CFT  ",
                    "       LJL       FCCKLKCCF   ",
                    "        P           CCC      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAA  ",
                    "AAAAAAAAAAAAAAAAAAAACCCAAAA7 ",
                    "11  F   F         CCKLKCC  8 ",
                    "11 GGGGGGG        C  O  C  7 ",
                    "11  F   F        CK     KC   ",
                    "11  F   F      U CLO N OLC   ",
                    "                 CK     KC   ",
                    "    P             C  O  C    ",
                    "                  CCKLKCC    ",
                    "        P           CCC      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAA  ",
                    "AAADDDADDDAAA   AAAACCCAAAA7 ",
                    "11 DDD DDD        CCKLKCC  8 ",
                    "21 DDD DDD        C  O  C  7 ",
                    "21EDDD DDDE      CK  O  KC   ",
                    "11EDDD DDDE    U CLOONOOLC   ",
                    "  EDDD DDDE      CK  O  KC   ",
                    "  EDDD DDDE       C  O  C    ",
                    "  EDDD DDDE       CCKLKCC    ",
                    "   DDD DDD          CCC      ",
                    "   DDD DDD                   ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAA   AAAAAAAAAAA  ",
                    "AADKKKDKKKDAA   AAAAAAAAAAA7 ",
                    "11D   D   D        CCCCC   8 ",
                    "11DKKKDKKKD       CC   CC  7 ",
                    "11D   D   D       C     C    ",
                    "11DKKKDKKKD    U  C  N  C    ",
                    "  D   D   D       C     C    ",
                    "  DKKKDKKKD       CC   CC    ",
                    "  D   D   D        CCCCC     ",
                    "  DKKKDKKKD                  ",
                    "  D   D   D                  ",
                    "   DDD DDD                   ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAA F AAAAAAAAAAA  ",
                    "AADKOKDKOKDAA F AAAAAAAAAAA7 ",
                    "11D O D O D   F F          8 ",
                    "21HKOKDKOKH   F F   CCC    7 ",
                    "21H O D O H   FFF  CCCCC     ",
                    "11HKOKDKOKH   FUF  CCUCC     ",
                    "  H O D O H        CCCCC     ",
                    "  HKOKDKOKH         CCC      ",
                    "  H O D O H                  ",
                    "  HKOKDKOKH                  ",
                    "  D O D O D                  ",
                    "   DQD DQD                   ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAA   AAAAAAAAAAA66",
                    "AADKKKDKKKDAA   AAAAAAAAAAA76",
                    "11D   D   D                86",
                    "11DKKKDKKKD                76",
                    "11D   D   D     F  F        6",
                    "11DKKKDKKKD    UUUUUUU       ",
                    "  D   D   D                  ",
                    "  DKKKDKKKD                  ",
                    "  D   D   D                  ",
                    "  DKKKDKKKD                  ",
                    "  D   D   D                  ",
                    "   DDD DDD                   ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAA   AAAAAAAAAAA66",
                    "AAADDDDDDDAAA   AAAAAAAAAAA7 ",
                    "11 DDDDDDD      F  F       8 ",
                    "11 DDDDDDD      F  F       7 ",
                    "11CDDDDDDDC     F  F        6",
                    "11 DDDDDDD      F  F         ",
                    "  CDDDDDDDC                  ",
                    "   DDDDDDD                   ",
                    "  CDDDDDDDC                  ",
                    "   DDDDDDD                   ",
                    "   DDDDDDD                   ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    " AAAAAAAAAAA         AAA   66",
                    " AAADKKKDAAA               76",
                    "    D   D                 686",
                    "    DKKKD                 676",
                    "   CD   DC                666",
                    "    DKKKD                    ",
                    "   CD   DC                   ",
                    "    DKKKD                    ",
                    "   CD   DC                   ",
                    "    DKKKD                    ",
                    "    D   D                    ",
                    "     DDD                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    " AAAAAAAAAAA        FAAAF 666",
                    " AAADKOKDAAA        F 77F777 ",
                    "    D O D           F 88F888 ",
                    "    HKOKH           F 77F777 ",
                    "    H O H           F   F 6 6",
                    "    HKOKH                    ",
                    "    H O H                    ",
                    "    HKOKH                    ",
                    "    H O H                    ",
                    "    HKOKH                    ",
                    "    D O D                    ",
                    "     DQD                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "  AAAAAAAAA 000000 AAAAAAA666",
                    "  AADKKKDAA 111111 BDDDDDB6 6",
                    "    D   D   122221 BDDDDDB6 6",
                    "    DKKKD   123321 BDDDDDB6 6",
                    "    D   D   123321 BDDDDDB666",
                    "    DKKKD   122221  F   F    ",
                    "    D   D                    ",
                    "    DKKKD                    ",
                    "    D   D                    ",
                    "    DKKKD                    ",
                    "    D   D                    ",
                    "     DDD                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "   AAAAAAA  000000 AAAAAAA   ",
                    "   AADDDAA  111111  XINIX    ",
                    "     DDD    122221  XINIX    ",
                    "     DHD    355555  XINIX    ",
                    "    EDHDE   355555 BDDDDDB   ",
                    "    EDHDE   123321  T   T    ",
                    "    EDHDE                    ",
                    "    EDHDE                    ",
                    "    EDHDE                    ",
                    "     DHD                     ",
                    "     DDD                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "    aaaaa   000000taaaaaaat  ",
                    "    aaaaa   111111t XXXXX t  ",
                    "            122221t XXYXX t  ",
                    "            311115t XXXXX t  ",
                    "            311115tbdddddbt  ",
                    "            123321ttttttttt  ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "            000000 aaaaaaa   ",
                    "            111111 bt   tb   ",
                    "            122221 bt   tb   ",
                    "            355555 bt   tb   ",
                    "            355555 btbbbtb   ",
                    "            123321  t   t    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "            000000           ",
                    "            111111           ",
                    "            122221           ",
                    "            123321           ",
                    "            123321           ",
                    "            122221           ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
    };

    public static String[][] upgradedSlices() { return UPGRADED_SLICES; }

    public static BlockPattern buildUpgraded(MultiblockMachineDefinition definition) {
        verifySymbols(UPGRADED_SLICES, "升级", UPGRADED_WIDTH, UPGRADED_LENGTH, UPGRADED_EXTRA_SYMBOLS);

        // ── X 位：GTO 的 autoLaserAbilities(recipeTypes) + MAINTENANCE ──
        // 20 格，够放 进出仓 / 能源 / 激光 / 维护。
        TraceabilityPredicate mainHatchFace = blocksOf("gtceu", "high_power_casing")
                .or(ability(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                .or(ability(PartAbility.EXPORT_ITEMS).setPreviewCount(1))
                .or(ability(PartAbility.IMPORT_FLUIDS).setPreviewCount(1))
                .or(ability(PartAbility.EXPORT_FLUIDS).setPreviewCount(1))
                .or(ability(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(8, 0))
                .or(ability(PartAbility.INPUT_LASER).setMaxGlobalLimited(4, 1))
                .or(ability(PartAbility.MAINTENANCE).setExactLimit(1));

        // ── 附属模块口：线程仓 / 超频仓 / 加速仓 / 并行仓 + IO ──
        TraceabilityPredicate moduleHatches = ability(com.gtlmodular.common.data.thread.ThreadHatchMachines.THREAD_HATCH)
                .setMaxGlobalLimited(1)
                .or(com.gtlmodular.common.data.fuel.OverclockHatches.predicate())
                .or(com.gtlmodular.common.data.fuel.AccelerateHatches.predicate())
                .or(ability(PartAbility.PARALLEL_HATCH).setMaxGlobalLimited(1).setPreviewCount(1))
                .or(ability(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                .or(ability(PartAbility.EXPORT_ITEMS).setPreviewCount(1))
                .or(ability(PartAbility.IMPORT_FLUIDS).setPreviewCount(1))
                .or(ability(PartAbility.EXPORT_FLUIDS).setPreviewCount(1));

        FactoryBlockPattern pattern = FactoryBlockPattern.start();
        for (String[] slice : UPGRADED_SLICES) {
            pattern.aisle(slice);
        }
        return pattern
                .where(' ', Predicates.any())
                .where('Y', Predicates.controller(Predicates.blocks(definition.get())))
                // 应力结构外壳（GCYM）
                .where('A', blocksOf("gtceu", "stress_proof_casing"))
                // 抗氧化哈氏合金-N 机械方块（gtlcore 已有同 id 镜像）
                .where('B', blocksOf("gtlcore", "oxidation_resistant_hastelloy_n_mechanical_casing"))
                // ★ 压力容器机械方块（GTO 专有 ⇒ 本模组自造）
                .where('C', Predicates.blocks(WoodFactoryBlocks.PRESSURE_CONTAINMENT_CASING.get()))
                // 铱机械方块（gtlcore 同 id）
                .where('D', blocksOf("gtlcore", "iridium_casing"))
                // Ruridit / 不锈钢 / 硅岩合金框架（材料框架，按材料现取，取不到退回同名 id）
                .where('E', frame("ruridit"))
                .where('F', frame("stainless_steel"))
                .where('G', blocksOf("gtceu", "watertight_casing"))
                .where('H', blocksOf("gtceu", "fusion_glass"))
                .where('I', blocksOf("gtceu", "zpm_hermetic_casing"))
                .where('J', blocksOf("gtceu", "inert_machine_casing"))
                // 任意加热线圈（GTCEu 会把 CoilType 写进 match context，机器侧据此算并行）
                .where('K', Predicates.heatingCoils())
                .where('L', frame("naquadah_alloy"))
                .where('M', blocksOf("gtceu", "corrosion_proof_casing"))
                // ★ 哈氏合金-N75 油膜管道（GTO 专有）
                .where('N', Predicates.blocks(WoodFactoryBlocks.HASTELLOY_N_75_PIPE.get()))
                .where('O', blocksOf("gtceu", "electrolytic_cell"))
                // ★ 耐化学腐蚀管道机械方块（GTO 专有）
                .where('P', Predicates.blocks(
                        WoodFactoryBlocks.CHEMICAL_CORROSION_RESISTANT_PIPE_CASING.get()))
                .where('Q', Predicates.abilities(PartAbility.MUFFLER))
                .where('R', blocksOf("gtceu", "steel_turbine_casing"))
                .where('S', blocksOf("gtceu", "tungstensteel_pipe_casing"))
                .where('T', blocksOf("gtlcore", "process_machine_casing"))
                // ★ 耐高压管道机械方块（GTO 专有）
                .where('U', Predicates.blocks(WoodFactoryBlocks.HIGH_PRESSURE_PIPE_CASING.get()))
                // ★ 油气输送密封管道机械方块（GTO 专有）
                .where('V', Predicates.blocks(
                        WoodFactoryBlocks.OIL_GAS_TRANSPORTATION_PIPE_CASING.get()))
                .where('W', blocksOf("gtceu", "ptfe_pipe_casing"))
                .where('X', mainHatchFace)
                // ── 附属模块口（Z=58/59 控制舱面，原 A/T/B/D 改写为小写）──
                .where('a', blocksOf("gtceu", "stress_proof_casing").or(moduleHatches))
                .where('b', blocksOf("gtlcore", "oxidation_resistant_hastelloy_n_mechanical_casing")
                        .or(moduleHatches))
                .where('t', blocksOf("gtlcore", "process_machine_casing").or(moduleHatches))
                .where('d', blocksOf("gtlcore", "iridium_casing").or(moduleHatches))
                
                .where('!', blocksOf("gtceu", "naquadah_coil_block"))
                .where('#', blocksOf("gtceu", "naquadah_alloy_frame"))
                .where('$', blocksOf("gtceu", "corrosion_proof_casing"))
                .where('%', blocksOf("gtlmodular", "hastelloy_n_75_pipe"))
                .where('&', blocksOf("gtceu", "electrolytic_cell"))
                .where('0', blocksOf("gtceu", "stress_proof_casing"))
                .where('1', blocksOf("gtlcore", "oxidation_resistant_hastelloy_n_mechanical_casing"))
                .where('2', blocksOf("gtlmodular", "pressure_containment_casing"))
                .where('3', blocksOf("gtlcore", "iridium_casing"))
                .where('5', blocksOf("gtceu", "stainless_steel_frame"))
                .where('6', blocksOf("gtceu", "watertight_casing"))
                .where('7', blocksOf("gtceu", "fusion_glass"))
                .where('8', blocksOf("gtceu", "zpm_hermetic_casing"))
                .where('9', blocksOf("gtceu", "inert_machine_casing"))
                .build();
    }
}
