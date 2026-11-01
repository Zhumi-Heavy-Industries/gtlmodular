package com.gtlmodular.common.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 哦鲸鲸终端物品。
 *
 * <p>★ 这里<b>故意不继承 {@code ComponentItem}、也不在构造期引用 {@code GTItems}</b>：
 * GTCEu 的 {@code GTItems.<clinit>} 会一路触发 {@code TagPrefix → GTToolType → GTSoundEntries}，
 * 而本整合包里 GT 的 {@code gtceu:sound} 注册表在模组构造阶段已经冻结，
 * 早期引用会直接抛 {@code IllegalStateException: registry gtceu:sound has been frozen}。
 * 因此把行为直接委托给 {@link ShanhaiUltimateTerminalBehavior}，走纯原版 {@link Item} 的回调。
 */
public final class WhaleTerminalItem extends Item {

    public WhaleTerminalItem(Properties properties) {
        super(properties);
    }

    private static ShanhaiUltimateTerminalBehavior behavior() {
        return ShanhaiUltimateTerminalBehavior.INSTANCE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return behavior().use(this, level, player, hand);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return behavior().onItemUseFirst(stack, context);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return behavior().useOn(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        behavior().appendHoverText(stack, level, tooltip, flag);
    }
}
