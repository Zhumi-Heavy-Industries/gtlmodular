package com.gtlmodular.client.ctm;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.diag.SwallowedErrors;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtlmodular.common.data.NanochipBlocks;
import com.gtlmodular.common.data.nanochip.NanochipMachines;
import com.gtlmodular.common.machine.nanochip.NanochipVacuumHatchMachine;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/** 邻居连接按机壳组、真空仓同步状态和 NAC 仓位判断，避免在网格化时递归读取邻居模型。 */
public final class CtmHullGroup {

    public static final String MESH_CASING_ID = "nanochip_mesh_interface_casing";

    /** 图集可能使用资源或 minecraft 命名空间，因此按 sprite 路径识别同一机壳。 */
    public static final String MESH_BASE_SPRITE = "block/casings/nanochip_mesh_interface_casing";

    public static final String MESH_SPRITE_PREFIX = "block/casings/ctm/nanochip_mesh_interface_casing";

    public static final String TIER_CASING_TEXTURE_PREFIX = "block/casings/voltage/";

    public static final String TIER_CASING_ID_SUFFIX = "_machine_casing";

    public static String tierName(int tier) {
        if (tier < 0 || tier >= GTValues.VN.length) {
            return null;
        }
        return GTValues.VN[tier].toLowerCase(Locale.ROOT);
    }

    public static String tierCasingSprite(int tier, String face) {
        String name = tierName(tier);
        if (name == null) {
            return null;
        }
        return TIER_CASING_TEXTURE_PREFIX + name + "/" + face;
    }

    public static ResourceLocation tierCasingBlockId(int tier) {
        String name = tierName(tier);
        return name == null ? null : new ResourceLocation("gtceu", name + TIER_CASING_ID_SUFFIX);
    }

    private static final String[] TIER_CASING_FACES = { "side", "top", "bottom" };

    /** 只匹配 tier 机壳目录和面名，避免替换正脸覆盖层、发光层或管道条纹。 */
    public static boolean isTierCasingSpritePath(String path) {
        if (path == null || !path.startsWith(TIER_CASING_TEXTURE_PREFIX)) {
            return false;
        }
        String rest = path.substring(TIER_CASING_TEXTURE_PREFIX.length());
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash == rest.length() - 1) {
            return false;
        }
        String face = rest.substring(slash + 1);
        for (String allowed : TIER_CASING_FACES) {
            if (allowed.equals(face)) {
                return true;
            }
        }
        return false;
    }

    public static final String[] MACHINE_HULL_IDS = {
            "nanochip_assembly_complex",
            "nanochip_assembly_matrix",
            "nanochip_smd_processor",
            "nanochip_board_processor",
            "nanochip_etching_array",
            "nanochip_cutting_chamber",
            "nanochip_wire_tracer",
            "nanochip_superconductor_splitter",
            "nanochip_optical_organizer",
            "nanochip_exotic_organizer",
            "nanochip_cosmic_organizer",
            "nanochip_supracausal_organizer",
            "nanochip_biological_coordinator",
            "nanochip_encasement_wrapper",
            "nanochip_splitter",
            "nanochip_vacuum_input_hatch",
            "nanochip_vacuum_output_hatch",
    };

    public static final String[] VACUUM_HATCH_IDS = {
            "nanochip_vacuum_input_hatch",
            "nanochip_vacuum_output_hatch",
    };

    private static volatile Set<Block> machineHullBlocks;
    private static volatile Set<Block> vacuumHatchBlocks;
    private static volatile Set<String> machineHullIdSet;
    private static volatile Boolean tierCasingRegistered;

    private CtmHullGroup() {
    }

    /** 注册名视图由已解析方块派生，避免一侧命中渲染目标、另一侧却不认邻居。 */
    public static Set<String> machineHullIdSet() {
        Set<String> cached = machineHullIdSet;
        if (cached != null) {
            return cached;
        }
        Set<String> built = new LinkedHashSet<>();
        for (Block block : machineHullBlocks()) {
            ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
            if (key == null) {
                continue;
            }
            built.add(hullId(key));
        }
        machineHullIdSet = built;
        return built;
    }

    private static String hullId(ResourceLocation id) {
        return NanochipModContext.modId().equals(id.getNamespace()) ? id.getPath()
                : new ResourceLocation(id.getNamespace(), id.getPath()).toString();
    }

    public static boolean isMachineHull(ResourceLocation id) {
        return machineHullIdSet().contains(hullId(id));
    }

    static Block meshCasingBlock() {
        return NanochipBlocks.MESH_INTERFACE_CASING.get();
    }

    static Set<Block> machineHullBlocks() {
        Set<Block> cached = machineHullBlocks;
        if (cached != null) {
            return cached;
        }
        Set<Block> built = new LinkedHashSet<>();
        for (String id : MACHINE_HULL_IDS) {
            Block b = ForgeRegistries.BLOCKS.getValue(com.gtlmodular.common.machine.nanochip.registration.NanochipModules.parseId(id));
            if (b == null) {
                NanochipModContext.logger().warn("[gtlmodular] CTM: 白名单里的机器方块 {}:{} 取不到（注册名漂移？）"
                        + "—— 它的外壳面不会参与连接纹理；★ 注册名视图由本视图派生，"
                        + "所以它**两侧一起不认**（不会出现「自己换了 sprite、邻居不认」的单侧连接）。",
                        NanochipModContext.modId(), id);
                continue;
            }
            built.add(b);
        }
        for (var spec : com.gtlmodular.common.machine.nanochip.registration.NanochipModules.all()) {
            if (spec.legacyKind() != null || !spec.casing().equals(NanochipModContext.assetId(MESH_BASE_SPRITE))) continue;
            Block block = ForgeRegistries.BLOCKS.getValue(spec.id());
            if (block != null) built.add(block);
        }
        machineHullBlocks = built;
        return built;
    }

    static Set<Block> vacuumHatchBlocks() {
        Set<Block> cached = vacuumHatchBlocks;
        if (cached != null) {
            return cached;
        }
        Set<Block> built = new LinkedHashSet<>();
        for (String id : VACUUM_HATCH_IDS) {
            Block b = ForgeRegistries.BLOCKS.getValue(com.gtlmodular.common.machine.nanochip.registration.NanochipModules.parseId(id));
            if (b != null) {
                built.add(b);
            }
        }
        vacuumHatchBlocks = built;
        return built;
    }

    public static boolean isVacuumHatch(Block block) {
        return vacuumHatchBlocks().contains(block);
    }

    public static String hullViewMismatch() {
        Set<Block> blocks = machineHullBlocks();
        Set<String> ids = machineHullIdSet();
        if (blocks.size() != ids.size()) {
            return "方块视图 " + blocks.size() + " 台 ≠ 注册名视图 " + ids.size() + " 条";
        }
        for (Block block : blocks) {
            ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
            if (key == null || !ids.contains(hullId(key))) {
                return "方块 " + block + " 的注册名 " + key + " 不在注册名视图里";
            }
        }
        for (String id : ids) {
            Block block = ForgeRegistries.BLOCKS.getValue(com.gtlmodular.common.machine.nanochip.registration.NanochipModules.parseId(id));
            if (block == null || !blocks.contains(block)) {
                return "注册名 " + NanochipModContext.modId() + ":" + id + " 解不出方块视图里的同一台";
            }
        }
        for (String id : VACUUM_HATCH_IDS) {
            if (!ids.contains(id)) {
                return "两态名单里的 " + id + " 不在外壳白名单里（两态会套到名单外的方块上）";
            }
        }
        return null;
    }

    static void warnIfTierCasingMissing(int tier) {
        if (tierCasingRegistered != null) {
            return;
        }
        ResourceLocation id = tierCasingBlockId(tier);
        boolean ok = id != null && ForgeRegistries.BLOCKS.containsKey(id);
        tierCasingRegistered = ok;
        if (!ok) {
            NanochipModContext.logger().warn("[gtlmodular] CTM: 按 GTValues.VN[{}] 推出的 tier 机壳 {} 不在注册表里"
                    + "—— 真空仓未并入成形结构时的外壳会退回接口机壳（不会缺失贴图）。",
                    tier, id);
        }
    }

    public static boolean rendersMeshCasingTile(BlockGetter level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block == meshCasingBlock()) {
            return true;
        }
        if (!machineHullBlocks().contains(block)) {
            var spec = com.gtlmodular.common.machine.nanochip.registration.NanochipModules.byId(ForgeRegistries.BLOCKS.getKey(block));
            if (spec != null && spec.legacyKind() == null) return false;
            return block instanceof IMachineBlock && NacSlotIndex.isNacSlot(level, pos);
        }
        if (vacuumHatchBlocks().contains(block)) {
            return isCasingFromStructure(level, pos);
        }
        return true;
    }

    /** 无法读取真空仓实例时保留并入态外观，避免暂缺同步数据造成外壳退化。 */
    public static boolean isCasingFromStructure(BlockGetter level, BlockPos pos) {
        try {
            MetaMachine machine = MetaMachine.getMachine(level, pos);
            if (machine instanceof NanochipVacuumHatchMachine hatch) {
                return hatch.isCasingFromStructure();
            }
        } catch (Throwable ignored) {
            SwallowedErrors.report("CtmHullGroup#isCasingFromStructure", ignored);
        }
        return true;
    }

    public static int hatchTier() {
        return NanochipMachines.VACUUM_HATCH_TIER;
    }
}
