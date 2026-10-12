# gtlmodular 开发规矩

写给在这个仓库里写代码的 AI（以及人）。每次开始干活前先读一遍。

## 项目

GTL 整合包的附属模组，Forge 1.20.1 + GTCEu 1.4.4。同仓构建两个互斥产物：完整的「GTL更多实用设备」（`gtlmodular`）和 NAC 独立版（`gtl_nac`）。完整版内容：

- 纳米芯片组装复合体（NAC，移植自 GTNH）：主机、模块、独立线程、校准、真空传送
- 猪咪样板总成系列（MV～UIV、超级、惰性、ME 通配符）与样板仓复制/剪切工具
- 地心涌动能量转换枢纽及其三个模块

独立版只组合共享 NAC 和独立入口，不包含本仓库的样板总成、归档工具及地心实现。两个 jar 二选一安装；自有注册空间不同，本期没有 `gtlmodular:` → `gtl_nac:` 存档迁移器，旧存档继续用完整版。

两份 jar 含同包同名的共享类，必须在 Forge 创建 GAME 层前拒绝双装。拦截放在 `src/nacmod/resources/META-INF/mods.toml`：对 `gtlmodular` 声明版本区间恒空的可选依赖，单装放行、双装拒绝，不能移到模组构造器。

## 构建与验证

- 编译和当前整包运行都需要 JDK 21（本项目产物仍是 Java 17 字节码，但 NAC 的 gtladditions 基类需要 Java 21）。依赖 jar 放在 `libs/`，不进 git，具体版本和运行依赖见 `README.md`。
- 验证命令：`gradlew.bat build --offline`（Linux/macOS 用 `./gradlew build --offline`）；首次缓存开发环境和 JUnit 测试依赖时去掉 `--offline`，编译及测试运行依赖都缓存齐全后才能离线。一条命令编译、打包并完成 `reobfJar` / `reobfNacJar`，生成 `build/libs/gtlmodular-<版本>.jar` 和 `build/libs/gtl_nac-<版本>.jar`，并跑全部检查和单元测试。
- 单独运行测试：`gradlew.bat test --offline`（Linux/macOS 用 `./gradlew test --offline`）。`src/test/java/com/gtlmodular/common/thread/` 使用 JUnit 5，测试 classpath 显式包含 `nac.output`；测试类和 JUnit 不打入发行 jar。标准链路为 `test → check → build`，单独 test 通过不能代替完整 build。纯核心测试无需启动 Minecraft，只覆盖状态机和纯 Java 存档契约，不能当作真实 GTRecipe CODEC / NBT、handler 或游戏验收通过。
- **没跑过 build 并且通过，就不要说"改好了"。** 汇报时分清三类：编译和检查验证过的、进游戏测过的、还没测的。
- 完整版调试：`gradlew.bat runClient` / `runServer`，工作目录 `run/`；独立版调试：`gradlew.bat runNacClient` / `runNacServer`，工作目录 `run-nac/`。两套实例分别补齐运行依赖，不要在开发实例中再安装本仓库的发行 jar。

## 规矩

1. **一个改动一次 git 提交。** 第一行写改了什么，正文写为什么改。历史靠 git 记，不靠注释。
2. **注释只写"为什么"。** 不写日期、版本号、"队长说"、修改经过、"照抄某文件第几行"（行号会变）。这些写进提交信息；玩家能感知的变化写进 `CHANGELOG.md`。代码本身已经说清楚的事不用再注释。构建会检查：代码里带日期的行只许减少（`checks/dated-comment-baseline.txt`）。
3. **不复制类。** 两处要用同一段逻辑，就抽成共享的类或组件再复用。`LazyPatternAssemblyPartMachine` 和 `MoltenPatternAssemblyPartMachine` 保留各自类及上游基类，共用 `common/machine/pattern/` 中已提取的匹配、命名、查找和界面组件；后续修改继续复用这些组件。
4. **新 mixin 必须登记进对应产物的配置。** 完整版是 `gtlmodular.mixins.json`，独立版是 `gtl_nac.mixins.json`；两者都需要的共享 mixin 登记两份。配置文件同时由对应的 `mods.toml` 和 jar manifest 声明。构建检查两个 reobf jar 的实际登记；`checks/mixin-unregistered-allowlist.txt` 仅供完整版历史遗留使用，只许删，不许往里加，独立版不继承豁免。
5. **能量和物品必须守恒。** 扣不到就等，不许"视为已充足""按承诺记账"这类凭空放行。电网账户、无线电网终端都要真实扣电。
6. **功能开关放进配置组件**，不要再新增 `public static boolean` 开关。NAC 项统一定义在共享的 `NanochipConfig`，由 `GTLModularConfig` / `GTLNacConfig` 各自组合；样板和地心项留在 `GTLModularConfig`。保持 `gtlmodular-common.toml` 的文件名、键和默认值，独立版只读写 `gtl_nac-common.toml`。两份组合各持有独立的 Builder、ConfigValue 和 SPEC，新增配置定义放在 `build()` 之前，避免漏项或绑定到错误的配置；只有内置校准白名单按产物空间生成，用户自定义条目原样保留。
7. **上游行为不确定就去查，不要猜。** 先运行 `gradlew.bat decompileDeps`，它会把 `libs/` 里每个依赖反编译到 `deps-src/`（不进 git）。GTL 是社区魔改、几乎没有文档，GTL 的规则以 `deps-src/` 里的实际实现为准；印象和 GTNH wiki 都可能不对。
8. **版本号只改 `gradle.properties` 里的 `mod_version`。** 换版本后，mods 目录里的旧 jar 必须删掉，否则 Forge 会以 "Duplicate mod" 拒绝加载。
9. **大类不要继续变大。** 新功能放进新类；改到上千行的类时，顺手把相关的部分拆出去。
10. **依赖方向只能是 `main → nac`、`nacmod → nac`。** `nac` 只依赖共享源码和外部库，不得直接或通过反射、类名字符串依赖 `main` / `nacmod` 实现；`nacmod` 不得依赖 `main`。需要产物差异时在 `nac` 定义上下文或扩展点，由入口注入；不要把旧入口、样板或地心实现拉回 NAC。
11. **拆分不得改变完整版兼容面。** 已有类的 package 和全限定名、注册 ID、NBT 键、`@Persisted` / `@DescSynced` 字段名和类型、配置文件名和键、语言键保持原样。所有既有 `static final boolean` 默认值保留，尤其 `HAS_PATTERN_SLOT_ARCHIVE_FALLBACK=true`；「未命名加工模式」Unicode 字面量一字不改，上游 Enhancedcore 用它做名字白名单。
12. **验证脚手架不进仓库。** 快照、哈希、全量清单、一次性对照程序、替身类和 notes 留在会话中验证（临时文件放 `/tmp` 或 `.scratch/`），结论写进提交信息；只有同时满足以下三点的检查才进 build：保护玩家利益或独立版构建所需的不变量，正常新增无需更新基线且只拦必需内容缺失或禁止内容混入，实现短小且无需替身类或启动 MC。

## 已确认的游戏规则

改动会影响玩家手感的规则之前（功率、时长、并行、扣电方式），先写下你假设的规则，让负责人确认，再动手。已经确认的：

- **电网供电**（GTMThings 无线电网账户、GTL Additions 无线电网输入终端）：电网里有多少电就给多少，相当于无限超频，不会出现超长批次。用电网的机器每 tick 按需扣电，不预存整批。
- **只用能源仓供电的 NAC 模块**：按原版 GTNH，起跑前缓存要够整批的电（"不会中途断电"）。

## 代码地图

以下包路径均相对各源码根的 `java/com/gtlmodular/`，已有类只是换源码根，包名不变。

| 源码根 | 内容与入口 |
|---|---|
| `src/main/` | `GTLModular.java` 完整版入口；`common/machine/` 的样板总成、归档、地心设备及 `pattern/` 共享组件；`common/data/GTLModularMachines` 总注册与地心结构；`GTLModularNacContext` 等 NAC 适配；旧配置门面和界面、非 NAC 客户端组件及其余 mixin |
| `src/nac/` | `common/thread/` 纯 Java 批次核心及 `common/thread/gt/` 存档适配；`common/machine/nanochip/` 主机、模块、独立线程策略、并行、校准、真空物流；`MultiTypeLookupHost` 契约；`common/data/nanochip/` 注册、结构、上下文、预览、自检与扩展点；`NanochipBlocks`、`HatchPredicates`、`GTLModularAddon`；共享 `NanochipConfig` / `NanochipConfigPage`、事件和命令；CC 染色、CTM、NAC 渲染、Jade/JEI 和 `MixinWorkableProgressBar` |
| `src/nacmod/` | `nac/GTLNac.java` 独立入口，以及 `GTLNacContext`、`GTLNacConfig`、`GTLNacRegistration`、`GTLNacAddon`、`GTLNacClient` 组合层 |
| `src/test/` | `common/thread/ConcurrentBatchEngineTest` 状态机测试与 `BatchArchiveTest` 纯 Java 存档测试 |

独立批次分为三层，全部位于 `src/nac/`：

- `common/thread/`：`ConcurrentBatchEngine` 与 `BatchState` / 工厂、`BatchPolicy`、`BatchArchive` / `BatchCodec`，只管批次状态机与纯 Java 存档契约，不依赖 MC、GTCEu、gtladditions 或 NAC。
- `common/thread/gt/`：`GtRecipeBatchPersistence` / `BatchNbtKeys`，依赖 MC、GTCEu 和 gtlcore，不依赖 NAC；键、兜底 ID 和诊断回调由调用方提供。普通 GTRecipe CODEC 适配不保证完整恢复无线配方扩展。
- `common/machine/nanochip/`：`NanochipThreadedRecipeLogic` 等类提供 NAC 候选、并行、节流、能源及收尾策略；`NanochipBatchRuntime` 按 GTRecipe 对象身份管理概率缓存、预留和显示编号。`NanochipConcurrentBatches` 是兼容门面，保留旧全限定名、嵌套 `Batch` / `Host`、公开读口和旧存档键，委托前两层并与内核共用同一份批次状态；它仍使用 NAC 上下文与日志，不能再称为无 NAC 依赖的核心。

共享引擎及 GT-NBT 适配层随两份 jar 打包，可供显式接入复用；独立版的机器功能仍只有 NAC。当前没有全局推广 mixin 或 `independentThreadsForOtherMachines` 配置开关，没有给所有 NAC 模块或其他机器新增独立线程。其他机器的适配边界见 [docs/independent-threads.md](docs/independent-threads.md)；完整版适配放在 main，经 `main → nac` 使用共享接口，不反向引入产品实现。

`common/data/host/ModulePreviewSlots` 留在 main，NAC 预览使用共享的 `NanochipModulePreview` / `ModulePreviewSupport`。共享代码中的旧类名（如 `GTLModularAddon`、`GTLModularCommands`、`GTLModularJeiPlugin`）不代表它依赖完整版入口。

完整版打包 `main + nac`，独立版打包 `nac + nacmod`。资源只维护 `src/main/resources/` 的原始语言、模型、贴图、配方等。`gradle/nac-resources.gradle` 从这些文件和共享注册源码生成 `build/generated/nac/resources/`，供 nacmod 打包；独立元数据单独维护在 `src/nacmod/resources/`。不要手改生成目录或复制一套资源。独立版注册与自有数据使用 `gtl_nac:`，复用模型/贴图仍使用 `assets/gtlmodular/`；独立方块状态、物品模型和语言文件生成到 `assets/gtl_nac/`，既有语言键和上游 ID 保持原样。

新增 NAC 资源先改共享注册和原资源；新配方用途、动态渲染地址或动态语言键超出现有规则时，同步调整生成脚本的 `recipeDirectories` / `supportRecipes`、`dynamicAssets` 或语言筛选，具体入口见 [README.md](README.md#源码与资源)。`expected_recipes.txt` 由生成器提供给独立版运行时自检读取，属于运行资源，不是验证脚手架。

## 交接

实现全部提交、构建和游戏验收完成之后，运行 `scripts\导出交接包.bat`，把生成的 `.bundle` 文件发给对方（QQ 传文件之类都行）。
