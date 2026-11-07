package com.gtlmodular.common.data.hpfc;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconSet;
import com.gregtechceu.gtceu.api.fluids.FluidBuilder;
import com.gtlmodular.GTLModular;

/**
 * GTO「高维等离子聚变核心」材料链 —— 整合包里<b>没有</b>的 <b>5 味材料</b>：
 *
 * <table border="1">
 *   <caption>数值全部照 GTO 源码 {@code MaterialB.java} / {@code MaterialC.java}</caption>
 *   <tr><th>材料 id</th><th>中文</th><th>GTO 定义</th><th>本类注册的形态</th></tr>
 *   <tr><td>{@code abs}</td><td>ABS 塑料</td>
 *       <td>{@code .polymer().fluid().components(Acrylonitrile 1, Butadiene 1, Styrene 2)
 *           .color(0xE8E7E5).iconSet(DULL).flags(GENERATE_PLATE, DISABLE_DECOMPOSITION)}</td>
 *       <td>{@code .dust().ingot().fluid().polymer()} + {@code GENERATE_PLATE} / {@code FORCE_GENERATE_BLOCK}</td></tr>
 *   <tr><td>{@code polystyrene}</td><td>聚苯乙烯</td>
 *       <td>{@code .polymer(1).liquid(new FluidBuilder().temperature(240))
 *           .flags(GENERATE_FOIL).color(0xC6C6C6)}</td>
 *       <td>{@code .dust().ingot().liquid(240K).polymer(1)} + {@code GENERATE_FOIL} / {@code GENERATE_PLATE} /
 *           {@code FORCE_GENERATE_BLOCK}</td></tr>
 *   <tr><td>{@code strontium_sulfate}</td><td>硫酸锶</td>
 *       <td>{@code .dust().color(0x6A67F1).iconSet(DULL)}</td><td>{@code .dust()}</td></tr>
 *   <tr><td>{@code cobalt_oxide_ceramic}</td><td>氧化钴陶瓷</td>
 *       <td>{@code .dust().color(0x222534).iconSet(BRIGHT).temp(1800)}</td><td>{@code .dust()}</td></tr>
 *   <tr><td>{@code strontium_carbonate_ceramic}</td><td>碳酸锶陶瓷</td>
 *       <td>{@code .dust().color(0xC5C5C5).temp(5400).iconSet(BRIGHT)}</td><td>{@code .dust()}</td></tr>
 * </table>
 *
 * <h2>★ 为什么 ABS / 聚苯乙烯要显式补 {@code .dust() / .ingot()} / {@code FORCE_GENERATE_BLOCK}</h2>
 * <p>GTCEu 1.4.4 的 {@code Material.Builder#polymer()} 只做 {@code ensureSet(POLYMER)}（javap 实测），
 * 而 GTO 用的 gtolib 分支会把聚合物默认形态一并补齐 —— 照抄 GTO 的链式调用，本包
 * <b>不会</b>生成 {@code abs_ingot / abs_plate / abs_block / polystyrene_ingot / polystyrene_foil}，
 * 结构配方（ABS 机壳）就会因缺件而静默加载失败。因此在链式调用里显式补齐：
 * <ul>
 *   <li>{@code .dust()} —— 粉（{@code abs_dust} / {@code polystyrene_dust}，GTO 的粉碎/液化链要用）；</li>
 *   <li>{@code .ingot()} —— 锭 + 粒（{@code abs_ingot/nugget}）；</li>
 *   <li>{@code MaterialFlags.FORCE_GENERATE_BLOCK} —— 材料块（{@code abs_block}；GTCEu 的
 *       {@code TagPrefix.block} 生成条件是 {@code (INGOT||GEM) && FORCE_GENERATE_BLOCK}，
 *       见 {@code _census/out/tagprefix_conditions.txt:127-128}）；</li>
 *   <li>{@code MaterialFlags.GENERATE_PLATE} / {@code GENERATE_FOIL} —— 板 / 箔
 *       （ABS 机壳与聚苯乙烯箔是结构配方的直接输入）。</li>
 * </ul>
 * <p>★ 两味陶瓷只出粉：GTO 的粗坯/块/砖/薄片是自己的 TagPrefix 形态，本包用普通物品重建
 * （见 {@link HpfcItems}），不再给陶瓷材料加形态。
 *
 * <h2>幂等 / 防崩</h2>
 * <p>与 {@code StellarForgeMaterials} 同款：布尔闸门只跑一次；注册前先用
 * {@code GTCEuAPI.materialManager.getMaterial(id)} 查重，已存在只 WARN 跳过（不抛）。
 */
public final class HpfcMaterials {

    private HpfcMaterials() {
    }

    /** 幂等闸门。 */
    private static boolean registered;

    /** 本类注册的 5 味材料 id（材料挂在 {@code gtceu} 命名空间下）。 */
    public static final String[] MATERIAL_IDS = {
            "abs",
            "polystyrene",
            "strontium_sulfate",
            "cobalt_oxide_ceramic",
            "strontium_carbonate_ceramic",
    };

    /**
     * <b>唯一入口</b>：必须在 GTCEu 的 {@code MaterialEvent} 窗口里调用
     * （由 {@code GTLModularAddon#registerNacMaterials()} 转调）。
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        // ------------------------------------------------------------ 1. ABS 塑料
        // GTO：MaterialB.java:3115-3121
        if (!skipIfPresent("abs")) {
            new Material.Builder(GTCEu.id("abs"))
                    .polymer()
                    .dust()
                    .ingot()
                    .fluid()
                    .color(0xE8E7E5)
                    .secondaryColor(lighten(0xE8E7E5, 0.55F))
                    .iconSet(MaterialIconSet.DULL)
                    .flags(MaterialFlags.GENERATE_PLATE,
                            MaterialFlags.FORCE_GENERATE_BLOCK,
                            MaterialFlags.DISABLE_DECOMPOSITION)
                    .buildAndRegister()
                    .setFormula("(C8H8·C4H6·C3H3N)n", false);
        }

        // ------------------------------------------------------------ 2. 聚苯乙烯
        // GTO：MaterialC.java:573-579
        if (!skipIfPresent("polystyrene")) {
            new Material.Builder(GTCEu.id("polystyrene"))
                    .polymer(1)
                    .dust()
                    .ingot()
                    .liquid(new FluidBuilder().temperature(240))
                    .color(0xC6C6C6)
                    .secondaryColor(lighten(0xC6C6C6, 0.55F))
                    .iconSet(MaterialIconSet.DULL)
                    .flags(MaterialFlags.GENERATE_FOIL,
                            MaterialFlags.GENERATE_PLATE,
                            MaterialFlags.FORCE_GENERATE_BLOCK)
                    .buildAndRegister()
                    .setFormula("(C8H8)n", false);
        }

        // ------------------------------------------------------------ 3. 硫酸锶
        // GTO：MaterialB.java:5056-5060
        if (!skipIfPresent("strontium_sulfate")) {
            new Material.Builder(GTCEu.id("strontium_sulfate"))
                    .dust()
                    .color(0x6A67F1)
                    .iconSet(MaterialIconSet.DULL)
                    .buildAndRegister()
                    .setFormula("SrSO4", false);
        }

        // ------------------------------------------------------------ 4. 氧化钴陶瓷
        // GTO：MaterialB.java:4948-4954（.temp(1800) 本包没有该 API，按恒星炎炀锻炉同款跳过）
        if (!skipIfPresent("cobalt_oxide_ceramic")) {
            new Material.Builder(GTCEu.id("cobalt_oxide_ceramic"))
                    .dust()
                    .color(0x222534)
                    .iconSet(MaterialIconSet.BRIGHT)
                    .buildAndRegister()
                    .setFormula("CoO", false);
        }

        // ------------------------------------------------------------ 5. 碳酸锶陶瓷
        // GTO：MaterialB.java:4996-5003（.temp(5400) 同上，跳过）
        if (!skipIfPresent("strontium_carbonate_ceramic")) {
            new Material.Builder(GTCEu.id("strontium_carbonate_ceramic"))
                    .dust()
                    .color(0xC5C5C5)
                    .iconSet(MaterialIconSet.BRIGHT)
                    .buildAndRegister()
                    .setFormula("SrCO3", false);
        }

        GTLModular.LOGGER.info("[gtlmodular] 高维等离子聚变核心·材料已注册 5 味："
                + "ABS 塑料 / 聚苯乙烯 / 硫酸锶 / 氧化钴陶瓷 / 碳酸锶陶瓷");
    }

    /** 材料已被别的 mod 注册时只 WARN 跳过（不抛）。 */
    private static boolean skipIfPresent(String name) {
        if (GTCEuAPI.materialManager == null) {
            return false;
        }
        Material existing = GTCEuAPI.materialManager.getMaterial(name);
        if (existing != null) {
            GTLModular.LOGGER.warn("[gtlmodular] 高维等离子聚变核心·材料 {} 已被其它 mod 注册，跳过", name);
            return true;
        }
        return false;
    }

    /** 与恒星炎炀锻炉同款：取主色的 55% 亮度作 secondaryColor（只影响粒子/图标副色）。 */
    private static int lighten(int rgb, float factor) {
        int r = (int) (((rgb >> 16) & 0xFF) * factor);
        int g = (int) (((rgb >> 8) & 0xFF) * factor);
        int b = (int) ((rgb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }
}
