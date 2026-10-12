package com.gtlmodular.common.machine.pattern.ui;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PatternConfiguratorWidgets {
    private PatternConfiguratorWidgets() {}

    public static Component modeComponent(GTRecipeType type) {
        if (type == null || type.registryName == null) {
            return Component.literal("?");
        }
        String key = type.registryName.toLanguageKey();
        String translated = Component.translatable(key).getString();
        if (translated.isEmpty() || translated.equals(key)) {
            return Component.literal(type.registryName.getPath());
        }
        return Component.literal(translated);
    }

    public static String recipeTypeName(String typeId) {
        try {
            ResourceLocation id = ResourceLocation.tryParse(typeId);
            if (id != null) {
                String key = id.toLanguageKey();
                String translated = Component.translatable(key).getString();
                if (!translated.isEmpty() && !translated.equals(key)) {
                    return translated;
                }
            }
        } catch (RuntimeException ignored) {
        }
        try {
            ResourceLocation id = ResourceLocation.tryParse(typeId);
            return id == null ? typeId : id.getPath();
        } catch (RuntimeException ignored) {
            return typeId;
        }
    }

    public static String modeName(List<GTRecipeType> types, int index) {
        if (types.isEmpty()) {
            return "?";
        }
        return modeComponent(types.get(Math.max(0, Math.min(types.size() - 1, index)))).getString();
    }

    public static String assignStateText(String id) {
        if (id == null || id.isEmpty()) {
            return Component.translatable("gtlmodular.pattern_assembly.mode.manual_state.none").getString();
        }
        return Component.translatable("gtlmodular.pattern_assembly.mode.manual_state.assigned",
                recipeTypeName(id)).getString();
    }

    public static Widget scaleInput(int x, int y, int width, int height,
                                    Supplier<Integer> value, Consumer<Integer> changed, int min, int max) {
        return new IntInputWidget(x, y, width, height, value, changed).setMin(min).setMax(max);
    }

    public static Widget applyButton(int x, int y, int width, int height, Runnable apply) {
        return new ButtonWidget(x, y, width, height,
                new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(
                        Component.translatable("gtlmodular.pattern_assembly.scale.apply").getString())),
                data -> apply.run())
                .setHoverTooltips(Component.translatable("gtlmodular.pattern_assembly.scale.apply.tip"));
    }

    public static Widget foaToggle(int x, int y, int width, int height,
                                   BooleanSupplier enabled, BooleanConsumer changed) {
        return new ToggleButtonWidget(x, y, width, height, GuiTextures.BUTTON_POWER, enabled, changed)
                .setTooltipText("gtlmodular.pattern_assembly.foa");
    }

    public static Widget manualActionButton(int x, int y, int width, int height,
                                            boolean clear, Runnable action) {
        String key = clear ? "gtlmodular.pattern_assembly.mode.manual_clear"
                : "gtlmodular.pattern_assembly.mode.manual_assign";
        ButtonWidget button = new ButtonWidget(x, y, width, height,
                new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(Component.translatable(key).getString())),
                data -> action.run());
        if (clear) {
            return button.setHoverTooltips(Component.translatable("gtlmodular.pattern_assembly.mode.manual_clear.tip"));
        }
        return button.setHoverTooltips(
                Component.translatable("gtlmodular.pattern_assembly.mode.manual_assign.tip"),
                Component.translatable("gtlmodular.pattern_assembly.mode.manual_rc.tip"));
    }
}
