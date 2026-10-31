package com.gtlmodular.common.data.print;

import com.gtlmodular.common.data.GTLModularMachines;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * 10 个「一次性模具」物品（{@code gtlmodular:disposable_<tool>_mold}）。
 *
 * <p>贴图直接取自 GTO：{@code assets/gtocore/textures/item/disposable_<tool>_mold.png}
 * （刀/研钵两张按同一风格补画）。这些物品只作为 3D 打印配方里的 <b>notConsumable</b> 模具；
 * 获得方式 = 流体固化机「576mB 钢 + 一把对应工具（forge:tools/<tag>）」浇铸（配方见
 * {@link DisposableToolPrintRecipes}）。
 *
 * <p>注册时机与 {@code GTLModularItems} 相同：必须在 Forge {@code RegisterEvent<Item>} 之前，
 * 由 {@code GTLModular} 构造函数在 {@code GTLModularMachines.initBlocks()} 之后调用 {@link #init()}。
 */
public final class DisposableToolPrintItems {

    /** 工具 → 模具物品。顺序 = {@link DisposableTools} 声明顺序。 */
    public static final Map<DisposableTools, ItemEntry<Item>> MOLDS =
            new EnumMap<>(DisposableTools.class);

    private static boolean initialized;

    private DisposableToolPrintItems() {}

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        for (DisposableTools tool : DisposableTools.values()) {
            MOLDS.put(tool, GTLModularMachines.REGISTRATE
                    .item(tool.moldId(), Item::new)
                    .register());
        }
    }

    /** 某个工具的模具物品栈（未注册时返回空栈，绝不抛异常）。 */
    public static ItemStack mold(DisposableTools tool) {
        ItemEntry<Item> entry = MOLDS.get(tool);
        return entry == null ? ItemStack.EMPTY : new ItemStack(entry.get());
    }

    /** JEI 分类图标：撬棍模具（最便宜的一把，不会引用空气）。 */
    public static ItemStack iconStack() {
        return mold(DisposableTools.CROWBAR);
    }
}
