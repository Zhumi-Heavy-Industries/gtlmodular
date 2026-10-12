package com.gtlmodular.common.machine.pattern.ui;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.TankWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public final class PatternCatalystWidgets {
    private PatternCatalystWidgets() {}

    public static IGuiTexture buttonTexture(BooleanSupplier pageOpen, boolean hover) {
        TextTexture text = new TextTexture(
                () -> Component.translatable(pageOpen.getAsBoolean()
                        ? "gtlmodular.pattern_assembly.catalyst.close"
                        : "gtlmodular.pattern_assembly.catalyst.open").getString());
        text.setWidth(114);
        text.setDropShadow(true);
        if (hover) {
            return new GuiTextureGroup(GuiTextures.BUTTON, new ColorRectTexture(0x40FFFFFF), text);
        }
        return new GuiTextureGroup(GuiTextures.BUTTON, text);
    }

    public static void openSlotFromPattern(int slotIndex, IntConsumer selectSlot, Runnable openPage,
                                           Supplier<ConfiguratorPanel> panel, Supplier<ConfiguratorPanel.Tab> tab) {
        selectSlot.accept(slotIndex + 1);
        openPage.run();
        try {
            if (panel.get() != null && tab.get() != null && panel.get().getExpanded() != tab.get()) {
                panel.get().expandTab(tab.get());
            }
        } catch (RuntimeException ignored) {
            // Slot selection remains available if the panel cannot expand.
        }
    }

    public static void layoutSideBySide(WidgetGroup catalystUI, WidgetGroup root,
                                        int pageWidth, int pageOpenHeight, boolean conditionalResize) {
        List<Widget> children = catalystUI.widgets;
        removeCacheCountWidget(children);
        Widget itemBox = findContainer(children, SlotWidget.class, null);
        Widget fluidBox = findContainer(children, TankWidget.class, itemBox);
        if (itemBox == null || fluidBox == null) {
            return;
        }
        itemBox.setSelfPosition(4, 0);
        fluidBox.setSelfPosition(4 + itemBox.getSizeWidth() + 8, 0);
        if (!conditionalResize || catalystUI.getSizeWidth() != pageWidth - 8
                || catalystUI.getSizeHeight() != pageOpenHeight - 48) {
            catalystUI.setSize(pageWidth - 8, pageOpenHeight - 48);
        }
        if (root != null) {
            root.setSize(pageWidth, pageOpenHeight);
        }
    }

    private static void removeCacheCountWidget(List<Widget> children) {
        for (int i = children.size() - 1; i >= 0; i--) {
            if (containsIntInput(children.get(i))) {
                children.remove(i);
            }
        }
    }

    private static boolean containsIntInput(Widget widget) {
        if (widget instanceof IntInputWidget) {
            return true;
        }
        if (widget instanceof WidgetGroup group) {
            for (Widget child : group.widgets) {
                if (containsIntInput(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static WidgetGroup findContainer(List<Widget> widgets, Class<?> slotType, Widget exclude) {
        for (Widget child : widgets) {
            if (child instanceof WidgetGroup group && group != exclude && containsSlot(group, slotType)) {
                return group;
            }
        }
        return null;
    }

    private static boolean containsSlot(Widget widget, Class<?> slotType) {
        if (slotType.isInstance(widget)) {
            return true;
        }
        if (widget instanceof WidgetGroup group) {
            for (Widget child : group.widgets) {
                if (containsSlot(child, slotType)) {
                    return true;
                }
            }
        }
        return false;
    }
}
