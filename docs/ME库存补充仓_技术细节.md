# ME库存补充仓（gtlmodular:me_requester）技术细节

> 版本 `0.3.480` ｜ 源码 `D:/gtlmodular-recipe`（`D:/gtlmodular` 的 git worktree）
> 部署 `/mnt/d/gtlpack/mods/gtlmodular-0.3.480.jar`
> 整理日期 2026-11-23

---

## 0. 一句话定位

一个 EV 档的独立方块：**盯住 AE 网络库存，不足就自动下单合成，产物直接回网络**。
不依赖 merequester；可选接管 neoecoae（ECO）的计算子系统。

方块 id 仍是 `gtlmodular:me_requester`（**不能改**，见 §7.1）。

---

## 1. 文件清单（2694 行）

| 文件 | 行数 | 职责 |
|---|---|---|
| `RequesterMachine.java` | 498 | 方块实体：AE 节点、持久化字段、主界面 |
| `RequesterOrderEngine.java` | 491 | 下单状态机（串行 / 三重读数 / 宽限 / 冷却 / 自动缩单） |
| `RequesterSlotWidget.java` | 568 | 单格控件：双层格、JEI 拖拽、点击同步 |
| `RequesterSlot.java` | 260 | 单格数据模型（key/target/batch/stock）+ NBT |
| `RequesterAmountBox.java` | 243 | 「数量 / 并发」浮动面板（可拖动） |
| `RequesterSlotGrid.java` | 144 | 槽位网格容器（翻页只切可见性） |
| `RequesterSlots.java` | 135 | 槽位表容器（LDLib 托管 + 旧存档迁移） |
| `EcoPlanningBridge.java` | 134 | ECO 规划器的反射桥接（可选） |
| `RequesterNumbers.java` | 106 | 数字格式化/解析（科学计数、mB/B） |
| `RequesterNumberRow.java` | 72 | 数字输入行（默认批量页用） |
| `RequesterPageBar.java` | 43 | 翻页条 |

---

## 2. 方块与渲染

### 2.1 注册（`RequesterMachines`）

```java
REGISTRATE.machine("me_requester", RequesterMachine::new)
    .rotationState(RotationState.ALL)
    .tier(4)                                  // EV，与 GTCEu ME 总线同档
    .overlayTieredHullRenderer("me_requester")
    .langValue("ME库存补充仓")
```

- `overlayTieredHullRenderer` → `OverlayTieredMachineRenderer(tier, "gtlmodular:block/machine/part/me_requester")`
- 外壳贴图由 `TieredHullMachineRenderer` 按 tier 覆盖为 `gtceu:block/casings/voltage/ev/{top,side,bottom}`

### 2.2 正脸模型（内缩，不盖外壳边框）

`assets/gtlmodular/models/block/machine/part/me_requester.json`

```json
"parent": "block/block",
"elements": [{
  "from": [3, 3, 0], "to": [13, 13, 0.05],
  "faces": { "north": { "uv": [3, 3, 13, 13], "texture": "#overlay", "cullface": "north" } }
}]
```

| 项 | 值 | 理由 |
|---|---|---|
| 几何 | 3~13（10×10） | GTCEu 原始 `overlay/front` 是 -0.001~16.001 **铺满整面**，会盖掉 EV 外壳边框 |
| UV | `3,3 → 13,13` | 让贴图自带的边框与外框对齐，而非把 16×16 硬拉进 10×10 |
| 厚度 | `0 ~ 0.05` | 比 GTCEu 的 `0.002` 更厚一点，避免与外壳共面闪烁（z-fighting） |

### 2.3 贴图

`assets/gtlmodular/textures/block/machine/part/me_requester_front.png`
→ 16×16 单帧，取自资源包 `AE亮色UI-3.5fix` 的 `assets/merequester/textures/block/requester.png`。

> 原实现是 32×128 四帧动画（雪花图案）+ `.mcmeta`，已删除 mcmeta：
> 单帧贴图若保留 4 帧动画声明，UV 只会取到第一帧的 1/4，显示错乱。

---

## 3. 槽位布局

```
GUI 150 × 122
 ├─ 3,0    网络状态（LabelWidget，与网格左沿同 x）
 ├─ 3,10   网格 144×74
 │          8 列 × 2 行 = 16 格/页，共 4 页 = 64 格
 │          单格 18×36（上=配置 / 下=库存），列距 18、行距 38
 ├─ 3,85   翻页条 144×18（箭头贴两端，页码居中）
 └─ 3,112  阻塞提示
数量/并发面板：默认挂在 x=GUI_WIDTH（窗口右侧），点格子才出现，可拖动
```

常量（`RequesterMachine`）：

| 常量 | 值 |
|---|---|
| `ROWS` / `COLUMNS` | 2 / 8 |
| `PAGE_SIZE` / `PAGES` / `SLOT_COUNT` | 16 / 4 / **64** |
| `SLOT_SIZE` / `SLOT_HEIGHT` | 18 / 36 |
| `GUI_WIDTH` / `GUI_HEIGHT` | 150 / 122 |

`RequesterSlotGrid.width() = 8×18 = 144`；`height() = 2×38 − 2 = 74`。
（与 gtlcore `AEDualConfigWidget` 的 `CONFIG_SIZE/2*18` 完全同值。）

---

## 4. 数据模型

### 4.1 `RequesterSlot`（单格）

| 字段 | 类型 | 含义 |
|---|---|---|
| `key` | `AEKey` | 物品或流体，`null` = 空；**一格二选一**（后拖的赢） |
| `target` | `long` | 目标量（库存要维持到多少） |
| `batch` | `long` | 逐格并发量；`0` = 用默认值 |
| `stock` | `long` | 网络现有量快照（引擎每轮写入，仅显示用） |

- 上限 `MAX_AMOUNT = 9.2e18`；流体内部一律按 **mB** 存，`BUCKET_VOLUME = 1000`
- 批次有效值：`effectiveBatch(fallback) = batch > 0 ? batch : fallback`

### 4.2 `RequesterSlots`（整表容器）

为什么必须包一层：`RequesterSlot[]` 直接标 `@Persisted` 会在放方块时崩 ——
LDLib 的 `ReadonlyArrayRef` 要求元素是 `IManaged` 或 `IContentChangeAware`，否则抛
`complex sync field must be an IContentChangeAware if not lazy`。
让**容器**实现这两个接口即可（照 GTCEu `ExportOnlyAESlot` 的做法）。

`mutate(index, fn)` 只在真变了时 `markDirty()`；`setStockSilently()` 不标脏（避免每轮轮询都存档）。

### 4.3 存档与迁移

```java
serializeNBT():   { "List": [...], "LayoutV2": true }
deserializeNBT(): legacy = !tag.getBoolean("LayoutV2")
```

- `LayoutV2` 缺失 ⇒ 按**旧 128 格布局**读，下标 0~63 顺序保留，64 以后丢弃。
- 写入后带标记，不会二次换算。

### 4.4 同步字段（LDLib）

| 字段 | 注解 | 说明 |
|---|---|---|
| `slots` | `@Persisted` | **不带 `@DescSynced`** —— 客户端那份是空的 |
| `defaultItemBatch` / `defaultFluidBatch` | `@Persisted @DescSynced` | 64 / 1000 |
| `page` | `@Persisted @DescSynced` | 1..4 |
| `selectedSlot` | `@Persisted @DescSynced` | -1 = 未选 |
| `slotStatus` | `@Persisted @DescSynced` | 64 字符状态串 |
| `blockedText` | `@Persisted @DescSynced` | 聚合阻塞原因 |
| `isOnline` | `@DescSynced` | AE 节点在线 |

> **关键约束**：槽位内容靠每个 `RequesterSlotWidget` 自己推快照（`writeUpdateInfo(UPDATE_SLOT)`），
> 不能读 `machine.slot(i)` —— 客户端拿到的是空槽。所有客户端显示都必须走
> `grid.snapshotOf(i)` / `widget.displayedSlot()`。

---

## 5. 界面协议

### 5.1 单格控件 action（客户端 → 服务端）

| id | 名称 | 载荷 |
|---|---|---|
| 1 | `ACTION_SET_ITEM` | `ItemStack` |
| 2 | `ACTION_SET_FLUID` | LDLib `FluidStack` |
| 3 | `ACTION_CLEAR` | – |
| 4 | `ACTION_SELECT` | – |
| 100 | `UPDATE_SLOT`（服务端 → 客户端） | 整格 NBT + 选中布尔 |

`detectAndSendChanges()` 里比较 `snapshot()`，变了才推包；`writeInitialData` 保证开界面即有值。

### 5.2 JEI 拖拽

一个格子**同时**注册两种 `Target`（物品 / 流体），不双继承 `IGhostItemTarget + IGhostFluidTarget`
（两者都继承 `IGhostIngredientTarget` 且有各自的 `convertIngredient` 默认实现，Java 会报"继承了不相关默认值"）。

**流体判定必须解包**（这是"只能拖物品"的根因）：

```java
if (ingredient instanceof ITypedIngredient<?> typed) {
    Object inner = typed.getIngredient();          // ← 真正的 Forge FluidStack 在这里
    ...
}
```

另需排除"流体被 JEI 包成 ItemStack"的情况，否则会误判成物品格。

### 5.3 数量/并发面板

- 位置：默认贴在主面板右边缘外（`x = GUI_WIDTH`），点格子弹 `openFor(index)`。
- **可拖动**：抓顶部 11px 标题栏拖动，位置夹在父面板内，只存客户端（不写存档）。
- **客户端不挂 `textSupplier`**（关键）：

  `TextFieldWidget.detectAndSendChanges()` 每 tick 会把输入框文本改回 supplier 的值。
  客户端那份槽位是空的（只有 `@Persisted`），挂 supplier 就会把玩家刚打的字**每秒刷回 0**
  —— 这就是"怎么改都改不了数量"的根因。服务端挂 supplier 用于回推规范化文本。

### 5.4 绘制顺序陷阱

`WidgetGroup.drawInBackground()` 先画自己背景，再按加入顺序画子控件。
若在 `super.drawInBackground()` **之后**手绘 `DISPLAY` 底板，会把已画好的输入框文字盖住
（表现为"上面标签变了、下面输入框没反应"）。所以底板做成 `ImageWidget` 子控件，
并按「底板 → 输入框」顺序加入。

### 5.5 翻页可见性（客户端）

`applyPageVisibility()` 必须**同时**挂在 `detectAndSendChanges()` 和 `updateScreen()`：

- `detectAndSendChanges()` 只在**服务端**调用（`ModularUIContainer.tick`）
- `updateScreen()` 是客户端每帧钩子（`modularUI.mainGroup.updateScreen()`）

只挂前者 ⇒ 客户端 `shownPage` 永远停在 1，第 1 页格子一直可见（翻到第 2 页还能看到第 1 页）。

### 5.6 格内数字

格子只有 18px 宽，必须用紧凑格式：

```java
TextFormattingUtil.formatLongToCompactString(amount, 3)   // 1.8k / 1.83M / 9.2E
```

不能用 `RequesterNumbers.displayFor()`（会输出 `1.83e6`、`1.5B` 这类长串并溢出格子）。
带单位的精确值放在悬浮提示里。

---

## 6. 下单引擎（`RequesterOrderEngine`）

### 6.1 常量

| 常量 | 值 | 含义 |
|---|---|---|
| `POLL_INTERVAL` | 20 tick | 轮询周期（与原版请求器同值） |
| `GRACE_TICKS` | 300 tick（15s） | 在途归零但库存没涨时的宽限，防连环下单 |
| `FAILURE_COOLDOWN` | 100 tick（5s） | 失败后同槽冷却 |
| `CALC_TIMEOUT` | 400 tick（20s） | 单次计算超时 |
| `MIN_AUTO_SHRINK` | 1 | 自动缩单下限 |

### 6.2 串行

同一时刻只跑一个 `beginCraftingCalculation` future。
AE 的合成计算在 `CRAFTING_POOL` 线程池执行，一次丢 16 个会拖慢服务端 tick（终端实证结论）。

### 6.3 三重读数

```
stored   = grid.getStorageService().getCachedInventory().get(key)
inFlight = grid.getCraftingService().getRequestedAmount(key)
gap      = target − stored − inFlight
```

- `gap ≤ 0` → 充足（IDLE）
- `inFlight > 0` → 在途（IN_FLIGHT）
- 在途归零但 `gameTime − lastInFlightSeen < GRACE_TICKS` → 仍算在途（产物回网延迟）
- 冷却中 → 阻塞（BLOCKED，保留原具体原因）

申请量：`request = clamp(min(gap, batch), 1, amountCap)`。

### 6.4 产物去向

```java
grid.getCraftingService().submitJob(plan, null, null, true, actionSource);
```

`requester == null` ⇒ standalone 链接 ⇒ `CraftingService.addLink` 跳过 ⇒
`CraftingLink.insert` 返回 0 ⇒ `NetworkStorage.insert` 继续往后投递 ⇒ **产物进普通 ME 存储**。
（与终端下单同一路径。）

### 6.5 自动缩单（解决 `NO_SUITABLE_CPU_FOUND`）

AE2 的 `plan.bytes()` 与合成量大致成正比，CPU 存储装不下时只会返回
`NO_SUITABLE_CPU_FOUND(tooSmall)`，而旧逻辑冷却后**用同样的大单重试**，永远失败。

现在的流程：

```
biggestCpu = max(cpu.getAvailableStorage() for 空闲 CPU)
若 plan.bytes() > biggestCpu:
    shrunk = max(1, requested * biggestCpu / plan.bytes())
    amountCap[slot] = shrunk        // 记住上限，下轮沿用
    用 shrunk 重新计算 → 提交
成功 → amountCap[slot] = 0          // 解除限制，回到正常批量
失败且仍是 NO_SUITABLE_CPU_FOUND → 再砍一半
缩到 1 仍装不下 → 判定容量真不够，提示加存储元件
```

效果：目标量 1e5 不变，但会**分批慢慢补满**，而不是卡死。

### 6.6 状态字符

| 字符 | 常量 | 含义 | 格上角标 |
|---|---|---|---|
| `0` | `STATUS_IDLE` | 空槽或已充足 | – |
| `1` | `STATUS_IN_FLIGHT` | 在途 | `…` 蓝 |
| `2` | `STATUS_ORDERING` | 计算中 / 待提交 | `*` 金 |
| `3` | `STATUS_BLOCKED` | 失败冷却中 | `!` 橙 |
| `4` | `STATUS_IMPOSSIBLE` | 无配方 / 缺口补不上 | `×` 红 |
| `5` | `STATUS_OFFLINE` | 网络离线 | `-` 灰 |

### 6.7 错误码翻译

`NO_SUITABLE_CPU_FOUND` 会被展开成计数明细
（`UnsuitableCpus(offline, busy, tooSmall, excluded)`）：

> `哈斯特洛依合金-X锭 没有可接单的 CPU：2 台因选择模式被排除（改成"任意"或"仅机器"）`

四种排除原因（AE2 `CraftingService.findSuitableCraftingCPU` 的判断顺序）：

1. `!cpu.isActive()` → 离线 / 未供电 / 结构未成型
2. `cpu.isBusy()` → 忙碌
3. `getAvailableStorage() < plan.bytes()` → 容量不足
4. `!cpu.canBeAutoSelectedFor(src)` → 选择模式不匹配（仅玩家 vs 机器请求）

---

## 7. AE 接入

### 7.1 无线 ME 网络（gtlcore）

方块 id **必须以 `me_` 开头** —— `WirelessAeNetworkRuntime.isCompatibleExternalMeLikeId`
要求 `path.startsWith("me_")`。所以是 `gtlmodular:me_requester`，不能叫 `requester`。

需同时满足：

1. `MetaMachine` 子类（`hasMachinePartMarker`）且提供 `getMainNode()`（`hasGridNodeAccess`）——
   通过实现 `IGridConnectedMachine` 满足；
2. 方块 id 以 `me_` 开头。

六面均可接线缆（`nodeHolder.getMainNode().setExposedOnSides(EnumSet.allOf(Direction.class))`），
ME 仓默认只开正面。

### 7.2 ECO（neoecoae）计算子系统

**下单路径本来就通**：ECO 的 `CraftingServiceMixin` 把计算集群注入 AE2 标准的
`CraftingService.getCpus()` / `submitJob()`，连"假 CPU"占位符也进列表。

**规划路径需要桥接**：ECO 的快速规划器 (`ECOPlanningService.begin`) 只被这两处调用 ——
`CraftConfirmMenuMixin`（ECO 自己的界面）、`MERequesterPlanningMixin`（merequester）。
我们走的是 AE2 原生 `beginCraftingCalculation`，遇到循环/替代配方就算不出方案。

`EcoPlanningBridge` 按 merequester 那份 mixin 的判定条件反射调用：

```
actionSource 非空
  → ECOCraftingNetworkSettings.of(grid) 非空
  → neoecoae$isFastPlannerEnabled()
  → neoecoae$hasComputationHost(IActionSource)
        ↓ 全部满足
ECOPlanningService.begin(level, grid, source, key, amount, strategy, options)
        ↓ 任一不满足 / 抛异常
退回 AE2 原生 beginCraftingCalculation
```

实现要点：

- **不添加编译依赖**（`libs/` 无 neoecoae）。全程 `Class.forName` + `MethodHandles`，
  类不存在就返回 `null` 自动退回，避免 `NoClassDefFoundError`。
- **必须用真实声明类型解析**：`begin(...)` 声明返回 `ECOPlanningService$Request` 而非 `Future`；
  `MethodHandles` 查找对声明类型敏感，用 `Future` 会直接 `NoSuchMethodException` 让桥接永远失效。
  解析后 `.asType(...)` 转成 `Future` 形状。
- 需同时解析 `ECOPlanningService$Request`（内部类）作为返回类型。

日志：

```
[ME库存补充仓] 已接入 ECO（neoecoae）计算子系统：规划与下单都走计算集群
[ME库存补充仓] 未检测到 ECO（neoecoae），使用 AE2 原生合成规划
```

---

## 8. 数字格式

### 8.1 `RequesterNumbers.display(long)`

| 区间 | 输出 |
|---|---|
| `< 100 000` | 原样 `64000` |
| `>= 100 000` | 科学计数 `1.28e5`、`9.2e18`（尾数 2 位小数、去尾零） |

### 8.2 `RequesterNumbers.displayFluid(mB)`

| 区间 | 输出 |
|---|---|
| `< 1000 mB` | `640mB` |
| `>= 1000 mB` | 按桶，2 位小数去尾零：`1.5B`、`1000B` |

### 8.3 `RequesterNumbers.parse(String)`

接受 `64000` / `1.5e6` / `1,500,000`（容忍千分位），大小写不敏感；
用 `BigDecimal` 避免 `9.2e18` 附近丢精度。非法/负数 → `-1`（调用方忽略）。

### 8.4 格内用 `TextFormattingUtil`（LDLib）

`formatLongToCompactString(amount, 3)`：`999` / `1.8k` / `1.83M` / `9.2E`
（后缀表 k / M / G / T / P / E）。

---

## 9. 已知约束与坑（按症状索引）

| 症状 | 根因 | 修法 |
|---|---|---|
| 放方块立刻崩 `Failed to create ref of slots` | 数组直接 `@Persisted` | 包成 `RequesterSlots` 并实现 `IContentChangeAware` |
| JEI 拖进去完全没反应 | 翻页时 `clearAllWidgets()` 重建控件，`uiAccess` 为 null，action 发不出去 | 控件**一次建好常驻**，翻页只改 `setVisible` |
| 只能拖物品、拖不进流体 | JEI 给的是 `ITypedIngredient` 包装，真正 FluidStack 在内层 | 解包 `typed.getIngredient()` |
| 打字了但框里没变化 | 在 `super.drawInBackground()` 之后手绘 DISPLAY 底，盖住文字 | 底板做成子控件，按「底板→输入框」加入 |
| 数量怎么改都被刷回 0 | 客户端挂了 `textSupplier`，读的是未同步的空槽位 | 客户端 `textSupplier = null`，只服务端挂 |
| 翻到第 2 页，第 1 页格子还在 | 可见性只在服务端 `detectAndSendChanges` 里切 | 同时挂 `updateScreen()`（客户端每帧） |
| 数量框画到窗口外 | 旧坐标 `x = 168 − 4 = 164`，宽 96，界面只有 176 | 改为右侧可拖动面板，位置夹在父面板内 |
| 格内数字 `1.83e6` 溢出 18px 格 | 用了科学计数格式 | 改用 `formatLongToCompactString(·, 3)` |
| 正脸盖住 EV 外壳边框 | GTCEu `overlay/front` 是 -0.001~16.001 铺满整面 | 自写内缩模型 3~13 + UV 3,3→13,13 |
| 单帧贴图显示错乱 | 保留了 4 帧 `.mcmeta` | 删掉 mcmeta |
| `NO_SUITABLE_CPU_FOUND` 永远重试失败 | 冷却后用同样大单重试 | 自动缩单（§6.5） |
| ECO 装了但规划不走 ECO | 只调了 AE2 原生 `beginCraftingCalculation` | `EcoPlanningBridge`（§7.2） |
| ECO 桥接静默失效 | `MethodHandles` 按 `Future` 查，实际声明是 `ECOPlanningService$Request` | 用真实类型 + `asType` |

---

## 10. 构建与部署

```bash
cd /mnt/d/gtlmodular-recipe
export JAVA_HOME='D:\Java\jdk-21'
export GRADLE_USER_HOME='D:\gradle-home-gtl'
/mnt/c/Windows/System32/cmd.exe /c "chcp 65001>nul && gradlew.bat --offline build"
```

- 判成败看日志里的 `BUILD SUCCESSFUL`（尾部 `1 个警告` 是既有的 `registerMaterials` 弃用告警）
- 版本号唯一来源：`build.gradle:12` 的 `version = '0.3.480'`
- **换版本号后必须删掉 mods 里的旧 jar**，否则同 modId 两份 jar → `Duplicate mod` 拒绝加载

部署：

```bash
cd /mnt/d/gtlpack/mods
rm -f gtlmodular-<旧版本>.jar
cp /mnt/d/gtlmodular-recipe/build/libs/gtlmodular-0.3.480.jar .
```

部署前确认游戏未运行（`tasklist.exe | grep -i javaw`），否则等于没部署。

---

## 11. 配方（EV 档）

```json
"pattern": ["PCP",
            "IHI",
            "PSP"]
P = gtceu:stainless_steel_plate      C = gtceu:advanced_smd_capacitor
I = ae2:cable_interface              H = gtceu:ev_machine_hull
S = gtceu:ev_sensor
```

---

## 12. 本轮界面改动汇总（0.3.479 → 0.3.480）

| 项 | 改前 | 改后 |
|---|---|---|
| 槽位布局 | 8×4=32 格/页 × 4 页 = 128 格，单层 | **8×2=16 格/页 × 4 页 = 64 格，双层（上配置/下库存）** |
| 界面高 | 176 | **122**（与输入总成同级） |
| 数量编辑 | 浮动框，位置算错画到窗口外 | **右侧可拖动面板**，含「数量」+「并发」两行 |
| 并发量 | 无 UI 入口 | **可逐格设置**（0 = 用默认批量） |
| 子页签 | 「数量设置」「默认批量」 | **全部删除** |
| 格内数字 | `1.83e6`（溢出） | `1.83M`（紧凑） |
| 翻页残留 | 第 1 页一直可见 | 已修 |
| 正脸 | 32×128 四帧雪花动画，铺满整面 | **16×16 材质包请求器图案，内缩 3px 露外壳边框** |
| 名字 | ME自动补货牌 | **ME库存补充仓** |
| CPU 容量不足 | 冷却后原样重试，永远失败 | **自动缩单**，分批补满 |
| ECO | 只用 AE2 原生规划 | **可选桥接 ECO 规划器** |
