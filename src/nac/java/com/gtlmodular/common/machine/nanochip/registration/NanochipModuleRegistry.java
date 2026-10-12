package com.gtlmodular.common.machine.nanochip.registration;

import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtlmodular.common.machine.nanochip.NanochipModuleKind;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class NanochipModuleRegistry {

    private final Map<ResourceLocation, NanochipModuleSpec> specs = new LinkedHashMap<>();
    private final Map<NanochipModuleKind, NanochipModuleSpec> builtins = new EnumMap<>(NanochipModuleKind.class);
    private List<NanochipModules.ResolvedModule> frozen;

    void collect(NanochipModuleSpec spec) {
        Objects.requireNonNull(spec, "spec");
        if (frozen != null) {
            throw new IllegalStateException("NAC module registry is frozen: " + spec.id());
        }
        if (specs.containsKey(spec.id())) {
            throw new IllegalArgumentException("Duplicate NAC module ID: " + spec.id());
        }
        if (spec.legacyKind() != null && builtins.containsKey(spec.legacyKind())) {
            throw new IllegalArgumentException("Duplicate NAC legacy kind: " + spec.legacyKind());
        }
        specs.put(spec.id(), spec);
        if (spec.legacyKind() != null) builtins.put(spec.legacyKind(), spec);
    }

    List<NanochipModuleSpec> all() { return List.copyOf(specs.values()); }

    NanochipModuleSpec byId(ResourceLocation id) {
        return specs.get(id);
    }

    NanochipModuleSpec builtin(NanochipModuleKind kind) {
        return Objects.requireNonNull(builtins.get(kind), "Missing NAC builtin: " + kind);
    }

    List<NanochipModules.ResolvedModule> freeze() {
        if (frozen != null) {
            return frozen;
        }
        if (!specs.values().stream().filter(spec -> spec.legacyKind() != null).toList().equals(
                java.util.Arrays.stream(NanochipModuleKind.values()).map(this::builtin).toList())) {
            throw new IllegalStateException("NAC builtins must retain legacy enum order");
        }
        List<NanochipModules.ResolvedModule> resolved = new ArrayList<>();
        for (NanochipModuleSpec spec : specs.values()) {
            com.gtlmodular.common.data.nanochip.NanochipModuleTemplates.require(spec.template(), spec.id());
            GTRecipeType recipeType = spec.recipeType();
            if (recipeType == null || GTRegistries.RECIPE_TYPES.get(recipeType.registryName) != recipeType) {
                throw new IllegalStateException("NAC module recipe type is not registered: " + spec.id());
            }
            if (GTRegistries.MACHINES.get(spec.id()) != null) {
                throw new IllegalStateException("NAC module machine ID already registered: " + spec.id());
            }
            resolved.add(new NanochipModules.ResolvedModule(spec, recipeType));
        }
        // 全部解析通过才发布冻结结果，避免工厂看到只解析了一半的目录。
        frozen = List.copyOf(resolved);
        return frozen;
    }
}
