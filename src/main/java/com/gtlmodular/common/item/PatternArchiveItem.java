package com.gtlmodular.common.item;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.data.GTLModularMachines;
import com.gtlmodular.common.machine.LazyPatternArchiveApi;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/** 通过归档 API 覆盖惰性机与普通样板仓，避免上游工具的具体类判定排除惰性机。 */
public final class PatternArchiveItem extends Item {

    public static final String ITEM_ID = "pattern_archive";

    /** 必须在物品注册事件前触发类初始化，否则 Registrate 条目无法进入注册表。 */
    public static final String CUT_ITEM_ID = "pattern_archive_cut";

    public static void register() {
        GTLModular.LOGGER.debug("[gtlmodular] 样板仓工具条目已就绪: {} / {}", ITEM_ID, CUT_ITEM_ID);
    }

    public static final com.tterrag.registrate.util.entry.ItemEntry<PatternArchiveItem> ITEM =
            GTLModularMachines.REGISTRATE.item(ITEM_ID,
                            properties -> new PatternArchiveItem(properties.stacksTo(1), false))
                    .lang("Pattern Buffer Copy Tool")
                    .register();

    public static final com.tterrag.registrate.util.entry.ItemEntry<PatternArchiveItem> CUT_ITEM =
            GTLModularMachines.REGISTRATE.item(CUT_ITEM_ID,
                            properties -> new PatternArchiveItem(properties.stacksTo(1), true))
                    .lang("Pattern Buffer Cut Tool")
                    .register();

    private final boolean cutMode;

    public PatternArchiveItem(Properties properties, boolean cutMode) {
        super(properties);
        this.cutMode = cutMode;
    }

    /** 旧构造入口默认创建复制工具，以保持已有调用方的操作语义。 */
    public PatternArchiveItem(Properties properties) {
        this(properties, false);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null || level == null) {
            return InteractionResult.PASS;
        }
        Object machine = LazyPatternArchiveApi.machineAt(level, context.getClickedPos());
        if (!LazyPatternArchiveApi.supports(machine)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            // 客户端只消费交互，库存与档案由服务端权威修改。
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        try {
            boolean sneaking = player.isShiftKeyDown();
            LazyPatternArchiveApi.StampResult result;
            String action;
            // 已有档案先粘贴，避免后续复制或剪切覆盖手里的档案。
            if (LazyPatternArchiveApi.hasArchive(stack)) {
                action = "粘贴";
                result = LazyPatternArchiveApi.pasteInto(machine, stack, player);
            } else if (cutMode) {
                // 复制工具不以潜行触发剪切，避免误触清空整仓。
                action = "剪切(剪切工具)";
                result = LazyPatternArchiveApi.cutFrom(machine, stack, player);
            } else {
                action = "复制";
                result = LazyPatternArchiveApi.copyFrom(machine, stack, player);
            }
            try {
                GTLModular.LOGGER.info("[gtlmodular] 样板仓工具动作 @{} 物品={} 剪切模式={} 潜行={} 手里有档={} ⇒ 实际动作={}",
                        context.getClickedPos(), cutMode ? "剪切工具" : "复制工具", cutMode, sneaking,
                        LazyPatternArchiveApi.hasArchive(stack) || "粘贴".equals(action), action);
            } catch (Throwable ignored) {
                // 诊断失败不能影响已完成的归档动作。
            }
            if (result != null) {
                result.tell(player);
            }
        } catch (Throwable failure) {
            // 归档异常只反馈给玩家，不能中断交互处理。
            GTLModular.LOGGER.warn("[gtlmodular] 样板仓归档：右键处理异常（已忽略）pos={} 原因={}",
                    context.getClickedPos(), failure.toString());
        }
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player == null ? ItemStack.EMPTY : player.getItemInHand(hand);
        if (level == null || player == null) {
            return net.minecraft.world.InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            return net.minecraft.world.InteractionResultHolder.pass(stack);
        }
        try {
            if (player.isShiftKeyDown()) {
                int before = LazyPatternArchiveApi.archivePatternCount(stack);
                LazyPatternArchiveApi.clearArchive(stack);
                LazyPatternArchiveApi.tellKey(player, "gtlmodular.pattern_assembly.archive.cleared", before);
                return net.minecraft.world.InteractionResultHolder.success(stack);
            }
            if (LazyPatternArchiveApi.hasArchive(stack)) {
                LazyPatternArchiveApi.tellKey(player, "gtlmodular.pattern_assembly.archive.held",
                        LazyPatternArchiveApi.archivePatternCount(stack));
            }
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn("[gtlmodular] 样板仓归档：空手右键处理异常（已忽略）原因={}", failure.toString());
        }
        return net.minecraft.world.InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        int count = LazyPatternArchiveApi.archivePatternCount(stack);
        if (count > 0) {
            tooltip.add(Component.translatable("tooltip.gtlcore.pattern_buffer_cut_patterns", count)
                    .withStyle(ChatFormatting.AQUA));
            String source = LazyPatternArchiveApi.archiveSource(stack);
            if (!source.isEmpty()) {
                tooltip.add(Component.translatable("tooltip.gtlcore.pattern_buffer_cut_source", source)
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        tooltip.add(Component.translatable("gtlmodular.pattern_assembly.archive.item.tip")
                .withStyle(ChatFormatting.GRAY));
        if (flag.isAdvanced()) {
            tooltip.add(Component.literal("\u00a78" + LazyPatternArchiveApi.MOD_ARCHIVE_KEY + " v"
                    + LazyPatternArchiveApi.ARCHIVE_VERSION).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
