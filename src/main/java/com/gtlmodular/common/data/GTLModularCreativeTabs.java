package com.gtlmodular.common.data;

import com.gtlmodular.GTLModular;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static com.gtlmodular.common.data.GTLModularMachines.REGISTRATE;

/**
 * 创造模式标签页。
 *
 * <p>旧标签页（{@code itemGroup.gtlmodular.creative_tab}）从「GTL·熔岩模块化机组」改名为
 * 「更多实用设备」（机器/方块用）；本类别新增「更多实用设备-工具」页放终端类工具。
 *
 * <p>注册方式照 {@link GTLModularMachines} 里 CREATIVE_TAB 的同款写法：走 GTRegistrate 的
 * {@code defaultCreativeTab(...)}，条目会随 {@code registerEventListeners} 一起落库。
 */
public final class GTLModularCreativeTabs {

    private GTLModularCreativeTabs() {}

    /** 工具页：目前只收「哦鲸鲸终端」；后续工具物品直接加进 displayItems 即可。 */
    public static final RegistryEntry<CreativeModeTab> TOOLS =
            REGISTRATE.defaultCreativeTab(REGISTRATE, "tools", builder -> {
                builder.title(Component.translatable("itemGroup.gtlmodular.tools"));
                builder.icon(() -> GTLModularItems.WHALE_TERMINAL == null
                        ? new ItemStack(Items.LAPIS_LAZULI)
                        : GTLModularItems.WHALE_TERMINAL.asStack());
                builder.displayItems((params, output) -> {
                    if (GTLModularItems.WHALE_TERMINAL != null) {
                        output.accept(GTLModularItems.WHALE_TERMINAL.get());
                    }
                });
                builder.build();
            }).register();

    /** 触发类初始化（标签页条目在静态块里建好）。 */
    public static void init() {
        GTLModular.LOGGER.debug("[gtlmodular] 工具创造标签页已就绪: {}", TOOLS.getId());
    }
}
