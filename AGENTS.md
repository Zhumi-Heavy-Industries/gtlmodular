# gtlmodular 开发规矩

写代码前先读完这一页。不确定的地方去 [README.md](README.md) 和 `docs/` 查，不要猜。

## 这是什么

GTL 整合包的附属模组（Forge 1.20.1 + GTCEu 1.4.4）。一个仓库构建两个 jar，玩家二选一安装：

- `gtlmodular`：完整版。NAC（纳米芯片组装复合体）+ 猪咪样板总成 + 地心涌动能量转换枢纽。
- `gtl_nac`：只有 NAC 的独立版。

| 源码目录 | 放什么 | 打进哪个 jar |
|---|---|---|
| `src/nac/` | NAC 的全部实现 | 两个都打 |
| `src/main/` | 完整版入口、样板总成、地心设备、其他机器独立线程 | 只打完整版 |
| `src/nacmod/` | 独立版入口 | 只打独立版 |
| `src/test/` | 不启动游戏的单元测试 | 不打 |
| `src/gametest/` | 启动测试服的自动测试 | 不打 |

资源只改 `src/main/resources/`。独立版的资源在构建时自动生成，不要手改 `build/generated/`。

## 去哪找

下表里的 `...` 代表 `java/com/gtlmodular/common`。

| 要改的东西 | 从这里看起 |
|---|---|
| NAC 主机 | `src/nac/.../machine/nanochip/NanochipAssemblyComplexMachine` |
| NAC 模块 | 同目录的 `NanochipModuleMachine`；模块和主机的连接 `NanochipModuleLinks` |
| NAC 的批次、并行、线程 | `NanochipThreadedRecipeLogic`；纯 Java 批次引擎在 `src/nac/.../thread/` |
| NAC 供电 | 电网 `NanochipGridPower`，模块供电 `NanochipModulePower`，自带能源仓 `NanochipLocalBatchEnergy` |
| NAC 出料 | `NanochipRecipeOutputs` |
| NAC 校准 | `NanochipCalibration` |
| NAC 注册（机器、模块、方块） | `src/nac/.../data/nanochip/NanochipMachines`；外部模块接口 `machine/nanochip/registration/` |
| NAC 配置 | `src/nac/.../config/NanochipConfig`；配置界面 `src/nac/java/com/gtlmodular/client/config/NanochipConfigPage` |
| 样板总成 | `src/main/.../machine/MoltenPatternAssemblyPartMachine`、`LazyPatternAssemblyPartMachine`；两者共用的匹配、命名、界面在 `machine/pattern/` |
| 地心设备 | `src/main/.../machine/MoltenModuleHostMachine` 和 `Molten*ModuleMachine` |
| 其他机器独立线程 | `src/main/.../machine/independent/`，说明见 [docs/independent-threads.md](docs/independent-threads.md) |
| 完整版注册和配置 | `src/main/.../data/GTLModularMachines`、`config/GTLModularConfig` |
| 吞掉的异常怎么记录 | `src/nac/.../diag/SwallowedErrors` |

## 改完代码必须做

1. `gradlew.bat build --offline` 通过。
2. 动了 NAC、样板总成、能量、配方相关的代码，再跑两套 GameTest，FAIL 必须是 0（见 [docs/测试.md](docs/测试.md)）。
3. 一件事开一个分支、一个 PR，不要直接改 `main`（它是锁住的，流程见 README 的「协作流程」）。PR 标题写改了什么，正文写为什么改、测了什么，合并后它们就是 `main` 上的提交信息。
4. 玩家能感觉到的变化，写进 `CHANGELOG.md` 的「未发布」段。

没做完 1 和 2，不要说"改好了"。汇报时分清：构建过了、测试过了、进游戏试过了、还没试。

## 规矩

标了【检查】的，违反了 `build` 会失败，并告诉你改哪里。

1. **注释只写"为什么"。** 不写日期、版本号、谁说的、修改经过、"见某文件第几行"，这些写进提交信息。代码本身说清楚了的事不用注释。【检查：代码里不能出现日期】
2. **不复制代码。** 两处要用同一段逻辑，抽成共享的类再复用。
3. **大类不要继续变大。** 新功能放新类；改到上千行的类，顺手把相关部分拆出去。
4. **依赖方向只能是 `main → nac`、`nacmod → nac`。** `src/nac/` 里不许引用 `main` 和 `nacmod` 的类，反射和类名字符串也不行。【检查】
5. **新 mixin 要登记。** 完整版写进 `gtlmodular.mixins.json`，独立版写进 `gtl_nac.mixins.json`，两边都用的两边都登记。【检查】
6. **注入 gtlcore / GTCEu 的类之前，先查冲突。** 在 `deps-src/` 里搜 gtladditions 和 GTL-Enhancedcore 有没有改同一个方法。gtladditions 用优先级 1000 重写了 `MultipleRecipesLogic.getRecipe` 等方法，Enhancedcore 用 1200 改了 `getRecipe` 里的 `getMaxParallel` 调用，找不到目标就崩溃。我们的 mixin 统一用 1300。测试服测不出这类冲突（原因见 [docs/开发经验.md](docs/开发经验.md)），改完要在真实整合包里启动一次。
7. **能量和物品必须守恒。** 扣不到就等，不许"先当作够了"放行。电网账户和无线终端也要真实扣电。
8. **输出满了就停下等，** 腾出空间再出。不许丢产物，也不许重复出。
9. **不要新增 `catch (Throwable)`。** 捕获具体的异常类型。【检查】
10. **新开关放进配置类。** NAC 的放 `NanochipConfig`，其他的放 `GTLModularConfig`。不要再加 `public static boolean` 开关。
11. **上游怎么实现的不确定，就去看源码。** `gradlew.bat decompileDeps` 会把 `libs/` 里的依赖反编译到 `deps-src/`。GTL 几乎没有文档，以源码为准。
12. **版本号只改 `gradle.properties` 的 `mod_version`。**
13. **这些不能改**，改了会弄坏玩家存档或别的模组：
    - 已有类的包名和类名、注册 ID、NBT 键、`@Persisted` / `@DescSynced` 字段、配置文件名和键、语言键；
    - 已有 `static final boolean` 的默认值，尤其 `HAS_PATTERN_SLOT_ARCHIVE_FALLBACK=true`（改过一次，吃了玩家的样板）；
    - 字符串「未命名加工模式」（Enhancedcore 靠它识别占位名）。
14. **两个 jar 的互斥拦截不要挪。** 它写在 `src/nacmod/resources/META-INF/mods.toml`：对 `gtlmodular` 声明一个永远满足不了的可选依赖。挪进代码就拦晚了。
15. **一次性的验证脚本、笔记不进仓库。** 放 `.scratch/`，这个目录已经被 git 忽略。

## 已确认的游戏规则

改功率、时长、并行、扣电方式之前，先写下你认为的规则，让负责人确认，再动手。已经确认的：

- **电网供电**（GTMThings 无线电网、GTL Additions 无线输入终端）：电网有多少给多少，相当于无限超频。每 tick 现扣，不预存整批，不加功率上限。
- **主机有限能源仓给模块中央供电**：开工前模块缓存要够整批的电。
- **模块自带有限能源仓**：开工前存够整批的电；仓装不下整批就缩小并行；一份都装不下，就等仓满后开工、逐 tick 扣电，断电时按 GT 的规则退进度。
- **输出堵塞**：停下等，腾出空间再出，不丢、不重复。等出料期间不扣加工电。存档重载后也一样。
- **直通档并行**：每条新批次 = `directParallelCap`（默认 1024）× 每台卷绕器 4 × 每台分流器 2，同种最多算 2 台。
- **校准**：按实际产出的电路数记账，只记白名单里的配方。校准折扣在开批前按配方族算进去。
- **其他机器独立线程**（实验功能，默认关）：多个批次加起来的并行和功率，不能超过上游一台机器合批时的并行和功率。细节见 [docs/independent-threads.md](docs/independent-threads.md)。

## 发版清单

1. 开一个发版分支，改 `gradle.properties` 的 `mod_version`，把 `CHANGELOG.md` 的「未发布」段改成新版本号（上面留一个空的「未发布」）。
2. `gradlew.bat build --offline` 和两套 GameTest 全部通过。这一版改了 mixin 的话，先在真实整合包里启动一次。
3. 提 PR，批准后合并。
4. 切回最新的 `main`，打标签并推送：`git tag v<版本>`、`git push origin v<版本>`。
5. 在 `main` 上重新 `gradlew.bat build`，在 GitHub 上用这个标签建一个 Release，挂上 `build/libs/` 里的两个 jar。
6. 提醒测试的人删掉 mods 里的旧 jar。
