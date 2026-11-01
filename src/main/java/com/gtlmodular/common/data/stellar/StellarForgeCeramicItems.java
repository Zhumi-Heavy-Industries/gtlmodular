package com.gtlmodular.common.data.stellar;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularMachines;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;

/**
 * GTO「恒星炎炀锻炉」材料链里的 <b>8 件陶瓷中间品</b>（纯 {@link Item}，无方块、无 NBT、无自定义行为）。
 *
 * <table border="1">
 *   <caption>id ↔ 中文名（lang 片段由 Lead 合并，本类不写 lang 文件）</caption>
 *   <tr><th>id（{@code gtlmodular:}）</th><th>中文</th><th>贴图母版</th></tr>
 *   <tr><td>{@code boron_carbide_ceramics_rough_blank}</td><td>碳化硼陶瓷粗坯</td><td>gtceu dull/crushed</td></tr>
 *   <tr><td>{@code boron_carbide_ceramics_block}</td><td>碳化硼陶瓷块</td><td>gtceu dull/block</td></tr>
 *   <tr><td>{@code boron_carbide_ceramics_brick}</td><td>碳化硼陶瓷砖</td><td>gtceu dull/ingot</td></tr>
 *   <tr><td>{@code boron_carbide_ceramics_flake}</td><td>碳化硼陶瓷薄片</td><td>gtocore dull/flake</td></tr>
 *   <tr><td>{@code magnesium_oxide_ceramic_rough_blank}</td><td>氧化镁陶瓷粗坯</td><td>gtceu dull/crushed</td></tr>
 *   <tr><td>{@code magnesium_oxide_ceramic_block}</td><td>氧化镁陶瓷块</td><td>gtceu dull/block</td></tr>
 *   <tr><td>{@code magnesium_oxide_ceramic_brick}</td><td>氧化镁陶瓷砖</td><td>gtceu dull/ingot</td></tr>
 *   <tr><td>{@code magnesium_oxide_ceramic_flake}</td><td>氧化镁陶瓷薄片</td><td>gtocore dull/flake</td></tr>
 * </table>
 *
 * <h2>为什么是「普通物品」而不是材料形态</h2>
 * <p>这 8 件在 GTO 里是**独立的中间品**（粗坯 → 块/砖/薄片 的加工链），
 * 不是任何材料的 TagPrefix 形态（GTCEu 1.4.4 的 71 个 TagPrefix 里没有
 * {@code ceramicBrick} / {@code ceramicFlake} 这类形态，见
 * {@code _census/out/tagprefix_conditions.txt:213}）⇒ 只能自注册真实物品。
 * 与它们同名的<b>材料</b>（{@code boron_carbide_ceramics} / {@code magnesium_oxide_ceramic}）
 * 由 {@link StellarForgeMaterials} 注册，只出粉；两者 id 不重叠（材料形态带 {@code gtceu:} 前缀）。
 *
 * <h2>资产（随包发布，本工程<b>不跑 runData</b>）</h2>
 * <ul>
 *   <li>{@code assets/gtlmodular/models/item/<id>.json}：{@code item/generated} + 一层
 *       {@code gtlmodular:item/ceramics/<id>}；</li>
 *   <li>{@code assets/gtlmodular/textures/item/ceramics/<id>.png}：16×16，由
 *       {@code _scratch/stellar/gen_ceramic_item_textures.py} 从 gtocore/gtceu 的 dull 母版
 *       <b>按材料色相乘</b>生成（boron 0x2B2B31 / MgO 0xFFE198），每张的母版与 sha256 记在
 *       {@code _scratch/stellar/texture_manifest_items.json} 与 {@code materials_report.md}；</li>
 *   <li>语言键：{@code item.gtlmodular.<id>}，片段见
 *       {@code _scratch/stellar/lang_items_zh.json} / {@code lang_items_en.json}（<b>不</b>直接改 mod 的 lang）。</li>
 * </ul>
 *
 * <h2>注册时机</h2>
 * <p>条目在<b>静态初始化</b>时建好（与 {@code NanochipMissingItems} 同款），
 * {@link #init()} 只负责强制类加载 + 打一行日志。★ 调用点必须在 Forge 的
 * {@code RegisterEvent<Item>} <b>之前</b>（Lead 接线，见交付说明）。
 */
public final class StellarForgeCeramicItems {

    private StellarForgeCeramicItems() {
    }

    /** 8 件陶瓷物品的 id（不带命名空间；供配方层 / 自检引用，避免手写拼错）。 */
    public static final String[] ITEM_IDS = {
            "boron_carbide_ceramics_rough_blank",
            "boron_carbide_ceramics_block",
            "boron_carbide_ceramics_brick",
            "boron_carbide_ceramics_flake",
            "magnesium_oxide_ceramic_rough_blank",
            "magnesium_oxide_ceramic_block",
            "magnesium_oxide_ceramic_brick",
            "magnesium_oxide_ceramic_flake",
    };

    /** 碳化硼陶瓷粗坯（GTO 粗坯 → 块/砖/薄片的链头）。 */
    public static final ItemEntry<Item> BORON_CARBIDE_CERAMICS_ROUGH_BLANK =
            item("boron_carbide_ceramics_rough_blank");

    /** 碳化硼陶瓷块。 */
    public static final ItemEntry<Item> BORON_CARBIDE_CERAMICS_BLOCK =
            item("boron_carbide_ceramics_block");

    /** 碳化硼陶瓷砖。 */
    public static final ItemEntry<Item> BORON_CARBIDE_CERAMICS_BRICK =
            item("boron_carbide_ceramics_brick");

    /** 碳化硼陶瓷薄片。 */
    public static final ItemEntry<Item> BORON_CARBIDE_CERAMICS_FLAKE =
            item("boron_carbide_ceramics_flake");

    /** 氧化镁陶瓷粗坯。 */
    public static final ItemEntry<Item> MAGNESIUM_OXIDE_CERAMIC_ROUGH_BLANK =
            item("magnesium_oxide_ceramic_rough_blank");

    /** 氧化镁陶瓷块。 */
    public static final ItemEntry<Item> MAGNESIUM_OXIDE_CERAMIC_BLOCK =
            item("magnesium_oxide_ceramic_block");

    /** 氧化镁陶瓷砖。 */
    public static final ItemEntry<Item> MAGNESIUM_OXIDE_CERAMIC_BRICK =
            item("magnesium_oxide_ceramic_brick");

    /** 氧化镁陶瓷薄片。 */
    public static final ItemEntry<Item> MAGNESIUM_OXIDE_CERAMIC_FLAKE =
            item("magnesium_oxide_ceramic_flake");

    /**
     * 建一条普通物品（与 {@code NanochipMissingItems} 的写法逐字同款：
     * 同一个 {@link GTLModularMachines#REGISTRATE}，{@code Item::new}，不额外加模型/label provider
     * —— 模型与 lang 都是手写资源，由 {@code runData} 之外的路径随包发布）。
     */
    private static ItemEntry<Item> item(String id) {
        return GTLModularMachines.REGISTRATE.item(id, Item::new).register();
    }

    /**
     * 强制类加载（接线用；条目已在静态初始化里建好，重复调用无副作用）。
     *
     * <p>★ 必须在 Forge 的 {@code RegisterEvent<Item>} <b>之前</b>调用
     * （与 {@code NanochipMissingItems.init()} / {@code StellarForgeBlocks.init()} 同一个位置与理由）。
     */
    public static void init() {
        GTLModular.LOGGER.info("[gtlmodular] 恒星炎炀锻炉·陶瓷物品已注册 8 件（id 见 StellarForgeCeramicItems.ITEM_IDS）："
                + "碳化硼陶瓷 粗坯/块/砖/薄片 ×1 + 氧化镁陶瓷 粗坯/块/砖/薄片 ×1");
    }
}
