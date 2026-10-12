package com.gtlmodular.common.machine.nanochip.vacuum;

import com.gtlmodular.common.machine.nanochip.NanochipModuleMachine;
import com.gtlmodular.common.machine.nanochip.NanochipAssemblyComplexMachine;
import com.gtlmodular.common.machine.nanochip.NanochipSplitterRules;
import com.gtlmodular.common.machine.nanochip.NanochipColor;
import com.gtlmodular.common.machine.nanochip.display.NanochipLogisticsText;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.List;

public final class NanochipSplitterPanel implements IFancyConfigurator {
    private final NanochipModuleMachine machine;

    public NanochipSplitterPanel(NanochipModuleMachine machine) {
        this.machine = machine;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gtlmodular.nanochip.splitter.title");
    }

    @Override
    public IGuiTexture getIcon() {
        return new ResourceTexture("gtceu:textures/item/portable_debug_scanner.png");
    }

    @Override
    public List<Component> getTooltips() {
        return List.of(Component.translatable("gtlmodular.nanochip.splitter.title"),
                NanochipLogisticsText.deprecated());
    }

    private static IGuiTexture swatch(NanochipColor color, boolean selected) {
        ColorRectTexture rect = new ColorRectTexture();
        rect.color = 0xFF000000 | color.textColor();
        return selected ? new GuiTextureGroup(rect, new ColorBorderTexture(1, 0xFFFFFFFF)) : rect;
    }

    @Override
    public Widget createConfigurator() {
        int w = 156;
        int rowH = 22;
        WidgetGroup group = new WidgetGroup(0, 0, w, 18 + NanochipSplitterRules.MAX_RULES * rowH + 24);
        group.addWidget(new LabelWidget(4, 2,
                Component.translatable("gtlmodular.nanochip.splitter.header",
                        NanochipSplitterRules.MAX_RULES).getString())
                .setHoverTooltips(NanochipLogisticsText.deprecated()));

        List<NanochipSplitterRules.Rule> rules = machine.splitterRules().rules();
        for (int i = 0; i < rules.size(); i++) {
            NanochipSplitterRules.Rule rule = rules.get(i);
            int y = 16 + i * rowH;
            group.addWidget(new ButtonWidget(2, y, 18, 18, swatch(rule.in(), true),
                    data -> rule.cycleIn())
                    .setHoverTooltips(Component.translatable("gtlmodular.nanochip.splitter.in",
                            rule.in().nameZh())));
            group.addWidget(new LabelWidget(21, y + 5, "→"));
            group.addWidget(new ButtonWidget(30, y, 18, 18, swatch(rule.out(), true),
                    data -> rule.cycleOut())
                    .setHoverTooltips(Component.translatable("gtlmodular.nanochip.splitter.out",
                            rule.out().nameZh())));
            // 从通道现有物品中选择过滤项，避免玩家手动输入注册 ID。
            group.addWidget(new ButtonWidget(52, y, 56, 18, buttonTexture(filterLabel(rule).getString()),
                    data -> cycleFilter(rule))
                    .setHoverTooltips(Component.translatable("gtlmodular.nanochip.splitter.filter.tip")));
            group.addWidget(new IntInputWidget(112, y, 22, 18,
                    rule::redstoneThreshold, rule::setRedstoneThreshold)
                    .setMin(0).setMax(15)
                    .setHoverTooltips(Component.translatable("gtlmodular.nanochip.splitter.redstone.tip")));
            group.addWidget(new ButtonWidget(136, y, 18, 18, new ColorRectTexture(0x40FF5555),
                    data -> {
                        machine.splitterRules().removeRule(rules.indexOf(rule));
                        machine.scheduleRenderUpdate();
                    }).setHoverTooltips(Component.translatable("gtlmodular.nanochip.splitter.remove")));
        }
        group.addWidget(new ButtonWidget(2, 16 + NanochipSplitterRules.MAX_RULES * rowH, 80, 18,
                buttonTexture(Component.translatable("gtlmodular.nanochip.splitter.add").getString()),
                data -> {
                    machine.splitterRules().addRule();
                    machine.scheduleRenderUpdate();
                }));
        group.addWidget(new LabelWidget(88, 20 + NanochipSplitterRules.MAX_RULES * rowH,
                Component.translatable("gtlmodular.nanochip.splitter.hint").getString()));
        return group;
    }

    private static IGuiTexture buttonTexture(String text) {
        return new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(text));
    }

    private Component filterLabel(NanochipSplitterRules.Rule rule) {
        String filter = rule.itemFilter();
        return Component.literal(filter == null || filter.isBlank() ? "不限" : shortName(filter));
    }

    private static String shortName(String id) {
        String s = id;
        int colon = s.indexOf(':');
        if (colon >= 0) {
            s = s.substring(colon + 1);
        }
        return s.length() > 18 ? s.substring(0, 17) + "…" : s;
    }

    private void cycleFilter(NanochipSplitterRules.Rule rule) {
        NanochipAssemblyComplexMachine host = machine.getHost();
        List<String> candidates = new java.util.ArrayList<>();
        candidates.add("");
        if (host != null && host.isFormed()) {
            for (ItemStack proto : host.vacuumNetwork().prototypesOf(rule.in())) {
                var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(proto.getItem());
                if (id != null && !candidates.contains(id.toString())) {
                    candidates.add(id.toString());
                }
            }
        }
        String current = rule.itemFilter() == null ? "" : rule.itemFilter();
        int index = candidates.indexOf(current);
        rule.setFilter(candidates.get((index + 1) % candidates.size()));
    }
}
