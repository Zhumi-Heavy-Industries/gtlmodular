# GTL更多实用设备 / NAC 独立版

GTL 整合包的附属模组，Minecraft 1.20.1 + Forge + GTCEu 1.4.4。同一仓库构建两个产物，共享 NAC 实现。

- 写代码的规矩（AI 和人都要看）：[AGENTS.md](AGENTS.md)
- 玩家能感知的改动：[CHANGELOG.md](CHANGELOG.md)

## 选择产物与安装

| 产物 | 内容 | COMMON 配置文件（实例目录下） |
|---|---|---|
| `gtlmodular-<版本>.jar` | 完整版：NAC、猪咪/惰性样板总成、样板仓归档工具、地心涌动枢纽及模块 | `config/gtlmodular-common.toml` |
| `gtl_nac-<版本>.jar` | NAC 独立版：主机、14 类模块、真空物流、CC/PC 及配套方块、物品和配方 | `config/gtl_nac-common.toml` |

**两个 jar 二选一安装到实例的 `mods/`，不要同时安装。** 独立版也不能与旧版 `gtlmodular` 同装；Forge 会在加载早期拒绝双装并提示移除其中一个。升级所选产物时删掉它的旧 jar，避免 Duplicate mod。

**已有 `gtlmodular` 存档继续使用完整版。** 独立版的自有注册 ID 使用 `gtl_nac:`，完整版仍使用 `gtlmodular:`；不能直接换 jar 来替换旧存档中的 NAC 方块、物品和配方。本期没有存档迁移器，测试时使用存档副本或另建实例。

两个产物分别读取自己的配置；独立版配置界面只有 NAC 项。完整版的配置文件名、键和默认值保持兼容；独立版的内置校准配方白名单使用 `gtl_nac:`。两者都需要下文列出的实际运行依赖，独立版无需安装本仓库的样板总成。

## NAC 供料与换算

默认开启简单物流模式，可在模组配置中调整，切换后需重启游戏。

- **AE 供料**：主机 H 位或模块 V 位可用 `gtlcore` 原生 ME 样板仓，接网后按 JEI 编码加工样板并下单；模块还需备足耗材液体。原生 ME 样板总成镜像是可选路径，用闪存先右键源样板仓，再右键镜像绑定。
- **普通仓供料**：H/V 位按配方配置物品/流体输入仓和对应输出仓。普通输出仓不会自动回 ME；回网需连接 ME 的输出能力，只有输入能力的样板仓要另配输出设备，镜像的实际能力取决于源仓。
- **换算**：简单模式主机同轮搜索组装与换算配方，无需猪咪样板总成或手动/自动切换主机配方模式。按已有配方进行真件与未处理 CC 的互转；已处理 PC 不可还原。关闭简单模式后按原版 NH 真空仓物流要求布置，不追加简单模式专用换算表。

以上用法已按当前源码和 `deps-src/` 的仓室能力核对，实际 AE 下单、普通仓加工和专服表现仍需游戏验收。

## 第一次搭环境（Windows）

1. 安装 [Git for Windows](https://git-scm.com/download/win) 和 JDK 21（推荐 Eclipse Temurin 21）。
2. 从交接包取出仓库：
   ```
   git clone gtlmodular-<时间>.bundle gtl-modular
   ```
3. 把依赖 jar 放进 `gtl-modular\libs\`（与 `build.gradle` 同级，文件名不要改），清单见下文。
4. 设置一次提交时用的名字：
   ```
   cd gtl-modular
   git config user.name "你的名字"
   git config user.email "你的邮箱"
   ```
5. 第一次构建。会下载 Minecraft 开发环境，需要几分钟到十几分钟：
   ```
   set JAVA_HOME=<JDK 21 的路径>
   gradlew.bat build
   ```
   一条 `build` 同时生成 `build\libs\gtlmodular-<版本>.jar` 和 `build\libs\gtl_nac-<版本>.jar`，分别完成 `reobfJar` 和 `reobfNacJar` 重混淆，可供发行环境加载。两者版本均取 `gradle.properties` 的 `mod_version`。首次构建还会从 Maven Central 缓存 JUnit 5 测试依赖；开发环境、插件以及编译和测试运行依赖都缓存齐全后，才可用 `gradlew.bat build --offline`。

Linux/macOS 在仓库根目录使用 `./gradlew build --offline`；下文所有 `gradlew.bat` 命令同样可替换为 `./gradlew`。

## 日常开发

- 改代码 → `gradlew.bat build` 通过 → `git commit`。一个改动一次提交。
- 查上游依赖的实现：`gradlew.bat decompileDeps`，把 `libs/` 里的 jar 反编译到 `deps-src/`（第一次要几分钟）
- 发新版本：只改 `gradle.properties` 里的 `mod_version`，两个产物自动跟随；在 `CHANGELOG.md` 写上变化，再 build 并完成游戏验收。

| 调试目标 | 客户端 | 专用服务端 | 工作目录 |
|---|---|---|---|
| 完整版 | `gradlew.bat runClient` | `gradlew.bat runServer` | `run/` |
| NAC 独立版 | `gradlew.bat runNacClient` | `gradlew.bat runNacServer` | `run-nac/` |

两套工作目录分别保存配置、存档和日志。启动前补齐整合包运行依赖；不要往开发实例的 `mods/` 再放本仓库的发行 jar，开发任务已经加载对应源码。专服首次运行还需按 Minecraft 提示处理 EULA。任务存在或构建成功不代表实例已能进游戏。

`gradlew.bat build` 会执行两份 jar 的打包、重混淆和全部检查，主要包括：

- **test**：通过 JUnit 5 执行共享批次引擎的状态机和纯 Java 存档测试，接在标准 `test → check → build` 链路上，无需启动 Minecraft。
- **checkMixins**：核对两份产物实际加载的 mixin 配置、登记和类。完整版使用 `gtlmodular.mixins.json`，独立版使用 `gtl_nac.mixins.json`；共享 mixin 需在两份配置中登记。
- **checkDatedComments**：三个源码根中带日期的 Java 行只许减少。日期和修改经过写进提交信息，不要写进注释。
- **checkProductContents**：禁止 NAC 源码（含反射字符串）和独立 jar 混入样板总成，核对每份 jar 只有自己的模组入口、编译和资源输出没有漏打包、本地模型及贴图引用没有缺失。

单独运行单元测试用 `gradlew.bat test --offline`（Linux/macOS：`./gradlew test --offline`），交付前仍需运行完整 `build --offline`。测试位于 `src/test/java/com/gtlmodular/common/thread/`：`ConcurrentBatchEngineTest` 覆盖独立推进、等待、完成补位、扣料失败及升降槽；`BatchArchiveTest` 覆盖编码恢复、坏记录跳过和恢复不重扣料。它们不验证真实 GTRecipe CODEC / NBT、机器 handler 或 Mixin 运行效果。

测试的编译和运行 classpath 显式加入 `nac.output`。JUnit BOM 固定为 5.11.4，Jupiter 使用 `testImplementation`，Platform launcher 使用 `testRuntimeOnly`；测试类和 JUnit 不打入两份发行 jar。缓存不全时先联网执行 `gradlew.bat test` 或 `gradlew.bat build`，只运行编译任务不足以缓存测试运行依赖。

这些检查不替代游戏验收。双产物仍处于测试阶段，发布前需在依赖完整的隔离实例中验证两端启动、旧存档兼容、供料加工与守恒、配置隔离及双装提示；未实测项不能记为通过。

## 源码与资源

| 位置 | 职责 |
|---|---|
| `src/main/java/` | 完整版入口、样板总成及归档、地心设备、兼容适配；依赖共享 NAC |
| `src/nac/java/` | 通用批次引擎及 GTRecipe / NBT 适配；共享 NAC 机器、策略、注册、配置、预览、事件、客户端组件及进度条 mixin |
| `src/nacmod/java/` | 独立版入口、上下文、配置和客户端组合；只依赖 NAC 与外部库 |
| `src/test/java/` | 共享引擎的 JUnit 5 状态机和纯 Java 存档测试，不进入发行 jar |
| `src/main/resources/` | 完整版资源及共享 NAC 资源的唯一维护源 |
| `src/nacmod/resources/` | 独立版 `mods.toml`、`pack.mcmeta` 和 mixin 配置 |
| `gradle/nac-resources.gradle` | 独立版资源生成规则；输出到 `build/generated/nac/resources/`，不手工维护输出 |

完整版打包 `main + nac`，独立版打包 `nac + nacmod`。依赖方向为 `main → nac`、`nacmod → nac`；`nac` 不得反向依赖两个产物入口或样板/地心实现，具体地图和规矩见 [AGENTS.md](AGENTS.md)。已有类移动源码根时保留原 package 和全限定名。

独立批次相关代码均在 `src/nac/java/com/gtlmodular/` 下，按职责分为：

| 包路径 | 职责与依赖 |
|---|---|
| `common/thread/` | `ConcurrentBatchEngine` 管有序批次表、推进、完成补位和恢复；`BatchState` / 工厂、`BatchPolicy`、`BatchArchive` / `BatchCodec` 分离状态与策略。纯 Java 核心不依赖 Minecraft、GTCEu、gtladditions 或 NAC |
| `common/thread/gt/` | `GtRecipeBatchPersistence` / `BatchNbtKeys` 适配普通 GTRecipe CODEC 与 NBT，键、兜底 ID 和诊断回调由调用方提供。依赖 Minecraft、GTCEu 和 gtlcore，不依赖 NAC；不保证完整恢复无线配方扩展 |
| `common/machine/nanochip/` | `NanochipThreadedRecipeLogic` 等类保留 NAC 候选、并行、节流、能源及收尾策略；`NanochipBatchRuntime` 按 GTRecipe 对象身份管理概率缓存、预留和显示编号 |

`NanochipConcurrentBatches` 留在 NAC 机器包作为兼容门面，保留旧全限定名、嵌套 `Batch` / `Host`、公开读口和存档键，让展示与联动代码继续读取内核持有的同一份批次状态。它委托通用引擎和存档适配器，仍使用 NAC 上下文与日志，不是无 NAC 依赖的核心。

两份 jar 都带共享引擎及 GT-NBT 适配层，`gtl_nac` 的机器功能仍只有 NAC。当前接入沿用 NAC 主机的独立批次行为，不改变吞吐规则，也未给所有 NAC 模块或其他机器新增独立线程。其他机器需按[显式接入说明](docs/independent-threads.md)逐机适配；当前没有全局推广 mixin，两份配置都未注册 `independentThreadsForOtherMachines` 开关。

两份 jar 包含同包同名的共享 NAC 类，双装会发生类与模块冲突。`src/nacmod/resources/META-INF/mods.toml` 将 `gtlmodular` 声明为版本区间恒空的可选依赖，单装无需它，双装由 Forge 在创建 GAME 层前拒绝；保留此处拦截，放到模组构造器已太晚。

NAC 配置由共享 `NanochipConfig` 定义，`GTLModularConfig` / `GTLNacConfig` 各用自己的 Builder、ConfigValue 和 SPEC 组合，避免值串到另一份配置；新增定义须放在各自 `build()` 之前，界面行复用 `NanochipConfigPage`。完整版保留旧配置门面、文件名、键和默认值，独立版只读写自己的文件；内置校准白名单的两个通配前缀按产物命名空间生成，用户自定义条目不做命名空间替换。

`gradle/nac-resources.gradle` 的 `generateNacResources` 从 `src/main/resources/` 和共享注册源码生成 `build/generated/nac/resources/`，由独立版资源处理任务打包。不要手改生成目录或维护第二份 NAC 语言/模型/配方。

独立版自有注册及数据使用 `gtl_nac:`，复用的贴图和模型仍从 `assets/gtlmodular/` 读取；独立方块状态、物品模型和语言文件生成到 `assets/gtl_nac/`，既有语言键保留。所需资源随独立 jar 一起提供，无需另装完整版；上游材料仍保留实际注册空间（如 `gtceu:`）。

新增 NAC 内容时，先改 `src/nac/java/` 的共享注册和 `src/main/resources/` 的原资源，再按资源用途维护生成规则：

- **语言**：只编辑 `assets/gtlmodular/lang/*.json`；生成器按 NAC 前缀、共享源码中的完整语言键和注册名选取翻译，并生成独立版的注册名称键。新增动态拼接且不在现有前缀内的语言键时，补充脚本的语言筛选规则，避免独立版漏译。
- **模型与贴图**：只编辑原 `assets/gtlmodular/`；生成器从共享注册提取物品模型、结构方块状态，递归收集 JSON 引用的模型和贴图。运行时拼接地址的渲染或 CTM 资源靠 `dynamicAssets` 选入，超出现有匹配范围时补充该规则；改变注册声明写法时同步核对脚本的源码提取规则。
- **配方**：只编辑 `data/gtlmodular/recipes/`；`nanochip/`、`missing_acquisition/`、`coolant/` 及按注册名选取的根配方自动纳入，新用途目录或额外根配方分别补到 `recipeDirectories` / `supportRecipes`。生成器转换本模组注册和数据引用、追入所需战利品表及物品标签，并生成独立版运行时自检读取的 `expected_recipes.txt`，无需手工维护配方全表。

## 外部 Java NAC 模块

两份产物均提供 `com.gtlmodular.common.machine.nanochip.registration` 下的公开 Java API。扩展模组在自己的构造器中收集规格，不需要 KubeJS，也不需要复制 NAC 源码。运行时仍只安装一个 NAC 产物；扩展方应为实际安装的产物声明依赖。

```java
import com.gtlmodular.common.machine.nanochip.registration.NanochipModuleSpec;
import com.gtlmodular.common.machine.nanochip.registration.NanochipModules;
import net.minecraft.resources.ResourceLocation;

// 只收集定义，避免在注册表尚未落地时读取材料或机器实例。
NanochipModules.register(NanochipModuleSpec
        .opticalOrganizer(new ResourceLocation("examplemod", "optical_plus"))
        .displayName("扩展光学优化器")
        .frame(new ResourceLocation("gtceu", "orichalcum_frame"))
        .build());
```

完整 ID 保留 namespace，因此 `examplemod:optical_plus` 和 `anothermod:optical_plus` 是不同机器。字符串重载的短 ID 按当前 NAC 产物补齐；跨产物扩展推荐显式使用自己的 namespace。内置 ID 不可覆盖，外部模块没有伪造的枚举身份：`getModuleSpec()` 返回实际规格，旧 `getModuleKind()` 对外部模块返回 `null`。旧枚举与 `NANOCHIP_MODULES` 仍只表示 14 种内置模块；查询新定义用 `NanochipMachines.moduleDefinition(ResourceLocation)`，枚举全部规格用 `NanochipModules.all()`。

构造期 `register` 只收集数据，允许早于 NAC 入口绑定。GTCEu 的机器事件统一校验配方表、模板、重复完整 ID 并冻结，再由对应 namespace 的 Registrate 创建方块、物品和机器；冻结后再注册会报错。内置 ID 冲突最迟在这个窗口报错，不会覆盖原条目。新配方表须由扩展方在更早的 GTCEu 配方表窗口创建；`recipeType(Supplier<GTRecipeType>)` 或 `recipeType(ResourceLocation)` 在机器窗口才解析，不能在回调里创建配方表。Forge 流体/方块注册晚于机器事件，因此外部耗材和显式框架在 common setup 注册完成后校验；错误包含模块 ID 和资源 ID，启动失败，不会按缺耗材放行。此接口不支持热加载或运行中修改注册目录。

`opticalOrganizer` 的参数及默认值如下。值对象不可变，列表防御性复制；Builder 可继续复用，已生成的规格不受后续 Builder 修改影响。

| Builder / 值对象 | 默认值与含义 |
| --- | --- |
| `displayName` / `display(Display)` | 名称默认完整 ID；`displayName` 同时设中英文名称。`Display(overlayPrefix, nameZh, nameEn, mechanicZh)` 的说明用于运行提示，外部 overlay 路径由 `overlay` 单独指定。内置语言键不变，外部提示键为 `<namespace>.nanochip.module.<path>.role/desc`。 |
| `recipeType` | 延迟复用当前产物的光学优化组织器配方表，独立版不引用完整版配方表 ID。复用表不会复制或新建配方。 |
| `bonuses(Bonuses)` | 主机加成：EU ×0.90、时长 ×0.90、并行 ×1、不解锁全流程、空族过滤（适用全部）。字段依次为 `euFactor, durationFactor, parallelFactor, unlocksFullMode, familyFilter`；倍率须正且有限。计算与堆叠上限沿用 NAC 策略，并行倍率沿用整数截断。 |
| `operation(Operation)` | `maxRecipeTicks=200, router=false, organizerFamily=OPTICAL, upkeepFluidId="gtceu:grade_1_purified_water", upkeepMbPerSecond=100`。时间预算用于能源缓存，不截断配方。组织器沿用 1～16 级净化水强度及对应族满校准翻倍；流体消耗走原模块输入仓与周期。无耗材用 `null, 0`，无水等级机制用 `organizerFamily=null`。 |
| `features(Features)` | `assembly=false, board=false, laser=false, biologicalExemption=false, energyPriority=1`。分别控制组装矩阵的主机倍率/产出换算/校准记账、浸板杂质、蚀刻激光、湿件/生物满校准耗材豁免；负优先级让电，有限仓缓存按优先级排序。它们独立于全流程资格。 |
| `template(String)` / `frame(ResourceLocation)` | 默认 `nanochip_optical_organizer` 塔身及简并态铼框架。模板名可用 14 种内置机器 path，未知模板拒绝；框架覆盖模板的 FRAME 格，必须是已注册的非空气方块。模板共用原格位/安装方向，不提供任意新主机布局。 |
| `casing(ResourceLocation)` / `overlay(ResourceLocation)` | 默认 `gtlmodular:block/casings/nanochip_mesh_interface_casing` 和 `gtlmodular:block/multiblock/nanochip_optical_organizer`，两产物均打包这些资源。自定义贴图由扩展方提供；改 casing 只改机器外观，不改结构材料。网格接口机壳使用 NAC CTM，自定义 casing 不强加该 CTM。 |
| 等级、安装、预览与图标 | 不设固定定义等级，运行电压仍取本机能源仓或主机代理。模块安装在原 F 位，坐标和数量不变；内置预览次序不变，外部条目按收集顺序追加到候选目录。各模块有自己的 GT 多方块预览及物品图标。 |

客户端为外部规格提供低优先级的内存资源包：运行名称、角色/说明语言和 LDLib 机器模型，资源重载时重新建立；`.langValue` 本身只是 datagen 配置，不能代替这个路径。扩展资源包可覆盖默认模型和语言。新增 ID 不需要复制 `assets/gtlmodular/`；`generateNacResources` 仍只处理本仓内置资源，不猜测外部模组内容。新机器的合成与加工配方由扩展模组数据包或正常 `server_scripts` 提供，示例本身不新增合成配方，可用 `/give @s examplemod:optical_plus` 取得控制器。

该接口的编译与静态检查不代表实机验收完成。扩展方需在选用产物中测试放置、模板成形、接入 F 位、实际扣电/耗材、加工与主机加成、名称/图标/预览及资源重载；移除带新方块的扩展模组前备份存档。

## KubeJS startup NAC 模块

两份产物提供可选 `NACEvents.registerModules`。安装表中 KubeJS、Rhino 和 Architectury 后，将以下脚本保存为 `kubejs/startup_scripts/nac_modules.js`，完整重启客户端和专服：

```js
NACEvents.registerModules(event => {
  event.create('kubejs:optical_plus')
    .opticalOrganizer()
    .displayName('扩展光学优化器')
    .frame('gtceu:orichalcum_frame')
})
```

示例复用当前产物的光学配方表、运行策略和已打包材质，不需要提供贴图或写死 `gtlmodular:` 配方表。可用 `/give @s kubejs:optical_plus` 取得控制器；合成与加工配方另用数据包或 `server_scripts` 添加。完整模块 ID 原样保留，短 ID 补当前 NAC 产物空间；不同空间的同 path 可共存，内置 ID 不可覆盖。

脚本 Builder 是上面 Java Builder 的薄包装。`create` 已使用 `opticalOrganizer` 默认值，链上的 `.opticalOrganizer()` 可省略，不重置已填写参数。无需 `.build()` 或 `.register()`，回调完成后统一提交。可用参数如下，未填写的值均沿用 Java 表格：

| 脚本方法 | 对应 Java 参数与转换 |
| --- | --- |
| `displayName(name)`、`display(overlayPrefix, nameZh, nameEn, mechanicZh)` | 对应同名方法及 `Display`。 |
| `recipeType(id)` | 对应延迟解析的 `recipeType(ResourceLocation)`；短 ID 补当前产物空间，完整 ID 原样保留。新配方表须在更早的 GTCEu 配方表注册事件创建。 |
| `bonuses(euFactor, durationFactor, parallelFactor, unlocksFullMode, familyFilter)` | 对应 `Bonuses`；族过滤传字符串数组，如 `['OPTICAL', 'EXOTIC']`，`[]` 表示全部。倍率须正且有限。 |
| `operation(maxRecipeTicks, router, organizerFamily, upkeepFluidId, upkeepMbPerSecond)` | 对应 `Operation`；关闭水等级机制传 `organizerFamily=null`，无耗材传 `null, 0`。流体 ID 使用完整 ID。 |
| `features(assembly, board, laser, biologicalExemption, energyPriority)` | 逐项对应 `Features`，不改变既有模块策略。 |
| `template(name)`、`frame(id)`、`casing(id)`、`overlay(id)` | 对应 Java 同名方法；后面三项按 `ResourceLocation` 解析，建议总是填写完整 ID。模板名仍是内置机器 path。 |

族名忽略大小写，接受 Java `NanochipCalibration.Family` 的名称：`PRIMITIVE`、`CRYSTAL`、`WETWARE`、`BIOWARE`、`OPTICAL`、`EXOTIC`、`COSMIC`、`TEMPORAL`、`SPECIAL`；`SPECIAL` 沿用原有合并后的行为，不新增校准族。未知族、非法 ID/倍率/耗材组合报错，模板、配方表、框架和流体仍在 Java API 的相同阶段校验。

事件仅用于 STARTUP：插件先登记事件，KubeJS 加载脚本监听器，然后 `initStartup` 投递一次、收集规格，最后由 GTCEu 机器窗口校验并创建，早于 `MACHINES.freeze()`。此时只描述规格，不能获取世界、机器实例或提前读取材料/配方表结果。回调异常会阻止提交，重复 ID 不会覆盖条目。事件结束后保留的 event/Builder 无法再修改注册内容。

`/reload`、资源重载和 `/kubejs reload_startup_scripts` 都不能增删模块；startup 重载会记录需要完整重启的警告，不会再次投递事件。改脚本后务必完整重启。自定义资源可放到 KubeJS 的 `assets/<namespace>/`，覆盖低优先级的默认名称/模型；`generateNacResources` 不扫描运行时脚本。客户端与服务端必须使用一致的模块脚本。

插件仅通过两份 jar 根的 `kubejs.plugins.txt` 发现，共享 Java API 和产品入口不引用 KubeJS/Rhino 类型。本模组将 KubeJS 声明为两端可选依赖；这不保证其他 GTL 依赖也支持无 KubeJS 启动。开发环境的 `libs/*.jar` 会进入运行 classpath，无 KubeJS 验证必须同时从 classpath 移除 KubeJS/Rhino，不能只清空实例的 `mods/`。完整双端游戏矩阵和脚本模块的成形、供电、加工、显示验收仍需在配齐运行依赖的整包完成。

## 交接（互相发 git bundle 文件）

- **发给对方**：实现全部提交、构建和游戏验收完成之后，双击 `scripts\导出交接包.bat`。它会在仓库的上一级目录生成 `gtlmodular-<时间>.bundle`，把这个文件发给对方（QQ 传文件之类都行）。
- **收到对方的**：把 `.bundle` 文件拖到 `scripts\导入交接包.bat` 上，或者在仓库里运行：
  ```
  git pull <bundle 路径> main
  ```
- 双方同时改同一个文件会冲突。进行大改动之前先说一声，错开同一批文件。

## 依赖（libs/）

**编译和运行当前整包均使用 JDK 21。** 本项目输出 Java 17 字节码（major 61），但 NAC 直接继承的 GTL Additions 基类是 Java 21 字节码（major 65），不能据此改用 Java 17 启动整包。

开发环境使用 Minecraft 1.20.1、Forge 47.3.7、Gradle Wrapper 8.5。Minecraft/Forge 和构建工具需要先缓存；其余编译期模组依赖来自 `libs/` 根目录的本地 jar，使用下列版本：

| jar | 说明 |
|---|---|
| `gtceu-1.20.1-1.4.4.jar` | GTCEu |
| `gtlcore-1.2.3.2-fix4.jar` | GTL Core |
| `[Custom-亚空间]gtladditions-3.3.1Custom_SubSpace.jar` | GTL Additions（NAC 的基类硬依赖，需要 Java 21） |
| `[修复高级舱室的崩溃]gtmthings-1.3.5.fix(…).jar` | GTM Things（无线电网） |
| `ldlib-forge-1.20.1-1.0.33.b.jar` | LDLib |
| `Registrate-MC1.20-1.3.3.jar` | Registrate |
| `appliedenergistics2-forge-15.4.10.jar` | AE2 |
| `extendedae_plus_gtladd-1.0.3.jar` | ExtendedAE Plus |
| `GTLsupb-3.4.beta.7.jar`、`GTMAdvancedHatch-0.1.9-hotfix2.jar`、`Industrial Platform-1.20.1-1.8.0.jar`、`infinitepower-2.0.jar` | 其他 GT 附属 |
| `Jade-1.20.1-Forge-11.13.3.jar`、`jei-1.20.1-forge-15.49.0.188.jar` | 显示类 |
| `kubejs-forge-2001.6.5-build.26.jar`、`architectury-9.2.14-forge.jar` | KubeJS 可选集成及其前置，沿用此版本组合 |
| `rhino-forge-2001.2.3-build.10.jar` | KubeJS 路径需要的脚本引擎（插件编译也需要）；来源：[Rhino 官方 Modrinth 发布](https://modrinth.com/mod/rhino/version/uNALdylI)，[该版本下载](https://cdn.modrinth.com/data/sk9knFPE/versions/uNALdylI/rhino-forge-2001.2.3-build.10.jar)。不内嵌进本模组 jar |
| `cloth-config-11.1.136-forge.jar` | 配置界面前置 |

独立版声明的必需依赖为 Forge ≥47.3.7、GTCEu ≥1.4.4、LDLib ≥1.0.33.b、AE2 ≥15、gtlcore ≥1.2.3.2-fix4、gtladditions ≥3.3.1Custom、gtmthings ≥1.3.5；客户端另需 Cloth Config ≥11。Jade ≥11 为两端可选插件，JEI ≥15 为客户端可选插件。版本范围是加载器声明，当前开发仍按表中实际 jar 对齐。

独立版 `gtl_nac` 运行时可使用普通版 `gtladditions-3.3.1Custom.jar` 或亚空间版 `gtladditions-3.3.1Custom_SubSpace.jar`，二选一安装。

完整版沿用旧元数据，其中 gtladditions 的“可选/只取图标”描述已不能代表实际 NAC 依赖，应按当前实现配齐 gtladditions 和无线电网等运行依赖。两个产物都不内嵌依赖 jar，也不保证仅凭这份编译依赖清单就能启动；运行还需补齐各依赖要求的其他模组，并满足它们的 Forge 版本要求。

`libs/` 下的子目录（例如旧版备份 `_old_*`）不参与编译。GTL-Enhancedcore 不在编译依赖里，完整版的相关兼容由整合包提供它的运行环境。`libs/runtime/` 仅供 `decompileDeps` 一并反编译运行时依赖，不会自动加入开发启动 classpath；需要时将运行时模组放入对应开发实例的 `mods/`。
