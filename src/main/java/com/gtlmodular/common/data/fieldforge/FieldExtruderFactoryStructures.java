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
 * 力场压模零件工厂（{@code gtlmodular:field_extruder_factory}）的结构 —— GTO
 * {@code gtocore:field_extruder_factory} 的 <b>1:1 照搬</b>（方块替换见下表）。
 *
 * <h2>取证</h2>
 * <ul>
 *   <li>结构文件：GTO jar 内 {@code pattern/field_extruder_factory.mbs}（425 B，gzip 后 7,434 B）。</li>
 *   <li>格式：{@code FF FF FF FF | 0F | ver=1 | charDir=5 | stringDir=0 | aisleDir=2 |
 *       LEB(aisleCount=29) | LEB(stringsPerAisle=15)}，随后每行 {@code [0x00][1 字节长度][字符]}，
 *       片与片之间插 1 字节标记（0x0f）。解析器 {@code _scratch/tentacle/parse_mbs.py}
 *       ⇒ <b>0 字节剩余</b>（解析完整）。mbs 的 char/row/aisle 跨度 = 15 / 15 / 29。</li>
 *   <li>谓词绑定：{@code _gto_src/.../machines/MultiBlockF.java:249} 的 {@code FIELD_EXTRUDER_FACTORY}。</li>
 *   <li><b>轴向：★ 必须按 mbs 头部的方向字节重排，不能直转（2026-11-14 修正）</b>。
 *       本文件的头 3 字节是 {@code 05 00 02} = {@code charDir=BACK, stringDir=UP, aisleDir=LEFT}。
 *       GTO 的 {@code RelativeDirection} 枚举序号（{@code struct_报告.md §3.1}）：
 *       {@code 0=UP, 1=DOWN, 2=LEFT, 3=RIGHT, 4=FRONT, 5=BACK}；
 *       {@code Orientation.VEC}：{@code BACK=(0,0,+1)}、{@code UP=(0,+1,0)}、{@code LEFT=(-1,0,0)}。
 *       代入 {@code CompiledPiece} 的公式
 *       {@code x = char*vecChar[0] + row*vecString[0] + aisle*vecAisle[0]}（y、z 同理）得：
 *       <pre>x = -aisle　　y = row　　z = char</pre>
 *       ⇒ <b>mbs 的 aisle 轴对应世界 X（29 格），char 轴对应世界 Z（15 格）</b>，
 *       即 29 (X) × 15 (Y) × 15 (Z)。
 *       <br><b>踩过的坑</b>：初版照抄奈亚那份 mbs（头是 {@code 02 00 04} = LEFT/UP/FRONT，
 *       恰好「直转不颠倒」）的做法，把本文件也按直转处理 ⇒ X/Z 两轴互换，
 *       控制器被摆到 {@code x=0,z=14}（结构侧面内部），正脸埋进机体里。现已按上式重排。</li>
 * </ul>
 *
 * <h2>尺寸与符号</h2>
 * <p><b>29 (X) × 15 (Y) × 15 (Z) = 6,525 格</b>，其中实心 3,261 格、空气 3,264 格、控制器 1 格。
 *
 * <p><b>控制器位置（修正后）</b>：mbs {@code (char=0, row=7, aisle=14)} ⇒ 世界坐标
 * {@code x=14, y=7, z=14}（在我们 {@code SLICES[z][y]} 的写法里是 {@code SLICES[14][7]} 的第 14 个字符，
 * 即 {@code "    DBCBD   DBMBD   DBCBD    "} 里那个 {@code M}；
 * 写进 {@link #SLICES} 时按奈亚同口径换成 {@code a}，由 {@code .where('a', controller)} 绑定）。
 * 它落在<b>正面端面的正中</b> —— 最后一片（{@code SLICES[14]}）是三组并排的圆形端盖，
 * 控制器就在中间那组的圆心；背面（{@code SLICES[0]}）同形状但中间是空的。
 * <br><b>Z 轴方向</b>：按 GTCEu 原版惯例，<b>{@code .aisle(...)} 的最后一片 = 正面</b>
 * （与 {@code FactoryBlockPattern} 的 {@code structureDir} 一致：aisle 增 → 朝正面）。
 * 所以数据里控制器必须落在<b>最后一片</b>，否则控制器会朝进结构内部、正脸被埋。
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
 *   <tr><td>F</td><td>288</td><td>硼硅酸盐玻璃</td><td>{@code gtceu:borosilicate_glass_block}</td></tr>
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

    static final int WIDTH = 29;
    static final int HEIGHT = 15;
    static final int LENGTH = 15;

    private FieldExtruderFactoryStructures() {
    }

    /** 15 片 Z 层数据（{@code slices[z][y]}，{@code z=0} 最背面、{@code z=14} 正面 = 控制器所在）。 */
    public static String[][] slices() {
        return SLICES;
    }

    /**
     * 15 片 Z 层，每层 15 行（Y=0 在前），行宽 <b>29</b>。数据来自 GTO 的 .mbs，
     * <b>已按 mbs 头部方向字节重排</b>（见 {@link #build} 上方注释与类注释「轴向」一节）。
     */
    private static final String[][] SLICES = {
            { // Z=0
                    "    LDLDL   LDLDL   LDLDL    ", // Y=0
                    "    LDLDL   LDLDL   LDLDL    ", // Y=1
                    "    LDLDL   LDLDL   LDLDL    ", // Y=2
                    "    LDLDL   LDLDL   LDLDL    ", // Y=3
                    "    LDLDL   LDLDL   LDLDL    ", // Y=4
                    "    LDLDL   DDDDD   LDLDL    ", // Y=5
                    "    DBCBD   D   D   DBCBD    ", // Y=6
                    "    DBCBD   D   D   DBCBD    ", // Y=7
                    "    DBCBD   D   D   DBCBD    ", // Y=8
                    "    LDLDL   DDDDD   LDLDL    ", // Y=9
                    "    LDLDL   LDLDL   LDLDL    ", // Y=10
                    "    LDLDL   LDLDL   LDLDL    ", // Y=11
                    "    LDLDL   LDLDL   LDLDL    ", // Y=12
                    "    LDLDL   LDLDL   LDLDL    ", // Y=13
                    "    LDLDL   LDLDL   LDLDL    ", // Y=14
            },
            { // Z=1
                    "    LDLDL   LDLDL   LDLDL    ", // Y=0
                    "    DD DD   DD DD   DD DD    ", // Y=1
                    "    DDDDD   DDDDD   DDDDD    ", // Y=2
                    "    DDDDD   DDDDD   DDDDD    ", // Y=3
                    "AAAAADDDAAAAADDDAAAAADDDAAAAA", // Y=4
                    "AFFFADDDAFFFADDDAFFFADDDAFFFA", // Y=5
                    "E   EDDDE   EDDDE   EDDDE   E", // Y=6
                    "    DDDDD   DDDDD   DDDDD    ", // Y=7
                    "E   EDDDE   EDDDE   EDDDE   E", // Y=8
                    "AFFFADDDAFFFADDDAFFFADDDAFFFA", // Y=9
                    "AAAAADDDAAAAADDDAAAAADDDAAAAA", // Y=10
                    "    DDDDD   DDDDD   DDDDD    ", // Y=11
                    "    DDDDD   DDDDD   DDDDD    ", // Y=12
                    "    DD DD   DD DD   DD DD    ", // Y=13
                    "    LDLDL   LDLDL   LDLDL    ", // Y=14
            },
            { // Z=2
                    "    LDLDL   LDLDL   LDLDL    ", // Y=0
                    "    DDDDD   DDDDD   DDDDD    ", // Y=1
                    "    DDDDD   DDDDD   DDDDD    ", // Y=2
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=3
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=4
                    "AFFFAFFFAFFFAFFFAFFFAFFFAFFFA", // Y=5
                    "EEEEE   EEEEE   EEEEE   EEEEE", // Y=6
                    "                             ", // Y=7
                    "EEEEE   EEEEE   EEEEE   EEEEE", // Y=8
                    "AFFFAFFFAFFFAFFFAFFFAFFFAFFFA", // Y=9
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=10
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=11
                    "    DDDDD   DDDDD   DDDDD    ", // Y=12
                    "    DDDDD   DDDDD   DDDDD    ", // Y=13
                    "    LDLDL   LDLDL   LDLDL    ", // Y=14
            },
            { // Z=3
                    "    DDDDD   DDDDD   DDDDD    ", // Y=0
                    "    DDDDD   DDDDD   DDDDD    ", // Y=1
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=2
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=3
                    "AIIIA   AIIIA   AIIIA   AIIIA", // Y=4
                    "A   A   A   A   A   AFFFA   A", // Y=5
                    "F   F   F   F   F   F   F   F", // Y=6
                    "                             ", // Y=7
                    "F   F   F   F   F   F   F   F", // Y=8
                    "A   AFFFA   A   A   A   A   A", // Y=9
                    "AIIIA   AIIIA   AIIIA   AIIIA", // Y=10
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=11
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=12
                    "    DDDDD   DDDDD   DDDDD    ", // Y=13
                    "    DDDDD   DDDDD   DDDDD    ", // Y=14
            },
            { // Z=4
                    "    DDDDD   DDDDD   DDDDD    ", // Y=0
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=1
                    "BAAAD   DAAAD   DAAAD   DAAAB", // Y=2
                    "C   C   C   C   C   C   C   C", // Y=3
                    "BJJJD   DJJJD   DJJJD   DJJJB", // Y=4
                    "A   A   A   A   A   A   A   A", // Y=5
                    "F   F   F   F   F   F   F   F", // Y=6
                    "                             ", // Y=7
                    "F   F   F   F   F   F   F   F", // Y=8
                    "A   A   A   A   A   A   A   A", // Y=9
                    "BJJJD   DJJJD   DJJJD   DJJJB", // Y=10
                    "C   C   C   C   C   C   C   C", // Y=11
                    "BAAAD   DAAAD   DAAAD   DAAAB", // Y=12
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=13
                    "    DDDDD   DDDDD   DDDDD    ", // Y=14
            },
            { // Z=5
                    "                             ", // Y=0
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=1
                    "B   D   D   D   D   D   D   B", // Y=2
                    "CGGGC   CGGGC   CGGGC   CGGGC", // Y=3
                    "BJJJD   DJJJD   DJJJD   DJJJB", // Y=4
                    "A   A   A   A   A   A   A   A", // Y=5
                    "F   F   F   F   F   F   F   F", // Y=6
                    "                             ", // Y=7
                    "F   F   F   F   F   F   F   F", // Y=8
                    "A   A   A   A   A   A   A   A", // Y=9
                    "BJJJD   DJJJD   DJJJD   DJJJB", // Y=10
                    "CGGGC   CGGGC   CGGGC   CGGGC", // Y=11
                    "B   D   D   D   D   D   D   B", // Y=12
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=13
                    "                             ", // Y=14
            },
            { // Z=6
                    "                             ", // Y=0
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=1
                    "B   D   D   D   D   D   D   B", // Y=2
                    "DHHHHHHHHHHHHHHHHHHHHHHHHHHHD", // Y=3
                    "BKKKD   DKKKD   DKKKD   DKKKB", // Y=4
                    "A   A   A   A   A   A   A   A", // Y=5
                    "F   F   F   F   F   F   F   F", // Y=6
                    "                             ", // Y=7
                    "F   F   F   F   F   F   F   F", // Y=8
                    "A   A   A   A   A   A   A   A", // Y=9
                    "BKKKD   DKKKD   DKKKD   DKKKB", // Y=10
                    "DHHHHHHHHHHHHHHHHHHHHHHHHHHHD", // Y=11
                    "B   D   D   D   D   D   D   B", // Y=12
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=13
                    "                             ", // Y=14
            },
            { // Z=7
                    "                             ", // Y=0
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=1
                    "B   D   D   D   D   D   D   B", // Y=2
                    "DIIIIIIIIIIIIIIIIIIIIIIIIIIID", // Y=3
                    "BKKKD   DKKKD   DKKKD   DKKKB", // Y=4
                    "A   A   A   A   A   A   A   A", // Y=5
                    "F   F   F   F   F   F   F   F", // Y=6
                    "                             ", // Y=7
                    "F   F   F   F   F   F   F   F", // Y=8
                    "A   A   A   A   A   A   A   A", // Y=9
                    "BKKKD   DKKKD   DKKKD   DKKKB", // Y=10
                    "DIIIIIIIIIIIIIIIIIIIIIIIIIIID", // Y=11
                    "B   D   D   D   D   D   D   B", // Y=12
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=13
                    "                             ", // Y=14
            },
            { // Z=8
                    "                             ", // Y=0
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=1
                    "B   D   D   D   D   D   D   B", // Y=2
                    "DHHHHHHHHHHHHHHHHHHHHHHHHHHHD", // Y=3
                    "BKKKD   DKKKD   DKKKD   DKKKB", // Y=4
                    "A   A   A   A   A   A   A   A", // Y=5
                    "F   F   F   F   F   F   F   F", // Y=6
                    "                             ", // Y=7
                    "F   F   F   F   F   F   F   F", // Y=8
                    "A   A   A   A   A   A   A   A", // Y=9
                    "BKKKD   DKKKD   DKKKD   DKKKB", // Y=10
                    "DHHHHHHHHHHHHHHHHHHHHHHHHHHHD", // Y=11
                    "B   D   D   D   D   D   D   B", // Y=12
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=13
                    "                             ", // Y=14
            },
            { // Z=9
                    "                             ", // Y=0
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=1
                    "B   D   D   D   D   D   D   B", // Y=2
                    "CGGGC   CGGGC   CGGGC   CGGGC", // Y=3
                    "BJJJD   DJJJD   DJJJD   DJJJB", // Y=4
                    "A   A   A   A   A   A   A   A", // Y=5
                    "F   F   F   F   F   F   F   F", // Y=6
                    "                             ", // Y=7
                    "F   F   F   F   F   F   F   F", // Y=8
                    "A   A   A   A   A   A   A   A", // Y=9
                    "BJJJD   DJJJD   DJJJD   DJJJB", // Y=10
                    "CGGGC   CGGGC   CGGGC   CGGGC", // Y=11
                    "B   D   D   D   D   D   D   B", // Y=12
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=13
                    "                             ", // Y=14
            },
            { // Z=10
                    "    DDDDD   DDDDD   DDDDD    ", // Y=0
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=1
                    "BAAAD   DAAAD   DAAAD   DAAAB", // Y=2
                    "C   C   C   C   C   C   C   C", // Y=3
                    "BJJJD   DJJJD   DJJJD   DJJJB", // Y=4
                    "A   A   A   A   A   A   A   A", // Y=5
                    "F   F   F   F   F   F   F   F", // Y=6
                    "                             ", // Y=7
                    "F   F   F   F   F   F   F   F", // Y=8
                    "A   A   A   A   A   A   A   A", // Y=9
                    "BJJJD   DJJJD   DJJJD   DJJJB", // Y=10
                    "C   C   C   C   C   C   C   C", // Y=11
                    "BAAAD   DAAAD   DAAAD   DAAAB", // Y=12
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=13
                    "    DDDDD   DDDDD   DDDDD    ", // Y=14
            },
            { // Z=11
                    "    DDDDD   DDDDD   DDDDD    ", // Y=0
                    "    DDDDD   DDDDD   DDDDD    ", // Y=1
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=2
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=3
                    "AIIIA   AIIIA   AIIIA   AIIIA", // Y=4
                    "A   AFFFA   A   A   A   A   A", // Y=5
                    "F   F   F   F   F   F   F   F", // Y=6
                    "                             ", // Y=7
                    "F   F   F   F   F   F   F   F", // Y=8
                    "A   A   A   A   A   AFFFA   A", // Y=9
                    "AIIIA   AIIIA   AIIIA   AIIIA", // Y=10
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=11
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=12
                    "    DDDDD   DDDDD   DDDDD    ", // Y=13
                    "    DDDDD   DDDDD   DDDDD    ", // Y=14
            },
            { // Z=12
                    "    LDLDL   LDLDL   LDLDL    ", // Y=0
                    "    DDDDD   DDDDD   DDDDD    ", // Y=1
                    "    DDDDD   DDDDD   DDDDD    ", // Y=2
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=3
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=4
                    "AFFFAFFFAFFFAFFFAFFFAFFFAFFFA", // Y=5
                    "EEEEE   EEEEE   EEEEE   EEEEE", // Y=6
                    "                             ", // Y=7
                    "EEEEE   EEEEE   EEEEE   EEEEE", // Y=8
                    "AFFFAFFFAFFFAFFFAFFFAFFFAFFFA", // Y=9
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=10
                    "AAAAA   AAAAA   AAAAA   AAAAA", // Y=11
                    "    DDDDD   DDDDD   DDDDD    ", // Y=12
                    "    DDDDD   DDDDD   DDDDD    ", // Y=13
                    "    LDLDL   LDLDL   LDLDL    ", // Y=14
            },
            { // Z=13
                    "    LDLDL   LDLDL   LDLDL    ", // Y=0
                    "    DD DD   DD DD   DD DD    ", // Y=1
                    "    DDDDD   DDDDD   DDDDD    ", // Y=2
                    "    DDDDD   DDDDD   DDDDD    ", // Y=3
                    "AAAAADDDAAAAADDDAAAAADDDAAAAA", // Y=4
                    "AFFFADDDAFFFADDDAFFFADDDAFFFA", // Y=5
                    "E   EDDDE   EDDDE   EDDDE   E", // Y=6
                    "    DDDDD   DDDDD   DDDDD    ", // Y=7
                    "E   EDDDE   EDDDE   EDDDE   E", // Y=8
                    "AFFFADDDAFFFADDDAFFFADDDAFFFA", // Y=9
                    "AAAAADDDAAAAADDDAAAAADDDAAAAA", // Y=10
                    "    DDDDD   DDDDD   DDDDD    ", // Y=11
                    "    DDDDD   DDDDD   DDDDD    ", // Y=12
                    "    DD DD   DD DD   DD DD    ", // Y=13
                    "    LDLDL   LDLDL   LDLDL    ", // Y=14
            },
            { // Z=14
                    "    LDLDL   LDLDL   LDLDL    ", // Y=0
                    "    LDLDL   LDLDL   LDLDL    ", // Y=1
                    "    LDLDL   LDLDL   LDLDL    ", // Y=2
                    "    LDLDL   LDLDL   LDLDL    ", // Y=3
                    "    LDLDL   LDLDL   LDLDL    ", // Y=4
                    "    LDLDL   DDDDD   LDLDL    ", // Y=5
                    "    DBCBD   DBBBD   DBCBD    ", // Y=6
                    "    DBCBD   DBaBD   DBCBD    ", // Y=7
                    "    DBCBD   DBBBD   DBCBD    ", // Y=8
                    "    LDLDL   DDDDD   LDLDL    ", // Y=9
                    "    LDLDL   LDLDL   LDLDL    ", // Y=10
                    "    LDLDL   LDLDL   LDLDL    ", // Y=11
                    "    LDLDL   LDLDL   LDLDL    ", // Y=12
                    "    LDLDL   LDLDL   LDLDL    ", // Y=13
                    "    LDLDL   LDLDL   LDLDL    ", // Y=14
            },
    };


    /**
     * 构建结构。控制器格 = <b>x=14, y=7, z=14</b>（最后一片 = 正面端面正中）。
     *
     * <p>符号映射逐条对齐 {@code MultiBlockF.java:257~275}（见类注释的符号表）。</p>
     */
    public static BlockPattern build(MultiblockMachineDefinition definition) {
        // ★ 2026-11-14 前置自检：结构数据里出现的每个符号，下面都必须有对应的 .where(...)。
        //   否则 GTCEu 会在 FactoryBlockPattern.build() 里抛
        //   `IllegalStateException: Predicates for character(s) X are missing`，
        //   而那个异常是从 JEI 取 tooltip 时才触发的 —— 表现为「获取原料提示框时崩溃」，
        //   日志里只有一句 JEI 的 `Caught an error getting an Ingredient's tooltip`，
        //   排查成本极高。实测踩过：重排坐标时把 mbs 的 'M'（控制器）原样写进了数据，
        //   而 where 绑的是 'a' ⇒ 整个物品的 tooltip 一碰就崩。
        //   这里在交给 GTCEu 之前先自己点一遍名，报错直指「哪个字符没绑定」。
        verifySymbols();

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
                .where('a', Predicates.controller(Predicates.blocks(definition.get())))
                // 高能耐压机械方块（gtocore 专有 ⇒ 本模组自造）
                .where('A', Predicates.blocks(FieldExtruderFactoryBlocks.HIGH_PRESSURE_RESISTANT_CASING.get()))
                .where('B', hatchCasing)
                .where('C', blocksOf("kubejs", "containment_field_generator"))
                .where('D', blocksOf("gtlcore", "molecular_casing"))
                // 氧化锂陶瓷防热抗震机械方块（gtocore 专有 ⇒ 本模组自造）
                .where('E', Predicates.blocks(
                        FieldExtruderFactoryBlocks.LITHIUM_OXIDE_CERAMIC_HEAT_RESISTANT_SHOCK_RESISTANT_MECHANICAL_CUBE.get()))
                .where('F', blocksOf("gtceu", "borosilicate_glass_block"))
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

    /**
     * 结构数据用到的符号全集（必须与 {@link #build} 里的 {@code .where(...)} 一一对应）。
     *
     * <p>加字符时两处都要改：数据里出现它，且这里列进去并在 {@code build} 里绑谓词。
     * {@code ' '}（空气/任意）也是符号，同样在 {@code build} 里绑了 {@code Predicates.any()}。</p>
     */
    private static final String DECLARED_SYMBOLS = "aABCDEFGHIJKL ";

    /**
     * 自检：{@link #SLICES} 里出现的每个字符都必须在 {@link #DECLARED_SYMBOLS} 里。
     *
     * <p>失败即抛 {@link IllegalStateException} 并列出具体字符与首次出现位置，
     * 免得又变成「JEI 取 tooltip 时崩溃」这种难查的间接症状。</p>
     */
    private static void verifySymbols() {
        for (int z = 0; z < SLICES.length; z++) {
            String[] rows = SLICES[z];
            for (int y = 0; y < rows.length; y++) {
                String row = rows[y];
                for (int x = 0; x < row.length(); x++) {
                    char c = row.charAt(x);
                    if (DECLARED_SYMBOLS.indexOf(c) < 0) {
                        throw new IllegalStateException(String.format(
                                "力场压模零件工厂结构：符号 '%c'（0x%02X）在 SLICES[%d][%d] 第 %d 列出现，"
                                        + "但没有对应的 .where(...) 绑定。"
                                        + "DECLARED_SYMBOLS=%s。"
                                        + "请在该类里补 where 并把它加进 DECLARED_SYMBOLS。",
                                c, (int) c, z, y, x, DECLARED_SYMBOLS));
                    }
                }
            }
        }
        if (SLICES.length != LENGTH) {
            throw new IllegalStateException(String.format(
                    "力场压模零件工厂结构：SLICES 有 %d 片，但 LENGTH=%d", SLICES.length, LENGTH));
        }
        for (String[] rows : SLICES) {
            if (rows.length != HEIGHT) {
                throw new IllegalStateException(String.format(
                        "力场压模零件工厂结构：某片有 %d 行，但 HEIGHT=%d", rows.length, HEIGHT));
            }
            for (String row : rows) {
                if (row.length() != WIDTH) {
                    throw new IllegalStateException(String.format(
                            "力场压模零件工厂结构：某行宽 %d，但 WIDTH=%d", row.length(), WIDTH));
                }
            }
        }
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
            // ★ 2026-11-14：这里**必须炸**，不能只记 error 然后返回「永不匹配」的谓词。
            //   踩过的坑：F 位写成了 gtceu:borosilicate_glass（真实 id 是 ..._block），
            //   于是 F 位 288 格玻璃永远判不中 ⇒ 机器**怎么摆都不成形**，
            //   而现场表现只是「放了方块但结构不认」，日志里那条 error 很容易被忽略。
            //   结构配置错属于「代码 bug」，应该在第一次判定/预览时就以异常形式暴露出来。
            throw new IllegalStateException(String.format(
                    "力场压模零件工厂结构：方块 %s:%s 在注册表里取不到。"
                            + "该符号对应的格子将永远判不中、机器无法成形。"
                            + "请核对方块 id（注意 GTCEu 有些方块带 _block 后缀，"
                            + "如 borosilicate_glass_block）。",
                    namespace, path));
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
                    "[gtlmodular] 力场压模零件工厂结构：PartAbility[{}] 下没有任何方块（该能力退化为永不匹配）",
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
