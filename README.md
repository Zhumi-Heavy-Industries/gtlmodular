# GTL-Modular（`gtlmodular`）

> 本文给**第一次打开这个工程**的人：5 分钟看懂它在做什么、怎么编译起来、改哪儿要小心。
> 全部内容为**只读核对**所得（源码 / `build.gradle` / `mods.toml` / 语言文件逐行实读），行号可直接复核。
> ⚠ 本文件**替换**了旧版 README（旧版写「JDK 17」「`kubejs/` 目录」，两者都已过时）。

---

## 1 · 这是什么

这是 **GTL（格雷科技休闲版）的一个附属模组**，主题是「**纳米芯片组装复合体（NAC, Nanochip Assembly Complex）**」——
GTNH 2.9.0 同名多方块的移植版：一台巨型主机 + 14 种外围模块，把 GregTech 的电路生产压缩进一座塔里。

模组同时保留了早期的「**熔岩模块化机组**」血统（地心涌动能量转换枢纽 + 高炉 / 星核剥离 / 天基炼矿三个模块），
以及一整套「**猪咪样板总成**」（MV~UIV 10 级普通版 + 超级版 + 惰性版，含分页样板槽、逐槽配方模式、
自动切模式、样板身份闸、样板倍率 / 神锻模式、专属催化剂槽）。

主机的核心机制是 **跨配方真实多线程**：同一时刻挂起 N 条**不同**配方，每条各自计时、各自扣料出料，
并支持「组装表 + 换算表」同轮搜（复合型机器语义）。CE 侧还提供从无线电网直接取电、
把真件 ⇄ 封装件（CC）双向换算的能力。

| 项 | 值 | 出处 |
|---|---|---|
| modid | `gtlmodular` | `GTLModular.java:25` |
| 当前版本 | `0.3.132` | `build.gradle:12`（唯一来源；`processResources` 注入 `mods.toml:8` 的 `${mod_version}`） |
| 显示名 | `GTL更多实用设备` | `build.gradle:100`（`\u` 转义） |
| 作者 / 许可 | 小爱与太肥鱼 / 本模组自身 **MIT** | `build.gradle:101-103` |
| Java | **编译 JDK 21**，产物字节码目标 **17** | `build.gradle:19-25` |
| Forge | 1.20.1，`forge-1.20.1-47.3.7` | `build.gradle:81` |
| 类数 | 108 个 `.java` | `grep "^package com\.gtlmodular"` ⇒ 108 命中 |

### 依赖

| 模组 | 必要性 | 我们用到什么 | 出处 |
|---|---|---|---|
| `gtceu ≥1.4.4` | **必须** | 机器 / 多方块 / 配方 / UI 基类 | `mods.toml:41-46` |
| `ldlib ≥1.0` | **必须** | 界面控件、同步、渲染 | `mods.toml:48-53` |
| `ae2 ≥15` | **必须** | 样板的 `IPatternDetails` / `PatternDetailsHelper` | `mods.toml:56-61` |
| `gtlcore ≥1.2` | **必须** | `MEPatternBufferPartMachine` 基类、分页 / 催化剂界面、镜像总成协议 | `mods.toml:64-69` |
| `gtladditions ≥3.2` | 可选 | 神锻配置页图标；`GTLAdd*` 机器与配方逻辑基类、线程仓接口（缺了退回本机图标；**但 `libs\` 里必须有它才能编译**） | `mods.toml:71-78` |
| `jei ≥15` | 可选（客户端） | 只用来把「NAC 孤儿封装件」在 JEI 里隐藏 | `mods.toml:80-87` |

> **许可与署名**：所有被引用的 GTL 系列模组（gtceu / gtlcore / gtladditions）均为 LGPLv3.0 / GPL，
> LGPL 明确允许第三方代码链接 / 继承它们，本模组因此可以保持自己的 MIT 许可。
> 我们**没有** `jarJar` 打包（一直是 SKIPPED）、也没有复制它们的源码；
> 机器结构、材质与配方表的版权归各自作者所有（见 `mods.toml` 的 `credits` 段）。

---

## 2 · 怎么编译、怎么进游戏

### 2.1 前置：JDK 21（**不是 17**）

`build.gradle:19` 是 `java.toolchain.languageVersion = JavaLanguageVersion.of(21)`。
原因逐字写在 `build.gradle:15-18`：`libs\` 里的 `gtladditions` 是 **Java 21 字节码**（major 65），
源码真的 `import` 了它的 `IThreadModifierPart` / `CommonUtils`；
**javac 17 读 major-65 的类会直接报 `class file has wrong version 65.0, should be 61.0`**。
产物目标仍是 17（`options.release = 17`，`:25`）⇒ jar 在 JVM 17 / 21 上都能跑。

### 2.2 前置：`libs\` 里的本地依赖

**除 Forge / MC 本体之外的所有依赖都来自 `libs\` 下的本地 jar**，不走 Maven：

```groovy
flatDir { dirs 'libs' }                                              // build.gradle:76
implementation fg.deobf(fileTree(dir: 'libs', include: ['*.jar']))    // build.gradle:90
```

`libs\` 现读有 17 个 jar（`gtceu-1.20.1-1.4.4`、`gtlcore-1.2.3.2-fix4`、
`[Custom-亚空间]gtladditions-3.3.1Custom_SubSpace`、`ldlib-forge-1.20.1-1.0.33.b`、
`appliedenergistics2-forge-15.4.10`、`jei-1.20.1-forge-15.49.0.188`、`Jade-1.20.1-Forge-11.13.3`、
`gtmthings-1.3.5.fix`、`GTLsupb-3.4.beta.7`、`extendedae_plus_gtladd-1.0.3`、
`GTMAdvancedHatch-0.1.9-hotfix2`、`architectury-9.2.14-forge`、`cloth-config-11.1.136` 等）。

**⇒ 换依赖版本 = 换 `libs\` 里的文件，不要改 `build.gradle`。**
⚠ `build.gradle:86` 的注释还写着 `fix2` / `gtladditions fix2`，**已落后于 `libs\` 的实际内容**
（见交接文档 §7.4）。`mavenCentral()` 与两个 maven 仓库仍在 `repositories` 里（`:71-78`），
但只是**声明**，实际解析走 `libs\`。

### 2.3 编译命令

```powershell
# 本机可用的本地 gradle（wrapper 走 services.gradle.org，实测常卡在下载）
#   E:\大鲸鱼工作区\gtl-dev\tools\gradle-8.5-bin.zip   ← 现读只有 zip，用前先解压
# 共享依赖缓存（各项目共用 GRADLE_USER_HOME）
#   E:\大鲸鱼工作区\gtl-dev\gradle-home

& '<gradle-8.5>\bin\gradle.bat' -p 'E:\大鲸鱼工作区\gtl-modular' build `
    -x spotlessJavaCheck -x spotlessCheck `
    --offline --no-daemon --no-watch-fs --max-workers=1 --console=plain
```

- **★ 不要去掉 `-x spotlessJavaCheck -x spotlessCheck`。** 本工程线上就是这条纪律
  （`_census\错误历史与工作经验.md` §六-6.1 与 C21，逐字：「固定加 `-x spotlessJavaCheck -x spotlessCheck`；
  构建过；**但不记为"格式检查通过"**」）。本工程 `build.gradle` 里**并未配置** spotless 插件，
  所以跳过它是零成本保险 —— 但**别把它从命令里拿掉**，因为同事与 CI 都按这条命令走。
- `--offline` 是**必须的**（无网 / 弱网环境；共享缓存 `gtl-dev\gradle-home`）。
- **判构建成败看日志里的 `BUILD SUCCESSFUL` 字样，不要只看退出码** ——
  PowerShell 管道会把 gradle 写 stderr 的告警当 `NativeCommandError`，命令行报 `[exit code: 1]`
  而构建其实成功。要干净退出码就改用 `cmd /c … > log 2>&1`。
- 产物：`gtl-modular\build\libs\gtlmodular-0.3.184.jar`。

> **保留纪律**：`build.gradle:93-96` 的 `processResources { filteringCharset = 'UTF-8' }` **不许删**，
> 否则 `expand` 会按系统默认字符集（中文 Windows = GBK）读写 `mods.toml`，
> 游戏里模组名称与简介会显示成一串乱码。

### 2.4 部署（四验，别省）

```powershell
# ① 部署前必须确认游戏没在跑（游戏在跑时部署 = 等于没部署）
(Get-Process javaw -ErrorAction SilentlyContinue | Measure-Object).Count      # 期望 0
# ② 产物新鲜度 + 哈希
Get-ChildItem 'E:\大鲸鱼工作区\gtl-modular\build\libs\gtlmodular-*.jar' |
  ForEach-Object { "{0}`t{1}B`t{2}`tsha256={3}" -f $_.Name, $_.Length, $_.LastWriteTime,
                   (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower() }
# ③ 清旧 jar → 落位（★ 同一 modId 只允许 1 个 jar，多余的挪 _PARKED_JARS\，不要删）
# ④ 四验：数量 = 1 / 大小一致 / sha256 一致 / jar 内 META-INF/mods.toml 版本号正确
```

实例 `mods\` 目录（本工程既有书面记录）：`D:\Prism Launcher\sl\GTL猪咪整合包\.minecraft\mods`。

**⚠ 关键开关改完必须用 `javap` 读实例 jar 里的常量值**（`static final` 会被 javac **内联**进每个使用点）：

```powershell
& "$env:JAVA_HOME\bin\javap.exe" -p -constants -cp <实例\mods\gtlmodular-0.3.184.jar> `
    com.gtlmodular.common.machine.nanochip.NanochipThreadedRecipeLogic |
  Select-String 'FAN_OUT_SAME_RECIPE'
```

### 2.5 进游戏

```powershell
& '<gradle-8.5>\bin\gradle.bat' -p 'E:\大鲸鱼工作区\gtl-modular' runClient `
    -x spotlessJavaCheck -x spotlessCheck --offline --no-daemon --console=plain
```

`runs.client` 的工作目录是 `gtl-modular\run\`（`build.gradle:34-44`）；`runServer` 同理（`:45-54`）。
datagen 用 `runData`（`:55-67`，输出到 `src/generated/resources/`）——
**注意它会自己删陈旧文件**（删掉的键真的会从生成目录消失），跑之前先存一份快照。

---

## 3 · 包结构导航

> 全部在 `gtl-modular\src\main\java\com\gtlmodular\` 下。**改代码前先在这里定位**。

| 包 | 一句话职责 |
|---|---|
| `com.gtlmodular` | **模组主入口**。`GTLModular.java`（`@Mod` 构造：配置注册、机器 / 配方类型注册窗口、NAC 自检命令、材料事件）；`MOD_ID` / `LOGGER` 也在这里 |
| `com.gtlmodular.api.module` | 对外最小的模块契约：`ModuleKind` |
| `com.gtlmodular.common.config` | 配置定义 `GTLModularConfig`（Forge `ModConfig`：物流模式、校准白名单、各调试日志开关等） |
| `com.gtlmodular.common.data` | **注册中枢**：`GTLModularMachines`（所有机器方块的 Registrate 注册）、`GTLModularAddon`（GTCEu addon：给**已存在**的材料补 `DUST + GENERATE_FRAME`）、`NanochipBlocks`、`HatchPredicates` |
| `com.gtlmodular.common.data.nanochip` | **NAC 的数据层**：`NanochipMachines`（NAC 主机 + 真空仓 + 管道）、`NanochipRecipeTypes`（自有配方表 ASSEMBLY_COMPLEX / CONVERSION）、`NanochipComplexPattern`（63×49×63 结构图案）、缺件材料 / 物品 / 晶圆、`NanochipSelfCheck`（运行时自检） |
| `com.gtlmodular.common.data.module` | 天基炼矿模块的图案层与**配方镜像**（`SpaceOreRecipeMirror`：把 `steam_space_ore_processor` 那批配方复制过来） |
| `com.gtlmodular.common.data.host` | 熔岩主机（地心枢纽）的图案层与模块预览位点（`ModulePreviewSlots`） |
| `com.gtlmodular.common.machine` | **机器实现**：熔岩主机与 3 个熔岩模块、**猪咪样板总成**（普通 / 超级 / 惰性三套 + 归档工具）、逐槽隔离（`PatternSlotScope` / `PatternTypeGate` / `PatternSlotIsolationGuard`）、`MultiTypeLookupHost`（查找侧收窄的机器侧契约） |
| `com.gtlmodular.common.machine.nanochip` | **本工程的重心**：NAC 主机（`NanochipAssemblyComplexMachine`，8450 行）、**独立跨配方线程**（`NanochipThreadedRecipeLogic` / `NanochipConcurrentBatches` / `NanochipThreadHost` / `FairCandidateSelector` / `NanochipCandidateSearch` / `NanochipParallelFit`）、14 种模块（`NanochipModuleKind` / `NanochipModuleMachine`）、纳米校准（`NanochipCalibration` + 面板）、结构检查三道门与预检 |
| `com.gtlmodular.common.machine.boost` / `.fuel` | 线圈增益计算；双流体税与"休闲降档超频" |
| `com.gtlmodular.common.block` / `.item` / `.event` | NAC 玻璃方块；样板归档工具物品；喷漆、方块移除、剥离器嵌入等事件处理 |
| `com.gtlmodular.common.compat` | 与 GTL-Enhancedcore 的**软依赖**对接（把猪咪登记为它的 IV 样板仓；反射调用，缺了不影响启动） |
| `com.gtlmodular.common.command` | 唯一的游戏内命令 `/gtlmodular_nac`（NAC 自检） |
| `com.gtlmodular.client.config` | Cloth Config 图形配置屏（`Mods → 配置` 入口） |
| `com.gtlmodular.client.ctm` | **连接纹理（CTM）**：机壳 / 玻璃 / 线圈的 47 格自动连片，及"外壳罩"所需的槽位索引与刷新跟踪 |
| `com.gtlmodular.client.renderer` / `.render` | NAC 外壳罩渲染器（`NacMachineShellRenderer`）、真空管道渲染器、样板外壳混合渲染 |
| `com.gtlmodular.client.jade` | **Jade 显示**：独立线程上限 / 在用条数、逐条线程进度条、真空仓颜色名 |
| `com.gtlmodular.client.jei` | JEI 插件：把 NAC 孤儿封装件在 JEI 里隐藏 |
| `com.gtlmodular.mixin` + `.gtceu` / `.gtlcore` / `.enhancedcore` | 混入补丁。**只有 `gtlmodular.mixins.json` 里列的那几条在生效**（见 §6 雷区 1）；`enhancedcore` 那几条**当前未注册** |

---

## 4 · 主机制 5 条

### 4.1 NAC 主机（两档运行）

`NanochipAssemblyComplexMachine`（`common\machine\nanochip\`）是加工主体，挂本模组自己的配方表
`NanochipRecipeTypes.ASSEMBLY_COMPLEX`（id `gtlmodular:nanochip_assembly_complex`），
该表通过 GTCEu 的"代理配方表"机制索引进电路组装机表的**同一批 GTRecipe 对象**
（`NanochipRecipeTypes.ProxiedRecipeType`）。

**两档运行**：**直通档**（没装组装矩阵模块）= 样板总成直供、并行从 `DEFAULT_DIRECT_PARALLEL_CAP = 1024` 起、
时长下限 20 秒（`DIRECT_MIN_DURATION_TICKS = 400`）；**全流程档**（组装矩阵模块在线）= 行走完整真空链路、
**每条配方各自满并行**、时长下限 5 秒（`FULL_MIN_DURATION_TICKS = 100`）。
超频一律**封顶 2 次**（`MAX_OVERCLOCKS = 2`）且优先于并行，配方时长**不会被压到下限以下**（照抄原版）。

### 4.2 14 类模块

`NanochipModuleKind` 枚举定义 **14** 种模块（组装矩阵 / 元件预处理装置 / 全板浸没设备 / 超高能蚀刻阵列 /
纳米精度切割仓 / 纳米精度导线追踪器 / 超导线束分离器 / 光学·奇异·寰宇·超因果四个优化组织器 /
加速生物调谐器 / 纳米封装卷绕器 / 纳米组件分流器）。
每种是一个 7×3×7 的小多方块（`NanochipComplexPattern.MOD_W/H/D`），在自己成形后**自动挂到附近的 NAC 上**
（宿主侧 `IModularMachineHost` + `NanochipThreadHost`），提供各自加成。
模块生效需要**框架扫描 + 快照兜底**（`NanochipAssemblyComplexMachine.scanAndConnectModules()`）。
一部分模块有**持续耗材**（例：超导线束分离器 1000 mB/s `gtceu:super_coolant`，见 `NanochipModuleUpkeep`）。

### 4.3 跨配方多线程（L3）

这是与 gtlcore 原生最大的差别。**一般 gtlcore 机器用的是"多配方批量合并"（L2）**：
一次搜索把所有命中配方合并成**一条**配方执行，**只有一份 progress**（`MultipleRecipesLogic`，
`remain = getMaxParallel() × 64L`）。
而 NAC 走的是 gtladditions 血统的 **`NanochipThreadedRecipeLogic`**
（`extends GTLAddMultipleTypeRecipesLogic`）：同时挂起 N 条**不同**配方，
**每条各自计时、各自扣料出料**，谁先跑完谁让位（`FairCandidateSelector` 稳定轮转，
避免常备配方霸占线程）。

- 总开关：`NanochipAssemblyComplexMachine.NAC_INDEPENDENT_THREADS`（**当前值 = `true`**），
  判定点唯一 = `createRecipeLogic(...)`（`@Override` 回来的 `RecipeLogic`）。
- 线程数：`getMultipleThreads()` = 电压基线 + 天球分歧引擎仓追加，夹在 `MAX_CROSS_RECIPE_THREADS = 256`。
- 批次推进全在 `NanochipConcurrentBatches.tick(...)`；存档键 `gtlmodular_nanochip_threads`（**纯追加**，老档读不到即安全回退）。
- 机器侧只需回答四件事（`NanochipThreadHost`：最多几条 / 本轮搜哪些类型 / 这条候选能不能用 / 轮转游标），
  ⇒ **零 mixin、零结构改动、零存档格式改动**。

### 4.4 纳米校准

`NanochipCalibration` 记录最近 **10 万个**产出电路（`BATCH_SIZE = 1000 × BATCH_COUNT = 100 = HISTORY_SIZE`），
按 **8 个族**（基础 / 晶体 / 湿件 / 生物 / 光学 / 奇异 / 寰宇 / 超因果）的占比自动给出 3 档增益
（时长倍率 / 生效族等），面板由 `NanochipCalibrationPanel` 绘制。

**只有被允许的配方才计入校准**：走配置项 `nanochipComplex.calibrationRecipeWhitelist`
（默认 = 那 30 条带 `"data": {"circuit-calibration": "<族名>"}` 元数据的配方），
目的是把"换算"（真件 ⇄ CC 的**搬运**，不产电路）挡在校准之外。
入口闸 = `NanochipAssemblyComplexMachine.CALIBRATION_WHITELIST_ENABLED`（默认 `true`）。

### 4.5 CC 换算

「真件 ⇄ 封装件（CC）」的双向换算在 NH 里是**控制室的仓位行为、不是配方**，所以 AE 样板总成推不动它。
本模组把它落成**真配方**：`NanochipRecipeTypes.CONVERSION`（`gtlmodular:nanochip_conversion`），
并补了 201 条对偶「CC → 真件」（`data\gtlmodular\recipes\nanochip\conversion_reverse\`）。

简单物流模式下，`getRecipeTypes()` 会把换算表**追加**到主机类型表**末尾**（下标 0 仍是主机自己的表，
既有槽位识别 / 模式下标不受影响），且 `NAC_MULTI_TYPE_RECIPES = true`（默认）时
**同一轮就搜全部类型** ⇒ 组装与换算不必切模式。
配套要求：`searchesAllRecipeTypes()` 返回真 ⇒ 两台样板总成的**查找侧收窄会放行全量**，
否则「非激活类型」的冷样板槽会永久饥饿。

---

## 5 · 调试入口

| 入口 | 怎么用 | 出处 |
|---|---|---|
| **日志前缀** | 全部日志带 `[gtlmodular]`（logger 名 `GTL-Modular`） | `GTLModular.java:26`；例 `:91`、`NanochipSelfCheck.java:85-90` |
| **游戏内自检命令** | **`/gtlmodular_nac`**（**已核实：就这一个命令、无子参数、权限等级 0、单人存档可直接用**）。输出 `=== NAC 自检（N 项，失败 M 项）===` + 逐行明细，失败行以 `✗` 开头 | `common\command\GTLModularCommands.java:22`、`:37-52` |
| **进世界自动自检** | `ServerStartedEvent` 时**自动跑一次并写日志**（不需要玩家做任何操作） | `GTLModularCommands.java:56-62` |
| **Jade** | 两个 provider：① 真空传送带仓显示「颜色: 紫色」（用颜色本身上色）；② **NAC 独立线程状态** —— `独立线程：上限 %s 条 · 在用 %s 条`、逐条线程的绿色进度条、`本刻在跑 K 条线程 · K′ 种配方（按配方 id 去重）` | `client\jade\NanochipJadePlugin.java:20-59`；键 `NanochipJadeKeys.java:117-153`；文案 `lang\zh_cn.json:235-247`、`:271` |
| **Jade 开关** | 配置键 `config.jade.plugin_gtlmodular.threaded_recipe_logic` = 「独立线程状态」 | `zh_cn.json:247` |
| **图形配置屏** | `Mods → 配置`（Cloth Config）；改完实时生效并打一行 `配置已更新（…）` | `GTLModular.java:34-37`、`GTLModularConfigScreen.java:458` |
| **CTM 诊断** | 日志搜 `CTM:`（机壳连片、变体 sprite 就绪度、硬上限回退、白名单不一致） | `CtmClientSetup.java:311-543` |
| **线程/并行归因日志** | 搜 `NAC 独立线程`、`配方定档`、`输入面`、`补料`（多数限流 1 秒一条，绝不刷屏） | `NanochipThreadedRecipeLogic.java:3241-3370`、`NanochipAssemblyComplexMachine.java:4633`（`logCraftedRecipe`） |

---

## 6 · 雷区 3 条

### 6.1 ★ 改 `MOD_ID` / 命名空间 = 炸存档

`GTLModular.MOD_ID = "gtlmodular"` 是**机器方块 id、配方表 id、物品 id、语言键、数据包路径、mixin 包名**的共同前缀。
改它 ≈ 所有方块与物品从存档里消失。**永远不要为了"换个更好听的名字"去动它。**

同一类"三处必须一致"的还有 mixin 文件名：`gtlmodular.mixins.json`、
`mods.toml:30-31` 的 `[[mixins]] config=`、`build.gradle:120` 的 `MixinConfigs` ——
**改一处漏两处 = mixin 静默不加载**（不报错，只是补丁没生效）。

另外：`src\main\resources\gtlmodular.enhancedcore.mixins.json` 是**死文件**
（未在任何地方注册，且列了一条从来没有源码的 mixin）—— 详见交接文档 §2。

### 6.2 ★ 兜底开关的默认值 = 行为，不是"建议"

本工程有大量 `public static final boolean` **编译期常量**，它们**不是配置项，是当前行为**。
两个方向都要小心：

- **不要顺手把默认关的打开**。例：`NAC_OUTPUT_AMOUNT_TO_CEILING`
  （`NanochipParallelFit.java:255`）一开，若出料侧不是 **ME 扩展输出仓**，
  `matchRecipeOutput` 会判"放不下" ⇒ 二分收敛到 0 ⇒ **机器静默不开工**（不刷屏、不崩，就是不干活）。
  `ALLOW_MODE_SWITCH_WITH_RUNNING_BATCHES`（`LazyPatternAssemblyPartMachine.java:3450`）
  改回 `true` 会让换模式**丢掉整张批次表且不退料**。
- **改完必须 `javap` 核实例 jar 里的常量值** —— javac 会把 `static final` 内联进每个使用点，
  "我改了源码" 到 "它生效了" 之间隔着**重编 + 落位**两道门（B28）。
- 当前所有默认关的开关**逐条状态与后果**见
  [04_已知问题与待办.md](E:\大鲸鱼工作区\_deliver\接手文档\04_已知问题与待办.md) §11。

### 6.3 ★ 两台样板总成必须同改

`MoltenPatternAssemblyPartMachine`（**猪咪**）与 `LazyPatternAssemblyPartMachine`（**惰性猪咪**）
是**同一套能力的两个实现**（惰性版多了"惰性建槽 + 一次性批量扩容"）。
二者共享大量同源逻辑与同款开关（`LOOKUP_SIDE_SLOT_NARROWING`、
`ALLOW_MODE_SWITCH_WITH_RUNNING_BATCHES`、`KEEP_FOREIGN_SLOT_RECIPE_CACHE_ON_MODE_SWITCH`、
`FILTER_CACHED_RECIPES_BY_AVAILABLE_TYPE_ON_READ`、`DIRECT_SLOT_RECIPE_SUPPLY_REQUIRES_TYPE_INDEX` …）。

**只改一台 = 两台行为分叉**，而且分叉只在玩家用另一台时才暴露。
改样板总成侧的任何东西，**先在两个文件里各 `grep` 一次同名符号**。

### 6.4（附）同 modId 只允许一个 jar

`build.gradle:8-11` 逐字写着：换版本号后 **`mods\` 目录里必须删掉旧 jar**，
否则同一个 modId 两份 jar，**Forge 会以 `Duplicate mod` 拒绝加载**（直接崩）。
多余的挪 `<实例>\_PARKED_JARS\`，**不要删**。

---

## 7 · 进一步阅读

| 文档 | 看什么 |
|---|---|
| [04_已知问题与待办.md](E:\大鲸鱼工作区\_deliver\接手文档\04_已知问题与待办.md) | ★ **接手第一份**：13 组待办，每条含「现状（文件:行号）/ 建议怎么改 / 风险 / 证据等级」；**§0 有一条前提更正必读** |
| [_census\错误历史与工作经验.md](E:\大鲸鱼工作区\_census\错误历史与工作经验.md) | ★ **本工程最值钱的一份**：§一 症状速查表（S1–S17，按"你看到什么"索引，不按"你以为是什么原因"）+ §二·续（B22–B28：「静默兜底重载」「×p² 记账」「部署了≠生效了」）+ §六 环境故障手册 |
| [_census\真实多线程_机制与我方现状.md](E:\大鲸鱼工作区\_census\真实多线程_机制与我方现状.md) | 「真实多线程」的三层定义（L1 并行 / L2 批量合并 / **L3 跨配方线程**）、gtlcore 与 gtladditions 的逐字节码对照、星律（shanhai）对照。**注意：它描述的是改造前状态**（见交接文档 §0） |
| [_census\按槽开批_实施记录.md](E:\大鲸鱼工作区\_census\按槽开批_实施记录.md) 与 [_census\NAC合成链路_原初对照与重构方案.md](E:\大鲸鱼工作区\_census\NAC合成链路_原初对照与重构方案.md) | 「K 上不去、并行被夹在首槽」的完整机制与治本方案（对应 `PER_SLOT_PARALLEL_BOUND`） |
| [_census\独立跨配方线程_实施记录.md](E:\大鲸鱼工作区\_census\独立跨配方线程_实施记录.md)、[_census\NAC工作原理_全解析.md](E:\大鲸鱼工作区\_census\NAC工作原理_全解析.md) | 独立线程的落地过程；NAC 整体工作原理 |
| [_census\我造的东西_清单与使用状况.md](E:\大鲸鱼工作区\_census\我造的东西_清单与使用状况.md) | 本模组自己新增的每一件材料 / 流体 / 框架：**谁产出、谁消耗、有几个消费方**（判断"孤儿件"的第一入口） |

> `_census\` 目录里有 **300+ 份**施工与审计报告。**别从第一份开始读** ——
> 先读上面这 6 份，其余按 §一 症状速查表**反查**。

## 8 · 目录速览

- `build.gradle`：版本号（**唯一来源**）、JDK 21 toolchain、`libs\` flatDir 依赖、`processResources` 占位符展开、jar 的 `MixinConfigs`
- `src/main/java/com/gtlmodular/`：模组代码（108 类）
- `src/main/resources/`：`META-INF/mods.toml`、两份 mixin json、`assets/`（语言、材质、模型）、`data/gtlmodular/recipes/`（本模组全部配方）
- `src/generated/resources/`：datagen 产物（`runData` 输出）
- `libs/`：本地依赖 jar（flatDir，17 个）
- `run/`：`runClient` / `runServer` / `runData` 的工作目录
- `docs/`：**早期**设计方案与调研记录（`01_设计方案_GTL熔岩模块化机组.md`、`02_调研记录.md`、`03_线圈收益速查.md`、`线圈方块一览.md`、`名称与机制.md`）。
  ⚠ **早期文档**：模组重心已从"熔岩模块化机组"转到 NAC，**现行文档以 `_census\`（300+ 份施工/审计记录）与 `_deliver\` 为准**

---

## 9 · 哦鲸鲸终端（移植自 GT-Shanhai）

**物品**：`gtlmodular:whale_terminal`（中文名「哦鲸鲸终端」，英文名 "Oh Whale Terminal"）；工作台配方：`gtlcore:ultimate_terminal` + 2×`gtceu:bronze_ingot` + 1×`gtceu:steel_ingot`（无序合成），
放在创造标签页 **「更多实用设备-工具」**（旧标签页已从「GTL·熔岩模块化机组」改名为「更多实用设备-机器」）。

**功能**（完整移植自山海的「山海终极终端」，见下方授权）：

| 操作 | 行为 |
|---|---|
| 右键空气 | 打开设置屏（重复层数 / 镜像 / 功能部件替换 / 绝对替换 / AE 模式 / 下单 / 无仓室 / 拆解 / 模块检查 / 替换等级） |
| 左击多方块控制器 | **只扫描**：聊天摘要 + 世界内 15 秒方块高亮 + 完整报告链接 |
| 右键多方块控制器 | 扫描结构 → 备料（背包 / AE 绑定网络）→ 施工；有替换计划时先准备、潜行确认 |
| 潜行右击 AE 节点 | 绑定 AE 网络（取料 / 回收 / 自动下单） |
| 潜行右击控制器 | 确认施工 |
| 拆解模式右键控制器 | 按结构回收材料（有 AE 入 AE，无 AE 入背包） |
| 聊天里的「[点击查看详细报告]」 | 打开完整报告屏（按类别筛选、滚动、点击复制坐标、显示坐标方框） |

**代码位置**：
- 服务端：`com.gtlmodular.common.item.ShanhaiUltimateTerminalBehavior` + `common/item/terminal/**`
  （结构扫描 Planner / 施工 Executor / 备料 MaterialService / AE 下单 CraftingManager / 报告 Report）
- 客户端：`com.gtlmodular.client.gui.terminal.WhaleTerminalScreen`（设置屏）、
  `WhaleTerminalReportScreen`（报告屏）、`ShanhaiStructureHighlightClient`（高亮）、
  `ShanhaiTerminalScanInput`（左键扫描）、`TerminalChatCommands`（报告链接命令）
- 网络：`com.gtlmodular.network.GTLModularNetwork`（3 个包）
- 资源：`assets/gtlmodular/textures/item/whale_terminal.png`（16×128，8 帧）、
  `textures/gui/whale_logo.png`、`models/item/whale_terminal.json`

**授权与协议**：终端逻辑移植自 **GT-Shanhai**（作者：山海恒长在 / dishanhai，
https://github.com/dishanhai/gtl_shanhai-dishanhai ，LGPL-3.0），**已获作者授权**。
移植文件均保留原始版权头；按 LGPL-3.0，这些文件的修改版需继续以 LGPL-3.0 提供源码。
