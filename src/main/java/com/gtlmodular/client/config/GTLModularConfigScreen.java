package com.gtlmodular.client.config;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/**
 * 游戏内配置界面（Mods → GTL更多实用设备 → 配置）。
 *
 * <p><b>本模组所有配置项都在这里</b>，分七组：双流体税 / 线圈减免 / 猪咪黑奴挖矿机 / 猪咪样板总成 /
 * NAC 物流模式（2026-09-19 新增：原版(NH) ⇄ 简单）/ NAC 孤儿封装件（2026-09-19 新增：JEI 隐藏 42 件孤儿）/
 * NAC 校准与排障（★ 2026-09-21 队长令「做」新增：校准分母口径 + 结构日志全打）。
 * 数字框支持①直接键入 ②鼠标悬停滚轮 ±1 微调（Shift = ±10）；开关点一下切换。
 * 点「保存」即实时写入（机器逻辑实时读取，无需重启），Esc 放弃。
 *
 * <p>★ 2026-09-21 落盘口径（javap 取证，写在这里省得以后再猜）：Forge 的 TOML 配置器
 * {@code ConfigFileTypeHandler.lambda$reader$1} 建文件时链了 nightconfig 的 {@code autosave()}
 * ⇒ 本界面每次写值都会<b>立刻</b>改写 {@code config/gtlmodular-common.toml}（不是等退出游戏），
 * 所以玩家改完界面<b>不需要</b>再去手改 toml，也不会出现「下次启动又被旧 toml 里的旧值覆盖」。
 * 反过来说：<b>手改 toml 仍然要重启</b>（Forge 只在启动时读文件）。
 *
 * <p>布局自适应：两列排布，窗口窄时按当前宽度分列，不会顶出屏幕。
 */
public final class GTLModularConfigScreen extends Screen {

    private static final int MARGIN_X = 16;
    private static final int MARGIN_TOP = 30;
    private static final int LABEL_W = 132;
    private static final int BOX_W = 96;
    private static final int ROW_H = 18;
    private static final int SECTION_H = 14;
    private static final int TOGGLE_W = 44;
    private static final int WIDGET_H = 16;

    private final Screen parent;

    private final List<IntRow> intRows = new ArrayList<>();
    private final List<DoubleRow> doubleRows = new ArrayList<>();
    private final List<StringRow> stringRows = new ArrayList<>();
    private final List<BoolRow> boolRows = new ArrayList<>();

    /** 当前列的起始 x / 当前列可用的右边界。 */
    private int columnX;
    private int columnRight;
    private int cursorY;
    private int columnIndex;

    private GTLModularConfigScreen(Screen parent) {
        super(Component.literal("GTL更多实用设备 · 配置"));
        this.parent = parent;
    }

    /** 供 Forge ConfigGUI 扩展点调用。 */
    public static Screen create(Minecraft minecraft, Screen parent) {
        return new GTLModularConfigScreen(parent);
    }

    // ================= 行模型 =================

    private static final class IntRow {
        final EditBox box;
        final IntConsumer save;
        final int fallback;

        IntRow(EditBox box, IntConsumer save, int fallback) {
            this.box = box;
            this.save = save;
            this.fallback = fallback;
        }
    }

    private static final class DoubleRow {
        final EditBox box;
        final DoubleConsumer save;
        final double fallback;

        DoubleRow(EditBox box, DoubleConsumer save, double fallback) {
            this.box = box;
            this.save = save;
            this.fallback = fallback;
        }
    }

    private static final class StringRow {
        final EditBox box;
        final Consumer<String> save;
        final String fallback;

        StringRow(EditBox box, Consumer<String> save, String fallback) {
            this.box = box;
            this.save = save;
            this.fallback = fallback;
        }
    }

    private static final class BoolRow {
        final Button button;
        final String label;
        final Consumer<Boolean> save;
        boolean value;
        final boolean fallback;

        BoolRow(Button button, String label, Consumer<Boolean> save, boolean value, boolean fallback) {
            this.button = button;
            this.label = label;
            this.save = save;
            this.value = value;
            this.fallback = fallback;
            refresh();
        }

        void refresh() {
            button.setMessage(Component.literal(value ? "§a开" : "§7关"));
        }
    }

    // ================= 布局 =================

    @Override
    protected void init() {
        int columnWidth = LABEL_W + BOX_W + 24;
        int usable = this.width - MARGIN_X * 2;
        int columns = Math.max(1, Math.min(2, usable / columnWidth));
        // ★ 2026-09-21 队长令「做」又添 2 行（校准分母 / 结构日志全打）⇒ 20.0 → 22.0，
        //   与 2026-09-19 加「JEI 隐藏孤儿件」那次同款（那次 19.0 → 20.0）。
        //   ⚠ 这个数只是估算（breakColumn 实际上不按它切分），真正管「放不放得下」的是下面的换列断点。
        int perColumn = (int) Math.ceil(22.0 / columns);   // 22 个数值/开关行 + 分组标题
        this.columnX = MARGIN_X;
        this.columnRight = columnX + columnWidth;
        this.cursorY = MARGIN_TOP;
        this.columnIndex = 0;

        // ---- 第 1 组：双流体税 ----
        section("流体税（模块运行税）");
        addBool("启用双流体税", GTLModularConfig.isDualFluidTaxEnabled(), true,
                GTLModularConfig::setEnabled);
        addString("岩浆流体 id", GTLModularConfig.getLavaFluidId(), "minecraft:lava",
                GTLModularConfig::setLavaFluidId);
        addString("液态烈焰流体 id", GTLModularConfig.getBlazeFluidId(), "gtceu:blaze",
                GTLModularConfig::setBlazeFluidId);
        addInt("IV 每秒扣量 (mb)", GTLModularConfig.getIvBaselineMbPerSecond(), 83,
                GTLModularConfig::setIvBaselineMbPerSecond);
        addDouble("每高一档倍率 (×)", GTLModularConfig.getPerTierMultiplier(), 4.0,
                GTLModularConfig::setPerTierMultiplier);
        addInt("基准电压档", GTLModularConfig.getBaselineTier(), 5,
                GTLModularConfig::setBaselineTier);
        addBool("允许低于基准运行", GTLModularConfig.isAllowBelowBaseline(), false,
                GTLModularConfig::setAllowBelowBaseline);
        addInt("扣税间隔 (tick)", GTLModularConfig.getDrainIntervalTicks(), 20,
                GTLModularConfig::setDrainIntervalTicks);

        // ---- 第 2 组：线圈减免 ----
        section("线圈减免（枢纽供能）");
        addBool("启用线圈减免", GTLModularConfig.isCoilDiscountEnabled(), true,
                GTLModularConfig::setCoilDiscountEnabled);
        addInt("基准温度 (K)", GTLModularConfig.getCoilDiscountBaseTemperature(), 5400,
                GTLModularConfig::setCoilDiscountBaseTemperature);
        addInt("每级温度 (K)", GTLModularConfig.getCoilDiscountTemperaturePerLevel(), 240,
                GTLModularConfig::setCoilDiscountTemperaturePerLevel);
        addDouble("每级减免系数", GTLModularConfig.getCoilDiscountFactorPerLevel(), 0.01,
                GTLModularConfig::setCoilDiscountFactorPerLevel);
        addDouble("最低减免系数", GTLModularConfig.getCoilDiscountMinFactor(), 0.5,
                GTLModularConfig::setCoilDiscountMinFactor);
        addDouble("减免时长倍率", GTLModularConfig.getCoilDiscountDurationMultiplier(), 1.0,
                GTLModularConfig::setCoilDiscountDurationMultiplier);

        // ★ 2026-09-21（队长令「做」）：换列断点从「第 1 组之后」挪到「第 2 组之后」。
        //   依据（实测这台机器，不是猜）：实例 options.txt 里 guiScale=3、fullscreen=true，
        //   显示器 1920×1080 ⇒ 界面只有 640×360（页脚在 height-40=320、按钮在 height-26=334）。
        //   旧断点（左列只放第 1 组）下右列已排到 y=298..314，本轮再往右列添「NAC 校准与排障」
        //   一组（标题 14 + 2 行 36 = 50px）⇒ 新行会压到页脚与「保存并关闭」按钮，最后一行还会掉出屏幕，
        //   队长等于还是看不见这两个开关（这正是他要的「界面开关」）。把「线圈减免」留在左列后：
        //   左列最后一行 y=292..308、右列只剩 5 组最后一行 y=226..242，两列都离页脚 12px 以上。
        //   窄窗口（列宽不够）时本调用是无操作 ⇒ 单列模式的顺序仍按下面的插入顺序，一点没变。
        breakColumn(perColumn);

        // ---- 第 3 组：猪咪黑奴挖矿机 ----
        section("猪咪黑奴挖矿机");
        addDouble("能耗倍率", GTLModularConfig.getStripperEnergyMultiplier(), 1.0,
                GTLModularConfig::setStripperEnergyMultiplier);
        addDouble("耗时倍率", GTLModularConfig.getStripperDurationMultiplier(), 1.0,
                GTLModularConfig::setStripperDurationMultiplier);

        // ---- 第 4 组：猪咪样板总成 ----
        section("猪咪样板总成");
        addBool("样板身份闸", GTLModularConfig.isPatternIdentityGateEnabled(), true,
                GTLModularConfig::setPatternIdentityGateEnabled);
        addBool("自动切换配方模式", GTLModularConfig.isAutoRecipeModeSwitchEnabled(), true,
                GTLModularConfig::setAutoRecipeModeSwitchEnabled);

        // ---- 第 5 组：NAC（纳米芯片组装复合体）物流模式 ----
        // ★ 2026-09-19（队长：要简单模式 / 原版模式开关）。
        // ★★ 2026-09-21 两处变更（**沿革写在这里，别抹掉**）：
        //   ① 默认值 false → **true**（队长「让默认就是简单模式」）⇒ addBool 的第 3 个参数
        //      （= 「恢复默认」按钮写回的值）必须同步改成 true；原来那处写死 false，
        //      改了配置默认值而不同步它，就会出现「配置文件默认 true、点恢复默认变 false」的自相矛盾。
        //   ② **产出不再随模式变**（口径变更，2026-09-18 → 2026-09-21 → 同日成阶梯）：
        //      2026-09-18 定的是「原版 16 / 简单 8」——配方 JSON 写 16，简单模式在结算处 ×0.5；
        //      2026-09-21 队长拍板「组装矩阵的 JSON 16 → 8（让 JEI 也显示 8，跟运行期一致）」⇒
        //      产出直接写进配方（30 条 JSON + 两份规格 outCount 全部 16 → 8），运行期
        //      SIMPLE_LOGISTICS_OUTPUT_MULTIPLIER 改成 1.0D（×1 = 空操作）；
        //      ★ 同一天再按队长令改成**阶梯**：最基础的处理器 8 / 装配 4 / 电脑 2 / 主机 1
        //      （`_census/_apply_matrix_output_ladder.js`）
        //      ⇒ **两种模式走同一套阶梯**，本开关现在只影响结构（真空仓下限）与物流玩法。
        section("NAC 物流模式");
        addBool("简单物流模式", GTLModularConfig.isNanochipSimpleLogistics(), true,
                GTLModularConfig::setNanochipSimpleLogistics,
                "简单物流模式 / Simple logistics mode",
                "开（★ 2026-09-21 起为默认，简单）：用「样板总成 + 镜像连接」直接给模块供 CC/PC，不必铺真空仓（真空仓下限放宽为 0）；主机会额外挂上「换算」表 ⇒ 真件 ⇄ CC 双向都能用样板总成在 ME 终端直接下单",
                "ON (default since 2026-09-21, simple): feed the modules with a pattern assembly + its mirror/proxy over AE2 (no vacuum hatch needed; its minimum drops to 0); the host also gains the CONVERSION table so both directions (real <-> CC) are orderable straight from the ME terminal",
                "关（原版 NH）：CC/PC 走真空传送带仓；主机 H 位要求「真空传送带仓 ≥ 1」",
                "OFF (vanilla NH): CC/PC travel through vacuum conveyor hatches; the host requires >=1 vacuum hatch",
                "★ 产出与模式无关：两种模式的组装矩阵走同一套阶梯 —— 最基础的处理器 8 个、装配 4 个、电脑 2 个、主机 1 个电路（2026-09-18 曾一律 16、2026-09-21 改成阶梯；运行期不再缩放，JEI 与实出一致）",
                "* Output does NOT depend on the mode: both modes share one ladder - the most basic processor 8, assembly 4, computer 2, mainframe 1 circuits per craft (a flat 16 back in 2026-09-18, laddered on 2026-09-21; runtime scaling is off, JEI agrees with the machine)",
                "【简单模式怎么用】① 把样板总成插进主机 H 位（或任一模块的 V 位）并接上 ME 网络；② 每台要给料的模块 V 位各插一个镜像仓（gtlcore:me_pattern_buffer_proxy），用闪存右键样板总成存坐标、再右键各镜像仓绑定；③ 在 AE 样板编码台写样板（模块耗材液体也写进样板）放进样板总成，然后在 ME 终端下单，机器自动拉料自动加工；④ 产物自动回到样板总成 / ME 网络，下一个模块下单时自己来拉；⑤ 换算也能下单：主机 H 位挂「猪咪样板总成」，写「1 个 cc_xxx → 1 个真件」的样板（反向同理），ME 终端下单即可批量换算 —— 换算必须用「猪咪样板总成」（它会按样板自动切主机配方模式），gtlcore 原生样板总成不会切模式。",
                "[How to use] 1) put a pattern assembly into the host H slots (or any module's V slots) and hook it to your ME network; 2) put one me_pattern_buffer_proxy (mirror) into each module's V slots and bind it with a data stick; 3) encode patterns (include the module's upkeep fluid) into the pattern assembly and order from the ME terminal; 4) products return straight to the pattern assembly / ME network for the next module to pull; 5) conversions are orderable too: put a piggy pattern assembly into the host H slots and encode '1 cc_xxx -> 1 real item' (or the reverse); the piggy pattern assembly auto-switches the host recipe mode, the stock gtlcore buffer does not.",
                "⚠ 改这一项需要重启游戏：结构谓词在机器注册时只构建一次（产出与模式无关，不再随本项变化）",
                "⚠ Changing this needs a game restart (the structure predicate is built once at registration; output no longer depends on this switch).");

        // ---- 第 6 组：NAC 孤儿封装件（JEI 隐藏）----
        // ★ 2026-09-19（队长：「那 42 个孤儿的话，就先隐藏吧」）。
        // 默认 = 开 = 隐藏；关掉即主动撤销隐藏、恢复可见。只动 JEI 显示层，不动注册/配方。
        section("NAC 孤儿封装件");
        addBool("JEI 隐藏孤儿件", GTLModularConfig.isHideOrphanItemsEnabled(), true,
                GTLModularConfig::setHideOrphanItemsEnabled,
                "JEI 隐藏孤儿件 / Hide orphan items in JEI",
                "开（默认）：_census/残留物品清理_队员.md §8.1 那 42 件（有产出配方、但现存配方没人消费的 CC 封装件）在 JEI 里不显示",
                "ON (default): the 42 orphan circuit components are hidden in JEI",
                "关：主动撤销隐藏，恢复可见（将来想找它们就关掉这一项）",
                "OFF: un-hide them so they show up again",
                "只影响 JEI 显示层：不删注册、不注销 Entry、不动任何配方 ⇒ 仍可用配方/指令/创造模式拿到",
                "Display-layer only: nothing is unregistered and no recipe is changed");

        // ---- 第 7 组：NAC 校准与排障（★ 2026-09-21 队长令「做」新增）----
        //
        // 队长原话（2026-09-21）：「做」—— 他此前的状态是「只能在 config/gtlmodular-common.toml 里手改，
        // 找不到界面开关」，要的就是把下面这两个 nanochipComplex 项搬到游戏内配置界面来：
        //   ① nanochipComplex.calibrationUseFullWindow（校准分母口径，代码里默认 false = 已记录数）
        //   ② nanochipComplex.verboseStructureLogs（结构日志全打，代码里默认 true）
        // ★ 边界纪律（本轮只许改本文件）：两项的**默认值一律没动**（仍是 false / true）——
        //   界面只是让玩家能改；addBool 的第 3 个参数（「恢复默认」写回值）分别取 false / true，
        //   与 GTLModularConfig 里 define(...) 的第二参逐字一致（配置屏与配置文件不许自相矛盾）。
        // ★ 读写机制 = 上面六组同一套，没有另造：**读**走 GTLModularConfig 的公开 getter
        //   （isNanochipCalibrationUseFullWindow / isNanochipVerboseStructureLogs，两者都是现读现取）；
        //   **写**走 GTLModularConfig 的公开 setter（setNanochipCalibrationUseFullWindow /
        //   setNanochipVerboseStructureLogs —— ★ 2026-09-21 队长令「做」第二轮补齐，与 setCoilDiscountEnabled
        //   那批逐字同款：值没变不写 + ConfigValue.set(...)）。
        //   沿革：第一轮这两项在 GTLModularConfig 里只有 getter、而边界只许改本文件 ⇒ 临时用反射够那个
        //   ConfigValue；第二轮边界放宽到 GTLModularConfig 后已把 setter 补上、反射连同 import 一起删掉。
        // ★ 位置依据：这两项都属于 nanochipComplex，与上面两组「NAC 物流模式 / NAC 孤儿封装件」同族，
        //   所以并排放在右列同一个 NAC 段落里，队长一屏就能看到三个 NAC 开关。
        section("NAC 校准与排障");
        addBool("校准分母用原版10万", GTLModularConfig.isNanochipCalibrationUseFullWindow(), false,
                GTLModularConfig::setNanochipCalibrationUseFullWindow,
                "校准分母：用原版 10 万 / Calibration denominator: vanilla 100000",
                "开（= 配置文件 nanochipComplex.calibrationUseFullWindow = true）= 原版旧口径：判定分母恒为写死的 100000（整个历史窗口）",
                "ON (= config nanochipComplex.calibrationUseFullWindow = true) = the old vanilla denominator, hard-coded to 100000 (the whole history window)",
                "      ⇒ 刚跑起来时各电路族占比只有千分之几，族条几乎不动，湿件第一档（20%）要攒够 20000 个对应电路才看得见",
                "      => a freshly started machine shows only a few tenths of a percent, so tier 1 (20%) needs 20,000 matching circuits before anything moves",
                "关（= false，★ 代码里的默认值，队长 2026-09-21 拍板）= 新口径：分母 = 你实际记录过的电路总数 ⇒ 做完一批就看得见；攒满 10 万个之后两种口径完全一致",
                "OFF (= false, the code default since 2026-09-21) = the denominator is the number of circuits actually recorded so far, so a single batch is visible right away; identical to the vanilla branch once 100,000 circuits are recorded",
                "只换分母：窗口容量 100000、批次数 100、每批 1000、9 个电路族与 0.2 / 0.4 / 0.6 阈值都不变",
                "Only the divisor changes: window 100000, 100 batches, the 9 circuit families and the 0.2 / 0.4 / 0.6 thresholds are untouched",
                "★ 改完点「保存并关闭」立即生效（机器每 tick 现读配置，无需重启）；同一次保存会立刻写回 config/gtlmodular-common.toml，不用再手改 toml",
                "★ Saving applies it immediately (the machine re-reads the config every tick, no restart) and the same save also rewrites config/gtlmodular-common.toml, so no manual toml edit is needed");

        addBool("结构日志全打（排障用）", GTLModularConfig.isNanochipVerboseStructureLogs(), true,
                GTLModularConfig::setNanochipVerboseStructureLogs,
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
                "★ 在界面里改完点「保存并关闭」立即生效（闸门每次判定都现读配置，无需重启）；只有手改 config/gtlmodular-common.toml 才需要重启游戏",
                "★ Saving the GUI applies it immediately (the gate re-reads the config on every check, no restart); only editing config/gtlmodular-common.toml by hand requires a game restart");

        // ---- 底部按钮 ----
        int buttonY = this.height - 26;
        addRenderableWidget(Button.builder(Component.literal("保存并关闭"), b -> saveAndClose())
                .bounds(this.width / 2 - 110, buttonY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("恢复默认"), b -> resetDefaults())
                .bounds(this.width / 2 + 10, buttonY, 100, 20).build());
    }

    /**
     * 换列：一次调用把<b>后续各组</b>整体排到右侧第二列（列宽不够时本调用是无操作 ⇒ 保持单列）。
     *
     * <p>★ 2026-09-21：断点从「第 1 组之后」挪到「第 2 组之后」，理由（含实测数字）写在
     * {@code init()} 的调用处。{@code perColumn} 只是目标行数估算，本方法<b>不按它切分</b>；
     * 保留这个参数是为了不动调用点签名。
     */
    private void breakColumn(int perColumn) {
        if (columnIndex == 0 && this.width - MARGIN_X * 2 > (LABEL_W + BOX_W + 24) * 2 - 1) {
            columnIndex = 1;
            columnX = MARGIN_X + (LABEL_W + BOX_W + 24);
            columnRight = columnX + LABEL_W + BOX_W;
            cursorY = MARGIN_TOP;
        }
    }

    private void section(String title) {
        addRenderableWidget(new StringWidget(columnX, cursorY, LABEL_W + BOX_W, 10,
                Component.literal("§e§l" + title), this.font));
        cursorY += SECTION_H;
    }

    private void nextRow() {
        cursorY += ROW_H;
    }

    private void addBool(String label, boolean value, boolean fallback, Consumer<Boolean> save) {
        addBool(label, value, fallback, save, (String[]) null);
    }

    /**
     * 带多行说明（tooltip）的开关。
     *
     * <p>★ 2026-09-19 新增：NAC 物流模式开关的两种取值差别较大（物流路径 + 结构硬要求 + 产出数量），
     * 只靠 132px 宽的中文标签说不清，所以在按钮上挂一段<b>中文 + 英文镜像</b>的说明。
     */
    private void addBool(String label, boolean value, boolean fallback, Consumer<Boolean> save,
                         String... tooltipLines) {
        final BoolRow[] holder = new BoolRow[1];
        Button button = Button.builder(Component.empty(), b -> {
            BoolRow row = holder[0];
            if (row == null) {
                return;
            }
            row.value = !row.value;
            row.refresh();
        }).bounds(columnX + LABEL_W, cursorY, TOGGLE_W, WIDGET_H).build();
        if (tooltipLines != null && tooltipLines.length > 0) {
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal(String.join("\n", tooltipLines))));
        }
        BoolRow row = new BoolRow(button, label, save, value, fallback);
        holder[0] = row;
        addRenderableWidget(button);
        boolRows.add(row);
        addRenderableWidget(new StringWidget(columnX, cursorY + 4, LABEL_W - 4, 10,
                Component.literal(label), this.font));
        nextRow();
    }

    private void addInt(String label, int value, int fallback, IntConsumer save) {
        EditBox box = new EditBox(this.font, columnX + LABEL_W, cursorY, BOX_W, WIDGET_H, Component.literal(label));
        box.setValue(String.valueOf(value));
        box.setFilter(s -> s.matches("-?\\d*"));
        box.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("悬停滚轮可微调（Shift = ±10）")));
        addRenderableWidget(box);
        addRenderableWidget(new StringWidget(columnX, cursorY + 4, LABEL_W - 4, 10,
                Component.literal(label), this.font));
        intRows.add(new IntRow(box, save, fallback));
        nextRow();
    }

    private void addDouble(String label, double value, double fallback, DoubleConsumer save) {
        EditBox box = new EditBox(this.font, columnX + LABEL_W, cursorY, BOX_W, WIDGET_H, Component.literal(label));
        box.setValue(trim(value));
        box.setFilter(s -> s.matches("-?\\d*(\\.\\d*)?"));
        box.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("悬停滚轮可微调（Shift = ±10）")));
        addRenderableWidget(box);
        addRenderableWidget(new StringWidget(columnX, cursorY + 4, LABEL_W - 4, 10,
                Component.literal(label), this.font));
        doubleRows.add(new DoubleRow(box, save, fallback));
        nextRow();
    }

    private void addString(String label, String value, String fallback, Consumer<String> save) {
        EditBox box = new EditBox(this.font, columnX + LABEL_W, cursorY, BOX_W, WIDGET_H, Component.literal(label));
        box.setValue(value);
        box.setMaxLength(128);
        addRenderableWidget(box);
        addRenderableWidget(new StringWidget(columnX, cursorY + 4, LABEL_W - 4, 10,
                Component.literal(label), this.font));
        stringRows.add(new StringRow(box, save, fallback));
        nextRow();
    }

    // ================= 渲染 / 交互 =================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
        // ★ 2026-09-21：页脚原写「即实时生效（机器逻辑实时读取，无需重启）」——对多数项成立，但**不是所有项都成立**：
        //   NAC 物流模式影响的是**注册期构建一次**的结构谓词（真空仓下限），必须重启；
        //   产出则**已经与模式无关**（两种模式走同一套阶梯：处理器 8 / 装配 4 / 电脑 2 / 主机 1，运行期缩放已停用）⇒ 这句话不再与产出挂钩。
        //   这里把例外写进页脚，具体项上仍各自标「⚠ 需要重启」。
        graphics.drawCenteredString(this.font, "§7改完点「保存并关闭」即生效（机器逻辑实时读取；标注「⚠ 需要重启」的项除外）",
                this.width / 2, this.height - 40, 0xAAAAAA);
    }

    /** 悬停滚轮微调：鼠标在输入框上就能调。 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double step = (hasShiftDown() ? 10 : 1) * (delta > 0 ? 1 : -1);
        for (IntRow row : intRows) {
            if (row.box.isMouseOver(mouseX, mouseY)) {
                int v = parseInt(row.box.getValue()).orElse(row.fallback);
                row.box.setValue(String.valueOf((int) (v + step)));
                return true;
            }
        }
        for (DoubleRow row : doubleRows) {
            if (row.box.isMouseOver(mouseX, mouseY)) {
                double v = parseDouble(row.box.getValue()).orElse(row.fallback);
                row.box.setValue(trim(v + step));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void saveAndClose() {
        for (IntRow row : intRows) {
            row.save.accept(parseInt(row.box.getValue()).orElse(row.fallback));
        }
        for (DoubleRow row : doubleRows) {
            row.save.accept(parseDouble(row.box.getValue()).orElse(row.fallback));
        }
        for (StringRow row : stringRows) {
            String text = row.box.getValue();
            row.save.accept(text == null || text.isBlank() ? row.fallback : text.trim());
        }
        for (BoolRow row : boolRows) {
            row.save.accept(row.value);
        }
        // ★ 2026-09-19：改完立刻把「孤儿件隐藏」重新应用一次（开=隐藏 / 关=撤销隐藏）。
        // 走反射：本类是客户端类，但没有 JEI 时也不该因为直接引用插件类而加载失败。
        try {
            Class.forName("com.gtlmodular.client.jei.GTLModularJeiPlugin").getMethod("refresh").invoke(null);
        } catch (Throwable ignored) {
            // 没装 JEI / JEI 运行时还没就绪 ⇒ 本项无副作用。
        }
        GTLModular.LOGGER.info("[gtlmodular] 配置已更新（{} 个数值 / {} 个文本 / {} 个开关），实时生效",
                intRows.size() + doubleRows.size(), stringRows.size(), boolRows.size());
        onClose();
    }

    private void resetDefaults() {
        for (IntRow row : intRows) {
            row.box.setValue(String.valueOf(row.fallback));
        }
        for (DoubleRow row : doubleRows) {
            row.box.setValue(trim(row.fallback));
        }
        for (StringRow row : stringRows) {
            row.box.setValue(row.fallback);
        }
        for (BoolRow row : boolRows) {
            row.value = row.fallback;
            row.refresh();
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    // ★ 2026-09-21：这里原本有一段「临时写回器」（第一轮边界只许改配置屏、而 GTLModularConfig 里这两项
    //   只有 getter ⇒ 当时只好靠反射去够那个 ConfigValue）。第二轮队长把边界放宽到 GTLModularConfig，
    //   两个 setter（setNanochipCalibrationUseFullWindow / setNanochipVerboseStructureLogs）已补齐
    //   ⇒ 那段反射与随之而来的两个 import 一并删掉，两处 save 改成普通方法引用。
    //   注：本文件里剩下的反射只有 saveAndClose() 里调用 JEI 插件那一处（2026-09-19 起就有，不在本轮范围）。

    private static String trim(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private static Optional<Integer> parseInt(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(text.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<Double> parseDouble(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Double.parseDouble(text.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
