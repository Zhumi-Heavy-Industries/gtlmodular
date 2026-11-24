package com.gtlmodular.common.data;

import com.gtlmodular.common.item.WhaleTerminalItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;

/**
 * 本模组的独立物品（非机器）注册。
 *
 * <p>注册时机与 {@link GTLModularMachines#initBlocks()} 相同：必须在 Forge 的
 * {@code RegisterEvent<Item>} 之前调用，否则条目进不了注册表。
 */
public final class GTLModularItems {

    /** 哦鲸鲸终端（手持多方块施工/拆解终端，移植自 GT-Shanhai 的「山海终极终端」）。 */
    public static ItemEntry<WhaleTerminalItem> WHALE_TERMINAL;

    private GTLModularItems() {}

    public static void init() {
        WHALE_TERMINAL = GTLModularMachines.REGISTRATE
                .item("whale_terminal", WhaleTerminalItem::new)
                .properties(properties -> properties.stacksTo(1))
                // 模型/贴图用静态资源（assets/gtlmodular/models/item/whale_terminal.json），不走数据生成
                .model(NonNullBiConsumer.noop())
                .register();
    }
}
