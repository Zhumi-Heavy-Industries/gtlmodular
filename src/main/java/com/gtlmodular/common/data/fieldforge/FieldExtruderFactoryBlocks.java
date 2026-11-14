package com.gtlmodular.common.data.fieldforge;

import com.gtlmodular.common.data.GTLModularMachines;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * GTO「力场压模工厂」（{@code gtocore:field_extruder_factory}）结构里
 * <b>本整合包没有的 2 种机壳方块</b> —— 与 {@link com.gtlmodular.common.data.stellar.StellarForgeBlocks}
 * 同一套「只补缺件」口径：结构里其余符号位全部落到整合包已有的方块上。
 *
 * <table border="1">
 *   <caption>缺件 → 本模组新方块（贴图全部取自 GTO 原包，逐字节拷贝）</caption>
 *   <tr><th>GTO 方块</th><th>贴图（{@code gtocore:}）</th><th>本模组 id</th><th>结构格数</th></tr>
 *   <tr><td>{@code high_pressure_resistant_casing} 高能耐压机械方块</td>
 *       <td>{@code block/high_pressure_resistant_casing.png}（16×16，705 B）</td>
 *       <td>{@code gtlmodular:high_pressure_resistant_casing}</td><td>968（A）</td></tr>
 *   <tr><td>{@code lithium_oxide_ceramic_heat_resistant_shock_resistant_mechanical_cube}
 *       氧化锂陶瓷防热抗震机械方块</td>
 *       <td>{@code block/lithium_oxide_ceramic_heat_resistant_shock_resistant_mechanical_cube.png}
 *       （16×16，871 B）</td>
 *       <td>{@code gtlmodular:lithium_oxide_ceramic_heat_resistant_shock_resistant_mechanical_cube}</td>
 *       <td>112（E）</td></tr>
 * </table>
 *
 * <h2>第三件「加速管道」为什么不在本类</h2>
 * <p>GTO 的 {@code accelerated_pipeline}（加速管道）<b>本整合包已经有了</b>：
 * KubeJS 在 {@code startup_scripts/block_registration.js:102}
 * （{@code basicMetal("accelerated_pipeline").requiresTool(true)}）注册为
 * <b>{@code kubejs:accelerated_pipeline}</b>，且贴图与 GTO 原包<b>逐字节相同</b>
 * （md5 {@code 78fdc92d3f3a9e1368985b27be922f6e}，已核对），
 * 并在 {@code server_scripts/circuit_boards.js:241} 挂了装配机产能配方。
 * ⇒ 结构 H 位直接引用 {@code kubejs:accelerated_pipeline}，无需再造。</p>
 *
 * <p><b>为什么这 2 件必须自造</b>：GTO（{@code gtocore}）并不在整合包里，它把力场压模工厂的
 * 机壳符号位绑到了自己的方块上；整合包只有 {@code gtlcore} / {@code gtladditions}。
 * 逐 id 在实例 {@code logs/debug.log} 与 gtlcore / gtladditions jar 里核过，两个模组都没有这两个方块。</p>
 *
 * <p>贴图来源核对口径（照 {@code StellarForgeBlocks}）：先读 GTO 的
 * {@code assets/gtocore/models/block/<gtoid>.json} 里的 {@code textures.all}，再按该路径逐字节拷成
 * 我们的 {@code block/casings/<id>.png}。源 {@code .png.mcmeta} 是 LDLib 的 CTM 声明
 * （{@code {"ldlib": {"connection": "gtocore:block/..._ctm"}}}），本模组<b>不拷</b>
 * —— 那些 {@code gtocore:} 精灵在本包里根本不存在。
 * （{@code high_pressure_resistant_casing} 本来就没有 mcmeta。）</p>
 *
 * <p><b>没有跑 runData</b>（本工程纪律）：blockstate / block model / item model / 掉落表
 * 全部手写在 {@code src/main/resources} 下随包发布。</p>
 */
public final class FieldExtruderFactoryBlocks {

    private FieldExtruderFactoryBlocks() {
    }

    /** 高能耐压机械方块（GTO {@code high_pressure_resistant_casing}）—— 结构 A 位，968 格，最大一块。 */
    public static final BlockEntry<Block> HIGH_PRESSURE_RESISTANT_CASING =
            casing("high_pressure_resistant_casing");

    /**
     * 氧化锂陶瓷防热抗震机械方块
     * （GTO {@code lithium_oxide_ceramic_heat_resistant_shock_resistant_mechanical_cube}）—— 结构 E 位，112 格。
     */
    public static final BlockEntry<Block> LITHIUM_OXIDE_CERAMIC_HEAT_RESISTANT_SHOCK_RESISTANT_MECHANICAL_CUBE =
            casing("lithium_oxide_ceramic_heat_resistant_shock_resistant_mechanical_cube");

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
                "[gtlmodular] 力场压模工厂·结构缺件方块已注册 2 个："
                        + "高能耐压机械方块 / 氧化锂陶瓷防热抗震机械方块"
                        + "（加速管道复用整合包已有的 kubejs:accelerated_pipeline）");
    }
}
