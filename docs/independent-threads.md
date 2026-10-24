# 其他机器如何接入独立批次引擎

完整版的配置 `independentThreadsForOtherMachines`（默认关闭）可以让 NAC 以外的多方块机器也用独立批次，带黑白名单、批次所有权和存档组件。**这是实验功能，不要用于正式存档。** 开关关闭且没有自有批次时，机器完全走原逻辑；独立版没有这些配置、适配器和恢复命令。

这里的“线程”是服务器 tick 内按顺序推进的批次，各自保存配方和进度，不创建 Java 工作线程。接入其他机器必须逐机确定并行、时长、能源和退出规则，会影响玩家手感的规则要先确认再改。

总开关和黑白名单的配置监听只提交重载通知，服务端在 tick 开头发布不可变设置并唤醒机器，同一 tick 内不会因文件监控线程而切换准入规则。普通/无线族的 `getGTRecipe()` 和 Mutable 的 `getRecipe()` 一旦放行并返回配方，对应的 `setupRecipe()` 沿用这次决定，因为合批已经扣料；该放行只认同一配方对象、仅使用一次，不适用于未经过合批的直接开工。新设置仍只影响后续准入，已有批次按原规则结清；读档保留原批次和排空状态，待首个 tick 发布配置后再决定是否追加排空请求。

接管同时检查逻辑族、燃料模式及仓室处理器兼容性。不支持 ME 样板/输出处理器或特殊仓室时，没有自有批次的机器继续使用上游逻辑。兼容机器若找不到可准入候选，或候选在实际扣料前被拒绝，也交回上游并保留其配方缓存；同一 tick 的合批、缓存重试和 setup 沿用这次放行。已扣料、待出料或无法解码的自有记录仍保留所有权，暂不支持时等待，不能通过回退丢弃记录；最后一笔结清的 tick 仍由独立调度占有。

## 可复用接口与依赖方向

共享代码位于 `src/nac/java/com/gtlmodular/common/thread/`：

| 接口或类 | 负责什么 |
| --- | --- |
| [`ConcurrentBatchEngine<P, B>`](../src/nac/java/com/gtlmodular/common/thread/ConcurrentBatchEngine.java) | 有序批次表、推进、完成后补位、清空、存读档；`B extends BatchState<P>` |
| [`BatchState<P>`](../src/nac/java/com/gtlmodular/common/thread/BatchState.java) | `payload()`、`duration()`、`progress()`、`setProgress(int)`；`Factory.create(payload, duration, progress)` 创建同一份运行状态 |
| [`BatchPolicy<P>`](../src/nac/java/com/gtlmodular/common/thread/BatchPolicy.java) | `slots()`、`findBatch()`、`duration(P)`、`consumeInputs(P)`、`tickInputs(P)`、`tryFinishOutputs(P)`，兼容入口 `finishOutputs(P)` |
| [`BatchArchive<E>`](../src/nac/java/com/gtlmodular/common/thread/BatchArchive.java) / [`BatchCodec<P, E>`](../src/nac/java/com/gtlmodular/common/thread/BatchCodec.java) | 纯 Java 存档记录和 payload 编解码；记录中的 duration、progress 独立保存，payload 快照由 codec 负责 |
| [`GtRecipeBatchPersistence`](../src/nac/java/com/gtlmodular/common/thread/gt/GtRecipeBatchPersistence.java) / [`BatchNbtKeys`](../src/nac/java/com/gtlmodular/common/thread/gt/BatchNbtKeys.java) | 使用普通 GTRecipe CODEC 的 NBT 适配器；调用方提供全部键、兜底 ID 和诊断回调 |

`common.thread` 的纯 Java 核心不依赖 MC、GTCEu、gtladditions 或 NAC；`common.thread.gt` 依赖 MC、GTCEu 和 gtlcore，不依赖 NAC。两者均随共享源码进入两份 jar。完整版的机器适配器应放在 `main`，通过 `main → nac` 使用这些接口；独立版入口保持 `nacmod → nac`。共享代码不能反向引用产品入口、产品配置或样板总成，独立版的机器功能仍只有 NAC。

[`NanochipConcurrentBatches`](../src/nac/java/com/gtlmodular/common/machine/nanochip/NanochipConcurrentBatches.java) 现在是保留旧类型和方法的 NAC 门面，包含 NAC 存档键、产物上下文和日志。其他机器直接使用上述通用接口，不继承这个门面，也不复制它。NAC 的候选、并行、节流和能源策略仍在 `NanochipThreadedRecipeLogic` 等 NAC 类中，运行期概率缓存、预留和编号由 `NanochipBatchRuntime` 按 GTRecipe 对象身份管理。

## 各上游逻辑族的接入位置

以下名称以 `libs/[Custom-亚空间]gtladditions-3.3.1Custom_SubSpace.jar` 的实际实现为准。逻辑类位于 `com.gtladd.gtladditions.api.machine.logic`。更新依赖后先运行 `./gradlew decompileDeps --offline`，重新核对 `deps-src/`；任务会跳过已存在的目录，同名 jar 被替换时须先删除对应的旧反编译目录。反编译失败的方法用实际 jar 的 `javap -c -p` 查看。

机器作者可在类型兼容的逻辑子类中组合引擎；本仓库的通用族适配保留原逻辑对象，通过 main 访问器调用其 protected 方法。接管独立批次的 tick、开始和完成路径后，不能同时执行父类的合并开批或单配方推进；仍须保留原机器的可用性、暂停、唤醒、状态同步和后处理。

| 逻辑族 | 未合并候选的入口 | 适配时保留的差异 |
| --- | --- | --- |
| 普通 `GTLAddMultipleRecipesLogic` | 子类中的 `lookupRecipeIterator()`，沿用虚调用的 `checkRecipe()` | 单配方表查找和配方锁；条件、EU 等级和 `recipeCheck`；`checkBeforeWorking()` 的 proxies、电压及构造器 `beforeWorking` 谓词 |
| 多类型 `GTLAddMultipleTypeRecipesLogic` | 其覆写的 `lookupRecipeIterator()` | 遍历 `machine.getRecipeTypes()`，锁配方仍须校验；不能改成只查当前一张表。其余仍继承普通族的合并路径，需要显式接管 |
| 无线 `GTLAddMultipleWirelessRecipesLogic` / `GTLAddMultipleTypeWirelessRecipesLogic` | 各自的 `lookupRecipeIterator()`，保留无线版本 `checkRecipe()` / `checkBeforeWorking()` | 在线 handler、条件及构造器谓词；无线版本没有普通族的 EU 等级闸，不能擅自加回。多类型无线类另覆写 `buildWirelessRecipe()` 传入机器配方类型，新适配也要保留对应配方类型语义 |
| `MutableRecipesLogic<T>` | 子类中的 `lookupRecipeSet()`，沿用 `checkRecipe()` | 直接继承 `RecipeLogic`，不是普通族的子类。`findAndHandleRecipe()`、`handleRecipeWorking()`、`onRecipeFinish()` 按 `useMultipleRecipes` 分支；保留单配方分支、锁配方、`recipeCheck`、维护和能耗/时长倍率，以及具体子类的钩子 |

这些 lookup 入口返回的仍是未扣料候选；准备批次时要建立各批次独有的配方对象，避免修改注册配方或让概率缓存、预留跨批次共享。候选筛选和轮转由机器适配器负责，引擎不规定公平策略、同配方能否多开或每 tick 新开条数。

不能用 `getGTRecipe()` 的返回值喂给引擎：普通族经 `calculateParallels()`、`buildFinalNormalRecipe()` / `buildFinalWirelessRecipe()` 已经处理概率、真实扣料并合并产出，再由引擎扣一次会重复扣料。Mutable 族的对应入口叫 `getRecipe()`；更早的 `calculateParallels()` 就会调用 `RecipeCalculationHelper.calculateParallelsWithGreedyAllocation()`，后者实际执行 `handleRecipeInput()`，也不能当只读试算调用。具体子类覆写可能增加副作用，不能只凭方法名字判断。

类型兼容同样必须逐机核实。下列机器的 `getRecipeLogic()` 都硬转到具体逻辑族：

| 机器类（位于 `api.machine` 下） | 返回类型 |
| --- | --- |
| `multiblock.GTLAddWorkableElectricMultipleRecipesMachine` | `GTLAddMultipleRecipesLogic` |
| `wireless.GTLAddWirelessWorkableElectricMultipleRecipesMachine` | `GTLAddMultipleWirelessRecipesLogic` |
| `wireless.GTLAddWirelessWorkableElectricMultipleTypeRecipesMachine` | `GTLAddMultipleTypeWirelessRecipesLogic` |
| `mutable.MutableElectricMultiblockMachine` / `MutableCoilElectricMultiblockMachine` | `MutableRecipesLogic<对应机器>` |

某些机器还有更具体的类型，例如 `AddMutableElectricParallelHatchMultiblockMachine` 返回 `AddMutableRecipesLogic`。用任意一个通用 `RecipeLogic` 替换它们会破坏硬转和子类行为。线程仓连接 Mutable 机器时还会调用 `setUseMultipleRecipes(true)`，机器失效时有复位为 false 的路径；独立批次的跨模式处理必须一并设计。

## 线程数、并行、时长和能源

`BatchPolicy.slots()` 取原逻辑的 **虚调用** `getMultipleThreads()`，保留具体子类覆写，不在适配器里重算默认公式：

| 上游默认实现 | 实际规则 |
| --- | --- |
| `GTLAddMultipleRecipesLogic.getMultipleThreads()`，包括继承它的多类型和无线族 | `Ints.saturatedCast(128L + machine.getAdditionalThread())`；128 是基线，不是独立批次数上限 |
| `MutableRecipesLogic.getMultipleThreads()` | `getAdditionalThread() > 0` 时取追加值，否则取 1 |
| `IThreadModifierMachine.getAdditionalThread()` | 当前线程仓的 `getThreadCount()`，无仓为 0 |
| `ThreadPartMachine` 内部计数 | `Ints.saturatedCast(星阵物品数 * 64L * threadMultiplier)`，乘数由机器定义对应的 `ThreadMultiplierStrategy` 提供 |

星阵数量已经进入追加线程数，不能再乘一次。NAC 主机自己的电压基线、满并行策略和 256 上限不适用于这些机器。通用引擎只把槽数下限夹到 1，没有 256 上限；槽数缩小时已有批次全部继续运行，数量低于新上限才补位。

### 巨构级并行与单批安全阀

- 机器实现 `LongParallelMachine` 时，适配器优先读 `getMaxParallelLong()`，否则退回 `getMaxParallel()`；总预算 `totalBudget = longLimit × threads` 用 long 饱和，不截到 int、不回绕。
- 每轮候选的 `p` 还要过单批安全阀：`p ≤ min(输入可容纳, 输出可容纳, maxBatchRuns, Long.MAX / 单份最大物料量, power × maxBatchTicks / 单份总 EU)`。前两项仍由真实匹配决定，后三项只防"一批跑几年 / 内容乘爆 / 固定份数过大"。
- `independentThreadsMaxBatchTicks` 默认 10000（约 8 分钟），0 表示不限制；`independentThreadsMaxBatchRuns` 默认 0（不限制）。它们是安全阀，不是吞吐上限：长期配方会自动缩批，短配方仍可开很大。
- 候选内容用 `IAdvancedContentModifier.preciseMultiplier` 做 long 精确放大，并按原配方逐项校正数量；超出 `Long.MAX` 的批直接拒绝，不静默饱和。EU/t 不在物料倍率里重复放大，仍由能量层重新计算。
- `independentThreadsAllowMeIo` 默认 false。开启且机器装了 ME 样板/输出处理器时，非 tick 的物品/流体走上游 ME 的 long 计数路径；模拟通过才真实转移，剩余量写回同一张 Content 表。ME 的 tick 物品/流体尚未适配，遇到会拒绝该批；普通总线机器不受影响。
- 候选按“最久未服务”轮转，不再从 lookup 的哈希顺序队首截断；`independentThreadsMaxBatchesPerRecipe` 默认 1（一种配方最多占一条批次）。轮转周期约 `ceil(配方种数 / 槽数) × 单批时长`；单批时长仍由 `independentThreadsMaxBatchTicks` 控制。
- **线程与配方亲和**（地心模块）：补位时先在候选队列里挑「没在跑」的配方，刚结算的配方排最前（优先回到自己那条线程），正在跑的排最后。一种矿石的多种电路处理（例如天基矿石处理的 4 种，各要一张不同的编程电路）因此稳定地各占一条线程、各用自己的电路，而不是每轮换一遍。只有队列里全是正在跑的配方时，才允许同一配方再占一条（此时写一行「同配方补位」日志）。这条规则不改变并行/供电/时长计算，只改补位顺序；其它逻辑族的适配器不实现 `isRunning`，行为不变。
- `independentThreadsCandidateCacheTicks` 默认 100：候选 lookup 结果短缓存，降低每 tick 重查成本；0 关闭。真实扣料/配置改变会主动失效；外部输入变化没有统一 revision，最多延迟 TTL，但真实扣料仍逐条校验，不会凭空开工。
- `independentThreadsCandidateScanBudget` 默认 0 = 每轮最多 16 条候选：限制每 tick 试算的候选条数，调低压 TPS 峰值，调高让补位更充分。
- `independentThreadsStats` 默认 false：开启后机器面板显示候选扫描和批次 tick 的平均/最大耗时，用于调 active 线程数和候选预算。
- 安全阀只在"新批准入"阶段使用；已扣料批次继续按原时长和进度运行，不重算、不裁剪。
- **长批次**（`independentThreadsLongBatchTicks`，默认 200，0 = 关闭）：一条批次合并 N 次配方，`N = 目标时长 ÷ 当前单批时长`，再被输入库存夹住（`getMaxParallel` 按目标份数问一次真实库存，缩到 1 就是原行为）。开批一次扣 `p×N`、每 tick 仍只按 `p` 付电、结算一次出 `p×N`；总耗电、总产出、总时长都按 N 倍记账，所以单位时间产出与功率不变，只是开批/结算次数降到 1/N。实现对齐山海的 `DShanhaiBatchLogic.batch`（输入/输出/时长整体×N，EUt 恢复原值）。
  - 只接在聚合族（地心炼矿模块）的 normal 通道上；无线族与非该模块的候选仍是 N=1，避免在"输入已在聚合时扣过"的路径上再乘一次产出。
  - 概率产物按 `p×N` 份一起抽一次（与现在按 p 并行抽签是同一段上游代码），期望值等价、方差不同于逐份抽。
  - 存档只在新批次写 `runs` 键，旧档按 N=1 读；`runs > 1` 时面板与 Jade 显示「本批 N 次配方，第 k 次」。
  - GameTest 固定按 N=1 运行（用例断言的是 20 tick、精确产物数量的旧口径）；长批次的实际时长与产量对账在游戏内验收。
- 地心炼矿模块（`gtlmodular:molten_space_ore_module`）是第一个 `MultipleRecipesLogic` 专用适配目标：引擎用 `getRecipeType().getLookup()` 只读枚举候选，不调用会真实扣料的 `getRecipe()`；模块实现 `IndependentEnergyMultiplier`，主机线圈折扣后的 EU 在引擎 tick 前按比例缩放，实际扣电仍走 `MoltenModuleHostMachine.requestEnergy`。旧 `MultipleRecipesLogic` 合批路径保留给未开启的存档。启用方式：总开关开启 + 白名单加入该机器 ID；ME 进出料还需 `independentThreadsAllowMeIo`。

已确认的跨 tick 规则：总并行预算沿用 `maxParallel × getMultipleThreads()`（`maxParallel` 允许来自 `getMaxParallelLong()`，乘积用 long 饱和）。运行和待结算批次继续占用额度，实际出料结清才释放；补位仅使用剩余量。线程缩减时不删除批次，存量超额期间停止补位。普通族按上游均分后再分配未满足需求的算法，余量不足一轮时保留尾数；Mutable 按查找顺序分配，`LongLongPair.firstLong()` 是实际并行，`secondLong()` 是预算扣减，二者不能互换。

例如 `maxParallel=100`、线程数 2，A/B 各占 100：A 完成但堵输出时仍无剩余额度，A 实际结清后才能使用释放的 100。此时降到 1 线程，B 的 100 占满新总额，继续等待它结清，不裁剪旧批次。

同配方类型＋同配方 ID 默认允许同时存在多批，共用总预算；`independentThreadsMaxBatchesPerRecipe` 大于 0 时按该上限过滤候选（已扣料批次不受影响）。每批沿用原族单条配方的并行算法和上限；上游单候选允许使用整个可用预算，不能另加 `maxParallel` 单批硬上限，也不能每批重新领取完整总额。NAC 的 `FAN_OUT_SAME_RECIPE` 和每配方满并行规则不适用于本功能。

已确认整机功率也必须共用：同时运行的批次每 tick 总耗电不超过上游一台机器按相同电压、电流合批时的耗电，总吞吐与上游一致，不能是批次数倍。无线和电网沿用上游的功率计算方式，批次之间同样共用。

普通族先逐配方算 `getTotalEuOfRecipe(origin) * p * getEuMultiplier()`，再按上游顺序相加；Mutable 仍用自己的 `getRecipeEut(origin) * duration * p * getEuMultiplier()`。同轮实际接纳的新批次使用这个总能量计算公共时长：`roundToInt(max(totalEu / availablePower, limitedDuration))`；超过下限时整组 EU/t 取可用功率，否则取 `(long) (totalEu / limitedDuration)`。普通族下限虚调用 `getLimitedDuration()`，Mutable 为 20，维护及能耗/时长折扣仍来自原 getter。整组 EU/t 按各批总能耗比例分配，按候选顺序分配整数尾数，合计严格等于上游合批值；只运行一批时保持原公式。各批仍独立持有进度、概率结果和产物。

新批次的可用功率为当前 `getOverclockVoltage()` 减去未完成运行批次已占用的 EU/t；待出料批次不占加工功率。候选查找阶段不扣料，最终时钟在本轮扣料结束、首次 tick IO 之前确定，只计真实接纳的候选，避免输入重叠或容量检查失败的候选拖慢其他批次。已经运行的批次、读档批次不重算配方或进度。每个服务端 tick 另外限制能源仓实际扣电合计不超过一台机器的 `getOverclockVoltage()`，缓存里有更多电也不能超额。旧存档多批各占满功率或降压后，按现有批次顺序付电；单批一 tick 的电量超过当前额度时分多次真实扣取，余量仍写入原有 `TICK_INPUT`，整 tick 的 IO 付清才推进进度。没有电量承诺记账，也没有新增存档键。

无线账户和无线终端复用上游无线构建器：先按原乘法顺序用 BigDecimal/BigInteger 计算各批总能量，再将合计交给 `buildWirelessRecipe()`。当前支持的上游族固定使用最低时长，整机每 tick 的有符号电量为 `-totalEu / limitedDuration`，没有有线电压上限；不能因账户机器 `getOverclockVoltage()` 为零而停机，也不能把每批当作一台满功率机器。合批电量取整后再分配给各批，避免重复丢弃除法尾数，保留耗电/发电符号和多类型无线的配方类型。每 tick 共享开始时读取的实际账户/终端可用额度，仍逐笔调用原 handler 的 `consumeEnergy`，失败就等待；已支付标记沿用原存档契约，不预存整批。

例如两份各需 10000 EU 的有线批次，机器功率 100 EU/t、最低 20 tick：两批均为 200 tick、各 50 EU/t，合计 100 EU/t、20000 EU，与上游合批相同。若本轮只有一份实际扣料成功，则它恢复为 100 tick、100 EU/t，不被落空的另一份占住功率。无线同样两份、最低 20 tick 时，合计 1000 EU/t，每批 500 EU/t，继续使用无线的上游时长。


## 完整版候选组件的使用边界

`IndependentRecipeAdapters.select()` 返回族适配器或具体不支持原因，调用方须先通过配置准入。它仅判断候选计算能力，不能替代 S5 的 handler/持久化准入或 S6 的调度器互斥。适配器内部按配方 ID 的“最久未服务”顺序轮转，并可按 `independentThreadsMaxBatchesPerRecipe` 过滤活跃配方；`candidates(availableSlots, availableBudget, availablePower)` 接受会话给出的剩余并行预算和有线功率，返回一轮预分配结果；每条成功开工前仍须重新校验真实输入，由 `IndependentBatchSession` 的 `BatchBudgetLedger` 预留预算。调用方不能把候选列表当成库存预留，也不能脱离会话另算一份总预算。默认 `independentThreadsMaxBatchesPerRecipe=0` 时下一轮仍可再次查到同配方，不按运行中的配方 ID 去重。

候选的 `ingredients` 是独占的原类型缩放快照，保留 ID、条件和未抽取的概率内容；`execution` 保存独立时长、能耗及上游无线构建类型（多类型无线使用机器当前类型，其余标准无线使用上游默认类型）。两者不得在 S5 再叠加 `fullModifyRecipe()`。概率抽取须使用原类型语义，真实扣料前须完成 S3 payload 的无损 prepare 校验。原配方子类、不可编码条件和 ingredient actions 在扣料前拒绝，等待专用适配。

| 族 | 继续虚调用的入口 | 不调用的合并入口 |
| --- | --- | --- |
| 普通、多类型、无线 | `lookupRecipeIterator`（内部虚调用 `checkRecipe`，包含条件、线圈等 `recipeCheck` 和锁配方）、`checkBeforeWorking`（包含宿主等 `beforeWorking`）、`getMultipleThreads`、`getMaxParallel`、能耗/倍率 getter；标准无线 `buildWirelessRecipe` | `getGTRecipe`、`calculateParallels`、`buildFinalNormalRecipe`、`buildFinalWirelessRecipe` |
| Mutable | `lookupRecipeSet` / `checkRecipe`、`checkBeforeWorking`、`calculateParallel`、线程数和倍率 getter；新批次先检查 `isMultipleRecipeMode()` | `getRecipe`、`calculateParallels`、greedy helper、两种 `buildFinal` |

白名单不会让专有合并覆写自动变成可拆分算法。标准类之外，适配器仅允许表内读/计算覆写；检测到额外生命周期、合并生成或其他未转发方法，先报告类名和方法名，再停止准入。`AddMutableRecipesLogic`、伪神之煅炉、终焉曲折量子波动矩阵等仍须专用单批策略，不能仅跳过其覆写。转发方法的无 IO 契约仍需对具体白名单子类核实；反射检查不能证明任意第三方方法没有副作用。

访问器仅登记在完整版 mixin JSON。候选阶段不抽概率、不扣物料、不调用机器工作钩子或无线 `consumeEnergy`；无线只检查原 handler 是否在线，不按余额预存整批能量。真实逐 tick 扣电和出料由 `GtIndependentBatchHost` 执行，`IndependentMachineController` 与 `OtherMachineRecipeLogicMixin` 负责运行接管。

无线 tick 必须走实际扣电路径。上游 `IWirelessRecipeLogic.handleWirelessTickInput(IWirelessGTRecipe)` 读取 `getWirelessEuTickInputs()`，有数值时要求 handler 存在、在线且 `consumeEnergy(value)` 成功；无数值时返回 true。接入耗电配方必须先正确建立并恢复该数值，不能把“没有无线字段”当作供电充足。余额查询或开批准入检查不能代替真实扣电；每 tick 按需从账户或终端扣取，扣不到就等待。能源仓供电的缓存和准入由对应机器适配器决定，不能统一搬用 NAC 模块整批预蓄电策略。

## 策略与生命周期的连接方式

其他机器的批次所有权使用世界级 `data/gtlmodular_independent_ownership.dat` 账本，跨维度共用。沿用物品已有的 `gtlmodular_independent_transfer` UUID 键，并将它随方块批次保存；原批次根键、各记录字段及暂停键不变。旧机器首次序列化时补齐令牌，旧物品令牌首次接管时登记，完成接管后原令牌永久失效。批次存档内容改变时换发令牌，防止旧快照重新交付已产出的物品。备份和恢复应包含完整世界及这份账本。

物品放置先保留记录，待 Forge 放置捕获/回滚结束后才确认所有权并清除创造模式手持物品的批次数据。快照捕获、回滚和方块移动时不生成额外的批次控制器；普通拆机继续保留已扣料批次。`/clone` 普通复制保留源机器的批次、拒绝目标副本，`move` 和重叠 `force` 只允许一处接管；覆盖不属于源快照的其他机器时仍保护被覆盖机器的批次。当前 BG 1.0.8 先保存方块实体 NBT、移除实体，再替换方块，因此沿用它的搬运路径，不另发掉落物；重复粘贴同一份快照不能再次兑现。检查搬运源时会读取源区块，不能把区块未加载误判成源机器已移除；源维度不可用时保留记录等待。

输入准备、运行条件和 IO 支持性检查失败时显示原因并等待，条件恢复后自动继续；未真正扣料的候选不保留为 `INPUT_UNCERTAIN`。实际转移或工作钩子抛出结果不明的异常时，故障批次保留已知余量、进度和预算，停止自动重放；其他正常的已开工批次仍可继续，未处理的故障或原文记录会阻止补位。

OP（权限等级 2）可在机器所在维度使用以下命令，跨维度可配合 `/execute in`：

- `/gtlmodular_threads <x> <y> <z> status`：查看等待原因、故障阶段和恢复令牌。令牌只对应当前加载的记录，重载或已执行重试后需重新查询。
- `/gtlmodular_threads <x> <y> <z> retry <token> confirm_remainders`：管理员先核实已保存余量与实际库存/扣电结果相符，再解除该批次的故障锁定；保留已付 tick、概率结果、输出余量和收尾阶段，不重新开批。输入扣料不明、收尾钩子未返回及无法解码的记录不能使用此命令。
- `/gtlmodular_threads <x> <y> <z> settle <token> confirm_settled`：管理员完成外部核账及必要补偿后，确认移除对应故障批次或无法解码的原文记录并释放其占用。命令本身不返料、不补发产物、不重放钩子，不可用它自动“算作已足额扣料”。

两类修改均记录操作者、维度、坐标、令牌及批次/保留记录，并标记机器需要保存。恢复令牌仅驻留内存，不改变现有 NBT 格式。真实 ME 仓更换、真实存档重载和命令权限仍须游戏验收；纯 Java 回归仅验证会话与策略契约。

下面是连接形状的伪代码。`MachineBatch` 是机器作者提供的 `BatchState<GTRecipe>` 实现，构造器接收 `(payload, duration, progress)`；`adapter` 的方法名只表示每机需要实现的职责，并非仓库新增 API。`this` 表示保留上游逻辑类型的机器子类。

```java
IntSupplier originalThreads = this::getMultipleThreads;
ConcurrentBatchEngine<GTRecipe, MachineBatch> engine =
        new ConcurrentBatchEngine<>(MachineBatch::new);

BatchPolicy<GTRecipe> policy = new BatchPolicy<>() {
    public int slots() { return originalThreads.getAsInt(); }
    public GTRecipe findBatch() { return adapter.prepareUnconsumedCandidate(); }
    public int duration(GTRecipe recipe) { return recipe.duration; }
    public boolean consumeInputs(GTRecipe recipe) {
        return adapter.startAndConsumeOnce(recipe);
    }
    public boolean tickInputs(GTRecipe recipe) {
        return adapter.checkConditionsAndExecuteTickIO(recipe);
    }
    public void finishOutputs(GTRecipe recipe) {
        tryFinishOutputs(recipe);
    }
    public boolean tryFinishOutputs(GTRecipe recipe) {
        return adapter.tryOutputThenFinishOnceAndReleaseRuntimeState(recipe);
    }
};

boolean working = engine.tick(policy);
```

`engine.tick(policy)` 应放在机器子类已处理可用性、工作许可和暂停的 tick 路径中；上面的片段不包含完整 `serverTick()`。各职责要落实为：

- **准备**：按对应逻辑族查找未扣料候选，保留锁配方、条件、`recipeCheck`、`checkBeforeWorking()` 及具体子类修改器，完成经确认的并行和能源试算。返回的配方必须独立且未被合并流程扣料。
- **开批扣料**：保留原机开工钩子；区分构造器传入的 `beforeWorking` 谓词和 `machine.beforeWorking(recipe)`。适用 GTCEu 开工流程时，后者通过后执行 `recipe.preWorking(machine)` 和实际输入处理，成功后才建立附注。引擎只根据 boolean 决定入表，不提供跨 handler 的事务回滚。
- **tick IO**：逐批检查条件并执行其能源及其他 tick IN / OUT，只有允许推进才返回 true。概率缓存和预留按批次隔离。机器级 `onWorking()` 的调用次数、时机和否决处理由适配器按原机生命周期维护，不能每条批次重复执行机器级副作用。
- **完成**：保留对应机器的 `afterWorking`、配方 `postWorking`、真实出料和专有后处理，最后只释放这一批的附注；具体钩子的有无、顺序按原实现核对。不要调用会使用共享 `lastRecipe` 并立即再次合并开批的父类完成入口。
- **状态和唤醒**：维护 WORKING / WAITING / IDLE、暂停、输入唤醒、计时、Jade / UI 镜像、区块加载与卸载；单配方字段的展示镜像不能再被父类当作另一份待结算配方。

内核按表序对未完成批次尝试 tick，成功才加进度；进度达到时长后调用 `tryFinishOutputs()`，仅返回 true 才移除批次。返回 false 时保留完成进度和线程槽，下一 tick 只重试输出，不再调用 `tickInputs()`；读档恢复的完成批次也遵守此规则。遍历完才补位；候选为空或扣料失败即停止本 tick 补位，新批次下一 tick 才推进。`tick()` 的返回值只代表已有批次本 tick 有推进，不能拿 false 判定整机没有在跑或没有待处理批次。

`clear()` 只清表，不退款、不调用完成回调、不清理适配器的附注；暂停不能随手调用它。兼容入口 `finishOutputs()` 由默认的 `tryFinishOutputs()` 调用并视为成功；需要等待出料的适配器必须覆盖 boolean 入口。内核保留批次，但不回滚 handler 的实际输出；适配器须自行保存概率结果与未交付余量，返回 false 后不能重放已交付部分，完成钩子与附注释放只能执行一次。NAC 主机和模块共用 `NanochipRecipeOutputs`，在原配方输出中保存确定的待交付内容，沿用配方 CODEC 和既有校准来源数据；没有额外的待输出 NBT 键。

## 存档适配的范围

纯 Java 路径使用 `engine.save(codec)` / `engine.load(archive, codec)`；`BatchCodec.decode()` 返回 `Optional.empty()` 可跳过无法恢复的记录。`BatchArchive` 固定记录列表及 duration/progress，payload 的深拷贝或不可变编码由 codec 实现，不能把运行中的可变配方引用当作已保存快照。load 先清表，不重新扣料，也不按当前槽数裁剪。

普通 GTRecipe 的 NBT 路径可使用 GT 存档适配器。以下 `keys`、`fallbackId`、`diagnostics` 由机器适配器提供，类型分别为 `BatchNbtKeys`、`IntFunction<ResourceLocation>`、`GtRecipeBatchPersistence.Diagnostics`：

```java
GtRecipeBatchPersistence persistence =
        new GtRecipeBatchPersistence(keys, fallbackId, diagnostics);
persistence.save(tag, engine);

engine.clear();
persistence.load(tag, MachineBatch::new, engine::restoreBatch);
```

`BatchNbtKeys` 的参数顺序是 `(batches, recipe, id, progress, runs)`；根键及四个条目键都由调用方指定。load 不自行清空接收方，因此调用方须先清表及适当重建自己的运行期附注；`restoreBatch()` 是追加已恢复批次的接口，不走准入流程。

这个适配器只保证现有 NAC 使用面：

- 空表也写 ListTag；每条保存 CODEC 配方、ID 字符串、int 进度和 long 运行份数。时长从 `recipe.duration` 恢复并夹到至少 1，外层没有 duration 或版本键；使用它时批次固定时长须与配方时长保持一致。
- 配方本体缺失或 CODEC 解析失败跳过该条；ID 需要补回且存档值无效时，调用方按原始条目位置生成兜底 ID。进度原值读回；缺失或非正的 runs 不覆盖扩展默认值。
- 不替代父类的持久化，不管理 `lastRecipe`、状态、`chance_cache` 或每机扩展。`forDrop` 由外层决定；NAC 当前先调用父类保存，掉落物不写独立批次表。

上游 `mixin/gtceu/recipe/GTRecipePayloadMixin` 包装的单配方存档另外保存 `id`、`realParallels`、`batchSize`、`ocTier` 和 `wirelessEut`，读取时按无线字段恢复 `WirelessGTRecipe`。直接调用普通 `GTRecipeSerializer.CODEC` 不会经过这个包装；`GtRecipeBatchPersistence` 只额外处理 ID / runs，不能完整恢复无线数值、配方子类和其他扩展状态。无线或依赖这些扩展的机器必须另写对应的 payload codec / NBT 适配器，并验证真实旧档与运行恢复。

其他机器应定义自己的存档契约，不能占用 NAC 的 `gtlmodular_nanochip_threads` 根键。NAC 保留旧格式、旧类全限定名、字段和键，不因通用化发生迁移；两产物使用的 NAC 旧根键也不会随注册空间改名。

## 还没解决的问题

- 太空电梯这类机器自己覆写的特殊逻辑（工作钩子）会被独立批次绕过。
- 开关打开后，空闲机器也会占用调度时间，机器多时拖慢 TPS。
- 真实 15×31×11 炼矿模块外壳、ME 输出、真实 MK7 动力模块已经进入 GameTest；ME 网络自动连接复用 AE 的 `MEIOPartMachine`，引擎不再维护重连事件；ME tick 物品/流体仍未覆盖。
- 输出总成直接交付已验证到 2^63-1（两个 key 的 Int128 聚合）；引擎配方链路验证到 1,000,000，超过 long 的引擎真实产物仍未验证。

炼矿模块的 GameTest 已从“只验证所有权”扩展为完整加工链：测试主机 + 真实 15×31×11 外壳成形、双向连接、独立控制器、引擎开批、创造能源仓供能、物品/流体总线、输出总成接高速无限磁盘写入 AE，以及两条不同配方并行完成并把泥土/沙子都送入 ME；真实外壳还预放 MK7 动力模块并断言线程数随之提高；ME 网络自动连接复用 AE 的 `MEIOPartMachine`，不再由引擎维护重连事件；运行中双批已通过控制器 NBT save/load 恢复并继续 ME 输出，完整世界重启仍未覆盖。夹具使用 `GameTestHooks` 集中测试接缝，配方按幂等 ID 共享，避免同输入配方跨用例冲突。

接入新机器或改动以上行为时，还要逐机核对无线扩展、具体类型强转、Mutable 切模式与子类钩子，以及 gtladditions 的 `mixin/gtlcore/machine/trait/MultipleRecipesLogicMixin` 对合批和无线工作的覆写。一个基类注入覆盖不了所有逻辑族。新增 mixin 只放在完整版，登记到 `gtlmodular.mixins.json`，不能混进独立版。

每机接入需验证独立等待/完成、槽数变化、真实扣料和扣电、输出及概率结算、暂停与卸载、存档和关态恢复。共享核心单元测试只覆盖状态机与纯 Java 存档契约，不能代替真实 MC handler、Mixin 变换或游戏存档验证。
