package com.gtlmodular.common.data.fieldforge;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gtlmodular.GTLModular;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 力场压模工厂（{@code gtlmodular:field_extruder_factory}）的结构 —— GTO
 * {@code gtocore:field_extruder_factory} 的 <b>1:1 照搬</b>（方块替换见下表）。
 *
 * <h2>取证</h2>
 * <ul>
 *   <li>结构文件：GTO jar 内 {@code pattern/field_extruder_factory.mbs}（425 B，gzip 后 7,434 B）。</li>
 *   <li>格式：{@code FF FF FF FF | 0F | ver=1 | charDir=5 | stringDir=0 | aisleDir=2 |
 *       LEB(aisleCount=29) | LEB(stringsPerAisle=15)}，随后每行 {@code [0x00][1 字节长度][字符]}，
 *       片与片之间插 1 字节标记（0x0f）。解析器 {@code _scratch/tentacle/parse_mbs.py}
 *       ⇒ <b>0 字节剩余</b>（解析完整），15 (X) × 15 (Y) × 29 (Z)。</li>
 *   <li>谓词绑定：{@code _gto_src/.../machines/MultiBlockF.java:249} 的 {@code FIELD_EXTRUDER_FACTORY}。</li>
 *   <li>朝向：直转，不做任何轴颠倒 —— 第 1 片 = 最背面（z=0），第 29 片 = 正面（z=28）；
 *       每片第 1 行 = 最低层（y=0）；每行第 1 个字符 = x 最小侧。
 *       控制器 {@code a} 在 <b>x=0, y=7, z=14</b>（侧面正中，不是正面 —— 与 GTO 一致）。</li>
 * </ul>
 *
 * <h2>尺寸与符号</h2>
 * <p><b>15 (X) × 15 (Y) × 29 (Z) = 6,525 格</b>，其中实心 3,261 格、空气 3,264 格、控制器 1 格。
 *
 * <table border="1">
 *   <caption>符号表（GTO 方块 → 我方方块；缺的 3 件见下注）</caption>
 *   <tr><th>符号</th><th>格数</th><th>GTO</th><th>我方</th></tr>
 *   <tr><td>a</td><td>1</td><td>控制器</td><td>{@code gtlmodular:field_extruder_factory}</td></tr>
 *   <tr><td>A</td><td>968</td><td>高能耐压机械方块</td><td><b>{@code gtlmodular:high_pressure_resistant_casing}（新造，见 {@link FieldExtruderFactoryBlocks}）</b></td></tr>
 *   <tr><td>B</td><td>88</td><td>高能机器外壳 + 仓室</td><td>{@code gtceu:high_power_casing} + IO/能源/激光/线程/超频/加速/并行/维护</td></tr>
 *   <tr><td>C</td><td>76</td><td>遏制场发生器</td><td>{@code kubejs:containment_field_generator}</td></tr>
 *   <tr><td>D</td><td>1026</td><td>分子机械方块</td><td>{@code gtlcore:molecular_casing}</td></tr>
 *   <tr><td>E</td><td>112</td><td>氧化锂陶瓷防热抗震机械方块</td><td><b>{@code gtlmodular:lithium_oxide_ceramic_heat_resistant_shock_resistant_mechanical_cube}（新造）</b></td></tr>
 *   <tr><td>F</td><td>288</td><td>硼硅酸盐玻璃</td><td>{@code gtceu:borosilicate_glass}</td></tr>
 *   <tr><td>G</td><td>48</td><td>超能核心</td><td>{@code gtlcore:hyper_core}</td></tr>
 *   <tr><td>H</td><td>108</td><td>加速管道</td><td>{@code kubejs:accelerated_pipeline}（整合包已有，贴图与 GTO 逐字节相同）</td></tr>
 *   <tr><td>I</td><td>102</td><td>电解器</td><td>{@code gtceu:electrolytic_cell}</td></tr>
 *   <tr><td>J</td><td>96</td><td>聚变玻璃</td><td>{@code gtceu:fusion_glass}</td></tr>
 *   <tr><td>K</td><td>72</td><td>超能机械方块</td><td>{@code gtlcore:hyper_mechanical_casing}</td></tr>
 *   <tr><td>L</td><td>276</td><td>三钛框架</td><td>{@code gtceu:tritanium_frame}</td></tr>
 * </table>
 *
 * <h2>缺件如何处理（不代替、不复用错方块）</h2>
 * <p>GTO 的 13 个符号位里，本整合包**没有** 3 个：
 * <ol>
 *   <li><b>A —— {@code high_pressure_resistant_casing}（高能耐压机械方块，968 格）</b>：
 *       gtocore 专有，整合包内 gtlcore / gtladditions / kubejs 均无。
 *       ⇒ <b>本模组自造</b> {@code gtlmodular:high_pressure_resistant_casing}，
 *       贴图从 GTO 原包逐字节拷贝（见 {@link FieldExtruderFactoryBlocks}）。</li>
 *   <li><b>E —— {@code lithium_oxide_ceramic_heat_resistant_shock_resistant_mechanical_cube}
 *       （氧化锂陶瓷防热抗震机械方块，112 格）</b>：同上，gtocore 专有。
 *       ⇒ 本模组自造同名方块，贴图同样逐字节拷贝。</li>
 *   <li><b>H —— {@code accelerated_pipeline}（加速管道，108 格）</b>：
 *       <b>整合包已经有了</b> —— KubeJS {@code startup_scripts/block_registration.js:102}
 *       注册为 {@code kubejs:accelerated_pipeline}，贴图 md5 与 GTO 原包一致
 *       （{@code 78fdc92d3f3a9e1368985b27be922f6e}），且 {@code circuit_boards.js:241} 有产能配方。
 *       ⇒ 直接引用 {@code kubejs:accelerated_pipeline}，<b>不再造第二份</b>。</li>
 * </ol>
 * 三者都<b>只影响外观</b>，它们在结构里都是纯装饰位（不接任何能力）：
 * 机器能否成形、能放哪些仓、并行多少，与 GTO 原机完全一致。
 *
 * <p><b>方块解析时机</b>：{@code .pattern(...)} 在注册期只登记一个 memoize 的 supplier，{@link #build}
 * 到<b>第一次结构判定 / 首次打开 JEI 预览</b>才执行 —— 那时所有模组的方块（含 KubeJS）都已就位。
 */
public final class FieldExtruderFactoryStructures {

    static final int WIDTH = 15;
    static final int HEIGHT = 15;
    static final int LENGTH = 29;

    private FieldExtruderFactoryStructures() {
    }

    /** 29 片 Z 层数据（{@code slices[z][y]}，{@code z=0} 最背面、{@code z=28} 正面）。 */
    public static String[][] slices() {
        return SLICES;
    }

    /** 29 片 Z 层，每层 15 行（Y=0 在前），行宽 15。数据来自 GTO 的 .mbs，直转无颠倒。 */
    private static final String[][] SLICES = {
            { // Z=0
                    "               ", // Y=0
                    "    AAAAAAA    ", // Y=1
                    "   ABBBBBBBA   ", // Y=2
                    "  AACCDDDCCAA  ", // Y=3
                    " AAABBBBBBBAAA ", // Y=4
                    " AAAAAAAAAAAAA ", // Y=5
                    " EEFFFFFFFFFEE ", // Y=6
                    "               ", // Y=7
                    " EEFFFFFFFFFEE ", // Y=8
                    " AAAAAAAAAAAAA ", // Y=9
                    " AAABBBBBBBAAA ", // Y=10
                    "  AACCDDDCCAA  ", // Y=11
                    "   ABBBBBBBA   ", // Y=12
                    "    AAAAAAA    ", // Y=13
                    "               ", // Y=14
            },
            { // Z=1
                    "               ", // Y=0
                    "    AAAAAAA    ", // Y=1
                    "   AA     AA   ", // Y=2
                    "  AA GHIHG AA  ", // Y=3
                    " AAIJJKKKJJIAA ", // Y=4
                    " FF         FF ", // Y=5
                    "  E         E  ", // Y=6
                    "               ", // Y=7
                    "  E         E  ", // Y=8
                    " FF         FF ", // Y=9
                    " AAIJJKKKJJIAA ", // Y=10
                    "  AA GHIHG AA  ", // Y=11
                    "   AA     AA   ", // Y=12
                    "    AAAAAAA    ", // Y=13
                    "               ", // Y=14
            },
            { // Z=2
                    "               ", // Y=0
                    "    AAAAAAA    ", // Y=1
                    "   AA     AA   ", // Y=2
                    "  AA GHIHG AA  ", // Y=3
                    " AAIJJKKKJJIAA ", // Y=4
                    " FF         FF ", // Y=5
                    "  E         E  ", // Y=6
                    "               ", // Y=7
                    "  E         E  ", // Y=8
                    " FF         FF ", // Y=9
                    " AAIJJKKKJJIAA ", // Y=10
                    "  AA GHIHG AA  ", // Y=11
                    "   AA     AA   ", // Y=12
                    "    AAAAAAA    ", // Y=13
                    "               ", // Y=14
            },
            { // Z=3
                    "               ", // Y=0
                    "    AAAAAAA    ", // Y=1
                    "   AA     AA   ", // Y=2
                    "  AA GHIHG AA  ", // Y=3
                    " AAIJJKKKJJIAA ", // Y=4
                    " FF         FF ", // Y=5
                    "  E         E  ", // Y=6
                    "               ", // Y=7
                    "  E         E  ", // Y=8
                    " FF         FF ", // Y=9
                    " AAIJJKKKJJIAA ", // Y=10
                    "  AA GHIHG AA  ", // Y=11
                    "   AA     AA   ", // Y=12
                    "    AAAAAAA    ", // Y=13
                    "               ", // Y=14
            },
            { // Z=4
                    "LLLDD     DDLLL", // Y=0
                    "LDDDAAAAAAADDDL", // Y=1
                    "LDDADDDDDDDADDL", // Y=2
                    "LDAACCHIHCCAADL", // Y=3
                    "LAAADDDDDDDAAAL", // Y=4
                    "LAAAAAAAAAAAAAL", // Y=5
                    "DEEFFFFFFFFFEED", // Y=6
                    "DD           DD", // Y=7
                    "DEEFFFFFFFFFEED", // Y=8
                    "LAAAAAAAAAAAAAL", // Y=9
                    "LAAADDDDDDDAAAL", // Y=10
                    "LDAACCHIHCCAADL", // Y=11
                    "LDDADDDDDDDADDL", // Y=12
                    "LDDDAAAAAAADDDL", // Y=13
                    "LLLDD     DDLLL", // Y=14
            },
            { // Z=5
                    "DDDDD     DDDDD", // Y=0
                    "DDDD       DDDD", // Y=1
                    "DDD         DDD", // Y=2
                    "DD    HIH    DD", // Y=3
                    "DD           DD", // Y=4
                    "DDF        FFDD", // Y=5
                    "BD           DB", // Y=6
                    "BD           DB", // Y=7
                    "BD           DB", // Y=8
                    "DDFF        FDD", // Y=9
                    "DD           DD", // Y=10
                    "DD    HIH    DD", // Y=11
                    "DDD         DDD", // Y=12
                    "DDDD       DDDD", // Y=13
                    "DDDDD     DDDDD", // Y=14
            },
            { // Z=6
                    "LLLDD     DDLLL", // Y=0
                    "L DD       DD L", // Y=1
                    "LDD         DDL", // Y=2
                    "LD    HIH    DL", // Y=3
                    "LD           DL", // Y=4
                    "LDF        FFDL", // Y=5
                    "CD           DC", // Y=6
                    "CD           DC", // Y=7
                    "CD           DC", // Y=8
                    "LDFF        FDL", // Y=9
                    "LD           DL", // Y=10
                    "LD    HIH    DL", // Y=11
                    "LDD         DDL", // Y=12
                    "L DD       DD L", // Y=13
                    "LLLDD     DDLLL", // Y=14
            },
            { // Z=7
                    "DDDDD     DDDDD", // Y=0
                    "DDDD       DDDD", // Y=1
                    "DDD         DDD", // Y=2
                    "DD    HIH    DD", // Y=3
                    "DD           DD", // Y=4
                    "DDF        FFDD", // Y=5
                    "BD           DB", // Y=6
                    "BD           DB", // Y=7
                    "BD           DB", // Y=8
                    "DDFF        FDD", // Y=9
                    "DD           DD", // Y=10
                    "DD    HIH    DD", // Y=11
                    "DDD         DDD", // Y=12
                    "DDDD       DDDD", // Y=13
                    "DDDDD     DDDDD", // Y=14
            },
            { // Z=8
                    "LLLDD     DDLLL", // Y=0
                    "LDDDAAAAAAADDDL", // Y=1
                    "LDDADDDDDDDADDL", // Y=2
                    "LDAACCHIHCCAADL", // Y=3
                    "LAAADDDDDDDAAAL", // Y=4
                    "LAAAAAAAAAAAAAL", // Y=5
                    "DEEFFFFFFFFFEED", // Y=6
                    "DD           DD", // Y=7
                    "DEEFFFFFFFFFEED", // Y=8
                    "LAAAAAAAAAAAAAL", // Y=9
                    "LAAADDDDDDDAAAL", // Y=10
                    "LDAACCHIHCCAADL", // Y=11
                    "LDDADDDDDDDADDL", // Y=12
                    "LDDDAAAAAAADDDL", // Y=13
                    "LLLDD     DDLLL", // Y=14
            },
            { // Z=9
                    "               ", // Y=0
                    "    AAAAAAA    ", // Y=1
                    "   AA     AA   ", // Y=2
                    "  AA GHIHG AA  ", // Y=3
                    " AAIJJKKKJJIAA ", // Y=4
                    " FF         FF ", // Y=5
                    "  E         E  ", // Y=6
                    "               ", // Y=7
                    "  E         E  ", // Y=8
                    " FF         FF ", // Y=9
                    " AAIJJKKKJJIAA ", // Y=10
                    "  AA GHIHG AA  ", // Y=11
                    "   AA     AA   ", // Y=12
                    "    AAAAAAA    ", // Y=13
                    "               ", // Y=14
            },
            { // Z=10
                    "               ", // Y=0
                    "    AAAAAAA    ", // Y=1
                    "   AA     AA   ", // Y=2
                    "  AA GHIHG AA  ", // Y=3
                    " AAIJJKKKJJIAA ", // Y=4
                    " FF         FF ", // Y=5
                    "  E         E  ", // Y=6
                    "               ", // Y=7
                    "  E         E  ", // Y=8
                    " FF         FF ", // Y=9
                    " AAIJJKKKJJIAA ", // Y=10
                    "  AA GHIHG AA  ", // Y=11
                    "   AA     AA   ", // Y=12
                    "    AAAAAAA    ", // Y=13
                    "               ", // Y=14
            },
            { // Z=11
                    "               ", // Y=0
                    "    AAAAAAA    ", // Y=1
                    "   AA     AA   ", // Y=2
                    "  AA GHIHG AA  ", // Y=3
                    " AAIJJKKKJJIAA ", // Y=4
                    " FF         FF ", // Y=5
                    "  E         E  ", // Y=6
                    "               ", // Y=7
                    "  E         E  ", // Y=8
                    " FF         FF ", // Y=9
                    " AAIJJKKKJJIAA ", // Y=10
                    "  AA GHIHG AA  ", // Y=11
                    "   AA     AA   ", // Y=12
                    "    AAAAAAA    ", // Y=13
                    "               ", // Y=14
            },
            { // Z=12
                    "LLLDD     DDLLL", // Y=0
                    "LDDDAAAAAAADDDL", // Y=1
                    "LDDADDDDDDDADDL", // Y=2
                    "LDAACCHIHCCAADL", // Y=3
                    "LAAADDDDDDDAAAL", // Y=4
                    "DAAAAAAAAAAAAAD", // Y=5
                    "DEEFFFFFFFFFEED", // Y=6
                    "DD           DD", // Y=7
                    "DEEFFFFFFFFFEED", // Y=8
                    "DAAAAAAAAAAAAAD", // Y=9
                    "LAAADDDDDDDAAAL", // Y=10
                    "LDAACCHIHCCAADL", // Y=11
                    "LDDADDDDDDDADDL", // Y=12
                    "LDDDAAAAAAADDDL", // Y=13
                    "LLLDD     DDLLL", // Y=14
            },
            { // Z=13
                    "DDDDD     DDDDD", // Y=0
                    "DDDD       DDDD", // Y=1
                    "DDD         DDD", // Y=2
                    "DD    HIH    DD", // Y=3
                    "DD           DD", // Y=4
                    "DDF         FDD", // Y=5
                    "BD           D ", // Y=6
                    "BD           D ", // Y=7
                    "BD           D ", // Y=8
                    "DDF         FDD", // Y=9
                    "DD           DD", // Y=10
                    "DD    HIH    DD", // Y=11
                    "DDD         DDD", // Y=12
                    "DDDD       DDDD", // Y=13
                    "DDDDD     DDDDD", // Y=14
            },
            { // Z=14
                    "LLLDD     DDLLL", // Y=0
                    "L DD       DD L", // Y=1
                    "LDD         DDL", // Y=2
                    "LD    HIH    DL", // Y=3
                    "LD           DL", // Y=4
                    "DDF         FDD", // Y=5
                    "BD           D ", // Y=6
                    "aD           D ", // Y=7
                    "BD           D ", // Y=8
                    "DDF         FDD", // Y=9
                    "LD           DL", // Y=10
                    "LD    HIH    DL", // Y=11
                    "LDD         DDL", // Y=12
                    "L DD       DD L", // Y=13
                    "LLLDD     DDLLL", // Y=14
            },
            { // Z=15
                    "DDDDD     DDDDD", // Y=0
                    "DDDD       DDDD", // Y=1
                    "DDD         DDD", // Y=2
                    "DD    HIH    DD", // Y=3
                    "DD           DD", // Y=4
                    "DDF         FDD", // Y=5
                    "BD           D ", // Y=6
                    "BD           D ", // Y=7
                    "BD           D ", // Y=8
                    "DDF         FDD", // Y=9
                    "DD           DD", // Y=10
                    "DD    HIH    DD", // Y=11
                    "DDD         DDD", // Y=12
                    "DDDD       DDDD", // Y=13
                    "DDDDD     DDDDD", // Y=14
            },
            { // Z=16
                    "LLLDD     DDLLL", // Y=0
                    "LDDDAAAAAAADDDL", // Y=1
                    "LDDADDDDDDDADDL", // Y=2
                    "LDAACCHIHCCAADL", // Y=3
                    "LAAADDDDDDDAAAL", // Y=4
                    "DAAAAAAAAAAAAAD", // Y=5
                    "DEEFFFFFFFFFEED", // Y=6
                    "DD           DD", // Y=7
                    "DEEFFFFFFFFFEED", // Y=8
                    "DAAAAAAAAAAAAAD", // Y=9
                    "LAAADDDDDDDAAAL", // Y=10
                    "LDAACCHIHCCAADL", // Y=11
                    "LDDADDDDDDDADDL", // Y=12
                    "LDDDAAAAAAADDDL", // Y=13
                    "LLLDD     DDLLL", // Y=14
            },
            { // Z=17
                    "               ", // Y=0
                    "    AAAAAAA    ", // Y=1
                    "   AA     AA   ", // Y=2
                    "  AA GHIHG AA  ", // Y=3
                    " AAIJJKKKJJIAA ", // Y=4
                    " FF         FF ", // Y=5
                    "  E         E  ", // Y=6
                    "               ", // Y=7
                    "  E         E  ", // Y=8
                    " FF         FF ", // Y=9
                    " AAIJJKKKJJIAA ", // Y=10
                    "  AA GHIHG AA  ", // Y=11
                    "   AA     AA   ", // Y=12
                    "    AAAAAAA    ", // Y=13
                    "               ", // Y=14
            },
            { // Z=18
                    "               ", // Y=0
                    "    AAAAAAA    ", // Y=1
                    "   AA     AA   ", // Y=2
                    "  AA GHIHG AA  ", // Y=3
                    " AAIJJKKKJJIAA ", // Y=4
                    " FF         FF ", // Y=5
                    "  E         E  ", // Y=6
                    "               ", // Y=7
                    "  E         E  ", // Y=8
                    " FF         FF ", // Y=9
                    " AAIJJKKKJJIAA ", // Y=10
                    "  AA GHIHG AA  ", // Y=11
                    "   AA     AA   ", // Y=12
                    "    AAAAAAA    ", // Y=13
                    "               ", // Y=14
            },
            { // Z=19
                    "               ", // Y=0
                    "    AAAAAAA    ", // Y=1
                    "   AA     AA   ", // Y=2
                    "  AA GHIHG AA  ", // Y=3
                    " AAIJJKKKJJIAA ", // Y=4
                    " FF         FF ", // Y=5
                    "  E         E  ", // Y=6
                    "               ", // Y=7
                    "  E         E  ", // Y=8
                    " FF         FF ", // Y=9
                    " AAIJJKKKJJIAA ", // Y=10
                    "  AA GHIHG AA  ", // Y=11
                    "   AA     AA   ", // Y=12
                    "    AAAAAAA    ", // Y=13
                    "               ", // Y=14
            },
            { // Z=20
                    "LLLDD     DDLLL", // Y=0
                    "LDDDAAAAAAADDDL", // Y=1
                    "LDDADDDDDDDADDL", // Y=2
                    "LDAACCHIHCCAADL", // Y=3
                    "LAAADDDDDDDAAAL", // Y=4
                    "LAAAAAAAAAAAAAL", // Y=5
                    "DEEFFFFFFFFFEED", // Y=6
                    "DD           DD", // Y=7
                    "DEEFFFFFFFFFEED", // Y=8
                    "LAAAAAAAAAAAAAL", // Y=9
                    "LAAADDDDDDDAAAL", // Y=10
                    "LDAACCHIHCCAADL", // Y=11
                    "LDDADDDDDDDADDL", // Y=12
                    "LDDDAAAAAAADDDL", // Y=13
                    "LLLDD     DDLLL", // Y=14
            },
            { // Z=21
                    "DDDDD     DDDDD", // Y=0
                    "DDDD       DDDD", // Y=1
                    "DDD         DDD", // Y=2
                    "DD    HIH    DD", // Y=3
                    "DD           DD", // Y=4
                    "DDFF        FDD", // Y=5
                    "BD           DB", // Y=6
                    "BD           DB", // Y=7
                    "BD           DB", // Y=8
                    "DDF        FFDD", // Y=9
                    "DD           DD", // Y=10
                    "DD    HIH    DD", // Y=11
                    "DDD         DDD", // Y=12
                    "DDDD       DDDD", // Y=13
                    "DDDDD     DDDDD", // Y=14
            },
            { // Z=22
                    "LLLDD     DDLLL", // Y=0
                    "L DD       DD L", // Y=1
                    "LDD         DDL", // Y=2
                    "LD    HIH    DL", // Y=3
                    "LD           DL", // Y=4
                    "LDFF        FDL", // Y=5
                    "CD           DC", // Y=6
                    "CD           DC", // Y=7
                    "CD           DC", // Y=8
                    "LDF        FFDL", // Y=9
                    "LD           DL", // Y=10
                    "LD    HIH    DL", // Y=11
                    "LDD         DDL", // Y=12
                    "L DD       DD L", // Y=13
                    "LLLDD     DDLLL", // Y=14
            },
            { // Z=23
                    "DDDDD     DDDDD", // Y=0
                    "DDDD       DDDD", // Y=1
                    "DDD         DDD", // Y=2
                    "DD    HIH    DD", // Y=3
                    "DD           DD", // Y=4
                    "DDFF        FDD", // Y=5
                    "BD           DB", // Y=6
                    "BD           DB", // Y=7
                    "BD           DB", // Y=8
                    "DDF        FFDD", // Y=9
                    "DD           DD", // Y=10
                    "DD    HIH    DD", // Y=11
                    "DDD         DDD", // Y=12
                    "DDDD       DDDD", // Y=13
                    "DDDDD     DDDDD", // Y=14
            },
            { // Z=24
                    "LLLDD     DDLLL", // Y=0
                    "LDDDAAAAAAADDDL", // Y=1
                    "LDDADDDDDDDADDL", // Y=2
                    "LDAACCHIHCCAADL", // Y=3
                    "LAAADDDDDDDAAAL", // Y=4
                    "LAAAAAAAAAAAAAL", // Y=5
                    "DEEFFFFFFFFFEED", // Y=6
                    "DD           DD", // Y=7
                    "DEEFFFFFFFFFEED", // Y=8
                    "LAAAAAAAAAAAAAL", // Y=9
                    "LAAADDDDDDDAAAL", // Y=10
                    "LDAACCHIHCCAADL", // Y=11
                    "LDDADDDDDDDADDL", // Y=12
                    "LDDDAAAAAAADDDL", // Y=13
                    "LLLDD     DDLLL", // Y=14
            },
            { // Z=25
                    "               ", // Y=0
                    "    AAAAAAA    ", // Y=1
                    "   AA     AA   ", // Y=2
                    "  AA GHIHG AA  ", // Y=3
                    " AAIJJKKKJJIAA ", // Y=4
                    " FF         FF ", // Y=5
                    "  E         E  ", // Y=6
                    "               ", // Y=7
                    "  E         E  ", // Y=8
                    " FF         FF ", // Y=9
                    " AAIJJKKKJJIAA ", // Y=10
                    "  AA GHIHG AA  ", // Y=11
                    "   AA     AA   ", // Y=12
                    "    AAAAAAA    ", // Y=13
                    "               ", // Y=14
            },
            { // Z=26
                    "               ", // Y=0
                    "    AAAAAAA    ", // Y=1
                    "   AA     AA   ", // Y=2
                    "  AA GHIHG AA  ", // Y=3
                    " AAIJJKKKJJIAA ", // Y=4
                    " FF         FF ", // Y=5
                    "  E         E  ", // Y=6
                    "               ", // Y=7
                    "  E         E  ", // Y=8
                    " FF         FF ", // Y=9
                    " AAIJJKKKJJIAA ", // Y=10
                    "  AA GHIHG AA  ", // Y=11
                    "   AA     AA   ", // Y=12
                    "    AAAAAAA    ", // Y=13
                    "               ", // Y=14
            },
            { // Z=27
                    "               ", // Y=0
                    "    AAAAAAA    ", // Y=1
                    "   AA     AA   ", // Y=2
                    "  AA GHIHG AA  ", // Y=3
                    " AAIJJKKKJJIAA ", // Y=4
                    " FF         FF ", // Y=5
                    "  E         E  ", // Y=6
                    "               ", // Y=7
                    "  E         E  ", // Y=8
                    " FF         FF ", // Y=9
                    " AAIJJKKKJJIAA ", // Y=10
                    "  AA GHIHG AA  ", // Y=11
                    "   AA     AA   ", // Y=12
                    "    AAAAAAA    ", // Y=13
                    "               ", // Y=14
            },
            { // Z=28
                    "               ", // Y=0
                    "    AAAAAAA    ", // Y=1
                    "   ABBBBBBBA   ", // Y=2
                    "  AACCDDDCCAA  ", // Y=3
                    " AAABBBBBBBAAA ", // Y=4
                    " AAAAAAAAAAAAA ", // Y=5
                    " EEFFFFFFFFFEE ", // Y=6
                    "               ", // Y=7
                    " EEFFFFFFFFFEE ", // Y=8
                    " AAAAAAAAAAAAA ", // Y=9
                    " AAABBBBBBBAAA ", // Y=10
                    "  AACCDDDCCAA  ", // Y=11
                    "   ABBBBBBBA   ", // Y=12
                    "    AAAAAAA    ", // Y=13
                    "               ", // Y=14
            },
    };

    /**
     * 构建结构。控制器格 = <b>x=0, y=7, z=14</b>。
     *
     * <p>符号映射逐条对齐 {@code MultiBlockF.java:257~275}（见类注释的符号表）。</p>
     */
    public static BlockPattern build(MultiblockMachineDefinition definition) {
        TraceabilityPredicate hatchCasing = blocksOf("gtceu", "high_power_casing")
                // —— 常规 IO：GTO 用 autoThreadLaserAbilities(...)，这里展开成逐条能力。
                .or(ability(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                .or(ability(PartAbility.EXPORT_ITEMS).setPreviewCount(1))
                .or(ability(PartAbility.IMPORT_FLUIDS).setPreviewCount(1))
                .or(ability(PartAbility.EXPORT_FLUIDS).setPreviewCount(1))
                // —— 能源 / 激光（GTO 的 autoLaserAbilities）
                .or(ability(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(8, 0))
                .or(ability(PartAbility.INPUT_LASER).setMaxGlobalLimited(4, 1))
                // —— 线程仓（GTO 线程仓的等价物；GTO 原机允许 1 个）
                .or(ability(com.gtlmodular.common.data.tentacle.ThreadHatchMachines.THREAD_HATCH)
                        .setMaxGlobalLimited(1))
                // —— 超频仓 / 加速仓（本模组自建，GTO 同名能力的等价物；凡可插线程仓的机器都同样可插）
                .or(com.gtlmodular.common.data.fuel.OverclockHatches.predicate())
                .or(com.gtlmodular.common.data.fuel.AccelerateHatches.predicate())
                // —— 并行仓：GTO 原谓词为 setMaxGlobalLimited(1)，照搬
                .or(ability(PartAbility.PARALLEL_HATCH).setMaxGlobalLimited(1).setPreviewCount(1))
                // —— 维护仓：恰好 1（GTO setExactLimit(1)）
                .or(ability(PartAbility.MAINTENANCE).setExactLimit(1));

        return FactoryBlockPattern.start()
                .aisle(SLICES[0])
                .aisle(SLICES[1])
                .aisle(SLICES[2])
                .aisle(SLICES[3])
                .aisle(SLICES[4])
                .aisle(SLICES[5])
                .aisle(SLICES[6])
                .aisle(SLICES[7])
                .aisle(SLICES[8])
                .aisle(SLICES[9])
                .aisle(SLICES[10])
                .aisle(SLICES[11])
                .aisle(SLICES[12])
                .aisle(SLICES[13])
                .aisle(SLICES[14])
                .aisle(SLICES[15])
                .aisle(SLICES[16])
                .aisle(SLICES[17])
                .aisle(SLICES[18])
                .aisle(SLICES[19])
                .aisle(SLICES[20])
                .aisle(SLICES[21])
                .aisle(SLICES[22])
                .aisle(SLICES[23])
                .aisle(SLICES[24])
                .aisle(SLICES[25])
                .aisle(SLICES[26])
                .aisle(SLICES[27])
                .aisle(SLICES[28])
                .where('a', Predicates.controller(Predicates.blocks(definition.get())))
                // 高能耐压机械方块（gtocore 专有 ⇒ 本模组自造）
                .where('A', Predicates.blocks(FieldExtruderFactoryBlocks.HIGH_PRESSURE_RESISTANT_CASING.get()))
                .where('B', hatchCasing)
                .where('C', blocksOf("kubejs", "containment_field_generator"))
                .where('D', blocksOf("gtlcore", "molecular_casing"))
                // 氧化锂陶瓷防热抗震机械方块（gtocore 专有 ⇒ 本模组自造）
                .where('E', Predicates.blocks(
                        FieldExtruderFactoryBlocks.LITHIUM_OXIDE_CERAMIC_HEAT_RESISTANT_SHOCK_RESISTANT_MECHANICAL_CUBE.get()))
                .where('F', blocksOf("gtceu", "borosilicate_glass"))
                .where('G', blocksOf("gtlcore", "hyper_core"))
                // 加速管道（整合包已有 kubejs:accelerated_pipeline，贴图与 GTO 逐字节相同）
                .where('H', blocksOf("kubejs", "accelerated_pipeline"))
                .where('I', blocksOf("gtceu", "electrolytic_cell"))
                .where('J', blocksOf("gtceu", "fusion_glass"))
                .where('K', blocksOf("gtlcore", "hyper_mechanical_casing"))
                .where('L', blocksOf("gtceu", "tritanium_frame"))
                .where(' ', Predicates.any())
                .build();
    }

    // ------------------------------------------------------------------
    // 谓词辅助（与奈亚结构同款做法，但本包自洽、不跨类引用）
    // ------------------------------------------------------------------

    /**
     * 按 id 取方块并包成 {@link TraceabilityPredicate}，同时把预览候选设成该方块本身。
     *
     * <p><b>为什么不用自定义 lazy 谓词</b>：{@code Predicates.blocks(Block...)} 才能让
     * JEI / 结构预览把「这一格允许什么」画对。{@code build()} 是 memoize 的 supplier，
     * 到首次判定 / 首次预览才跑，那时所有模组方块都已注册。</p>
     */
    private static TraceabilityPredicate blocksOf(String namespace, String path) {
        Block block = blockById(new ResourceLocation(namespace, path));
        if (block == null) {
            GTLModular.LOGGER.error(
                    "[gtlmodular] 力场压模工厂结构：方块 {}/{} 取不到（该符号将永不匹配，请检查模组加载顺序）",
                    namespace, path);
            return new TraceabilityPredicate(state -> false, () -> new com.lowdragmc.lowdraglib.utils.BlockInfo[0]);
        }
        TraceabilityPredicate predicate = Predicates.blocks(block);
        com.lowdragmc.lowdraglib.utils.BlockInfo[] candidates = {
                com.lowdragmc.lowdraglib.utils.BlockInfo.fromBlockState(block.defaultBlockState())};
        for (com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate simple : predicate.common) {
            simple.candidates = () -> candidates;
        }
        return predicate;
    }

    /**
     * 能力谓词：{@link Predicates#abilities(PartAbility)}，但预览候选换成「一台代表仓」。
     *
     * <p>为什么必须换：{@code PredicateBlocks.candidates} 用 {@code BlockInfo.fromBlock} 会真的
     * {@code newBlockEntity(...)}；{@code IMPORT_ITEMS} 在本包挂着 gtlcore / gtladditions 的
     * ME 样板总成（构造 O(槽位²)），预览一构建就逐台实例化 ⇒ 已知的 40 秒卡死。
     * <b>匹配语义一个字没改</b>，候选只影响预览画哪台仓。</p>
     */
    private static TraceabilityPredicate ability(PartAbility ability) {
        if (ability.getAllBlocks().isEmpty()) {
            GTLModular.LOGGER.error(
                    "[gtlmodular] 力场压模工厂结构：PartAbility[{}] 下没有任何方块（该能力退化为永不匹配）",
                    ability.getName());
            return new TraceabilityPredicate(state -> false, () -> new com.lowdragmc.lowdraglib.utils.BlockInfo[0]);
        }
        TraceabilityPredicate predicate = Predicates.abilities(ability);
        Block representative = representative(ability);
        if (representative != null) {
            com.lowdragmc.lowdraglib.utils.BlockInfo[] candidates = {
                    com.lowdragmc.lowdraglib.utils.BlockInfo.fromBlockState(representative.defaultBlockState())};
            for (com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate simple : predicate.common) {
                simple.candidates = () -> candidates;
            }
            for (com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate simple : predicate.limited) {
                simple.candidates = () -> candidates;
            }
        }
        return predicate;
    }

    /** 能力 → 一台「代表仓」：按 ID 字典序取第一个，保证确定性（不随 HashSet 迭代顺序变）。 */
    private static Block representative(PartAbility ability) {
        Block best = null;
        String bestId = null;
        for (Block block : ability.getAllBlocks()) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null) continue;
            String key = id.toString();
            if (bestId == null || key.compareTo(bestId) < 0) {
                best = block;
                bestId = key;
            }
        }
        return best;
    }

    /** 按 id 取方块；注册表未就绪或方块不存在返回 {@code null}。 */
    private static Block blockById(ResourceLocation id) {
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        return (block == null || block == Blocks.AIR) ? null : block;
    }
}
