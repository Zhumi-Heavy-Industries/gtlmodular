# 复核：NAC 读档后「已成形但零件表为空、不加工」

复核人：amireux
对应报告：[报告-NAC模块零件表为空导致不加工.md](报告-NAC模块零件表为空导致不加工.md)

> 这份和原报告一样是临时文件，合并进 `main` 之前两份都删掉。长期有用的结论已经写进 [开发经验.md](开发经验.md)。

## 一、结论

根因是我们自己的结构检查节流和上游的检查节奏对不上，**只在 `originalMode = false` 时出现**。猪咪包的这个配置这两天被改成了 `false`，默认值是 `true`。

- **不是重构引入的。** 0.3.132 里就是同一段代码、同一个默认值。
- **不需要新的"补零件表"机制。** 也不要从外部周期性重跑匹配。

## 二、证据

### 1. 上游读档后怎么填回零件表

`deps-src/gtceu-1.20.1-1.4.4/com/gregtechceu/gtceu/api/machine/multiblock/MultiblockControllerMachine.java`：

- `isFormed` 带 `@Persisted`，读档后直接恢复成 `true`；零件表 `parts` 不存档，读档后是空的。
- `asyncCheckPattern(periodID)` 在两个条件同时满足时做一次完整匹配，成功后由 `onStructureFormed()` 把零件表填回来：
  - `hasError() || !isFormed`；
  - `(getOffset() + periodID) % 4 == 0`。

`deps-src/.../api/pattern/MultiblockState.java`：

- 构造时 `error = UNINIT_ERROR`，所以读档后 `hasError()` 是 `true`。

**所以上游的设计是：** 读档后，第一次轮到相位对上的那一轮，就会重跑匹配、填回零件表。原报告说"已成形以后再没有代码重跑匹配"，这句不对。

还有两个事实：

- `MultiblockWorldSavedData` 每 250 ms 调一轮，每轮 `periodID + 1`。
- `getOffset()` 是方块实体创建时随机取的 0~19。它不存档，每次读档都会变。

### 2. 我们的节流把这一次挡住了

`NanochipModuleStructure.asyncCheckPattern` 和 `NanochipHostStructure.asyncCheckPattern` 里，已成形的结构要先过 `structureCheckThrottle.shouldRun(true)`，才交给上游。主机和模块都受影响。

`NanochipStructureCheckThrottle` 的行为：

- `originalMode = true`（默认）：直接放行，没问题。
- `originalMode = false`：已成形的结构每 6 次调用才放行 1 次（`FORMED_SKIP = 6`）。

放行的那几轮，`periodID` 每次相差 6。6 除以 4 余 2，所以 `(offset + periodID) % 4` 只会在两个值之间来回跳：

- 这两个值是 0 和 2：每 3 秒检查一次，正常。
- 这两个值是 1 和 3：**永远等不到 0**。上游永远不重跑匹配，零件表一直是空的。

每台机器每次读档，落到第二种情况的概率是一半。机器一多，每次读档几乎都有几台卡住。`offset` 每次读档重新随机，所以重进存档只是换一批机器卡住，看起来就像"重进也没用"。

### 3. 和现象对得上

- 日志里的 `part数 -1→0 清单=空`：`isFormed` 已经从存档恢复，零件表还没重收，和上面一致。
- 增强版 2.7 的 `originalMode` 是 `true`，所以没有这个问题。

## 三、怎么修

**第一步：先验证，不改代码。**

把猪咪包 `config\gtlmodular-common.toml` 的 `nanochipComplex.originalMode` 改回 `true`，重启后进存档。按原报告第六节的判读点看：tooltip 那两行消失、`有料槽` 非空、开出批次。

**第二步：修代码。**

1. 把 `NanochipStructureCheckThrottle.FORMED_SKIP` 从 6 改成 5，注释写原因：

   ```java
   /** 必须与上游 asyncCheckPattern 的 4 相位过滤互质，否则一部分机器的周期检查永远落不到相位上。 */
   public static final int FORMED_SKIP = 5;
   ```

   5 和 4 互质，放行的轮次会轮遍 4 个相位，最坏 20 轮（5 秒）内一定能检查到。

2. 在 `src/test/` 加一个单元测试锁住这个约束：`FORMED_SKIP` 和 4 的最大公约数必须是 1。以后谁把它改成偶数，构建就会失败。

3. 改完照规矩做：
   - `build` 通过；
   - 两套 GameTest 保持原报告第四节的基线：完整版 PASS 41 / XFAIL 5 / FAIL 0，独立版 PASS 28 / XFAIL 4 / XPASS 1 / FAIL 0；
   - `CHANGELOG.md` 的「未发布」写一句。

**不要做的：**

- 不要加外部周期重跑。原报告第四节已经试过，会把 GameTest 弄挂。
- 不要打开 `LOAD_PARTS_RECOVERY_ENABLED`，用不上。

## 四、分支上其他提交的复核

| 提交 | 结论 |
|---|---|
| `d8187e5` CC 名字判空气 | 对。 |
| `a52bd8d` 方法名改成 `m_187482_` | 对。最后只改名字、保留 `require` 是正确做法：名字对了就该找得到，找不到应该报错。 |
| `6159927` 撤回换配方 | 对。那次崩溃的真正原因是 gtlcore 的配置被重置，不是配方。 |
| `e6c9906` 配方缓存为空时也重建接线 | 可以收。有次数上限和冷却，不会空转。 |
| `60e261c` 单批并行上限 100 万 | **要队长确认**，见下面。 |

`60e261c` 有两个问题：

- **提交说明写错了。** 它说"全流程档的设计上限逐字不变"，但全流程档的上限是 `FULL_PARALLEL_CAP = Integer.MAX_VALUE`（约 21 亿），现在每批实际被压到 100 万。
- **这属于改并行。** 按 AGENTS.md「已确认的游戏规则」，改并行之前要先把规则写下来，让队长确认。

请队长定：要不要这个上限，上限多少。确定要的话，改正 CHANGELOG 和说明；不要就撤回。

## 五、下次查问题时

这次绕了弯路。以后按这个顺序查：

1. **先撤掉最近改过的东西。** 出问题前改过配置、换过 jar，先改回去试一次，再查代码。这次的 `originalMode` 就是当天改的，改回去一试就能定位。
2. **说"回归"之前，先对比旧版本。** `git grep -n FORMED_SKIP v0.3.132` 一秒钟就能看到旧版本里有没有同样的代码。没对比过，就只能写"怀疑"。
3. **存档恢复了什么、没恢复什么，去源码里看。** 先看字段有没有 `@Persisted`，再找读档后由谁把没存的东西重建出来。
4. **两层节流叠在一起时，算一下周期。** 两个周期不互质，就可能永远错开。
5. **提交说明只写核实过的事。** 要写"设计上限不变"这种话，先去查清那个上限是多少。
6. **改功率、时长、并行、扣电之前，先问队长。**
