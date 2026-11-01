# 第三方素材声明 · GregTech Odyssey（GTO / GTOCore）

> **Required Notice: Copyright GregTech Odyssey (https://github.com/GregTech-Odyssey)**
>
> 本模组随包附带上游许可全文（请一并分发）：
> - `licenses/GTOCore-LICENSE.md` —— PolyForm Noncommercial 1.0.0 全文 + 上述 Required Notice 行
> - `licenses/CC-BY-NC-SA-4.0.txt` —— 非代码资源（纹理/文本）许可全文
>
> jar 内对应位置：`META-INF/licenses/GTOCore-LICENSE.txt`、`META-INF/licenses/CC-BY-NC-SA-4.0.txt`。

本模组（`gtlmodular`，GTL 更多实用设备）**包含从 GregTech Odyssey 的 GTOCore 派生的少量素材与数据**。
本文件按 GTO 仓库 `README_zh.md → 许可证` 章节（2026-11 现行版，链接见下）履行署名与许可告知义务。

## 1. 来源

| 项 | 值 |
|---|---|
| 上游项目 | GregTech Odyssey（GTO），核心模组 GTOCore |
| 仓库 | https://github.com/GregTech-Odyssey/GregTech-Odyssey · https://github.com/GregTech-Odyssey/GTOCore-Main |
| 许可章节 | https://github.com/GregTech-Odyssey/GregTech-Odyssey/blob/main/README_zh.md#许可证 |
| 我们取证的构件 | `gtocore-forge-1.20.1-26.10.1.jar`（2026-10-04 构建） |

## 2. 上游许可（按 README 许可证章节）

| 内容 | 许可 |
|---|---|
| 整合包仓库（模组选择、配置、脚本、整体编排） | 保留所有权利（All Rights Reserved） |
| **GTOCore 代码** | **PolyForm Noncommercial 1.0.0**（<https://polyformproject.org/licenses/noncommercial/1.0.0>） |
| **GTOCore 非代码资源**（原创纹理、模型、文本等） | **CC BY-NC-SA 4.0**（<https://creativecommons.org/licenses/by-nc-sa/4.0/>） |
| GTOCore 任务数据 / 监控器系统（`com.gtocore.common.machine.monitor`） | 保留所有权利（本模组**未使用**） |
| gtolib | 闭源，保留所有权利（本模组**未使用其代码**） |
| 社区翻译 | CC BY-NC-SA 4.0 |

> 注：上游 jar 内 `META-INF/mods.toml` 仍写着 `license="LGPLv3.0"`，与现行 README 不一致；
> 本文件以上游 README 的「统一许可证说明」为准。整个项目以**非商业用途**为目的发布。

## 3. 本模组中来自 GTO 的内容（逐项）

### 3.1 纹理（CC BY-NC-SA 4.0，**逐字节原样拷贝，未修改**）

下列 PNG 与 GTOCore jar 内原件 sha256 完全相同（2026-11-01 核对）：

| 本模组文件 | GTOCore 原件 | sha256 |
|---|---|---|
| `assets/gtlmodular/textures/block/casings/laser_casing.png` | `assets/gtocore/textures/block/laser_casing.png` | `9b142639…be66` |
| `assets/gtlmodular/textures/block/casings/amprosium_active_casing.png` (+`.mcmeta`) | `assets/gtocore/textures/block/neutronium_active_casing.png` | `860f2fbc…e3455c` |
| `assets/gtlmodular/textures/block/casings/quark_pipe.png` (+`.mcmeta`) | `assets/gtocore/textures/block/quark_pipe.png` | `6b2aa98a…7a0b2` |
| `assets/gtlmodular/textures/block/casings/electron_permeable_amprosium_coated_glass.png` | `assets/gtocore/textures/block/casings/electron_permeable_neutronium_coated_glass.png` | `734dcf4b…fcbc` |
| `assets/gtlmodular/textures/block/casings/amprosium_borosilicate_glass.png` | `assets/gtocore/textures/block/casings/neutronium_borosilicate_glass.png` | `813d7212…a973` |

这些文件按 **CC BY-NC-SA 4.0** 分发（署名、非商业、相同方式共享）；**无改动**。

### 3.2 结构数据（源自 GTOCore，PolyForm NC 1.0.0 代码/数据）

- `gtlmodular:nyarlathoteps_tentacle` 的 165×48×49 结构，来自 GTOCore 的
  `pattern/nyarlathoteps_tentacle.mbs`（二进制 → 本模组 Java 字符串数组，等价转写）。
- 各符号 → 方块的语义映射取自 GTOCore `MultiBlockD.java` 的谓词表。

### 3.3 文本（CC BY-NC-SA 4.0）

- 奈亚拉托提普之触的 18 行工具提示（设定文案 + 可特殊并行/并行数/激光仓/多线程/配方类型），
  来自 GTOCore 语言文件（`gtocore.lang.nyarlathoteps_tentacle.*` 等），仅删去一句「允许安装线程仓…」
  并追加「结构来源：GTO」（改动已在此标注）。

### 3.4 配方数据

- 彩色玻璃板四步链、电子渗透安普洛涂层玻璃三步链、夸克管道组装线等配方，是**按 GTOCore 运行时
  配方代码的数值**（机器、输入输出、EUt、时长）在我们自己的配方格式里重写的；不含 GTOCore 代码。

## 4. 本模组未使用的内容

- GTOCore 任务数据（`quest/`、任务贴图、`questbook.json`、`gtocore.quest.*` 文本）；
- GTOCore 监控器系统（`com.gtocore.common.machine.monitor`）；
- gtolib 的代码（我们只按其 `.mbs` 文件格式自行实现了 `parse_mbs.py` 解析器）；
- GTOCore 的 Java 类（未反编译复制代码；只做了接口/数值层面的取证）。

## 5. 使用范围声明

- 本模组仅供**非商业**用途（个人整合包/服务器游玩与改造），符合 PolyForm Noncommercial 1.0.0
  与 CC BY-NC-SA 4.0 的非商业条款。
- **不得**将本模组或其派生内容用于商业用途。
- 若要把包含上述 GTO 派生素材的模组**公开分发/再发布**：需保留本声明与上游许可链接；
  派生纹理/文本按 CC BY-NC-SA 4.0 同许可分发；并建议按上游 README 中「超出下载/游玩范围的
  复制、修改与再发布需事先取得授权」的表述，先与 GTO 作者确认。

## 6. Required Notice（已取得原文）

上游 GTOCore 仓库 `LICENSE` 文件（分支 `0.6.0`，sha `9df1402a…`）开头即为 Required Notice 原文：

```
Required Notice: Copyright GregTech Odyssey (https://github.com/GregTech-Odyssey)
```

该行已原样抄录在本文件顶部，并按 PolyForm Noncommercial 1.0.0「Notices」条款随包附带许可全文
（`licenses/GTOCore-LICENSE.md` 与 jar 内 `META-INF/licenses/GTOCore-LICENSE.txt`）。

> 取证方式（2026-11-01）：GitHub API
> `GET https://api.github.com/repos/GregTech-Odyssey/GTOCore-Main/contents/LICENSE?ref=0.6.0`
> （base64 解码，5,127 B）；同仓库 `licenses/` 目录下另有 `CC-BY-NC-SA-4.0.txt`（20,849 B）等全文。
