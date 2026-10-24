package com.gtlmodular.common.machine.independent.control;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * 机器 UI 右侧「独立线程」页：线程条数（每台机器单独设）+「允许同一种配方占多条线程」开关。
 * <p>
 * 线程数直接决定引擎给这台机器开几个批次槽（0 = 沿用机器自报的总线程数）。
 * 每台机器各存各的，所以不同的机器可以有不同条数。
 */
public final class IndependentThreadsConfigurator implements IFancyConfigurator {

    private static final int MAX_THREADS = 4096;

    private final IntSupplier threads;
    private final IntConsumer setThreads;
    private final BooleanSupplier multi;
    private final Consumer<Boolean> setMulti;

    public IndependentThreadsConfigurator(IntSupplier threads, IntConsumer setThreads,
                                          BooleanSupplier multi, Consumer<Boolean> setMulti) {
        this.threads = threads;
        this.setThreads = setThreads;
        this.multi = multi;
        this.setMulti = setMulti;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gtlmodular.independent.config.title");
    }

    @Override
    public IGuiTexture getIcon() {
        return new TextTexture("⚙");
    }

    @Override
    public List<Component> getTooltips() {
        return List.of(
                Component.translatable("gtlmodular.independent.config.threads.tip"),
                Component.translatable("gtlmodular.independent.config.multi.tip"));
    }

    @Override
    public Widget createConfigurator() {
        WidgetGroup group = new WidgetGroup(0, 0, 176, 60);
        group.addWidget(new LabelWidget(6, 4, () -> Component
                .translatable("gtlmodular.independent.config.threads", threads.getAsInt()).getString()));

        TextFieldWidget field = new TextFieldWidget(6, 18, 48, 16,
                () -> String.valueOf(threads.getAsInt()),
                text -> applyThreads(text));
        field.setNumbersOnly(0, MAX_THREADS);
        group.addWidget(field);
        group.addWidget(new ButtonWidget(58, 18, 20, 16,
                new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture("-")),
                data -> setThreads.accept(Math.max(0, threads.getAsInt() - 1))));
        group.addWidget(new ButtonWidget(80, 18, 20, 16,
                new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture("+")),
                data -> setThreads.accept(Math.min(MAX_THREADS, threads.getAsInt() + 1))));

        group.addWidget(new LabelWidget(6, 40, () -> Component
                .translatable("gtlmodular.independent.config.multi", multi.getAsBoolean()
                        ? Component.translatable("gtlmodular.independent.config.on")
                        : Component.translatable("gtlmodular.independent.config.off")).getString()));
        group.addWidget(new ButtonWidget(140, 37, 30, 16,
                new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture("⇄")),
                data -> setMulti.accept(!multi.getAsBoolean())));
        return group;
    }

    private void applyThreads(String text) {
        try {
            int value = Integer.parseInt(text.trim());
            setThreads.accept(Math.max(0, Math.min(MAX_THREADS, value)));
        } catch (NumberFormatException ignored) {
            // 输入过程中的空串/半截数字不处理，等玩家敲完。
        }
    }
}
