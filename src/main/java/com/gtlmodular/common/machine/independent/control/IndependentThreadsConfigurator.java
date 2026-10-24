package com.gtlmodular.common.machine.independent.control;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * 机器 UI 右侧「独立线程」页。
 * <p>
 * 线程条数由机器代码写死（{@code threads} 只读），这里只负责显示；
 * 可切换的是「允许同一种配方同时占多条线程」，页面上直接写出开/关，不再只靠一个没文字的图标。
 */
public final class IndependentThreadsConfigurator implements IFancyConfigurator {

    private final IntSupplier threads;
    private final BooleanSupplier multi;
    private final Consumer<Boolean> setMulti;

    public IndependentThreadsConfigurator(IntSupplier threads, BooleanSupplier multi, Consumer<Boolean> setMulti) {
        this.threads = threads;
        this.multi = multi;
        this.setMulti = setMulti;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gtlmodular.independent.config.title");
    }

    @Override
    public IGuiTexture getIcon() {
        return new ResourceTexture("gtceu:textures/item/portable_debug_scanner.png");
    }

    @Override
    public List<Component> getTooltips() {
        return List.of(
                Component.translatable("gtlmodular.independent.config.threads", threads.getAsInt()),
                Component.translatable("gtlmodular.independent.config.multi",
                        Component.translatable(multi.getAsBoolean()
                                ? "gtlmodular.independent.config.on"
                                : "gtlmodular.independent.config.off")));
    }

    @Override
    public Widget createConfigurator() {
        WidgetGroup group = new WidgetGroup(0, 0, 190, 54);

        group.addWidget(new LabelWidget(6, 6, () -> Component
                .translatable("gtlmodular.independent.config.threads", threads.getAsInt()).getString()));

        group.addWidget(new LabelWidget(6, 22, () -> Component
                .translatable("gtlmodular.independent.config.multi",
                        Component.translatable(multi.getAsBoolean()
                                ? "gtlmodular.independent.config.on"
                                : "gtlmodular.independent.config.off"))
                .getString()));

        group.addWidget(new ButtonWidget(6, 36, 178, 16,
                new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture("⇄")),
                data -> setMulti.accept(!multi.getAsBoolean())));
        return group;
    }
}
