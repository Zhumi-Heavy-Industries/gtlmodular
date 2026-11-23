package com.gtlmodular.common.machine.requester;

import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

/**
 * 槽位网格容器：把<b>全部</b> {@link RequesterMachine#SLOT_COUNT} 个双层格一次性铺开，
 * 翻页只切换哪一页可见（照抄 ME 输入总成的 {@code AEDualConfigWidget}）。
 *
 * <h2>为什么不在翻页时重建控件</h2>
 * <p>第一版是"每次翻页 {@code clearAllWidgets()} + 重新 {@code addWidget()}"。
 * 这在<b>构造函数</b>里调用时，widget 还没挂到界面上，{@code uiAccess} 为 null；
 * 而 {@code Widget#writeClientAction} 的第一行就是
 * <pre>
 * if (this.uiAccess != null &amp;&amp; !this.isClientSideWidget) { ... }
 * </pre>
 * 结果：<b>JEI 拖进去完全没反应</b>。所以这里照 GTCEu 的思路：
 * 所有格子一次性建好并常驻，翻页只改 {@code setVisible}。
 */
public class RequesterSlotGrid extends WidgetGroup {

    private static final int CELL = RequesterMachine.SLOT_SIZE;
    /** 列间距 0（与 ME 输入总成完全一致：每列恰好 18px）。 */
    private static final int COLUMN_PITCH = CELL;
    /** 行间距 2px（与 ME 输入总成一致：每行 18*2+2 = 38px）。 */
    private static final int ROW_PITCH = RequesterMachine.SLOT_HEIGHT + 2;

    /** 网格宽：8 列 × 18 = 144（= AEDualConfigWidget 的 CONFIG_SIZE/2*18）。 */
    public static int width() {
        return RequesterMachine.COLUMNS * COLUMN_PITCH;
    }

    /** 网格高：2 行 × 38 - 2（末行不留间距）= 74。 */
    public static int height() {
        return RequesterMachine.ROWS * ROW_PITCH - 2;
    }

    private final RequesterMachine machine;
    private final RequesterSlotWidget[] widgets = new RequesterSlotWidget[RequesterMachine.SLOT_COUNT];
    /** 「数量」弹窗（可选；由界面层注入）。 */
    private RequesterAmountBox amountBox;
    private int shownPage = -1;

    public RequesterSlotGrid(RequesterMachine machine, int x, int y) {
        super(x, y, width(), height());
        this.machine = machine;

        for (int page = 0; page < RequesterMachine.PAGES; page++) {
            for (int row = 0; row < RequesterMachine.ROWS; row++) {
                for (int col = 0; col < RequesterMachine.COLUMNS; col++) {
                    int globalIndex = page * RequesterMachine.PAGE_SIZE
                            + row * RequesterMachine.COLUMNS + col;
                    RequesterSlotWidget widget = new RequesterSlotWidget(machine, globalIndex,
                            col * COLUMN_PITCH,
                            row * ROW_PITCH);
                    widgets[globalIndex] = widget;
                    addWidget(widget);
                }
            }
        }
        applyPageVisibility();
    }

    /** 由界面层注入「数量」弹窗：点格子时立即打开它。 */
    public void attachAmountBox(RequesterAmountBox amountBox) {
        this.amountBox = amountBox;
        for (RequesterSlotWidget widget : widgets) {
            if (widget != null) widget.setAmountBox(amountBox);
        }
    }

    /** 给外部（如「数量设置」子页）按全局下标取控件，用来读它同步下来的快照。 */
    public RequesterSlotWidget widgetAt(int globalIndex) {
        if (globalIndex < 0 || globalIndex >= widgets.length) return null;
        return widgets[globalIndex];
    }

    /** 客户端：取某格的已同步快照（服务端返回真实数据）。 */
    public RequesterSlot snapshotOf(int globalIndex) {
        RequesterSlotWidget widget = widgetAt(globalIndex);
        return widget == null ? null : widget.displayedSlot();
    }

    /**
     * 客户端：当前被选中的格子下标；-1 = 无。
     *
     * <p>选中态由每个槽位控件单独同步（服务端权威），这里只在当前页范围内找，
     * 因为 {@link RequesterMachine#setSelectedSlot} 本身也只允许选中当前页。
     */
    public int clientSelectedIndex() {
        int from = (machine.getPage() - 1) * RequesterMachine.PAGE_SIZE;
        int to = from + RequesterMachine.PAGE_SIZE;
        for (int i = from; i < to && i < widgets.length; i++) {
            RequesterSlotWidget widget = widgets[i];
            if (widget != null && widget.isClientSelected()) return i;
        }
        return -1;
    }

    /** 只让当前页的格子可见/可点。 */
    private void applyPageVisibility() {
        int page = machine.getPage();
        if (page == shownPage) return;
        shownPage = page;
        int visibleFrom = (page - 1) * RequesterMachine.PAGE_SIZE;
        int visibleTo = visibleFrom + RequesterMachine.PAGE_SIZE;
        for (int i = 0; i < widgets.length; i++) {
            boolean visible = i >= visibleFrom && i < visibleTo;
            RequesterSlotWidget widget = widgets[i];
            if (widget != null) {
                widget.setVisible(visible);
                widget.setActive(visible);
            }
        }
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        applyPageVisibility();
    }

    /**
     * <b>客户端每帧也要对齐页面可见性。</b>
     *
     * <p>这是“翻到第 2 页，第 1 页的格子仍然留着”的根因：{@code detectAndSendChanges()}
     * 只在服务端被调用（`ModularUIContainer.tick` 驱动），客户端根本不执行
     * {@link #applyPageVisibility()}，于是 {@code shownPage} 永远停在 1，所有页的格子
     * （包括第 1 页）一直是 visible。{@code WidgetGroup.updateScreen()} 是客户端每帧的钩子，
     * 这里补上它。
     */
    @Override
    public void updateScreen() {
        super.updateScreen();
        applyPageVisibility();
    }

    @Override
    public void initWidget() {
        super.initWidget();
        // 挂到界面后再对齐一次（此时 uiAccess 已就绪）
        shownPage = -1;
        applyPageVisibility();
    }
}
