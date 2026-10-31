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

/**
 * <b>P2：「样板仓归档」工具物品</b>（{@code gtlmodular:pattern_archive}）——
 * 惰性猪咪样板总成批量迁移（含 26 台现役机）的载体。
 *
 * <h3>为什么必须自己做（不沿用 gtlcore 的复制 / 剪切工具）</h3>
 * 取证结论（反汇编存盘 {@code _javap_p2_api.txt}【1】）：
 * gtlcore 的 {@code MEPatternBufferCopyBehavior} / {@code MEPatternBufferCutBehavior} 字节码里都是
 * {@code instanceof MetaMachineBlockEntity → checkcast} → <b>{@code instanceof MEPatternBufferPartMachine}</b>
 * → {@code checkcast} → {@code invokevirtual copyFromTag/copyToTag/pasteFromTag/cutToTag}，
 * 判定落在<b>具体类</b>上；而惰性机继承的是<b>抽象基类</b> {@code MEPatternBufferPartMachineBase}
 * ⇒ 那两个 behavior 直接跳过我们（拿 gtlcore 工具右键惰性机毫无反应）。
 * 且 gtlcore 那四个方法<b>只在具体类上</b>（基类一个都没有）⇒ 必须自己做一套。
 *
 * <h3>操作口径（与 gtlcore 两个工具的"用户操作口径"对齐；判定改成按基类 / 接口）</h3>
 * <table border="1">
 *   <caption>本物品的操作</caption>
 *   <tr><th>操作</th><th>行为</th></tr>
 *   <tr><td>右键惰性猪咪样板总成</td><td><b>空工具 ⇒ 复制</b>（把该机全仓存进工具）；<br>
 *       <b>已有档案 ⇒ 粘贴</b>（把档案写进该机；容量装不下<b>整体拒绝</b>，机器一个字节都不动）</td></tr>
 *   <tr><td>潜行右键惰性猪咪样板总成</td><td><b>剪切</b>：先复制进工具、<b>写成功之后</b>才清空源机</td></tr>
 *   <tr><td>右键其它方块</td><td><b>一律不拦</b>（{@code PASS} ⇒ 原版与 GTCEu 的行为照常，机器照常开界面）</td></tr>
 *   <tr><td>右键空气</td><td>手里已有档案 ⇒ 聊天里报一下张数（免得"右键没反应"）；否则什么都不做</td></tr>
 *   <tr><td>潜行右键空气</td><td><b>清空工具里的档案</b></td></tr>
 *   <tr><td>界面「归档」页</td><td>复制 / 剪切 / 粘贴 / 清空 —— 与上面同一套引擎（{@link LazyPatternArchiveApi}），
 *       另有"是否含设置 / 是否含名字"两个勾选框与只读张数展示</td></tr>
 * </table>
 *
 * <h3>为什么只按"基类"判定</h3>
 * {@link LazyPatternArchiveApi#supports(Object)} 只认 gtlcore 的抽象基类 {@code MEPatternBufferPartMachineBase}
 * （惰性机是它的子类）⇒ 我们自己的机器（含将来的惰性档位）天然都在内。
 * gtlcore 的具体类 {@code MEPatternBufferPartMachine} 也在其内，但编排层（{@link LazyPatternArchiveApi}）
 * <b>明确只处理惰性机</b>：那台机器有它自己的工具与格式，混用只会写出"两边都认一半"的档案
 * （见 {@code LazyPatternArchiveCodec} 的版本兼容说明）。
 *
 * <h3>为什么左键不做专门的"清空/拦截"</h3>
 * gtlcore 那两个 behavior 走的是 GTCEu 的 {@code IItemBehavior}（左键 + 右键两套回调）。
 * 本模组<b>从不引入那个接口</b>（没有既有用例可对照签名，猜签名的代价是编译不过），
 * 因此这里只用<b>原版 {@link Item} 的 {@code useOn} / {@code use}</b> 承载右键语义：
 * 清空落在"潜行右键空气"上，而原版左键（挖 / 打）<b>一律照旧</b>，我们不做拦截。
 * 面板「归档」页提供 复制 / 剪切 / 粘贴 / 清空 四个按钮，功能一个不缺。
 */
public final class PatternArchiveItem extends Item {

    /** 物品 id 的路径部分。 */
    public static final String ITEM_ID = "pattern_archive";

    /**
     * 注册入口。★ <b>时序要求与 NAC 那些自注册物品完全一致</b>：
     * 必须在 {@code GTLModularMachines.initBlocks()}（= Forge 的 {@code RegisterEvent<Item>} 之前）里被调，
     * 否则条目永远进不了物品注册表（见 {@code GTLModularMachines.initBlocks()} 的注释）。
     */
    /** 剪切工具的 id 路径部分。 */
    public static final String CUT_ITEM_ID = "pattern_archive_cut";

    public static void register() {
        // Registrate 条目在类初始化时建，这里只是"触发器"（与 NanochipMissingItems.init() 同款做法）。
        GTLModular.LOGGER.debug("[gtlmodular] 样板仓工具条目已就绪: {} / {}", ITEM_ID, CUT_ITEM_ID);
    }

    /** 复制工具（右键机器：空工具 ⇒ 复制整仓；手里有档案 ⇒ 粘贴；潜行右键 ⇒ 也当剪切用）。 */
    public static final com.tterrag.registrate.util.entry.ItemEntry<PatternArchiveItem> ITEM =
            GTLModularMachines.REGISTRATE.item(ITEM_ID,
                            properties -> new PatternArchiveItem(properties.stacksTo(1), false))
                    .lang("Pattern Buffer Copy Tool")
                    .register();

    /** 剪切工具（右键机器：空工具 ⇒ **剪切** 整仓进工具并清空源机；手里有档案 ⇒ 粘贴）。 */
    public static final com.tterrag.registrate.util.entry.ItemEntry<PatternArchiveItem> CUT_ITEM =
            GTLModularMachines.REGISTRATE.item(CUT_ITEM_ID,
                            properties -> new PatternArchiveItem(properties.stacksTo(1), true))
                    .lang("Pattern Buffer Cut Tool")
                    .register();

    /** true = 这把是剪切工具（右键即剪切）；false = 复制工具（右键即复制，潜行右键才是剪切）。 */
    private final boolean cutMode;

    public PatternArchiveItem(Properties properties, boolean cutMode) {
        super(properties);
        this.cutMode = cutMode;
    }

    /** 兼容旧签名（默认 = 复制工具）。 */
    public PatternArchiveItem(Properties properties) {
        this(properties, false);
    }

    /**
     * 右键方块。
     *
     * <p>顺序（每一步都是"别抢别人活"）：
     * <ol>
     *   <li>目标不是样板总成家族 ⇒ {@code PASS}（把 GTCEu 的"右键开界面"完整让出去）；</li>
     *   <li>潜行 ⇒ 剪切（先写成功再清源）；</li>
     *   <li>工具空 ⇒ 复制；工具非空 ⇒ 粘贴（失败时聊天告知，机器不动）；</li>
     *   <li>成功一律 {@link InteractionResult#sidedSuccess(boolean)}（服务端权威）：
     *       <b>故意不让 GTCEu 再开一次界面</b> —— 玩家刚做完复制/粘贴，弹出机器界面反而挡路。
     *       想看界面就空手右键（空手时我们直接 {@code PASS}）。</li>
     * </ol>
     */
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
            // 客户端只表达"这个动作我们接了"；真正的数据改动全在服务端（与 GTCEu 的 sidedSuccess 同套路）。
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        try {
            boolean sneaking = player.isShiftKeyDown();
            LazyPatternArchiveApi.StampResult result;
            String action;
            // ★ 顺序很重要：**手里已经有档案 ⇒ 右键一律是粘贴**。
            //   （否则"潜行右键 = 剪切"会在你取完档之后又把档案覆盖掉）
            if (LazyPatternArchiveApi.hasArchive(stack)) {
                action = "粘贴";
                result = LazyPatternArchiveApi.pasteInto(machine, stack, player);
            } else if (cutMode) {
                // ★ 2026-09-27 队长报"复制工具把仓剪空了" ⇒ **去掉"复制工具潜行右键=剪切"这条捷径**：
                //   现在有独立的剪切工具，"潜行右键=剪切"只会被误触、把整仓剪走（数据安全事故）。
                //   ⇒ 复制工具无论怎么右键都只会复制/粘贴；要剪切请用剪切工具（或面板上的「剪切」按钮）。
                action = "剪切(剪切工具)";
                result = LazyPatternArchiveApi.cutFrom(machine, stack, player);
            } else {
                action = "复制";
                result = LazyPatternArchiveApi.copyFrom(machine, stack, player);
            }
            // ★ 诊断（2026-09-27 队长报"复制与剪切效果一样"）：一次复现即可定案
            //   看这一行就知道：当时拿的是哪把、有没有潜行、手里有没有档、实际走了哪个动作。
            try {
                GTLModular.LOGGER.info("[gtlmodular] 样板仓工具动作 @{} 物品={} 剪切模式={} 潜行={} 手里有档={} ⇒ 实际动作={}",
                        context.getClickedPos(), cutMode ? "剪切工具" : "复制工具", cutMode, sneaking,
                        LazyPatternArchiveApi.hasArchive(stack) || "粘贴".equals(action), action);
            } catch (Throwable ignored) {
                // 诊断绝不影响动作
            }
            if (result != null) {
                result.tell(player);
            }
        } catch (Throwable failure) {
            // 玩家交互路径绝不许外抛
            GTLModular.LOGGER.warn("[gtlmodular] 样板仓归档：右键处理异常（已忽略）pos={} 原因={}",
                    context.getClickedPos(), failure.toString());
        }
        return InteractionResult.sidedSuccess(false);
    }

    /**
     * 右键空气 / 右键其它东西：一律不拦，只在"手里已经有档案"时提示一下张数。
     * <p>潜行右键空气 = 清空档案（设计稿：「右键空气 / 潜行右键 = 清空」）。
     */
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

    /**
     * tooltip：复用 gtlcore <b>已经存在</b>的两个语言键
     * （{@code tooltip.gtlcore.pattern_buffer_cut_patterns} = "已剪切样板：%s 个"、
     * {@code tooltip.gtlcore.pattern_buffer_cut_source} = "来源样板总成：%s"）——
     * 语义逐字相同，所以<b>不必新增</b>这几个键；我们只加自己那句"怎么用"。
     */
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
