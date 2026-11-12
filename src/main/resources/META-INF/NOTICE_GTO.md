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
