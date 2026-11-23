package com.gtlmodular.common.machine.requester;

import appeng.api.stacks.AEFluidKey;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
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
 * 「数量 / 并发」面板：点格子后在窗口内出现，<b>可拖动</b>。
 *
 * <h2>版式照抄 ME 输入总成的 AEDualAmountSetWidget</h2>
 * <pre>
 *   背景  GuiTextures.BACKGROUND
 *   标题  数量 / 数量(mB)，颜色 0x404040
 *   输入  DISPLAY 底 + 无边框 TextFieldWidget
 *   第二行 并发 / 并发(mB)
 * </pre>
 *
 * <h2>怎么拖</h2>
 * <p>抓住<b>标题栏</b>（顶部 11px）拖动即可；位置只存在客户端，不写存档（纯显示偏好）。
 * 位置被夹在父面板范围内，避免拖出窗口后点不到。
 *
 * <h2>为什么客户端不挂 textSupplier</h2>
 * <p>{@code TextFieldWidget.detectAndSendChanges()} 每 tick 用 supplier 的值覆盖输入框；
 * 客户端那份槽位数据未同步（{@code @Persisted} 非 {@code @DescSynced}），挂了就会把
 * 玩家刚输入的数字刷回 0。
 */
public class RequesterAmountBox extends WidgetGroup {

    public static final int WIDTH = 118;
    public static final int HEIGHT = 62;
    /** 标题栏高度：只有这块区域响应拖动。 */
    private static final int TITLE_H = 11;

    private static final int PAD_X = 3;
    private static final int FIELD_W = WIDTH - PAD_X * 2;
    private static final int FIELD_H = 14;

    private static final int FIELD_TARGET_Y = 14;
    private static final int FIELD_BATCH_Y = 40;

    private final RequesterMachine machine;
    private RequesterSlotGrid grid;
    private int forcedIndex = -1;

    private final TextFieldWidget targetField;
    private final TextFieldWidget batchField;

    /** 拖动状态（客户端）。 */
    private boolean draggingTitle;
    private double grabOffsetX;
    private double grabOffsetY;

    public RequesterAmountBox(RequesterMachine machine, int x, int y) {
        super(new Position(x, y), new Size(WIDTH, HEIGHT));
        this.machine = machine;
        setBackground(GuiTextures.BACKGROUND);

        boolean client = LDLib.isRemote();

        addWidget(new ImageWidget(PAD_X, FIELD_TARGET_Y, FIELD_W, FIELD_H, GuiTextures.DISPLAY));
        this.targetField = new TextFieldWidget(PAD_X + 2, FIELD_TARGET_Y + 1, FIELD_W - 4, FIELD_H - 2,
                client ? null : this::targetText, raw -> apply(raw, true));
        targetField.setMaxStringLength(20).setBordered(false);
        addWidget(targetField);

        addWidget(new ImageWidget(PAD_X, FIELD_BATCH_Y, FIELD_W, FIELD_H, GuiTextures.DISPLAY));
        this.batchField = new TextFieldWidget(PAD_X + 2, FIELD_BATCH_Y + 1, FIELD_W - 4, FIELD_H - 2,
                client ? null : this::batchText, raw -> apply(raw, false));
        batchField.setMaxStringLength(20).setBordered(false);
        addWidget(batchField);

        setVisible(false);
        setEditable(false);
    }

    public void attachGrid(RequesterSlotGrid grid) {
        this.grid = grid;
    }

    // ------------------------------------------------------------------
    // 选中格
    // ------------------------------------------------------------------

    private int selectedIndex() {
        if (forcedIndex >= 0) return forcedIndex;
        if (LDLib.isRemote()) {
            return grid == null ? -1 : grid.clientSelectedIndex();
        }
        return machine.getSelectedSlot();
    }

    private RequesterSlot current() {
        int sel = selectedIndex();
        if (sel < 0) return null;
        return LDLib.isRemote()
                ? (grid == null ? null : grid.snapshotOf(sel))
                : machine.slot(sel);
    }

    private boolean fluid() {
        RequesterSlot slot = current();
        return slot != null && slot.key() instanceof AEFluidKey;
    }

    private String targetText() {
        RequesterSlot slot = current();
        return slot == null || slot.isEmpty() ? "0" : Long.toString(slot.target());
    }

    private String batchText() {
        RequesterSlot slot = current();
        return slot == null || slot.isEmpty() ? "0" : Long.toString(slot.batch());
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

    // ------------------------------------------------------------------
    // 显隐
    // ------------------------------------------------------------------

    public void openFor(int slotIndex) {
        forcedIndex = slotIndex;
        setVisible(true);
        setEditable(true);
    }

    private void setEditable(boolean editable) {
        if (targetField.isActive() != editable) targetField.setActive(editable);
        if (batchField.isActive() != editable) batchField.setActive(editable);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        RequesterSlot slot = current();
        boolean show = slot != null && !slot.isEmpty();
        if (isVisible() != show) setVisible(show);
        setEditable(show);
        if (forcedIndex >= 0 && forcedIndex != selectedIndex()) {
            forcedIndex = -1;
        }
    }

    // ------------------------------------------------------------------
    // 拖动
    // ------------------------------------------------------------------

    @OnlyIn(Dist.CLIENT)
    private boolean overTitle(double mouseX, double mouseY) {
        Position pos = getPosition();
        return isMouseOver(pos.x, pos.y, WIDTH, TITLE_H, mouseX, mouseY);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible()) return false;
        if (button == 0 && overTitle(mouseX, mouseY)) {
            draggingTitle = true;
            grabOffsetX = mouseX - getPositionX();
            grabOffsetY = mouseY - getPositionY();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isVisible() && draggingTitle) {
            WidgetGroup parent = getParent();
            int maxX = (parent == null ? WIDTH : parent.getSizeWidth()) - WIDTH;
            int maxY = (parent == null ? HEIGHT : parent.getSizeHeight()) - HEIGHT;
            int nx = (int) Math.round(mouseX - grabOffsetX);
            int ny = (int) Math.round(mouseY - grabOffsetY);
            nx = Math.max(0, Math.min(nx, Math.max(0, maxX)));
            ny = Math.max(0, Math.min(ny, Math.max(0, maxY)));
            setSelfPosition(new Position(nx, ny));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingTitle = false;
        return super.mouseReleased(mouseX, mouseY, button);
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
        boolean fluid = fluid();

        // 标题栏（拖动把手）：浅色底条 + 文字，与输入总成同款字号/颜色
        int barColor = draggingTitle ? 0x40A0A0A0 : 0x20FFFFFF;
        new ColorRectTexture(barColor).draw(graphics, mouseX, mouseY, pos.x, pos.y, WIDTH, TITLE_H);
        DrawerHelper.drawStringSized(graphics, fluid ? "数量(mB)" : "数量",
                pos.x + PAD_X, pos.y + 2, 0x404040, false, 1.0F, false);
        DrawerHelper.drawStringSized(graphics, fluid ? "并发(mB)" : "并发",
                pos.x + PAD_X, pos.y + FIELD_BATCH_Y - 9, 0x404040, false, 1.0F, false);
    }

    /** 隐藏时不向 JEI 上报“额外区域”，免得 JEI 面板无故让位。 */
    @Override
    public java.util.List<net.minecraft.client.renderer.Rect2i> getGuiExtraAreas(
            net.minecraft.client.renderer.Rect2i guiRect,
            java.util.List<net.minecraft.client.renderer.Rect2i> list) {
        if (!isVisible()) return list;
        return super.getGuiExtraAreas(guiRect, list);
    }
}
