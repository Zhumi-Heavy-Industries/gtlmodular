package com.gtlmodular.common.machine.requester;

import appeng.api.stacks.AEFluidKey;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

/**
 * 「数量 / 并发」弹层：点格子后<b>贴在该格旁边</b>弹出，按格定锚，收起即无。
 *
 * <h2>版式照 ME 输入总成的 {@code AEDualAmountSetWidget}</h2>
 * <pre>
 *   背景  GuiTextures.BACKGROUND（与参照控件同一张底图）
 *   标签  数量 / 并发（流体格带 mB 提示，颜色 0x404040）
 *   输入  GuiTextures.DISPLAY 底 + 无边框 TextFieldWidget
 * </pre>
 * 参照控件是 80×30 单字段；补货牌要同时给「目标量」和「单次批量」两个值，所以是 74×42 双行,
 * 但底图、字号、输入框观感与它一致。
 *
 * <h2>位置为什么由自己算</h2>
 * <p>原版实现把面板钉在 {@code (GUI_WIDTH, 10)}，也就是<b>主面板右边缘之外</b>——
 * 主面板宽只有 150，面板落在窗口外面，玩家看到的是一块被窗口裁掉的东西。
 * 现在按选中格的行列算出锚点，并夹在网格范围（144×74）内，所以弹层永远在窗口里、
 * 且总是贴着被点的那一格。
 *
 * <h2>为什么两侧都不挂 textSupplier</h2>
 * <p>{@code TextFieldWidget.detectAndSendChanges()} 在 supplier 非空时会把 supplier 的值
 * 直接写回输入框并推给对端，<b>不看焦点</b>；客户端那份槽位快照不会因玩家键入而变化，
 * 于是每 tick 都会把刚输入的数字刷回去。所以这里两侧都传 {@code null}，
 * 由 {@link #openFor(int)} 在打开时把当前值填进输入框（纯客户端显示）。
 *
 * <h2>写入路径</h2>
 * <p>客户端键入 → {@code onTextChanged} → 本地 responder（{@link #apply} 里 isRemote 直接返回）
 * → {@code writeClientAction} → 服务端 {@code handleClientAction} 再跑一次 responder，
 * 这次才真正写 {@link RequesterMachine#setSlotTarget}/{@link RequesterMachine#setSlotBatch}。
 * 写入目标永远是服务端的 {@code selectedSlot}，与旧版完全一致。
 */
public class RequesterAmountBox extends WidgetGroup {

    public static final int WIDTH = 74;
    public static final int HEIGHT = 42;

    private static final int PAD = 3;
    private static final int LABEL_W = 15;
    private static final int FIELD_X = PAD + LABEL_W;
    private static final int FIELD_W = WIDTH - FIELD_X - PAD;
    private static final int FIELD_H = 14;
    /** 「数量」行输入框的 y。 */
    private static final int ROW_TARGET = 2;
    /** 「并发」行输入框的 y。 */
    private static final int ROW_BATCH = 23;

    private static final int COLUMN_PITCH = RequesterMachine.SLOT_SIZE;
    private static final int ROW_PITCH = RequesterMachine.SLOT_HEIGHT + 2;

    private final RequesterMachine machine;
    /** 网格原点（页面坐标）：弹层按它算锚点。 */
    private final int gridX;
    private final int gridY;
    private RequesterSlotGrid grid;

    /** 正在编辑的槽位（全局下标）；-1 = 收起。 */
    private int index = -1;

    private final TextFieldWidget targetField;
    private final TextFieldWidget batchField;

    public RequesterAmountBox(RequesterMachine machine, int gridX, int gridY) {
        super(new Position(gridX, gridY), new Size(WIDTH, HEIGHT));
        this.machine = machine;
        this.gridX = gridX;
        this.gridY = gridY;
        setBackground(GuiTextures.BACKGROUND);

        addWidget(new ImageWidget(FIELD_X, ROW_TARGET, FIELD_W, FIELD_H, GuiTextures.DISPLAY));
        this.targetField = createField(FIELD_X + 2, ROW_TARGET + 1, FIELD_W - 4, FIELD_H - 2, true);
        addWidget(targetField);

        addWidget(new ImageWidget(FIELD_X, ROW_BATCH, FIELD_W, FIELD_H, GuiTextures.DISPLAY));
        this.batchField = createField(FIELD_X + 2, ROW_BATCH + 1, FIELD_W - 4, FIELD_H - 2, false);
        addWidget(batchField);

        setVisible(false);
        setEditable(false);
    }

    private TextFieldWidget createField(int x, int y, int width, int height, boolean target) {
        return new TextFieldWidget(x, y, width, height, null, raw -> apply(raw, target))
                .setMaxStringLength(20)
                .setBordered(false);
    }

    public void attachGrid(RequesterSlotGrid grid) {
        this.grid = grid;
    }

    // ------------------------------------------------------------------
    // 开 / 关
    // ------------------------------------------------------------------

    /** 打开并定位到某一格（照 {@code AEDualConfigWidget.enableAmount(index)} 的语义）。 */
    public void openFor(int globalIndex) {
        if (globalIndex < 0) {
            close();
            return;
        }
        this.index = globalIndex;
        moveTo(globalIndex);
        setVisible(true);
        setEditable(true);
        if (LDLib.isRemote()) {
            RequesterSlot slot = current();
            if (slot != null && !slot.isEmpty()) {
                targetField.setCurrentString(Long.toString(slot.target()));
                batchField.setCurrentString(slot.batch() > 0L ? Long.toString(slot.batch()) : "");
            }
        }
    }

    /** 收起弹层（照 {@code disableAmount()}）。 */
    public void close() {
        if (index < 0 && !isVisible()) return;
        index = -1;
        setVisible(false);
        setEditable(false);
    }

    public boolean isEditing() {
        return index >= 0 && isVisible();
    }

    public int editingIndex() {
        return index;
    }

    private void setEditable(boolean editable) {
        if (targetField.isActive() != editable) targetField.setActive(editable);
        if (batchField.isActive() != editable) batchField.setActive(editable);
    }

    // ------------------------------------------------------------------
    // 锚点
    // ------------------------------------------------------------------

    /** 按行列把弹层贴到格边，并夹在网格范围里，保证不会跑到窗口外。 */
    private void moveTo(int globalIndex) {
        int visible = globalIndex - (machine.getPage() - 1) * RequesterMachine.PAGE_SIZE;
        if (visible < 0 || visible >= RequesterMachine.PAGE_SIZE) visible = 0;
        int col = visible % RequesterMachine.COLUMNS;
        int row = visible / RequesterMachine.COLUMNS;

        int cellX = gridX + col * COLUMN_PITCH;
        int cellY = gridY + row * ROW_PITCH;
        int maxX = gridX + RequesterSlotGrid.width() - WIDTH;
        int maxY = gridY + RequesterSlotGrid.height() - HEIGHT;

        // 优先贴在格子右侧（照参照控件「浮在格旁」的手感），放不下就翻到左侧。
        int x = cellX + RequesterMachine.SLOT_SIZE + 1;
        if (x > maxX) x = cellX - WIDTH - 1;
        setSelfPosition(new Position(clamp(x, gridX, maxX), clamp(cellY, gridY, maxY)));
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) return min;
        return Math.max(min, Math.min(value, max));
    }

    // ------------------------------------------------------------------
    // 读写
    // ------------------------------------------------------------------

    /** 客户端读自己的同步快照，服务端读真数据。 */
    private RequesterSlot current() {
        int sel = LDLib.isRemote()
                ? index
                : (machine.getSelectedSlot() >= 0 ? machine.getSelectedSlot() : index);
        if (sel < 0) return null;
        return LDLib.isRemote()
                ? (grid == null ? null : grid.snapshotOf(sel))
                : machine.slot(sel);
    }

    private void apply(String raw, boolean target) {
        if (LDLib.isRemote()) return;
        int sel = machine.getSelectedSlot();
        if (sel < 0) return;
        if (machine.slot(sel).isEmpty()) return;
        long parsed = RequesterNumbers.parse(raw);
        if (parsed < 0L) return;
        if (target) {
            machine.setSlotTarget(sel, parsed);
        } else {
            machine.setSlotBatch(sel, parsed);
        }
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        RequesterSlot slot = current();
        if (slot == null || slot.isEmpty()) close();
    }

    // ------------------------------------------------------------------
    // 绘制
    // ------------------------------------------------------------------

    @OnlyIn(Dist.CLIENT)
    @Override
    public void drawInBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        if (!isVisible()) return;
        Position pos = getPosition();
        // 标签用 0.75 倍字号：两字 CJK 约 13.5px，正好塞进 LABEL_W=15，不会压到输入框。
        // 流体格的 mB 单位不写在标签里（会挤出标签宽度），改在输入框右侧用 0.5 倍字号标。
        boolean fluid = isFluid();
        DrawerHelper.drawStringSized(graphics, "数量",
                pos.x + PAD, pos.y + ROW_TARGET + 3, 0x404040, false, 0.75F, false);
        DrawerHelper.drawStringSized(graphics, "并发",
                pos.x + PAD, pos.y + ROW_BATCH + 3, 0x404040, false, 0.75F, false);
        if (fluid) {
            int unitX = pos.x + WIDTH - PAD - 9;
            DrawerHelper.drawStringSized(graphics, "mB", unitX, pos.y + ROW_TARGET + 4,
                    0x808080, false, 0.5F, false);
            DrawerHelper.drawStringSized(graphics, "mB", unitX, pos.y + ROW_BATCH + 4,
                    0x808080, false, 0.5F, false);
        }
    }

    private boolean isFluid() {
        RequesterSlot slot = current();
        return slot != null && slot.key() instanceof AEFluidKey;
    }
}
