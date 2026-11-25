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

#### 3.1.1 2026-11-01 新增（0.3.270 研究/计算件 + 0.3.271 集控核心）

同样**逐字节原样拷贝**（含 `.mcmeta` 动画文件），sha256 与 GTOCore jar 内原件完全相同
（2026-11-01 用 `sha256sum` 对 GTOCore 26.10.1 jar 逐对比对）：

| 本模组文件 | GTOCore 原件 | sha256 |
|---|---|---|
| `assets/gtlmodular/textures/item/cerebrum.png` | `assets/gtocore/textures/item/cerebrum.png` | `f5c418a2…5ba4` |
| `assets/gtlmodular/textures/item/super_cerebrum.png` | `assets/gtocore/textures/item/super_cerebrum.png` | `2560c2f8…bef3` |
| `assets/gtlmodular/textures/item/neural_matrix.png` (+`.mcmeta`) | `assets/gtocore/textures/item/neural_matrix.png` | `f30fce52…26b4` |
| `assets/gtlmodular/textures/item/obsidian_matrix.png` (+`.mcmeta`) | `assets/gtocore/textures/item/obsidian_matrix.png` | `6188e174…e791` |
| `assets/gtlmodular/textures/item/relativistic_spinorial_memory_system.png` (+`.mcmeta`) | `assets/gtocore/textures/item/relativistic_spinorial_memory_system.png` | `b9b47964…3375` |
| `assets/gtlmodular/textures/item/rydberg_spinorial_assembly.png` (+`.mcmeta`) | `assets/gtocore/textures/item/rydberg_spinorial_assembly.png` | `1003bd9f…364a` |
| `assets/gtlmodular/textures/item/nich_empty_component.png` | `assets/gtocore/textures/block/casings/about_computer/nich_empty_component.png` | `c417faf2…995a` |
| `assets/gtlmodular/textures/item/nich_computing_component.png` | `assets/gtocore/textures/block/casings/about_computer/nich_computing_components.png` | `b29ac744…aa35` |
| `assets/gtlmodular/textures/item/gwca_empty_component.png` (+`.mcmeta`) | `assets/gtocore/textures/block/casings/about_computer/gwca_empty_component.png` | `bd9b0ec2…2be8` |
| `assets/gtlmodular/textures/item/gwca_computing_component.png` (+`.mcmeta`) | `assets/gtocore/textures/block/casings/about_computer/gwca_computing_components.png` | `c9b59864…665a` |
| `assets/gtlmodular/textures/block/casings/biocomputer_casing.png` | `assets/gtocore/textures/block/casings/about_computer/biocomputer_shell.png` | `405c9540…0d49` |
| `assets/gtlmodular/textures/item/uv_integrated_control_core.png` (+`.mcmeta`) | `assets/gtocore/textures/item/uv_integrated_control_core.png` | `223069d6…399d` |
| `assets/gtlmodular/textures/item/uhv_integrated_control_core.png` (+`.mcmeta`) | `assets/gtocore/textures/item/uhv_integrated_control_core.png` | `6eab0bbd…8be4` |
| `assets/gtlmodular/textures/item/uev_integrated_control_core.png` (+`.mcmeta`) | `assets/gtocore/textures/item/uev_integrated_control_core.png` | `2ff2736c…cec4` |
| `assets/gtlmodular/textures/item/uiv_integrated_control_core.png` (+`.mcmeta`) | `assets/gtocore/textures/item/uiv_integrated_control_core.png` | `3a313498…d045` |
| `assets/gtlmodular/textures/item/uxv_integrated_control_core.png` (+`.mcmeta`) | `assets/gtocore/textures/item/uxv_integrated_control_core.png` | `e331fe10…d11c` |
| `assets/gtlmodular/textures/item/opv_integrated_control_core.png` (+`.mcmeta`) | `assets/gtocore/textures/item/opv_integrated_control_core.png` | `b5cfac91…be9e` |
| `assets/gtlmodular/textures/item/max_integrated_control_core.png` (+`.mcmeta`) | `assets/gtocore/textures/item/max_integrated_control_core.png` | `c6e7ebbc…9f02` |

### 3.2 结构数据（源自 GTOCore，PolyForm NC 1.0.0 代码/数据）

- `gtlmodular:nyarlathoteps_tentacle` 的 165×48×49 结构，来自 GTOCore 的
  `pattern/nyarlathoteps_tentacle.mbs`（二进制 → 本模组 Java 字符串数组，等价转写）。
- 各符号 → 方块的语义映射取自 GTOCore `MultiBlockD.java` 的谓词表。

### 3.3 文本（CC BY-NC-SA 4.0）

- 奈亚拉托提普之触的 18 行工具提示（设定文案 + 可特殊并行/并行数/激光仓/多线程/配方类型），
  来自 GTOCore 语言文件（`gtocore.lang.nyarlathoteps_tentacle.*` 等），仅删去一句「允许安装线程仓…」
  并追加「结构来源：GTO」（改动已在此标注）。
- 集控核心 7 档的物品名（`§xUV§r集控核心` … `§4§lMAX§r集控核心`），来自 GTOCore 语言文件
  `item.gtocore.<tier>_integrated_control_core`，仅把注册名改成 `integrated_control_core_<tier>`
  （0.3.270 研究/计算件的名称同理，取自 `item.gtocore.*`）。

### 3.4 配方数据

- 彩色玻璃板四步链、电子渗透安普洛涂层玻璃三步链、夸克管道组装线等配方，是**按 GTOCore 运行时
  配方代码的数值**（机器、输入输出、EUt、时长）在我们自己的配方格式里重写的；不含 GTOCore 代码。
- 集控核心的组装线 / 部件装配线两条配方（UV..OpV 六档），同样按 GTOCore
  `ComponentRecipes.integrated_control_core` 的数值重写；材料按本整合包实际存在的物品 id 落地，
  MAX 档因 `ChaosInfinityAlloy` / `SpacetimeContinuum` 本包不存在而暂缓（见模组启动日志）。

### 3.5 纹理补充（2026-11-01 · 0.3.269–0.3.282 批次）

本批次新增 32 个贴图（另含同名 `.mcmeta` 动画声明，均逐字节同源），全部取自 GTOCore jar
`assets/gtocore/textures/`；路径不同的三处（NICH/GWCA 计算组件、生物计算机外壳）在 GTO 里是**方块**贴图，
本模组把它们用作对应物品/方块贴图，内容未改动。

| 本模组路径（`assets/gtlmodular/textures/`） | GTOCore 原路径（`assets/gtocore/textures/`） | sha256 前 16 位 |
|---|---|---|
| `block/casings/biocomputer_casing.png` | `block/casings/about_computer/biocomputer_shell.png` | `405c954018a0d19d` |
| `block/machines/thread_hatch/thread_hatch_mk1/overlay_front.png` | `block/machines/thread_hatch/thread_hatch_mk1/overlay_front.png` | `465d3cfdc05c686a` |
| `block/machines/thread_hatch/thread_hatch_mk1/overlay_front_active.png` | `block/machines/thread_hatch/thread_hatch_mk1/overlay_front_active.png` | `866d8a528bebd8a1` |
| `block/machines/thread_hatch/thread_hatch_mk2/overlay_front.png` | `block/machines/thread_hatch/thread_hatch_mk2/overlay_front.png` | `690245f4d60d0697` |
| `block/machines/thread_hatch/thread_hatch_mk2/overlay_front_active.png` | `block/machines/thread_hatch/thread_hatch_mk2/overlay_front_active.png` | `dde0cd7806d68af0` |
| `block/machines/thread_hatch/thread_hatch_mk3/overlay_front.png` | `block/machines/thread_hatch/thread_hatch_mk3/overlay_front.png` | `19d9b3d6e68903f1` |
| `block/machines/thread_hatch/thread_hatch_mk3/overlay_front_active.png` | `block/machines/thread_hatch/thread_hatch_mk3/overlay_front_active.png` | `db21f66ebe4b45e8` |
| `block/machines/thread_hatch/thread_hatch_mk4/overlay_front.png` | `block/machines/thread_hatch/thread_hatch_mk4/overlay_front.png` | `53faac3b5a977e04` |
| `block/machines/thread_hatch/thread_hatch_mk4/overlay_front_active.png` | `block/machines/thread_hatch/thread_hatch_mk4/overlay_front_active.png` | `467db441feb6784f` |
| `block/machines/thread_hatch/thread_hatch_mk5/overlay_front.png` | `block/machines/thread_hatch/thread_hatch_mk5/overlay_front.png` | `43eb019da817541f` |
| `block/machines/thread_hatch/thread_hatch_mk5/overlay_front_active.png` | `block/machines/thread_hatch/thread_hatch_mk5/overlay_front_active.png` | `713a91892f539c32` |
| `block/machines/thread_hatch/thread_hatch_mk6/overlay_front.png` | `block/machines/thread_hatch/thread_hatch_mk6/overlay_front.png` | `0231bf2201132ab9` |
| `block/machines/thread_hatch/thread_hatch_mk6/overlay_front_active.png` | `block/machines/thread_hatch/thread_hatch_mk6/overlay_front_active.png` | `1ddaaee588bf302e` |
| `block/machines/thread_hatch/thread_hatch_mk7/overlay_front.png` | `block/machines/thread_hatch/thread_hatch_mk7/overlay_front.png` | `1e49e35f9af026ea` |
| `block/machines/thread_hatch/thread_hatch_mk7/overlay_front_active.png` | `block/machines/thread_hatch/thread_hatch_mk7/overlay_front_active.png` | `e131809cae7ecf0d` |
| `block/machines/overclock_hatch/overclock_hatch_mk1/overlay_front.png` | `block/machines/overclock_hatch/overclock_hatch_mk1/overlay_front.png` | `8aed32dca2f2a0d3` |
| `block/machines/overclock_hatch/overclock_hatch_mk2/overlay_front.png` | `block/machines/overclock_hatch/overclock_hatch_mk2/overlay_front.png` | `7731a0f62f83175d` |
| `block/machines/overclock_hatch/overclock_hatch_mk3/overlay_front.png` | `block/machines/overclock_hatch/overclock_hatch_mk3/overlay_front.png` | `ef3148dbf19da3a2` |
| `block/machines/overclock_hatch/overclock_hatch_mk4/overlay_front.png` | `block/machines/overclock_hatch/overclock_hatch_mk4/overlay_front.png` | `2a59cbf63c9b344c` |
| `block/machines/overclock_hatch/overclock_hatch_mk5/overlay_front.png` | `block/machines/overclock_hatch/overclock_hatch_mk5/overlay_front.png` | `aa876c0e664ef3cc` |
| `block/machines/overclock_hatch/overclock_hatch_mk6/overlay_front.png` | `block/machines/overclock_hatch/overclock_hatch_mk6/overlay_front.png` | `007bdbc8a36c273a` |
| `block/machines/overclock_hatch/overclock_hatch_mk7/overlay_front.png` | `block/machines/overclock_hatch/overclock_hatch_mk7/overlay_front.png` | `03687ffea7cdd969` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk1/overlay_front.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk1/overlay_front.png` | `8493142ac1e9fcab` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk1/overlay_front_active.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk1/overlay_front_active.png` | `0243d3fd5cf107fe` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk2/overlay_front.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk2/overlay_front.png` | `1197e33d2bd31883` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk2/overlay_front_active.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk2/overlay_front_active.png` | `d71afedff68ca2cb` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk3/overlay_front.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk3/overlay_front.png` | `e13d0390a9bbbcd3` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk3/overlay_front_active.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk3/overlay_front_active.png` | `193df88845b2c06a` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk4/overlay_front.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk4/overlay_front.png` | `28dcb1f00670a029` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk4/overlay_front_active.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk4/overlay_front_active.png` | `65a021c7a68336c5` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk5/overlay_front.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk5/overlay_front.png` | `272b0e084c835428` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk5/overlay_front_active.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk5/overlay_front_active.png` | `92c11566c110425f` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk6/overlay_front.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk6/overlay_front.png` | `2b3742813cb49d29` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk6/overlay_front_active.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk6/overlay_front_active.png` | `66dcdf4aae2e7508` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk7/overlay_front.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk7/overlay_front.png` | `58c66b392aeb64ea` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk7/overlay_front_active.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk7/overlay_front_active.png` | `186c31dbafcaa7a1` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk8/overlay_front.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk8/overlay_front.png` | `e13d0390a9bbbcd3` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk8/overlay_front_active.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk8/overlay_front_active.png` | `193df88845b2c06a` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk9/overlay_front.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk9/overlay_front.png` | `068338f96ccedd15` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk9/overlay_front_active.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk9/overlay_front_active.png` | `1eec3da3bfee2a6b` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk10/overlay_front.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk10/overlay_front.png` | `1ff1e3c4814bd2bf` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk10/overlay_front_active.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk10/overlay_front_active.png` | `4fff5b800f4a499b` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk11/overlay_front.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk11/overlay_front.png` | `69e839691f219bf8` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk11/overlay_front_active.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk11/overlay_front_active.png` | `280bd80bf1560014` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk12/overlay_front.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk12/overlay_front.png` | `38ac6854c687ef10` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk12/overlay_front_active.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk12/overlay_front_active.png` | `187b53ac58536b6e` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk13/overlay_front.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk13/overlay_front.png` | `25e7303c162540cd` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk13/overlay_front_active.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk13/overlay_front_active.png` | `1efb1353264bba65` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk14/overlay_front.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk14/overlay_front.png` | `dc19ec2f5e4d951f` |
| `block/machines/accelerate_hatch/accelerate_hatch_mk14/overlay_front_active.png` | `block/machines/accelerate_hatch/accelerate_hatch_mk14/overlay_front_active.png` | `ef586c6eba7bdeec` |
| `item/cerebrum.png` | `item/cerebrum.png` | `f5c418a241f646cb` |
| `item/gwca_computing_component.png` | `block/casings/about_computer/gwca_computing_components.png` | `c9b59864dfba777d` |
| `item/gwca_empty_component.png` | `block/casings/about_computer/gwca_empty_component.png` | `bd9b0ec22992e53e` |
| `item/max_integrated_control_core.png` | `item/max_integrated_control_core.png` | `c6e7ebbc09079779` |
| `item/neural_matrix.png` | `item/neural_matrix.png` | `f30fce52e9cbae4b` |
| `item/nich_computing_component.png` | `block/casings/about_computer/nich_computing_components.png` | `b29ac744058289bc` |
| `item/nich_empty_component.png` | `block/casings/about_computer/nich_empty_component.png` | `c417faf2f511f4dc` |
| `item/obsidian_matrix.png` | `block/machines/slot_machine/symbol_obsidian_matrix.png` | `6188e1740fd8be79` |
| `item/opv_integrated_control_core.png` | `item/opv_integrated_control_core.png` | `b5cfac91fc312c26` |
| `item/relativistic_spinorial_memory_system.png` | `item/relativistic_spinorial_memory_system.png` | `b9b47964c6f812bd` |
| `item/rydberg_spinorial_assembly.png` | `item/rydberg_spinorial_assembly.png` | `1003bd9fa6780882` |
| `item/super_cerebrum.png` | `item/super_cerebrum.png` | `2560c2f8188f7375` |
| `item/uev_integrated_control_core.png` | `item/uev_integrated_control_core.png` | `2ff2736c8094d852` |
| `item/uhv_integrated_control_core.png` | `item/uhv_integrated_control_core.png` | `6eab0bbd5853da9d` |
| `item/uiv_integrated_control_core.png` | `item/uiv_integrated_control_core.png` | `3a31349854a75fee` |
| `item/uv_integrated_control_core.png` | `item/uv_integrated_control_core.png` | `223069d6f836eaae` |
| `item/uxv_integrated_control_core.png` | `item/uxv_integrated_control_core.png` | `e331fe10ed5cd9ee` |

> 校验方式：`sha256` 逐字节比对（2026-11-01，GTOCore 26.10.1）。校验脚本：`_scratch/tentacle/gen_notice_rows.py`。

### 3.6 高维等离子聚变核心（2026-11-02 · 0.3.309 批次）

> 来源：GTOCore `pattern/hyperdimensional_plasma_fusion_core.mbs`（结构）与
> `assets/gtocore/textures/`（贴图）。方块贴图与 `catalyst_base` **逐字节原样拷贝**；
> 其余物品贴图是**按材料色对母版相乘着色**的派生图 —— 两类都按 CC BY-NC-SA 4.0 分发
> （派生图按同许可「相同方式共享」）。

**方块贴图（18 件，逐字节拷贝）**

| 本模组文件（`assets/gtlmodular/textures/`） | GTOCore 原件（`assets/gtocore/textures/`） | sha256 |
|---|---|---|
| `block/casings/abs_white_casing.png` | `block/casings/abs_white_casing.png` | "ccc5c2d3baaa5634…19cf" |
| `block/casings/abs_black_casing.png` | `block/casings/abs_black_casing.png` | "170460a025b65fee…93c8" |
| `block/casings/abs_blue_casing.png` | `block/casings/abs_blue_casing.png` | "80290a5d6224478e…8e94" |
| `block/casings/abs_brown_casing.png` | `block/casings/abs_brown_casing.png` | "1f61f36ad185e2b0…b4ee" |
| `block/casings/abs_cyan_casing.png` | `block/casings/abs_cyan_casing.png` | "a49177ac0690da7a…ad3d" |
| `block/casings/abs_green_casing.png` | `block/casings/abs_green_casing.png` | "7e52d1bea4adfe6a…f210" |
| `block/casings/abs_grey_casing.png` | `block/casings/abs_grey_casing.png` | "d885fbcd71e4fd6a…d572" |
| `block/casings/abs_light_bull_casing.png` | `block/casings/abs_light_bull_casing.png` | "feb5bdc239b6e568…0319" |
| `block/casings/abs_light_grey_casing.png` | `block/casings/abs_light_grey_casing.png` | "0ae7b453e609265b…436b" |
| `block/casings/abs_lime_casing.png` | `block/casings/abs_lime_casing.png` | "ca2e39feeef5ddbc…b8ba" |
| `block/casings/abs_magenta_casing.png` | `block/casings/abs_magenta_casing.png` | "a7e42c7f00c4cbb2…d562" |
| `block/casings/abs_orange_casing.png` | `block/casings/abs_orange_casing.png` | "bc9868010565caa7…a8d2" |
| `block/casings/abs_pink_casing.png` | `block/casings/abs_pink_casing.png` | "f75ed04423abd655…ec8d" |
| `block/casings/abs_purple_casing.png` | `block/casings/abs_purple_casing.png` | "55dd9bd574683e69…00b1" |
| `block/casings/abs_red_casing.png` | `block/casings/abs_red_casing.png` | "6272789191675ab7…791a" |
| `block/casings/abs_yellow_casing.png` | `block/casings/abs_yellow_casing.png` | "4e27e689de190d95…ed26" |
| `block/casings/strontium_carbonate_ceramic_ray_absorbing_mechanical_cube.png` | `block/strontium_carbonate_ceramic_ray_absorbing_mechanical_cube.png` | "4297418a655871f6…ef76" |
| `block/casings/cobalt_oxide_ceramic_strong_thermally_conductive_mechanical_block.png` | `block/cobalt_oxide_ceramic_strong_thermally_conductive_mechanical_block.png` | "f7804f1d8ed7d24f…bcf1" |

**物品贴图（10 件）**

| 本模组文件（`assets/gtlmodular/textures/`） | 来源 | sha256 |
|---|---|---|
| `item/hpfc/catalyst_base.png` | `item/catalyst_base.png`（逐字节拷贝） | "ae679931508d30fa…ffe8" |
| `item/hpfc/hafnium_catalyst.png` | 以 `catalyst_base.png` 为母版、按铪色 `0x8A9AA8` 相乘着色（派生） | "2415283c2d08eaea…ac4c" |
| `item/hpfc/strontium_carbonate_ceramic_rough_blank.png` | 母版 gtceu dull/crushed + 材料色 0xC5C5C5 相乘着色（派生） | "8b3b1f5cba4767aa…35bb" |
| `item/hpfc/strontium_carbonate_ceramic_block.png` | 母版 gtceu dull/block + 材料色 0xC5C5C5 相乘着色（派生） | "35130685ed3fa3f9…2eff" |
| `item/hpfc/strontium_carbonate_ceramic_brick.png` | 母版 gtceu dull/ingot + 材料色 0xC5C5C5 相乘着色（派生） | "e97dd0f0a9cdd2b7…3b9f" |
| `item/hpfc/strontium_carbonate_ceramic_flake.png` | 母版 gtocore dull/flake + 材料色 0xC5C5C5 相乘着色（派生） | "cca3e7474c282991…6f44" |
| `item/hpfc/cobalt_oxide_ceramic_rough_blank.png` | 母版 gtceu dull/crushed + 材料色 0x222534 相乘着色（派生） | "0c13336c5a5250ec…c90a" |
| `item/hpfc/cobalt_oxide_ceramic_block.png` | 母版 gtceu dull/block + 材料色 0x222534 相乘着色（派生） | "86cc61a161360859…cb33" |
| `item/hpfc/cobalt_oxide_ceramic_brick.png` | 母版 gtceu dull/ingot + 材料色 0x222534 相乘着色（派生） | "8e5b6e5aed430304…e19f" |
| `item/hpfc/cobalt_oxide_ceramic_flake.png` | 母版 gtocore dull/flake + 材料色 0x222534 相乘着色（派生） | "63873ad5b2836e8d…cf4f" |

**结构数据（等价转写）**

- `gtlmodular:hyperdimensional_plasma_fusion_core` 的 **63×83×47（245,763 格）** 结构，来自
  GTOCore 的 `pattern/hyperdimensional_plasma_fusion_core.mbs`（gzip 二进制 → 本模组 Java 字符串数组，
  逐格 0 差异，校验脚本 `_scratch/hpfc/gen_structure_java.py` + `verify_delivery.py`）；
- 各符号 → 方块的语义映射取自 GTOCore `MultiBlockD.java:155-176` 的谓词表。

**配方数据（数值重写，不含代码）**

- 53 条配方按 GTOCore 运行时配方数值重写：聚合反应（ABS / 聚苯乙烯）落到本包 `gtceu:chemical_reactor`
  （队长 2026-11-02 口径），等静压成型 → 装配机（本包压缩机无流体输入口）、烧结 → 电力高炉（1200K），
  其余机器按本包同名机器落地。

### 3.7 既有批次补录（0.3.210 ~ 0.3.266，2026-11-02 核查后补录）

> 2026-11-02 复核发现：以下几批 GTO 派生内容此前**没有逐项**写进本文件（许可文本 / URL /
> Required Notice 一直随包附带，缺的是「逐项清单」）。现按 3.1 同口径补录：
> 贴图为**逐字节拷贝**（CC BY-NC-SA 4.0），结构为**等价转写**，配方为按 GTO 运行时数值**重写**。

#### 3.7.1 恒星炎炀锻炉（0.3.259，`gtceu:stellar_forge` 换壳）

- **结构**：GTO `pattern/stellar_forge.mbs`（65 (X) × 65 (Y) × 15 (Z) = 63,375 格），
  等价转写为 `StellarForgeStructures.SLICES`（逐格 0 差异）；GTO 的 `stellar_forge_magic` 子结构未移植；
- **6 张方块贴图（逐字节拷贝）**：

| 本模组文件（`assets/gtlmodular/textures/`） | GTOCore 原件（`assets/gtocore/textures/`） | sha256 |
|---|---|---|
| `block/casings/radiation_absorbent_casing.png` | `block/radiation_absorbent_casing.png` | "28b87ca9bda9a0d2...824f" |
| `block/casings/boron_carbide_ceramic_radiation_resistant_mechanical_cube.png` | `block/boron_carbide_ceramic_radiation_resistant_mechanical_cube.png` | "913eaa65860bf09f...a389" |
| `block/casings/magnesium_oxide_ceramic_high_temperature_insulation_mechanical_block.png` | `block/magnesium_oxide_ceramic_high_temperature_insulation_mechanical_block.png` | "ee453bb0c95d93d2...3720" |
| `block/casings/magtech_casing.png` | `block/casings/magtech_casing.png` | "67973370f153ca9a...140e" |
| `block/casings/plasma_heater_casing.png` | `block/plasma_heater_casing.png` | "d7ecd6ec92481742...6c89" |
| `block/casings/neutronium_stable_casing.png` | `block/neutronium_stable_casing.png` | "075f5b6ad03ded98...e273" |

- **8 张陶瓷物品贴图（派生图）**：以 gtceu / gtocore 的 dull 母版（crushed / block / ingot / flake）
  按材料色相乘着色（碳化硼陶瓷 `0x2B2B31`、氧化镁陶瓷 `0xFFE198`），按 CC BY-NC-SA 4.0 同许可分发：

| 本模组文件（`assets/gtlmodular/textures/`） | sha256 |
|---|---|
| `item/ceramics/boron_carbide_ceramics_rough_blank.png` | "d89251daa3c56c55...ac25" |
| `item/ceramics/boron_carbide_ceramics_block.png` | "b2ad6d64674d227d...7db3" |
| `item/ceramics/boron_carbide_ceramics_brick.png` | "d6a289ff8e55e860...ef74" |
| `item/ceramics/boron_carbide_ceramics_flake.png` | "42e5f600a09e5771...1b02" |
| `item/ceramics/magnesium_oxide_ceramic_rough_blank.png` | "1d414f9bc0b60240...cfe8" |
| `item/ceramics/magnesium_oxide_ceramic_block.png` | "810470a3ba25bf86...b9c2" |
| `item/ceramics/magnesium_oxide_ceramic_brick.png` | "ffcb50227592160d...0e9b" |
| `item/ceramics/magnesium_oxide_ceramic_flake.png` | "dea2225a70271d1b...03a5" |

- **配方**：3 味合金熔炼 / 两套陶瓷链 / 6 个机壳的装配机配方，按 GTO 运行时配方数值重写
  （机器替代：烧结炉 → 电力高炉、等静压 → 压缩机），不含 GTOCore 代码。

#### 3.7.2 一次性工具 3D 打印机（0.3.210）

- **结构**：GTO `pattern/three_dimensional_printer.mbs`（5×8×7），等价转写，仅多方块
  `gtlmodular:disposable_tool_printing_array` 使用（GTO 的巨构版本已按队长口径删除）；
- **8 张模具贴图（逐字节拷贝）**（刀 / 研钵两张为本模组自绘，不在此列）：

| 本模组文件（`assets/gtlmodular/textures/`） | GTOCore 原件（`assets/gtocore/textures/`） | sha256 |
|---|---|---|
| `item/disposable_crowbar_mold.png` | `item/disposable_crowbar_mold.png` | "3f27079827fe6f91...4aa5" |
| `item/disposable_file_mold.png` | `item/disposable_file_mold.png` | "f07c0a6884fbe95b...bb8b" |
| `item/disposable_hammer_mold.png` | `item/disposable_hammer_mold.png` | "24adef5b89ebdf64...0230" |
| `item/disposable_mallet_mold.png` | `item/disposable_mallet_mold.png` | "9d537669d8d36392...b654" |
| `item/disposable_saw_mold.png` | `item/disposable_saw_mold.png` | "6b14ad5231767eda...1cac" |
| `item/disposable_screwdriver_mold.png` | `item/disposable_screwdriver_mold.png` | "cfb2251622efb306...2632" |
| `item/disposable_wire_cutter_mold.png` | `item/disposable_wire_cutter_mold.png` | "e46c6fa20b19eea9...641d" |
| `item/disposable_wrench_mold.png` | `item/disposable_wrench_mold.png` | "c68fe702381ed6ad...5c64" |

- **配方**：一次性工具 3D 打印配方（单方块 60 条 / 多方块 60 条 / 模具 10 条）按 GTO 数值重写。

#### 3.7.3 部件组装机 / 大型部件组装机（0.3.230 ~ 0.3.231）

- **结构**：GTO「部件组装机」本体 7×5×5，以及本体 + ASSEMBLY 扩展合体 29×6×13（等价转写；
  GTO 的可选模块 / 原地升级未移植，直接拆成两台多方块）；
- **方块语义**：按 GTO 原结构符号表；GTO 专属方块由 `gtlcore` 提供，本模组**未拷贝**这批方块的贴图；
- **配方**：GTO `ComponentRecipes` 的部件装配配方数值重写（`gtceu:component_assembly_line`）。

### 3.8 集成木质生物质工厂（GTO「木化工厂」移植，2026-11-24 批次）

> 来源：GTOCore `pattern/wood_distillation.mbs`（结构）、`pattern/wood_dist_crossrecipe.mbs`（未搬，见下）、
> `assets/gtocore/textures/`（5 张机壳贴图）、`com/gtocore/common/data/machines/MultiBlockB.java:74`（谓词表）、
> `com/gtocore/common/data/GTORecipeTypes.java:243`（配方表参数）、
> `com/gtocore/data/recipe/MiscRecipe.java:91-93`（三条配方数值）、
> `com/gtocore/data/recipe/classified/Assembler.java:125` 与 `AssemblerA.java:75-77`（5 件机壳的装配配方）、
> `GTOMachineStories`（机器描述 6 行）。
> **本模组里机器叫「集成木质生物质工厂」（`gtlmodular:wood_biomass_factory`）**，GTO 原名「木化工厂」
> （`gtocore:wood_distillation`）—— 按队长口径**只改名字**，结构 / 运行模式 / 配方照搬。

**方块贴图（5 件，逐字节拷贝）**

| 本模组文件（`assets/gtlmodular/textures/`） | GTOCore 原件（`assets/gtocore/textures/`） | sha256 |
|---|---|---|
| `block/casings/pressure_containment_casing.png` | `block/casings/pressure_containment_casing.png` | "825a3659098e7fa4…d79a" |
| `block/casings/hastelloy_n_75_pipe.png` | `block/hastelloy_n_75_pipe.png` | "c0f7e602db5eb414…ab0e" |
| `block/casings/chemical_corrosion_resistant_pipe_casing.png` | `block/casings/chemical_corrosion_resistant_pipe_casing.png` | "c43a68d126ec4a79…b02f" |
| `block/casings/high_pressure_pipe_casing.png` | `block/casings/high_pressure_pipe_casing.png` | "4ab384af0b470c9e…2d5e" |
| `block/casings/oil_gas_transportation_pipe_casing.png` | `block/casings/oil_gas_transportation_pipe_casing.png` | "cfd95ad915e8c529…a235" |

> 5 张均为 16×16 本体贴图，**不拷源 `.mcmeta`**（源 mcmeta 是 LDLib 的 CTM 声明，指向 gtocore 的
> `*_ctm` 图；gtocore 不在整合包里，拷了会变成缺图）。与恒星炎炀锻炉 / 高维等离子聚变核心同一处理。

**结构数据（等价转写）**

- `gtlmodular:wood_biomass_factory` 的 **28 (X) × 56 (Y) × 60 (Z) = 94,080 格**（实心 10,400 格）
  结构，来自 GTOCore 的 `pattern/wood_distillation.mbs`
  （gzip 3,093 B → 解开 100,870 B，解析 **0 字节剩余**；60 过道 × 每过道 56 行 × 每行 28 字符），
  转写为本模组 Java 字符串数组 `WoodFactoryStructures.SLICES`（逐格 0 差异）；
- 各符号 → 方块的语义映射取自 GTOCore `MultiBlockB.java:74` 的谓词表（26 个符号位）。

**文本（CC BY-NC-SA 4.0）**

| 本模组 lang 键 | GTOCore 原键 | 改动 |
|---|---|---|
| `gtlmodular.wood_biomass_factory.tooltip.0` | `gtocore.lang.wood_distillation.0` | 逐字照搬 |
| `gtlmodular.wood_biomass_factory.tooltip.1` | `gtocore.lang.wood_distillation.1` | 逐字照搬 |
| `gtlmodular.wood_biomass_factory.tooltip.2` | `gtocore.lang.wood_distillation.2` | 逐字照搬 |
| `gtlmodular.wood_biomass_factory.tooltip.3` | `gtocore.lang.wood_distillation.3` | 逐字照搬 |
| `gtlmodular.wood_biomass_factory.tooltip.4` | `gtocore.lang.wood_distillation.4` | 逐字照搬 |
| `gtlmodular.wood_biomass_factory.tooltip.5` | `gtocore.lang.wood_distillation.5` | 逐字照搬（原文是高亮行「可持续发展化工」） |
| `gtlmodular.wood_biomass_factory.tooltip.6` … `11` | —（GTO 由 `.laserTooltips()` / `.coilParallelTooltips()` / `.moduleTooltips()` 代码生成） | **本模组自写**：运行模式 / 仓室 / 配方类型说明，语义与 GTO 生成行一致，未拷贝其运行时字符串 |
| `block.gtlmodular.pressure_containment_casing` 等 5 个方块名 | `block.gtocore.<同名>` | 中文名逐字照搬（压力容器机械方块 等） |
| `gtlmodular.wood_distillation`（配方表显示名） | `gtceu.wood_distillation` | 逐字照搬「集成木质生物质热解」 |

**配方数值（按 GTO 运行时配方数值重写，不含 GTOCore 代码）**

| 配方 | 数值（与 GTO 逐项一致） |
|---|---|
| `wood_distillation_with_nitrogen` | 16× `#minecraft:logs` + 1000mB 氮 → 2× 灰烬 + 苯 1050 / 二甲苯 1000 / 杂酚油 900 / CO₂ 490 / 甲醇 480 / CO 340 / 苯酚 225 / 甲苯 225 / 乙酸 160 / 甲烷 130 / 丙酮 80 / 乙烯 20 / 氢气 20 / 乙酸甲酯 16 / 乙醇 16；电路 1；**120 EU/t，300 tick** |
| `wood_distillation_with_steam` | 16× `#minecraft:logs` + 1000mB 蒸汽 → 2× 灰烬 + 氨 600 / CO₂ 500 / 乙苯 500 / 萘 410 / 硫化氢 307 / 杂酚油 205 / 苯酚 102；电路 2；120 EU/t，300 tick |
| `wood_distillation_with_water` | 16× `#minecraft:logs` + 6000mB 水 → 6× 肥料 + 甲烷 3300 / CO₂ 2200 / 甲醇 825 / 乙醇 825 / 杂酚油 560 / 氨 550 / 乙酸 137；电路 3；120 EU/t，300 tick |
| 机器本体（装配机 ZPM） | 16× 热解炉（`gtceu:pyrolyse_oven`） + 16× 大型蒸馏塔 + 64× `#gtceu:circuits/zpm` + 32× LuV 发射器 + 64× 不锈钢巨型流体管道 + 32× IV 电动泵 + 64× 水密钢板 + 64× 双层不锈钢板 + 5184mB 焊锡 → 1× 本机；800 tick |
| 5 件机壳（装配机） | 照 GTO `Assembler.java:125` / `AssemblerA.java:75-77` 逐项重写；**两处材料替代**见下 |

**材料替代（GTO 专属、本包没有）**

1. `HighPressureNitrogen`（GTO 材料）→ **氮**（`gtceu:nitrogen`），用于耐高压管道机壳；
2. 机器本体的 `large_pyrolyse_oven`（GCyM 大型热解炉）本包**没有**该 id（gtceu 1.4.4 只到
   `gtceu:pyrolyse_oven`）→ 换成 **`gtceu:pyrolyse_oven`**（GTCEu 自带热解炉，同语义：
   原木 → 木炭 / 杂酚油 / 灰烬）；
3. 压力容器机壳原配方用 **PTFE 框架**（本包未生成该材料形态）→ **水密钢框架 + 2× PTFE 板**，
   主体化工语义（PTFE 内衬）保留；
4. 其余材料（Hastelloy-N 75 / Mar-M 200 钢 / 水密钢 / 钛 / 钨钢 / HSS-G / 铌钛 /
   铑镀钯 / 大马士革钢 / 焊锡）本包均有，id 逐项对过整合包脚本与 gtceu 语言文件。

**未纳入（明确没搬）**

- GTO 的附属模块子结构 `pattern/wood_dist_crossrecipe.mbs`（61×7×29）与 GTO 的模块框架
  （`Piece` / `Slot` / `StructureModuleKeys` / `MultiBlockFileReader`）—— 本模组没有该框架，
  模块口改成主结构 `Z=58/59` 两片控制舱的 71 格可装仓舱面（方块一格未动，只是那 71 格多允许插仓）；
- GTOLib 的 `CoilCrossRecipeMultiblockMachine`（上游以 native 方式发布，无源码）——
  线圈并行按 GTO 自己的 tooltip 与 `MultiBlockA.java:180` 的 `1L << (int)(temp / 900)` 重写；
- GTO 的 `StainlessSteelGC4` 材料（只在模块子结构里用，随模块一起未搬）。

### 3.9 补录：力场压模零件工厂批次的 6 张贴图（2026-11-24 补）

> 起因：`scripts/gto_license_audit.py` 第五验（逐字节同源贴图对账）在 0.3.480 上仍报 6 条缺项，
> 全部属于 2026-11-14「力场压模零件工厂」那批（§3.8 之前就存在），本次随木化工厂一起补齐点名。

| 本模组文件（`assets/gtlmodular/textures/`） | GTOCore 原件（`assets/gtocore/textures/`） | sha256 |
|---|---|---|
| `block/casings/high_pressure_resistant_casing.png` | `block/high_pressure_resistant_casing.png` | "2a08599f61186a2e…ca61" |
| `block/casings/lithium_oxide_ceramic_heat_resistant_shock_resistant_mechanical_cube.png` | `block/lithium_oxide_ceramic_heat_resistant_shock_resistant_mechanical_cube.png` | "24389f830caf3fab…fa8b" |
| `block/multiblock/general1/overlay_front.png` | `block/multiblock/general1/overlay_front.png` | "c5793fe7540dfb45…9088" |
| `block/multiblock/general1/overlay_front_active.png` | `block/multiblock/general1/overlay_front.png`（派生：同字节镜像，控制器未启动态） | "c5793fe7540dfb45…9088" |
| `block/multiblock/general1/overlay_front_active_emissive.png` | `block/multiblock/general1/overlay_front_active_emissive.png` | "128ef6c323b6edb5…e88e" |
| `block/multiblock/general1/overlay_front_emissive.png` | `block/multiblock/general1/overlay_front_emissive.png` | "b7bc03a3cc6df5a3…0ecd" |

### 3.10 石化线两台机器：集成石油化工处理工厂 / 燃料精炼复合体（2026-11-26 批次，0.3.485）

> 来源：GTOCore `pattern/petrochemical_plant.mbs`、`pattern/petrochem_crossrecipe.mbs`（未搬）、
> `pattern/fuel_refining_complex.mbs`、`pattern/fuel_refining_crossrecipe.mbs`（未搬）、
> `assets/gtocore/textures/`（8 张机壳贴图）、
> `com/gtocore/common/data/machines/MultiBlockA.java:162`（石化工厂谓词表）、
> `MultiBlockB.java:79`（燃料精炼复合体谓词表）、
> `com/gtocore/common/data/GTORecipeTypes.java`（两张配方表参数）、
> `com/gtocore/data/recipe/classified/PetrochemicalPlant.java`（8 条处理配方）、
> `com/gtocore/data/recipe/classified/Assembler.java`（石化工厂本体配方）、
> `AssemblyLine.java`（燃料精炼复合体本体配方）、`GTOMachineStories` + `assets/gtocore/lang/zh_cn.json`（机器描述）。
> **本模组里**：
> - `gtlmodular:petrochemical_plant` = **集成石油化工处理工厂**（GTO 原名「石化工厂」
>   `gtocore:petrochemical_plant`）—— 按队长口径**改名**，结构 / 运行模式照搬；
> - `gtlmodular:fuel_refining_complex` = **燃料精炼复合体**（GTO 同名机器）—— 按队长口径
>   **机器本体不做改动**，配方沿用整合包已有的 `gtceu:fuel_refining`。

**方块贴图（8 件，逐字节拷贝）**

| 本模组文件（`assets/gtlmodular/textures/`） | GTOCore 原件（`assets/gtocore/textures/`） | sha256 |
|---|---|---|
| `block/casings/supercritical_turbine_casing.png` | `block/casings/supercritical_turbine_casing.png` | "e513a0094eb4fff8…6306" |
| `block/casings/pikyonium_machine_casing.png` | `block/casings/pikyonium_machine_casing.png` | "d3e21ec17e12fc3e…e716" |
| `block/casings/chemical_grade_glass.png` | `block/casings/chemical_grade_glass.png` | "b780662d3d293757…f8e5" |
| `block/casings/iridium_pipe_casing.png` | `block/machine_casing_pipe_iridium.png` | "3e2f0be8e7ce3b3c…8ee4" |
| `block/casings/pps_corrosion_resistant_mechanical_housing.png` | `block/pps_corrosion_resistant_mechanical_housing.png` | "213cb462f62cc40b…3d2c" |
| `block/casings/high_strength_support_mechanical_casing.png` | `block/casings/high_strength_support_mechanical_casing.png` | "d798524016f6ff09…64d5" |
| `block/casings/calcium_oxide_ceramic_anti_metal_corrosion_mechanical_block.png` | `block/calcium_oxide_ceramic_anti_metal_corrosion_mechanical_block.png` | "34849b6eadbcf545…567a" |
| `block/casings/industrial_frameless_glass.png` | `block/industrial_frameless_glass.png` | "5ea51e823ef69bb8…7f9d" |

> 8 张均为 16×16 本体贴图，**不拷源 `.mcmeta`**（同 §3.8：源 mcmeta 是 LDLib 的 CTM 声明，
> 指向 gtocore 的 `*_ctm` 图）。两件玻璃（化学级 / 工业无框）按 `Blocks.GLASS` 口径注册，
> 模型 `render_type: minecraft:translucent`。

**结构数据（等价转写，逐格 0 差异）**

- `gtlmodular:petrochemical_plant`：**28 (X) × 60 (Y) × 65 (Z) = 109,200 格**（实心 11,729 格），
  来自 `pattern/petrochemical_plant.mbs`（gzip 4,068 B → 解开 117,075 B，解析 **0 字节剩余**；
  65 过道 × 每过道 60 行 × 每行 28 字符），转写为 `PetrochemicalPlantStructures.SLICES`；
  控制器 `U` 在 `[Z=64][Y=3][X=22]`；
- `gtlmodular:fuel_refining_complex`：**31 (X) × 27 (Y) × 63 (Z) = 52,731 格**（实心 10,597 格），
  来自 `pattern/fuel_refining_complex.mbs`（gzip 3,133 B → 解开 56,206 B，解析 **0 字节剩余**；
  63 过道 × 每过道 27 行 × 每行 31 字符），转写为 `FuelRefiningComplexStructures.SLICES`；
  控制器 `` ` `` 在 `[Z=61][Y=2][X=7]`；
- 各符号 → 方块的语义映射取自 `MultiBlockA.java:162` / `MultiBlockB.java:79` 的谓词表
  （石化 29 个符号位、燃料 31 个符号位），逐一在类注释里点名。

**文本（CC BY-NC-SA 4.0）**

| 本模组 lang 键 | GTOCore 原键 | 改动 |
|---|---|---|
| `gtlmodular.petrochemical_plant.tooltip.0` | `gtocore.lang.petrochemical_plant.0` | 逐字照搬（加色码） |
| `gtlmodular.petrochemical_plant.tooltip.1` | `gtocore.lang.petrochemical_plant.1` | 逐字照搬（加色码） |
| `gtlmodular.petrochemical_plant.tooltip.2` | `gtocore.lang.petrochemical_plant.2` | 逐字照搬（加色码） |
| `gtlmodular.petrochemical_plant.tooltip.3` | `gtocore.lang.petrochemical_plant.3` | 逐字照搬（加色码） |
| `gtlmodular.petrochemical_plant.tooltip.4` | `gtocore.lang.petrochemical_plant.4` | 逐字照搬（加色码） |
| `gtlmodular.petrochemical_plant.tooltip.5` | `gtocore.lang.petrochemical_plant.5` | 逐字照搬（原文是高亮行） |
| `gtlmodular.petrochemical_plant.tooltip.6` … `10` | —（GTO 由 `.laserTooltips()` / `.coilParallelTooltips()` / `.moduleTooltips()` 代码生成） | **本模组自写**：运行模式 / 仓室 / 配方类型说明 |
| `gtlmodular.fuel_refining_complex.tooltip.0` | `gtceu.machine.electric_blast_furnace.tooltip.2` | 逐字照搬（GTO 原机 `tooltipsKey` 那一行，GTCEu 自带的键） |
| `gtlmodular.fuel_refining_complex.tooltip.1` … `7` | — | **本模组自写** |
| `block.gtlmodular.<8 件机壳>` | `block.gtocore.<同名>` | 中文名逐字照搬 |

**配方数值（按 GTO 运行时配方数值重写，不含 GTOCore 代码）**

| 配方 | 数值 |
|---|---|
| 石化工厂本体（装配机，**三个主机 ×2**） | 16× 水密钢框架 + **16× 大型蒸馏机**（GTO 8）+ **8× 大型裂化机**（GTO 4）+ **2× 化工厂**（GTO 1） + 4× `#gtceu:circuits/uhv` + 4× ZPM 发射器 + 16× ZPM 流体校准器 + 16× 不锈钢巨型流体管道 + 32× 双层不锈钢板 + 1296mB 焊锡 → 1× 本机；**122,880 EU/t，1000 tick** |
| 燃料精炼复合体本体（装配线） | 4× LuV 搅拌机 + 4× LuV 离心机 + 4× LuV 蒸馏机 + 4× LuV 化学反应釜 + 8× 碳化钨巨型流体管道 + 4× LuV 发射器 + 4× `#gtceu:circuits/zpm` + 8× ZPM 电动泵 + 16× 双层 HSSE 板 + 2304mB 焊锡 + 2304mB PTFE + 1152mB 水密钢 → 1× 本机；**122,880 EU/t，1000 tick**（GTO 的「研究」门槛本包没有研究站，未搬） |
| 氢化 1~4（`gtceu:petrochemical_plant`） | 1000mB 油品（oil / oil_medium / oil_heavy / oil_light）+ 1000mB 氢 → 甲烷 / 丁烷 / 丙烷 / 乙烷 / 氦 / 环己烷 / 苯 / 辛烷；**1920 EU/t，600 tick**，四项数值逐项照 GTO `PetrochemicalPlant.java` 的 `hydro1..4` |

**材料替代（GTO 专属、本包没有）**

1. `GTOMaterials.Cetane`（十六烷，GTOCore 独有材料）→ **并入 `gtceu:octane`**：
   GTO 的 `hydro1..4` 各产 Cetane 30 / 60 / 45 / 15 mB，本包全栈（gtceu 1.4.4 + gtlcore +
   gtladditions + Enhancedcore）**只有 `cetane_boosted_diesel`，没有 Cetane**，
   故与原有的辛烷 60 / 120 / 90 / 30 合并为 90 / 180 / 135 / 45；
2. `GTOMaterials.StainlessSteelGC4` 框架（石化工厂 `N` 位，78 格）→ **`gtceu:stainless_steel_frame`**
   （同族同形状，本包无 GC4 材料）；
3. 其余方块（应力外壳 / 水密外壳 / 洁净不锈钢 / 坚固钨钢 / 钛·钢·钨钢·PTFE 管道 / 聚变玻璃 /
   计算机外壳 / 电解槽 / 各材料框架 / GT 灯）本包全部现成。

**未纳入（明确没搬）**

- GTO 的两个附属模块子结构（`pattern/petrochem_crossrecipe.mbs` 29×51×65、
  `pattern/fuel_refining_crossrecipe.mbs` 29×20×63）与 GTO 的模块框架
  （`Piece` / `Slot` / `StructureModuleKeys` / `MultiBlockFileReader`）—— 本模组没有该框架，
  模块口改成主结构舱面：石化工厂 `H` 26 格、燃料精炼复合体 `^` 26 格
  （方块一格未动，只是那些格多允许插 线程仓 / 超频仓 / 加速仓 / 并行仓）；
- GTOLib 的 `CoilCrossRecipeMultiblockMachine`（上游以 native 方式发布，无源码）——
  并行按 GTO 自己的 tooltip 重写为 `2^(⌊温度/900⌋)`，温度闸沿用 GTCEu 自带的
  `GTRecipeModifiers.ebfOverclock`（`线圈温度 + 100×max(0, 档位-MV)`）；
- GTO 的 `FuelRefining.java`（整套燃料精炼配方，引用大量 GTOCore 独有材料）——
  按队长口径改用整合包已有的 `gtceu:fuel_refining` 配方（`nuclear_rocket_fuels.js`）；
- GTO 装配线配方的「研究」门槛（`researchStation` / `MachinesNode`）—— 本包没有研究站。

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

## 7. 山海渲染特效（历史：0.3.299 移植 → 0.3.328 移除）

> 2026-11-02 的 0.3.299 曾把 **dishanhai/gtl_shanhai-dishanhai**（
> <https://github.com/dishanhai/gtl_shanhai-dishanhai>，commit
> `d3012f051d1b5da98b46b0d5292bec015fa4a5c2`）的 5 个几何/渲染类移植进
> `client.renderer.preview`（黑日 / 密文 / 电弧 / 冲击环）。
>
> **0.3.328 按玩家要求「之前做的那些全部都去掉」**：这 5 个类以及 0.3.306 的
> 「千面」几何类已全部从源码树删除，**不再随包分发**。上游文件的逐字节留档仍在
> `_scratch/shanhai_port_upstream/`（仅作历史核对，不参与构建）。
>
> 唯一保留的是 `ShanhaiInstabilityRenderTypes`（两条 RenderType 的状态组合，代码自写，
> 状态语义参考上游 `HostFxDraw#beamLayer() / blackCoreLayer()`）；它不含任何上游几何/着色器代码，
> 现由奈亚中央黑日（`BlackSunRenderer`）与恒星炎炀锻炉的几何兜底共用。
