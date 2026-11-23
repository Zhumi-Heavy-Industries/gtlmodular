package com.gtlmodular.common.machine.requester;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import java.util.function.Supplier;

/**
 * 翻页条：左右箭头 + 「页码 / 总页数」。
 *
 * <h2>版式</h2>
 * <p>与 ME 输入总成（{@code AEDualConfigWidget}）一致：箭头 16×16，贴在网格左右两端，
 * 页码在正中。宽度由调用方传入（= 网格宽 144），保证与列宽严格对齐。
 *
 * <h2>翻页为什么不用本地状态</h2>
 * <p>页码是<b>服务端字段</b>（{@link RequesterMachine#getPage()}，{@code @DescSynced}）。
 * 按钮只在服务端改它；客户端等同步值回来，由 {@link RequesterSlotGrid} 发现页码变了再切可见性。
 */
public class RequesterPageBar extends WidgetGroup {

    private static final int ARROW = 16;
    private static final int HEIGHT = 18;

    public RequesterPageBar(RequesterMachine machine, int x, int y, int width) {
        super(x, y, width, HEIGHT);

        addWidget(new ButtonWidget(0, 1, ARROW, ARROW, GuiTextures.BUTTON_LEFT, data -> {
            if (!data.isRemote) {
                machine.setPage(machine.getPage() - 1);
            }
        }));
        addWidget(new ButtonWidget(width - ARROW, 1, ARROW, ARROW, GuiTextures.BUTTON_RIGHT, data -> {
            if (!data.isRemote) {
                machine.setPage(machine.getPage() + 1);
            }
        }));
        // 页码居中：LabelWidget 宽度随文本自适应，这里按 9px 字高把它摆到中线附近。
        addWidget(new LabelWidget(width / 2 - 12, 5, (Supplier<String>) () ->
                machine.getPage() + " / " + RequesterMachine.PAGES));
    }
}
