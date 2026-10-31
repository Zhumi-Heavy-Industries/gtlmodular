package com.gtlmodular.common.data.tentacle;

import com.gtlmodular.common.block.NanochipComplexGlassBlock;
import com.gtlmodular.common.data.GTLModularMachines;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * GTO「奈亚拉托提普之触」（{@code gtocore:nyarlathoteps_tentacle}，165×48×49）结构里
 * <b>我方整合包没有的 4 种方块</b>—— 只造缺的那几件，其余 26 个符号位全部落到整合包已有的方块上
 * （{@code gtlcore:} / {@code gtladditions:} / {@code gtceu:} / {@code kubejs:}，见
 * {@link NyarlathotepsTentacleStructures} 的符号表）。
 *
 * <table border="1">
 *   <caption>缺件 → 本模组新方块（贴图全部取自 GTO 原包，逐字节拷贝）</caption>
 *   <tr><th>GTO 方块</th><th>贴图</th><th>本模组 id</th></tr>
 *   <tr><td>{@code gtocore:laser_casing} 激光机械方块</td>
 *       <td>{@code gtocore:block/laser_casing.png}（16×16，CTM 基底）</td>
 *       <td>{@code gtlmodular:laser_casing}</td></tr>
 *   <tr><td>{@code gtocore:amprosium_active_casing} 安普洛活性机械方块</td>
 *       <td>{@code gtocore:block/neutronium_active_casing.png}（16×256 动画 16 帧 @frametime 1）</td>
 *       <td>{@code gtlmodular:amprosium_active_casing}</td></tr>
 *   <tr><td>{@code gtocore:electron_permeable_amprosium_coated_glass} 电子渗透安普洛涂层玻璃</td>
 *       <td>{@code gtocore:block/casings/electron_permeable_neutronium_coated_glass.png}（16×16）</td>
 *       <td>{@code gtlmodular:electron_permeable_amprosium_coated_glass}</td></tr>
 *   <tr><td>{@code gtocore:quark_pipe} 夸克管道</td>
 *       <td>{@code gtocore:block/quark_pipe.png}（16×160 动画 10 帧 @frametime 2）</td>
 *       <td>{@code gtlmodular:quark_pipe}</td></tr>
 * </table>
 *
 * <p><b>为什么这 4 件必须自造</b>（2026-10-31 普查，证据见 {@code _scratch/tentacle/map_报告.md}）：
 * GTO 把<b>中子素</b>换名成了「安普洛」（{@code amprosium}，取证
 * {@code gtocore/common/data/material/GTMaterialExtend.java:42}：
 * {@code Neutronium = material("amprosium", "安普洛")}），因此
 * {@code amprosium_active_casing} 与 {@code electron_permeable_amprosium_coated_glass}
 * 的英文路径里写的是 {@code neutronium_*}；我方整合包有中子素材料，却<b>没有这几个方块</b>；
 * {@code laser_casing}、{@code quark_pipe} 同理（GTO 独有）。
 *
 * <p>两片玻璃（{@code laser_casing} 在 GTO 里是 glass casing）用
 * {@link NanochipComplexGlassBlock}：相邻同类玻璃共享面不重复绘制，与 NAC 的复合体玻璃同款处理。
 *
 * <p><b>没有跑 runData</b>（本工程纪律）：blockstate / block model / item model / 掉落表
 * 全部手写在 {@code src/main/resources} 下随包发布。
 */
public final class TentacleBlocks {

    private TentacleBlocks() {
    }

    /** 激光机械方块（GTO {@code laser_casing}）—— 玻璃质感，结构符号 {@code C}。 */
    public static final BlockEntry<NanochipComplexGlassBlock> LASER_CASING =
            glass("laser_casing");

    /**
     * 安普洛活性机械方块（GTO {@code amprosium_active_casing}，贴图 {@code neutronium_active_casing}）
     * —— 结构符号 {@code U}，16 帧动画（{@code frametime: 1}）。
     */
    public static final BlockEntry<Block> AMPROSIUM_ACTIVE_CASING =
            casing("amprosium_active_casing");

    /** 电子渗透安普洛涂层玻璃（GTO {@code electron_permeable_amprosium_coated_glass}）—— 结构符号 {@code S}。 */
    public static final BlockEntry<NanochipComplexGlassBlock> ELECTRON_PERMEABLE_AMPROSIUM_COATED_GLASS =
            glass("electron_permeable_amprosium_coated_glass");

    /** 夸克管道（GTO {@code quark_pipe}）—— 结构符号 {@code ]}，10 帧动画（{@code frametime: 2}）。 */
    public static final BlockEntry<Block> QUARK_PIPE =
            casing("quark_pipe");

    /** 机械方块：铁块底子、金属声、8/10 强度（与 NAC 结构方块同口径）。 */
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

    /** 玻璃类机械方块：透明、不遮挡、不阻光判定（与 {@code NanochipBlocks} 的玻璃同款属性）。 */
    private static BlockEntry<NanochipComplexGlassBlock> glass(String name) {
        return GTLModularMachines.REGISTRATE.block(name, NanochipComplexGlassBlock::new)
                .initialProperties(() -> Blocks.GLASS)
                .properties((BlockBehaviour.Properties p) -> p
                        .strength(8.0F, 10.0F)
                        .sound(SoundType.GLASS)
                        .noOcclusion()
                        .isValidSpawn((s, l, p2, e) -> false)
                        .isRedstoneConductor((s, l, p2) -> false)
                        .isSuffocating((s, l, p2) -> false)
                        .isViewBlocking((s, l, p2) -> false))
                .item()
                .build()
                .register();
    }

    /** 强制类加载（在机器注册链里调一次）。 */
    public static void init() {
        com.gtlmodular.GTLModular.LOGGER.info(
                "[gtlmodular] 奈亚拉托提普之触·结构缺件方块已注册 4 个："
                        + "激光机械方块 / 安普洛活性机械方块 / 电子渗透安普洛涂层玻璃 / 夸克管道");
    }
}
