package com.gtlmodular.common.machine.nanochip.registration;

import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtlmodular.common.machine.nanochip.NanochipModuleKind;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** 旧枚举与机器工厂必须读取同一份规格，不能各自维护参数表。 */
public final class NanochipModules {

    public record ResolvedModule(NanochipModuleSpec spec, GTRecipeType recipeType) {
    }

    private static NanochipModuleRegistry registry;
    private static final java.util.Map<ResourceLocation, NanochipModuleSpec> pending = new java.util.LinkedHashMap<>();

    /** 收集阶段可早于 NAC 入口初始化，不能读取配方表、材料或产物注册器。 */
    public static synchronized void register(NanochipModuleSpec spec) {
        java.util.Objects.requireNonNull(spec, "spec");
        if (spec.legacyKind() != null) throw new IllegalArgumentException("Cannot register NAC builtin as extension: " + spec.id());
        if (registry != null) {
            registry.collect(spec);
        } else if (pending.putIfAbsent(spec.id(), spec) != null) {
            throw new IllegalArgumentException("Duplicate NAC module ID: " + spec.id());
        }
    }

    public static ResourceLocation parseId(String id) {
        return id.indexOf(':') < 0
                ? com.gtlmodular.common.data.nanochip.NanochipModContext.earlyRegistryId(id)
                : new ResourceLocation(id);
    }

    public static synchronized List<NanochipModuleSpec> all() {
        return registry().all();
    }

    private static synchronized NanochipModuleRegistry registry() {
        if (registry == null) {
            NanochipModuleRegistry collected = collectBuiltins();
            pending.values().forEach(collected::collect);
            registry = collected;
            pending.clear();
        }
        return registry;
    }

    private NanochipModules() {
    }

    private static NanochipModuleRegistry collectBuiltins() {
        NanochipModuleRegistry registry = new NanochipModuleRegistry();
        NanochipBuiltinModules.create().forEach(registry::collect);
        return registry;
    }

    public static NanochipModuleSpec builtin(NanochipModuleKind kind) {
        synchronized (NanochipModules.class) {
            return registry().builtin(kind);
        }
    }

    /** 完整 ID 保留命名空间，避免不同模组的同名路径命中内置规格。 */
    public static NanochipModuleSpec byId(ResourceLocation id) {
        synchronized (NanochipModules.class) {
            return registry().byId(id);
        }
    }

    /** 仅由现有机器注册窗口调用；此时配方类型已注册，机器表尚未冻结。 */
    public static List<ResolvedModule> freezeBuiltins() {
        synchronized (NanochipModules.class) {
            return registry().freeze();
        }
    }
}
