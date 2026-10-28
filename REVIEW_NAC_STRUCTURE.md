# NAC 结构检查（评审用，对应游戏里 0.3.184）

这个分支的内容 = **玩家实际运行那条树**里的代码原样搬过来，路径沿用本仓库的 `src/nac/java/...`。
和 `main` 的 diff 就是「线上版本 vs 现在还在跑的那份」的全部差异，可以直接按文件看。

## 现在在查的问题

**模块拆掉一格后仍然显示成型**（主机拆块会失效，模块不会）。

## 已定位的根因

上游拆块事件走 `MultiblockWorldSavedData.mapping`（控制器坐标 → `MultiblockState`）：

- 主机有自建索引 `NanochipStructurePosIndex` 兜底，所以拆块 → 命中 → `MultiblockState.onBlockStateChanged` → 失效；
- 模块**从没进过那张 mapping 表**。它成形走「方块变化 → `checkPatternWithLock` → `onStructureFormed`」这条路时，上游不会自己 `addMapping`，
  于是 `ChunkMixin` 按区块查表查不到它，没有回调 ⇒ 一直显示成型。

## 本次改动

`NanochipModuleMachine.onStructureFormed()` 里补一句（幂等）：

```java
MultiblockWorldSavedData.getOrCreate(serverLevel).addMapping(state);
```

## 读的顺序

1. **模块**（出问题的一侧）
   - `src/nac/java/com/gtlmodular/common/machine/nanochip/NanochipModuleMachine.java`
     - `asyncCheckPattern`：只剩「super + 打未成形原因日志」，不参与判定
     - `onStructureFormed`：本次补 `addMapping` 的位置
2. **主机**
   - `src/nac/java/com/gtlmodular/common/machine/nanochip/NanochipAssemblyComplexMachine.java`
     - `asyncCheckPattern` / `checkPatternWithTryLock`：预检、变化闸、节流已全部摘掉，直接交上游
     - `noteCachedBlockRemoved` + `invalidateOnCachedRemoval`：主机自己的拆块待办消费
3. **结构定义（判据本身）**
   - `src/nac/java/com/gtlmodular/common/data/nanochip/NanochipComplexPattern.java`
     - `build(...)` 复合体图案 / `buildModule(...)` 模块图案
4. **自建索引（只登记主机）**
   - `src/nac/java/com/gtlmodular/common/machine/nanochip/NanochipStructurePosIndex.java`（`register` / `noteBlockRemoved`）
5. **拆块事件登记**
   - `src/nac/java/com/gtlmodular/common/event/NanochipBlockRemovalHandler.java`
6. **已停用、仅供对照**
   - `NanochipExactPrecheck.java`、`NanochipStructureCheckGate.java`：调用点已全摘，现在没有运行期作用
