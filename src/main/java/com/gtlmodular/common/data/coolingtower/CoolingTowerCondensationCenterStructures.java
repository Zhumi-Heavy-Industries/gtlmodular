package com.gtlmodular.common.data.coolingtower;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.tentacle.ThreadHatchMachines;
import com.gtlmodular.common.data.fuel.OverclockHatches;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 「冷却塔冷凝中枢」的结构 —— 从存档 {@code 琪露诺} 里实际建好的冷却塔 <b>1:1 导出</b>。
 *
 * <h2>结构来源（取证）</h2>
 * <ul>
 *   <li>存档：{@code saves/琪露诺/data/buildinggadgets2.dat}（Building Gadgets 2 复制记录，
 *       13×16×16，{@code statelist} 3328 格 / 非空气 1094 格）。</li>
 *   <li>世界实建位置：{@code x=5..17, y=-52..-37, z=-34..-19}（{@code r.0.-1.mca}）；
 *       旧控制器 {@code gtceu:cooling_tower[facing=west]} 位于 <b>{@code (10,-48,-27)}</b>，
 *       在本结构里是 {@code aisle=7 / row=4 / char=7}（= 模式原点）。</li>
 *   <li>逐格与存档比对：3328/3328 全等（含空气）。方块计数与截图材料清单逐项一致：
 *       寒冰机械方块 190、淡蓝色混凝土 314、蓝色羊毛 172、白色陶瓦 160、蓝色混凝土 88、
 *       白色羊毛 52、白色混凝土 36、染色玻璃 8+16、浮冰 14、蓝冰 12、霜冻机械方块 11……</li>
 * </ul>
 *
 * <h2>朝向（为什么数组长这样）</h2>
 * <p>GTCEu {@code FactoryBlockPattern.start()} 默认 {@code charDir=LEFT / stringDir=UP /
 * aisleDir=FRONT}。把解出的世界坐标代入 {@code BlockPattern#setActualRelativeOffset}：
 * 控制器朝 <b>西</b>（{@code facing=west}）时
 * <pre>世界偏移 = (-pz, py, px)</pre>
 * 即：<b>模式 +Z（aisle 递增）朝机器正面（西）</b>，模式 +X（char）朝南，+Y 朝上。
 * 因此数组是：
 * <ul>
 *   <li>{@code SLICES[aisle]}：aisle 0 = 最背面（世界东侧 x=17）→ aisle 12 = 最正面（x=5）；</li>
 *   <li>每片 16 行：行 0 = 最底层（y=-52）→ 行 15 = 最顶层（y=-37）；</li>
 *   <li>每行 16 字符：字符 0 = 世界 z=-34 → 字符 15 = z=-19。</li>
 * </ul>
 * 控制器在 {@code SLICES[7][4].charAt(7)}。
 *
 * <h2>符号表</h2>
 * <table border="1">
 *   <caption>19 个符号；'H' 就是「可替换成舱室 / 也可用霜冻机械方块堵上」的 11 格</caption>
 *   <tr><th>符号</th><th>方块</th><th>格数</th></tr>
 *   <tr><td>C</td><td>{@code gtlcore:cold_ice_casing}（寒冰机械方块）</td><td>190</td></tr>
 *   <tr><td>H</td><td>{@code gtceu:frostproof_machine_casing}（铝制防冻机械方块）
 *       <b>或任意仓室</b> + 并行仓 + 维护仓 + 激光仓 + <b>本模组线程仓</b></td><td>11</td></tr>
 *   <tr><td>M</td><td>控制器（{@code gtlmodular:cooling_tower_condensation_center}）</td><td>1</td></tr>
 *   <tr><td colspan="3">注：2026-11-10 按队长要求，结构里原有的 2 根橡木原木（选区角标）已删除，不再判定。</td></tr>
 *   <tr><td>2</td><td>淡蓝色染色玻璃</td><td>8</td></tr>
 *   <tr><td>3</td><td>蓝色染色玻璃</td><td>16</td></tr>
 *   <tr><td>4</td><td>浮冰</td><td>14</td></tr>
 *   <tr><td>5</td><td>蓝色羊毛</td><td>172</td></tr>
 *   <tr><td>6</td><td>白色陶瓦</td><td>160</td></tr>
 *   <tr><td>7</td><td>蓝冰</td><td>12</td></tr>
 *   <tr><td>8</td><td>白色混凝土</td><td>36</td></tr>
 *   <tr><td>9</td><td>蓝色混凝土</td><td>88</td></tr>
 *   <tr><td>A</td><td>淡蓝色混凝土</td><td>314</td></tr>
 *   <tr><td>B</td><td>淡蓝色羊毛</td><td>4</td></tr>
 *   <tr><td>D</td><td>棕色陶瓦</td><td>8</td></tr>
 *   <tr><td>E</td><td>白色羊毛</td><td>52</td></tr>
 *   <tr><td>F</td><td>红色混凝土</td><td>2</td></tr>
 *   <tr><td>G</td><td>黑色羊毛</td><td>4</td></tr>
 *   <tr><td>空格</td><td>不判定（{@code Predicates.any()}）</td><td>2234</td></tr>
 * </table>
 */
public final class CoolingTowerCondensationCenterStructures {

    /** 结构宽度（X，字符数）。 */
    public static final int WIDTH = 16;
    /** 结构高度（Y，每片行数）。 */
    public static final int HEIGHT = 16;
    /** 结构厚度（Z，片数 / aisle 数）。 */
    public static final int LENGTH = 13;

    private CoolingTowerCondensationCenterStructures() {
    }

    /** 结构层数据本体（{@code slices[aisle][row]}，只读）。 */
    public static String[][] slices() {
        return SLICES;
    }
    /** 13 片 Z 层（aisle，0 = 最背面 = 世界东侧），每层 16 行（0 = 最底层），行宽 16。 */
    private static final String[][] SLICES = {
            { // AISLE=0  (复制坐标 x=12)
                    "                ", // Y=0
                    "                ", // Y=1
                    "                ", // Y=2
                    "                ", // Y=3
                    "                ", // Y=4
                    "    5      5    ", // Y=5
                    "   555    555   ", // Y=6
                    "   555    555   ", // Y=7
                    "   555    555   ", // Y=8
                    "     555555     ", // Y=9
                    "   555555555    ", // Y=10
                    "  555555555555  ", // Y=11
                    "  555555555555  ", // Y=12
                    "  55555  55555  ", // Y=13
                    "   555    555   ", // Y=14
                    "                ", // Y=15
            },
            { // AISLE=1  (复制坐标 x=11)
                    "                ", // Y=0
                    "                ", // Y=1
                    "                ", // Y=2
                    "                ", // Y=3
                    "   55      55   ", // Y=4
                    "   555    555   ", // Y=5
                    "   55      55   ", // Y=6
                    "    55AAAA55    ", // Y=7
                    "     AAAAAA     ", // Y=8
                    "   55AAAAAA55   ", // Y=9
                    "  55555555555   ", // Y=10
                    " 5555AAAAAA5555 ", // Y=11
                    " 55555AAAA55555 ", // Y=12
                    " 55555555555555 ", // Y=13
                    " 555555  555555 ", // Y=14
                    "  555      555  ", // Y=15
            },
            { // AISLE=2  (复制坐标 x=10)
                    "     999999     ", // Y=0
                    "                ", // Y=1
                    "                ", // Y=2
                    "                ", // Y=3
                    "                ", // Y=4
                    "                ", // Y=5
                    "      AAAA      ", // Y=6
                    "     AAAAAA     ", // Y=7
                    "     AAAAAA     ", // Y=8
                    "    AAAAAAAA    ", // Y=9
                    "   5AAAAAAAA5   ", // Y=10
                    "  55AAAAAAAA55  ", // Y=11
                    "  5 AAAAAAAA 5  ", // Y=12
                    "  5  AAAAAA  5  ", // Y=13
                    "   5   55   5   ", // Y=14
                    "                ", // Y=15
            },
            { // AISLE=3  (复制坐标 x=9)
                    " 33 9      9 33 ", // Y=0
                    "2 44 999999 44 2", // Y=1
                    "   3443  3443   ", // Y=2
                    " 377 37  73 773 ", // Y=3
                    "347 7      7 743", // Y=4
                    "2  73 AAAA 37  2", // Y=5
                    "  44 AAAAAA 44  ", // Y=6
                    " 22 A666666A 22 ", // Y=7
                    "    A666666A    ", // Y=8
                    "   AA666666AA   ", // Y=9
                    "   AA666666AA   ", // Y=10
                    "   AA666666AA   ", // Y=11
                    "    ACCCCCCA    ", // Y=12
                    "    ACCCCCCA    ", // Y=13
                    "     AAAAAA     ", // Y=14
                    "                ", // Y=15
            },
            { // AISLE=4  (复制坐标 x=8)
                    "   9 EEEEEE 9   ", // Y=0
                    "    9EEEEEE9    ", // Y=1
                    "     999999     ", // Y=2
                    "     999999     ", // Y=3
                    "     999999     ", // Y=4
                    "     999999     ", // Y=5
                    "    A998899A    ", // Y=6
                    "   AA6CCCC6AA   ", // Y=7
                    "   AA6CCCC6AA   ", // Y=8
                    "   AA6CCCC6AA   ", // Y=9
                    "   AA6CCCC6AA   ", // Y=10
                    "   AA666666AA   ", // Y=11
                    "   AACCCCCCAA   ", // Y=12
                    "    ACCCCCCA    ", // Y=13
                    "     AAAAAA     ", // Y=14
                    "                ", // Y=15
            },
            { // AISLE=5  (复制坐标 x=7)
                    "   9 ECCCCE 9   ", // Y=0
                    "    9ECCCCE9    ", // Y=1
                    "  66 9CCCC9 66  ", // Y=2
                    "  6666CCCC6666  ", // Y=3
                    "   686CCCC686   ", // Y=4
                    "   8B6CCCC6B8   ", // Y=5
                    "    86CCCC68    ", // Y=6
                    "   AA6CCCC6AA   ", // Y=7
                    "   AA6CCCC6AA   ", // Y=8
                    "   AA6CCCC6AA   ", // Y=9
                    "   AA6CCCC6AA   ", // Y=10
                    "   AA666666AA   ", // Y=11
                    "   AACCCCCCAA   ", // Y=12
                    "    ACCCCCCA    ", // Y=13
                    "     AAAAAA     ", // Y=14
                    "                ", // Y=15
            },
            { // AISLE=6  (复制坐标 x=6)
                    "   9 ECCCCE 9   ", // Y=0
                    "    9ECCCCE9    ", // Y=1
                    "  66 9CCCC9 66  ", // Y=2
                    "  6666CCCC6666  ", // Y=3
                    "   686CCCC686   ", // Y=4
                    "   8B6CCCC6B8   ", // Y=5
                    "    86CCCC68    ", // Y=6
                    "   AA6CCCC6AA   ", // Y=7
                    "   AA6CCCC6AA   ", // Y=8
                    "   AA6CCCC6AA   ", // Y=9
                    "   AA6CCCC6AA   ", // Y=10
                    "   AA666666AA   ", // Y=11
                    "   AACCCCCCAA   ", // Y=12
                    "    ACCCCCCA    ", // Y=13
                    "     AAAAAA     ", // Y=14
                    "                ", // Y=15
            },
            { // AISLE=7  (复制坐标 x=5)
                    "   9 E6666E 9   ", // Y=0
                    "    9E6666E9    ", // Y=1
                    "     996699     ", // Y=2
                    "     9HHHH9     ", // Y=3
                    "     9HMHH9     ", // Y=4
                    "     8HHHH8     ", // Y=5
                    "    A898898A    ", // Y=6
                    "    A6CCCC6A    ", // Y=7
                    "   AA6CCCC6AA   ", // Y=8
                    "   AA6CCCC6AA   ", // Y=9
                    "   AA6CCCC6AA   ", // Y=10
                    "   AA666666AA   ", // Y=11
                    "   AACCCCCCAA   ", // Y=12
                    "    ACCCCCCA    ", // Y=13
                    "     AAAAAA     ", // Y=14
                    "                ", // Y=15
            },
            { // AISLE=8  (复制坐标 x=4)
                    "   99EEEEEE99   ", // Y=0
                    "    9EEEEEE9    ", // Y=1
                    "     999999     ", // Y=2
                    "                ", // Y=3
                    "                ", // Y=4
                    "                ", // Y=5
                    "    A      A    ", // Y=6
                    "    AF6666FA    ", // Y=7
                    "    A8A66A8A    ", // Y=8
                    "   AA8A66A8AA   ", // Y=9
                    "   AAGG66GGAA   ", // Y=10
                    "   AA666666AA   ", // Y=11
                    "    ACCCCCCA    ", // Y=12
                    "    AACCCCAA    ", // Y=13
                    "      AAAA      ", // Y=14
                    "                ", // Y=15
            },
            { // AISLE=9  (复制坐标 x=3)
                    "   8 EE  EE 8   ", // Y=0
                    "    8EE  EE8    ", // Y=1
                    "     998899     ", // Y=2
                    "                ", // Y=3
                    "                ", // Y=4
                    "                ", // Y=5
                    "     A    A     ", // Y=6
                    "    A      A    ", // Y=7
                    "    A      A    ", // Y=8
                    "    A      A    ", // Y=9
                    "    AA AA AA    ", // Y=10
                    "    AAAAAAAA    ", // Y=11
                    "    AAAAAAAA    ", // Y=12
                    "     AAAAAA     ", // Y=13
                    "                ", // Y=14
                    "                ", // Y=15
            },
            { // AISLE=10  (复制坐标 x=2)
                    "     66  66     ", // Y=0
                    "     668866     ", // Y=1
                    "     88  88     ", // Y=2
                    "                ", // Y=3
                    "                ", // Y=4
                    "                ", // Y=5
                    "                ", // Y=6
                    "                ", // Y=7
                    "                ", // Y=8
                    "                ", // Y=9
                    "                ", // Y=10
                    "                ", // Y=11
                    "                ", // Y=12
                    "                ", // Y=13
                    "                ", // Y=14
                    "                ", // Y=15
            },
            { // AISLE=11  (复制坐标 x=1)
                    "     EE  EE     ", // Y=0
                    "     EE  EE     ", // Y=1
                    "                ", // Y=2
                    "                ", // Y=3
                    "                ", // Y=4
                    "                ", // Y=5
                    "                ", // Y=6
                    "                ", // Y=7
                    "                ", // Y=8
                    "                ", // Y=9
                    "                ", // Y=10
                    "                ", // Y=11
                    "                ", // Y=12
                    "                ", // Y=13
                    "                ", // Y=14
                    "                ", // Y=15
            },
            { // AISLE=12  (复制坐标 x=0)
                    "     DD  DD     ", // Y=0
                    "     DD  DD     ", // Y=1
                    "                ", // Y=2
                    "                ", // Y=3
                    "                ", // Y=4
                    "                ", // Y=5
                    "                ", // Y=6
                    "                ", // Y=7
                    "                ", // Y=8
                    "                ", // Y=9
                    "                ", // Y=10
                    "                ", // Y=11
                    "                ", // Y=12
                    "                ", // Y=13
                    "                ", // Y=14
                    "                ", // Y=15
            },
    };

    /**
     * 结构谓词。'H' = 寒冰机械方块（塔身同款）<b>或</b> 铝制防冻机械方块（可堵住舱室位）
     * <b>或</b> 标准仓室 + 并行仓 + 维护仓 + 激光仓 + 本模组线程仓；其余符号见类注释符号表。
     * 2026-11-10：橡木原木角标已从结构里删除（那两格变成「不判定」）。
     */
    static BlockPattern build(MultiblockMachineDefinition definition) {
        FactoryBlockPattern pattern = FactoryBlockPattern.start();
        for (String[] slice : SLICES) {
            pattern.aisle(slice);
        }

        // 主外壳：寒冰机械方块（190 格）
        TraceabilityPredicate casing = blocksOf("gtlcore", "cold_ice_casing");

        // 舱室位（11 格）：默认就是铝制防冻机械方块（存档里已经堵成这个样子）；
        // 放仓时接受标准 IO / 能源 / 维护 / 并行 / 激光，以及本模组 7 档线程仓。
        // 2026-11-10：也接受寒冰机械方块（塔身同款、带连接纹理）⇒ 想整面墙连片就换成它。
        // 不用共享 casing 谓词（.where 会 sort 拷贝，避免同一实例被两处修改）
        TraceabilityPredicate hatch = blocksOf("gtlcore", "cold_ice_casing")
                // 2026-11-10：舱室位也接受寒冰机械方块（塔身同款，带 LDLib 连接纹理）
                // —— 想把控制器/舱室位这一面墙也连成一片，把这 11 格换成寒冰机械方块即可。
                .or(Predicates.blocks(blockOr("gtceu", "frostproof_machine_casing", Blocks.IRON_BLOCK)))
                .or(Predicates.autoAbilities(definition.getRecipeTypes()))
                .or(Predicates.abilities(PartAbility.PARALLEL_HATCH).setMaxGlobalLimited(1).setPreviewCount(1))
                .or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1))
                .or(Predicates.abilities(PartAbility.INPUT_LASER).setMaxGlobalLimited(1))
                .or(Predicates.abilities(ThreadHatchMachines.THREAD_HATCH).setMaxGlobalLimited(1))
                // 超频仓 17 档（对齐 GTO）：全局 1 台，现读其 S/E 取代普通超频。
                .or(OverclockHatches.predicate())
                .or(com.gtlmodular.common.data.fuel.AccelerateHatches.predicate());

        return pattern
                .where('C', casing)
                .where('H', hatch)
                .where('M', Predicates.controller(Predicates.blocks(definition.get())))
                .where('2', blocksOf("minecraft", "light_blue_stained_glass"))
                .where('3', blocksOf("minecraft", "blue_stained_glass"))
                .where('4', blocksOf("minecraft", "packed_ice"))
                .where('5', blocksOf("minecraft", "blue_wool"))
                .where('6', blocksOf("minecraft", "white_terracotta"))
                .where('7', blocksOf("minecraft", "blue_ice"))
                .where('8', blocksOf("minecraft", "white_concrete"))
                .where('9', blocksOf("minecraft", "blue_concrete"))
                .where('A', blocksOf("minecraft", "light_blue_concrete"))
                .where('B', blocksOf("minecraft", "light_blue_wool"))
                .where('D', blocksOf("minecraft", "brown_terracotta"))
                .where('E', blocksOf("minecraft", "white_wool"))
                .where('F', blocksOf("minecraft", "red_concrete"))
                .where('G', blocksOf("minecraft", "black_wool"))
                .where(' ', Predicates.any())
                .build();
    }

    /** 按 id 取方块；取不到退回惰性谓词（不会崩服，只在日志里报错）。 */
    private static TraceabilityPredicate blocksOf(String namespace, String path) {
        Block block = blockOr(namespace, path, null);
        if (block == null) {
            GTLModular.LOGGER.error(
                    "[gtlmodular] 冷却塔冷凝中枢结构：方块 {}/{} 取不到（该符号退化为永不匹配）",
                    namespace, path);
            ResourceLocation id = new ResourceLocation(namespace, path);
            return new TraceabilityPredicate(state -> {
                Block b = ForgeRegistries.BLOCKS.getValue(id);
                return b != null && state.getBlockState().getBlock() == b;
            }, () -> new com.lowdragmc.lowdraglib.utils.BlockInfo[0]);
        }
        return Predicates.blocks(block);
    }

    private static Block blockOr(String namespace, String path, Block fallback) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(namespace, path));
        return block == null || block == Blocks.AIR ? fallback : block;
    }
}
