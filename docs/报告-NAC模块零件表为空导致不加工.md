# 报告：NAC 模块「假装成形、完全不加工」（零件表为空）

## 一、现象

NAC 主机与模块的 tooltip 长期显示：

```
结构已成型
结构部件尚未就绪：加载所有结构区块，等待能源仓与样板总成重新连接。
```

同时**完全不加工配方**（有料也不开工）；拆掉主机、重进存档都无效。

同一条问题在日志里的读数（`logs/latest.log`）：

```
[gtlmodular] 自动切模式检查: 当前=0/2 状态=IDLE 找到目标=-1 样板=2 类型已知=[1=0 2=0]
             有料槽=[] 空转=20906tick 当前模式样板=2 其中有料=0 未认配方=2
[gtlmodular] NAC 独立线程候选诊断：原始枚举=0 条；通过资格闸=0 条；开出批次=0；
             输入侧[能力代理=有；样板仓接线=3；已缓存配方=0；接线重建=0次（余 32 次）]
```

`2147483647`（`Integer.MAX_VALUE`）**不是判据**，只是没有在跑批次时回落到
`FULL_PARALLEL_CAP` 的显示值（`NanochipHostDisplay`；常量定义在
`NanochipAssemblyComplexMachine.FULL_PARALLEL_CAP`）。

## 二、已定位的根因

**判据只有一条：`getParts()`（运行期零件表）为空。**

- 面板：`NanochipHostDisplay` 里 `host.isFormed() && !hasRegisteredParts`
- 判据实现：`NanochipLoadRecovery.hasRegisteredParts(host)`
  = `host.getParts() != null && !host.getParts().isEmpty()`
  ⇒ **与「能源仓是否 isActive」「样板总成是否绑定」「结构区块是否加载」都无关**，
  提示里的那两个名字只是机制描述。
- 不加工的直接原因：`NanochipThreadedRecipeLogic` 里
  `if (!machine.isRecipeLogicAvailable()) { … return; }`
  ⇒ **每个 tick 直接返回**；`isRecipeLogicAvailable() = isFormed && !hasError`，
  但配方搜索所需的是零件表，零件表为空时枚举必然 0 条。

**⇒ 结论：结构一旦报出「已成形」，就再也没有代码替它重跑一次匹配；而零件表是运行期快照，
读档 / 区块重载后没被填回来时，机器会永久停在「成形但零件表为空」。**

仓库里唯一能补齐它的入口 `NanochipLoadRecovery` 被写死关闭
（`NanochipAssemblyComplexMachine.LOAD_PARTS_RECOVERY_ENABLED = false`
⇒ `NanochipLoadRecovery` 第一行就 `return`）；**模块侧连这个类都没有**。

## 三、关键事实：这是重构引入的回归

**重构之前（旧的 `gtl-modular` 源码）模块加工是正常的。**
⇒ 说明这条路径在重构过程中被改坏了，而不是一直存在的设计缺陷。

**建议的下一步：把重构前的源码与现在的源码，在「零件表注册」这条路径上逐字节对比。**
可疑范围（按调用顺序）：

1. `NanochipModuleMachine`：`onLoad()` / `onStructureFormed()` / `onStructureInvalid()`
   —— 谁调用 `recordFormedParts` / `publishNacSlots`；
2. `NanochipModuleStructure`：`confirmFoldedPattern()`、`asyncCheckPattern()`、
   结构匹配回调是否仍然把零件表交给上游；
3. `NanochipSlotPublisher`：`part数` 读 `machine.getParts().size()`，
   `-1` 是 `resetPartCount()` 的哨兵；
4. 上游 `MultiblockControllerMachine.getParts()` 是否真在重匹配后被重建。

日志里可以用来判断「读档态」的旁证：
`NAC外壳连片：仓位清单心跳重发(模块) pos=… part数 -1→0 清单=空（0 条）`
（`-1` 说明 `isFormed` 已由存档恢复、而零件表还没重收）。

## 四、试过的修法（已回退，供后人不要重复）

**思路**：新增一个 `NanochipFormedPartsRecovery`，在「已成形 + 零件表为空 + 结构区块已加载」
时，每 40 tick 用仓库既有的 `NanochipPatternLockHardening.withLock` 走上游 `checkPattern()`
重跑一次完整匹配，最多 8 次。

**结果：回退了。** 加上它以后：

- 完整版 `runFullGameTest`：BUILD FAILED
- 独立版 `runNacGameTest`：日志报告 `FAIL:20`（改动前是 `PASS 28 / XFAIL 4 / XPASS 1 / FAIL 0`）

⇒ 说明「从外部周期性重跑匹配」这条路会扰动结构判定，**不要再用这个办法**。

（改动前两套基线：完整版 `PASS 41 / XFAIL 5 / FAIL 0`；独立版 `PASS 28 / XFAIL 4 / XPASS 1 / FAIL 0`。
任何后续改动都必须保持这两组数。）

## 五、已经修好、可以对照的两条（都属重构引入）

| 提交 | 问题 | 说明 |
|---|---|---|
| `a52bd8d` | 完整版进存档崩溃 | 复制方块补丁的 `m_187480_` 应为 `m_187482_`（remap=false 无 refmap ⇒ 正式环境 0 命中 ⇒ require=1 崩） |
| `60e261c` | 库存读数被当成天文批次 | 无限存储元件/真空网络给出的天文读数直接变成一批的份数（实测 `×8997930`）⇒ 单批并行加上限 |
| `e6c9906` | 输入接线缓存为空却跳过重建 | 接线重建闸门只看「样板仓接线数 > 0」⇒ `已缓存配方=0` 时永不重建 |

## 六、复现与判读点

**复现**：进入装了完整版的存档，看 NAC 主机/模块 tooltip 是否出现「结构部件尚未就绪」，
并且日志里 `自动切模式检查` 的 `有料槽=[]`、`找到目标=-1`。

**判读**：真正修好时应当看到

1. tooltip 那两行消失；
2. `自动切模式检查` 的 `有料槽` 非空、`找到目标 >= 0`、`空转` 归零；
3. `NAC 独立线程候选诊断` 的 `已缓存配方 > 0`、`原始枚举 > 0`、`开出批次 > 0`；
4. 机器 tooltip 出现真实并行数（而不是 `2147483647`）。