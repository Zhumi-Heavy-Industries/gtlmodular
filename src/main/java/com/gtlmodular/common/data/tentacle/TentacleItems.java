package com.gtlmodular.common.data.tentacle;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularMachines;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;

/**
 * 奈亚拉托提普之触 · 配套物品（2026-11-01 队长口径）。
 *
 * <p>这三个件是 GTO 的研究数据链中间物，也是「集控核心」的前置：
 * <ol>
 *   <li>{@code gtlmodular:cerebrum} 大脑 —— GTO 里由「生物提取机」从生物蛋提取；
 *       <b>本模组改成 GTCEu 提取机（{@code gtceu:extractor}）+ 任意生物蛋</b>
 *       （tag {@code gtlmodular:spawn_eggs}，含 1.20.1 全部原版生物蛋 + 常见模组生物蛋）；</li>
 *   <li>{@code gtlmodular:super_cerebrum} 超级大脑 —— 大脑 + 超能硅岩粉 + 突变剂/生物质（孵化器步骤，我方用化学反应釜替代）；</li>
 *   <li>{@code gtlmodular:neural_matrix} 神经矩阵 —— 电路组装机：生物电路板 + UHV 电路 + 超级大脑×2 +
 *       神经处理单元 + 高级SoC + 细线（钌凯金镅中子素氧化物）。</li>
 * </ol>
 *
 * <p>贴图逐字节取自 GTOCore（{@code assets/gtocore/textures/item/{cerebrum,super_cerebrum,neural_matrix}.png}，
 * 前两张静态、神经矩阵自带 {@code .mcmeta} 动画），许可见 {@code NOTICE_第三方素材_GTO.md}。
 *
 * <p>注册时序：必须从 {@code GTLModularMachines.initBlocks()} 调用（Forge 的 {@code RegisterEvent<Item>} 之前）。
 */
public final class TentacleItems {

    private TentacleItems() {
    }

    /** 大脑（GTO {@code cerebrum}）。 */
    public static final ItemEntry<Item> CEREBRUM =
            GTLModularMachines.REGISTRATE.item("cerebrum", Item::new).register();

    /** 超级大脑（GTO {@code super_cerebrum}）。 */
    public static final ItemEntry<Item> SUPER_CEREBRUM =
            GTLModularMachines.REGISTRATE.item("super_cerebrum", Item::new).register();

    /** 神经矩阵（GTO {@code neural_matrix}，研究数据介质第 4 级）。 */
    public static final ItemEntry<Item> NEURAL_MATRIX =
            GTLModularMachines.REGISTRATE.item("neural_matrix", Item::new).register();

    // ------------------------------------------------------------------ 研究/计算件（2026-11-01，0.3.270）
    //
    // 这批件在 GTOCore 里是「超算部件」（ExResearchMachines 的 HPCA part，方块机器），
    // 本模组按队长口径做成 gtlmodular: 物品，继续沿「研究数据链」当中间物用。
    // 贴图逐字节取自 GTO jar：
    //   ① 物品贴图（GTO 本来就是 item）：
    //      item/obsidian_matrix.png (128×2048, 16 帧)、
    //      item/relativistic_spinorial_memory_system.png (16×288, 18 帧)、
    //      item/rydberg_spinorial_assembly.png (16×256, 16 帧)；
    //   ② 方块面贴图（GTO 里是机器方块，取 block/casings/about_computer/ 的正面）：
    //      nich_empty_component.png / nich_computing_components.png（16×16 静态）、
    //      gwca_empty_component.png / gwca_computing_components.png（16×192, 12 帧动画）。

    /** NICH 空组件（GTO {@code gtocore:nich_empty_component}）。 */
    public static final ItemEntry<Item> NICH_EMPTY_COMPONENT =
            item("nich_empty_component");

    /** NICH 计算组件（GTO {@code gtocore:nich_computing_components}，GTO 里名字是复数）。 */
    public static final ItemEntry<Item> NICH_COMPUTING_COMPONENT =
            item("nich_computing_component");

    /** GWCA 空组件（GTO {@code gtocore:gwca_empty_component}）。 */
    public static final ItemEntry<Item> GWCA_EMPTY_COMPONENT =
            item("gwca_empty_component");

    /** GWCA 计算组件（GTO {@code gtocore:gwca_computing_components}，GTO 里名字是复数）。 */
    public static final ItemEntry<Item> GWCA_COMPUTING_COMPONENT =
            item("gwca_computing_component");

    /** 黑曜石矩阵（GTO {@code gtocore:obsidian_matrix}，T5 研究数据介质）。 */
    public static final ItemEntry<Item> OBSIDIAN_MATRIX =
            item("obsidian_matrix");

    /** 相对论旋量存储器（GTO {@code gtocore:relativistic_spinorial_memory_system}）。 */
    public static final ItemEntry<Item> RELATIVISTIC_SPINORIAL_MEMORY_SYSTEM =
            item("relativistic_spinorial_memory_system");

    /** 里德堡旋量集群（GTO {@code gtocore:rydberg_spinorial_assembly}）。 */
    public static final ItemEntry<Item> RYDBERG_SPINORIAL_ASSEMBLY =
            item("rydberg_spinorial_assembly");

    // ------------------------------------------------------------------ 集控核心（2026-11-01，0.3.271）
    //
    // GTO 的 CraftingComponents.INTEGRATED_CONTROL_CORE（UV..OpV 六档 + MAX 一档）。
    // 本模组按 0.3.271 施工规格改名成 integrated_control_core_<tier>
    // （GTO 原名是 <tier>_integrated_control_core）。
    // 贴图逐字节取自 GTOCore jar：assets/gtocore/textures/item/<tier>_integrated_control_core.png
    // （7 张全部带 .mcmeta 动画，.mcmeta 一并复制），许可见 NOTICE_第三方素材_GTO.md。
    //
    // 配方：A=装配线（带研究门）、B=部件装配线，都在 TentacleRecipes 里运行期注入。
    // MAX 档的配方暂不注入（ChaosInfinityAlloy / SpacetimeContinuum 本包缺），物品先注册。

    /** UV 集控核心。 */
    public static final ItemEntry<Item> INTEGRATED_CONTROL_CORE_UV =
            item("integrated_control_core_uv");

    /** UHV 集控核心。 */
    public static final ItemEntry<Item> INTEGRATED_CONTROL_CORE_UHV =
            item("integrated_control_core_uhv");

    /** UEV 集控核心。 */
    public static final ItemEntry<Item> INTEGRATED_CONTROL_CORE_UEV =
            item("integrated_control_core_uev");

    /** UIV 集控核心。 */
    public static final ItemEntry<Item> INTEGRATED_CONTROL_CORE_UIV =
            item("integrated_control_core_uiv");

    /** UXV 集控核心。 */
    public static final ItemEntry<Item> INTEGRATED_CONTROL_CORE_UXV =
            item("integrated_control_core_uxv");

    /** OpV 集控核心。 */
    public static final ItemEntry<Item> INTEGRATED_CONTROL_CORE_OPV =
            item("integrated_control_core_opv");

    /** MAX 集控核心（物品先注册，配方等上游补全 ChaosInfinityAlloy / SpacetimeContinuum 再补）。 */
    public static final ItemEntry<Item> INTEGRATED_CONTROL_CORE_MAX =
            item("integrated_control_core_max");

    /** 统一注册口径（与上面三个老件同风格）。 */
    private static ItemEntry<Item> item(String name) {
        return GTLModularMachines.REGISTRATE.item(name, Item::new).register();
    }

    /** 强制类加载。 */
    public static void init() {
        GTLModular.LOGGER.info("[gtlmodular] 奈亚配套物品已注册：大脑 / 超级大脑 / 神经矩阵"
                + " + 研究计算件 7 个（NICH/GWCA 空件与计算件、黑曜石矩阵、"
                + "相对论旋量存储器、里德堡旋量集群）"
                + " + 集控核心 7 档（UV/UHV/UEV/UIV/UXV/OpV/MAX）");
    }
}
