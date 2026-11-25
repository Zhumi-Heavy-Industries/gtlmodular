package com.gtlmodular.common.data.woodfactory;

import com.gtlmodular.common.data.GTLModularMachines;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 集成木质生物质工厂（{@code gtlmodular:wood_biomass_factory}）结构里
 * <b>本整合包没有的方块</b> —— GTO「木化工厂」（{@code gtocore:wood_distillation}）用到、
 * 而 {@code gtceu} / {@code gtlcore} / {@code gtladditions} / 本模组里都没有的 5 件。
 *
 * <h2>清单（GTO 原 id → 本模组 id，贴图逐字节取自 GTO 原包）</h2>
 * <table border="1">
 *   <caption>5 件缺件；符号列是结构里的字符位</caption>
 *   <tr><th>符号</th><th>格数</th><th>GTO 方块</th><th>GTO 贴图</th><th>本模组 id</th></tr>
 *   <tr><td>{@code C}</td><td>3476</td><td>{@code pressure_containment_casing}（压力容器机械方块）</td>
 *       <td>{@code gtocore:block/casings/pressure_containment_casing}</td>
 *       <td>{@code gtlmodular:pressure_containment_casing}</td></tr>
 *   <tr><td>{@code N}</td><td>302</td><td>{@code hastelloy_n_75_pipe}（哈斯特洛依合金-N75 油膜管道）</td>
 *       <td>{@code gtocore:block/hastelloy_n_75_pipe}</td>
 *       <td>{@code gtlmodular:hastelloy_n_75_pipe}</td></tr>
 *   <tr><td>{@code P}</td><td>39</td><td>{@code chemical_corrosion_resistant_pipe_casing}（耐化学腐蚀管道机械方块）</td>
 *       <td>{@code gtocore:block/casings/chemical_corrosion_resistant_pipe_casing}</td>
 *       <td>{@code gtlmodular:chemical_corrosion_resistant_pipe_casing}</td></tr>
 *   <tr><td>{@code U}</td><td>41</td><td>{@code high_pressure_pipe_casing}（耐高压管道机械方块）</td>
 *       <td>{@code gtocore:block/casings/high_pressure_pipe_casing}</td>
 *       <td>{@code gtlmodular:high_pressure_pipe_casing}</td></tr>
 *   <tr><td>{@code V}</td><td>92</td><td>{@code oil_gas_transportation_pipe_casing}（油气输送密封管道机械方块）</td>
 *       <td>{@code gtocore:block/casings/oil_gas_transportation_pipe_casing}</td>
 *       <td>{@code gtlmodular:oil_gas_transportation_pipe_casing}</td></tr>
 * </table>
 *
 * <h2>其余符号为什么不新造</h2>
 * <p>结构另外 20 个方块位在本包<b>全部现成</b>：
 * {@code A}=gutceu {@code stress_proof_casing}、{@code B}=gutlcore
 * {@code oxidation_resistant_hastelloy_n_mechanical_casing}、{@code D}={@code iridium_casing}、
 * {@code T}={@code process_machine_casing}（后三件 gtlcore 已有同 id 镜像）、
 * {@code E/F/L}=Ruridit / 不锈钢 / 硅岩合金<b>框架</b>、{@code G}=水密外壳、{@code H}=聚变玻璃、
 * {@code I}=ZPM 气密外壳、{@code J}=惰性机壳、{@code K}=任意加热线圈、{@code M}=防腐外壳、
 * {@code O}=电解槽、{@code R}=钢涡轮外壳、{@code S}=钨钢管道、{@code W}=聚四氟乙烯管道、
 * {@code X}=高能机壳（仓室面）、{@code Q}=消音仓、{@code a/b/t/d}=附属模块口（本文件之外，
 * 见 {@link WoodFactoryStructures}）。
 *
 * <h2>贴图口径</h2>
 * <p>5 张 PNG 从 GTO jar 逐字节拷贝（16×16 本体贴图），<b>不拷 {@code .mcmeta}</b>：
 * 源 mcmeta 是 LDLib 的 CTM 声明（指向 {@code gtocore:..._ctm}），而 gtocore 不在整合包里，
 * 拷了反而会变成缺图。与恒星炎炀锻炉 / 高维等离子聚变核心那两轮同一处理。
 * 许可与逐项清单见 {@code NOTICE_第三方素材_GTO.md} §3.10。
 *
 * <p>没有跑 {@code runData}（本工程纪律）：blockstate / 模型 / 掉落表全部手写在
 * {@code src/main/resources} 下随包发布。方块在 {@code GTLModularMachines.initBlocks()}
 * 里建条目（必须早于 Forge 的 {@code RegisterEvent<Block>}）。
 */
public final class WoodFactoryBlocks {

    private WoodFactoryBlocks() {
    }

    /** 压力容器机械方块（GTO 同名方块；结构符号 {@code C}，3476 格 —— 本机最大的外壳位）。 */
    public static final BlockEntry<Block> PRESSURE_CONTAINMENT_CASING =
            casing("pressure_containment_casing");

    /** 哈斯特洛依合金-N75 油膜管道（GTO 同名方块；结构符号 {@code N}，302 格）。 */
    public static final BlockEntry<Block> HASTELLOY_N_75_PIPE =
            casing("hastelloy_n_75_pipe");

    /** 耐化学腐蚀管道机械方块（GTO 同名方块；结构符号 {@code P}，39 格）。 */
    public static final BlockEntry<Block> CHEMICAL_CORROSION_RESISTANT_PIPE_CASING =
            casing("chemical_corrosion_resistant_pipe_casing");

    /** 耐高压管道机械方块（GTO 同名方块；结构符号 {@code U}，41 格）。 */
    public static final BlockEntry<Block> HIGH_PRESSURE_PIPE_CASING =
            casing("high_pressure_pipe_casing");

    /** 油气输送密封管道机械方块（GTO 同名方块；结构符号 {@code V}，92 格）。 */
    public static final BlockEntry<Block> OIL_GAS_TRANSPORTATION_PIPE_CASING =
            casing("oil_gas_transportation_pipe_casing");

    /** 机械方块：铁块底子、金属声、8/10 强度（与 {@code HpfcBlocks} / {@code TentacleBlocks} 同口径）。 */
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

    /** 强制类加载（在 {@code GTLModularMachines.initBlocks()} 里调一次）。 */
    public static void init() {
        com.gtlmodular.GTLModular.LOGGER.info(
                "[gtlmodular] 集成木质生物质工厂·结构缺件方块已注册 5 个："
                        + "压力容器机械方块 / 哈斯特洛依合金-N75 油膜管道 / 耐化学腐蚀管道机械方块 / "
                        + "耐高压管道机械方块 / 油气输送密封管道机械方块");
    }
}
