package com.gtlmodular.common.machine.nanochip.registration;

import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gtlmodular.common.data.nanochip.NanochipRecipeTypes;
import com.gtlmodular.common.machine.nanochip.NanochipCalibration;
import com.gtlmodular.common.machine.nanochip.NanochipModuleKind;

import java.util.List;
import java.util.function.Supplier;

/** 内置参数只在此声明；资源生成器从 builtin 的首参数读取同一份机器 ID。 */
final class NanochipBuiltinModules {

    private NanochipBuiltinModules() {
    }

    static List<NanochipModuleSpec> create() {
        return List.of(
                builtin("nanochip_assembly_matrix", NanochipModuleKind.ASSEMBLY_MATRIX,
                        new NanochipModuleSpec.Display("ASSEMBLY_MATRIX", "纳米芯片组装矩阵", "Nanochip Assembly Matrix",
                                "组装电路；解锁全流程档（无限并行 + 配方时长下限 5 秒）"),
                        () -> NanochipRecipeTypes.ASSEMBLY_MATRIX,
                        new NanochipModuleSpec.Bonuses(1.0D, 1.0D, 1.0D, true, List.of()),
                        new NanochipModuleSpec.Operation(10000, false, null, null, 0)),

                builtin("nanochip_smd_processor", NanochipModuleKind.SMD_PROCESSOR,
                        new NanochipModuleSpec.Display("SMD_PROCESSOR", "元件预处理装置", "Part Preparation Apparatus",
                                "精炼元件；每台为复合体减免 5% 能耗"),
                        () -> NanochipRecipeTypes.SMD_PROCESSOR,
                        new NanochipModuleSpec.Bonuses(0.95D, 1.0D, 1.0D, false, List.of()),
                        new NanochipModuleSpec.Operation(100, false, null, null, 0)),

                builtin("nanochip_board_processor", NanochipModuleKind.BOARD_PROCESSOR,
                        new NanochipModuleSpec.Display("BOARD_PROCESSOR", "全板浸没设备", "Full-Board Immersion Device",
                                "精炼电路板；每台减免 10% 能耗；持续消耗净化水，杂质影响耗电"),
                        () -> NanochipRecipeTypes.BOARD_PROCESSOR,
                        new NanochipModuleSpec.Bonuses(0.90D, 1.0D, 1.0D, false, List.of()),
                        new NanochipModuleSpec.Operation(400, false, null, "gtceu:grade_1_purified_water", 100)),

                builtin("nanochip_etching_array", NanochipModuleKind.ETCHING_ARRAY,
                        new NanochipModuleSpec.Display("ETCHING_ARRAY", "超高能蚀刻阵列", "Ultra-high Energy Etching Array",
                                "蚀刻晶体芯片；每台缩短 5% 时长；晶体电路满校准后激光源仓额外生效"),
                        () -> NanochipRecipeTypes.ETCHING_ARRAY,
                        new NanochipModuleSpec.Bonuses(1.0D, 0.95D, 1.0D, false, List.of()),
                        new NanochipModuleSpec.Operation(400, false, null, null, 0)),

                builtin("nanochip_cutting_chamber", NanochipModuleKind.CUTTING_CHAMBER,
                        new NanochipModuleSpec.Display("CUTTING_CHAMBER", "纳米精度切割仓", "Nanoprecision Cutting Chamber",
                                "切割芯片/螺栓/板材/框架；每台缩短 5% 时长"),
                        () -> NanochipRecipeTypes.CUTTING_CHAMBER,
                        new NanochipModuleSpec.Bonuses(1.0D, 0.95D, 1.0D, false, List.of()),
                        new NanochipModuleSpec.Operation(800, false, null, null, 0)),

                builtin("nanochip_wire_tracer", NanochipModuleKind.WIRE_TRACER,
                        new NanochipModuleSpec.Display("WIRE_TRACER", "纳米精度导线追踪器", "Nanoprecision Wire Tracer",
                                "为 NAC 减免 5% 能耗"),
                        () -> NanochipRecipeTypes.WIRE_TRACER,
                        new NanochipModuleSpec.Bonuses(0.95D, 1.0D, 1.0D, false, List.of()),
                        new NanochipModuleSpec.Operation(200, false, null, null, 0)),

                builtin("nanochip_superconductor_splitter", NanochipModuleKind.SUPERCONDUCTOR_SPLITTER,
                        new NanochipModuleSpec.Display("SUPERCONDUCTOR_SPLITTER", "超导线束分离器", "Superconductive Strand Splitter",
                                "为 NAC 缩短 10% 时长；需持续供给 1000 mB/s 超级冷却液（NH 原文 Materials.SuperCoolant）"),
                        () -> NanochipRecipeTypes.SUPERCONDUCTOR_SPLITTER,
                        new NanochipModuleSpec.Bonuses(1.0D, 0.90D, 1.0D, false, List.of()),
                        new NanochipModuleSpec.Operation(400, false, null, "gtceu:super_coolant", 1000)),

                builtin("nanochip_optical_organizer", NanochipModuleKind.OPTICAL_ORGANIZER,
                        new NanochipModuleSpec.Display("OPTICAL_ORGANIZER", "光学优化组织器", "Optically Optimized Organizer",
                                "整理光学芯片；每台减免 10% 能耗并缩短 10% 时长；净化水等级越高加成越大，光学满校准后翻倍"),
                        () -> NanochipRecipeTypes.OPTICAL_ORGANIZER,
                        new NanochipModuleSpec.Bonuses(0.90D, 0.90D, 1.0D, false, List.of()),
                        new NanochipModuleSpec.Operation(200, false, NanochipCalibration.Family.OPTICAL, "gtceu:grade_1_purified_water", 100)),

                builtin("nanochip_exotic_organizer", NanochipModuleKind.EXOTIC_ORGANIZER,
                        new NanochipModuleSpec.Display("EXOTIC_ORGANIZER", "奇异优化组织器", "Exotically Optimized Organizer",
                                "整理奇异芯片；每台减免 10% 能耗并缩短 10% 时长；净化水等级越高加成越大"),
                        () -> NanochipRecipeTypes.EXOTIC_ORGANIZER,
                        new NanochipModuleSpec.Bonuses(0.90D, 0.90D, 1.0D, false, List.of()),
                        new NanochipModuleSpec.Operation(200, false, NanochipCalibration.Family.EXOTIC, "gtceu:grade_1_purified_water", 100)),

                builtin("nanochip_cosmic_organizer", NanochipModuleKind.COSMIC_ORGANIZER,
                        new NanochipModuleSpec.Display("COSMIC_ORGANIZER", "寰宇优化组织器", "Cosmically Optimized Organizer",
                                "整理寰宇芯片；每台减免 10% 能耗并缩短 10% 时长；净化水等级越高加成越大"),
                        () -> NanochipRecipeTypes.COSMIC_ORGANIZER,
                        new NanochipModuleSpec.Bonuses(0.90D, 0.90D, 1.0D, false, List.of()),
                        new NanochipModuleSpec.Operation(200, false, NanochipCalibration.Family.COSMIC, "gtceu:grade_1_purified_water", 100)),

                builtin("nanochip_supracausal_organizer", NanochipModuleKind.SUPRACAUSAL_ORGANIZER,
                        new NanochipModuleSpec.Display("SUPRACAUSAL_ORGANIZER", "超因果优化组织器", "Supracausally Optimized Organizer",
                                "整理超因果芯片；每台减免 10% 能耗并缩短 10% 时长；净化水等级越高加成越大"),
                        () -> NanochipRecipeTypes.SUPRACAUSAL_ORGANIZER,
                        new NanochipModuleSpec.Bonuses(0.90D, 0.90D, 1.0D, false, List.of()),
                        new NanochipModuleSpec.Operation(200, false, NanochipCalibration.Family.TEMPORAL, "gtceu:grade_1_purified_water", 100)),

                builtin("nanochip_biological_coordinator", NanochipModuleKind.BIOLOGICAL_COORDINATOR,
                        new NanochipModuleSpec.Display("BIOLOGICAL_COORDINATION", "加速生物调谐器", "Accelerated Biological Coordinator",
                                "调谐生物/湿件核心件；湿件与生物电路缩短 10% 时长；耗生长培养基，满校准后免耗"),
                        () -> NanochipRecipeTypes.BIOLOGICAL_COORDINATOR,
                        new NanochipModuleSpec.Bonuses(1.0D, 0.90D, 1.0D, false, List.of(NanochipCalibration.Family.WETWARE, NanochipCalibration.Family.BIOWARE)),
                        new NanochipModuleSpec.Operation(600, false, null, "gtceu:raw_growth_medium", 100)),

                builtin("nanochip_encasement_wrapper", NanochipModuleKind.ENCASEMENT_WRAPPER,
                        new NanochipModuleSpec.Display("ENCASEMENT_WRAPPER", "纳米封装卷绕器", "Nanometer Encasement Wrapper",
                                "卷绕封装件（框架盒/箔材/主板外壳/热超导体）；每台提供 4 倍并行"),
                        () -> NanochipRecipeTypes.ENCASEMENT_WRAPPER,
                        new NanochipModuleSpec.Bonuses(1.0D, 1.0D, 4.0D, false, List.of()),
                        new NanochipModuleSpec.Operation(400, false, null, null, 0)),

                builtin("nanochip_splitter", NanochipModuleKind.SPLITTER,
                        new NanochipModuleSpec.Display("SPLITTER", "纳米组件分流器", "Nanopart Splitter",
                                "不加工，只调度：每 20 tick 按规则把虚拟件在颜色通道间搬运；每台提供 2 倍并行"),
                        () -> NanochipRecipeTypes.SPLITTER_SCHEDULE,
                        new NanochipModuleSpec.Bonuses(1.0D, 1.0D, 2.0D, false, List.of()),
                        new NanochipModuleSpec.Operation(100, true, null, null, 0)));
    }

    static NanochipModuleSpec.Features features(NanochipModuleKind kind) {
        return new NanochipModuleSpec.Features(kind == NanochipModuleKind.ASSEMBLY_MATRIX,
                kind == NanochipModuleKind.BOARD_PROCESSOR,
                kind == NanochipModuleKind.ETCHING_ARRAY,
                kind == NanochipModuleKind.BIOLOGICAL_COORDINATOR,
                kind == NanochipModuleKind.ASSEMBLY_MATRIX ? -1 : 1);
    }

    private static NanochipModuleSpec builtin(String path, NanochipModuleKind kind,
                                              NanochipModuleSpec.Display display, Supplier<GTRecipeType> recipeType,
                                              NanochipModuleSpec.Bonuses bonuses, NanochipModuleSpec.Operation operation) {
        return new NanochipModuleSpec(NanochipModContext.registryId(path), kind, display, recipeType, bonuses, operation);
    }
}
