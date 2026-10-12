package com.gtlmodular.common.machine.nanochip;

import com.gtlmodular.common.machine.nanochip.vacuum.NanochipVacuumPort;
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
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/** VCI/VCO 以网络视角命名，控制室封装与还原方向和普通物品总线相反，不能直接沿用基类自动物流。 */
public class NanochipVacuumHatchMachine extends ItemBusPartMachine {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(NanochipVacuumHatchMachine.class,
                    ItemBusPartMachine.MANAGED_FIELD_HOLDER);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    public static final int ROOM_ENCAPSULATE_INTERVAL = 100;

    public static final int TRANSFER_INTERVAL = 20;

    public static final int VACUUM_MOVE_TICK = 17;

    public static final int SLOTS = 16;

    @DescSynced
    private int colorIndex = NanochipColor.INDEX_UNPAINTED;

    @DescSynced
    private boolean casingFromStructure;

    public boolean isCasingFromStructure() {
        return casingFromStructure;
    }

    /** 只在结构归属变化时同步外壳状态，避免每 tick 重复广播。 */
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
        refreshCasingState();
    }

    @Override
    public void removedFromController(IMultiController controller) {
        super.removedFromController(controller);
        refreshCasingState();
    }

    private final NanochipVacuumPort vacuumPort = new NanochipVacuumPort(
            this, () -> this.io, this::refreshCasingState);

    /** 动画窗口略长于搬运周期，避免持续搬运时工作动画闪断。 */
    public static final int WORK_ANIM_WINDOW = 110;

    public boolean isVacuumWorking() {
        return vacuumPort.isVacuumWorking();
    }

    private TickableSubscription vacuumTickSub;

    public NanochipVacuumHatchMachine(IMachineBlockEntity holder, int tier, IO io) {
        super(holder, tier, io);
    }

    @Override
    protected int getInventorySize() {
        return SLOTS;
    }

    /** 封装端需要外部插入真实物品，能力 IO 与配方 IO 必须分开设置。 */
    @Override
    protected NotifiableItemStackHandler createInventory(Object... args) {
        return new NotifiableItemStackHandler(this, SLOTS, io, io == IO.OUT ? IO.BOTH : io);
    }

    /** GUI 插槽还单独限制手动插入，不能只放宽外部能力接口。 */
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

    /** 未染色与白色通道必须区分，避免未喷漆的仓意外参与搬运。 */
    public NanochipColor color() {
        return NanochipColor.fromIndex(colorIndex);
    }

    public boolean isPainted() {
        return colorIndex >= 0;
    }

    public boolean canConnectOnSide(Direction dir) {
        return dir != null && dir == getFrontFacing();
    }

    public void setColor(NanochipColor color) {
        int next = color == null ? NanochipColor.INDEX_UNPAINTED : color.ordinal();
        if (next != colorIndex) {
            this.colorIndex = next;
            scheduleRenderUpdate();
            // 端口换色会改变同色连通关系，必须使管网缓存失效。
            NanochipPipeGraph.bumpTopologyEpoch();
        }
        syncPaintingColor();
    }

    /** 同步 GT 喷漆显示字段，避免外部提示与实际通道颜色不一致。 */
    private void syncPaintingColor() {
        try {
            NanochipColor c = color();
            super.setPaintingColor(c == null ? 0xFFFFFFFF : c.paintArgb());
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void setPaintingColor(int argb) {
        NanochipColor mapped = NanochipColor.ofArgb(argb);
        if (mapped != null) {
            setColor(mapped);
        }
        syncPaintingColor();
    }

    public static final int TINT_OVERLAY = 2;

    /** 只对前脸 overlay 染色，避免通道色覆盖结构机壳。 */
    private static final java.util.Set<Integer> DYEABLE_TINT_INDEXES = java.util.Set.of(TINT_OVERLAY);

    private static final java.util.Set<Integer> TINT_INDEX_SEEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public int tintColor(int index) {
        if (TINT_INDEX_SEEN.add(index)) {
            NanochipModContext.logger().debug(
                    "[gtlmodular] 真空传送 tintindex={} 请求染色（白名单={}；其它一律保持原色）",
                    index, DYEABLE_TINT_INDEXES);
        }
        if (DYEABLE_TINT_INDEXES.contains(index)) {
            NanochipColor c = color();
            if (c == null) {
                return -1;
            }
            return c.paintArgb();
        }
        return -1;
    }

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

    /** 保留仓的颜色序号读取语义，避免旧档端口失去已有颜色路由。 */
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
        syncPaintingColor();
    }

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

    @Override
    public void onLoad() {
        super.onLoad();
        Level level = getLevel();
        if (level != null && !level.isClientSide) {
            syncPaintingColor();
            unsubscribeTick();
            vacuumTickSub = subscribeServerTick(this::tickVacuum);
        }
    }

    @Override
    public void onUnload() {
        unsubscribeTick();
        NanochipPipeGraph.bumpTopologyEpoch();
        super.onUnload();
    }

    /** 卸载时显式回收订阅，避免离开世界后仍持有空跑回调。 */
    private void unsubscribeTick() {
        if (vacuumTickSub != null) {
            vacuumTickSub.unsubscribe();
            vacuumTickSub = null;
        }
    }

    /** 控制室封装端从邻居拉入，还原端向邻居推出，与基类按 IO 选方向的逻辑相反。 */
    @Override
    protected void autoIO() {
        vacuumPort.autoIO();
    }

    private void tickVacuum() {
        vacuumPort.tickVacuum();
    }

    private IMultiController currentController() {
        return vacuumPort.currentController();
    }

    public String channelSummary(NanochipVacuumNetwork net) {
        return vacuumPort.channelSummary(net);
    }

    public int routeComponent() {
        return vacuumPort.routeComponent();
    }

    public String routingModeText() {
        return vacuumPort.routingModeText();
    }

    public void clearChannel() {
        vacuumPort.clearChannel();
    }

    @Override
    public void attachConfigurators(ConfiguratorPanel panel) {
        super.attachConfigurators(panel);
        // 颜色与校准合并一页，避免附加配置图标挤满侧栏。
        panel.attachConfigurators(new VacuumConfigurator(this));
        IMultiController controller = currentController();
        if (controller instanceof NanochipModuleMachine module
                && module.getModuleSpec().isRouter()) {
            panel.attachConfigurators(new NanochipModuleMachine.SplitterConfigurator(module));
        }
        if (controller instanceof NanochipModuleMachine module
                && module.getModuleSpec().features().board()) {
            panel.attachConfigurators(new BoardProcessorConfigurator(module));
        }
    }

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
            // LabelWidget 的文本会经过格式化，百分号使用全角形式以免被当作格式占位符。
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

    private NanochipAssemblyComplexMachine hostComplex() {
        return vacuumPort.hostComplex();
    }

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

        /** 共用 NanochipCalibrationPanel，使仓室与主机的校准显示采用同一套布局和读数。 */
        static WidgetGroup calibrationSection(NanochipVacuumHatchMachine machine) {
            return NanochipCalibrationPanel.section(machine::historyProgress, machine::historyCount,
                    machine::familyShare, machine::usesFullWindowDenominator,
                    machine::activeFamilyOrdinal, machine::calibrationDenominator,
                    machine::familyCount, machine::familyTier,
                    null);
        }
    }

    public boolean usesFullWindowDenominator() {
        NanochipAssemblyComplexMachine nac = hostComplex();
        return nac == null ? NanochipCalibration.useFullWindowDenominator()
                : nac.syncedUsesFullWindowDenominator();
    }

    public double historyProgress() {
        NanochipAssemblyComplexMachine nac = hostComplex();
        return nac == null ? 0.0D : nac.syncedHistoryProgress();
    }

    public long historyCount() {
        NanochipAssemblyComplexMachine nac = hostComplex();
        return nac == null ? 0L : nac.syncedHistoryTotal();
    }

    public double familyShare(int ordinal) {
        NanochipAssemblyComplexMachine nac = hostComplex();
        return nac == null ? 0.0D : nac.syncedShareOf(ordinal);
    }

    // 校准事实源在主机，仓室只转发同步读数，避免用默认值伪造档位与族数量。

    public int activeFamilyOrdinal() {
        NanochipAssemblyComplexMachine nac = hostComplex();
        return nac == null ? -1 : nac.syncedActiveFamilyOrdinal();
    }

    public double calibrationDenominator() {
        NanochipAssemblyComplexMachine nac = hostComplex();
        return nac == null ? 0.0D : nac.syncedCalibrationDenominator();
    }

    public long familyCount(int ordinal) {
        NanochipAssemblyComplexMachine nac = hostComplex();
        return nac == null ? -1L : nac.syncedFamilyCount(ordinal);
    }

    /** 取不到主机时返回未知档位，与真实未达第一档区分。 */
    public int familyTier(int ordinal) {
        NanochipAssemblyComplexMachine nac = hostComplex();
        return nac == null ? -1 : nac.syncedTierOf(ordinal);
    }

    static final class SprayCanIcon extends ResourceTexture {
        SprayCanIcon(NanochipColor color) {
            super(textureOf(color));
        }

        void setFor(NanochipColor color) {
            this.imageLocation = new ResourceLocation(textureOf(color));
        }

        private static String textureOf(NanochipColor color) {
            return (color == null ? NanochipColor.WHITE : color).sprayCanTexture();
        }
    }

    private static void syncIcon(SprayCanIcon icon, NanochipColor color) {
        if (icon != null) {
            icon.setFor(color);
        }
    }

    /** 面板向机器界面上方展开，控制高度才能避免顶部被裁掉。 */
    static WidgetGroup colorSection(NanochipVacuumHatchMachine machine, SprayCanIcon icon) {
        WidgetGroup group = new WidgetGroup(0, 0, 152, 108);
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
            // 使用边框标识选中颜色，避免不透明底图盖住色块。
            IGuiTexture texture = option == machine.color()
                    ? new GuiTextureGroup(swatch, new ColorBorderTexture(1, 0xFFFFFFFF))
                    : swatch;
            group.addWidget(new ButtonWidget(x, y, 20, 16, texture, data -> {
                machine.setColor(option);
                syncIcon(icon, option);
            }).setHoverTooltips(Component.literal(option.nameZh())));
        }
        group.addWidget(new LabelWidget(4, 96,
                Component.translatable("gtlmodular.nanochip.vacuum.color.hint").getString()));
        group.addWidget(new ButtonWidget(100, 92, 48, 16, new com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture(0xFF3A3A3A),
                data -> machine.clearChannel())
                .setHoverTooltips(Component.translatable("gtlmodular.nanochip.vacuum.color.clear.tip")));
        return group;
    }

    /** 颜色和校准合并配置入口，减少侧栏占用。 */
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
