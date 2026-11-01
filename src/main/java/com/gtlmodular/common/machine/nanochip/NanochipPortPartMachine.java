package com.gtlmodular.common.machine.nanochip;

import appeng.api.crafting.PatternDetailsHelper;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * <b>NAC 总成（收件 / 发件）的共用骨架</b> —— NAC 重做 P1 的「部件骨架」。
 *
 * <p>这两种部件是 NAC 重做方案里用来<b>替代 NH 真空端口（VCI/VCO）与真空管道</b>的新体系
 * （规划书 {@code NAC_REWORK_PLAN.md} §4）：不再走「颜色相同的端口 + 看得见的管道」这条物理连接，
 * 而是「<b>同频 + 同色</b>即同一条线」的<b>无线</b>路由。P1 只落骨架，路由/账目/主机节拍留给 P2/P3：
 * <ol>
 *   <li>两个部件（收件总成 / 发件总成）的机器注册、方块实体、物品；</li>
 *   <li>{@code frequency}（1~9999，小 UI 可调、闪存可复制粘贴）；</li>
 *   <li>染色（原版喷漆罐 / 螺丝刀循环 / 界面色板；<b>未染色 = 断线</b>，与 NH 同口径）；</li>
 *   <li>样板槽（只收 AE 编码样板，样板由玩家写入）；</li>
 *   <li>NBT 存读 + 客户端同步。</li>
 * </ol>
 *
 * <h2>为什么继承 {@link ItemBusPartMachine}（而不是 {@code MetaMachine}）</h2>
 * 这两个部件在结构里就是「仓位」：收件总成要从线路取料喂给所在机器（物品输入语义），
 * 发件总成要把机器产物写回线路（物品输出语义）。继承 GTCEu 的物品总线仓可以直接拿到：
 * 结构成型/失形的 {@code IMultiPart} 语义、16 格物品缓冲、fancy UI 与配置页、
 * 能力面（管道/AE/漏斗）与配方处理链 —— 这些都不需要自己重写。
 * 唯一的差别是<b>不启用自动 IO</b>（见 {@link #autoIO()}）：路由由 P2 的总成服务接管，
 * P1 阶段这两个方块不搬运任何东西（规划书里 P1 的回退口径就是「不启用」）。
 *
 * <h2>字段口径</h2>
 * <ul>
 *   <li>{@code frequency}：{@code @DescSynced}（客户端要显示/编辑），落盘走本类的
 *       {@link #saveCustomPersistedData}；范围 1~9999，读档时夹区间；</li>
 *   <li>{@code colorIndex}：{@code -1 = 未染色}（{@link NanochipColor#INDEX_UNPAINTED}），
 *       与 {@link NanochipVacuumHatchMachine} 完全同一套语义与存档键风格；</li>
 *   <li>{@code patternInventory}：{@link NotifiableItemStackHandler}（{@code IO.NONE}）。
 *       它是 GTCEu 的 MachineTrait：构造即挂到 {@code traits} 上 ⇒
 *       存档由 {@code MetaMachine.saveCustomPersistedData} 逐 trait 处理、
 *       客户端同步由 trait 自己的 {@code @DescSynced storage} 处理，零额外代码。
 *       {@code IO.NONE} 表示<b>不参与配方</b>——样板只是数据，不该被配方逻辑当输入吃掉。</li>
 * </ul>
 *
 * <h2>与 gtlcore 的 ME 样板总成的关系</h2>
 * <b>不共用</b>（规划书 §10.1）：这是 NAC 自己的新体系，不认识 AE 网络、不注册
 * {@code ICraftingProvider}、不解析样板。P1 的样板槽只是一个「能存住/同步/落盘」的容器，
 * 解析与派料在 P2/P4。
 */
public abstract class NanochipPortPartMachine extends ItemBusPartMachine implements IInteractedMachine {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(NanochipPortPartMachine.class, ItemBusPartMachine.MANAGED_FIELD_HOLDER);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /** 物品缓冲格数：与真空仓一致，16 = 原版 4×4。 */
    public static final int SLOTS = 16;

    /** 样板槽数：3×3。P1 只做容器，解析在 P2。 */
    public static final int PATTERN_SLOTS = 9;

    /** 频率范围（规划书 §4.1：int 1~9999）。 */
    public static final int MIN_FREQUENCY = 1;
    public static final int MAX_FREQUENCY = 9999;

    /** 前脸 overlay 的 tintindex（与 {@link NanochipVacuumHatchMachine#TINT_OVERLAY} 同值）。 */
    public static final int TINT_OVERLAY = 2;

    /** 存档键（与真空仓一样用 {@code GTLModular*} 前缀，避免与其它模组撞键）。 */
    private static final String NBT_FREQUENCY = "GTLModularPortFrequency";
    private static final String NBT_COLOR = "GTLModularPortColor";
    private static final String NBT_PATTERNS = "patternInventory";

    /** 闪存在物品 NBT 上存频道的键。公开出来给 {@code NanochipPortFlashHandler} 的潜行复制分支用。 */
    public static final String STICK_FREQUENCY = "GTLModularPortFrequency";

    /**
     * 频率（1~9999）。同频 = 同一台 NAC 的部件；同频<b>且</b>同色 = 同一条线。
     *
     * <p>{@code @DescSynced} 让客户端能显示与编辑；落盘由 {@link #saveCustomPersistedData} 负责。
     */
    @DescSynced
    private int frequency = MIN_FREQUENCY;

    /**
     * 颜色序号；{@code -1 = 未染色}（= NH 的 {@code colorization == -1}）。
     *
     * <p><b>未染色 = 断线</b>：P2 的路由会直接跳过它；界面上也明确提示。
     */
    @DescSynced
    private int colorIndex = NanochipColor.INDEX_UNPAINTED;

    /**
     * 样板槽：由玩家写入 AE 编码样板。
     *
     * <p>字段名保持 {@code patternInventory}（与项目里其它样板总成同名，便于后续工具/脚本按名识别）。
     */
    private final NotifiableItemStackHandler patternInventory;

    /** 样板解析结果缓存：lineAccepts/lineSends 用（空 = 没放样板 ⇒ 全放行，兼容旧行为）。 */
    private java.util.Set<net.minecraft.world.item.Item> patternInputItems = java.util.Set.of();
    private java.util.Set<net.minecraft.world.item.Item> patternOutputItems = java.util.Set.of();
    private int patternSignature = Integer.MIN_VALUE;

    // ---------- P2：路由与账目 ----------

    /** 三笔账之一：本机累计<b>发出</b>的数量（收件总成恒为 0）。 */
    @Persisted
    @DescSynced
    private long totalPushed;

    /** 三笔账之二：本机累计<b>收到</b>的数量（发件总成恒为 0）。 */
    @Persisted
    @DescSynced
    private long totalReceived;

    /** 三笔账之三：发件总成缓冲里<b>推不出去</b>的余量（每 tick 末从 16 格缓冲重算）。 */
    @DescSynced
    private long pendingItems;

    /** 本机所在线当前有几个发件总成（0 = 未染色/无线；>1 = 冲突，整线罢工）。 */
    @DescSynced
    private int lineSenderCount;

    /**
     * 本机是否<b>已被收进一台成形的多方块</b>（客户端渲染外壳两态用）。
     *
     * <p>与 {@link NanochipVacuumHatchMachine} 完全同一套口径：未并入 ⇒ GTCEu 的 UIV tier 机壳，
     * 已并入 ⇒ 纳米芯片接口机壳（+ CTM 与周围机壳连成一片）。
     * 客户端不能直接读 {@code isFormed()}（那是服务端字段），所以必须同步过来。
     */
    @DescSynced
    private boolean casingFromStructure;

    private TickableSubscription portTickSub;
    private long lastConflictLogTick = Long.MIN_VALUE;

    protected NanochipPortPartMachine(IMachineBlockEntity holder, int tier, IO io) {
        super(holder, tier, io);
        this.patternInventory = new NotifiableItemStackHandler(this, PATTERN_SLOTS, IO.NONE);
        // 只收 AE 编码样板（处理/合成/切石/锻造模板都算）；与 gtlcore 的「只收处理样板」相比更宽，
        // 因为 NAC 的总成是线路节点，样板种类由后续 P2 的路由再判，不在槽位这一层武断拒绝。
        this.patternInventory.setFilter(PatternDetailsHelper::isEncodedPattern);
    }

    // ---------- P2：生命周期与投递节拍 ----------

    @Override
    public void onLoad() {
        super.onLoad();
        Level level = getLevel();
        if (level != null && !level.isClientSide) {
            // 上线即登记到它现在的线（未染色则不进表）；区块重新加载后也靠这一步自愈。
            NanochipPortNetwork.refresh(this);
            if (portTickSub == null) {
                portTickSub = subscribeServerTick(this::tickPort);
            }
        }
    }

    @Override
    public void onUnload() {
        if (portTickSub != null) {
            portTickSub.unsubscribe();
            portTickSub = null;
        }
        NanochipPortNetwork.unregister(this);
        super.onUnload();
    }

    /** 本机所在的线（维度 + 频率 + 颜色）；未染色或缺 Level ⇒ {@code null}（= 断线）。 */
    public NanochipPortNetwork.ChannelKey channelKey() {
        Level level = getLevel();
        if (level == null) {
            return null;
        }
        if (colorIndex < 0 || colorIndex >= NanochipColor.values().length) {
            return null;
        }
        return new NanochipPortNetwork.ChannelKey(level.dimension(), frequency, colorIndex);
    }

    /**
     * 投递节拍（每 {@link NanochipPortNetwork#TRANSFER_INTERVAL} tick 一次，相位按坐标错开）：
     * <ol>
     *   <li>解析本机所在的线，先看<b>发件唯一性</b>：≥2 个发件 ⇒ 整线罢工（日志限流）；</li>
     *   <li>合法则交给子类钩子 {@link #onLineTick}（发件总成在这里做平摊投递）；</li>
     *   <li>最后重算「挂起」三笔账。</li>
     * </ol>
     */
    private void tickPort() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        // 外壳两态兜底：除了 added/removedFromController，每拍再校一次（与真空仓的 tick 兜底同理由）。
        refreshCasingState();
        // P2 回退开关：关掉路由后连冲突判定与账目重算都停（方块/界面/存档照常）。
        if (!GTLModularConfig.isNanochipPortRoutingEnabled()) {
            return;
        }
        long gameTime = level.getGameTime();
        if (Math.floorMod(gameTime + getPos().asLong(), NanochipPortNetwork.TRANSFER_INTERVAL) != 0) {
            return;
        }
        NanochipPortNetwork.ChannelKey key = channelKey();
        NanochipPortNetwork.Line line = NanochipPortNetwork.resolve(key);
        int senders = line.senders().size();
        if (senders != lineSenderCount) {
            lineSenderCount = senders;
        }
        if (line.conflict()) {
            if (gameTime - lastConflictLogTick >= NanochipPortNetwork.CONFLICT_LOG_INTERVAL) {
                lastConflictLogTick = gameTime;
                GTLModular.LOGGER.warn(
                        "[gtlmodular] NAC 线路冲突：{} 上有 {} 个发件总成（一条线只允许 1 个），整线罢工。"
                                + "涉及方块位置 {}",
                        key, senders, getPos());
            }
            refreshPending();
            return;
        }
        onLineTick(line);
        refreshPending();
    }

    /** 子类钩子：发件总成覆写它做平摊投递；收件总成不需要（它是被投递方）。 */
    protected void onLineTick(NanochipPortNetwork.Line line) {
    }

    // ---------- 外壳两态（与真空仓同口径） ----------

    /** 本机是否"已被收进成形的多方块"（客户端读这个同步字段，不要读服务端的 {@code isFormed()}）。 */
    public boolean isCasingFromStructure() {
        return casingFromStructure;
    }

    /** 服务端刷新"已并入"态；只在变化时写字段（{@code @DescSynced} 无脑写会打满同步通道）。 */
    private void refreshCasingState() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        boolean merged = isFormed();
        if (merged != casingFromStructure) {
            casingFromStructure = merged;
        }
    }

    @Override
    public void addedToController(com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController controller) {
        super.addedToController(controller);
        refreshCasingState();
    }

    @Override
    public void removedFromController(com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController controller) {
        super.removedFromController(controller);
        refreshCasingState();
    }

    /** 把「挂起」重算成发件总成 16 格缓冲里的实际余量（收件总成恒 0）。 */
    private void refreshPending() {
        long total = 0;
        if (io == IO.OUT) {
            NotifiableItemStackHandler inventory = getInventory();
            if (inventory != null) {
                for (int i = 0; i < inventory.getSlots(); i++) {
                    total += inventory.getStackInSlot(i).getCount();
                }
            }
        }
        if (total != pendingItems) {
            pendingItems = total;
        }
    }

    // ---------- P2：三笔账 ----------

    public long totalPushed() {
        return totalPushed;
    }

    public long totalReceived() {
        return totalReceived;
    }

    public long pendingItems() {
        return pendingItems;
    }

    /** 本机所在线当前有几个发件总成（>1 = 冲突）。 */
    public int lineSenderCount() {
        return lineSenderCount;
    }

    public boolean lineConflict() {
        return lineSenderCount > 1;
    }

    /** 发件总成每成功推出一件就记一笔。 */
    protected void accountPushed(int amount) {
        if (amount > 0) {
            totalPushed += amount;
        }
    }

    /** 收件总成每实际收下一件就记一笔（由投递方调用）。 */
    public void accountReceived(int amount) {
        if (amount > 0) {
            totalReceived += amount;
        }
    }

    // ---------- 物品缓冲 ----------

    @Override
    protected int getInventorySize() {
        return SLOTS;
    }

    /**
     * <b>物品仓对 GT 配方引擎是否可见</b>——按"挂在谁身上"分流（P3 实测修复 + P4 模块接入）。
     *
     * <p><b>主机上的总成：隐藏。</b>总成继承 {@code ItemBusPartMachine}，16 格仓自带
     * {@code IRecipeHandlerTrait}；主机里那套旧引擎（{@code NanochipThreadedRecipeLogic} + 换算配方）
     * 会把它当普通输入/输出总线：从收件总成拉走真物品当配方输入、把产物写进发件总成
     * （0.3.190 实测：251 拍全是收/发 0，旧引擎在跑 conversion / conversion_reverse 来回倒手）。
     * 主机的搬运由 {@code NanochipHostPortBeat} 自己负责，不需要这条链。
     *
     * <p><b>挂在 {@link NanochipModuleMachine} 上的总成：恢复成普通输入/输出总线。</b>
     * 模块的配方逻辑（原版 {@code RecipeLogic}）会直接把收件总成当输入总线吃料、
     * 把产物写进发件总成（发件总成再交给 P2 的线路投递发出去）——这就是 P4「模块接入」。
     *
     * <p>判据用 {@link net.minecraft.world.level.block.entity.BlockEntity} 同款的
     * {@code getControllers()}：多部件机成型后上游会把控制器填进来。
     */
    @Override
    public List<com.gregtechceu.gtceu.api.machine.trait.IRecipeHandlerTrait> getRecipeHandlers() {
        for (com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController controller
                : getControllers()) {
            if (controller instanceof NanochipModuleMachine) {
                return super.getRecipeHandlers();     // P4：模块侧当普通总线用
            }
        }
        return List.of();                             // 主机侧（或独立摆放）：隐藏
    }

    /**
     * 与真空仓同一套放宽：出口（发件总成）对自动化也开放插入，免得 P1 实机验证时连料都塞不进去。
     * 配方语义不变（handlerIO 仍是 {@code io}），只改能力面。
     */
    @Override
    protected NotifiableItemStackHandler createInventory(Object... args) {
        return new NotifiableItemStackHandler(this, SLOTS, io, io == IO.OUT ? IO.BOTH : io);
    }

    /**
     * P1 阶段<b>关闭自动 IO</b>：这两个部件不靠「贴脸推拉」，而是靠 P2 的同频同色路由。
     * 保留空实现（而不是删订阅）是为了让 P2 能按同一入口接回自动搬运。
     */
    @Override
    protected void autoIO() {
        // P2 总成路由接管；P1 不搬任何东西。
    }

    /**
     * 发件总成（{@code IO.OUT}）也允许玩家在界面里<b>手动放料</b>。
     *
     * <p>理由与真空仓那份覆写完全相同：{@code ItemBusPartMachine.createUIWidget()} 用 {@code io.support(IO.IN)}
     * 算槽位的"可放入"开关，{@code io == OUT} 时恒为 false ⇒ 玩家手动也塞不进去。
     * P2 实机验证需要往发件总成里放东西看它推给收件总成，所以这里把"放入"打开；"取出"仍按 {@code io} 走。
     * 收件总成（{@code IO.IN}）本来就是只进不出，不受影响。
     */
    @Override
    public Widget createUIWidget() {
        Widget widget = super.createUIWidget();
        if (io == IO.OUT && widget instanceof WidgetGroup group) {
            for (SlotWidget slot : group.getWidgetsByType(SlotWidget.class)) {
                slot.setCanPutItems(true);
            }
        }
        return widget;
    }

    // ---------- 频率 ----------

    public int frequency() {
        return frequency;
    }

    /**
     * 设置频率；越界自动夹到 1~9999。
     *
     * <p>只在服务端真正改值（客户端编辑走 LDLib 控件的 RPC 回调，服务端再同步回来）。
     */
    public void setFrequency(int value) {
        int clamped = clampFrequency(value);
        if (clamped == frequency) {
            return;
        }
        this.frequency = clamped;
        // 客户端那份只做显示：真正改值在服务端（控件回调/闪存粘贴都在服务端执行），
        // 这里再兜一层，避免客户端硬写字段与同步包打架。
        if (!isRemote()) {
            markDirty();
            // 改频率 = 换线：路由表要立刻把本机从旧线摘掉、登记到新线（P2）。
            NanochipPortNetwork.refresh(this);
        }
    }

    public static int clampFrequency(int value) {
        return Math.max(MIN_FREQUENCY, Math.min(MAX_FREQUENCY, value));
    }

    // ---------- 颜色 ----------

    /** 当前颜色；<b>未染色时返回 {@code null}</b>（= NH 的 {@code colorization == -1}）。 */
    public NanochipColor color() {
        return NanochipColor.fromIndex(colorIndex);
    }

    public boolean isPainted() {
        return colorIndex >= 0;
    }

    /** 设置颜色；{@code color == null} = 清成未染色（复原溶剂喷漆罐的效果）。 */
    public void setColor(NanochipColor color) {
        int next = color == null ? NanochipColor.INDEX_UNPAINTED : color.ordinal();
        if (next != colorIndex) {
            this.colorIndex = next;
            scheduleRenderUpdate();
            // 改色 = 换线（未染色 = 断线）：立刻重登记（客户端调用由 refresh 内部挡掉）。
            NanochipPortNetwork.refresh(this);
        }
        syncPaintingColor();
    }

    /** 只染前脸 overlay；未染色时保持贴图原色。 */
    @Override
    public int tintColor(int index) {
        if (index == TINT_OVERLAY) {
            NanochipColor c = color();
            return c == null ? -1 : c.paintArgb();
        }
        return -1;
    }

    /**
     * 把 GT 的「喷漆色」同步成本机颜色 —— <b>纯显示</b>（Jade/TOP 那行 {@code 颜色: #xxxxxxxx} 读它）。
     * 与真空仓的 {@code syncPaintingColor} 同一套理由与写法。
     */
    private void syncPaintingColor() {
        try {
            NanochipColor c = color();
            super.setPaintingColor(c == null ? 0xFFFFFFFF : c.paintArgb());
        } catch (Throwable ignored) {
            // 老版本 API 差异，忽略
        }
    }

    /** GT 的喷漆入口（API 侧）：映射进本模组的 16 色通道，然后无条件把 GT 字段拉回本机颜色。 */
    @Override
    public void setPaintingColor(int argb) {
        NanochipColor mapped = NanochipColor.ofArgb(argb);
        if (mapped != null) {
            setColor(mapped);
        }
        syncPaintingColor();
    }

    // ---------- 样板槽 ----------

    public NotifiableItemStackHandler getPatternInventory() {
        return patternInventory;
    }

    /**
     * 本总成的<b>样板</b>是否允许"从线路收下"这件物品（P4：让样板槽真正参与物流）。
     *
     * <p>语义：样板槽为空 ⇒ 全放行（保持旧行为，测试/无样板也能用）；
     * 放了样板 ⇒ 只接受出现在该样板<b>输入或输出</b>里的物品。
     */
    public boolean lineAccepts(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        refreshPatternSets();
        if (patternInputItems.isEmpty() && patternOutputItems.isEmpty()) {
            return true;
        }
        return patternInputItems.contains(stack.getItem()) || patternOutputItems.contains(stack.getItem());
    }

    /**
     * 本总成的<b>样板</b>是否允许把这件物品"写进线路"（P4）。
     *
     * <p>样板槽为空 ⇒ 全放行；放了样板 ⇒ 只发送样板<b>输出</b>里有的物品。
     */
    public boolean lineSends(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        refreshPatternSets();
        if (patternInputItems.isEmpty() && patternOutputItems.isEmpty()) {
            return true;
        }
        return patternOutputItems.contains(stack.getItem());
    }

    /** 样板内容变了才重新解码（O(9) 签名，便宜；解码走 AE2 的 PatternDetailsHelper）。 */
    private void refreshPatternSets() {
        Level level = getLevel();
        if (level == null) {
            return;
        }
        int sig = 1;
        for (int i = 0; i < patternInventory.getSlots(); i++) {
            ItemStack stack = patternInventory.getStackInSlot(i);
            sig = 31 * sig + (stack.isEmpty() ? 0 : net.minecraft.world.item.Item.getId(stack.getItem()));
            sig = 31 * sig + stack.getCount();
        }
        if (sig == patternSignature) {
            return;
        }
        patternSignature = sig;
        java.util.Set<net.minecraft.world.item.Item> inputs = new java.util.HashSet<>();
        java.util.Set<net.minecraft.world.item.Item> outputs = new java.util.HashSet<>();
        for (int i = 0; i < patternInventory.getSlots(); i++) {
            ItemStack stack = patternInventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            try {
                appeng.api.crafting.IPatternDetails details =
                        appeng.api.crafting.PatternDetailsHelper.decodePattern(stack, level);
                if (details == null) {
                    continue;
                }
                for (appeng.api.crafting.IPatternDetails.IInput input : details.getInputs()) {
                    appeng.api.stacks.GenericStack[] possible = input == null ? null : input.getPossibleInputs();
                    if (possible == null) {
                        continue;
                    }
                    for (appeng.api.stacks.GenericStack gs : possible) {
                        if (gs != null && gs.what() instanceof appeng.api.stacks.AEItemKey key) {
                            inputs.add(key.getItem());
                        }
                    }
                }
                for (appeng.api.stacks.GenericStack gs : details.getOutputs()) {
                    if (gs != null && gs.what() instanceof appeng.api.stacks.AEItemKey key) {
                        outputs.add(key.getItem());
                    }
                }
            } catch (Throwable ignored) {
                // 解不开的样板当"没放"处理，绝不因为一个样板卡死整条线。
            }
        }
        patternInputItems = inputs;
        patternOutputItems = outputs;
    }

    public boolean hasPatterns() {
        for (int i = 0; i < patternInventory.getSlots(); i++) {
            if (!patternInventory.getStackInSlot(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // ---------- 交互 ----------

    /** 螺丝刀右键：未染色 → 白 → 橙 → … → 黑 循环。 */
    @Override
    public InteractionResult onScrewdriverClick(Player player, InteractionHand hand,
                                                Direction gridSide, BlockHitResult hitResult) {
        Level level = getLevel();
        if (level != null && !level.isClientSide) {
            NanochipColor current = color();
            NanochipColor next = current == null ? NanochipColor.WHITE : current.next();
            setColor(next);
            if (player != null) {
                player.displayClientMessage(
                        Component.literal("[NAC 总成] 颜色 → ")
                                .append(Component.literal(next.nameZh())
                                        .withStyle(style -> style.withColor(next.textColor()))),
                        true);
            }
        }
        return InteractionResult.sidedSuccess(level != null && level.isClientSide);
    }

    /**
     * 闪存（{@code GTItems.TOOL_DATA_STICK}）复制/粘贴频道：
     * <ul>
     *   <li><b>潜行右键</b> = 复制本机频道进闪存 —— 注意这条走
     *       {@code NanochipPortFlashHandler}（Forge {@code RightClickBlock} 事件）：潜行时原版通常
     *       把交互优先交给物品，方块 {@code use} 不一定被调用，所以不能只靠本方法；</li>
     *   <li><b>普通右键</b> = 闪存里存过频道就粘贴；没存过则先复制（两步操作也能用）。</li>
     * </ul>
     * 与 gtlcore「闪存右键存坐标、再右键绑定」的习惯保持一致，只是这里存的是一个整数频道。
     */
    @Override
    public InteractionResult onUse(BlockState state, Level level, BlockPos pos, Player player,
                                   InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(GTItems.TOOL_DATA_STICK.get())) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            // 潜行复制由 Forge 事件兜底（见 NanochipPortFlashHandler）；这里交回去，避免两条路都写一遍。
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        CompoundTag tag = stack.getOrCreateTag();
        if (tag.contains(STICK_FREQUENCY)) {
            setFrequency(tag.getInt(STICK_FREQUENCY));
            player.displayClientMessage(
                    Component.literal("[NAC 总成] 已粘贴频道 → " + frequency), true);
        } else {
            tag.putInt(STICK_FREQUENCY, frequency);
            player.displayClientMessage(
                    Component.literal("[NAC 总成] 已复制频道 → " + frequency), true);
        }
        return InteractionResult.SUCCESS;
    }

    // ---------- 信息浮层 / 提示 ----------

    @Override
    public void onAddFancyInformationTooltip(List<Component> tooltips) {
        super.onAddFancyInformationTooltip(tooltips);
        tooltips.add(Component.translatable("gtlmodular.nanochip.port.frequency.line", frequency)
                .withStyle(ChatFormatting.GRAY));
        NanochipColor c = color();
        if (c == null) {
            tooltips.add(Component.translatable("gtlmodular.nanochip.vacuum.color.unpainted")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltips.add(Component.translatable("gtlmodular.nanochip.vacuum.color.line",
                            Component.literal(c.nameZh()).withStyle(s -> s.withColor(c.textColor())))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (!hasPatterns()) {
            tooltips.add(Component.translatable("gtlmodular.nanochip.port.pattern.empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltips.add(Component.translatable("gtlmodular.nanochip.port.account.line",
                        totalPushed, totalReceived, pendingItems)
                .withStyle(ChatFormatting.GRAY));
        if (lineConflict()) {
            tooltips.add(Component.translatable("gtlmodular.nanochip.port.conflict.line", lineSenderCount)
                    .withStyle(ChatFormatting.RED));
        }
    }

    // ---------- 存档 ----------

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        tag.putInt(NBT_FREQUENCY, frequency);
        tag.putInt(NBT_COLOR, colorIndex);
        // 样板：trait 自己（NotifiableItemStackHandler.storage 带 @Persisted）在正常存档时已会落盘，
        // 这里<b>再显式写一份</b>是为了「掉落也带走」—— 否则拆一次总成就丢光玩家写的样板。
        // 读侧 deserializeNBT 会覆盖 trait 自动还原的那份，两份内容同源、不会打架。
        tag.put(NBT_PATTERNS, patternInventory.storage.serializeNBT());
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        frequency = tag.contains(NBT_FREQUENCY) ? clampFrequency(tag.getInt(NBT_FREQUENCY)) : MIN_FREQUENCY;
        if (tag.contains(NBT_COLOR)) {
            int stored = tag.getInt(NBT_COLOR);
            colorIndex = (stored >= 0 && stored < NanochipColor.values().length)
                    ? stored
                    : NanochipColor.INDEX_UNPAINTED;
        } else {
            colorIndex = NanochipColor.INDEX_UNPAINTED;
        }
        if (tag.contains(NBT_PATTERNS, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            patternInventory.storage.deserializeNBT(tag.getCompound(NBT_PATTERNS));
        }
        syncPaintingColor();
        // 读档可能改了频率/颜色（旧档迁移），登记要跟上；客户端调用被 refresh 内部挡掉。
        NanochipPortNetwork.refresh(this);
    }

    // ---------- 界面 ----------

    @Override
    public void attachConfigurators(ConfiguratorPanel panel) {
        super.attachConfigurators(panel);
        panel.attachConfigurators(new ChannelConfigurator(this));
        panel.attachConfigurators(new PatternConfigurator(this));
        panel.attachConfigurators(new AccountConfigurator(this));
    }

    /** 「NAC 总成频道」页：频率 + 16 色色板 + 操作提示。 */
    static final class ChannelConfigurator implements IFancyConfigurator {
        private final NanochipPortPartMachine machine;

        ChannelConfigurator(NanochipPortPartMachine machine) {
            this.machine = machine;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gtlmodular.nanochip.port.channel.title");
        }

        @Override
        public IGuiTexture getIcon() {
            return new ResourceTexture("gtceu:textures/item/portable_debug_scanner.png");
        }

        @Override
        public List<Component> getTooltips() {
            return List.of(Component.translatable("gtlmodular.nanochip.port.channel.title"));
        }

        @Override
        public Widget createConfigurator() {
            WidgetGroup group = new WidgetGroup(0, 0, 152, 116);
            group.addWidget(new LabelWidget(4, 3,
                    Component.translatable("gtlmodular.nanochip.port.channel.title").getString()));
            group.addWidget(new LabelWidget(4, 20,
                    Component.translatable("gtlmodular.nanochip.port.frequency").getString()));
            // ★ 宽度必须 ≥ 62（本项目血泪：NumberInputWidget 两侧 +/- 按钮吃掉 2×clamp(w/5,15,40)+4，
            //   w<62 时中间文本框只剩个位数像素，看着就是"一片黑、没数字"）。这里给 112，4 位数也够。
            group.addWidget(new IntInputWidget(36, 17, 112, 16,
                    machine::frequency, machine::setFrequency)
                    .setMin(MIN_FREQUENCY).setMax(MAX_FREQUENCY)
                    .setHoverTooltips(Component.translatable("gtlmodular.nanochip.port.frequency.tip")));
            group.addWidget(new LabelWidget(4, 37, () -> {
                NanochipColor cur = machine.color();
                return cur == null
                        ? Component.translatable("gtlmodular.nanochip.vacuum.color.unpainted").getString()
                        : Component.translatable("gtlmodular.nanochip.vacuum.color.current",
                        Component.literal(cur.nameZh()).withStyle(s -> s.withColor(cur.textColor())))
                        .getString();
            }));
            // 8×2 色板（横向排）：4×4 会多占 80px 高度，配置页是向上展开的，越矮越不容易顶出屏幕。
            for (int i = 0; i < 16; i++) {
                NanochipColor option = NanochipColor.fromIndex(i);
                int x = 4 + (i % 8) * 18;
                int y = 50 + (i / 8) * 18;
                ColorRectTexture swatch = new ColorRectTexture();
                swatch.color = 0xFF000000 | option.textColor();
                IGuiTexture texture = option == machine.color()
                        ? new GuiTextureGroup(swatch, new ColorBorderTexture(1, 0xFFFFFFFF))
                        : swatch;
                group.addWidget(new ButtonWidget(x, y, 16, 16, texture,
                        data -> machine.setColor(option))
                        .setHoverTooltips(Component.literal(option.nameZh())));
            }
            group.addWidget(new LabelWidget(4, 88,
                    Component.translatable("gtlmodular.nanochip.port.color.hint").getString()));
            group.addWidget(new LabelWidget(4, 102,
                    Component.translatable("gtlmodular.nanochip.port.frequency.hint").getString()));
            return group;
        }
    }

    /** 「NAC 总成样板」页：3×3 样板槽 + 提示。 */
    static final class PatternConfigurator implements IFancyConfigurator {
        private final NanochipPortPartMachine machine;

        PatternConfigurator(NanochipPortPartMachine machine) {
            this.machine = machine;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gtlmodular.nanochip.port.pattern.title");
        }

        @Override
        public IGuiTexture getIcon() {
            return new ResourceTexture("gtceu:textures/item/portable_debug_scanner.png");
        }

        @Override
        public List<Component> getTooltips() {
            return List.of(Component.translatable("gtlmodular.nanochip.port.pattern.title"));
        }

        @Override
        public Widget createConfigurator() {
            WidgetGroup group = new WidgetGroup(0, 0, 152, 96);
            group.addWidget(new LabelWidget(4, 3,
                    Component.translatable("gtlmodular.nanochip.port.pattern.title").getString()));
            for (int i = 0; i < PATTERN_SLOTS; i++) {
                int x = 4 + (i % 3) * 20;
                int y = 18 + (i / 3) * 20;
                group.addWidget(new SlotWidget(machine.patternInventory.storage, i, x, y, true, true)
                        .setBackgroundTexture(new ColorRectTexture(0xFF2B2B2B))
                        .setHoverTooltips(Component.translatable("gtlmodular.nanochip.port.pattern.slot.tip")));
            }
            group.addWidget(new LabelWidget(4, 80,
                    Component.translatable("gtlmodular.nanochip.port.pattern.hint").getString()));
            return group;
        }
    }

    /**
     * 「NAC 总成账目」页（P2）：三笔账 {@code 已发出 / 已收到 / 挂起} + 线路冲突提示。
     *
     * <p>三个数都是 {@code @DescSynced}，控件用 supplier 每帧读，所以是<b>实时</b>的；
     * 「挂起」= 发件总成 16 格缓冲里推不出去的余量，丢件时它一定会涨，玩家/我们都能从这一页定位。
     */
    static final class AccountConfigurator implements IFancyConfigurator {
        private final NanochipPortPartMachine machine;

        AccountConfigurator(NanochipPortPartMachine machine) {
            this.machine = machine;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gtlmodular.nanochip.port.account.title");
        }

        @Override
        public IGuiTexture getIcon() {
            return new ResourceTexture("gtceu:textures/item/portable_debug_scanner.png");
        }

        @Override
        public List<Component> getTooltips() {
            return List.of(Component.translatable("gtlmodular.nanochip.port.account.title"));
        }

        @Override
        public Widget createConfigurator() {
            WidgetGroup group = new WidgetGroup(0, 0, 152, 76);
            group.addWidget(new LabelWidget(4, 3,
                    Component.translatable("gtlmodular.nanochip.port.account.title").getString()));
            group.addWidget(new LabelWidget(4, 18, () ->
                    Component.translatable("gtlmodular.nanochip.port.account.pushed", machine.totalPushed())
                            .getString()));
            group.addWidget(new LabelWidget(4, 32, () ->
                    Component.translatable("gtlmodular.nanochip.port.account.received", machine.totalReceived())
                            .getString()));
            group.addWidget(new LabelWidget(4, 46, () ->
                    Component.translatable("gtlmodular.nanochip.port.account.pending", machine.pendingItems())
                            .getString()));
            group.addWidget(new LabelWidget(4, 60, () -> machine.lineConflict()
                    ? Component.translatable("gtlmodular.nanochip.port.conflict.line", machine.lineSenderCount())
                    .getString()
                    : ""));
            return group;
        }
    }
}
