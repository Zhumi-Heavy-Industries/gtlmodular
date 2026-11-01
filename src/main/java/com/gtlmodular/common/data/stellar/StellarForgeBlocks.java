package com.gtlmodular.common.data.stellar;

import com.gtlmodular.common.data.GTLModularMachines;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * GTO「恒星炎炀锻炉」（{@code gtocore:stellar_forge}）结构里
 * <b>我方整合包没有的 6 种机壳方块</b>—— 只造缺的那几件，其余符号位全部落到整合包已有的方块上。
 *
 * <table border="1">
 *   <caption>缺件 → 本模组新方块（贴图全部取自 GTO 原包，逐字节拷贝）</caption>
 *   <tr><th>GTO 方块</th><th>贴图（{@code gtocore:}）</th><th>本模组 id</th></tr>
 *   <tr><td>{@code radiation_absorbent_casing} 辐射吸收机械方块</td>
 *       <td>{@code block/radiation_absorbent_casing.png}（16×16）</td>
 *       <td>{@code gtlmodular:radiation_absorbent_casing}</td></tr>
 *   <tr><td>{@code boron_carbide_ceramic_radiation_resistant_mechanical_cube} 碳化硼陶瓷耐辐射机械方块</td>
 *       <td>{@code block/boron_carbide_ceramic_radiation_resistant_mechanical_cube.png}（16×16）</td>
 *       <td>{@code gtlmodular:boron_carbide_ceramic_radiation_resistant_mechanical_cube}</td></tr>
 *   <tr><td>{@code magnesium_oxide_ceramic_high_temperature_insulation_mechanical_block} 氧化镁陶瓷高温绝缘机械方块</td>
 *       <td>{@code block/magnesium_oxide_ceramic_high_temperature_insulation_mechanical_block.png}（16×16）</td>
 *       <td>{@code gtlmodular:magnesium_oxide_ceramic_high_temperature_insulation_mechanical_block}</td></tr>
 *   <tr><td>{@code magtech_casing} 磁力机械方块</td>
 *       <td>{@code block/casings/magtech_casing.png}（16×16）</td>
 *       <td>{@code gtlmodular:magtech_casing}</td></tr>
 *   <tr><td>{@code plasma_heater_casing} 等离子加热机械方块</td>
 *       <td>{@code block/plasma_heater_casing.png}（16×48 动画 3 帧）</td>
 *       <td>{@code gtlmodular:plasma_heater_casing}</td></tr>
 *   <tr><td>{@code neutronium_stable_casing} 中子稳定机械方块</td>
 *       <td>{@code block/neutronium_stable_casing.png}（16×16）</td>
 *       <td>{@code gtlmodular:neutronium_stable_casing}</td></tr>
 * </table>
 *
 * <p><b>为什么这 6 件必须自造</b>：GTO（{@code gtocore}）并不在整合包里，它把恒星炎炀锻炉
 * 的机壳符号位绑到了自己的这 6 个方块上；整合包只有 {@code gtlcore} / {@code gtladditions}，
 * 因此按「只补缺件」的口径在本模组重建。
 *
 * <p>贴图来源核对口径：先读 GTO 的 {@code assets/gtocore/models/block/<gtoid>.json} 里的
 * {@code textures.all}，再按该路径逐字节拷成我们的 {@code block/casings/<id>.png}
 * （注意 {@code magtech_casing} 在 GTO 里就放在 {@code block/casings/} 下，其余 5 件在
 * {@code block/} 根下）。源 {@code .png.mcmeta} 基本是 LDLib 的 CTM 声明
 * （{@code {"ldlib": {"connection": "gtocore:block/..._ctm"}}}），本模组<b>不拷</b>
 * —— 那些 {@code gtocore:} 精灵在本包里根本不存在。只有 {@code plasma_heater_casing}
 * 的 mcmeta 带 {@code animation}，才保留下来，且只保留 {@code animation} 段。
 *
 * <p><b>没有跑 runData</b>（本工程纪律）：blockstate / block model / item model / 掉落表
 * 全部手写在 {@code src/main/resources} 下随包发布。
 */
public final class StellarForgeBlocks {

    private StellarForgeBlocks() {
    }

    /** 辐射吸收机械方块（GTO {@code radiation_absorbent_casing}）。 */
    public static final BlockEntry<Block> RADIATION_ABSORBENT_CASING =
            casing("radiation_absorbent_casing");

    /** 碳化硼陶瓷耐辐射机械方块（GTO {@code boron_carbide_ceramic_radiation_resistant_mechanical_cube}）。 */
    public static final BlockEntry<Block> BORON_CARBIDE_CERAMIC_RADIATION_RESISTANT_MECHANICAL_CUBE =
            casing("boron_carbide_ceramic_radiation_resistant_mechanical_cube");

    /** 氧化镁陶瓷高温绝缘机械方块（GTO {@code magnesium_oxide_ceramic_high_temperature_insulation_mechanical_block}）。 */
    public static final BlockEntry<Block> MAGNESIUM_OXIDE_CERAMIC_HIGH_TEMPERATURE_INSULATION_MECHANICAL_BLOCK =
            casing("magnesium_oxide_ceramic_high_temperature_insulation_mechanical_block");

    /** 磁力机械方块（GTO {@code magtech_casing}，贴图在 GTO 的 {@code block/casings/} 子目录）。 */
    public static final BlockEntry<Block> MAGTECH_CASING =
            casing("magtech_casing");

    /**
     * 等离子加热机械方块（GTO {@code plasma_heater_casing}）
     * —— 贴图 16×48，3 帧动画（{@code frametime: 20}，{@code interpolate: true}），
     * 随包附带 {@code plasma_heater_casing.png.mcmeta}。
     */
    public static final BlockEntry<Block> PLASMA_HEATER_CASING =
            casing("plasma_heater_casing");

    /** 中子稳定机械方块（GTO {@code neutronium_stable_casing}）。 */
    public static final BlockEntry<Block> NEUTRONIUM_STABLE_CASING =
            casing("neutronium_stable_casing");

    /** 机械方块：铁块底子、金属声、8/10 强度（与 {@link com.gtlmodular.common.data.tentacle.TentacleBlocks} 同口径）。 */
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
                "[gtlmodular] 恒星炎炀锻炉·结构缺件方块已注册 6 个："
                        + "辐射吸收机械方块 / 碳化硼陶瓷耐辐射机械方块 / 氧化镁陶瓷高温绝缘机械方块 / "
                        + "磁力机械方块 / 等离子加热机械方块 / 中子稳定机械方块");
    }
}
