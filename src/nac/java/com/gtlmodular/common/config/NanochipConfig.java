package com.gtlmodular.common.config;

import com.gtlmodular.common.diag.SwallowedErrors;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class NanochipConfig {

    private final ForgeConfigSpec.BooleanValue FAN_OUT_SAME_RECIPE;
    private final ForgeConfigSpec.BooleanValue PER_SLOT_PARALLEL_BOUND;
    private final String fileName;
    private final List<String> builtInCalibrationWhitelist;
    private final ForgeConfigSpec.BooleanValue NANOCHIP_ENABLED;
    private final ForgeConfigSpec.IntValue NANOCHIP_DIRECT_PARALLEL_CAP;
    private final ForgeConfigSpec.IntValue NANOCHIP_DIRECT_THREADS;
    private final ForgeConfigSpec.BooleanValue NANOCHIP_CALIBRATION_USE_FULL_WINDOW;
    private final ForgeConfigSpec.BooleanValue NANOCHIP_VACUUM_REQUIRES_PIPES;
    private final ForgeConfigSpec.DoubleValue NANOCHIP_IMPURITY_PER_RECIPE;
    private final ForgeConfigSpec.BooleanValue NANOCHIP_DEBUG_LOGS;
    private final ForgeConfigSpec.BooleanValue NANOCHIP_VERBOSE_STRUCTURE_LOGS;
    private final ForgeConfigSpec.BooleanValue NANOCHIP_SIMPLE_LOGISTICS;
    private final ForgeConfigSpec.BooleanValue NANOCHIP_GRID_POWER;
    private final ForgeConfigSpec.BooleanValue NANOCHIP_HIDE_ORPHAN_ITEMS;
    private final ForgeConfigSpec.BooleanValue NANOCHIP_ORIGINAL_MODE;
    private final ForgeConfigSpec.ConfigValue<List<? extends String>> NANOCHIP_CALIBRATION_RECIPE_WHITELIST;

    // 每个产物各持有一组 ConfigValue，避免第二个 Builder 覆盖已有值的 Spec 归属。
    public NanochipConfig(ForgeConfigSpec.Builder builder, String registryNamespace) {
        fileName = registryNamespace + "-common.toml";
        builtInCalibrationWhitelist = List.of(
                registryNamespace + ":nanochip/assembly_complex/*",
                registryNamespace + ":nanochip/assembly_matrix/*");
        NANOCHIP_ENABLED =
            builder.comment("纳米芯片组装复合体总开关（默认开）")
                    .define("nanochipComplex.enabled", true);

        NANOCHIP_DIRECT_PARALLEL_CAP =
            builder.comment("NAC「直通档」的并行上限（默认 1024）",
                            "直通档 = 不依赖组装矩阵模块、直接用样板总成/AE 推样板出芯片的那条路。",
                            "模块里的『纳米封装包装器』每台 ×4 并行、『纳米组件分流器』每台 ×2 并行（每种最多计 2 台）。",
                            "想要原版上限（无限并行）就得把『纳米芯片组装矩阵』模块立在复合体旁边，",
                            "届时并行不再受这个值限制，且配方时长下限从 20 秒降到 5 秒。")
                    .defineInRange("nanochipComplex.directParallelCap", 1024, 1, Integer.MAX_VALUE);

        NANOCHIP_DIRECT_THREADS =
            builder.comment("NAC「直通档」的跨配方线程基线（默认 1）",
                            "跨配方线程 = 同一台复合体在同一时刻挂起的【不同配方】条数（每条各自独立进度与完成时刻）。",
                            "直通档默认 1 条；全流程档（组装矩阵模块在线）不吃这个值，改为按电压等级给线程。")
                    .defineInRange("nanochipComplex.directCrossRecipeThreads", 1, 1, 16);

        FAN_OUT_SAME_RECIPE = builder.comment(
                        "默认关闭：同一配方已有批次时不再新增；开启后可用空闲线程继续开批，不取消在跑批次。",
                        "Disabled by default: do not add a batch while the same recipe is running. Enable to use spare threads; running batches are retained.",
                        "同料、同机、相同供电和出料条件下，关闭时单位时间产出低于开启时才应打开。",
                        "Enable only if output per unit time is lower when disabled with the same inputs, machine, power and output conditions.",
                        "专服以服务端配置为准；客户端配置屏不会修改远程服务器。改服务端文件后重启。",
                        "Dedicated servers use server configuration. The client screen does not change a remote server. Restart after editing its file.")
                .define("nanochipComplex.fanOutSameRecipe",
                        com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic.FAN_OUT_SAME_RECIPE);
        PER_SLOT_PARALLEL_BOUND = builder.comment(
                        "默认关闭：只有首可用槽可得量远小于全槽汇总、最大单槽并行确实更大，且单条并行被首槽卡住时才应打开。",
                        "Disabled by default: enable only when first-slot inputs are far below the aggregate, another single slot supports more parallel, and the first slot limits a batch.",
                        "全槽总和不允许跨槽拼料；等量多槽只有总和更大不保证受益。实际准入和扣料仍须通过原有检查。",
                        "The aggregate does not allow cross-slot consumption. Equal-sized slots alone do not guarantee a benefit. Admission and consumption still use the existing checks.",
                        "专服以服务端配置为准；客户端配置屏不会修改远程服务器。改服务端文件后重启。",
                        "Dedicated servers use server configuration. The client screen does not change a remote server. Restart after editing its file.")
                .define("nanochipComplex.perSlotParallelBound",
                        com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic.PER_SLOT_PARALLEL_BOUND);

        NANOCHIP_CALIBRATION_USE_FULL_WINDOW =
            builder.comment("NAC 纳米校准「判定分母」模式（默认 false = 已记录数）",
                            "false（默认）：分母用「已记录数」⇒ 窗口没攒满时族条涨得看得见，攒满 10 万后与 true 完全一致",
                            "true ：分母固定为原版硬编码的 100000（= 整个历史窗口），第一档 20％ 要攒够 20000 个对应电路",
                            "只改分母；窗口容量/批次数/电路族/阈值与三档累积关系都不变")
                    .define("nanochipComplex.calibrationUseFullWindow", false);

        NANOCHIP_VACUUM_REQUIRES_PIPES =
            builder.comment("NAC 真空传送路由模式（默认 false = 颜色即线、不用铺管道）",
                            "NAC vacuum routing mode (default false = color channels without pipes).",
                            "旧真空物流的两种路由均已废弃，不保证可用；建议保持 simpleLogisticsMode=true。",
                            "Both legacy vacuum routing modes are deprecated; functionality is not guaranteed. Keep simpleLogisticsMode=true (recommended).",
                            "false：按颜色通道；true：按同色管网分量；此处的颜色路由不是简单物流模式。",
                            "false: color channels; true: same-color pipe components. Color routing is not simple logistics mode.",
                            "保留旧配置供存档兼容；建议修改后重启游戏，不保证旧网络迁移或传输可靠。",
                            "Retained for existing saves. Restart after changes; legacy network migration and transfer are not guaranteed.")
                    .define("nanochipComplex.vacuumRequiresPipes", false);

        NANOCHIP_IMPURITY_PER_RECIPE =
            builder.comment("NAC「全板浸没装置」每完成一次配方上涨的浸泡液杂质百分点（默认 0.5）",
                            "原版由流体量 / 100 万 L 槽容决定上涨速度，这里折算成固定步进（0 = 关闭杂质机制）。",
                            "杂质三段式的效果照抄原版：",
                            "  0–15%  ：EU ×(1 − (30 − 2y)/100)，y=0 时省 30%",
                            "  15–65% ：EU ×1.00（默认行为）",
                            "  65–100%：EU ×(1 + (y − 65)×2%)，跑脏了反而更费电",
                            "到「排液阈值」（默认 100%，机器界面显示）就自动排液并把杂质清零。")
                    .defineInRange("nanochipComplex.impurityPerRecipe", 0.5D, 0.0D, 100.0D);

        NANOCHIP_DEBUG_LOGS =
            builder.comment("NAC 调试日志（默认关）：排液、真空搬运异常等细节日志")
                    .define("nanochipComplex.debugLogs", false);

        NANOCHIP_VERBOSE_STRUCTURE_LOGS =
            builder.comment("NAC 结构日志「全打」开关（排障用，默认 true = 一律 INFO、不折叠）",
                            "NAC structure-log verbosity (troubleshooting, default true = always INFO)",
                            "true （默认）：结构结论 / 逐格原因（期望方块 vs 实际方块 / GTCEu 记录位置）一律 INFO、不折叠、不降 debug；",
                            "true  (default): every structure conclusion and per-cell reason goes to INFO, never folded, never demoted to debug;",
                            "       代价 = 日志量很大，一进游戏 NAC 就持续刷日志；",
                            "       cost = very noisy logs from the moment the world loads;",
                            "false：降噪（同机同结论 10 秒一条 + 跨机器折叠成一条汇总，重复降 debug）；",
                            "false: quiet mode (one line per clock + one cross-machine summary, repeats at debug);",
                            "       排障结束后请改回 false。改这一项【不用重启】：配置界面「NAC 校准与排障」里点一下「保存并关闭」当场生效、并立刻写回 toml；只有手改 config/" + fileName + " 才需要重启游戏。",
                            "       set it back to false when the investigation is over. No restart needed: toggle it in the in-game config screen and save - it applies at once and is written straight back to the toml; only editing the toml by hand requires a game restart.",
                            "只改日志级别，不改任何判定：落点仅是 NanochipStructureLogGate.shouldEmitInfo 的返回值",
                            "Log level only, no decision changes: the only hook is the return value of NanochipStructureLogGate.shouldEmitInfo")
                    .define("nanochipComplex.verboseStructureLogs", true);

        NANOCHIP_SIMPLE_LOGISTICS =
            builder.comment("NAC 物流模式开关（默认 true = 简单模式）",
                            "NAC logistics mode switch (default true = simple mode)",
                            "关（原版 NH 真空物流）已废弃，不保证可用；建议保持简单模式。originalMode 是独立的结构开关，不在废弃范围内。",
                            "OFF (vanilla NH vacuum logistics) is deprecated; functionality is not guaranteed. Keep simple mode enabled (recommended). originalMode is a separate structure option and is not deprecated.",
                            "开（默认，简单）：主机 H 位真空仓下限为 0，可用样板仓或普通输入/输出仓供料、取料。",
                            "ON (default, simple): the host H-slot vacuum-hatch minimum is 0; use pattern buffers or ordinary input/output buses and hatches.",
                            "关（原版 NH）：CC/PC 走真空传送带仓，主机 H 位要求至少 1 个真空仓；不追加简单模式的换算配方表。",
                            "OFF (vanilla NH): CC/PC use vacuum conveyor hatches; host H slots require at least 1 vacuum hatch. The simple-mode conversion table is not added.",
                            "简单模式下，主机同轮搜索组装与换算配方，无需样板仓切换主机模式；换算仅适用于有对应配方的真件与未处理 CC，PC 不能还原。",
                            "In simple mode, the host searches assembly and conversion recipes together; buffers need not switch its mode. Conversion requires a matching recipe for a real item and unprocessed CC; PC cannot be reverted.",
                            "AE 用法：主机 H 位或模块 V 位放 gtlcore 原生 ME 样板仓并接网，按 JEI 编码加工样板后在 ME 终端下单；真件→CC 与 CC→真件分别编码，模块还需备足耗材液体。",
                            "AE: connect a native gtlcore ME pattern buffer in host H slots or module V slots to ME, encode processing patterns from JEI, then order at the terminal. Encode each conversion direction separately and supply module upkeep fluids.",
                            "镜像连接可选：可在仓位放 ME 样板总成镜像，用闪存先右键源样板仓存坐标，再右键镜像绑定；也可直接放样板仓。",
                            "Mirrors are optional: use an ME Pattern Buffer Proxy in a hatch slot, right-click the source buffer with a data stick, then right-click the proxy to bind it; a direct buffer also works.",
                            "普通仓用法：在主机 H 位或模块 V 位按配方放物品/流体输入仓，并配置对应输出仓取走产物；普通输出仓不会自动回 ME。",
                            "Ordinary hatches: use item/fluid input buses or hatches in host H slots or module V slots, and matching output buses or hatches to collect products. Ordinary outputs do not return to ME automatically.",
                            "AE 出料需有连接 ME 的输出能力；只有输入能力的样板仓需另配输出设备，镜像的实际能力取决于所绑定的源仓。",
                            "AE return requires an output-capable device connected to ME. An input-only pattern buffer needs a separate output device; a proxy inherits its source buffer capabilities.",
                            "产出与物流模式无关：组装矩阵配方基础产量为处理器 8 / 装配 4 / 电脑 2 / 主机 1；本开关不缩放产量。",
                            "Logistics mode does not change output: base Assembly Matrix recipe yields are processor 8 / assembly 4 / computer 2 / mainframe 1; this switch does not scale them.",
                            "改这一项需要重启游戏，才能让结构的真空仓要求与所选物流模式一致。",
                            "Restart the game after changing this option so the vacuum-hatch requirement matches the selected logistics mode.")
                    .define("nanochipComplex.simpleLogisticsMode", true);

        NANOCHIP_GRID_POWER =
            builder.comment("NAC 电网取电（默认 true）：从绑定账户（或其 FTB 队伍）的 GTMThings 无线电网取电",
                            "true ：电网优先、能源仓兜底（电网余额不够时用仓；两者都没有 ⇒ 原地等电，不退进度、不掉料）",
                            "false：必须有能源仓，\"直接从电网取电\"整条关闭",
                            "电网账户：玩家放置控制器时自动绑定到放置者；没有绑定时空手潜行右键控制器绑定到自己。",
                            "本项独立于 nanochipComplex.originalMode 与 nanochipComplex.simpleLogisticsMode。",
                            "取电层是运行期读配置（改完即生效）；结构层「能源仓 min 0」在机器注册时构建一次，不受本项影响。")
                    .define("nanochipComplex.gridPower.enabled", true);

        NANOCHIP_HIDE_ORPHAN_ITEMS =
            builder.comment("NAC 孤儿封装件在 JEI 里隐藏（默认 true = 隐藏）",
                            "Hide the 41 orphan NAC circuit components in JEI (default true = hidden)",
                            "true （默认）：有产出配方、但没有任何配方消费的 41 件 CC 封装件在 JEI 里不显示，清单见 assets/gtlmodular/jei/hidden_orphans.txt",
                            "true  (default): the 41 items listed in assets/gtlmodular/jei/hidden_orphans.txt are hidden in JEI",
                            "false：主动撤销隐藏，恢复可见（将来想找它们就关掉这一项）",
                            "false: un-hide them so they show up again in JEI",
                            "只影响 JEI 显示层：不删注册、不注销 Entry、不动任何配方 ⇒ 隐藏后仍可用配方/指令/创造拿到",
                            "Display-layer only: nothing is unregistered and no recipe is changed, so the items stay obtainable",
                            "改完在配置屏点「保存并关闭」立即生效（没有装 JEI 时本项无副作用）",
                            "Takes effect on saving the config screen; harmless when JEI is absent.")
                    .define("nanochipComplex.hideOrphanItems", true);

        NANOCHIP_ORIGINAL_MODE =
            builder.comment("NAC「原初模式」开关（默认 true）：结构/成型层已经**整条交给上游**，本项现在只剩「模块连接怎么重算」这一处",
                            "NAC 'original mode' (default true): structure/formation are now fully upstream; this flag only picks how module links are re-scanned",
                            "true （默认）：模块连接由复合体按结构 F 位自行重算（原初行为）。",
                            "false：沿用本模组此前的“催促 + 合并重算”路径（保留，便于对比排查）。",
                            "",
                            "变迁说明：早在 2026-10-28，A+ 精确预检、结构检查节流/变化闸、拆块位置索引整条链已从代码里删除，",
                            "不再由本开关控制 —— 那时起「机器无法成型」的成因（预检在上游匹配之前 return false）也一并消失。",
                            "如果日志里还出现『裸 return（必然不成形，直接挡掉）』，说明跑的是旧 jar。",
                            "",
                            "true  (default): module links re-scanned by the complex from structure F slots (stock behaviour).",
                            "false: keep our older 'prompt + coalesced rescan' path, for A/B diagnostics only.",
                            "",
                            "History: on 2026-10-28 the A+ exact precheck, the structure-check throttle/change-gate and the block-break position index",
                            "were deleted outright and are no longer toggled by this flag; that also removed the cause of 'machine never forms'",
                            "(the precheck answered 'certainly unformed' before upstream ever ran a real match).",
                            "If the log still shows 'bare return', the running jar is old.",
                            "",
                            "零存档影响：不改结构图案、不新增持久化字段、不改 NBT、不改方块 id。",
                            "Zero save impact: no pattern change, no persisted field, no NBT change, no block id change.")
                    .define("nanochipComplex.originalMode", true);

        NANOCHIP_CALIBRATION_RECIPE_WHITELIST =
            builder.comment("NAC 校准计数白名单：只有名单里的配方（最终产出电路的配方）才增加校准计数",
                            "每一项 = 一个配方 ID（namespace:path）；以 * 结尾 = 前缀匹配（例 abc* 命中 abcdef），否则 = 精确匹配（例 abc 只命中 abc）",
                            "命中即允许计数；不命中 ⇒ 这条配方不计入校准（换算表、只产中间件 cc_processed_* 的前道工序因此不再涨校准）",
                            "列表为空 = 回退到内置默认名单（= 那两张真正造电路的表的 30 条），刻意不是「一条都不计」：清空 = 恢复默认，不会让校准永远为 0",
                            "默认 = " + registryNamespace + ":nanochip/assembly_complex/* + " + registryNamespace + ":nanochip/assembly_matrix/*（全模组唯一带 circuit-calibration 元数据的 30 条 = 成品电路）",
                            "要放开某条新配方的记账：在下面这个列表里加一行，ID 从日志「校准白名单未命中 ⇒ 这条配方不计入校准：<id>」里整行复制即可；前缀匹配就写 `xxx*`",
                            "一行回退到「全部计入」：NanochipAssemblyComplexMachine.CALIBRATION_WHITELIST_ENABLED = false（本项随即被完全忽略）",
                            "本项列出的是最终产出电路的组装/装配配方；换算（真件 <-> CC 互换）与任何中间件工序都不属于「造电路」",
                            "生效时机：每次记账现读 ⇒ 配置屏改完即生效；只有手改 config/" + fileName + " 才需要重启游戏。",
                            "Whichever recipe is listed here is allowed to increase the calibration counters; entries ending in '*' mean prefix match. Empty list falls back to the built-in default list.")
                    .defineList("nanochipComplex.calibrationRecipeWhitelist", builtInCalibrationWhitelist,

                            o -> o instanceof String);

    }

    public boolean isFanOutSameRecipe() {
        return readBoolean(FAN_OUT_SAME_RECIPE);
    }

    public boolean isPerSlotParallelBound() {
        return readBoolean(PER_SLOT_PARALLEL_BOUND);
    }

    public void setFanOutSameRecipe(boolean value) {
        FAN_OUT_SAME_RECIPE.set(value);
    }

    public void setPerSlotParallelBound(boolean value) {
        PER_SLOT_PARALLEL_BOUND.set(value);
    }

    private static boolean readBoolean(ForgeConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    public boolean isVacuumRequiresPipes() {
        try {
            return NANOCHIP_VACUUM_REQUIRES_PIPES.get();
        } catch (Throwable ignored) {
            SwallowedErrors.report("NanochipConfig#isVacuumRequiresPipes", ignored);
            return NANOCHIP_VACUUM_REQUIRES_PIPES.getDefault();
        }
    }

    public boolean isNanochipComplexEnabled() {
        try {
            return NANOCHIP_ENABLED.get();
        } catch (Throwable ignored) {
            SwallowedErrors.report("NanochipConfig#isNanochipComplexEnabled", ignored);
            return NANOCHIP_ENABLED.getDefault();
        }
    }

    public boolean isNanochipOriginalMode() {
        try {
            return NANOCHIP_ORIGINAL_MODE.get();
        } catch (Throwable ignored) {
            SwallowedErrors.report("NanochipConfig#isNanochipOriginalMode", ignored);
            return NANOCHIP_ORIGINAL_MODE.getDefault();
        }
    }

    public int getNanochipDirectParallelCap() {
        try {
            return NANOCHIP_DIRECT_PARALLEL_CAP.get();
        } catch (Throwable ignored) {
            SwallowedErrors.report("NanochipConfig#getNanochipDirectParallelCap", ignored);
            return NANOCHIP_DIRECT_PARALLEL_CAP.getDefault();
        }
    }

    public int getNanochipDirectCrossRecipeThreads() {
        try {
            return Math.max(1, NANOCHIP_DIRECT_THREADS.get());
        } catch (Throwable ignored) {
            SwallowedErrors.report("NanochipConfig#getNanochipDirectCrossRecipeThreads", ignored);
            return NANOCHIP_DIRECT_THREADS.getDefault();
        }
    }

    public boolean isNanochipCalibrationUseFullWindow() {
        try {
            return NANOCHIP_CALIBRATION_USE_FULL_WINDOW.get();
        } catch (Throwable ignored) {
            SwallowedErrors.report("NanochipConfig#isNanochipCalibrationUseFullWindow", ignored);
            return NANOCHIP_CALIBRATION_USE_FULL_WINDOW.getDefault();
        }
    }

    public void setNanochipCalibrationUseFullWindow(boolean value) {
        if (NANOCHIP_CALIBRATION_USE_FULL_WINDOW.get() != value) {
            NANOCHIP_CALIBRATION_USE_FULL_WINDOW.set(value);
        }
    }

    // 清空名单仍允许成品电路积累校准，避免清空一次就永久停在零。
    public List<String> getNanochipCalibrationRecipeWhitelist() {
        List<? extends String> configured;
        try {
            configured = NANOCHIP_CALIBRATION_RECIPE_WHITELIST.get();
        } catch (Throwable ignored) {
            SwallowedErrors.report("NanochipConfig#getNanochipCalibrationRecipeWhitelist", ignored);
            configured = NANOCHIP_CALIBRATION_RECIPE_WHITELIST.getDefault();
        }
        if (configured == null || configured.isEmpty()) {

            return builtInCalibrationWhitelist;
        }
        return List.copyOf(configured);
    }

    public int getNanochipCalibrationRecipeWhitelistCount() {
        return getNanochipCalibrationRecipeWhitelist().size();
    }

    public double getNanochipImpurityPerRecipe() {
        try {
            return NANOCHIP_IMPURITY_PER_RECIPE.get();
        } catch (Throwable ignored) {
            SwallowedErrors.report("NanochipConfig#getNanochipImpurityPerRecipe", ignored);
            return NANOCHIP_IMPURITY_PER_RECIPE.getDefault();
        }
    }

    public boolean isNanochipDebugLogsEnabled() {
        try {
            return NANOCHIP_DEBUG_LOGS.get();
        } catch (Throwable ignored) {
            SwallowedErrors.report("NanochipConfig#isNanochipDebugLogsEnabled", ignored);
            return NANOCHIP_DEBUG_LOGS.getDefault();
        }
    }

    public boolean isNanochipVerboseStructureLogs() {
        try {
            return NANOCHIP_VERBOSE_STRUCTURE_LOGS.get();
        } catch (Throwable ignored) {
            SwallowedErrors.report("NanochipConfig#isNanochipVerboseStructureLogs", ignored);
            return NANOCHIP_VERBOSE_STRUCTURE_LOGS.getDefault();
        }
    }

    public void setNanochipVerboseStructureLogs(boolean value) {
        if (NANOCHIP_VERBOSE_STRUCTURE_LOGS.get() != value) {
            NANOCHIP_VERBOSE_STRUCTURE_LOGS.set(value);
        }
    }

    // 注册阶段配置可能尚不可读，必须使用该配置自身的默认值。
    public boolean isNanochipSimpleLogistics() {
        try {
            return NANOCHIP_SIMPLE_LOGISTICS.get();
        } catch (Throwable ignored) {
            SwallowedErrors.report("NanochipConfig#isNanochipSimpleLogistics", ignored);
            return NANOCHIP_SIMPLE_LOGISTICS.getDefault();
        }
    }

    public boolean isNanochipGridPowerEnabled() {
        try {
            return NANOCHIP_GRID_POWER.get();
        } catch (Throwable ignored) {
            SwallowedErrors.report("NanochipConfig#isNanochipGridPowerEnabled", ignored);
            return NANOCHIP_GRID_POWER.getDefault();
        }
    }

    public void setNanochipSimpleLogistics(boolean value) {
        if (NANOCHIP_SIMPLE_LOGISTICS.get() != value) {
            NANOCHIP_SIMPLE_LOGISTICS.set(value);
        }
    }

    public boolean isHideOrphanItemsEnabled() {
        try {
            return NANOCHIP_HIDE_ORPHAN_ITEMS.get();
        } catch (Throwable ignored) {
            SwallowedErrors.report("NanochipConfig#isHideOrphanItemsEnabled", ignored);
            return NANOCHIP_HIDE_ORPHAN_ITEMS.getDefault();
        }
    }

    public void setHideOrphanItemsEnabled(boolean value) {
        if (NANOCHIP_HIDE_ORPHAN_ITEMS.get() != value) {
            NANOCHIP_HIDE_ORPHAN_ITEMS.set(value);
        }
    }

    public ForgeConfigSpec.BooleanValue gridPowerValue() {
        return NANOCHIP_GRID_POWER;
    }

    public List<String> defaultCalibrationRecipeWhitelist() {
        return builtInCalibrationWhitelist;
    }

    public String fileName() {
        return fileName;
    }

    public void save() {
        NANOCHIP_ENABLED.save();
    }
}
