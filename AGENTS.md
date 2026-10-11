# gtlmodular 开发规矩

写给在这个仓库里写代码的 AI（以及人）。每次开始干活前先读一遍。

## 项目

GTL 整合包的附属模组「GTL更多实用设备」，Forge 1.20.1 + GTCEu 1.4.4。主要内容：

- 纳米芯片组装复合体（NAC，移植自 GTNH）：主机、模块、独立线程、校准、真空传送
- 猪咪样板总成系列（MV～UIV、超级、惰性、ME 通配符）与样板仓复制/剪切工具
- 地心涌动能量转换枢纽及其三个模块

## 构建与验证

- 需要 JDK 21（产物仍是 Java 17 字节码）。依赖 jar 放在 `libs/`，不进 git，见 `README.md`。
- 验证命令：`gradlew.bat build`（Linux/macOS 用 `./gradlew build`）。它会编译、打包，并跑 `checks/` 里的检查。
- **没跑过 build 并且通过，就不要说"改好了"。** 汇报时分清三类：编译和检查验证过的、进游戏测过的、还没测的。
- 进游戏调试：`gradlew.bat runClient`。

## 规矩

1. **一个改动一次 git 提交。** 第一行写改了什么，正文写为什么改。历史靠 git 记，不靠注释。
2. **注释只写"为什么"。** 不写日期、版本号、"队长说"、修改经过、"照抄某文件第几行"（行号会变）。这些写进提交信息；玩家能感知的变化写进 `CHANGELOG.md`。代码本身已经说清楚的事不用再注释。构建会检查：代码里带日期的行只许减少（`checks/dated-comment-baseline.txt`）。
3. **不复制类。** 两处要用同一段逻辑，就抽成共享的类或组件再复用。`LazyPatternAssemblyPartMachine` 和 `MoltenPatternAssemblyPartMachine` 就是复制出来的（正在合并），不要再新增这种复制。
4. **新 mixin 必须登记进 `gtlmodular.mixins.json`。** 只有 `mods.toml` 里列出的 mixin 配置会被加载，没登记的 mixin 永远不生效。构建会检查。`checks/mixin-unregistered-allowlist.txt` 里是历史遗留、待决定的，只许删，不许往里加。
5. **能量和物品必须守恒。** 扣不到就等，不许"视为已充足""按承诺记账"这类凭空放行。电网账户、无线电网终端都要真实扣电。
6. **功能开关放进 `GTLModularConfig`**，不要再新增 `public static boolean` 开关。
7. **上游 API 不确定就去查，不要猜。** 用 `javap -cp libs/<jar> <完整类名>` 看签名；看实现加 `-c -p`。
8. **版本号只改 `gradle.properties` 里的 `mod_version`。** 换版本后，mods 目录里的旧 jar 必须删掉，否则 Forge 会以 "Duplicate mod" 拒绝加载。
9. **大类不要继续变大。** 新功能放进新类；改到上千行的类时，顺手把相关的部分拆出去。

## 代码地图

- `common/machine/nanochip/`：NAC 主机、模块、独立线程、并行、校准。`NanochipConcurrentBatches` 是与 NAC 无关的通用批次引擎。
- `common/machine/`：样板总成（猪咪 / 惰性）、样板仓归档、地心涌动枢纽与模块
- `common/data/`：机器与方块注册、多方块结构、谓词
- `client/`：Jade、JEI、CTM、渲染
- `mixin/`：mixin（登记见规矩 4）
- 入口 `GTLModular.java`；配置 `common/config/GTLModularConfig.java`

## 交接

改完一批、都提交之后，运行 `scripts\导出交接包.bat`，把生成的 `.bundle` 文件发给对方（QQ 传文件之类都行）。
