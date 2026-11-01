# NeoECO AE 计算子系统 CPU 无法提交合成订单 — 问题报告

**Report title（English）:** ECO computation CPU cannot submit crafting jobs; `neoecoae$startBigOrder` returns silently while plan/CPU are present

**Affected versions:** `neoecoae-20.5.0`, `neoecoae-20.5.2`
**Environment:** Minecraft 1.20.1 · Forge 47.4.16 · AE2 15.4.10 · GTCEu 1.4.4 · GTLCore 1.2.3.2-fix4 · 单人集成服务器

---

## 1. 问题摘要

计算子系统（Computation System）已成型、已接入 AE 网络，AE 确认界面中能看到 CPU、计划已计算完成，但点击「开始」后：

- 确认界面不关闭
- CPU 中没有生成任务
- 服务端只收到提交包，随后静默返回
- 没有聊天栏错误、没有 `submitError`、没有异常

同一套装置里，**原版 AE2 的 `CraftingCPUCluster` 在特定路径下可以正常提交**，但 **ECO 的 `ECOCraftingCPU` 无论走哪条路径都无法提交**。

---

## 2. 复现步骤

1. 搭建并成型计算子系统，接入 AE 网络。
2. 放入编码好的**合成样板**（本报告使用最简单的橡木木板合成样板，1 → 4）。
3. 在 AE 终端请求合成，打开确认界面。
4. 确认界面显示：
   - `合成CPU：处理器 #1`（ECO CPU），`可存储 128 MB`、`并行处理单元 12.67k`
   - 计划已就绪（`READY`）
   - 左侧合成图：橡木木板 `请求 1 / 库存 0 / 待合成 1 / 缺少 0 / 状态 合成中 / 来源样板 1`
5. 点击「开始」。
6. 界面停留在开始界面，无任务进入 CPU。

---

## 3. 预期行为 / 实际行为

| | 预期 | 实际 |
|---|---|---|
| ECO CPU + 正常点击开始 | 任务提交到 CPU 并关闭确认界面 | 无反应，无任务 |
| 原版 AE2 CPU + 正常点击开始 | 任务提交 | 同样无反应 |
| 原版 AE2 CPU + 强制原生提交 | 任务提交 | **可以正常提交** |
| ECO CPU + 强制原生提交 | 任务提交 | 仍然无任务 |
| 移除第三方终端模组后 | — | **仍然复现** |

---

## 4. 服务端观测证据

### 4.1 正常点击（走 eco big-order 分支）

客户端/服务端日志：

```text
[big-order-submit] Client start: container=3, forced=false, bigOrder=false, status=SUCCESS
[craft-submit] Server received: container=3, currentContainer=3, forced=false
```

此后**没有任何** `Submitting to CPU`、`Admission denied`、`Reject` 或 `Submission failed` 日志。

### 4.2 用只读 Mixin 读取 `CraftConfirmMenu` 服务端字段

在 `CraftConfirmMenu` 的 `m_38946_`（服务端菜单 tick）中安全打印字段，得到：

```text
plan=present
result=CraftingPlan
cpu=ECOCraftingCPU
cpuName=null
cpuList=2
noCPU=false
submitError=SyncableSubmitResult[result=null]
```

其中：

- `cpuList=2`：服务端能看到 ECO CPU 和原版 AE2 CPU 两个
- `selectedCpu` 确实同步到了服务端，并且是 `ECOCraftingCPU`
- `plan` 和 `result` 都存在
- `submitError` 为 `result=null`，也就是**没有提交错误被写回**

### 4.3 `ECOCraftingCPU.getName()` 返回 null

调用 `selectedCpu.getName().getString()` 时直接触发：

```text
java.lang.NullPointerException: Cannot invoke
"net.minecraft.network.chat.Component.getString()" because the return value of
"appeng.api.networking.crafting.ICraftingCPU.getName()" is null
```

栈顶确认返回 null 的正是 `ECOCraftingCPU.getName()`。
（注：该 NPE 发生在我们自己的诊断日志里，不是 eco 代码自身抛出的；但它证明了 `getName()` 返回 null。）

### 4.4 切换到原版 CPU

在确认界面把 CPU 切换到原版 AE2 的 `CraftingCPUCluster`：

```text
broadcast: plan=present result=CraftingPlan cpu=CraftingCPUCluster cpuName=null cpuList=2 ...
```

原版 CPU 的 `getName()` 也是 null，但在“强制原生提交”路径下可以正常提交。说明问题不只是名字 null，而是 **ECO CPU 的提交路径**。

---

## 5. 关键对照实验

| 实验 | 结果 |
|---|---|
| ECO CPU + `usesBigOrderSubmission=true`（默认） | 卡在开始，无任务 |
| 原版 CPU + `usesBigOrderSubmission=true`（默认） | 卡在开始，无任务 |
| 原版 CPU + 强制 `usesBigOrderSubmission=false`（走 `ECOForceCraftStartFlagC2SPacket` → `CraftConfirmMenu.startJob()`） | **成功提交** |
| ECO CPU + 强制 `usesBigOrderSubmission=false` | 仍然无任务 |
| 完全移除我们的终端模组 | 仍然复现（20.5.0 / 20.5.2 均测试） |

强制原生提交的服务端日志：

```text
[craft-submit] Server received: container=7, currentContainer=7, forced=false
```

之后原版 CPU 出现任务；ECO CPU 没有。

---

## 6. 相关代码路径

### 6.1 正常点击（ECO 分支）

```text
ECOCraftConfirmScreen.submit(boolean)
  → 使用 ECOBigOrderStartC2SPacket
  → ECOBigOrderStartC2SPacket.handle(...)
  → menu.neoecoae$startBigOrder(forced)
```

可疑点：`neoecoae$startBigOrder` 内有多处提前 `return`（`neoecoae$rejectBigOrder`），但在默认日志级别下**没有输出 reason**；`submitError` 也没有被写入，客户端因此看起来“完全没反应”。

### 6.2 强制原生分支

```text
ECOCraftConfirmScreen.usesBigOrderSubmission(boolean) == false
  → ECOForceCraftStartFlagC2SPacket
  → menu.neoecoae$setForceCraftStart(...)
  → menu.startJob()
```

此路径下原版 `CraftingCPUCluster` 成功，`ECOCraftingCPU` 仍然失败。

### 6.3 相关类/方法

- `cn.dancingsnow.neoecoae.client.gui.crafting.ECOCraftConfirmScreen#usesBigOrderSubmission(boolean)`
- `cn.dancingsnow.neoecoae.network.ECOBigOrderStartC2SPacket#handle`
- `cn.dancingsnow.neoecoae.network.ECOForceCraftStartFlagC2SPacket#handle`
- `cn.dancingsnow.neoecoae.mixins.ae2.menu.CraftConfirmMenuMixin#neoecoae$startBigOrder(boolean)`
- `cn.dancingsnow.neoecoae.mixins.ae2.menu.CraftConfirmMenuMixin#neoecoae$rejectBigOrder(String, ICraftingSubmitResult)`
- `cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU#getName()`
- `appeng.menu.me.crafting.CraftConfirmMenu` 的 `plan` / `result` / `selectedCpu` / `noCPU` / `submitError` 字段

---

## 7. 建议的修复/诊断增强

1. **为 `neoecoae$rejectBigOrder` 增加 INFO/WARN 日志**，至少包含：
   - `reason`
   - `containerId`
   - `plan == null`
   - `result == null`
   - `selectedCpu` 类名与 null 状态
   - `grid == null`
   
   目前静默拒绝是定位困难的主要原因。

2. **检查 `ECOCraftingCPU.getName()`**：在没有 owner/未加载时返回 null。AE2 原生 UI/日志会直接调用 `getName().getString()`，建议返回稳定 fallback（例如 `"ECO CPU"`），至少不要让下游 NPE。

3. **检查 `neoecoae$startBigOrder` 对 `selectedCpu` 的处理**：
   - 服务端字段已确认 `selectedCpu=ECOCraftingCPU`
   - `plan/result` 均存在
   - 请确认 `resolveOwnedPlanningResult` / `ECOBigOrderAdmission` / `resolveBigOrderCpu` 哪个分支返回了 null
   - 建议在拒绝时把 `submitError` 写回客户端，而不是直接 return

4. **验证 `ECOForceCraftStartFlagC2SPacket` 路径对 `ECOCraftingCPU` 的支持**：
   - 原版 `CraftingCPUCluster` 可提交，`ECOCraftingCPU` 不可提交
   - 请检查 `ECOCraftingCPU` 是否实现了 `submitJob`/`ICraftingCPU` 所需的状态同步

5. **复现用例**：任意简单合成样板（如橡木木板 1→4），确认界面选择 ECO CPU，点击开始；服务端开启 `ecoCraftSubmissionDebug=true` 时应能看到拒绝原因。

---

## 8. 影响

- 计算子系统成型、网络正常、计划正常、CPU 空闲时，**ECO CPU 无法承担任何合成任务**
- 玩家只能改用原版 AE2 CPU，ECO 计算子系统的并行/CPU 资源实际不可用于下单
- 由于界面无反馈，玩家会反复点击，进一步造成“没有可用 CPU”的假象

---

## 9. 附件/日志位置

- 客户端/服务端日志：`minecraft/logs/latest.log`
- 崩溃报告（由第三方诊断代码触发，仅用于证明 `getName()` 为 null）：
  `minecraft/crash-reports/crash-2026-10-27_21.49.19-server.txt`
- 关键日志片段：

```text
[big-order-submit] Client start: container=3, forced=false, bigOrder=false, status=SUCCESS
[craft-submit] Server received: container=3, currentContainer=3, forced=false

broadcast: plan=present result=CraftingPlan cpu=ECOCraftingCPU cpuName=null cpuList=2 noCPU=false submitError=SyncableSubmitResult[result=null]
```

---

## 10. 备注

- 上述“强制原生提交”和“CPU 名字回退”是第三方终端模组为了恢复原版 CPU 下单做的临时绕过；**移除该模组后问题仍复现**，因此不是终端模组引入的。
- 本报告只描述 eco 侧行为，不包含第三方模组的业务逻辑。
