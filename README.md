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
   一条 `build` 同时生成 `build\libs\gtlmodular-<版本>.jar` 和 `build\libs\gtl_nac-<版本>.jar`，分别完成 `reobfJar` 和 `reobfNacJar` 重混淆，可供发行环境加载。两者版本均取 `gradle.properties` 的 `mod_version`。开发环境、插件和依赖缓存齐全后，可用 `gradlew.bat build --offline`。

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

- **checkMixins**：核对两份产物实际加载的 mixin 配置、登记和类。完整版使用 `gtlmodular.mixins.json`，独立版使用 `gtl_nac.mixins.json`；共享 mixin 需在两份配置中登记。
- **checkDatedComments**：三个源码根中带日期的 Java 行只许减少。日期和修改经过写进提交信息，不要写进注释。
- **checkProductContents**：禁止 NAC 源码（含反射字符串）和独立 jar 混入样板总成，核对每份 jar 只有自己的模组入口、编译和资源输出没有漏打包、本地模型及贴图引用没有缺失。

这些检查不替代游戏验收。双产物仍处于测试阶段，发布前需在依赖完整的隔离实例中验证两端启动、旧存档兼容、供料加工与守恒、配置隔离及双装提示；未实测项不能记为通过。

## 源码与资源

| 位置 | 职责 |
|---|---|
| `src/main/java/` | 完整版入口、样板总成及归档、地心设备、兼容适配；依赖共享 NAC |
| `src/nac/java/` | 共享 NAC 机器、注册、配置、预览、事件、客户端组件及进度条 mixin |
| `src/nacmod/java/` | 独立版入口、上下文、配置和客户端组合；只依赖 NAC 与外部库 |
| `src/main/resources/` | 完整版资源及共享 NAC 资源的唯一维护源 |
| `src/nacmod/resources/` | 独立版 `mods.toml`、`pack.mcmeta` 和 mixin 配置 |
| `gradle/nac-resources.gradle` | 独立版资源生成规则；输出到 `build/generated/nac/resources/`，不手工维护输出 |

完整版打包 `main + nac`，独立版打包 `nac + nacmod`。依赖方向为 `main → nac`、`nacmod → nac`；`nac` 不得反向依赖两个产物入口或样板/地心实现，具体地图和规矩见 [AGENTS.md](AGENTS.md)。已有类移动源码根时保留原 package 和全限定名。

两份 jar 包含同包同名的共享 NAC 类，双装会发生类与模块冲突。`src/nacmod/resources/META-INF/mods.toml` 将 `gtlmodular` 声明为版本区间恒空的可选依赖，单装无需它，双装由 Forge 在创建 GAME 层前拒绝；保留此处拦截，放到模组构造器已太晚。

NAC 配置由共享 `NanochipConfig` 定义，`GTLModularConfig` / `GTLNacConfig` 各用自己的 Builder、ConfigValue 和 SPEC 组合，避免值串到另一份配置；新增定义须放在各自 `build()` 之前，界面行复用 `NanochipConfigPage`。完整版保留旧配置门面、文件名、键和默认值，独立版只读写自己的文件；内置校准白名单的两个通配前缀按产物命名空间生成，用户自定义条目不做命名空间替换。

`gradle/nac-resources.gradle` 的 `generateNacResources` 从 `src/main/resources/` 和共享注册源码生成 `build/generated/nac/resources/`，由独立版资源处理任务打包。不要手改生成目录或维护第二份 NAC 语言/模型/配方。

独立版自有注册及数据使用 `gtl_nac:`，复用的贴图和模型仍从 `assets/gtlmodular/` 读取；独立方块状态、物品模型和语言文件生成到 `assets/gtl_nac/`，既有语言键保留。所需资源随独立 jar 一起提供，无需另装完整版；上游材料仍保留实际注册空间（如 `gtceu:`）。

新增 NAC 内容时，先改 `src/nac/java/` 的共享注册和 `src/main/resources/` 的原资源，再按资源用途维护生成规则：

- **语言**：只编辑 `assets/gtlmodular/lang/*.json`；生成器按 NAC 前缀、共享源码中的完整语言键和注册名选取翻译，并生成独立版的注册名称键。新增动态拼接且不在现有前缀内的语言键时，补充脚本的语言筛选规则，避免独立版漏译。
- **模型与贴图**：只编辑原 `assets/gtlmodular/`；生成器从共享注册提取物品模型、结构方块状态，递归收集 JSON 引用的模型和贴图。运行时拼接地址的渲染或 CTM 资源靠 `dynamicAssets` 选入，超出现有匹配范围时补充该规则；改变注册声明写法时同步核对脚本的源码提取规则。
- **配方**：只编辑 `data/gtlmodular/recipes/`；`nanochip/`、`missing_acquisition/`、`coolant/` 及按注册名选取的根配方自动纳入，新用途目录或额外根配方分别补到 `recipeDirectories` / `supportRecipes`。生成器转换本模组注册和数据引用、追入所需战利品表及物品标签，并生成独立版运行时自检读取的 `expected_recipes.txt`，无需手工维护配方全表。

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
| `kubejs-forge-2001.6.5-build.26.jar`、`architectury-9.2.14-forge.jar`、`cloth-config-11.1.136-forge.jar` | 前置 |

独立版声明的必需依赖为 Forge ≥47.3.7、GTCEu ≥1.4.4、LDLib ≥1.0.33.b、AE2 ≥15、gtlcore ≥1.2.3.2-fix4、gtladditions ≥3.3.1Custom_SubSpace、gtmthings ≥1.3.5；客户端另需 Cloth Config ≥11。Jade ≥11 为两端可选插件，JEI ≥15 为客户端可选插件。版本范围是加载器声明，当前开发仍按表中实际 jar 对齐。

完整版沿用旧元数据，其中 gtladditions 的“可选/只取图标”描述已不能代表实际 NAC 依赖，应按当前实现配齐 gtladditions 和无线电网等运行依赖。两个产物都不内嵌依赖 jar，也不保证仅凭这份编译依赖清单就能启动；运行还需补齐各依赖要求的其他模组，并满足它们的 Forge 版本要求。

`libs/` 下的子目录（例如旧版备份 `_old_*`）不参与编译。GTL-Enhancedcore 不在编译依赖里，完整版的相关兼容由整合包提供它的运行环境。`libs/runtime/` 仅供 `decompileDeps` 一并反编译运行时依赖，不会自动加入开发启动 classpath；需要时将运行时模组放入对应开发实例的 `mods/`。
