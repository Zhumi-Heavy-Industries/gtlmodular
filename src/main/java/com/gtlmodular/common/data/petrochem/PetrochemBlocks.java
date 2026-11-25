package com.gtlmodular.common.data.petrochem;

import com.gtlmodular.common.data.GTLModularMachines;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 集成石油化工处理工厂 / 燃料精炼复合体 两套结构里<b>本整合包没有的方块</b> ——
 * GTO 用到、而 {@code gtceu} / {@code gtlcore} / {@code gtladditions} / Enhancedcore / 本模组
 * 都没有的 <b>8 件</b>。
 *
 * <h2>清单（GTO 原 id → 本模组 id，贴图逐字节取自 GTO jar）</h2>
 * <table border="1">
 *   <caption>8 件缺件；符号列是结构里的字符位</caption>
 *   <tr><th>符号</th><th>格数</th><th>GTO 方块</th><th>本模组 id</th></tr>
 *   <tr><td>{@code B}</td><td>274</td><td>{@code supercritical_turbine_casing}（超临界涡轮外壳）</td>
 *       <td>{@code gtlmodular:supercritical_turbine_casing}</td></tr>
 *   <tr><td>{@code G}</td><td>40</td><td>{@code pikyonium_machine_casing}（皮基翁合金机械方块）</td>
 *       <td>{@code gtlmodular:pikyonium_machine_casing}</td></tr>
 *   <tr><td>{@code V}</td><td>20</td><td>{@code chemical_grade_glass}（化学级玻璃）</td>
 *       <td>{@code gtlmodular:chemical_grade_glass}</td></tr>
 *   <tr><td>{@code [}</td><td>27</td><td>{@code iridium_pipe_casing}（铱管道机械方块）</td>
 *       <td>{@code gtlmodular:iridium_pipe_casing}</td></tr>
 *   <tr><td>{@code N}</td><td>424</td><td>{@code pps_corrosion_resistant_mechanical_housing}
 *       （PPS 耐腐蚀机械外壳）</td>
 *       <td>{@code gtlmodular:pps_corrosion_resistant_mechanical_housing}</td></tr>
 *   <tr><td>{@code R}</td><td>210</td><td>{@code high_strength_support_mechanical_casing}
 *       （高强度支撑机械方块）</td>
 *       <td>{@code gtlmodular:high_strength_support_mechanical_casing}</td></tr>
 *   <tr><td>{@code V}</td><td>268</td><td>{@code calcium_oxide_ceramic_anti_metal_corrosion_mechanical_block}
 *       （氧化钙陶瓷抗金属腐蚀机械方块）</td>
 *       <td>{@code gtlmodular:calcium_oxide_ceramic_anti_metal_corrosion_mechanical_block}</td></tr>
 *   <tr><td>{@code W}</td><td>24</td><td>{@code industrial_frameless_glass}（工业无框玻璃）</td>
 *       <td>{@code gtlmodular:industrial_frameless_glass}</td></tr>
 * </table>
 *
 * <h2>贴图口径</h2>
 * <p>8 张 PNG 从 GTO jar 逐字节拷贝（16×16 本体贴图），<b>不拷 {@code .mcmeta}</b>：
 * 源 mcmeta 是 LDLib 的 CTM 声明（指向 {@code gtocore:..._ctm}），而 gtocore 不在整合包里，
 * 拷了反而会变成缺图（与木化工厂 / 恒星炎炀锻炉 / 高维等离子聚变核心同一处理）。
 *
 * <p>两件玻璃（{@code chemical_grade_glass} 透明度 150~255、{@code industrial_frameless_glass}
 * 整片 ~151）按 {@code Blocks.GLASS} 口径注册（{@code noOcclusion()} + 不挡光 + 不窒息），
 * 模型写 {@code render_type: minecraft:translucent}，与 {@code NanochipBlocks.COMPLEX_GLASS} 同一套。
 *
 * <p>没有跑 {@code runData}（本工程纪律）：blockstate / 模型 / 掉落表全部手写在
 * {@code src/main/resources} 下随包发布。方块在 {@code GTLModularMachines.initBlocks()}
 * 里建条目（必须早于 Forge 的 {@code RegisterEvent<Block>}）。
 */
public final class PetrochemBlocks {

    private PetrochemBlocks() {
    }

    // ------------------------------------------------------------------ 集成石油化工处理工厂

    /** 超临界涡轮外壳（GTO 同名方块；结构符号 {@code B}，274 格）。 */
    public static final BlockEntry<Block> SUPERCRITICAL_TURBINE_CASING =
            casing("supercritical_turbine_casing");

    /** 皮基翁合金机械方块（GTO 同名方块；结构符号 {@code G}，40 格）。 */
    public static final BlockEntry<Block> PIKYONIUM_MACHINE_CASING =
            casing("pikyonium_machine_casing");

    /** 化学级玻璃（GTO 同名方块；结构符号 {@code V}，20 格）。 */
    public static final BlockEntry<Block> CHEMICAL_GRADE_GLASS =
            glass("chemical_grade_glass");

    /** 铱管道机械方块（GTO 同名方块；结构符号 {@code [}，27 格）。 */
    public static final BlockEntry<Block> IRIDIUM_PIPE_CASING =
            casing("iridium_pipe_casing");

    // ------------------------------------------------------------------ 燃料精炼复合体

    /** PPS 耐腐蚀机械外壳（GTO 同名方块；结构符号 {@code N}，424 格）。 */
    public static final BlockEntry<Block> PPS_CORROSION_RESISTANT_MECHANICAL_HOUSING =
            casing("pps_corrosion_resistant_mechanical_housing");

    /** 高强度支撑机械方块（GTO 同名方块；结构符号 {@code R}，210 格）。 */
    public static final BlockEntry<Block> HIGH_STRENGTH_SUPPORT_MECHANICAL_CASING =
            casing("high_strength_support_mechanical_casing");

    /** 氧化钙陶瓷抗金属腐蚀机械方块（GTO 同名方块；结构符号 {@code V}，268 格）。 */
    public static final BlockEntry<Block> CALCIUM_OXIDE_CERAMIC_CASING =
            casing("calcium_oxide_ceramic_anti_metal_corrosion_mechanical_block");

    /** 工业无框玻璃（GTO 同名方块；结构符号 {@code W}，24 格）。 */
    public static final BlockEntry<Block> INDUSTRIAL_FRAMELESS_GLASS =
            glass("industrial_frameless_glass");

    /** 机械方块：铁块底子、金属声、8/10 强度（与 {@code WoodFactoryBlocks} / {@code HpfcBlocks} 同口径）。 */
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

    /** 玻璃方块：{@code Blocks.GLASS} 底子、玻璃声、不挡光/不窒息（与 {@code NanochipBlocks.COMPLEX_GLASS} 同口径）。 */
    private static BlockEntry<Block> glass(String name) {
        return GTLModularMachines.REGISTRATE.block(name, Block::new)
                .initialProperties(() -> Blocks.GLASS)
                .properties((BlockBehaviour.Properties p) -> p
                        .strength(3.0F)
                        .sound(SoundType.GLASS)
                        .noOcclusion()
                        .isValidSpawn((s, l, pos, e) -> false)
                        .isRedstoneConductor((s, l, pos) -> false)
                        .isSuffocating((s, l, pos) -> false)
                        .isViewBlocking((s, l, pos) -> false))
                .item()
                .build()
                .register();
    }

    /** 强制类加载（在 {@code GTLModularMachines.initBlocks()} 里调一次）。 */
    public static void init() {
        com.gtlmodular.GTLModular.LOGGER.info(
                "[gtlmodular] 石化线·结构缺件方块已注册 8 个："
                        + "超临界涡轮外壳 / 皮基翁合金机械方块 / 化学级玻璃 / 铱管道机械方块 / "
                        + "PPS 耐腐蚀机械外壳 / 高强度支撑机械方块 / 氧化钙陶瓷抗金属腐蚀机械方块 / "
                        + "工业无框玻璃");
    }
}
