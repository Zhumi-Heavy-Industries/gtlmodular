# 合并熔岩模块引擎与通用独立线程引擎

目标：让 `MoltenModuleMachineBase$MoltenModuleRecipeLogic` 不再自带一份「扫描/开批/IO」纪律，
改为复用通用引擎的 `BatchFinder` / `BatchIo` / `BatchDiagnostics` / `ConcurrentRecipeEngine`。
**不动电价与并行数学**（见下「刻意不合并」），生产数字必须逐字不变。

分支：`refactor/unify-batch-engine`，worktree `E:\大鲸鱼工作区\_worktrees\engine-merge`，
基于 `refactor/thread-hatch-package` @ 303b05f（0.3.486）。

## 为什么不直接把两边合成一个类（继承链证据）

| 事实 | 出处 |
|---|---|
| 通用引擎 `ThreadedRecipeLogic extends RecipeLogic` | engine/ThreadedRecipeLogic.java:74 |
| 熔岩模块必须 extends gtlcore `CoilWorkableElectricMultipleRecipesMachine` | MoltenModuleMachineBase.java:64 |
| 该基类 `createRecipeLogic()` 固定 `new MultipleRecipesLogic(this, EBF_CHECK)` | deps-src gtlcore `CoilWorkableElectricMultipleRecipesMachine.java:35` |
| 该基类 `getRecipeLogic()` 把返回值**强转**成 `MultipleRecipesLogic` | 同上:33 |
| 熔岩逻辑 `MoltenModuleRecipeLogic extends MultipleRecipesLogic` | MoltenModuleMachineBase.java:293 |

⇒ 熔岩模块的 `RecipeLogic` 必须是 `MultipleRecipesLogic` 的子类，而 `ThreadedRecipeLogic`
是它的**兄弟**。Java 单继承 ⇒ 两个逻辑壳必须各自保留；能合并的是它们**调用同一批引擎组件**。

## 合并什么（本次）

1. 新增 `engine/IBatchLogicOwner`：`BatchFinder`/`BatchIo` 目前硬依赖 `ThreadedRecipeLogic`
   具体类，只用到 4 个方法（`meCachedCandidates` / `hasRealItemOrFluidInput` / `modifyRecipe` /
   `chanceCaches`，见 BatchFinder:188/203/237、BatchIo:81）。抽成接口后两个逻辑壳都能实现。
2. `BatchFinder` 增加两个策略钩子：`acceptsCandidate(raw)`（熔岩要补层级/条件/EBF 温度判定）、
   `crossTableScan()`（熔岩按 `engineCrossTable()`，通用恒 true）。
3. `BatchFinder` 增加「每 tick 最多新开 8 条」限速（熔岩现有 `ENGINE_MAX_STARTS_PER_TICK = 8`，
   通用引擎没有 ⇒ 首次填满 256 槽会一次开几百条）。这是两边合并后**通用机也受益**的一处。
4. `BatchFinder` 增加并行/折算分流：
   - `usesStandardParallel() == true` → 现有 modifyRecipe + accurateParallel 路径（通用机，行为不变）
   - `== false` → `prepareBatch(raw)` 自备折算（熔岩机，保留 gtlcore 电价数学）
5. 熔岩逻辑壳：删掉 `ScanState` / `scanStates` / `lastTypeIndex` / 三个 `ENGINE_*` 常量 /
   `findBatchForType` / `isRunningElsewhere`（≈180 行），改为持有 `BatchFinder` + `BatchIo` + `BatchDiagnostics`，
   `findBatch()` 转发 `finder.findBatch()`，IO 转发 `io.*`。
6. `MoltenModuleMachineBase` 实现 `IThreadedRecipeMachine`（`baseThreads` / `parallelMultiplier`
   / `usesParallelHatch=false` / 已有 `threadHatchThreads` / `engineRequiresThreadHatch=true`），
   供 `BatchFinder` 读取线程仓与并行倍率。

## 刻意不合并（保护生产数字）

- **并行算法**：通用 `accurateParallel` vs 熔岩 `IParallelLogic.getMaxParallel`。
- **耗电模型**：通用走 `BatchIo.tickInputs` + 机器能源仓；熔岩把每 tick EU 烘进配方
  `tickInputs` 并累加 `host.consumeEU`。
- **时长公式**：通用用配方 duration；熔岩用 `max(totalEu/超频电压, 20)`。

若要连这三项也统一，是一次**数值变更**，需要单独拍板（见文末「待决」）。

## 验收

1. `mc_gradle build` 绿（本分支无 GameTest：`src` 下只有 `main`，自动化回归网为零）。
2. 通用引擎 8 台机器的行为不变（改动只走 `usesStandardParallel()==true` 分支 + 新增限速）。
3. 熔岩三个模块的批次仍能开、NBT 键 `gtlmodular_print_threads` 不变、Jade 逐条显示不变。
4. 版本号 0.3.486 → 0.3.487。

## 待决

**是否把熔岩的电价模型也统一到通用模型？**
熔岩的 `getEnergyContainer()` 已经代理到主机（`HostEnergyContainer`），电学上可以直接走
`BatchIo.tickInputs`；但并行算法与时长公式会变，产出/耗电数字会动。需要用户拍板后再做。
