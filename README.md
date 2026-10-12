# GTL更多实用设备 / NAC 独立版

GTL 整合包的附属模组，Minecraft 1.20.1 + Forge + GTCEu 1.4.4。同一个仓库构建两个 jar，共用 NAC 的实现。

发起人：王小爱（[wangxiaoai9547](https://github.com/wangxiaoai9547)）　重构：amireux（[MaribelHearm](https://github.com/MaribelHearm)）

| 文档 | 内容 |
|---|---|
| [AGENTS.md](AGENTS.md) | 写代码的规矩、已确认的游戏规则、代码地图、发版清单。**改代码前先读** |
| [CHANGELOG.md](CHANGELOG.md) | 玩家能感觉到的变化 |
| [docs/开发经验.md](docs/开发经验.md) | 以前踩过的坑：现象、原因、以后怎么做 |
| [docs/测试.md](docs/测试.md) | 怎么跑自动测试、怎么看结果、测不到什么 |
| [docs/依赖.md](docs/依赖.md) | 编译和测试需要的 jar |
| [docs/扩展模块.md](docs/扩展模块.md) | 用 Java 或 KubeJS 注册新的 NAC 模块 |
| [docs/independent-threads.md](docs/independent-threads.md) | 其他机器独立线程（实验功能）的实现细节 |

## 选择产物与安装

| 产物 | 内容 | 配置文件 |
|---|---|---|
| `gtlmodular-<版本>.jar` | 完整版：NAC、猪咪/惰性样板总成、样板仓归档工具、地心涌动能量转换枢纽及模块 | `config/gtlmodular-common.toml` |
| `gtl_nac-<版本>.jar` | NAC 独立版：主机、14 种模块、CC/PC 及配套方块、物品和配方 | `config/gtl_nac-common.toml` |

- **两个 jar 二选一，不要同时装。** 同时装了 Forge 会在启动早期报错，提示删掉一个。
- **已有 `gtlmodular` 存档继续用完整版。** 独立版的注册 ID 是 `gtl_nac:`，完整版是 `gtlmodular:`，换 jar 会让旧存档里的 NAC 方块和物品消失。没有存档迁移工具。
- 升级时删掉 mods 里的旧 jar，否则 Forge 报 Duplicate mod。
- 运行依赖见 [docs/依赖.md](docs/依赖.md)。

## NAC 怎么用

默认是简单物流模式（`simpleLogisticsMode=true`）：

- **AE 供料**：主机 H 位或模块 V 位放 gtlcore 原生 ME 样板仓并接网，按 JEI 编码加工样板，在 ME 终端下单。模块还要备足耗材液体。也可以放 ME 样板总成镜像：用闪存先右键源样板仓，再右键镜像绑定。
- **普通仓供料**：H/V 位放物品/流体输入仓和对应的输出仓。普通输出仓不会自动回 ME。
- **换算**：主机同时搜索组装配方和换算配方，不需要切换模式。真件和未处理的 CC 可以互相换算；已处理的 PC 不能还原。

关掉简单物流模式会用回原版 NH 的真空物流。那套已经废弃、不保证能用，建议保持默认。

**线程显示**：主机面板和 Jade 显示每个批次的 EU/t、并行、进度和时长。等待中的批次显示的 EU/t 是它需要的功率，不代表这一 tick 已经扣了电。模块对主机的加成在主机面板里看。

## 常用配置

配置写在上表的配置文件 `[nanochipComplex]` 段里，单机也可以在游戏内的模组配置界面改。专服要改服务端的文件并重启。

| 配置键 | 默认值 | 作用 |
|---|---|---|
| `nanochipComplex.directParallelCap` | `1024` | 直通档每条新批次的并行基数。再乘模块加成：每台卷绕器 ×4、每台分流器 ×2，同种最多算 2 台，所以最高 1024×16×4 = 65536。全流程档不受这项限制 |
| `nanochipComplex.gridPower.enabled` | `true` | 允许从绑定账户的 GTMThings 无线电网取电，每 tick 现扣 |
| `nanochipComplex.fanOutSameRecipe` | `false` | 打开后，同一个配方已经有批次在跑时，空闲线程也可以再开一批 |
| `nanochipComplex.perSlotParallelBound` | `false` | 打开后按单个槽位估算可开的并行（不会跨槽拼料） |
| `nanochipComplex.calibrationUseFullWindow` | `false` | 校准分母用原版固定的 100000。默认按已记录的电路数算，做完一批就能看到校准涨 |
| `nanochipComplex.simpleLogisticsMode` | `true` | 简单物流模式。改了要重启 |
| `nanochipComplex.originalMode` | `true` | 结构和连接完全交给 gtceu/gtlcore 原本的方式 |
| `independentThreadsForOtherMachines` | `false` | （仅完整版）让其他多方块机器也用独立批次。实验功能，不要用于正式存档 |

其他键的说明写在配置文件的注释里。

## 诊断命令

| 产物 | NAC 自检 | 查看本次运行捕获的异常 |
|---|---|---|
| 完整版 | `/gtlmodular_nac` | `/gtlmodular_nac errors` |
| 独立版 | `/gtl_nac` | `/gtl_nac errors` |

不需要 OP。`errors` 列出每个捕获点的次数和最近一次异常，重启游戏才清零。日志里搜 `/swallowed]` 也能找到这些异常（每个捕获点第一次会打完整堆栈）。

## 第一次搭环境（Windows）

1. 安装 [Git for Windows](https://git-scm.com/download/win) 和 JDK 21（推荐 Eclipse Temurin 21）。
2. 拿到仓库：
   ```
   git clone https://github.com/Zhumi-Heavy-Industries/gtlmodular.git gtl-modular
   cd gtl-modular
   ```
3. 设置提交用的名字和邮箱（只设一次）。邮箱要用 GitHub 账号里登记过的邮箱，或者 GitHub 给你的 noreply 地址（在 GitHub 的 Settings → Emails 里能看到），提交才会显示成你的账号：
   ```
   git config user.name "你的名字"
   git config user.email "你的邮箱"
   ```
4. 把依赖 jar 放进 `libs\`，清单见 [docs/依赖.md](docs/依赖.md)。
5. 第一次构建，会下载 Minecraft 开发环境，要几分钟到十几分钟：
   ```
   set JAVA_HOME=<JDK 21 的路径>
   gradlew.bat build
   ```
   成功后 `build\libs\` 里有 `gtlmodular-<版本>.jar` 和 `gtl_nac-<版本>.jar`。之后可以加 `--offline`。

Linux/macOS 把 `gradlew.bat` 换成 `./gradlew`。

## 日常开发

```
gradlew.bat build --offline          编译、打包两个 jar、跑全部检查和单元测试
gradlew.bat runFullGameTest --offline 完整版自动测试（见 docs/测试.md）
gradlew.bat runNacGameTest --offline  独立版自动测试
gradlew.bat decompileDeps            把 libs/ 里的依赖反编译到 deps-src/，查上游源码用
gradlew.bat runClient / runServer    启动完整版调试实例（目录 run/）
gradlew.bat runNacClient / runNacServer  启动独立版调试实例（目录 run-nac/）
```

调试实例要自己把整合包的其他运行依赖放进对应目录的 `mods/`，但不要放本仓库自己的 jar。

`build` 会自动做这些检查，失败时会告诉你哪里要改：

- **checkDatedComments**：Java 代码里不能出现日期。
- **checkMixins**：每个 mixin 都登记在对应产物的 mixins.json 里。
- **checkProductContents**：独立版里没有混进样板总成和地心的代码；两个 jar 各自只有自己的入口；模型和贴图引用齐全。
- **checkSwallowedErrors**：`catch (Throwable)` 只许减少；已有的每一处都要调用 `SwallowedErrors.report` 留下记录，或者写一行 `// 探测：<为什么可以忽略>` 注释。
- **test**：单元测试。

## 源码与资源

源码目录和关键类见 [AGENTS.md](AGENTS.md) 的代码地图。资源只改 `src/main/resources/`：

- 独立版的资源由 `gradle/nac-resources.gradle` 在构建时从这里生成（注册空间换成 `gtl_nac:`），不要手改 `build/generated/`。
- 新加的 NAC 配方放在 `data/gtlmodular/recipes/` 的 `nanochip/`、`missing_acquisition/`、`coolant/` 目录下会自动带进独立版；放到别的目录要在生成脚本的 `recipeDirectories` 里加上。
- 运行时才拼出来的贴图地址和语言键，生成脚本找不到，要加进它的 `dynamicAssets` 或语言筛选规则，否则独立版会缺资源。

## 协作流程

`main` 分支是锁住的：不能直接推送，只能通过 PR 合并，而且要另一个人批准。每件事都在自己的分支上做：

1. 从最新的 `main` 开一个新分支。分支名用英文，比如 `fix/calibration`、`feature/new-module`：
   ```
   git switch main
   git pull
   git switch -c fix/calibration
   ```
2. 改代码，`gradlew.bat build` 通过，需要的话跑 GameTest，然后提交。一个分支里可以提交很多次。
3. 推到 GitHub（第一次推送时会弹出浏览器让你登录 GitHub）：
   ```
   git push -u origin fix/calibration
   ```
4. 在 GitHub 网页上点 **Compare & pull request** 开 PR。标题写改了什么，正文写为什么改、测了什么。合并后这两段会变成 `main` 上的提交信息。
5. 等另一个人批准，点 **Squash and merge**。分支会自动删掉。
6. 回到本地：
   ```
   git switch main
   git pull
   ```

只想打个 jar 给自己测，在自己的分支上 `gradlew.bat build` 就行，不用合并。
