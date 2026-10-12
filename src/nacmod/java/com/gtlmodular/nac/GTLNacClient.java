package com.gtlmodular.nac;

import com.gtlmodular.client.config.NanochipConfigPage;
import com.gtlmodular.client.config.NanochipConfigRefresh;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

import java.util.Arrays;
import java.util.function.Consumer;

public final class GTLNacClient {

    private GTLNacClient() {
    }

    public static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(GTLNacClient::createConfigScreen));
    }

    private static Screen createConfigScreen(Minecraft minecraft, Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create().setParentScreen(parent)
                .setTitle(Component.literal("GTL NAC · 配置"));
        NanochipConfigPage.append(new NanochipConfigPage.Rows() {
            private ConfigCategory category;

            @Override
            public void section(String title) {
                category = builder.getOrCreateCategory(Component.literal(title));
            }

            @Override
            public void addBool(String label, boolean value, boolean fallback, Consumer<Boolean> save,
                                String... tooltipLines) {
                category.addEntry(builder.entryBuilder().startBooleanToggle(Component.literal(label), value)
                        .setDefaultValue(fallback).setSaveConsumer(save)
                        .setTooltip(Arrays.stream(tooltipLines).map(Component::literal).toArray(Component[]::new))
                        .build());
            }
        }, GTLNacConfig.NANOCHIP);
        builder.setSavingRunnable(() -> {
            GTLNacConfig.NANOCHIP.save();
            NanochipConfigRefresh.refresh();
        });
        return builder.build();
    }
}
