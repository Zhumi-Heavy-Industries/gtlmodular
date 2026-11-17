package com.gtlmodular.common.data.hpfc;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularMachines;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;

/**
 * GTO「高维等离子聚变核心」材料链里的 <b>10 件普通物品</b>（纯 {@link Item}，无方块、无 NBT、无自定义行为）：
 *
 * <table border="1">
 *   <caption>id ↔ 中文名 ↔ 来源</caption>
 *   <tr><th>id（{@code gtlmodular:}）</th><th>中文</th><th>GTO 原 id / 说明</th></tr>
 *   <tr><td>{@code catalyst_base}</td><td>催化剂基底</td>
 *       <td>{@code gtocore:catalyst_base}（GTO 有同 id 物品，贴图逐字节取自 GTO）</td></tr>
 *   <tr><td>{@code hafnium_catalyst}</td><td>铪催化剂</td>
 *       <td>{@code gtocore:hafnium_catalyst}（GTO 的新版催化件；本包按普通物品重建，装配机配方见 HpfcRecipes）</td></tr>
 *   <tr><td>{@code strontium_carbonate_ceramic_rough_blank}</td><td>碳酸锶陶瓷粗坯</td>
 *       <td>GTO 的 {@code rough_blank} 形态（本包 GTCEu 无该 TagPrefix ⇒ 普通物品）</td></tr>
 *   <tr><td>{@code strontium_carbonate_ceramic_block}</td><td>碳酸锶陶瓷块</td>
 *       <td>GTO 的烧结产物（材料块形态）</td></tr>
 *   <tr><td>{@code strontium_carbonate_ceramic_brick}</td><td>碳酸锶陶瓷砖</td>
 *       <td>GTO 的 {@code brick} 形态</td></tr>
 *   <tr><td>{@code strontium_carbonate_ceramic_flake}</td><td>碳酸锶陶瓷薄片</td>
 *       <td>GTO 的 {@code flake} 形态（结构符号 F 的方块主材）</td></tr>
 *   <tr><td>{@code cobalt_oxide_ceramic_rough_blank}</td><td>氧化钴陶瓷粗坯</td><td>同上（钴系）</td></tr>
 *   <tr><td>{@code cobalt_oxide_ceramic_block}</td><td>氧化钴陶瓷块</td><td>同上（钴系）</td></tr>
 *   <tr><td>{@code cobalt_oxide_ceramic_brick}</td><td>氧化钴陶瓷砖</td><td>同上（钴系）</td></tr>
 *   <tr><td>{@code cobalt_oxide_ceramic_flake}</td><td>氧化钴陶瓷薄片</td><td>同上（钴系，结构符号 Q 的方块主材）</td></tr>
 * </table>
 *
 * <p><b>为什么是「普通物品」而不是材料形态</b>：GTCEu 1.4.4 的 71 个 TagPrefix 里没有
 * {@code rough_blank} / {@code brick} / {@code flake} 这类形态（GTO 是在自己的
 * {@code GTOTagPrefix} 里造的 {@code ROUGH_BLANK / BRICK / FLAKES}）⇒ 只能自注册真实物品，
 * 与恒星炎炀锻炉那轮的 8 件陶瓷中间品（{@code StellarForgeCeramicItems}）同一口径。
 * 同名的<b>材料</b>（{@code strontium_carbonate_ceramic} / {@code cobalt_oxide_ceramic} /
 * {@code strontium_sulfate}）由 {@link HpfcMaterials} 注册，只出粉；两者 id 不重叠
 * （材料形态带 {@code gtceu:} 前缀）。
 *
 * <h2>资产（随包发布，本工程<b>不跑 runData</b>）</h2>
 * <ul>
 *   <li>{@code assets/gtlmodular/models/item/<id>.json}：{@code item/generated} +
 *       {@code gtlmodular:item/hpfc/<id>}；</li>
 *   <li>{@code assets/gtlmodular/textures/item/hpfc/<id>.png}：16×16，由
 *       {@code _scratch/hpfc/gen_assets.py} 从 gtocore/gtceu 的 dull 母版按材料色相乘生成；
 *       两件催化件直接逐字节拷 GTO 贴图；</li>
 *   <li>语言键：{@code item.gtlmodular.<id>}。</li>
 * </ul>
 */
public final class HpfcItems {

    private HpfcItems() {
    }

    /** 10 件物品的 id（不带命名空间；供配方生成脚本 / 自检引用，避免手写拼错）。 */
    public static final String[] ITEM_IDS = {
            "catalyst_base",
            "hafnium_catalyst",
            "strontium_carbonate_ceramic_rough_blank",
            "strontium_carbonate_ceramic_block",
            "strontium_carbonate_ceramic_brick",
            "strontium_carbonate_ceramic_flake",
            "cobalt_oxide_ceramic_rough_blank",
            "cobalt_oxide_ceramic_block",
            "cobalt_oxide_ceramic_brick",
            "cobalt_oxide_ceramic_flake",
    };

    /** 催化剂基底（GTO {@code catalyst_base}）。 */
    public static final ItemEntry<Item> CATALYST_BASE = item("catalyst_base");
    /** 铪催化剂（GTO {@code hafnium_catalyst}）。 */
    public static final ItemEntry<Item> HAFNIUM_CATALYST = item("hafnium_catalyst");

    /** 碳酸锶陶瓷粗坯。 */
    public static final ItemEntry<Item> STRONTIUM_CARBONATE_CERAMIC_ROUGH_BLANK =
            item("strontium_carbonate_ceramic_rough_blank");
    /** 碳酸锶陶瓷块。 */
    public static final ItemEntry<Item> STRONTIUM_CARBONATE_CERAMIC_BLOCK =
            item("strontium_carbonate_ceramic_block");
    /** 碳酸锶陶瓷砖。 */
    public static final ItemEntry<Item> STRONTIUM_CARBONATE_CERAMIC_BRICK =
            item("strontium_carbonate_ceramic_brick");
    /** 碳酸锶陶瓷薄片。 */
    public static final ItemEntry<Item> STRONTIUM_CARBONATE_CERAMIC_FLAKE =
            item("strontium_carbonate_ceramic_flake");

    /** 氧化钴陶瓷粗坯。 */
    public static final ItemEntry<Item> COBALT_OXIDE_CERAMIC_ROUGH_BLANK =
            item("cobalt_oxide_ceramic_rough_blank");
    /** 氧化钴陶瓷块。 */
    public static final ItemEntry<Item> COBALT_OXIDE_CERAMIC_BLOCK =
            item("cobalt_oxide_ceramic_block");
    /** 氧化钴陶瓷砖。 */
    public static final ItemEntry<Item> COBALT_OXIDE_CERAMIC_BRICK =
            item("cobalt_oxide_ceramic_brick");
    /** 氧化钴陶瓷薄片。 */
    public static final ItemEntry<Item> COBALT_OXIDE_CERAMIC_FLAKE =
            item("cobalt_oxide_ceramic_flake");

    private static ItemEntry<Item> item(String id) {
        return GTLModularMachines.REGISTRATE.item(id, Item::new).register();
    }

    /** 强制类加载（必须在 Forge 的 {@code RegisterEvent<Item>} 之前调用）。 */
    public static void init() {
        GTLModular.LOGGER.info("[gtlmodular] 高维等离子聚变核心·材料链物品已注册 10 件（id 见 HpfcItems.ITEM_IDS）");
    }
}
