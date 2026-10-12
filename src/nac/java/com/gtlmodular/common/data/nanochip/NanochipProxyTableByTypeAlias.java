package com.gtlmodular.common.data.nanochip;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.lookup.Branch;
import com.gregtechceu.gtceu.api.recipe.lookup.GTRecipeLookup;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/** 主机已有自有配方，别名默认停用，避免重新引入电路组装机配方。 */
public final class NanochipProxyTableByTypeAlias {

    private NanochipProxyTableByTypeAlias() {
    }

    // 别名会引入主机自有配方以外的配方，因此默认不参与事件处理。
    private static final boolean ENABLED = false;

    private static final int CHECK_INTERVAL_TICKS = 5;

    /** 开发与生产环境的映射名称不同，需要保留两种字段名候选。 */
    private static final String[] BY_TYPE_FIELD_NAMES = {"f_44007_", "recipes"};

    private static Field byTypeField;

    private static boolean resolutionFailed;

    private static boolean installedLogged;

    private static boolean refillLogged;

    private static boolean failureLogged;

    private static int tickCounter;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!ENABLED) {
            return;
        }
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        if (tickCounter++ % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        try {
            install(server.getRecipeManager());
        } catch (Throwable t) {
            if (claimFailureLog()) {
                NanochipModContext.logger().warn("[gtlmodular] 主机表 byType 别名：安装过程异常（已忽略，不影响启动）：{}", t.toString());
            }
        }
    }

    /** 识别失败结果可能已缓存，区块加载时让扩展重新检查。 */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!ENABLED) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel)) {
            return;
        }
        if (!(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        NanochipRecipeCacheHooks.invalidateStaleCaches(chunk, NanochipProxyTableByTypeAlias::claimFailureLog);
    }

    // 别名安装与外部缓存清理共用异常日志额度，避免重复报错。
    private static boolean claimFailureLog() {
        if (failureLogged) {
            return false;
        }
        failureLogged = true;
        return true;
    }

    private static void install(RecipeManager manager) throws IllegalAccessException {
        if (manager == null || resolutionFailed) {
            return;
        }
        GTRecipeType alias = NanochipRecipeTypes.ASSEMBLY_COMPLEX;
        if (alias == null) {
            return;
        }
        Field field = resolve(manager);
        if (field == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> byType =
                (Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>) field.get(manager);
        if (byType == null) {
            return;
        }
        Map<ResourceLocation, Recipe<?>> parent = byType.get(GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES);
        if (parent == null || parent.isEmpty()) {
            return;
        }
        if (!byType.containsKey(alias)) {
            // 外层表可能不可变，复制后追加别名；内层只读共享，避免复制配方对象。
            Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> next = new LinkedHashMap<>(byType);
            next.put(alias, parent);
            field.set(manager, next);
            if (!installedLogged) {
                installedLogged = true;
                NanochipModContext.logger().info("[gtlmodular] 主机表 byType 别名已挂上：{} → 电路组装机那 {} 条（恒等共享，不动父表）",
                        alias.registryName, parent.size());
            }
        }
        refillLookupIfEmpty(parent);
    }

    private static Field resolve(RecipeManager manager) {
        if (byTypeField != null) {
            return byTypeField;
        }
        if (resolutionFailed) {
            return null;
        }
        for (String name : BY_TYPE_FIELD_NAMES) {
            try {
                Field candidate = RecipeManager.class.getDeclaredField(name);
                if (!Map.class.isAssignableFrom(candidate.getType())) {
                    continue;
                }
                candidate.setAccessible(true);
                Object value = candidate.get(manager);
                // 其他 Map 字段可能同名，必须确认键类型，避免把 byName 误当成 byType。
                if (value instanceof Map<?, ?> map && !map.isEmpty() && !looksLikeByType(map)) {
                    continue;
                }
                byTypeField = candidate;
                return candidate;
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) {
                if (claimFailureLog()) {
                    NanochipModContext.logger().warn("[gtlmodular] 主机表 byType 别名：试探字段 {} 时报错（已忽略）：{}", name, t.toString());
                }
            }
        }
        for (Field candidate : RecipeManager.class.getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(candidate.getType())) {
                continue;
            }
            try {
                candidate.setAccessible(true);
                if (candidate.get(manager) instanceof Map<?, ?> map && looksLikeByType(map)) {
                    byTypeField = candidate;
                    return candidate;
                }
            } catch (Throwable ignored) {
            }
        }
        resolutionFailed = true;
        NanochipModContext.logger().warn("[gtlmodular] 主机表 byType 别名：在 {} 上找不到「按配方类型索引」那张 Map"
                        + "（候选名 {} / 结构扫描都失败）⇒ 本修法在当前 MC/映射环境下失效，"
                        + "主机模式会退回「完全用不了」，请把这条日志发回",
                RecipeManager.class.getName(), String.join("/", BY_TYPE_FIELD_NAMES));
        return null;
    }

    private static boolean looksLikeByType(Map<?, ?> map) {
        if (map.isEmpty()) {
            return false;
        }
        return map.keySet().iterator().next() instanceof RecipeType;
    }

    /** 只在 lookup 为空时回填，避免装载端已写入后再次登记同一批配方。 */
    private static void refillLookupIfEmpty(Map<ResourceLocation, Recipe<?>> aliasRecipes) {
        GTRecipeType alias = NanochipRecipeTypes.ASSEMBLY_COMPLEX;
        if (alias == null) {
            return;
        }
        GTRecipeLookup lookup = alias.getLookup();
        if (lookup == null) {
            return;
        }
        Branch branch = lookup.getLookup();
        if (branch == null || !branch.isEmptyBranch()) {
            return;
        }
        int added = 0;
        for (Recipe<?> recipe : aliasRecipes.values()) {
            if (recipe instanceof GTRecipe gt && lookup.addRecipe(gt)) {
                added++;
            }
        }
        if (added > 0 && !refillLogged) {
            refillLogged = true;
            NanochipModContext.logger().warn("[gtlmodular] 主机表 lookup 竟然是空的（装载端没克隆进来？）⇒ 已从 byType 别名恒等回填 {} 条；"
                    + "请把这条日志发回（说明 RecipeManagerMixin/GregTechKubeJSPlugin 那条路在本包没跑）", added);
        }
    }
}
