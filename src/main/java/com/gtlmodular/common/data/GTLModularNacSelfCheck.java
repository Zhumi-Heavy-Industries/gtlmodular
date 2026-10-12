package com.gtlmodular.common.data;

import com.gtlmodular.common.data.nanochip.NanochipExpectedRecipes;
import java.util.List;

public final class GTLModularNacSelfCheck {

    private GTLModularNacSelfCheck() {
    }

    public static void register() {
        NanochipExpectedRecipes.include(List.of(
                "ev_me_pattern_assembly",
                "hv_me_pattern_assembly",
                "iv_me_pattern_assembly",
                "luv_me_pattern_assembly",
                "molten_blast_module",
                "molten_module_host",
                "molten_space_ore_module",
                "molten_stripper_module",
                "mv_me_pattern_assembly",
                "super_me_pattern_assembly",
                "uev_me_pattern_assembly",
                "uhv_me_pattern_assembly",
                "uiv_me_pattern_assembly",
                "uv_me_pattern_assembly",
                "zpm_me_pattern_assembly"),
                "5 方块 + 15 机器 + 2 仓室 + 15 既有"
                + " + 4 补充材料框架 + 3 既有材料补框架（radox / kevlar / octiron；★ 2026-09-21 再加 degenerate_rhenium："
                + "队长在 JEI 里确认「简并态铼框架没配方」，故补一条框架配方 `degenerate_rhenium_frame_crafting.json`，塔身一格不动） + 1 富集钬链头 + 2 UHV 粗胚链"
                + " + 7 缺件方块配方 + 1 UEV 粗胚链 + 1 真空管道 + 1 超级冷却液（2026-09-21 队长拍板：GTL 无此流体，本模组注册并给化学反应釜配方）"
                + " + 1 维度偏移超流体（2026-09-21 队长给数：超维度等离子锻炉 7500 mB，本模组注册该流体）"
                + " + 94 纳米芯片换算 + 10 组装矩阵（仅晶体/湿件两族沿用 NH 段移植；原为 99 与 23，2026-09-18 退役「特殊系列」8 条，2026-09-21 再退役「时空导线」链 1 条）"
                + " + 78 纳米芯片模块加工（P2 的 9 张模块表；清单 82 条 − 裁决 ADJ-1 扣留板处理器 4 条 fake）"
                + " + 103 波2 自造段纳米芯片换算（NH 段 94 与波2 103，共 197 条换算；真件=源物品，5 s / EU 0）"
                + " + 88 波2 模块加工（P2 的 9 张模块表；未处理 CC 变已处理 PC，档位照包内 tag.js 电路档位）"
                + " + 57 纳米精度切割仓「维度偏移超流体」加速分支（2026-09-21 队长令：现有配方都加超流体分支、减少时长与耗电；"
                + "62 条基准按产物去重成 57 条，时长与耗电各 ×1/2，超流体量按品类 芯片 10 / 螺栓 5 / 板材 50 / 框架 100 L；"
                + "目录 data/gtlmodular/recipes/nanochip/cutting_chamber_superfluid/，规格 _census/nac_cc_superfluid_spec.json）"
                + " + 6 三台新组织器模块加工（2026-09-18 队长指示：三族「处理核心与 RAM 晶片」分别归奇异/寰宇/超因果优化组织器）"
                + " + 23 GTL 官方配方重推（3 条成品 mainframe 换算与 20 条五族组装矩阵；规格 nac_matrix_spec.json）"
                + " + 10 缺件获取链（2026-09-19：残留物品清理报告 §8.2 的 10 件「被我方配方引用但游戏里拿不到」"
                + "逐条补上获得配方 —— 4 件外壳走切割机切板、5 件功率IC走激光蚀刻机蚀刻、1 件活性晶体走生物调谐器；"
                + "目录 data/gtlmodular/recipes/missing_acquisition/，交付说明见 _census/缺件获取链_队员.md）"
                + " + 5 新造PIC晶圆两步链（2026-09-19 队长「那就新造还原吧」：新注册 5 件 PIC 晶圆"
                + "（量子/阿米/仄米/幺米/普朗克功率IC晶圆，NanochipMissingWafers）把 NH 的两步链还原 ——"
                + "第一步 激光蚀刻机 母晶圆与橙色透镜(不消耗) 造出同级晶圆，第二步 板材切割机 同级晶圆切出芯片；"
                + "原 5 条一步链（激光蚀刻机 母晶圆直接到芯片）就地改写成第二步，净增 5 条；"
                + "交付说明见 _census/新造晶圆还原两步链_队员.md）"
                + " + 200 反向换算（2026-09-19 队长方案A「简单模式下换算也要能用样板总成下单」："
                + "为 conversion/ 里 200 条「真件 → CC」逐条生成对偶「CC → 真件」，落独立目录 conversion_reverse/；"
                + "生成器 _census/gen_nac_conversion_reverse.js（正向表是唯一真相源），"
                + "守卫体检 _census/check_conversion_reverse.js）"
                + " + 2 机壳装配线版（★ 2026-09-21 队长给数：计算矩阵 / 防火墙投影机壳各一条装配线配方，"
                + "用 Eco 模组的 CE9 闪存晶阵 / FT9 并行核心 / 合成子系统结构散热器；包内没有 Eco 时由原组装机版生效"
                + "——两条原配方已加 forge:not 条件，两份配方互斥不共存）");
    }
}
