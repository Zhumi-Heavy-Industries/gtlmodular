# GTL更多实用设备（gtlmodular）

GTL 整合包的附属模组，Forge 1.20.1 + GTCEu 1.4.4。

- 写代码的规矩（AI 和人都要看）：[AGENTS.md](AGENTS.md)
- 玩家能感知的改动：[CHANGELOG.md](CHANGELOG.md)

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
   产物在 `build\libs\gtlmodular-<版本>.jar`。环境下载好之后，可以加 `--offline`。

## 日常开发

- 改代码 → `gradlew.bat build` 通过 → `git commit`。一个改动一次提交。
- 进游戏调试：`gradlew.bat runClient`
- 发新版本：改 `gradle.properties` 里的 `mod_version`，在 `CHANGELOG.md` 写上这一版的变化，再 build。

`gradlew.bat build` 会顺带跑两项检查，没通过就不能交付：

- **checkMixins**：每个 mixin 都要登记进 `gtlmodular.mixins.json`，否则它永远不会生效。
- **checkDatedComments**：代码里带日期的行只许减少。日期和修改经过写进提交信息，不要写进注释。

## 交接（互相发 git bundle 文件）

- **发给对方**：改完、都提交之后，双击 `scripts\导出交接包.bat`。它会在仓库的上一级目录生成 `gtlmodular-<时间>.bundle`，把这个文件发给对方（QQ 传文件之类都行）。
- **收到对方的**：把 `.bundle` 文件拖到 `scripts\导入交接包.bat` 上，或者在仓库里运行：
  ```
  git pull <bundle 路径> main
  ```
- 双方同时改同一个文件会冲突。进行大改动之前先说一声，错开同一批文件。

## 依赖（libs/）

编译期依赖全部是本地 jar（不走 maven 远程坐标），版本必须一致：

| jar | 说明 |
|---|---|
| `gtceu-1.20.1-1.4.4.jar` | GTCEu |
| `gtlcore-1.2.3.2-fix4.jar` | GTL Core |
| `[Custom-亚空间]gtladditions-3.3.1Custom_SubSpace.jar` | GTL Additions（Java 21 字节码，所以必须用 JDK 21 编译） |
| `[修复高级舱室的崩溃]gtmthings-1.3.5.fix(…).jar` | GTM Things（无线电网） |
| `ldlib-forge-1.20.1-1.0.33.b.jar` | LDLib |
| `Registrate-MC1.20-1.3.3.jar` | Registrate |
| `appliedenergistics2-forge-15.4.10.jar` | AE2 |
| `extendedae_plus_gtladd-1.0.3.jar` | ExtendedAE Plus |
| `GTLsupb-3.4.beta.7.jar`、`GTMAdvancedHatch-0.1.9-hotfix2.jar`、`Industrial Platform-1.20.1-1.8.0.jar`、`infinitepower-2.0.jar` | 其他 GT 附属 |
| `Jade-1.20.1-Forge-11.13.3.jar`、`jei-1.20.1-forge-15.49.0.188.jar` | 显示类 |
| `kubejs-forge-2001.6.5-build.26.jar`、`architectury-9.2.14-forge.jar`、`cloth-config-11.1.136-forge.jar` | 前置 |

`libs/` 下的子目录（例如旧版备份 `_old_*`）不参与编译。GTL-Enhancedcore 不在编译依赖里，运行时由整合包提供。
