package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gtlmodular.common.config.GTLModularConfig;
import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 真空传送仓（VCI/VCO）的<b>颜色 / 染色 tint / autoIO / 配置页 / NBT 只读与纯算部分</b>
 * —— P4 M6-prep（2026-11-08）从 {@link NanochipVacuumHatchMachine} <b>逐字搬出</b>。
 *
 * <p>本类<b>不持有机器状态</b>：所有方法都是静态的纯读 / 纯算 / 纯构造，机器作为参数传入。
 * 留在机器里的仍然是"要写字段"的那些：{@code colorIndex} 的写入、{@code autoIO()} 的实际搬货、
 * {@code syncPaintingColor()} 的 {@code super.setPaintingColor(...)}、NBT 的读写、
 * {@link NanochipVacuumHatchMachine#tickVacuum()} 的真空核心（<b>整体未动，且本轮不接回</b>）。
 *
 * <p>颜色语义不变：<b>16 色 + 未染色（null）</b>；{@code getColor/tint} 的返回值与调用点不变。
 * NBT 键 {@code GTLModularHatchColor} 与所有 {@code @DescSynced} 字段一律未动。
 *
 * <p>注：{@code routeComponent / routingModeText} 依赖机器私有的 {@code hostComplex()}，
 * 所以由机器侧把 {@code nac} 作为参数传进来（不复制那套"控制室/模块→主机"的判定）。
 */
public final class NanochipVacuumIo {

    private NanochipVacuumIo() {
    }

    // ==========================================================================================
    // 颜色判定 / 染色 tint
    // ==========================================================================================

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
    static boolean canConnectOnSide(Direction dir, Direction front) {
        return dir != null && dir == front;
    }

    /**
     * ★【2026-09-15 第二轮】本机颜色 → GT 的"喷漆色"值 —— <b>纯显示</b>，与渲染无关。
     *
     * <p>读它的只有 Jade/TOP 的 {@code StainedColorProvider}（打出 {@code 颜色: #xxxxxxxx}）
     * 与存档 NBT；{@code tintColor(int)} 只读 {@code color()}（{@code colorIndex}），
     * <b>从不</b>读 {@code getPaintingColor()} ⇒ 写这个字段碰不到外观。
     *
     * <p>用 {@link NanochipColor#paintArgb()}（不透明 ARGB）而不是 RGB：GTCEu 打印时用
     * {@code Integer.toHexString(...).toUpperCase()} 且<b>不补前导零</b>，传 RGB 会让绿色
     * {@code 0x00FF00} 显示成 {@code #FF00}；传 ARGB 恒 8 位，例如绿 = {@code #FF00FF00}。
     */
    static int paintingArgb(NanochipColor color) {
        // 未染色 ⇒ 复位成 GTCEu 自己的默认喷漆色，别在提示里显示一个假颜色名。
        return color == null ? 0xFFFFFFFF : color.paintArgb();
    }

    /** 只为日志用：记录 GTCEu 实际请求过的 tintindex，确认哪个索引属于外壳、哪个属于覆盖层。 */
    private static final Set<Integer> TINT_INDEX_SEEN = ConcurrentHashMap.newKeySet();

    /**
     * ★【2026-09-16 回退 + 纪律】<b>允许吃染料的 tintindex 白名单 —— 只有前脸 overlay</b>。
     *
     * <p>为什么要有这个常量：上一轮为了让"染色看得见"，把 index 1（GTCEu 的**外壳/机身**层）
     * 也接上了染料 ⇒ **整个端口方块全绿**，违反了用户从第一天起定的核心规格：
     * <blockquote>「染色的时候，<b>外壳颜色不变</b>，只是那个<b>白色的正脸</b>那个会变，
     * 就是<b>被黑色像素包围到内侧的那一圈</b>，<b>白色像素</b>会变」「正脸黑框里<b>只有白色像素</b>会改变」</blockquote>
     * ⇒ 现回退成"只有 {@code TINT_OVERLAY} 吃染料"，并把这条写成**显式白名单**：
     * <b>外壳与其它任何层永不进入该白名单</b>。以后谁想"让它看得见"，请走"前脸两层的
     * 前后顺序 / 渲染刷新"那条路（见 `_census/hatch_tint_diagnosis.md` 与报告 §49），**不许再染外壳**。
     */
    private static final Set<Integer> DYEABLE_TINT_INDEXES = Set.of(NanochipVacuumHatchMachine.TINT_OVERLAY);

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
     * 我们的 overlay 模型把那一面标成 {@code TINT_OVERLAY}（= 前脸）。
     *
     * <p><b>★ 2026-09-15 第二轮更正（子代理逐字节取证 + 玩家实测"染了不变色"）</b>：
     * 真正的情况是：{@code hull_machine.json} 的六个面**全都是 {@code tintindex:1}**，而我们把
     * index 1 判成了"保持原色" ⇒ <b>6 个面里有 5 个从设计上永远不可能变色</b>；而前脸那两层里，
     * **不吃染料的 {@code #pipe_port} 被放在 z=−0.002（更靠外）**、吃染料的 {@code #overlay} 在
     * z=−0.001 ⇒ 可见的"纯白像素"只剩约 44 个，染色几乎看不出来。
     * 原版依据：{@code MACHINE_CASINGS[tier][mIndex+1] = TextureFactory.of(bottom, top, side, Dyes.getRGBA())}
     * —— <b>原版就是"整个机壳吃染料"</b>。所以这里改成 <b>{@code TINT_OVERLAY} 与 1（外壳）都返回染料</b>，
     * 其余（含 {@code -1}）仍不染。
     * <p>仍不影响结构判定与其它机器：{@code tintColor} 只在渲染线程被 GTCEu 的 {@code colorTinted} 调用。
     */
    static int tintColor(NanochipVacuumHatchMachine machine, int index) {
        if (TINT_INDEX_SEEN.add(index)) {
            // 探针：确认 GTCEu 请求过哪些 index（2=前脸，吃染料；1=外壳，**明确拒绝**）
            com.gtlmodular.GTLModular.LOGGER.debug(
                    "[gtlmodular] 真空传送 tintindex={} 请求染色（白名单={}；其它一律保持原色）",
                    index, DYEABLE_TINT_INDEXES);
        }
        // ★ 白名单只含前脸 overlay（TINT_OVERLAY）。外壳（index 1）与其它层**恒不染**：
        //   用户规格 = 外壳颜色不变、黑像素不变、透明不动、只有正脸黑框内侧那圈白像素变色。
        if (DYEABLE_TINT_INDEXES.contains(index)) {
            NanochipColor c = machine.color();
            if (c == null) {
                return -1;   // 未染色 ⇒ 前脸保持原色（NH：colorization == -1 时调制量退化成 MACHINE_METAL）
            }
            // 用 GT5U 原版染料值（明亮），不是 MC 的文字暗色 —— 见 NanochipColor.paintRgb()
            return c.paintArgb();
        }
        return -1;
    }

    /** 机器信息浮层里加一行可读的颜色名（用该颜色自己上色；未染色时提示"未染色"并附 NH 的 tooltip 口径）。 */
    static void addColorTooltip(NanochipColor color, List<Component> tooltips) {
        if (color == null) {
            tooltips.add(Component.translatable("gtlmodular.nanochip.vacuum.color.unpainted")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltips.add(Component.translatable("gtlmodular.nanochip.vacuum.color.line",
                        Component.literal(color.nameZh()).withStyle(s -> s.withColor(color.textColor())))
                .withStyle(ChatFormatting.GRAY));
    }

    // ==========================================================================================
    // 通道路由（只读文案/读数）
    // ==========================================================================================

    /** 给 NAC 控制室用：本仓当前通道摘要（聚合该颜色所有分量）；未染色时返回空串。 */
    static String channelSummary(NanochipVacuumHatchMachine machine, NanochipVacuumNetwork net) {
        NanochipColor c = machine.color();
        return net == null || c == null ? "" : net.summary(c);
    }

    /**
     * 本端口当前的分量号（管道模式才有意义；简单模式恒 0）。
     *
     * <p>给界面/自检显示用 —— 玩家在管道模式下要能看出"我这个仓到底连到了哪一段管网"。
     */
    static int routeComponent(NanochipVacuumHatchMachine machine, NanochipAssemblyComplexMachine nac) {
        if (!GTLModularConfig.isVacuumRequiresPipes()) {
            return 0;
        }
        NanochipColor c = machine.color();
        var level = machine.getLevel();
        // 未染色的仓不在任何颜色的管网里（componentOf 对 null 颜色也返回 UNWIRED）。
        if (nac == null || level == null) {
            return 0;
        }
        return nac.pipeGraph().componentOf(level, c, machine.getPos());
    }

    /** 当前路由模式的一行文案（界面/自检用；管道模式下顺带报本仓的分量号）。 */
    static String routingModeText(NanochipVacuumHatchMachine machine, NanochipAssemblyComplexMachine nac) {
        if (machine.color() == null) {
            return "§7路由模式：§c未染色§7（未染色不连接、不搬运；先用螺丝刀右键或「真空传送」页染色）";
        }
        if (!GTLModularConfig.isVacuumRequiresPipes()) {
            return "§7路由模式：§a简单（颜色即线，不用铺管道）";
        }
        int comp = routeComponent(machine, nac);
        return comp == NanochipPipeGraph.UNWIRED
                ? "§7路由模式：§e管道定向§7 · 本仓§c未接管道§7（只能与同样未接管道的同色仓互通）"
                : "§7路由模式：§e管道定向§7 · 本仓已在同色管网分量 §f#" + comp;
    }

    // ==========================================================================================
    // IO（构造 + 能力面）
    // ==========================================================================================

    /**
     * ★★ 2026-09-19（队长：「真空输入塞不进去东西呀」）—— <b>本方法只放宽"外部（玩家/管道/漏斗/AE）能不能插"</b>。
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
     * 而控制室上「真物品 → CC」的正门恰恰是 <b>VCO</b>，VCI 是「CC → 真物品」的还原口
     * ⇒ 想用 AE/管道喂料的人会撞死在 VCO 上。
     *
     * <h3>修法（只放宽插入，不减任何既有能力）</h3>
     * <ul>
     *   <li>{@code handlerIO} 仍是 {@code io} ⇒ <b>配方/物流语义一个字不变</b>；</li>
     *   <li>VCO 的 {@code capabilityIO} 由 {@code OUT} 放宽成 {@code BOTH} ⇒ 自动化可以往里插真物品；
     *       <b>原来就能抽</b>（OUT 允许 extract）现在仍然能抽 ⇒ 没有任何能力被拿掉；</li>
     *   <li>VCI 的 {@code capabilityIO} 保持 {@code IN} ⇒ "只能往里塞、不能被别人抽走"这条不变。</li>
     * </ul>
     * <p>残余风险（如实登记）：放宽后，自动化也能往<b>模块</b>的 VCO 里塞东西（此前只有 GUI 能塞），
     * 这些件会被 {@code modulePush} 按"件自身的 processed 状态"推回通道（见
     * {@code NanochipConversionMap.materializableFlag}）。要彻底避免，请在 AE/管道侧只对
     * <b>控制室的 VCO</b> 下料。
     */
    static NotifiableItemStackHandler createInventory(NanochipVacuumHatchMachine machine, IO io) {
        // 三参构造会把 capabilityIO 也设成 io；这里显式给第四参：VCO 放开成 BOTH（可插可抽），VCI 维持 IN。
        return new NotifiableItemStackHandler(machine, NanochipVacuumHatchMachine.SLOTS, io,
                io == IO.OUT ? IO.BOTH : io);
    }

    // ==========================================================================================
    // 配置页
    // ==========================================================================================

    /**
     * 挂配置页（机器侧 {@code attachConfigurators} 里调；{@code super.attachConfigurators(panel)} 仍在机器侧）。
     *
     * <p>只挂【一个】页：颜色 + 纳米校准 合并（标签图标是当前颜色的喷漆罐）。
     * 原来挂两页，加上 GTCEu/gtlcore 自带的「已恢复工作」「总线隔离」等，左列会挤到互相压住。
     */
    static void attachConfigurators(NanochipVacuumHatchMachine machine, ConfiguratorPanel panel,
                                   IMultiController controller) {
        panel.attachConfigurators(new VacuumConfigurator(machine));
        // 分流器规则页也挂在这里：规则属于「分流器模块」，而模块是多方块、没法挂配置页，
        // 所以通过模块结构里的这个仓室来编辑它（仓室走的是带配置面板的 fancy UI）。
        if (controller instanceof NanochipModuleMachine module
                && module.getModuleKind().isRouter()) {
            panel.attachConfigurators(new NanochipModuleMachine.SplitterConfigurator(module));
        }
        // 全板浸没装置的「排液阈值」也做在这里（同一个原因：模块是多方块、挂不了配置页）
        if (controller instanceof NanochipModuleMachine module
                && module.getModuleKind() == NanochipModuleKind.BOARD_PROCESSOR) {
            panel.attachConfigurators(new NanochipVacuumHatchMachine.BoardProcessorConfigurator(module));
        }
    }

    // ==========================================================================================
    // 颜色：喷漆罐图标 + 色板
    // ==========================================================================================

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

    static void syncIcon(SprayCanIcon icon, NanochipColor color) {
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
        group.addWidget(new ButtonWidget(100, 92, 48, 16, new ColorRectTexture(0xFF3A3A3A),
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
        public WidgetGroup createConfigurator() {
            WidgetGroup color = colorSection(machine, icon);
            WidgetGroup calibration = NanochipVacuumHatchMachine.CalibrationConfigurator.calibrationSection(machine);
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
