package com.gtlmodular.common.data.stellar;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconSet;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gtlmodular.GTLModular;

/**
 * GTO「恒星炎炀锻炉」（{@code gtocore:stellar_forge}）结构材料链 ——
 * 整合包里<b>没有</b>的 <b>5 味材料</b>（3 味合金 + 2 味陶瓷粉）。
 *
 * <h2>为什么必须自注册</h2>
 * <p>恒星炎炀锻炉的结构符号位 / 配方输入引用了这 5 味材料，而本整合包
 * （{@code gtceu} 1.4.4 / {@code gtlcore} / {@code gtladditions} / {@code GTLsupb}，且 <b>GTO 本体不在包里</b>）
 * 一味都没有。取证：{@code _census/out/runtime_registry.json} 的 {@code minecraft:item}
 * 全量 16261 条里，{@code babbitt} / {@code trinaquad} / {@code magnesium_oxide} 命中 <b>0</b> 条；
 * {@code boron_carbide} 只有 GTCEu 自己的粉末形态（{@code gtceu:boron_carbide_dust}，
 * 与本次注册的 {@code boron_carbide_ceramics} <b>不是同一味</b>，id 不冲突）；
 * 整合包 kubejs 脚本（{@code kubejs/**}）对这 5 个 id 也 <b>0</b> 命中。
 *
 * <h2>数值来源</h2>
 * <p>颜色 / 图标集 / 成分 / 高炉温度全部由队长从 GTO 源码 {@code MaterialA.java} / {@code MaterialB.java}
 * 核对后给出（GTO 的 {@code MaterialA/B} 不在本机转储里，本类<b>不重算</b>，照抄任务表的 24 位色值）。
 *
 * <table border="1">
 *   <caption>本类注册的 5 味材料</caption>
 *   <tr><th>材料 id</th><th>中文</th><th>色值</th><th>图标集</th><th>blastTemp</th><th>flags</th></tr>
 *   <tr><td>{@code babbitt_alloy}</td><td>巴氏合金</td><td>{@code 0xA19CA4}</td><td>METALLIC</td>
 *       <td>737</td><td>FRAME / PLATE / GEAR</td></tr>
 *   <tr><td>{@code depleted_uranium_alloy}</td><td>贫铀合金</td><td>{@code 0x444B42}</td><td>METALLIC</td>
 *       <td>3450</td><td>FRAME / PLATE</td></tr>
 *   <tr><td>{@code trinaquadalloy}</td><td>碳化凯金硅岩合金</td><td>{@code 0x281832}</td><td>METALLIC</td>
 *       <td>8747</td><td>FRAME / PLATE</td></tr>
 *   <tr><td>{@code boron_carbide_ceramics}</td><td>碳化硼陶瓷</td><td>{@code 0x2B2B31}</td><td>BRIGHT</td>
 *       <td>—</td><td>（无；只出粉）</td></tr>
 *   <tr><td>{@code magnesium_oxide_ceramic}</td><td>氧化镁陶瓷</td><td>{@code 0xFFE198}</td><td>BRIGHT</td>
 *       <td>—</td><td>（无；只出粉）</td></tr>
 * </table>
 *
 * <h2>★ 与任务清单的两处<b>刻意偏差</b>（都记在 {@code _scratch/stellar/materials_report.md}）</h2>
 * <ol>
 *   <li><b>3 味合金额外补了 {@code .dust()}</b>：任务清单只列了 {@code .ingot()}，但 GTCEu 1.4.4 的
 *       {@code Material.Builder#ingot()} <b>只</b> {@code ensureSet(PropertyKey.INGOT)}（{@code javap -c}
 *       逐字节读出），<b>不会</b>顺带给 DUST；而
 *       <ul>
 *         <li>框架的生成条件是 {@code hasProperty(DUST) && hasFlag(GENERATE_FRAME)}
 *             （{@code _census/out/tagprefix_conditions.txt:147-148}，{@code prop:DUST & GENERATE_FRAME}）
 *             ⇒ 不补 DUST 就<b>没有</b> {@code <材料>_frame}，结构直接判定失败；</li>
 *         <li>任务注释本身也期望自动出 {@code dust} 形态。</li>
 *       </ul>
 *       ⇒ 补 {@code .dust()}（与 {@code NanochipMissingMaterials.newMaterial()} 同款、同 API，无破坏性）。</li>
 *   <li><b>2 味陶瓷没有 {@code .temp(int)} / {@code .formula(...)}</b>：GTCEu 1.4.4 的
 *       {@code Material$Builder} 方法表里<b>没有</b>这两个方法（{@code javap} 全量方法表实测；
 *       {@code formula} 只存在于 {@code Material#setFormula(String)}，不是 Builder 链式 API）
 *       ⇒ 按任务口径「API 没有就跳过」，不硬编。</li>
 * </ol>
 *
 * <h2>幂等 / 防崩</h2>
 * <p>{@code registered} 布尔闸门保证<b>只跑一次</b>（重复注册材料会让 GTCEu 的
 * {@code MaterialRegistry#register} 抛异常、游戏起不来）；另外每味材料注册前再用
 * {@code GTCEuAPI.materialManager.getMaterial(id)} 查一次「是否已被别的 mod 注册」，
 * 已存在只 WARN 跳过（与 {@code GTLModularAddon#upgradeExistingMaterial} 同款防御，不用
 * {@code GTCEuAPI.materialManager == null} 时也安全 —— 那种情况下 getMaterial 不会被调用）。
 *
 * <p>★ 语言键：材料显示名需要 {@code material.gtceu.<id>} / 物品形态需要 GTCEu 的
 * {@code item.gtceu.<prefix>.<material>} 键，本类<b>不</b>写 lang 文件（Lead 合并）；
 * 批次片段见 {@code _scratch/stellar/lang_materials_zh.json} / {@code lang_materials_en.json}。
 */
public final class StellarForgeMaterials {

    private StellarForgeMaterials() {
    }

    /** 幂等闸门（与 addon 侧的 {@code materialsRegistered} 互相独立，双保险）。 */
    private static boolean registered;

    /** 本类注册的 5 味材料 id（供报告 / 自检 / 配方层引用，不带命名空间 —— 材料挂在 {@code gtceu} 下）。 */
    public static final String[] MATERIAL_IDS = {
            "babbitt_alloy",
            "depleted_uranium_alloy",
            "trinaquadalloy",
            "boron_carbide_ceramics",
            "magnesium_oxide_ceramic",
    };

    /**
     * ★ <b>唯一入口</b>：必须在 GTCEu 的 {@code MaterialEvent} 窗口里调用
     * （与 {@code NanochipMissingMaterials.registerMissingMaterials()} 同一个时机；
     * 由 {@code GTLModularAddon#registerNacMaterials()} 或 Lead 指定的等价位置转调）。
     *
     * <p>幂等：重复调用直接返回；材料已被别的 mod 注册时只 WARN 跳过、<b>不抛</b>。
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        // ------------------------------------------------------------ 1. 巴氏合金
        // GTO：.ingot() .color(0xa19ca4) .iconSet(METALLIC)
        //      .components(Tin 5, Lead 36, Antimony 8, Astatine 1) .blastTemp(737)
        //      .flags(GENERATE_FRAME, GENERATE_PLATE, GENERATE_GEAR)
        // components 用的 4 个字段都在 GTCEu 1.4.4 的 GTMaterials 里
        //   （_scratch/tentacle/GTMaterials.javap.txt：Tin:238 / Lead:124 / Antimony:22 / Astatine:28）
        if (!skipIfPresent("babbitt_alloy")) {
            new Material.Builder(GTCEu.id("babbitt_alloy"))
                    .color(0xA19CA4)
                    .secondaryColor(lighten(0xA19CA4, 0.55F))
                    .iconSet(MaterialIconSet.METALLIC)
                    .dust()
                    .ingot()
                    .components(GTMaterials.Tin, 5, GTMaterials.Lead, 36,
                            GTMaterials.Antimony, 8, GTMaterials.Astatine, 1)
                    .blastTemp(737)
                    .flags(MaterialFlags.GENERATE_FRAME, MaterialFlags.GENERATE_PLATE,
                            MaterialFlags.GENERATE_GEAR, MaterialFlags.GENERATE_ROD)
                    .buildAndRegister();
        }

        // ------------------------------------------------------------ 2. 贫铀合金
        // GTO：.ingot() .color(0x444b42) .iconSet(METALLIC) .components(Uranium238 9, Titanium 1)
        //      .blastTemp(3450) .flags(GENERATE_FRAME, GENERATE_PLATE)
        // Uranium238:246 / Titanium:240（GTMaterials.javap.txt）都在。
        if (!skipIfPresent("depleted_uranium_alloy")) {
            new Material.Builder(GTCEu.id("depleted_uranium_alloy"))
                    .color(0x444B42)
                    .secondaryColor(lighten(0x444B42, 0.55F))
                    .iconSet(MaterialIconSet.METALLIC)
                    .dust()
                    .ingot()
                    .components(GTMaterials.Uranium238, 9, GTMaterials.Titanium, 1)
                    .blastTemp(3450)
                    .flags(MaterialFlags.GENERATE_FRAME, MaterialFlags.GENERATE_PLATE,
                            MaterialFlags.GENERATE_ROD)
                    .buildAndRegister();
        }

        // ------------------------------------------------------------ 3. 碳化凯金硅岩合金
        // GTO：.ingot() .color(0x281832) .iconSet(METALLIC) .components(Trinium 6, Naquadah 2, Carbon 1)
        //      .blastTemp(8747) .flags(GENERATE_FRAME, GENERATE_PLATE)
        // Trinium:274 / Naquadah:262 / Carbon:50（GTMaterials.javap.txt）都在。
        if (!skipIfPresent("trinaquadalloy")) {
            new Material.Builder(GTCEu.id("trinaquadalloy"))
                    .color(0x281832)
                    .secondaryColor(lighten(0x281832, 0.55F))
                    .iconSet(MaterialIconSet.METALLIC)
                    .dust()
                    .ingot()
                    .components(GTMaterials.Trinium, 6, GTMaterials.Naquadah, 2, GTMaterials.Carbon, 1)
                    .blastTemp(8747)
                    .flags(MaterialFlags.GENERATE_FRAME, MaterialFlags.GENERATE_PLATE,
                            MaterialFlags.GENERATE_ROD)
                    .buildAndRegister();
        }

        // ------------------------------------------------------------ 4. 碳化硼陶瓷（只有粉）
        // GTO：.dust() .color(0x2b2b31) .iconSet(BRIGHT) .temp(4500) .formula("B4C")
        // ★ .temp(int) / .formula(String) 在 GTCEu 1.4.4 的 Material$Builder 上**不存在**（javap 实测）
        //   ⇒ 按任务口径跳过。没有 flags ⇒ 不生成板/框架，只有 dust / small_dust / tiny_dust。
        // ⚠️ BRIGHT 图标集在 1.4.4 里 dump 的 dust 模型被上游禁用（models/item/material_sets/bright/
        //   dust.json_old）；模型按 BRIGHT→SHINY→METALLIC 的 parentIconset 链回退（javap 实测
        //   BRIGHT.parent=SHINY、SHINY.parent=METALLIC），最终用 metallic 的粉底 + bright overlay。
        if (!skipIfPresent("boron_carbide_ceramics")) {
            new Material.Builder(GTCEu.id("boron_carbide_ceramics"))
                    .color(0x2B2B31)
                    .secondaryColor(lighten(0x2B2B31, 0.55F))
                    .iconSet(MaterialIconSet.BRIGHT)
                    .dust()
                    .buildAndRegister();
        }

        // ------------------------------------------------------------ 5. 氧化镁陶瓷（只有粉）
        // GTO：.dust() .color(0xffe198) .iconSet(BRIGHT) .temp(3400) .formula("MgO")
        // 同上：.temp / .formula 无 API，跳过。
        if (!skipIfPresent("magnesium_oxide_ceramic")) {
            new Material.Builder(GTCEu.id("magnesium_oxide_ceramic"))
                    .color(0xFFE198)
                    .secondaryColor(lighten(0xFFE198, 0.55F))
                    .iconSet(MaterialIconSet.BRIGHT)
                    .dust()
                    .buildAndRegister();
        }

        GTLModular.LOGGER.info("[gtlmodular] 恒星炎炀锻炉·材料链：已注册 5 味材料"
                + "（babbitt_alloy / depleted_uranium_alloy / trinaquadalloy / "
                + "boron_carbide_ceramics / magnesium_oxide_ceramic）"
                + "⇒ GTCEu 自动生成 3 味合金的 dust/ingot/hot_ingot/nugget/plate/double_plate/frame"
                + "（巴氏合金另有 gear）+ 2 味陶瓷粉；清单见 _scratch/stellar/materials_report.md");
    }

    // ---------------------------------------------------------------- 工具

    /**
     * 材料已被别的 mod 注册时返回 {@code true}（只 WARN 跳过，<b>绝不抛</b>）。
     *
     * <p>与 {@code GTLModularAddon#upgradeExistingMaterial} 同款防御：{@code materialManager}
     * 尚未就绪时<b>不</b>做查重（返回 {@code false}，照常注册 —— 本类只在 MaterialEvent
     * 窗口里被调用，那时管理器一定已经就绪）。
     */
    private static boolean skipIfPresent(String id) {
        if (GTCEuAPI.materialManager == null) {
            return false;
        }
        if (GTCEuAPI.materialManager.getMaterial(id) != null) {
            GTLModular.LOGGER.warn("[gtlmodular] 恒星炎炀锻炉·材料链：gtceu:{} 已存在（别的 mod 已注册）"
                    + "⇒ 跳过本味，配方会引用既有那味", id);
            return true;
        }
        return false;
    }

    /** 向白色方向线性提亮（{@code 0}=原色，{@code 1}=纯白）；副色派生口径与 {@code NanochipMissingMaterials} 一致。 */
    private static int lighten(int rgb, float amount) {
        final float a = Math.max(0F, Math.min(1F, amount));
        final int r = (rgb >> 16) & 0xFF;
        final int g = (rgb >> 8) & 0xFF;
        final int b = rgb & 0xFF;
        final int nr = Math.round(r + (0xFF - r) * a);
        final int ng = Math.round(g + (0xFF - g) * a);
        final int nb = Math.round(b + (0xFF - b) * a);
        return (nr << 16) | (ng << 8) | nb;
    }
}
