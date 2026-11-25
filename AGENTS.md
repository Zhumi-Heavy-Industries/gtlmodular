# gtlmodular · 代码地图与硬规则（给 agent）

> 目的：**跳过探索，直接动手。** 30 秒定位到该改的文件，并避开这个仓库已经踩过的坑。
> 面向人类的长介绍在 `README.md`（⚠️ 它的版本号/类数已过时，别引用其中的数字；以 `build.gradle:12` 为准）。
> 工作区层面的须知（哪个仓库是现役、怎么开 worktree、怎么检索）在 `E:\大鲸鱼工作区\AGENTS.md`。

---

## 1 · 一句话与构建

GTL（格雷科技休闲版）的附属模组，Forge **1.20.1 / GTCEu 1.4.4**。~300 个 java、981 个配方 JSON。

```powershell
# 构建（用 mc_gradle 工具，projectDir 传 worktree 绝对路径；不要在 shell 里裸敲 gradlew）
# 产物：build/libs/gtlmodular-<version>.jar
```

- **版本号唯一来源**：`build.gradle:12` 的 `version = '0.3.xxx'`，由 `processResources` 注入 `mods.toml`。**每次交付 +1**。
- 编译 JDK 21，字节码目标 17；`gradle.properties` 固定 `org.gradle.java.installations.paths=D:/Java/jdk-21`。
- 依赖是 `libs/` 下的 18 个本地 jar（gitignore）。**新 worktree 必须把 `libs` junction 过来**，否则依赖解析直接失败。
- 本机 `runServer`/`runClient` **起不来**（ForgeGradle 下载不了 MC 资源）。所以「游戏内验证」永远交给人，别声称已验证。

## 2 · 三个入口类（改东西先看这里）

| 类 | 职责 |
|---|---|
| `GTLModular.java` | `@Mod` 构造。**所有注册窗口**都在这：mod 总线监听器、配方表注册、各子系统 `register()` 调用。加新内容基本都要在这里挂一行 |
| `common/data/GTLModularMachines.java` | 机器注册表（REGISTRATE）与 `init()` 的注册顺序。**顺序有意义**，见 §4.5 |
| `common/data/GTLModularAddon.java` | `IGTAddon` 实现，带 `@GTAddon`（`AddonFinder` 靠这个注解发现它）。**材料注册走 `MaterialEvent`**；`addRecipes(provider)` 是配方自动生成的正式入口（见 §4.3）。注意 `registerMaterials()` 在 1.4.4 已被废弃且不会被调用 |

包布局：`api`(1) / `client`(64) / `common`(200) / `mixin`(27) / `network`(7)。

## 3 · 子系统地图（`common/data/<x>` 定义 + `common/machine/<x>` 实现）

| 子系统 | data | machine | 是什么 |
|---|---|---|---|
| `nanochip` | 11 | **28** | 纳米芯片组装复合体（主机 + 14 外围模块）。最大的一个 |
| `engine` | — | **15** | 通用独立线程引擎 / 线程仓宿主（跨配方多线程的实现） |
| `output` | — | 13 | 输出总成 / ME 输出侧 |
| `fuel` | 2 | 13 | 燃料链（含冷却塔冷凝中枢） |
| `stellar` | 6 | 1 | 恒星炎炀锻炉 + 三味合金材料（`StellarForgeMaterials`） |
| `tentacle` | 7 | 3 | 奈亚拉托提普之触（多方块 + 机壳/物品/配方） |
| `fieldforge` | 4 | 1 | **力场压模零件工厂**（零部件加工，配方代码生成） |
| `hpfc` | 5 | 1 | 高维等离子聚变核心 |
| `component` | 3 | 1 | 部件组装机 / 部件装配线 |
| `coolingtower` | 3 | 1 | 冷却塔 |
| `pcb` | 2 | 1 | 小型 PCB 工厂 |
| `print` | 5 | 2 | 一次性工具 3D 打印 |
| `module` / `host` | 3 / 3 | — | 模块、线程宿主抽象 |
| `thread` | 1 | 2 | **线程仓**（`ThreadHatchMachines` / `IThreadHatchPart` / `ThreadHatchPartMachine`）。通用设施，全模组 20+ 处引用 |
| `requester` | 1 | 11 | **ME库存补充仓**（`gtlmodular:me_requester`）：盯 AE 库存、不足自动下单、产物回网络。自 `3db7d43` 起未改动。挂载点 `GTLModularMachines.java:232`；详细设计见 `docs/ME库存补充仓_技术细节.md` |

## 4 · 硬规则（每条都是这个仓库真实踩过的坑）

### 4.1 一个配方只能有一个来源
**不要**同时用「Java 代码注册」和「数据包 JSON」写同一条配方。配方 id 相同或输入相同，就会在 JEI 里并列两页。

- 实例 A：`stellar/` 的 28 条配方曾同时由 `StellarForgeRecipes.installSimple(...)` 和 `data/gtlmodular/recipes/stellar/*.json` 提供 ⇒ 每条并列两页；`lookup.addRecipe` 因数据包已有同配方返回 `false`，日志表现为「31 条只成功 11 条」。
- 实例 B：`component_parts` 的 112 条 JSON 与代码生成二选一 —— 现已全部改成代码生成并删光 JSON。

判断：**加配方前先 `Get-ChildItem` 一下对应的 `recipes/<分区>` 目录，再 grep 一下 `recipeBuilder`。**

### 4.2 自定义配方表要自己 `new GTRecipeType`
`GTRecipeTypes.register(...)` 把 id 硬编码成 `gtceu:` 命名空间。要 `gtlmodular:` 下的表，就地 `new GTRecipeType(id, group, recipeTypes)` 再把同一个 `ResourceLocation` 登记进三个表：`BuiltInRegistries.RECIPE_TYPE` / `RECIPE_SERIALIZER` / `GTRegistries.RECIPE_TYPES`。参考 `fieldforge/FieldExtruderPartsRecipeTypes.java`。

### 4.3 配方自动生成走 `IGTAddon.addRecipes(provider)`
GTCEu 在数据包配方生成阶段遍历 `AddonFinder.getAddons()` 调 `addon.addRecipes(provider)`（`GTRecipes.java:91`）。

✅ **用这个**：`GTRecipeTypes.X.recipeBuilder(id).inputItems(...).save(provider)`
❌ **不要**再用「`ServerStartedEvent` 里构建 `GTRecipe` + 反射往 `RecipeManager.byType` 塞」那套。它注册晚、依赖反射取字段、还会和数据包打架。历史上 `StellarForgeRecipes` / `FieldExtruderFactoryRecipe` / `CoolingTowerRecipe` / `TentacleRecipes` 都走这条路，正在逐步迁出。

### 4.4 形态配方 GTCEu 会自己生成，别重复写
材料带上 `MaterialFlags.GENERATE_PLATE / GENERATE_ROD / GENERATE_FRAME` 后，GTCEu 会自己出：

| 形态 | 机器 | 关键参数 | 出处 |
|---|---|---|---|
| 板 | bender | EU 24，`mass` t，`circuitMeta(1)` | `MaterialRecipeHandler:361` |
| 双板 | bender | EU 96，`mass×2` t，`circuitMeta(2)` | `PartsRecipeHandler.processPlateDouble` |
| 杆 | extruder | `ingot + rod_extruder_mold`（不消耗）→ 杆×2，EU `6×电压倍率`，`mass×2` t | `MaterialRecipeHandler:291` |
| 框 | assembler | `rod×4 + circuitMeta(4)`，EU 30，64 t | `MaterialRecipeHandler:544` |

⇒ 手写这些**就是重复页**。确有必要时，请给 GT 那条加 `circuitMeta` 区分，或干脆删掉我们的。

材料量口径（**1 锭 = 3628800**）：rod 1814400 / plate 3628800 / bolt 453600 / screw 403200 / ring 907200 / gear 14515200 / smallGear 3628800 / spring 3628800 / smallSpring 907200 / foil 907200 / frame 7257600 / rotor 14515200。

### 4.5 机器/部件的注册顺序有意义
`PartAbility.getAllBlocks()` 是 memoize 的，`Predicates.abilities(...)` 在构建谓词时就把方块表取走了。⇒ 线程仓这类部件**必须先注册，再注册结构**，否则结构谓词拿到空表、部件永远放不进去。见 `GTLModularMachines.init()`。

### 4.6 反编译/依赖源码就在本地
`D:\gtlmodular\deps-src\` 下是**未混淆的 `.java` 源码**：`gtceu-1.20.1-1.4.4`、`gtlcore-1.2.3.2-fix4`、`[Custom-亚空间]gtladditions-3.3.1Custom_SubSpace`、`GTL-Enhancedcore-3.3.3`、`appliedenergistics2-forge-15.4.10`、`kubejs-*`、`jei-*` 等。
**写任何 GT/gtlcore API 之前先在这里读签名**，不要凭记忆写。`libs/` 里还有对应 jar 可 `javap`。

### 4.7 别在主工作树动手
`D:\gtlmodular` 常年 1200+ 未提交改动（用户手改）。开 worktree 干活，见 `E:\大鲸鱼工作区\AGENTS.md` §3。

## 5 · 验证标准（这个仓库的"做完"）

1. `mc_gradle build` 通过（含 `reobfJar`）。
2. **读 jar 核对**关键条目，别只看"编译成功"：
   ```powershell
   Add-Type -AssemblyName System.IO.Compression.FileSystem
   $z = [System.IO.Compression.ZipFile]::OpenRead("build\libs\gtlmodular-<ver>.jar")
   $z.Entries | Where-Object { $_.FullName -like "*关键字*" } | Select-Object -ExpandProperty FullName
   $z.Dispose()
   ```
3. 改动涉及数值时，**写脚本对齐旧数据逐条比**（例：`E:\大鲸鱼工作区\_scratch\stellar-mixer-fix-20261124\compare_stellar.py`、`scan_dupe_recipes.py`）。
4. 明确写出**没验证的部分**（游戏内行为一律属于此类）。

## 6 · 顺手可用的脚本

| 脚本 | 用途 |
|---|---|
| `_scratch\stellar-mixer-fix-20261124\scan_dupe_recipes.py` | 扫配方重复（按 机器类型+输入+输出 规范化） |
| `_scratch\stellar-mixer-fix-20261124\compare_stellar.py` | Java 配方 vs JSON 逐项比对 |
| `_port\tools\apply_to_repo.py --dry-run` | 移植回灌预检（只读，看会写哪些文件） |
