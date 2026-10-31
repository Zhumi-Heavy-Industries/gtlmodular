package com.gtlmodular.client;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.material.IMaterialRegistryManager;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.nanochip.NanochipCircuitComponents;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * <b>NAC 电路元件（CC/PC，366 件）的材料贴图上色接线</b> —— 客户端 mod 总线，只订阅 {@code Dist.CLIENT}。
 *
 * <h2>根因（为什么会「转换以后全是白的」）</h2>
 * 366 件 CC 里有一类用的是 <b>GTCEu 的灰度材料母版</b>做 {@code layer0}，例如
 * {@code cc_wire_niobium_titanium.json}：
 * <pre>
 *   { "parent": "item/generated",
 *     "textures": { "layer0": "gtceu:item/material_sets/dull/wire_fine",
 *                   "layer1": "gtlmodular:item/cc/circuit_component_overlay" } }
 * </pre>
 * GTCEu 自己的材料物品之所以有色，是因为它<b>注册了 ItemColor</b>
 * （{@code TagPrefixItem.tintColor()} → {@code material.getLayerARGB(tintIndex)}，
 * 注册走 Registrate 的 {@code ItemBuilder.color(...)} → {@code registerItemColor()}）；
 * 而本模组的 CC 件是**自注册物品**（{@code CcItem}），此前全仓没有任何
 * {@code RegisterColorHandlersEvent} / {@code ItemColor} 注册
 * ⇒ 灰度底原样画 = <b>全白</b>。
 *
 * <p><b>本类自己的取证（不引用别人的普查结论）</b>：扫
 * {@code assets/gtlmodular/models/item/cc_*.json} 366 个模型，
 * <b>{@code layer0} 以 {@code gtceu:item/material_sets/} 或 {@code gtceu:block/material_sets/} 开头的恰好
 * <b>116</b> 个</b>（其余 250 个的 layer0 是本色贴图或本模组自己的 png）⇒ 116 件需要上色，
 * 250 件<b>一律不碰</b>（再乘一层色就毁了）。
 *
 * <h2>挂点为什么在这里</h2>
 * <ul>
 *   <li>照 {@code com.gtlmodular.client.ctm.CtmClientSetup} 的先例：{@code @Mod.EventBusSubscriber}
 *       指定 {@code bus = Bus.MOD, value = Dist.CLIENT} —— 服务端不加载本类，缺客户端类也不会崩；
 *       Forge 的 {@code RegisterColorHandlersEvent} 是 {@code IModBusEvent} ⇒ 只能挂 mod 总线。</li>
 *   <li>物品注册行在 {@code NanochipCircuitComponents} 的静态块里（{@code REGISTRATE.item(...).register()}），
 *       那里没有 {@code .color(...)} 可用（该类本轮**不许改**）⇒ 用等价的 Forge 事件直接注册，
 *       与 Registrate {@code registerItemColor()} 内部做的事完全一致。</li>
 * </ul>
 *
 * <h2>要点 / 纪律</h2>
 * <ol>
 *   <li><b>材料现推，无手写映射表（两条路）</b>：
 *     <ol>
 *       <li><b>注册行</b>：{@code add(...)} 的第 5 个参数 {@code realRef} 形如
 *           {@code mat:<gtceu 材料名>:<tagprefix 键>}（例 {@code mat:niobium_titanium:fine_wire}）
 *           ⇒ 取第一个 {@code ':'} 之前那段材料名，交给
 *           {@link IMaterialRegistryManager#getMaterial(String)}（无命名空间 ⇒ 遍历全部材料注册表）；</li>
 *       <li><b>配方图生成表</b>（2026-09-21 队长裁决新增，只对 {@code realRef} 为空的件启用）：
 *           {@link CcMaterialByItem#MATERIALS} 是**构建期**由 {@code _census/gen_cc_material_table.js}
 *           从本模组换算配方 {@code gtlmodular:nanochip_conversion}（真件 → CC 的正向边）现推出来的常量表
 *           —— <b>生成物不算手写表</b>，且自检会把"生成物 == 人读来源 {@code _census/cc_material_table.md}
 *           == 配方推导"逐条互校。
 *           <p>材料名的**全集** = 各 jar 的离线材料表 <b>∪</b> <b>本模组自己注册的材料</b>
 *           （后者由 {@code _census/cc_material_rule.js} 从本模组源码的四种调用形态派生：
 *           {@code newMaterial(…)} / {@code new Material.Builder(GTCEu.id(…))} /
 *           {@code upgradeExistingMaterial(…)} / {@code upgradeSuperconductorBoltForm(…)}）。
 *           少算后者会把 {@code chromatic_glass} / {@code six_phased_copper} / {@code universium} /
 *           {@code hexanite} 这 4 味（本模组注册、离线 jar 表里没有）误判成"查不到"。</li>
 *     </ol></li>
 *   <li><b>只认 {@code mat:} 前缀 + 生成表</b>：{@code item:...} 的 40 件是 {@code gtceuItem}（layer0 就是 GTCEu
 *       **本色**物品贴图，如 {@code cc_adv_smd_resistor.json} → {@code gtceu:item/advanced_smd_resistor}），
 *       再乘材料色会把它染坏；{@code itemns:...}（106 件 kubejs 族贴图）与两张表都没有的件同理一律跳过
 *       ⇒ <b>本类只可能给"灰度材料母版"那类上色</b>。本类里**不做任何物品 id 字符串解析**
 *       （材料名只来自上面两个数据源）。</li>
 *   <li><b>解析不出来就跳过（不返回白/黑）</b>：材料查不到或物品还没注册 ⇒ 不登记，并打一条 WARN
 *       把件名列全（便于收口审计）。<b>宁可不动，也不瞎染</b>。</li>
 *   <li><b>只染 {@code layer0}</b>：{@code tintIndex != 0} 一律返回 {@code -1}（= 白色乘数 = 原样）。
 *       GTCEu 的 {@code TagPrefixItem.tintColor()} 是 {@code material.getLayerARGB(tintIndex)}（逐层，
 *       layer1 用材料的"次色"）；<b>我们故意不那样做</b> —— 我们的 layer1 是**全模组共用的
 *       {@code circuit_component_overlay} 印章贴图**，若按材料次色染，同一枚印章会随件变色，
 *       而另外 250 件（不上色）的同一枚印章仍是本色 ⇒ 视觉不一致。故 layer1 恒定不染。</li>
 * </ol>
 *
 * <p><b>离线已核（本机只读，见 {@code _census/check_item_colors.js}）</b>：
 * 116 件灰度母版件 = <b>86 件</b>注册行 {@code mat:} 真件（42 种材料）
 * ＋ <b>30 件</b>由**配方图**推得（{@link CcMaterialByItem}）
 * ⇒ <b>116 / 116 全部可上色</b>（余 0 件；材料全集 = 各 jar 1469 ∪ 本模组注册 23 = 1479）。
 * 详见 {@code _census/_log_append_O92.md}（接线）、{@code O93.md}（配方图回退）、{@code O94.md}（并入本模组注册材料）。
 */
@Mod.EventBusSubscriber(modid = GTLModular.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CcItemColorSetup {

    /** 真件引用里"这是一个 GTCEu 材料件"的前缀（见 {@link NanochipCircuitComponents.Entry#realRef}）。 */
    private static final String MATERIAL_REF_PREFIX = "mat:";

    /** 不染色（{@code 0xFFFFFFFF} = 原样乘 1）。 */
    private static final int NO_TINT = -1;

    /** 已解析成功的「本模组物品 → GTCEu 材料」。用 {@link IdentityHashMap}（物品是单例，按引用比最稳）。 */
    private static final Map<Item, Material> MATERIAL_BY_ITEM = new IdentityHashMap<>();

    /** 已解析过的物品 id → 失败原因（只在 WARN 里用）。 */
    private static final List<String> UNRESOLVED = new ArrayList<>();

    /** 幂等开关：颜色事件理论只来一次，但重复调用不应重复解析/重复注册。 */
    private static boolean resolved;

    private CcItemColorSetup() {
    }

    /**
     * 注册物品颜色处理器。只在 {@code mat:} 真件的物品上登记；
     * 一个处理器 + 一张运行期现推出来的 {@code 物品 → 材料} 表（没有手写映射表）。
     */
    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        resolveMaterials();
        if (MATERIAL_BY_ITEM.isEmpty()) {
            return;
        }
        Item[] items = MATERIAL_BY_ITEM.keySet().toArray(new Item[0]);
        event.register(CcItemColorSetup::colorOf, items);
        GTLModular.LOGGER.info("[gtlmodular] CC ItemColor: 已为 {} 件「灰度材料母版」贴图的 CC 件注册物品颜色"
                + "（layer0 = 材料色，layer1 = 原样；其余 CC 件一律不染）。", items.length);
    }

    /**
     * {@code ItemColor}：只染 {@code layer0}。
     *
     * @return {@code layer0} → 该件材料色（{@link Material#getLayerARGB(int)}，与 GTCEu 自己的口径一致）；
     *         其它层 / 查不到材料 → {@code -1}（原样，不染）
     */
    private static int colorOf(ItemStack stack, int tintIndex) {
        if (tintIndex != 0) {
            return NO_TINT;
        }
        Material material = MATERIAL_BY_ITEM.get(stack.getItem());
        return material == null ? NO_TINT : material.getLayerARGB(0);
    }

    /** 现推「物品 → 材料」表（幂等；两条路都取不到就跳过，绝不返回白/黑）。 */
    private static void resolveMaterials() {
        if (resolved) {
            return;
        }
        resolved = true;
        // 必须在静态初始化之后调用：NanochipCircuitComponents 的静态块负责 addRows + 物品注册。
        IMaterialRegistryManager manager = GTCEuAPI.materialManager;
        if (manager == null) {
            GTLModular.LOGGER.warn("[gtlmodular] CC ItemColor: GTCEuAPI.materialManager 还是 null（材料注册表未就绪）"
                    + " ⇒ 本轮不注册任何物品颜色；CC 件维持现状（全白），不会误染。");
            return;
        }
        int byRegistry = 0;
        int byRecipe = 0;
        for (NanochipCircuitComponents.Entry entry : NanochipCircuitComponents.all()) {
            Material material = materialOfEntry(manager, entry);
            if (material == null) {
                continue;   // 两条路都取不到（或本来就不该染）⇒ 不登记
            }
            Item item = entry.item();
            if (item == null) {
                UNRESOLVED.add(entry.itemId + "(物品未注册)");
                continue;
            }
            MATERIAL_BY_ITEM.put(item, material);
            if (entry.realRef != null && entry.realRef.startsWith(MATERIAL_REF_PREFIX)) {
                byRegistry++;
            } else {
                byRecipe++;
            }
        }
        GTLModular.LOGGER.info("[gtlmodular] CC ItemColor: 材料现推完成 —— 注册行 {} 件 + 配方图生成表 {} 件 = {} 件"
                + "（只染 layer0；其余 CC 件一律不碰）。", byRegistry, byRecipe, MATERIAL_BY_ITEM.size());
        if (!UNRESOLVED.isEmpty()) {
            GTLModular.LOGGER.warn("[gtlmodular] CC ItemColor: {} 件**本轮打算染但没染成**（不会返回白/黑，保持原样）：{}",
                    UNRESOLVED.size(), String.join(", ", UNRESOLVED));
        }
    }

    /**
     * 一件 CC 的材料（**两条路，按顺序**）：
     * <ol>
     *   <li><b>注册行</b>：{@code realRef = mat:<材料名>:<前缀>} ⇒ 直接查材料注册表（86 件走这条）；</li>
     *   <li><b>配方图生成表</b>：{@code realRef} 为空时，查**构建期从配方 JSON 现推**的
     *       {@link CcMaterialByItem#MATERIALS}（26 件走这条；该表由 {@code _census/gen_cc_material_table.js}
     *       生成，内容由 {@code _census/check_item_colors.js} 与配方逐条互校）。</li>
     * </ol>
     * 两条都取不到 ⇒ 返回 {@code null}（调用方跳过：**不返回白/黑**）。
     * 注意本方法里**没有任何**对物品 id 的字符串解析 —— 材料名一律来自上面两个数据源，
     * 保证"零手写映射"（负向验证会钉这一点）。
     */
    private static Material materialOfEntry(IMaterialRegistryManager manager, NanochipCircuitComponents.Entry entry) {
        String realRef = entry.realRef;
        if (realRef != null && realRef.startsWith(MATERIAL_REF_PREFIX)) {
            Material byRegistry = materialOf(manager, realRef);
            if (byRegistry == null) {
                UNRESOLVED.add(entry.itemId + "(" + realRef + "：材料查不到)");
            }
            return byRegistry;
        }
        String byRecipeName = CcMaterialByItem.MATERIALS.get(entry.itemId);
        if (byRecipeName == null) {
            return null;    // 两张表都没有它 ⇒ 本轮不打算染（250 件本色贴图 / 4 件材料不在本包）
        }
        Material byRecipe = manager.getMaterial(byRecipeName);
        if (byRecipe == null) {
            UNRESOLVED.add(entry.itemId + "(配方表材料查不到：" + byRecipeName + ")");
        }
        return byRecipe;
    }

    /**
     * {@code mat:<gtceu 材料名>:<tagprefix 键>} → {@link Material}（查不到返回 {@code null}）。
     *
     * <p>只取第一个 {@code ':'} 之前的那段（材料名），不做任何改名/别名猜测；
     * {@code getMaterial(String)} 对**无命名空间**的名字会遍历全部材料注册表
     * （{@code gtceu} / {@code gtladditions} / …），所以 GTL 自注册的材料同样能查到。
     */
    private static Material materialOf(IMaterialRegistryManager manager, String realRef) {
        String body = realRef.substring(MATERIAL_REF_PREFIX.length());
        int separator = body.indexOf(':');
        if (separator <= 0) {
            return null;
        }
        String materialName = body.substring(0, separator);
        return manager.getMaterial(materialName);
    }
}
