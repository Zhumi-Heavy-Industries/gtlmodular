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

/**
 * 主机表已有自有配方，别名保持停用，以免重新引入电路组装机配方。
 * 保留别名补全与空 lookup 回填，供需要代理关系的环境使用；外部识别缓存由扩展点负责。
 */
public final class NanochipProxyTableByTypeAlias {

    private NanochipProxyTableByTypeAlias() {
    }

    // 别名会引入主机自有配方以外的配方，因此默认不参与事件处理。
    private static final boolean ENABLED = false;

    /** 轮询间隔（tick）：5 tick 一次。单次代价 = 一次反射读字段 + 一次 HashMap.containsKey。 */
    private static final int CHECK_INTERVAL_TICKS = 5;

    /**
     * {@code RecipeManager.byType} 的候选字段名：
     * 生产（SRG）{@code f_44007_}、开发（official 映射）{@code recipes}。
     * （javap -p {@code net.minecraft.world.item.crafting.RecipeManager}：
     * {@code private Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> f_44007_;}，
     * <b>非 final</b> ⇒ 反射可替换；紧邻的 {@code f_199900_} 是 byName，别认错。）
     */
    private static final String[] BY_TYPE_FIELD_NAMES = {"f_44007_", "recipes"};

    /** 已解析到的 byType 字段（字段属于类，与环境无关 ⇒ 缓存一次）。 */
    private static Field byTypeField;

    /** byType 字段解析彻底失败 ⇒ 不再重试（只打一条 WARN）。 */
    private static boolean resolutionFailed;

    /** 首次安装成功是否已打过日志。 */
    private static boolean installedLogged;

    /** 防呆回填是否已打过日志。 */
    private static boolean refillLogged;

    /** 意外异常是否已打过日志（打一条就够，不刷屏）。 */
    private static boolean failureLogged;

    private static int tickCounter;

    /**
     * 服务端 tick：保证别名在位。
     *
     * <p>挂在 {@code Phase.START}：Forge 补丁后的 {@code MinecraftServer.m_5705_} 偏移 6 是
     * {@code onPreServerTick}(= START)、偏移 20 才是 {@code m_5703_}(tickChildren → 机器 tick)
     * ⇒ START 一定早于本 tick 里的任何样板识别（javap -c 实证），而世界加载后的第一次探测就在 tick 1。
     */
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
            // 诊断型代码绝不允许影响游戏：只记一条日志，下一轮继续尝试
            if (claimFailureLog()) {
                NanochipModContext.logger().warn("[gtlmodular] 主机表 byType 别名：安装过程异常（已忽略，不影响启动）：{}", t.toString());
            }
        }
    }

    /** 旧的识别失败结果可能已落盘，区块加载时需让扩展重新检查缓存。 */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!ENABLED) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel)) {
            return;   // 只在服务端做：读 byType 的那条链路在服务端
        }
        if (!(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        NanochipRecipeCacheHooks.invalidateStaleCaches(chunk, NanochipProxyTableByTypeAlias::claimFailureLog);
    }

    // 别名安装与外部缓存清理共用一次异常日志额度，避免重复报错。
    private static boolean claimFailureLog() {
        if (failureLogged) {
            return false;
        }
        failureLogged = true;
        return true;
    }

    /** ① 给主机表在 {@code byType} 里补只读别名键；幂等、自愈。 */
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
        // 父表那一格：没有 / 还空着（加载中）⇒ 什么都不做，下个检查点再看
        Map<ResourceLocation, Recipe<?>> parent = byType.get(GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES);
        if (parent == null || parent.isEmpty()) {
            return;
        }
        if (!byType.containsKey(alias)) {
            // vanilla apply 用 ImmutableMap 收口 ⇒ 复制一份外层表再换字段。
            // 只加一个键；值 = 父表内层 Map 的同一个实例（只读共享，绝不写入）。
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

    /** 解析 {@code byType} 字段：先按名字，再按结构兜底；失败只打一条 WARN。 */
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
                // 名字撞车保护：有值时必须是"键为 RecipeType"的那张表（byName 的键是 ResourceLocation）
                if (value instanceof Map<?, ?> map && !map.isEmpty() && !looksLikeByType(map)) {
                    continue;
                }
                byTypeField = candidate;
                return candidate;
            } catch (NoSuchFieldException ignored) {
                // 这个环境没有这个名字 ⇒ 试下一个
            } catch (Throwable t) {
                if (claimFailureLog()) {
                    NanochipModContext.logger().warn("[gtlmodular] 主机表 byType 别名：试探字段 {} 时报错（已忽略）：{}", name, t.toString());
                }
            }
        }
        // 结构兜底：扫所有 Map 字段，认"键是 RecipeType"的那一个
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
                // 单个字段读不到就跳过
            }
        }
        resolutionFailed = true;
        NanochipModContext.logger().warn("[gtlmodular] 主机表 byType 别名：在 {} 上找不到「按配方类型索引」那张 Map"
                        + "（候选名 {} / 结构扫描都失败）⇒ 本修法在当前 MC/映射环境下失效，"
                        + "主机模式会退回「完全用不了」，请把这条日志发回",
                RecipeManager.class.getName(), String.join("/", BY_TYPE_FIELD_NAMES));
        return null;
    }

    /** 结构判据：这张 Map 的键是 {@code RecipeType}（byName 那张的键是 {@code ResourceLocation}）。 */
    private static boolean looksLikeByType(Map<?, ?> map) {
        if (map.isEmpty()) {
            return false;
        }
        return map.keySet().iterator().next() instanceof RecipeType;
    }

    /**
     * ③ 防呆：<b>仅当</b>主机表自己的 lookup 彻底为空时，把别名里那批 {@code GTRecipe} 恒等灌进去。
     *
     * <p>为什么这么设闸门：按证据一，装载端（{@code RecipeManagerMixin} 偏移 342-381 与
     * {@code GregTechKubeJSPlugin} 偏移 241-332）本来就会把它填满 ⇒ 常态下这里<b>直接 return</b>，
     * 既不会重复写，也不会造出重复配方。只有"装载端没填 / 被清了没填回来"这种异常情形才会动手。
     * 无论哪种情形都只写<b>我们自己的</b> lookup，父表一个字都不动。
     */
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
            return;   // 非空 ⇒ 装载端已经克隆进来了，绝不再写
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
