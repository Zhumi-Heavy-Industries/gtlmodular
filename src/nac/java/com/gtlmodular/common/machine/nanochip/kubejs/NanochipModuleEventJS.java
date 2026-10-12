package com.gtlmodular.common.machine.nanochip.kubejs;

import com.gtlmodular.common.machine.nanochip.registration.NanochipModules;
import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NanochipModuleEventJS extends EventJS {

    private final Map<ResourceLocation, NanochipModuleBuilderJS> builders = new LinkedHashMap<>();
    private boolean open = true;
    private boolean submitted;
    private Throwable failure;

    NanochipModuleEventJS() {
    }

    public NanochipModuleBuilderJS create(String id) {
        requireOpen();
        ResourceLocation key = NanochipModules.parseId(id);
        if (builders.containsKey(key)) {
            throw new IllegalArgumentException("Duplicate NAC module ID: " + key);
        }
        var builder = new NanochipModuleBuilderJS(key, this::requireOpen);
        builders.put(key, builder);
        return builder;
    }

    private void requireOpen() {
        if (!open) {
            throw new IllegalStateException("NAC module builders are only writable during NACEvents.registerModules");
        }
    }

    void failed(Throwable error) {
        if (failure == null) failure = error;
    }

    void close() {
        open = false;
    }

    int submit() {
        if (open || submitted) {
            throw new IllegalStateException("NAC module event must finish before submitting once");
        }
        submitted = true;
        if (failure != null) {
            throw new IllegalStateException("NAC module startup registration failed; see logs/kubejs/startup.log", failure);
        }
        var specs = builders.values().stream().map(NanochipModuleBuilderJS::build).toList();
        specs.forEach(NanochipModules::register);
        return specs.size();
    }
}
