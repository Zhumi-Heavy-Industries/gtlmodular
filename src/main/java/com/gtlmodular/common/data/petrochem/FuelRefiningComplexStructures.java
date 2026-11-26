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
    private static final String DECLARED_SYMBOLS = " ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`";

    /** 结构总格数（自检用）。 */
    public static final int CELLS = WIDTH * HEIGHT * LENGTH;

    /** 构建结构。{@code FactoryBlockPattern.start()} 用 GTCEu 1.4.4 的默认轴
     * {@code (LEFT, UP, FRONT)} —— 与 mbs 头部 {@code charDir=LEFT / stringDir=UP / aisleDir=FRONT} 一致，
     * 所以<b>直填，不重排</b>。 */
    public static BlockPattern build(MultiblockMachineDefinition definition) {
        verifySymbols();
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
    private static void verifySymbols() {
        if (SLICES.length != LENGTH) {
            throw new IllegalStateException("燃料精炼复合体 结构：共 " + SLICES.length + " 片，应为 " + LENGTH);
        }
        for (int z = 0; z < SLICES.length; z++) {
            String[] rows = SLICES[z];
            if (rows.length != HEIGHT) {
                throw new IllegalStateException("燃料精炼复合体 结构：Z=" + z + " 只有 " + rows.length
                        + " 行，应为 " + HEIGHT);
            }
            for (int y = 0; y < rows.length; y++) {
                String row = rows[y];
                if (row.length() != WIDTH) {
                    throw new IllegalStateException("燃料精炼复合体 结构：SLICES[" + z + "][" + y + "] 行长 "
                            + row.length() + "，应为 " + WIDTH);
                }
                for (int x = 0; x < row.length(); x++) {
                    char c = row.charAt(x);
                    if (DECLARED_SYMBOLS.indexOf(c) < 0) {
                        throw new IllegalStateException(String.format(
                                "燃料精炼复合体 结构：符号 '%c'（0x%02X）在 SLICES[%d][%d] 第 %d 列出现，"
                                        + "但没有对应的 .where(...) 绑定。DECLARED_SYMBOLS=%s。",
                                c, (int) c, z, y, x, DECLARED_SYMBOLS));
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
}
