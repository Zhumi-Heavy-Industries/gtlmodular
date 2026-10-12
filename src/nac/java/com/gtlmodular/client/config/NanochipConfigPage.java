package com.gtlmodular.client.config;

import com.gtlmodular.common.config.NanochipConfig;

import java.util.function.Consumer;

public final class NanochipConfigPage {

    private NanochipConfigPage() {
    }

    // 页面只描述 NAC 项，让混合界面和独立界面复用同一份读写与提示。
    public interface Rows {
        void section(String title);

        void addBool(String label, boolean value, boolean fallback, Consumer<Boolean> save,
                     String... tooltipLines);
    }

    public static void append(Rows rows, NanochipConfig config) {
        rows.section("NAC 物流模式");
        rows.addBool("简单物流模式", config.isNanochipSimpleLogistics(), true,
                config::setNanochipSimpleLogistics,
                "简单物流模式 / Simple logistics mode",
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
                "Restart the game after changing this option so the vacuum-hatch requirement matches the selected logistics mode.");

        rows.section("NAC 孤儿封装件");
        rows.addBool("JEI 隐藏孤儿件", config.isHideOrphanItemsEnabled(), true,
                config::setHideOrphanItemsEnabled,
                "JEI 隐藏孤儿件 / Hide orphan items in JEI",
                "开（默认）：_census/残留物品清理_队员.md §8.1 那 42 件（有产出配方、但现存配方没人消费的 CC 封装件）在 JEI 里不显示",
                "ON (default): the 42 orphan circuit components are hidden in JEI",
                "关：主动撤销隐藏，恢复可见（将来想找它们就关掉这一项）",
                "OFF: un-hide them so they show up again",
                "只影响 JEI 显示层：不删注册、不注销 Entry、不动任何配方 ⇒ 仍可用配方/指令/创造模式拿到",
                "Display-layer only: nothing is unregistered and no recipe is changed");

        rows.section("NAC 校准与排障");
        rows.addBool("校准分母用原版10万", config.isNanochipCalibrationUseFullWindow(), false,
                config::setNanochipCalibrationUseFullWindow,
                "校准分母：用原版 10 万 / Calibration denominator: vanilla 100000",
                "开（= 配置文件 nanochipComplex.calibrationUseFullWindow = true）= 原版旧口径：判定分母恒为写死的 100000（整个历史窗口）",
                "ON (= config nanochipComplex.calibrationUseFullWindow = true) = the old vanilla denominator, hard-coded to 100000 (the whole history window)",
                "      ⇒ 刚跑起来时各电路族占比只有千分之几，族条几乎不动，湿件第一档（20%）要攒够 20000 个对应电路才看得见",
                "      => a freshly started machine shows only a few tenths of a percent, so tier 1 (20%) needs 20,000 matching circuits before anything moves",
                "关（= false，★ 代码里的默认值，队长 2026-09-21 拍板）= 新口径：分母 = 你实际记录过的电路总数 ⇒ 做完一批就看得见；攒满 10 万个之后两种口径完全一致",
                "OFF (= false, the code default since 2026-09-21) = the denominator is the number of circuits actually recorded so far, so a single batch is visible right away; identical to the vanilla branch once 100,000 circuits are recorded",
                "只换分母：窗口容量 100000、批次数 100、每批 1000、9 个电路族与 0.2 / 0.4 / 0.6 阈值都不变",
                "Only the divisor changes: window 100000, 100 batches, the 9 circuit families and the 0.2 / 0.4 / 0.6 thresholds are untouched",
                "★ 改完点「保存并关闭」立即生效（机器每 tick 现读配置，无需重启）；同一次保存会立刻写回 config/" + config.fileName() + "，不用再手改 toml",
                "★ Saving applies it immediately (the machine re-reads the config every tick, no restart) and the same save also rewrites config/" + config.fileName() + ", so no manual toml edit is needed");

        rows.addBool("结构日志全打（排障用）", config.isNanochipVerboseStructureLogs(), true,
                config::setNanochipVerboseStructureLogs,
                "结构日志全打（排障用）/ Verbose NAC structure logs (troubleshooting)",
                "开（= true，★ 代码里的默认值）= 主机/模块每给一次结构结论，就把「尚未成形」的逐格原因打进日志（期望方块 vs 实际方块 + GTCEu 记录位置），一律 INFO、不折叠、不降 debug",
                "ON (= true, the code default) = every structure conclusion logs its per-cell reasons at INFO (expected block vs actual block + the GTCEu-recorded position), never folded and never demoted to debug",
                "      ⇒ 代价是日志量回到队长抱怨过的「一进游戏 NAC 一直刷」那种量级；查完请改回「关」",
                "      => the cost is the noisy log volume the captain complained about; switch it back OFF when the investigation is over",
                "关（= false）= 回到 0.1.40 的降噪口径：同一台机器 + 同一结论 + 同一原因 10 秒内只留一条 INFO，窗口内的重复降 debug，跨机器再合并成一条「NAC 结构结论未变汇总」",
                "OFF (= false) = the quiet 0.1.40 behaviour: one INFO per machine/conclusion/reason per 10 seconds, repeats demoted to debug, plus one folded cross-machine summary line",
                "队长原话（2026-09-21）：「我更新了add，还有core现在我们的模块都不做东西了」＋（对我给的三个选项回）「查」—— 他这台机器的 logs/debug.log 是 09/15 的旧文件 ⇒ 逐格原因看不见、只能猜，本开关就是把这个口子重新打开",
                "Captain's own words (2026-09-21): after updating add/core the modules stopped doing anything, and he answered \"investigate\" -- his logs/debug.log was stale, so the per-cell reasons were invisible; this switch reopens that hole",
                "★ 只改「打不打、打多细」，一个判定都不动：唯一落点是 NanochipStructureLogGate.shouldEmitInfo 的返回值（只决定 LOGGER.info 还是 LOGGER.debug）",
                "★ Log verbosity only, no decision changes: the single hook is the return value of NanochipStructureLogGate.shouldEmitInfo, which only picks LOGGER.info vs LOGGER.debug",
                "★ 在界面里改完点「保存并关闭」立即生效（闸门每次判定都现读配置，无需重启）；只有手改 config/" + config.fileName() + " 才需要重启游戏",
                "★ Saving the GUI applies it immediately (the gate re-reads the config on every check, no restart); only editing config/" + config.fileName() + " by hand requires a game restart");

    }
}
