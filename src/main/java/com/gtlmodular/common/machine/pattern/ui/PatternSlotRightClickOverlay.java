package com.gtlmodular.common.machine.pattern.ui;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.minecraft.network.FriendlyByteBuf;
import org.gtlcore.gtlcore.integration.ae2.widget.AEPatternViewExtendSlotWidget;

import java.util.function.IntConsumer;

public final class PatternSlotRightClickOverlay extends WidgetGroup {
    private static final int ACTION_OPEN_CATALYST_SLOT = 20;
    private static final int ACTION_ASSIGN_MODE_SLOT = 21;

    private final WidgetGroup base;
    private final IntConsumer assignModeSlot;
    private final IntConsumer openCatalystSlot;

    public PatternSlotRightClickOverlay(WidgetGroup base, int width, int height,
                                        IntConsumer assignModeSlot, IntConsumer openCatalystSlot) {
        super(0, 0, width, height);
        this.base = base;
        this.assignModeSlot = assignModeSlot;
        this.openCatalystSlot = openCatalystSlot;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 1 && button != 2) {
            return false;
        }
        if (button == 1 && handsNotEmpty()) {
            return false;
        }
        int slot = findPatternSlotIndex(base, mouseX, mouseY);
        if (slot < 0) {
            return false;
        }
        boolean assign = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        if (!assign) {
            // 本侧先跑一遍再发包：页签展开、页面控件都要在本帧建好，等服务端回包再建会让首包内容对不上。
            openCatalystSlot.accept(slot);
        }
        writeClientAction(assign ? ACTION_ASSIGN_MODE_SLOT : ACTION_OPEN_CATALYST_SLOT,
                buffer -> buffer.writeVarInt(slot));
        return true;
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        super.handleClientAction(id, buffer);
        if (id == ACTION_ASSIGN_MODE_SLOT) {
            assignModeSlot.accept(buffer.readVarInt());
            return;
        }
        if (id != ACTION_OPEN_CATALYST_SLOT) {
            return;
        }
        openCatalystSlot.accept(buffer.readVarInt());
    }

    private static boolean handsNotEmpty() {
        try {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            return minecraft.player != null && !minecraft.player.containerMenu.getCarried().isEmpty();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static int findPatternSlotIndex(Widget widget, double mouseX, double mouseY) {
        if (widget instanceof AEPatternViewExtendSlotWidget slot) {
            if (!slot.isMouseOverElement(mouseX, mouseY) || slot.getHandler() == null) {
                return -1;
            }
            return slot.getHandler().getContainerSlot();
        }
        if (widget instanceof WidgetGroup group) {
            for (Widget child : group.widgets) {
                int found = findPatternSlotIndex(child, mouseX, mouseY);
                if (found >= 0) {
                    return found;
                }
            }
        }
        return -1;
    }
}
