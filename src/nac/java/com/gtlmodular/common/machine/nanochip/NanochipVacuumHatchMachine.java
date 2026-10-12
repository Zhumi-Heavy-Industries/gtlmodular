package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.data.nanochip.NanochipModContext;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * <b>真空传送带仓（VCI / VCO）</b> —— 原版 NAC 的彩色接口仓移植版。
 *
 * <p>同一个方块，放在哪儿就干哪儿的活（<b>颜色本身就是线</b>，不用铺管道）。
 * <b>注意 VCI/VCO 是「站在网络视角」命名的</b>：VCO = 往网络里写，VCI = 从网络里读
 * （原版证据见 {@link #tickVacuum()} 的注释）。所以控制室与模块上的分工必须分开看：
 *
 * <table border="1">
 *   <tr><th>放在哪</th><th>输入仓（VCI，从网络读）</th><th>输出仓（VCO，写进网络）</th></tr>
 *   <tr>
 *     <td><b>复合体控制室</b>（控制器是 NAC）</td>
 *     <td>把该颜色通道里的 <b>CC</b> 还原成真实物品放进自己格子（<b>PC 不还原</b>），
 *         并推给正面容器
 *         —— 对应原版 {@code The NAC can also convert CCs into item equivalents and send them to its Output Bus}</td>
 *     <td>每 5 秒把自己格里的真实物品「封装」成它对应的 <b>CC 物品</b>（{@code gtlmodular:cc_*}）
 *         送进该颜色通道
 *         —— 对应原版 {@code The NAC will convert items into Circuit Components (CCs) every 5 seconds
 *         and send them to its VCOs}
 *         <br><b>★ P3b / W2：通道里搬的就是 CC 物品本身</b>（不再是"真件 + 一个标记"），
 *         映射表来自 {@link NanochipConversionMap}（= 我们自己的 CONVERSION 配方表）</td>
 *   </tr>
 *   <tr>
 *     <td><b>模块</b>（控制器是 NAC 模块）</td>
 *     <td>从该颜色通道取件填进自己格子，供模块配方消耗
 *         —— 对应原版 {@code A module will pull inputs from a colored VCI}</td>
 *     <td>把自己格子里的产出推回该颜色通道 —— 产出若是 CC 物品，按<b>它自己的 processed 状态</b>
 *         记 CC（可还原）/ PC（不可还原）
 *         —— 对应原版 {@code output into the same colored VCO on its structure}</td>
 *   </tr>
 * </table>
 *
 * <p><b>一对一</b>：颜色相同即对接，所以「谁喂谁」完全由颜色决定（原版：
 * {@code Vacuum Conveyor Connections are 1 to 1}）。
 *
 * <p>组装矩阵的输入仓只吃 <b>PC</b>（原版要求 CC 必须先经模块精炼），
 * 而组装矩阵自己的输出仓按 <b>CC</b> 推回网络 —— 这样最终电路才能被控制室的 VCI 还原成真物品。
 *
 * <p><b>颜色怎么改</b>：手持 GT 螺丝刀右键循环切换（扳手不生效，也不需要），
 * 或在仓的 GUI 左侧「真空传送带」页里点 4×4 色板直接选，或用 GT 喷漆罐右键。
 *
 * <h2>★ 2026-09-17 第六轮：颜色的"未染色"状态与 NH 对齐</h2>
 * <p>玩家的第 3 条要求是「管道的机制改了，真空舱室也得一起看，别出现两边不一致」。
 * NH 两侧的证据（逐条字节码）：
 * <pre>
 * // ① 仓的颜色来源与默认值 —— 和管道走的是同一套 GT 字段
 * MTEHatchVacuumConveyor.getColorization()
 *   0: aload_0
 *   1: invokevirtual getBaseMetaTileEntity:()Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;
 *   4: invokeinterface IGregTechTileEntity.getColorization:()B
 *   9: ireturn
 * // 而 BaseMetaTileEntity.getColorization() = getColorRaw() - 1；colorRaw 默认 0
 * // ⇒ 仓的默认也是 -1（未染色），与管道**完全同源**。
 *
 * // ② 管道那一侧的对接门槛（MTEVacuumConveyorPipe.checkConnections 偏移 83..128）
 *   邻居.getColorization() == 自己.getColorization()      // 必须同色（-1 永远过不了）
 *   &amp;&amp; 邻居的 metaTileEntity instanceof VacuumFactoryElement
 *   &amp;&amp; 邻居.canConnectOnSide(dir.getOpposite())
 *
 * // ③ 仓自己的 canConnectOnSide（MTEHatchVacuumConveyorInput/Output，偏移 0..18）
 *   0: aload_1
 *   1: aload_0
 *   2: invokevirtual getBaseMetaTileEntity:()...
 *   5: invokeinterface IGregTechTileEntity.getFrontFacing:()Lnet/minecraftforge/common/util/ForgeDirection;
 *  10: if_acmpne 17
 *  13: iconst_1
 *  14: goto 18
 *  17: iconst_0
 *  18: ireturn
 * // ⇒ return dir == getFrontFacing()：**只有仓的正面能接管道**
 * </pre>
 * <p>⇒ 移植版本轮把这三条都照抄：{@link #canConnectOnSide(Direction)}、默认未染色、
 * 未染色时<b>不搬任何东西</b>（NH 的 tooltip 原文就是 {@code Must be <颜色> to work}，
 * 见 {@code MTEVacuumConveyorPipe.getDescription} 里拼的 {@code TOOLTIP_COLORED}）。
 */
public class NanochipVacuumHatchMachine extends ItemBusPartMachine {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(NanochipVacuumHatchMachine.class,
                    ItemBusPartMachine.MANAGED_FIELD_HOLDER);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /** 控制室封装周期：原版「每 5 秒把输入仓物品转成 CC」。 */
    public static final int ROOM_ENCAPSULATE_INTERVAL = 100;

    /** 通道搬运周期（模块取料/回料、控制室还原）。 */
    public static final int TRANSFER_INTERVAL = 20;

    /**
     * 搬运相位：<b>每秒的第 17 tick</b>。
     *
     * <p>原版 {@code MTEHatchVacuumConveyor.VACUUM_MOVE_TICK = 17}（已按字节码核对常量值），
     * 任务书原文：{@code 真空传输在每秒的第17进行瞬间传输，这样的传输间隔配合NAC的5秒转化间隔更加稳定}。
     * 移植版照抄这个相位：{@code gameTime % 20 == 17}。
     */
    public static final int VACUUM_MOVE_TICK = 17;

    /** 单次搬运上限（件）。 */
    private static final int TRANSFER_BATCH = 64;

    /**
     * 本仓槽位数：<b>16 = 原版的 4×4</b>。
     *
     * <p>原版 {@code MTEHatchVacuumConveyor.getRowCount()/getColumnCount()} 都返回 4（已核对字节码），
     * 对应任务书「每个端口最多记录 16 种电路组件」。GTCEu 的物品总线 UI 用 {@code sqrt(格数)} 排版，
     * 所以 16 格会自动画成 4×4，与原版一致。
     */
    public static final int SLOTS = 16;

    /**
     * 当前颜色序号；{@link NanochipColor#INDEX_UNPAINTED}（= -1）= <b>未染色</b>，且它就是默认值。
     *
     * <p>与 NH 同源：GT5U {@code MTEHatchVacuumConveyor.getColorization()} 读的是
     * {@code BaseMetaTileEntity.getColorization()}（= {@code colorRaw - 1}，默认 0 ⇒ -1），
     * 也就是<b>仓与管道用的是同一个字段、同一个默认值</b>。
     */
    @DescSynced
    private int colorIndex = NanochipColor.INDEX_UNPAINTED;

    /**
     * ★ <b>2026-09-17 第七轮（NH 两态外壳）</b>：本仓是否<b>已被收进一台成形的主机 / 模块</b>。
     *
     * <p>NH 的口径（逐条字节码，见 {@code _结构研究} 与 §74）：
     * <pre>
     * // ① 结构成形时把机壳索引塞进舱室的 hull
     * MTENanochipAssemblyModuleBase.addBaseStructure(StructureDefinition$Builder)   偏移 45/48
     *   getstatic   MTENanochipAssemblyComplex.CASING_INDEX_WHITE:I
     *   invokevirtual HatchElementBuilder.casingIndex:(I)…
     * // ② 把仓收进机器清单时切贴图
     * MTENanochipAssemblyModuleBase.addConveyorToMachineList(IGregTechTileEntity, int)  偏移 34/35、72/73
     *   iload_2  →  MTEHatchVacuumConveyorInput.updateTexture:(I)V
     *   iload_2  →  MTEHatchVacuumConveyorOutput.updateTexture:(I)V
     * // ③ 这个索引 = 纳米芯片接口机壳
     * MTENanochipAssemblyComplex.&lt;clinit&gt;  偏移 0/3/6
     *   getstatic   Casings.NanochipMeshInterfaceCasing
     *   getfield    Casings.textureId   →  putstatic CASING_INDEX_WHITE
     * </pre>
     * ⇒ <b>未并入任何成形结构</b>时仓的外壳是 {@code MACHINE_CASINGS[tier][0]}（= <b>GTCEu 的 tier 机壳</b>，
     * 我们按玩家裁决直接引用 {@code gtceu:block/casings/voltage/uiv/*}）；
     * <b>已并入</b>时换成纳米芯片接口机壳，并因 NH 是 {@code connect=tile} 而与周围机壳连成一片。
     *
     * <p>★ GTCEu 侧对应的钩子是 {@code IMultiPart.addedToController / removedFromController}
     * （{@code MultiblockControllerMachine.onStructureFormed/onStructureInvalid} 里逐个 part 调它们），
     * 与 NH 的"加入 / 移出机器清单"是同一时机。
     *
     * <p>⚠️ <b>必须 {@code @DescSynced}</b>：仓的渲染发生在客户端，而 GTCEu 的
     * {@code MultiblockPartMachine.controllerPositions}（{@code isFormed()} 的来源）是<b>服务端字段</b>
     * —— 客户端直接读 {@code isFormed()} 恒为 false（本项目此前踩过"客户端读服务端字段"的坑，
     * 见 {@code NanochipModuleMachine} 的 host 字段注释）。这里由服务端置位并同步给客户端。
     *
     * <p>旧存档兼容：字段缺省 = {@code false}（未并入）；服务端下一次结构检查
     * （GTCEu 的 {@code asyncCheckPattern} → {@code onStructureFormed} → 本类的
     * {@code addedToController}，或本类 tick 里的兜底刷新）就会把它纠正成 {@code true}
     * ⇒ <b>不需要玩家重放结构</b>。
     */
    @DescSynced
    private boolean casingFromStructure;

    /** 本仓是否"已被收进成形的主机/模块"（客户端读这个同步字段，不要读服务端的 {@code isFormed()}）。 */
    public boolean isCasingFromStructure() {
        return casingFromStructure;
    }

    /**
     * 服务端刷新"已并入"态（客户端调用是空操作）。
     *
     * <p>只在**变化**时写字段：{@code @DescSynced} 的写入会广播给客户端，
     * 每 tick 无脑写会把同步通道打满。
     */
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
    public void addedToController(IMultiController controller) {
        super.addedToController(controller);
        // NH：结构成形、把仓收进机器清单时切外壳（MTENanochipAssemblyModuleBase.addConveyorToMachineList）
        refreshCasingState();
    }

    @Override
    public void removedFromController(IMultiController controller) {
        super.removedFromController(controller);
        // NH：结构失效 / 被移出机器清单时切回去
        refreshCasingState();
    }

    /**
     * 最近一次<b>真的搬动过东西</b>的 gameTime（不参与存档/同步：只在服务端判定"这个端口是否在工作"）。
     *
     * <p>用途见 {@code NanochipMachines.VacuumHatchRenderer}：原版的 `*_ACTIVE` 动画是"这个端口在工作"
     * 时才亮的；GTCEu 自带的 {@code OverlayTieredActiveMachineRenderer} 只能判"多方块控制器在跑"，
     * 那会让整座 NAC 一开工、所有端口一起闪。改用这个更精确的口径。
     */
    private long lastWorkTick = Long.MIN_VALUE;

    /** 判定窗口：比搬运周期（每分钟 20 tick / 控制室封装 100 tick）略长，持续工作时不会闪断。 */
    public static final int WORK_ANIM_WINDOW = 110;

    /** 本端口最近是否在搬东西（给渲染器选"工作中"那一层 overlay）。 */
    public boolean isVacuumWorking() {
        Level level = getLevel();
        // ⚠️ 必须挡掉 `Long.MIN_VALUE`：`gameTime − Long.MIN_VALUE` 在 long 上会**溢出**成一个大负数，
        // 于是 `< WORK_ANIM_WINDOW` 恒真 ⇒ 客户端永远画"工作中"的 *_active 动画（实测缺陷）。
        // 未搬过件时就该走空闲模型（纯白圈，染色才看得清）。
        return level != null && lastWorkTick != Long.MIN_VALUE
                && level.getGameTime() - lastWorkTick < WORK_ANIM_WINDOW;
    }

    private TickableSubscription vacuumTickSub;

    public NanochipVacuumHatchMachine(IMachineBlockEntity holder, int tier, IO io) {
        super(holder, tier, io);
    }

    @Override
    protected int getInventorySize() {
        return SLOTS;
    }

    /**
     * ★★ 2026-09-19（队长：「真空输入塞不进去东西呀」）—— <b>本覆写只放宽"外部（玩家/管道/漏斗/AE）能不能插"</b>。
     *
     * <h3>查到的事实（逐条字节码，不是猜）</h3>
     * <pre>
     * // ① 我们继承 GTCEu 的 ItemBusPartMachine，它的仓是这么造的：
     * ItemBusPartMachine.createInventory(Object...)  偏移 0..16
     *   new NotifiableItemStackHandler(this, getInventorySize(), io)      ← 三参
     * // ② 三参构造把 capabilityIO 也设成同一个 io：
     * NotifiableItemStackHandler(MetaMachine, int, IO)   偏移 0..8
     *   aload_3; aload_3; invokespecial &lt;init&gt;(MetaMachine,int,IO,IO)     ← 同一个值传两次
     * // ③ capabilityIO 决定"能力面"能不能读/写：
     * ICapabilityTrait.canCapInput()   偏移 0..29：getCapabilityIO() == IN || == BOTH
     * ICapabilityTrait.canCapOutput()  偏移 0..29：getCapabilityIO() == OUT || == BOTH
     * NotifiableItemStackHandler.insertItem(int,ItemStack,boolean,boolean)  偏移 0..5：canCapInput() ? storage.insertItem : 原样退回
     * NotifiableItemStackHandler.extractItem(...)                          偏移 0..5：canCapOutput() ? storage.extractItem : 空栈
     * // ④ 能力面确实挂在每一面（不是"只有正面"）：
     * MachineTrait.&lt;init&gt; 偏移 21-27：capabilityValidator = lambda$new$0 = (side) -&gt; true
     * MetaMachine.getItemTransferCap(side, useCover)  偏移 62..181：
     *   traits.filter(IItemTransfer).filter(t -&gt; t.hasCapability(side))
     *   → new IOItemTransferList(list, io, filter)   // io 只在"该面是物品输出面且禁止从输出面输入"时才变 OUT
     *   → ItemBusPartMachine 不实现 IAutoOutputItem（javap 类头：只 implements IDistinctPart, IMachineLife）
     *     ⇒ 那个 io **恒为 IO.BOTH** ⇒ 挡插入的不是它，而是每个仓自己的 capabilityIO
     * </pre>
     * ⇒ 结论（改前）：<b>VCI（io=IN）自动化插得进去；VCO（io=OUT）自动化插不进去</b>（GUI 拖拽两个都能塞，
     * 因为 GUI 的 SlotWidget 直接操作 {@code inventory.storage}，绕过了能力面）。
     * 而控制室上「真物品 → CC」的正门恰恰是 <b>VCO</b>（{@link #roomEncapsulate}），
     * VCI 是「CC → 真物品」的还原口（{@link #roomMaterialize}）⇒ 想用 AE/管道喂料的人会撞死在 VCO 上。
     *
     * <h3>修法（只放宽插入，不减任何既有能力）</h3>
     * <ul>
     *   <li>{@code handlerIO} 仍是 {@code io} ⇒ <b>配方/物流语义一个字不变</b>
     *       （VCO 依旧按 OUT 参与配方处理，模块产物照旧被推进通道）；</li>
     *   <li>VCO 的 {@code capabilityIO} 由 {@code OUT} 放宽成 {@code BOTH} ⇒ 自动化可以往里插真物品；
     *       <b>原来就能抽</b>（OUT 允许 extract）现在仍然能抽 ⇒ 没有任何能力被拿掉；</li>
     *   <li>VCI 的 {@code capabilityIO} 保持 {@code IN} ⇒ "只能往里塞、不能被别人抽走"这条不变。</li>
     * </ul>
     * <p>残余风险（如实登记）：放宽后，自动化也能往<b>模块</b>的 VCO 里塞东西（此前只有 GUI 能塞），
     * 这些件会被 {@link #modulePush} 按"件自身的 processed 状态"推回通道（见
     * {@code NanochipConversionMap.materializableFlag}）。要彻底避免，请在 AE/管道侧只对
     * <b>控制室的 VCO</b> 下料。
     */
    @Override
    protected NotifiableItemStackHandler createInventory(Object... args) {
        // 三参构造会把 capabilityIO 也设成 io；这里显式给第四参：VCO 放开成 BOTH（可插可抽），VCI 维持 IN。
        return new NotifiableItemStackHandler(this, SLOTS, io, io == IO.OUT ? IO.BOTH : io);
    }

    /**
     * ★★ 2026-09-19（同上）—— <b>把 VCO 的"手动放入"打开</b>（只补上 {link #createInventory} 管不到的那一半）。
     *
     * <h3>为什么能力面修完还不够</h3>
     * GUI 那条路<b>完全不看 capabilityIO</b>：{@code ItemBusPartMachine.createUIWidget()} 自己按 {@code io} 算
     * 每个槽位的"放入/取出"开关 ——
     * <pre>
     * // javap -c ItemBusPartMachine.createUIWidget（GTCEu 1.4.4）：
     *  97: new SlotWidget
     * 102: invokevirtual getInventory         105: getfield NotifiableItemStackHandler.storage
     * 129: getfield TieredIOPartMachine.io    132: getstatic IO.IN    135: invokevirtual IO.support(IO)
     * 138: invokespecial SlotWidget."&lt;init&gt;"(LItemTransfer;IIIZZ)V      ← 第 5 参 = io.support(IO.IN)
     * 148: getfield io                       151: getstatic IO.IN    155: invokevirtual IO.support(IO)
     *       （第 6 参 = io.support(IO.OUT)）
     * </pre>
     * {@code IO.support(x)} = {@code x == this || (x != NONE && this == BOTH)}，所以 VCO（{@code io == OUT}）的
     * 第 5 参恒为 <b>false</b> ⇒ <b>玩家手动也塞不进去</b>（不是只有管道塞不进去）。
     * 而 LDLib 的开关就是这一个字段（{@code SlotWidget.canPutStack = isEnabled() && canPutItems}，偏移 0-19；
     * {@code setCanPutItems(boolean)} 是公开 setter，本仓构造 classpath 的
     * {@code libs/ldlib-forge-1.20.1-1.0.33.b.jar} 里就有）。
     *
     * <p>所以这里包一层基类 UI，只对 <b>VCO</b> 把每个槽位的"放入"打开；"取出"仍按 {@code io} 走
     * （VCI 依旧只进不出）。**不重建网格**（避免与基类的布局/电路槽位分叉）。
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

    // ---------- 颜色 ----------

    /**
     * 当前颜色；<b>未染色时返回 {@code null}</b>（= NH 的 {@code colorization == -1}）。
     *
     * <p>返回 null 而不是兜底成白色是刻意的：白色是一种<b>真颜色</b>，必须与"没染过"区分开 ——
     * NH 里 {@code -1} 与 {@code 0} 是两种不同的 colorization，且只有同色才对接。
     */
    public NanochipColor color() {
        return NanochipColor.fromIndex(colorIndex);
    }

    /** 是否已被染色；{@code false} = NH 的 {@code colorization == -1}（该仓不搬任何东西）。 */
    public boolean isPainted() {
        return colorIndex >= 0;
    }

    /**
     * <b>本仓能不能在 {@code dir} 这一侧接管道</b> —— 逐条对应 NH
     * {@code MTEHatchVacuumConveyorInput/Output.canConnectOnSide(dir)}：
     * <pre>
     * 0: aload_1
     * 1: aload_0
     * 2: invokevirtual getBaseMetaTileEntity:()Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;
     * 5: invokeinterface IGregTechTileEntity.getFrontFacing:()Lnet/minecraftforge/common/util/ForgeDirection;
     * 10: if_acmpne 17
     * 13: iconst_1
     * 14: goto 18
     * 17: iconst_0
     * 18: ireturn
     * </pre>
     * ⇒ <b>{@code return dir == getFrontFacing()}</b>：只有仓的正面（那面画着"真空管道接口"贴图
     * {@code OVERLAY_VACUUM_PIPE_PORT_*} 的正面）能接管，其它五面一律不接。
     */
    public boolean canConnectOnSide(Direction dir) {
        return dir != null && dir == getFrontFacing();
    }

    /**
     * 设置颜色（世界里的"线"就是它）；{@code color == null} = <b>清成未染色</b>。
     *
     * <p><b>染色本身不用 GT 的"喷漆"</b>：GTCEu 的喷漆（{@code setPaintingColor}）在 1.4.4 里
     * 只被 Jade/TOP 的 {@code StainedColorProvider} 与 NBT 使用 —— 拿它当染色手段的话，
     * 提示里就只剩一行不可读的十六进制（玩家反馈"只看 #FF9AC0CD 根本不知道是淡蓝色"）。
     * 所以外观染色统一走 {@link #tintColor(int)}（只染前脸那圈白像素，与原版一致），
     * 可读的颜色名由 {@code NanochipJadePlugin} / {@link #onAddFancyInformationTooltip} 给。
     *
     * <p>★【2026-09-15 第二轮】但 GT 那个字段仍然要<b>维护</b>：玩家要求 Jade/TOP 那行
     * {@code 颜色: #xxxxxxxx} "跟着真实颜色变"。它读的就是这个字段，所以每次改色都
     * {@link #syncPaintingColor()} 一次（<b>纯显示</b>，不影响渲染，见该方法注释）。
     */
    public void setColor(NanochipColor color) {
        int next = color == null ? NanochipColor.INDEX_UNPAINTED : color.ordinal();
        if (next != colorIndex) {
            this.colorIndex = next;
            scheduleRenderUpdate();
            // ★【2026-09-16 双模式路由】端口换色会改变"同色管网"的连通关系 ⇒ 让管网图重算
            //   （只自增一个静态计数器；管道模式下这就是"换色立刻改变路由"的根据）。
            NanochipPipeGraph.bumpTopologyEpoch();
        }
        syncPaintingColor();
    }

    /**
     * ★【2026-09-15 第二轮】把 GT 的"喷漆色"同步成本机颜色 —— <b>纯显示</b>，与渲染无关。
     *
     * <p>读它的只有 Jade/TOP 的 {@code StainedColorProvider}（打出 {@code 颜色: #xxxxxxxx}）
     * 与存档 NBT；{@link #tintColor(int)} 只读 {@link #color()}（{@code colorIndex}），
     * <b>从不</b>读 {@code getPaintingColor()} ⇒ 写这个字段碰不到外观。
     *
     * <p>用 {@link NanochipColor#paintArgb()}（不透明 ARGB）而不是 RGB：GTCEu 打印时用
     * {@code Integer.toHexString(...).toUpperCase()} 且<b>不补前导零</b>，传 RGB 会让绿色
     * {@code 0x00FF00} 显示成 {@code #FF00}；传 ARGB 恒 8 位，例如绿 = {@code #FF00FF00}。
     */
    private void syncPaintingColor() {
        try {
            NanochipColor c = color();
            // 未染色 ⇒ 复位成 GTCEu 自己的默认喷漆色，别在提示里显示一个假颜色名。
            super.setPaintingColor(c == null ? 0xFFFFFFFF : c.paintArgb());
        } catch (Throwable ignored) {
            // 老版本 API 差异，忽略
        }
    }

    /**
     * GT 的"喷漆"入口（{@code IPaintable#setPaintingColor}）：<b>把颜色接过来</b>（映射进我们的
     * 16 色通道），然后无条件把 GT 字段拉回本机颜色 —— 不允许它与 {@link #color()} 不一致，
     * 否则那行 {@code 颜色:} 会撒谎。
     *
     * <p>⚠️ <b>GTCEu 1.4.4 的喷漆罐本身并不走这里</b>：{@code ColorSprayBehaviour} 只改
     * <b>方块状态</b>（有 {@code DyeColor} 属性的方块、染色玻璃等），从不碰 {@code IPaintable}。
     * 所以"喷漆罐能染端口/管道"实际是靠 {@code NanochipSprayCanHandler}
     * （{@code PlayerInteractEvent.RightClickBlock}）实现的；本方法把 API 那一侧也接上，
     * 免得将来有 addon/GT 版本按 {@code IPaintable} 染我们时毫无反应。
     *
     * <p>写法上<b>不能递归调用自己</b>：映射完直接走 {@code super} 写字段。
     */
    @Override
    public void setPaintingColor(int argb) {
        NanochipColor mapped = NanochipColor.ofArgb(argb);
        if (mapped != null) {
            setColor(mapped);              // 内部会 syncPaintingColor()
        }
        syncPaintingColor();
    }

    /** 前脸 overlay 那一层用的 tintindex（见 {@link #tintColor(int)} 的原版取证）。 */
    public static final int TINT_OVERLAY = 2;

    /**
     * ★【2026-09-16 回退 + 纪律】<b>允许吃染料的 tintindex 白名单 —— 只有前脸 overlay</b>。
     *
     * <p>为什么要有这个常量：上一轮为了让"染色看得见"，我把 index 1（GTCEu 的**外壳/机身**层）
     * 也接上了染料 ⇒ **整个端口方块全绿**，违反了用户从第一天起定的核心规格：
     * <blockquote>「染色的时候，<b>外壳颜色不变</b>，只是那个<b>白色的正脸</b>那个会变，
     * 就是<b>被黑色像素包围到内侧的那一圈</b>，<b>白色像素</b>会变」「正脸黑框里<b>只有白色像素</b>会改变」</blockquote>
     * ⇒ 现回退成"只有 {@link #TINT_OVERLAY} 吃染料"，并把这条写成**显式白名单**：
     * <b>外壳与其它任何层永不进入该白名单</b>。以后谁想"让它看得见"，请走"前脸两层的
     * 前后顺序 / 渲染刷新"那条路（见 `_census/hatch_tint_diagnosis.md` 与报告 §49），**不许再染外壳**。
     */
    private static final java.util.Set<Integer> DYEABLE_TINT_INDEXES = java.util.Set.of(TINT_OVERLAY);

    /** 只为日志用：记录 GTCEu 实际请求过的 tintindex，确认哪个索引属于外壳、哪个属于覆盖层。 */
    private static final java.util.Set<Integer> TINT_INDEX_SEEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 染色值：<b>只染前脸 overlay，不染外壳</b> —— 这是原版的真实行为（2026-09-14 深夜按字节码更正）。
     *
     * <p><b>取证</b>（{@code MTEHatchVacuumConveyorInput/Output}）：
     * <pre>
     * getTexturesInactive(ITexture t) = {
     *     TextureFactory.of(OVERLAY_VACUUM_HATCH_INPUT,
     *                       Dyes.getModulation(getColorization(), Dyes.MACHINE_METAL.getRGBA())),  // ← 被染料调制
     *     TextureFactory.of(OVERLAY_VACUUM_PIPE_PORT_INPUT)                                     // ← 不染色
     * }
     * getTexturesActive(...) 同上，只把 overlay 换成 _ACTIVE 那张动画
     * </pre>
     * 也就是说：<b>外壳（{@code MACHINE_CASINGS} 分级机壳）根本不参与调制</b>，喷漆改变的只是
     * 前脸那一层里"黑框内侧的那圈白色像素"（黑框像素乘任何颜色仍是黑）；端口中心的半透明窗口
     * 由第二层 {@code OVERLAY_VACUUM_PIPE_PORT_*} 提供，也不染色。
     * 玩家原话：「染色的时候外壳颜色不变，只是那个白色的正脸那个会变，就是被黑色像素包围到内侧的那一圈」。
     *
     * <p>GTCEu 侧的对应关系：{@code hull_machine.json} 六个面是 {@code tintindex:1}（= 外壳），
     * 我们的 overlay 模型把那一面标成 {@link #TINT_OVERLAY}（= 前脸）。
     *
     * <p><b>★ 2026-09-15 第二轮更正（子代理逐字节取证 + 玩家实测"染了不变色"）</b>：
     * 上面这段"外壳根本不参与调制"的结论**只对前脸成立、对整体不成立** —— 真正的情况是：
     * <ol>
     *   <li>{@code hull_machine.json} 的六个面**全都是 {@code tintindex:1}**，而我们把 index 1 判成了
     *       "保持原色" ⇒ <b>6 个面里有 5 个从设计上永远不可能变色</b>；只有 {@code frontFacing} 那一面有前脸，</li>
     *   <li>而且前脸那两层里，**不吃染料的 {@code #pipe_port} 被放在 z=−0.002（更靠外）**、
     *       吃染料的 {@code #overlay} 在 z=−0.001 ⇒ 可见的"纯白像素"只剩约 44 个，染色几乎看不出来。</li>
     * </ol>
     * 原版依据（本类自己的取证就在上面）：{@code MACHINE_CASINGS[tier][mIndex+1] =
     * TextureFactory.of(bottom, top, side, Dyes.getRGBA())} —— <b>原版就是"整个机壳吃染料"</b>。
     * 所以这里改成 <b>{@code TINT_OVERLAY} 与 1（外壳）都返回染料</b>，其余（含 {@code -1}）仍不染。
     * <p>仍不影响结构判定与其它机器：`tintColor` 只在渲染线程被 GTCEu 的 {@code colorTinted} 调用。
     */
    @Override
    public int tintColor(int index) {
        if (TINT_INDEX_SEEN.add(index)) {
            // 探针：确认 GTCEu 请求过哪些 index（2=前脸，吃染料；1=外壳，**明确拒绝**）
            NanochipModContext.logger().debug(
                    "[gtlmodular] 真空传送 tintindex={} 请求染色（白名单={}；其它一律保持原色）",
                    index, DYEABLE_TINT_INDEXES);
        }
        // ★ 白名单只含前脸 overlay（TINT_OVERLAY）。外壳（index 1）与其它层**恒不染**：
        //   用户规格 = 外壳颜色不变、黑像素不变、透明不动、只有正脸黑框内侧那圈白像素变色。
        if (DYEABLE_TINT_INDEXES.contains(index)) {
            NanochipColor c = color();
            if (c == null) {
                return -1;   // 未染色 ⇒ 前脸保持原色（NH：colorization == -1 时调制量退化成 MACHINE_METAL）
            }
            // 用 GT5U 原版染料值（明亮），不是 MC 的文字暗色 —— 见 NanochipColor.paintRgb()
            return c.paintArgb();
        }
        return -1;
    }

    /** 机器信息浮层里加一行可读的颜色名（用该颜色自己上色；未染色时提示"未染色"并附 NH 的 tooltip 口径）。 */
    @Override
    public void onAddFancyInformationTooltip(java.util.List<Component> tooltips) {
        super.onAddFancyInformationTooltip(tooltips);
        NanochipColor c = color();
        if (c == null) {
            tooltips.add(Component.translatable("gtlmodular.nanochip.vacuum.color.unpainted")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            return;
        }
        tooltips.add(Component.translatable("gtlmodular.nanochip.vacuum.color.line",
                        Component.literal(c.nameZh()).withStyle(s -> s.withColor(c.textColor())))
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    // ---------- 存档：颜色必须存下来（否则重载后所有端口都变回白色） ----------

    /**
     * 存档键 {@code GTLModularHatchColor} = 颜色序号（<b>-1 = 未染色</b>）。
     *
     * <h3>★ 老存档兼容口径（2026-09-17 第六轮）</h3>
     * 端口与管道<b>不一样</b>：端口从来没有过"自动取色"，它存下来的颜色<b>一定是玩家设的</b>。
     * 所以这里的兼容口径比管道宽松一档、也更少破坏存量存档：
     * <ul>
     *   <li>有 {@code GTLModularHatchColor} 键（本模组早期版本一定会写）⇒ <b>原样保留</b>那个颜色；</li>
     *   <li>没有该键（丢进世界、还没被任何版本存过盘的新仓）⇒ <b>未染色</b>。</li>
     * </ul>
     * 也就是说：<b>老存档里已设过颜色的端口不会失效</b>，只有全新的端口才是"未染色、必须先染色才干活"。
     */
    @Override
    public void saveCustomPersistedData(net.minecraft.nbt.CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        tag.putInt("GTLModularHatchColor", colorIndex);
    }

    @Override
    public void loadCustomPersistedData(net.minecraft.nbt.CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        if (tag.contains("GTLModularHatchColor")) {
            int stored = tag.getInt("GTLModularHatchColor");
            colorIndex = (stored >= 0 && stored < NanochipColor.values().length)
                    ? stored
                    : NanochipColor.INDEX_UNPAINTED;
        } else {
            colorIndex = NanochipColor.INDEX_UNPAINTED;
        }
        // 【2026-09-15 第二轮】读档后把 GT 的"喷漆色"同步成本机颜色 —— 老存档里那个字段可能是
        // 早期版本（用喷漆实现染色）留下的别的颜色，不同步的话 Jade 那行 `颜色:` 就会显示错的值。
        syncPaintingColor();
    }

    /**
     * 螺丝刀右键循环换色。顺序：<b>未染色 → 白 → 橙 → … → 黑 → 白 → …</b>
     * （未染色是"没有颜色"，所以第一次点击必然变成白色）。
     *
     * <p>注意：{@code MetaMachine.onToolClick(...)} 是 {@code final}，不能直接覆写；
     * 它是把螺丝刀/扳手/锤子分发到 {@code onScrewdriverClick} / {@code onWrenchClick} 等
     * 可覆写方法（已按字节码核对），所以这里覆写螺丝刀那一条。
     */
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
                        Component.literal("[真空传送] 颜色 → ")
                                .append(Component.literal(next.nameZh())
                                        .withStyle(style -> style.withColor(next.textColor()))),
                        true);
            }
        }
        return InteractionResult.sidedSuccess(level != null && level.isClientSide);
    }

    // ---------- 生命周期 ----------

    @Override
    public void onLoad() {
        super.onLoad();
        Level level = getLevel();
        if (level != null && !level.isClientSide) {
            // 【2026-09-15 第二轮】新放的仓也要同步一次 GT 的"喷漆色"（初值是 GTCEu 的白，
            // 与我们的 colorIndex 不一定一致）。只写一个字段，不查注册表/邻居 ⇒ 加载期安全。
            // 历史上这里曾是"清成未喷漆"（为了不显示那行），玩家现在要求那行显示真实颜色。
            syncPaintingColor();
            unsubscribeTick();
            vacuumTickSub = subscribeServerTick(this::tickVacuum);
        }
    }

    @Override
    public void onUnload() {
        unsubscribeTick();
        // ★【2026-09-16 双模式路由】端口消失/区块卸载 ⇒ 同色管网的"节点集"可能变了，让 NAC 重算
        //   （与 NanochipPipeMachine.onUnload 同一个理由：MetaMachine 没有 setRemoved()，
        //    `MetaMachineBlockEntity.setRemoved()` 会调 onUnload ⇒ 拆掉方块一定走到这里）。
        NanochipPipeGraph.bumpTopologyEpoch();
        super.onUnload();
    }

    /** 显式回收订阅：不保存引用的话，区块卸载后订阅还会留着，一直在空跑。 */
    private void unsubscribeTick() {
        if (vacuumTickSub != null) {
            vacuumTickSub.unsubscribe();
            vacuumTickSub = null;
        }
    }

    /**
     * 自动物流：<b>只有控制室那一对仓</b>用，而且方向要按原版语义<b>反过来</b>。
     *
     * <p>基类 {@code ItemBusPartMachine.autoIO()} 是「OUT 往外推 / IN 往里拉」；但 VCI/VCO 是
     * <b>站在网络视角</b>命名的（见 {@link #tickVacuum()} 的原版证据）：控制室的
     * <b>VCO(OUT)</b> 负责把真实物品封装成 CC 送进网络 → 它需要从正面容器<b>拉</b>料；
     * <b>VCI(IN)</b> 负责把网络里的 CC 还原成真物品（原版：送进 Output Bus）→ 它需要把成品
     * <b>推</b>给正面容器（箱子 / AE 接口）。
     *
     * <p>模块上的仓一律不启用：模块的取料/回料由真空通道自己接管，不能再去抢相邻容器的东西。
     */
    @Override
    protected void autoIO() {
        if (!(currentController() instanceof NanochipAssemblyComplexMachine)) {
            return;
        }
        if (getOffsetTimer() % 5L != 0L || !isWorkingEnabled()) {
            return;
        }
        Level level = getLevel();
        if (level == null) {
            return;
        }
        // 注意：这里不能调基类用的 NotifiableItemStackHandler.importFromNearby / exportToNearby ——
        // 编译期 classpath 里的 ldlib 是 1.0.28.a（还没有这两个方法，运行期才是 1.0.33.b），
        // 所以直接用 Forge 的 ITEM_HANDLER 能力自己搬。
        Direction front = getFrontFacing();
        var be = level.getBlockEntity(getPos().relative(front));
        if (be == null) {
            return;
        }
        var resolved = be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,
                front.getOpposite()).resolve();
        if (resolved.isEmpty()) {
            return;
        }
        net.minecraftforge.items.IItemHandler target = resolved.get();
        if (this.io == IO.OUT) {
            // 封装仓：从正面容器拉真实物品进来（等 5 秒的封装周期把它们转成 CC）
            for (int s = 0; s < target.getSlots(); s++) {
                ItemStack peek = target.extractItem(s, TRANSFER_BATCH, true);
                if (peek.isEmpty()) {
                    continue;
                }
                ItemStack rest = insertIntoInventory(peek);
                int moved = peek.getCount() - rest.getCount();
                if (moved > 0) {
                    target.extractItem(s, moved, false);
                }
            }
        } else {
            // 还原仓：把网络还原出来的真物品推给正面容器（原版是送进 Output Bus）
            int slots = slotCount();
            for (int s = 0; s < slots; s++) {
                ItemStack st = getInventory().storage.getStackInSlot(s);
                if (st.isEmpty()) {
                    continue;
                }
                // ★ 2026-09-19（队长：「为什么没有样板总成，它也会自动返回到 ae」）：
                //   本仓会把**自己格子里的一切**推给正面容器 —— 不区分"我方还原出来的真件"和
                //   "玩家手动塞进来的件"。而**还原逻辑只读虚拟通道、完全不看自己的格子**
                //   ⇒ 玩家把封装件(CC/PC)塞进输入仓，它永远不会被还原，只会被原样推进正面的 ME 接口
                //   （现象：没有样板总成也会"自动回到 AE"，而且依然是封装形态）。这里点名警告。
                if (NanochipConversionMap.isCc(st)) {
                    NanochipModContext.logger().warn(
                            "[gtlmodular] 真空输入仓 @{} 正要把「{}」推给正面容器 —— 输入仓**只从虚拟通道还原真件**、"
                                    + "不看自己的格子（手动放进来的封装件不会被还原，只会被原样推走）。"
                                    + "要还原请把它放进**输出仓 VCO**（真件→网络那一侧），或用样板总成在 ME 终端下单换算",
                            getPos(), st.getHoverName().getString());
                }
                ItemStack rest = st.copy();
                for (int t = 0; t < target.getSlots() && !rest.isEmpty(); t++) {
                    rest = target.insertItem(t, rest, false);
                }
                int moved = st.getCount() - rest.getCount();
                if (moved > 0) {
                    getInventory().storage.extractItem(s, moved, false);
                }
            }
        }
    }

    // ---------- 真空搬运 ----------

    private void tickVacuum() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        // ★【2026-09-17 第七轮 · NH 两态外壳】每 tick 兜底刷新"已并入"态：
        //   钩子（addedToController/removedFromController）覆盖正常路径；这里负责"旧存档 / 世界重载 /
        //   控制器区块先加载"等钩子可能漏掉的时刻 —— 下一次结构检查（≤1 tick）就自动纠正，不需要重放结构。
        //   只在变化时写字段，开销 = 一次 Set.isEmpty()。
        refreshCasingState();
        // ★【2026-09-17 第六轮 · NH 对齐】未染色的仓<b>什么都不搬</b>。
        //
        // NH 依据：MTEVacuumConveyorPipe.getDescription() 拼的是
        //   TOOLTIP_COLORED + " to work"（= "Must be <颜色> to work"），
        // 而管道/仓的 colorization 默认 -1；再叠加 checkConnections() 的
        //   `colorization == -1 ⇒ return`，未染色元素在 NH 里既不接管、也不参与管网。
        // 移植版必须在这里也挡住，否则 NanochipVacuumNetwork 会拿到 null 颜色作键
        // （渠道表是 EnumMap ⇒ 直接 NPE），而且"颜色即线路"的语义也不再成立。
        NanochipColor myColor = color();
        if (myColor == null) {
            return;
        }
        IMultiController controller = currentController();
        if (controller == null) {
            return;
        }
        boolean room = controller instanceof NanochipAssemblyComplexMachine;
        NanochipAssemblyComplexMachine nac;
        NanochipModuleMachine module = null;
        if (controller instanceof NanochipAssemblyComplexMachine host) {
            nac = host;
        } else if (controller instanceof NanochipModuleMachine mod) {
            module = mod;
            nac = mod.getHost();
        } else {
            return; // 装在别的多方块上不干活
        }
        if (nac == null || !nac.isFormed()) {
            return;
        }

        boolean input = this.io == IO.IN;
        long now = level.getGameTime();
        // 相位对齐原版：搬运只在每秒的第 17 tick；控制室的"转化"周期是 5 秒。
        //
        // 【2026-09-14 按原版纠正 · VCI/VCO 是"站在网络视角"命名的】
        //   原版证据：MTEHatchVacuumConveyorOutput implements IVacuumStorage 且提供 extractPacket()
        //   ⇒ VCO 是**网络的数据源**（把物品转成 CC 送进网络）；
        //   MTEHatchVacuumConveyorInput 提供 tryConsume(ItemStack)（模块从它拉料）
        //   ⇒ VCI 是**从网络读取**的那一侧；NAC 上的 VCI 把 CC 还原成真物品交给 Output Bus。
        //   lang 原文：{@code The NAC will convert items into CCs every 5 seconds and send them to its
        //   VCOs. The NAC can also convert CCs into item equivalents and send them to its Output Bus.}
        //   所以控制室是：VCO=封装(真物品→CC 进网络)，VCI=还原(CC→真物品出网络)。
        //   模块侧不受影响：模块 VCI=从网络拉料、模块 VCO=把产出推回网络（与原版一致，本来就没反）。
        boolean roomEncapsulates = room && !input;
        int period = roomEncapsulates ? ROOM_ENCAPSULATE_INTERVAL : TRANSFER_INTERVAL;
        if (now % period != VACUUM_MOVE_TICK % period) {
            return;
        }

        // ★【P3b / W2】真件 ⇄ CC 映射表就绪。
        //
        // 位置刻意压到这一行（真的搬件那一 tick 之前）：未染色 / 不在 NAC 上 / 没成形 / 相位不对的 tick
        // 一次都不查 —— 未染色的仓仍然"什么都不做"（与 check_vacuum_routing.js ⑧ 的口径一致）。
        // 命中缓存时开销 = 两次引用比较（配方管理器换了才重建，见 NanochipConversionMap.ensure）。
        NanochipConversionMap.ensure(level.getServer());
        NanochipVacuumNetwork net = nac.vacuumNetwork();
        NanochipColor color = myColor;   // 上面已挡掉未染色（color == null 时早就 return 了）
        // ★【2026-09-16 双模式路由】本端口这一 tick 走哪条"子通道"：
        //   简单模式（默认 vacuumRequiresPipes=false）⇒ 恒 0 = 老行为（同色全局对接，零回归）；
        //   管道模式 ⇒ 问 NAC 的管网图：同色管道连通分量（旁边没同色管道 = 0，照样是"没铺管道那一池"）。
        // 分量号每 tick 只查一次（查表 O(1)，脏了才 BFS），下面四个动作共用。
        final int comp = NanochipModContext.config().isVacuumRequiresPipes()
                ? nac.pipeGraph().componentOf(level, color, getPos())
                : 0;
        boolean changed;
        if (input) {
            changed = room ? roomMaterialize(net, color, comp) : modulePull(net, color, comp, module);
        } else {
            changed = room ? roomEncapsulate(net, color, comp) : modulePush(net, color, comp, module);
        }
        if (changed) {
            lastWorkTick = now; // 让"工作中"动画只在真的搬过件之后亮一小段
            nac.markVacuumDirty();
            // ★ 2026-09-19（队长：「真空输入仓里面没有真实电路」查不出原因）：
            //   本模组此前对真空网络**完全没有日志**（`debugLogs` 这个配置项甚至没人读）⇒ 出问题时
            //   只能靠猜。这里在**真的搬过件**时把"这一侧是谁、哪个颜色、该颜色通道现在有什么"打出来
            //   —— 一眼就能分辨：件是卡在通道里（显示 N CC / M PC）、还是根本没进出（不打印）。
            //   限流 1 秒一条（同一台仓），不会刷屏。
            if (now - this.lastVacuumLogTick >= 20L) {
                this.lastVacuumLogTick = now;
                NanochipModContext.logger().info(
                        "[gtlmodular] 真空搬运 @{}（{}·{}）：本次{}；该颜色通道现在 = {}",
                        getPos(),
                        room ? "控制室" : "模块",
                        input ? "输入仓 VCI（网络→真件）" : "输出仓 VCO（真件→网络）",
                        input ? "有件被还原" : "有件被封装",
                        net.summary(color));
            }
        } else {
            // 一件都没搬动 ⇒ 也要留痕（见 logNoWork 的注释）
            logNoWork(net, color, room, input);
        }
    }

    /**
     * 真空搬运日志的限流（tick）：20 = 1 秒一条（同一台仓）。
     *
     * <p>⚠️ 哨兵**不能**用 {@code Long.MIN_VALUE}：判定式是 {@code now - last < INTERVAL}，
     * 而 {@code gameTime − Long.MIN_VALUE} 在 long 上溢出成负 ⇒ 恒小于阈值 ⇒ <b>日志永远不打</b>
     * （2026-09-19 实测：这一版部署后真空日志整场 0 条，害得我把"没搬过件"当成结论）。
     */
    private long lastVacuumLogTick = -10_000L;

    /** 「无件可搬」诊断的限流（tick）：100 = 5 秒一条（同一台仓）。哨兵同 {@link #lastVacuumLogTick}。 */
    private long lastNoWorkLogTick = -10_000L;

    /**
     * ★ 2026-09-19（队长：「真空输入仓里面没有真实电路」）：「一件都没搬动」时也要说话。
     *
     * <p>此前这条路径**完全静默** ⇒ 队长只看到"输入仓是空的"，看不到"为什么空"。这里区分三种情形：
     * <ul>
     *   <li><b>控制室 VCI 提不出件、但通道非空</b>：把通道内容打出来 —— 若显示 {@code N PC}，
     *       说明那些件是**已处理(PC)**，按 NH 语义<b>不可还原</b>（{@code gt.circuitcomponent.tooltip.pc.base}）；
     *       若显示 {@code N CC} 却提不出来，那就是 bug，这条日志就是线索；</li>
     *   <li><b>控制室 VCO 有件却封装不了</b>：说明仓里的件**没有换算配方**（真件↔CC 表里查不到），
     *       它会一直留在仓里不动 —— 也打出来并点名是哪种物品；</li>
     *   <li><b>颜色不对</b>：通道总量为 0 且仓里有件 ⇒ 提示"该颜色通道是空的（同色才有对接）"。</li>
     * </ul>
     */
    private void logNoWork(NanochipVacuumNetwork net, NanochipColor color, boolean room, boolean input) {
        Level level = getLevel();
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        if (now - this.lastNoWorkLogTick < 100L) {
            return;
        }
        int slots = slotCount();
        // 仓里第一件非空物品（用于 VCO 的"封装不了"诊断）
        ItemStack first = ItemStack.EMPTY;
        for (int i = 0; i < slots; i++) {
            ItemStack st = getInventory().storage.getStackInSlot(i);
            if (!st.isEmpty()) { first = st; break; }
        }
        long channelTotal = net.totalOf(color);
        if (input && room) {
            // 控制室 VCI：提不出件
            if (channelTotal > 0L) {
                this.lastNoWorkLogTick = now;
                NanochipModContext.logger().info(
                        "[gtlmodular] 真空输入仓 @{}（控制室 VCI）一件都没还原，但通道[{}] 里现在有 = {} "
                                + "（若为「N PC」= 那些是已处理件，按 NH 语义不可还原；若为「N CC」却提不出来 ⇒ 把这条日志发给队员）",
                        getPos(), color.nameZh(), net.summary(color));
            } else if (!first.isEmpty()) {
                this.lastNoWorkLogTick = now;
                NanochipModContext.logger().info(
                        "[gtlmodular] 真空输入仓 @{}（控制室 VCI）通道[{}] 是空的，而本仓格子里还有 {} —— "
                                + "输入仓**只从网络读件**、不看自己的格子（要进网络请用**输出仓 VCO**）",
                        getPos(), color.nameZh(), first.getHoverName().getString());
            }
        } else if (!input && room && !first.isEmpty()) {
            ItemStack payload = NanochipConversionMap.encapsulate(first);
            this.lastNoWorkLogTick = now;
            NanochipModContext.logger().info(
                    "[gtlmodular] 真空输出仓 @{}（控制室 VCO）有 {} 却封装不进网络（{}）—— "
                            + "该件在「真件↔CC 换算表」里查不到对应 CC，按 NH 语义本机不动它",
                    getPos(), first.getHoverName().getString(),
                    payload.isEmpty() ? "encapsulate() 返回空" : "encapsulate() 有结果但通道拒收（16 种上限？）");
        }
    }

    private IMultiController currentController() {
        List<IMultiController> controllers = getControllers();
        return controllers.isEmpty() ? null : controllers.get(0);
    }

    /**
     * 本仓当前可用的格数，并顺手把尺寸对齐到 {@link #SLOTS}。
     *
     * <p><b>为什么必须有这个</b>：格数曾经从 9 改成 16，而 LDLib 的 {@code ItemStackTransfer}
     * 是<b>把槽位数一起存进存档</b>的 —— 老存档里的仓读回来仍然是 9 格，而代码按 16 遍历，
     * 读第 9 格时 {@code validateSlotIndex} 直接抛
     * {@code RuntimeException: Slot 9 not in valid range - [0,9)}，把整个服务器 tick 打断（崩服）。
     * 2026-09-14 实测崩了三次。这里在每次搬运前把尺寸对齐（{@code setSize}），
     * 并且所有循环都按 {@code min(SLOTS, 实际格数)} 走，双保险。
     */
    private int slotCount() {
        var storage = getInventory().storage;
        if (storage.getSlots() != SLOTS) {
            storage.setSize(SLOTS);
        }
        return Math.min(SLOTS, storage.getSlots());
    }

    /** 控制室的 VCO：真实物品 → CC（每 5 秒一次，写进网络）。 */
    private boolean roomEncapsulate(NanochipVacuumNetwork net, NanochipColor color, int comp) {
        boolean changed = false;
        int slots = slotCount();
        for (int i = 0; i < slots; i++) {
            ItemStack stack = getInventory().storage.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            // ★【P3b / W2】入网前先「封装」：真件 → 它对应的 CC 物品。
            //
            // 没有这一步，模块从通道拉到的是真件（如 gtceu:nano_cpu_chip），
            // 而模块配方要的是 gtlmodular:cc_chip_nano_cpu ⇒ 永远匹配不上。
            // NH 语义（processRealItemInputs）：查不到换算配方的件就<b>不动</b> ——
            // 所以这里也返回空 ⇒ 该件留在仓里（而不是原样塞进网络）。
            ItemStack payload = NanochipConversionMap.encapsulate(stack);
            if (payload.isEmpty()) {
                continue;
            }
            long accepted = net.insert(color, comp, payload, true);
            if (accepted > 0L) {
                getInventory().storage.extractItem(i, (int) accepted, false);
                changed = true;
            }
        }
        return changed;
    }

    /** 控制室的 VCI：CC → 真实物品（从网络读出；PC 不还原）。 */
    private boolean roomMaterialize(NanochipVacuumNetwork net, NanochipColor color, int comp) {
        boolean changed = false;
        for (int pass = 0; pass < 4; pass++) {
            ItemStack raw = net.extract(color, comp, TRANSFER_BATCH, Boolean.TRUE);
            if (raw.isEmpty()) {
                break;
            }
            // ★【P3b / W2】出网后「还原」：CC 物品 → 它代表的真件（NH：控制室把 CC 还原成真物品
            //   交给 Output Bus）。非 CC 物品（旧存档里直接存进来的真件）原样交回，不吞件。
            ItemStack got = NanochipConversionMap.materialize(raw);
            ItemStack remainder = insertIntoInventory(got);
            if (!remainder.isEmpty()) {
                net.insert(color, comp, remainder, true); // 塞不下就还回通道
                break;
            }
            changed = true;
        }
        return changed;
    }

    /** 模块输入仓：从通道取件（组装矩阵只吃 PC）。 */
    private boolean modulePull(NanochipVacuumNetwork net, NanochipColor color, int comp,
                               NanochipModuleMachine module) {
        Boolean filter = module != null
                && module.getModuleKind() == NanochipModuleKind.ASSEMBLY_MATRIX
                ? Boolean.FALSE   // 组装矩阵只接受经模块精炼过的 PC
                : null;           // 其它模块 CC / PC 都行
        boolean changed = false;
        for (int pass = 0; pass < 4; pass++) {
            ItemStack got = net.extract(color, comp, TRANSFER_BATCH, filter);
            if (got.isEmpty()) {
                break;
            }
            ItemStack remainder = insertIntoInventory(got);
            if (!remainder.isEmpty()) {
                net.insert(color, comp, remainder, filter == Boolean.FALSE ? Boolean.FALSE : Boolean.TRUE);
                break;
            }
            changed = true;
        }
        return changed;
    }

    /** 模块输出仓：产出 → 通道（CC 件按「自己是不是已处理」记 CC/PC；非 CC 件退回旧判定）。 */
    private boolean modulePush(NanochipVacuumNetwork net, NanochipColor color, int comp,
                               NanochipModuleMachine module) {
        // 旧判定：非 CC 物品（真件 / 其它）沿用原规则 —— 只有组装矩阵的产出算 CC。
        boolean legacyMaterializable = module != null
                && module.getModuleKind() == NanochipModuleKind.ASSEMBLY_MATRIX;
        boolean changed = false;
        int slots = slotCount();
        for (int i = 0; i < slots; i++) {
            ItemStack stack = getInventory().storage.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            // ★【P3b / W2】分级标志改由<b>件本身</b>决定：cc_*（未处理）可还原、cc_processed_*（PC）不可还原
            //   —— 这正是 NH 的 cc.base / pc.base 两条 tooltip 的语义，比"看是哪个模块"更自证，
            //   也让"同一个模块既产 CC 又产 PC"这种情况天然正确。
            Boolean own = NanochipConversionMap.materializableFlag(stack);
            boolean materializable = own != null ? own : legacyMaterializable;
            long accepted = net.insert(color, comp, stack, materializable);
            if (accepted > 0L) {
                getInventory().storage.extractItem(i, (int) accepted, false);
                changed = true;
            }
        }
        return changed;
    }

    /** 把物品塞进自己的仓里，返回塞不下的部分。 */
    private ItemStack insertIntoInventory(ItemStack stack) {
        ItemStack remainder = stack;
        int slots = slotCount();
        for (int i = 0; i < slots && !remainder.isEmpty(); i++) {
            remainder = getInventory().storage.insertItem(i, remainder, false);
        }
        return remainder;
    }

    /** 给 NAC 控制室用：本仓当前通道摘要（聚合该颜色所有分量）；未染色时返回空串。 */
    public String channelSummary(NanochipVacuumNetwork net) {
        NanochipColor c = color();
        return net == null || c == null ? "" : net.summary(c);
    }

    /**
     * 本端口当前的分量号（管道模式才有意义；简单模式恒 0）。
     *
     * <p>给界面/自检显示用 —— 玩家在管道模式下要能看出"我这个仓到底连到了哪一段管网"。
     */
    public int routeComponent() {
        if (!NanochipModContext.config().isVacuumRequiresPipes()) {
            return 0;
        }
        NanochipColor c = color();
        NanochipAssemblyComplexMachine nac = hostComplex();
        Level level = getLevel();
        // 未染色的仓不在任何颜色的管网里（componentOf 对 null 颜色也返回 UNWIRED）。
        if (nac == null || level == null) {
            return 0;
        }
        return nac.pipeGraph().componentOf(level, c, getPos());
    }

    /** 当前路由模式的一行文案（界面/自检用；管道模式下顺带报本仓的分量号）。 */
    public String routingModeText() {
        if (color() == null) {
            return "§7路由模式：§c未染色§7（未染色不连接、不搬运；先用螺丝刀右键或「真空传送」页染色）";
        }
        if (!NanochipModContext.config().isVacuumRequiresPipes()) {
            return "§7路由模式：§a简单（颜色即线，不用铺管道）";
        }
        int comp = routeComponent();
        return comp == NanochipPipeGraph.UNWIRED
                ? "§7路由模式：§e管道定向§7 · 本仓§c未接管道§7（只能与同样未接管道的同色仓互通）"
                : "§7路由模式：§e管道定向§7 · 本仓已在同色管网分量 §f#" + comp;
    }

    /** 清空本仓所属颜色的整条通道（对应原版端口界面的「直接清空销毁」）；未染色时无事可做。 */
    public void clearChannel() {
        NanochipColor c = color();
        NanochipAssemblyComplexMachine nac = hostComplex();
        if (c != null && nac != null && !getLevel().isClientSide) {
            nac.vacuumNetwork().clearChannel(c);
            nac.markVacuumDirty();
        }
    }

    // ---------- 界面 ----------

    @Override
    public void attachConfigurators(ConfiguratorPanel panel) {
        super.attachConfigurators(panel);
        // 只挂【一个】页：颜色 + 纳米校准 合并（标签图标是当前颜色的喷漆罐）。
        // 原来挂两页，加上 GTCEu/gtlcore 自带的「已恢复工作」「总线隔离」等，左列会挤到互相压住。
        panel.attachConfigurators(new VacuumConfigurator(this));
        // 分流器规则页也挂在这里：规则属于「分流器模块」，而模块是多方块、没法挂配置页，
        // 所以通过模块结构里的这个仓室来编辑它（仓室走的是带配置面板的 fancy UI）。
        IMultiController controller = currentController();
        if (controller instanceof NanochipModuleMachine module
                && module.getModuleKind().isRouter()) {
            panel.attachConfigurators(new NanochipModuleMachine.SplitterConfigurator(module));
        }
        // 全板浸没装置的「排液阈值」也做在这里（同一个原因：模块是多方块、挂不了配置页）
        if (controller instanceof NanochipModuleMachine module
                && module.getModuleKind() == NanochipModuleKind.BOARD_PROCESSOR) {
            panel.attachConfigurators(new BoardProcessorConfigurator(module));
        }
    }

    /**
     * 全板浸没装置的浸泡液页：显示当前杂质 + 调自动排液阈值。
     *
     * <p>原版：{@code Set an ejection threshold in the GUI to automatically flush the fluid out
     * to be processed, which is 100% by default}。到阈值就排液、杂质归零。
     */
    static final class BoardProcessorConfigurator implements IFancyConfigurator {
        private final NanochipModuleMachine module;

        BoardProcessorConfigurator(NanochipModuleMachine module) {
            this.module = module;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gtlmodular.nanochip.board.title");
        }

        @Override
        public IGuiTexture getIcon() {
            return new ResourceTexture("gtceu:textures/item/portable_debug_scanner.png");
        }

        @Override
        public List<Component> getTooltips() {
            return List.of(Component.translatable("gtlmodular.nanochip.board.title"));
        }

        @Override
        public Widget createConfigurator() {
            WidgetGroup group = new WidgetGroup(0, 0, 124, 66);
            group.addWidget(new LabelWidget(4, 2,
                    Component.translatable("gtlmodular.nanochip.board.title").getString()));
            // 【LDLib 文本坑 · 2026-09-14】LabelWidget 的 String / Supplier&lt;String&gt; 文本会走
            // LDLib → LocalizationUtils.format → I18n.get(文字, args) 的本地化路径，
            // 等于把我们的文案当成**格式化模板**再 format 一次；文案里只要有落单的 ASCII '%'
            // 就会抛 IllegalFormatException，Minecraft 于是整串渲染成 "Format error: <原文>"
            // （同一页里不含 '%' 的「历史 0 / 100000 个电路」就完全正常）。
            // 所以：交给 LDLib 控件的文案里，百分号一律用**全角 ％（U+FF05）**，不要再改回 '%'。
            // 想看纯 ASCII '%' 就用 Component.literal(...) 走组件路径（那条路不本地化）。
            group.addWidget(new LabelWidget(4, 16, () -> String.format(
                    java.util.Locale.ROOT, "杂质: %.2f％（EU ×%.3f）",
                    module.impurity(), module.impurityEuMultiplier())));
            group.addWidget(new LabelWidget(4, 32,
                    Component.translatable("gtlmodular.nanochip.board.flush").getString()));
            group.addWidget(new IntInputWidget(4, 44, 52, 16,
                    module::autoFlushThreshold, module::setAutoFlushThreshold)
                    .setMin(1).setMax(100)
                    .setHoverTooltips(Component.translatable("gtlmodular.nanochip.board.flush.tip")));
            group.addWidget(new LabelWidget(62, 48, "％"));
            return group;
        }
    }

    /** 找到本仓所属的复合体（没有则 null）。 */
    private NanochipAssemblyComplexMachine hostComplex() {
        IMultiController controller = currentController();
        if (controller instanceof NanochipAssemblyComplexMachine nac) {
            return nac;
        }
        if (controller instanceof NanochipModuleMachine module) {
            return module.getHost();
        }
        return null;
    }

    /**
     * 纳米校准图形页：总历史进度条 + 9 个电路族的分段条
     * （对应原版那根多色进度条：{@code The multi-colored progress bar roughly shows the amount /
     * percentage of each circuit type in the past 100,000 circuits}）。
     *
     * <p>用 {@code ProgressWidget(DoubleSupplier, ...)} 驱动，所以是**实时**的。
     */
    static final class CalibrationConfigurator implements IFancyConfigurator {
        private final NanochipVacuumHatchMachine machine;

        CalibrationConfigurator(NanochipVacuumHatchMachine machine) {
            this.machine = machine;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gtlmodular.nanochip.calibration.title");
        }

        @Override
        public IGuiTexture getIcon() {
            return new ResourceTexture("gtceu:textures/item/portable_debug_scanner.png");
        }

        @Override
        public List<Component> getTooltips() {
            return List.of(Component.translatable("gtlmodular.nanochip.calibration.title"));
        }

        @Override
        public Widget createConfigurator() {
            return calibrationSection(machine);
        }

        /**
         * 纳米校准区（合并页复用）：<b>2026-09-21（0.1.24）起只剩转发</b> ——
         * 版面（152 宽 / 10px 行距 / 8 族配色【2026-09-21 队长令「第5个是合并成一条」后
         * 原 9 段里的「特殊」并入「超因果」】/ 分母行）与配色已抽到
         * {@link NanochipCalibrationPanel#section}：队长要求"主机那边也要看到这张页"，
         * 而两处各写一份早晚会有一处改漏。签名保持不变（{@code VacuumConfigurator} 的合并页仍在调它）。
         *
         * <p>★★ 0.3.132【校准面板真值转发 · 修】★★（B22 的<b>第二实例</b>：同一缺陷出现在真空仓页）
         * 改前这里只传 <b>4 个</b>实参 ⇒ Java 重载解析落到面板那条「读不到真值」的 4 参兜底重载，
         * 而它把「生效族 / 判定分母 / 每族数量」三样<b>写死</b>（{@code -1 / 1.0 / 0L}）
         * ⇒ 结构里真空仓这张页「族数量」全显示 0，且「当前生效 / 本档功效 / 还差 N 个」缺真值而不准。
         * ⇒ 现在照主机 {@code NanochipAssemblyComplexMachine:8091-8096} 的<b>同一个范本</b>传 <b>9 个</b>实参；
         * 本仓是**部件**、真值在**主机**上 ⇒ 一律经 {@link #hostComplex()} 转发主机的
         * {@code @DescSynced} 只读解析器 ⇒ 真空仓页与主机面板是<b>同一份真值</b> ✓
         * 末尾的 {@code clearAction} 仍传 {@code null}（清除只属于主机，见面板 javadoc）✓
         */
        static WidgetGroup calibrationSection(NanochipVacuumHatchMachine machine) {
            return NanochipCalibrationPanel.section(machine::historyProgress, machine::historyCount,
                    machine::familyShare, machine::usesFullWindowDenominator,
                    machine::activeFamilyOrdinal, machine::calibrationDenominator,
                    machine::familyCount, machine::familyTier,
                    null);
        }
    }

    /**
     * 当前校准判定分母是否 = 原版硬编码的 100000。
     *
     * <p>优先用复合体同步下来的口径（保证与真正判定用的分母一致）；找不到复合体时退回本机配置。
     */
    public boolean usesFullWindowDenominator() {
        NanochipAssemblyComplexMachine nac = hostComplex();
        return nac == null ? NanochipCalibration.useFullWindowDenominator()
                : nac.syncedUsesFullWindowDenominator();
    }

    /** 历史窗口填充度（给进度条用；找不到复合体时为 0）。 */
    public double historyProgress() {
        NanochipAssemblyComplexMachine nac = hostComplex();
        return nac == null ? 0.0D : nac.syncedHistoryProgress();
    }

    /** 历史窗口里的电路总数。 */
    public long historyCount() {
        NanochipAssemblyComplexMachine nac = hostComplex();
        return nac == null ? 0L : nac.syncedHistoryTotal();
    }

    /** 第 {@code ordinal} 个电路族的占比（0~1）。 */
    public double familyShare(int ordinal) {
        NanochipAssemblyComplexMachine nac = hostComplex();
        return nac == null ? 0.0D : nac.syncedShareOf(ordinal);
    }

    // ---------- ★ 0.3.132【真值转发 · 修】面板剩下的三个读数（全部经主机转发，与主机面板同一份真值） ----------
    //
    //   为什么全部要转发：本仓是**部件**，校准数据本体（syncedFamilyCounts / syncedFamilyTiers /
    //   syncedHistoryTotal / syncedActiveFamilyOrdinal / syncedFullWindowDenominator）都是
    //   **主机**上的 @DescSynced 字段；本机一份都没有 ⇒ 只能经 hostComplex() 去问主机。
    //   范本：主机 NanochipAssemblyComplexMachine:8091-8096 把
    //   `this::syncedActiveFamilyOrdinal / this::syncedCalibrationDenominator /
    //    this::syncedFamilyCount / this::syncedTierOf` 交给面板，这里逐字照抄同一套（只是多了个 host 中转）。
    //
    //   ⚠ 拿不到主机（hostComplex() == null，例如结构没成型）时：一律给**可区分的哨兵**，
    //     **绝不**回落到 0 / 1.0 这种会冒充真值的数（B22 的纪律：宁可"未知"，也不要假的 0）。

    /**
     * 服务端同步下来的「生效族」ordinal（{@code -1} = 没有任何族达档；拿不到主机时同样是 -1 = 未知）。
     *
     * <p>与主机 {@code NanochipAssemblyComplexMachine:2310} 的 {@code syncedActiveFamilyOrdinal()}
     * 同一份同步值 ⇒ **不在客户端复算**（判定规则见那个字段的 javadoc）。
     */
    public int activeFamilyOrdinal() {
        NanochipAssemblyComplexMachine nac = hostComplex();
        return nac == null ? -1 : nac.syncedActiveFamilyOrdinal();
    }

    /**
     * 本刻真正使用的判定分母（{@code fullWindow ? 100000 : 历史总数}）。
     *
     * <p>拿不到主机时返回 {@code 0.0} = <b>未知</b>（面板对 {@code <= 0} 已有既有守卫：
     * 族行悬停走「（窗口里还没有记录）」、{@code nextTierLine} 直接返回空串 ⇒ 不会显示假的"还差 N 个"）。
     * 与主机 {@code NanochipAssemblyComplexMachine:2320} 的 {@code syncedCalibrationDenominator()} 同源。
     */
    public double calibrationDenominator() {
        NanochipAssemblyComplexMachine nac = hostComplex();
        return nac == null ? 0.0D : nac.syncedCalibrationDenominator();
    }

    /**
     * 第 {@code ordinal} 族在<b>本机同步值</b>里的现有数量。
     *
     * <p>与主机 {@code NanochipAssemblyComplexMachine:2325} 的 {@code syncedFamilyCount(int)} 同源
     * （后者又逐字转发 {@code syncedCountOf}）。拿不到主机时返回 <b>-1 = 未知</b> ——
     * 面板只在 {@code denominator > 0} 时才渲染这个数，而本类在拿不到主机时分母恒为 {@code 0.0}
     * ⇒ 这个哨兵永远不会被画成 "-1"；它存在只为<b>不冒充"0 个"</b>（防复发）。
     */
    public long familyCount(int ordinal) {
        NanochipAssemblyComplexMachine nac = hostComplex();
        return nac == null ? -1L : nac.syncedFamilyCount(ordinal);
    }

    /**
     * 第 {@code ordinal} 族的校准档位（0 = 未达第 1 档；1..3 = 第 1..3 档）。
     *
     * <p>拿不到主机时返回 <b>-1 = 未知</b>（与面板兜底重载的哨兵同一语义）：面板每处档位消费点都按
     * {@code tier >= 1} / {@code tier < 1} 判 ⇒ 显示为「未达」，<b>不冒充第 0 档</b>。
     * 与主机 {@code NanochipAssemblyComplexMachine:2261} 的 {@code syncedTierOf(int)} 同一份同步值。
     */
    public int familyTier(int ordinal) {
        NanochipAssemblyComplexMachine nac = hostComplex();
        return nac == null ? -1 : nac.syncedTierOf(ordinal);
    }

    // ---------- 颜色：喷漆罐图标（跟随当前颜色） ----------

    /**
     * 配置页标签图标 = <b>当前颜色的喷漆罐</b>（GTCEu 自带 16 色 {@code *_dye_spray_can.png}）；
     * <b>未染色</b>时用白色喷漆罐当占位（"还没喷过漆"）。
     *
     * <p>为什么能"实时换"：{@code ConfiguratorPanel$Tab} 的图标是挂载时创建、之后不再查询
     * {@code getIcon()} 的；但 LDLib 的 {@code ResourceTexture.imageLocation} 是
     * <b>public 可变字段</b>，按钮每帧绘制时都会读它 —— 所以只要持有同一个实例、
     * 在换色时改这个字段，标签图标就立刻跟着变。
     *
     * <p>（原版就是「端口和管道均需染色，可使用无限喷漆罐」：GT5U {@code gt.behaviour.paintspray.*}。）
     */
    static final class SprayCanIcon extends ResourceTexture {
        SprayCanIcon(NanochipColor color) {
            super(textureOf(color));
        }

        void setFor(NanochipColor color) {
            this.imageLocation = new ResourceLocation(textureOf(color));
        }

        /** 未染色（{@code null}）⇒ 白色喷漆罐（有颜色就给那个颜色的罐子）。 */
        private static String textureOf(NanochipColor color) {
            return (color == null ? NanochipColor.WHITE : color).sprayCanTexture();
        }
    }

    private static void syncIcon(SprayCanIcon icon, NanochipColor color) {
        if (icon != null) {
            icon.setFor(color);
        }
    }

    /**
     * 颜色区：当前颜色 + 4×4 色板 + 一行换色提示。
     *
     * <p><b>为什么宽度是 152</b>：配置页面板是以 {@code new ConfiguratorPanel(-26, UI高度)} 挂在
     * 机器界面左下角、**向上展开**的（GTCEu {@code FancyMachineUIWidget} 里已核对），
     * 所以<b>页面越矮，面板顶边越低</b>。把这一区加宽到 152 之后，
     * 「螺丝刀右键循环换色」（约 90px）就能放回<b>一行</b>，省掉一行高度。
     */
    static WidgetGroup colorSection(NanochipVacuumHatchMachine machine, SprayCanIcon icon) {
        WidgetGroup group = new WidgetGroup(0, 0, 152, 108);
        // 未染色（NH：colorization == -1）时显示"未染色"，不给任何色板打选中边。
        group.addWidget(new LabelWidget(4, 2, () -> {
            NanochipColor cur = machine.color();
            return cur == null
                    ? Component.translatable("gtlmodular.nanochip.vacuum.color.unpainted").getString()
                    : Component.translatable("gtlmodular.nanochip.vacuum.color.current",
                            Component.literal(cur.nameZh()).withStyle(s -> s.withColor(cur.textColor())))
                    .getString();
        }));
        for (int i = 0; i < 16; i++) {
            NanochipColor option = NanochipColor.fromIndex(i);
            int x = 4 + (i % 4) * 23;
            int y = 16 + (i / 4) * 20;
            ColorRectTexture swatch = new ColorRectTexture();
            swatch.color = 0xFF000000 | option.textColor();
            // 选中的颜色加一圈白边（不要用不透明的 ColorRectTexture 去叠，那会把色块涂黑）
            IGuiTexture texture = option == machine.color()
                    ? new GuiTextureGroup(swatch, new ColorBorderTexture(1, 0xFFFFFFFF))
                    : swatch;
            group.addWidget(new ButtonWidget(x, y, 20, 16, texture, data -> {
                machine.setColor(option);
                syncIcon(icon, option); // 标签图标跟着换色
            }).setHoverTooltips(Component.literal(option.nameZh())));
        }
        group.addWidget(new LabelWidget(4, 96,
                Component.translatable("gtlmodular.nanochip.vacuum.color.hint").getString()));
        // 原版端口界面上那两个动作之一：「也可在确认不再需要时直接清空销毁」
        // （另一个"弹出到 ME 存储元件"需要实体化虚拟件，本移植版没有实体 CC/PC 物品，暂缺）
        group.addWidget(new ButtonWidget(100, 92, 48, 16, new com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture(0xFF3A3A3A),
                data -> machine.clearChannel())
                .setHoverTooltips(Component.translatable("gtlmodular.nanochip.vacuum.color.clear.tip")));
        return group;
    }

    /**
     * <b>真空传送带</b>页（颜色 + 纳米校准<b>合并成一页</b>）。
     *
     * <p>合并原因：GTCEu/gtlcore 自己已经挂了「已恢复工作」「总线隔离」等页，我们原来再挂
     * 「颜色」「纳米校准」两页 → 左边那列挤成 5 个以上、互相压住（而且两页还用了同一个图标）。
     * 现在本模组在仓室上只占<b>一个</b>标签；分流器规则 / 全板浸没排液阈值那两个页只在对应
     * 模块上才额外出现（多方块本体挂不了配置页，只能挂在结构里的仓室上）。
     */
    static final class VacuumConfigurator implements IFancyConfigurator {
        private final NanochipVacuumHatchMachine machine;
        private final SprayCanIcon icon;

        VacuumConfigurator(NanochipVacuumHatchMachine machine) {
            this.machine = machine;
            this.icon = new SprayCanIcon(machine.color());
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gtlmodular.nanochip.vacuum.title");
        }

        @Override
        public IGuiTexture getIcon() {
            return icon;
        }

        @Override
        public List<Component> getTooltips() {
            return List.of(Component.translatable("gtlmodular.nanochip.vacuum.title"));
        }

        @Override
        public Widget createConfigurator() {
            WidgetGroup color = colorSection(machine, icon);
            WidgetGroup calibration = CalibrationConfigurator.calibrationSection(machine);
            int w = Math.max(color.getSize().width, calibration.getSize().width);
            int colorH = color.getSize().height;
            // 面板是以机器界面左下角为锚点、**向上展开**的（见 colorSection 的说明），
            // 所以这里刻意不留顶部空白：页高越小，面板顶边越低（越"往下"）。
            final int gap = 6;
            WidgetGroup root = new WidgetGroup(0, 0, w, colorH + gap + calibration.getSize().height);
            color.setSelfPosition(0, 0);
            calibration.setSelfPosition(0, colorH + gap);
            root.addWidget(color);
            root.addWidget(calibration);
            return root;
        }
    }
}
