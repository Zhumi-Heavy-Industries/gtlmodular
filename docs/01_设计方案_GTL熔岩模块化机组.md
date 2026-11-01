# GTL·熔岩模块化机组 —— 设计方案

> 目标整合包：**[GTL] 格雷科技休闲版 (GregTech Leisure)**，实例：`GTL推荐新手玩版（较难）`
> MC 1.20.1 / Forge 47.3.7 / GTM(gtceu) **1.4.4** / LDLib 1.0.33.b / GTLCore 1.2.3.2-fix2 / gtladditions 3.2.8Custom_SubSpace-fix2(Java 21) / GTLsupb 3.4.beta.7 · 编译 JDK 21 / 字节码目标 17
> 文档状态：**v0.1 草案，待用户确认后冻结** · 日期：见 git

---

## 0. 一句话目标

做一个 GTL 附属模组（暂名 **gtlmodular / “GTL·熔岩模块化机组”**），新增 **1 台主控制器（主体）+ 3 台可物理邻接的模块多方块**（太空电梯“主控+模块”式）。三个模块分别**沿用整合包内已有三台机器/配方表的内容**（电力高炉 / 星核剥离 / 天基矿石处理），但以**新机器名**注册；模块运转时**按烈焰高炉公式持续消耗岩浆（熔岩）与液态烈焰**（双流体，各自公式、电压每升一级需求 ×4，天基炼矿模块不再吃原机的火箭燃料税）；模块**换线圈可提升并行/降低能耗**，其中高炉模块沿用 GTM 的**线圈提温→无损超频**机制。原机器一律不改动。

---

## 1. 现状盘点（已在本机实例核对）

### 1.1 开发依赖 jar（已复制到 `gtl-dev/libs/`）

| 用途 | 文件 | 来源 |
|---|---|---|
| 编译主依赖 | `gtceu-1.20.1-1.4.4.jar` | 实例 mods 目录（GTM 官方 Release 同款） |
| 运行/编译参考 | `gtlcore-1.2.3.2-fix2.jar` | 实例 mods 目录 |
| 配方来源(星核剥离/天基矿石处理) | `gtladditions-3.2.8Custom_SubSpace-fix2.jar`（Java 21 字节码 ⇒ 编译须 JDK 21） | 实例 mods 目录 |
| 参考实现 | `GTLsupb-3.4.beta.7.jar`、`gtmthings-*.jar`、`GTMAdvancedHatch-*.jar`、`extendedae_plus_gtladd-1.0.3.jar`、`infinitepower-2.0.jar` | 实例 mods 目录 |
| 基础库 | `ldlib-forge-1.20.1-1.0.33.b.jar` | 实例 mods 目录 |

说明：GTM **无公开 dev maven**（已实测 saps.dev / firstdark.dev / Modrinth Maven 均无 gtceu 1.4.4），LDLib 有 maven（`maven.firstdark.dev/snapshots`，坐标 `com.lowdragmc.ldlib:ldlib-forge-1.20.1:1.0.33.b`）。因此工程采用 **flatDir 本地 jar + LDLib maven** 的组合；jar 放在项目 `libs/`（脚本一键从实例复制）。

### 1.2 三台“配方来源”机器（全部以 `gtceu:` 为注册命名空间，行为已在语言文件/tooltip 核实）

| # | 原机器（保留不动） | 注册 ID | 配方表(RecipeType) | 关键机制（来自官方 tooltip/语言文件） |
|---|---|---|---|---|
| A | 电力高炉 | `gtceu:electric_blast_furnace` | `electric_blast_furnace`(BLAST) | 线圈决定炉温；每超配方温度 900K → 耗能 ×95%（不计超频）；每超 1800K → 1 次超频 100% 效率（**无损超频**）；超过 MV 每级电压 +100K；gtlcore 改写耗时倍数 = max(配方温度/炉温, 0.5) |
| B | 宏原子谐振碎片剥离器 | `gtladditions:macro_atomic_resonant_fragment_stripper`（机器块语言键在 `gtceu` 空间，见 §1.3 注） | `star_core_stripper`（星核剥离） | 线圈温度每高出基准 1200K → 并行 +300；需 星辉(Starmetal) 级以上线圈；可嵌最多 256 星规矩阵，并行倍率 = 2^(6+10·((n-1)/184)²)；拥有跨配方并行、∞并行等 |
| C | 天基无尽集成矿石处理厂族 | 语言键 `block.gtladditions.space_infinity_integrated_ore_processor` | `space_ore_processor`（天基矿石处理） | 原机每次工作前耗 100B 星能火箭燃料 —— **新模块 C 不沿用该燃料税**，统一改为岩浆+液态烈焰双流体税（§2.3） |

> 注：星核剥离配方表与“碎片世界采集”配方表（`fragment_world_collection`，gtlcore 的 ulv/large 碎片世界采集器）**不是同一张**。经你确认，本次模块 B 走 **`star_core_stripper`（星核剥离）** 配方；如后续还要“碎片世界采集”配方组，模块接口预留即可（§5.3）。

### 1.3 烈焰高炉消耗公式（岩浆税的母本）

gtlcore 烈焰高炉（`gtceu:blaze_blast_furnace`）tooltip 原文：

> 需每秒提供 **10×(功率÷120)^½ mb** 的液态烈焰；最大并行固定 64。

本模组把“每秒持续提供流体”的机制复制过来，流体换成 **岩浆（熔岩）**，并叠加“电压每提升一级，所需量 ×4”。

---

## 2. 总体设计

### 2.1 结构形态（用户已选：主控制器 + 独立模块多方块，太空电梯式）

```
             [ 模块 B ]    [ 模块 C ]
    [模块 A]      |             |
 熔岩高炉单元    |             |
      └──────────┴── 主控制器(主体) ──┴──────────┐
                   熔岩枢纽·模块化主机            │ 物理邻接、互相识别
                                                ▼
        每个模块：独立多方块、独立控制器、独立配方表(共享原表)
        主机：识别已组装的模块 → 汇总 GUI / 状态 / 供电与流体
```

- 主控制器与模块在**同一水平层、面贴面**或隔固定间隙（具体构型待实现阶段定，参考 gtlcore 太空电梯 `SpaceElevatorMachine + SpaceElevatorModuleMachine` 的邻接识别方式）。
- 每个模块都能**脱离主机独立运转**（保守起点：模块可独立工作，主机仅做“联合体/汇总”增强）；若后续要“必须挂主机才能开”，通过配置开关。

### 2.2 命名提案（中文名 + 注册 ID，均可改）

| 新机器 | 中文名 | 注册 ID | 配方来源 |
|---|---|---|---|
| 主控制器 | 熔岩枢纽·模块化主机 | `molten_module_host` | ——（无配方，负责模块汇总/供流/供电状态） |
| 模块 A | 熔岩高炉单元 | `molten_blast_module` | `gtceu:electric_blast_furnace`（共享同表） |
| 模块 B | 熔岩星核剥离单元 | `molten_stripper_module` | `gtceu:star_core_stripper`（共享同表） |
| 模块 C | 熔岩天基炼矿单元 | `molten_space_ore_module` | `gtceu:space_ore_processor`（共享同表） |

配方“沿用原内容、名字是新机器名”：实现为**新机器直接引用原 RecipeType 实例**（配方归属在 `GTRecipeType`，不属机器；GTM 自带 MULTI_SMELTER 同时挂 FURNACE+ALLOY_SMELTER 两张表的先例可证）。原机器不动、数据包更新自动同步。JEI 中配方会同时出现在原机与新机名下（与 MULTI_SMELTER 现象一致，属预期）。

> 备选（若你坚持“每个新机一张独立新表、仅拷贝内容”）：为每模块注册新 RecipeType 并在加载时把原表配方逐条拷入。文档默认选**共享引用**，理由是维护简单、不破坏服务端/客户端 JEI 同步；切换成本低，方案里预留接口（§5.2）。

### 2.3 岩浆税机制（核心玩法差异点）

1. **流体**：岩浆。流体 ID 待游戏内确认（`minecraft:lava` 或 GT 系注册的 lava），做进配置文件 `gtlmodular-common.toml`，默认值实现阶段首跑定。
2. **速率公式**（运行时逐 tick 折算，参考烈焰高炉语义——每“工作秒”计）：
   `每秒需量 R = 10 × sqrt(有效功率 EU/t ÷ 120) [mb/s]`
   再乘电压系数：`K = 4^(tier − 基准tier)`，其中 tier 取模块能源仓/输入仓电压等级（基准 tier 默认 LV=1，可配置）。
   → 最终 `每秒需量 = R × K`；不满足则模块**暂停工作/降级运行**（降级还是暂停，配置开关，默认暂停并亮缺料灯）。
3. **双流体强制（已冻结）**：**岩浆 与 液态烈焰都要按上述公式持续供给**（各自一份速率、各自 ×4）；任何一方不足即按缺料处理。流体输入走模块自带的流体输入仓（`FluidHatch`）或主机统一供给（联合体模式下主机可代供，方向见 §5.3 未决项）。
4. **模块 C（天基炼矿）特殊**：不再消耗原机的星能火箭燃料，只吃 §2.3 的双流体税。

### 2.4 线圈机制（模块通用 + 模块 A 特化）

统一读取结构中的**加热线圈（heating coil）**：

- **通用**：每换高一级线圈 → 模块**并行上限提升**、**单配方能耗下降**（能耗系数可配），具体数值表放配置文件。
- **模块 A（高炉）特化**：完全沿用 GTM EBF 温度规则 ——
  - 炉温 = 线圈基准温 + 电压加成（超过 MV 每级 +100K，沿用 GTM/gtlcore 现行公式）；
  - 耗能：炉温每超过配方温度 900K → 单次超频耗能 ×95%；
  - **无损超频**：炉温每超配方温度 1800K → 该次超频效率 100%；
  - 耗时下限系数 max(配方温度/炉温, 0.5)（gtlcore 现行）。
- **模块 B 特化（可选项，默认按通用先做）**：参考宏原子剥离器“每高出 1200K → 并行 +300、星辉级以上生效”，若数值想完全复刻，实现阶段照搬其公式并做成配置。

### 2.5 与原机的兼容边界

- 原 `electric_blast_furnace` / 宏原子剥离器 / 天基矿石机：**不改不删**，仅作为配方表与机制蓝本。
- 新机注册进 `gtceu:` 命名空间还是自有 `gtlmodular:` 命名空间？→ **机器 ID 与方块走 `gtlmodular:` 命名空间**（避免覆盖/依赖包内 lang 覆盖），配方引用 `gtceu:` 的 RecipeType。若需要原机样式外观，用渲染器/模型资源自行提供，不依赖 gtlcore/gtladditions 资源。

---

## 3. 技术实现方案（GTM 1.4.4 / Forge 47.3.7）

### 3.1 依赖与工程

- Gradle 8.x + ForgeGradle（1.20.1 线，`net.minecraftforge.gradle:5.1.+` 或 6.x 按 MDK 47.3.7 模板）+ Java toolchain **17**（本机 JDK 25 过新，配 foojay-resolver 自动下载 17）。
- 依赖：`flatDir libs/` 引入 gtceu-1.20.1-1.4.4 + gtlcore + gtladditions + ldlib 本地 jar（`fg.deobf`），LDLib 另可从 firstdark maven 拉；**版本锁死**（GTM 破坏性更新警告）。
- 产物：Forge 1.20.1 模组 jar（不发布 maven）。

### 3.2 代码骨架（包结构）

```
com.gtlmodular
├─ GTLModular.java                 # @Mod 入口, modid=gtlmodular
├─ api/
│  ├─ module/IModularModule.java   # 模块能力接口(配方组枚举/线圈等级/岩浆税参数/电压)
│  └─ host/IModuleHost.java        # 主体能力接口(注册/识别模块)
├─ common/
│  ├─ data/
│  │  ├─ GTLModularMachines.java   # REGISTRATE.multiblock(...) 注册 4 台机器
│  │  ├─ GTLModularRecipeTypes.java# (备选B才需要；默认不建)
│  │  └─ GTLModularBlocks.java     # 新结构方块(外壳/线圈位判定等)
│  ├─ machine/
│  │  ├─ MoltenModuleHostMachine.java        # 主体机器类
│  │  ├─ MoltenBlastModuleMachine.java       # 模块A(继承Coil式EBF基类+岩浆税)
│  │  ├─ MoltenStripperModuleMachine.java    # 模块B
│  │  ├─ MoltenSpaceOreModuleMachine.java    # 模块C
│  │  └─ logic/
│  │     ├─ MagmaTaxLogic.java      # 每秒岩浆税结算(公式§2.3)
│  │     └─ CoilBoostLogic.java     # 线圈并行/能耗公式(§2.4)
│  └─ registry/GTRegistries.java    # 按 ResourceLocation 取 RecipeType/机器（弱引用 GTL）
└─ config/GTModularConfig.java      # 岩浆/烈焰开关、公式参数、tier基准、线圈数值表
```

### 3.3 关键 API 依据（读 GTM 1.4.4 源码核对）

- 注册：`GTRegistration.REGISTRATE.multiblock("id", MachineClass::new).recipeType(GTRecipeTypes.X)...register()`
- 配方归属在 `GTRecipeType`（内含 `GTRecipeLookup`）；机器定义仅持有引用 → **一台 RecipeType 可被多台机器共享**（MULTI_SMELTER 挂 FURNACE+ALLOY_SMELTER 先例）。
- 线圈基类：GTM `CoilWorkableElectricMultiblockMachine`（onStructureFormed 读取 `CoilType` matchContext → `getCoilType()/getCoilTier()`）；改并行/能耗/温度用 `recipeModifier(...)`（参考 `GTRecipeModifiers.ebfOverclock`）。
- 弱引用 GTL 配方表：`GTRegistries.RECIPE_TYPES.get(ResourceLocation)` 运行时取 `GTRecipeType`，**编译期不强依赖 gtlcore/gtladditions 类**（避免“更新即崩”）；如它们不在场则优雅跳过注册（配置/日志提示）。
- 参考蓝本（jar 内已核实存在的类）：
  - gtlcore：`CoilWorkableElectricMultipleRecipesMachine`、`MultipleRecipesLogic`、`IModularMachineHost`、`IModularMachineModule`、`SpaceElevatorMachine(+ModuleMachine)`、`BlazeBlastFurnace`(烈焰公式母本)、`WorkableElectricParallelHatchMultipleRecipesMachine`
  - gtladditions：`GTLAddMachines`、`GTLAddMultipleRecipesLogic`、`MutableMultiBlockModify`、`NewMultiBlockMachineController`、`SubspaceCorridorHubIndustrialArray(+ModuleBase)`、`IntegratedOreProcessor`、`ElectricBlastFurnace`
  - gtlsupb：`ZeroEnergyMultiTypeMachine`、`MultiTypeCoilElectricMultiblockMachine`、`FragmentWorldCollector`
- GTLCore 官方源不公开，GitHub 有镜像 `EasterFG/GTLCore`（分支 gtl-1431）；gtladditions/gtlsupb 无公开源 → 编译依赖走本地 jar + 反编译取证（开发时用 Vineflower/CFR 抽查类结构，不随包分发）。

### 3.4 GUI / JEI / 交互

- 主控制器 GUI：显示已识别模块清单 + 各模块岩浆供给状态；模块 GUI：配方进度、并行、线圈温度/等级、岩浆缺料告警（沿用 GTM 机器 UI 框架 `MachineDefinition`/FancyMachineUIWidget）。
- JEI：模块机器 category 直接复用对应 RecipeType → 无需额外 JEI 代码。
- Jade/TOP 提示可选（后续迭代）。

---

## 4. 迭代里程碑

| 里程碑 | 内容 | 验证 |
|---|---|---|
| M0 环境 | Gradle 工程跑通 `runClient`，注册一个最小多方块（占位）进游戏 | 进存档放置成功 |
| M1 配方共享 | 注册模块 A(EBF) 引用 `gtceu:electric_blast_furnace` 表，结构=简化 EBF，验证能出钢/出配方 | JEI 可见 + 能跑 BLAST 配方 |
| M2 岩浆税 | MagmaTaxLogic + 配置；验证公式与断料暂停 | 流体仓计量、断料停转 |
| M3 线圈 | CoilBoostLogic + EBF 温度/无损超频复刻 | 换线圈对比并行/能耗/温度 |
| M4 模块 B/C | 星核剥离、天基矿石处理两模块（弱引用 RecipeType） | 两者配方可跑 |
| M5 主机 | 主体机器 + 邻接识别 + 汇总 GUI + 联合供岩浆 | 挂载/卸载识别、GUI 状态 |
| M6 打磨 | 数值表配置化、lang/模型、兼容性回归（原机不受影响）、可选 碎片世界采集配方组 | 全流程回归 |

---

## 5. 未决问题（需要你确认/由实现阶段验证）

1. **岩浆流体 ID**：游戏内是 `minecraft:lava` 还是 GT 系 lava？（实现阶段进游戏查 JEI，或你直接告知）
2. **×4 规则口径**：电压每提升一级 ×4，基准电压按 LV=0 还是按某电压起算；是否 **岩浆与液态烈焰都强制**（§2.3.4 暂按“岩浆强制、烈焰可选”）。
3. **主机职责边界**：模块能否脱离主机独立运转？（默认：能）
4. **数值表初值**：线圈→并行/能耗系数、岩浆税断供是“暂停”还是“降级”等初值我拟好后你在游戏里试调。
5. **外观**：新机方块纹理先用简单色块/借用原机渲染，避免一开始做美术。
6. 本方案默认“共享同一 RecipeType”；若你要求“每模块独立新表、配方内容复制且名字全换”，说明一下，切到备选 B。

> 相关来源：GTM Release/仓库 https://github.com/GregTechCEu/GregTech-Modern （tag 1.20.1-1.4.4）· GTLCore 镜像 https://github.com/EasterFG/GTLCore · GTL 整合包页 https://www.mcmod.cn/modpack/769.html
