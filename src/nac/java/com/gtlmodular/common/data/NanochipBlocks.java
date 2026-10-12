package com.gtlmodular.common.data;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class NanochipBlocks {

    private NanochipBlocks() {
    }

    public static final BlockEntry<Block> MESH_INTERFACE_CASING =
            cube("nanochip_mesh_interface_casing", 6.0F, 8.0F);

    public static final BlockEntry<Block> REINFORCEMENT_CASING =
            cube("nanochip_reinforcement_casing", 8.0F, 10.0F);

    public static final BlockEntry<Block> COMPUTATIONAL_MATRIX_CASING =
            cube("nanochip_computational_matrix_casing", 8.0F, 10.0F);

    public static final BlockEntry<Block> FIREWALL_PROJECTION_CASING =
            REGISTRATE_BLOCK("nanochip_firewall_projection_casing")
                    .properties(p -> p.strength(8.0F, 10.0F))
                    .item()
                    .build()
                    .register();

    /** 相邻玻璃共享面需剔除，避免半透明纹理叠画；连片选图由客户端 CTM 处理。 */
    public static final BlockEntry<com.gtlmodular.common.block.NanochipComplexGlassBlock> COMPLEX_GLASS =
            NanochipModContext.machineRegistrate()
                    .block("nanochip_complex_glass", com.gtlmodular.common.block.NanochipComplexGlassBlock::new)
                    .initialProperties(() -> Blocks.GLASS)
                    .properties(p -> p.strength(3.0F)
                            .sound(SoundType.GLASS)
                            .noOcclusion()
                            .isValidSpawn((s, l, p2, e) -> false)
                            .isRedstoneConductor((s, l, p2) -> false)
                            .isSuffocating((s, l, p2) -> false)
                            .isViewBlocking((s, l, p2) -> false))
                    .item()
                    .build()
                    .register();

    public static final BlockEntry<Block> UHV_SOLENOID_SUPERCONDUCTOR_COIL =
            REGISTRATE_BLOCK("uhv_solenoid_superconductor_coil", 8.0F, 10.0F);

    public static final BlockEntry<Block> UEV_SOLENOID_SUPERCONDUCTOR_COIL =
            REGISTRATE_BLOCK("uev_solenoid_superconductor_coil", 8.0F, 10.0F);

    public static final BlockEntry<Block> NAQUADRIA_REINFORCED_WATER_PLANT_CASING =
            REGISTRATE_BLOCK("naquadria_reinforced_water_plant_casing", 8.0F, 10.0F);

    public static final BlockEntry<Block> SHIELDED_ACCELERATOR_CASING =
            REGISTRATE_BLOCK("shielded_accelerator_casing", 8.0F, 10.0F);

    public static final BlockEntry<com.gtlmodular.common.block.NanochipComplexGlassBlock>
            NON_PHOTONIC_MATTER_EXCLUSION_GLASS =
            REGISTRATE_BLOCK("non_photonic_matter_exclusion_glass",
                    com.gtlmodular.common.block.NanochipComplexGlassBlock::new)
                    .initialProperties(() -> Blocks.GLASS)
                    .properties(p -> p.strength(3.0F)
                            .sound(SoundType.GLASS)
                            .noOcclusion()
                            .isValidSpawn((s, l, p2, e) -> false)
                            .isRedstoneConductor((s, l, p2) -> false)
                            .isSuffocating((s, l, p2) -> false)
                            .isViewBlocking((s, l, p2) -> false))
                    .item()
                    .build()
                    .register();

    public static final BlockEntry<Block> NAQUADAH_ALLOY_SHEETMETAL =
            REGISTRATE_BLOCK("naquadah_alloy_sheetmetal", 6.0F, 8.0F);

    public static final BlockEntry<Block> SUPERCONDUCTOR_UEV_BASE_SHEETMETAL =
            REGISTRATE_BLOCK("superconductor_uev_base_sheetmetal", 6.0F, 8.0F);

    private static com.tterrag.registrate.builders.BlockBuilder<Block, com.tterrag.registrate.Registrate> REGISTRATE_BLOCK(String name) {
        return NanochipModContext.machineRegistrate().block(name, Block::new);
    }

    /** 保留可解析的注册调用形态，供独立产物资源生成器从源码提取方块 ID。 */
    private static BlockEntry<Block> REGISTRATE_BLOCK(String name, float destroyTime, float explosionResistance) {
        return REGISTRATE_BLOCK(name)
                .initialProperties(() -> Blocks.IRON_BLOCK)
                .properties((BlockBehaviour.Properties p) -> p.strength(destroyTime, explosionResistance))
                .item()
                .build()
                .register();
    }

    private static <T extends Block> com.tterrag.registrate.builders.BlockBuilder<T, com.tterrag.registrate.Registrate>
    REGISTRATE_BLOCK(String name,
                     com.tterrag.registrate.util.nullness.NonNullFunction<BlockBehaviour.Properties, T> factory) {
        return NanochipModContext.machineRegistrate().block(name, factory);
    }

    /** 普通方块需要随包提供模型与掉落资源；未配工具标签时不要求正确工具，以免挖掘无掉落。 */
    private static BlockEntry<Block> cube(String name, float destroyTime, float explosionResistance) {
        return REGISTRATE_BLOCK(name)
                .initialProperties(() -> Blocks.IRON_BLOCK)
                .properties((BlockBehaviour.Properties p) -> p
                        .strength(destroyTime, explosionResistance))
                .item()
                .build()
                .register();
    }

    private static ResourceLocation modLoc(String path) {
        return NanochipModContext.assetId(path);
    }

    public static void init() {
        NanochipModContext.logger().info("[gtlmodular] NAC 结构方块已注册: 网格接口/强化/计算矩阵/防火墙投影/玻璃"
                + " + 缺件方块 7 个（UHV/UEV 螺线管线圈、硅岩强化水厂外壳、屏蔽加速器外壳、"
                + "非光子排斥玻璃、2 个板金属）—— 全部是追加候选，既有替代码不变");
    }
}
