# NOTICE · 第三方素材（GTNH 2.9.0 NAC）

本文件记录本模组从 GTNH 2.9.0 的 NAC（Nanochip Assembly Complex，纳米芯片组装复合体）搬入的
文本、机制数值与配方数值。它不是对整个模组的许可声明；整个模组的许可仍以仓库根 `LICENSE` 为准。

## 1. 来源

| 上游 | 仓库 | 版本 | 许可 |
|---|---|---|---|
| GT5-Unofficial（格雷科技） | <https://github.com/GTNewHorizons/GT5-Unofficial> | tag `5.09.54.265`，commit `bb00f3f1023e20d3cce84cf09abb442d22a3a5a5` | **LGPL-3.0-or-later** |
| NewHorizonsCoreMod（GTNH 核心模组） | <https://github.com/GTNewHorizons/NewHorizonsCoreMod> | tag `2.9.99`，commit `9f108160f88f7d6ba7e20d830eeee760d7198144` | **GPL-3.0** |

许可全文随包提供：

- `licenses/LGPL-3.0.txt`（GT5U 根 `LICENSE.txt` 原样，7,652 字节）
- `licenses/GPL-3.0.txt`（CoreMod 根 `LICENSE` 原样，35,122 字节）
- jar 内：`META-INF/licenses/LGPL-3.0.txt`、`META-INF/licenses/GPL-3.0.txt`

GT5U 的 README 明示：可在 GNU LGPL 第 3 版或更高版本下再分发/修改（LGPL-3.0-or-later）。
CoreMod 根 `LICENSE` 是 GPL-3.0 全文。

## 2. 本模组中来自 GTNH 的内容（逐项）

### 2.1 文本（GT5U，LGPL-3.0-or-later）

以下 11 个模块的 tooltip 文本（`GT5U.tooltip.nac.module.*`、`GT5U.tooltip.nac.interface.module_description`）：

- Assembly Matrix（组装矩阵）
- Biological Coordinator（加速生物调谐器）
- Board Processor（全板浸没设备）
- Cutting Chamber（纳米精度切割仓）
- Etching Array（超高能蚀刻阵列）
- Encasement Wrapper（纳米封装卷绕器）
- Optical Organizer（光学优化组织器）
- Part Processor（元件预处理装配，本包显示名“元件预处理装置”）
- Splitter（纳米组件分流器）
- Superconductor Splitter（超导线束分离器）
- Wire Tracer（纳米精度导线追踪器）

本包对上述文本做了中文翻译、少量改写与换行重排；`§` 颜色码按上游保留。
本包另外新增的 3 台组织器（奇异/寰宇/超因果）不在 GT5U 中；它们的描述套用 Optical Organizer
的句式与数值，属于本模组扩展，不是 GT5U 原文。

**未搬**：GT5U 的 `GT5U.tooltip.nac.main.*`（主机 tooltip）、`GT5U.tooltip.nac.hatch.*`、
`GT5U.tooltip.nac.color_grid.*` 等未逐条复制；CC/PC 物品 tooltip 按本包口径不显示。

### 2.2 机制数值（GT5U，LGPL-3.0-or-later）

从 GT5U 反编译源取证、并在本包重写的机制数值，包括但不限于：

- 主机 5 秒（100 tick）转换周期；真空传送颜色/1:1 规则；输入总线 → 同色 VCO 的路由；
- 组装矩阵的机壳等级门槛与 `0.95^(machineTier − recipeTier)` EU 折扣；
- 全板浸没的 1,000,000 L 内部罐、杂质三段公式、排空规则；
- 蚀刻阵列的激光源仓公式（EU ÷ 每 256A 一档、时长 ÷ (仓等级 − 9)、晶体满校准 +1 档）；
- 光学组织器的 Grade 3~8 双水、六档水量与加成；
- 生物调谐器的湿件减半 / 生物全免；
- 超导分离器的 1000 L/s 超级冷却液；
- 校准 100 批 × 1000、各族阈值与三档效果；
- 主机 25%/75% 配电、模块 400 秒缓冲、两段超频与 100 tick 下限。

上述数值在本包以 Java/KubeJS 重新实现，**没有复制 GT5U 的 Java 方法体**；数值来源与差异见
`_gtnh290_nac_mechanics.md`（工作区文档，不随 jar 分发）。

### 2.3 配方数值（NewHorizonsCoreMod，GPL-3.0）

从 CoreMod 的 `ScriptNanochipRecipes.java` 取证、并在本包重新录入的配方数值，包括：

- NAC 主机本体（TT 研究型装配线）配方；
- 11 个模块本体（太空组装机 `spaceAssemblerRecipes`）配方；
- 真空输入/输出仓、真空管道、分级仓、四种 Nanochip 机壳、Nanochip 玻璃配方；
- CC ↔ 真物品转换（128 条可逆，1:1，100 tick / 0 EU）；
- 组装矩阵 27 条电路配方（原始/晶体/湿件/生物/光学/皮米/量子/普朗克）。

**未搬**：CoreMod 的 Java 源码、类、方法体；只取机器、输入输出、流体、时长、EU/t、
metadata 等数值，并按本包配方格式重写。部分配方因本包缺件/退役件未实现（见改单 §5）。

### 2.4 模块名 / 机壳名（GT5U，LGPL-3.0-or-later）

- 模块显示名（Assembly Matrix / Biological Coordinator / …）与 GTNH `en_US.lang` / `zh_CN.lang` 的
  `GT5U.tooltip.nac.module.*.name` 对应；
- 四种机壳名（Nanochip Mesh Interface / Reinforcement / Computational Matrix / Firewall Projection Casing）
  与 Nanochip Complex Glass 的名称对应。

## 3. 本模组做的修改

日期：2026-11-07。

- 中文翻译、少量改写、换行重排；
- 把 GTNH 的注册名/物品 ID 改到 `gtlmodular:`（独立版为 `gtl_nac:`）命名空间；
- 只保留/重写本包需要的数值，缺件部分未实现；
- CC/PC 物品的 tooltip 按本包口径不显示（不是复制 GT5U 的 CC/PC tooltip）。

## 4. 明确未纳入

- GT5U 的 Java 代码、类、方法体；
- GT5U 的贴图、模型、GUI 纹理、音效；
- GTNH 整合包仓库（GT-New-Horizons-Modpack）的配置、任务、脚本、整合包文本
  （该仓库是 CC BY-NC-SA 4.0，与本 NOTICE 无关）；
- GTNH wiki 的文本（wiki 许可未能直接核实；本模组未从 wiki 逐字复制）。

## 5. 许可边界

- 本模组中来自 **GT5-Unofficial** 的部分按 **LGPL-3.0-or-later** 分发；
- 本模组中来自 **NewHorizonsCoreMod** 的配方数值按 **GPL-3.0** 分发；
- 上述两部分**不附带**任何“禁止商用”条款；GTO 的 PolyForm Noncommercial / CC BY-NC-SA
  只适用于 GTO 来源的部分（如本仓库含 GTO 内容，见 `NOTICE_第三方素材_GTO.md`），
  不适用于 GT5U / CoreMod 部分；
- 本模组整体仍按其根 `LICENSE` 分发；本 NOTICE 只记录第三方来源与义务。

## 6. 源码获取（Corresponding Source）

- 本模组：<https://github.com/Zhumi-Heavy-Industries/gtlmodular>（对应源码 = 含本 NOTICE 的发版 tag）
- GT5U：<https://github.com/GTNewHorizons/GT5-Unofficial/tree/5.09.54.265>
- CoreMod：<https://github.com/GTNewHorizons/NewHorizonsCoreMod/tree/9f108160f88f7d6ba7e20d830eeee760d7198144>

## 7. 联系

如对上述来源/许可有疑问，请在本模组仓库开 issue，或联系维护者。
