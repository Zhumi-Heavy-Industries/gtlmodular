package com.gtlmodular.common.data.host;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularMachines;
import com.gtlmodular.common.data.nanochip.NanochipModulePreview;
import com.gtlmodular.common.data.nanochip.ModulePreviewSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.gtlcore.gtlcore.utils.datastructure.ModuleRenderInfo;

import java.util.ArrayList;
import java.util.List;

/** 地心预览与 NAC 兼容入口共存，避免旧调用方丢失原类及缓存类型。 */
public final class ModulePreviewSlots {

    private ModulePreviewSlots() {
    }

    public static List<ModuleRenderInfo> moltenHostModules(BlockPos hostPos, Cache cache) {
        if (hostPos == null || cache == null) {
            return List.of();
        }
        List<ModuleRenderInfo> cached = cache.get(hostPos);
        if (cached != null) {
            return cached;
        }
        List<ModuleRenderInfo> built;
        try {
            built = buildMoltenHost();
        } catch (Throwable t) {
            GTLModular.LOGGER.debug("[gtlmodular] 模块预览: 地心枢纽模块表构建失败，返回空表: {}", t.toString());
            built = List.of();
        }
        cache.put(hostPos, built);
        logOnce("molten@" + hostPos.asLong(), "地心涌动能量转换枢纽", hostPos, built);
        return built;
    }

    public static List<ModuleRenderInfo> nacModules(BlockPos hostPos) {
        return NanochipModulePreview.nacModules(hostPos);
    }

    /** 每台宿主仅保留一个坐标的结果，避免预览缓存随位置变化持续增长。 */
    public static final class Cache {
        private volatile BlockPos key;
        private volatile List<ModuleRenderInfo> value;

        public List<ModuleRenderInfo> get(BlockPos hostPos) {
            List<ModuleRenderInfo> v = value;
            return v != null && hostPos.equals(key) ? v : null;
        }

        public void put(BlockPos hostPos, List<ModuleRenderInfo> list) {
            this.key = hostPos.immutable();
            this.value = list;
        }
    }

    /** 此表已是相对主机的偏移，不能再减主机坐标或翻转 y；必须与 MOLTEN_HOST_FRONT 的参照系配套。 */
    private static final int[][] MOLTEN_MODULE_CONTROLLERS = {
            {22, 2, 51},
            {-22, 2, 51},
            {0, 20, 54},
    };

    private static final String[] MOLTEN_MODULE_IDS = {
            "molten_blast_module",
            "molten_stripper_module",
            "molten_space_ore_module",
    };

    @SuppressWarnings("unused")
    private static final BlockPos MOLTEN_HOST_CONTROLLER = new BlockPos(48, 8, 8);

    /** 偏移按 NORTH/UP 参照系提供，单改此朝向会使 ModuleRenderInfo 把模块旋转到另一侧。 */
    private static final Direction MOLTEN_HOST_FRONT = Direction.NORTH;

    private static final Direction MOLTEN_HOST_UP = Direction.UP;

    /** 模块自身朝向与偏移参照系分别传入，不能因修改主机参照系而同步改动模块朝向。 */
    private static final Direction MOLTEN_MODULE_FRONT = Direction.SOUTH;

    private static final Direction MOLTEN_MODULE_UP = Direction.UP;

    private static List<ModuleRenderInfo> buildMoltenHost() {
        List<ModuleRenderInfo> out = new ArrayList<>(MOLTEN_MODULE_IDS.length);
        for (int i = 0; i < MOLTEN_MODULE_IDS.length; i++) {
            int[] c = MOLTEN_MODULE_CONTROLLERS[i];
            MultiblockMachineDefinition def = moltenDefinition(MOLTEN_MODULE_IDS[i]);
            if (def == null) {
                continue;
            }
            BlockPos offset = new BlockPos(c[0], c[1], c[2]);
            out.add(new ModuleRenderInfo(offset, MOLTEN_HOST_FRONT, MOLTEN_HOST_UP,
                    MOLTEN_MODULE_FRONT, MOLTEN_MODULE_UP, def));
        }
        return List.copyOf(out);
    }

    private static MultiblockMachineDefinition moltenDefinition(String moduleId) {
        MultiblockMachineDefinition direct = switch (moduleId) {
            case "molten_blast_module" -> GTLModularMachines.MOLTEN_BLAST_MODULE;
            case "molten_stripper_module" -> GTLModularMachines.MOLTEN_STRIPPER_MODULE;
            case "molten_space_ore_module" -> GTLModularMachines.MOLTEN_SPACE_ORE_MODULE;
            default -> null;
        };
        return direct != null ? direct : lookupDefinition("gtlmodular", moduleId);
    }

    private static MultiblockMachineDefinition lookupDefinition(String namespace, String path) {
        return ModulePreviewSupport.lookupDefinition(namespace, path);
    }

    private static void logOnce(String key, String hostName, BlockPos hostPos, List<ModuleRenderInfo> list) {
        ModulePreviewSupport.logOnce(key, hostName, hostPos, list);
    }
}
