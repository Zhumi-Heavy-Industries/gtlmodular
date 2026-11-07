package com.gtlmodular.common.data.hpfc;

import com.gtlmodular.common.data.GTLModularMachines;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.List;

/**
 * GTO「高维等离子聚变核心」（{@code gtocore:hyperdimensional_plasma_fusion_core}）结构里
 * <b>我方整合包没有的方块</b> —— 只有 3 类、共 18 个方块 id：
 *
 * <table border="1">
 *   <caption>缺件 → 本模组新方块（贴图全部取自 GTO 原包，逐字节拷贝）</caption>
 *   <tr><th>GTO 方块</th><th>贴图（{@code gtocore:}）</th><th>本模组 id</th></tr>
 *   <tr><td>{@code abs_white_casing} … {@code abs_yellow_casing}（16 色）</td>
 *       <td>{@code block/casings/abs_*_casing.png}（16×16）</td>
 *       <td>{@code gtlmodular:abs_*_casing}（同名 16 色）</td></tr>
 *   <tr><td>{@code strontium_carbonate_ceramic_ray_absorbing_mechanical_cube}</td>
 *       <td>{@code block/strontium_carbonate_ceramic_ray_absorbing_mechanical_cube.png}（16×16）</td>
 *       <td>{@code gtlmodular:strontium_carbonate_ceramic_ray_absorbing_mechanical_cube}</td></tr>
 *   <tr><td>{@code cobalt_oxide_ceramic_strong_thermally_conductive_mechanical_block}</td>
 *       <td>{@code block/cobalt_oxide_ceramic_strong_thermally_conductive_mechanical_block.png}（16×16）</td>
 *       <td>{@code gtlmodular:cobalt_oxide_ceramic_strong_thermally_conductive_mechanical_block}</td></tr>
 * </table>
 *
 * <p><b>为什么是这 3 类</b>：结构 21 个符号位里，A/H/B 超维度机械方块、C 安普洛（=中子素）框架、
 * D 超临界外壳、G 聚变玻璃、I 高能机器外壳、J 分子线圈、K 硅岩框架、L 超能核心、
 * M 维度桥接方块、O 高强度混凝土、P 加热线圈、R 铼强化聚能玻璃、S 中空机械方块、
 * T 维度注入机械方块 —— 本包（{@code gtlcore} / {@code gtladditions} / {@code gtceu} / {@code kubejs}）
 * 都已有同名/等价方块；只有 ABS 机壳族和两件陶瓷方块是 GTO 独有。
 *
 * <p><b>贴图口径</b>：ABS 的源 {@code .png.mcmeta} 是 LDLib 的 CTM 声明
 * （{@code {"ldlib":{"connection":"gtocore:block/abs_*_casing_ctm"}}}），
 * 而 {@code gtocore} 不在整合包里 —— 本模组<b>不拷 mcmeta</b>，只拷 16×16 本体贴图，
 * 与恒星炎炀锻炉那轮的处理一致。两件陶瓷方块同理。
 *
 * <p><b>没有跑 runData</b>（本工程纪律）：blockstate / block model / item model / 掉落表
 * 全部手写在 {@code src/main/resources} 下随包发布。
 */
public final class HpfcBlocks {

    private HpfcBlocks() {
    }

    // ------------------------------------------------------------------
    // 16 色 ABS 塑料机械外壳（GTO id 原样照搬，含 GTO 的 abs_light_bull / abs_light_grey 拼写）
    // ------------------------------------------------------------------

    /** 白色 ABS 塑料机械外壳（16 色之底，其余颜色由它 + 染料化学浴得到）。 */
    public static final BlockEntry<Block> ABS_WHITE_CASING = casing("abs_white_casing");
    /** 黑色 ABS 塑料机械外壳。 */
    public static final BlockEntry<Block> ABS_BLACK_CASING = casing("abs_black_casing");
    /** 蓝色 ABS 塑料机械外壳。 */
    public static final BlockEntry<Block> ABS_BLUE_CASING = casing("abs_blue_casing");
    /** 棕色 ABS 塑料机械外壳。 */
    public static final BlockEntry<Block> ABS_BROWN_CASING = casing("abs_brown_casing");
    /** 青色 ABS 塑料机械外壳。 */
    public static final BlockEntry<Block> ABS_CYAN_CASING = casing("abs_cyan_casing");
    /** 绿色 ABS 塑料机械外壳。 */
    public static final BlockEntry<Block> ABS_GREEN_CASING = casing("abs_green_casing");
    /** 灰色 ABS 塑料机械外壳。 */
    public static final BlockEntry<Block> ABS_GREY_CASING = casing("abs_grey_casing");
    /** 浅蓝色 ABS 塑料机械外壳（GTO 原 id 是 {@code abs_light_bull_casing}，不是 light_blue）。 */
    public static final BlockEntry<Block> ABS_LIGHT_BULL_CASING = casing("abs_light_bull_casing");
    /** 浅灰色 ABS 塑料机械外壳（GTO 用英式 grey 拼写）。 */
    public static final BlockEntry<Block> ABS_LIGHT_GREY_CASING = casing("abs_light_grey_casing");
    /** 黄绿色 ABS 塑料机械外壳。 */
    public static final BlockEntry<Block> ABS_LIME_CASING = casing("abs_lime_casing");
    /** 品红色 ABS 塑料机械外壳。 */
    public static final BlockEntry<Block> ABS_MAGENTA_CASING = casing("abs_magenta_casing");
    /** 橙色 ABS 塑料机械外壳（结构符号 N 位用的就是这色）。 */
    public static final BlockEntry<Block> ABS_ORANGE_CASING = casing("abs_orange_casing");
    /** 粉色 ABS 塑料机械外壳。 */
    public static final BlockEntry<Block> ABS_PINK_CASING = casing("abs_pink_casing");
    /** 紫色 ABS 塑料机械外壳。 */
    public static final BlockEntry<Block> ABS_PURPLE_CASING = casing("abs_purple_casing");
    /** 红色 ABS 塑料机械外壳。 */
    public static final BlockEntry<Block> ABS_RED_CASING = casing("abs_red_casing");
    /** 黄色 ABS 塑料机械外壳。 */
    public static final BlockEntry<Block> ABS_YELLOW_CASING = casing("abs_yellow_casing");

    /**
     * 结构符号 {@code E} 的候选表：GTO {@code GTOPredicates.absBlocks()} 绑的是
     * {@code BlockMap.ABS_CASING} 全 16 色 —— 这里按同一集合登记（顺序与 GTO 一致：
     * 黑/蓝/棕/绿/灰/黄绿/橙/红/白/黄/青/品红/粉/紫/浅蓝/浅灰）。
     */
    public static final List<BlockEntry<Block>> ABS_CASINGS = List.of(
            ABS_BLACK_CASING,
            ABS_BLUE_CASING,
            ABS_BROWN_CASING,
            ABS_GREEN_CASING,
            ABS_GREY_CASING,
            ABS_LIME_CASING,
            ABS_ORANGE_CASING,
            ABS_RED_CASING,
            ABS_WHITE_CASING,
            ABS_YELLOW_CASING,
            ABS_CYAN_CASING,
            ABS_MAGENTA_CASING,
            ABS_PINK_CASING,
            ABS_PURPLE_CASING,
            ABS_LIGHT_BULL_CASING,
            ABS_LIGHT_GREY_CASING);

    /** 碳酸锶陶瓷射线吸收机械方块（GTO 同名方块，结构符号 {@code F}，364 格）。 */
    public static final BlockEntry<Block> STRONTIUM_CARBONATE_CERAMIC_RAY_ABSORBING_CUBE =
            casing("strontium_carbonate_ceramic_ray_absorbing_mechanical_cube");

    /** 氧化钴陶瓷坚固导热机械方块（GTO 同名方块，结构符号 {@code Q}，356 格）。 */
    public static final BlockEntry<Block> COBALT_OXIDE_CERAMIC_STRONG_THERMALLY_CONDUCTIVE_BLOCK =
            casing("cobalt_oxide_ceramic_strong_thermally_conductive_mechanical_block");

    /** 机械方块：铁块底子、金属声、8/10 强度（与 {@code StellarForgeBlocks} / {@code TentacleBlocks} 同口径）。 */
    private static BlockEntry<Block> casing(String name) {
        return GTLModularMachines.REGISTRATE.block(name, Block::new)
                .initialProperties(() -> Blocks.IRON_BLOCK)
                .properties((BlockBehaviour.Properties p) -> p
                        .strength(8.0F, 10.0F)
                        .sound(SoundType.METAL))
                .item()
                .build()
                .register();
    }

    /** 强制类加载（在机器注册链里调一次）。 */
    public static void init() {
        com.gtlmodular.GTLModular.LOGGER.info(
                "[gtlmodular] 高维等离子聚变核心·结构缺件方块已注册 18 个："
                        + "16 色 ABS 塑料机械外壳 / 碳酸锶陶瓷射线吸收机械方块 / 氧化钴陶瓷坚固导热机械方块");
    }
}
