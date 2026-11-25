package com.gtlmodular.common.machine.requester;

import appeng.api.networking.IGridNodeListener.State;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.EnumSet;
import java.util.function.Supplier;

/**
 * <b>ME库存补充仓</b>（独立请求器方块，{@code gtlmodular:requester}）。
 *
 * <h2>它是什么</h2>
 * <p>一块"会自动补货的牌子"：配置若干槽位，每格写"要什么、要多少"，
 * 方块自己盯 AE 网络库存，不够就下单合成，<b>产物直接回网络</b>（不需要收货人）。
 *
 * <h2>槽位：每格物品/流体二选一（与 ME 输入总成同语义）</h2>
 * <p>每格存一个 {@link appeng.api.stacks.AEKey}：拖物品进去就是物品槽，拖流体进去就是流体槽，
 * 互相覆盖（后拖的赢）。一页 {@value #ROWS} 行 × {@value #COLUMNS} 列 = {@value #PAGE_SIZE} 格，
 * 共 {@value #PAGES} 页 = {@value #SLOT_COUNT} 格，带翻页条。
 *
 * <h2>为什么不用 GTCEu 的 AE 配置槽控件</h2>
 * <p>{@code AEItemConfigWidget}/{@code AEFluidConfigWidget} 把物品和流体拆成两个固定数组，
 * 一个下标只能是一种类型（GTCEu 的 {@code ExportOnlyAEItemList} 与
 * {@code ExportOnlyAEFluidList} 是两个独立表）；而需求是"每格两用"。
 * 所以这里自绘槽位（{@link RequesterSlotWidget}），自己实现 JEI 拖拽
 * （{@code IGhostItemTarget} + {@code IGhostFluidTarget}）。
 *
 * <h2>批量 / 目标量怎么填</h2>
 * <p>点格子 → 那一格旁边弹出「数量 / 并发」弹层（{@link RequesterAmountBox}）；
 * 滚轮 = 目标量 ±1，Ctrl+滚轮 = 翻倍/减半。整套交互照 ME 输入总成的
 * {@code AEDualConfigWidget#enableAmount} 模式自绘（那个控件要求 AE2 的
 * {@code ExportOnlyAEItemList}，塞不进本机的 {@link RequesterSlots}）。
 *
 * <h2>产物去向（阶段 0 源码验证结论，2026-11-22）</h2>
 * <p>{@code submitJob(plan, null, ...)} ⇒ standalone 链接 ⇒ {@code CraftingService.addLink} 跳过
 * ⇒ {@code CraftingLink.insert} 返回 0 ⇒ {@code NetworkStorage.insert} 继续往后续存储投递
 * ⇒ 产物落到普通 ME 存储。与终端下单同一路径，<b>产物确实回网</b>。
 *
 * <h2>无线接入（无线舱室）</h2>
 * <p>本方块<b>直接支持 gtlcore 的「无线 ME 网络」</b>，不用接线缆。命中其
 * {@code WirelessAeNetworkRuntime} 的两组识别条件（按 1.2.3.3 字节码逐条核对）：
 * <ol>
 *   <li>{@code hasMachinePartMarker}：继承 {@code MetaMachine}；
 *       {@code hasGridNodeAccess}：提供 {@code getMainNode()}（同时实现
 *       {@link IGridConnectedMachine}，它本身是 {@code IInWorldGridNodeHost});</li>
 *   <li><b>方块 id 必须以 {@code me_} 开头</b> —— {@code isCompatibleExternalMeLikeId}
 *       要求 {@code path.startsWith("me_")}。所以本方块 id 是
 *       {@code gtlmodular:me_requester}，<b>不能</b>叫 {@code requester}。</li>
 * </ol>
 * 满足这两条后，玩家用无线网络核心（{@code gtlcore:wireless_network_core}）绑定本方块，
 * 它就通过 gtlcore 的虚拟连接进主网，行为与 ME 输入总成完全一致。
 *
 * <p>另外本方块<b>六面都可接线缆</b>（ME 仓默认只开正面），接电/接网都方便。
 */
public class RequesterMachine extends MetaMachine
        implements IFancyUIMachine, IGridConnectedMachine, IMachineLife {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(RequesterMachine.class, MetaMachine.MANAGED_FIELD_HOLDER);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    // ------------------------------------------------------------------
    // 槽位与状态
    // ------------------------------------------------------------------

    /** 每页行数（照抄 ME 输入总成：2 行双层格）。 */
    public static final int ROWS = 2;
    /** 每页列数（一行 8 格，与 GTCEu 配置槽同宽）。 */
    public static final int COLUMNS = 8;
    /** 每页格数。 */
    public static final int PAGE_SIZE = ROWS * COLUMNS;
    /** 页数。 */
    public static final int PAGES = 4;
    /** 槽位总数。 */
    public static final int SLOT_COUNT = PAGE_SIZE * PAGES;

    /** 单格控件尺寸：上=配置、下=库存，与 ME 输入总成相同的 18×36。 */
    public static final int SLOT_SIZE = 18;
    /** 单格控件总高（配置 18 + 库存 18）。 */
    public static final int SLOT_HEIGHT = SLOT_SIZE * 2;

    /** 网格左上角（页面坐标，与参照控件同为 x=3,y=10）。 */
    public static final int GRID_X = 3;
    public static final int GRID_Y = 10;

    /** 主界面宽（= AEDualConfigWidget 的 144 + 左右各 3）。 */
    public static final int GUI_WIDTH = 150;

    /**
     * 翻页条 y：网格底 + 3。
     *
     * <p>= {@code 10 + 74 + 3 = 87}；参照控件里是 {@code navigationY = rows*38+1 = 77}
     * （相对网格原点），换算到页面坐标同样是「末行底 + 3」。
     */
    public static final int PAGE_BAR_Y = GRID_Y + RequesterSlotGrid.height() + 3;

    /** 底部阻塞提示 y。 */
    public static final int HINT_Y = 108;

    /**
     * 主界面高：翻页条 87+18 + 提示行 + 余量。
     *
     * <p>数量 / 并发弹层是<b>页内控件</b>（{@link RequesterAmountBox}）：点格子后贴到那一格旁边、
     * 夹在网格范围内，所以不占主界面高度，也不会跑到窗口外去。
     */
    public static final int GUI_HEIGHT = 122;

    /** 状态：空槽或已充足。 */
    public static final char STATUS_IDLE = '0';
    /** 状态：已下单、产物在途。 */
    public static final char STATUS_IN_FLIGHT = '1';
    /** 状态：正在计算方案 / 等待提交。 */
    public static final char STATUS_ORDERING = '2';
    /** 状态：已失败，冷却中。 */
    public static final char STATUS_BLOCKED = '3';
    /** 状态：没有合成方式 / 缺口补不上。 */
    public static final char STATUS_IMPOSSIBLE = '4';
    /** 状态：网络离线。 */
    public static final char STATUS_OFFLINE = '5';

    private static final String TAG_PAGE = "Page";

    /**
     * 槽位表（每格一个 {@link RequesterSlot}，物品/流体二选一）。
     *
     * <p><b>存的是容器不是数组</b>：{@code RequesterSlot[]} 直接标 {@code @Persisted} 会崩
     * （LDLib 要求数组元素是 {@code IManaged} 或 {@code IContentChangeAware}）。
     * {@link RequesterSlots} 两者都满足，并负责整表 NBT。
     */
    @Persisted
    private final RequesterSlots slots = new RequesterSlots(SLOT_COUNT);

    /** 未单独设置批量时，物品格用的默认批量。 */
    @Persisted
    @DescSynced
    private int defaultItemBatch = 64;

    /** 未单独设置批量时，流体格用的默认批量（mB）。 */
    @Persisted
    @DescSynced
    private int defaultFluidBatch = 1000;

    /** 当前页（1..{@value #PAGES}）。 */
    @Persisted
    @DescSynced
    private int page = 1;

    /** 「数量 / 并发」弹层当前编辑的槽位下标；-1 = 未选。 */
    @Persisted
    @DescSynced
    private int selectedSlot = -1;

    /** 每槽状态串（{@value #SLOT_COUNT} 个字符 '0'..'5'）。String 是 LDLib 原生 payload。 */
    @Persisted
    @DescSynced
    private String slotStatus = zeroStatus();

    /** 当前最需要关注的阻塞原因。 */
    @Persisted
    @DescSynced
    private String blockedText = "";

    /** AE 节点是否在线。 */
    @DescSynced
    protected boolean isOnline;

    @Persisted
    protected final GridNodeHolder nodeHolder = new GridNodeHolder(this);

    protected final IActionSource actionSource = IActionSource.ofMachine(this);

    /** 下单引擎（future 不可序列化，不持久化）。 */
    private final RequesterOrderEngine engine = new RequesterOrderEngine(this);

    private TickableSubscription tickSubscription;

    public RequesterMachine(IMachineBlockEntity holder) {
        super(holder);
        // 补货牌是"网络旁边的一个方块"：六面都允许接线缆（ME 仓默认只开正面）。
        this.nodeHolder.getMainNode().setExposedOnSides(EnumSet.allOf(Direction.class));
    }

    private static String zeroStatus() {
        StringBuilder sb = new StringBuilder(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) sb.append(STATUS_IDLE);
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // AE 节点
    // ------------------------------------------------------------------

    public IManagedGridNode getMainNode() {
        return nodeHolder.getMainNode();
    }

    public IActionSource getActionSource() {
        return actionSource;
    }

    @Override
    public void onMainNodeStateChanged(State reason) {
        IGridConnectedMachine.super.onMainNodeStateChanged(reason);
        if (!isRemote()) {
            engine.onNetworkStateChanged(isOnline);
        }
    }

    @Override
    public boolean isOnline() {
        return isOnline;
    }

    @Override
    public void setOnline(boolean online) {
        this.isOnline = online;
    }

    @Override
    public void onMachinePlaced(LivingEntity player, ItemStack stack) {
        // 记录归属玩家：AE 权限/安全终端按节点 owner 判定，与 ME 仓一致。
        if (player instanceof Player p && !p.level().isClientSide()) {
            getMainNode().setOwningPlayer(p);
        }
    }

    @Override
    public void onRotated(Direction oldFacing, Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        getMainNode().setExposedOnSides(EnumSet.allOf(Direction.class));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (isRemote()) return;
        updateMEStatus();
        engine.subscribe();
    }

    @Override
    public void onUnload() {
        engine.unsubscribe();
        if (tickSubscription != null) {
            tickSubscription.unsubscribe();
            tickSubscription = null;
        }
        super.onUnload();
    }

    public TickableSubscription subscribeTick(Runnable runnable) {
        tickSubscription = subscribeServerTick(tickSubscription, runnable);
        return tickSubscription;
    }

    // ------------------------------------------------------------------
    // 槽位访问
    // ------------------------------------------------------------------

    public RequesterSlots slots() {
        return slots;
    }

    public RequesterSlot slot(int index) {
        return slots.slot(index);
    }

    /** 设置某格（拖入/清空时用）；返回是否发生变更。 */
    public boolean acceptStack(int index, appeng.api.stacks.GenericStack stack) {
        return slots.mutate(index, i -> slots.slot(i).accept(stack));
    }

    public boolean clearSlot(int index) {
        return slots.mutate(index, i -> slots.slot(i).setKey(null));
    }

    public boolean setSlotTarget(int index, long target) {
        return slots.mutate(index, i -> slots.slot(i).setTarget(target));
    }

    public boolean setSlotBatch(int index, long batch) {
        return slots.mutate(index, i -> slots.slot(i).setBatch(batch));
    }

    /** 引擎用：写"网络现有量"快照（静默，不触发落盘）。 */
    public void setSlotStock(int index, long stock) {
        slots.setStockSilently(index, stock);
    }

    /** 槽位有效批量：逐格优先，否则用同类默认值。 */
    public long batchAt(int index) {
        RequesterSlot slot = slot(index);
        boolean fluid = slot.key() instanceof AEFluidKey;
        return slot.effectiveBatch(fluid ? getDefaultFluidBatch() : getDefaultItemBatch());
    }

    // ------------------------------------------------------------------
    // 状态串
    // ------------------------------------------------------------------

    public void setSlotStatus(int index, char status) {
        if (index < 0 || index >= SLOT_COUNT) return;
        if (slotStatus.length() != SLOT_COUNT) slotStatus = zeroStatus();
        if (slotStatus.charAt(index) == status) return;
        char[] chars = slotStatus.toCharArray();
        chars[index] = status;
        slotStatus = new String(chars);
    }

    public char slotStatusAt(int index) {
        if (index < 0 || index >= SLOT_COUNT || slotStatus.length() != SLOT_COUNT) return STATUS_IDLE;
        return slotStatus.charAt(index);
    }

    public void setBlockedText(String text) {
        String next = text == null ? "" : text;
        if (!next.equals(blockedText)) blockedText = next;
    }

    public String getBlockedText() {
        return blockedText;
    }

    // ------------------------------------------------------------------
    // 批量 / 页码
    // ------------------------------------------------------------------

    public int getDefaultItemBatch() {
        return Math.max(1, defaultItemBatch);
    }

    public void setDefaultItemBatch(long value) {
        int next = (int) Math.max(1L, Math.min(value, RequesterSlot.MAX_AMOUNT));
        if (next == defaultItemBatch) return;
        defaultItemBatch = next;
        markDirty();
    }

    public int getDefaultFluidBatch() {
        return Math.max(1, defaultFluidBatch);
    }

    public void setDefaultFluidBatch(long value) {
        int next = (int) Math.max(1L, Math.min(value, RequesterSlot.MAX_AMOUNT));
        if (next == defaultFluidBatch) return;
        defaultFluidBatch = next;
        markDirty();
    }

    public int getPage() {
        return Math.max(1, Math.min(page, PAGES));
    }

    public void setPage(int value) {
        int next = Math.max(1, Math.min(value, PAGES));
        if (next == page) return;
        page = next;
        // 翻页后原选中格不在本页了：清掉选中，避免弹层编辑一个看不见的格子。
        int from = (next - 1) * PAGE_SIZE;
        if (selectedSlot >= 0 && (selectedSlot < from || selectedSlot >= from + PAGE_SIZE)) {
            selectedSlot = -1;
        }
        markDirty();
    }

    // ------------------------------------------------------------------
    // 「数量 / 并发」弹层的选中格
    // ------------------------------------------------------------------

    /** 当前选中的槽位下标；-1 = 未选。 */
    public int getSelectedSlot() {
        if (selectedSlot < 0 || selectedSlot >= SLOT_COUNT) return -1;
        return selectedSlot;
    }

    /** 只允许选中当前页内的格子（弹层编辑的就是它）。 */
    public void setSelectedSlot(int index) {
        int from = (getPage() - 1) * PAGE_SIZE;
        int next = (index < from || index >= from + PAGE_SIZE) ? -1 : index;
        if (next == selectedSlot) return;
        selectedSlot = next;
        markDirty();
    }

    public void clearSelectedSlot() {
        setSelectedSlot(-1);
    }

    // ------------------------------------------------------------------
    // 统计（界面汇总行）
    // ------------------------------------------------------------------

    /** 各类状态计数，下标 = 状态字符 - '0'。 */
    public int[] statusCounts() {
        int[] counts = new int[6];
        for (int i = 0; i < SLOT_COUNT; i++) {
            int idx = slotStatusAt(i) - '0';
            if (idx >= 0 && idx < counts.length) counts[idx]++;
        }
        return counts;
    }

    /** 已配置的槽位数。 */
    public int configuredSlots() {
        return slots.configuredCount();
    }

    // ------------------------------------------------------------------
    // 界面
    // ------------------------------------------------------------------

    /**
     * 外框：<b>照抄 ME 输入总成</b>（{@code MEDualInputHatchPartMachine#createUI} 的同一组数字）。
     *
     * <p>{@code ModularUI(176, 180)} + {@code FancyMachineUIWidget(this, 176, 185)}。
     * {@code IFancyUIMachine} 自带的默认实现是 176×166，本来就带外框；
     * 这里覆写不是为了"补外框"，而是让起手尺寸与参照机一致——载入后
     * {@code FancyMachineUIWidget#setupFancyUI} 会按「页面尺寸 + 四边留白」重新调窗口大小，
     * 所以这两个数只影响观感的一致性（标题栏 / 侧边页签 / 配置按钮 / 物品栏的位置）。
     */
    @Override
    public ModularUI createUI(Player player) {
        return new ModularUI(176, 180, this, player).widget(new FancyMachineUIWidget(this, 176, 185));
    }

    @Override
    public Widget createUIWidget() {
        // 页面布局严格对齐 ME 输入总成（AEDualConfigWidget）：
        //   3，0    网络状态
        //   3，10   网格（8 列 × 2 行双层格 = 144×74）
        //   3，87   翻页条（网格底 + 3，= 参照控件的 navigationY = rows*38+1）
        //   3，108  阻塞提示
        // 数量 / 并发弹层是页内控件：点格子才出现、贴在那一格旁边、夹在网格范围内，
        // 所以永远在窗口里（旧版把它钉在 x=GUI_WIDTH=150，也就是主面板右边缘之外 → 被窗口裁掉）。
        //
        // 页面不再自带 ResourceBorderTexture 底图：外框由 FancyMachineUIWidget 画 GT 机器背景，
        // 页面里再套一层边框就是双层框——这是旧版"看着不像别的 GT 机器"的主因。
        RequesterSlotGrid grid = new RequesterSlotGrid(this, GRID_X, GRID_Y);
        RequesterAmountBox amountBox = new RequesterAmountBox(this, GRID_X, GRID_Y);
        amountBox.attachGrid(grid);
        grid.attachAmountBox(amountBox);

        WidgetGroup group = new WidgetGroup(0, 0, GUI_WIDTH, GUI_HEIGHT) {
            @OnlyIn(Dist.CLIENT)
            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                // 点到弹层以外的地方就收起（照参照控件：先 disableAmount()，再把这次点击照常派发下去，
                // 这样点到别的格子时会重新在该格旁边弹出）。
                if (amountBox.isVisible() && !amountBox.isMouseOverElement(mouseX, mouseY)) {
                    grid.disableAmount();
                }
                return super.mouseClicked(mouseX, mouseY, button);
            }
        };

        group.addWidget(new LabelWidget(GRID_X, 0, (Supplier<String>) () -> isOnline
                ? "网络状态：§2在线§r"
                : "网络状态：§4离线§r"));

        group.addWidget(grid);
        group.addWidget(new RequesterPageBar(this, GRID_X, PAGE_BAR_Y, RequesterSlotGrid.width()));
        group.addWidget(new LabelWidget(GRID_X, HINT_Y, (Supplier<String>) this::blockedLine));
        // 弹层最后加：控件按加入顺序绘制，这样它画在网格和翻页条之上。
        group.addWidget(amountBox);
        return group;
    }

    private String statusLine() {
        int[] counts = statusCounts();
        String head = isOnline ? "§a网络状态: 在线" : "§c网络状态: 离线";
        return head + "§r §7充足" + counts[0] + " 在途" + counts[1]
                + " 计算" + counts[2] + " 阻塞" + counts[3] + " 无解" + counts[4];
    }

    public String blockedLine() {
        return blockedText.isEmpty() ? "" : "§c" + blockedText;
    }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    /** 服务端 tick 实体（不能叫 serverTick —— MetaMachine 里是 final）。 */
    public void engineTick() {
        if (isRemote()) return;
        // 刷新在线状态：按 GTCEu ME 仓同款节流；离线时每 tick 都试，网络一恢复就尽快跟上。
        if (!isOnline || shouldSyncME()) {
            updateMEStatus();
        }
        engine.tick(getLevel() == null ? 0L : getLevel().getGameTime());
    }

    // ------------------------------------------------------------------
    // 持久化补充
    // ------------------------------------------------------------------

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        // 槽位表由 RequesterSlots 的 @Persisted 负责，这里只补页码。
        tag.putInt(TAG_PAGE, page);
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        if (tag.contains(TAG_PAGE)) {
            int loaded = tag.getInt(TAG_PAGE);
            page = Math.max(1, Math.min(loaded, PAGES));
        }
    }
}
