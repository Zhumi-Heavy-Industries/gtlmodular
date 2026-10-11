package com.gtlmodular.common.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * 本模组公共配置（COMMON）。
 *
 * <p>M2 双流体税规则（用户规格，无 P 演算）：
 * 熔岩高炉单元每运行 1 秒扣一次税；每次「熔岩」与「液态烈焰」各自全额扣 X mb；
 * X = ivBaselineMbPerSecond（IV 档基准量），每高于 IV 一档电压 ×perTierMultiplier；
 * 低于 IV 不允许运行。
 */
public final class GTLModularConfig {

    private GTLModularConfig() {
    }

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ---- 双流体税 ----
    private static final ForgeConfigSpec.BooleanValue DUAL_FLUID_TAX_ENABLED =
            BUILDER.comment("M2：熔岩高炉单元双流体税总开关")
                    .define("dualFluidTax.enabled", true);

    private static final ForgeConfigSpec.ConfigValue<String> LAVA_FLUID_ID =
            BUILDER.comment("熔岩税流体的注册 id（玩家语境熔岩=原版岩浆）。",
                            "原版岩浆 minecraft:lava；若走 GT 材料熔岩可改 gtceu:lava")
                    .define("dualFluidTax.lavaFluidId", "minecraft:lava");

    private static final ForgeConfigSpec.ConfigValue<String> BLAZE_FLUID_ID =
            BUILDER.comment("液态烈焰税流体的注册 id（烈焰高炉同款燃料，材料 blaze 的流体）")
                    .define("dualFluidTax.blazeFluidId", "gtceu:blaze");

    private static final ForgeConfigSpec.IntValue IV_BASELINE_MB_PER_SECOND =
            BUILDER.comment("IV 档每 1 秒、每种流体的扣量（mb）。",
                            "默认 83 ≈ 10×√(8192/120)（按 IV 档额定功率 8192 EU/t 代入原公式），可自行调整。",
                            "每高于 IV 一档再乘 perTierMultiplier")
                    .defineInRange("dualFluidTax.ivBaselineMbPerSecond", 83, 0, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.DoubleValue PER_TIER_MULTIPLIER =
            BUILDER.comment("高于 IV 后，每提升一档电压的流体扣量倍率")
                    .defineInRange("dualFluidTax.perTierMultiplier", 4.0, 0.0, 1024.0);

    private static final ForgeConfigSpec.IntValue BASELINE_TIER =
            BUILDER.comment("基准电压档（默认 5 = GTValues.IV）；该档倍率为 1")
                    .defineInRange("dualFluidTax.voltageBaselineTier", 5, 0, 30);

    private static final ForgeConfigSpec.BooleanValue ALLOW_BELOW_BASELINE =
            BUILDER.comment("是否允许低于基准电压运行（默认 false：低于 IV 不允许运行）")
                    .define("dualFluidTax.allowBelowBaselineTier", false);

    private static final ForgeConfigSpec.IntValue DRAIN_INTERVAL_TICKS =
            BUILDER.comment("扣税周期（tick）。20 = 每游戏秒扣一次")
                    .defineInRange("dualFluidTax.drainIntervalTicks", 20, 1, 1200);

    // ---- 线圈温度降耗（模块A：并行数量一律由并行控制仓提供；换高级线圈只降 EU/t）----
    private static final ForgeConfigSpec.BooleanValue COIL_DISCOUNT_ENABLED =
            BUILDER.comment("M3：按线圈温度降耗总开关（并行由并行控制仓提供，线圈不再提供并行）")
                    .define("coilDiscount.enabled", true);

    private static final ForgeConfigSpec.IntValue COIL_DISCOUNT_BASE_TEMPERATURE =
            BUILDER.comment("降耗基准温度（K）：等于该温度记第 1 档，倍率 ×1。默认 1800 = 铜镍线圈")
                    .defineInRange("coilDiscount.baseTemperature", 1800, 1, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue COIL_DISCOUNT_TEMPERATURE_PER_LEVEL =
            BUILDER.comment("温度档跨度（K）：每升高该值记 +1 档")
                    .defineInRange("coilDiscount.temperaturePerLevel", 1800, 1, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.DoubleValue COIL_DISCOUNT_FACTOR_PER_LEVEL =
            BUILDER.comment("每高 1 档的 EU/t 系数（倍率 = 该值^(档−1)；0.95 = 每档 −5%）")
                    .defineInRange("coilDiscount.factorPerLevel", 0.95, 0.01, 1.0);

    private static final ForgeConfigSpec.DoubleValue COIL_DISCOUNT_MIN_FACTOR =
            BUILDER.comment("降耗封顶：EU/t 倍率不低于该值（0.5 = 最多省一半电）")
                    .defineInRange("coilDiscount.minFactor", 0.5, 0.01, 1.0);

    private static final ForgeConfigSpec.DoubleValue COIL_DISCOUNT_DURATION_MULTIPLIER =
            BUILDER.comment("模块A 全局耗时倍率（配方耗时 × 该值；0.1 = 耗时缩短到 1/10）")
                    .defineInRange("coilDiscount.durationMultiplier", 0.1, 0.01, 1.0);

    // ---- 猪咪黑奴挖矿机（原星核剥离单元）倍率 ----
    private static final ForgeConfigSpec.DoubleValue STRIPPER_ENERGY_MULTIPLIER =
            BUILDER.comment("猪咪黑奴挖矿机的耗能倍数（总耗能 × 该值；Jade 的『总耗能倍率』读的也是它）",
                            "默认 6 = 600%")
                    .defineInRange("stripper.energyMultiplier", 6.0, 0.0, 1024.0);

    private static final ForgeConfigSpec.DoubleValue STRIPPER_DURATION_MULTIPLIER =
            BUILDER.comment("猪咪黑奴挖矿机的耗时倍数（默认 1 = 100%，不额外改耗时）")
                    .defineInRange("stripper.durationMultiplier", 1.0, 0.0, 1024.0);

    // ---- 猪咪样板总成：样板身份闸 ----

    private static final ForgeConfigSpec.BooleanValue PATTERN_IDENTITY_GATE =
            BUILDER.comment("猪咪样板总成的「样板身份闸」开关（默认开）",
                            "gtlcore 的样板仓是「库存驱动」：只要仓里某个槽位的物料能凑出某配方的输入，",
                            "该配方就可能绑到这个槽上执行 —— 同一配方类型下存在重复输入时就会串",
                            "（例：A 橡木→白桦木、B 橡木+白桦木→钻石，单独一单 A 也可能被拿去跑 B）。",
                            "开：扣料时校验「候选配方是否属于该槽自己的样板」（类型一致 + 输入覆盖 + 主产物命中，跳过编程电路），",
                            "不匹配的槽直接拒绝参与该配方，把匹配从「库存驱动」纠正成「样板槽身份驱动」。",
                            "关：退回原生行为（只剩配方模式锁挡跨模式串）。",
                            "闸门一律 fail-open：样板解析不出来 / 出任何异常都放行，绝不会让机器卡死。")
                    .define("patternAssembly.identityGate", true);

    private static final ForgeConfigSpec.BooleanValue AUTO_RECIPE_MODE_SWITCH =
            BUILDER.comment("猪咪样板总成的「自动切换配方模式」开关（默认开）",
                            "开：当前模式下机器已经跑不动（IDLE/WAITING，即这一模式的料吃完或卡住）时，",
                            "自动扫描仓里其它「有料在槽位等着」的样板槽，把控制器切到那个模式并重置配方逻辑，",
                            "所以一台机器可以同时挂多种模式的样板、轮流自动跑，省一台机器。",
                            "只在机器「没在跑配方」时切（跑动中打断会让已扣的输入蒸发）；",
                            "切过去 40 tick 还没开跑的类型会被临时拉黑 200 tick，防止两个模式之间来回横跳。",
                            "关：只保留「AE 推样板时按槽位锁模式」的行为（不做主动轮询）。")
                    .define("patternAssembly.autoRecipeModeSwitch", true);

    private static final ForgeConfigSpec.BooleanValue PATTERN_DEBUG_LOGS =
            BUILDER.comment("猪咪样板总成的「调试日志」开关（默认关）",
                            "开：每次 AE 推送样板都打印明细（推了什么、各多少 mB/个）、",
                            "推送后内部槽条目数、以及「需要量 vs 网络可用量」的对比 —— 排查",
                            "「材料明明够却卡住 / 存量不足 / 死锁」时很有用，但会持续刷日志。",
                            "关：这三类啰嗦日志全部不打印（默认）。",
                            "注意：下面两条「只在出问题时才触发」的日志不受这个开关影响，始终保留 ——",
                            "  ①『XX 被合成 CPU 占用』死锁提示；",
                            "  ②『身份闸拒绝配方』误挡提示。")
                    .define("patternAssembly.debugPatternLogs", false);

    // ---- 纳米芯片组装复合体（NAC，GTNH 移植） ----

    private static final ForgeConfigSpec.BooleanValue NANOCHIP_ENABLED =
            BUILDER.comment("纳米芯片组装复合体总开关（默认开）")
                    .define("nanochipComplex.enabled", true);

    private static final ForgeConfigSpec.IntValue NANOCHIP_DIRECT_PARALLEL_CAP =
            BUILDER.comment("NAC「直通档」的并行上限（默认 1024）",
                            "直通档 = 不依赖组装矩阵模块、直接用样板总成/AE 推样板出芯片的那条路。",
                            "模块里的『纳米封装包装器』每台 ×4 并行、『纳米组件分流器』每台 ×2 并行（每种最多计 2 台）。",
                            "想要原版上限（无限并行）就得把『纳米芯片组装矩阵』模块立在复合体旁边，",
                            "届时并行不再受这个值限制，且配方时长下限从 20 秒降到 5 秒。")
                    .defineInRange("nanochipComplex.directParallelCap", 1024, 1, Integer.MAX_VALUE);

    /**
     * NAC「直通档」的<b>跨配方线程</b>基线（默认 1；★ 2026-10-02 L3 真实多线程落地时新增）。
     *
     * <p>直通档的语义是「样板总成直通、上限明显更低」，所以线程基线默认取 <b>1</b>：
     * 即「一次只挂起一条配方」，与改动前「一条合并配方」的玩家观感一致（只是合并预算的来源变了）。
     * 全流程档不吃这个值 —— 它按电压等级给线程（组装矩阵 = 解锁线程），见
     * {@code NanochipAssemblyComplexMachine.getMultipleThreads()}。
     *
     * <p>取上界 {@code 16}：直通档并行本身有 {@code directParallelCap}（默认 1024）封顶，
     * 再乘线程就是总预算；上界 16 保证 {@code getMaxParallel() × getMultipleThreads()}
     * 永远远小于 {@code Integer.MAX_VALUE / 64}（父类那套预算算术不会溢出）。
     */
    private static final ForgeConfigSpec.IntValue NANOCHIP_DIRECT_THREADS =
            BUILDER.comment("NAC「直通档」的跨配方线程基线（默认 1）",
                            "跨配方线程 = 同一台复合体在同一时刻挂起的【不同配方】条数（每条各自独立进度与完成时刻）。",
                            "直通档默认 1 条；全流程档（组装矩阵模块在线）不吃这个值，改为按电压等级给线程。")
                    .defineInRange("nanochipComplex.directCrossRecipeThreads", 1, 1, 16);

    /**
     * 纳米校准的「判定分母」模式（★ <b>2026-09-21 起默认 false = 已记录数</b>）。
     *
     * <p>★★ <b>2026-09-21 队长令「改」</b>★★：队长先报现象「<b>那个校准我在游戏里面制作了，
     * 也没见他有变化呀，我制作湿件，也不见湿件那个条有变动</b>」，诊断结论是
     * <b>分母口径</b>而不是记账断链（记账链路已逐条验证：主机与组装矩阵模块都进同一个记账口，
     * 产出件族判定也对；同步是每 tick 值差分推送、面板每帧重取值）——
     * 分母固定 100000 时，一次组装矩阵产出（8 个 × 并行）在 50px 的族条上只有
     * <b>0.004 像素</b>、标签恒为 {@code 0.0％}；要 <b>50 个</b>才第一次跳到 0.1％、
     * <b>2000 个</b>才有 1 像素、湿件第一档（20%）要 <b>20000 个</b> ⇒ 「条不动」是分母压出来的。
     * 队长据此拍板「<b>改</b>」⇒ 默认切成 {@code false}。
     *
     * <p>原版出处（GTNH 2.9.0-beta-2 的 {@code gregtech-5.09.54.20.jar}，仍然保留作为可回退口径）：
     * {@code gregtech.common.tileentities.machines.multi.nanochip.util.CircuitCalibration.getCurrentCalibration}
     * 里把分母<b>硬编码</b>成 {@code 100000.0d}（三处 {@code ldc2_w #88 // double 100000.0d} + {@code ddiv}），
     * 即 {@code ratio = 该族数量 / 100000.0} 与阈值比大小；配合「历史窗口 = 100 批 × 每批 1000 = 100,000 个电路」
     * （{@code CircuitBatch.isFull()} 的 {@code sipush 1000} + {@code addToHistory} 的 {@code bipush 100}），
     * 也就是「阈值占比 × 整个历史窗口」——第一档 20% 需要攒够 <b>20,000 个</b>对应电路。
     * 原版 lang 原文互证：{@code The specific amount is the calibration tiers threshold * the total circuit history (100,000).}
     *
     * <p>两种取值的手感差别（只差分母，其余规格一律相同）：
     * <ul>
     *   <li><b>false（现在的默认）</b>：分母 = 当前已记录数 → 窗口没攒满时分母很小，很快就能达标，
     *       窗口攒满（10 万个）后与 true <b>完全一致</b>；</li>
     *   <li><b>true（原版口径，可随时切回）</b>：分母固定 100000 → 前期升档很慢，机器刚跑起来时
     *       占比只有千分之几（队长 2026-09-21 报的"条不动"就是这个口径下的正常现象）。</li>
     * </ul>
     * 两种取值都<b>不改</b>：窗口容量 100000、批次数 100、每批 1000、电路族集合与显示段数、
     * 阈值（四族 0.2/0.4/0.6、特殊曲线族 0.01/0.05/0.2）、三档累积关系。
     *
     * <p>★ 2026-09-21 队长令「做」：本项已挂进<b>游戏内配置界面</b>
     * （Mods → GTL更多实用设备 → 配置 → 第 7 组「NAC 校准与排障 → 校准分母用原版10万」）。
     * 点「保存并关闭」<b>当场生效、不用重启</b>（{@code NanochipCalibration.useFullWindowDenominator()}
     * 每次算占比都现读本项），同一次保存还会<b>立刻写回</b> {@code config/gtlmodular-common.toml}
     * —— Forge 的 TOML 配置器建文件时链了 nightconfig {@code autosave()}（javap 取证于
     * {@code ConfigFileTypeHandler}）⇒ <b>不用再手改 toml</b>；反过来说，<b>手改 toml 才需要重启游戏</b>。
     */
    private static final ForgeConfigSpec.BooleanValue NANOCHIP_CALIBRATION_USE_FULL_WINDOW =
            BUILDER.comment("NAC 纳米校准「判定分母」模式（2026-09-21 队长令「改」⇒ 默认 false = 已记录数）",
                            "false（默认）：分母用「已记录数」⇒ 窗口没攒满时族条涨得看得见，攒满 10 万后与 true 完全一致",
                            "true ：分母固定为原版硬编码的 100000（= 整个历史窗口），第一档 20％ 要攒够 20000 个对应电路",
                            "只改分母；窗口容量/批次数/电路族/阈值与三档累积关系都不变")
                    .define("nanochipComplex.calibrationUseFullWindow", false);

    /**
     * 真空传送的<b>两种路由模式</b>（2026-09-16 新增；用户定案"两种模式都要能跑"）。
     *
     * <table border="1">
     *   <tr><th>取值</th><th>行为</th><th>对应</th></tr>
     *   <tr><td>{@code false}（<b>默认</b>，保住现有存档）</td>
     *       <td>颜色即线、同色全连、<b>不用铺管道</b></td>
     *       <td>本移植版一直以来的"简单版"</td></tr>
     *   <tr><td>{@code true}</td>
     *       <td>必须有<b>同色管道通路</b>才传输，同色才连（1 对 1）</td>
     *       <td>原版 GTNH（{@code VacuumFactoryNetwork} 的判定）</td></tr>
     * </table>
     *
     * <p>两条路<b>共用同一份通道内容存储</b>（{@link com.gtlmodular.common.machine.nanochip.NanochipVacuumNetwork}
     * 只管"通道里有什么"），只有"谁连谁"的判定不同：
     * 简单模式下每个端口的分量号恒为 {@code 0}；管道模式下分量号由
     * {@link com.gtlmodular.common.machine.nanochip.NanochipPipeGraph} 现算。
     * ⇒ 切换配置<b>不动存档数据</b>（切回简单模式时会把非 0 分量并回 0 池，见
     * {@code NanochipVacuumNetwork.flattenAll()}）。
     *
     * <p>⚠️ <b>本定义必须放在 {@link #SPEC} 之前</b>：{@code SPEC = BUILDER.build()} 之后定义的项
     * <b>不会进配置文件</b>（取值永远等于默认值）。历史上 {@code impurityPerRecipe} 与
     * {@code debugLogs} 就踩过这个坑（2026-09-16 一并搬到前面，行为变化见施工记录）。
     */
    private static final ForgeConfigSpec.BooleanValue NANOCHIP_VACUUM_REQUIRES_PIPES =
            BUILDER.comment("NAC 真空传送路由模式（默认 false = 颜色即线、不用铺管道）",
                            "false：颜色相同即对接，不用铺任何管道（本移植版原有行为，现有存档不受影响）",
                            "true ：照原版 —— 两个端口之间必须有【同色管道通路】才传输，不铺管道就不工作",
                            "两种模式共用同一份通道内容；改这一项需要重启游戏（配置只在启动时读）",
                            "切换回 false 时，管道模式下攒在分量里的虚拟件会自动并回主通道，不会丢件")
                    .define("nanochipComplex.vacuumRequiresPipes", false);

    private static final ForgeConfigSpec.DoubleValue NANOCHIP_IMPURITY_PER_RECIPE =
            BUILDER.comment("NAC「全板浸没装置」每完成一次配方上涨的浸泡液杂质百分点（默认 0.5）",
                            "原版由流体量 / 100 万 L 槽容决定上涨速度，这里折算成固定步进（0 = 关闭杂质机制）。",
                            "杂质三段式的效果照抄原版：",
                            "  0–15%  ：EU ×(1 − (30 − 2y)/100)，y=0 时省 30%",
                            "  15–65% ：EU ×1.00（默认行为）",
                            "  65–100%：EU ×(1 + (y − 65)×2%)，跑脏了反而更费电",
                            "到「排液阈值」（默认 100%，机器界面显示）就自动排液并把杂质清零。")
                    .defineInRange("nanochipComplex.impurityPerRecipe", 0.5D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.BooleanValue NANOCHIP_DEBUG_LOGS =
            BUILDER.comment("NAC 调试日志（默认关）：排液、真空搬运异常等细节日志")
                    .define("nanochipComplex.debugLogs", false);

    /**
     * ★★ 2026-09-21（队长排障专用，<b>默认开</b>）★★
     *
     * <h4>队长原话（2026-09-21）</h4>
     * <blockquote>「我更新了add，还有core现在我们的模块都不做东西了」＋（对我给的三个选项回了）「查」</blockquote>
     *
     * <h4>这个开关治的是什么</h4>
     * 0.1.40 给 NAC 结构日志加了降噪闸（{@link com.gtlmodular.common.machine.nanochip.NanochipStructureLogGate}）：
     * 「同一台机器 + 同一结论 + 同一原因」<b>10 秒内只许一条 INFO</b>，窗口内的重复<b>降到
     * {@code debug}</b>，跨机器的那批还合并成一条「NAC 结构结论未变汇总」。
     * 代价是：<b>「尚未成形」的逐格清单（期望什么方块 / 实际是什么 / GTCEu 记录在哪一格）在稳态下
     * 只剩每 10 秒一条汇总，明细要开 debug 才看得到。</b>
     * <p>而队长这台机器的 {@code logs/debug.log} 是 <b>09/15 的旧文件</b>（本会话根本没写，
     * 见该目录 {@code debug.log} 的 {@code LastWriteTime=2026/9/15 01:14}）⇒
     * 这次故障<b>逐格原因彻底看不见，只能猜</b>。这是本开关存在的<b>唯一理由</b>。
     *
     * <h4>取值的两个方向</h4>
     * <table border="1">
     *   <tr><th>取值</th><th>行为</th></tr>
     *   <tr><td><b>{@code true}（默认）</b></td>
     *       <td><b>结构结论 / 逐格原因一律 INFO、不折叠</b>：
     *           {@code NanochipStructureLogGate.shouldEmitInfo} 恒返回 {@code true} ⇒
     *           ①「尚未成形 / 缺件清单」每条逐格明细照打；
     *           ② 不再产生「NAC 结构结论未变汇总」那条合并行（没有东西被攒下）；
     *           ⇒ 代价是回到队长之前抱怨过的「一进游戏 NAC 一直刷、要好久」那种日志量。</td></tr>
     *   <tr><td>{@code false}</td>
     *       <td>恢复 0.1.40 的降噪口径（10 秒一条 + 跨机器折叠汇总），重复降 debug。
     *           <b>排障结束后请改回 {@code false}</b>。</td></tr>
     * </table>
     *
     * <h4>本开关的纪律（为什么这样设计才安全）</h4>
     * <ul>
     *   <li><b>只改"打不打 / 打多细"，一个判定语义都不动</b>：它唯一的落点是
     *       {@code NanochipStructureLogGate.shouldEmitInfo} 的<b>返回值</b>，
     *       而返回值只被调用方用来选 {@code LOGGER.info} 还是 {@code LOGGER.debug}
     *       ⇒ 「什么时候算成形」的结果、任何扣料/结构判定都<b>逐字节不变</b>；</li>
     *   <li><b>一行可关回</b>：<b>在游戏内配置界面</b>「NAC 校准与排障 → 结构日志全打（排障用）」
     *       里点一下改成 {@code false}、再点「保存并关闭」<b>当场生效、不用重启</b>
     *       （{@code NanochipStructureLogGate.shouldEmitInfo} 每次结构判定都现读本项）；
     *       同一次保存还会<b>立刻写回</b> {@code config/gtlmodular-common.toml}
     *       —— Forge 的 TOML 配置器建文件时链了 nightconfig {@code autosave()}（javap 取证于
     *       {@code ConfigFileTypeHandler}）⇒ 不用再手改 toml；<b>只有手改 toml 才需要重启游戏</b>
     *       （Forge 只在启动时读文件）。不碰任何其它代码；</li>
     *   <li><b>异常回落 = {@code true}（照打）</b>：配置还没加载（构造期 / 测试环境）时按"能看见"那一侧走
     *       —— 排障期宁可多打，也不要因为配置读不到又把原因藏起来。</li>
     * </ul>
     *
     * <p><b>英文镜像 / English mirror</b>：emergency verbosity switch for NAC structure logs
     * (<b>default true</b>). While {@code true}, every structure conclusion and every per-cell reason is
     * logged at INFO and never folded into the 10-second summary; set it to {@code false} to go back to
     * the quiet 0.1.40 behaviour. It only changes log <b>level</b>, never any decision.
     *
     * <p>⚠️ <b>本定义必须放在 {@link #SPEC} 之前</b>：{@code SPEC = BUILDER.build()} 之后定义的项
     * <b>不会进配置文件</b>（取值永远等于默认值）。
     */
    private static final ForgeConfigSpec.BooleanValue NANOCHIP_VERBOSE_STRUCTURE_LOGS =
            BUILDER.comment("NAC 结构日志「全打」开关（★ 排障用，默认 true = 一律 INFO、不折叠）",
                            "NAC structure-log verbosity (troubleshooting, default true = always INFO)",
                            "★ 2026-09-21 队长排障专用：队长原话「我更新了add，还有core现在我们的模块都不做东西了」",
                            "★ 2026-09-21 captain's troubleshooting switch",
                            "true （默认）：结构结论 / 逐格原因（期望方块 vs 实际方块 / GTCEu 记录位置）一律 INFO、不折叠、不降 debug；",
                            "true  (default): every structure conclusion and per-cell reason goes to INFO, never folded, never demoted to debug;",
                            "       代价 = 回到「一进游戏 NAC 一直刷日志」那种量级；",
                            "       cost = back to the noisy level the captain complained about;",
                            "false：恢复 0.1.40 降噪口径（同机同结论 10 秒一条 + 跨机器折叠成一条汇总，重复降 debug）；",
                            "false: restore the 0.1.40 quiet behaviour (one line per clock + one cross-machine summary, repeats at debug);",
                            "       排障结束后请改回 false。改这一项【不用重启】：配置界面「NAC 校准与排障」里点一下「保存并关闭」当场生效、并立刻写回 toml；只有手改 config/gtlmodular-common.toml 才需要重启游戏。",
                            "       set it back to false when the investigation is over. No restart needed: toggle it in the in-game config screen and save - it applies at once and is written straight back to the toml; only editing the toml by hand requires a game restart.",
                            "★ 只改日志级别，不改任何判定：落点仅是 NanochipStructureLogGate.shouldEmitInfo 的返回值",
                            "★ Log level only, no decision changes: the only hook is the return value of NanochipStructureLogGate.shouldEmitInfo")
                    .define("nanochipComplex.verboseStructureLogs", true);

    /**
     * ★★ 2026-09-19（队长原话）★★
     * <blockquote>
     * 「现在要对简单模式跟 NH 原版的模式做区分，要在游戏模组配置里面设置开关，我现在想简单模式，
     * 能上样板总成，通过样板总成，然后再连接镜像连接到模块上，这样来传输 CC 跟 PC 这种的物品，
     * 就没必要上真空仓，但是产出要降低到 1 出 8」
     * </blockquote>
     *
     * <p><b>NAC 物流模式开关</b>（两种取值）：
     * <table border="1">
     *   <tr><th>取值</th><th>行为</th></tr>
     *   <tr><td>{@code false}（原版(NH)）</td>
     *       <td>CC/PC 走<b>真空传送带仓</b>那条物流链；主机 {@code H} 位硬要求「真空传送带仓 ≥ 1」。
     *           <br>★ 产出：与下面那一支<b>走同一套阶梯</b>（见本注释末的「沿革」）。</td></tr>
     *   <tr><td>{@code true}（简单，<b>默认</b>）</td>
     *       <td>可以用「<b>样板总成 + 镜像连接</b>」（AE2 样板总线 / 样板总成镜像仓）直接给模块供
     *           CC/PC，<b>不必铺真空传送仓</b>（主机 {@code H} 位的真空仓下限放宽为 <b>0</b>）；
     *           ★ 且主机额外挂上<b>换算表</b>（{@link #NANOCHIP_SIMPLE_LOGISTICS 见下}）⇒
     *           「真件 ⇄ CC」两向都能用样板总成在 ME 终端<b>直接下单</b>，
     *           不必再靠真空输出仓/输入仓手工搬运。
     *           <br>★ 产出：<b>同一套阶梯</b>（两种模式一致）—— 最基础的处理器 8 个 / 装配 4 个 / 电脑 2 个 / 主机 1 个电路。</td></tr>
     * </table>
     *
     * <p><b>⚠️ 改这一项需要重启游戏</b>：结构谓词（真空仓下限）在<b>机器注册时</b>构建一次
     * （{@code NanochipComplexPattern.build()} 只跑一次），配置只在启动时读。
     * ★ 2026-09-21 起本项<b>不再影响产量</b>（两种模式走同一套阶梯：处理器 8 / 装配 4 / 电脑 2 / 主机 1），
     * 它现在只决定<b>结构要求与物流玩法</b>。
     *
     * <p><b>★ 沿革（别抹掉，2026-09-21 队长拍板）</b>：
     * <ol>
     *   <li><b>2026-09-18</b>：产出定为「原版 16 / 简单 8」—— 30 条 {@code assembly_matrix} 配方 JSON 里
     *       写 <b>16</b>，简单模式在结算处用 {@code NanochipModuleMachine.SIMPLE_LOGISTICS_OUTPUT_MULTIPLIER = 0.5D}
     *       现缩成 8（当时的意图：配方数据不动，产出随模式实时变、无需重启）；</li>
     *   <li><b>2026-09-21</b>：队长拍板「组装矩阵的 JSON 16 → 8（让 JEI 也显示 8，跟运行期一致）」⇒
     *       产出<b>直接写进配方</b>（30 条 JSON + 两份规格的 {@code outCount} 全部 16 → 8），
     *       运行期那层 ×0.5 <b>停用</b>（{@code SIMPLE_LOGISTICS_OUTPUT_MULTIPLIER} 改成 {@code 1.0D}，
     *       函数里的 {@code >= 1.0D} 短路使它成为空操作）
     *       ⇒ JEI 与运行期显示同一数字；</li>
     *   <li><b>同一天 · 再改（本轮）</b>：队长「修改电路产出的数量，让每个系列最基础的产出八，4，2，1
     *       最后每个系列的主机产出一份」⇒ 由<b>一律 8</b> 改成<b>按档位阶梯</b>：
     *       最基础的<b>处理器 8</b> / <b>装配 4</b> / <b>电脑 2</b> / <b>主机 1</b>
     *       （7 个系列同构：晶体 / 湿件 / 生物活性 / 光学 / 奇异 / 寰宇 / 超因果；
     *       脚本 {@code _census/_apply_matrix_output_ladder.js}，规格里的行级 {@code outCount} 同步写成阶梯）；
     *       运行期仍<b>不缩放</b>（{@code SIMPLE_LOGISTICS_OUTPUT_MULTIPLIER = 1.0D}）⇒ JEI = 机器实出；</li>
     *   <li><b>同一天</b>：本项默认值 {@code false → true}（队长「让默认就是简单模式」）；
     *       ⚠️ 但 getter 的<b>异常回落</b>仍留在 {@code false}（原版那一侧）—— 配置读不到时按"不改变行为"处理，
     *       与"配置文件里的默认值是 true"并不矛盾。</li>
     * </ol>
     *
     * <p><b>★ 简单模式怎么用（2026-09-19 打通轮）</b>（同一个口径也写在
     * {@code lang} 的 {@code gtlmodular.nanochip.complex.mode} 与模块
     * {@code gtlmodular.nanochip.module.simple_logistics} 上，以及游戏内配置界面）：
     * <ol>
     *   <li>把<b>样板总成</b>插进主机 {@code H} 位或任一模块的 {@code V} 位，接上 ME 网络
     *       （本模组的「猪咪样板总成」也行）；</li>
     *   <li>每台要给料的模块 {@code V} 位各插一个<b>镜像仓</b>（{@code gtlcore:me_pattern_buffer_proxy}），
     *       手持<b>闪存</b>右键样板总成存坐标、再右键各镜像仓完成绑定；</li>
     *   <li>在 AE 样板编码台把「要做什么」写成样板（<b>模块耗材液体也写进样板</b>）放进样板总成，
     *       然后在 ME 终端<b>下单</b> —— 机器自动拉料、自动加工；</li>
     *   <li>产物<b>自动回到样板总成 / ME 网络</b>（不掉地上、不需要输出仓），
     *       下一个模块下单时自己把要的材料从网络里拉走。</li>
     *   <li>★ <b>换算也能下单</b>（2026-09-19 方案 A）：主机 H 位挂<b>猪咪样板总成</b>，写
     *       「1 个 {@code cc_xxx} → 1 个真件」的样板（反向同理），ME 终端下单即可批量换算
     *       —— 对应配方在 {@code data/gtlmodular/recipes/nanochip/conversion{,_reverse}/}。
     *       ⚠ 换算这类<b>多配方类型</b>用法必须用本模组的「猪咪样板总成」（它按样板逐槽识别并
     *       自动切主机配方模式）；gtlcore 原生 {@code me_pattern_buffer} 没有切模式能力。</li>
     * </ol>
     *
     * <p><b>英文镜像 / English mirror</b>：
     * {@code false} (vanilla NH) = CC/PC travel through vacuum conveyor hatches; the host requires at
     * least one vacuum conveyor hatch on its {@code H} slots. {@code true} (simple, <b>default</b>) = feed
     * the modules with a ME pattern assembly plus its mirror/proxy part instead of vacuum hatches (the
     * vacuum hatch minimum drops to 0).
     * <b>Output is NOT mode-dependent</b>: BOTH modes follow one ladder - processor 8 / assembly 4 /
     * computer 2 / mainframe 1 circuits per craft (vanilla used to be 16, then a flat 8; since 2026-09-21
     * the counts live in the 30 assembly-matrix recipe JSONs and the runtime x0.5 scaling was switched off,
     * so JEI and the machine agree). Changing this needs a game restart
     * (the structure predicate is built once at registration).
     *
     * <p>⚠️ <b>本定义必须放在 {@link #SPEC} 之前</b>：{@code SPEC = BUILDER.build()} 之后定义的项
     * <b>不会进配置文件</b>（取值永远等于默认值）。
     */
    private static final ForgeConfigSpec.BooleanValue NANOCHIP_SIMPLE_LOGISTICS =
            BUILDER.comment("NAC 物流模式开关（★ 2026-09-21 起默认 true = 简单模式）",
                            "NAC logistics mode switch (default true = simple mode since 2026-09-21)",
                            "false = 原版(NH)：CC/PC 走真空传送带仓；主机 H 位要求「真空传送带仓 ≥ 1」；组装矩阵走同一套阶梯 —— 处理器 8 / 装配 4 / 电脑 2 / 主机 1（★ 两种模式一致；2026-09-18 曾一律 16、2026-09-21 先改一律 8 再改阶梯）",
                            "false = vanilla (NH): CC/PC move through vacuum conveyor hatches; host H slots require >=1 vacuum hatch; the Assembly Matrix follows one ladder - processor 8 / assembly 4 / computer 2 / mainframe 1 (both modes alike; a flat 16 in 2026-09-18, then a flat 8 and now the ladder on 2026-09-21)",
                            "true  = 简单：用「样板总成 + 镜像连接」直接给模块供 CC/PC，不必铺真空仓（真空仓下限放宽为 0）；组装矩阵走同一套阶梯 —— 处理器 8 / 装配 4 / 电脑 2 / 主机 1（与关掉时一致）",
                            "true  = simple: feed the modules with a pattern assembly + its mirror/proxy over AE2 (no vacuum hatch needed; its minimum becomes 0); the Assembly Matrix follows the same ladder - processor 8 / assembly 4 / computer 2 / mainframe 1 (same as the OFF branch)",
                            "true  = 简单：★ 主机额外挂上「换算」配方表 ⇒ 「真件 ⇄ CC」两向都能用样板总成在 ME 终端直接下单（换算必须用「猪咪样板总成」；gtlcore 原生样板总成不会切主机配方模式）",
                            "true  = simple: ★ the host additionally gains the CONVERSION table, so BOTH directions (real <-> CC) are orderable straight from the ME terminal (use the piggy pattern assembly; the stock gtlcore buffer cannot switch the host recipe mode)",
                            "【简单模式怎么用】① 把样板总成插进主机 H 位或任一模块的 V 位并接上 ME 网络（本模组「猪咪样板总成」也行）；",
                            "【简单模式怎么用】② 每台要给料的模块 V 位各插一个镜像仓（gtlcore:me_pattern_buffer_proxy），用闪存右键样板总成存坐标、再右键各镜像仓绑定；",
                            "【简单模式怎么用】③ 在 AE 样板编码台把「要做什么」写成样板（模块耗材液体也写进样板）放进样板总成，然后在 ME 终端下单，机器自动拉料自动加工；",
                            "【简单模式怎么用】④ 产物自动回到样板总成 / ME 网络（不掉地上、不需要输出仓），下一个模块下单时自己来拉。",
                            "【简单模式怎么用】⑤ 换算也能下单：主机 H 位挂「猪咪样板总成」，写「1 个 cc_xxx → 1 个真件」的样板（反向同理），ME 终端下单即可批量换算。",
                            "[How to use] 1) put a pattern assembly into the host H slots (or any module's V slots) and hook it to your ME network; 2) put one me_pattern_buffer_proxy (mirror) into each module's V slots and bind it with a data stick; 3) encode patterns (include the module's upkeep fluid) into the pattern assembly and order from the ME terminal; 4) products return straight to the pattern assembly / ME network for the next module to pull; 5) conversions are orderable too: order '1 cc_xxx -> 1 real item' (or the reverse) from the ME terminal with a piggy pattern assembly in the host H slots.",
                            "改这一项需要重启游戏：结构谓词在机器注册时构建一次，配置只在启动时读",
                            "Requires a game restart: the structure predicate is built once at machine registration.",
                            "★ 产出与本项无关（两种模式走同一套阶梯：处理器 8 / 装配 4 / 电脑 2 / 主机 1）：2026-09-18 曾定「原版 16 / 简单 8（结算处 ×0.5）」；2026-09-21 队长先拍板一律 8、再拍板改成阶梯，运行期缩放停用。",
                            "* Output does not depend on this switch: both modes follow one ladder - processor 8 / assembly 4 / computer 2 / mainframe 1 (2026-09-18 had vanilla 16 / simple 8 via a x0.5 runtime scaling; on 2026-09-21 the counts were baked into the recipes - first a flat 8, then the ladder - and the runtime scaling was switched off).")
                    .define("nanochipComplex.simpleLogisticsMode", true);   // ★ 2026-09-21 队长：「让默认就是简单模式」

    /**
     * ★★ 2026-11-13【NAC 电网取电】★★
     *
     * <p>用户原话：「让 NAC 的成型里面去掉能源仓、激光仓这些的，<b>直接从电网中取电</b>」。
     * 本开关（默认 {@code true}）打开时，NAC 在<b>没有能源仓</b>时也能跑：
     * 它把自己的取电口接到<b>放置者（或放置者所属 FTB 队伍）的 GTMThings 无线电网</b>上
     * （{@code com.hepdd.gtmthings.api.misc.WirelessEnergyManager.addEUToGlobalEnergyMap}，
     * 该类就在本工程编译类路径 {@code gtl-modular/libs/} 的 gtmthings-1.3.5.fix 里，
     * 本整合包先例＝GTL-Enhancedcore 的无线充能器）。
     *
     * <p>取电顺序（{@code EnergyContainerList} 是"加够就停"的循环 ⇒ 次序即优先级）：
     * <b>电网在最前</b> ⇒ 「直接从电网取电」；电网余额不够时由<b>能源仓兜底</b>，
     * 两者都没有 ⇒ 返回 0 ＝ 一点不扣、机器原地等（不退进度、不掉料）。
     * 反过来排列（仓前电网后）会让"空能源仓"把顺序吃掉 —— 详见实施记录里的风险 A。
     *
     * <p>★ <b>唯一一处存档格式变更与它的兜底</b>：放置者 UUID 存在
     * {@code NanochipAssemblyComplexMachine.gridPowerUser}（{@code @Persisted UUID}）。
     * <b>旧存档没有这个键 ⇒ 读档后为 null ⇒ 本功能对该机器自动退回"老行为"</b>
     * （＝必须有能源仓，逐字等于本改动之前），并在日志里<b>明确报一句</b>（不静默：
     * 载入时报 WARN、取电被拒时按 10 秒限流再报一次），同时给出恢复办法（拆放一次控制器）。
     *
     * <p>{@code false} ＝ <b>逐字回到本改动之前</b>（{@code getEnergyContainer()} 里先判本开关、
     * 判否直接返回基类结果，一行分支回退；不改任何原有分支、不删任何原有逻辑）。
     *
     * <p>本开关<b>独立于</b> {@code nanochipComplex.originalMode}（原初模式管的是结构/连接拦截层，
     * 见 {@code NanochipAssemblyComplexMachine} 里 {@code distributeModuleEnergy} 那一处的同款纪律）
     * 与 {@code nanochipComplex.simpleLogisticsMode}（后者管真空仓物流）。
     *
     * <p>⚠️ 取电层是<b>运行期</b>读配置（改完即生效，不必重启）；
     * 结构层那条下限（能源仓 {@code min 0}）在机器注册时构建一次 ⇒ 那一项本开关管不到。
     */
    public static final ForgeConfigSpec.BooleanValue NANOCHIP_GRID_POWER =
            BUILDER.comment("NAC 电网取电（默认 true）：从绑定账户（或其 FTB 队伍）的 GTMThings 无线电网取电",
                            "true ：电网优先、能源仓兜底（电网余额不够时用仓；两者都没有 ⇒ 原地等电，不退进度、不掉料）",
                            "false：必须有能源仓，\"直接从电网取电\"整条关闭",
                            "电网账户：玩家放置控制器时自动绑定到放置者；没有绑定时空手潜行右键控制器绑定到自己。",
                            "本项独立于 nanochipComplex.originalMode 与 nanochipComplex.simpleLogisticsMode。",
                            "取电层是运行期读配置（改完即生效）；结构层「能源仓 min 0」在机器注册时构建一次，不受本项影响。")
                    .define("nanochipComplex.gridPower.enabled", true);

    public static final ForgeConfigSpec.IntValue NANOCHIP_GRID_POWER_TIER =
            BUILDER.comment("NAC 电网取电时等效的能源仓电压等级（默认 14 = MAX）。决定主机与模块能超频到哪一级。")
                    .defineInRange("nanochipComplex.gridPower.tier", 14, 1, 14);

    public static final ForgeConfigSpec.IntValue NANOCHIP_GRID_POWER_AMPERAGE =
            BUILDER.comment("NAC 电网取电时等效的电流（安培，默认 65536）。电网功率 = 该电压等级 × 安培数，",
                            "模块的无限并行按这个功率封顶。上限 262144：再大的话，长配方整条的电量会超出可记录的范围。",
                            "结构成形或电网账户绑定变化时生效。")
                    .defineInRange("nanochipComplex.gridPower.amperage", 65536, 1, 262144);

    /**
     * ★★ 2026-09-19（队长原话）★★
     * <blockquote>「那 42 个孤儿的话，就先隐藏吧」</blockquote>
     *
     * <p><b>NAC 孤儿封装件在 JEI 里隐藏</b>（默认 {@code true} = 隐藏）。
     *
     * <p>「42 个孤儿」= {@code _census/残留物品清理_队员.md} §8.1 那批
     * {@code gtlmodular:cc_processed_*} / {@code cc_casing_*} / {@code cc_chip_*} —— 定义是
     * 「<b>有产出配方（玩家拿得到）、但现存配方里没有任何一条消费它</b>」（成因：2026-09-18
     * 「特殊系列」7 件退役后，它们在 NH 侧的唯一消费者那 4 条矩阵配方没了）。
     *
     * <p><b>只隐藏、不删注册、不动配方</b>（存档安全第一）：本开关只驱动
     * {@code com.gtlmodular.client.jei.GTLModularJeiPlugin} 调用 JEI 自己的隐藏机制
     * （{@code IEditModeConfig#hideIngredientUsingConfigFile}）——与玩家在 JEI 编辑模式里手动
     * 「隐藏物品」完全同一套语义。⇒ 隐藏后这 42 件<b>仍可</b>通过配方 / {@code /give} / 创造模式
     * 物品栏拿到，只是不再出现在 JEI 的物品列表与搜索里。
     *
     * <table border="1">
     *   <tr><th>取值</th><th>行为</th></tr>
     *   <tr><td>{@code true}（<b>默认</b>，队长要隐藏）</td>
     *       <td>清单 {@code assets/gtlmodular/jei/hidden_orphans.txt} 里的 42 件在 JEI 里被隐藏</td></tr>
     *   <tr><td>{@code false}</td>
     *       <td>主动调用 {@code showIngredientUsingConfigFile} 撤销隐藏 ⇒ 恢复可见（将来想找它们就关掉）</td></tr>
     * </table>
     *
     * <p>生效时机：改完在配置屏点「保存并关闭」会立刻重新应用一次（配置屏反射调用
     * {@code GTLModularJeiPlugin.refresh()}）；没有 JEI（服务端/没装）时本项无副作用。
     *
     * <p><b>英文镜像 / English mirror</b>：hide the 42 "orphan" NAC circuit-component items in JEI
     * ({@code true} = hidden, default). Display-layer only — nothing is unregistered and no recipe is
     * touched, so the items stay obtainable via recipes / {@code /give} / creative. Turning it off
     * re-shows them.
     *
     * <p>⚠️ <b>本定义必须放在 {@link #SPEC} 之前</b>：{@code SPEC = BUILDER.build()} 之后定义的项
     * <b>不会进配置文件</b>（取值永远等于默认值）。
     */
    private static final ForgeConfigSpec.BooleanValue NANOCHIP_HIDE_ORPHAN_ITEMS =
            BUILDER.comment("NAC 孤儿封装件在 JEI 里隐藏（默认 true = 隐藏）",
                            "Hide the 42 orphan NAC circuit components in JEI (default true = hidden)",
                            "true （默认，队长要隐藏）：_census/残留物品清理_队员.md §8.1 那 42 件（cc_processed_* / cc_casing_* / cc_chip_*）在 JEI 里不显示",
                            "true  (default): the 42 items listed in _census/残留物品清理_队员.md section 8.1 are hidden in JEI",
                            "false：主动撤销隐藏，恢复可见（将来想找它们就关掉这一项）",
                            "false: un-hide them so they show up again in JEI",
                            "只影响 JEI 显示层：不删注册、不注销 Entry、不动任何配方 ⇒ 隐藏后仍可用配方/指令/创造拿到",
                            "Display-layer only: nothing is unregistered and no recipe is changed, so the items stay obtainable",
                            "改完在配置屏点「保存并关闭」立即生效（没有装 JEI 时本项无副作用）",
                            "Takes effect on saving the config screen; harmless when JEI is absent.")
                    .define("nanochipComplex.hideOrphanItems", true);

    // ---- 天基矿石处理厂配方镜像（2026-10-01）----

    /**
     * ★★ 2026-10-01：<b>天基矿石处理厂（{@code gtceu:space_ore_processor}）配方镜像开关</b>（默认开）★★
     *
     * <h4>治的是什么</h4>
     * gtladditions 分支 {@code 3.2.8Custom_SubSpace-fix2} 自带
     * {@code RecipesModify.initSpaceOreProcessor()}，意图就是「同族矿石处理配方每造一条，
     * 就 {@code copyFrom} 一条到 {@code space_ore_processor}」，但它（以及我们第一版照抄它的写法）
     * 挂的 {@code onRecipeBuild} 回调<b>一次都没被触发</b> ⇒ {@code space_ore_processor} <b>0 条配方</b> ⇒
     * 「天基无尽集成矿石处理厂」（{@code gtladditions:space_infinity_integrated_ore_processor}）
     * 与我们的模块C（{@code gtlmodular:molten_space_ore_module}，共用同一张表）都没配方可跑。
     *
     * <h4>现在的做法（v2，2026-10-01 第二轮）</h4>
     * 不再依赖「配方构建回调」✗ —— 改为在 <b>{@code ServerStartedEvent}</b>（配方管理器就绪的那一刻，
     * 与 NAC 自检同一时机）里：反射读 {@code RecipeManager.byType} → 从 <b>首选源
     * {@code gtceu:steam_space_ore_processor}</b>（实机日志 999 条）枚举该表的全部配方 →
     * 逐条复制成 {@code space_ore_processor} 的配方（电压钳到 {@code VA[HV]}=512 EU/t、清空流体输入，
     * 与 gtladditions 上游口径一致）→ <b>同时登记进目标表的 {@code GTRecipeLookup} 与 {@code byType}</b>
     * （前者给机器用，后者给 JEI/计数用）。首选源一条都没有时自动回落到
     * {@code gtceu:integrated_ore_processor}；目标表已有配方则直接跳过（幂等）。
     *
     * <table border="1">
     *   <tr><th>取值</th><th>行为</th></tr>
     *   <tr><td><b>{@code true}（默认）</b></td><td>镜像开启：{@code space_ore_processor} 与源表条数一致
     *       （实机应为 999），JEI 里那个分类不再空、机器能跑。</td></tr>
     *   <tr><td>{@code false}</td><td>完全不执行（退回现状：该配方表 0 条）。用于对照实验。</td></tr>
     * </table>
     *
     * <p><b>改这一项需要重启游戏</b>：只在服务器启动完成（进世界 / 开服）时读一次。
     * 日志里会打两行自证：挂载时一行 {@code 天基矿石处理厂配方镜像已挂载…}，
     * 执行完一行 {@code 天基矿石处理厂配方镜像结果：源 … N 条 → 目标 … M 条，失败 K 条…}。
     *
     * <p>⚠️ <b>本定义必须放在 {@link #SPEC} 之前</b>：{@code SPEC = BUILDER.build()} 之后定义的项
     * <b>不会进配置文件</b>（取值永远等于默认值）。
     */
    private static final ForgeConfigSpec.BooleanValue SPACE_ORE_RECIPE_MIRROR =
            BUILDER.comment("天基矿石处理厂（gtceu:space_ore_processor）配方镜像开关（默认 true = 开）",
                            "Mirror the gtceu:steam_space_ore_processor recipes into gtceu:space_ore_processor (default true)",
                            "true （默认）：开启镜像 —— 服务器启动完成后，把源表全部配方复制一份到天基矿石处理厂配方表",
                            "true  (default): mirror every steam_space_ore_processor recipe into space_ore_processor once the recipe manager is ready",
                            "        源：gtceu:steam_space_ore_processor（实机 999 条）；它一条都没有时回落到 gtceu:integrated_ore_processor",
                            "        目标已有配方 ⇒ 直接跳过（幂等）；每条失败只跳过那一条",
                            "        依据：gtladditions 3.2.8Custom_SubSpace-fix2 分支自带的复制逻辑（onRecipeBuild 回调）实机一次都没触发，该表 0 条，机器转不动",
                            "false：完全不执行（退回现状：该配方表 0 条），仅用于对照实验",
                            "false: do not run the mirror at all (back to the broken state; for A/B testing only)",
                            "改这一项需要重启游戏：只在服务器启动完成（进世界/开服）时读一次",
                            "Requires a game restart: it is read once when the server has started.")
                    .define("spaceOre.recipeMirror", true);

    // ---- ★ 2026-11-12【原初模式】总开关：把我们所有自定义的「结构 / 成型 / 连接」层变透明 ----

    /**
     * ★★ <b>「原初模式」总开关</b>（默认 {@code true} = 开）★★
     *
     * <p>队长原话：「我们尝试一下，<b>直接用原初的连接和成型</b>吧，我们可能往上面堆的东西太多，
     * 给自己堵住了」。现象是<b>进游戏后主机与模块都自己不成型</b>，而日志里
     * 「{@code 裸 return（必然不成形，直接挡掉）}」反复刷 —— 说明挡路的层不止「样板身份闸」那一个。
     *
     * <p><b>它做什么</b>：一开就把本模组自己加在<b>结构判定链 / 结构位置簿记 / 结构检查调度 /
     * 模块↔主机连接注入</b>这四处的所有自定义层一次置为透明，让「成形 / 不成形」「结构失效」
     * 「什么时候重判」「模块怎么接上主机」<b>全部照上游原本的方式跑</b>。逐层清单：
     * <ol>
     *   <li><b>A+ 精确预检</b>（{@code NanochipExactPrecheck#scan}）：一律返回 {@code Verdict.UNKNOWN}
     *       （{@code mayForm=true}）⇒ <b>不再有"必然不成形 ⇒ 裸 return"这条</b>，昂贵匹配照常跑；</li>
     *   <li><b>便宜结构预检</b>（{@code NanochipStructurePrecheck#check}）：一律 {@code PASS_UNKNOWN}
     *       （{@code mayForm=true}）；</li>
     *   <li><b>结构检查节流</b>（{@code NanochipStructureCheckThrottle#shouldRun}）：一律 {@code true}
     *       ⇒ 不做我们那套 {@code FORMED_SKIP=6 / UNFORMED_SKIP=1}；</li>
     *   <li><b>变化闸</b>（{@code NanochipStructureCheckGate#shouldRunFullMatch}）：一律放行，
     *       <b>只留统计读数</b>（{@code noteUnformedPoll} / {@code recordOutcome} 照记）；</li>
     *   <li><b>结构位置缓存 / 索引 / 「方块移除 ⇒ 定点失效」</b>
     *       （{@code NanochipStructurePosIndex#register} / {@code #noteBlockRemoved}）：
     *       不登记、不建表、不记账 ⇒ 整条链不接管，失效判定回到上游自己的
     *       {@code LevelMixin → MultiblockState.onBlockStateChanged} 事件路径；</li>
     *   <li><b>连接 / 成型注入</b>（{@code NanochipAssemblyComplexMachine#onStructureFormed} /
     *       {@code #tickModuleRefresh} / {@code #checkPatternWithLock}）：不做「先清后扫」、
     *       不做 20 tick 补登记、不做周期重扫、不做同 tick 折叠 —— 只留上游
     *       {@code super.onStructureFormed()} 自己那套。</li>
     * </ol>
     *
     * <p><b>刻意保留（原初模式下也不关）</b>：
     * <ol>
     *   <li><b>结构匹配锁加固</b>（{@code NanochipPatternLockHardening}，开关
     *       {@code NanochipAssemblyComplexMachine.NAC_PATTERN_LOCK_HARD_FIX}）——
     *       它是「<b>唯一一条删了可能让整个存档停摆</b>」的改动：GTCEu 的
     *       {@code IMultiController.checkPatternWithLock()} 默认实现是<b>裸 {@code unlock()}</b>，
     *       {@code checkPattern()} 抛异常 ⇒ 锁永久泄漏 ⇒ 服务端线程永久阻塞
     *       （本工程实测 watchdog {@code 40001 / 85752 / 132410 ms}）。
     *       它正常路径与上游<b>逐字节等价</b>、只差异常路径「锁还回去」⇒ 保留它<b>不违背"原初"</b>
     *       （见 {@code _census/结构归上游_对照山海方案.md} §③.3 / §④ 第 ③ 行）；</li>
     *   <li><b>全部诊断日志</b>（缺件清单 / 逐格原因 / A+ 统计学读数）—— 用户要看的正是它们；</li>
     *   <li><b>界面 / Jade / 结构预览 / 能量缓存分配 / 仓位清单心跳 / 扣电与配方逻辑</b>
     *       —— 与「成形/不成形」判定无关。</li>
     * </ol>
     *
     * <p><b>为什么默认 {@code true}</b>：用户当前要做的就是这个实验（「先用原初试」），
     * 而本开关每一层都是「<b>只加判断分支、不删原逻辑</b>」⇒ 置 {@code false} 即<b>逐字</b>回到
     * 打开本开关之前的行为（改前原文留档见 {@code _census/原初模式_回退包/ORIGINAL_HUNKS.md}）。
     *
     * <p><b>生效时机</b>：每次判定现读配置（不用重启；配置屏改完即生效）。
     * <b>零存档影响</b>：不改结构图案、不新增 {@code @Persisted}、不改 NBT、不改方块 id。
     */
    private static final ForgeConfigSpec.BooleanValue NANOCHIP_ORIGINAL_MODE =
            BUILDER.comment("★ NAC「原初模式」总开关（默认 true = 开）：把我们所有自定义的「结构 / 成型 / 连接」层变透明，完全交回 gtceu/gtlcore 原本的方式",
                            "★ NAC 'original mode' master switch (default true = ON): make ALL of our own structure / formation / connection layers transparent and hand everything back to stock gtceu / gtlcore",
                            "true （默认=原初）：A+ 精确预检与便宜预检一律视为「可能成型」（不再有『必然不成形 ⇒ 裸 return』）；",
                            "                 结构检查节流与变化闸一律放行（只留统计读数）；结构位置缓存/索引/『方块移除⇒定点失效』整条链不接管；",
                            "                 onStructureFormed 不做『先清后扫』、不做 20 tick 补登记、不做周期重扫、不做同 tick 折叠。",
                            "                 日志里的『裸 return（必然不成形，直接挡掉）』应当降到 0。",
                            "true  (default): our exact/cheap prechecks always answer 'may form' (no more 'certainly unformed -> bare return');",
                            "                 throttle and change-gate always pass (stats are still recorded); the structure position cache/index and the",
                            "                 block-break invalidation chain stay out of the loop; onStructureFormed does no clear-then-scan, no 20-tick",
                            "                 re-registration, no periodic rescan and no same-tick folding. The 'bare return' counter should drop to 0.",
                            "false：逐字恢复本模组自己那几层（= 打开本开关之前的行为）。",
                            "false: restore our own layers verbatim (the behaviour from before this switch existed).",
                            "★ 刻意不关：结构匹配锁加固（唯一一条『删了可能让整个存档停摆』的改动；正常路径与上游逐字节等价）、全部诊断日志、界面/Jade/预览/能量分配/扣电与配方。",
                            "★ Deliberately NOT disabled: the pattern-lock hardening (the only change whose removal can stall the whole save; byte-identical on the normal path),",
                            "                 all diagnostics, GUI / Jade / preview / energy distribution / power draw and recipe logic.",
                            "零存档影响：不改结构图案、不新增持久化字段、不改 NBT、不改方块 id。每次判定现读，不用重启。",
                            "Zero save impact: no pattern change, no new persisted field, no NBT change, no block id change. Read live, no restart needed.")
                    .define("nanochipComplex.originalMode", true);

    /**
     * ★★ 2026-11-14【校准计数白名单】★★ —— NAC 校准「哪条配方才计入」的名册（**手动维护**，运行时可改）。
     *
     * <h4>队长原话（两道令，逐字）</h4>
     * <blockquote>「<b>那就弄白名单，把他们的ID手动添加到校准的名册里，只有被允许的才能增加校准的数量</b>」<br>
     * 「<b>只有最终产出电路才算</b>」</blockquote>
     *
     * <h4>为什么（已取证的根因）</h4>
     * 队长「一个寰宇电路都没做过」，但校准里寰宇在涨。实机日志逐字：
     * 线程样本 {@code #8 …/conversion/cc_cosmic_processing_unit_core ×1000 31%}；
     * 同刻记账 {@code 寰宇电路 +1000 ⇒ 本族 1064 / 历史 1080}。
     * 根因：<b>换算式（{@code gtlmodular:nanochip_conversion}）的产物是中间件</b>
     * {@code cc_cosmic_processing_unit_core}（{@code data/gtlmodular/recipes/nanochip/conversion/cc_cosmic_processing_unit_core.json:30}），
     * 族判定在配方元数据缺失时按产物 id 子串猜族 ⇒ 命中 {@code COSMIC} ⇒
     * 「<b>换算</b>」被算成了「<b>造电路</b>」。换算只是「真件 ⇄ CC」互换 = <b>搬运/包装，不产电路</b>。
     *
     * <h4>本项的语义（逐条，与代码逐字对应）</h4>
     * <ol>
     *   <li><b>每一项 = 一个配方 ID</b>（{@code namespace:path} 形态，如
     *       {@code gtlmodular:nanochip/assembly_matrix/002_cc_crystal_processor}）；</li>
     *   <li><b>以 {@code *} 结尾 ⇒ 前缀匹配</b>（例：{@code abc*} 命中 {@code abcdef}）；
     *       <b>否则 ⇒ 精确匹配</b>（例：{@code abc} 只命中 {@code abc}，<b>不</b>命中 {@code abcdef}）；</li>
     *   <li><b>命中即允许计数</b>；<b>不命中 ⇒ 这条配方不计入校准</b>（记 0 条，不是记 0 个）；</li>
     *   <li>★ <b>列表为空（{@code []}）⇒ 回退到「内置默认名单」</b>（见 {@link #BUILT_IN_CALIBRATION_WHITELIST} 与
     *       {@link #getNanochipCalibrationRecipeWhitelist()}）——
     *       <b>刻意不是「一条都不计」</b>：若把空列表当"全禁"，玩家在配置屏里清空一次就会让校准永远为 0，
     *       且现场毫无提示（一脸懵）。回退到默认名单 = 「清空」等价于「恢复默认」，语义可预期。</li>
     * </ol>
     *
     * <h4>默认值 = 本模组真正「造电路」的两张表（逐条核实过，不是按表名猜的）</h4>
     * {@code gtlmodular:nanochip/assembly_complex/*}（7 条）+ {@code gtlmodular:nanochip/assembly_matrix/*}（23 条），
     * 共 30 条，判据 = <b>这 30 条是全模组唯一带
     * {@code "data": {"circuit-calibration": "<族名>"}}</b> 的配方（该键 = 配方作者显式声明的「本配方产出某族电路」），
     * 且产物 id 逐条就是成品电路（{@code cc_crystal_processor} / {@code cc_wetware_mainframe} /
     * {@code cc_cosmic_processor} / {@code cc_supracausal_mainframe} …）。
     * <b>换算表（{@code nanochip_conversion}）与全部只产中间件（{@code cc_processed_*}）的前道工序
     * 绝对不在默认名单里</b> —— 挡掉的正是本次的根因。
     *
     * <h4>显示即事实</h4>
     * 白名单条数由 {@link #getNanochipCalibrationRecipeWhitelistCount()} 对外提供（当前生效条数，现读）；
     * 面板 / Jade 若要显示「当前白名单条数」直接取它即可（本项未强制接 UI，接口先备好）。
     *
     * <h4>一行回退（逐字回到"全部计入"）</h4>
     * 见 {@code NanochipAssemblyComplexMachine.CALIBRATION_WHITELIST_ENABLED}：置 {@code false} ⇒ 本项被完全忽略。
     *
     * <h4>生效时机</h4>
     * 每次记账<b>现读</b>（{@code ConfigValue.get()} 是哈希查表 + 现读缓存值）⇒ 配置屏改完即生效；
     * <b>只有手改 {@code config/gtlmodular-common.toml} 才需要重启游戏</b>。
     *
     * <h4>写法说明（为什么是 {@code defineList}）</h4>
     * 本文件此前<b>没有</b>列表型配置项（全部是 Boolean / Int / Double / String 单值，
     * 最近同款单值写法见 {@link #LAVA_FLUID_ID} 的 {@code ConfigValue<String>}），
     * 所以这一项照 Forge 自己的列表 API {@code defineList(path, defaultList, elementValidator)} 写；
     * 类型是 {@code ConfigValue<List<? extends String>>}（Forge 不允许把 {@code ConfigValue<List<String>>}
     * 当字段类型写 —— 通配符是它的签名要求）。
     */
    static final List<String> BUILT_IN_CALIBRATION_WHITELIST = List.of(
            "gtlmodular:nanochip/assembly_complex/*",
            "gtlmodular:nanochip/assembly_matrix/*");

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> NANOCHIP_CALIBRATION_RECIPE_WHITELIST =
            BUILDER.comment("NAC 校准计数白名单（★ 2026-11-14 队长：「那就弄白名单，把他们的ID手动添加到校准的名册里，只有被允许的才能增加校准的数量」+「只有最终产出电路才算」）",
                            "★ 每一项 = 一个配方 ID（namespace:path）；以 * 结尾 = 前缀匹配（例 abc* 命中 abcdef），否则 = 精确匹配（例 abc 只命中 abc）",
                            "★ 命中即允许计数；不命中 ⇒ 这条配方不计入校准（换算表、只产中间件 cc_processed_* 的前道工序因此不再涨校准）",
                            "★ 列表为空 = 回退到内置默认名单（= 那两张真正造电路的表的 30 条），刻意不是「一条都不计」：清空 = 恢复默认，不会让校准永远为 0",
                            "★ 默认 = gtlmodular:nanochip/assembly_complex/* + gtlmodular:nanochip/assembly_matrix/*（全模组唯一带 circuit-calibration 元数据的 30 条 = 成品电路）",
                            "★ 要放开某条新配方的记账：在下面这个列表里加一行，ID 从日志「校准白名单未命中 ⇒ 这条配方不计入校准：<id>」里整行复制即可；前缀匹配就写 `xxx*`",
                            "★ 一行回退到「全部计入」：NanochipAssemblyComplexMachine.CALIBRATION_WHITELIST_ENABLED = false（本项随即被完全忽略）",
                            "★ 本项列出的是最终产出电路的组装/装配配方；换算（真件 <-> CC 互换）与任何中间件工序都不属于「造电路」",
                            "★ 生效时机：每次记账现读 ⇒ 配置屏改完即生效；只有手改 config/gtlmodular-common.toml 才需要重启游戏。",
                            "★ Whichever recipe is listed here is allowed to increase the calibration counters; entries ending in '*' mean prefix match. Empty list falls back to the built-in default list.")
                    .defineList("nanochipComplex.calibrationRecipeWhitelist", BUILT_IN_CALIBRATION_WHITELIST,
                            // 元素校验：只接受字符串（非字符串项被 Forge 挡在配置读取之外 ⇒ 不会静默变成一个怪 ID）
                            o -> o instanceof String);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    /**
     * ★ 2026-10-01：天基矿石处理厂配方镜像是否开启（{@code spaceOre.recipeMirror}，默认 {@code true}）。
     *
     * <p>由 {@link com.gtlmodular.common.data.module.SpaceOreRecipeMirror} 在
     * {@code ServerStartedEvent} 里读一次。
     * 读配置失败（配置还没加载 / 测试环境）时回落到 {@code true} —— 与配置项默认值一致：
     * 这个补丁本来就是「把该有的配方补回来」，宁可跑也不要静默关掉。
     */
    public static boolean isSpaceOreRecipeMirrorEnabled() {
        try {
            return SPACE_ORE_RECIPE_MIRROR.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** 真空传送是否要求"同色管道通路"（默认 false = 简单模式）。 */
    public static boolean isVacuumRequiresPipes() {
        try {
            return NANOCHIP_VACUUM_REQUIRES_PIPES.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** NAC 是否启用。 */
    public static boolean isNanochipComplexEnabled() {
        try {
            return NANOCHIP_ENABLED.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * ★★ 2026-11-12【原初模式】是否开启（键 {@code nanochipComplex.originalMode}，默认 {@code true}）★★
     *
     * <p>{@code true} ⇒ 本模组所有自定义的「结构 / 成型 / 连接」层一律<b>透明</b>，
     * 完全交回 gtceu/gtlcore 原本的方式。逐层语义与「刻意保留项」见
     * {@link #NANOCHIP_ORIGINAL_MODE} 的完整中文注释。
     *
     * <p><b>读配置失败时回落到同一个默认值 {@code true}</b> —— 与配置项默认一致，
     * 也正是"最不拦"的那一侧（原初模式绝不会用我们自己的判据把结构判死），
     * 与 {@link #isNanochipComplexEnabled()} 同款纪律。
     *
     * <p><b>本方法被高频读</b>（每次结构判定、每次结构格移除事件一次）：Forge 的
     * {@code ConfigValue.get()} 是哈希查表 + 现读缓存值，无分配、无锁竞争 ⇒ 可忽略。
     */
    public static boolean isNanochipOriginalMode() {
        try {
            return NANOCHIP_ORIGINAL_MODE.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** NAC 直通档并行上限。 */
    public static int getNanochipDirectParallelCap() {
        try {
            return NANOCHIP_DIRECT_PARALLEL_CAP.get();
        } catch (Throwable ignored) {
            return 1024;
        }
    }

    /**
     * NAC 直通档的<b>跨配方线程</b>基线（默认 1）。
     *
     * <p>读配置失败时回落到同一个默认值 1 —— 与 {@link #getNanochipDirectParallelCap()} 同款纪律
     * （配置系统未初始化时不能让机器算出 0 条线程）。
     */
    public static int getNanochipDirectCrossRecipeThreads() {
        try {
            return Math.max(1, NANOCHIP_DIRECT_THREADS.get());
        } catch (Throwable ignored) {
            return 1;
        }
    }

    /**
     * 纳米校准的判定分母是否固定为原版硬编码的 100000（= 整个历史窗口）。
     *
     * <p>★ 2026-09-21 队长令「改」：默认值是 {@code false}（分母 = 已记录数）＝族条看得见地涨；
     * {@code true}＝原版口径（分母固定 100000，可随时切回）。
     * 读配置失败时回落到 <b>同一个默认值</b>（{@code false}），与配置项默认保持一致。
     */
    public static boolean isNanochipCalibrationUseFullWindow() {
        try {
            return NANOCHIP_CALIBRATION_USE_FULL_WINDOW.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * ★★ 2026-09-21（队长令「做」；边界放宽到本文件后补上）★★
     * 供游戏内配置界面写入（配置屏第 7 组「NAC 校准与排障 → 校准分母用原版10万」那一行）。
     *
     * <p>写法与本文件其它项<b>逐字同款</b>（值没变不写 + {@code ConfigValue.set(...)}，见
     * {@code setCoilDiscountEnabled}）。补齐它之前，配置屏只能靠反射去够
     * {@code NANOCHIP_CALIBRATION_USE_FULL_WINDOW}；现在反射已撤，界面改回普通方法引用。
     *
     * <p><b>生效时机：不用重启</b>——{@code NanochipCalibration.useFullWindowDenominator()}
     * 每次算占比、主机 {@code tickSync() → updateSyncedTexts()} 每 tick 同步时都现读本项。
     * 写值当场落盘（Forge 的 TOML 配置器带 nightconfig {@code autosave()}）。
     * 默认值<b>没动</b>（仍 {@code false} = 已记录数）。
     */
    public static void setNanochipCalibrationUseFullWindow(boolean value) {
        if (NANOCHIP_CALIBRATION_USE_FULL_WINDOW.get() != value) {
            NANOCHIP_CALIBRATION_USE_FULL_WINDOW.set(value);
        }
    }

    /**
     * ★★ 2026-11-14【校准计数白名单】当前生效名册（**现读**，不可变快照）★★
     *
     * <p>语义、默认值、为什么空列表回退、一行回退开关，逐条见
     * {@link #NANOCHIP_CALIBRATION_RECIPE_WHITELIST} 与本项默认值常量
     * {@link #BUILT_IN_CALIBRATION_WHITELIST} 的注释（队长原话：
     * 「<b>那就弄白名单，把他们的ID手动添加到校准的名册里，只有被允许的才能增加校准的数量</b>」
     * +「<b>只有最终产出电路才算</b>」）。
     *
     * <p><b>空列表 ⇒ 返回内置默认名单</b>（刻意不是空集合）：见配置项注释第 4 条 ——
     * 把空列表当"全禁"会让清空过一次的玩家看到校准永远为 0 且现场没有提示。
     *
     * <p>读配置失败（配置系统未初始化 / 测试环境）时同样回落到内置默认名单 ——
     * 与配置项默认值<b>逐字一致</b>（同 {@link #isNanochipCalibrationUseFullWindow()} 的纪律：
     * 回落值必须等于默认值，否则"配置没加载"这一态会偷偷改语义）。
     *
     * @return 逐项形如 {@code namespace:path}，或以 {@code *} 结尾的前缀项；<b>至少含内置默认名单</b>
     */
    public static List<String> getNanochipCalibrationRecipeWhitelist() {
        List<? extends String> configured;
        try {
            configured = NANOCHIP_CALIBRATION_RECIPE_WHITELIST.get();
        } catch (Throwable ignored) {
            return BUILT_IN_CALIBRATION_WHITELIST;
        }
        if (configured == null || configured.isEmpty()) {
            // ★ 空列表 = 恢复默认（见配置项注释第 4 条），不是「一条都不计」
            return BUILT_IN_CALIBRATION_WHITELIST;
        }
        return List.copyOf(configured);
    }

    /**
     * 当前生效的白名单<b>条数</b>（供面板 / Jade 显示「当前白名单条数」用；纯只读、现读）。
     *
     * <p>「显示即事实」：这个数就是 {@link #getNanochipCalibrationRecipeWhitelist()} 的长度，
     * 不另存副本、不缓存 ⇒ 界面上看到的条数与记账实际用的名册<b>永远是同一个</b>。
     */
    public static int getNanochipCalibrationRecipeWhitelistCount() {
        return getNanochipCalibrationRecipeWhitelist().size();
    }

    /**
     * 全板浸没装置每完成一次配方上涨的杂质百分点。
     */
    public static double getNanochipImpurityPerRecipe() {
        try {
            return NANOCHIP_IMPURITY_PER_RECIPE.get();
        } catch (Throwable ignored) {
            return 0.5D;
        }
    }

    /** NAC 调试日志开关。 */
    public static boolean isNanochipDebugLogsEnabled() {
        try {
            return NANOCHIP_DEBUG_LOGS.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * ★★ 2026-09-21（队长排障专用，默认 {@code true}）★★
     * NAC 结构日志是否「全打」：{@code true} ⇒ 结构结论 / 逐格原因一律 INFO、不折叠、不降 debug。
     *
     * <p>队长原话：「我更新了add，还有core现在我们的模块都不做东西了」＋（对三个选项回）「查」。
     * 0.1.40 的降噪把逐格原因折进 debug，而队长那台机器 {@code logs/debug.log} 是 09/15 的旧文件
     * ⇒ 原因看不见、只能猜。本开关就是把这个口子重新打开。
     *
     * <p><b>唯一落点</b>：{@code NanochipStructureLogGate.shouldEmitInfo} 的返回值
     * —— 只决定 {@code LOGGER.info} 还是 {@code LOGGER.debug}，
     * <b>不参与任何结构 / 配方 / 扣料判定</b>。
     *
     * <p><b>读配置失败回落 {@code true}</b>（排障期宁可多打）：配置还没加载时也照样看得见原因。
     * ★ 2026-09-21：改这一项<b>不用重启</b>——在配置界面「NAC 校准与排障 → 结构日志全打（排障用）」
     * 里点「保存并关闭」当场生效（本 getter 在 {@code shouldEmitInfo} 里每次判定都被调用），
     * 同一次保存立刻写回 toml；<b>只有手改 toml 才需要重启游戏</b>（Forge 只在启动时读文件）。
     */
    public static boolean isNanochipVerboseStructureLogs() {
        try {
            return NANOCHIP_VERBOSE_STRUCTURE_LOGS.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * ★★ 2026-09-21（队长令「做」；边界放宽到本文件后补上）★★
     * 供游戏内配置界面写入（配置屏第 7 组「NAC 校准与排障 → 结构日志全打（排障用）」那一行）。
     *
     * <p>写法与本文件其它项<b>逐字同款</b>（值没变不写 + {@code ConfigValue.set(...)}）。
     * 补齐它之前，配置屏只能靠反射去够 {@code NANOCHIP_VERBOSE_STRUCTURE_LOGS}；现在反射已撤。
     *
     * <p><b>生效时机：不用重启</b>——{@code NanochipStructureLogGate.shouldEmitInfo} 每次结构判定
     * 都现读本项（{@code get()}）；写值当场落盘（Forge 的 TOML 配置器带 nightconfig
     * {@code autosave()}）。默认值<b>没动</b>（仍 {@code true} = 全打）。
     */
    public static void setNanochipVerboseStructureLogs(boolean value) {
        if (NANOCHIP_VERBOSE_STRUCTURE_LOGS.get() != value) {
            NANOCHIP_VERBOSE_STRUCTURE_LOGS.set(value);
        }
    }

    /**
     * ★★ 2026-09-19：NAC 是否处于「简单物流模式」（{@code nanochipComplex.simpleLogisticsMode}）。
     *
     * <p>{@code true}（<b>★ 2026-09-21 起为配置文件默认值</b>）= 简单：样板总成 + 镜像连接给模块供
     * CC/PC、真空仓下限放宽为 0、主机额外挂「换算」表；
     * {@code false} = 原版(NH)：真空仓物流、真空仓 ≥ 1。
     * <p>★ <b>产出与本项无关</b>：两种模式走同一套阶梯 —— 最基础的处理器 8 / 装配 4 / 电脑 2 / 主机 1 个电路（沿革见
     * {@link #NANOCHIP_SIMPLE_LOGISTICS} 的类注释；2026-09-18 曾为「原版 16 / 简单 8」，2026-09-21 先一律 8 再改阶梯）。
     *
     * <p>读配置失败（还没加载 / 测试环境）时回落到 {@code false} ⇒ <b>永远是"原版行为"那一侧</b>：
     * 这是<b>异常回落</b>而非默认值（默认值由配置文件给出，现在是 {@code true}）；
     * 配置读不到时按"不改变行为"处理，与"默认开"不矛盾。
     */
    public static boolean isNanochipSimpleLogistics() {
        try {
            return NANOCHIP_SIMPLE_LOGISTICS.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * ★★ 2026-11-13【NAC 电网取电】是否开启（键 {@code nanochipComplex.gridPower.enabled}，默认 {@code true}）★★
     *
     * <p>{@code true} ⇒ NAC 在没有能源仓时也能跑：取电口优先向<b>放置者（或其 FTB 队伍）的
     * GTMThings 无线电网</b>要电，能源仓只作兜底；两者都没有 ⇒ 原地等电（不退进度、不掉料）。
     * {@code false} ⇒ <b>逐字回到本改动之前</b>（{@code getEnergyContainer()} 里先判本开关，
     * 判否直接返回基类结果 ⇒ 一个分支关闭整条改动）。
     *
     * <p><b>读配置失败时回落到同一个默认值 {@code true}</b> —— 与配置项默认一致
     * （{@link #isNanochipComplexEnabled()} / {@link #isNanochipOriginalMode()} 同款纪律）。
     *
     * <p>本方法被高频读（每次 {@code getEnergyContainer()}）：Forge 的 {@code ConfigValue.get()}
     * 是哈希查表 + 现读缓存值，无分配、无锁竞争 ⇒ 可忽略（同 {@link #isNanochipOriginalMode()} 的取证）。
     */
    public static boolean isNanochipGridPowerEnabled() {
        try {
            return NANOCHIP_GRID_POWER.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static int getNanochipGridPowerTier() {
        try {
            return NANOCHIP_GRID_POWER_TIER.get();
        } catch (Throwable ignored) {
            return 14;
        }
    }

    public static long getNanochipGridPowerAmperage() {
        try {
            return NANOCHIP_GRID_POWER_AMPERAGE.get();
        } catch (Throwable ignored) {
            return 65536L;
        }
    }

    /**
     * 供游戏内配置界面写入。
     *
     * <p>⚠️ 只有<b>结构谓词</b>需要重启才生效（真空仓下限在机器注册时构建一次）；
     * 产出<b>不再随本项变化</b>（2026-09-21 起两种模式走同一套阶梯：处理器 8 / 装配 4 / 电脑 2 / 主机 1，运行期缩放已停用）。
     */
    public static void setNanochipSimpleLogistics(boolean value) {
        if (NANOCHIP_SIMPLE_LOGISTICS.get() != value) {
            NANOCHIP_SIMPLE_LOGISTICS.set(value);
        }
    }

    /**
     * ★★ 2026-09-19：NAC 孤儿封装件是否在 JEI 里隐藏
     * （{@code nanochipComplex.hideOrphanItems}，默认 {@code true} = 隐藏）。
     *
     * <p>读配置失败（还没加载 / 测试环境）时回落到 {@code true} ⇒ 与队长口径「先隐藏吧」一致。
     */
    public static boolean isHideOrphanItemsEnabled() {
        try {
            return NANOCHIP_HIDE_ORPHAN_ITEMS.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** 供游戏内配置界面写入（配置屏保存时还会反射调用 JEI 插件重新应用一次）。 */
    public static void setHideOrphanItemsEnabled(boolean value) {
        if (NANOCHIP_HIDE_ORPHAN_ITEMS.get() != value) {
            NANOCHIP_HIDE_ORPHAN_ITEMS.set(value);
        }
    }

    /** 调试日志是否开启（AE 推送明细 / 内部槽 / 网络存量对比）。 */
    public static boolean isPatternDebugLogsEnabled() {
        try {
            return PATTERN_DEBUG_LOGS.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isPatternIdentityGateEnabled() {
        try {
            return PATTERN_IDENTITY_GATE.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static boolean isAutoRecipeModeSwitchEnabled() {
        try {
            return AUTO_RECIPE_MODE_SWITCH.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void setPatternIdentityGateEnabled(boolean value) {
        if (PATTERN_IDENTITY_GATE.get() != value) {
            PATTERN_IDENTITY_GATE.set(value);
        }
    }

    public static boolean isDualFluidTaxEnabled() {
        return DUAL_FLUID_TAX_ENABLED.get();
    }

    public static String getLavaFluidId() {
        return LAVA_FLUID_ID.get();
    }

    public static String getBlazeFluidId() {
        return BLAZE_FLUID_ID.get();
    }

    public static int getIvBaselineMbPerSecond() {
        return IV_BASELINE_MB_PER_SECOND.get();
    }

    public static double getPerTierMultiplier() {
        return PER_TIER_MULTIPLIER.get();
    }

    public static int getBaselineTier() {
        return BASELINE_TIER.get();
    }

    public static boolean isAllowBelowBaseline() {
        return ALLOW_BELOW_BASELINE.get();
    }

    public static int getDrainIntervalTicks() {
        return DRAIN_INTERVAL_TICKS.get();
    }

    // ---- 供游戏内配置界面写入（机器逻辑实时读取，改完即生效；退出时 Forge 自动落盘）----
    public static void setEnabled(boolean value) {
        if (DUAL_FLUID_TAX_ENABLED.get() != value) {
            DUAL_FLUID_TAX_ENABLED.set(value);
        }
    }

    public static void setLavaFluidId(String value) {
        if (!LAVA_FLUID_ID.get().equals(value)) {
            LAVA_FLUID_ID.set(value);
        }
    }

    public static void setBlazeFluidId(String value) {
        if (!BLAZE_FLUID_ID.get().equals(value)) {
            BLAZE_FLUID_ID.set(value);
        }
    }

    public static void setIvBaselineMbPerSecond(int value) {
        if (IV_BASELINE_MB_PER_SECOND.get() != value) {
            IV_BASELINE_MB_PER_SECOND.set(value);
        }
    }

    public static void setPerTierMultiplier(double value) {
        if (PER_TIER_MULTIPLIER.get() != value) {
            PER_TIER_MULTIPLIER.set(value);
        }
    }

    public static void setBaselineTier(int value) {
        if (BASELINE_TIER.get() != value) {
            BASELINE_TIER.set(value);
        }
    }

    public static void setAllowBelowBaseline(boolean value) {
        if (ALLOW_BELOW_BASELINE.get() != value) {
            ALLOW_BELOW_BASELINE.set(value);
        }
    }

    public static void setDrainIntervalTicks(int value) {
        if (DRAIN_INTERVAL_TICKS.get() != value) {
            DRAIN_INTERVAL_TICKS.set(value);
        }
    }

    public static boolean isCoilDiscountEnabled() {
        return COIL_DISCOUNT_ENABLED.get();
    }

    public static int getCoilDiscountBaseTemperature() {
        return COIL_DISCOUNT_BASE_TEMPERATURE.get();
    }

    public static int getCoilDiscountTemperaturePerLevel() {
        return COIL_DISCOUNT_TEMPERATURE_PER_LEVEL.get();
    }

    public static double getCoilDiscountFactorPerLevel() {
        return COIL_DISCOUNT_FACTOR_PER_LEVEL.get();
    }

    public static double getCoilDiscountMinFactor() {
        return COIL_DISCOUNT_MIN_FACTOR.get();
    }

    public static double getCoilDiscountDurationMultiplier() {
        return COIL_DISCOUNT_DURATION_MULTIPLIER.get();
    }

    public static double getStripperEnergyMultiplier() {
        return STRIPPER_ENERGY_MULTIPLIER.get();
    }

    public static double getStripperDurationMultiplier() {
        return STRIPPER_DURATION_MULTIPLIER.get();
    }

    // ---- 以下 setter 供游戏内配置界面写入（机器逻辑实时读取，改完即生效；退出时 Forge 自动落盘）----

    public static void setAutoRecipeModeSwitchEnabled(boolean value) {
        if (AUTO_RECIPE_MODE_SWITCH.get() != value) {
            AUTO_RECIPE_MODE_SWITCH.set(value);
        }
    }

    public static void setCoilDiscountEnabled(boolean value) {
        if (COIL_DISCOUNT_ENABLED.get() != value) {
            COIL_DISCOUNT_ENABLED.set(value);
        }
    }

    public static void setCoilDiscountBaseTemperature(int value) {
        if (COIL_DISCOUNT_BASE_TEMPERATURE.get() != value) {
            COIL_DISCOUNT_BASE_TEMPERATURE.set(value);
        }
    }

    public static void setCoilDiscountTemperaturePerLevel(int value) {
        if (COIL_DISCOUNT_TEMPERATURE_PER_LEVEL.get() != value) {
            COIL_DISCOUNT_TEMPERATURE_PER_LEVEL.set(value);
        }
    }

    public static void setCoilDiscountFactorPerLevel(double value) {
        if (COIL_DISCOUNT_FACTOR_PER_LEVEL.get() != value) {
            COIL_DISCOUNT_FACTOR_PER_LEVEL.set(value);
        }
    }

    public static void setCoilDiscountMinFactor(double value) {
        if (COIL_DISCOUNT_MIN_FACTOR.get() != value) {
            COIL_DISCOUNT_MIN_FACTOR.set(value);
        }
    }

    public static void setCoilDiscountDurationMultiplier(double value) {
        if (COIL_DISCOUNT_DURATION_MULTIPLIER.get() != value) {
            COIL_DISCOUNT_DURATION_MULTIPLIER.set(value);
        }
    }

    public static void setStripperEnergyMultiplier(double value) {
        if (STRIPPER_ENERGY_MULTIPLIER.get() != value) {
            STRIPPER_ENERGY_MULTIPLIER.set(value);
        }
    }

    public static void setStripperDurationMultiplier(double value) {
        if (STRIPPER_DURATION_MULTIPLIER.get() != value) {
            STRIPPER_DURATION_MULTIPLIER.set(value);
        }
    }
}
