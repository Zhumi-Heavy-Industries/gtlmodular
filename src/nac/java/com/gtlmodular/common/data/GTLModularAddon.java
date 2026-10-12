package com.gtlmodular.common.data;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconSet;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.DustProperty;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

/**
 * 本模组的 <b>GTCEu 扩展（addon）</b>：把 NAC 模块结构需要、而本整合包**缺失**的那几味材料的
 * <b>框架方块</b>补出来（玩家 2026-09-16 要求："我们不能把那些对应的框架自己做吗"）。
 *
 * <h3>为什么用这条路（而不是自己画方块）</h3>
 * <ul>
 *   <li>GTCEu 提供官方钩子：材料注册 ⇒ GTCEu 会**自动生成** {@code <材料>_frame} 方块、贴图、语言键与标签；</li>
 *   <li><b>本包已有活先例</b>：{@code gtlcore} 就是这么干的（运行时注册表里 {@code quantanium_frame} /
 *       {@code draconium_frame} / {@code radox_block} / {@code spacetime_block} 全是"注册材料 ⇒ 自动出方块"）；</li>
 *   <li>省掉"自己出方块 + 贴图 + lang + 标签 + 判定接入"一整套，且与 GTCEu 材料体系同源
 *       （别的 mod 的配方/工具也能认得这些材料）。</li>
 * </ul>
 *
 * <h3>★ 2026-09-16 实机修正：材料注册**必须走 {@link MaterialEvent} 事件**</h3>
 * 第一版把材料写在 {@link IGTAddon#registerMaterials()} 里，实机（19:02 那次启动）**一条都没注册上**。
 * 日志证据（`logs/latest.log`）：`Registering addon Materials` 之后**紧跟的是 gtlcore 的材料警告**，
 * 而本模组那条"已注册 4 味补充材料"完全没有出现，随后塔身判定一路回落
 * （`塔身方块 gtceu:enriched_holmium_frame 不存在，回落到 gtceu:naquadah_alloy_frame`）。
 *
 * <p>根因（`javap -c com.gregtechceu.gtceu.common.CommonProxy#initMaterials` 逐条读出）：
 * <pre>
 *   LOGGER.info("Registering GTCEu Materials");  GTMaterials.init();
 *   LOGGER.info("Registering addon Materials");
 *   ModLoader.get().postEvent(new MaterialEvent());      ← ★ 材料注册早已改成**事件**
 *   ...
 * </pre>
 * <b>这条路上没有任何 {@code AddonFinder} / {@code IGTAddon.registerMaterials()} 调用</b> —— 也就是说
 * {@code IGTAddon.registerMaterials()} 在 1.4.4 里是**被废弃且永不被调用**的方法
 * （编译期那条 "registerMaterials() in IGTAddon has been deprecated and marked for removal" 警告就是这个意思）。
 * 本包的 {@code gtlcore} 也是同款做法：它的 {@code GTLGTAddon} **没有**覆写 {@code registerMaterials()}
 * （javap 方法表里没有），而是在 {@code org.gtlcore.gtlcore.common.CommonProxy} 里收 {@code MaterialEvent}（javap 实测）。
 *
 * <p>⇒ 现在：材料在 {@link #onMaterialEvent(MaterialEvent)} 里注册（由产物入口挂到 mod 总线）；
 * 旧的 {@code registerMaterials()} 覆写**保留但只为兼容旧版 GTCEu**，两边共用同一个**幂等**实现
 * （{@code registerNacMaterials()}），绝不会重复注册。
 *
 * <h3>关键实现细节（都是查过字节码的，不是猜的）</h3>
 * <ol>
 *   <li><b>材料 id 必须挂在 {@code gtceu} 命名空间下</b>（用 {@link GTCEu#id(String)}）：
 *       结构图案里写的是 {@code gtceu:<snake>_frame}（见 {@code NanochipComplexPattern.frameIdOf}），
 *       注册到我们自己的命名空间会得到 {@code gtlmodular:<...>_frame} ⇒ 对不上 ✗。gtlcore 也是这么做的 ✓。</li>
 *   <li><b>必须有 DUST 属性，框架才会生成</b>：{@code TagPrefix} 里框架的生成条件是
 *       {@code hasProperty(PropertyKey.DUST) && hasFlag(GENERATE_FRAME)}
 *       —— 由 {@code javap -c com.gregtechceu.gtceu.api.data.tag.TagPrefix} 逐条读出 ✓
 *       ⇒ 所以下面每一味都调用 {@code .dust()}。</li>
 *   <li><b>实心块</b>：{@code MaterialFlags} 里**没有** {@code GENERATE_BLOCK}（javap 全量核对）⇒ 要强制生成
 *       要用 {@code FORCE_GENERATE_BLOCK}（UEV 粗胚那味需要它，因为 SHEET 候选链会试 {@code <材料>_block}）。</li>
 *   <li><b>注册期纪律</b>：这里只做"注册"，<b>不</b>解析图案、<b>不</b>算格子、<b>不</b>遍历注册表
 *       ⇒ 与 {@code check_registration_path.js} 的纪律不冲突 ✓。</li>
 *   <li><b>语言键</b>：框架名 = GTCEu 的 {@code tagprefix.frame}（{@code "%s Frame"} / {@code "%s框架"}）
 *       × 材料的本地化名，材料名键为 {@code material.<命名空间>.<材料名>}（GTCEu 自带 Holmium 即
 *       {@code "material.gtceu.holmium"}）⇒ 4 味材料的 en/zh 键必须补上，否则游戏里显示成原始键名 ✗
 *       （已补；{@code check_frame_landing.js} 有断言守着）。</li>
 * </ol>
 *
 * <h3>色值来源（逐条写明，便于玩家目视校准 —— 改一个 hex 即生效）</h3>
 * <table border="1">
 *   <tr><th>材料</th><th>色值</th><th>来源</th></tr>
 *   <tr><td>{@code enriched_holmium}（富集钬，GT++ 材料）</td><td>{@code 0xE2E88F}</td>
 *       <td>以 GTCEu 自己的 {@code Holmium}（同族元素材料）实测色 {@code 0xF6FC9C} 为基线略降亮度
 *           （{@code javap -c ElementMaterials}：{@code color(16186524)} = 0xF6FC9C / {@code secondaryColor} = 0xA3A3A3）。
 *           ⚠️ NH 侧真值需玩家目视校准。</td></tr>
 *   <tr><td>{@code octiron}（八角铁，GT++ 材料）</td><td>{@code 0x3C3A44}</td>
 *       <td>**近似值**：NH 侧没有 Octiron 的贴图可抽（GT++ 用"通用框架 + 材料色"，
 *           且本机参考集里没有任何 octiron 贴图）；GT++ 字节码里只有两个疑似数值（9120 / 14200），
 *           未能确证构造语义 ⇒ 取"暗金属灰紫"。⚠️ 待玩家目视校准。</td></tr>
 *   <tr><td>{@code superconductor_uhv_base}（UHV 超导粗胚）</td><td>{@code 0x5292C5}</td>
 *       <td>取自 NH 侧贴图 {@code assets/gregtech/textures/items/gt.circuitcomponent/processed/processedsuperconductoruhv.png}
 *           的**高亮区均色**（脚本 `_census/extract_material_color.js` 现算，只读）。</td></tr>
 *   <tr><td>{@code superconductor_uev_base}（UEV 超导粗胚）</td><td>{@code 0x7E869F}</td>
 *       <td>同上，取自 {@code processedsuperconductoruev.png} 的高亮区均色。</td></tr>
 * </table>
 * 说明：GTCEu 的框架外观 = **通用框架贴图 × 材料颜色**，NH(GT5U) 同理也是"通用框架 + 颜色"
 * ⇒ **色相可以对齐，底图笔触不同**（1.20 与 1.7.10 的美术差异），如实记录在施工记录 §64。
 */
public class GTLModularAddon implements IGTAddon {

    // 保留兼容字段，让材料注册与外部读取仍指向同一对象。
    public static GTRegistrate registrate;

    /** 幂等闸门：{@link MaterialEvent} 与（旧版 GTCEu 的）{@code registerMaterials()} 只允许生效一次。 */
    private static boolean materialsRegistered;

    @Override
    public GTRegistrate getRegistrate() {
        registrate = NanochipModContext.addonRegistrate();
        return registrate;
    }

    @Override
    public void initializeAddon() {
        // 没有需要在 addon 初始化阶段做的事（材料在 MaterialEvent 里注册）
    }

    @Override
    public String addonModId() {
        return NanochipModContext.modId();
    }

    /**
     * ★ <b>材料注册的正式入口</b>：GTCEu 在"方块注册之前"于 mod 总线 post {@link MaterialEvent}
     * 材料需要先于框架和其它生成形态注册。
     */
    public static void onMaterialEvent(MaterialEvent event) {
        registerNacMaterials();
    }

    /**
     * 兼容旧版 GTCEu 的钩子（1.4.4 里已废弃且不会被调用；留着的唯一目的是"万一整合包换成旧版 GTCEu"）。
     * 与 {@link #onMaterialEvent} 共用幂等实现 ⇒ 两边同时生效也只会注册一次 ✓。
     */
    @Override
    public void registerMaterials() {
        NanochipModContext.logger().warn("[gtlmodular] IGTAddon.registerMaterials() 被调用了 —— 当前 GTCEu 版本正常应该走 MaterialEvent，"
                + "这条只是兼容路径（幂等，不会重复注册）。如果你看到它，请把日志给我。");
        registerNacMaterials();
    }

    /**
     * 注册 NAC 结构需要、而本包缺失的 4 味材料（GTCEu 自动生成 {@code <材料>_frame} 方块）。
     *
     * <p>{@code synchronized} + 布尔闸门：事件路径与兼容路径同时被调用也只会注册一次
     * （重复注册会让 GTCEu 抱怨"材料已存在"）。
     *
     * <h3>★ 2026-09-17：两味材料按 NH 补「形态」，让 EBF / VF 自动链长出来（玩家已裁决）</h3>
     * 玩家裁决原文：选了「补齐材料形态（会凭空多出一堆物品）」，接受 JEI 里多出锭/热锭等条目。
     * <table border="1">
     *   <tr><th>材料</th><th>NH 定义（{@code _gtnh} 现读）</th><th>本轮补的</th><th>补完自动得到</th></tr>
     *   <tr><td>{@code enriched_holmium}</td>
     *       <td>{@code MaterialsInit#loadEnrichedHolmium}：{@code addMetalItems()} +
     *           {@code setBlastFurnaceTemp(3000)} + {@code setBlastFurnaceRequired(true)}，
     *           <b>没有</b>关自动 EBF/VF</td>
     *       <td>{@code .ingot()} + {@code .blastTemp(3000)}</td>
     *       <td>EBF {@code dust → hot_ingot @3000K} + VF {@code hot_ingot → ingot}</td></tr>
     *   <tr><td>{@code superconductor_uhv_base}</td>
     *       <td>{@code loadSuperconductorUHVBase}：{@code addDustItems()+addMetalItems()} +
     *           {@code setMeltingPoint(10800)}+{@code setBlastFurnaceTemp(10800)}+
     *           {@code setBlastFurnaceRequired(true)} +
     *           {@code setAutoGenerateBlastFurnaceRecipes(false)}+
     *           {@code setAutoGeneratedVacuumFreezerRecipe(false)}</td>
     *       <td>{@code .ingot()} + {@code .blastTemp(10800)}</td>
     *       <td>EBF {@code dust → hot_ingot @10800K} + VF {@code hot_ingot + 500L 液氦 → ingot}
     *           （+ 我们另写的 NH 原版 VF，见配方 JSON）</td></tr>
     *   <tr><td>{@code superconductor_uev_base}</td>
     *       <td>{@code loadSuperconductorUEVBase}：{@code addDustItems()+addMetalItems()} +
     *           {@code setMeltingPoint(11800)}+{@code setBlastFurnaceTemp(11800)}+
     *           {@code setBlastFurnaceRequired(true)}</td>
     *       <td>★ 2026-09-17 第五轮：{@code .ingot()} + {@code .blastTemp(11800)}</td>
     *       <td>EBF {@code dust → hot_ingot @11800K} + VF {@code hot_ingot + 500L 液氦 → ingot}
     *           （**不手写**，理由见下"没有关自动的 API"；NH 手写那条是 15720t/UV/带 11 号电路）</td></tr>
     * </table>
     *
     * <h3>⚠️ NH 有、GTCEu 1.4.4 <b>没有</b>的三个 API（javap 全量实证，别再找）</h3>
     * <ol>
     *   <li>{@code setAutoGenerateBlastFurnaceRecipes(boolean)} / {@code setAutoGeneratedVacuumFreezerRecipe(boolean)}
     *       —— {@code Material} / {@code Material.Builder} / {@code MaterialProperties} / {@code BlastProperty}
     *       的 javap 方法表里<b>都没有</b>；对整个 gtceu jar 的 1663 个 class 做常量池 ASCII 全量扫描，
     *       字符串 {@code "AutoGenerate"} / {@code "autoGenerate"} 命中数 = <b>0</b>
     *       ⇒ <b>本包 API 不支持关闭自动 EBF/VF</b>。后果：`superconductor_uhv_base` 一旦
     *       {@code blastTemp(10800)}，GTCEu 就会自动生成一条 VF
     *       （{@code hot_ingot + 500L 液氦 → ingot + 250L 氦}，时长 = mass×3，EU/t = 默认档），
     *       与 NH 手写那条（裸 {@code hot_ingot → ingot}，1600t，UHV）<b>并存</b>（输入不同，不冲突）。</li>
     *   <li>{@code setMeltingPoint(int)} —— GTCEu 的 {@code Material.Builder} 里没有这个方法
     *       （NH 的 GT5U 才有），也没有等价的 meltingPoint 字段 ⇒ <b>如实标注"本包无法表达"</b>。</li>
     *   <li>{@code setBlastFurnaceRequired(true)} —— GTCEu 同样没有这个方法。它的语义在 GTCEu 里是
     *       <b>由 {@code blastTemp > 0} 隐式实现</b>的：{@code MaterialRecipeHandler.processDust}
     *       在 {@code blastTemp <= 0} 时才加"熔炉烧粉出锭"的普通配方（字节码 {@code 685: iload 9;
     *       687: ifgt 721}），{@code blastTemp > 0} 直接走 {@code processEBFRecipe}
     *       ⇒ 只要写了 {@code blastTemp}，"必须走 EBF"这条就自动成立 ✓。</li>
     * </ol>
     *
     * <h3>自动链的判据（GTCEu 1.4.4 字节码逐条读出，不是猜的）</h3>
     * <pre>
     * MaterialRecipeHandler.processDust(prefix, material, dustProp, consumer):
     *   582: material.hasProperty(PropertyKey.INGOT) 否则跳到"压板"分支      ← ★ 必须有 INGOT
     *   592: hasAnyOfFlags(FLAMMABLE, NO_SMELTING) ⇒ 直接 return（不出配方）
     *   615: boolean hot = TagPrefix.ingotHot.doGenerateItem(material)       ← ingotHot 的生成条件是
     *                                                                          hasBlastProperty && (lambda)
     *   624: ingotStack = ChemicalHelper.get(hot ? ingotHot : ingot, material)
     *   685: if (blastTemp <= 0) → 普通熔炉 dust→ingotStack（除非 IS_MAGNETIC）
     *   721: else → processEBFRecipe(material, blastProp, ingotStack, consumer)
     * processEBFRecipe(...):
     *   60:  BLAST_RECIPES: dust ×1 → ingotStack，blastFurnaceTemp(blastTemp)，
     *        EUt = eutOverride>0 ? : GTValues.VA[2]，duration = durationOverride>0 ? : max(1, mass×blastTemp/50)
     *   229: if (TagPrefix.ingotHot.doGenerateItem(material))  ← ★ VF 是**在 EBF 方法内部**生成的
     *   239:   if blastTemp &lt; 5000 → VACUUM_RECIPES: ingotHot → ingot，duration = mass×3
     *   297:   else               → VACUUM_RECIPES: ingotHot + 500L 液氦 → ingot + 250L 氦，duration = mass×3
     * </pre>
     * ⇒ 这两味材料补完 {@code ingot + blastTemp} 后，EBF 与 VF <b>由 GTCEu 自动生成</b>，
     * 因此**不会**出现在 {@code data/gtlmodular/recipes/*.json} 里 —— 要确认只能进游戏看 JEI，
     * 或用 {@code NanochipSelfCheck} 的运行时段（本轮已加，见 §78）。
     */
    private static synchronized void registerNacMaterials() {
        if (materialsRegistered) {
            return;
        }
        materialsRegistered = true;

        // 富集钬（超高能蚀刻阵列的特征框架；玩家 2026-09-16 确认原版就是「富集钬框架」）
        //   ★ 2026-09-17（NH 对齐 · 形态补齐）：NH `MaterialsInit#loadEnrichedHolmium` 是
        //     `addMetalItems()` + `setBlastFurnaceTemp(3000)` + `setBlastFurnaceRequired(true)`，
        //     且**没有**关掉自动 EBF/VF ⇒ GT 自动生成 `dust → hot ingot @3000K` 与 `hot ingot → ingot`。
        //     本包等价物 = `.ingot()`（= addMetalItems 的最小等价）+ `.blastTemp(3000)`
        //     （"必须走 EBF"由 blastTemp>0 隐式成立，GTCEu 没有 blastFurnaceRequired 这个方法）。
        //     ⚠️ 代价（玩家已裁决接受）：JEI 里多出 `enriched_holmium_ingot` / `hot_enriched_holmium_ingot`。
        new Material.Builder(GTCEu.id("enriched_holmium"))
                .color(0xE2E88F)
                .secondaryColor(0xA3A3A3)
                .iconSet(MaterialIconSet.METALLIC)
                .dust()
                .ingot()
                .blastTemp(3000)
                .flags(MaterialFlags.GENERATE_FRAME)
                .buildAndRegister();

        // 八角铁（全板浸没的特征框架；GT++ 材料）
        new Material.Builder(GTCEu.id("octiron"))
                .color(0x3C3A44)
                .secondaryColor(0x8A8A96)
                .iconSet(MaterialIconSet.METALLIC)
                .dust()
                .flags(MaterialFlags.GENERATE_FRAME)
                .buildAndRegister();

        // UHV 超导粗胚（线材描绘器塔身框架；GT++/GT5U 材料名）
        //   ★ 2026-09-17（NH 对齐 · 形态补齐）：NH `loadSuperconductorUHVBase` 是
        //     `addDustItems()+addMetalItems()` + `setMeltingPoint(10800)` + `setBlastFurnaceTemp(10800)`
        //     + `setBlastFurnaceRequired(true)` + **关自动 EBF/VF**（这两味是因为 NH 的热锭来自 Exo Foundry）。
        //     GTCEu 1.4.4 **没有**关自动的 API（全 jar 常量池扫描 "AutoGenerate" 命中 0 次）
        //     ⇒ 本包只能接受自动生成；`setMeltingPoint` 也没有对应方法 ⇒ 如实标注"无法表达"。
        //     10800K 正好是 GTCEu 最高线圈 `CoilBlock.CoilType.TRITANIUM` 的温度（javap: sipush 10800）
        //     ⇒ 这条 EBF 在满配线圈下**真的能跑**，不是死配方。
        new Material.Builder(GTCEu.id("superconductor_uhv_base"))
                .color(0x5292C5)
                .secondaryColor(0xE0E8F0)
                .iconSet(MaterialIconSet.METALLIC)
                .dust()
                .ingot()
                .blastTemp(10800)
                .flags(MaterialFlags.GENERATE_FRAME)
                .buildAndRegister();

        // UEV 超导粗胚（线材描绘器的"板金属"位；本包无板金属形态 ⇒ 落到它的实心块）
        //   ★ 2026-09-17（第五轮·追加，玩家已裁决）：NH `loadSuperconductorUEVBase` 与 UHV 那味同构 ——
        //     `addDustItems()+addMetalItems()` + `setMeltingPoint(11800)` + `setBlastFurnaceTemp(11800)`
        //     + `setBlastFurnaceRequired(true)`；但它的**热锭/锭在 NH 侧也是 Exo Foundry 出来的**
        //     （NH 的 `BlastFurnaceRecipes` 里 Superconductor*Base 只有 UEV/UIV/UMV 三条，
        //      `COIL_HEAT` 分别是 11800/12700/13600；UEV 那条正是 11800）。
        //     ⇒ 本包补 `.ingot() + .blastTemp(11800)`，与 UHV 同款口径：
        //        **GTCEu 1.4.4 没有关自动 EBF/VF 的 API**（下面那段 javap 证据，全 jar 常量池
        //        `AutoGenerate` 命中 0）⇒ 不手写 EBF/VF，让 GTCEu 自动生成
        //        `dust → hot_ingot @11800K` 与 `hot_ingot + 500L 液氦 → ingot`。
        //     ⚠️ 与 NH 的参数差异（如实记录）：NH 那条 EBF 是 `dust×1 + circuit(11)`、**15720 t**、UV 档、
        //        COIL_HEAT 11800；GTCEu 自动那条是 `dust×1`、时长 = mass×blastTemp/50、EU/t = VA[2]（LV 档公式值），
        //        且**不消耗**程序电路 ⇒ 本包这条比 NH 更宽松（不会卡死在"要 11 号电路"上）。
        //     ⚠️ 另：`.ingot()` 会让 GTCEu 顺带生成 `superconductor_uev_base_plate`
        //        （GTCEu 的 ingot 材料默认出板，实例：`quantum_ingot` 与 `quantum_plate` 同时存在）。
        new Material.Builder(GTCEu.id("superconductor_uev_base"))
                .color(0x7E869F)
                .secondaryColor(0xEDEFF5)
                .iconSet(MaterialIconSet.METALLIC)
                .dust()
                .ingot()
                .blastTemp(11800)
                .flags(MaterialFlags.GENERATE_FRAME, MaterialFlags.FORCE_GENERATE_BLOCK)
                .buildAndRegister();

        NanochipModContext.logger().info("[gtlmodular] 已注册 4 味补充材料（GTCEu 自动生成框架方块）："
                + "enriched_holmium / octiron / superconductor_uhv_base / superconductor_uev_base");

        // ★ 2026-09-16 玩家要求：「拉多X聚合物 / 凯芙拉 / 觉醒龙」这三味**本包已有**的材料也要**真框架**
        //   （原来模块塔身只能退回"同材料实心块"）。做法 = 在**同一个 MaterialEvent 窗口**里给它们补
        //   框架生成所需的两个条件：`DUST` 属性 + `GENERATE_FRAME` 标志
        //   （生成条件 `hasProperty(DUST) && hasFlag(GENERATE_FRAME)` 见类注释里的 javap 证据；
        //    GTCEu 的方块生成发生在材料注册**之后** ⇒ 此时补还赶得上 ✓）。
        //   ⚠️ 已知副作用（玩家 2026-09-16 明确同意「补」）：这三味会顺带多出粉物品
        //      （radox_dust / kevlar_dust / draconiumawakened_dust），老存档安全。
        //   注意 `draconiumawakened` 的注册名**中间没有下划线**（§62.2 的实测结论）。
        upgradeExistingMaterial("radox");
        upgradeExistingMaterial("kevlar");
        upgradeExistingMaterial("draconiumawakened");

        // ★ 2026-09-18（队长指定）：「简并态铼框架用这个替换结构中的觉醒龙框架」。
        //   背景：机器锚点 = UHV 推出，而**觉醒龙在 UXV 阶段做不出来**（队长原话），实机日志
        //   `logs/latest.log:7029` 也记过玩家在觉醒龙框架那一格结构判定失败。
        //   简并态铼（`material.gtceu.degenerate_rhenium = 简并态铼`）在本包**只有粉/板/等离子体**，
        //   没有框架这件东西 ⇒ 按同一套做法给它补 `DUST + GENERATE_FRAME`，GTCEu 就会生成
        //   `gtceu:degenerate_rhenium_frame`，塔身 `A=FRAME:DegenerateRhenium` 才命中得了。
        //   副作用与上面三味相同（多出 `degenerate_rhenium_dust` 等粉形态），老存档安全。
        upgradeExistingMaterial("degenerate_rhenium");

        // ★ 2026-09-18（队长裁定·新增三台组织器）：给「山铜 / 魔金 / 塔兰」补框架。
        //   队长原话：「奇异优化组织器，山铜框架，寰宇优化组织器，魔金框架，超因果优化组织器，塔兰框架
        //   框架也需要你自己生成，他们结构都跟光学优化组织器一样，只是不同的框架」。
        //   取证（`_census/out/materials_lang.json`，来自 gtlcore-1.2.3.2-fix2.jar）：
        //     gtceu.orichalcum   = 山铜 / Orichalcum
        //     gtceu.infuscolium  = 魔金 / Infuscolium
        //     gtceu.taranium     = 塔兰 / Taranium
        //   三味材料在本包**都存在**（标签页/锭/板等形态齐全），但 `_census/out/pack_id_corpus.txt`
        //   里 `<mat>_frame` 命中 **0 次** ⇒ 与简并态铼同一情形：没有框架这件东西。
        //   ⇒ 同一套做法：在这个 MaterialEvent 窗口里补 `DUST + GENERATE_FRAME`，
        //      GTCEu 随后生成 `gtceu:orichalcum_frame` / `gtceu:infuscolium_frame` / `gtceu:taranium_frame`，
        //      塔身 `A=FRAME:Orichalcum|Infuscolium|Taranium`（驼峰材料名，`snake()` 转小写）才命中得了。
        //   副作用与 radox/kevlar/degenerate_rhenium 相同（多出 `_dust` 等粉形态，老存档安全）。
        //   ⚠️ 这三味的 lang 名由整合包（gtlcore）提供 ⇒ **不需要**我们补 `material.gtceu.*` 键
        //      （这也是 check_frame_landing.js 只对"我们新注册的 4 味"断言 lang 键的原因）。
        upgradeExistingMaterial("orichalcum");
        upgradeExistingMaterial("infuscolium");
        upgradeExistingMaterial("taranium");

        // ★ 2026-09-21（队长拍板）：「永恒/时空」那几条链的 OpV 材料换成 UXV 能拿到的 ——
        //   框架那一条队长指定用**星辉框架**。取证：`material.gtceu.starmetal = 星辉` 存在（gtlcore），
        //   但 `material_survey.md` §2.4 的「运行时真实存在的 55 个框架方块」里**没有** `gtceu:starmetal_frame`
        //   ⇒ 与简并态铼/山铜/魔金/塔兰同一情形：补 `DUST + GENERATE_FRAME`，GTCEu 随后生成
        //   `gtceu:starmetal_frame`，换算配方（`cc_framebox_hypogen` → `gtceu:starmetal_frame`）才命中得了。
        //   副作用与前面几味相同（多出 `starmetal_dust` 等粉形态，老存档安全）。
        upgradeExistingMaterial("starmetal");

        // ★ 2026-09-18（玩家裁定）：给本包**五味**超导体补「杆 + 螺栓」两件形态。
        //   为什么：NAC 的螺栓类 CC（NH 的 Bolt* 系列）的真件在包里**一件都没有**
        //   （整包带 GENERATE_BOLT_SCREW 的超导材料只有 EV 档 YBCO 一味），当年只能用 YBCO 螺栓顶替，
        //   还造成 `BoltUMVSuperconductor` 与 `BoltYttriumBariumCuprate` **撞件**。
        //   五味材料的形态现状（`_census/out/material_flags.json` javap 实证）：
        //     enriched_naquadah_trinium_europium_duranide（UV 超导体）ingot=true rod=false bolt=false
        //     ruthenium_trinium_americium_neutronate （UHV 超导体）ingot=true rod=false bolt=false
        //     enderite（UEV 超导体）                    ingot=true rod=false bolt=false
        //     echoite（UIV 超导体，回响合金）            ingot=true **rod=true**  bolt=false ← 只差螺栓
        //     legendarium（UXV 超导体）                 ingot=true rod=false bolt=false
        //   ⇒ 补 `GENERATE_ROD` + `GENERATE_BOLT_SCREW`（幂等：已有的不重复加），GTCEu 随后的材料物品注册
        //     会生成 `gtceu:<材料>_rod` 与 `gtceu:<材料>_bolt`，并由 `PartsRecipeHandler.processBolt`
        //     （门控 = `GENERATE_BOLT_SCREW`，见 `_nac_holmium/PartsRecipeHandler.txt:1623`）自动出配方。
        //   先例与机制同 `upgradeExistingMaterial`（radox/kevlar/draconiumawakened 的框架，
        //   游戏日志已实证 `gtceu:radox_frame` 生成成功）。
        upgradeSuperconductorBoltForm("enriched_naquadah_trinium_europium_duranide");
        upgradeSuperconductorBoltForm("ruthenium_trinium_americium_neutronate");
        upgradeSuperconductorBoltForm("enderite");
        upgradeSuperconductorBoltForm("echoite");
        upgradeSuperconductorBoltForm("legendarium");

        // ★ 2026-09-21（队长拍板）：「镀铑钯箔补上"箔"的生成条件」。
        //   起因：队长给「纳米芯片网格接口机械方块」定的新造价要吃 **48 个镀铑钯箔**，
        //   而本包的 `rhodium_plated_palladium` **没有 `GENERATE_FOIL`** ⇒ 这件物品全包不存在
        //   （写进配方会静默加载失败，正是我们一直在修的那类 bug）⇒ 按队长口径补标志。
        upgradeExistingMaterialFoil("rhodium_plated_palladium");

        // ★ 2026-09-17 P1（NAC 缺件）：注册本包缺的材料/形态 —— 7 档超导线材所需的
        //   superconductor_{luv,zpm,uv,uiv,umv}_base（并给已有 uhv/uev 补 cableProperties ⇒ 才有
        //   `<材料>_single_wire`）、chromatic_glass / six_phased_copper / universium / hexanite
        //   （只给配方要的最小形态：bolt / screw / plate）。数值与依据见该类注释。
        com.gtlmodular.common.data.nanochip.NanochipMissingMaterials.registerMissingMaterials();
    }

    /**
     * 给<b>本包已有</b>的材料补上"能生成框架方块"的两个条件（{@code DUST} 属性 + {@code GENERATE_FRAME}）。
     *
     * <p>为什么能这么补：{@code Material.addFlags(...)} / {@code setProperty(PropertyKey.DUST, ...)} 都是公开 API
     * （javap 实测，见类注释），而 GTCEu 生成材料方块发生在材料注册<b>之后</b>、本事件
     * （{@code MaterialEvent}）之内 ⇒ 补进去的标志会被后续的 TagPrefix 方块注册看到 ✓。
     *
     * <p>材料不存在时<b>只打一条 WARN 并跳过</b> —— 绝不能因为缺材料把注册搞崩（那会让玩家进不去游戏）。
     */
    private static void upgradeExistingMaterial(String name) {
        if (GTCEuAPI.materialManager == null) {
            NanochipModContext.logger().warn("[gtlmodular] 想给 {} 补框架，但 GTCEu 的材料管理器还没就绪 ⇒ 跳过", name);
            return;
        }
        Material material = GTCEuAPI.materialManager.getMaterial(name);
        if (material == null) {
            NanochipModContext.logger().warn("[gtlmodular] 想给 {} 补框架，但本包**没有**这味材料 ⇒ 跳过"
                    + "（模块塔身会退回同材料实心块）；请把这条日志告诉施工者", name);
            return;
        }
        boolean addedDust = false;
        if (!material.hasProperty(PropertyKey.DUST)) {
            material.setProperty(PropertyKey.DUST, new DustProperty());
            addedDust = true;
        }
        boolean addedFlag = !material.hasFlags(MaterialFlags.GENERATE_FRAME);
        if (addedFlag) {
            material.addFlags(MaterialFlags.GENERATE_FRAME);
        }
        NanochipModContext.logger().info("[gtlmodular] 已给已有材料 {} 补框架生成条件（DUST={} GENERATE_FRAME={}）"
                + "⇒ GTCEu 会生成 gtceu:{}_frame", name, addedDust ? "新增" : "已有", addedFlag ? "新增" : "已有", name);
    }

    /**
     * 给**本包已有**材料补「箔」生成条件（{@link MaterialFlags#GENERATE_FOIL}）。
     *
     * <p><b>为什么需要（2026-09-21 队长拍板）</b>：队长给「纳米芯片网格接口机械方块」定的新造价里要吃
     * <b>48 个镀铑钯箔</b>，但本包的 {@code rhodium_plated_palladium} 材料标志里**没有 {@code GENERATE_FOIL}**
     * （现读 {@code _census/out/material_flags.json}：只有 {@code GENERATE_BOLT_SCREW / DENSE / LONG_ROD /
     * PLATE / ROD / ROTOR / SMALL_GEAR}）⇒ 全包**没有**「镀铑钯箔」这件物品，配方会**静默加载失败**。
     * 队长口径：「镀铑钯箔补上"箔"的生成条件」⇒ 补标志，让 GTCEu 自己生成物品与配方。
     *
     * <p>机制与纪律同 {@link #upgradeExistingMaterial}：{@code Material.addFlags(...)} 是公开 API，
     * 而 GTCEu 的材料物品注册发生在材料注册<b>之后</b>、本事件（{@code MaterialEvent}）之内 ⇒ 补进去的标志
     * 会被后续 TagPrefix 物品注册看到 ✓。物品生成后，{@code MaterialRecipeHandler.processFoil}
     * 会按材料现有形态自动出配方（GTCEu 常量池实证：{@code processFoil} / {@code _plate_to_foil} /
     * {@code _ingot_to_foil} / {@code _dust_to_foil}）⇒ 箔拿得到。幂等；材料不存在时只 WARN 不抛。
     */
    private static void upgradeExistingMaterialFoil(String name) {
        if (GTCEuAPI.materialManager == null) {
            NanochipModContext.logger().warn("[gtlmodular] 想给 {} 补箔，但 GTCEu 的材料管理器还没就绪 ⇒ 跳过", name);
            return;
        }
        Material material = GTCEuAPI.materialManager.getMaterial(name);
        if (material == null) {
            NanochipModContext.logger().warn("[gtlmodular] 想给 {} 补箔，但本包**没有**这味材料 ⇒ 跳过；请把这条日志告诉施工者", name);
            return;
        }
        boolean addedFlag = !material.hasFlags(MaterialFlags.GENERATE_FOIL);
        if (addedFlag) {
            material.addFlags(MaterialFlags.GENERATE_FOIL);
        }
        NanochipModContext.logger().info("[gtlmodular] 已给已有材料 {} 补箔生成条件（GENERATE_FOIL={}）"
                + "⇒ GTCEu 会生成 gtceu:{}_foil 并自动出箔配方", name, addedFlag ? "新增" : "已有", name);
    }

    /**
     * 给<b>本包已有</b>的超导材料补「杆 + 螺栓」两件形态（{@code GENERATE_ROD} + {@code GENERATE_BOLT_SCREW}）。
     *
     * <p>为什么能这么补：与 {@link #upgradeExistingMaterial} 同一个理由 ——
     * {@code Material.addFlags(...)} 是公开 API，而 GTCEu 的材料物品注册发生在材料注册<b>之后</b>、
     * 本事件（{@code MaterialEvent}）之内 ⇒ 补进去的标志会被后续 TagPrefix 物品注册看到 ✓。
     *
     * <p>为什么需要：NAC 的 &quot;螺栓&quot; 类 CC 需要真件，而本包**整包**带 {@code GENERATE_BOLT_SCREW}
     * 的超导材料只有 EV 档 YBCO 一味 ⇒ 高档螺栓只能拿别的材料顶替（还会撞件）。补上后：
     * <ul>
     *   <li>{@code gtceu:legendarium_bolt} 成为 {@code cc_bolt_umv_superconductor} 的真件 ⇒ 撞件消失；</li>
     *   <li>三族（奇异/寰宇/超因果）的配方可按档取真螺栓。</li>
     * </ul>
     *
     * <p>材料不存在时<b>只打 WARN 并跳过</b>（绝不能把注册搞崩）；已有该标志时跳过（幂等）。
     */
    private static void upgradeSuperconductorBoltForm(String name) {
        if (GTCEuAPI.materialManager == null) {
            NanochipModContext.logger().warn("[gtlmodular] 想给 {} 补杆/螺栓，但 GTCEu 的材料管理器还没就绪 ⇒ 跳过", name);
            return;
        }
        Material material = GTCEuAPI.materialManager.getMaterial(name);
        if (material == null) {
            NanochipModContext.logger().warn("[gtlmodular] 想给 {} 补杆/螺栓，但本包**没有**这味材料 ⇒ 跳过", name);
            return;
        }
        boolean addedRod = !material.hasFlags(MaterialFlags.GENERATE_ROD);
        boolean addedBolt = !material.hasFlags(MaterialFlags.GENERATE_BOLT_SCREW);
        if (addedRod) {
            material.addFlags(MaterialFlags.GENERATE_ROD);
        }
        if (addedBolt) {
            material.addFlags(MaterialFlags.GENERATE_BOLT_SCREW);
        }
        NanochipModContext.logger().info("[gtlmodular] 已给超导材料 {} 补形态（GENERATE_ROD={} GENERATE_BOLT_SCREW={}）"
                + "⇒ GTCEu 会生成 gtceu:{}_rod 与 gtceu:{}_bolt", name,
                addedRod ? "新增" : "已有", addedBolt ? "新增" : "已有", name, name);
    }
}
