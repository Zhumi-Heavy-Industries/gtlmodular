package com.gtlmodular.common.data.petrochem;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.HatchPredicates;
import com.gtlmodular.common.data.woodfactory.WoodFactoryBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;


/**
 * 燃料精炼复合体（{@code gtlmodular:fuel_refining_complex}）的结构 —— GTO
 * {@code gtocore:fuel_refining_complex}（中文名「燃料精炼复合体」）的 <b>1:1 照搬</b>。
 *
 * <h2>取证</h2>
 * <ul>
 *   <li>结构文件：GTO jar 内 {@code pattern/fuel_refining_complex.mbs}
 *       （3,133 B，gzip 解开 56,206 B，<b>0 字节剩余</b> = 解析完整）。</li>
 *   <li>格式：{@code FF FF FF FF | 0F | ver=1 | charDir=2(LEFT) | stringDir=0(UP) | aisleDir=4(FRONT) |
 *       LEB(aisleCount=63) | LEB(stringsPerAisle=27)}。</li>
 *   <li>尺寸：<b>31 (X) × 27 (Y) × 63 (Z) = 52,731 格</b>，实心 <b>10,597 格</b>
 *       （空气 42,134；控制器 {@code `} 1 格）。</li>
 *   <li>朝向：直转，不重排（同 {@link PetrochemicalPlantStructures} 的取证口径）。</li>
 *   <li>谓词绑定：GTO {@code MultiBlockB.java:79} 的 {@code FUEL_REFINING_COMPLEX}
 *       （{@code CoilCrossRecipeMultiblockMachine.createCheckedTemperatureParallel(true)}）。</li>
 *   <li>控制器位置（mbs 坐标 → 本文件下标）：{@code [Z=61][Y=2][X=7]}。</li>
 * </ul>
 *
 * <h2>符号表（GTO → 我方；★ = 新增方块，见 {@link PetrochemBlocks}；✚ = 木化工厂那轮已造）</h2>
 * <table border="1">
 *   <caption>31 个符号位 + 空格；格数取本文件实测</caption>
 *   <tr><th>符号</th><th>格数</th><th>GTO</th><th>我方</th></tr>
 *   <tr><td>{@code `}</td><td>1</td><td>控制器</td><td>{@code gtlmodular:fuel_refining_complex}</td></tr>
 *   <tr><td>{@code A}</td><td>2265</td><td>应力结构外壳（GCYM）</td><td>{@code gtceu:stress_proof_casing}</td></tr>
 *   <tr><td>{@code H}</td><td>1470</td><td>惰性聚四氟乙烯外壳</td><td>{@code gtceu:inert_machine_casing}</td></tr>
 *   <tr><td>{@code O}</td><td>652</td><td>铱机械方块</td><td>{@code gtlcore:iridium_casing}</td></tr>
 *   <tr><td>{@code U}</td><td>563</td><td>洁净不锈钢机械方块</td><td>{@code gtceu:clean_machine_casing}</td></tr>
 *   <tr><td>{@code F}</td><td>519</td><td>钢框架</td><td>{@code gtceu:steel_frame}</td></tr>
 *   <tr><td>{@code \}</td><td>502</td><td>压力容器机械方块</td>
 *       <td>✚{@code gtlmodular:pressure_containment_casing}</td></tr>
 *   <tr><td>{@code E}</td><td>496</td><td>水密外壳（GCYM）</td><td>{@code gtceu:watertight_casing}</td></tr>
 *   <tr><td>{@code J}</td><td>435</td><td>钛管道外壳</td><td>{@code gtceu:titanium_pipe_casing}</td></tr>
 *   <tr><td>{@code M}</td><td>433</td><td>黑钢框架</td><td>{@code gtceu:black_steel_frame}</td></tr>
 *   <tr><td>{@code N}</td><td>424</td><td>PPS 耐腐蚀机械外壳</td>
 *       <td>★{@code gtlmodular:pps_corrosion_resistant_mechanical_housing}</td></tr>
 *   <tr><td>{@code G}</td><td>369</td><td>耐高压管道机械方块</td>
 *       <td>✚{@code gtlmodular:high_pressure_pipe_casing}</td></tr>
 *   <tr><td>{@code [}</td><td>287</td><td>任意加热线圈</td><td>{@code Predicates.heatingCoils()}</td></tr>
 *   <tr><td>{@code V}</td><td>268</td><td>氧化钙陶瓷抗金属腐蚀机械方块</td>
 *       <td>★{@code gtlmodular:calcium_oxide_ceramic_anti_metal_corrosion_mechanical_block}</td></tr>
 *   <tr><td>{@code I}</td><td>264</td><td>不锈钢框架</td><td>{@code gtceu:stainless_steel_frame}</td></tr>
 *   <tr><td>{@code S}</td><td>242</td><td>水密钢框架</td><td>{@code gtceu:watertight_steel_frame}</td></tr>
 *   <tr><td>{@code R}</td><td>210</td><td>高强度支撑机械方块</td>
 *       <td>★{@code gtlmodular:high_strength_support_mechanical_casing}</td></tr>
 *   <tr><td>{@code Z}</td><td>199</td><td>抗氧化哈氏合金-N 机械方块</td>
 *       <td>{@code gtlcore:oxidation_resistant_hastelloy_n_mechanical_casing}</td></tr>
 *   <tr><td>{@code X}</td><td>168</td><td>电解槽（GCYM）</td><td>{@code gtceu:electrolytic_cell}</td></tr>
 *   <tr><td>{@code K}</td><td>156</td><td>HSLA 钢框架</td><td>{@code gtceu:hslasteel_frame}</td></tr>
 *   <tr><td>{@code Q}</td><td>150</td><td>钨钢管道外壳</td><td>{@code gtceu:tungstensteel_pipe_casing}</td></tr>
 *   <tr><td>{@code L}</td><td>113</td><td>耐化学腐蚀管道机械方块</td>
 *       <td>✚{@code gtlmodular:chemical_corrosion_resistant_pipe_casing}</td></tr>
 *   <tr><td>{@code ]}</td><td>112</td><td>聚变玻璃</td><td>{@code gtceu:fusion_glass}</td></tr>
 *   <tr><td>{@code C}</td><td>107</td><td>坚固钨钢机械方块</td><td>{@code gtceu:robust_machine_casing}</td></tr>
 *   <tr><td>{@code T}</td><td>75</td><td>油气输送密封管道机械方块</td>
 *       <td>✚{@code gtlmodular:oil_gas_transportation_pipe_casing}</td></tr>
 *   <tr><td>{@code D}</td><td>28</td><td>灯（{@code GTOPredicates.light()} = GT 灯全集）</td>
 *       <td>{@code GTBlocks.LAMPS} 全部灯</td></tr>
 *   <tr><td>{@code ^}</td><td>26</td><td>高能机壳 + 激光仓 + 维护仓</td>
 *       <td>{@code gtceu:high_power_casing} + 进出仓 / 能源 / 激光 / 维护 / 线程 / 超频 舱面</td></tr>
 *   <tr><td>{@code W}</td><td>24</td><td>工业无框玻璃</td><td>★{@code gtlmodular:industrial_frameless_glass}</td></tr>
 *   <tr><td>{@code P}</td><td>22</td><td>高能机械外壳</td><td>{@code gtceu:high_power_casing}</td></tr>
 *   <tr><td>{@code _}</td><td>9</td><td>钢化玻璃</td><td>{@code gtceu:tempered_glass}</td></tr>
 *   <tr><td>{@code Y}</td><td>8</td><td>消音仓</td><td>{@code PartAbility.MUFFLER}</td></tr>
 * </table>
 *
 * <h2>✚ 附属模块（GTO 的 module 口 → 本模组的可装仓舱面）</h2>
 * <p>GTO 原机在控制器背后挂 {@code pattern/fuel_refining_crossrecipe.mbs}（{@code .port('`',
 * StructureModuleKeys.MODULE_OUT, BACK)} + {@code .atPort(MODULE_OUT, Slot.optional(piece(
 * "fuel_refining_crossrecipe", ...)).count(CROSS_RECIPE_MODULE))}）—— 同
 * {@code petrochemical_plant} 的「模块口」框架，本包没有，改为主结构 {@code ^} 那 26 格舱面开放
 * 线程仓（≤1）/ 超频仓 / 并行仓（≤1）。
 */
public final class FuelRefiningComplexStructures {


    /** X 跨度：每行 31 个字符。 */
    public static final int WIDTH = 31;
    /** Y 跨度：每片 27 行。 */
    public static final int HEIGHT = 27;
    /** GTO 模块并入后的升级结构维度。 */
    public static final int UPGRADED_WIDTH = 31;
    public static final int UPGRADED_LENGTH = 63;
    /** Z 跨度：共 63 片。 */
    public static final int LENGTH = 63;

    private FuelRefiningComplexStructures() {
    }

    /** 结构数据（{@code [z][y]} 的字符串），给自检与预览脚本用。 */
    public static String[][] slices() {
        return SLICES;
    }

    private static final String[][] SLICES = {
            { // Z=0
                    "   AAAAAAAAA         AAAAA     ", // Y=0
                    "                      AAA      ", // Y=1
                    "                       C       ", // Y=2
                    "                     CCCCC     ", // Y=3
                    "                       C       ", // Y=4
                    "                       C       ", // Y=5
                    "                       C       ", // Y=6
                    "                       C       ", // Y=7
                    "                     CCCCC     ", // Y=8
                    "                       C       ", // Y=9
                    "                       C       ", // Y=10
                    "                       D       ", // Y=11
                    "                               ", // Y=12
                    "                               ", // Y=13
                    "                               ", // Y=14
                    "                               ", // Y=15
                    "                               ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                               ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=1
                    "  AAAAAAAAAAA      AAAAAAAAA   ", // Y=0
                    "    AAAAAAA          AAAAA     ", // Y=1
                    "                     EEEEE     ", // Y=2
                    "                   CCEEEEECC   ", // Y=3
                    "                     EEEEE     ", // Y=4
                    "                     EEEEE     ", // Y=5
                    "     FGGGF           EEEEE     ", // Y=6
                    "                     EEEEE     ", // Y=7
                    "                   CCEEEEECC   ", // Y=8
                    "                     EEEEE     ", // Y=9
                    "                               ", // Y=10
                    "     FGGGF                     ", // Y=11
                    "                               ", // Y=12
                    "                               ", // Y=13
                    "                               ", // Y=14
                    "                               ", // Y=15
                    "     FGGGF                     ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "     FGGGF                     ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=2
                    "  AAAAHHHAAAA     AAAAAAAAAAA  ", // Y=0
                    "   AAAHHHAAA       AAAAAAAAA   ", // Y=1
                    "    I HHH I        EEEEEEEEE   ", // Y=2
                    "    I HHH I       CEE     EEC  ", // Y=3
                    "    I HHH I        EE     EE   ", // Y=4
                    "    I HHH I        EEJJKJJEE   ", // Y=5
                    "    IGHHHGI        EE     EE   ", // Y=6
                    "    I HHH I        EE     EE   ", // Y=7
                    "    I HHH I       CEE     EEC  ", // Y=8
                    "    I HHH I        EEJJKJJEE   ", // Y=9
                    "    I HHH I          EEEEE     ", // Y=10
                    "    IGHHHGI                    ", // Y=11
                    "    I HHH I                    ", // Y=12
                    "    I HHH I                    ", // Y=13
                    "    I HHH I                    ", // Y=14
                    "    I HHH I                    ", // Y=15
                    "    IGHHHGI                    ", // Y=16
                    "    I HHH I                    ", // Y=17
                    "    I HHH I                    ", // Y=18
                    "    I HHH I                    ", // Y=19
                    "    I HHH I                    ", // Y=20
                    "    IGHHHGI                    ", // Y=21
                    "    I HHH I                    ", // Y=22
                    "    I HHH I                    ", // Y=23
                    "    D HHH D                    ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=3
                    "  AAAHAAAHAAA    AAAAAAAAAAAAA ", // Y=0
                    "   AAHHHHHAA      AAAAAAAAAAA  ", // Y=1
                    "     H   H        EEEEEEEEEEE  ", // Y=2
                    "     H   H       CE         EC ", // Y=3
                    "     H   H        E         E  ", // Y=4
                    "     H   H        EJJJJKJJJJE  ", // Y=5
                    "   FGHHHHHGF      E         E  ", // Y=6
                    "     H   H        E         E  ", // Y=7
                    "     H   H       CE         EC ", // Y=8
                    "     H   H        EJJJJKJJJJE  ", // Y=9
                    "     H   H         EEE E EEE   ", // Y=10
                    "   FGHHHHHGF          EEE      ", // Y=11
                    "     H   H                     ", // Y=12
                    "     H   H                     ", // Y=13
                    "     H   H                     ", // Y=14
                    "     H   H                     ", // Y=15
                    "   FGHHHHHGF                   ", // Y=16
                    "     H   H                     ", // Y=17
                    "     H   H                     ", // Y=18
                    "     H   H                     ", // Y=19
                    "     H   H                     ", // Y=20
                    "   FGHHHHHGF                   ", // Y=21
                    "     H   H                     ", // Y=22
                    "     H   H                     ", // Y=23
                    "     H   H                     ", // Y=24
                    "      HHH                      ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=4
                    "  AAHAAAAAHAA    AAAAAAAAAAAAA ", // Y=0
                    "   AHHHHHHHA      AAAAAAAAAAA  ", // Y=1
                    "    H     H       EEEEEEEEEEE  ", // Y=2
                    "    H     H      CE         EC ", // Y=3
                    "    H     H       E         E  ", // Y=4
                    "    H     H       EJJJJKJJJJE  ", // Y=5
                    "   GHHJJJHHG      E         E  ", // Y=6
                    "    H     H       E         E  ", // Y=7
                    "    H     H      CE         EC ", // Y=8
                    "    H     H       EJJJJKJJJJE  ", // Y=9
                    "    H     H        E       E   ", // Y=10
                    "   GHHJJJHHG        EE E EE    ", // Y=11
                    "    H     H           EEE      ", // Y=12
                    "    H     H                    ", // Y=13
                    "    H     H                    ", // Y=14
                    "    H     H                    ", // Y=15
                    "   GHHJJJHHG                   ", // Y=16
                    "    H     H                    ", // Y=17
                    "    H     H                    ", // Y=18
                    "    H     H                    ", // Y=19
                    "    H     H                    ", // Y=20
                    "   GHHJJJHHG                   ", // Y=21
                    "    H     H                    ", // Y=22
                    "    H     H                    ", // Y=23
                    "    H     H                    ", // Y=24
                    "     H   H                     ", // Y=25
                    "      HHH                      ", // Y=26
            },
            { // Z=5
                    "  AAHAAAAAHAAAAAAAAAAAAAAAAAAAA", // Y=0
                    "   AHHHHHHHA     AAAAAAAAAAAAA ", // Y=1
                    "    H  J  H      EEEEEEEEEEEEE ", // Y=2
                    "    H  J  H     CE           EC", // Y=3
                    "    H  J  H      E           E ", // Y=4
                    "    H  J  H      EJJJJJKJJJJJE ", // Y=5
                    "   GHHJJJHHG     E           E ", // Y=6
                    "    H  J  H      E           E ", // Y=7
                    "    H  J  H     CE           EC", // Y=8
                    "    H  J  H      EJJJJJKJJJJJE ", // Y=9
                    "    H  J  H       EE       EE  ", // Y=10
                    "   GHHJJJHHG        E     E    ", // Y=11
                    "    H  J  H          EEEEE     ", // Y=12
                    "    H  J  H                    ", // Y=13
                    "    H  J  H                    ", // Y=14
                    "    H  J  H                    ", // Y=15
                    "   GHHJJJHHG                   ", // Y=16
                    "    H  J  H                    ", // Y=17
                    "    H  J  H                    ", // Y=18
                    "    H  J  H                    ", // Y=19
                    "    H  J  H                    ", // Y=20
                    "   GHHJJJHHG                   ", // Y=21
                    "    H  J  H                    ", // Y=22
                    "    H  J  H                    ", // Y=23
                    "    H  J  H                    ", // Y=24
                    "     H   H                     ", // Y=25
                    "      HHH                      ", // Y=26
            },
            { // Z=6
                    "  AAHAAAAAHAAAAAAAAAAAAAAAAAAAA", // Y=0
                    "   AHHHHHHHAAAAAAAAAAAAAAAAAAAA", // Y=1
                    "    H     H      EEEEEEEEEEEEE ", // Y=2
                    "    H     H     CE           EC", // Y=3
                    "    H     H      E           E ", // Y=4
                    "    H     H      EJJJJJKJJJJJE ", // Y=5
                    "   GHHJJJHHG     E           E ", // Y=6
                    "    H     H      E           E ", // Y=7
                    "    H     H     CE           EC", // Y=8
                    "    H     H      EJJJJJKJJJJJE ", // Y=9
                    "    H     H       E         E  ", // Y=10
                    "   GHHJJJHHG       E       E   ", // Y=11
                    "    H     H         EEEEEEE    ", // Y=12
                    "    H     H           FFF      ", // Y=13
                    "    H     H            F       ", // Y=14
                    "    H     H                    ", // Y=15
                    "   GHHJJJHHG                   ", // Y=16
                    "    H     H                    ", // Y=17
                    "    H     H                    ", // Y=18
                    "    H     H                    ", // Y=19
                    "    H     H                    ", // Y=20
                    "   GHHJJJHHG                   ", // Y=21
                    "    H     H                    ", // Y=22
                    "    H     H                    ", // Y=23
                    "    H     H                    ", // Y=24
                    "     H   H                     ", // Y=25
                    "      HHH                      ", // Y=26
            },
            { // Z=7
                    "  AAAHAAAHAAAAAAAAAAAAAAAAAAAAA", // Y=0
                    "   AAHHHHHAAAAAAAAAAAAAAAAAAAAA", // Y=1
                    "     H   H      CEEEEEEEEEEEEEC", // Y=2
                    "     H   H      CE     L     EC", // Y=3
                    "     H   H      CE     L     EC", // Y=4
                    "     H   H      CEKKKKKLKKKKKEC", // Y=5
                    "   FGHHHHHGF    CE     L     EC", // Y=6
                    "     H   H      CE     L     EC", // Y=7
                    "     H   H      CE     L     EC", // Y=8
                    "     H   H      CEKKKKKLKKKKKEC", // Y=9
                    "     H   H      C EE   L   EE C", // Y=10
                    "   FGHHHHHGF    D  EE  L  EE  D", // Y=11
                    "     H   H          EEELEEE    ", // Y=12
                    "     H   H    LLLLLLLLLLF      ", // Y=13
                    "     H   H            FFF      ", // Y=14
                    "     H   H                     ", // Y=15
                    "   FGHHHHHGF                   ", // Y=16
                    "     H   H                     ", // Y=17
                    "     H   H                     ", // Y=18
                    "     H   H                     ", // Y=19
                    "     H   H                     ", // Y=20
                    "   FGHHHHHGF                   ", // Y=21
                    "     H   H                     ", // Y=22
                    "     H   H                     ", // Y=23
                    "     H   H                     ", // Y=24
                    "      HHH                      ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=8
                    "  AAAAHHHAAAAAAAAAAAAAAAAAAAAAA", // Y=0
                    "   AAAHHHAAAAAAAAAAAAAAAAAAAAAA", // Y=1
                    "    I HHH I  F F EEEEEEEEEEEEE ", // Y=2
                    "    I HHH I  F FCE     L     EC", // Y=3
                    "    I HHH I  F F E           E ", // Y=4
                    "    I HHH I  F F EJJJJJKJJJJJE ", // Y=5
                    "    IGHHHGI  F F E           E ", // Y=6
                    "    I HHH I  F F E           E ", // Y=7
                    "    I HHH I  F FCE           EC", // Y=8
                    "    I HHH I  F F EJJJJJKJJJJJE ", // Y=9
                    "    I HHH I  F F  E         E  ", // Y=10
                    "    IGHHHGI  F F   E       E   ", // Y=11
                    "    I HHH I  FFF    EEEEEEE    ", // Y=12
                    "    I HHH I  FLF      FFF      ", // Y=13
                    "    I HHH I  FFF       F       ", // Y=14
                    "    I HHH I                    ", // Y=15
                    "    IGHHHGI                    ", // Y=16
                    "    I HHH I                    ", // Y=17
                    "    I HHH I                    ", // Y=18
                    "    I HHH I                    ", // Y=19
                    "    I HHH I                    ", // Y=20
                    "    IGHHHGI                    ", // Y=21
                    "    I HHH I                    ", // Y=22
                    "    I HHH I                    ", // Y=23
                    "    D HHH D                    ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=9
                    "  AAAAAAAAAAAAAAAAAAAAAAAAAAAAA", // Y=0
                    "    AAAAAAA      AAAAAAAAAAAAA ", // Y=1
                    "                 EEEEEEEEEEEEE ", // Y=2
                    "                CE     L     EC", // Y=3
                    "                 E           E ", // Y=4
                    "                 EJJJJJKJJJJJE ", // Y=5
                    "     FGGGF       E           E ", // Y=6
                    "                 E           E ", // Y=7
                    "                CE           EC", // Y=8
                    "                 EJJJJJKJJJJJE ", // Y=9
                    "                  EE       EE  ", // Y=10
                    "     FGGGF          E     E    ", // Y=11
                    "                     EEEEE     ", // Y=12
                    "              L                ", // Y=13
                    "                               ", // Y=14
                    "                               ", // Y=15
                    "     FGGGF                     ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "     FGGGF                     ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=10
                    "   AAAAAAAAA     AAAAAAAAAAAAA ", // Y=0
                    "                  AAAAAAAAAAA  ", // Y=1
                    "                  EEEEEEEEEEE  ", // Y=2
                    "                 CE    L    EC ", // Y=3
                    "                  E         E  ", // Y=4
                    "                  EJJJJKJJJJE  ", // Y=5
                    "      FGF         E         E  ", // Y=6
                    "                  E         E  ", // Y=7
                    "                 CE         EC ", // Y=8
                    "                  EJJJJKJJJJE  ", // Y=9
                    "                   E       E   ", // Y=10
                    "      FGF           EE E EE    ", // Y=11
                    "                      EEE      ", // Y=12
                    "              L                ", // Y=13
                    "                               ", // Y=14
                    "                               ", // Y=15
                    "      FGF                      ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "      FGF                      ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=11
                    "  AAAAAAAAAAA    AAAAAAAAAAAAA ", // Y=0
                    "    AAAAAAA       AAAAAAAAAAA  ", // Y=1
                    "                  EEEEEEEEEEE  ", // Y=2
                    "                 CE    L    EC ", // Y=3
                    "                  E         E  ", // Y=4
                    "                  EJJJJKJJJJE  ", // Y=5
                    "     FGGGF        E         E  ", // Y=6
                    "                  E         E  ", // Y=7
                    "                 CE         EC ", // Y=8
                    "                  EJJJJKJJJJE  ", // Y=9
                    "                   EEE E EEE   ", // Y=10
                    "     FGGGF            EEE      ", // Y=11
                    "                               ", // Y=12
                    "              L                ", // Y=13
                    "                               ", // Y=14
                    "                               ", // Y=15
                    "     FGGGF                     ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "     FGGGF                     ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=12
                    "  AAAAHHHAAAA     AAAAAAAAAAA  ", // Y=0
                    "   AAAHHHAAA       AAAAAAAAA   ", // Y=1
                    "    I HHH I        EEEEEEEEE   ", // Y=2
                    "    I HHH I       CEE  L  EEC  ", // Y=3
                    "    I HHH I        EE     EE   ", // Y=4
                    "    I HHH I        EEJJKJJEE   ", // Y=5
                    "    IGHHHGI        EE     EE   ", // Y=6
                    "    I HHH I        EE     EE   ", // Y=7
                    "    I HHH I       CEE     EEC  ", // Y=8
                    "    I HHH I        EEJJKJJEE   ", // Y=9
                    "    I HHH I          EEEEE     ", // Y=10
                    "    IGHHHGI                    ", // Y=11
                    "    I HHH I                    ", // Y=12
                    "    I HHH I   L                ", // Y=13
                    "    I HHH I                    ", // Y=14
                    "    I HHH I                    ", // Y=15
                    "    IGHHHGI                    ", // Y=16
                    "    I HHH I                    ", // Y=17
                    "    I HHH I                    ", // Y=18
                    "    I HHH I                    ", // Y=19
                    "    I HHH I                    ", // Y=20
                    "    IGHHHGI                    ", // Y=21
                    "    I HHH I                    ", // Y=22
                    "    I HHH I                    ", // Y=23
                    "    D HHH D                    ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=13
                    "  AAAHAAAHAAA      AAAAAAAAA   ", // Y=0
                    "   AAHHHHHAA         AAAAA     ", // Y=1
                    "     H   H           EEEEE     ", // Y=2
                    "     H   H         CCEELEECC   ", // Y=3
                    "     H   H           EEEEE     ", // Y=4
                    "     H   H           EEEEE     ", // Y=5
                    "   FGHHHHHGF         EEEEE     ", // Y=6
                    "     H   H           EEEEE     ", // Y=7
                    "     H   H         CCEEEEECC   ", // Y=8
                    "     H   H           EEEEE     ", // Y=9
                    "     H   H                     ", // Y=10
                    "   FGHHHHHGF                   ", // Y=11
                    "     H   H                     ", // Y=12
                    "     H   H    L                ", // Y=13
                    "     H   H                     ", // Y=14
                    "     H   H                     ", // Y=15
                    "   FGHHHHHGF                   ", // Y=16
                    "     H   H                     ", // Y=17
                    "     H   H                     ", // Y=18
                    "     H   H                     ", // Y=19
                    "     H   H                     ", // Y=20
                    "   FGHHHHHGF                   ", // Y=21
                    "     H   H                     ", // Y=22
                    "     H   H                     ", // Y=23
                    "     H   H                     ", // Y=24
                    "      HHH                      ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=14
                    "  AAHAAAAAHAAF F     AAAAA     ", // Y=0
                    "   AHHHHHHHA F F      AAA      ", // Y=1
                    "    H     H  F F       C       ", // Y=2
                    "    H     H  F F     CCLCC     ", // Y=3
                    "    H     H  F F       C       ", // Y=4
                    "    H     H  F F       C       ", // Y=5
                    "   GHHJJJHHG F F       C       ", // Y=6
                    "    H     H  F F       C       ", // Y=7
                    "    H     H  F F     CCCCC     ", // Y=8
                    "    H     H  F F       C       ", // Y=9
                    "    H     H  F F       C       ", // Y=10
                    "   GHHJJJHHG F F       D       ", // Y=11
                    "    H     H  FFF               ", // Y=12
                    "    H     H  FLF               ", // Y=13
                    "    H     H  FFF               ", // Y=14
                    "    H     H  F F               ", // Y=15
                    "   GHHJJJHHG F F               ", // Y=16
                    "    H     H  F F               ", // Y=17
                    "    H     H  F F               ", // Y=18
                    "    H     H  F F               ", // Y=19
                    "    H     H  F F               ", // Y=20
                    "   GHHJJJHHG F F               ", // Y=21
                    "    H     H  F F               ", // Y=22
                    "    H     H                    ", // Y=23
                    "    H     H                    ", // Y=24
                    "     H   H                     ", // Y=25
                    "      HHH                      ", // Y=26
            },
            { // Z=15
                    "  AAHAAAAAHAA        AAAAA     ", // Y=0
                    "   AHHHHHHHA          AAA      ", // Y=1
                    "    H  J  H                    ", // Y=2
                    "    H  J  H            L       ", // Y=3
                    "    H  J  H            L       ", // Y=4
                    "    H  J  H            L       ", // Y=5
                    "   GHHJJJHHG                   ", // Y=6
                    "    H  J  H                    ", // Y=7
                    "    H  J  H                    ", // Y=8
                    "    H  J  H                    ", // Y=9
                    "    H  J  H                    ", // Y=10
                    "   GHHJJJHHG                   ", // Y=11
                    "    H  J  H                    ", // Y=12
                    "    H  J  H   L                ", // Y=13
                    "    H  J  H                    ", // Y=14
                    "    H  J  H  F F               ", // Y=15
                    "   GHHJJJHHGGGGGGGGGGGGG       ", // Y=16
                    "    H  J  H  F F               ", // Y=17
                    "    H  J  H                    ", // Y=18
                    "    H  J  H                    ", // Y=19
                    "    H  J  H                    ", // Y=20
                    "   GHHJJJHHGGGGGGGGGGGGG       ", // Y=21
                    "    H  J  H  F F               ", // Y=22
                    "    H  J  H                    ", // Y=23
                    "    H  J  H                    ", // Y=24
                    "     H   H                     ", // Y=25
                    "      HHH                      ", // Y=26
            },
            { // Z=16
                    "  AAHAAAAAHAAF F     AAAAA     ", // Y=0
                    "   AHHHHHHHA F F      AAA      ", // Y=1
                    "    H     H  F F               ", // Y=2
                    "    H     H  F F               ", // Y=3
                    "    H     H  F F               ", // Y=4
                    "    H     H  F F   LLLLLLLLL   ", // Y=5
                    "   GHHJJJHHG F F               ", // Y=6
                    "    H     H  F F               ", // Y=7
                    "    H     H  F F               ", // Y=8
                    "    H     H  F F               ", // Y=9
                    "    H     H  F F               ", // Y=10
                    "   GHHJJJHHG F F               ", // Y=11
                    "    H     H  FFF               ", // Y=12
                    "    H     H  FLF               ", // Y=13
                    "    H     H  FFF               ", // Y=14
                    "    H     H  F F               ", // Y=15
                    "   GHHJJJHHG F F       G       ", // Y=16
                    "    H     H  F F               ", // Y=17
                    "    H     H  F F               ", // Y=18
                    "    H     H  F F               ", // Y=19
                    "    H     H  F F               ", // Y=20
                    "   GHHJJJHHG F F       G       ", // Y=21
                    "    H     H  F F               ", // Y=22
                    "    H     H                    ", // Y=23
                    "    H     H                    ", // Y=24
                    "     H   H                     ", // Y=25
                    "      HHH                      ", // Y=26
            },
            { // Z=17
                    "  AAAHAAAHAAA    AAAAAAAAAAAAA ", // Y=0
                    "   AAHHHHHAA      AAA AAA AAA  ", // Y=1
                    "     H   H            F F      ", // Y=2
                    "     H   H         M  F F  M   ", // Y=3
                    "     H   H        NNN F F NNN  ", // Y=4
                    "     H   H       MNLNMF FMNLNM ", // Y=5
                    "   FGHHHHHGF      NNN F F NNN  ", // Y=6
                    "     H   H         M  F F  M   ", // Y=7
                    "     H   H            F F      ", // Y=8
                    "     H   H         M  F F  M   ", // Y=9
                    "     H   H        OOO F F OOO  ", // Y=10
                    "   FGHHHHHGF     MOPOMF FMOPOM ", // Y=11
                    "     H   H        OOO F F OOO  ", // Y=12
                    "     H   H    L    M  F F  M   ", // Y=13
                    "     H   H            F F      ", // Y=14
                    "     H   H            FFF      ", // Y=15
                    "   FGHHHHHGF          FGF      ", // Y=16
                    "     H   H            FFF      ", // Y=17
                    "     H   H            F F      ", // Y=18
                    "     H   H            F F      ", // Y=19
                    "     H   H            FFF      ", // Y=20
                    "   FGHHHHHGF          FGF      ", // Y=21
                    "     H   H            FFF      ", // Y=22
                    "     H   H                     ", // Y=23
                    "     H   H                     ", // Y=24
                    "      HHH                      ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=18
                    "  AAAAHHHAAAA    AAAAAAAAAAAAA ", // Y=0
                    "   AAAHHHAAA      AAA AAA AAA  ", // Y=1
                    "    I HHH I        M       M   ", // Y=2
                    "    I HHH I       NNN     NNN  ", // Y=3
                    "    I HHH I      NNNNN   NNNNN ", // Y=4
                    "    I HHH I     MNNQNNM MNNQNNM", // Y=5
                    "    IGHHHGI      NNNNN   NNNNN ", // Y=6
                    "    I HHH I       NNN     NNN  ", // Y=7
                    "    I HHH I        M       M   ", // Y=8
                    "    I HHH I       OOO     OOO  ", // Y=9
                    "    I HHH I      OOOOO   OOOOO ", // Y=10
                    "    IGHHHGI     MOOQOOM MOOQOOM", // Y=11
                    "    I HHH I      OOOOO   OOOOO ", // Y=12
                    "    I HHH I   L   OOO     OOO  ", // Y=13
                    "    I HHH I       DMD     DMD  ", // Y=14
                    "    I HHH I                    ", // Y=15
                    "    IGHHHGI            G       ", // Y=16
                    "    I HHH I                    ", // Y=17
                    "    I HHH I                    ", // Y=18
                    "    I HHH I                    ", // Y=19
                    "    I HHH I                    ", // Y=20
                    "    IGHHHGI            G       ", // Y=21
                    "    I HHH I                    ", // Y=22
                    "    I HHH I                    ", // Y=23
                    "    D HHH D                    ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=19
                    "  AAAAAAAAAAA    AAAAAAAAAAAAA ", // Y=0
                    "    AAAAAAA      RAAARAAARAAAR ", // Y=1
                    "                 RRRRR   RRRRR ", // Y=2
                    "                 RNNNR   RNNNR ", // Y=3
                    "                RNSSSNR RNSSSNR", // Y=4
                    "                RNSQSNR RNSQSNR", // Y=5
                    "     FGGGF      RNSSSNR RNSSSNR", // Y=6
                    "                 RNNNR   RNNNR ", // Y=7
                    "                 RRRRR   RRRRR ", // Y=8
                    "                 ROOOR   ROOOR ", // Y=9
                    "                RO   OR RO   OR", // Y=10
                    "     FGGGF      RO Q OR RO Q OR", // Y=11
                    "                RO   OR RO   OR", // Y=12
                    "              L  ROOOR   ROOOR ", // Y=13
                    "                  RRR     RRR  ", // Y=14
                    "                               ", // Y=15
                    "     FGGGF             G       ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "     FGGGF             G       ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=20
                    "   AAAAAAAAA     AAAAAAAAAAAAA ", // Y=0
                    "                  AAA AAA AAA  ", // Y=1
                    "                   M       M   ", // Y=2
                    "                  NNN     NNN  ", // Y=3
                    "                 NSSSN   NSSSN ", // Y=4
                    "                MNSQSNM MNSQSNM", // Y=5
                    "      FGF        NSSSN   NSSSN ", // Y=6
                    "                  NNN     NNN  ", // Y=7
                    "                   M       M   ", // Y=8
                    "                  OOO     OOO  ", // Y=9
                    "                 O   O   O   O ", // Y=10
                    "      FGF       MO Q OM MO Q OM", // Y=11
                    "                 O   O   O   O ", // Y=12
                    "              L   OOO     OOO  ", // Y=13
                    "                   M       M   ", // Y=14
                    "                               ", // Y=15
                    "      FGF              G       ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "      FGF              G       ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=21
                    "  AAAAAAAAAAA    AAAAAAAAAAAAA ", // Y=0
                    "    AAAAAAA       AAA AAA AAA  ", // Y=1
                    "                   M       M   ", // Y=2
                    "                  NNN     NNN  ", // Y=3
                    "                 NSSSN   NSSSN ", // Y=4
                    "                MNSQSNM MNSQSNM", // Y=5
                    "     FGGGF       NSSSN   NSSSN ", // Y=6
                    "                  NNN     NNN  ", // Y=7
                    "                   M       M   ", // Y=8
                    "                  OOO     OOO  ", // Y=9
                    "                 O   O   O   O ", // Y=10
                    "     FGGGF      MO Q OM MO Q OM", // Y=11
                    "                 O   O   O   O ", // Y=12
                    "              L   OOO     OOO  ", // Y=13
                    "                   M       M   ", // Y=14
                    "                               ", // Y=15
                    "     FGGGF             G       ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "     FGGGF             G       ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=22
                    "  AAAAHHHAAAA    AAAAAAAAAAAAA ", // Y=0
                    "   AAAHHHAAA      AAA AAA AAA  ", // Y=1
                    "    I HHH I        M       M   ", // Y=2
                    "    I HHH I       NNN     NNN  ", // Y=3
                    "    I HHH I      NSSSN   NSSSN ", // Y=4
                    "    I HHH I     MNSQSNM MNSQSNM", // Y=5
                    "    IGHHHGI      NSSSN   NSSSN ", // Y=6
                    "    I HHH I       NNN     NNN  ", // Y=7
                    "    I HHH I        M       M   ", // Y=8
                    "    I HHH I       OOO     OOO  ", // Y=9
                    "    I HHH I      O   O   O   O ", // Y=10
                    "    IGHHHGI     MO Q OM MO Q OM", // Y=11
                    "    I HHH I      O   O   O   O ", // Y=12
                    "    I HHH I   L   OOO     OOO  ", // Y=13
                    "    I HHH I        M       M   ", // Y=14
                    "    I HHH I                    ", // Y=15
                    "    IGHHHGI            G       ", // Y=16
                    "    I HHH I                    ", // Y=17
                    "    I HHH I                    ", // Y=18
                    "    I HHH I                    ", // Y=19
                    "    I HHH I                    ", // Y=20
                    "    IGHHHGI            G       ", // Y=21
                    "    I HHH I                    ", // Y=22
                    "    I HHH I                    ", // Y=23
                    "    D HHH D                    ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=23
                    "  AAAHAAAHAAAAAAAAAAAAAAAAAAAA ", // Y=0
                    "   AAHHHHHAA      AAA AAA AAA  ", // Y=1
                    "     H   H         M       M   ", // Y=2
                    "     H   H        NNN     NNN  ", // Y=3
                    "     H   H       NSSSN   NSSSN ", // Y=4
                    "     H   H      MNSQSNM MNSQSNM", // Y=5
                    "   FGHHHHHGF     NSSSN   NSSSN ", // Y=6
                    "     H   H        NNN     NNN  ", // Y=7
                    "     H   H         M       M   ", // Y=8
                    "     H   H        OOO     OOO  ", // Y=9
                    "     H   H       O   O   O   O ", // Y=10
                    "   FGHHHHHGF    MO Q OM MO Q OM", // Y=11
                    "     H   H       O   O   O   O ", // Y=12
                    "     H   H    L   OOO     OOO  ", // Y=13
                    "     H   H         M       M   ", // Y=14
                    "     H   H                     ", // Y=15
                    "   FGHHHHHGF           G       ", // Y=16
                    "     H   H                     ", // Y=17
                    "     H   H                     ", // Y=18
                    "     H   H                     ", // Y=19
                    "     H   H                     ", // Y=20
                    "   FGHHHHHGF           G       ", // Y=21
                    "     H   H                     ", // Y=22
                    "     H   H                     ", // Y=23
                    "     H   H                     ", // Y=24
                    "      HHH                      ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=24
                    "  AAHAAAAAHAAAAAAAAAAAAAAAAAAA ", // Y=0
                    "   AHHHHHHHAAAAAA AAA AAA AAA  ", // Y=1
                    "    H     H        M       M   ", // Y=2
                    "    H     H       NNN     NNN  ", // Y=3
                    "    H     H      NSSSN   NSSSN ", // Y=4
                    "    H     H     MNSQSNM MNSQSNM", // Y=5
                    "   GHHJJJHHG     NSSSN   NSSSN ", // Y=6
                    "    H     H       NNN     NNN  ", // Y=7
                    "    H     H        M       M   ", // Y=8
                    "    H     H       OOO     OOO  ", // Y=9
                    "    H     H      O   O   O   O ", // Y=10
                    "   GHHJJJHHG    MO Q OM MO Q OM", // Y=11
                    "    H     H      O   O   O   O ", // Y=12
                    "    H     H   L   OOO     OOO  ", // Y=13
                    "    H     H        M       M   ", // Y=14
                    "    H     H                    ", // Y=15
                    "   GHHJJJHHG           G       ", // Y=16
                    "    H     H                    ", // Y=17
                    "    H     H                    ", // Y=18
                    "    H     H                    ", // Y=19
                    "    H     H                    ", // Y=20
                    "   GHHJJJHHG           G       ", // Y=21
                    "    H     H                    ", // Y=22
                    "    H     H                    ", // Y=23
                    "    H     H                    ", // Y=24
                    "     H   H                     ", // Y=25
                    "      HHH                      ", // Y=26
            },
            { // Z=25
                    "  AAHAAAAAHAAAAAAAAAAAAAAAAAAA ", // Y=0
                    "   AHHHHHHHAAAAAARAAARAAARAAAR ", // Y=1
                    "    H  J  H      RRRRR   RRRRR ", // Y=2
                    "    H  J  H      RNNNR   RNNNR ", // Y=3
                    "    H  J  H     RNSSSNR RNSSSNR", // Y=4
                    "    H  J  H     RNSQSNR RNSQSNR", // Y=5
                    "   GHHJJJHHG    RNSSSNR RNSSSNR", // Y=6
                    "    H  J  H      RNNNR   RNNNR ", // Y=7
                    "    H  J  H      RRRRR   RRRRR ", // Y=8
                    "    H  J  H      ROOOR   ROOOR ", // Y=9
                    "    H  J  H     RO   OR RO   OR", // Y=10
                    "   GHHJJJHHG    RO Q OR RO Q OR", // Y=11
                    "    H  J  H     RO   OR RO   OR", // Y=12
                    "    H  J  H   L  ROOOR   ROOOR ", // Y=13
                    "    H  J  H       RRR     RRR  ", // Y=14
                    "    H  J  H                    ", // Y=15
                    "   GHHJJJHHG           G       ", // Y=16
                    "    H  J  H                    ", // Y=17
                    "    H  J  H                    ", // Y=18
                    "    H  J  H                    ", // Y=19
                    "    H  J  H                    ", // Y=20
                    "   GHHJJJHHG           G       ", // Y=21
                    "    H  J  H                    ", // Y=22
                    "    H  J  H                    ", // Y=23
                    "    H  J  H                    ", // Y=24
                    "     H   H                     ", // Y=25
                    "      HHH                      ", // Y=26
            },
            { // Z=26
                    "  AAHAAAAAHAAAAAAAAAAAAAAAAAAA ", // Y=0
                    "   AHHHHHHHAAAAAA AAA AAA AAA  ", // Y=1
                    "    H     H        M       M   ", // Y=2
                    "    H     H       NNN     NNN  ", // Y=3
                    "    H     H      NSSSN   NSSSN ", // Y=4
                    "    H     H     MNSQSNM MNSQSNM", // Y=5
                    "   GHHJJJHHG     NSSSN   NSSSN ", // Y=6
                    "    H     H       NNN     NNN  ", // Y=7
                    "    H     H        M       M   ", // Y=8
                    "    H     H       OOO     OOO  ", // Y=9
                    "    H     H      O   O   O   O ", // Y=10
                    "   GHHJJJHHG    MO Q OM MO Q OM", // Y=11
                    "    H     H      O   O   O   O ", // Y=12
                    "    H     H   L   OOO     OOO  ", // Y=13
                    "    H     H        M       M   ", // Y=14
                    "    H     H                    ", // Y=15
                    "   GHHJJJHHG           G       ", // Y=16
                    "    H     H                    ", // Y=17
                    "    H     H                    ", // Y=18
                    "    H     H                    ", // Y=19
                    "    H     H                    ", // Y=20
                    "   GHHJJJHHG           G       ", // Y=21
                    "    H     H                    ", // Y=22
                    "    H     H                    ", // Y=23
                    "    H     H                    ", // Y=24
                    "     H   H                     ", // Y=25
                    "      HHH                      ", // Y=26
            },
            { // Z=27
                    "  AAAHAAAHAAAAAAAAAAAAAAAAAAAA ", // Y=0
                    "   AAHHHHHAA      AAA AAA AAA  ", // Y=1
                    "     H   H         M       M   ", // Y=2
                    "     H   H        NNN     NNN  ", // Y=3
                    "     H   H       NSSSN   NSSSN ", // Y=4
                    "     H   H      MNSQSNM MNSQSNM", // Y=5
                    "   FGHHHHHGF     NSSSN   NSSSN ", // Y=6
                    "     H   H        NNN     NNN  ", // Y=7
                    "     H   H         M       M   ", // Y=8
                    "     H   H        OOO     OOO  ", // Y=9
                    "     H   H       O   O   O   O ", // Y=10
                    "   FGHHHHHGF    MO Q OM MO Q OM", // Y=11
                    "     H   H       O   O   O   O ", // Y=12
                    "     H   H    L   OOO     OOO  ", // Y=13
                    "     H   H         M       M   ", // Y=14
                    "     H   H                     ", // Y=15
                    "   FGHHHHHGF           G       ", // Y=16
                    "     H   H                     ", // Y=17
                    "     H   H                     ", // Y=18
                    "     H   H                     ", // Y=19
                    "     H   H                     ", // Y=20
                    "   FGHHHHHGF           G       ", // Y=21
                    "     H   H                     ", // Y=22
                    "     H   H                     ", // Y=23
                    "     H   H                     ", // Y=24
                    "      HHH                      ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=28
                    "  AAAAHHHAAAA    AAAAAAAAAAAAA ", // Y=0
                    "   AAAHHHAAA      AAA AAA AAA  ", // Y=1
                    "    I HHH I        M       M   ", // Y=2
                    "    I HHH I       NNN     NNN  ", // Y=3
                    "    I HHH I      NSSSN   NSSSN ", // Y=4
                    "    I HHH I     MNSQSNM MNSQSNM", // Y=5
                    "    IGHHHGI      NSSSN   NSSSN ", // Y=6
                    "    I HHH I       NNN     NNN  ", // Y=7
                    "    I HHH I        M       M   ", // Y=8
                    "    I HHH I       OOO     OOO  ", // Y=9
                    "    I HHH I      O   O   O   O ", // Y=10
                    "    IGHHHGI     MO Q OM MO Q OM", // Y=11
                    "    I HHH I      O   O   O   O ", // Y=12
                    "    I HHH I   L   OOO     OOO  ", // Y=13
                    "    I HHH I        M       M   ", // Y=14
                    "    I HHH I                    ", // Y=15
                    "    IGHHHGI            G       ", // Y=16
                    "    I HHH I                    ", // Y=17
                    "    I HHH I                    ", // Y=18
                    "    I HHH I                    ", // Y=19
                    "    I HHH I                    ", // Y=20
                    "    IGHHHGI            G       ", // Y=21
                    "    I HHH I                    ", // Y=22
                    "    I HHH I                    ", // Y=23
                    "    D HHH D                    ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=29
                    "  AAAAAAAAAAA    AAAAAAAAAAAAA ", // Y=0
                    "    AAAAAAA       AAA AAA AAA  ", // Y=1
                    "                   M       M   ", // Y=2
                    "                  NNN     NNN  ", // Y=3
                    "                 NSSSN   NSSSN ", // Y=4
                    "                MNSQSNM MNSQSNM", // Y=5
                    "     FGGGF       NSSSN   NSSSN ", // Y=6
                    "                  NNN     NNN  ", // Y=7
                    "                   M       M   ", // Y=8
                    "                  OOO     OOO  ", // Y=9
                    "                 O   O   O   O ", // Y=10
                    "     FGGGF      MO Q OM MO Q OM", // Y=11
                    "                 O   O   O   O ", // Y=12
                    "              L   OOO     OOO  ", // Y=13
                    "                   M       M   ", // Y=14
                    "                               ", // Y=15
                    "     FGGGF             G       ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "     FGGGF             G       ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=30
                    "   AAAAAAAAA     AAAAAAAAAAAAA ", // Y=0
                    "      AAA         AAA AAA AAA  ", // Y=1
                    "       F           M       M   ", // Y=2
                    "       F          NNN     NNN  ", // Y=3
                    "       F         NSSSN   NSSSN ", // Y=4
                    "       F        MNSQSNM MNSQSNM", // Y=5
                    "      FGF        NSSSN   NSSSN ", // Y=6
                    "       G          NNN     NNN  ", // Y=7
                    "       G           M       M   ", // Y=8
                    "       G          OOO     OOO  ", // Y=9
                    "       G         O   O   O   O ", // Y=10
                    "      FGF       MO Q OM MO Q OM", // Y=11
                    "       G         O   O   O   O ", // Y=12
                    "       G      L   OOO     OOO  ", // Y=13
                    "       G           M       M   ", // Y=14
                    "       G                       ", // Y=15
                    "      FGF              G       ", // Y=16
                    "       G                       ", // Y=17
                    "       G                       ", // Y=18
                    "       G                       ", // Y=19
                    "       G                       ", // Y=20
                    "      FGF              G       ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=31
                    "     AAAAA   F F AAAAAAAAAAAAA ", // Y=0
                    "      AAA    F F RAAARAAARAAAR ", // Y=1
                    "             F F RRRRR   RRRRR ", // Y=2
                    "             F F RNNNR   RNNNR ", // Y=3
                    "             F FRNSSSNR RNSSSNR", // Y=4
                    "             F FRNSQSNR RNSQSNR", // Y=5
                    "       G     F FRNSSSNR RNSSSNR", // Y=6
                    "             F F RNNNR   RNNNR ", // Y=7
                    "             F F RRRRR   RRRRR ", // Y=8
                    "             F F ROOOR   ROOOR ", // Y=9
                    "             F FRO   OR RO   OR", // Y=10
                    "             F FRO Q OR RO Q OR", // Y=11
                    "             F FRO   OR RO   OR", // Y=12
                    "             FLF ROOOR   ROOOR ", // Y=13
                    "             FFF  RRR     RRR  ", // Y=14
                    "                               ", // Y=15
                    "                       G       ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                       G       ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=32
                    "     AAAAA       AAAAAAAAAAAAA ", // Y=0
                    "      AAA         AAA AAA AAA  ", // Y=1
                    "                   M       M   ", // Y=2
                    "                  NNN     NNN  ", // Y=3
                    "                 NNNNN   NNNNN ", // Y=4
                    "                MNNQNNM MNNQNNM", // Y=5
                    "       G         NNNNN   NNNNN ", // Y=6
                    "                  NNN     NNN  ", // Y=7
                    "                   M       M   ", // Y=8
                    "              L   OOO     OOO  ", // Y=9
                    "             FLF OOOOO   OOOOO ", // Y=10
                    "              L MOOQOOM MOOQOOM", // Y=11
                    "              L  OOOOO   OOOOO ", // Y=12
                    "             FLF  OOO     OOO  ", // Y=13
                    "                  DMD     DMD  ", // Y=14
                    "                               ", // Y=15
                    "                       G       ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                       G       ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=33
                    " AAAAAAAAAAAAA   AAAAAAAAAAAAA ", // Y=0
                    "      AAA         AAA AAA AAA  ", // Y=1
                    "                               ", // Y=2
                    "                   M       M   ", // Y=3
                    "                  NNN     NNN  ", // Y=4
                    "                 MNPNM   MNPNM ", // Y=5
                    "       G          NNN     NNN  ", // Y=6
                    "                   M       M   ", // Y=7
                    "                               ", // Y=8
                    "              L    M       M   ", // Y=9
                    "             FFF  OOO     OOO  ", // Y=10
                    "                 MOTOM   MOTOM ", // Y=11
                    "                  OOO     OOO  ", // Y=12
                    "             FFF   M       M   ", // Y=13
                    "                               ", // Y=14
                    "                               ", // Y=15
                    "                       G       ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                       G       ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=34
                    " AAAAAAAAAAAAA       AAAAA     ", // Y=0
                    "  AAAAAAAAAAA         AAA      ", // Y=1
                    "  M   MGM   M                  ", // Y=2
                    "  M   MGM   M                  ", // Y=3
                    "  M   MGM   M                  ", // Y=4
                    "  M   MGM   M                  ", // Y=5
                    "  M   MGM   M                  ", // Y=6
                    "  M   M M   M                  ", // Y=7
                    "  MMMMMMMMMMM                  ", // Y=8
                    "              L                ", // Y=9
                    "                               ", // Y=10
                    "                   TTTTTTTTT   ", // Y=11
                    "                               ", // Y=12
                    "                               ", // Y=13
                    "                               ", // Y=14
                    "                               ", // Y=15
                    "                       G       ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                       G       ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=35
                    " AAAAAAAAAAAAA       AAAAA     ", // Y=0
                    "  AUUUUUUUUUA         AAA      ", // Y=1
                    "   UUUUGUUUU          A A      ", // Y=2
                    "   UUUUUUUUU          A A      ", // Y=3
                    "   UUUUUUUUU       AAAAAAAAA   ", // Y=4
                    "   UUUUUUUUU          ATA      ", // Y=5
                    "   UUUUGUUUU          ATA      ", // Y=6
                    "   UUUUUUUUU       AAAATAAAA   ", // Y=7
                    "  M   M M   M         ATA      ", // Y=8
                    "              L       ATA      ", // Y=9
                    "                      ATA      ", // Y=10
                    "                       T       ", // Y=11
                    "                               ", // Y=12
                    "                               ", // Y=13
                    "                               ", // Y=14
                    "                               ", // Y=15
                    "                       G       ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                       G       ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=36
                    " AAAAAAAAAAAAA   AAAAAAAAAAAAA ", // Y=0
                    "  AUUUUUUUUUA     AAAAAAAAAAA  ", // Y=1
                    "   WX  Q  XW       AVVVVVVVA   ", // Y=2
                    "   U   S   U       AVVVVVVVA   ", // Y=3
                    "   U       U       AVVVVVVVA   ", // Y=4
                    "   U   S   U       AVVVJVVVA   ", // Y=5
                    "   WX  Q  XW       AVVVJVVVA   ", // Y=6
                    "   UYUUUUUYU       AVVVVVVVA   ", // Y=7
                    "  M   M M   M      AVVVVVVVA   ", // Y=8
                    "              L    AVVVVVVVA   ", // Y=9
                    "                   AAAAAAAAA   ", // Y=10
                    "                      F F      ", // Y=11
                    "                      F F      ", // Y=12
                    "                      F F      ", // Y=13
                    "                      F F      ", // Y=14
                    "                      FFF      ", // Y=15
                    "                      FGF      ", // Y=16
                    "                      FFF      ", // Y=17
                    "                      F F      ", // Y=18
                    "                      F F      ", // Y=19
                    "                      FFF      ", // Y=20
                    "                      FGF      ", // Y=21
                    "                      FFF      ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=37
                    " AAAAAAAAAAAAA   AAAAAAAAAAAAA ", // Y=0
                    "  AUUUUUUUUUA     AZZZZZZZZZA  ", // Y=1
                    "   WX  Q  XW       FVV[V[VVF   ", // Y=2
                    "   U   S   U       FVK[[[KVF   ", // Y=3
                    "   U       U       F[[ Q [[F   ", // Y=4
                    "   U   S   U       FV[QJQ[VF   ", // Y=5
                    "   WX  Q  XW       FV[QJQ[VF   ", // Y=6
                    "   UYUUUUUYU       F[[ Q [[F   ", // Y=7
                    "  M   M M   M      FVK[Q[KVF   ", // Y=8
                    "              L    FVV[G[VVF   ", // Y=9
                    "                   FFFFGFFFF   ", // Y=10
                    "                       G       ", // Y=11
                    "                       G       ", // Y=12
                    "                       G       ", // Y=13
                    "                       G       ", // Y=14
                    "                       G       ", // Y=15
                    "                       G       ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                       G       ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=38
                    " AAAAAAAAAAAAA   AAAAAAAAAAAAA ", // Y=0
                    "  AUUUUUUUUUA     AZZZZZZZZZA  ", // Y=1
                    "   WX  Q  XW       ZVV[V[VVZ   ", // Y=2
                    "   U   S   U       ZVK   KVZ   ", // Y=3
                    "   U       U       Z[  Q  [Z   ", // Y=4
                    "   U   S   U       ZV QJQ VZ   ", // Y=5
                    "   WX  Q  XW       ZV QJQ VZ   ", // Y=6
                    "   UUUUUUUUU       Z[  Q  [Z   ", // Y=7
                    "  M   M M   M      ZVK [ KVZ   ", // Y=8
                    "              L    ZVV[V[VVZ   ", // Y=9
                    "                   ZZZZZZZZZ   ", // Y=10
                    "                               ", // Y=11
                    "                               ", // Y=12
                    "                               ", // Y=13
                    "                               ", // Y=14
                    "                               ", // Y=15
                    "                               ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                       G       ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=39
                    " AAAAAAAAAAAAA   AAAAAAAAAAAAA ", // Y=0
                    "  AUUUUUUUUUA     AZZZZZZZZZA  ", // Y=1
                    "  MUX  Q  XUM      FVV[V[VVF   ", // Y=2
                    "  MU   S   UM      FVK   KVF   ", // Y=3
                    "  MU       UM      F[  Q  [F   ", // Y=4
                    "  MU   S   UM      FV QJQ VF   ", // Y=5
                    "  MUX  Q  XUM      FV QJQ VF   ", // Y=6
                    "  MUUUUUUUUUM      F[  Q  [F   ", // Y=7
                    "  MMMMM MMMMM      FVK   KVF   ", // Y=8
                    "            M L    FVV[V[VVF   ", // Y=9
                    "            M      FFFFFFFFF   ", // Y=10
                    "                       G       ", // Y=11
                    "                       G       ", // Y=12
                    "                       G       ", // Y=13
                    "                       G       ", // Y=14
                    "                       G       ", // Y=15
                    "                       G       ", // Y=16
                    "                       G       ", // Y=17
                    "                       G       ", // Y=18
                    "                       G       ", // Y=19
                    "                       G       ", // Y=20
                    "                       G       ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=40
                    " AAAAAAAAAAAAA   AAAAAAAAAAAAA ", // Y=0
                    "  AUUUUUUUUUA     AZZZZZZZZZA  ", // Y=1
                    "   UX  Q  XU       ZVV[V[VVZ   ", // Y=2
                    "   U   S   U       ZVK   KVZ   ", // Y=3
                    "   U       U       Z[  Q  [Z   ", // Y=4
                    "   U   S   U       ZV QJQ VZ   ", // Y=5
                    "   UX  Q  XU       ZV QJQ VZ   ", // Y=6
                    "   UUUULUUUU       Z[  Q  [Z   ", // Y=7
                    "  M   MLM   M      ZVK   KVZ   ", // Y=8
                    "       LLLLLLLL    ZVV[V[VVZ   ", // Y=9
                    "            M      ZZZZZZZZZ   ", // Y=10
                    "                       G       ", // Y=11
                    "                               ", // Y=12
                    "                               ", // Y=13
                    "                               ", // Y=14
                    "                               ", // Y=15
                    "                               ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                               ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=41
                    " AAAAAAAAAAAAA   AAAAAAAAAAAAA ", // Y=0
                    "  AUUUUUUUUUA     AZZZZZZZZZA  ", // Y=1
                    "   UX  Q  XU       FVV[V[VVF   ", // Y=2
                    "   U   S   U       FVK   KVF   ", // Y=3
                    "   U       U       F[  Q  [F   ", // Y=4
                    "   U   S   U       FV QJQ VF   ", // Y=5
                    "   UX  Q  XU       FV QJQ VF   ", // Y=6
                    "   UUUUUUUUU       F[  Q  [F   ", // Y=7
                    "  M   M M   M      FVK   KVF   ", // Y=8
                    "            M L    FVV[V[VVF   ", // Y=9
                    "            M      FFFFFFFFF   ", // Y=10
                    "                       G       ", // Y=11
                    "                               ", // Y=12
                    "                               ", // Y=13
                    "                               ", // Y=14
                    "                               ", // Y=15
                    "                               ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                               ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=42
                    " AAAAAAAAAAAAA   AAAAAAAAAAAAA ", // Y=0
                    "  AUUUUUUUUUA     AZZZZZZZZZA  ", // Y=1
                    "  MUX  Q  XUM      ZVV[V[VVZ   ", // Y=2
                    "  MU   S   UM      ZVK   KVZ   ", // Y=3
                    "  MU       UM      Z[  Q  [Z   ", // Y=4
                    "  MU   S   UM      ZV QJQ VZ   ", // Y=5
                    "  MUX  Q  XUM      ZV QJQ VZ   ", // Y=6
                    "  MUUUUUUUUUM      Z[  Q  [Z   ", // Y=7
                    "  MMMMM MMMMM      ZVK   KVZ   ", // Y=8
                    "              L    ZVV[V[VVZ   ", // Y=9
                    "                   ZZZZZZZZZ   ", // Y=10
                    "                       G       ", // Y=11
                    "                               ", // Y=12
                    "                               ", // Y=13
                    "                               ", // Y=14
                    "                               ", // Y=15
                    "                               ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                               ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=43
                    " AAAAAAAAAAAAA   AAAAAAAAAAAAA ", // Y=0
                    "  AUUUUUUUUUA     AZZZZZZZZZA  ", // Y=1
                    "   UX  Q  XU       FVV[V[VVF   ", // Y=2
                    "   U   S   U       FVK   KVF   ", // Y=3
                    "   U       U       F[  Q  [F   ", // Y=4
                    "   U   S   U       FV QJQ VF   ", // Y=5
                    "   UX  Q  XU       FV QJQ VF   ", // Y=6
                    "   UUUUUUUUU       F[  Q  [F   ", // Y=7
                    "  M   M M   M      FVK   KVF   ", // Y=8
                    "            M L    FVV[V[VVF   ", // Y=9
                    "            M      FFFFFFFFF   ", // Y=10
                    "                       G       ", // Y=11
                    "                               ", // Y=12
                    "                               ", // Y=13
                    "                               ", // Y=14
                    "                               ", // Y=15
                    "                               ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                               ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=44
                    " AAAAAAAAAAAAA   AAAAAAAAAAAAA ", // Y=0
                    "  AUUUUUUUUUA     AZZZZZZZZZA  ", // Y=1
                    "   UX  Q  XU       ZVV[V[VVZ   ", // Y=2
                    "   U   S   U       ZVK   KVZ   ", // Y=3
                    "   U       U       Z[  Q  [Z   ", // Y=4
                    "   U   S   U       ZV QJQ VZ   ", // Y=5
                    "   UX  Q  XU       ZV QJQ VZ   ", // Y=6
                    "   UUUULUUUU       Z[  Q  [Z   ", // Y=7
                    "  M   MLM   M      ZVK [ KVZ   ", // Y=8
                    "       LLLLLLLL    ZVV[V[VVZ   ", // Y=9
                    "            M      ZZZZZZZZZ   ", // Y=10
                    "                       G       ", // Y=11
                    "                               ", // Y=12
                    "                               ", // Y=13
                    "                               ", // Y=14
                    "                               ", // Y=15
                    "                               ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                               ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=45
                    " AAAAAAAAAAAAA   AAAAAAAAAAAAA ", // Y=0
                    "  AUUUUUUUUUA     AZZZZZZZZZA  ", // Y=1
                    "   UX  Q  XU       FVV[V[VVF   ", // Y=2
                    "   U   S   U       FVK[[[KVF   ", // Y=3
                    "   U       U       F[[ Q [[F   ", // Y=4
                    "   U   S   U       FV[QJQ[VF   ", // Y=5
                    "   UX  Q  XU       FV[QJQ[VF   ", // Y=6
                    "   UUUUUUUUU       F[[ Q [[F   ", // Y=7
                    "  M   M M   M      FVK[Q[KVF   ", // Y=8
                    "            M L    FVV[G[VVF   ", // Y=9
                    "            M      FFFFGFFFF   ", // Y=10
                    "                      FGF      ", // Y=11
                    "                      FFF      ", // Y=12
                    "                               ", // Y=13
                    "                               ", // Y=14
                    "                               ", // Y=15
                    "                               ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                               ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=46
                    " AAAAAAAAAAAAA   AAAAAAAAAAAAA ", // Y=0
                    "  AUUUUUUUUUA     AAAAAAAAAAA  ", // Y=1
                    "  MUX  Q  XUM      AVVVVVVVA   ", // Y=2
                    "  MU   S   UM      AVVVVVVVA   ", // Y=3
                    "  MU       UM      AVVVVVVVA   ", // Y=4
                    "  MU   S   UM      AVVVJVVVA   ", // Y=5
                    "  MUX  Q  XUM      AVVVJVVVA   ", // Y=6
                    "  MUUUUUUUUUM      AVVVVVVVA   ", // Y=7
                    "  MMMMM MMMMM      AVVVVVVVA   ", // Y=8
                    "              L    AVVVVVVVA   ", // Y=9
                    "                   AAAAAAAAA   ", // Y=10
                    "                               ", // Y=11
                    "                               ", // Y=12
                    "                               ", // Y=13
                    "                               ", // Y=14
                    "                               ", // Y=15
                    "                               ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                               ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=47
                    " AAAAAAAAAAAAA       AAAAA     ", // Y=0
                    "  AUUUUUUUUUA         AAA      ", // Y=1
                    "   UX  Q  XU          A A      ", // Y=2
                    "   U   S   U          A A      ", // Y=3
                    "   U       U       AAAAAAAAA   ", // Y=4
                    "   U   S   U          ATA      ", // Y=5
                    "   UX  Q  XU          ATA      ", // Y=6
                    "   UUUUUUUUU       AAAATAAAA   ", // Y=7
                    "  M   M M   M         ATA      ", // Y=8
                    "            M L       ATA      ", // Y=9
                    "            M         ATA      ", // Y=10
                    "                       T       ", // Y=11
                    "                       T       ", // Y=12
                    "                       T       ", // Y=13
                    "                       T       ", // Y=14
                    "                       T       ", // Y=15
                    "                               ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                               ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=48
                    " AAAAAAAAAAAAA     AAAAAAAAA   ", // Y=0
                    "  AUUUUUUUUUA         AAA      ", // Y=1
                    "   UX  Q  XU                   ", // Y=2
                    "   U   S   U                   ", // Y=3
                    "   U       U                   ", // Y=4
                    "   U   S   U                   ", // Y=5
                    "   UX  Q  XU                   ", // Y=6
                    "   UUUULUUUU                   ", // Y=7
                    "  M   MLM   M                  ", // Y=8
                    "       LLLLLLLL                ", // Y=9
                    "            M                  ", // Y=10
                    "                               ", // Y=11
                    "                               ", // Y=12
                    "                               ", // Y=13
                    "                               ", // Y=14
                    "                       T       ", // Y=15
                    "                               ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                               ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=49
                    " AAAAAAAAAAAAA   KAAAAAAAAAAAK ", // Y=0
                    "  AUUUUUUUUUA    K  AAAAAAA  K ", // Y=1
                    "  MUX  Q  XUM    K  OKKOKKO  K ", // Y=2
                    "  MU   S   UM    K  O  O  O  K ", // Y=3
                    "  MU       UM    K  O  O  O  K ", // Y=4
                    "  MU   S   UM    K  O  O  O  K ", // Y=5
                    "  MUX  Q  XUM    K  O  O  O  K ", // Y=6
                    "  MUUUUUUUUUM    K  O  O  O  K ", // Y=7
                    "  MMMMM MMMMM    K  O  O  O  K ", // Y=8
                    "            M    K  O  O  O  K ", // Y=9
                    "            M    K  O  O  O  K ", // Y=10
                    "                 K  O  O  O  K ", // Y=11
                    "                 OOOOOOOOOOOOO ", // Y=12
                    "                      FOF      ", // Y=13
                    "                      FFF      ", // Y=14
                    "                      FTF      ", // Y=15
                    "                      FFF      ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                               ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=50
                    " AAAAAAAAAAAAA   AAAAAAAAAAAAA ", // Y=0
                    "  AUUUUUUUUUA     AAAAAAAAAAA  ", // Y=1
                    "   WX  Q  XW       \\\\\\\\\\\\\\\\\\   ", // Y=2
                    "   U   S   U       \\\\\\\\\\\\\\\\\\   ", // Y=3
                    "   U       U       \\\\]]\\]]\\\\   ", // Y=4
                    "   U   S   U       \\\\]]\\]]\\\\   ", // Y=5
                    "   WX  Q  XW       \\\\]]\\]]\\\\   ", // Y=6
                    "   UUUUUUUUU       \\\\]]\\]]\\\\   ", // Y=7
                    "  M   M M   M      \\\\]]\\]]\\\\   ", // Y=8
                    "                   \\\\]]\\]]\\\\   ", // Y=9
                    "                   \\\\]]\\]]\\\\   ", // Y=10
                    "                   \\\\\\\\\\\\\\\\\\   ", // Y=11
                    "                 O \\\\\\\\\\\\\\\\\\ O ", // Y=12
                    "                    O  O  O    ", // Y=13
                    "                       O       ", // Y=14
                    "                       T       ", // Y=15
                    "                               ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                               ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=51
                    " AAAAAAAAAAAAA  AAAAAAAAAAAAAAA", // Y=0
                    "  AUUUUUUUUUA     AA\\\\\\\\\\\\\\AA  ", // Y=1
                    "   WX  Q  XW      \\\\ [X[X[\\\\\\  ", // Y=2
                    "   U   S   U      \\\\ [X[X[\\\\\\  ", // Y=3
                    "   U       U      \\\\ [X[X[\\\\\\  ", // Y=4
                    "   U   S   U      \\\\ [X[X[\\\\\\  ", // Y=5
                    "   WX  Q  XW      \\\\ [X[X[\\\\\\  ", // Y=6
                    "   UYUUUUUYU      \\\\ [X[X[\\\\\\  ", // Y=7
                    "  M   M M   M     \\\\ [X[X[\\\\\\  ", // Y=8
                    "                  \\\\ [X[X[\\\\\\  ", // Y=9
                    "                  \\\\ [X[X[\\\\\\  ", // Y=10
                    "                  \\\\ [X[X[\\\\\\  ", // Y=11
                    "                 O\\\\ [X[X[\\\\\\O ", // Y=12
                    "                   \\\\\\\\\\\\\\\\\\   ", // Y=13
                    "                   DO  O  OD   ", // Y=14
                    "                       T       ", // Y=15
                    "                               ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                               ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=52
                    " AAAAAAAAAAAAA  AAAAAAAAAAAAAAA", // Y=0
                    "  AUUUUUUUUUA    AA\\\\\\\\\\\\\\\\\\AA ", // Y=1
                    "   WX  Q  XW     O\\ J[ [ [J \\O ", // Y=2
                    "   U   S   U     O\\ J     J \\O ", // Y=3
                    "   U       U     O\\ J     J \\O ", // Y=4
                    "   U   S   U     O\\ J     J \\O ", // Y=5
                    "   WX  Q  XW     O\\ J     J \\O ", // Y=6
                    "   UYUUUUUYU     O\\ J     J \\O ", // Y=7
                    "  M   M M   M    O\\ J     J \\O ", // Y=8
                    "                 O\\ J     J \\O ", // Y=9
                    "                 O\\ J     J \\O ", // Y=10
                    "                 O\\ J     J \\O ", // Y=11
                    "                 O\\ J[ [ [J \\O ", // Y=12
                    "                  O\\\\\\\\\\\\\\\\\\O  ", // Y=13
                    "                   OOOOOOOOO   ", // Y=14
                    "                       T       ", // Y=15
                    "                               ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                               ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=53
                    " AAAAAAAAAAAAA  AAAAAAAAAAAAAAA", // Y=0
                    "  AUUUUUUUUUA    AA\\\\\\\\\\\\\\\\\\AA ", // Y=1
                    "   UUUUTUUUU     K\\[[[[[[[[[\\K ", // Y=2
                    "   UUUUUUUUU      \\[       [\\  ", // Y=3
                    "   UUUUUUUUU      ][       []  ", // Y=4
                    "   UUUUUUUUU      ][       []  ", // Y=5
                    "   UUUUTUUUU      ][       []  ", // Y=6
                    "   UUUUUUUUU      ][       []  ", // Y=7
                    "  M   M M   M     ][       []  ", // Y=8
                    "                  ][       []  ", // Y=9
                    "                  ][       []  ", // Y=10
                    "                  \\[       [\\  ", // Y=11
                    "                 O\\[[[[[[[[[\\O ", // Y=12
                    "                   \\\\\\\\\\\\\\\\\\   ", // Y=13
                    "                    O  O  O    ", // Y=14
                    "                       T       ", // Y=15
                    "                               ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                               ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=54
                    " AAAAAAAAAAAAA  AAAAAAAAAAAAAAA", // Y=0
                    "  AAAAATAAAAA    AA\\\\\\\\\\\\\\\\\\AA ", // Y=1
                    "  M   MTM   M    K\\X [J J[ X\\K ", // Y=2
                    "  M   MTM   M     \\X  J J  X\\  ", // Y=3
                    "  M   MTM   M     ]X  J J  X]  ", // Y=4
                    "  M   MTM   M     ]X  J J  X]  ", // Y=5
                    "  M   MTM   M     ]X  J J  X]  ", // Y=6
                    "  M   M M   M     ]X  J J  X]  ", // Y=7
                    "  MMMMMMMMMMM     ]X  J J  X]  ", // Y=8
                    "                  ]X  J J  X]  ", // Y=9
                    "                  ]X  J J  X]  ", // Y=10
                    "                  \\X  J J  X\\  ", // Y=11
                    "                 O\\X [J J[ X\\O ", // Y=12
                    "                   \\\\\\\\\\\\\\\\\\   ", // Y=13
                    "                    O KKK O    ", // Y=14
                    "                       T       ", // Y=15
                    "                       K       ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                               ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=55
                    " AAAAAAAAAAAAA  AAAAAAAAAAAAAAA", // Y=0
                    "      ATTTTTTTTTTTTTTTTT\\\\\\\\AA ", // Y=1
                    "                 O\\[[[ T [ [\\O ", // Y=2
                    "                 O\\[   T   [\\O ", // Y=3
                    "                 O\\[   T   [\\O ", // Y=4
                    "                 O\\[   T   [\\O ", // Y=5
                    "                 O\\[   T   [\\O ", // Y=6
                    "                 O\\[   T   [\\O ", // Y=7
                    "                 O\\[   T   [\\O ", // Y=8
                    "                 O\\[   T   [\\O ", // Y=9
                    "                 O\\[   T   [\\O ", // Y=10
                    "                 O\\[   T   [\\O ", // Y=11
                    "                 O\\[[[ T [[[\\O ", // Y=12
                    "                 OO\\\\\\\\T\\\\\\\\OO ", // Y=13
                    "                  OOOOKTKOOOO  ", // Y=14
                    "                      KTK      ", // Y=15
                    "                      KKK      ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                               ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=56
                    "     AAAAA      AAAAAAAAAAAAAAA", // Y=0
                    "      AAA        AA\\\\\\\\\\\\\\\\\\AA ", // Y=1
                    "                 K\\X [J J[ X\\K ", // Y=2
                    "                  \\X  J J  X\\  ", // Y=3
                    "                  ]X  J J  X]  ", // Y=4
                    "                  ]X  J J  X]  ", // Y=5
                    "                  ]X  J J  X]  ", // Y=6
                    "                  ]X  J J  X]  ", // Y=7
                    "                  ]X  J J  X]  ", // Y=8
                    "                  ]X  J J  X]  ", // Y=9
                    "                  ]X  J J  X]  ", // Y=10
                    "                  \\X  J J  X\\  ", // Y=11
                    "                 O\\X [J J[ X\\O ", // Y=12
                    "                   \\\\\\\\\\\\\\\\\\   ", // Y=13
                    "                    O KKK O    ", // Y=14
                    "                       K       ", // Y=15
                    "                       K       ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                               ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=57
                    "     AAAAA      AAAAAAAAAAAAAAA", // Y=0
                    "      AAA        AA\\\\\\\\\\\\\\\\\\AA ", // Y=1
                    "                 K\\[[[[[[[[[\\K ", // Y=2
                    "                  \\[       [\\  ", // Y=3
                    "                  ][       []  ", // Y=4
                    "                  ][       []  ", // Y=5
                    "                  ][       []  ", // Y=6
                    "                  ][       []  ", // Y=7
                    "                  ][       []  ", // Y=8
                    "                  ][       []  ", // Y=9
                    "                  ][       []  ", // Y=10
                    "                  \\[       [\\  ", // Y=11
                    "                 O\\[[[[[[[[[\\O ", // Y=12
                    "                   \\\\\\\\\\\\\\\\\\   ", // Y=13
                    "                    O  O  O    ", // Y=14
                    "                               ", // Y=15
                    "                               ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                               ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=58
                    "   AAAAAAAAA    AAAAAAAAAAAAAAA", // Y=0
                    "    A AAA A      AA\\\\\\\\\\\\\\\\\\AA ", // Y=1
                    "    A     A      O\\ J[ [ [J \\O ", // Y=2
                    "    A     A      O\\ J     J \\O ", // Y=3
                    "    AAAAAAA      O\\ J     J \\O ", // Y=4
                    "                 O\\ J     J \\O ", // Y=5
                    "                 O\\ J     J \\O ", // Y=6
                    "                 O\\ J     J \\O ", // Y=7
                    "                 O\\ J     J \\O ", // Y=8
                    "                 O\\ J     J \\O ", // Y=9
                    "                 O\\ J     J \\O ", // Y=10
                    "                 O\\ J     J \\O ", // Y=11
                    "                 O\\ J[ [ [J \\O ", // Y=12
                    "                  O\\\\\\\\\\\\\\\\\\O  ", // Y=13
                    "                   OOOOOOOOO   ", // Y=14
                    "                               ", // Y=15
                    "                               ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                               ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=59
                    "   AAAAAAAAA    AAAAAAAAAAAAAAA", // Y=0
                    "   AZ^PPP^ZA      AA\\\\\\\\\\\\\\AA  ", // Y=1
                    "   AZ^^^^^ZA      \\\\ [X[X[\\\\\\  ", // Y=2
                    "   AZ^^^^^ZA      \\\\ [X[X[\\\\\\  ", // Y=3
                    "   A_XX_XX_A      \\\\ [X[X[\\\\\\  ", // Y=4
                    "                  \\\\ [X[X[\\\\\\  ", // Y=5
                    "                  \\\\ [X[X[\\\\\\  ", // Y=6
                    "                  \\\\ [X[X[\\\\\\  ", // Y=7
                    "                  \\\\ [X[X[\\\\\\  ", // Y=8
                    "                  \\\\ [X[X[\\\\\\  ", // Y=9
                    "                  \\\\ [X[X[\\\\\\  ", // Y=10
                    "                  \\\\ [X[X[\\\\\\  ", // Y=11
                    "                 O\\\\ [X[X[\\\\\\O ", // Y=12
                    "                   \\\\\\\\\\\\\\\\\\   ", // Y=13
                    "                   DO  O  OD   ", // Y=14
                    "                               ", // Y=15
                    "                               ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                               ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=60
                    "   AAAAAAAAA     AAAAAAAAAAAAA ", // Y=0
                    "    ZPPPPPZ       AAAAAAAAAAA  ", // Y=1
                    "    ZPPPPPZ        \\\\\\\\\\\\\\\\\\   ", // Y=2
                    "    ZPPPPPZ        \\\\\\\\\\\\\\\\\\   ", // Y=3
                    "   A_XX_XX_A       \\\\]]\\]]\\\\   ", // Y=4
                    "                   \\\\]]\\]]\\\\   ", // Y=5
                    "                   \\\\]]\\]]\\\\   ", // Y=6
                    "                   \\\\]]\\]]\\\\   ", // Y=7
                    "                   \\\\]]\\]]\\\\   ", // Y=8
                    "                   \\\\]]\\]]\\\\   ", // Y=9
                    "                   \\\\]]\\]]\\\\   ", // Y=10
                    "                   \\\\\\\\\\\\\\\\\\   ", // Y=11
                    "                 O \\\\\\\\\\\\\\\\\\ O ", // Y=12
                    "                    O  O  O    ", // Y=13
                    "                       O       ", // Y=14
                    "                               ", // Y=15
                    "                               ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                               ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=61
                    "   AAAAAAAAA     KAAAAAAAAAAAK ", // Y=0
                    "   AZ^^^^^ZA     K  AAAAAAA  K ", // Y=1
                    "   AZ^^`^^ZA     K  OKKOKKO  K ", // Y=2
                    "   AZ^^^^^ZA     K  O  O  O  K ", // Y=3
                    "   A_XX_XX_A     K  O  O  O  K ", // Y=4
                    "                 K  O  O  O  K ", // Y=5
                    "                 K  O  O  O  K ", // Y=6
                    "                 K  O  O  O  K ", // Y=7
                    "                 K  O  O  O  K ", // Y=8
                    "                 K  O  O  O  K ", // Y=9
                    "                 K  O  O  O  K ", // Y=10
                    "                 K  O  O  O  K ", // Y=11
                    "                 OOOOOOOOOOOOO ", // Y=12
                    "                       O       ", // Y=13
                    "                               ", // Y=14
                    "                               ", // Y=15
                    "                               ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                               ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
            { // Z=62
                    "   AAAAAAAAA       AAAAAAAAA   ", // Y=0
                    "    A     A                    ", // Y=1
                    "    A     A                    ", // Y=2
                    "    A     A                    ", // Y=3
                    "    AAAAAAA                    ", // Y=4
                    "                               ", // Y=5
                    "                               ", // Y=6
                    "                               ", // Y=7
                    "                               ", // Y=8
                    "                               ", // Y=9
                    "                               ", // Y=10
                    "                               ", // Y=11
                    "                               ", // Y=12
                    "                               ", // Y=13
                    "                               ", // Y=14
                    "                               ", // Y=15
                    "                               ", // Y=16
                    "                               ", // Y=17
                    "                               ", // Y=18
                    "                               ", // Y=19
                    "                               ", // Y=20
                    "                               ", // Y=21
                    "                               ", // Y=22
                    "                               ", // Y=23
                    "                               ", // Y=24
                    "                               ", // Y=25
                    "                               ", // Y=26
            },
    };
    /** 结构数据里允许出现的符号全集（必须与 {@link #build} 里的 {@code .where(...)} 一一对应）。 */
    /** GTO 模块并入后新增的结构符号（数字/标点，都有对应 .where 绑定）。 */
    private static final String UPGRADED_EXTRA_SYMBOLS = "!#$%&()*0123456789=?~@";

    private static final String DECLARED_SYMBOLS = " ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`";

    /** 结构总格数（自检用）。 */
    public static final int CELLS = WIDTH * HEIGHT * LENGTH;

    /** 构建结构。{@code FactoryBlockPattern.start()} 用 GTCEu 1.4.4 的默认轴
     * {@code (LEFT, UP, FRONT)} —— 与 mbs 头部 {@code charDir=LEFT / stringDir=UP / aisleDir=FRONT} 一致，
     * 所以<b>直填，不重排</b>。 */
    public static BlockPattern build(MultiblockMachineDefinition definition) {
        verifySymbols(SLICES, "基础", WIDTH, LENGTH, "");
        // ── 附属模块口：线程仓 / 超频仓 / 加速仓 / 并行仓（GTO 模块 'd'/'c' 位的语义）──
        TraceabilityPredicate moduleHatches =
                ability(com.gtlmodular.common.data.thread.ThreadHatchMachines.THREAD_HATCH)
                        .setMaxGlobalLimited(1)
                        .or(com.gtlmodular.common.data.fuel.OverclockHatches.predicate())
                        .or(com.gtlmodular.common.data.fuel.AccelerateHatches.predicate())
                        .or(ability(PartAbility.PARALLEL_HATCH).setMaxGlobalLimited(1).setPreviewCount(1));

        // ── 主结构正面舱面（GTO 的 autoLaserAbilities + MAINTENANCE + 附属模块口）──
        TraceabilityPredicate hatchFace = blocksOf("gtceu", "high_power_casing")
                .or(ability(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                .or(ability(PartAbility.EXPORT_ITEMS).setPreviewCount(1))
                .or(ability(PartAbility.IMPORT_FLUIDS).setPreviewCount(1))
                .or(ability(PartAbility.EXPORT_FLUIDS).setPreviewCount(1))
                .or(ability(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(8, 0))
                .or(ability(PartAbility.INPUT_LASER).setMaxGlobalLimited(4, 1))
                .or(ability(PartAbility.MAINTENANCE).setExactLimit(1))
                .or(moduleHatches);
        FactoryBlockPattern pattern = FactoryBlockPattern.start();
        for (String[] slice : SLICES) {
            pattern.aisle(slice);
        }
        return pattern
                .where(' ', Predicates.any())
                .where('`', Predicates.controller(Predicates.blocks(definition.get())))
                .where('A', blocksOf("gtceu", "stress_proof_casing"))
                .where('C', blocksOf("gtceu", "robust_machine_casing"))
                .where('D', lights())
                .where('E', blocksOf("gtceu", "watertight_casing"))
                .where('F', frame("steel"))
                .where('G', Predicates.blocks(WoodFactoryBlocks.HIGH_PRESSURE_PIPE_CASING.get()))
                .where('H', blocksOf("gtceu", "inert_machine_casing"))
                .where('I', frame("stainless_steel"))
                .where('J', blocksOf("gtceu", "titanium_pipe_casing"))
                .where('K', frame("hslasteel"))
                .where('L', Predicates.blocks(WoodFactoryBlocks.CHEMICAL_CORROSION_RESISTANT_PIPE_CASING.get()))
                .where('M', frame("black_steel"))
                .where('N', Predicates.blocks(PetrochemBlocks.PPS_CORROSION_RESISTANT_MECHANICAL_HOUSING.get()))
                .where('O', blocksOf("gtlcore", "iridium_casing"))
                .where('P', blocksOf("gtceu", "high_power_casing"))
                .where('Q', blocksOf("gtceu", "tungstensteel_pipe_casing"))
                .where('R', Predicates.blocks(PetrochemBlocks.HIGH_STRENGTH_SUPPORT_MECHANICAL_CASING.get()))
                .where('S', frame("watertight_steel"))
                .where('T', Predicates.blocks(WoodFactoryBlocks.OIL_GAS_TRANSPORTATION_PIPE_CASING.get()))
                .where('U', blocksOf("gtceu", "clean_machine_casing"))
                .where('V', Predicates.blocks(PetrochemBlocks.CALCIUM_OXIDE_CERAMIC_CASING.get()))
                .where('W', Predicates.blocks(PetrochemBlocks.INDUSTRIAL_FRAMELESS_GLASS.get()))
                .where('X', blocksOf("gtceu", "electrolytic_cell"))
                .where('Y', Predicates.abilities(PartAbility.MUFFLER))
                .where('Z', blocksOf("gtlcore", "oxidation_resistant_hastelloy_n_mechanical_casing"))
                .where('[', Predicates.heatingCoils())
                .where('\\', Predicates.blocks(WoodFactoryBlocks.PRESSURE_CONTAINMENT_CASING.get()))
                .where(']', blocksOf("gtceu", "fusion_glass"))
                .where('^', hatchFace)
                .where('_', blocksOf("gtceu", "tempered_glass"))
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
            throw new IllegalStateException("燃料精炼复合体 " + tag + "结构：共 " + slices.length + " 片，应为 " + length);
        }
        String allowed = DECLARED_SYMBOLS + extraSymbols;
        for (int z = 0; z < slices.length; z++) {
            String[] rows = slices[z];
            if (rows.length != HEIGHT) {
                throw new IllegalStateException("燃料精炼复合体 " + tag + "结构：Z=" + z + " 只有 " + rows.length
                        + " 行，应为 " + HEIGHT);
            }
            for (int y = 0; y < rows.length; y++) {
                String row = rows[y];
                if (row.length() != width) {
                    throw new IllegalStateException("燃料精炼复合体 " + tag + "结构：SLICES[" + z + "][" + y + "] 行长 "
                            + row.length() + "，应为 " + width);
                }
                for (int x = 0; x < row.length(); x++) {
                    char c = row.charAt(x);
                    if (c != ' ' && allowed.indexOf(c) < 0) {
                        throw new IllegalStateException(String.format(
                                "燃料精炼复合体 " + tag + "结构：符号 '%c'（0x%02X）在 SLICES[%d][%d] 第 %d 列出现，"
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
            GTLModular.LOGGER.warn("[gtlmodular] 燃料精炼复合体：方块 {}/{} 不存在，该符号退回 any()",
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

    /** GTO 的 {@code GTOPredicates.light()} = GT 灯全集（{@code GTBlocks.LAMPS}）；为空时 any()。 */
    private static TraceabilityPredicate lights() {
        TraceabilityPredicate predicate = null;
        for (com.tterrag.registrate.util.entry.BlockEntry<com.gregtechceu.gtceu.common.block.LampBlock> lamp
                : com.gregtechceu.gtceu.common.data.GTBlocks.LAMPS.values()) {
            TraceabilityPredicate one = Predicates.blocks(lamp.get());
            predicate = predicate == null ? one : predicate.or(one);
        }
        if (predicate == null) {
            GTLModular.LOGGER.warn("[gtlmodular] 燃料精炼复合体：GTBlocks.LAMPS 为空，灯位退回 any()");
            return Predicates.any();
        }
        return predicate;
    }

    /** GTO 可选模块并入后的切片：下列数字/符号格为二级结构格。 */
    private static final String[][] UPGRADED_SLICES = {
            {
                    "444AAAAAAAAA         AAAAA     ",
                    "444                   AAA      ",
                    "444                    C       ",
                    "                     CCCCC     ",
                    "                       C       ",
                    "                       C       ",
                    "                       C       ",
                    "                       C       ",
                    "                     CCCCC     ",
                    "                       C       ",
                    "                       C       ",
                    "                       D       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    "44AAAAAAAAAAA      AAAAAAAAA   ",
                    "444 AAAAAAA          AAAAA     ",
                    "444                  EEEEE     ",
                    "                   CCEEEEECC   ",
                    "                     EEEEE     ",
                    "                     EEEEE     ",
                    "     FGGGF           EEEEE     ",
                    "                     EEEEE     ",
                    "                   CCEEEEECC   ",
                    "                     EEEEE     ",
                    "                               ",
                    "     FGGGF                     ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "     FGGGF                     ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "     FGGGF                     ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    "44AAAAHHHAAAA     AAAAAAAAAAA  ",
                    "444AAAHHHAAA       AAAAAAAAA   ",
                    "444 I HHH I        EEEEEEEEE   ",
                    "    I HHH I       CEE     EEC  ",
                    "    I HHH I        EE     EE   ",
                    "    I HHH I        EEJJKJJEE   ",
                    "    IGHHHGI        EE     EE   ",
                    "    I HHH I        EE     EE   ",
                    "    I HHH I       CEE     EEC  ",
                    "    I HHH I        EEJJKJJEE   ",
                    " 8  I HHH I          EEEEE     ",
                    " 8  IGHHHGI                    ",
                    " 8  I HHH I                    ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    IGHHHGI                    ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    IGHHHGI                    ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    D HHH D                    ",
                    "                               ",
                    "                               ",
            },
            {
                    "44AAAHAAAHAAA    AAAAAAAAAAAAA ",
                    "4#4AAHHHHHAA      AAAAAAAAAAA  ",
                    "4#4  H   H        EEEEEEEEEEE  ",
                    "$#   H   H       CE         EC ",
                    "$#   H   H        E         E  ",
                    "$#   H   H        EJJJJKJJJJE  ",
                    "$# FGHHHHHGF      E         E  ",
                    "$#   H   H        E         E  ",
                    "$#   H   H       CE         EC ",
                    "$#   H   H        EJJJJKJJJJE  ",
                    "$#8  H   H         EEE E EEE   ",
                    "$#8FGHHHHHGF          EEE      ",
                    "$#8  H   H                     ",
                    "$#   H   H                     ",
                    "$####H   H                     ",
                    "     H   H                     ",
                    "   FGHHHHHGF                   ",
                    "     H   H                     ",
                    "     H   H                     ",
                    "     H   H                     ",
                    "     H   H                     ",
                    "   FGHHHHHGF                   ",
                    "     H   H                     ",
                    "     H   H                     ",
                    "     H   H                     ",
                    "      HHH                      ",
                    "                               ",
            },
            {
                    "44AAHAAAAAHAA    AAAAAAAAAAAAA ",
                    "444AHHHHHHHA      AAAAAAAAAAA  ",
                    "444 H     H       EEEEEEEEEEE  ",
                    "    H     H      CE         EC ",
                    "    H     H       E         E  ",
                    "    H     H       EJJJJKJJJJE  ",
                    "   GHHJJJHHG      E         E  ",
                    "    H     H       E         E  ",
                    "    H     H      CE         EC ",
                    "    H     H       EJJJJKJJJJE  ",
                    " 8  H     H        E       E   ",
                    " 8 GHHJJJHHG        EE E EE    ",
                    " 8  H     H           EEE      ",
                    "    H     H                    ",
                    "    H     H                    ",
                    "    H     H                    ",
                    "   GHHJJJHHG                   ",
                    "    H     H                    ",
                    "    H     H                    ",
                    "    H     H                    ",
                    "    H     H                    ",
                    "   GHHJJJHHG                   ",
                    "    H     H                    ",
                    "    H     H                    ",
                    "    H     H                    ",
                    "     H   H                     ",
                    "      HHH                      ",
            },
            {
                    "44AAHAAAAAHAAAAAAAAAAAAAAAAAAAA",
                    "444AHHHHHHHA     AAAAAAAAAAAAA ",
                    "444 H  J  H      EEEEEEEEEEEEE ",
                    "    H  J  H     CE           EC",
                    "    H  J  H      E           E ",
                    "    H  J  H      EJJJJJKJJJJJE ",
                    "   GHHJJJHHG     E           E ",
                    "    H  J  H      E           E ",
                    "    H  J  H     CE           EC",
                    "    H  J  H      EJJJJJKJJJJJE ",
                    "    H  J  H       EE       EE  ",
                    "   GHHJJJHHG        E     E    ",
                    "    H  J  H          EEEEE     ",
                    "    H  J  H                    ",
                    "    H  J  H                    ",
                    "    H  J  H                    ",
                    "   GHHJJJHHG                   ",
                    "    H  J  H                    ",
                    "    H  J  H                    ",
                    "    H  J  H                    ",
                    "    H  J  H                    ",
                    "   GHHJJJHHG                   ",
                    "    H  J  H                    ",
                    "    H  J  H                    ",
                    "    H  J  H                    ",
                    "     H   H                     ",
                    "      HHH                      ",
            },
            {
                    "44AAHAAAAAHAAAAAAAAAAAAAAAAAAAA",
                    "444AHHHHHHHAAAAAAAAAAAAAAAAAAAA",
                    "444 H     H      EEEEEEEEEEEEE ",
                    "    H     H     CE           EC",
                    "    H     H      E           E ",
                    "    H     H      EJJJJJKJJJJJE ",
                    "   GHHJJJHHG     E           E ",
                    "    H     H      E           E ",
                    "    H     H     CE           EC",
                    "    H     H      EJJJJJKJJJJJE ",
                    "    H     H       E         E  ",
                    "   GHHJJJHHG       E       E   ",
                    "    H     H         EEEEEEE    ",
                    "    H     H           FFF      ",
                    "    H     H            F       ",
                    " 8  H     H                    ",
                    " 8 GHHJJJHHG                   ",
                    " 8  H     H                    ",
                    "    H     H                    ",
                    "    H     H                    ",
                    "    H     H                    ",
                    "   GHHJJJHHG                   ",
                    "    H     H                    ",
                    "    H     H                    ",
                    "    H     H                    ",
                    "     H   H                     ",
                    "      HHH                      ",
            },
            {
                    "44AAAHAAAHAAAAAAAAAAAAAAAAAAAAA",
                    "4#4AAHHHHHAAAAAAAAAAAAAAAAAAAAA",
                    "4#4  H   H      CEEEEEEEEEEEEEC",
                    "$#   H   H      CE     L     EC",
                    "$#   H   H      CE     L     EC",
                    "$#   H   H      CEKKKKKLKKKKKEC",
                    "$# FGHHHHHGF    CE     L     EC",
                    "$#   H   H      CE     L     EC",
                    "$#   H   H      CE     L     EC",
                    "$#   H   H      CEKKKKKLKKKKKEC",
                    "$#   H   H      C EE   L   EE C",
                    "$# FGHHHHHGF    D  EE  L  EE  D",
                    "$#   H   H          EEELEEE    ",
                    "$#   H   H    LLLLLLLLLLF      ",
                    "$#   H   H            FFF      ",
                    "$#8  H   H                     ",
                    "$#8FGHHHHHGF                   ",
                    "$#8  H   H                     ",
                    "$#   H   H                     ",
                    "$####H   H                     ",
                    "     H   H                     ",
                    "   FGHHHHHGF                   ",
                    "     H   H                     ",
                    "     H   H                     ",
                    "     H   H                     ",
                    "      HHH                      ",
                    "                               ",
            },
            {
                    "44AAAAHHHAAAAAAAAAAAAAAAAAAAAAA",
                    "444AAAHHHAAAAAAAAAAAAAAAAAAAAAA",
                    "444 I HHH I  F F EEEEEEEEEEEEE ",
                    "    I HHH I  F FCE     L     EC",
                    "    I HHH I  F F E           E ",
                    "    I HHH I  F F EJJJJJKJJJJJE ",
                    "    IGHHHGI  F F E           E ",
                    "    I HHH I  F F E           E ",
                    "    I HHH I  F FCE           EC",
                    "    I HHH I  F F EJJJJJKJJJJJE ",
                    "    I HHH I  F F  E         E  ",
                    "    IGHHHGI  F F   E       E   ",
                    "    I HHH I  FFF    EEEEEEE    ",
                    "    I HHH I  FLF      FFF      ",
                    "    I HHH I  FFF       F       ",
                    " 8  I HHH I                    ",
                    " 8  IGHHHGI                    ",
                    " 8  I HHH I                    ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    IGHHHGI                    ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    D HHH D                    ",
                    "                               ",
                    "                               ",
            },
            {
                    "44AAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                    "444 AAAAAAA      AAAAAAAAAAAAA ",
                    "444              EEEEEEEEEEEEE ",
                    "                CE     L     EC",
                    "                 E           E ",
                    "                 EJJJJJKJJJJJE ",
                    "     FGGGF       E           E ",
                    "                 E           E ",
                    "                CE           EC",
                    "                 EJJJJJKJJJJJE ",
                    "                  EE       EE  ",
                    "     FGGGF          E     E    ",
                    "                     EEEEE     ",
                    "              L                ",
                    "                               ",
                    "                               ",
                    "     FGGGF                     ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "     FGGGF                     ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    "444AAAAAAAAA     AAAAAAAAAAAAA ",
                    "444               AAAAAAAAAAA  ",
                    "444               EEEEEEEEEEE  ",
                    "                 CE    L    EC ",
                    "                  E         E  ",
                    "                  EJJJJKJJJJE  ",
                    "      FGF         E         E  ",
                    "                  E         E  ",
                    "                 CE         EC ",
                    "                  EJJJJKJJJJE  ",
                    "                   E       E   ",
                    "      FGF           EE E EE    ",
                    "                      EEE      ",
                    "              L                ",
                    "                               ",
                    "                               ",
                    "      FGF                      ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "      FGF                      ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    "44AAAAAAAAAAA    AAAAAAAAAAAAA ",
                    "444 AAAAAAA       AAAAAAAAAAA  ",
                    "444               EEEEEEEEEEE  ",
                    "                 CE    L    EC ",
                    "                  E         E  ",
                    "                  EJJJJKJJJJE  ",
                    "     FGGGF        E         E  ",
                    "                  E         E  ",
                    "                 CE         EC ",
                    "                  EJJJJKJJJJE  ",
                    "                   EEE E EEE   ",
                    "     FGGGF            EEE      ",
                    "                               ",
                    "              L                ",
                    "                               ",
                    "                               ",
                    "     FGGGF                     ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "     FGGGF                     ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    "44AAAAHHHAAAA     AAAAAAAAAAA  ",
                    "444AAAHHHAAA       AAAAAAAAA   ",
                    "444 I HHH I        EEEEEEEEE   ",
                    "    I HHH I       CEE  L  EEC  ",
                    "    I HHH I        EE     EE   ",
                    " 8  I HHH I        EEJJKJJEE   ",
                    " 8  IGHHHGI        EE     EE   ",
                    " 8  I HHH I        EE     EE   ",
                    "    I HHH I       CEE     EEC  ",
                    "    I HHH I        EEJJKJJEE   ",
                    "    I HHH I          EEEEE     ",
                    "    IGHHHGI                    ",
                    "    I HHH I                    ",
                    "    I HHH I   L                ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    IGHHHGI                    ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    IGHHHGI                    ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    D HHH D                    ",
                    "                               ",
                    "                               ",
            },
            {
                    "44AAAHAAAHAAA      AAAAAAAAA   ",
                    "4#4AAHHHHHAA         AAAAA     ",
                    "4#4  H   H           EEEEE     ",
                    "$#   H   H         CCEELEECC   ",
                    "$#   H   H           EEEEE     ",
                    "$#8  H   H           EEEEE     ",
                    "$#8FGHHHHHGF         EEEEE     ",
                    "$#8  H   H           EEEEE     ",
                    "$#   H   H         CCEEEEECC   ",
                    "$####H   H           EEEEE     ",
                    "     H   H                     ",
                    "   FGHHHHHGF                   ",
                    "     H   H                     ",
                    "     H   H    L                ",
                    "     H   H                     ",
                    "     H   H                     ",
                    "   FGHHHHHGF                   ",
                    "     H   H                     ",
                    "     H   H                     ",
                    "     H   H                     ",
                    "     H   H                     ",
                    "   FGHHHHHGF                   ",
                    "     H   H                     ",
                    "     H   H                     ",
                    "     H   H                     ",
                    "      HHH                      ",
                    "                               ",
            },
            {
                    "44AAHAAAAAHAAF F     AAAAA     ",
                    "444AHHHHHHHA F F      AAA      ",
                    "444 H     H  F F       C       ",
                    "    H     H  F F     CCLCC     ",
                    "    H     H  F F       C       ",
                    " 8  H     H  F F       C       ",
                    " 8 GHHJJJHHG F F       C       ",
                    " 8  H     H  F F       C       ",
                    "    H     H  F F     CCCCC     ",
                    "    H     H  F F       C       ",
                    "    H     H  F F       C       ",
                    "   GHHJJJHHG F F       D       ",
                    "    H     H  FFF               ",
                    "    H     H  FLF               ",
                    "    H     H  FFF               ",
                    "    H     H  F F               ",
                    "   GHHJJJHHG F F               ",
                    "    H     H  F F               ",
                    "    H     H  F F               ",
                    "    H     H  F F               ",
                    "    H     H  F F               ",
                    "   GHHJJJHHG F F               ",
                    "    H     H  F F               ",
                    "    H     H                    ",
                    "    H     H                    ",
                    "     H   H                     ",
                    "      HHH                      ",
            },
            {
                    "44AAHAAAAAHAA        AAAAA     ",
                    "444AHHHHHHHA          AAA      ",
                    "444 H  J  H                    ",
                    "    H  J  H            L       ",
                    "    H  J  H            L       ",
                    "    H  J  H            L       ",
                    "   GHHJJJHHG                   ",
                    "    H  J  H                    ",
                    "    H  J  H                    ",
                    "    H  J  H                    ",
                    "    H  J  H                    ",
                    "   GHHJJJHHG                   ",
                    "    H  J  H                    ",
                    "    H  J  H   L                ",
                    "    H  J  H                    ",
                    "    H  J  H  F F               ",
                    "   GHHJJJHHGGGGGGGGGGGGG       ",
                    "    H  J  H  F F               ",
                    "    H  J  H                    ",
                    "    H  J  H                    ",
                    "    H  J  H                    ",
                    "   GHHJJJHHGGGGGGGGGGGGG       ",
                    "    H  J  H  F F               ",
                    "    H  J  H                    ",
                    "    H  J  H                    ",
                    "     H   H                     ",
                    "      HHH                      ",
            },
            {
                    "44AAHAAAAAHAAF F     AAAAA     ",
                    "444AHHHHHHHA F F      AAA      ",
                    "444 H     H  F F               ",
                    "    H     H  F F               ",
                    "    H     H  F F               ",
                    "    H     H  F F   LLLLLLLLL   ",
                    "   GHHJJJHHG F F               ",
                    "    H     H  F F               ",
                    "    H     H  F F               ",
                    "    H     H  F F               ",
                    " 8  H     H  F F               ",
                    " 8 GHHJJJHHG F F               ",
                    " 8  H     H  FFF               ",
                    "    H     H  FLF               ",
                    "    H     H  FFF               ",
                    "    H     H  F F               ",
                    "   GHHJJJHHG F F       G       ",
                    "    H     H  F F               ",
                    "    H     H  F F               ",
                    "    H     H  F F               ",
                    "    H     H  F F               ",
                    "   GHHJJJHHG F F       G       ",
                    "    H     H  F F               ",
                    "    H     H                    ",
                    "    H     H                    ",
                    "     H   H                     ",
                    "      HHH                      ",
            },
            {
                    "44AAAHAAAHAAA    AAAAAAAAAAAAA ",
                    "4#4AAHHHHHAA      AAA AAA AAA  ",
                    "4#4  H   H            F F      ",
                    "$#   H   H         M  F F  M   ",
                    "$#   H   H        NNN F F NNN  ",
                    "$#   H   H       MNLNMF FMNLNM ",
                    "$# FGHHHHHGF      NNN F F NNN  ",
                    "$#   H   H         M  F F  M   ",
                    "$#   H   H            F F      ",
                    "$#   H   H         M  F F  M   ",
                    "$#8  H   H        OOO F F OOO  ",
                    "$#8FGHHHHHGF     MOPOMF FMOPOM ",
                    "$#8  H   H        OOO F F OOO  ",
                    "$#   H   H    L    M  F F  M   ",
                    "$####H   H            F F      ",
                    "     H   H            FFF      ",
                    "   FGHHHHHGF          FGF      ",
                    "     H   H            FFF      ",
                    "     H   H            F F      ",
                    "     H   H            F F      ",
                    "     H   H            FFF      ",
                    "   FGHHHHHGF          FGF      ",
                    "     H   H            FFF      ",
                    "     H   H                     ",
                    "     H   H                     ",
                    "      HHH                      ",
                    "                               ",
            },
            {
                    "44AAAAHHHAAAA    AAAAAAAAAAAAA ",
                    "444AAAHHHAAA      AAA AAA AAA  ",
                    "444 I HHH I        M       M   ",
                    "    I HHH I       NNN     NNN  ",
                    "    I HHH I      NNNNN   NNNNN ",
                    "    I HHH I     MNNQNNM MNNQNNM",
                    "    IGHHHGI      NNNNN   NNNNN ",
                    "    I HHH I       NNN     NNN  ",
                    "    I HHH I        M       M   ",
                    "    I HHH I       OOO     OOO  ",
                    " 8  I HHH I      OOOOO   OOOOO ",
                    " 8  IGHHHGI     MOOQOOM MOOQOOM",
                    " 8  I HHH I      OOOOO   OOOOO ",
                    "    I HHH I   L   OOO     OOO  ",
                    "    I HHH I       DMD     DMD  ",
                    "    I HHH I                    ",
                    "    IGHHHGI            G       ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    IGHHHGI            G       ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    D HHH D                    ",
                    "                               ",
                    "                               ",
            },
            {
                    "44AAAAAAAAAAA    AAAAAAAAAAAAA ",
                    "444 AAAAAAA      RAAARAAARAAAR ",
                    "464              RRRRR   RRRRR ",
                    "666              RNNNR   RNNNR ",
                    "                RNSSSNR RNSSSNR",
                    "                RNSQSNR RNSQSNR",
                    "     FGGGF      RNSSSNR RNSSSNR",
                    "                 RNNNR   RNNNR ",
                    "                 RRRRR   RRRRR ",
                    "                 ROOOR   ROOOR ",
                    "                RO   OR RO   OR",
                    "     FGGGF      RO Q OR RO Q OR",
                    "                RO   OR RO   OR",
                    "              L  ROOOR   ROOOR ",
                    "                  RRR     RRR  ",
                    "                               ",
                    "     FGGGF             G       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "     FGGGF             G       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    "444AAAAAAAAA     AAAAAAAAAAAAA ",
                    "444               AAA AAA AAA  ",
                    "464                M       M   ",
                    "666               NNN     NNN  ",
                    "                 NSSSN   NSSSN ",
                    "                MNSQSNM MNSQSNM",
                    "      FGF        NSSSN   NSSSN ",
                    "                  NNN     NNN  ",
                    "                   M       M   ",
                    "                  OOO     OOO  ",
                    "                 O   O   O   O ",
                    "      FGF       MO Q OM MO Q OM",
                    "                 O   O   O   O ",
                    "              L   OOO     OOO  ",
                    "                   M       M   ",
                    "                               ",
                    "      FGF              G       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "      FGF              G       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    "44AAAAAAAAAAA    AAAAAAAAAAAAA ",
                    "444 AAAAAAA       AAA AAA AAA  ",
                    "464                M       M   ",
                    "666               NNN     NNN  ",
                    "                 NSSSN   NSSSN ",
                    "                MNSQSNM MNSQSNM",
                    "     FGGGF       NSSSN   NSSSN ",
                    "                  NNN     NNN  ",
                    "                   M       M   ",
                    "                  OOO     OOO  ",
                    "                 O   O   O   O ",
                    "     FGGGF      MO Q OM MO Q OM",
                    "                 O   O   O   O ",
                    "              L   OOO     OOO  ",
                    "                   M       M   ",
                    "                               ",
                    "     FGGGF             G       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "     FGGGF             G       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    "44AAAAHHHAAAA    AAAAAAAAAAAAA ",
                    "444AAAHHHAAA      AAA AAA AAA  ",
                    "444 I HHH I        M       M   ",
                    "    I HHH I       NNN     NNN  ",
                    "    I HHH I      NSSSN   NSSSN ",
                    " 8  I HHH I     MNSQSNM MNSQSNM",
                    " 8  IGHHHGI      NSSSN   NSSSN ",
                    " 8  I HHH I       NNN     NNN  ",
                    "    I HHH I        M       M   ",
                    "    I HHH I       OOO     OOO  ",
                    "    I HHH I      O   O   O   O ",
                    "    IGHHHGI     MO Q OM MO Q OM",
                    "    I HHH I      O   O   O   O ",
                    "    I HHH I   L   OOO     OOO  ",
                    "    I HHH I        M       M   ",
                    "    I HHH I                    ",
                    "    IGHHHGI            G       ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    IGHHHGI            G       ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    D HHH D                    ",
                    "                               ",
                    "                               ",
            },
            {
                    "44AAAHAAAHAAAAAAAAAAAAAAAAAAAA ",
                    "4#4AAHHHHHAA      AAA AAA AAA  ",
                    "4#4  H   H         M       M   ",
                    "$#   H   H        NNN     NNN  ",
                    "$#   H   H       NSSSN   NSSSN ",
                    "$#8  H   H      MNSQSNM MNSQSNM",
                    "$#8FGHHHHHGF     NSSSN   NSSSN ",
                    "$#8  H   H        NNN     NNN  ",
                    "$#   H   H         M       M   ",
                    "$####H   H        OOO     OOO  ",
                    "     H   H       O   O   O   O ",
                    "   FGHHHHHGF    MO Q OM MO Q OM",
                    "     H   H       O   O   O   O ",
                    "     H   H    L   OOO     OOO  ",
                    "     H   H         M       M   ",
                    "     H   H                     ",
                    "   FGHHHHHGF           G       ",
                    "     H   H                     ",
                    "     H   H                     ",
                    "     H   H                     ",
                    "     H   H                     ",
                    "   FGHHHHHGF           G       ",
                    "     H   H                     ",
                    "     H   H                     ",
                    "     H   H                     ",
                    "      HHH                      ",
                    "                               ",
            },
            {
                    "44AAHAAAAAHAAAAAAAAAAAAAAAAAAA ",
                    "444AHHHHHHHAAAAAA AAA AAA AAA  ",
                    "444 H     H        M       M   ",
                    "    H     H       NNN     NNN  ",
                    "    H     H      NSSSN   NSSSN ",
                    " 8  H     H     MNSQSNM MNSQSNM",
                    " 8 GHHJJJHHG     NSSSN   NSSSN ",
                    " 8  H     H       NNN     NNN  ",
                    "    H     H        M       M   ",
                    "    H     H       OOO     OOO  ",
                    "    H     H      O   O   O   O ",
                    "   GHHJJJHHG    MO Q OM MO Q OM",
                    "    H     H      O   O   O   O ",
                    "    H     H   L   OOO     OOO  ",
                    "    H     H        M       M   ",
                    "    H     H                    ",
                    "   GHHJJJHHG           G       ",
                    "    H     H                    ",
                    "    H     H                    ",
                    "    H     H                    ",
                    "    H     H                    ",
                    "   GHHJJJHHG           G       ",
                    "    H     H                    ",
                    "    H     H                    ",
                    "    H     H                    ",
                    "     H   H                     ",
                    "      HHH                      ",
            },
            {
                    "44AAHAAAAAHAAAAAAAAAAAAAAAAAAA ",
                    "444AHHHHHHHAAAAAARAAARAAARAAAR ",
                    "444 H  J  H      RRRRR   RRRRR ",
                    "    H  J  H      RNNNR   RNNNR ",
                    "    H  J  H     RNSSSNR RNSSSNR",
                    "    H  J  H     RNSQSNR RNSQSNR",
                    "   GHHJJJHHG    RNSSSNR RNSSSNR",
                    "    H  J  H      RNNNR   RNNNR ",
                    "    H  J  H      RRRRR   RRRRR ",
                    "    H  J  H      ROOOR   ROOOR ",
                    "    H  J  H     RO   OR RO   OR",
                    "   GHHJJJHHG    RO Q OR RO Q OR",
                    "    H  J  H     RO   OR RO   OR",
                    "    H  J  H   L  ROOOR   ROOOR ",
                    "    H  J  H       RRR     RRR  ",
                    "    H  J  H                    ",
                    "   GHHJJJHHG           G       ",
                    "    H  J  H                    ",
                    "    H  J  H                    ",
                    "    H  J  H                    ",
                    "    H  J  H                    ",
                    "   GHHJJJHHG           G       ",
                    "    H  J  H                    ",
                    "    H  J  H                    ",
                    "    H  J  H                    ",
                    "     H   H                     ",
                    "      HHH                      ",
            },
            {
                    "44AAHAAAAAHAAAAAAAAAAAAAAAAAAA ",
                    "444AHHHHHHHAAAAAA AAA AAA AAA  ",
                    "444 H     H        M       M   ",
                    "    H     H       NNN     NNN  ",
                    "    H     H      NSSSN   NSSSN ",
                    "    H     H     MNSQSNM MNSQSNM",
                    "   GHHJJJHHG     NSSSN   NSSSN ",
                    "    H     H       NNN     NNN  ",
                    "    H     H        M       M   ",
                    "    H     H       OOO     OOO  ",
                    "    H     H      O   O   O   O ",
                    "   GHHJJJHHG    MO Q OM MO Q OM",
                    "    H     H      O   O   O   O ",
                    "    H     H   L   OOO     OOO  ",
                    "    H     H        M       M   ",
                    " 8  H     H                    ",
                    " 8 GHHJJJHHG           G       ",
                    " 8  H     H                    ",
                    "    H     H                    ",
                    "    H     H                    ",
                    "    H     H                    ",
                    "   GHHJJJHHG           G       ",
                    "    H     H                    ",
                    "    H     H                    ",
                    "    H     H                    ",
                    "     H   H                     ",
                    "      HHH                      ",
            },
            {
                    "44AAAHAAAHAAAAAAAAAAAAAAAAAAAA ",
                    "4#4AAHHHHHAA      AAA AAA AAA  ",
                    "4#4  H   H         M       M   ",
                    "$#   H   H        NNN     NNN  ",
                    "$#   H   H       NSSSN   NSSSN ",
                    "$#   H   H      MNSQSNM MNSQSNM",
                    "$# FGHHHHHGF     NSSSN   NSSSN ",
                    "$#   H   H        NNN     NNN  ",
                    "$#   H   H         M       M   ",
                    "$#   H   H        OOO     OOO  ",
                    "$#   H   H       O   O   O   O ",
                    "$# FGHHHHHGF    MO Q OM MO Q OM",
                    "$#   H   H       O   O   O   O ",
                    "$#   H   H    L   OOO     OOO  ",
                    "$#   H   H         M       M   ",
                    "$#8  H   H                     ",
                    "$#8FGHHHHHGF           G       ",
                    "$#8  H   H                     ",
                    "$#   H   H                     ",
                    "$####H   H                     ",
                    "     H   H                     ",
                    "   FGHHHHHGF           G       ",
                    "     H   H                     ",
                    "     H   H                     ",
                    "     H   H                     ",
                    "      HHH                      ",
                    "                               ",
            },
            {
                    "44AAAAHHHAAAA    AAAAAAAAAAAAA ",
                    "444AAAHHHAAA      AAA AAA AAA  ",
                    "444 I HHH I        M       M   ",
                    "    I HHH I       NNN     NNN  ",
                    "    I HHH I      NSSSN   NSSSN ",
                    "    I HHH I     MNSQSNM MNSQSNM",
                    "    IGHHHGI      NSSSN   NSSSN ",
                    "    I HHH I       NNN     NNN  ",
                    "    I HHH I        M       M   ",
                    "    I HHH I       OOO     OOO  ",
                    "    I HHH I      O   O   O   O ",
                    "    IGHHHGI     MO Q OM MO Q OM",
                    "    I HHH I      O   O   O   O ",
                    "    I HHH I   L   OOO     OOO  ",
                    "    I HHH I        M       M   ",
                    " 8  I HHH I                    ",
                    " 8  IGHHHGI            G       ",
                    " 8  I HHH I                    ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    IGHHHGI            G       ",
                    "    I HHH I                    ",
                    "    I HHH I                    ",
                    "    D HHH D                    ",
                    "                               ",
                    "                               ",
            },
            {
                    "44AAAAAAAAAAA    AAAAAAAAAAAAA ",
                    "444 AAAAAAA       AAA AAA AAA  ",
                    "444                M       M   ",
                    "                  NNN     NNN  ",
                    "                 NSSSN   NSSSN ",
                    "                MNSQSNM MNSQSNM",
                    "     FGGGF       NSSSN   NSSSN ",
                    "                  NNN     NNN  ",
                    "                   M       M   ",
                    "                  OOO     OOO  ",
                    "                 O   O   O   O ",
                    "     FGGGF      MO Q OM MO Q OM",
                    "                 O   O   O   O ",
                    "              L   OOO     OOO  ",
                    "                   M       M   ",
                    "                               ",
                    "     FGGGF             G       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "     FGGGF             G       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    "444AAAAAAAAA     AAAAAAAAAAAAA ",
                    "444   AAA         AAA AAA AAA  ",
                    "444    F           M       M   ",
                    "       F          NNN     NNN  ",
                    "       F         NSSSN   NSSSN ",
                    "       F        MNSQSNM MNSQSNM",
                    "      FGF        NSSSN   NSSSN ",
                    "       G          NNN     NNN  ",
                    "       G           M       M   ",
                    "       G          OOO     OOO  ",
                    "       G         O   O   O   O ",
                    "      FGF       MO Q OM MO Q OM",
                    "       G         O   O   O   O ",
                    "       G      L   OOO     OOO  ",
                    "       G           M       M   ",
                    "       G                       ",
                    "      FGF              G       ",
                    "       G                       ",
                    "       G                       ",
                    "       G                       ",
                    "       G                       ",
                    "      FGF              G       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    "     AAAAA   F F AAAAAAAAAAAAA ",
                    "      AAA    F F RAAARAAARAAAR ",
                    "             F F RRRRR   RRRRR ",
                    "             F F RNNNR   RNNNR ",
                    "             F FRNSSSNR RNSSSNR",
                    "             F FRNSQSNR RNSQSNR",
                    "       G     F FRNSSSNR RNSSSNR",
                    "             F F RNNNR   RNNNR ",
                    "             F F RRRRR   RRRRR ",
                    "             F F ROOOR   ROOOR ",
                    "             F FRO   OR RO   OR",
                    "             F FRO Q OR RO Q OR",
                    "             F FRO   OR RO   OR",
                    "             FLF ROOOR   ROOOR ",
                    "             FFF  RRR     RRR  ",
                    "                               ",
                    "                       G       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                       G       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    "     AAAAA       AAAAAAAAAAAAA ",
                    "      AAA         AAA AAA AAA  ",
                    "                   M       M   ",
                    "                  NNN     NNN  ",
                    "                 NNNNN   NNNNN ",
                    "                MNNQNNM MNNQNNM",
                    "       G         NNNNN   NNNNN ",
                    "                  NNN     NNN  ",
                    "                   M       M   ",
                    "              L   OOO     OOO  ",
                    "             FLF OOOOO   OOOOO ",
                    "              L MOOQOOM MOOQOOM",
                    "              L  OOOOO   OOOOO ",
                    "             FLF  OOO     OOO  ",
                    "                  DMD     DMD  ",
                    "                               ",
                    "                       G       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                       G       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    " AAAAAAAAAAAAA6 6AAAAAAAAAAAAA ",
                    "      AAA     6 6 AAA6AAA AAA  ",
                    "               6               ",
                    "                   M       M   ",
                    "                  NNN     NNN  ",
                    "                 MNPNM   MNPNM ",
                    "       G          NNN     NNN  ",
                    "                   M       M   ",
                    "                               ",
                    "              L    M       M   ",
                    "             FFF  OOO     OOO  ",
                    "                 MOTOM   MOTOM ",
                    "                  OOO     OOO  ",
                    "             FFF   M       M   ",
                    "                               ",
                    "                               ",
                    "                       G       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                       G       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    " AAAAAAAAAAAAA6 6    AAAAA     ",
                    "  AAAAAAAAAAA  7777777AAA      ",
                    "  M   MGM   M 676    6         ",
                    "  M   MGM   M  7               ",
                    "  M   MGM   M  7               ",
                    "  M   MGM   M  7               ",
                    "  M   MGM   M  7               ",
                    "  M   M M   M                  ",
                    "  MMMMMMMMMMM                  ",
                    "              L                ",
                    "                               ",
                    "                   TTTTTTTTT   ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                       G       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                       G       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    " AAAAAAAAAAAAA6 6    AAAAA     ",
                    "  AUUUUUUUUUA 6 6    6AAA      ",
                    "   UUUUGUUUU   6      A A      ",
                    "   UUUUUUUUU          A A      ",
                    "   UUUUUUUUU       AAAAAAAAA   ",
                    "   UUUUUUUUU          ATA      ",
                    "   UUUUGUUUU   7      ATA      ",
                    "   UUUUUUUUU       AAAATAAAA   ",
                    "  M   M M   M     8888ATA8888  ",
                    "              L       ATA      ",
                    "                      ATA      ",
                    "                       T       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                       G       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                       G       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    " AAAAAAAAAAAAA   AAAAAAAAAAAAA ",
                    "  AUUUUUUUUUA     AAAAAAAAAAA  ",
                    "   WX  Q  XW       AVVVVVVVA   ",
                    "   U   S   U       AVVVVVVVA   ",
                    "   U       U       AVVVVVVVA   ",
                    "   U   S   U       AVVVJVVVA   ",
                    "   WX  Q  XW   7   AVVVJVVVA   ",
                    "   UYUUUUUYU       AVVVVVVVA   ",
                    "  M   M M   M     8AVVVVVVVA8  ",
                    "              L    AVVVVVVVA   ",
                    "                   AAAAAAAAA   ",
                    "                      F F      ",
                    "                      F F      ",
                    "                      F F      ",
                    "                      F F      ",
                    "                      FFF      ",
                    "                      FGF      ",
                    "                      FFF      ",
                    "                      F F      ",
                    "                      F F      ",
                    "                      FFF      ",
                    "                      FGF      ",
                    "                      FFF      ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    " AAAAAAAAAAAAA   AAAAAAAAAAAAA ",
                    "  AUUUUUUUUUA     AZZZZZZZZZA  ",
                    "   WX  Q  XW       FVV[V[VVF   ",
                    "   U   S   U       FVK[[[KVF   ",
                    "   U       U       F[[ Q [[F   ",
                    "   U   S   U       FV[QJQ[VF   ",
                    "   WX  Q  XW   7   FV[QJQ[VF   ",
                    "   UYUUUUUYU       F[[ Q [[F   ",
                    "  M   M M   M     8FVK[Q[KVF8  ",
                    "              L    FVV[G[VVF   ",
                    "                   FFFFGFFFF   ",
                    "                   8888G8888   ",
                    "                   8888G8888   ",
                    "                   9999G9999   ",
                    "                       G       ",
                    "                       G       ",
                    "                       G       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                       G       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    " AAAAAAAAAAAAA444AAAAAAAAAAAAA ",
                    "  AUUUUUUUUUA45554AZZZZZZZZZA  ",
                    "   WX  Q  XW 455548ZVV[V[VVZ8  ",
                    "   U   S   U  555 8ZVK   KVZ8  ",
                    "   U       U  555 8Z[  Q  [Z8  ",
                    "   U   S   U  555 8ZV QJQ VZ8  ",
                    "   WX  Q  XW   7  8ZV QJQ VZ8  ",
                    "   UUUUUUUUU      8Z[  Q  [Z8  ",
                    "  M   M M   M     8ZVK [ KVZ8  ",
                    "              L   8ZVV[V[VVZ8  ",
                    "                  8ZZZZZZZZZ8  ",
                    "                  88998 89988  ",
                    "                   8998 8998   ",
                    "                   9!!9 9!!9   ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                       G       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    " AAAAAAAAAAAAA444AAAAAAAAAAAAA ",
                    "  AUUUUUUUUUA 555 AZZZZZZZZZA  ",
                    "  MUX  Q  XUM 5 5  FVV[V[VVF   ",
                    "  MU   S   UM 5 5  FVK   KVF   ",
                    "  MU       UM 5 5  F[  Q  [F   ",
                    "  MU   S   UM 575  FV QJQ VF   ",
                    "  MUX  Q  XUM 676  FV QJQ VF   ",
                    "  MUUUUUUUUUM  6   F[  Q  [F   ",
                    "  MMMMM MMMMM     8FVK   KVF8  ",
                    "            M L    FVV[V[VVF   ",
                    "            M      FFFFFFFFF   ",
                    "                   8998G8998   ",
                    "                   8998G8998   ",
                    "                   9!!9G9!!9   ",
                    "                       G       ",
                    "                       G       ",
                    "                       G       ",
                    "                       G       ",
                    "                       G       ",
                    "                       G       ",
                    "                       G       ",
                    "                       G       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    " AAAAAAAAAAAAA444AAAAAAAAAAAAA ",
                    "  AUUUUUUUUUA45554AZZZZZZZZZA  ",
                    "   UX  Q  XU 455548ZVV[V[VVZ8  ",
                    "   U   S   U  555 8ZVK   KVZ8  ",
                    "   U       U  555 8Z[  Q  [Z8  ",
                    "   U   S   U  555 8ZV QJQ VZ8  ",
                    "   UX  Q  XU   7  8ZV QJQ VZ8  ",
                    "   UUUULUUUU      8Z[  Q  [Z8  ",
                    "  M   MLM   M     8ZVK   KVZ8  ",
                    "       LLLLLLLL   8ZVV[V[VVZ8  ",
                    "            M     8ZZZZZZZZZ8  ",
                    "                  88998G89988  ",
                    "                   8998 8998   ",
                    "                   9!!9 9!!9   ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    " AAAAAAAAAAAAA   AAAAAAAAAAAAA ",
                    "  AUUUUUUUUUA     AZZZZZZZZZA  ",
                    "   UX  Q  XU       FVV[V[VVF   ",
                    "   U   S   U       FVK   KVF   ",
                    "   U       U       F[  Q  [F   ",
                    "   U   S   U       FV QJQ VF   ",
                    "   UX  Q  XU   7   FV QJQ VF   ",
                    "   UUUUUUUUU       F[  Q  [F   ",
                    "  M   M M   M     8FVK   KVF8  ",
                    "            M L    FVV[V[VVF   ",
                    "            M      FFFFFFFFF   ",
                    "                   8998G8998   ",
                    "                   8998 8998   ",
                    "                   9!!9 9!!9   ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    " AAAAAAAAAAAAA   AAAAAAAAAAAAA ",
                    "  AUUUUUUUUUA     AZZZZZZZZZA  ",
                    "  MUX  Q  XUM     8ZVV[V[VVZ8  ",
                    "  MU   S   UM     8ZVK   KVZ8  ",
                    "  MU       UM     8Z[  Q  [Z8  ",
                    "  MU   S   UM     8ZV QJQ VZ8  ",
                    "  MUX  Q  XUM  7  8ZV QJQ VZ8  ",
                    "  MUUUUUUUUUM     8Z[  Q  [Z8  ",
                    "  MMMMM MMMMM     8ZVK   KVZ8  ",
                    "              L   8ZVV[V[VVZ8  ",
                    "                  8ZZZZZZZZZ8  ",
                    "                  88998G89988  ",
                    "                   8998 8998   ",
                    "                   9!!9 9!!9   ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    " AAAAAAAAAAAAA444AAAAAAAAAAAAA ",
                    "  AUUUUUUUUUA45554AZZZZZZZZZA  ",
                    "   UX  Q  XU 45554 FVV[V[VVF   ",
                    "   U   S   U  555  FVK   KVF   ",
                    "   U       U  555  F[  Q  [F   ",
                    "   U   S   U  555  FV QJQ VF   ",
                    "   UX  Q  XU   7   FV QJQ VF   ",
                    "   UUUUUUUUU       F[  Q  [F   ",
                    "  M   M M   M     8FVK   KVF8  ",
                    "            M L    FVV[V[VVF   ",
                    "            M      FFFFFFFFF   ",
                    "                   8998G8998   ",
                    "                   8998 8998   ",
                    "                   9!!9 9!!9   ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    " AAAAAAAAAAAAA444AAAAAAAAAAAAA ",
                    "  AUUUUUUUUUA 555 AZZZZZZZZZA  ",
                    "   UX  Q  XU  5 5 8ZVV[V[VVZ8  ",
                    "   U   S   U  5 5 8ZVK   KVZ8  ",
                    "   U       U  5 5 8Z[  Q  [Z8  ",
                    "   U   S   U  575 8ZV QJQ VZ8  ",
                    "   UX  Q  XU  676 8ZV QJQ VZ8  ",
                    "   UUUULUUUU   6  8Z[  Q  [Z8  ",
                    "  M   MLM   M     8ZVK [ KVZ8  ",
                    "       LLLLLLLL   8ZVV[V[VVZ8  ",
                    "            M     8ZZZZZZZZZ8  ",
                    "                  88888G88888  ",
                    "                   8888 8888   ",
                    "                   9999 9999   ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    " AAAAAAAAAAAAA444AAAAAAAAAAAAA ",
                    "  AUUUUUUUUUA45554AZZZZZZZZZA  ",
                    "   UX  Q  XU 45554 FVV[V[VVF   ",
                    "   U   S   U  555  FVK[[[KVF   ",
                    "   U       U  555  F[[ Q [[F   ",
                    "   U   S   U  555  FV[QJQ[VF   ",
                    "   UX  Q  XU   7   FV[QJQ[VF   ",
                    "   UUUUUUUUU       F[[ Q [[F   ",
                    "  M   M M   M     8FVK[Q[KVF8  ",
                    "            M L    FVV[G[VVF   ",
                    "            M      FFFFGFFFF   ",
                    "                      FGF      ",
                    "                      FFF      ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    " AAAAAAAAAAAAA   AAAAAAAAAAAAA ",
                    "  AUUUUUUUUUA     AAAAAAAAAAA  ",
                    "  MUX  Q  XUM      AVVVVVVVA   ",
                    "  MU   S   UM      AVVVVVVVA   ",
                    "  MU       UM      AVVVVVVVA   ",
                    "  MU   S   UM      AVVVJVVVA   ",
                    "  MUX  Q  XUM  7   AVVVJVVVA   ",
                    "  MUUUUUUUUUM      AVVVVVVVA   ",
                    "  MMMMM MMMMM     8AVVVVVVVA8  ",
                    "              L    AVVVVVVVA   ",
                    "                   AAAAAAAAA   ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    " AAAAAAAAAAAAA       AAAAA     ",
                    "  AUUUUUUUUUA         AAA      ",
                    "   UX  Q  XU          A A      ",
                    "   U   S   U          A A      ",
                    "   U       U       AAAAAAAAA   ",
                    "   U   S   U          ATA      ",
                    "   UX  Q  XU   7      ATA      ",
                    "   UUUUUUUUU       AAAATAAAA   ",
                    "  M   M M   M     8888ATA8888  ",
                    "            M L       ATA      ",
                    "            M         ATA      ",
                    "                       T       ",
                    "                       T       ",
                    "                       T       ",
                    "                       T       ",
                    "                       T       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    " AAAAAAAAAAAAA4444 AAAAAAAAA   ",
                    "  AUUUUUUUUUA45554    AAA      ",
                    "   UX  Q  XU 45554             ",
                    "   U   S   U  555              ",
                    "   U       U  555              ",
                    "   U   S   U  555              ",
                    "   UX  Q  XU   7               ",
                    "   UUUULUUUU                   ",
                    "  M   MLM   M                  ",
                    "       LLLLLLLL                ",
                    "            M                  ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                       T       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    " AAAAAAAAAAAAA444KAAAAAAAAAAAK ",
                    "  AUUUUUUUUUA 555K  AAAAAAA  K ",
                    "  MUX  Q  XUM 5 5K  OKKOKKO  K ",
                    "  MU   S   UM 5 5K  O  O  O  K ",
                    "  MU       UM 5 5K  O  O  O  K ",
                    "  MU   S   UM 575K  O  O  O  K ",
                    "  MUX  Q  XUM 676K  O  O  O  K ",
                    "  MUUUUUUUUUM  6 K  O  O  O  K ",
                    "  MMMMM MMMMM    K  O  O  O  K ",
                    "            M    K  O  O  O  K ",
                    "            M    K  O  O  O  K ",
                    "                 K  O  O  O  K ",
                    "                 OOOOOOOOOOOOO ",
                    "                      FOF      ",
                    "                      FFF      ",
                    "                      FTF      ",
                    "                      FFF      ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    " AAAAAAAAAAAAA444AAAAAAAAAAAAA ",
                    "  AUUUUUUUUUA45554AAAAAAAAAAA  ",
                    "   WX  Q  XW 45554 \\\\\\\\\\\\\\\\\\   ",
                    "   U   S   U  555  \\\\\\\\\\\\\\\\\\   ",
                    "   U       U  555  \\\\]]\\]]\\\\   ",
                    "   U   S   U  555  \\\\]]\\]]\\\\   ",
                    "   WX  Q  XW   6   \\\\]]\\]]\\\\   ",
                    "   UUUUUUUUU       \\\\]]\\]]\\\\   ",
                    "  M   M M   M      \\\\]]\\]]\\\\   ",
                    "                   \\\\]]\\]]\\\\   ",
                    "                   \\\\]]\\]]\\\\   ",
                    "                   \\\\\\\\\\\\\\\\\\   ",
                    "                 O \\\\\\\\\\\\\\\\\\ O ",
                    "                    O  O  O    ",
                    "                       O       ",
                    "                       T       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    " AAAAAAAAAAAAA  AAAAAAAAAAAAAAA",
                    "  AUUUUUUUUUA     AA\\\\\\\\\\\\\\AA  ",
                    "   WX  Q  XW      \\\\ [X[X[\\\\\\  ",
                    "   U   S   U      \\\\ [X[X[\\\\\\  ",
                    "   U       U      \\\\ [X[X[\\\\\\  ",
                    "   U   S   U      \\\\ [X[X[\\\\\\  ",
                    "   WX  Q  XW      \\\\ [X[X[\\\\\\  ",
                    "   UYUUUUUYU      \\\\ [X[X[\\\\\\  ",
                    "  M   M M   M     \\\\ [X[X[\\\\\\  ",
                    "                  \\\\ [X[X[\\\\\\  ",
                    "                  \\\\ [X[X[\\\\\\  ",
                    "                  \\\\ [X[X[\\\\\\  ",
                    "                 O\\\\ [X[X[\\\\\\O ",
                    "                   \\\\\\\\\\\\\\\\\\   ",
                    "                   DO  O  OD   ",
                    "                       T       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    " AAAAAAAAAAAAA  AAAAAAAAAAAAAAA",
                    "  AUUUUUUUUUA    AA\\\\\\\\\\\\\\\\\\AA ",
                    "   WX  Q  XW     O\\ J[ [ [J \\O ",
                    "   U   S   U     O\\ J     J \\O ",
                    "   U       U     O\\ J     J \\O ",
                    "   U   S   U     O\\ J     J \\O ",
                    "   WX  Q  XW     O\\ J     J \\O ",
                    "   UYUUUUUYU     O\\ J     J \\O ",
                    "  M   M M   M    O\\ J     J \\O ",
                    "                 O\\ J     J \\O ",
                    "                 O\\ J     J \\O ",
                    "                 O\\ J     J \\O ",
                    "                 O\\ J[ [ [J \\O ",
                    "                  O\\\\\\\\\\\\\\\\\\O  ",
                    "                   OOOOOOOOO   ",
                    "                       T       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    " AAAAAAAAAAAAA  AAAAAAAAAAAAAAA",
                    "  AUUUUUUUUUA    AA\\\\\\\\\\\\\\\\\\AA ",
                    "   UUUUTUUUU     K\\[[[[[[[[[\\K ",
                    "   UUUUUUUUU      \\[       [\\  ",
                    "   UUUUUUUUU      ][       []  ",
                    "   UUUUUUUUU      ][       []  ",
                    "   UUUUTUUUU      ][       []  ",
                    "   UUUUUUUUU      ][       []  ",
                    "  M   M M   M     ][       []  ",
                    "                  ][       []  ",
                    "                  ][       []  ",
                    "                  \\[       [\\  ",
                    "                 O\\[[[[[[[[[\\O ",
                    "                   \\\\\\\\\\\\\\\\\\   ",
                    "                    O  O  O    ",
                    "                       T       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    " AAAAAAAAAAAAA  AAAAAAAAAAAAAAA",
                    "  AAAAATAAAAA    AA\\\\\\\\\\\\\\\\\\AA ",
                    "  M   MTM   M    K\\X [J J[ X\\K ",
                    "  M   MTM   M     \\X  J J  X\\  ",
                    "  M   MTM   M     ]X  J J  X]  ",
                    "  M   MTM   M     ]X  J J  X]  ",
                    "  M   MTM   M     ]X  J J  X]  ",
                    "  M   M M   M     ]X  J J  X]  ",
                    "  MMMMMMMMMMM     ]X  J J  X]  ",
                    "                  ]X  J J  X]  ",
                    "                  ]X  J J  X]  ",
                    "                  \\X  J J  X\\  ",
                    "                 O\\X [J J[ X\\O ",
                    "                   \\\\\\\\\\\\\\\\\\   ",
                    "                    O KKK O    ",
                    "                       T       ",
                    "                       K       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    " AAAAAAAAAAAAA  AAAAAAAAAAAAAAA",
                    "      ATTTTTTTTTTTTTTTTT\\\\\\\\AA ",
                    "                 O\\[[[ T [ [\\O ",
                    "                 O\\[   T   [\\O ",
                    "                 O\\[   T   [\\O ",
                    "                 O\\[   T   [\\O ",
                    "                 O\\[   T   [\\O ",
                    "                 O\\[   T   [\\O ",
                    "                 O\\[   T   [\\O ",
                    "                 O\\[   T   [\\O ",
                    "                 O\\[   T   [\\O ",
                    "                 O\\[   T   [\\O ",
                    "                 O\\[[[ T [[[\\O ",
                    "                 OO\\\\\\\\T\\\\\\\\OO ",
                    "                  OOOOKTKOOOO  ",
                    "                      KTK      ",
                    "                      KKK      ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    "     AAAAA      AAAAAAAAAAAAAAA",
                    "      AAA        AA\\\\\\\\\\\\\\\\\\AA ",
                    "                 K\\X [J J[ X\\K ",
                    "                  \\X  J J  X\\  ",
                    "                  ]X  J J  X]  ",
                    "                  ]X  J J  X]  ",
                    "                  ]X  J J  X]  ",
                    "                  ]X  J J  X]  ",
                    "                  ]X  J J  X]  ",
                    "                  ]X  J J  X]  ",
                    "                  ]X  J J  X]  ",
                    "                  \\X  J J  X\\  ",
                    "                 O\\X [J J[ X\\O ",
                    "                   \\\\\\\\\\\\\\\\\\   ",
                    "                    O KKK O    ",
                    "                       K       ",
                    "                       K       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    "     AAAAA      AAAAAAAAAAAAAAA",
                    "      AAA        AA\\\\\\\\\\\\\\\\\\AA ",
                    "                 K\\[[[[[[[[[\\K ",
                    "                  \\[       [\\  ",
                    "                  ][       []  ",
                    "                  ][       []  ",
                    "                  ][       []  ",
                    "                  ][       []  ",
                    "                  ][       []  ",
                    "                  ][       []  ",
                    "                  ][       []  ",
                    "                  \\[       [\\  ",
                    "                 O\\[[[[[[[[[\\O ",
                    "                   \\\\\\\\\\\\\\\\\\   ",
                    "                    O  O  O    ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    "000AAAAAAAAA0000AAAAAAAAAAAAAAA",
                    "010 A AAA A 0110 AA\\\\\\\\\\\\\\\\\\AA ",
                    "111 A     A 1111 O\\ J[ [ [J \\O ",
                    "010 A     A 0110 O\\ J     J \\O ",
                    "000 AAAAAAA 0000 O\\ J     J \\O ",
                    "                 O\\ J     J \\O ",
                    "                 O\\ J     J \\O ",
                    "                 O\\ J     J \\O ",
                    "                 O\\ J     J \\O ",
                    "                 O\\ J     J \\O ",
                    "                 O\\ J     J \\O ",
                    "                 O\\ J     J \\O ",
                    "                 O\\ J[ [ [J \\O ",
                    "                  O\\\\\\\\\\\\\\\\\\O  ",
                    "                   OOOOOOOOO   ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    "000AAAAAAAAA0000AAAAAAAAAAAAAAA",
                    "111AZ^PPP^ZA1111  AA\\\\\\\\\\\\\\AA  ",
                    "010AZ^^^^^ZA0110  \\\\ [X[X[\\\\\\  ",
                    "111AZ^^^^^ZA1111  \\\\ [X[X[\\\\\\  ",
                    "030A_XX_XX_A0330  \\\\ [X[X[\\\\\\  ",
                    "                  \\\\ [X[X[\\\\\\  ",
                    "                  \\\\ [X[X[\\\\\\  ",
                    "                  \\\\ [X[X[\\\\\\  ",
                    "                  \\\\ [X[X[\\\\\\  ",
                    "                  \\\\ [X[X[\\\\\\  ",
                    "                  \\\\ [X[X[\\\\\\  ",
                    "                  \\\\ [X[X[\\\\\\  ",
                    "                 O\\\\ [X[X[\\\\\\O ",
                    "                   \\\\\\\\\\\\\\\\\\   ",
                    "                   DO  O  OD   ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    "000AAAAAAAAA0000 AAAAAAAAAAAAA ",
                    "111 ZPPPPPZ 1111  AAAAAAAAAAA  ",
                    "010 ZPPPPPZ 0110   \\\\\\\\\\\\\\\\\\   ",
                    "111 ZPPPPPZ 1111   \\\\\\\\\\\\\\\\\\   ",
                    "030A_XX_XX_A0330   \\\\]]\\]]\\\\   ",
                    "                   \\\\]]\\]]\\\\   ",
                    "                   \\\\]]\\]]\\\\   ",
                    "                   \\\\]]\\]]\\\\   ",
                    "                   \\\\]]\\]]\\\\   ",
                    "                   \\\\]]\\]]\\\\   ",
                    "                   \\\\]]\\]]\\\\   ",
                    "                   \\\\\\\\\\\\\\\\\\   ",
                    "                 O \\\\\\\\\\\\\\\\\\ O ",
                    "                    O  O  O    ",
                    "                       O       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    "000AAAAAAAAA0000 KAAAAAAAAAAAK ",
                    "111AZ^^^^^ZA1111 K  AAAAAAA  K ",
                    "010AZ^^`^^ZA0110 K  OKKOKKO  K ",
                    "111AZ^^^^^ZA1111 K  O  O  O  K ",
                    "030A_XX_XX_A0330 K  O  O  O  K ",
                    "                 K  O  O  O  K ",
                    "                 K  O  O  O  K ",
                    "                 K  O  O  O  K ",
                    "                 K  O  O  O  K ",
                    "                 K  O  O  O  K ",
                    "                 K  O  O  O  K ",
                    "                 K  O  O  O  K ",
                    "                 OOOOOOOOOOOOO ",
                    "                       O       ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
            {
                    "000AAAAAAAAA0000   AAAAAAAAA   ",
                    "010 A     A 0110               ",
                    "111 A     A 1111               ",
                    "010 A     A 0110               ",
                    "000 AAAAAAA 0000               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
                    "                               ",
            },
    };

    public static String[][] upgradedSlices() { return UPGRADED_SLICES; }

    public static BlockPattern buildUpgraded(MultiblockMachineDefinition definition) {
        verifySymbols(UPGRADED_SLICES, "升级", UPGRADED_WIDTH, UPGRADED_LENGTH, UPGRADED_EXTRA_SYMBOLS);
        // ── 附属模块口：线程仓 / 超频仓 / 加速仓 / 并行仓（GTO 模块 'd'/'c' 位的语义）──
        TraceabilityPredicate moduleHatches =
                ability(com.gtlmodular.common.data.thread.ThreadHatchMachines.THREAD_HATCH)
                        .setMaxGlobalLimited(1)
                        .or(com.gtlmodular.common.data.fuel.OverclockHatches.predicate())
                        .or(com.gtlmodular.common.data.fuel.AccelerateHatches.predicate())
                        .or(ability(PartAbility.PARALLEL_HATCH).setMaxGlobalLimited(1).setPreviewCount(1));

        // ── 主结构正面舱面（GTO 的 autoLaserAbilities + MAINTENANCE + 附属模块口）──
        TraceabilityPredicate hatchFace = blocksOf("gtceu", "high_power_casing")
                .or(ability(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                .or(ability(PartAbility.EXPORT_ITEMS).setPreviewCount(1))
                .or(ability(PartAbility.IMPORT_FLUIDS).setPreviewCount(1))
                .or(ability(PartAbility.EXPORT_FLUIDS).setPreviewCount(1))
                .or(ability(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(8, 0))
                .or(ability(PartAbility.INPUT_LASER).setMaxGlobalLimited(4, 1))
                .or(ability(PartAbility.MAINTENANCE).setExactLimit(1))
                .or(moduleHatches);
        FactoryBlockPattern pattern = FactoryBlockPattern.start();
        for (String[] slice : UPGRADED_SLICES) {
            pattern.aisle(slice);
        }
        return pattern
                .where(' ', Predicates.any())
                .where('`', Predicates.controller(Predicates.blocks(definition.get())))
                .where('A', blocksOf("gtceu", "stress_proof_casing"))
                .where('C', blocksOf("gtceu", "robust_machine_casing"))
                .where('D', lights())
                .where('E', blocksOf("gtceu", "watertight_casing"))
                .where('F', frame("steel"))
                .where('G', Predicates.blocks(WoodFactoryBlocks.HIGH_PRESSURE_PIPE_CASING.get()))
                .where('H', blocksOf("gtceu", "inert_machine_casing"))
                .where('I', frame("stainless_steel"))
                .where('J', blocksOf("gtceu", "titanium_pipe_casing"))
                .where('K', frame("hslasteel"))
                .where('L', Predicates.blocks(WoodFactoryBlocks.CHEMICAL_CORROSION_RESISTANT_PIPE_CASING.get()))
                .where('M', frame("black_steel"))
                .where('N', Predicates.blocks(PetrochemBlocks.PPS_CORROSION_RESISTANT_MECHANICAL_HOUSING.get()))
                .where('O', blocksOf("gtlcore", "iridium_casing"))
                .where('P', blocksOf("gtceu", "high_power_casing"))
                .where('Q', blocksOf("gtceu", "tungstensteel_pipe_casing"))
                .where('R', Predicates.blocks(PetrochemBlocks.HIGH_STRENGTH_SUPPORT_MECHANICAL_CASING.get()))
                .where('S', frame("watertight_steel"))
                .where('T', Predicates.blocks(WoodFactoryBlocks.OIL_GAS_TRANSPORTATION_PIPE_CASING.get()))
                .where('U', blocksOf("gtceu", "clean_machine_casing"))
                .where('V', Predicates.blocks(PetrochemBlocks.CALCIUM_OXIDE_CERAMIC_CASING.get()))
                .where('W', Predicates.blocks(PetrochemBlocks.INDUSTRIAL_FRAMELESS_GLASS.get()))
                .where('X', blocksOf("gtceu", "electrolytic_cell"))
                .where('Y', Predicates.abilities(PartAbility.MUFFLER))
                .where('Z', blocksOf("gtlcore", "oxidation_resistant_hastelloy_n_mechanical_casing"))
                .where('[', Predicates.heatingCoils())
                .where('\\', Predicates.blocks(WoodFactoryBlocks.PRESSURE_CONTAINMENT_CASING.get()))
                .where(']', blocksOf("gtceu", "fusion_glass"))
                .where('^', hatchFace)
                .where('_', blocksOf("gtceu", "tempered_glass"))
                
                .where('!', blocksOf("gtceu", "hss_steel_frame"))
                .where('#', blocksOf("gtlmodular", "chemical_corrosion_resistant_pipe_casing"))
                .where('$', blocksOf("gtceu", "black_steel_frame"))
                .where('0', blocksOf("gtceu", "stress_proof_casing"))
                .where('1', blocksOf("gtlcore", "oxidation_resistant_hastelloy_n_mechanical_casing"))
                .where('3', blocksOf("minecraft", "sea_lantern"))
                .where('4', blocksOf("gtceu", "watertight_casing"))
                .where('5', blocksOf("gtceu", "steel_frame"))
                .where('6', blocksOf("gtlmodular", "high_pressure_pipe_casing"))
                .where('7', blocksOf("gtceu", "inert_machine_casing"))
                .where('8', blocksOf("gtceu", "stainless_steel_frame"))
                .where('9', blocksOf("gtceu", "titanium_pipe_casing"))
                .build();
    }
}
