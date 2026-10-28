# NAC 结构检查代码（评审用快照，0.3.183）

> 这份是**玩家实际运行那条树**（`E:\大鲸鱼工作区\gtl-modular`）的原样拷贝，只搬文件、没改内容。
> 目录结构在仓库里变了（原来在 `src/main/java/...`、`src/nac/...` 两处），这里放平方便直接看。
> 行号与源文件一致，可直接跳到对应行。

## 1. 成型判定入口

### 主机 `NanochipAssemblyComplexMachine.java`

| 行 | 方法 | 说明 |
|---|---|---|
| 287 | `asyncCheckPattern(long)` | 上游异步线程每轮调；未成形侧已改成直接 `super` |
| 349 | `checkPatternWithTryLock()` | 昂贵匹配前最后一道闸；已去掉精确预检，直接 `super` |
| 422 | `checkPatternWithLock()` | 方块变化事件走这里；仍保留「同 tick 折叠」 |
| 488 | `confirmFoldedPattern()` | 折叠的下一 tick 兑现点 |
| 6877 | `noteCachedBlockRemoved(BlockPos)` | 拆块待办入口（自建索引调用） |
| 6929 | `invalidateOnCachedRemoval()` | 消费拆块待办，调 `MultiblockState.onBlockStateChanged` |
| 7524 | `onStructureFormed()` | 成形回调 |
| 7642 | `onStructureInvalid()` | 失效回调 |

### 模块 `NanochipModuleMachine.java`

| 行 | 方法 | 说明 |
|---|---|---|
| 230 | `checkPatternWithTryLock()` | 已去掉精确预检，直接 `super` |
| 264 | `checkPatternWithLock()` | 已去掉同 tick 折叠，直接 `super` |
| 337 | `asyncCheckPattern(long)` | 已去掉变化闸/计数节流，直接 `super` |
| 1888 | `onStructureFormed()` | 成形回调；1905-1912 是本次新加的 `addMapping` 补登记 |
| 1940 | `onStructureInvalid()` | 失效回调 |

## 2. 真正的判据：结构定义

`NanochipComplexPattern.java`

- 2010 `build(...)` —— 复合体图案
- 2239 `buildModule(...)` —— 模块图案

## 3. 拆块 → 失效链

| 文件 | 行 | 说明 |
|---|---|---|
| `NanochipBlockRemovalHandler.java` | 82 / 113 | 挖方块 / 爆炸时登记坐标 |
| `NanochipStructurePosIndex.java` | 332 register / 376 unregister / 506 noteBlockRemoved / 578 structureChunksLoaded | 自建位置索引，**只登记主机** |

## 4. 已停用（只剩声明，无调用点）

`NanochipExactPrecheck.java`、`NanochipStructureCheckGate.java`（以及未拷贝的 `NanochipStructureCheckThrottle` / `NanochipStructurePrecheck`）。
0.3.180/181 把它们的调用都摘掉了，留着只为少改文件。

## 5. 正在查的问题

现象：**模块拆掉一格后仍然显示成型**。

已定位：上游 `ChunkMixin` 走的是 `MultiblockWorldSavedData.mapping`（控制器坐标 → `MultiblockState`）。
- 主机有自建索引兜底，所以拆块会失效；
- 模块从没进过这张表（成形走「方块变化 → `checkPatternWithLock` → `onStructureFormed`」这条路时，上游不会自己 `addMapping`），所以 `ChunkMixin` 按区块查表查不到它，没有回调。

本次改动：模块 `onStructureFormed()` 补 `MultiblockWorldSavedData.getOrCreate(level).addMapping(state)`（幂等）。
