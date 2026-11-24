package com.gtlmodular.common.item.terminal;

import com.gtlmodular.common.data.GTLModularItems;

import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

/**
 * GTO 式订单标记：不生成真实样板物品，只作为临时处理样板的隐藏产物。
 *
 * <p>有两种形态：
 * <ul>
 *   <li><b>普通</b>（LEGACY 引擎 / 没有 gtlcore）：鲸鱼终端物品 + 自定义 NBT；</li>
 *   <li><b>GRAPH 兼容</b>：barrier + display 名 + 自定义 NBT。gtlcore 的 GRAPH/LEGACY
 *       CPU 只对「barrier + display」的占位产物收尾；临时样板借用这个形状，让订单在
 *       GRAPH 引擎下也能结束。自定义 NBT 保证我们只认自己的订单，不会误收尾 gtlcore
 *       自己的结构样板订单。</li>
 * </ul>
 *
 * <p>临时样板本身仍然只挂在 {@code CraftingService} 的内存字段上，不注册 AE 样板、
 * 不进入样板表，因此不会和 gtlcore 的结构样板冲突。
 */
public final class TerminalOrderMarker {

    private static final String MARKER = "gtlmodular_order_marker";
    private static final String TARGET = "gtlmodular_order_target";
    private static final String FINGERPRINT = "gtlmodular_order_fp";
    private static final String DIMENSION = "gtlmodular_order_dim";
    /** GRAPH 占位符名字；gtlcore 只检查 display 是否存在，不关心具体内容。 */
    private static final String GRAPH_DISPLAY = "§b哦鲸鲸终端 · 临时结构订单";

    private TerminalOrderMarker() {}

    public static ItemStack create(GlobalPos target, String fingerprint) {
        return create(target, fingerprint, false);
    }

    public static ItemStack create(GlobalPos target, String fingerprint, boolean graphToken) {
        ItemStack stack = graphToken
                ? new ItemStack(Items.BARRIER)
                : new ItemStack(GTLModularItems.WHALE_TERMINAL.get());
        if (graphToken) {
            String suffix = fingerprint.length() > 8 ? fingerprint.substring(0, 8) : fingerprint;
            stack.setHoverName(Component.literal(GRAPH_DISPLAY + " §7#" + suffix));
        }
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(MARKER, true);
        tag.putLong(TARGET, target.pos().asLong());
        tag.putString(FINGERPRINT, fingerprint);
        tag.putString(DIMENSION, target.dimension().location().toString());
        return stack;
    }

    public static boolean isMarker(AEKey key) {
        if (!(key instanceof AEItemKey itemKey)) return false;
        CompoundTag tag = itemKey.toStack().getTag();
        return tag != null && tag.getBoolean(MARKER);
    }

    public static boolean isMarker(GenericStack stack) {
        return stack != null && isMarker(stack.what());
    }

    public static long targetPos(GenericStack stack) {
        if (!isMarker(stack)) return Long.MIN_VALUE;
        return ((AEItemKey) stack.what()).toStack().getOrCreateTag().getLong(TARGET);
    }

    public static String fingerprint(GenericStack stack) {
        if (!isMarker(stack)) return "";
        return ((AEItemKey) stack.what()).toStack().getOrCreateTag().getString(FINGERPRINT);
    }

    public static String dimension(GenericStack stack) {
        if (!isMarker(stack)) return "";
        return ((AEItemKey) stack.what()).toStack().getOrCreateTag().getString(DIMENSION);
    }
}
