# NAC 配方生成器（快照）

这两个脚本原本放在工作区外层 `E:\大鲸鱼工作区\_census\` 与 `E:\大鲸鱼工作区\`，
**不在本仓库内**，因此不受版本控制。这里放一份快照，便于回溯与交接。

> ⚠️ 快照 ≠ 可直接运行。脚本依赖外层的 `_census/` 数据表与 `_gtnh/` 取证转储，
> 直接在本仓库里跑会因为找不到输入而报错。要真正重跑，请回到工作区外层执行。

## gen_nac_cc_items.js

生成 NAC 的 **CircuitComponent（CC）物品层**：

| 产出 | 路径 |
|---|---|
| Java 注册表 | `src/main/java/com/gtlmodular/common/data/nanochip/NanochipCircuitComponents.java` |
| 物品模型 | `src/main/resources/assets/gtlmodular/models/item/cc_*.json` |
| CC 贴图 | `src/main/resources/assets/gtlmodular/textures/item/cc/` |

输入（只读）：
- `_census/nac_cc_items.json` —— NH 段 166 条 CC 权威表
- `_census/nac_wave2_spec.json` —— 本包自造段（现 210 条，metaId ≥ 2200）
- `_gtnh/jarfull/` —— NH 原始贴图与语言（**本机可能缺失，脚本已做回退**）

### 2026-11-14 的三处修复

1. **贴图目录被清空不重建**
   旧逻辑先删 `textures/item/cc/` 再逐条写回；一旦中途校验失败退出，目录就只剩残片
   （实测删掉 108 个贴图）。现在改为「NH 源贴图不在时，回退到仓库里已拷好的成品图」。

2. **整合包路径写成 Windows 盘符**
   `GTL_PACK = 'D:/...'` 在 WSL 下读不到 kubejs 语言与贴图源，
   导致大量条目被判「查不到语言键 / 缺贴图」。已改为 `/mnt/d/...`。

3. **仓库路径大小写笔误**
   `gtlmodular/src/...` → `gtl-modular/src/...`，否则回退检查永远为 false。

### 新增 5 个 AD SoC

`nac_wave2_spec.json` 里追加了 metaId 2400–2409：

| metaId | enumName | itemId |
|---|---|---|
| 2400 | OutstandingSoc | `cc_outstanding_soc` |
| 2401 | ProcessedOutstandingSoc | `cc_processed_outstanding_soc` |
| 2402 | ExtraordinarySoc | `cc_extraordinary_soc` |
| 2403 | ProcessedExtraordinarySoc | `cc_processed_extraordinary_soc` |
| 2404 | ChaosSoc | `cc_chaos_soc` |
| 2405 | ProcessedChaosSoc | `cc_processed_chaos_soc` |
| 2406 | SpacetimeSoc | `cc_spacetime_soc` |
| 2407 | ProcessedSpacetimeSoc | `cc_processed_spacetime_soc` |
| 2408 | PrimarySoc | `cc_primary_soc` |
| 2409 | ProcessedPrimarySoc | `cc_processed_primary_soc` |

贴图取自 `gtladditions-3.3.1Custom.jar` 的 `assets/gtladditions/textures/item/<soc>.png`。

## gen_nac_p1_recipes.js

生成换算（conversion）与组装链（assembly_complex / assembly_matrix）配方。

### 2026-11-14 的修复

1. **换算只生成 94 条**
   `p1_input_map.conversion` 只登记了 94 条，而 CC 表有 200 个未处理件
   ⇒ 目录里另 106 条（三系列 RAM/SMD/PCB、PEEK 箔…）每次重跑都被删掉却不重建。
   现改为覆盖 CC 表全部未处理件，缺的按 `realRef` 推导。

2. **`mat:<材料>:<形态>` 推导顺序反了**
   生成出 `gtceu:plate_adamantine`（不存在），正确是 `gtceu:adamantine_plate`。
   已加 `SHAPE_ALIAS` 处理 `wireGtDouble → double_wire` 等特例。

3. **整目录 `removeTree`**
   改为白名单式清理，只删退役名单内的陈旧产物，其余（三系列自造矩阵等）原样保留。

4. **用 NH 旧口径覆盖手工调好的配方**
   矩阵循环原本按 `p1_input_map` 重建，会把磁盘上手工调过的配方改回 NH 值
   （实测「生物处理器 出 8 → 出 1」）。现改为「磁盘已有则沿用其内容，只补流体」。

5. **新增双焊料与高产量配方段**
   - 双焊料：普通版 `mutated_living_solder` / 超级版 `super_mutated_living_solder`（产量相同、耗时减半）
   - 高产量：处理器 16 / 集群 8 / 超算 8 / 主机 8；高产量版用 AD 专属 SoC 并追加区分输入（防串配方）
