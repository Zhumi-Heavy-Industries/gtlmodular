# 其他机器如何接入独立批次引擎

只有 NAC 主机沿用现有独立批次功能，其他机器继续使用原逻辑。目前没有全局推广 mixin，也没有注册 `independentThreadsForOtherMachines` 配置键；两份配置中都没有可启用其他机器独立线程的开关。

这里的“线程”是服务器 tick 内按顺序推进的批次，各自保存配方和进度，不创建 Java 工作线程。接入其他机器必须逐机确定并行、时长、能源和退出规则，并由负责人确认会影响玩家手感的规则。

## 可复用接口与依赖方向

共享代码位于 `src/nac/java/com/gtlmodular/common/thread/`：

| 接口或类 | 负责什么 |
| --- | --- |
| [`ConcurrentBatchEngine<P, B>`](../src/nac/java/com/gtlmodular/common/thread/ConcurrentBatchEngine.java) | 有序批次表、推进、完成后补位、清空、存读档；`B extends BatchState<P>` |
| [`BatchState<P>`](../src/nac/java/com/gtlmodular/common/thread/BatchState.java) | `payload()`、`duration()`、`progress()`、`setProgress(int)`；`Factory.create(payload, duration, progress)` 创建同一份运行状态 |
| [`BatchPolicy<P>`](../src/nac/java/com/gtlmodular/common/thread/BatchPolicy.java) | `slots()`、`findBatch()`、`duration(P)`、`consumeInputs(P)`、`tickInputs(P)`、`finishOutputs(P)` |
| [`BatchArchive<E>`](../src/nac/java/com/gtlmodular/common/thread/BatchArchive.java) / [`BatchCodec<P, E>`](../src/nac/java/com/gtlmodular/common/thread/BatchCodec.java) | 纯 Java 存档记录和 payload 编解码；记录中的 duration、progress 独立保存，payload 快照由 codec 负责 |
| [`GtRecipeBatchPersistence`](../src/nac/java/com/gtlmodular/common/thread/gt/GtRecipeBatchPersistence.java) / [`BatchNbtKeys`](../src/nac/java/com/gtlmodular/common/thread/gt/BatchNbtKeys.java) | 使用普通 GTRecipe CODEC 的 NBT 适配器；调用方提供全部键、兜底 ID 和诊断回调 |

`common.thread` 的纯 Java 核心不依赖 MC、GTCEu、gtladditions 或 NAC；`common.thread.gt` 依赖 MC、GTCEu 和 gtlcore，不依赖 NAC。两者均随共享源码进入两份 jar。完整版的机器适配器应放在 `main`，通过 `main → nac` 使用这些接口；独立版入口保持 `nacmod → nac`。共享代码不能反向引用产品入口、产品配置或样板总成，独立版的机器功能仍只有 NAC。

[`NanochipConcurrentBatches`](../src/nac/java/com/gtlmodular/common/machine/nanochip/NanochipConcurrentBatches.java) 现在是保留旧类型和方法的 NAC 门面，包含 NAC 存档键、产物上下文和日志。其他机器直接使用上述通用接口，不继承这个门面，也不复制它。NAC 的候选、并行、节流和能源策略仍在 `NanochipThreadedRecipeLogic` 等 NAC 类中，运行期概率缓存、预留和编号由 `NanochipBatchRuntime` 按 GTRecipe 对象身份管理。

## 各上游逻辑族的接入位置

以下名称以 `libs/[Custom-亚空间]gtladditions-3.3.1Custom_SubSpace.jar` 的实际实现为准。逻辑类位于 `com.gtladd.gtladditions.api.machine.logic`。更新依赖后先运行 `./gradlew decompileDeps --offline`，重新核对 `deps-src/`；任务会跳过已存在的目录，同名 jar 被替换时须先删除对应的旧反编译目录。反编译失败的方法用实际 jar 的 `javap -c -p` 查看。

优先由机器作者在 `createRecipeLogic(...)` 返回类型兼容的子类，并在该子类内组合引擎。接管独立批次的 tick、开始和完成路径后，不能同时执行父类的合并开批或单配方推进；仍须保留原机器的可用性、暂停、唤醒、状态同步和后处理。

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

上游“线程数”还参与合并并行预算：普通族和 Mutable 族都用 `(long) maxParallel * getMultipleThreads()` 分配候选并行。直接改成每条批次各取整个预算会改变吞吐和总能源需求，不能作为默认接入策略。每机适配器必须另行提供并确认并行分配、修改器、输出容量和 tick IO 预留规则。

`RecipeCalculationHelper.buildNormalRecipe()` 的合并时长为 `roundToInt(max(totalEu / maxEUt, limitedDuration))`；无线合并时长直接取传入的 `limitedDuration`，每 tick 的无线数值由 `totalEu.divide(BigInteger.valueOf(limitedDuration)).negate()` 得到。普通族传入机器的 `getLimitedDuration()`，Mutable 基础实现传入 20。这些是合并规则，不能据此声称各候选已经按自己的原时长独立运行。拆成独立时钟后的时长、EU/t、整数舍入和发电/耗电符号须逐机确定。

无线 tick 必须走实际扣电路径。上游 `IWirelessRecipeLogic.handleWirelessTickInput(IWirelessGTRecipe)` 读取 `getWirelessEuTickInputs()`，有数值时要求 handler 存在、在线且 `consumeEnergy(value)` 成功；无数值时返回 true。接入耗电配方必须先正确建立并恢复该数值，不能把“没有无线字段”当作供电充足。余额查询或开批准入检查不能代替真实扣电；每 tick 按需从账户或终端扣取，扣不到就等待。能源仓供电的缓存和准入由对应机器适配器决定，不能统一搬用 NAC 模块整批预蓄电策略。

## 策略与生命周期的连接方式

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
        adapter.finishOnceAndReleaseRuntimeState(recipe);
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

内核按表序尝试 tick，成功才加进度；随后检查完成，先移除再调用 `finishOutputs()`。即使恢复的进度已达时长、这次 tick 返回 false，仍会进入完成处理。遍历完才补位；候选为空或扣料失败即停止本 tick 补位，新批次下一 tick 才推进。`tick()` 的返回值只代表已有批次本 tick 有推进，不能拿 false 判定整机没有在跑或没有待处理批次。

`clear()` 只清表，不退款、不调用完成回调、不清理适配器的附注；暂停不能随手调用它。内核在完成回调之前已经移除批次，不提供输出重试队列。适配器必须在准入、容量预留和完成处理中落实对应机器的物品守恒与异常处理，不能把这些能力当作引擎自带。

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

## 未来全局开关的前置条件

未来若另立全局接入任务，建议键名仍为 `independentThreadsForOtherMachines`，默认值必须为 `false`，只由 `main` 中的配置组件提供，在 `GTLModularConfig.SPEC` 的 `build()` 之前组合进 `gtlmodular-common.toml`。`NanochipConfig`、`nac`、`nacmod` 不读取这个键，`gtl_nac-common.toml` 不增加它。本期只记录建议，没有空实现或可用配置。

未来所有全局注入在关闭时都必须早返回，保留上游行为。但这还不能解决开过批次后的关态：上游只有一份合并 `lastRecipe` / progress，无法无损表示多个不同进度且已经扣料的批次。只镜像第 0 条会丢失其余条，还可能重复结算；关态继续恢复或排空自有批次又会改变上游关态行为。必须先解决运行中关闭、关服改配置再启动、存档再保存和回滚的规则，不能只加 `if (!enabled) return` 就宣称可逆。

还要逐机核对无线扩展、具体类型硬转、Mutable 切模式与子类钩子，以及 gtladditions 已有的 `mixin/gtlcore/machine/trait/MultipleRecipesLogicMixin` 对合并和无线工作的覆写。一个基类注入不能覆盖所有逻辑族。以后确需新增 mixin 时，仅放在完整版并登记到 `gtlmodular.mixins.json`，保持对应 TOML 和 manifest 声明；不能混入独立版。

每机接入需验证独立等待/完成、槽数变化、真实扣料和扣电、输出及概率结算、暂停与卸载、存档和关态恢复。共享核心单元测试只覆盖状态机与纯 Java 存档契约，不能代替真实 MC handler、Mixin 变换或游戏存档验证。
