package com.gtlmodular.common.data;

import com.gtlmodular.common.diag.SwallowedErrors;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gtlmodular.common.machine.nanochip.registration.NanochipModuleSpec;
import com.gtlmodular.common.machine.nanochip.registration.NanochipModules;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/** BlockInfo#fromBlock 会创建方块实体，预览只选代表仓以避免批量实例化昂贵的样板仓。 */
public final class HatchPredicates {

    private HatchPredicates() {
    }

    private static final String[] TIER_PREFIXES = {
            "ulv", "lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv", "uhv", "uev", "uiv", "uxv", "opv", "max"
    };

    private static String[] standardSuffixes(PartAbility ability) {
        if (ability == PartAbility.IMPORT_ITEMS) {
            return new String[]{"input_bus"};
        }
        if (ability == PartAbility.EXPORT_ITEMS) {
            return new String[]{"output_bus"};
        }
        if (ability == PartAbility.IMPORT_FLUIDS) {
            return new String[]{"input_hatch"};
        }
        if (ability == PartAbility.EXPORT_FLUIDS) {
            return new String[]{"output_hatch"};
        }
        if (ability == PartAbility.INPUT_ENERGY) {
            return new String[]{"energy_input_hatch"};
        }
        if (ability == PartAbility.INPUT_LASER) {
            return new String[]{"256a_laser_source_hatch", "1024a_laser_source_hatch"};
        }
        if (ability == PartAbility.MAINTENANCE) {
            return new String[]{"maintenance_hatch"};
        }
        if (ability == PartAbility.PARALLEL_HATCH) {
            return new String[]{"parallel_hatch"};
        }
        return new String[0];
    }

    /** 优先避开构造昂贵的机器；只影响预览候选，不收窄能力匹配。 */
    private static final String[] HEAVY_HINTS = {
            "pattern_buffer", "super_pattern", "stocking", "proxy", "assembly", "window", "core"
    };

    /** 先选标准仓再按注册 ID 排序，避免 HashSet 迭代顺序使预览随启动变化。 */
    private static Block representative(PartAbility ability) {
        List<Block> blocks = new ArrayList<>(ability.getAllBlocks());
        if (blocks.isEmpty()) {
            return null;
        }
        String[] suffixes = standardSuffixes(ability);
        for (String suffix : suffixes) {
            for (String tier : TIER_PREFIXES) {
                String wanted = "gtceu:" + tier + "_" + suffix;
                for (Block block : blocks) {
                    if (idOf(block).equals(wanted)) {
                        return block;
                    }
                }
            }
            String plain = "gtceu:" + suffix;
            for (Block block : blocks) {
                if (idOf(block).equals(plain)) {
                    return block;
                }
            }
        }
        return blocks.stream().min((a, b) -> {
            int c = Integer.compare(rank(a), rank(b));
            return c != 0 ? c : idOf(a).compareTo(idOf(b));
        }).orElse(blocks.get(0));
    }

    private static int rank(Block block) {
        String id = idOf(block);
        for (String hint : HEAVY_HINTS) {
            if (id.contains(hint)) {
                return 2;
            }
        }
        return id.startsWith("gtceu:") ? 0 : 1;
    }

    private static String idOf(Block block) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        return key == null ? "" : key.toString();
    }

    public static final boolean PREVIEW_MODULE_HOSTS_ONLY = true;

    /** 模块自身的仓位应展示 IO 仓，不能沿用主机展示模块控制器的候选表。 */
    public static final boolean MODULE_SIDE_PREVIEW_IO_ONLY = true;

    /** 按产物命名空间与模块目录识别，避免用 hatch 等名称关键词误排有效模块。 */
    public static boolean isPreviewModuleHost(Block block) {
        if (block == null) {
            return false;
        }
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        if (key == null) {
            return false;
        }
        return NanochipModules.byId(key) != null;
    }

    /** 预览阶段按 ID 查询已注册方块，避免静态依赖机器注册类而引入初始化环。 */
    private static Block moduleHostBlock(NanochipModuleSpec kind) {
        if (kind == null) {
            return null;
        }
        try {
            Block block = BuiltInRegistries.BLOCK.get(kind.id());
            return block == null || block == Blocks.AIR ? null : block;
        } catch (Throwable t) {
            SwallowedErrors.report("HatchPredicates#moduleHostBlock", t);
            return null;
        }
    }

    /** 模块候选全缺失时先用机壳占位，避免上游将空候选画成空气洞。 */
    private static BlockInfo previewFallback() {
        try {
            Block mesh = NanochipBlocks.MESH_INTERFACE_CASING.get();
            return mesh == null ? null : BlockInfo.fromBlockState(mesh.defaultBlockState());
        } catch (Throwable t) {
            SwallowedErrors.report("HatchPredicates#previewFallback", t);
            return null;
        }
    }

    /** 纯展示候选单独构造，避免改动 lowCostAbilities 的自动搭建及点格候选。 */
    private static BlockInfo[] previewOfModuleHosts(PartAbility... abilityList) {
        if (!PREVIEW_MODULE_HOSTS_ONLY) {
            return previewOf(abilityList);
        }
        List<BlockInfo> hosts = new ArrayList<>(NanochipModules.all().size());
        for (NanochipModuleSpec kind : NanochipModules.all()) {
            Block block = moduleHostBlock(kind);
            if (isPreviewModuleHost(block)) {
                hosts.add(BlockInfo.fromBlock(block));
            }
        }
        if (!hosts.isEmpty()) {
            return hosts.toArray(new BlockInfo[0]);
        }
        BlockInfo fallback = previewFallback();
        return fallback == null ? previewOf(abilityList) : new BlockInfo[]{fallback};
    }

    private static BlockInfo[] previewOf(PartAbility... abilityList) {
        List<BlockInfo> preview = new ArrayList<>(abilityList.length);
        for (PartAbility ability : abilityList) {
            Block block = representative(ability);
            if (block != null) {
                preview.add(BlockInfo.fromBlock(block));
            }
        }
        return preview.toArray(new BlockInfo[0]);
    }

    private static boolean applies(MultiblockState state, PartAbility[] abilityList) {
        Block block = state.getBlockState().getBlock();
        for (PartAbility ability : abilityList) {
            if (ability.isApplicable(block)) {
                return true;
            }
        }
        return false;
    }

    public static TraceabilityPredicate lowCostAbilities(PartAbility... abilityList) {
        return new TraceabilityPredicate(
                (MultiblockState state) -> applies(state, abilityList),
                () -> previewOf(abilityList));
    }

    /** 使用独立预览谓词，避免在 H/F 共用的限量谓词上设置预览配额而盖掉模块示意。 */
    public static TraceabilityPredicate previewAbilities(PartAbility... abilityList) {
        return new TraceabilityPredicate(
                (MultiblockState state) -> applies(state, abilityList),
                () -> previewOfModuleHosts(abilityList)).setPreviewCount(1);
    }

    public static TraceabilityPredicate previewAbilitiesForModule(PartAbility... abilityList) {
        if (!MODULE_SIDE_PREVIEW_IO_ONLY || !PREVIEW_MODULE_HOSTS_ONLY) {
            return previewAbilities(abilityList);
        }
        return new TraceabilityPredicate(
                (MultiblockState state) -> applies(state, abilityList),
                () -> previewOf(abilityList)).setPreviewCount(1);
    }

    public static final PartAbility[] NAC_HATCH_ABILITIES = {
            PartAbility.IMPORT_ITEMS,
            PartAbility.EXPORT_ITEMS,
            PartAbility.IMPORT_FLUIDS,
            PartAbility.EXPORT_FLUIDS,
            PartAbility.INPUT_ENERGY,
            PartAbility.INPUT_LASER,
            PartAbility.MAINTENANCE
    };

    public static TraceabilityPredicate ioUnlimited(boolean allowParallel) {
        TraceabilityPredicate p = lowCostAbilities(PartAbility.IMPORT_ITEMS).setMinGlobalLimited(0)
                .or(lowCostAbilities(PartAbility.EXPORT_ITEMS).setMinGlobalLimited(0))
                .or(lowCostAbilities(PartAbility.IMPORT_FLUIDS).setMinGlobalLimited(0))
                .or(lowCostAbilities(PartAbility.EXPORT_FLUIDS).setMinGlobalLimited(0));
        if (allowParallel) {
            p = p.or(lowCostAbilities(PartAbility.PARALLEL_HATCH)
                    .setMinGlobalLimited(0).setMaxGlobalLimited(1));
        }
        return p;
    }

    @SuppressWarnings("unused")
    private static TraceabilityPredicate vanillaForComparison(PartAbility... abilityList) {
        return com.gregtechceu.gtceu.api.pattern.Predicates.abilities(abilityList);
    }
}
