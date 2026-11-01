# 反馈：ECO 计算子系统 CPU 无法提交合成订单（20.5.0 / 20.5.2）

## 环境

- Minecraft 1.20.1 / Forge 47.4.16
- AE2 15.4.10
- neoecoae 20.5.0、20.5.2（两个版本都能复现）
- GTCEu 1.4.4、GTLCore 1.2.3.2-fix4
- 单人存档（集成服务器）

## 问题现象

计算子系统已成型并接入 AE 网络，确认界面里能看到 ECO CPU（处理器 #1，128 MB / 12.67k 并行），计划也已计算完成，但点「开始」后：

- 确认界面不关闭
- CPU 里没有生成任务
- 服务端只收到提交包，之后没有任何日志/报错
- 聊天栏没有错误提示，`submitError` 也没有被写回

## 复现步骤

1. 成型计算子系统并接入 AE 网络。
2. 放入简单的合成样板（测试用橡木木板 1 → 4）。
3. 在 AE 终端请求合成，打开确认界面。
4. 选择 ECO 计算子系统 CPU（处理器 #1）。
5. 点击「开始」。
6. 界面停在开始页，CPU 无任务。

## 服务端观测到的状态

通过只读诊断读取 `CraftConfirmMenu` 服务端字段：

```text
plan=present
result=CraftingPlan
cpu=ECOCraftingCPU
cpuName=null
cpuList=2
noCPU=false
submitError=SyncableSubmitResult[result=null]
```

说明：

- `cpuList=2`：服务端能看到 ECO CPU 和原版 AE2 CPU 两个
- `selectedCpu` 已同步到服务端，并且就是 `ECOCraftingCPU`
- `plan` 和 `result` 都存在
- `submitError` 为 `result=null`，即没有提交错误被写回

点击开始后的服务端日志只有：

```text
[big-order-submit] Client start: container=3, forced=false, bigOrder=false, status=SUCCESS
[craft-submit] Server received: container=3, currentContainer=3, forced=false
```

之后**没有** `Submitting to CPU`、`Admission denied`、`Reject`、`Submission failed` 等日志。

## 对照实验

| 场景 | 结果 |
|---|---|
| ECO CPU + 默认 big-order 路径 | 卡在开始，无任务 |
| 原版 AE2 CPU + 默认 big-order 路径 | 同样卡在开始 |
| 原版 AE2 CPU + 强制原生提交（`startJob`） | **可以正常提交** |
| ECO CPU + 强制原生提交（`startJob`） | 仍然无任务 |
| 移除第三方终端模组（20.5.0） | 仍然复现 |
| 不加载任何 eco 相关 mixin（20.5.2） | 仍然复现 |

强制原生提交的方式是把 `ECOCraftConfirmScreen#usesBigOrderSubmission(boolean)` 强制返回 `false`，客户端改走：

```text
ECOForceCraftStartFlagC2SPacket
  → CraftConfirmMenu.startJob()
```

该路径下原版 `CraftingCPUCluster` 能提交，`ECOCraftingCPU` 仍然不能提交。

## 另一个可疑点：`getName()` 返回 null

对选中的 CPU 调用 `getName().getString()` 时会触发：

```text
java.lang.NullPointerException: Cannot invoke
"net.minecraft.network.chat.Component.getString()" because the return value of
"appeng.api.networking.crafting.ICraftingCPU.getName()" is null
```

确认返回 null 的是 `ECOCraftingCPU#getName()`。
另外原版 `CraftingCPUCluster#getName()` 在未命名 CPU 上也会返回 null，但原版 CPU 在原生路径下能正常提交，所以这不是唯一原因，但会让下游 UI/日志出错。

## 相关代码路径

- `cn.dancingsnow.neoecoae.client.gui.crafting.ECOCraftConfirmScreen#usesBigOrderSubmission(boolean)`
- `cn.dancingsnow.neoecoae.network.ECOBigOrderStartC2SPacket#handle`
- `cn.dancingsnow.neoecoae.network.ECOForceCraftStartFlagC2SPacket#handle`
- `cn.dancingsnow.neoecoae.mixins.ae2.menu.CraftConfirmMenuMixin#neoecoae$startBigOrder(boolean)`
- `cn.dancingsnow.neoecoae.mixins.ae2.menu.CraftConfirmMenuMixin#neoecoae$rejectBigOrder(String, ICraftingSubmitResult)`
- `cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU#getName()`
- `appeng.menu.me.crafting.CraftConfirmMenu` 的 `plan`、`result`、`selectedCpu`、`noCPU`、`submitError`

## 建议排查/修复

1. **给 `neoecoae$rejectBigOrder` 加日志**：至少输出 `reason`、`containerId`、`plan`/`result` 是否为 null、`selectedCpu` 类名、`grid` 是否为 null。  
   目前静默返回是定位困难的主要原因。
2. **检查 `neoecoae$startBigOrder` 的提前返回分支**：服务端已确认 `plan/result` 存在、`selectedCpu=ECOCraftingCPU`，请确认是 `resolveOwnedPlanningResult`、`ECOBigOrderAdmission` 还是 `resolveBigOrderCpu` 返回了 null；拒绝时把 `submitError` 写回客户端。
3. **`ECOCraftingCPU#getName()` 返回 null 的问题**：建议给一个稳定 fallback，避免下游 UI/日志 NPE。
4. **验证原生 `startJob` 路径对 `ECOCraftingCPU` 的支持**：原版 `CraftingCPUCluster` 能提交，ECO CPU 不能，说明 ECO CPU 在 `submitJob`/状态同步上可能还有缺口。
5. 复现时可在服务端开启 `ecoCraftSubmissionDebug=true`，但本次测试该开关没有输出额外原因日志，也建议一并检查。

## 影响

ECO CPU 空闲、计划正常时仍无法下单，玩家只能改用原版 AE2 CPU；计算子系统的并行/CPU 资源实际不可用于合成，且界面没有反馈，容易让玩家误以为是 CPU 被占满。

## 附件

- `logs/latest.log`
- `crash-reports/crash-2026-10-27_21.49.19-server.txt`（由第三方诊断代码调用 `getName()` 触发，仅用于证明返回 null）
