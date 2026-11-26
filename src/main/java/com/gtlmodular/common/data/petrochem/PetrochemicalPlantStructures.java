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
 * 集成石油化工处理工厂（{@code gtlmodular:petrochemical_plant}）的结构 —— GTO
 * {@code gtocore:petrochemical_plant}（中文名「石化工厂」）的 <b>1:1 照搬</b>。
 *
 * <h2>取证</h2>
 * <ul>
 *   <li>结构文件：GTO jar 内 {@code pattern/petrochemical_plant.mbs}
 *       （4,068 B，gzip 解开 117,075 B，<b>0 字节剩余</b> = 解析完整）。</li>
 *   <li>格式：{@code FF FF FF FF | 0F | ver=1 | charDir=2(LEFT) | stringDir=0(UP) | aisleDir=4(FRONT) |
 *       LEB(aisleCount=65) | LEB(stringsPerAisle=60)}，随后每行 {@code [1 字节行宽][字符]}；
 *       解析器 {@code _port/tools/parse_mbs.py}。</li>
 *   <li>尺寸：<b>28 (X) × 60 (Y) × 65 (Z) = 109,200 格</b>，实心 <b>11,729 格</b>
 *       （空气 97,471；控制器 {@code U} 1 格）。</li>
 *   <li>朝向：直转（charDir=LEFT / stringDir=UP / aisleDir=FRONT 与 GTCEu 1.4.4
 *       {@code FactoryBlockPattern.start()} 默认轴完全一致）。第 1 片 = 最背面（Z=0），
 *       第 65 片 = 正面（Z=64）；每片第 1 行 = 最低层（Y=0）；每行第 1 个字符 = 机器左手边。</li>
 *   <li>谓词绑定：GTO {@code MultiBlockA.java:162} 的 {@code PETROCHEMICAL_PLANT}
 *       （{@code CoilCrossRecipeMultiblockMachine::createCoilParallel}）。</li>
 *   <li>控制器位置（mbs 坐标 → 本文件下标）：{@code [Z=64][Y=3][X=22]}，即控制器本体嵌在
 *       整机<b>正面偏下（Y=3）的 5×5 计算机舱</b>里，舱口朝 +Z。</li>
 * </ul>
 *
 * <h2>符号表（GTO → 我方；★ = 新增方块，见 {@link PetrochemBlocks}；✚ = 木化工厂那轮已造）</h2>
 * <table border="1">
 *   <caption>29 个符号位 + 空格；格数取本文件实测</caption>
 *   <tr><th>符号</th><th>格数</th><th>GTO</th><th>我方</th></tr>
 *   <tr><td>{@code U}</td><td>1</td><td>控制器</td><td>{@code gtlmodular:petrochemical_plant}</td></tr>
 *   <tr><td>{@code I}</td><td>2552</td><td>水密外壳（GCYM）</td><td>{@code gtceu:watertight_casing}</td></tr>
 *   <tr><td>{@code A}</td><td>2438</td><td>应力结构外壳（GCYM）</td><td>{@code gtceu:stress_proof_casing}</td></tr>
 *   <tr><td>{@code C}</td><td>1495</td><td>洁净不锈钢机械方块</td><td>{@code gtceu:clean_machine_casing}</td></tr>
 *   <tr><td>{@code Z}</td><td>966</td><td>铱机械方块</td><td>{@code gtlcore:iridium_casing}</td></tr>
 *   <tr><td>{@code E}</td><td>616</td><td>油气输送密封管道机械方块</td>
 *       <td>✚{@code gtlmodular:oil_gas_transportation_pipe_casing}</td></tr>
 *   <tr><td>{@code M}</td><td>614</td><td>任意加热线圈</td><td>{@code Predicates.heatingCoils()}</td></tr>
 *   <tr><td>{@code J}</td><td>490</td><td>硅岩合金框架</td><td>{@code gtceu:naquadah_alloy_frame}</td></tr>
 *   <tr><td>{@code F}</td><td>420</td><td>钨钢管道外壳</td><td>{@code gtceu:tungstensteel_pipe_casing}</td></tr>
 *   <tr><td>{@code D}</td><td>348</td><td>钢框架</td><td>{@code gtceu:steel_frame}</td></tr>
 *   <tr><td>{@code Y}</td><td>339</td><td>坚固钨钢机械方块</td><td>{@code gtceu:robust_machine_casing}</td></tr>
 *   <tr><td>{@code L}</td><td>329</td><td>聚变玻璃</td><td>{@code gtceu:fusion_glass}</td></tr>
 *   <tr><td>{@code B}</td><td>274</td><td>超临界涡轮外壳</td>
 *       <td>★{@code gtlmodular:supercritical_turbine_casing}</td></tr>
 *   <tr><td>{@code K}</td><td>220</td><td>钨框架</td><td>{@code gtceu:tungsten_frame}</td></tr>
 *   <tr><td>{@code R}</td><td>141</td><td>钛管道外壳</td><td>{@code gtceu:titanium_pipe_casing}</td></tr>
 *   <tr><td>{@code N}</td><td>78</td><td>不锈钢 GC4 框架</td>
 *       <td>{@code gtceu:stainless_steel_frame}（本包无 GC4 材料，见下）</td></tr>
 *   <tr><td>{@code O}</td><td>74</td><td>计算机外壳</td><td>{@code gtceu:computer_casing}</td></tr>
 *   <tr><td>{@code T}</td><td>61</td><td>钢管道外壳</td><td>{@code gtceu:steel_pipe_casing}</td></tr>
 *   <tr><td>{@code X}</td><td>54</td><td>耐化学腐蚀管道机械方块</td>
 *       <td>✚{@code gtlmodular:chemical_corrosion_resistant_pipe_casing}</td></tr>
 *   <tr><td>{@code G}</td><td>40</td><td>皮基翁合金机械方块</td>
 *       <td>★{@code gtlmodular:pikyonium_machine_casing}</td></tr>
 *   <tr><td>{@code ^}</td><td>28</td><td>耐高压管道机械方块</td>
 *       <td>✚{@code gtlmodular:high_pressure_pipe_casing}</td></tr>
 *   <tr><td>{@code [}</td><td>27</td><td>铱管道机械方块</td>
 *       <td>★{@code gtlmodular:iridium_pipe_casing}</td></tr>
 *   <tr><td>{@code H}</td><td>26</td><td>高能机壳 + 激光仓 + 加速仓</td>
 *       <td>{@code gtceu:high_power_casing} + 进出仓 / 能源 / 激光 / 维护 / 线程 / 超频 舱面</td></tr>
 *   <tr><td>{@code r}</td><td>25</td><td>电解槽（GCYM）</td><td>{@code gtceu:electrolytic_cell}</td></tr>
 *   <tr><td>{@code S}</td><td>24</td><td>高能机械外壳</td><td>{@code gtceu:high_power_casing}</td></tr>
 *   <tr><td>{@code V}</td><td>20</td><td>化学级玻璃</td><td>★{@code gtlmodular:chemical_grade_glass}</td></tr>
 *   <tr><td>{@code P}</td><td>15</td><td>计算机散热口</td><td>{@code gtceu:computer_heat_vent}</td></tr>
 *   <tr><td>{@code W}</td><td>8</td><td>消音仓</td><td>{@code PartAbility.MUFFLER}</td></tr>
 *   <tr><td>{@code ]}</td><td>6</td><td>聚四氟乙烯管道外壳</td><td>{@code gtceu:ptfe_pipe_casing}</td></tr>
 * </table>
 *
 * <h2>✚ 附属模块（GTO 的 module 口 → 本模组的可装仓舱面）</h2>
 * <p>GTO 原机在控制器背后挂 {@code pattern/petrochem_crossrecipe.mbs} 这个<b>可拆可叠</b>的
 * 「多线程扩展段」（结构谓词 {@code .port('U', StructureModuleKeys.EXT_OUT, BACK)} +
 * {@code .atPort(EXT_OUT, Slot.optional(piece("petrochem_crossrecipe", ...).port('@', EXT_IN, FRONT))
 * .count(PETROCHEMICAL_PLANT_EXTENSION))}；工具提示写「搭建后可在其外壳上安装线程仓与超频仓」）。
 * <b>本模组没有 GTO 的模块框架</b>（{@code StructureModuleKeys} / {@code Slot} / {@code Piece} 都是 GTO
 * 魔改 GTCEu 的东西），所以按队长口径把「模块口」实现成 <b>主结构正面那 26 格 {@code H} 舱面</b>：
 * 方块一格未动，只是这 26 格在原谓词（高能机壳 ≥5 + 激光 + 加速仓）之外再开放
 * <b>物品/流体进出仓、能源输入、维护仓、线程仓（≤1）、超频仓、并行仓（≤1）</b>。
 *
 * <h2>与上游的已知偏差</h2>
 * <ul>
 *   <li>附属模块子结构（{@code petrochem_crossrecipe.mbs}，29×51×65）不移植，见上。</li>
 *   <li>{@code N} 位 GTO 用 <b>不锈钢 GC4</b> 框架（{@code GTOMaterials.StainlessSteelGC4}），
 *       该材料是 GTOCore 独有、本包（gtceu 1.4.4 + gtlcore + gtladditions + Enhancedcore）没有，
 *       退回 {@code gtceu:stainless_steel_frame}（同族、同形状）。</li>
 * </ul>
 */
public final class PetrochemicalPlantStructures {


    /** X 跨度：每行 28 个字符。 */
    public static final int WIDTH = 28;
    /** Y 跨度：每片 60 行。 */
    public static final int HEIGHT = 60;
    /** GTO 模块并入后的升级结构维度。 */
    public static final int UPGRADED_WIDTH = 29;
    public static final int UPGRADED_LENGTH = 65;
    /** Z 跨度：共 65 片。 */
    public static final int LENGTH = 65;

    private PetrochemicalPlantStructures() {
    }

    /** 结构数据（{@code [z][y]} 的字符串），给自检与预览脚本用。 */
    public static String[][] slices() {
        return SLICES;
    }

    private static final String[][] SLICES = {
            { // Z=0
                    "AAAAAAAAAAAAAAA   AAAAAAAAA ", // Y=0
                    "AAAAAAAAAAAAAAA   AAAAAAAAA ", // Y=1
                    "                  AAAAAAAAA ", // Y=2
                    "                            ", // Y=3
                    "                            ", // Y=4
                    "         YYY                ", // Y=5
                    "                            ", // Y=6
                    "                    DEEED   ", // Y=7
                    "                            ", // Y=8
                    "                            ", // Y=9
                    "                            ", // Y=10
                    "                            ", // Y=11
                    "                    DEEED   ", // Y=12
                    "         YYY                ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                    DEEED   ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "         YYY                ", // Y=21
                    "                    DEEED   ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                    DEEED   ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                    DEEED   ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                    DEEED   ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                    DEEED   ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                    DEEED   ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                    DEEED   ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=1
                    "AAAAAAAAAAAAAAA   AAAAAAAAA ", // Y=0
                    "AAAAAAAAAAAAAAA   AAAIIIAAA ", // Y=1
                    "         Z Z J    AAAIIIAAA ", // Y=2
                    "         Z Z J     J III J  ", // Y=3
                    "         Z Z J     J III J  ", // Y=4
                    "        YZZZYJ     J III J  ", // Y=5
                    "         ZLZ J     J III J  ", // Y=6
                    "         ZLZ J     JEIIIEJ  ", // Y=7
                    "         ZLZ J     J III J  ", // Y=8
                    "         ZLZ J     J III J  ", // Y=9
                    "         ZLZ J     J III J  ", // Y=10
                    "         ZLZ J     J III J  ", // Y=11
                    "         ZLZ J     JEIIIEJ  ", // Y=12
                    "        YZZZYJ     J III J  ", // Y=13
                    "         ZLZ J     J III J  ", // Y=14
                    "         ZLZ J     J III J  ", // Y=15
                    "         ZLZ J     J III J  ", // Y=16
                    "         ZZZ J     JEIIIEJ  ", // Y=17
                    "         ZZZ J     J III J  ", // Y=18
                    "         ZZZ J     J III J  ", // Y=19
                    "         ZZZ J     J III J  ", // Y=20
                    "        YZZZYJ     J III J  ", // Y=21
                    "         ZZZ J     JEIIIEJ  ", // Y=22
                    "             J     J III J  ", // Y=23
                    "                   J III J  ", // Y=24
                    "                   J III J  ", // Y=25
                    "                   J III J  ", // Y=26
                    "                   JEIIIEJ  ", // Y=27
                    "                   J III J  ", // Y=28
                    "                   J III J  ", // Y=29
                    "                   J III J  ", // Y=30
                    "                   J III J  ", // Y=31
                    "                   JEIIIEJ  ", // Y=32
                    "                   K III K  ", // Y=33
                    "                   K III K  ", // Y=34
                    "                   K III K  ", // Y=35
                    "                   K III K  ", // Y=36
                    "                   KEIIIEK  ", // Y=37
                    "                   K III K  ", // Y=38
                    "                   K III K  ", // Y=39
                    "                   K III K  ", // Y=40
                    "                   K III K  ", // Y=41
                    "                   KEIIIEK  ", // Y=42
                    "                   K III K  ", // Y=43
                    "                   K III K  ", // Y=44
                    "                   K III K  ", // Y=45
                    "                   K III K  ", // Y=46
                    "                   KEIIIEK  ", // Y=47
                    "                   K III K  ", // Y=48
                    "                   K III K  ", // Y=49
                    "                   K III K  ", // Y=50
                    "                   K III K  ", // Y=51
                    "                   KEIIIEK  ", // Y=52
                    "                     III    ", // Y=53
                    "                     III    ", // Y=54
                    "                     III    ", // Y=55
                    "                     III    ", // Y=56
                    "                     III    ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=2
                    "AAAAAAAAAAAAAAA   AAAAAAAAA ", // Y=0
                    "AAAAAAAAAAAAAAA   AAIAAAIAA ", // Y=1
                    "                  AAIMMMIAA ", // Y=2
                    "                    I   I   ", // Y=3
                    "                    I   I   ", // Y=4
                    "       YZZZZZY      I   I   ", // Y=5
                    "        ZMMMZ       IMMMI   ", // Y=6
                    "        Z   Z     DEIIIIIED ", // Y=7
                    "        ZMMMZ       I   I   ", // Y=8
                    "        Z   Z       I   I   ", // Y=9
                    "        ZMMMZ       I   I   ", // Y=10
                    "        Z   Z       IMMMI   ", // Y=11
                    "        Z   Z     DEIIIIIED ", // Y=12
                    "       YZ   ZY      I   I   ", // Y=13
                    "        Z   Z       I   I   ", // Y=14
                    "        ZMMMZ       I   I   ", // Y=15
                    "        Z   Z       IMMMI   ", // Y=16
                    "        Z   Z     DEIIIIIED ", // Y=17
                    "        Z   Z       I   I   ", // Y=18
                    "        Z   Z       I   I   ", // Y=19
                    "        ZMMMZ       I   I   ", // Y=20
                    "       YZ   ZY      IMMMI   ", // Y=21
                    "        Z   Z     DEIIIIIED ", // Y=22
                    "         ZZZ        I   I   ", // Y=23
                    "                    I   I   ", // Y=24
                    "                    I   I   ", // Y=25
                    "                    IMMMI   ", // Y=26
                    "                  DEIIIIIED ", // Y=27
                    "                    I   I   ", // Y=28
                    "                    I   I   ", // Y=29
                    "                    I   I   ", // Y=30
                    "                    IMMMI   ", // Y=31
                    "                  DEIIIIIED ", // Y=32
                    "                    I   I   ", // Y=33
                    "                    I   I   ", // Y=34
                    "                    I   I   ", // Y=35
                    "                    I   I   ", // Y=36
                    "                  DEIIIIIED ", // Y=37
                    "                    I   I   ", // Y=38
                    "                    I   I   ", // Y=39
                    "                    I   I   ", // Y=40
                    "                    I   I   ", // Y=41
                    "                  DEIIIIIED ", // Y=42
                    "                    I   I   ", // Y=43
                    "                    I   I   ", // Y=44
                    "                    I   I   ", // Y=45
                    "                    I   I   ", // Y=46
                    "                  DEIIIIIED ", // Y=47
                    "                    I   I   ", // Y=48
                    "                    I   I   ", // Y=49
                    "                    I   I   ", // Y=50
                    "                    I   I   ", // Y=51
                    "                  DEIIIIIED ", // Y=52
                    "                    I   I   ", // Y=53
                    "                    I   I   ", // Y=54
                    "                    I   I   ", // Y=55
                    "                    I   I   ", // Y=56
                    "                    I   I   ", // Y=57
                    "                     III    ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=3
                    "AAAAAAAAAAAAAAA   AAAAAAAAA ", // Y=0
                    "AAAAAAAAAAARAAA   AIAMMMAIA ", // Y=1
                    "      JZ   R Z    AIM   MIA ", // Y=2
                    "      JZ   R Z     I     I  ", // Y=3
                    "      JZ   R Z     I     I  ", // Y=4
                    "   YYYJZZZZRZZY    I     I  ", // Y=5
                    "      JZM  RMZ     IM   MI  ", // Y=6
                    "      JZ   R Z    EIIFFFIIE ", // Y=7
                    "      JZM  RMZ     I     I  ", // Y=8
                    "      JZ   R Z     I     I  ", // Y=9
                    "      JZM  RMZ     I     I  ", // Y=10
                    "      JZ   R Z     IM   MI  ", // Y=11
                    "      JZ   R Z    EIIFFFIIE ", // Y=12
                    "   YYYJZ   R ZY    I     I  ", // Y=13
                    "      JZ   R Z     I     I  ", // Y=14
                    "      JZM  RMZ     I     I  ", // Y=15
                    "      JZ   R Z     IM   MI  ", // Y=16
                    "      JZ   R Z    EIIFFFIIE ", // Y=17
                    "      JZ   R Z     I     I  ", // Y=18
                    "      JZ   R Z     I     I  ", // Y=19
                    "      JZM  RMZ     I     I  ", // Y=20
                    "   YYYJZ   R ZY    IM   MI  ", // Y=21
                    "      JZ   R Z    EIIFFFIIE ", // Y=22
                    "      J Z   Z      I     I  ", // Y=23
                    "         ZZZ       I     I  ", // Y=24
                    "                   I     I  ", // Y=25
                    "                   IM   MI  ", // Y=26
                    "                  EIIFFFIIE ", // Y=27
                    "                   I     I  ", // Y=28
                    "                   I     I  ", // Y=29
                    "                   I     I  ", // Y=30
                    "                   IM   MI  ", // Y=31
                    "                  EIIFFFIIE ", // Y=32
                    "                   I     I  ", // Y=33
                    "                   I     I  ", // Y=34
                    "                   I     I  ", // Y=35
                    "                   IM   MI  ", // Y=36
                    "                  EIIFFFIIE ", // Y=37
                    "                   I     I  ", // Y=38
                    "                   I     I  ", // Y=39
                    "                   I     I  ", // Y=40
                    "                   IM   MI  ", // Y=41
                    "                  EIIFFFIIE ", // Y=42
                    "                   I     I  ", // Y=43
                    "                   I     I  ", // Y=44
                    "                   I     I  ", // Y=45
                    "                   IM   MI  ", // Y=46
                    "                  EIIFFFIIE ", // Y=47
                    "                   I     I  ", // Y=48
                    "                   I     I  ", // Y=49
                    "                   I     I  ", // Y=50
                    "                   IM   MI  ", // Y=51
                    "                  EIIFFFIIE ", // Y=52
                    "                   I     I  ", // Y=53
                    "                   I     I  ", // Y=54
                    "                   I     I  ", // Y=55
                    "                   I     I  ", // Y=56
                    "                   I     I  ", // Y=57
                    "                    I   I   ", // Y=58
                    "                     III    ", // Y=59
            },
            { // Z=4
                    "AAAAAAAAAAAAAAA   AAAAAAAAA ", // Y=0
                    "AAAAAAAAAAAAAAA   AIAMFMAIA ", // Y=1
                    " J Z Z Z          AIM F MIA ", // Y=2
                    " J Z Z Z           I  F  I  ", // Y=3
                    " J Z Z Z           I  F  I  ", // Y=4
                    " JYZZZYZZZZZZZY    I  F  I  ", // Y=5
                    " J ZZZ ZM   MZ     IM F MI  ", // Y=6
                    " J ZZZ Z     Z    EIIFFFIIE ", // Y=7
                    " J ZZZ ZM [ MZ     I  F  I  ", // Y=8
                    " J ZZZ Z  [  Z     I  F  I  ", // Y=9
                    " J ZZZ ZM [ MZ     I  F  I  ", // Y=10
                    " J ZZZ Z     Z     IM F MI  ", // Y=11
                    " J ZZZ Z     Z    EIIFFFIIE ", // Y=12
                    " JYZZZYZ     ZY    I  F  I  ", // Y=13
                    " J ZZZ Z  [  Z     I  F  I  ", // Y=14
                    " J ZZZ ZM [ MZ     I  F  I  ", // Y=15
                    " J ZZZ Z  [  Z     IM F MI  ", // Y=16
                    " J ZZZ Z     Z    EIIFFFIIE ", // Y=17
                    " J ZZZ Z     Z     I  F  I  ", // Y=18
                    " J ZZZ Z  [  Z     I  F  I  ", // Y=19
                    " J ZZZ ZM [ MZ     I  F  I  ", // Y=20
                    " JYZZZYZ  [  ZY    IM F MI  ", // Y=21
                    " J ZZZ Z     Z    EIIFFFIIE ", // Y=22
                    " J      Z   Z      I  F  I  ", // Y=23
                    "         ZWZ       I  F  I  ", // Y=24
                    "                   I  F  I  ", // Y=25
                    "                   IM F MI  ", // Y=26
                    "                  EIIFFFIIE ", // Y=27
                    "                   I  F  I  ", // Y=28
                    "                   I  F  I  ", // Y=29
                    "                   I  F  I  ", // Y=30
                    "                   IM F MI  ", // Y=31
                    "                  EIIFFFIIE ", // Y=32
                    "                   I  F  I  ", // Y=33
                    "                   I  F  I  ", // Y=34
                    "                   I  F  I  ", // Y=35
                    "                   IM F MI  ", // Y=36
                    "                  EIIFFFIIE ", // Y=37
                    "                   I  F  I  ", // Y=38
                    "                   I  F  I  ", // Y=39
                    "                   I  F  I  ", // Y=40
                    "                   IM F MI  ", // Y=41
                    "                  EIIFFFIIE ", // Y=42
                    "                   I  F  I  ", // Y=43
                    "                   I  F  I  ", // Y=44
                    "                   I  F  I  ", // Y=45
                    "                   IM F MI  ", // Y=46
                    "                  EIIFFFIIE ", // Y=47
                    "                   I  F  I  ", // Y=48
                    "                   I  F  I  ", // Y=49
                    "                   I  F  I  ", // Y=50
                    "                   IM F MI  ", // Y=51
                    "                  EIIFFFIIE ", // Y=52
                    "                   I  F  I  ", // Y=53
                    "                   I  F  I  ", // Y=54
                    "                   I  F  I  ", // Y=55
                    "                   I  F  I  ", // Y=56
                    "                   I  F  I  ", // Y=57
                    "                    I   I   ", // Y=58
                    "                     IWI    ", // Y=59
            },
            { // Z=5
                    "AAAAAAAAAAAAAAA   AAAAAAAAA ", // Y=0
                    "AAAAAAAAAFAAAAA   AIAMMMAIA ", // Y=1
                    "       Z F   Z    AIM   MIA ", // Y=2
                    "       Z F   Z     I     I  ", // Y=3
                    "       Z F   Z     I     I  ", // Y=4
                    " YZZZZZZZFZZZZY    I     I  ", // Y=5
                    "  ZMMMZZMF  MZ     IM   MI  ", // Y=6
                    "  Z   ZZ F   Z    EIIFFFIIE ", // Y=7
                    "  ZMMMZZMF  MZ     I     I  ", // Y=8
                    "  Z   ZZ F   Z     I     I  ", // Y=9
                    "  ZMMMZZMF  MZ     I     I  ", // Y=10
                    "  Z   ZZ F   Z     IM   MI  ", // Y=11
                    "  Z   ZZ F   Z    EIIFFFIIE ", // Y=12
                    " YZ   ZZ F   ZY    I     I  ", // Y=13
                    "  Z   ZZ F   Z     I     I  ", // Y=14
                    "  ZMMMZZMF  MZ     I     I  ", // Y=15
                    "  Z   ZZ F   Z     IM   MI  ", // Y=16
                    "  Z   ZZ F   Z    EIIFFFIIE ", // Y=17
                    "  Z   ZZ F   Z     I     I  ", // Y=18
                    "  Z   ZZ F   Z     I     I  ", // Y=19
                    "  ZMMMZZMF  MZ     I     I  ", // Y=20
                    " YZ   ZZ F   ZY    IM   MI  ", // Y=21
                    "  Z   ZZ F   Z    EIIFFFIIE ", // Y=22
                    "   ZZZ  Z   Z      I     I  ", // Y=23
                    "         ZZZ       I     I  ", // Y=24
                    "                   I     I  ", // Y=25
                    "                   IM   MI  ", // Y=26
                    "                  EIIFFFIIE ", // Y=27
                    "                   I     I  ", // Y=28
                    "                   I     I  ", // Y=29
                    "                   I     I  ", // Y=30
                    "                   IM   MI  ", // Y=31
                    "                  EIIFFFIIE ", // Y=32
                    "                   I     I  ", // Y=33
                    "                   I     I  ", // Y=34
                    "                   I     I  ", // Y=35
                    "                   IM   MI  ", // Y=36
                    "                  EIIFFFIIE ", // Y=37
                    "                   I     I  ", // Y=38
                    "                   I     I  ", // Y=39
                    "                   I     I  ", // Y=40
                    "                   IM   MI  ", // Y=41
                    "                  EIIFFFIIE ", // Y=42
                    "                   I     I  ", // Y=43
                    "                   I     I  ", // Y=44
                    "                   I     I  ", // Y=45
                    "                   IM   MI  ", // Y=46
                    "                  EIIFFFIIE ", // Y=47
                    "                   I     I  ", // Y=48
                    "                   I     I  ", // Y=49
                    "                   I     I  ", // Y=50
                    "                   IM   MI  ", // Y=51
                    "                  EIIFFFIIE ", // Y=52
                    "                   I     I  ", // Y=53
                    "                   I     I  ", // Y=54
                    "                   I     I  ", // Y=55
                    "                   I     I  ", // Y=56
                    "                   I     I  ", // Y=57
                    "                    I   I   ", // Y=58
                    "                     III    ", // Y=59
            },
            { // Z=6
                    "AAAAAAAAAAAAAAA   AAAAAAAAA ", // Y=0
                    "AAAAAAAAAAAAAAA   AAIAAAIAA ", // Y=1
                    " Z R              AAIMMMIAA ", // Y=2
                    " Z R                I   I   ", // Y=3
                    " Z R                I   I   ", // Y=4
                    "YZZRZZZZZZZZZY      I   I   ", // Y=5
                    " ZMR  MZZMMMZ       IMMMI   ", // Y=6
                    " Z R   ZZ   Z     DEIIIIIED ", // Y=7
                    " ZMR  MZZMMMZ       I   I   ", // Y=8
                    " Z R   ZZ   Z       I   I   ", // Y=9
                    " ZMR  MZZMMMZ       I   I   ", // Y=10
                    " Z R   ZZ   Z       IMMMI   ", // Y=11
                    " Z R   ZZ   Z     DEIIIIIED ", // Y=12
                    "YZ R   ZZ   ZY      I   I   ", // Y=13
                    " Z R   ZZ   Z       I   I   ", // Y=14
                    " ZMR  MZZMMMZ       I   I   ", // Y=15
                    " Z R   ZZ   Z       IMMMI   ", // Y=16
                    " Z R   ZZ   Z     DEIIIIIED ", // Y=17
                    " Z R   ZZ   Z       I   I   ", // Y=18
                    " Z R   ZZ   Z       I   I   ", // Y=19
                    " ZMR  MZZMMMZ       I   I   ", // Y=20
                    "YZ R   ZZ   ZY      IMMMI   ", // Y=21
                    " Z R   ZZ   Z     DEIIIIIED ", // Y=22
                    "  Z  FZ  ZZZ        I   I   ", // Y=23
                    "   ZZZ              I   I   ", // Y=24
                    "                    I   I   ", // Y=25
                    "                    IMMMI   ", // Y=26
                    "                  DEIIIIIED ", // Y=27
                    "                    I   I   ", // Y=28
                    "                    I   I   ", // Y=29
                    "                    I   I   ", // Y=30
                    "                    IMMMI   ", // Y=31
                    "                  DEIIIIIED ", // Y=32
                    "                    I   I   ", // Y=33
                    "                    I   I   ", // Y=34
                    "                    I   I   ", // Y=35
                    "                    I   I   ", // Y=36
                    "                  DEIIIIIED ", // Y=37
                    "                    I   I   ", // Y=38
                    "                    I   I   ", // Y=39
                    "                    I   I   ", // Y=40
                    "                    I   I   ", // Y=41
                    "                  DEIIIIIED ", // Y=42
                    "                    I   I   ", // Y=43
                    "                    I   I   ", // Y=44
                    "                    I   I   ", // Y=45
                    "                    I   I   ", // Y=46
                    "                  DEIIIIIED ", // Y=47
                    "                    I   I   ", // Y=48
                    "                    I   I   ", // Y=49
                    "                    I   I   ", // Y=50
                    "                    I   I   ", // Y=51
                    "                  DEIIIIIED ", // Y=52
                    "                    I   I   ", // Y=53
                    "                    I   I   ", // Y=54
                    "                    I   I   ", // Y=55
                    "                    I   I   ", // Y=56
                    "                    I   I   ", // Y=57
                    "                     III    ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=7
                    "AAAAAAAAAAAAAAA   AAAAAAAAA ", // Y=0
                    "AAAAAAAAAAAAAAA   AAAIIIAAA ", // Y=1
                    "         ZZZJ     AAAIIIAAA ", // Y=2
                    "         ZZZJ      J III J  ", // Y=3
                    "         ZZZJ      J III J  ", // Y=4
                    "YZZZZZZZ ZZZJ      J III J  ", // Y=5
                    " LM   MZ ZZZJ      J III J  ", // Y=6
                    " L     Z ZZZJ      JEIIIEJ  ", // Y=7
                    " LM [ MZ ZZZJ      J III J  ", // Y=8
                    " L  [  Z ZZZJ      J IVI J  ", // Y=9
                    " LM [ MZ ZZZJ      J IVI J  ", // Y=10
                    " L     Z ZZZJ      J III J  ", // Y=11
                    " L     Z ZZZJ      JEIIIEJ  ", // Y=12
                    "YZ     Z ZZZJ      J III J  ", // Y=13
                    " L  [  Z ZZZJ      J III J  ", // Y=14
                    " LM [ MZ ZZZJ      J III J  ", // Y=15
                    " L  [  Z ZZZJ      J III J  ", // Y=16
                    " Z     Z ZZZJ      JEIIIEJ  ", // Y=17
                    " Z     Z ZZZJ      J III J  ", // Y=18
                    " Z  [  Z ZZZJ      J IVI J  ", // Y=19
                    " ZM [ MZ ZZZJ      J IVI J  ", // Y=20
                    "YZ  [  Z ZZZJ      J III J  ", // Y=21
                    " Z     Z ZZZJ      JEIIIEJ  ", // Y=22
                    "  Z   Z     J      J III J  ", // Y=23
                    "   ZWZ             J III J  ", // Y=24
                    "                   J III J  ", // Y=25
                    "                   J III J  ", // Y=26
                    "                   JEIIIEJ  ", // Y=27
                    "                   J III J  ", // Y=28
                    "                   J IVI J  ", // Y=29
                    "                   J IVI J  ", // Y=30
                    "                   J III J  ", // Y=31
                    "                   JEIIIEJ  ", // Y=32
                    "                   K III K  ", // Y=33
                    "                   K III K  ", // Y=34
                    "                   K III K  ", // Y=35
                    "                   K III K  ", // Y=36
                    "                   KEIIIEK  ", // Y=37
                    "                   K III K  ", // Y=38
                    "                   K IVI K  ", // Y=39
                    "                   K IVI K  ", // Y=40
                    "                   K III K  ", // Y=41
                    "                   KEIIIEK  ", // Y=42
                    "                   K III K  ", // Y=43
                    "                   K III K  ", // Y=44
                    "                   K III K  ", // Y=45
                    "                   K III K  ", // Y=46
                    "                   KEIIIEK  ", // Y=47
                    "                   K III K  ", // Y=48
                    "                   K IVI K  ", // Y=49
                    "                   K IVI K  ", // Y=50
                    "                   K III K  ", // Y=51
                    "                   KEIIIEK  ", // Y=52
                    "                     III    ", // Y=53
                    "                     III    ", // Y=54
                    "                     III    ", // Y=55
                    "                     III    ", // Y=56
                    "                     III    ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=8
                    "AAAAAAAAAAAAAAA   AAAAAAAAA ", // Y=0
                    "AAAAAAAAAAAAAAA   AAAAAAAAA ", // Y=1
                    " Z   F            AAAAAAAAA ", // Y=2
                    " Z   F                      ", // Y=3
                    " Z   F                      ", // Y=4
                    "YZZZZFZZZZZZZY              ", // Y=5
                    " ZM  FMZZMMMZ          N    ", // Y=6
                    " Z   F ZZ   Z       DEEED   ", // Y=7
                    " ZM  FMZZMMMZ               ", // Y=8
                    " Z   F ZZ   Z               ", // Y=9
                    " ZM  FMZZMMMZ               ", // Y=10
                    " Z   F ZZ   Z        N      ", // Y=11
                    " Z   F ZZ   Z       DEEED   ", // Y=12
                    "YZ   F ZZ   ZY              ", // Y=13
                    " Z   F ZZ   Z               ", // Y=14
                    " ZM  FMZZMMMZ               ", // Y=15
                    " Z   F ZZ   Z          N    ", // Y=16
                    " Z   F ZZ   Z       DEEED   ", // Y=17
                    " Z   F ZZ   Z               ", // Y=18
                    " Z   F ZZ   Z               ", // Y=19
                    " ZM  FMZZMMMZ               ", // Y=20
                    "YZ   F ZZ   ZY       N      ", // Y=21
                    " Z   F ZZ   Z       DEEED   ", // Y=22
                    "  Z   Z  ZZZ                ", // Y=23
                    "   ZZZ                      ", // Y=24
                    "                            ", // Y=25
                    "                       N    ", // Y=26
                    "                    DEEED   ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                     N      ", // Y=31
                    "                    DEEED   ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                       N    ", // Y=36
                    "                    DEEED   ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                     N      ", // Y=41
                    "                    DEEED   ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                       N    ", // Y=46
                    "                    DEEED   ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                     N      ", // Y=51
                    "                    DEEED   ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=9
                    "AAAAAAAAAAAAAAA   AAAAAAAAA ", // Y=0
                    "AAAAAAAAAAAFAAA     AAAAA   ", // Y=1
                    "       Z   F Z       AAA    ", // Y=2
                    "       Z   F Z              ", // Y=3
                    "       Z   F Z              ", // Y=4
                    " YZZZZZZZZZFZZY             ", // Y=5
                    "  ZMMMZZM  FMZ         N    ", // Y=6
                    "  Z   ZZ   F Z        E     ", // Y=7
                    "  ZMMMZZM  FMZ              ", // Y=8
                    "  Z   ZZ   F Z              ", // Y=9
                    "  ZMMMZZM  FMZ              ", // Y=10
                    "  Z   ZZ   F Z       N      ", // Y=11
                    "  Z   ZZ   F Z        E     ", // Y=12
                    " YZ   ZZ   F ZY             ", // Y=13
                    "  Z   ZZ   F Z              ", // Y=14
                    "  ZMMMZZM  FMZ              ", // Y=15
                    "  Z   ZZ   F Z         N    ", // Y=16
                    "  Z   ZZ   F Z        E     ", // Y=17
                    "  Z   ZZ   F Z              ", // Y=18
                    "  Z   ZZ   F Z              ", // Y=19
                    "  ZMMMZZM  FMZ              ", // Y=20
                    " YZ   ZZ   F ZY      N      ", // Y=21
                    "  Z   ZZ   F Z        E     ", // Y=22
                    "   ZZZ  Z   Z               ", // Y=23
                    "         ZZZ                ", // Y=24
                    "                            ", // Y=25
                    "                       N    ", // Y=26
                    "                      E     ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                     N      ", // Y=31
                    "                      E     ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                       N    ", // Y=36
                    "                      E     ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                     N      ", // Y=41
                    "                      E     ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                       N    ", // Y=46
                    "                      E     ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                     N      ", // Y=51
                    "                      E     ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=10
                    "AAAAAAAAAAAAAAA   AAAAAAAAA ", // Y=0
                    "AAAAAAAAAAAAAAA     AAAAA   ", // Y=1
                    " J Z Z Z             AAA    ", // Y=2
                    " J Z Z Z                    ", // Y=3
                    " J Z Z Z                    ", // Y=4
                    " JYZZZYZZZZZZZY        N    ", // Y=5
                    " J ZZZ ZM   MZ              ", // Y=6
                    " J ZZZ Z     Z        E     ", // Y=7
                    " J ZZZ ZM [ MZ              ", // Y=8
                    " J ZZZ Z  [  Z              ", // Y=9
                    " J ZZZ ZM [ MZ       N      ", // Y=10
                    " J ZZZ Z     Z              ", // Y=11
                    " J ZZZ Z     Z EEEEEEEE     ", // Y=12
                    " JYZZZYZ     ZY             ", // Y=13
                    " J ZZZ Z  [  Z              ", // Y=14
                    " J ZZZ ZM [ MZ         N    ", // Y=15
                    " J ZZZ Z  [  Z              ", // Y=16
                    " J ZZZ Z     Z        E     ", // Y=17
                    " J ZZZ Z     Z              ", // Y=18
                    " J ZZZ Z  [  Z              ", // Y=19
                    " J ZZZ ZM [ MZ       N      ", // Y=20
                    " JYZZZYZ  [  ZY             ", // Y=21
                    " J ZZZ Z     Z EEEEEEEE     ", // Y=22
                    " J      Z   Z               ", // Y=23
                    "         ZWZ                ", // Y=24
                    "                       N    ", // Y=25
                    "                            ", // Y=26
                    "                      E     ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                     N      ", // Y=30
                    "                            ", // Y=31
                    "                      E     ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                       N    ", // Y=35
                    "                            ", // Y=36
                    "                      E     ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                     N      ", // Y=40
                    "                            ", // Y=41
                    "                      E     ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                       N    ", // Y=45
                    "                            ", // Y=46
                    "                      E     ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                     N      ", // Y=50
                    "                            ", // Y=51
                    "                      E     ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=11
                    "AAAAAAAAAAAAAAA   AAAAAAAAA ", // Y=0
                    "AAAAAAAAARAAAAA     AAAAA   ", // Y=1
                    "      JZ R   Z       AAA    ", // Y=2
                    "      JZ R   Z              ", // Y=3
                    "      JZ R   Z         N    ", // Y=4
                    "   YYYJZZRZZZZY             ", // Y=5
                    "      JZMR  MZ              ", // Y=6
                    "      JZ R   Z        E     ", // Y=7
                    "      JZMR  MZ              ", // Y=8
                    "      JZ R   Z       N      ", // Y=9
                    "      JZMR  MZ              ", // Y=10
                    "      JZ R   Z              ", // Y=11
                    "      JZ R   Z E      E     ", // Y=12
                    "   YYYJZ R   ZY             ", // Y=13
                    "      JZ R   Z         N    ", // Y=14
                    "      JZMR  MZ              ", // Y=15
                    "      JZ R   Z              ", // Y=16
                    "      JZ R   Z        E     ", // Y=17
                    "      JZ R   Z              ", // Y=18
                    "      JZ R   Z       N      ", // Y=19
                    "      JZMR  MZ              ", // Y=20
                    "   YYYJZ R   ZY             ", // Y=21
                    "      JZ R   Z E      E     ", // Y=22
                    "      J Z   Z               ", // Y=23
                    "         ZZZ           N    ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                      E     ", // Y=27
                    "                            ", // Y=28
                    "                     N      ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                      E     ", // Y=32
                    "                            ", // Y=33
                    "                       N    ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                      E     ", // Y=37
                    "                            ", // Y=38
                    "                     N      ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                      E     ", // Y=42
                    "                            ", // Y=43
                    "                       N    ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                      E     ", // Y=47
                    "                            ", // Y=48
                    "                     N      ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                      E     ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=12
                    "AAAAAAAAAAAAAAA   AAAAAAAAA ", // Y=0
                    "AAAAAAAAAAAAAAA   AAAAAAAAA ", // Y=1
                    "                  AAAAAAAAA ", // Y=2
                    "                       N    ", // Y=3
                    "                            ", // Y=4
                    "       YZZZZZY              ", // Y=5
                    "        ZMMMZ               ", // Y=6
                    "        Z   Z       DEEED   ", // Y=7
                    "        ZMMMZ        N      ", // Y=8
                    "        Z   Z               ", // Y=9
                    "        ZMMMZ               ", // Y=10
                    "        Z   Z               ", // Y=11
                    "        Z   Z  E    DEEED   ", // Y=12
                    "       YZ   ZY         N    ", // Y=13
                    "        Z   Z               ", // Y=14
                    "        ZMMMZ               ", // Y=15
                    "        Z   Z               ", // Y=16
                    "        Z   Z       DEEED   ", // Y=17
                    "        Z   Z        N      ", // Y=18
                    "        Z   Z               ", // Y=19
                    "        ZMMMZ               ", // Y=20
                    "       YZ   ZY              ", // Y=21
                    "        Z   Z  E    DEEED   ", // Y=22
                    "         ZZZ           N    ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                    DEEED   ", // Y=27
                    "                     N      ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                    DEEED   ", // Y=32
                    "                       N    ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                    DEEED   ", // Y=37
                    "                     N      ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                    DEEED   ", // Y=42
                    "                       N    ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                    DEEED   ", // Y=47
                    "                     N      ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                    DEEED   ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=13
                    "AAAAAAAAAAAAAAA   AAAAAAAAA ", // Y=0
                    "AAAAAAAAAAAAAAA   AAAIIIAAA ", // Y=1
                    "         Z Z J    AAAIIIAAA ", // Y=2
                    "         Z Z J     J III J  ", // Y=3
                    "         Z Z J     J III J  ", // Y=4
                    "        YZZZYJ     J III J  ", // Y=5
                    "         ZLZ J     J III J  ", // Y=6
                    "         ZLZ J     JEIIIEJ  ", // Y=7
                    "         ZLZ J     J III J  ", // Y=8
                    "         ZLZ J     J IVI J  ", // Y=9
                    "         ZLZ J     J IVI J  ", // Y=10
                    "         ZLZ J     J III J  ", // Y=11
                    "         ZLZ J E   JEIIIEJ  ", // Y=12
                    "        YZZZYJ     J III J  ", // Y=13
                    "         ZLZ J     J III J  ", // Y=14
                    "         ZLZ J     J III J  ", // Y=15
                    "         ZLZ J     J III J  ", // Y=16
                    "         ZZZ J     JEIIIEJ  ", // Y=17
                    "         ZZZ J     J III J  ", // Y=18
                    "         ZZZ J     J IVI J  ", // Y=19
                    "         ZZZ J     J IVI J  ", // Y=20
                    "        YZZZYJ     J III J  ", // Y=21
                    "         ZZZ J E   JEIIIEJ  ", // Y=22
                    "             J     J III J  ", // Y=23
                    "                   J III J  ", // Y=24
                    "                   J III J  ", // Y=25
                    "                   J III J  ", // Y=26
                    "                   JEIIIEJ  ", // Y=27
                    "                   J III J  ", // Y=28
                    "                   J IVI J  ", // Y=29
                    "                   J IVI J  ", // Y=30
                    "                   J III J  ", // Y=31
                    "                   JEIIIEJ  ", // Y=32
                    "                   K III K  ", // Y=33
                    "                   K III K  ", // Y=34
                    "                   K III K  ", // Y=35
                    "                   K III K  ", // Y=36
                    "                   KEIIIEK  ", // Y=37
                    "                   K III K  ", // Y=38
                    "                   K IVI K  ", // Y=39
                    "                   K IVI K  ", // Y=40
                    "                   K III K  ", // Y=41
                    "                   KEIIIEK  ", // Y=42
                    "                   K III K  ", // Y=43
                    "                   K III K  ", // Y=44
                    "                   K III K  ", // Y=45
                    "                   K III K  ", // Y=46
                    "                   KEIIIEK  ", // Y=47
                    "                   K III K  ", // Y=48
                    "                   K IVI K  ", // Y=49
                    "                   K IVI K  ", // Y=50
                    "                   K III K  ", // Y=51
                    "                   KEIIIEK  ", // Y=52
                    "                     III    ", // Y=53
                    "                     III    ", // Y=54
                    "                     III    ", // Y=55
                    "                     III    ", // Y=56
                    "                     III    ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=14
                    "AAAAAAAAAAAAAAA   AAAAAAAAA ", // Y=0
                    "AAAAAAAAAAAAAAA   AAIAAAIAA ", // Y=1
                    "                  AAIMMMIAA ", // Y=2
                    "                    I   I   ", // Y=3
                    "                    I   I   ", // Y=4
                    "         YYY        I   I   ", // Y=5
                    "                    IMMMI   ", // Y=6
                    "                  DEIIIIIED ", // Y=7
                    "                    I   I   ", // Y=8
                    "                    I   I   ", // Y=9
                    "                    I   I   ", // Y=10
                    "                    IMMMI   ", // Y=11
                    "               E  DEIIIIIED ", // Y=12
                    "         YYY        I   I   ", // Y=13
                    "                    I   I   ", // Y=14
                    "                    I   I   ", // Y=15
                    "                    IMMMI   ", // Y=16
                    "                  DEIIIIIED ", // Y=17
                    "                    I   I   ", // Y=18
                    "                    I   I   ", // Y=19
                    "                    I   I   ", // Y=20
                    "         YYY        IMMMI   ", // Y=21
                    "               E  DEIIIIIED ", // Y=22
                    "                    I   I   ", // Y=23
                    "                    I   I   ", // Y=24
                    "                    I   I   ", // Y=25
                    "                    IMMMI   ", // Y=26
                    "                  DEIIIIIED ", // Y=27
                    "                    I   I   ", // Y=28
                    "                    I   I   ", // Y=29
                    "                    I   I   ", // Y=30
                    "                    IMMMI   ", // Y=31
                    "                  DEIIIIIED ", // Y=32
                    "                    I   I   ", // Y=33
                    "                    I   I   ", // Y=34
                    "                    I   I   ", // Y=35
                    "                    I   I   ", // Y=36
                    "                  DEIIIIIED ", // Y=37
                    "                    I   I   ", // Y=38
                    "                    I   I   ", // Y=39
                    "                    I   I   ", // Y=40
                    "                    I   I   ", // Y=41
                    "                  DEIIIIIED ", // Y=42
                    "                    I   I   ", // Y=43
                    "                    I   I   ", // Y=44
                    "                    I   I   ", // Y=45
                    "                    I   I   ", // Y=46
                    "                  DEIIIIIED ", // Y=47
                    "                    I   I   ", // Y=48
                    "                    I   I   ", // Y=49
                    "                    I   I   ", // Y=50
                    "                    I   I   ", // Y=51
                    "                  DEIIIIIED ", // Y=52
                    "                    I   I   ", // Y=53
                    "                    I   I   ", // Y=54
                    "                    I   I   ", // Y=55
                    "                    I   I   ", // Y=56
                    "                    I   I   ", // Y=57
                    "                     III    ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=15
                    "     AAAAA        AAAAAAAAA ", // Y=0
                    "      AAA         AIAMMMAIA ", // Y=1
                    "                  AIM   MIA ", // Y=2
                    "                   I     I  ", // Y=3
                    "                   I     I  ", // Y=4
                    "                   I     I  ", // Y=5
                    "                   IM   MI  ", // Y=6
                    "                  EIIFFFIIE ", // Y=7
                    "                   I     I  ", // Y=8
                    "                   I     I  ", // Y=9
                    "                   I     I  ", // Y=10
                    "                   IM   MI  ", // Y=11
                    "               E  EIIFFFIIE ", // Y=12
                    "                   I     I  ", // Y=13
                    "                   I     I  ", // Y=14
                    "                   I     I  ", // Y=15
                    "                   IM   MI  ", // Y=16
                    "                  EIIFFFIIE ", // Y=17
                    "                   I     I  ", // Y=18
                    "                   I     I  ", // Y=19
                    "                   I     I  ", // Y=20
                    "                   IM   MI  ", // Y=21
                    "               E  EIIFFFIIE ", // Y=22
                    "                   I     I  ", // Y=23
                    "                   I     I  ", // Y=24
                    "                   I     I  ", // Y=25
                    "                   IM   MI  ", // Y=26
                    "                  EIIFFFIIE ", // Y=27
                    "                   I     I  ", // Y=28
                    "                   I     I  ", // Y=29
                    "                   I     I  ", // Y=30
                    "                   IM   MI  ", // Y=31
                    "                  EIIFFFIIE ", // Y=32
                    "                   I     I  ", // Y=33
                    "                   I     I  ", // Y=34
                    "                   I     I  ", // Y=35
                    "                   IM   MI  ", // Y=36
                    "                  EIIFFFIIE ", // Y=37
                    "                   I     I  ", // Y=38
                    "                   I     I  ", // Y=39
                    "                   I     I  ", // Y=40
                    "                   IM   MI  ", // Y=41
                    "                  EIIFFFIIE ", // Y=42
                    "                   I     I  ", // Y=43
                    "                   I     I  ", // Y=44
                    "                   I     I  ", // Y=45
                    "                   IM   MI  ", // Y=46
                    "                  EIIFFFIIE ", // Y=47
                    "                   I     I  ", // Y=48
                    "                   I     I  ", // Y=49
                    "                   I     I  ", // Y=50
                    "                   IM   MI  ", // Y=51
                    "                  EIIFFFIIE ", // Y=52
                    "                   I     I  ", // Y=53
                    "                   I     I  ", // Y=54
                    "                   I     I  ", // Y=55
                    "                   I     I  ", // Y=56
                    "                   I     I  ", // Y=57
                    "                    I   I   ", // Y=58
                    "                     III    ", // Y=59
            },
            { // Z=16
                    "     AAAAA        AAAAAAAAA ", // Y=0
                    "      A^A         AIAMFMAIA ", // Y=1
                    "       ^          AIM F MIA ", // Y=2
                    "       ^           I  F  I  ", // Y=3
                    "       ^           I  F  I  ", // Y=4
                    "       ^           I  F  I  ", // Y=5
                    "       ^           IM F MI  ", // Y=6
                    "       ^          EIIFFFIIE ", // Y=7
                    "       ^           I  F  I  ", // Y=8
                    "       ^           I  F  I  ", // Y=9
                    "       ^           I  F  I  ", // Y=10
                    "       ^           IM F MI  ", // Y=11
                    "       ^       E  EIIFFFIIE ", // Y=12
                    "                   I  F  I  ", // Y=13
                    "                   I  F  I  ", // Y=14
                    "                   I  F  I  ", // Y=15
                    "                   IM F MI  ", // Y=16
                    "                  EIIFFFIIE ", // Y=17
                    "                   I  F  I  ", // Y=18
                    "                   I  F  I  ", // Y=19
                    "                   I  F  I  ", // Y=20
                    "                   IM F MI  ", // Y=21
                    "               E  EIIFFFIIE ", // Y=22
                    "                   I  F  I  ", // Y=23
                    "                   I  F  I  ", // Y=24
                    "                   I  F  I  ", // Y=25
                    "                   IM F MI  ", // Y=26
                    "                  EIIFFFIIE ", // Y=27
                    "                   I  F  I  ", // Y=28
                    "                   I  F  I  ", // Y=29
                    "                   I  F  I  ", // Y=30
                    "                   IM F MI  ", // Y=31
                    "                  EIIFFFIIE ", // Y=32
                    "                   I  F  I  ", // Y=33
                    "                   I  F  I  ", // Y=34
                    "                   I  F  I  ", // Y=35
                    "                   IM F MI  ", // Y=36
                    "                  EIIFFFIIE ", // Y=37
                    "                   I  F  I  ", // Y=38
                    "                   I  F  I  ", // Y=39
                    "                   I  F  I  ", // Y=40
                    "                   IM F MI  ", // Y=41
                    "                  EIIFFFIIE ", // Y=42
                    "                   I  F  I  ", // Y=43
                    "                   I  F  I  ", // Y=44
                    "                   I  F  I  ", // Y=45
                    "                   IM F MI  ", // Y=46
                    "                  EIIFFFIIE ", // Y=47
                    "                   I  F  I  ", // Y=48
                    "                   I  F  I  ", // Y=49
                    "                   I  F  I  ", // Y=50
                    "                   IM F MI  ", // Y=51
                    "                  EIIFFFIIE ", // Y=52
                    "                   I  F  I  ", // Y=53
                    "                   I  F  I  ", // Y=54
                    "                   I  F  I  ", // Y=55
                    "                   I  F  I  ", // Y=56
                    "                   I  F  I  ", // Y=57
                    "                    I   I   ", // Y=58
                    "                     IWI    ", // Y=59
            },
            { // Z=17
                    "     AAAAA        AAAAAAAAA ", // Y=0
                    "      AAA         AIAMMMAIA ", // Y=1
                    "                  AIM   MIA ", // Y=2
                    "                   I     I  ", // Y=3
                    "                   I     I  ", // Y=4
                    "                   I     I  ", // Y=5
                    "                   IM   MI  ", // Y=6
                    "                  EIIFFFIIE ", // Y=7
                    "                   I     I  ", // Y=8
                    "                   I     I  ", // Y=9
                    "                   I     I  ", // Y=10
                    "                   IM   MI  ", // Y=11
                    "       ^       E  EIIFFFIIE ", // Y=12
                    "       ^           I     I  ", // Y=13
                    "                   I     I  ", // Y=14
                    "                   I     I  ", // Y=15
                    "                   IM   MI  ", // Y=16
                    "                  EIIFFFIIE ", // Y=17
                    "                   I     I  ", // Y=18
                    "                   I     I  ", // Y=19
                    "                   I     I  ", // Y=20
                    "                   IM   MI  ", // Y=21
                    "               E  EIIFFFIIE ", // Y=22
                    "                   I     I  ", // Y=23
                    "                   I     I  ", // Y=24
                    "                   I     I  ", // Y=25
                    "                   IM   MI  ", // Y=26
                    "                  EIIFFFIIE ", // Y=27
                    "                   I     I  ", // Y=28
                    "                   I     I  ", // Y=29
                    "                   I     I  ", // Y=30
                    "                   IM   MI  ", // Y=31
                    "                  EIIFFFIIE ", // Y=32
                    "                   I     I  ", // Y=33
                    "                   I     I  ", // Y=34
                    "                   I     I  ", // Y=35
                    "                   IM   MI  ", // Y=36
                    "                  EIIFFFIIE ", // Y=37
                    "                   I     I  ", // Y=38
                    "                   I     I  ", // Y=39
                    "                   I     I  ", // Y=40
                    "                   IM   MI  ", // Y=41
                    "                  EIIFFFIIE ", // Y=42
                    "                   I     I  ", // Y=43
                    "                   I     I  ", // Y=44
                    "                   I     I  ", // Y=45
                    "                   IM   MI  ", // Y=46
                    "                  EIIFFFIIE ", // Y=47
                    "                   I     I  ", // Y=48
                    "                   I     I  ", // Y=49
                    "                   I     I  ", // Y=50
                    "                   IM   MI  ", // Y=51
                    "                  EIIFFFIIE ", // Y=52
                    "                   I     I  ", // Y=53
                    "                   I     I  ", // Y=54
                    "                   I     I  ", // Y=55
                    "                   I     I  ", // Y=56
                    "                   I     I  ", // Y=57
                    "                    I   I   ", // Y=58
                    "                     III    ", // Y=59
            },
            { // Z=18
                    "    AAAAAAA       AAAAAAAAA ", // Y=0
                    "      AAA         AAIAAAIAA ", // Y=1
                    "       Y          AAIMMMIAA ", // Y=2
                    "       Y            I   I   ", // Y=3
                    "       Y            I   I   ", // Y=4
                    "       Y            I   I   ", // Y=5
                    "       Y            IMMMI   ", // Y=6
                    "       Y          DEIIIIIED ", // Y=7
                    "     YYYYY          I   I   ", // Y=8
                    "       Y            I   I   ", // Y=9
                    "       Y            I   I   ", // Y=10
                    "       Y            IMMMI   ", // Y=11
                    "               E  DEIIIIIED ", // Y=12
                    "       ^            I   I   ", // Y=13
                    "       ^            I   I   ", // Y=14
                    "                    I   I   ", // Y=15
                    "                    IMMMI   ", // Y=16
                    "                  DEIIIIIED ", // Y=17
                    "                    I   I   ", // Y=18
                    "                    I   I   ", // Y=19
                    "                    I   I   ", // Y=20
                    "                    IMMMI   ", // Y=21
                    "               E  DEIIIIIED ", // Y=22
                    "                    I   I   ", // Y=23
                    "                    I   I   ", // Y=24
                    "                    I   I   ", // Y=25
                    "                    IMMMI   ", // Y=26
                    "                  DEIIIIIED ", // Y=27
                    "                    I   I   ", // Y=28
                    "                    I   I   ", // Y=29
                    "                    I   I   ", // Y=30
                    "                    IMMMI   ", // Y=31
                    "                  DEIIIIIED ", // Y=32
                    "                    I   I   ", // Y=33
                    "                    I   I   ", // Y=34
                    "                    I   I   ", // Y=35
                    "                    I   I   ", // Y=36
                    "                  DEIIIIIED ", // Y=37
                    "                    I   I   ", // Y=38
                    "                    I   I   ", // Y=39
                    "                    I   I   ", // Y=40
                    "                    I   I   ", // Y=41
                    "                  DEIIIIIED ", // Y=42
                    "                    I   I   ", // Y=43
                    "                    I   I   ", // Y=44
                    "                    I   I   ", // Y=45
                    "                    I   I   ", // Y=46
                    "                  DEIIIIIED ", // Y=47
                    "                    I   I   ", // Y=48
                    "                    I   I   ", // Y=49
                    "                    I   I   ", // Y=50
                    "                    I   I   ", // Y=51
                    "                  DEIIIIIED ", // Y=52
                    "                    I   I   ", // Y=53
                    "                    I   I   ", // Y=54
                    "                    I   I   ", // Y=55
                    "                    I   I   ", // Y=56
                    "                    I   I   ", // Y=57
                    "                     III    ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=19
                    "   AAAAAAAAA      AAAAAAAAA ", // Y=0
                    "    AACCCAA       AAAIIIAAA ", // Y=1
                    "      CCC         AAAIIIAAA ", // Y=2
                    "      CCC          J III J  ", // Y=3
                    "      CCC          J III J  ", // Y=4
                    "      CCC          J III J  ", // Y=5
                    "      CCC          J III J  ", // Y=6
                    "      CCC          JEIIIEJ  ", // Y=7
                    "   YY CCC YY       J III J  ", // Y=8
                    "      CCC          J III J  ", // Y=9
                    "      CCC          J III J  ", // Y=10
                    "                   J III J  ", // Y=11
                    "               E   JEIIIEJ  ", // Y=12
                    "                   J III J  ", // Y=13
                    "       ^           J III J  ", // Y=14
                    "       ^           J III J  ", // Y=15
                    "                   J III J  ", // Y=16
                    "                   JEIIIEJ  ", // Y=17
                    "                   J III J  ", // Y=18
                    "                   J III J  ", // Y=19
                    "                   J III J  ", // Y=20
                    "                   J III J  ", // Y=21
                    "               E   JEIIIEJ  ", // Y=22
                    "                   J III J  ", // Y=23
                    "                   J III J  ", // Y=24
                    "                   J III J  ", // Y=25
                    "                   J III J  ", // Y=26
                    "                   JEIIIEJ  ", // Y=27
                    "                   J III J  ", // Y=28
                    "                   J III J  ", // Y=29
                    "                   J III J  ", // Y=30
                    "                   J III J  ", // Y=31
                    "                   JEIIIEJ  ", // Y=32
                    "                   K III K  ", // Y=33
                    "                   K III K  ", // Y=34
                    "                   K III K  ", // Y=35
                    "                   K III K  ", // Y=36
                    "                   KEIIIEK  ", // Y=37
                    "                   K III K  ", // Y=38
                    "                   K III K  ", // Y=39
                    "                   K III K  ", // Y=40
                    "                   K III K  ", // Y=41
                    "                   KEIIIEK  ", // Y=42
                    "                   K III K  ", // Y=43
                    "                   K III K  ", // Y=44
                    "                   K III K  ", // Y=45
                    "                   K III K  ", // Y=46
                    "                   KEIIIEK  ", // Y=47
                    "                   K III K  ", // Y=48
                    "                   K III K  ", // Y=49
                    "                   K III K  ", // Y=50
                    "                   K III K  ", // Y=51
                    "                   KEIIIEK  ", // Y=52
                    "                     III    ", // Y=53
                    "                     III    ", // Y=54
                    "                     III    ", // Y=55
                    "                     III    ", // Y=56
                    "                     III    ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=20
                    "  AAAAAAAAAAA     AAAAAAAAA ", // Y=0
                    "   ACCCCCCCA      AAAAAAAAA ", // Y=1
                    "    CC   CC       AAAAAAAAA ", // Y=2
                    "    CC   CC                 ", // Y=3
                    "    CC   CC                 ", // Y=4
                    "    CC   CC                 ", // Y=5
                    "    CC   CC                 ", // Y=6
                    "    CC   CC         DEEED   ", // Y=7
                    "  Y CC   CC Y               ", // Y=8
                    "    CC   CC                 ", // Y=9
                    "    CC   CC                 ", // Y=10
                    "      CCC                   ", // Y=11
                    "               E    DEEED   ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "       ^                    ", // Y=15
                    "                            ", // Y=16
                    "                    DEEED   ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E    DEEED   ", // Y=22
                    "                            ", // Y=23
                    "                            ", // Y=24
                    "                            ", // Y=25
                    "                            ", // Y=26
                    "                    DEEED   ", // Y=27
                    "                            ", // Y=28
                    "                            ", // Y=29
                    "                            ", // Y=30
                    "                            ", // Y=31
                    "                    DEEED   ", // Y=32
                    "                            ", // Y=33
                    "                            ", // Y=34
                    "                            ", // Y=35
                    "                            ", // Y=36
                    "                    DEEED   ", // Y=37
                    "                            ", // Y=38
                    "                            ", // Y=39
                    "                            ", // Y=40
                    "                            ", // Y=41
                    "                    DEEED   ", // Y=42
                    "                            ", // Y=43
                    "                            ", // Y=44
                    "                            ", // Y=45
                    "                            ", // Y=46
                    "                    DEEED   ", // Y=47
                    "                            ", // Y=48
                    "                            ", // Y=49
                    "                            ", // Y=50
                    "                            ", // Y=51
                    "                    DEEED   ", // Y=52
                    "                            ", // Y=53
                    "                            ", // Y=54
                    "                            ", // Y=55
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=21
                    " AAAAAAAAAAAAA      AAAAA   ", // Y=0
                    "  ACCCCCCCCCA        AAA    ", // Y=1
                    "   C       C                ", // Y=2
                    "   C       C                ", // Y=3
                    "   C       C                ", // Y=4
                    "   C       C                ", // Y=5
                    "   C       C                ", // Y=6
                    "   C       C                ", // Y=7
                    " Y C       C Y              ", // Y=8
                    "   C       C                ", // Y=9
                    "   C       C                ", // Y=10
                    "    CCC CCC                 ", // Y=11
                    "       C       E            ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "       ^                    ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=22
                    "AAAAAAAAAAAAAAD D   AAAAA   ", // Y=0
                    " ACCCCCCCCCCCAD D    AAA    ", // Y=1
                    "  C         C D D           ", // Y=2
                    "  C         C D D           ", // Y=3
                    "  C         C D D           ", // Y=4
                    "  C         C D D           ", // Y=5
                    "  C         C D D           ", // Y=6
                    "  C         C D D           ", // Y=7
                    " YC         CYD D           ", // Y=8
                    "  C         C D D           ", // Y=9
                    "  C         C D D           ", // Y=10
                    "   CC     CC  DDD           ", // Y=11
                    "     CCCCC    DED           ", // Y=12
                    "              DDD           ", // Y=13
                    "              D D           ", // Y=14
                    "       ^      D D           ", // Y=15
                    "              D D           ", // Y=16
                    "              D D           ", // Y=17
                    "              D D           ", // Y=18
                    "              D D           ", // Y=19
                    "              D D           ", // Y=20
                    "              DDD           ", // Y=21
                    "              DED           ", // Y=22
                    "              DDD           ", // Y=23
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=23
                    "AAAAAAAAAAAAAAA     AAAAA   ", // Y=0
                    " ACCCCCCCCCCCA       AAA    ", // Y=1
                    "  C         C               ", // Y=2
                    "  C         C               ", // Y=3
                    "  C         C               ", // Y=4
                    "  C         C               ", // Y=5
                    "  C         C               ", // Y=6
                    "  C         C               ", // Y=7
                    "Y C         C Y             ", // Y=8
                    "  C         C               ", // Y=9
                    "  C         C               ", // Y=10
                    "   C       C                ", // Y=11
                    "    CCC CCC    E            ", // Y=12
                    "       C                    ", // Y=13
                    "                            ", // Y=14
                    "       ^                    ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=24
                    "AAAAAAAAAAAAAAA   AAAAAAAAAA", // Y=0
                    "ACCCCCCCCCCCCCA      AAA    ", // Y=1
                    " C           C              ", // Y=2
                    " C           C              ", // Y=3
                    " C           C     C     C  ", // Y=4
                    " C           C              ", // Y=5
                    " C           C              ", // Y=6
                    " C           C              ", // Y=7
                    "YC           CY             ", // Y=8
                    " C           C              ", // Y=9
                    " C           C              ", // Y=10
                    "  CC       CC               ", // Y=11
                    "    CC   CC    E            ", // Y=12
                    "      CCC                   ", // Y=13
                    "                            ", // Y=14
                    "       ^                    ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                   C     C  ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=25
                    "AAAAAAAAAAAAAAA   AAAAAAAAAA", // Y=0
                    "ACCCCCCCCCCCCCA    C AAA C  ", // Y=1
                    "YC           CY    C     C  ", // Y=2
                    "YC           CY    C     C  ", // Y=3
                    "YC           CY   CCCCCCCCC ", // Y=4
                    "YC           CY    CLLLLLC  ", // Y=5
                    "YC           CY    CLLLLLC  ", // Y=6
                    "YC           CY    CLLLLLC  ", // Y=7
                    "YC           CY    CLLLLLC  ", // Y=8
                    "YC           CY    CLLLLLC  ", // Y=9
                    "YC           CY    CLCCCLC  ", // Y=10
                    "Y C    ^    C Y    CLCCCLC  ", // Y=11
                    "   CC  ^  CC   E   CLCCCLC  ", // Y=12
                    "     CC^CC         CLLLLLC  ", // Y=13
                    "       ^           CLLLLLC  ", // Y=14
                    "       ^           CLLLLLC  ", // Y=15
                    "                   CLLLLLC  ", // Y=16
                    "                   CLLLLLC  ", // Y=17
                    "                  CCCCCCCCC ", // Y=18
                    "                   C     C  ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=26
                    "AAAAAAAAAAAAAAA   AAAAAAAAAA", // Y=0
                    "ACCCCCCCCCCCCCA   AAAAAAAAA ", // Y=1
                    " C           C              ", // Y=2
                    " C           C              ", // Y=3
                    " C           C     CCCCCCC  ", // Y=4
                    " C           C     L     L  ", // Y=5
                    " C           C     LMMMMML  ", // Y=6
                    " C           C     L     L  ", // Y=7
                    "YC           CY    LMMMMML  ", // Y=8
                    " C           C     L     L  ", // Y=9
                    " C           C     LMMMMML  ", // Y=10
                    "  CC       CC      L  F  L  ", // Y=11
                    "    CC   CC    E   LMMMMML  ", // Y=12
                    "      CCC          L     L  ", // Y=13
                    "                   LMMMMML  ", // Y=14
                    "                   L     L  ", // Y=15
                    "                   LMMMMML  ", // Y=16
                    "                   L     L  ", // Y=17
                    "                   CLLLLLC  ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=27
                    "AAAAAAAAAAAAAAA   AAA A AAAA", // Y=0
                    " ACCCCCCCCCCCA    AAARARAAA ", // Y=1
                    "  C         C        R R    ", // Y=2
                    "  C         C        R R    ", // Y=3
                    "  C         C      CCRCRCC  ", // Y=4
                    "  C         C      L J J L  ", // Y=5
                    "  C         C      LMJMJML  ", // Y=6
                    "  C         C      L J J L  ", // Y=7
                    "Y C         C Y    LMJMJML  ", // Y=8
                    "  C         C      L J J L  ", // Y=9
                    "  C         C      LMJMJML  ", // Y=10
                    "   C       C       L JFJ L  ", // Y=11
                    "    CCC CCC    E   LMJMJML  ", // Y=12
                    "       C           L J J L  ", // Y=13
                    "                   LMJMJML  ", // Y=14
                    "                   L J J L  ", // Y=15
                    "                   LMJMJML  ", // Y=16
                    "                   L J J L  ", // Y=17
                    "                   CLCCCLC  ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=28
                    "AAAAAAAAAAAAAA    AAAAAAAAAA", // Y=0
                    " ACCCCCCCCCCCA    AAAAAAAAA ", // Y=1
                    "  C         C               ", // Y=2
                    "  C         C               ", // Y=3
                    "  C         C      CCCCCCC  ", // Y=4
                    "  C         C      L     L  ", // Y=5
                    "  C         C      LMMMMML  ", // Y=6
                    "  C         C      L     L  ", // Y=7
                    " YC         CY     LMMMMML  ", // Y=8
                    "  C         C      L     L  ", // Y=9
                    "  C         C      LMMMMML  ", // Y=10
                    "   CC     CC       L  F  L  ", // Y=11
                    "     CCCCC     E   LMMMMML  ", // Y=12
                    "                   L     L  ", // Y=13
                    "                   LMMMMML  ", // Y=14
                    "                   L     L  ", // Y=15
                    "                   LMMMMML  ", // Y=16
                    "                   L     L  ", // Y=17
                    "                   CLCWCLC  ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=29
                    " AAAAAAAAAAAAA    AAA A AAAA", // Y=0
                    "  ACCCCCCCCCA     AAARARAAA ", // Y=1
                    "   C       C         R R    ", // Y=2
                    "   C       C         R R    ", // Y=3
                    "   C       C       CCRCTCC  ", // Y=4
                    "   C       C       L J J L  ", // Y=5
                    "   C       C       LMJMJML  ", // Y=6
                    "   C       C       L J J L  ", // Y=7
                    " Y C       C Y     LMJMJML  ", // Y=8
                    "   C       C       L J J L  ", // Y=9
                    "   C       C       LMJMJML  ", // Y=10
                    "    CCC CCC        L JFJ L  ", // Y=11
                    "       C       E   LMJMJML  ", // Y=12
                    "                   L J J L  ", // Y=13
                    "                   LMJMJML  ", // Y=14
                    "                   L J J L  ", // Y=15
                    "                   LMJMJML  ", // Y=16
                    "                   L J J L  ", // Y=17
                    "                   CLCCCLC  ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=30
                    "  AAAAAAAAAAA     AAAAAAAAAA", // Y=0
                    "   ACCCCCCCA      AAAAAAAAA ", // Y=1
                    "    CC   CC                 ", // Y=2
                    "    CC   CC                 ", // Y=3
                    "    CC   CC        CCCCCCC  ", // Y=4
                    "    CC   CC        L     L  ", // Y=5
                    "    CC   CC        LMMMMML  ", // Y=6
                    "    CC   CC        L     L  ", // Y=7
                    "  Y CC   CC Y      LMMMMML  ", // Y=8
                    "    CC   CC        L     L  ", // Y=9
                    "    CC   CC        LMMMMML  ", // Y=10
                    "      CCC          L  F  L  ", // Y=11
                    "               E   LMMMMML  ", // Y=12
                    "                   L     L  ", // Y=13
                    "                   LMMMMML  ", // Y=14
                    "                   L     L  ", // Y=15
                    "                   LMMMMML  ", // Y=16
                    "                   L     L  ", // Y=17
                    "                   CLLLLLC  ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=31
                    "   AAAAAAAAA      AAAAAAAAAA", // Y=0
                    "    AACCCAA        C AAA C  ", // Y=1
                    "      CCC          C     C  ", // Y=2
                    "      CCC          C     C  ", // Y=3
                    "      CCC         CCCCCCCCC ", // Y=4
                    "      CCC          CLLLLLC  ", // Y=5
                    "      CCC          CLLLLLC  ", // Y=6
                    "      CCC          CLLLLLC  ", // Y=7
                    "   YY CCC YY       CLLLLLC  ", // Y=8
                    "      CCC          CLLLLLC  ", // Y=9
                    "      CCC          CLCCCLC  ", // Y=10
                    "                   CLCFCLC  ", // Y=11
                    "               E   CLCCCLC  ", // Y=12
                    "                   CLLLLLC  ", // Y=13
                    "                   CLLLLLC  ", // Y=14
                    "                   CLLLLLC  ", // Y=15
                    "                   CLLLLLC  ", // Y=16
                    "                   CLLLLLC  ", // Y=17
                    "                  CCCCCCCCC ", // Y=18
                    "                   C     C  ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=32
                    "    AAAAAAA       AAAAAAAAAA", // Y=0
                    "      AAA            AAA    ", // Y=1
                    "       Y                    ", // Y=2
                    "       Y                    ", // Y=3
                    "       Y           C     C  ", // Y=4
                    "       Y                    ", // Y=5
                    "       Y                    ", // Y=6
                    "       Y                    ", // Y=7
                    "     YYYYY                  ", // Y=8
                    "       Y                    ", // Y=9
                    "       Y              L     ", // Y=10
                    "       Y             LFL    ", // Y=11
                    "               E      L     ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                   C     C  ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=33
                    "     AAAAA          AAAAA   ", // Y=0
                    "      AAA            AAA    ", // Y=1
                    "                            ", // Y=2
                    "                            ", // Y=3
                    "                            ", // Y=4
                    "                            ", // Y=5
                    "                            ", // Y=6
                    "                            ", // Y=7
                    "                            ", // Y=8
                    "                            ", // Y=9
                    "                      L     ", // Y=10
                    "                     LFL    ", // Y=11
                    "               E      L     ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=34
                    "     AAAAA    D D   AAAAA   ", // Y=0
                    "      AAA     D D    AAA    ", // Y=1
                    "              D D           ", // Y=2
                    "              D D           ", // Y=3
                    "              D D           ", // Y=4
                    "              D D           ", // Y=5
                    "              D D           ", // Y=6
                    "              D D           ", // Y=7
                    "              D D           ", // Y=8
                    "              D D           ", // Y=9
                    "              D D     L     ", // Y=10
                    "              DDD    LFL    ", // Y=11
                    "              DED     L     ", // Y=12
                    "              DDD           ", // Y=13
                    "              D D           ", // Y=14
                    "              D D           ", // Y=15
                    "              D D           ", // Y=16
                    "              D D           ", // Y=17
                    "              D D           ", // Y=18
                    "              D D           ", // Y=19
                    "              D D           ", // Y=20
                    "              DDD           ", // Y=21
                    "              DED           ", // Y=22
                    "              DDD           ", // Y=23
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=35
                    "     AAAAA          AAAAA   ", // Y=0
                    "      AAA            AAA    ", // Y=1
                    "                            ", // Y=2
                    "                            ", // Y=3
                    "                            ", // Y=4
                    "                            ", // Y=5
                    "                            ", // Y=6
                    "                            ", // Y=7
                    "                            ", // Y=8
                    "                            ", // Y=9
                    "                      L     ", // Y=10
                    "                     LFL    ", // Y=11
                    "               E      L     ", // Y=12
                    "                            ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=36
                    "     AAAAA        AAAAAAAAA ", // Y=0
                    "      AAA          AAAAAAA  ", // Y=1
                    "                            ", // Y=2
                    "                            ", // Y=3
                    "                            ", // Y=4
                    "                            ", // Y=5
                    "                            ", // Y=6
                    "                            ", // Y=7
                    "       C                    ", // Y=8
                    "      CCC            BBB    ", // Y=9
                    "     CCCCC          BBBBB   ", // Y=10
                    "      CCC           BBFBB   ", // Y=11
                    "       C       E    BBBBB   ", // Y=12
                    "                     BBB    ", // Y=13
                    "                            ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=37
                    "    AAAAAAA      AAAAAAAAAAA", // Y=0
                    "      AAA         AAAAAAAAA ", // Y=1
                    "                            ", // Y=2
                    "                            ", // Y=3
                    "                            ", // Y=4
                    "                            ", // Y=5
                    "       C                    ", // Y=6
                    "     CCCCC                  ", // Y=7
                    "    CCC CCC         BBBBB   ", // Y=8
                    "    CC   CC        BB   BB  ", // Y=9
                    "   CC     CC       B     B  ", // Y=10
                    "    CC   CC        B  F  B  ", // Y=11
                    "    CCC CCC    E   B  F  B  ", // Y=12
                    "     CCCCC         BB F BB  ", // Y=13
                    "       C            BBBBB   ", // Y=14
                    "                            ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=38
                    "  AAAAAAAAAAA    AAAAAAAAAAA", // Y=0
                    "    AAAAAAA      AAAAAAAAAAA", // Y=1
                    "                   J     J  ", // Y=2
                    "                   J     J  ", // Y=3
                    "                   J     J  ", // Y=4
                    "      CCC          J     J  ", // Y=5
                    "    CCCCCCC        J     J  ", // Y=6
                    "   CC     CC       JBBBBBJ  ", // Y=7
                    "   C       C       B     B  ", // Y=8
                    "  CC       CC     B  MMM  B ", // Y=9
                    "  C         C     B       B ", // Y=10
                    "  CC       CC     B   F   B ", // Y=11
                    "   C       C   E  B       B ", // Y=12
                    "   CC     CC      B       B ", // Y=13
                    "    CCCCCCC        B  F  B  ", // Y=14
                    "      CCC           BBBBB   ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=39
                    "  AAAAAAAAAAA    AAAAAAAAAAA", // Y=0
                    "  AAAAAAAAAAA    AAAAAAAAAAA", // Y=1
                    "                    J   J   ", // Y=2
                    "                    J   J   ", // Y=3
                    "                    J   J   ", // Y=4
                    "      CCC           J   J   ", // Y=5
                    "    CC   CC         JBBBJ   ", // Y=6
                    "   C       C       BB   BB  ", // Y=7
                    "   C       C      B       B ", // Y=8
                    "  C         C     B  M M  B ", // Y=9
                    "  C         C    B         B", // Y=10
                    "  C         C    B    F    B", // Y=11
                    "   C       C   E B         B", // Y=12
                    "   C       C      B       B ", // Y=13
                    "    CC   CC       B       B ", // Y=14
                    "      CCC          BB F BB  ", // Y=15
                    "                     BBB    ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=40
                    "  AAAAAAAAAAA    AAAAAAAAAAA", // Y=0
                    "  AAAAAAAAAAA    AAAAAAAAAAA", // Y=1
                    "  YYYY   YYYY         L     ", // Y=2
                    "  YYYY   YYYY         L     ", // Y=3
                    "  YYYY   YYYY         L     ", // Y=4
                    "  YYYYCCCYYYY         L     ", // Y=5
                    "  YYCC   CCYY       BBBBB   ", // Y=6
                    "  YC       CY      B MMM B  ", // Y=7
                    "   C  T T  C      B       B ", // Y=8
                    "  C         C    BBMMMMMMMBB", // Y=9
                    "  C         C    B         B", // Y=10
                    "  C         C    B    F    B", // Y=11
                    "   C       C   E B         B", // Y=12
                    "   C       C     BB       BB", // Y=13
                    "    CC F CC       B       B ", // Y=14
                    "      CCC          B  F  B  ", // Y=15
                    "                    BBBBB   ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=41
                    "  AAAAAAAAAAA    AAAAAAAAAAA", // Y=0
                    "  AAAAAAAAAAA    AAAAATAAAAA", // Y=1
                    "                     LTL    ", // Y=2
                    "                     LTL    ", // Y=3
                    "                     LTL    ", // Y=4
                    "      CCC            LTL    ", // Y=5
                    "    CC   CC         BBTBB   ", // Y=6
                    "   C  T T  C       B MTM B  ", // Y=7
                    "   C   TT  C      B   T   B ", // Y=8
                    "  C         C    BBM M M MBB", // Y=9
                    "  C   r     C    B         B", // Y=10
                    "  C   r     C    B FFFFFFF B", // Y=11
                    "   C  r    C   E BF   F   FB", // Y=12
                    "   C  F    C     BF   F   FB", // Y=13
                    "    CC F CC       BF  F  FB ", // Y=14
                    "      CCC          BFFFFFB  ", // Y=15
                    "                    BBWBB   ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=42
                    "  AAAAAAAAAAAAAA AAAAAAAAAAA", // Y=0
                    "  AAAAAAAAAAAAAA AAAAAAAAAAA", // Y=1
                    "                      L     ", // Y=2
                    "                      L     ", // Y=3
                    "                      L     ", // Y=4
                    "      CCC             L     ", // Y=5
                    "    CC   CC         BBBBB   ", // Y=6
                    "   C       C       B MMM B  ", // Y=7
                    "   C  T T  C      B       B ", // Y=8
                    "  C         C    BBMMMMMMMBB", // Y=9
                    "  C         C    B         B", // Y=10
                    "  C         C    B    F    B", // Y=11
                    "   C       C   E B         B", // Y=12
                    "   C       C     B         B", // Y=13
                    "    CC F CC       B       B ", // Y=14
                    "      CCC          B  F  B  ", // Y=15
                    "                    BBBBB   ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=43
                    "  AAAAAAAAAAAAXA AAAAAAAAAAA", // Y=0
                    "  AAAAAAAAAAAAXA AAAAAAAAAAA", // Y=1
                    "              X     J   J   ", // Y=2
                    "              X     J   J   ", // Y=3
                    "              X     J   J   ", // Y=4
                    "      CCC     X     J   J   ", // Y=5
                    "    CC   CC   X     JBBBJ   ", // Y=6
                    "   C       C  X    BB   BB  ", // Y=7
                    "   C  T T  C  X   B       B ", // Y=8
                    "  C         C X   B  M M  B ", // Y=9
                    "  C         C X  B         B", // Y=10
                    "  C     r   C X  B    F    B", // Y=11
                    "   C    r  C  XE B         B", // Y=12
                    "   C    F  C  X   B       B ", // Y=13
                    "    CC F CC  XX   B       B ", // Y=14
                    "      CXC   XX     BB F BB  ", // Y=15
                    "       X   XX        BBB    ", // Y=16
                    "       XXXXX                ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=44
                    "  AAAAAAAAAAAAAA AAAAAAAAAAA", // Y=0
                    "  AAAAAAAAAAAAAA AAAAAAAAAAA", // Y=1
                    "                   J     J  ", // Y=2
                    "                   J     J  ", // Y=3
                    "                   J     J  ", // Y=4
                    "      CCC          J     J  ", // Y=5
                    "    CC   CC        J     J  ", // Y=6
                    "   C       C       JBBBBBJ  ", // Y=7
                    "   C  T T  C       B     B  ", // Y=8
                    "  C         C     B  MMM  B ", // Y=9
                    "  C         C     B       B ", // Y=10
                    "  C         C     B   F   B ", // Y=11
                    "   C       C   E  B       B ", // Y=12
                    "   C       C      B       B ", // Y=13
                    "    CC F CC        B  F  B  ", // Y=14
                    "      CCC           BBBBB   ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=45
                    "  AAAAAAAAAAA    AAAAAAAAAAA", // Y=0
                    "  AAAAAAAAAAA     AAAAAAAAA ", // Y=1
                    "                            ", // Y=2
                    "                            ", // Y=3
                    "                            ", // Y=4
                    "      CCC                   ", // Y=5
                    "    CC   CC                 ", // Y=6
                    "   C       C                ", // Y=7
                    "   C  T T  C        BBBBB   ", // Y=8
                    "  C         C      BB   BB  ", // Y=9
                    "  C   r     C      B     B  ", // Y=10
                    "  C   r     C      B     B  ", // Y=11
                    "   C  r    C   E   B  F  B  ", // Y=12
                    "   C  F    C       BB F BB  ", // Y=13
                    "    CC F CC         BBBBB   ", // Y=14
                    "      CCC                   ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=46
                    "  AAAAAAAAAAA     AAAAAAAAA ", // Y=0
                    "  AAAAAAAAAAA      AAAAAAA  ", // Y=1
                    "  YYYY   YYYY               ", // Y=2
                    "  YYYY   YYYY               ", // Y=3
                    "  YYYY   YYYY               ", // Y=4
                    "  YYYYCCCYYYY               ", // Y=5
                    "  YYCC   CCYY               ", // Y=6
                    "  YC       CY               ", // Y=7
                    "   C  T T  C                ", // Y=8
                    "  C         C        BBB    ", // Y=9
                    "  C         C       BLLLB   ", // Y=10
                    "  C         C       BLLLB   ", // Y=11
                    "   C       C   E    BLLLB   ", // Y=12
                    "   C       C         BBB    ", // Y=13
                    "    CC F CC                 ", // Y=14
                    "      CCC                   ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=47
                    "  AAAAAAAAAAA       AAAAA   ", // Y=0
                    "  AAAAAAAAAAA        AAA    ", // Y=1
                    "                            ", // Y=2
                    "                            ", // Y=3
                    "                            ", // Y=4
                    "      CCC                   ", // Y=5
                    "    CC   CC                 ", // Y=6
                    "   C       C                ", // Y=7
                    "   C  T T  C                ", // Y=8
                    "  C         C               ", // Y=9
                    "  C         C               ", // Y=10
                    "  C     r   C               ", // Y=11
                    "   C    r  C   E            ", // Y=12
                    "   C    F  C                ", // Y=13
                    "    CC F CC                 ", // Y=14
                    "      CCC                   ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=48
                    "  AAAAAAAAAAA D D   AAAAA   ", // Y=0
                    "  AAAAAAAAAAA D D    AAA    ", // Y=1
                    "              D D           ", // Y=2
                    "              D D           ", // Y=3
                    "              D D           ", // Y=4
                    "      CCC     D D           ", // Y=5
                    "    CC   CC   D D           ", // Y=6
                    "   C       C  D D           ", // Y=7
                    "   C  T T  C  D D           ", // Y=8
                    "  C         C D D           ", // Y=9
                    "  C         C D D           ", // Y=10
                    "  C         C DDD           ", // Y=11
                    "   C       C  DED           ", // Y=12
                    "   C       C  DDD           ", // Y=13
                    "    CC F CC   D D           ", // Y=14
                    "      CCC     D D           ", // Y=15
                    "              D D           ", // Y=16
                    "              D D           ", // Y=17
                    "              D D           ", // Y=18
                    "              D D           ", // Y=19
                    "              D D           ", // Y=20
                    "              DDD           ", // Y=21
                    "              DED           ", // Y=22
                    "              DDD           ", // Y=23
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=49
                    "  AAAAAAAAAAA       AAAAA   ", // Y=0
                    "  AAAAAAAAAAA        AAA    ", // Y=1
                    "                            ", // Y=2
                    "                            ", // Y=3
                    "                            ", // Y=4
                    "      CCC                   ", // Y=5
                    "    CC   CC                 ", // Y=6
                    "   C       C                ", // Y=7
                    "   C  T T  C                ", // Y=8
                    "  C         C               ", // Y=9
                    "  C   r     C               ", // Y=10
                    "  C   r     C               ", // Y=11
                    "   C  r    C   E            ", // Y=12
                    "   C  F    C                ", // Y=13
                    "    CC F CC                 ", // Y=14
                    "      CCC                   ", // Y=15
                    "                            ", // Y=16
                    "                            ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E            ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=50
                    "  AAAAAAAAAAA     AAAAAAAAA ", // Y=0
                    "  AAAAATAAAAA     AAAAAAAAA ", // Y=1
                    "       T          AAAAAAAAA ", // Y=2
                    "       T                    ", // Y=3
                    "       T                    ", // Y=4
                    "      CTC                   ", // Y=5
                    "    CC T CC                 ", // Y=6
                    "   C   T   C        DEEED   ", // Y=7
                    "   C  T T  C                ", // Y=8
                    "  C         C               ", // Y=9
                    "  C         C               ", // Y=10
                    "  C         C               ", // Y=11
                    "   C       C   E    DEEED   ", // Y=12
                    "   C       C                ", // Y=13
                    "    CC F CC                 ", // Y=14
                    "      CCC                   ", // Y=15
                    "                            ", // Y=16
                    "                    DEEED   ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "               E    DEEED   ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=51
                    "  AAAAAAAAAAA     AAAAAAAAA ", // Y=0
                    "  AAAAAAAAAAA     AAAIIIAAA ", // Y=1
                    "                  AAAIIIAAA ", // Y=2
                    "                   K III K  ", // Y=3
                    "                   K III K  ", // Y=4
                    "      CCC          K III K  ", // Y=5
                    "    CC   CC        K III K  ", // Y=6
                    "   C       C       KEIIIEK  ", // Y=7
                    "   C  T T  C       K III K  ", // Y=8
                    "  C         C      K III K  ", // Y=9
                    "  C         C      K III K  ", // Y=10
                    "  C     r   C      K III K  ", // Y=11
                    "   C    r  C   E   KEIIIEK  ", // Y=12
                    "   C    F  C       K III K  ", // Y=13
                    "    CC F CC        K III K  ", // Y=14
                    "      CCC          K III K  ", // Y=15
                    "                   K III K  ", // Y=16
                    "                   KEIIIEK  ", // Y=17
                    "                   N III N  ", // Y=18
                    "                   N III N  ", // Y=19
                    "                   N III N  ", // Y=20
                    "                   N III N  ", // Y=21
                    "               E   NEIIIEN  ", // Y=22
                    "                   N III N  ", // Y=23
                    "                   N III N  ", // Y=24
                    "                     III    ", // Y=25
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=52
                    "  AAAAAAAAAAAAAAAAAAAAAAAAA ", // Y=0
                    "  AAAAAAAAAAA     AAIAAAIAA ", // Y=1
                    "                  AAIMMMIAA ", // Y=2
                    "                    I   I   ", // Y=3
                    "                    I   I   ", // Y=4
                    "      CCC           I   I   ", // Y=5
                    "    CC   CC         I   I   ", // Y=6
                    "   C       C      DEIIIIIED ", // Y=7
                    "   C  T T  C        I   I   ", // Y=8
                    "  C         C       I   I   ", // Y=9
                    "  C         C       I   I   ", // Y=10
                    "  C         C       IMMMI   ", // Y=11
                    "   C       C   E  DEIIIIIED ", // Y=12
                    "   C       C        I   I   ", // Y=13
                    "    CC F CC         I   I   ", // Y=14
                    "      CCC           I   I   ", // Y=15
                    "                    IMMMI   ", // Y=16
                    "                  DEIIIIIED ", // Y=17
                    "                    I   I   ", // Y=18
                    "                    I   I   ", // Y=19
                    "                    I   I   ", // Y=20
                    "                    IMMMI   ", // Y=21
                    "               E  DEIIIIIED ", // Y=22
                    "                    I   I   ", // Y=23
                    "                    I   I   ", // Y=24
                    "                    I   I   ", // Y=25
                    "                     III    ", // Y=26
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=53
                    "  AAAAAAAAAAAAAAAAAAAAAAAAA ", // Y=0
                    "  AAAAAAAAAAAAAAAAAIAMRMAIA ", // Y=1
                    "                  AIM   MIA ", // Y=2
                    "                   I     I  ", // Y=3
                    "                   I     I  ", // Y=4
                    "      CCC          I     I  ", // Y=5
                    "    CC   CC        I     I  ", // Y=6
                    "   C       C      EIIRRRIIE ", // Y=7
                    "   C  T T  C       I     I  ", // Y=8
                    "  C         C      I     I  ", // Y=9
                    "  C   r     C      I     I  ", // Y=10
                    "  C   r     C      IM   MI  ", // Y=11
                    "   C  r    C   E  EIIRRRIIE ", // Y=12
                    "   C  F    C       I     I  ", // Y=13
                    "    CC F CC        I     I  ", // Y=14
                    "      CCC          I     I  ", // Y=15
                    "                   I     I  ", // Y=16
                    "                  EIIRRRIIE ", // Y=17
                    "                   I     I  ", // Y=18
                    "                   I     I  ", // Y=19
                    "                   I     I  ", // Y=20
                    "                   I     I  ", // Y=21
                    "               E  EIIRRRIIE ", // Y=22
                    "                   I     I  ", // Y=23
                    "                   I     I  ", // Y=24
                    "                   I     I  ", // Y=25
                    "                    I   I   ", // Y=26
                    "                     III    ", // Y=27
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=54
                    "  AAAAAAAAAAAAAAAAAAAAAAAAA ", // Y=0
                    "  AAAAAAAAAAAAAAAAAIARRRAIA ", // Y=1
                    "  YYYY   YYYY     AIM R MIA ", // Y=2
                    "  YYYY   YYYY      I  R  I  ", // Y=3
                    "  YYYY   YYYY      I  R  I  ", // Y=4
                    "  YYYYCCCYYYY      I  R  I  ", // Y=5
                    "  YYCC   CCYY      I  R  I  ", // Y=6
                    "  YC       CY     EIIRRRIIE ", // Y=7
                    "   C  T T  C       I  R  I  ", // Y=8
                    "  C         C      I  R  I  ", // Y=9
                    "  C         C      I  R  I  ", // Y=10
                    "  C         C      IM R MI  ", // Y=11
                    "   C       C   EEEEIIRRRIIE ", // Y=12
                    "   C       C       I  R  I  ", // Y=13
                    "    CC F CC        I  R  I  ", // Y=14
                    "      CCC          I  R  I  ", // Y=15
                    "                   I  R  I  ", // Y=16
                    "                  EIIRRRIIE ", // Y=17
                    "                   I  R  I  ", // Y=18
                    "                   I  R  I  ", // Y=19
                    "                   I  R  I  ", // Y=20
                    "                   I  R  I  ", // Y=21
                    "               EEEEIIRRRIIE ", // Y=22
                    "                   I  R  I  ", // Y=23
                    "                   I  R  I  ", // Y=24
                    "                   I  R  I  ", // Y=25
                    "                    I   I   ", // Y=26
                    "                     IWI    ", // Y=27
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=55
                    "  AAAAAAAAAAAAAAAAAAAAAAAAA ", // Y=0
                    "  AAAAAAAAAAAAAAAAAIAMRMAIA ", // Y=1
                    "                  AIM   MIA ", // Y=2
                    "                   I     I  ", // Y=3
                    "                   I     I  ", // Y=4
                    "      CCC          I     I  ", // Y=5
                    "    CC   CC        I     I  ", // Y=6
                    "   C       C      EIIRRRIIE ", // Y=7
                    "   C  T T  C       I     I  ", // Y=8
                    "  C         C      I     I  ", // Y=9
                    "  C         C      I     I  ", // Y=10
                    "  C     r   C      IM   MI  ", // Y=11
                    "   C    r  C      EIIRRRIIE ", // Y=12
                    "   C    F  C       I     I  ", // Y=13
                    "    CC F CC        I     I  ", // Y=14
                    "      CCC          I     I  ", // Y=15
                    "                   I     I  ", // Y=16
                    "                  EIIRRRIIE ", // Y=17
                    "                   I     I  ", // Y=18
                    "                   I     I  ", // Y=19
                    "                   I     I  ", // Y=20
                    "                   I     I  ", // Y=21
                    "                  EIIRRRIIE ", // Y=22
                    "                   I     I  ", // Y=23
                    "                   I     I  ", // Y=24
                    "                   I     I  ", // Y=25
                    "                    I   I   ", // Y=26
                    "                     III    ", // Y=27
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=56
                    "  AAAAAAAAAAAAAAAAAAAAAAAAA ", // Y=0
                    "  AAAAA]AAAAA     AAIAAAIAA ", // Y=1
                    "       ]          AAIMMMIAA ", // Y=2
                    "       ]            I   I   ", // Y=3
                    "       ]            I   I   ", // Y=4
                    "      C]C           I   I   ", // Y=5
                    "    CC ] CC         I   I   ", // Y=6
                    "   C       C      DEIIIIIED ", // Y=7
                    "   C  T T  C        I   I   ", // Y=8
                    "  C         C       I   I   ", // Y=9
                    "  C         C       I   I   ", // Y=10
                    "  C         C       IMMMI   ", // Y=11
                    "   C       C      DEIIIIIED ", // Y=12
                    "   C       C        I   I   ", // Y=13
                    "    CC F CC         I   I   ", // Y=14
                    "      CCC           I   I   ", // Y=15
                    "                    IMMMI   ", // Y=16
                    "                  DEIIIIIED ", // Y=17
                    "                    I   I   ", // Y=18
                    "                    I   I   ", // Y=19
                    "                    I   I   ", // Y=20
                    "                    IMMMI   ", // Y=21
                    "                  DEIIIIIED ", // Y=22
                    "                    I   I   ", // Y=23
                    "                    I   I   ", // Y=24
                    "                    I   I   ", // Y=25
                    "                     III    ", // Y=26
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=57
                    "  AAAAAAAAAAA     AAAAAAAAA ", // Y=0
                    "  AAAAAAAAAAA     AAAIIIAAA ", // Y=1
                    "                  AAAIIIAAA ", // Y=2
                    "                   K III K  ", // Y=3
                    "                   K III K  ", // Y=4
                    "      CCC          K III K  ", // Y=5
                    "    CC   CC        K III K  ", // Y=6
                    "   C       C       KEIIIEK  ", // Y=7
                    "   C  T T  C       K III K  ", // Y=8
                    "  C         C      K III K  ", // Y=9
                    "  C   r     C      K III K  ", // Y=10
                    "  C   r     C      K III K  ", // Y=11
                    "   C  r    C       KEIIIEK  ", // Y=12
                    "   C  F    C       K III K  ", // Y=13
                    "    CC F CC        K III K  ", // Y=14
                    "      CCC          K III K  ", // Y=15
                    "                   K III K  ", // Y=16
                    "                   KEIIIEK  ", // Y=17
                    "                   N III N  ", // Y=18
                    "                   N III N  ", // Y=19
                    "                   N III N  ", // Y=20
                    "                   N III N  ", // Y=21
                    "                   NEIIIEN  ", // Y=22
                    "                   N III N  ", // Y=23
                    "                   N III N  ", // Y=24
                    "                     III    ", // Y=25
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=58
                    "  AAAAAAAAAAAAAA  AAAAAAAAA ", // Y=0
                    "  AAAAAAAAAAAAAA  AAAAAAAAA ", // Y=1
                    "                  AAAAAAAAA ", // Y=2
                    "                            ", // Y=3
                    "                            ", // Y=4
                    "      CCC                   ", // Y=5
                    "    CC   CC                 ", // Y=6
                    "   C       C        DEEED   ", // Y=7
                    "   C  T T  C                ", // Y=8
                    "  C         C               ", // Y=9
                    "  C         C               ", // Y=10
                    "  C         C               ", // Y=11
                    "   C       C        DEEED   ", // Y=12
                    "   C       C                ", // Y=13
                    "    CC F CC                 ", // Y=14
                    "      CCC                   ", // Y=15
                    "                            ", // Y=16
                    "                    DEEED   ", // Y=17
                    "                            ", // Y=18
                    "                            ", // Y=19
                    "                            ", // Y=20
                    "                            ", // Y=21
                    "                    DEEED   ", // Y=22
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=59
                    "  AAAAAAAAAAAAXA     AAA    ", // Y=0
                    "  AAAAAAAAAAAAXA            ", // Y=1
                    "              X             ", // Y=2
                    "              X             ", // Y=3
                    "              X             ", // Y=4
                    "      CCC     X             ", // Y=5
                    "    CC   CC   X             ", // Y=6
                    "   C  T T  C  X             ", // Y=7
                    "   C   T   C  X             ", // Y=8
                    "  C         C X             ", // Y=9
                    "  C         C X             ", // Y=10
                    "  C     r   C X             ", // Y=11
                    "   C    r  C  X             ", // Y=12
                    "   C    F  C  X             ", // Y=13
                    "    CC F CC  XX             ", // Y=14
                    "      CXC   XX              ", // Y=15
                    "       X   XX               ", // Y=16
                    "       XXXXX                ", // Y=17
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=60
                    "  AAAAAAAAAAAAAA  AAAAAAAAA ", // Y=0
                    "  AAAAAAAAAAAAAA   GOOOOOG  ", // Y=1
                    "  YYYY   YYYY      GOPPPOG  ", // Y=2
                    "  YYYY   YYYY      GOPPPOG  ", // Y=3
                    "  YYYY   YYYY      GOPPPOG  ", // Y=4
                    "  YYYYCCCYYYY      GOOOOOG  ", // Y=5
                    "  YYCC   CCYY      GGGGGGG  ", // Y=6
                    "  YC       CY               ", // Y=7
                    "   C  T T  C                ", // Y=8
                    "  C         C               ", // Y=9
                    "  C         C               ", // Y=10
                    "  C         C               ", // Y=11
                    "   C       C                ", // Y=12
                    "   C       C                ", // Y=13
                    "    CC F CC                 ", // Y=14
                    "      CCC                   ", // Y=15
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=61
                    "  AAAAAAAAAAA     AAAAAAAAA ", // Y=0
                    "  AAAAAAAAAAA      HOOOOOH  ", // Y=1
                    "                   HOSSSOH  ", // Y=2
                    "                    OS SO   ", // Y=3
                    "                    PSSSP   ", // Y=4
                    "      CCC          HOOOOOH  ", // Y=5
                    "    CC   CC        G     G  ", // Y=6
                    "   C       C                ", // Y=7
                    "   C       C                ", // Y=8
                    "  C         C               ", // Y=9
                    "  C         C               ", // Y=10
                    "  C         C               ", // Y=11
                    "   C       C                ", // Y=12
                    "   C       C                ", // Y=13
                    "    CC   CC                 ", // Y=14
                    "      CCC                   ", // Y=15
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=62
                    "  AAAAAAAAAAA     AAAAAAAAA ", // Y=0
                    "    AAAAAAA        HOOOOOH  ", // Y=1
                    "                   HOSSSOH  ", // Y=2
                    "                    OS SO   ", // Y=3
                    "                    PSSSP   ", // Y=4
                    "      CCC          HOOOOOH  ", // Y=5
                    "    CCC CCC        G     G  ", // Y=6
                    "   CC     CC                ", // Y=7
                    "   C       C                ", // Y=8
                    "  CC       CC               ", // Y=9
                    "  C         C               ", // Y=10
                    "  CC       CC               ", // Y=11
                    "   C       C                ", // Y=12
                    "   CC     CC                ", // Y=13
                    "    CCC CCC                 ", // Y=14
                    "      CCC                   ", // Y=15
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=63
                    "    AAAAAAA       AAAAAAAAA ", // Y=0
                    "      AAA          HOOOOOH  ", // Y=1
                    "                   HOSSSOH  ", // Y=2
                    "                    OS SO   ", // Y=3
                    "                    PSSSP   ", // Y=4
                    "                   HOOOOOH  ", // Y=5
                    "       C           G     G  ", // Y=6
                    "     CCCCC                  ", // Y=7
                    "    CCC CCC                 ", // Y=8
                    "    CC   CC                 ", // Y=9
                    "   CC     CC                ", // Y=10
                    "    CC   CC                 ", // Y=11
                    "    CCC CCC                 ", // Y=12
                    "     CCCCC                  ", // Y=13
                    "       C                    ", // Y=14
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
            { // Z=64
                    "     AAAAA        AAAAAAAAA ", // Y=0
                    "      AAA          GOOOOOG  ", // Y=1
                    "                   GOHHHOG  ", // Y=2
                    "                   GOHUHOG  ", // Y=3
                    "                   GOHHHOG  ", // Y=4
                    "                   GOOOOOG  ", // Y=5
                    "                   GGGGGGG  ", // Y=6
                    "                            ", // Y=7
                    "       C                    ", // Y=8
                    "      CCC                   ", // Y=9
                    "     CCCCC                  ", // Y=10
                    "      CCC                   ", // Y=11
                    "       C                    ", // Y=12
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
                    "                            ", // Y=56
                    "                            ", // Y=57
                    "                            ", // Y=58
                    "                            ", // Y=59
            },
    };
    /** 结构数据里允许出现的符号全集（必须与 {@link #build} 里的 {@code .where(...)} 一一对应）。 */
    /** GTO 模块并入后新增的结构符号（数字/标点，都有对应 .where 绑定）。 */
    private static final String UPGRADED_EXTRA_SYMBOLS = "!#$%&()*0123456789=?~@";

    private static final String DECLARED_SYMBOLS = " ABCDEFGHIJKLMNOPRSTUVWXYZ[]^r";

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
        TraceabilityPredicate hatchFace = blocksOf("gtceu", "high_power_casing").setMinGlobalLimited(5)
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
                .where('U', Predicates.controller(Predicates.blocks(definition.get())))
                .where('A', blocksOf("gtceu", "stress_proof_casing"))
                .where('B', Predicates.blocks(PetrochemBlocks.SUPERCRITICAL_TURBINE_CASING.get()))
                .where('C', blocksOf("gtceu", "clean_machine_casing"))
                .where('D', frame("steel"))
                .where('E', Predicates.blocks(WoodFactoryBlocks.OIL_GAS_TRANSPORTATION_PIPE_CASING.get()))
                .where('F', blocksOf("gtceu", "tungstensteel_pipe_casing"))
                .where('G', Predicates.blocks(PetrochemBlocks.PIKYONIUM_MACHINE_CASING.get()))
                .where('H', hatchFace)
                .where('I', blocksOf("gtceu", "watertight_casing"))
                .where('J', frame("naquadah_alloy"))
                .where('K', frame("tungsten"))
                .where('L', blocksOf("gtceu", "fusion_glass"))
                .where('M', Predicates.heatingCoils())
                .where('N', frame("stainless_steel"))
                .where('O', blocksOf("gtceu", "computer_casing"))
                .where('P', blocksOf("gtceu", "computer_heat_vent"))
                .where('R', blocksOf("gtceu", "titanium_pipe_casing"))
                .where('S', blocksOf("gtceu", "high_power_casing"))
                .where('T', blocksOf("gtceu", "steel_pipe_casing"))
                .where('V', Predicates.blocks(PetrochemBlocks.CHEMICAL_GRADE_GLASS.get()))
                .where('W', Predicates.abilities(PartAbility.MUFFLER))
                .where('X', Predicates.blocks(WoodFactoryBlocks.CHEMICAL_CORROSION_RESISTANT_PIPE_CASING.get()))
                .where('Y', blocksOf("gtceu", "robust_machine_casing"))
                .where('Z', blocksOf("gtlcore", "iridium_casing"))
                .where('[', Predicates.blocks(PetrochemBlocks.IRIDIUM_PIPE_CASING.get()))
                .where('r', blocksOf("gtceu", "electrolytic_cell"))
                .where(']', blocksOf("gtceu", "ptfe_pipe_casing"))
                .where('^', Predicates.blocks(WoodFactoryBlocks.HIGH_PRESSURE_PIPE_CASING.get()))
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
            throw new IllegalStateException("集成石油化工处理工厂 " + tag + "结构：共 " + slices.length + " 片，应为 " + length);
        }
        String allowed = DECLARED_SYMBOLS + extraSymbols;
        for (int z = 0; z < slices.length; z++) {
            String[] rows = slices[z];
            if (rows.length != HEIGHT) {
                throw new IllegalStateException("集成石油化工处理工厂 " + tag + "结构：Z=" + z + " 只有 " + rows.length
                        + " 行，应为 " + HEIGHT);
            }
            for (int y = 0; y < rows.length; y++) {
                String row = rows[y];
                if (row.length() != width) {
                    throw new IllegalStateException("集成石油化工处理工厂 " + tag + "结构：SLICES[" + z + "][" + y + "] 行长 "
                            + row.length() + "，应为 " + width);
                }
                for (int x = 0; x < row.length(); x++) {
                    char c = row.charAt(x);
                    if (c != ' ' && allowed.indexOf(c) < 0) {
                        throw new IllegalStateException(String.format(
                                "集成石油化工处理工厂 " + tag + "结构：符号 '%c'（0x%02X）在 SLICES[%d][%d] 第 %d 列出现，"
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
            GTLModular.LOGGER.warn("[gtlmodular] 集成石油化工处理工厂：方块 {}/{} 不存在，该符号退回 any()",
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
            GTLModular.LOGGER.warn("[gtlmodular] 集成石油化工处理工厂：GTBlocks.LAMPS 为空，灯位退回 any()");
            return Predicates.any();
        }
        return predicate;
    }

    /** GTO 可选模块并入后的切片：下列数字/符号格为二级结构格。 */
    private static final String[][] UPGRADED_SLICES = {
            {
                    "AAAAAAAAAAAAAAA 44AAAAAAAAA44",
                    "AAAAAAAAAAAAAAA 44AAAAAAAAA44",
                    " ***            44AAAAAAAAA44",
                    " *)*                         ",
                    " *)*                         ",
                    " *)*     YYY                 ",
                    " *)*                         ",
                    " *)*                DEEED    ",
                    " *)*                         ",
                    " *)*                         ",
                    " ***                         ",
                    "                             ",
                    "                    DEEED    ",
                    "         YYY                 ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "         YYY                 ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAAAA 44AAAAAAAAA44",
                    "AAAAAAAAAAAAAAA 44AAAIIIAAA44",
                    "****     Z Z J  44AAAIIIAAA44",
                    "* ()     Z Z J   6 J III J 6 ",
                    "* ()     Z Z J     J III J   ",
                    "* ()    YZZZYJ     J III J   ",
                    "* ()     ZLZ J     J III J   ",
                    "* ()     ZLZ J     JEIIIEJ   ",
                    "* ()     ZLZ J     J III J   ",
                    "* ()     ZLZ J     J III J   ",
                    "****     ZLZ J     J III J   ",
                    "         ZLZ J   6 J III J 6 ",
                    "         ZLZ J   6 JEIIIEJ 6 ",
                    "        YZZZYJ   6 J III J 6 ",
                    "         ZLZ J     J III J   ",
                    "         ZLZ J     J III J   ",
                    "         ZLZ J     J III J   ",
                    "         ZZZ J     JEIIIEJ   ",
                    "         ZZZ J     J III J   ",
                    "         ZZZ J     J III J   ",
                    "         ZZZ J     J III J   ",
                    "        YZZZYJ   6 J III J 6 ",
                    "         ZZZ J   6 JEIIIEJ 6 ",
                    "             J   6 J III J 6 ",
                    "                   J III J   ",
                    "                   J III J   ",
                    "                   J III J   ",
                    "                   JEIIIEJ   ",
                    "                   J III J   ",
                    "                   J III J   ",
                    "                   J III J   ",
                    "                   J III J   ",
                    "                   JEIIIEJ   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   KEIIIEK   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   KEIIIEK   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   KEIIIEK   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   KEIIIEK   ",
                    "                     III     ",
                    "                     III     ",
                    "                     III     ",
                    "                     III     ",
                    "                     III     ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAAAA 44AAAAAAAAA44",
                    "AAAAAAAAAAAAAAA 4#AAIAAAIAA#4",
                    "****            4#AAIMMMIAA#4",
                    ")( *            6#  I   I  #6",
                    ")( *            6#  I   I  #6",
                    ")( *   YZZZZZY  6#  I   I  #6",
                    ")( *    ZMMMZ   6#  IMMMI  #6",
                    ")( *    Z   Z   6#DEIIIIIED#6",
                    ")( *    ZMMMZ   6#  I   I  #6",
                    ")( *    Z   Z   6#  I   I  #6",
                    "****    ZMMMZ   6#  I   I  #6",
                    "        Z   Z   6#  IMMMI  #6",
                    "        Z   Z   6#DEIIIIIED#6",
                    "       YZ   ZY  6#  I   I  #6",
                    "        Z   Z   6#  I   I  #6",
                    "        ZMMMZ   6#  I   I  #6",
                    "        Z   Z   6#  IMMMI  #6",
                    "        Z   Z   6#DEIIIIIED#6",
                    "        Z   Z   6#  I   I  #6",
                    "        Z   Z   6#  I   I  #6",
                    "        ZMMMZ   6#  I   I  #6",
                    "       YZ   ZY  6#  IMMMI  #6",
                    "        Z   Z   6#DEIIIIIED#6",
                    "         ZZZ    6#  I   I  #6",
                    "                6#  I   I###6",
                    "                6#  I   I    ",
                    "                6#  IMMMI    ",
                    "                6#DEIIIIIED  ",
                    "                6#  I   I    ",
                    "                6#  I   I    ",
                    "                6###I   I    ",
                    "                    IMMMI    ",
                    "                  DEIIIIIED  ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                  DEIIIIIED  ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                  DEIIIIIED  ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                  DEIIIIIED  ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                  DEIIIIIED  ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                     III     ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAAAA 44AAAAAAAAA44",
                    "AAAAAAAAAAARAAA 44AIAMMMAIA44",
                    "***   JZ   R Z  44AIM   MIA44",
                    "*)*   JZ   R Z   6 I     I 6 ",
                    "*)*   JZ   R Z     I     I   ",
                    "*)*YYYJZZZZRZZY    I     I   ",
                    "*)*   JZM  RMZ     IM   MI   ",
                    "*)*   JZ   R Z    EIIFFFIIE  ",
                    "*)*   JZM  RMZ     I     I   ",
                    "*)*   JZ   R Z     I     I   ",
                    "***   JZM  RMZ     I     I   ",
                    "      JZ   R Z   6 IM   MI 6 ",
                    "      JZ   R Z   6EIIFFFIIE6 ",
                    "   YYYJZ   R ZY  6 I     I 6 ",
                    "      JZ   R Z     I     I   ",
                    "      JZM  RMZ     I     I   ",
                    "      JZ   R Z     IM   MI   ",
                    "      JZ   R Z    EIIFFFIIE  ",
                    "      JZ   R Z     I     I   ",
                    "      JZ   R Z     I     I   ",
                    "      JZM  RMZ     I     I   ",
                    "   YYYJZ   R ZY  6 IM   MI 6 ",
                    "      JZ   R Z   6EIIFFFIIE6 ",
                    "      J Z   Z    6 I     I 6 ",
                    "         ZZZ       I     I   ",
                    "                   I     I   ",
                    "                   IM   MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   IM   MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   IM   MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   IM   MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   IM   MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   IM   MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                    I   I    ",
                    "                     III     ",
            },
            {
                    "AAAAAAAAAAAAAAA 44AAAAAAAAA44",
                    "AAAAAAAAAAAAAAA 44AIAMFMAIA44",
                    " J Z Z Z        44AIM F MIA44",
                    " J Z Z Z         6 I  F  I 6 ",
                    " J Z Z Z           I  F  I   ",
                    " JYZZZYZZZZZZZY    I  F  I   ",
                    " J ZZZ ZM   MZ     IM F MI   ",
                    " J ZZZ Z     Z    EIIFFFIIE  ",
                    " J ZZZ ZM [ MZ     I  F  I   ",
                    " J ZZZ Z  [  Z     I  F  I   ",
                    " J ZZZ ZM [ MZ     I  F  I   ",
                    " J ZZZ Z     Z     IM F MI   ",
                    " J ZZZ Z     Z    EIIFFFIIE  ",
                    " JYZZZYZ     ZY    I  F  I   ",
                    " J ZZZ Z  [  Z     I  F  I   ",
                    " J ZZZ ZM [ MZ     I  F  I   ",
                    " J ZZZ Z  [  Z     IM F MI   ",
                    " J ZZZ Z     Z    EIIFFFIIE  ",
                    " J ZZZ Z     Z     I  F  I   ",
                    " J ZZZ Z  [  Z     I  F  I   ",
                    " J ZZZ ZM [ MZ     I  F  I   ",
                    " JYZZZYZ  [  ZY    IM F MI   ",
                    " J ZZZ Z     Z    EIIFFFIIE  ",
                    " J      Z   Z      I  F  I   ",
                    "         ZWZ       I  F  I   ",
                    "                   I  F  I   ",
                    "                   IM F MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   IM F MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   IM F MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   IM F MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   IM F MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   IM F MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                    I   I    ",
                    "                     IWI     ",
            },
            {
                    "AAAAAAAAAAAAAAA 44AAAAAAAAA44",
                    "AAAAAAAAAFAAAAA 44AIAMMMAIA44",
                    "       Z F   Z  44AIM   MIA44",
                    "       Z F   Z   6 I     I 6 ",
                    "       Z F   Z     I     I   ",
                    " YZZZZZZZFZZZZY    I     I   ",
                    "  ZMMMZZMF  MZ     IM   MI   ",
                    "  Z   ZZ F   Z    EIIFFFIIE  ",
                    "  ZMMMZZMF  MZ     I     I   ",
                    "  Z   ZZ F   Z     I     I   ",
                    "  ZMMMZZMF  MZ     I     I   ",
                    "  Z   ZZ F   Z   6 IM   MI 6 ",
                    "  Z   ZZ F   Z   6EIIFFFIIE6 ",
                    " YZ   ZZ F   ZY  6 I     I 6 ",
                    "  Z   ZZ F   Z     I     I   ",
                    "  ZMMMZZMF  MZ     I     I   ",
                    "  Z   ZZ F   Z     IM   MI   ",
                    "  Z   ZZ F   Z    EIIFFFIIE  ",
                    "  Z   ZZ F   Z     I     I   ",
                    "  Z   ZZ F   Z     I     I   ",
                    "  ZMMMZZMF  MZ     I     I   ",
                    " YZ   ZZ F   ZY  6 IM   MI   ",
                    "  Z   ZZ F   Z   6EIIFFFIIE  ",
                    "   ZZZ  Z   Z    6 I     I   ",
                    "         ZZZ       I     I   ",
                    "                   I     I   ",
                    "                   IM   MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                 6 IM   MI   ",
                    "                 6EIIFFFIIE  ",
                    "                 6 I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   IM   MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   IM   MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   IM   MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   IM   MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                    I   I    ",
                    "                     III     ",
            },
            {
                    "AAAAAAAAAAAAAAA 44AAAAAAAAA44",
                    "AAAAAAAAAAAAAAA 4#AAIAAAIAA#4",
                    " Z R            4#AAIMMMIAA#4",
                    " Z R            6#  I   I  #6",
                    " Z R            6#  I   I  #6",
                    "YZZRZZZZZZZZZY  6#  I   I  #6",
                    " ZMR  MZZMMMZ   6#  IMMMI  #6",
                    " Z R   ZZ   Z   6#DEIIIIIED#6",
                    " ZMR  MZZMMMZ   6#  I   I  #6",
                    " Z R   ZZ   Z   6#  I   I  #6",
                    " ZMR  MZZMMMZ   6#  I   I  #6",
                    " Z R   ZZ   Z   6#  IMMMI  #6",
                    " Z R   ZZ   Z   6#DEIIIIIED#6",
                    "YZ R   ZZ   ZY  6#  I   I  #6",
                    " Z R   ZZ   Z   6#  I   I  #6",
                    " ZMR  MZZMMMZ   6#  I   I###6",
                    " Z R   ZZ   Z   6#  IMMMI    ",
                    " Z R   ZZ   Z   6#DEIIIIIED  ",
                    " Z R   ZZ   Z   6#  I   I    ",
                    " Z R   ZZ   Z   6#  I   I    ",
                    " ZMR  MZZMMMZ   6#  I   I    ",
                    "YZ R   ZZ   ZY  6#  IMMMI    ",
                    " Z R   ZZ   Z   6#DEIIIIIED  ",
                    "  Z  FZ  ZZZ    6#  I   I    ",
                    "   ZZZ          6#  I   I    ",
                    "                6#  I   I    ",
                    "                6#  IMMMI    ",
                    "                6#DEIIIIIED  ",
                    "                6#  I   I    ",
                    "                6#  I   I    ",
                    "                6#  I   I    ",
                    "                6#  IMMMI    ",
                    "                6#DEIIIIIED  ",
                    "                6#  I   I    ",
                    "                6#  I   I    ",
                    "                6#  I   I    ",
                    "                6#  I   I    ",
                    "                6#DEIIIIIED  ",
                    "                6#  I   I    ",
                    "                6#  I   I    ",
                    "                6###I   I    ",
                    "                    I   I    ",
                    "                  DEIIIIIED  ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                  DEIIIIIED  ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                  DEIIIIIED  ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                     III     ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAAAA 44AAAAAAAAA44",
                    "AAAAAAAAAAAAAAA 44AAAIIIAAA44",
                    "         ZZZJ   44AAAIIIAAA44",
                    "         ZZZJ    6 J III J 6 ",
                    "         ZZZJ      J III J   ",
                    "YZZZZZZZ ZZZJ      J III J   ",
                    " LM   MZ ZZZJ      J III J   ",
                    " L     Z ZZZJ      JEIIIEJ   ",
                    " LM [ MZ ZZZJ      J III J   ",
                    " L  [  Z ZZZJ      J IVI J   ",
                    " LM [ MZ ZZZJ      J IVI J   ",
                    " L     Z ZZZJ    6 J III J 6 ",
                    " L     Z ZZZJ    6 JEIIIEJ 6 ",
                    "YZ     Z ZZZJ    6 J III J 6 ",
                    " L  [  Z ZZZJ      J III J   ",
                    " LM [ MZ ZZZJ      J III J   ",
                    " L  [  Z ZZZJ      J III J   ",
                    " Z     Z ZZZJ      JEIIIEJ   ",
                    " Z     Z ZZZJ      J III J   ",
                    " Z  [  Z ZZZJ      J IVI J   ",
                    " ZM [ MZ ZZZJ      J IVI J   ",
                    "YZ  [  Z ZZZJ    6 J III J   ",
                    " Z     Z ZZZJ    6 JEIIIEJ   ",
                    "  Z   Z     J    6 J III J   ",
                    "   ZWZ             J III J   ",
                    "                   J III J   ",
                    "                   J III J   ",
                    "                   JEIIIEJ   ",
                    "                   J III J   ",
                    "                   J IVI J   ",
                    "                   J IVI J   ",
                    "                 6 J III J   ",
                    "                 6 JEIIIEJ   ",
                    "                 6 K III K   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   KEIIIEK   ",
                    "                   K III K   ",
                    "                   K IVI K   ",
                    "                   K IVI K   ",
                    "                   K III K   ",
                    "                   KEIIIEK   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   KEIIIEK   ",
                    "                   K III K   ",
                    "                   K IVI K   ",
                    "                   K IVI K   ",
                    "                   K III K   ",
                    "                   KEIIIEK   ",
                    "                     III     ",
                    "                     III     ",
                    "                     III     ",
                    "                     III     ",
                    "                     III     ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAAAA 44AAAAAAAAA44",
                    "AAAAAAAAAAAAAAA 44AAAAAAAAA44",
                    " Z   F          44AAAAAAAAA44",
                    " Z   F                       ",
                    " Z   F                       ",
                    "YZZZZFZZZZZZZY               ",
                    " ZM  FMZZMMMZ          N     ",
                    " Z   F ZZ   Z       DEEED    ",
                    " ZM  FMZZMMMZ                ",
                    " Z   F ZZ   Z                ",
                    " ZM  FMZZMMMZ                ",
                    " Z   F ZZ   Z        N       ",
                    " Z   F ZZ   Z       DEEED    ",
                    "YZ   F ZZ   ZY               ",
                    " Z   F ZZ   Z                ",
                    " ZM  FMZZMMMZ                ",
                    " Z   F ZZ   Z          N     ",
                    " Z   F ZZ   Z       DEEED    ",
                    " Z   F ZZ   Z                ",
                    " Z   F ZZ   Z                ",
                    " ZM  FMZZMMMZ                ",
                    "YZ   F ZZ   ZY       N       ",
                    " Z   F ZZ   Z       DEEED    ",
                    "  Z   Z  ZZZ                 ",
                    "   ZZZ                       ",
                    "                             ",
                    "                       N     ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                     N       ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                       N     ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                     N       ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                       N     ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                     N       ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAAAA 44AAAAAAAAA44",
                    "AAAAAAAAAAAFAAA 44  AAAAA  44",
                    "       Z   F Z  44   AAA   44",
                    "       Z   F Z               ",
                    "       Z   F Z               ",
                    " YZZZZZZZZZFZZY              ",
                    "  ZMMMZZM  FMZ         N     ",
                    "  Z   ZZ   F Z        E      ",
                    "  ZMMMZZM  FMZ               ",
                    "  Z   ZZ   F Z               ",
                    "  ZMMMZZM  FMZ               ",
                    "  Z   ZZ   F Z       N       ",
                    "  Z   ZZ   F Z        E      ",
                    " YZ   ZZ   F ZY              ",
                    "  Z   ZZ   F Z               ",
                    "  ZMMMZZM  FMZ               ",
                    "  Z   ZZ   F Z         N     ",
                    "  Z   ZZ   F Z        E      ",
                    "  Z   ZZ   F Z               ",
                    "  Z   ZZ   F Z               ",
                    "  ZMMMZZM  FMZ               ",
                    " YZ   ZZ   F ZY      N       ",
                    "  Z   ZZ   F Z        E      ",
                    "   ZZZ  Z   Z                ",
                    "         ZZZ                 ",
                    "                             ",
                    "                       N     ",
                    "                      E      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                     N       ",
                    "                      E      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                       N     ",
                    "                      E      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                     N       ",
                    "                      E      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                       N     ",
                    "                      E      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                     N       ",
                    "                      E      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAAAA 44AAAAAAAAA44",
                    "AAAAAAAAAAAAAAA 44  AAAAA  44",
                    " J Z Z Z        44   AAA   44",
                    " J Z Z Z                     ",
                    " J Z Z Z                     ",
                    " JYZZZYZZZZZZZY        N     ",
                    " J ZZZ ZM   MZ               ",
                    " J ZZZ Z     Z        E      ",
                    " J ZZZ ZM [ MZ               ",
                    " J ZZZ Z  [  Z               ",
                    " J ZZZ ZM [ MZ       N       ",
                    " J ZZZ Z     Z               ",
                    " J ZZZ Z     Z EEEEEEEE      ",
                    " JYZZZYZ     ZY              ",
                    " J ZZZ Z  [  Z               ",
                    " J ZZZ ZM [ MZ         N     ",
                    " J ZZZ Z  [  Z               ",
                    " J ZZZ Z     Z        E      ",
                    " J ZZZ Z     Z               ",
                    " J ZZZ Z  [  Z               ",
                    " J ZZZ ZM [ MZ       N       ",
                    " JYZZZYZ  [  ZY              ",
                    " J ZZZ Z     Z EEEEEEEE      ",
                    " J      Z   Z                ",
                    "         ZWZ                 ",
                    "                       N     ",
                    "                             ",
                    "                      E      ",
                    "                             ",
                    "                             ",
                    "                     N       ",
                    "                             ",
                    "                      E      ",
                    "                             ",
                    "                             ",
                    "                       N     ",
                    "                             ",
                    "                      E      ",
                    "                             ",
                    "                             ",
                    "                     N       ",
                    "                             ",
                    "                      E      ",
                    "                             ",
                    "                             ",
                    "                       N     ",
                    "                             ",
                    "                      E      ",
                    "                             ",
                    "                             ",
                    "                     N       ",
                    "                             ",
                    "                      E      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAAAA 44AAAAAAAAA44",
                    "AAAAAAAAARAAAAA 44  AAAAA  44",
                    "***   JZ R   Z  44   AAA   44",
                    "*)*   JZ R   Z               ",
                    "*)*   JZ R   Z         N     ",
                    "*)*YYYJZZRZZZZY              ",
                    "*)*   JZMR  MZ               ",
                    "*)*   JZ R   Z        E      ",
                    "*)*   JZMR  MZ               ",
                    "*)*   JZ R   Z       N       ",
                    "***   JZMR  MZ               ",
                    "      JZ R   Z               ",
                    "      JZ R   Z E      E      ",
                    "   YYYJZ R   ZY              ",
                    "      JZ R   Z         N     ",
                    "      JZMR  MZ               ",
                    "      JZ R   Z               ",
                    "      JZ R   Z        E      ",
                    "      JZ R   Z               ",
                    "      JZ R   Z       N       ",
                    "      JZMR  MZ               ",
                    "   YYYJZ R   ZY              ",
                    "      JZ R   Z E      E      ",
                    "      J Z   Z                ",
                    "         ZZZ           N     ",
                    "                             ",
                    "                             ",
                    "                      E      ",
                    "                             ",
                    "                     N       ",
                    "                             ",
                    "                             ",
                    "                      E      ",
                    "                             ",
                    "                       N     ",
                    "                             ",
                    "                             ",
                    "                      E      ",
                    "                             ",
                    "                     N       ",
                    "                             ",
                    "                             ",
                    "                      E      ",
                    "                             ",
                    "                       N     ",
                    "                             ",
                    "                             ",
                    "                      E      ",
                    "                             ",
                    "                     N       ",
                    "                             ",
                    "                             ",
                    "                      E      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAAAA 44AAAAAAAAA44",
                    "AAAAAAAAAAAAAAA 44AAAAAAAAA44",
                    "****            44AAAAAAAAA44",
                    ")( *                   N     ",
                    ")( *                         ",
                    ")( *   YZZZZZY               ",
                    ")( *    ZMMMZ                ",
                    ")( *    Z   Z       DEEED    ",
                    ")( *    ZMMMZ        N       ",
                    ")( *    Z   Z                ",
                    "****    ZMMMZ                ",
                    "        Z   Z                ",
                    "        Z   Z  E    DEEED    ",
                    "       YZ   ZY         N     ",
                    "        Z   Z                ",
                    "        ZMMMZ                ",
                    "        Z   Z                ",
                    "        Z   Z       DEEED    ",
                    "        Z   Z        N       ",
                    "        Z   Z                ",
                    "        ZMMMZ                ",
                    "       YZ   ZY               ",
                    "        Z   Z  E    DEEED    ",
                    "         ZZZ           N     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                    DEEED    ",
                    "                     N       ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                    DEEED    ",
                    "                       N     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                    DEEED    ",
                    "                     N       ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                    DEEED    ",
                    "                       N     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                    DEEED    ",
                    "                     N       ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAAAA 44AAAAAAAAA44",
                    "AAAAAAAAAAAAAAA 44AAAIIIAAA44",
                    "****     Z Z J  44AAAIIIAAA44",
                    "* ()     Z Z J   6 J III J 6 ",
                    "* ()     Z Z J     J III J   ",
                    "* ()    YZZZYJ     J III J   ",
                    "* ()     ZLZ J   6 J III J 6 ",
                    "* ()     ZLZ J   6 JEIIIEJ 6 ",
                    "* ()     ZLZ J   6 J III J 6 ",
                    "* ()     ZLZ J     J IVI J   ",
                    "****     ZLZ J     J IVI J   ",
                    "         ZLZ J     J III J   ",
                    "         ZLZ J E   JEIIIEJ   ",
                    "        YZZZYJ     J III J   ",
                    "         ZLZ J     J III J   ",
                    "         ZLZ J     J III J   ",
                    "         ZLZ J   6 J III J 6 ",
                    "         ZZZ J   6 JEIIIEJ 6 ",
                    "         ZZZ J   6 J III J 6 ",
                    "         ZZZ J     J IVI J   ",
                    "         ZZZ J     J IVI J   ",
                    "        YZZZYJ     J III J   ",
                    "         ZZZ J E   JEIIIEJ   ",
                    "             J     J III J   ",
                    "                   J III J   ",
                    "                   J III J   ",
                    "                 6 J III J 6 ",
                    "                 6 JEIIIEJ 6 ",
                    "                 6 J III J 6 ",
                    "                   J IVI J   ",
                    "                   J IVI J   ",
                    "                   J III J   ",
                    "                   JEIIIEJ   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                 6 K III K   ",
                    "                 6 KEIIIEK   ",
                    "                 6 K III K   ",
                    "                   K IVI K   ",
                    "                   K IVI K   ",
                    "                   K III K   ",
                    "                   KEIIIEK   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                 6 K III K   ",
                    "                 6 KEIIIEK   ",
                    "                 6 K III K   ",
                    "                   K IVI K   ",
                    "                   K IVI K   ",
                    "                   K III K   ",
                    "                   KEIIIEK   ",
                    "                     III     ",
                    "                     III     ",
                    "                     III     ",
                    "                     III     ",
                    "                     III     ",
                    "                             ",
                    "                             ",
            },
            {
                    "AAAAAAAAAAAAAAA 44AAAAAAAAA44",
                    "AAAAAAAAAAAAAAA 4#AAIAAAIAA#4",
                    " ***            4#AAIMMMIAA#4",
                    " *)*            6#  I   I  #6",
                    " *)*            6#  I   I  #6",
                    " *)*     YYY    6#  I   I  #6",
                    " *)*            6#  IMMMI  #6",
                    " *)*            6#DEIIIIIED#6",
                    " *)*            6#  I   I  #6",
                    " *)*            6#  I   I  #6",
                    " ***            6#  I   I  #6",
                    "                6#  IMMMI  #6",
                    "               E6#DEIIIIIED#6",
                    "         YYY    6#  I   I  #6",
                    "                6#  I   I  #6",
                    "                6#  I   I  #6",
                    "                6#  IMMMI  #6",
                    "                6#DEIIIIIED#6",
                    "                6#  I   I  #6",
                    "                6#  I   I  #6",
                    "                6#  I   I  #6",
                    "         YYY    6#  IMMMI  #6",
                    "               E6#DEIIIIIED#6",
                    "                6#  I   I  #6",
                    "                6#  I   I  #6",
                    "                6#  I   I  #6",
                    "                6#  IMMMI  #6",
                    "                6#DEIIIIIED#6",
                    "                6#  I   I  #6",
                    "                6#  I   I  #6",
                    "                6#  I   I  #6",
                    "                6#  IMMMI  #6",
                    "                6#DEIIIIIED#6",
                    "                6#  I   I  #6",
                    "                6#  I   I  #6",
                    "                6#  I   I###6",
                    "                6#  I   I    ",
                    "                6#DEIIIIIED  ",
                    "                6#  I   I    ",
                    "                6#  I   I    ",
                    "                6#  I   I    ",
                    "                6#  I   I    ",
                    "                6#DEIIIIIED  ",
                    "                6#  I   I    ",
                    "                6#  I   I    ",
                    "                6#  I   I    ",
                    "                6#  I   I    ",
                    "                6#DEIIIIIED  ",
                    "                6#  I   I    ",
                    "                6#  I   I    ",
                    "                6###I   I    ",
                    "                    I   I    ",
                    "                  DEIIIIIED  ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                     III     ",
                    "                             ",
            },
            {
                    "     AAAAA      44AAAAAAAAA44",
                    "      AAA       44AIAMMMAIA44",
                    "                44AIM   MIA44",
                    "                 6 I     I 6 ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                 6 IM   MI 6 ",
                    "                 6EIIFFFIIE6 ",
                    "                 6 I     I 6 ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   IM   MI   ",
                    "               E  EIIFFFIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                 6 IM   MI 6 ",
                    "                 6EIIFFFIIE6 ",
                    "                 6 I     I 6 ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   IM   MI   ",
                    "               E  EIIFFFIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                 6 IM   MI 6 ",
                    "                 6EIIFFFIIE6 ",
                    "                 6 I     I 6 ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   IM   MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                 6 IM   MI   ",
                    "                 6EIIFFFIIE  ",
                    "                 6 I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   IM   MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                 6 IM   MI   ",
                    "                 6EIIFFFIIE  ",
                    "                 6 I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   IM   MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                    I   I    ",
                    "                     III     ",
            },
            {
                    "     AAAAA      44AAAAAAAAA44",
                    "      A^A       44AIAMFMAIA44",
                    "       ^        44AIM F MIA44",
                    "       ^         6 I  F  I 6 ",
                    "       ^           I  F  I   ",
                    "       ^           I  F  I   ",
                    "       ^           IM F MI   ",
                    "       ^          EIIFFFIIE  ",
                    "       ^           I  F  I   ",
                    "       ^           I  F  I   ",
                    "       ^           I  F  I   ",
                    "       ^           IM F MI   ",
                    "       ^       E  EIIFFFIIE  ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   IM F MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   IM F MI   ",
                    "               E  EIIFFFIIE  ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   IM F MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   IM F MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   IM F MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   IM F MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   IM F MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   IM F MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                   I  F  I   ",
                    "                    I   I    ",
                    "                     IWI     ",
            },
            {
                    "     AAAAA      44AAAAAAAAA44",
                    "      AAA       44AIAMMMAIA44",
                    "                44AIM   MIA44",
                    "                 6 I     I 6 ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                 6 IM   MI 6 ",
                    "                 6EIIFFFIIE6 ",
                    "                 6 I     I 6 ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   IM   MI   ",
                    "       ^       E  EIIFFFIIE  ",
                    "       ^           I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                 6 IM   MI 6 ",
                    "                 6EIIFFFIIE6 ",
                    "                 6 I     I 6 ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   IM   MI   ",
                    "               E  EIIFFFIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   IM   MI 6 ",
                    "                  EIIFFFIIE6 ",
                    "                   I     I 6 ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   IM   MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   IM   MI 6 ",
                    "                  EIIFFFIIE6 ",
                    "                   I     I 6 ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   IM   MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   IM   MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   IM   MI   ",
                    "                  EIIFFFIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                    I   I    ",
                    "                     III     ",
            },
            {
                    "    AAAAAAA     44AAAAAAAAA44",
                    "      AAA       4#AAIAAAIAA#4",
                    "       Y        4#AAIMMMIAA#4",
                    "       Y        6#  I   I  #6",
                    "       Y        6#  I   I  #6",
                    "       Y        6#  I   I  #6",
                    "       Y        6#  IMMMI  #6",
                    "       Y        6#DEIIIIIED#6",
                    "     YYYYY      6#  I   I  #6",
                    "       Y        6#  I   I  #6",
                    "       Y        6#  I   I  #6",
                    "       Y        6#  IMMMI  #6",
                    "               E6#DEIIIIIED#6",
                    "       ^        6#  I   I  #6",
                    "       ^        6#  I   I  #6",
                    "                6#  I   I  #6",
                    "                6#  IMMMI  #6",
                    "                6#DEIIIIIED#6",
                    "                6#  I   I  #6",
                    "                6#  I   I  #6",
                    "                6#  I   I  #6",
                    "                6#  IMMMI  #6",
                    "               E6#DEIIIIIED#6",
                    "                6#  I   I  #6",
                    "                6#  I   I  #6",
                    "                6###I   I  #6",
                    "                    IMMMI  #6",
                    "                  DEIIIIIED#6",
                    "                    I   I  #6",
                    "                    I   I  #6",
                    "                    I   I  #6",
                    "                    IMMMI  #6",
                    "                  DEIIIIIED#6",
                    "                    I   I  #6",
                    "                    I   I  #6",
                    "                    I   I  #6",
                    "                    I   I  #6",
                    "                  DEIIIIIED#6",
                    "                    I   I  #6",
                    "                    I   I  #6",
                    "                    I   I  #6",
                    "                    I   I  #6",
                    "                  DEIIIIIED#6",
                    "                    I   I  #6",
                    "                    I   I  #6",
                    "                    I   I###6",
                    "                    I   I    ",
                    "                  DEIIIIIED  ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                  DEIIIIIED  ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                     III     ",
                    "                             ",
            },
            {
                    "   AAAAAAAAA    44AAAAAAAAA44",
                    "    AACCCAA     44AAAIIIAAA44",
                    "      CCC       44AAAIIIAAA44",
                    "      CCC        6 J III J 6 ",
                    "      CCC          J III J   ",
                    "      CCC          J III J   ",
                    "      CCC        6 J III J 6 ",
                    "      CCC        6 JEIIIEJ 6 ",
                    "   YY CCC YY     6 J III J 6 ",
                    "      CCC          J III J   ",
                    "      CCC          J III J   ",
                    "                   J III J   ",
                    "               E   JEIIIEJ   ",
                    "                   J III J   ",
                    "       ^           J III J   ",
                    "       ^           J III J   ",
                    "                 6 J III J 6 ",
                    "                 6 JEIIIEJ 6 ",
                    "                 6 J III J 6 ",
                    "                   J III J   ",
                    "                   J III J   ",
                    "                   J III J   ",
                    "               E   JEIIIEJ   ",
                    "                   J III J   ",
                    "                   J III J   ",
                    "                   J III J   ",
                    "                   J III J 6 ",
                    "                   JEIIIEJ 6 ",
                    "                   J III J 6 ",
                    "                   J III J   ",
                    "                   J III J   ",
                    "                   J III J   ",
                    "                   JEIIIEJ   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   K III K 6 ",
                    "                   KEIIIEK 6 ",
                    "                   K III K 6 ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   KEIIIEK   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   KEIIIEK   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   K III K   ",
                    "                   KEIIIEK   ",
                    "                     III     ",
                    "                     III     ",
                    "                     III     ",
                    "                     III     ",
                    "                     III     ",
                    "                             ",
                    "                             ",
            },
            {
                    "  AAAAAAAAAAA   44AAAAAAAAA44",
                    "   ACCCCCCCA    44AAAAAAAAA44",
                    "    CC   CC     44AAAAAAAAA44",
                    "    CC   CC                  ",
                    "    CC   CC                  ",
                    "    CC   CC                  ",
                    "    CC   CC                  ",
                    "    CC   CC         DEEED    ",
                    "  Y CC   CC Y                ",
                    "    CC   CC                  ",
                    "    CC   CC                  ",
                    "      CCC                    ",
                    "               E    DEEED    ",
                    "                             ",
                    "                             ",
                    "       ^                     ",
                    "                             ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
            },
            {
                    " AAAAAAAAAAAAA      AAAAA    ",
                    "  ACCCCCCCCCA        AAA     ",
                    "   C       C                 ",
                    "   C       C                 ",
                    "   C       C                 ",
                    "   C       C                 ",
                    "   C       C                 ",
                    "   C       C                 ",
                    " Y C       C Y               ",
                    "   C       C                 ",
                    "   C       C                 ",
                    "    CCC CCC                  ",
                    "       C       E             ",
                    "                             ",
                    "                             ",
                    "       ^                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "AAAAAAAAAAAAAAD D   AAAAA    ",
                    " ACCCCCCCCCCCAD D    AAA     ",
                    "  C         C D D            ",
                    "  C         C D D            ",
                    "  C         C D D            ",
                    "  C         C D D            ",
                    "  C         C D D            ",
                    "  C         C D D            ",
                    " YC         CYD D            ",
                    "  C         C D D            ",
                    "  C         C D D            ",
                    "   CC     CC  DDD            ",
                    "     CCCCC    DED            ",
                    "              DDD            ",
                    "              D D            ",
                    "       ^      D D            ",
                    "              D D            ",
                    "              D D            ",
                    "              D D            ",
                    "              D D            ",
                    "              D D            ",
                    "              DDD            ",
                    "              DED            ",
                    "              DDD            ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "AAAAAAAAAAAAAAA     AAAAA    ",
                    " ACCCCCCCCCCCA       AAA     ",
                    "  C         C                ",
                    "  C         C                ",
                    "  C         C                ",
                    "  C         C                ",
                    "  C         C                ",
                    "  C         C                ",
                    "Y C         C Y              ",
                    "  C         C                ",
                    "  C         C                ",
                    "   C       C                 ",
                    "    CCC CCC    E             ",
                    "       C                     ",
                    "                             ",
                    "       ^                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "AAAAAAAAAAAAAAA   AAAAAAAAAA ",
                    "ACCCCCCCCCCCCCA      AAA     ",
                    " C           C               ",
                    " C           C               ",
                    " C           C     C     C   ",
                    " C           C               ",
                    " C           C               ",
                    " C           C               ",
                    "YC           CY              ",
                    " C           C               ",
                    " C           C               ",
                    "  CC       CC                ",
                    "    CC   CC    E             ",
                    "      CCC                    ",
                    "                             ",
                    "       ^                     ",
                    "                             ",
                    "                             ",
                    "                   C     C   ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "AAAAAAAAAAAAAAA   AAAAAAAAAA ",
                    "ACCCCCCCCCCCCCA    C AAA C   ",
                    "YC           CY    C     C   ",
                    "YC           CY    C     C   ",
                    "YC           CY   CCCCCCCCC  ",
                    "YC           CY    CLLLLLC   ",
                    "YC           CY    CLLLLLC   ",
                    "YC           CY    CLLLLLC   ",
                    "YC           CY    CLLLLLC   ",
                    "YC           CY    CLLLLLC   ",
                    "YC           CY    CLCCCLC   ",
                    "Y C    ^    C Y    CLCCCLC   ",
                    "   CC  ^  CC   E   CLCCCLC   ",
                    "     CC^CC         CLLLLLC   ",
                    "       ^           CLLLLLC   ",
                    "       ^           CLLLLLC   ",
                    "                   CLLLLLC   ",
                    "                   CLLLLLC   ",
                    "                  CCCCCCCCC  ",
                    "                   C     C   ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "AAAAAAAAAAAAAAA   AAAAAAAAAA ",
                    "ACCCCCCCCCCCCCA   AAAAAAAAA  ",
                    " C           C               ",
                    " C           C               ",
                    " C           C     CCCCCCC   ",
                    " C           C     L     L   ",
                    " C           C     LMMMMML   ",
                    " C           C     L     L   ",
                    "YC           CY    LMMMMML   ",
                    " C           C     L     L   ",
                    " C           C     LMMMMML   ",
                    "  CC       CC      L  F  L   ",
                    "    CC   CC    E   LMMMMML   ",
                    "      CCC          L     L   ",
                    "                   LMMMMML   ",
                    "                   L     L   ",
                    "                   LMMMMML   ",
                    "                   L     L   ",
                    "                   CLLLLLC   ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "AAAAAAAAAAAAAAA   AAA A AAAA ",
                    " ACCCCCCCCCCCA    AAARARAAA  ",
                    "  C         C        R R     ",
                    "  C         C        R R     ",
                    "  C         C      CCRCRCC   ",
                    "  C         C      L J J L   ",
                    "  C         C      LMJMJML   ",
                    "  C         C      L J J L   ",
                    "Y C         C Y    LMJMJML   ",
                    "  C         C      L J J L   ",
                    "  C         C      LMJMJML   ",
                    "   C       C       L JFJ L   ",
                    "    CCC CCC    E   LMJMJML   ",
                    "       C           L J J L   ",
                    "                   LMJMJML   ",
                    "                   L J J L   ",
                    "                   LMJMJML   ",
                    "                   L J J L   ",
                    "                   CLCCCLC   ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "AAAAAAAAAAAAAA    AAAAAAAAAA ",
                    " ACCCCCCCCCCCA    AAAAAAAAA  ",
                    "  C         C                ",
                    "  C         C                ",
                    "  C         C      CCCCCCC   ",
                    "  C         C      L     L   ",
                    "  C         C      LMMMMML   ",
                    "  C         C      L     L   ",
                    " YC         CY     LMMMMML   ",
                    "  C         C      L     L   ",
                    "  C         C      LMMMMML   ",
                    "   CC     CC       L  F  L   ",
                    "     CCCCC     E   LMMMMML   ",
                    "                   L     L   ",
                    "                   LMMMMML   ",
                    "                   L     L   ",
                    "                   LMMMMML   ",
                    "                   L     L   ",
                    "                   CLCWCLC   ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    " AAAAAAAAAAAAA    AAA A AAAA ",
                    "  ACCCCCCCCCA     AAARARAAA  ",
                    "   C       C         R R     ",
                    "   C       C         R R     ",
                    "   C       C       CCRCTCC   ",
                    "   C       C       L J J L   ",
                    "   C       C       LMJMJML   ",
                    "   C       C       L J J L   ",
                    " Y C       C Y     LMJMJML   ",
                    "   C       C       L J J L   ",
                    "   C       C       LMJMJML   ",
                    "    CCC CCC        L JFJ L   ",
                    "       C       E   LMJMJML   ",
                    "                   L J J L   ",
                    "                   LMJMJML   ",
                    "                   L J J L   ",
                    "                   LMJMJML   ",
                    "                   L J J L   ",
                    "                   CLCCCLC   ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "  AAAAAAAAAAA     AAAAAAAAAA ",
                    "   ACCCCCCCA      AAAAAAAAA  ",
                    "    CC   CC                  ",
                    "    CC   CC                  ",
                    "    CC   CC        CCCCCCC   ",
                    "    CC   CC        L     L   ",
                    "    CC   CC        LMMMMML   ",
                    "    CC   CC        L     L   ",
                    "  Y CC   CC Y      LMMMMML   ",
                    "    CC   CC        L     L   ",
                    "    CC   CC        LMMMMML   ",
                    "      CCC          L  F  L   ",
                    "               E   LMMMMML   ",
                    "                   L     L   ",
                    "                   LMMMMML   ",
                    "                   L     L   ",
                    "                   LMMMMML   ",
                    "                   L     L   ",
                    "                   CLLLLLC   ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "   AAAAAAAAA      AAAAAAAAAA ",
                    "    AACCCAA        C AAA C   ",
                    "      CCC          C     C   ",
                    "      CCC          C     C   ",
                    "      CCC         CCCCCCCCC  ",
                    "      CCC          CLLLLLC   ",
                    "      CCC          CLLLLLC   ",
                    "      CCC          CLLLLLC   ",
                    "   YY CCC YY       CLLLLLC   ",
                    "      CCC          CLLLLLC   ",
                    "      CCC          CLCCCLC   ",
                    "                   CLCFCLC   ",
                    "               E   CLCCCLC   ",
                    "                   CLLLLLC   ",
                    "                   CLLLLLC   ",
                    "                   CLLLLLC   ",
                    "                   CLLLLLC   ",
                    "                   CLLLLLC   ",
                    "                  CCCCCCCCC  ",
                    "                   C     C   ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    " 333AAAAAAA       AAAAAAAAAA ",
                    " 3 3  AAA            AAA     ",
                    "       Y                     ",
                    "       Y                     ",
                    "       Y           C     C   ",
                    "       Y                     ",
                    "       Y                     ",
                    "       Y                     ",
                    "     YYYYY                   ",
                    "       Y                     ",
                    "       Y              L      ",
                    "       Y             LFL     ",
                    "               E      L      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                   C     C   ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "33333AAAAA         6AAAAA6   ",
                    "3%%%3 AAA          66AAA66   ",
                    "3%%%3              6&&&&&6   ",
                    "3%%%3              6&&&&&6   ",
                    " %%%               6666666   ",
                    " %%%                         ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                      L      ",
                    "                     LFL     ",
                    "               E      L      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "33333AAAAA    D D  6AAAAA6   ",
                    " % %  AAA     D D  66AAA66   ",
                    " % %          D D  $$$$$$$   ",
                    " % %          D D  $$$$$$$   ",
                    " % %          D D  6$$$$$6   ",
                    " % %          D D            ",
                    " %%%          D D            ",
                    "              D D            ",
                    "              D D            ",
                    "              D D            ",
                    "              D D     L      ",
                    "              DDD    LFL     ",
                    "              DED     L      ",
                    "              DDD            ",
                    "              D D            ",
                    "              D D            ",
                    "              D D            ",
                    "              D D            ",
                    "              D D            ",
                    "              D D            ",
                    "              D D            ",
                    "              DDD            ",
                    "              DED            ",
                    "              DDD            ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "33333AAAAA         6AAAAA6   ",
                    " % %  AAA          66AAA66   ",
                    " % %               6&&&&&6   ",
                    " % %               6&&&&&6   ",
                    " % %               6666666   ",
                    " % %                         ",
                    " %%%                         ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                      L      ",
                    "                     LFL     ",
                    "               E      L      ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "33333AAAAA        AAAAAAAAA  ",
                    "3%%%3 AAA          AAAAAAA   ",
                    "3%%%3                        ",
                    "3%%%3                        ",
                    " %%%                         ",
                    " %%%                         ",
                    "                             ",
                    "                             ",
                    "       C                     ",
                    "      CCC            BBB     ",
                    "     CCCCC          BBBBB    ",
                    "      CCC           BBFBB    ",
                    "       C       E    BBBBB    ",
                    "                     BBB     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    " 333AAAAAAA      AAAAAAAAAAA ",
                    " 3 3  AAA         AAAAAAAAA  ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "       C                     ",
                    "     CCCCC                   ",
                    "    CCC CCC         BBBBB    ",
                    "    CC   CC        BB   BB   ",
                    "   CC     CC       B     B   ",
                    "    CC   CC        B  F  B   ",
                    "    CCC CCC    E   B  F  B   ",
                    "     CCCCC         BB F BB   ",
                    "       C            BBBBB    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "  AAAAAAAAAAA    AAAAAAAAAAA ",
                    "    AAAAAAA      AAAAAAAAAAA ",
                    "                   J     J   ",
                    "                   J     J   ",
                    "                   J     J   ",
                    "      CCC          J     J   ",
                    "    CCCCCCC        J     J   ",
                    "   CC     CC       JBBBBBJ   ",
                    "   C       C       B     B   ",
                    "  CC       CC     B  MMM  B  ",
                    "  C         C     B       B  ",
                    "  CC       CC     B   F   B  ",
                    "   C       C   E  B       B  ",
                    "   CC     CC      B       B  ",
                    "    CCCCCCC        B  F  B   ",
                    "      CCC           BBBBB    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "  AAAAAAAAAAA    AAAAAAAAAAA ",
                    "  AAAAAAAAAAA    AAAAAAAAAAA ",
                    "                    J   J    ",
                    "                    J   J    ",
                    "                    J   J    ",
                    "      CCC           J   J    ",
                    "    CC   CC         JBBBJ    ",
                    "   C       C       BB   BB   ",
                    "   C       C      B       B  ",
                    "  C         C     B  M M  B  ",
                    "  C         C    B         B ",
                    "  C         C    B    F    B ",
                    "   C       C   E B         B ",
                    "   C       C      B       B  ",
                    "    CC   CC       B       B  ",
                    "      CCC          BB F BB   ",
                    "                     BBB     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "33AAAAAAAAAAA    AAAAAAAAAAA ",
                    "11AAAAAAAAAAA    AAAAAAAAAAA ",
                    "11YYYY   YYYY         L      ",
                    "11YYYY   YYYY         L      ",
                    "11YYYY   YYYY         L      ",
                    "11YYYYCCCYYYY         L      ",
                    "  YYCC   CCYY       BBBBB    ",
                    "  YC       CY      B MMM B   ",
                    "   C  T T  C      B       B  ",
                    "  C         C    BBMMMMMMMBB ",
                    "  C         C    B         B ",
                    "  C         C    B    F    B ",
                    "   C       C   E B         B ",
                    "   C       C     BB       BB ",
                    "    CC F CC       B       B  ",
                    "      CCC          B  F  B   ",
                    "                    BBBBB    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "33AAAAAAAAAAA    AAAAAAAAAAA ",
                    "11AAAAAAAAAAA    AAAAATAAAAA ",
                    "516661   8888        LTL     ",
                    "116661   8888        LTL     ",
                    "516661   8888        LTL     ",
                    "111111CCC            LTL     ",
                    "    CC   CC         BBTBB    ",
                    "   C  T T  C       B MTM B   ",
                    "   C   TT  C      B   T   B  ",
                    "  C         C    BBM M M MBB ",
                    "  C   r     C    B         B ",
                    "  C   r     C    B FFFFFFF B ",
                    "   C  r    C   E BF   F   FB ",
                    "   C  F    C     BF   F   FB ",
                    "    CC F CC       BF  F  FB  ",
                    "      CCC          BFFFFFB   ",
                    "                    BBWBB    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "33AAAAAAAAAAAAAA AAAAAAAAAAA ",
                    "11AAAAAAAAAAAAAA AAAAAAAAAAA ",
                    "5166 1   8888         L      ",
                    "1166 1   8  8         L      ",
                    "5166 1   8888         L      ",
                    "199111CCC             L      ",
                    "    CC   CC         BBBBB    ",
                    "   C       C       B MMM B   ",
                    "   C  T T  C      B       B  ",
                    "  C         C    BBMMMMMMMBB ",
                    "  C         C    B         B ",
                    "  C         C    B    F    B ",
                    "   C       C   E B         B ",
                    "   C       C     B         B ",
                    "    CC F CC       B       B  ",
                    "      CCC          B  F  B   ",
                    "                    BBBBB    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "33AAAAAAAAAAAAXA AAAAAAAAAAA ",
                    "11AAAAAAAAAAAAXA AAAAAAAAAAA ",
                    "5!7771   8888 X     J   J    ",
                    "116671   8  8 X     J   J    ",
                    "5!7771   8888 X     J   J    ",
                    "199111CCC     X     J   J    ",
                    "    CC   CC   X     JBBBJ    ",
                    "   C       C  X    BB   BB   ",
                    "   C  T T  C  X   B       B  ",
                    "  C         C X   B  M M  B  ",
                    "  C         C X  B         B ",
                    "  C     r   C X  B    F    B ",
                    "   C    r  C  XE B         B ",
                    "   C    F  C  X   B       B  ",
                    "    CC F CC  XX   B       B  ",
                    "      CXC   XX     BB F BB   ",
                    "       X   XX        BBB     ",
                    "       XXXXX                 ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "33AAAAAAAAAAAAAA AAAAAAAAAAA ",
                    "11AAAAAAAAAAAAAA AAAAAAAAAAA ",
                    "516671   8888      J     J   ",
                    "1166 1   8  8      J     J   ",
                    "5166 1   8888      J     J   ",
                    "199111CCC          J     J   ",
                    "    CC   CC        J     J   ",
                    "   C       C       JBBBBBJ   ",
                    "   C  T T  C       B     B   ",
                    "  C         C     B  MMM  B  ",
                    "  C         C     B       B  ",
                    "  C         C     B   F   B  ",
                    "   C       C   E  B       B  ",
                    "   C       C      B       B  ",
                    "    CC F CC        B  F  B   ",
                    "      CCC           BBBBB    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "33AAAAAAAAAAA    AAAAAAAAAAA ",
                    "11AAAAAAAAAAA     AAAAAAAAA  ",
                    "5166777777788                ",
                    "116661   8888                ",
                    "516661   8888                ",
                    "111111CCC                    ",
                    "    CC   CC                  ",
                    "   C       C                 ",
                    "   C  T T  C        BBBBB    ",
                    "  C         C      BB   BB   ",
                    "  C   r     C      B     B   ",
                    "  C   r     C      B     B   ",
                    "   C  r    C   E   B  F  B   ",
                    "   C  F    C       BB F BB   ",
                    "    CC F CC         BBBBB    ",
                    "      CCC                    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "33AAAAAAAAAAA     AAAAAAAAA  ",
                    "11AAAAAAAAAAA      AAAAAAA   ",
                    "11YYYY   YYYY                ",
                    "11YYYY   YYYY                ",
                    "11YYYY   YYYY                ",
                    "11YYYYCCCYYYY                ",
                    "  YYCC   CCYY                ",
                    "  YC       CY                ",
                    "   C  T T  C                 ",
                    "  C         C        BBB     ",
                    "  C         C       BLLLB    ",
                    "  C         C       BLLLB    ",
                    "   C       C   E    BLLLB    ",
                    "   C       C         BBB     ",
                    "    CC F CC                  ",
                    "      CCC                    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "  AAAAAAAAAAA       AAAAA    ",
                    "  AAAAAAAAAAA        AAA     ",
                    " 1$$1    8888                ",
                    "1$$$$1   8888                ",
                    "1$$$$1   8888                ",
                    " 1$$1 CCC888                 ",
                    "  11CC   CC                  ",
                    "   C       C                 ",
                    "   C  T T  C                 ",
                    "  C         C                ",
                    "  C         C                ",
                    "  C     r   C                ",
                    "   C    r  C   E             ",
                    "   C    F  C                 ",
                    "    CC F CC                  ",
                    "      CCC                    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "  AAAAAAAAAAA D D   AAAAA    ",
                    "  AAAAAAAAAAA D D    AAA     ",
                    " 1$$1    8888 D D            ",
                    "1$$$$1   8  8 D D            ",
                    "1$$$$1   8  8 D D            ",
                    " 1$$1 CCC8888 D D            ",
                    "  11CC   CC   D D            ",
                    "   C       C  D D            ",
                    "   C  T T  C  D D            ",
                    "  C         C D D            ",
                    "  C         C D D            ",
                    "  C         C DDD            ",
                    "   C       C  DED            ",
                    "   C       C  DDD            ",
                    "    CC F CC   D D            ",
                    "      CCC     D D            ",
                    "              D D            ",
                    "              D D            ",
                    "              D D            ",
                    "              D D            ",
                    "              D D            ",
                    "              DDD            ",
                    "              DED            ",
                    "              DDD            ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "  AAAAAAAAAAA       AAAAA 444",
                    "  AAAAAAAAAAA        AAA  444",
                    " 1$$1    8888             444",
                    "1$$$$1   8  8                ",
                    "1$$$$1   8  8                ",
                    " 1$$1 CCC8888                ",
                    "  11CC   CC                  ",
                    "   C       C                 ",
                    "   C  T T  C                 ",
                    "  C         C                ",
                    "  C   r     C                ",
                    "  C   r     C                ",
                    "   C  r    C   E             ",
                    "   C  F    C                 ",
                    "    CC F CC                  ",
                    "      CCC                    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "  AAAAAAAAAAA     AAAAAAAAA44",
                    "  AAAAATAAAAA     AAAAAAAAA44",
                    " 1$$1  T 8888     AAAAAAAAA44",
                    "1$$$$1 T 8  8                ",
                    "1$$$$1 T 8  8                ",
                    " 1$$1 CTC8888                ",
                    "  11CC T CC                  ",
                    "   C   T   C        DEEED    ",
                    "   C  T T  C                 ",
                    "  C         C                ",
                    "  C         C                ",
                    "  C         C                ",
                    "   C       C   E    DEEED    ",
                    "   C       C                 ",
                    "    CC F CC                  ",
                    "      CCC                    ",
                    "                             ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "               E    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "  AAAAAAAAAAA     AAAAAAAAA44",
                    "  AAAAAAAAAAA     AAAIIIAAA44",
                    " 1$$1    8888     AAAIIIAAA44",
                    "1$$$$1   8  8      K III K 6 ",
                    "1$$$$1   8  8      K III K   ",
                    " 1$$1 CCC8888      K III K   ",
                    "  11CC   CC        K III K 6 ",
                    "   C       C       KEIIIEK 6 ",
                    "   C  T T  C       K III K 6 ",
                    "  C         C      K III K   ",
                    "  C         C      K III K   ",
                    "  C     r   C      K III K   ",
                    "   C    r  C   E   KEIIIEK   ",
                    "   C    F  C       K III K   ",
                    "    CC F CC        K III K   ",
                    "      CCC          K III K   ",
                    "                   K III K   ",
                    "                   KEIIIEK   ",
                    "                   N III N   ",
                    "                   N III N   ",
                    "                   N III N   ",
                    "                   N III N   ",
                    "               E   NEIIIEN   ",
                    "                   N III N   ",
                    "                   N III N   ",
                    "                     III     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "  AAAAAAAAAAAAAAAAAAAAAAAAA44",
                    "  AAAAAAAAAAA     AAIAAAIAA#4",
                    " 1$$1    8888     AAIMMMIAA#4",
                    "1$$$$1   8  8       I   I  #6",
                    "1$$$$1   8  8       I   I  #6",
                    " 1$$1 CCC8888       I   I  #6",
                    "  11CC   CC         I   I  #6",
                    "   C       C      DEIIIIIED#6",
                    "   C  T T  C        I   I  #6",
                    "  C         C       I   I###6",
                    "  C         C       I   I    ",
                    "  C         C       IMMMI    ",
                    "   C       C   E  DEIIIIIED  ",
                    "   C       C        I   I    ",
                    "    CC F CC         I   I    ",
                    "      CCC           I   I    ",
                    "                    IMMMI    ",
                    "                  DEIIIIIED  ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    IMMMI    ",
                    "               E  DEIIIIIED  ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                     III     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "  AAAAAAAAAAAAAAAAAAAAAAAAA44",
                    "  AAAAAAAAAAAAAAAAAIAMRMAIA44",
                    " 1$$777777888     AIM   MIA44",
                    "1$$$$1   8888      I     I 6 ",
                    "1$$$$1   8888      I     I   ",
                    " 1$$1 CCC888       I     I   ",
                    "  11CC   CC        I     I 6 ",
                    "   C       C      EIIRRRIIE6 ",
                    "   C  T T  C       I     I 6 ",
                    "  C         C      I     I   ",
                    "  C   r     C      I     I   ",
                    "  C   r     C      IM   MI   ",
                    "   C  r    C   E  EIIRRRIIE  ",
                    "   C  F    C       I     I   ",
                    "    CC F CC        I     I   ",
                    "      CCC          I     I   ",
                    "                   I     I   ",
                    "                  EIIRRRIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "               E  EIIRRRIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                    I   I    ",
                    "                     III     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "33AAAAAAAAAAAAAAAAAAAAAAAAA44",
                    "11AAAAAAAAAAAAAAAAAIARRRAIA44",
                    "11YYYY   YYYY     AIM R MIA44",
                    "11YYYY   YYYY      I  R  I 6 ",
                    "11YYYY   YYYY      I  R  I   ",
                    "11YYYYCCCYYYY      I  R  I   ",
                    "  YYCC   CCYY      I  R  I   ",
                    "  YC       CY     EIIRRRIIE6 ",
                    "   C  T T  C       I  R  I   ",
                    "  C         C      I  R  I   ",
                    "  C         C      I  R  I   ",
                    "  C         C      IM R MI   ",
                    "   C       C   EEEEIIRRRIIE  ",
                    "   C       C       I  R  I   ",
                    "    CC F CC        I  R  I   ",
                    "      CCC          I  R  I   ",
                    "                   I  R  I   ",
                    "                  EIIRRRIIE  ",
                    "                   I  R  I   ",
                    "                   I  R  I   ",
                    "                   I  R  I   ",
                    "                   I  R  I   ",
                    "               EEEEIIRRRIIE  ",
                    "                   I  R  I   ",
                    "                   I  R  I   ",
                    "                   I  R  I   ",
                    "                    I   I    ",
                    "                     IWI     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "33AAAAAAAAAAAAAAAAAAAAAAAAA44",
                    "11AAAAAAAAAAAAAAAAAIAMRMAIA44",
                    "516661   8888     AIM   MIA44",
                    "116661   8888      I     I 6 ",
                    "516661   8888      I     I   ",
                    "111111CCC          I     I   ",
                    "    CC   CC        I     I 6 ",
                    "   C       C      EIIRRRIIE6 ",
                    "   C  T T  C       I     I 6 ",
                    "  C         C      I     I   ",
                    "  C         C      I     I   ",
                    "  C     r   C      IM   MI 6 ",
                    "   C    r  C      EIIRRRIIE6 ",
                    "   C    F  C       I     I 6 ",
                    "    CC F CC        I     I   ",
                    "      CCC          I     I   ",
                    "                   I     I   ",
                    "                  EIIRRRIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                  EIIRRRIIE  ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                   I     I   ",
                    "                    I   I    ",
                    "                     III     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "33AAAAAAAAAAAAAAAAAAAAAAAAA44",
                    "11AAAAA]AAAAA     AAIAAAIAA#4",
                    "5166 1 ] 8888     AAIMMMIAA#4",
                    "1166 1 ] 8  8       I   I  #6",
                    "5166 1 ] 8888       I   I  #6",
                    "199111C]C           I   I  #6",
                    "    CC ] CC         I   I  #6",
                    "   C       C      DEIIIIIED#6",
                    "   C  T T  C        I   I  #6",
                    "  C         C       I   I  #6",
                    "  C         C       I   I  #6",
                    "  C         C       IMMMI  #6",
                    "   C       C      DEIIIIIED#6",
                    "   C       C        I   I  #6",
                    "    CC F CC         I   I###6",
                    "      CCC           I   I    ",
                    "                    IMMMI    ",
                    "                  DEIIIIIED  ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    IMMMI    ",
                    "                  DEIIIIIED  ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                    I   I    ",
                    "                     III     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "33AAAAAAAAAAA     AAAAAAAAA44",
                    "11AAAAAAAAAAA     AAAIIIAAA44",
                    "5!77 1   8888     AAAIIIAAA44",
                    "1167 1   8  8      K III K 6 ",
                    "5!77 1   8888      K III K   ",
                    "199111CCC          K III K   ",
                    "    CC   CC        K III K 6 ",
                    "   C       C       KEIIIEK 6 ",
                    "   C  T T  C       K III K 6 ",
                    "  C         C      K III K   ",
                    "  C   r     C      K III K   ",
                    "  C   r     C      K III K 6 ",
                    "   C  r    C       KEIIIEK 6 ",
                    "   C  F    C       K III K 6 ",
                    "    CC F CC        K III K   ",
                    "      CCC          K III K   ",
                    "                   K III K   ",
                    "                   KEIIIEK   ",
                    "                   N III N   ",
                    "                   N III N   ",
                    "                   N III N   ",
                    "                   N III N   ",
                    "                   NEIIIEN   ",
                    "                   N III N   ",
                    "                   N III N   ",
                    "                     III     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "33AAAAAAAAAAAAAA  AAAAAAAAA44",
                    "11AAAAAAAAAAAAAA  AAAAAAAAA44",
                    "5167 1   8888     AAAAAAAAA44",
                    "1166 1   8  8                ",
                    "5166 1   8888                ",
                    "199111CCC                    ",
                    "    CC   CC                  ",
                    "   C       C        DEEED    ",
                    "   C  T T  C                 ",
                    "  C         C                ",
                    "  C         C                ",
                    "  C         C                ",
                    "   C       C        DEEED    ",
                    "   C       C                 ",
                    "    CC F CC                  ",
                    "      CCC                    ",
                    "                             ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                    DEEED    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "33AAAAAAAAAAAAXA     AAA  444",
                    "11AAAAAAAAAAAAXA          444",
                    "5167777777888 X           444",
                    "116661   8  8 X              ",
                    "516661   8888 X              ",
                    "111111CCC     X              ",
                    "    CC   CC   X              ",
                    "   C  T T  C  X              ",
                    "   C   T   C  X              ",
                    "  C         C X              ",
                    "  C         C X              ",
                    "  C     r   C X              ",
                    "   C    r  C  X              ",
                    "   C    F  C  X              ",
                    "    CC F CC  XX              ",
                    "      CXC   XX               ",
                    "       X   XX                ",
                    "       XXXXX                 ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "33AAAAAAAAAAAAAA  AAAAAAAAA  ",
                    "11AAAAAAAAAAAAAA   GOOOOOG   ",
                    "11YYYY   YYYY      GOPPPOG   ",
                    "11YYYY   YYYY      GOPPPOG   ",
                    "11YYYY   YYYY      GOPPPOG   ",
                    "11YYYYCCCYYYY      GOOOOOG   ",
                    "  YYCC   CCYY      GGGGGGG   ",
                    "  YC       CY                ",
                    "   C  T T  C                 ",
                    "  C         C                ",
                    "  C         C                ",
                    "  C         C                ",
                    "   C       C                 ",
                    "   C       C                 ",
                    "    CC F CC                  ",
                    "      CCC                    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "  AAAAAAAAAAA     AAAAAAAAA  ",
                    "  AAAAAAAAAAA      HOOOOOH   ",
                    "     11111         HOSSSOH   ",
                    "     11111          OS SO    ",
                    "     11111          PSSSP    ",
                    "      CCC          HOOOOOH   ",
                    "    CC   CC        G     G   ",
                    "   C       C                 ",
                    "   C       C                 ",
                    "  C         C                ",
                    "  C         C                ",
                    "  C         C                ",
                    "   C       C                 ",
                    "   C       C                 ",
                    "    CC   CC                  ",
                    "      CCC                    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "  AAAAAAAAAAA     AAAAAAAAA  ",
                    "    AAAAAAA        HOOOOOH   ",
                    "     12221         HOSSSOH   ",
                    "     12221          OS SO    ",
                    "     12221          PSSSP    ",
                    "      CCC          HOOOOOH   ",
                    "    CCC CCC        G     G   ",
                    "   CC     CC                 ",
                    "   C       C                 ",
                    "  CC       CC                ",
                    "  C         C                ",
                    "  CC       CC                ",
                    "   C       C                 ",
                    "   CC     CC                 ",
                    "    CCC CCC                  ",
                    "      CCC                    ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "    AAAAAAA       AAAAAAAAA  ",
                    "      AAA          HOOOOOH   ",
                    "                   HOSSSOH   ",
                    "                    OS SO    ",
                    "                    PSSSP    ",
                    "                   HOOOOOH   ",
                    "       C           G     G   ",
                    "     CCCCC                   ",
                    "    CCC CCC                  ",
                    "    CC   CC                  ",
                    "   CC     CC                 ",
                    "    CC   CC                  ",
                    "    CCC CCC                  ",
                    "     CCCCC                   ",
                    "       C                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
                    "     AAAAA        AAAAAAAAA  ",
                    "      AAA          GOOOOOG   ",
                    "                   GOHHHOG   ",
                    "                   GOHUHOG   ",
                    "                   GOHHHOG   ",
                    "                   GOOOOOG   ",
                    "                   GGGGGGG   ",
                    "                             ",
                    "       C                     ",
                    "      CCC                    ",
                    "     CCCCC                   ",
                    "      CCC                    ",
                    "       C                     ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
                    "                             ",
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
        // ── 附属模块口：线程仓 / 超频仓 / 加速仓 / 并行仓（GTO 模块 'd'/'c' 位的语义）──
        TraceabilityPredicate moduleHatches =
                ability(com.gtlmodular.common.data.thread.ThreadHatchMachines.THREAD_HATCH)
                        .setMaxGlobalLimited(1)
                        .or(com.gtlmodular.common.data.fuel.OverclockHatches.predicate())
                        .or(com.gtlmodular.common.data.fuel.AccelerateHatches.predicate())
                        .or(ability(PartAbility.PARALLEL_HATCH).setMaxGlobalLimited(1).setPreviewCount(1));

        // ── 主结构正面舱面（GTO 的 autoLaserAbilities + MAINTENANCE + 附属模块口）──
        TraceabilityPredicate hatchFace = blocksOf("gtceu", "high_power_casing").setMinGlobalLimited(5)
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
                .where('U', Predicates.controller(Predicates.blocks(definition.get())))
                .where('A', blocksOf("gtceu", "stress_proof_casing"))
                .where('B', Predicates.blocks(PetrochemBlocks.SUPERCRITICAL_TURBINE_CASING.get()))
                .where('C', blocksOf("gtceu", "clean_machine_casing"))
                .where('D', frame("steel"))
                .where('E', Predicates.blocks(WoodFactoryBlocks.OIL_GAS_TRANSPORTATION_PIPE_CASING.get()))
                .where('F', blocksOf("gtceu", "tungstensteel_pipe_casing"))
                .where('G', Predicates.blocks(PetrochemBlocks.PIKYONIUM_MACHINE_CASING.get()))
                .where('H', hatchFace)
                .where('I', blocksOf("gtceu", "watertight_casing"))
                .where('J', frame("naquadah_alloy"))
                .where('K', frame("tungsten"))
                .where('L', blocksOf("gtceu", "fusion_glass"))
                .where('M', Predicates.heatingCoils())
                .where('N', frame("stainless_steel"))
                .where('O', blocksOf("gtceu", "computer_casing"))
                .where('P', blocksOf("gtceu", "computer_heat_vent"))
                .where('R', blocksOf("gtceu", "titanium_pipe_casing"))
                .where('S', blocksOf("gtceu", "high_power_casing"))
                .where('T', blocksOf("gtceu", "steel_pipe_casing"))
                .where('V', Predicates.blocks(PetrochemBlocks.CHEMICAL_GRADE_GLASS.get()))
                .where('W', Predicates.abilities(PartAbility.MUFFLER))
                .where('X', Predicates.blocks(WoodFactoryBlocks.CHEMICAL_CORROSION_RESISTANT_PIPE_CASING.get()))
                .where('Y', blocksOf("gtceu", "robust_machine_casing"))
                .where('Z', blocksOf("gtlcore", "iridium_casing"))
                .where('[', Predicates.blocks(PetrochemBlocks.IRIDIUM_PIPE_CASING.get()))
                .where('r', blocksOf("gtceu", "electrolytic_cell"))
                .where(']', blocksOf("gtceu", "ptfe_pipe_casing"))
                .where('^', Predicates.blocks(WoodFactoryBlocks.HIGH_PRESSURE_PIPE_CASING.get()))
                
                .where('!', blocksOf("gtceu", "tungsten_frame"))
                .where('#', blocksOf("gtceu", "fusion_glass"))
                .where('$', blocksOf("gtceu", "naquadah_coil_block"))
                .where('%', blocksOf("gtceu", "stainless_steel_frame"))
                .where('&', blocksOf("gtceu", "computer_casing"))
                .where('(', blocksOf("gtceu", "palladium_substation_casing"))
                .where(')', blocksOf("gtceu", "titanium_pipe_casing"))
                .where('*', blocksOf("gtceu", "computer_heat_vent"))
                .where('1', blocksOf("gtlmodular", "supercritical_turbine_casing"))
                .where('2', blocksOf("gtceu", "clean_machine_casing"))
                .where('3', blocksOf("gtceu", "steel_frame"))
                .where('4', blocksOf("gtlmodular", "oil_gas_transportation_pipe_casing"))
                .where('5', blocksOf("gtceu", "tungstensteel_pipe_casing"))
                .where('6', blocksOf("gtlmodular", "pikyonium_machine_casing"))
                .where('7', blocksOf("gtceu", "high_power_casing"))
                .where('8', blocksOf("gtceu", "watertight_casing"))
                .where('9', blocksOf("gtceu", "naquadah_alloy_frame"))
                .build();
    }
}
